package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.InventoryCodec;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import dev.vupe.core.util.TimeUtil;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class NativeAuctionModule extends VupeModule {
    private enum SortMode { ENDING_SOON, NEWEST, HIGHEST_BID, MOST_BIDS }
    private record Session(String mode, int page, String filter, SortMode sort, String auctionId) {}

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<String> processing = new HashSet<>();
    private BukkitTask expiryTask;

    public NativeAuctionModule(VupeCore plugin) {
        super(plugin, "native-auction");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("ah", this::command);
        migrateLegacyRecords();
        long period = Math.max(10L, plugin.configs().get("auctionhouse")
            .getLong("auction-house.expiry-check-ticks", 20L));
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::settleExpired, period, period);
        Bukkit.getScheduler().runTaskLater(plugin, this::settleExpired, 20L);
    }

    @Override
    protected void onDisable() {
        if (expiryTask != null) expiryTask.cancel();
        expiryTask = null;
        sessions.clear();
        processing.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.configs().get("auctionhouse").getBoolean("auction-house.claim-notify-on-join", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            PlayerData data = plugin.data().player(player.getUniqueId());
            int wins = data.auctionClaims.size();
            int expired = data.auctionExpiredItems.size();
            if (wins + expired <= 0) return;
            Text.send(player, "<#FBBF24>You have auction items waiting: <white>" + wins
                + " won <dark_gray>• <white>" + expired + " expired<#FBBF24>. Use <white>/ah claim<#FBBF24>.");
            plugin.effects().sound(player, "reward");
        }, 30L);
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.auction-house", true)
            || !plugin.configs().get("auctionhouse").getBoolean("auction-house.enabled", true)) {
            Text.send(sender, "<red>Auction House is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }

        settleExpired();

        if (args.length == 0) {
            openMain(player, 0, "", defaultSort());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> sellCommand(player, args);
            case "claim" -> openClaims(player, false, 0);
            case "expired" -> openClaims(player, true, 0);
            case "mine", "my", "listings" -> openMine(player, 0);
            case "search" -> {
                String filter = args.length >= 2
                    ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim()
                    : "";
                openMain(player, 0, filter, defaultSort());
            }
            case "page" -> openMain(player, args.length >= 2 ? Math.max(0, parseInt(args[1], 1) - 1) : 0,
                "", defaultSort());
            case "bid" -> {
                if (args.length < 2) Text.send(player, "<red>Usage: /ah bid <auction-id>");
                else openBid(player, args[1]);
            }
            case "end", "cancel" -> {
                if (args.length < 2) Text.send(player, "<red>Usage: /ah end <auction-id>");
                else endOwnAuction(player, args[1]);
            }
            case "help" -> help(player);
            default -> {
                // `/ah <number>` convenience page syntax from old-style servers.
                int page = parseInt(sub, -1);
                if (page > 0) openMain(player, page - 1, "", defaultSort());
                else help(player);
            }
        }
        return true;
    }

    private void sellCommand(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /ah sell <starting-bid> [hours]");
            return;
        }

        double starting;
        try { starting = Double.parseDouble(args[1].replace(",", "")); }
        catch (NumberFormatException ex) {
            Text.send(player, "<red>Invalid starting bid.");
            return;
        }

        double min = plugin.configs().get("auctionhouse").getDouble("auction-house.minimum-starting-bid", 1000);
        double max = plugin.configs().get("auctionhouse").getDouble("auction-house.maximum-bid", 1.0E15);
        if (!Double.isFinite(starting) || starting < min || starting > max) {
            Text.send(player, "<red>Starting bid must be between <green>$" + Text.format(min)
                + " <red>and <green>$" + Text.format(max) + "<red>.");
            return;
        }

        int maxHours = Math.max(1, plugin.configs().get("auctionhouse").getInt("auction-house.maximum-hours", 12));
        int hours = args.length >= 3
            ? parseInt(args[2], plugin.configs().get("auctionhouse").getInt("auction-house.default-hours", maxHours))
            : plugin.configs().get("auctionhouse").getInt("auction-house.default-hours", maxHours);
        if (hours < 1 || hours > maxHours) {
            Text.send(player, "<red>Duration must be between <white>1 <red>and <white>" + maxHours + " hours<red>.");
            return;
        }

        long active = activeBySeller(player.getUniqueId()).size();
        int maxListings = Math.max(1, plugin.configs().get("auctionhouse")
            .getInt("auction-house.maximum-listings-per-player", 14));
        if (active >= maxListings && !player.hasPermission("vupe.admin")) {
            Text.send(player, "<red>You already have <white>" + active + "/" + maxListings + " <red>active listings.");
            plugin.effects().error(player);
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            Text.send(player, "<red>Hold the item stack you want to auction.");
            return;
        }

        ItemStack listing = held.clone();
        String id = Integer.toString(plugin.data().server().nextAuctionId++);

        ServerData.AuctionRecord record = new ServerData.AuctionRecord();
        record.id = id;
        record.seller = player.getUniqueId().toString();
        record.itemBase64 = InventoryCodec.encodeItem(listing);
        record.price = starting;
        record.startingBid = starting;
        record.topBid = 0;
        record.bidder = "";
        record.bids = 0;
        record.createdAt = System.currentTimeMillis();
        record.expiresAt = record.createdAt + hours * 3_600_000L;
        record.status = "ACTIVE";

        plugin.data().server().auctions.put(id, record);
        plugin.data().markServerDirty();

        player.getInventory().setItemInMainHand(null);
        Text.send(player, "<green>Listed <white>" + displayName(listing) + " ×" + listing.getAmount()
            + " <green>for a starting bid of <white>$" + Text.format(starting)
            + " <green>for <white>" + hours + "h<green>. <dark_gray>ID #" + id);
        plugin.effects().purchase(player);
    }

    private void openMain(Player player, int page, String filter, SortMode sort) {
        List<ServerData.AuctionRecord> rows = activeAuctions(filter, sort);
        int pageSize = Math.max(7, plugin.configs().get("auctionhouse").getInt("auction-house.page-size", 21));
        pageSize = Math.min(pageSize, 28);
        int maxPage = Math.max(0, (rows.size() - 1) / pageSize);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(null, 54, Text.component(
            plugin.configs().get("auctionhouse").getString("auction-house.title",
                "<gradient:#FBBF24:#F472B6><bold>VUPE AUCTION HOUSE</bold></gradient>")));
        decorate(inv);

        int[] slots = listingSlots();
        int start = page * pageSize;
        for (int i = 0; i < Math.min(pageSize, rows.size() - start) && i < slots.length; i++) {
            ServerData.AuctionRecord record = rows.get(start + i);
            ItemStack icon = listingIcon(record, player);
            setAction(icon, "listing:" + record.id);
            inv.setItem(slots[i], icon);
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        inv.setItem(45, Items.tagged(Material.CHEST, "<#34D399><bold>CLAIM WON ITEMS</bold>",
            List.of("<gray>Waiting: <white>" + data.auctionClaims.size(), "", "<yellow>Click to open."),
            "auction_action", "claims:0"));
        inv.setItem(46, Items.tagged(Material.CLOCK, "<#A78BFA><bold>EXPIRED ITEMS</bold>",
            List.of("<gray>Waiting: <white>" + data.auctionExpiredItems.size(), "", "<yellow>Click to open."),
            "auction_action", "expired:0"));
        inv.setItem(47, Items.tagged(Material.PLAYER_HEAD, "<#22D3EE><bold>YOUR AUCTIONS</bold>",
            List.of("<gray>Active: <white>" + activeBySeller(player.getUniqueId()).size(), "", "<yellow>Click to manage."),
            "auction_action", "mine:0"));

        if (page > 0) inv.setItem(48, Items.tagged(Material.ARROW, "<white>← Previous",
            List.of(), "auction_action", "main:" + (page - 1) + ":" + encodeFilter(filter) + ":" + sort.name()));

        inv.setItem(49, Items.item(Material.PAPER, "<white><bold>PAGE " + (page + 1) + " / " + (maxPage + 1) + "</bold>",
            List.of("<gray>Listings: <white>" + rows.size(),
                filter.isBlank() ? "<gray>Filter: <white>None" : "<gray>Filter: <white>" + filter,
                "<gray>Sort: <white>" + pretty(sort.name()))));

        if (page < maxPage) inv.setItem(50, Items.tagged(Material.ARROW, "<white>Next →",
            List.of(), "auction_action", "main:" + (page + 1) + ":" + encodeFilter(filter) + ":" + sort.name()));

        SortMode next = nextSort(sort);
        inv.setItem(51, Items.tagged(Material.COMPARATOR, "<#FBBF24><bold>SORT</bold>",
            List.of("<gray>Current: <white>" + pretty(sort.name()),
                "<gray>Next: <white>" + pretty(next.name()), "", "<yellow>Click to cycle."),
            "auction_action", "main:" + page + ":" + encodeFilter(filter) + ":" + next.name()));

        inv.setItem(52, Items.item(Material.NAME_TAG, "<#67E8F9><bold>SEARCH</bold>",
            List.of("<gray>Use: <white>/ah search <material/name>",
                "<gray>Examples: <white>/ah search diamond",
                "<gray>          /ah search generator")));

        inv.setItem(53, Items.item(Material.EMERALD, "<green><bold>SELL AN ITEM</bold>",
            List.of("<gray>Hold an item stack and use:",
                "<white>/ah sell <starting-bid> [hours]",
                "",
                "<gray>Maximum duration: <white>" + plugin.configs().get("auctionhouse")
                    .getInt("auction-house.maximum-hours", 12) + "h")));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(), new Session("main", page, filter, sort, ""));
        plugin.effects().open(player);
    }

    private void openBid(Player player, String id) {
        ServerData.AuctionRecord record = active(id);
        if (record == null) {
            Text.send(player, "<red>That auction no longer exists.");
            openMain(player, 0, "", defaultSort());
            return;
        }

        UUID seller = uuid(record.seller);
        if (seller != null && seller.equals(player.getUniqueId())) {
            Text.send(player, "<red>You cannot bid on your own auction.");
            plugin.effects().error(player);
            return;
        }
        if (player.getUniqueId().toString().equals(record.bidder)) {
            Text.send(player, "<red>You already have the highest bid.");
            return;
        }

        ItemStack item = safeDecode(record.itemBase64);
        if (item == null) return;
        double next = requiredBid(record);

        Inventory inv = Bukkit.createInventory(null, 27, Text.component(
            plugin.configs().get("auctionhouse").getString("auction-house.bid-title",
                "<gradient:#FBBF24:#F472B6><bold>PLACE BID</bold></gradient>")));
        decorate(inv);

        List<String> lore = listingLore(record, player);
        lore.add("");
        lore.add("<gray>Your Money: <green>$" + Text.format(plugin.modules().economy().money(player.getUniqueId())));
        lore.add("<gray>Required bid: <green>$" + Text.format(next));
        inv.setItem(13, itemWithLore(item, lore));

        inv.setItem(11, Items.tagged(Material.LIME_CONCRETE, "<green><bold>PLACE $" + Text.format(next) + " BID</bold>",
            List.of("<gray>The full bid is escrowed immediately.",
                "<gray>If someone outbids you, Vupe refunds it automatically.",
                "", "<yellow>Click to confirm."),
            "auction_action", "bidconfirm:" + id));
        inv.setItem(15, Items.tagged(Material.RED_CONCRETE, "<red><bold>CANCEL</bold>",
            List.of("<gray>Return to the Auction House."), "auction_action", "main:0::" + defaultSort().name()));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(), new Session("bid", 0, "", defaultSort(), id));
        plugin.effects().open(player);
    }

    private void placeBid(Player player, String id) {
        if (!processing.add(id)) return;
        try {
            ServerData.AuctionRecord record = active(id);
            if (record == null) {
                Text.send(player, "<red>This auction ended before your bid was processed.");
                plugin.effects().error(player);
                return;
            }

            UUID seller = uuid(record.seller);
            if (seller != null && seller.equals(player.getUniqueId())) {
                Text.send(player, "<red>You cannot bid on your own auction.");
                return;
            }
            if (player.getUniqueId().toString().equals(record.bidder)) {
                Text.send(player, "<red>You are already the highest bidder.");
                return;
            }

            double bid = requiredBid(record);
            double max = plugin.configs().get("auctionhouse").getDouble("auction-house.maximum-bid", 1.0E15);
            if (!Double.isFinite(bid) || bid > max) {
                Text.send(player, "<red>This auction has reached the maximum allowed bid.");
                return;
            }
            if (!plugin.modules().economy().takeMoney(player.getUniqueId(), bid)) {
                Text.send(player, "<red>You need <green>$" + Text.format(bid) + "<red> to place this bid.");
                plugin.effects().error(player);
                return;
            }

            String previous = record.bidder;
            double previousAmount = record.topBid;
            if (previous != null && !previous.isBlank() && previousAmount > 0) {
                UUID previousUuid = uuid(previous);
                if (previousUuid != null) {
                    plugin.modules().economy().addMoney(previousUuid, previousAmount);
                    Player previousPlayer = Bukkit.getPlayer(previousUuid);
                    if (previousPlayer != null) {
                        Text.send(previousPlayer, "<red>You were outbid on auction <white>#" + id
                            + "<red>. <green>$" + Text.format(previousAmount) + " <red>was refunded.");
                        sound(previousPlayer, "outbid");
                    }
                }
            }

            record.bidder = player.getUniqueId().toString();
            record.topBid = bid;
            record.price = bid;
            record.bids++;
            record.lastBidAt = System.currentTimeMillis();

            long remaining = record.expiresAt - System.currentTimeMillis();
            if (plugin.configs().get("auctionhouse").getBoolean("auction-house.anti-snipe.enabled", true)) {
                long trigger = Math.max(1, plugin.configs().get("auctionhouse")
                    .getLong("auction-house.anti-snipe.trigger-seconds", 30)) * 1000L;
                long extend = Math.max(1, plugin.configs().get("auctionhouse")
                    .getLong("auction-house.anti-snipe.extend-seconds", 30)) * 1000L;
                if (remaining > 0 && remaining <= trigger) record.expiresAt += extend;
            }

            plugin.data().markServerDirty();
            Text.send(player, "<green>You are now winning auction <white>#" + id
                + " <green>with <white>$" + Text.format(bid) + "<green>.");
            sound(player, "bid");
            plugin.effects().title(player, "<#FBBF24><bold>BID PLACED</bold>",
                "<gray>Auction #" + id + " • $" + Text.format(bid));
        } finally {
            processing.remove(id);
        }
    }

    private void openMine(Player player, int page) {
        List<ServerData.AuctionRecord> rows = activeBySeller(player.getUniqueId());
        int pageSize = 28, maxPage = Math.max(0, (rows.size() - 1) / pageSize);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(null, 54, Text.component(
            plugin.configs().get("auctionhouse").getString("auction-house.mine-title", "<#22D3EE><bold>YOUR AUCTIONS</bold>")));
        decorate(inv);
        int[] slots = listingSlots();
        int start = page * pageSize;
        for (int i = 0; i < Math.min(pageSize, rows.size() - start) && i < slots.length; i++) {
            ServerData.AuctionRecord record = rows.get(start + i);
            ItemStack icon = listingIcon(record, player);
            List<String> lore = new ArrayList<>();
            if (icon.hasItemMeta() && icon.getItemMeta().lore() != null) {
                lore.addAll(icon.getItemMeta().lore().stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
                    .toList());
            }
            lore.add("");
            lore.add("<red>Click to end this listing early.");
            ItemStack edited = itemWithLore(icon, lore);
            setAction(edited, "endconfirm:" + record.id);
            inv.setItem(slots[i], edited);
        }
        inv.setItem(49, Items.tagged(Material.ARROW, "<white>← Auction House",
            List.of(), "auction_action", "main:0::" + defaultSort().name()));
        player.openInventory(inv);

        sessions.put(player.getUniqueId(), new Session("mine", page, "", defaultSort(), ""));
        plugin.effects().open(player);
    }

    private void openEndConfirm(Player player, String id) {
        ServerData.AuctionRecord record = active(id);
        if (record == null || !record.seller.equals(player.getUniqueId().toString())) {
            Text.send(player, "<red>You no longer own that active listing.");
            return;
        }
        ItemStack item = safeDecode(record.itemBase64);
        if (item == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<red><bold>END AUCTION?</bold>"));
        decorate(inv);
        List<String> lore = listingLore(record, player);
        lore.add("");
        lore.add(record.bidder == null || record.bidder.isBlank()
            ? "<gray>No bids: item moves to your expired-item queue."
            : "<yellow>Has bids: ending now finalizes the current winner and pays you.");
        inv.setItem(13, itemWithLore(item, lore));
        inv.setItem(11, Items.tagged(Material.LIME_CONCRETE, "<green><bold>CONFIRM END</bold>",
            List.of("<gray>This cannot be undone."), "auction_action", "end:" + id));
        inv.setItem(15, Items.tagged(Material.RED_CONCRETE, "<red><bold>KEEP LISTED</bold>",
            List.of(), "auction_action", "mine:0"));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(), new Session("endconfirm", 0, "", defaultSort(), id));
        plugin.effects().open(player);
    }

    private void endOwnAuction(Player player, String id) {
        ServerData.AuctionRecord record = active(id);
        if (record == null || !record.seller.equals(player.getUniqueId().toString())) {
            Text.send(player, "<red>You do not own that active listing.");
            return;
        }
        settle(record, true);
        Text.send(player, "<green>Auction <white>#" + id + " <green>was ended safely.");
    }

    private void openClaims(Player player, boolean expired, int page) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        List<String> encoded = expired ? data.auctionExpiredItems : data.auctionClaims;
        int pageSize = 21, maxPage = Math.max(0, (encoded.size() - 1) / pageSize);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(null, 54, Text.component(plugin.configs().get("auctionhouse").getString(
            expired ? "auction-house.expired-title" : "auction-house.claim-title",
            expired ? "<#A78BFA><bold>EXPIRED ITEMS</bold>" : "<#34D399><bold>CLAIMED WINS</bold>")));
        decorate(inv);

        int[] slots = listingSlots();
        int start = page * pageSize;
        for (int i = 0; i < Math.min(pageSize, encoded.size() - start) && i < slots.length; i++) {
            ItemStack item = safeDecode(encoded.get(start + i));
            if (item == null) continue;
            List<String> lore = new ArrayList<>();
            lore.add(expired ? "<gray>Returned from an auction with no winner." : "<gray>You won this auction item.");
            lore.add("");
            lore.add("<yellow>Click to claim safely.");
            ItemStack icon = itemWithLore(item, lore);
            setAction(icon, (expired ? "claimexpired:" : "claimwon:") + (start + i));
            inv.setItem(slots[i], icon);
        }

        if (page > 0) inv.setItem(48, Items.tagged(Material.ARROW, "<white>← Previous",
            List.of(), "auction_action", (expired ? "expired:" : "claims:") + (page - 1)));
        inv.setItem(49, Items.tagged(Material.COMPASS, "<white><bold>AUCTION HOUSE</bold>",
            List.of("<gray>Waiting: <white>" + encoded.size(), "", "<yellow>Click to return."),
            "auction_action", "main:0::" + defaultSort().name()));
        if (page < maxPage) inv.setItem(50, Items.tagged(Material.ARROW, "<white>Next →",
            List.of(), "auction_action", (expired ? "expired:" : "claims:") + (page + 1)));
        inv.setItem(53, Items.tagged(Material.CHEST_MINECART, "<green><bold>CLAIM ALL THAT FITS</bold>",
            List.of("<gray>Items that do not fit remain safely queued."),
            "auction_action", expired ? "claimallexpired" : "claimallwon"));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(), new Session(expired ? "expired" : "claims", page, "", defaultSort(), ""));
        plugin.effects().open(player);
    }

    private void claimIndex(Player player, boolean expired, int index) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        List<String> list = expired ? data.auctionExpiredItems : data.auctionClaims;
        if (index < 0 || index >= list.size()) return;
        ItemStack item = safeDecode(list.get(index));
        if (item == null) {
            list.remove(index);
            plugin.data().markDirty(player.getUniqueId());
            return;
        }
        if (!canFit(player, item)) {
            Text.send(player, "<red>Your inventory does not have enough room for that entire stack.");
            plugin.effects().error(player);
            return;
        }
        player.getInventory().addItem(item);
        list.remove(index);
        plugin.data().markDirty(player.getUniqueId());
        sound(player, "claim");
        Text.send(player, "<green>Claimed <white>" + displayName(item) + " ×" + item.getAmount() + "<green>.");
    }

    private void claimAll(Player player, boolean expired) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        List<String> list = expired ? data.auctionExpiredItems : data.auctionClaims;
        int claimed = 0;
        for (Iterator<String> it = list.iterator(); it.hasNext();) {
            ItemStack item = safeDecode(it.next());
            if (item == null) {
                it.remove();
                continue;
            }
            if (!canFit(player, item)) continue;
            player.getInventory().addItem(item);
            it.remove();
            claimed++;
        }
        plugin.data().markDirty(player.getUniqueId());
        if (claimed > 0) {
            Text.send(player, "<green>Claimed <white>" + claimed + " <green>auction stack(s).");
            sound(player, "claim");
        } else {
            Text.send(player, "<gray>No queued items currently fit in your inventory.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !sessions.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getRawSlot() < 0
            || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        String action = Items.tag(event.getCurrentItem(), "auction_action");
        if (action == null) return;
        plugin.effects().click(player);

        String[] p = action.split(":", -1);
        try {
            switch (p[0]) {
                case "listing" -> openBid(player, p[1]);
                case "bidconfirm" -> {
                    String id = p[1];
                    player.closeInventory();
                    placeBid(player, id);
                }
                case "main" -> openMain(player, parseInt(p[1], 0),
                    p.length >= 3 ? decodeFilter(p[2]) : "",
                    p.length >= 4 ? parseSort(p[3]) : defaultSort());
                case "mine" -> openMine(player, parseInt(p[1], 0));
                case "endconfirm" -> openEndConfirm(player, p[1]);
                case "end" -> {
                    String id = p[1];
                    player.closeInventory();
                    endOwnAuction(player, id);
                }
                case "claims" -> openClaims(player, false, parseInt(p[1], 0));
                case "expired" -> openClaims(player, true, parseInt(p[1], 0));
                case "claimwon" -> {
                    claimIndex(player, false, parseInt(p[1], -1));
                    Bukkit.getScheduler().runTask(plugin, () -> openClaims(player, false, 0));
                }
                case "claimexpired" -> {
                    claimIndex(player, true, parseInt(p[1], -1));
                    Bukkit.getScheduler().runTask(plugin, () -> openClaims(player, true, 0));
                }
                case "claimallwon" -> {
                    claimAll(player, false);
                    Bukkit.getScheduler().runTask(plugin, () -> openClaims(player, false, 0));
                }
                case "claimallexpired" -> {
                    claimAll(player, true);
                    Bukkit.getScheduler().runTask(plugin, () -> openClaims(player, true, 0));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Auction GUI action failed: " + action + " -> " + ex.getMessage());
            plugin.effects().error(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (sessions.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void settleExpired() {
        long now = System.currentTimeMillis();
        List<ServerData.AuctionRecord> due = plugin.data().server().auctions.values().stream()
            .filter(this::isActive)
            .filter(r -> r.expiresAt > 0 && r.expiresAt <= now)
            .toList();
        for (ServerData.AuctionRecord record : due) settle(record, false);
    }

    private void settle(ServerData.AuctionRecord record, boolean early) {
        if (record == null || !processing.add(record.id)) return;
        try {
            ServerData.AuctionRecord current = plugin.data().server().auctions.get(record.id);
            if (current == null || !isActive(current)) return;
            current.status = "SETTLING";

            UUID seller = uuid(current.seller);
            UUID bidder = uuid(current.bidder);
            if (bidder != null && current.topBid > 0) {
                PlayerData winnerData = plugin.data().player(bidder);
                winnerData.auctionClaims.add(current.itemBase64);
                plugin.data().markDirty(bidder);

                if (seller != null) plugin.modules().economy().addMoney(seller, current.topBid);

                Player winner = Bukkit.getPlayer(bidder);
                if (winner != null) {
                    Text.send(winner, "<green>You won auction <white>#" + current.id
                        + "<green>. Claim the item with <white>/ah claim<green>.");
                    sound(winner, "sold");
                }
                Player sellerPlayer = seller == null ? null : Bukkit.getPlayer(seller);
                if (sellerPlayer != null) {
                    Text.send(sellerPlayer, "<green>Your auction <white>#" + current.id
                        + " <green>sold for <white>$" + Text.format(current.topBid) + "<green>.");
                    sound(sellerPlayer, "sold");
                }
            } else if (seller != null) {
                PlayerData sellerData = plugin.data().player(seller);
                sellerData.auctionExpiredItems.add(current.itemBase64);
                plugin.data().markDirty(seller);

                Player sellerPlayer = Bukkit.getPlayer(seller);
                if (sellerPlayer != null) {
                    Text.send(sellerPlayer, "<gray>Auction <white>#" + current.id
                        + " <gray>ended with no bids. Reclaim it with <white>/ah expired<gray>.");
                }
            }

            current.status = early ? "ENDED_EARLY" : "EXPIRED";
            plugin.data().server().auctions.remove(current.id);
            plugin.data().markServerDirty();
        } finally {
            processing.remove(record.id);
        }
    }

    private void migrateLegacyRecords() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ServerData.AuctionRecord record : plugin.data().server().auctions.values()) {
            if (record.id == null || record.itemBase64 == null || record.seller == null) continue;
            if (record.startingBid <= 0) {
                record.startingBid = record.price > 0 ? record.price : 1000;
                changed = true;
            }
            if (record.createdAt <= 0) {
                record.createdAt = Math.max(0, record.expiresAt - 43_200_000L);
                if (record.createdAt <= 0) record.createdAt = now;
                changed = true;
            }
            if (record.status == null || record.status.isBlank()) {
                record.status = "ACTIVE";
                changed = true;
            }
        }
        if (changed) plugin.data().markServerDirty();
    }

    private List<ServerData.AuctionRecord> activeAuctions(String filter, SortMode sort) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        Comparator<ServerData.AuctionRecord> comparator = switch (sort) {
            case NEWEST -> Comparator.comparingLong((ServerData.AuctionRecord r) -> r.createdAt).reversed();
            case HIGHEST_BID -> Comparator.comparingDouble(this::currentBid).reversed();
            case MOST_BIDS -> Comparator.comparingInt((ServerData.AuctionRecord r) -> r.bids).reversed();
            case ENDING_SOON -> Comparator.comparingLong(r -> r.expiresAt);
        };

        return plugin.data().server().auctions.values().stream()
            .filter(this::isActive)
            .filter(record -> {
                if (needle.isBlank()) return true;
                ItemStack item = safeDecode(record.itemBase64);
                if (item == null) return false;
                String haystack = (item.getType().name() + " " + displayName(item)).toLowerCase(Locale.ROOT);
                OfflinePlayer seller = offline(record.seller);
                if (seller != null && seller.getName() != null) haystack += " " + seller.getName().toLowerCase(Locale.ROOT);
                return haystack.contains(needle);
            })
            .sorted(comparator.thenComparingInt(r -> parseInt(r.id, Integer.MAX_VALUE)))
            .toList();
    }

    private List<ServerData.AuctionRecord> activeBySeller(UUID seller) {
        return plugin.data().server().auctions.values().stream()
            .filter(this::isActive)
            .filter(r -> seller.toString().equals(r.seller))
            .sorted(Comparator.comparingLong(r -> r.expiresAt))
            .toList();
    }

    private ServerData.AuctionRecord active(String id) {
        ServerData.AuctionRecord record = plugin.data().server().auctions.get(id);
        if (record == null || !isActive(record)) return null;
        if (record.expiresAt <= System.currentTimeMillis()) {
            settle(record, false);
            return null;
        }
        return record;
    }

    private boolean isActive(ServerData.AuctionRecord record) {
        return record != null && (record.status == null || record.status.isBlank() || record.status.equalsIgnoreCase("ACTIVE"));
    }

    private double currentBid(ServerData.AuctionRecord record) {
        return record.topBid > 0 ? record.topBid : record.startingBid;
    }

    private double requiredBid(ServerData.AuctionRecord record) {
        if (record.bids <= 0 || record.topBid <= 0) return record.startingBid;
        double increase = Math.max(0, plugin.configs().get("auctionhouse")
            .getDouble("auction-house.minimum-bid-increase-percent", 20.0)) / 100.0;
        return Math.ceil(record.topBid * (1.0 + increase) * 100.0) / 100.0;
    }

    private ItemStack listingIcon(ServerData.AuctionRecord record, Player viewer) {
        ItemStack item = safeDecode(record.itemBase64);
        if (item == null) item = new ItemStack(Material.BARRIER);
        return itemWithLore(item, listingLore(record, viewer));
    }

    private List<String> listingLore(ServerData.AuctionRecord record, Player viewer) {
        OfflinePlayer seller = offline(record.seller);
        OfflinePlayer bidder = offline(record.bidder);
        long remaining = Math.max(0, record.expiresAt - System.currentTimeMillis());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Seller: <white>" + (seller == null || seller.getName() == null ? "Unknown" : seller.getName()));
        lore.add("<gray>Bids: <white>" + record.bids);
        lore.add("<gray>Starting bid: <green>$" + Text.format(record.startingBid));
        if (record.bids > 0) {
            lore.add("<gray>Top bid: <green>$" + Text.format(record.topBid));
            lore.add("<gray>Highest bidder: <white>" + (bidder == null || bidder.getName() == null ? "Unknown" : bidder.getName()));
            lore.add("<gray>Next bid: <green>$" + Text.format(requiredBid(record)));
        } else {
            lore.add("<gray>First bid: <green>$" + Text.format(record.startingBid));
        }
        lore.add("<gray>Time left: <#FBBF24>" + TimeUtil.pretty(remaining));
        lore.add("<dark_gray>Auction #" + record.id);
        lore.add("");
        if (record.seller.equals(viewer.getUniqueId().toString())) lore.add("<#22D3EE>This is your listing.");
        else if (viewer.getUniqueId().toString().equals(record.bidder)) lore.add("<green>✓ You are currently winning.");
        else lore.add("<yellow>Click to inspect / bid.");
        return lore;
    }

    private ItemStack itemWithLore(ItemStack original, List<String> extraLore) {
        ItemStack item = original.clone();
        var meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        for (String line : extraLore) lore.add(Text.component(line));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setAction(ItemStack item, String action) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "auction_action"),
            org.bukkit.persistence.PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private boolean canFit(Player player, ItemStack incoming) {
        int remaining = incoming.getAmount();
        int max = incoming.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                remaining -= max;
            } else if (item.isSimilar(incoming)) {
                remaining -= Math.max(0, Math.min(max, item.getMaxStackSize()) - item.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void sound(Player player, String id) {
        String raw = plugin.configs().get("auctionhouse").getString("auction-house.sounds." + id, "");
        if (raw == null || raw.isBlank()) return;
        try { player.playSound(player.getLocation(), Sound.valueOf(raw), 0.8f, 1.0f); }
        catch (IllegalArgumentException ignored) {}
    }

    private ItemStack safeDecode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try { return InventoryCodec.decodeItem(encoded); }
        catch (Exception ex) {
            plugin.getLogger().warning("Could not decode auction item: " + ex.getMessage());
            return null;
        }
    }

    private OfflinePlayer offline(String raw) {
        UUID uuid = uuid(raw);
        return uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private SortMode defaultSort() {
        return parseSort(plugin.configs().get("auctionhouse").getString("auction-house.sort-default", "ENDING_SOON"));
    }

    private SortMode parseSort(String raw) {
        try { return SortMode.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception ex) { return SortMode.ENDING_SOON; }
    }

    private SortMode nextSort(SortMode current) {
        List<String> allowed = plugin.configs().get("auctionhouse").getStringList("auction-house.allowed-sorts");
        List<SortMode> modes = allowed.stream().map(this::parseSort).distinct().toList();
        if (modes.isEmpty()) modes = List.of(SortMode.values());
        int index = modes.indexOf(current);
        return modes.get((index + 1) % modes.size());
    }

    private void help(Player player) {
        Text.raw(player, "<gradient:#FBBF24:#F472B6><bold>VUPE AUCTION HOUSE</bold></gradient>");
        Text.raw(player, "<gray>/ah <dark_gray>• <white>Browse auctions");
        Text.raw(player, "<gray>/ah sell <price> [hours] <dark_gray>• <white>List held stack");
        Text.raw(player, "<gray>/ah search <text> <dark_gray>• <white>Search item/seller");
        Text.raw(player, "<gray>/ah mine <dark_gray>• <white>Your active listings");
        Text.raw(player, "<gray>/ah claim <dark_gray>• <white>Won items");
        Text.raw(player, "<gray>/ah expired <dark_gray>• <white>Unsold returns");
        Text.raw(player, "<gray>/ah end <id> <dark_gray>• <white>Safely end your listing");
    }

    private static void decorate(Inventory inv) {
        ItemStack pane = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        int rows = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) inv.setItem(i, pane);
        }
    }

    // Old AH used 21 listings/page. Keep that familiar density with a 3x7 center grid.
    private static int[] listingSlots() {
        List<Integer> list = new ArrayList<>();
        for (int row = 1; row <= 3; row++) for (int col = 1; col <= 7; col++) list.add(row * 9 + col);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); }
        catch (Exception ex) { return fallback; }
    }

    private static String displayName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        }
        return pretty(item.getType().name());
    }

    private static String pretty(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String encodeFilter(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeFilter(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return new String(Base64.getUrlDecoder().decode(raw), java.nio.charset.StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return ""; }
    }
}

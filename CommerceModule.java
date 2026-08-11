package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class CommerceModule extends VupeModule {
    private record Session(String root, String category) {}
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new HashMap<>();

    public CommerceModule(VupeCore plugin) {
        super(plugin, "commerce");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("crystalshop", this::crystalShopCommand);
        plugin.commands().register("store", this::storeCommand);
        plugin.commands().register("vupegrant", this::grantCommand);
    }

    @Override
    protected void onDisable() {
        animationTasks.values().forEach(BukkitTask::cancel);
        animationTasks.clear();
        sessions.clear();
    }

    private boolean crystalShopCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        openRoot(player, "crystal");
        return true;
    }

    private boolean storeCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        openRoot(player, "store");
        return true;
    }

    private void openRoot(Player player, String root) {
        ConfigurationSection base = base(root);
        if (base == null) {
            Text.send(player, "<red>That commerce menu is not configured.");
            plugin.effects().error(player);
            return;
        }

        int size = inventorySize(base.getInt("size", 54));
        Inventory inv = Bukkit.createInventory(null, size, Text.component(base.getString("title",
            root.equals("crystal") ? "<#8B5CF6><bold>CRYSTAL SHOP</bold>" : "<#F472B6><bold>VUPE STORE</bold>")));

        fill(inv);
        ConfigurationSection categories = base.getConfigurationSection("categories");
        if (categories != null) {
            for (String id : categories.getKeys(false)) {
                String p = id + ".";
                int slot = categories.getInt(p + "slot", -1);
                Material material = Material.matchMaterial(categories.getString(p + "material", "CHEST"));
                if (slot < 0 || slot >= inv.getSize() || material == null) continue;
                List<String> lore = new ArrayList<>(categories.getStringList(p + "lore"));
                lore.add("");
                if (root.equals("crystal")) {
                    lore.add("<gray>Balance: <#8B5CF6>" + plugin.modules().economy().crystals(player.getUniqueId()) + " ✦");
                } else {
                    lore.add("<gray>Secure purchases are fulfilled by the web store.");
                }
                lore.add("<yellow>Click to browse →");
                inv.setItem(slot, Items.tagged(material,
                    categories.getString(p + "display", id), lore, "commerce_action", "category:" + root + ":" + id));
            }
        }

        if (root.equals("crystal")) {
            inv.setItem(size - 5, Items.item(Material.AMETHYST_SHARD,
                "<#8B5CF6><bold>YOUR CRYSTALS</bold>",
                List.of("<gray>Balance: <white>" + plugin.modules().economy().crystals(player.getUniqueId()) + " ✦",
                    "", "<gray>Earn Crystals from activities, events,", "<gray>missions, voting and crates.")));
        } else {
            inv.setItem(size - 5, Items.item(Material.NETHER_STAR,
                "<gradient:#F472B6:#8B5CF6><bold>VUPE STORE</bold></gradient>",
                List.of("<gray>Ranks • Bundles • Keys • Boosters • Cosmetics",
                    "", "<gray>Purchases are granted through <white>/vupegrant<gray>.", "<gray>Tebex can execute that command automatically.")));
        }

        sessions.put(player.getUniqueId(), new Session(root, ""));
        player.openInventory(inv);
        plugin.effects().open(player);
        animate(player, inv);
    }

    private void openCategory(Player player, String root, String id) {
        ConfigurationSection base = base(root);
        if (base == null) return;
        ConfigurationSection cat = base.getConfigurationSection("categories." + id);
        if (cat == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, Text.component(
            cat.getString("display", "<white>" + id.toUpperCase(Locale.ROOT))));
        fill(inv);

        ConfigurationSection offers = cat.getConfigurationSection("offers");
        if (offers != null) {
            for (String offer : offers.getKeys(false)) {
                String p = offer + ".";
                int slot = offers.getInt(p + "slot", -1);
                Material material = Material.matchMaterial(offers.getString(p + "material", "PAPER"));
                if (slot < 0 || slot >= 45 || material == null) continue;

                List<String> lore = new ArrayList<>(offers.getStringList(p + "lore"));
                lore.add("");
                if (root.equals("crystal")) {
                    long price = offers.getLong(p + "price", 0);
                    lore.add("<gray>Price: <#8B5CF6><bold>" + price + " ✦</bold>");
                    lore.add(plugin.modules().economy().crystals(player.getUniqueId()) >= price
                        ? "<green>✓ You can afford this" : "<red>✗ You need more Crystals");
                    lore.add("");
                    lore.add("<yellow>Click to purchase");
                } else {
                    double price = offers.getDouble(p + "price-usd", 0);
                    lore.add("<gray>Price: <green><bold>$" + String.format(Locale.US, "%.2f", price) + "</bold>");
                    lore.add("");
                    lore.add("<yellow>Click for secure purchase link");
                }

                inv.setItem(slot, Items.tagged(material,
                    offers.getString(p + "display", offer), lore,
                    "commerce_action", "offer:" + root + ":" + id + ":" + offer));
            }
        }

        inv.setItem(49, Items.tagged(Material.ARROW, "<#67E8F9><bold>← BACK</bold>",
            List.of("<gray>Return to categories."), "commerce_action", "back:" + root));
        sessions.put(player.getUniqueId(), new Session(root, id));
        player.openInventory(inv);
        plugin.effects().open(player);
        animate(player, inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getClickedInventory() == null || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        String action = Items.tag(clicked, "commerce_action");
        if (action == null) return;
        plugin.effects().click(player);

        String[] split = action.split(":", 4);
        if (split[0].equals("category") && split.length >= 3) {
            openCategory(player, split[1], split[2]);
        } else if (split[0].equals("back") && split.length >= 2) {
            openRoot(player, split[1]);
        } else if (split[0].equals("offer") && split.length >= 4) {
            if (split[1].equals("crystal")) buyCrystalOffer(player, split[2], split[3]);
            else openStoreOffer(player, split[2], split[3]);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        BukkitTask task = animationTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (sessions.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void buyCrystalOffer(Player player, String category, String offerId) {
        ConfigurationSection offer = plugin.configs().get("crystalshop")
            .getConfigurationSection("crystal-shop.categories." + category + ".offers." + offerId);
        if (offer == null) return;

        long price = Math.max(0, offer.getLong("price", 0));
        if (price <= 0 || !plugin.modules().economy().takeCrystals(player.getUniqueId(), price)) {
            Text.send(player, "<red>You need <#8B5CF6>" + price + " Crystals<red>.");
            plugin.effects().error(player);
            return;
        }

        if (!grant(player, offer.getString("type", ""), offer.getString("value", ""), offer.getDouble("amount", 1))) {
            plugin.modules().economy().addCrystals(player.getUniqueId(), price);
            Text.send(player, "<red>This offer is misconfigured. Your Crystals were refunded.");
            plugin.effects().error(player);
            return;
        }

        plugin.effects().purchase(player);
        plugin.effects().title(player, "<green><bold>PURCHASE COMPLETE</bold>", offer.getString("display", offerId));
        Bukkit.getScheduler().runTaskLater(plugin, () -> openCategory(player, "crystal", category), 2L);
    }

    private void openStoreOffer(Player player, String category, String offerId) {
        ConfigurationSection offer = plugin.configs().get("store")
            .getConfigurationSection("store.categories." + category + ".offers." + offerId);
        if (offer == null) return;

        String base = plugin.configs().get("store").getString("store.url", "");
        String url = base;
        if (!url.endsWith("/")) url += "/";
        url += "?package=" + offerId;

        player.sendMessage(Text.component(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "<gradient:#F472B6:#8B5CF6><bold>VUPE STORE</bold></gradient>\n" +
            "<gray>Selected: " + offer.getString("display", offerId) + "\n" +
            "<gray>Price: <green>$" + String.format(Locale.US, "%.2f", offer.getDouble("price-usd", 0)) + "\n" +
            "<yellow><underlined>CLICK HERE TO OPEN THE SECURE STORE</underlined>\n" +
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        ).clickEvent(ClickEvent.openUrl(url)));
        plugin.effects().sound(player, "broadcast");
        player.closeInventory();
    }

    private boolean grantCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>Console/admin only.");
            return true;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /vupegrant <player> <money:x|crystals:x|generator:id:n|genslots:n|sellmulti:x|crate:id:n|autosellchest:n|sellwand:x|rank:id|tag:id|bundle:id|offer:id>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String grant = args[1];
        if (grant.startsWith("offer:")) {
            String id = grant.substring("offer:".length());
            grant = findStoreGrant(id);
            if (grant == null) {
                Text.send(sender, "<red>Unknown store offer.");
                return true;
            }
        }

        if (!grantOffline(target, grant)) {
            Text.send(sender, "<red>Unknown or invalid Vupe grant.");
            return true;
        }
        Text.send(sender, "<green>Fulfilled <white>" + grant + " <green>for <white>" + args[0] + "<green>.");
        Player online = target.getPlayer();
        if (online != null) {
            plugin.effects().title(online, "<gradient:#F472B6:#8B5CF6><bold>STORE DELIVERY</bold></gradient>",
                "<gray>Your purchase was delivered!");
            plugin.effects().celebrate(online);
        }
        return true;
    }

    public boolean grant(Player player, String typeRaw, String value, double amount) {
        String type = typeRaw == null ? "" : typeRaw.toUpperCase(Locale.ROOT);
        PlayerData data = plugin.data().player(player.getUniqueId());

        switch (type) {
            case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            case "CRYSTALS" -> plugin.modules().economy().addCrystals(player.getUniqueId(), Math.round(amount));
            case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Math.round(amount));
            case "GENERATOR" -> { return plugin.modules().generators().give(player, value, Math.max(1, (int)Math.round(amount))); }
            case "GEN_SLOTS" -> {
                data.generatorSlots += Math.max(1, (int)Math.round(amount));
                plugin.data().markDirty(player.getUniqueId());
            }
            case "SELL_MULTIPLIER" -> {
                data.sellMultiplierBonus += amount;
                plugin.data().markDirty(player.getUniqueId());
            }
            case "CRATE_KEY" -> plugin.modules().crates().addKeys(player.getUniqueId(), value, Math.max(1, (int)Math.round(amount)));
            case "RANK" -> { return plugin.modules().progression().setDonorRank((OfflinePlayer) player, value); }
            case "KIT" -> { return plugin.modules().progression().giveKit(player, value); }
            case "SELLWAND" -> player.getInventory().addItem(Items.tagged(Material.BLAZE_ROD,
                "<gold><bold>" + Text.format(amount) + "x Sellwand</bold>",
                List.of("<gray>Right-click a container to instantly sell its sellable contents."),
                "sellwand", Double.toString(amount)));
            case "LOOTBOX" -> player.getInventory().addItem(Items.tagged(Material.SHULKER_BOX,
                "<gradient:#8B5CF6:#F472B6><bold>" + pretty(value) + " Lootbox</bold></gradient>",
                List.of("<gray>Multi-roll Vupe reward bundle.", "<yellow>Right-click to open."),
                "lootbox", value));
            case "AUTOSELL_CHEST" -> plugin.modules().autosellChests().give(player, Math.max(1, (int)Math.round(amount)));
            case "TAG" -> {
                data.tags.add(value.toLowerCase(Locale.ROOT));
                plugin.data().markDirty(player.getUniqueId());
            }
            case "SELL_BOOSTER" -> {
                data.sellBoosterMultiplier = Math.max(1.0, amount);
                long minutes = parseLong(value, 30);
                data.sellBoosterUntil = System.currentTimeMillis() + minutes * 60_000L;
                plugin.data().markDirty(player.getUniqueId());
            }
            case "CRYSTAL_BOOSTER" -> {
                data.crystalBoosterMultiplier = Math.max(1.0, amount);
                long minutes = parseLong(value, 30);
                data.crystalBoosterUntil = System.currentTimeMillis() + minutes * 60_000L;
                plugin.data().markDirty(player.getUniqueId());
            }
            case "COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.replace("%player%", player.getName()));
            default -> { return false; }
        }
        return true;
    }

    private boolean grantOffline(OfflinePlayer target, String grantRaw) {
        if (target.getName() == null || grantRaw == null) return false;
        String[] parts = grantRaw.split(":", 4);
        String type = parts[0].toUpperCase(Locale.ROOT);
        String value = parts.length > 1 ? parts[1] : "";

        switch (type) {
            case "RANK" -> { return plugin.modules().progression().setDonorRank(target, value); }
            case "MONEY" -> {
                try { plugin.modules().economy().addMoney(target.getUniqueId(), Double.parseDouble(value)); return true; }
                catch (NumberFormatException ex) { return false; }
            }
            case "CRYSTALS" -> {
                try { plugin.modules().economy().addCrystals(target.getUniqueId(), Long.parseLong(value)); return true; }
                catch (NumberFormatException ex) { return false; }
            }
            case "GOLD" -> {
                try { plugin.modules().economy().addGold(target.getUniqueId(), Long.parseLong(value)); return true; }
                catch (NumberFormatException ex) { return false; }
            }
            case "TAG" -> {
                PlayerData data = plugin.data().player(target.getUniqueId());
                data.tags.add(value.toLowerCase(Locale.ROOT));
                plugin.data().markDirty(target.getUniqueId());
                return true;
            }
            case "GENERATOR" -> {
                if (parts.length < 3 || target.getPlayer() == null) return false;
                int amount;
                try { amount = Math.max(1, Integer.parseInt(parts[2])); }
                catch (NumberFormatException ex) { return false; }
                return plugin.modules().generators().give(target.getPlayer(), value, amount);
            }
            case "GENSLOTS" -> {
                int amount;
                try { amount = Math.max(1, Integer.parseInt(value)); }
                catch (NumberFormatException ex) { return false; }
                PlayerData data = plugin.data().player(target.getUniqueId());
                data.generatorSlots += amount;
                plugin.data().markDirty(target.getUniqueId());
                return true;
            }
            case "SELLMULTI" -> {
                double amount;
                try { amount = Math.max(0, Double.parseDouble(value)); }
                catch (NumberFormatException ex) { return false; }
                PlayerData data = plugin.data().player(target.getUniqueId());
                data.sellMultiplierBonus += amount;
                plugin.data().markDirty(target.getUniqueId());
                return true;
            }
            case "CRATE" -> {
                if (parts.length < 3) return false;
                int amount;
                try { amount = Math.max(1, Integer.parseInt(parts[2])); }
                catch (NumberFormatException ex) { return false; }
                plugin.modules().crates().addKeys(target.getUniqueId(), value, amount);
                return true;
            }
            case "AUTOSELLCHEST" -> {
                if (target.getPlayer() == null) return false;
                int amount;
                try { amount = Math.max(1, Integer.parseInt(value)); }
                catch (NumberFormatException ex) { return false; }
                plugin.modules().autosellChests().give(target.getPlayer(), amount);
                return true;
            }
            case "SELLWAND" -> {
                if (target.getPlayer() == null) return false;
                double multiplier;
                try { multiplier = Math.max(1, Double.parseDouble(value)); }
                catch (NumberFormatException ex) { return false; }
                target.getPlayer().getInventory().addItem(Items.tagged(Material.BLAZE_ROD,
                    "<gold><bold>" + Text.format(multiplier) + "x Sellwand</bold>",
                    List.of("<gray>Right-click a container to instantly sell its sellable contents."),
                    "sellwand", Double.toString(multiplier)));
                return true;
            }
            case "LOOTBOX" -> {
                if (target.getPlayer() == null) return false;
                target.getPlayer().getInventory().addItem(Items.tagged(Material.SHULKER_BOX,
                    "<gradient:#8B5CF6:#F472B6><bold>" + pretty(value) + " Lootbox</bold></gradient>",
                    List.of("<gray>Multi-roll Vupe reward bundle.", "<yellow>Right-click to open."),
                    "lootbox", value));
                return true;
            }
            case "BUNDLE" -> {
                List<String> commands = plugin.configs().get("store").getStringList("store.grant-bundles." + value);
                if (commands.isEmpty()) return false;
                for (String command : commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", target.getName()));
                return true;
            }
            case "BOOSTER" -> {
                Player online = target.getPlayer();
                if (online == null || parts.length < 4) return false;
                double multi;
                long minutes;
                try { multi = Double.parseDouble(parts[2]); minutes = Long.parseLong(parts[3]); }
                catch (NumberFormatException ex) { return false; }
                return grant(online, value.equalsIgnoreCase("crystal") ? "CRYSTAL_BOOSTER" : "SELL_BOOSTER",
                    Long.toString(minutes), multi);
            }
            case "GLOBALBOOSTER" -> {
                if (parts.length < 4) return false;
                double multiplier;
                long minutes;
                try {
                    multiplier = Math.max(1.0, Double.parseDouble(parts[2]));
                    minutes = Math.max(1L, Long.parseLong(parts[3]));
                } catch (NumberFormatException ex) {
                    return false;
                }
                long until = System.currentTimeMillis() + minutes * 60_000L;
                if (value.equalsIgnoreCase("sell")) {
                    plugin.data().server().globalSellBoosterMultiplier = multiplier;
                    plugin.data().server().globalSellBoosterUntil = until;
                } else if (value.equalsIgnoreCase("crystal")) {
                    plugin.data().server().globalCrystalBoosterMultiplier = multiplier;
                    plugin.data().server().globalCrystalBoosterUntil = until;
                } else {
                    return false;
                }
                plugin.data().markServerDirty();
                plugin.effects().broadcast(Text.prefix() + "<#F472B6><bold>GLOBAL BOOSTER</bold> <gray>"
                    + Text.format(multiplier) + "x " + value + " for " + minutes + " minutes.", "broadcast");
                return true;
            }
            default -> { return false; }
        }
    }

    private String findStoreGrant(String offerId) {
        ConfigurationSection cats = plugin.configs().get("store").getConfigurationSection("store.categories");
        if (cats == null) return null;
        for (String cat : cats.getKeys(false)) {
            String path = cat + ".offers." + offerId + ".grant";
            if (cats.contains(path)) return cats.getString(path);
        }
        return null;
    }


    private ConfigurationSection base(String root) {
        return root.equals("crystal")
            ? plugin.configs().get("crystalshop").getConfigurationSection("crystal-shop")
            : plugin.configs().get("store").getConfigurationSection("store");
    }

    private void animate(Player player, Inventory inv) {
        BukkitTask old = animationTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();
        if (!plugin.configs().get("effects").getBoolean("gui.animate-borders", true)) return;
        long period = Math.max(4L, plugin.configs().get("effects").getLong("gui.animation-period-ticks", 8L));
        final int[] frame = {0};
        Material[] glass = {Material.PURPLE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();
                return;
            }
            Material material = glass[(frame[0]++) % glass.length];
            for (int slot : borderSlots(inv.getSize())) {
                ItemStack existing = inv.getItem(slot);
                if (existing == null || existing.getType().name().endsWith("STAINED_GLASS_PANE")) {
                    inv.setItem(slot, Items.item(material, " ", List.of()));
                }
            }
        }, period, period);
        animationTasks.put(player.getUniqueId(), task);
    }

    private static void fill(Inventory inv) {
        ItemStack filler = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : borderSlots(inv.getSize())) if (inv.getItem(slot) == null) inv.setItem(slot, filler);
    }

    private static int[] borderSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) slots.add(i);
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int inventorySize(int size) {
        size = Math.max(9, Math.min(54, size));
        return ((size + 8) / 9) * 9;
    }

    private static long parseLong(String raw, long fallback) {
        try { return Long.parseLong(raw); } catch (Exception ex) { return fallback; }
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.replace('_',' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}

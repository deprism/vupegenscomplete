package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;

import java.util.*;

public final class ShopModule extends VupeModule {
    private final Map<UUID, String> openMenus = new HashMap<>();

    public ShopModule(VupeCore plugin) {
        super(plugin, "shop");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("sell", this::sellCommand);
        plugin.commands().register("autosell", this::autosellCommand);
        plugin.commands().register("boosters", this::boostersCommand);
        plugin.commands().register("box", this::boxCommand);
        plugin.commands().register("sellwandgive", this::sellwandCompatibility);
    }

    public double effectiveSellMultiplier(Player player) {
        return effectiveSellMultiplier(player.getUniqueId());
    }

    public double effectiveSellMultiplier(UUID uuid) {
        PlayerData data = plugin.data().player(uuid);
        double booster = data.sellBoosterUntil > System.currentTimeMillis()
            ? Math.max(1.0, data.sellBoosterMultiplier) : 1.0;
        if (data.sellBoosterUntil <= System.currentTimeMillis() && data.sellBoosterMultiplier != 1.0) {
            data.sellBoosterMultiplier = 1.0;
            data.sellBoosterUntil = 0;
            plugin.data().markDirty(uuid);
        }
        double global = plugin.data().server().globalSellBoosterUntil > System.currentTimeMillis()
            ? Math.max(1.0, plugin.data().server().globalSellBoosterMultiplier) : 1.0;
        double base = plugin.configs().get("economy").getDouble("sell.multiplier-base", 1.0);
        return Math.max(0, base + data.sellMultiplierBonus) * booster * global;
    }

    public double sellInventory(Player player, Inventory inventory) {
        double base = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            double each = sellValue(item);
            if (each <= 0) continue;
            base += each * item.getAmount();
            inventory.setItem(slot, null);
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        for (Map.Entry<String, Integer> entry : new HashMap<>(data.virtualGeneratorStorage).entrySet()) {
            GeneratorModule.GeneratorType type = plugin.modules().generators().type(entry.getKey());
            if (type == null || entry.getValue() <= 0) continue;
            base += type.sell() * entry.getValue();
            data.virtualGeneratorStorage.remove(entry.getKey());
        }

        if (base <= 0) return 0;
        double finalValue = Math.round(base * effectiveSellMultiplier(player) * 100.0) / 100.0;
        plugin.modules().economy().addMoney(player.getUniqueId(), finalValue);
        plugin.modules().events().progress(player, "sell", finalValue);
        plugin.data().markDirty(player.getUniqueId());
        return finalValue;
    }

    public double sellValue(ItemStack item) {
        String compressed = Items.tag(item, "compressed_generator_drop");
        if (compressed != null) {
            String[] parts = compressed.split(":", 2);
            if (parts.length == 2) {
                GeneratorModule.GeneratorType type = plugin.modules().generators().type(parts[0]);
                try {
                    int count = Integer.parseInt(parts[1]);
                    if (type != null && count > 0) return type.sell() * count;
                } catch (NumberFormatException ignored) {}
            }
        }

        String genDrop = Items.tag(item, "generator_drop_type");
        if (genDrop != null) {
            GeneratorModule.GeneratorType type = plugin.modules().generators().type(genDrop);
            if (type != null) return type.sell();
        }
        return plugin.configs().get("economy").getDouble("sell.inventory." + item.getType().name(), 0);
    }


    private boolean sellwandCompatibility(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /sellwandgive <player> <multiplier>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(sender, "<red>Player not found.");
            return true;
        }
        double multiplier;
        try { multiplier = Math.max(1, Double.parseDouble(args[1])); }
        catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid multiplier."); return true; }
        target.getInventory().addItem(Items.tagged(Material.BLAZE_ROD,
            "<gold><bold>" + Text.format(multiplier) + "x Sellwand</bold>",
            List.of("<gray>Right-click a container to sell its contents."),
            "sellwand", Double.toString(multiplier)));
        Text.send(sender, "<green>Sellwand given.");
        return true;
    }

    private boolean autosellCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (!plugin.configs().modules().getBoolean("modules.autosell", true)
            || !plugin.configs().get("shops").getBoolean("autosell.enabled", true)) {
            Text.send(player, "<red>Autosell is disabled.");
            return true;
        }
        String permission = plugin.configs().get("shops").getString("autosell.permission", "");
        if (permission != null && !permission.isBlank()
            && !player.hasPermission(permission) && !player.hasPermission("vupe.admin")) {
            Text.send(player, "<red>You do not have access to autosell.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.autosellEnabled = !data.autosellEnabled;
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<gray>Generator autosell: " + (data.autosellEnabled ? "<green>enabled" : "<red>disabled"));
        return true;
    }

    private boolean boostersCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        long now = System.currentTimeMillis();
        Text.raw(player, "<#8B5CF6><bold>VUPE BOOSTERS</bold>");
        Text.raw(player, "<gray>Sell booster: <white>" +
            (data.sellBoosterUntil > now ? Text.format(data.sellBoosterMultiplier) + "x (" +
                dev.vupe.core.util.TimeUtil.pretty(data.sellBoosterUntil - now) + ")" : "None"));
        Text.raw(player, "<gray>Crystal booster: <white>" +
            (data.crystalBoosterUntil > now ? Text.format(data.crystalBoosterMultiplier) + "x (" +
                dev.vupe.core.util.TimeUtil.pretty(data.crystalBoosterUntil - now) + ")" : "None"));
        return true;
    }

    private boolean sellCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        double value = sellInventory(player, player.getInventory());
        if (value <= 0) Text.send(player, "<red>You have nothing sellable.");
        else Text.send(player, "<gray>Sold items for <green>$" + Text.format(value) + "<gray>.");
        return true;
    }

    private boolean shopCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return handleAdmin(player, Arrays.copyOfRange(args, 1, args.length));
        }

        openGeneratorShop(player);
        return true;
    }

    private void openGeneratorShop(Player player) {
        int size = 54;
        Inventory inventory = Bukkit.createInventory(null, size, Text.component(
            plugin.configs().get("shops").getString("shop.title", "<#8B5CF6><bold>VUPE SHOP</bold>")
        ));

        int slot = 0;
        for (GeneratorModule.GeneratorType type : plugin.modules().generators().types()) {
            if (slot >= 45) break;
            double price = Math.max(1, type.upgrade() > 0 ? type.upgrade() : type.sell() * 100);
            ItemStack button = Items.tagged(
                type.block(),
                "<" + type.color() + "><bold>" + type.display() + " Core</bold>",
                List.of(
                    "<gray>Buy price: <green>$" + Text.format(price),
                    "<gray>Drop value: <green>$" + Text.format(type.sell()),
                    "",
                    "<yellow>Click to buy 1."
                ),
                "shop_generator",
                type.id()
            );
            inventory.setItem(slot++, button);
        }

        inventory.setItem(49, Items.item(Material.EMERALD, "<green><bold>SELL INVENTORY</bold>", List.of(
            "<gray>Current multiplier: <white>" + Text.format(effectiveSellMultiplier(player)) + "x",
            "",
            "<yellow>Click to sell all."
        )));
        inventory.setItem(53, Items.item(Material.NETHER_STAR, "<#8B5CF6><bold>GOLD STORE</bold>", List.of("<yellow>Click to browse.")));

        openMenus.put(player.getUniqueId(), "shop");
        player.openInventory(inventory);
    }

    private boolean storeCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.gold-store", true)) {
            Text.send(sender, "<red>Gold store is disabled."); return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        openStore(player);
        return true;
    }

    private void openStore(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<gradient:#8B5CF6:#F472B6><bold>VUPE STORE</bold></gradient>"));
        PlayerData data = plugin.data().player(player.getUniqueId());

        int slot = 9;
        ConfigurationSection ranks = plugin.configs().get("shops").getConfigurationSection("gold-store.ranks");
        if (ranks != null) {
            for (String id : ranks.getKeys(false)) {
                if (slot > 13) break;
                long price = ranks.getLong(id);
                inv.setItem(slot++, Items.tagged(Material.NETHER_STAR,
                    plugin.modules().progression().donorDisplay(id),
                    List.of("<gray>Price: <gold>" + price + " Gold", "", "<yellow>Click to buy."),
                    "store_rank", id));
            }
        }

        inv.setItem(16, Items.item(Material.GOLD_INGOT, "<gold><bold>YOUR GOLD</bold>", List.of(
            "<gray>Balance: <gold>" + data.gold,
            "<gray>Store URL: <white>" + plugin.configs().get("branding").getString("brand.website", "CHANGE_ME_STORE_URL")
        )));
        inv.setItem(22, Items.item(Material.TRIPWIRE_HOOK, "<#22D3EE><bold>CRATE KEYS</bold>", List.of("<gray>Use <white>/crates<gray> to view your keys.")));

        openMenus.put(player.getUniqueId(), "store");
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getRawSlot() < 0) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (menu.equals("shop")) {
            String typeId = Items.tag(clicked, "shop_generator");
            if (typeId != null) {
                GeneratorModule.GeneratorType type = plugin.modules().generators().type(typeId);
                if (type == null) return;
                double price = Math.max(1, type.upgrade() > 0 ? type.upgrade() : type.sell() * 100);
                if (!plugin.modules().economy().takeMoney(player.getUniqueId(), price)) {
                    Text.send(player, "<red>You need <green>$" + Text.format(price) + "<red>.");
                    return;
                }
                plugin.modules().generators().give(player, type.id(), 1);
                Text.send(player, "<gray>Bought one <" + type.color() + ">" + type.display() + " Core<gray>.");
                return;
            }
            if (event.getRawSlot() == 49) {
                double sold = sellInventory(player, player.getInventory());
                Text.send(player, sold > 0 ? "<gray>Sold for <green>$" + Text.format(sold) : "<red>Nothing sellable.");
                return;
            }
            if (event.getRawSlot() == 53) {
                openStore(player);
            }
        } else if (menu.equals("crystalshop")) {
            String offer = Items.tag(clicked, "crystal_offer");
            if (offer != null) {
                buyCrystalOffer(player, offer);
                crystalShopCommand(player, "crystalshop", new String[0]);
            }
        } else if (menu.equals("store")) {
            String rank = Items.tag(clicked, "store_rank");
            if (rank != null) {
                long price = plugin.configs().get("shops").getLong("gold-store.ranks." + rank, -1);
                if (price <= 0) return;
                if (!plugin.modules().economy().takeGold(player.getUniqueId(), price)) {
                    Text.send(player, "<red>You need <gold>" + price + " Gold<red>.");
                    return;
                }
                plugin.modules().progression().setDonorRank(player, rank);
                Text.send(player, "<gray>You unlocked " + plugin.modules().progression().donorDisplay(rank) + "<gray>.");
                openStore(player);
            }
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpecialItemUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        String simpleBox = Items.tag(item, "simple_box");
        if (simpleBox != null && plugin.configs().modules().getBoolean("modules.boxes", true)) {
            event.setCancelled(true);
            consumeOne(item);
            openSimpleBox(event.getPlayer(), simpleBox);
            return;
        }

        String lootbox = Items.tag(item, "lootbox");
        if (lootbox != null && plugin.configs().modules().getBoolean("modules.boxes", true)) {
            event.setCancelled(true);
            consumeOne(item);
            openLootbox(event.getPlayer(), lootbox);
            return;
        }

        String voucher = Items.tag(item, "voucher");
        if (voucher != null && plugin.configs().modules().getBoolean("modules.vouchers", true)) {
            event.setCancelled(true);
            redeemVoucher(event.getPlayer(), item, voucher);
            return;
        }

        String box = Items.tag(item, "box_crate");
        if (box != null) {
            event.setCancelled(true);
            consumeOne(item);
            plugin.modules().crates().openFree(event.getPlayer(), box);
            return;
        }

        String wand = Items.tag(item, "sellwand");
        if (wand != null && plugin.configs().modules().getBoolean("modules.sellwands", true) && event.getClickedBlock() != null) {
            BlockState state = event.getClickedBlock().getState();
            if (!(state instanceof InventoryHolder holder)) return;
            event.setCancelled(true);
            double multiplier;
            try { multiplier = Double.parseDouble(wand); }
            catch (NumberFormatException ex) { multiplier = 1; }

            double base = 0;
            Inventory inventory = holder.getInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack == null) continue;
                double each = sellValue(stack);
                if (each <= 0) continue;
                base += each * stack.getAmount();
                inventory.setItem(i, null);
            }
            if (base <= 0) {
                Text.send(event.getPlayer(), "<red>No sellable items in that container.");
                return;
            }
            double total = base * multiplier * effectiveSellMultiplier(event.getPlayer());
            plugin.modules().economy().addMoney(event.getPlayer().getUniqueId(), total);
            plugin.modules().events().progress(event.getPlayer(), "sell", total);
            Text.send(event.getPlayer(), "<gray>Sellwand sold the container for <green>$" + Text.format(total) + "<gray>.");
        }
    }

    private boolean handleAdmin(Player sender, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<red>/shop admin <voucher|box|booster|sellwand> ...");
            return true;
        }

        if (args[0].equalsIgnoreCase("voucher") && args.length >= 5) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { Text.send(sender, "<red>Player not found."); return true; }
            String type = args[2].toUpperCase(Locale.ROOT);
            String value = args[3];
            int amount;
            try { amount = Math.max(1, Integer.parseInt(args[4])); } catch (NumberFormatException ex) { amount = 1; }
            ItemStack voucher = Items.tagged(Material.PAPER,
                "<#F472B6><bold>Vupe Voucher</bold>",
                List.of("<gray>Type: <white>" + type, "<gray>Value: <white>" + value, "", "<yellow>Right-click to redeem."),
                "voucher", type + ":" + value);
            voucher.setAmount(Math.min(amount, 64));
            target.getInventory().addItem(voucher);
            return true;
        }

        if (args[0].equalsIgnoreCase("box") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || plugin.modules().crates().crate(args[2]) == null) {
                Text.send(sender, "<red>Invalid player or crate reward table.");
                return true;
            }
            target.getInventory().addItem(Items.tagged(Material.ENDER_CHEST,
                "<#8B5CF6><bold>Vupe Box</bold>",
                List.of("<gray>Reward table: <white>" + args[2], "", "<yellow>Right-click to open."),
                "box_crate", args[2].toLowerCase(Locale.ROOT)));
            return true;
        }

        if (args[0].equalsIgnoreCase("globalbooster") && args.length >= 4) {
            double multiplier;
            long minutes;
            try {
                multiplier = Math.max(1, Double.parseDouble(args[2]));
                minutes = Math.max(1, Long.parseLong(args[3]));
            } catch (NumberFormatException ex) {
                Text.send(sender, "<red>Usage: /shop admin globalbooster <sell|crystal> <multiplier> <minutes>");
                return true;
            }
            long until = System.currentTimeMillis() + minutes * 60_000L;
            if (args[1].equalsIgnoreCase("sell")) {
                plugin.data().server().globalSellBoosterMultiplier = multiplier;
                plugin.data().server().globalSellBoosterUntil = until;
            } else if (args[1].equalsIgnoreCase("crystal")) {
                plugin.data().server().globalCrystalBoosterMultiplier = multiplier;
                plugin.data().server().globalCrystalBoosterUntil = until;
            } else {
                Text.send(sender, "<red>Global booster type must be sell or crystal.");
                return true;
            }
            plugin.data().markServerDirty();
            Bukkit.broadcast(Text.component(Text.prefix() + "<#F472B6><bold>GLOBAL BOOSTER</bold> <gray>"
                + Text.format(multiplier) + "x " + args[1] + " for " + minutes + " minutes."));
            return true;
        }

        if (args[0].equalsIgnoreCase("booster") && args.length >= 5) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { Text.send(sender, "<red>Player not found."); return true; }
            double multiplier;
            long minutes;
            try {
                multiplier = Math.max(1, Double.parseDouble(args[3]));
                minutes = Math.max(1, Long.parseLong(args[4]));
            } catch (NumberFormatException ex) {
                Text.send(sender, "<red>Invalid multiplier/minutes.");
                return true;
            }
            PlayerData data = plugin.data().player(target.getUniqueId());
            long until = System.currentTimeMillis() + minutes * 60_000L;
            if (args[2].equalsIgnoreCase("sell")) {
                data.sellBoosterMultiplier = multiplier;
                data.sellBoosterUntil = until;
            } else if (args[2].equalsIgnoreCase("crystal")) {
                data.crystalBoosterMultiplier = multiplier;
                data.crystalBoosterUntil = until;
            } else {
                Text.send(sender, "<red>Booster type must be sell or crystal.");
                return true;
            }
            plugin.data().markDirty(target.getUniqueId());
            Text.send(sender, "<green>Booster applied.");
            return true;
        }

        if (args[0].equalsIgnoreCase("sellwand") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return true;
            double mult;
            try { mult = Double.parseDouble(args[2]); } catch (NumberFormatException ex) { mult = 2; }
            target.getInventory().addItem(Items.tagged(Material.BLAZE_ROD,
                "<gold><bold>" + Text.format(mult) + "x Sellwand</bold>",
                List.of("<gray>Right-click a container to sell its contents."),
                "sellwand", Double.toString(mult)));
            return true;
        }

        Text.send(sender, "<red>Invalid admin shop command.");
        return true;
    }


    private boolean crystalShopCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.crystal-shop", true)) {
            Text.send(sender, "<red>Crystal shop is disabled."); return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        Inventory inv = Bukkit.createInventory(null, 27, Text.component(
            plugin.configs().get("crystalshop").getString("crystal-shop.title", "<#8B5CF6><bold>CRYSTAL SHOP</bold>")
        ));
        ConfigurationSection offers = plugin.configs().get("crystalshop").getConfigurationSection("crystal-shop.offers");
        if (offers != null) {
            for (String id : offers.getKeys(false)) {
                int slot = offers.getInt(id + ".slot", 0);
                Material material = Material.matchMaterial(offers.getString(id + ".material", "PAPER"));
                if (material == null || slot < 0 || slot >= inv.getSize()) continue;
                long price = offers.getLong(id + ".price", 1);
                inv.setItem(slot, Items.tagged(material,
                    offers.getString(id + ".display", id),
                    List.of("<gray>Price: <#8B5CF6>" + price + " Crystals", "", "<yellow>Click to buy."),
                    "crystal_offer", id));
            }
        }
        openMenus.put(player.getUniqueId(), "crystalshop");
        player.openInventory(inv);
        return true;
    }

    private boolean boxCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.boxes", true)) {
            Text.send(sender, "<red>Boxes are disabled."); return true;
        }
        if (args.length == 0) {
            Text.send(sender, "<gray>Boxes are configurable in <white>boxes.yml<gray>.");
            return true;
        }
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 4 || !args[0].equalsIgnoreCase("give")) {
            Text.send(sender, "<red>/box give <player> <money|crystals|lootbox> <rarity|id> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, "<red>Player not found.");
            return true;
        }
        int amount = 1;
        if (args.length >= 5) {
            try { amount = Math.max(1, Integer.parseInt(args[4])); } catch (NumberFormatException ignored) {}
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String value = args[3].toLowerCase(Locale.ROOT);
        ItemStack box;
        if (type.equals("lootbox")) {
            String display = plugin.configs().get("boxes").getString("lootboxes.definitions." + value + ".display", value);
            box = Items.tagged(Material.SHULKER_BOX, display,
                List.of("<gray>Contains multiple configurable Vupe rewards.", "", "<yellow>Right-click to open."),
                "lootbox", value);
        } else {
            String path = "boxes.types." + type + "." + value;
            if (!plugin.configs().get("boxes").contains(path)) {
                Text.send(sender, "<red>Unknown box type/rarity.");
                return true;
            }
            box = Items.tagged(Material.ENDER_CHEST,
                "<gradient:#8B5CF6:#22D3EE><bold>" + prettify(value) + " " + prettify(type) + " Box</bold></gradient>",
                List.of("<gray>Right-click to reveal your reward."),
                "simple_box", type + ":" + value);
        }
        box.setAmount(Math.min(amount, box.getMaxStackSize()));
        target.getInventory().addItem(box);
        return true;
    }

    private void buyCrystalOffer(Player player, String id) {
        String path = "crystal-shop.offers." + id;
        long price = plugin.configs().get("crystalshop").getLong(path + ".price", -1);
        if (price <= 0 || !plugin.modules().economy().takeCrystals(player.getUniqueId(), price)) {
            Text.send(player, "<red>You do not have enough crystals.");
            return;
        }
        String type = plugin.configs().get("crystalshop").getString(path + ".type", "MONEY").toUpperCase(Locale.ROOT);
        String value = plugin.configs().get("crystalshop").getString(path + ".value", "");
        double amount = plugin.configs().get("crystalshop").getDouble(path + ".amount", 1);
        switch (type) {
            case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Math.round(amount));
            case "GEN_SLOTS" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.generatorSlots += Math.max(1, (int) Math.round(amount));
                plugin.data().markDirty(player.getUniqueId());
            }
            case "SELL_MULTIPLIER" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.sellMultiplierBonus += amount;
                plugin.data().markDirty(player.getUniqueId());
            }
            case "GENERATOR" -> plugin.modules().generators().give(player, value, Math.max(1, (int) Math.round(amount)));
            case "CRATE_KEY" -> plugin.modules().crates().addKeys(player.getUniqueId(), value, Math.max(1, (int) Math.round(amount)));
            case "SELLWAND" -> player.getInventory().addItem(Items.tagged(Material.BLAZE_ROD,
                "<gold><bold>" + Text.format(amount) + "x Sellwand</bold>",
                List.of("<gray>Right-click a container to sell its contents."), "sellwand", Double.toString(amount)));
            case "BOX" -> {
                String[] parts = value.split(":", 2);
                if (parts.length == 2) giveSimpleBox(player, parts[0], parts[1], 1);
            }
            case "LOOTBOX" -> giveLootbox(player, value, 1);
            default -> {
                plugin.modules().economy().addCrystals(player.getUniqueId(), price);
                Text.send(player, "<red>That crystal-shop offer is misconfigured; your crystals were refunded.");
                return;
            }
        }
        Text.send(player, "<green>Crystal-shop purchase complete.");
    }

    private void giveSimpleBox(Player player, String type, String rarity, int amount) {
        ItemStack box = Items.tagged(Material.ENDER_CHEST,
            "<gradient:#8B5CF6:#22D3EE><bold>" + prettify(rarity) + " " + prettify(type) + " Box</bold></gradient>",
            List.of("<gray>Right-click to reveal your reward."), "simple_box", type + ":" + rarity);
        box.setAmount(Math.min(amount, box.getMaxStackSize()));
        player.getInventory().addItem(box);
    }

    private void giveLootbox(Player player, String id, int amount) {
        String display = plugin.configs().get("boxes").getString("lootboxes.definitions." + id + ".display", id);
        ItemStack box = Items.tagged(Material.SHULKER_BOX, display,
            List.of("<gray>Contains multiple configurable Vupe rewards.", "", "<yellow>Right-click to open."),
            "lootbox", id);
        box.setAmount(Math.min(amount, box.getMaxStackSize()));
        player.getInventory().addItem(box);
    }

    private void openSimpleBox(Player player, String encoded) {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2) return;
        String path = "boxes.types." + parts[0] + "." + parts[1];
        if (!plugin.configs().get("boxes").contains(path)) {
            Text.send(player, "<red>This box reward is no longer configured.");
            return;
        }
        long min = plugin.configs().get("boxes").getLong(path + ".min", 1);
        long max = Math.max(min, plugin.configs().get("boxes").getLong(path + ".max", min));
        long amount = java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max + 1);
        if (parts[0].equals("money")) {
            plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            Text.send(player, "<gray>Your box contained <green>$" + Text.format(amount) + "<gray>.");
        } else {
            plugin.modules().economy().addCrystals(player.getUniqueId(), amount);
            Text.send(player, "<gray>Your box contained <#8B5CF6>" + amount + " Crystals<gray>.");
        }
    }

    private void openLootbox(Player player, String id) {
        String path = "lootboxes.definitions." + id;
        if (!plugin.configs().get("boxes").contains(path)) {
            Text.send(player, "<red>That lootbox is no longer configured.");
            return;
        }
        int rolls = Math.max(1, plugin.configs().get("boxes").getInt(path + ".rolls", 3));
        List<Map<?, ?>> rewards = plugin.configs().get("boxes").getMapList(path + ".rewards");
        if (rewards.isEmpty()) return;
        for (int i = 0; i < rolls; i++) {
            Map<?, ?> reward = chooseWeighted(rewards);
            if (reward == null) continue;
            grantLooseReward(player, reward);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.25f);
    }

    private Map<?, ?> chooseWeighted(List<Map<?, ?>> rewards) {
        double total = rewards.stream().mapToDouble(r -> mapNumber(r, "weight", 1)).sum();
        if (total <= 0) return null;
        double roll = Math.random() * total, current = 0;
        for (Map<?, ?> reward : rewards) {
            current += Math.max(0, mapNumber(reward, "weight", 1));
            if (roll <= current) return reward;
        }
        return rewards.getLast();
    }

    private void grantLooseReward(Player player, Map<?, ?> reward) {
        String type = mapString(reward, "type", "MONEY").toUpperCase(Locale.ROOT);
        double amount = mapNumber(reward, "amount", 1);
        if (reward.containsKey("min") || reward.containsKey("max")) {
            long min = Math.round(mapNumber(reward, "min", amount));
            long max = Math.max(min, Math.round(mapNumber(reward, "max", min)));
            amount = java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max + 1);
        }
        String value = mapString(reward, "value", "");
        switch (type) {
            case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            case "CRYSTALS" -> plugin.modules().economy().addCrystals(player.getUniqueId(), Math.round(amount));
            case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Math.round(amount));
            case "CRATE_KEY" -> plugin.modules().crates().addKeys(player.getUniqueId(), value, Math.max(1, (int) Math.round(amount)));
            case "GENERATOR" -> plugin.modules().generators().give(player, value, Math.max(1, (int) Math.round(amount)));
            case "GEN_SLOTS" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.generatorSlots += Math.max(1, (int) Math.round(amount));
                plugin.data().markDirty(player.getUniqueId());
            }
            case "SELL_MULTIPLIER" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.sellMultiplierBonus += amount;
                plugin.data().markDirty(player.getUniqueId());
            }
            case "SELL_BOOSTER" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.sellBoosterMultiplier = Math.max(1, amount);
                long minutes = Math.max(1, Math.round(mapNumber(reward, "duration-minutes", 30)));
                data.sellBoosterUntil = System.currentTimeMillis() + minutes * 60_000L;
                plugin.data().markDirty(player.getUniqueId());
            }
        }
    }

    private static String mapString(Map<?, ?> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static double mapNumber(Map<?, ?> map, String key, double fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ex) { return fallback; }
    }

    private static String prettify(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.replace('_', ' ').split(" ")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private void redeemVoucher(Player player, ItemStack item, String encoded) {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2) return;
        String type = parts[0];
        String value = parts[1];
        try {
            switch (type) {
                case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), Double.parseDouble(value));
                case "CRYSTALS" -> plugin.modules().economy().addCrystals(player.getUniqueId(), Long.parseLong(value));
                case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Long.parseLong(value));
                case "GEN_SLOTS" -> {
                    PlayerData data = plugin.data().player(player.getUniqueId());
                    data.generatorSlots += Integer.parseInt(value);
                    plugin.data().markDirty(player.getUniqueId());
                }
                case "SELL_MULTIPLIER" -> {
                    PlayerData data = plugin.data().player(player.getUniqueId());
                    data.sellMultiplierBonus += Double.parseDouble(value);
                    plugin.data().markDirty(player.getUniqueId());
                }
                case "RANK" -> plugin.modules().progression().setDonorRank(player, value);
                case "KIT" -> plugin.modules().progression().giveKit(player, value);
                default -> { Text.send(player, "<red>Unknown voucher type."); return; }
            }
            consumeOne(item);
            Text.send(player, "<green>Voucher redeemed.");
        } catch (NumberFormatException ex) {
            Text.send(player, "<red>This voucher is malformed.");
        }
    }

    private void consumeOne(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }
}

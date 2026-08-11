package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class AutoSellChestModule extends VupeModule {
    private final Map<UUID, String> sessions = new HashMap<>();
    private BukkitTask sellTask;

    public AutoSellChestModule(VupeCore plugin) {
        super(plugin, "autosell-chests");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("autosellchest", this::command);
        rebuildHolograms();
        sellTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    protected void onDisable() {
        if (sellTask != null) sellTask.cancel();
        sellTask = null;
        sessions.clear();
    }

    public void give(Player player, int amount) {
        ItemStack item = item();
        int left = Math.max(1, amount);
        while (left > 0) {
            ItemStack stack = item.clone();
            int each = Math.min(left, stack.getMaxStackSize());
            stack.setAmount(each);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            left -= each;
        }
        plugin.effects().success(player);
    }

    private ItemStack item() {
        String path = "autosell-chests.base-item.";
        Material material = Material.matchMaterial(plugin.configs().get("autosellchests").getString(path + "material", "CHEST"));
        if (material == null) material = Material.CHEST;
        return Items.tagged(material,
            plugin.configs().get("autosellchests").getString(path + "name", "<#22D3EE><bold>AUTOSELL CHEST</bold>"),
            plugin.configs().get("autosellchests").getStringList(path + "lore"),
            "autosell_chest", "true");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!Items.hasTag(event.getItemInHand(), "autosell_chest", "true")) return;
        Player player = event.getPlayer();

        if (plugin.modules().plots().enabled() && !plugin.modules().plots().canBuild(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            Text.send(player, "<red>You can only place an AutoSell Chest where PlotSquared lets you build.");
            plugin.effects().error(player);
            return;
        }

        long owned = plugin.data().server().autosellChests.values().stream()
            .filter(r -> player.getUniqueId().toString().equals(r.owner)).count();
        int max = plugin.configs().get("autosellchests").getInt("autosell-chests.maximum-per-player", 25);
        if (owned >= max && !player.hasPermission("vupe.admin")) {
            event.setCancelled(true);
            Text.send(player, "<red>You reached your AutoSell Chest limit (<white>" + max + "<red>).");
            plugin.effects().error(player);
            return;
        }

        ServerData.AutoSellChestRecord record = new ServerData.AutoSellChestRecord();
        record.id = UUID.randomUUID().toString().substring(0, 8);
        record.owner = player.getUniqueId().toString();
        record.location = Locations.serialize(event.getBlockPlaced().getLocation());
        record.tier = 1;
        record.lastSellAt = System.currentTimeMillis();
        record.hologram = plugin.configs().get("autosellchests").getBoolean("autosell-chests.holograms", true);

        plugin.data().server().autosellChests.put(Locations.blockKey(event.getBlockPlaced().getLocation()), record);
        spawnHologram(record);
        plugin.data().markServerDirty();
        plugin.effects().success(player);
        player.sendActionBar(Text.component("<#34D399>AutoSell Chest placed <dark_gray>• <gray>Right-click to manage"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ServerData.AutoSellChestRecord record = plugin.data().server().autosellChests.get(Locations.blockKey(event.getBlock().getLocation()));
        if (record == null) return;
        event.setCancelled(true);
        Text.send(event.getPlayer(), "<yellow>Use the <white>Pick Up <yellow>button in the AutoSell Chest GUI so contents and earnings stay safe.");
        plugin.effects().error(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        String key = Locations.blockKey(event.getClickedBlock().getLocation());
        ServerData.AutoSellChestRecord record = plugin.data().server().autosellChests.get(key);
        if (record == null) return;
        event.setCancelled(true);

        if (!canManage(event.getPlayer(), record)) {
            Text.send(event.getPlayer(), "<red>You do not own this AutoSell Chest.");
            plugin.effects().error(event.getPlayer());
            return;
        }
        open(event.getPlayer(), key);
    }

    private void open(Player player, String key) {
        ServerData.AutoSellChestRecord record = plugin.data().server().autosellChests.get(key);
        if (record == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, Text.component(
            plugin.configs().get("autosellchests").getString("autosell-chests.gui.title",
                "<gradient:#22D3EE:#8B5CF6><bold>AUTOSELL CHEST</bold></gradient>")));

        ItemStack filler = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        int tier = record.tier;
        long interval = interval(tier);
        double tierMulti = tierMultiplier(tier);
        long next = Math.max(0, interval * 1000L - (System.currentTimeMillis() - record.lastSellAt));

        inv.setItem(10, Items.tagged(Material.HOPPER,
            "<#22D3EE><bold>LIVE STATISTICS</bold>",
            List.of(
                "<gray>Tier: <white>" + tier,
                "<gray>Cycle: <white>" + interval + "s",
                "<gray>Tier multiplier: <green>" + Text.format(tierMulti) + "x",
                "<gray>Items sold: <white>" + record.itemsSold,
                "<gray>Next cycle: <white>" + dev.vupe.core.util.TimeUtil.pretty(next),
                "",
                "<gray>Owner multiplier is applied too."
            ), "asc_action", "noop"));

        inv.setItem(13, Items.tagged(Material.EMERALD,
            "<green><bold>COLLECT EARNINGS</bold>",
            List.of("<gray>Stored: <green>$" + Text.format(record.earnings), "", "<yellow>Click to collect."),
            "asc_action", "collect"));

        inv.setItem(16, Items.tagged(Material.CHEST,
            "<#67E8F9><bold>OPEN STORAGE</bold>",
            List.of("<gray>View/add items to the real chest inventory.", "", "<yellow>Click to open."),
            "asc_action", "contents"));

        int maxTier = maxTier();
        double cost = upgradeCost(tier);
        inv.setItem(29, Items.tagged(tier >= maxTier ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE,
            tier >= maxTier ? "<#F472B6><bold>MAX TIER</bold>" : "<#A78BFA><bold>UPGRADE TO TIER " + (tier + 1) + "</bold>",
            tier >= maxTier ? List.of("<gray>This AutoSell Chest is fully upgraded.")
                : List.of(
                    "<gray>Current cycle: <white>" + interval + "s",
                    "<gray>Next cycle: <white>" + interval(tier + 1) + "s",
                    "<gray>Current tier multi: <white>" + Text.format(tierMulti) + "x",
                    "<gray>Next tier multi: <green>" + Text.format(tierMultiplier(tier + 1)) + "x",
                    "",
                    "<gray>Cost: <green>$" + Text.format(cost),
                    "<yellow>Click to upgrade."
                ), "asc_action", "upgrade"));

        inv.setItem(31, Items.tagged(record.hologram ? Material.LIME_DYE : Material.GRAY_DYE,
            record.hologram ? "<green><bold>HOLOGRAM: ON</bold>" : "<gray><bold>HOLOGRAM: OFF</bold>",
            List.of("<gray>Shows tier, earnings and status above the chest.", "", "<yellow>Click to toggle."),
            "asc_action", "hologram"));

        inv.setItem(33, Items.tagged(Material.BARRIER,
            "<red><bold>PICK UP CHEST</bold>",
            List.of("<gray>Requires storage to be empty.", "<gray>Unclaimed earnings are collected first.", "", "<red>Click to pick up."),
            "asc_action", "pickup"));

        inv.setItem(49, Items.tagged(Material.ARROW, "<gray>Close", List.of(), "asc_action", "close"));

        sessions.put(player.getUniqueId(), key);
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String key = sessions.get(player.getUniqueId());
        if (key == null) return;
        event.setCancelled(true);

        String action = Items.tag(event.getCurrentItem(), "asc_action");
        if (action == null || action.equals("noop")) return;
        ServerData.AutoSellChestRecord record = plugin.data().server().autosellChests.get(key);
        if (record == null) {
            player.closeInventory();
            return;
        }
        plugin.effects().click(player);

        switch (action) {
            case "collect" -> {
                collect(player, record);
                open(player, key);
            }
            case "contents" -> {
                Location loc = Locations.deserialize(record.location);
                if (loc != null && loc.getBlock().getState() instanceof Container container) {
                    sessions.remove(player.getUniqueId());
                    player.openInventory(container.getInventory());
                    plugin.effects().open(player);
                }
            }
            case "upgrade" -> {
                upgrade(player, record);
                open(player, key);
            }
            case "hologram" -> {
                record.hologram = !record.hologram;
                if (record.hologram) spawnHologram(record); else removeHologram(record);
                plugin.data().markServerDirty();
                open(player, key);
            }
            case "pickup" -> pickup(player, key, record);
            case "close" -> player.closeInventory();
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiDrag(InventoryDragEvent event) {
        if (sessions.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ServerData.AutoSellChestRecord> entry :
            new ArrayList<>(plugin.data().server().autosellChests.entrySet())) {
            ServerData.AutoSellChestRecord record = entry.getValue();
            long intervalMs = interval(record.tier) * 1000L;
            if (now - record.lastSellAt < intervalMs) continue;

            Location loc = Locations.deserialize(record.location);
            if (loc == null || loc.getWorld() == null || !loc.getChunk().isLoaded()) continue;
            if (!(loc.getBlock().getState() instanceof Container container)) continue;

            double base = 0;
            long sold = 0;
            Inventory inventory = container.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                double each = plugin.modules().shop().sellValue(item);
                if (each <= 0) continue;
                base += each * item.getAmount();
                sold += item.getAmount();
                inventory.setItem(slot, null);
            }

            record.lastSellAt = now;
            if (base > 0) {
                UUID owner;
                try { owner = UUID.fromString(record.owner); } catch (IllegalArgumentException ex) { continue; }
                double finalValue = base * plugin.modules().shop().effectiveSellMultiplier(owner) * tierMultiplier(record.tier);
                record.earnings += finalValue;
                record.itemsSold += sold;
                Player online = Bukkit.getPlayer(owner);
                if (online != null) {
                    online.sendActionBar(Text.component("<#34D399>AutoSell <dark_gray>• <green>+$"
                        + Text.format(finalValue) + " <dark_gray>• <gray>" + sold + " items"));
                    plugin.modules().events().progress(online, "sell", finalValue);
                }
                updateHologram(record);
            }
            plugin.data().markServerDirty();
        }
    }

    private void collect(Player player, ServerData.AutoSellChestRecord record) {
        if (record.earnings <= 0) {
            Text.send(player, "<gray>There are no stored earnings yet.");
            plugin.effects().error(player);
            return;
        }
        double amount = record.earnings;
        record.earnings = 0;
        plugin.modules().economy().addMoney(player.getUniqueId(), amount);
        plugin.data().markServerDirty();
        Text.send(player, "<green>Collected <white>$" + Text.format(amount) + "<green>.");
        plugin.effects().purchase(player);
        updateHologram(record);
    }

    private void upgrade(Player player, ServerData.AutoSellChestRecord record) {
        if (record.tier >= maxTier()) {
            Text.send(player, "<#F472B6>This chest is already max tier.");
            return;
        }
        double cost = upgradeCost(record.tier);
        if (cost <= 0 || !plugin.modules().economy().takeMoney(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You need <green>$" + Text.format(cost) + "<red>.");
            plugin.effects().error(player);
            return;
        }
        record.tier++;
        plugin.data().markServerDirty();
        plugin.effects().title(player, "<#A78BFA><bold>AUTOSELL UPGRADED</bold>",
            "<gray>Tier " + record.tier + " • " + interval(record.tier) + "s cycles");
        plugin.effects().celebrate(player);
        updateHologram(record);
    }

    private void pickup(Player player, String key, ServerData.AutoSellChestRecord record) {
        Location loc = Locations.deserialize(record.location);
        if (loc == null || !(loc.getBlock().getState() instanceof Container container)) return;
        if (!inventoryEmpty(container.getInventory())) {
            Text.send(player, "<red>Empty the chest storage before picking it up.");
            plugin.effects().error(player);
            return;
        }
        collect(player, record);
        removeHologram(record);
        loc.getBlock().setType(Material.AIR, false);
        plugin.data().server().autosellChests.remove(key);
        plugin.data().markServerDirty();
        give(player, 1);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        Text.send(player, "<green>AutoSell Chest picked up safely.");
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("vupe.admin") || args.length < 2) {
                Text.send(sender, "<red>Usage: /autosellchest give <player> [amount]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Text.send(sender, "<red>Player must be online.");
                return true;
            }
            int amount = 1;
            if (args.length >= 3) {
                try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
            }
            give(target, amount);
            Text.send(sender, "<green>Gave AutoSell Chest ×" + amount + " to <white>" + target.getName() + "<green>.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        long count = plugin.data().server().autosellChests.values().stream()
            .filter(r -> player.getUniqueId().toString().equals(r.owner)).count();
        double earnings = plugin.data().server().autosellChests.values().stream()
            .filter(r -> player.getUniqueId().toString().equals(r.owner)).mapToDouble(r -> r.earnings).sum();
        Text.raw(player, "<gradient:#22D3EE:#8B5CF6><bold>VUPE AUTOSELL NETWORK</bold></gradient>");
        Text.raw(player, "<gray>Chests: <white>" + count + " <dark_gray>• <gray>Unclaimed: <green>$" + Text.format(earnings));
        Text.raw(player, "<gray>Place an AutoSell Chest and right-click it for the full management GUI.");
        return true;
    }

    private boolean inventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) return false;
        }
        return true;
    }

    private boolean canManage(Player player, ServerData.AutoSellChestRecord record) {
        return player.hasPermission("vupe.admin") || player.getUniqueId().toString().equals(record.owner);
    }

    private long interval(int tier) {
        return Math.max(1L, plugin.configs().get("autosellchests").getLong("autosell-chests.tiers." + tier + ".interval-seconds", 5));
    }

    private double tierMultiplier(int tier) {
        return Math.max(0.01, plugin.configs().get("autosellchests").getDouble("autosell-chests.tiers." + tier + ".multiplier", 1));
    }

    private double upgradeCost(int tier) {
        return Math.max(0, plugin.configs().get("autosellchests").getDouble("autosell-chests.tiers." + tier + ".upgrade-cost", 0));
    }

    private int maxTier() {
        var section = plugin.configs().get("autosellchests").getConfigurationSection("autosell-chests.tiers");
        if (section == null) return 1;
        return section.getKeys(false).stream().mapToInt(id -> {
            try { return Integer.parseInt(id); } catch (NumberFormatException ex) { return 1; }
        }).max().orElse(1);
    }

    private void rebuildHolograms() {
        NamespacedKey key = new NamespacedKey(plugin, "asc_id");
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String id = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (id != null) display.remove();
            }
        }
        plugin.data().server().autosellChests.values().stream().filter(r -> r.hologram).forEach(this::spawnHologram);
    }

    private void spawnHologram(ServerData.AutoSellChestRecord record) {
        if (!record.hologram) return;
        removeHologram(record);
        Location loc = Locations.deserialize(record.location);
        if (loc == null) return;
        double height = plugin.configs().get("autosellchests").getDouble("autosell-chests.hologram-height", 1.6);
        TextDisplay display = loc.getWorld().spawn(loc.clone().add(0.5, height, 0.5), TextDisplay.class);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.getPersistentDataContainer().set(new NamespacedKey(plugin, "asc_id"), PersistentDataType.STRING, record.id);
        record.hologramUuid = display.getUniqueId().toString();
        updateHologram(record);
    }

    private void updateHologram(ServerData.AutoSellChestRecord record) {
        if (!record.hologram || record.hologramUuid == null || record.hologramUuid.isBlank()) return;
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(record.hologramUuid));
            if (entity instanceof TextDisplay display) {
                display.text(Text.component(
                    "<gradient:#22D3EE:#8B5CF6><bold>AUTOSELL CHEST</bold></gradient>\n" +
                    "<gray>Tier <white>" + record.tier + " <dark_gray>• <green>$" + Text.format(record.earnings) +
                    "\n<dark_gray>Right-click to manage"
                ));
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private void removeHologram(ServerData.AutoSellChestRecord record) {
        if (record.hologramUuid == null || record.hologramUuid.isBlank()) return;
        try {
            Entity entity = Bukkit.getEntity(UUID.fromString(record.hologramUuid));
            if (entity != null) entity.remove();
        } catch (IllegalArgumentException ignored) {}
        record.hologramUuid = "";
    }
}

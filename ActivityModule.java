package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.InventoryCodec;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class ActivityModule extends VupeModule {
    public ActivityModule(VupeCore plugin) {
        super(plugin, "activities");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("lake", this::lakeCommand);
        plugin.commands().register("cooler", this::coolerCommand);
        plugin.commands().register("rod", this::rodCommand);
        plugin.commands().register("fishtravel", this::fishTravelCommand);
        plugin.commands().register("mine", this::mineCommand);
        plugin.commands().register("drill", this::drillCommand);
        plugin.commands().register("mining", this::miningCommand);
        plugin.commands().register("farming", this::farmingCommand);
    }

    private boolean lakeCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.fishing", true)) {
            Text.send(sender, "<red>Fishing is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        Location loc = Locations.deserialize(plugin.data().server().locations.get("fishing"));
        if (loc == null) {
            Text.send(player, "<red>The fishing point is not configured.");
            return true;
        }
        player.teleportAsync(loc);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.fishing", true)) return;
        Player player = event.getPlayer();

        if (event.getState() == PlayerFishEvent.State.BITE) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.4f);
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item itemEntity)) return;

        PlayerData data = plugin.data().player(player.getUniqueId());
        int bait = enchant(data.fishingEnchants, "bait");
        int catches = 1;
        if (rollPercent(bait * fishingChance("bait"))) {
            catches += java.util.concurrent.ThreadLocalRandom.current().nextInt(5, 11);
        }

        itemEntity.remove();
        for (int catchNo = 0; catchNo < catches; catchNo++) {
            Map<?, ?> selected = weightedFish();
            if (selected == null) continue;

            Material material = Material.matchMaterial(String.valueOf(selected.get("material")));
            if (material == null) continue;
            double money = number(selected.get("money"), 0);
            int xp = (int) Math.round(number(selected.get("xp"), 1));

            int lure = enchant(data.fishingEnchants, "lure");
            if (rollPercent(lure * fishingChance("lure"))) money *= 1.5;

            int processor = enchant(data.fishingEnchants, "processor");
            if (rollPercent(processor * fishingChance("processor"))) money *= java.util.concurrent.ThreadLocalRandom.current().nextDouble(1.1, 2.5);

            ItemStack fish = Items.tagged(
                material,
                "<#22D3EE>" + prettify(material.name()),
                List.of("<gray>Fishing value: <green>$" + Text.format(money)),
                "fish_value",
                Double.toString(money)
            );

            int capacity = Math.max(1, data.coolerSlots);
            if (data.cooler.size() < capacity) {
                data.cooler.add(InventoryCodec.encodeItem(fish));
            } else {
                player.getInventory().addItem(fish);
            }

            data.fishingXp += xp;
            plugin.modules().progression().addXp(player, xp);

            if (rollPercent(enchant(data.fishingEnchants, "generosity") * fishingChance("generosity"))) {
                long reward = java.util.concurrent.ThreadLocalRandom.current().nextLong(100, 501);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    plugin.modules().economy().addCrystals(online.getUniqueId(), reward);
                    Text.send(online, "<#22D3EE>" + player.getName() + "<gray>'s Generosity gave everyone <#8B5CF6>" + reward + " Crystals<gray>!");
                }
            }
            if (rollPercent(enchant(data.fishingEnchants, "intelligence") * fishingChance("intelligence"))) {
                plugin.modules().progression().addXp(player, java.util.concurrent.ThreadLocalRandom.current().nextInt(5, 16));
            }
            if (rollPercent(enchant(data.fishingEnchants, "treasurefinder") * fishingChance("treasurefinder"))) {
                String type = Math.random() < 0.72 ? "crystals" : "money";
                String[] rarities = {"common","common","common","uncommon","uncommon","rare","epic","legendary"};
                giveActivityBox(player, type, rarities[new Random().nextInt(rarities.length)]);
            }
            if (rollPercent(enchant(data.fishingEnchants, "income") * fishingChance("income"))) {
                plugin.modules().economy().addMoney(player.getUniqueId(), java.util.concurrent.ThreadLocalRandom.current().nextLong(2500, 25001));
            }
            if (rollPercent(enchant(data.fishingEnchants, "librarian") * fishingChance("librarian"))) {
                plugin.modules().progression().addXp(player, Math.max(10, data.level * 100L));
            }
            if (rollPercent(enchant(data.fishingEnchants, "cratefinder") * fishingChance("cratefinder"))) {
                plugin.modules().crates().addKeys(player.getUniqueId(), "vote", 1);
                Text.send(player, "<gray>Crate Finder discovered a <white>Vote Key<gray>.");
            }
        }

        int needed = Math.max(10, data.fishingLevel * 25);
        while (data.fishingXp >= needed) {
            data.fishingXp -= needed;
            data.fishingLevel++;
            needed = Math.max(10, data.fishingLevel * 25);
            Text.send(player, "<green>Fishing level increased to <white>" + data.fishingLevel + "<green>.");
        }

        if (enchant(data.fishingEnchants, "autosell") > 0
            && System.currentTimeMillis() >= data.cooldowns.getOrDefault("rod-autosell", 0L)) {
            data.cooldowns.put("rod-autosell", System.currentTimeMillis() + 60_000L);
            sellCooler(player, data);
        }

        plugin.data().markDirty(player.getUniqueId());
    }

    private Map<?, ?> weightedFish() {
        var section = plugin.configs().get("fishing").getConfigurationSection("fishing.catches");
        if (section == null) return null;
        List<Map<String, Object>> rows = new ArrayList<>();
        double total = 0;
        for (String key : section.getKeys(false)) {
            double weight = section.getDouble(key + ".weight", 1);
            if (weight <= 0) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("material", key);
            row.put("weight", weight);
            row.put("money", section.getDouble(key + ".money", 0));
            row.put("xp", section.getInt(key + ".xp", 1));
            rows.add(row);
            total += weight;
        }
        if (rows.isEmpty()) return null;
        double roll = Math.random() * total;
        double current = 0;
        for (Map<String, Object> row : rows) {
            current += number(row.get("weight"), 1);
            if (roll <= current) return row;
        }
        return rows.getLast();
    }

    private boolean rodCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.fishing", true)) {
            Text.send(sender, "<red>Fishing is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length == 0) {
            Text.raw(player, "<gradient:#22D3EE:#8B5CF6><bold>VUPE ROD ENCHANTS</bold></gradient>");
            var section = plugin.configs().get("fishing").getConfigurationSection("fishing.enchants");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    int level = enchant(data.fishingEnchants, id);
                    int max = section.getInt(id + ".max", 1);
                    Text.raw(player, " <dark_gray>• <white>" + prettify(id) + ": <#22D3EE>" + level + "<dark_gray>/<white>" + max
                        + " <gray>— " + section.getString(id + ".description", ""));
                }
            }
            Text.raw(player, "<gray>Use <white>/rod upgrade <enchant><gray>.");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!player.hasPermission("vupe.admin")) { Text.send(player, "<red>No permission."); return true; }
            player.getInventory().addItem(fishingRod(player));
            return true;
        }

        if (args[0].equalsIgnoreCase("upgrade") && args.length >= 2) {
            upgradeEnchant(player, "fishing", data.fishingEnchants, args[1].toLowerCase(Locale.ROOT));
            updateFishingRod(player);
            return true;
        }

        Text.send(player, "<gray>/rod [upgrade <enchant>]");
        return true;
    }

    private boolean drillCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.mining", true)) {
            Text.send(sender, "<red>Mining is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length == 0) {
            Text.raw(player, "<gradient:#22D3EE:#8B5CF6><bold>VUPE DRILL ENCHANTS</bold></gradient>");
            var section = plugin.configs().get("mining").getConfigurationSection("mining.enchants");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    int level = enchant(data.miningEnchants, id);
                    int max = section.getInt(id + ".max", 1);
                    Text.raw(player, " <dark_gray>• <white>" + prettify(id) + ": <#22D3EE>" + level + "<dark_gray>/<white>" + max
                        + " <gray>— " + section.getString(id + ".description", ""));
                }
            }
            Text.raw(player, "<gray>Use <white>/drill upgrade <enchant><gray>.");
            return true;
        }

        if (args[0].equalsIgnoreCase("upgrade") && args.length >= 2) {
            upgradeEnchant(player, "mining", data.miningEnchants, args[1].toLowerCase(Locale.ROOT));
            return true;
        }

        Text.send(player, "<gray>/drill [upgrade <enchant>]");
        return true;
    }

    private void upgradeEnchant(Player player, String tree, Map<String, Integer> levels, String id) {
        String path = tree + ".enchants." + id;
        var cfg = plugin.configs().get(tree.equals("fishing") ? "fishing" : "mining");
        if (!cfg.contains(path)) {
            Text.send(player, "<red>Unknown enchant.");
            return;
        }
        int current = levels.getOrDefault(id, 0);
        int max = cfg.getInt(path + ".max", 1);
        if (current >= max) {
            Text.send(player, "<#F472B6>That enchant is max level.");
            return;
        }
        double base = cfg.getDouble(path + ".base-cost", 10);
        double growth = cfg.getDouble(path + ".growth", 1.05);
        long cost = Math.max(1, Math.round(base * Math.pow(growth, current)));
        if (!plugin.modules().economy().takeCrystals(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You need <#8B5CF6>" + cost + " Crystals<red>.");
            return;
        }
        levels.put(id, current + 1);
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Upgraded <white>" + prettify(id) + " <green>to level <white>" + (current + 1) + "<green>.");
    }

    private ItemStack fishingRod(Player player) {
        ItemStack rod = Items.tagged(Material.FISHING_ROD,
            plugin.configs().get("fishing").getString("fishing.rod.name", "<#22D3EE><bold>Vupe Rod</bold>"),
            List.of("<gray>Fishing progression tool.", "<gray>Use <white>/rod <gray>to upgrade enchants."),
            "vupe_tool", "rod");
        int skill = enchant(plugin.data().player(player.getUniqueId()).fishingEnchants, "skill");
        if (skill > 0) {
            Enchantment lure = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lure"));
            if (lure != null) rod.addUnsafeEnchantment(lure, Math.min(5, skill));
        }
        return rod;
    }

    private void updateFishingRod(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (Items.hasTag(stack, "vupe_tool", "rod")) {
                player.getInventory().setItem(i, fishingRod(player));
            }
        }
    }

    private boolean fishTravelCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!plugin.configs().modules().getBoolean("modules.fishing", true)) {
            Text.send(player, "<red>Fishing is disabled.");
            return true;
        }

        if (args.length > 0) {
            String id = args[0].toLowerCase(Locale.ROOT);
            if (!id.equals("ship") && !id.equals("beach")) {
                Text.send(player, "<red>Usage: /fishtravel <ship|beach>");
                return true;
            }
            Location location = Locations.deserialize(plugin.data().server().locations.get("fishtravel:" + id));
            if (location == null) {
                Text.send(player, "<red>The " + id + " fishing travel point is not configured.");
                return true;
            }
            player.teleportAsync(location);
            return true;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<#67E8F9><bold>FISHING TRAVEL</bold>"));
        inv.setItem(11, Items.tagged(Material.OAK_BOAT, "<#22D3EE><bold>Ship</bold>",
            List.of("<gray>Travel to the fishing ship."), "fishtravel", "ship"));
        inv.setItem(15, Items.tagged(Material.SAND, "<#FBBF24><bold>Beach</bold>",
            List.of("<gray>Travel to the fishing beach."), "fishtravel", "beach"));
        player.openInventory(inv);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishingTravelClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Text.component("<#67E8F9><bold>FISHING TRAVEL</bold>"))) return;
        event.setCancelled(true);
        String destination = Items.tag(event.getCurrentItem(), "fishtravel");
        if (destination != null) {
            player.closeInventory();
            fishTravelCommand(player, "fishtravel", new String[]{destination});
        }
    }

    private boolean coolerCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.fishing", true)) {
            Text.send(sender, "<red>Fishing is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            double value = 0;
            for (String encoded : data.cooler) {
                try {
                    ItemStack item = InventoryCodec.decodeItem(encoded);
                    String raw = Items.tag(item, "fish_value");
                    if (raw != null) value += Double.parseDouble(raw) * item.getAmount();
                } catch (Exception ignored) {}
            }
            if (value <= 0) {
                Text.send(player, "<red>Your cooler is empty.");
                return true;
            }
            sellCooler(player, data);
            plugin.data().markDirty(player.getUniqueId());
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("rod")) {
            if (player.getInventory().all(Material.FISHING_ROD).values().stream().anyMatch(i -> Items.hasTag(i, "vupe_tool", "rod"))) {
                Text.send(player, "<red>You already have a Vupe Rod.");
                return true;
            }
            player.getInventory().addItem(fishingRod(player));
            Text.send(player, "<green>Received your Vupe Rod.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("upgrade")) {
            int max = plugin.configs().get("fishing").getInt("fishing.cooler.max-slots", 54);
            if (data.coolerSlots >= max) {
                Text.send(player, "<#F472B6>Your cooler is max size.");
                return true;
            }
            double base = plugin.configs().get("fishing").getDouble("fishing.cooler.upgrade-cost-base", 25000);
            double mult = plugin.configs().get("fishing").getDouble("fishing.cooler.upgrade-cost-multiplier", 2.0);
            int step = Math.max(0, (data.coolerSlots - plugin.configs().get("fishing").getInt("fishing.cooler.base-slots", 9)) / 9);
            double cost = base * Math.pow(mult, step);
            if (!plugin.modules().economy().takeMoney(player.getUniqueId(), cost)) {
                Text.send(player, "<red>You need <green>$" + Text.format(cost) + "<red>.");
                return true;
            }
            data.coolerSlots = Math.min(max, data.coolerSlots + 9);
            plugin.data().markDirty(player.getUniqueId());
            Text.send(player, "<green>Cooler upgraded to <white>" + data.coolerSlots + " slots<green>.");
            return true;
        }

        Text.raw(player, "<gradient:#22D3EE:#8B5CF6><bold>VUPE COOLER</bold></gradient>");
        Text.raw(player, "<gray>Fish: <white>" + data.cooler.size() + "<dark_gray>/<white>" + data.coolerSlots);
        Text.raw(player, "<gray>Fishing level: <white>" + data.fishingLevel + " <dark_gray>• <gray>XP: <white>" + data.fishingXp);
        Text.raw(player, "<gray>Use <white>/cooler sell <gray>or <white>/cooler upgrade<gray>.");
        return true;
    }

    private void sellCooler(Player player, PlayerData data) {
        double value = 0;
        for (String encoded : data.cooler) {
            try {
                ItemStack item = InventoryCodec.decodeItem(encoded);
                String raw = Items.tag(item, "fish_value");
                if (raw != null) value += Double.parseDouble(raw) * item.getAmount();
            } catch (Exception ignored) {}
        }
        if (value <= 0) return;
        double finalValue = value * plugin.modules().shop().effectiveSellMultiplier(player);
        data.cooler.clear();
        plugin.modules().economy().addMoney(player.getUniqueId(), finalValue);
        plugin.modules().events().progress(player, "sell", finalValue);
        Text.send(player, "<gray>Rod Autosell sold your cooler for <green>$" + Text.format(finalValue) + "<gray>.");
    }

    private int enchant(Map<String, Integer> levels, String id) {
        return Math.max(0, levels.getOrDefault(id.toLowerCase(Locale.ROOT), 0));
    }

    private double fishingChance(String id) {
        return plugin.configs().get("fishing").getDouble("fishing.enchants." + id + ".chance-per-level", 0);
    }

    private double miningChance(String id) {
        return plugin.configs().get("mining").getDouble("mining.enchants." + id + ".chance-per-level", 0);
    }

    private boolean rollPercent(double chance) {
        if (chance <= 0) return false;
        if (chance >= 100) return true;
        return Math.random() * 100.0 < chance;
    }

    private void giveActivityBox(Player player, String type, String rarity) {
        ItemStack box = Items.tagged(Material.ENDER_CHEST,
            "<gradient:#8B5CF6:#22D3EE><bold>" + prettify(rarity) + " " + prettify(type) + " Box</bold></gradient>",
            List.of("<gray>Right-click to reveal your reward."),
            "simple_box", type + ":" + rarity);
        player.getInventory().addItem(box);
    }

    private boolean mineCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.mining", true)) {
            Text.send(sender, "<red>Mining is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        Location loc = Locations.deserialize(plugin.data().server().locations.get("mine"));
        if (loc == null) {
            Text.send(player, "<red>The mine is not configured.");
            return true;
        }
        player.teleportAsync(loc);
        Text.send(player, "<gray>Use <white>/mining <gray>to buy a drill or manage your backpack.");
        return true;
    }

    private boolean miningCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.mining", true)) {
            Text.send(sender, "<red>Mining is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length > 0 && args[0].equalsIgnoreCase("buydrill")) {
            double price = plugin.configs().get("mining").getDouble("mining.drill.price", 15000);
            if (!plugin.modules().economy().takeMoney(player.getUniqueId(), price)) {
                Text.send(player, "<red>You need <green>$" + Text.format(price) + "<red>.");
                return true;
            }
            player.getInventory().addItem(drill());
            Text.send(player, "<green>Purchased the Vupe Drill.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            double value = data.miningValue;
            if (value <= 0) {
                Text.send(player, "<red>Your backpack is empty.");
                return true;
            }
            double finalValue = value * plugin.modules().shop().effectiveSellMultiplier(player);
            data.miningBlocks = 0;
            data.miningValue = 0;
            plugin.modules().economy().addMoney(player.getUniqueId(), finalValue);
            plugin.modules().events().progress(player, "sell", finalValue);
            plugin.data().markDirty(player.getUniqueId());
            Text.send(player, "<gray>Sold backpack for <green>$" + Text.format(finalValue) + "<gray>.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("upgrade")) {
            int max = plugin.configs().get("mining").getInt("mining.backpack.max-level", 20);
            if (data.backpackLevel >= max) {
                Text.send(player, "<#F472B6>Backpack is max level.");
                return true;
            }
            double cost = plugin.configs().get("mining").getDouble("mining.backpack.upgrade-base-cost", 100000) * data.backpackLevel;
            if (!plugin.modules().economy().takeMoney(player.getUniqueId(), cost)) {
                Text.send(player, "<red>You need <green>$" + Text.format(cost) + "<red>.");
                return true;
            }
            data.backpackLevel++;
            plugin.data().markDirty(player.getUniqueId());
            Text.send(player, "<green>Backpack level is now <white>" + data.backpackLevel + "<green>.");
            return true;
        }

        Text.raw(player, "<gradient:#22D3EE:#8B5CF6><bold>VUPE MINING</bold></gradient>");
        Text.raw(player, "<gray>Backpack: <white>" + data.miningBlocks + "<dark_gray>/<white>" + backpackCapacity(data));
        Text.raw(player, "<gray>Stored value: <green>$" + Text.format(data.miningValue));
        Text.raw(player, "<gray>Commands: <white>/mining buydrill<gray>, <white>/mining sell<gray>, <white>/mining upgrade");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMineBreak(BlockBreakEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.mining", true)) return;
        String world = plugin.configs().get("worlds").getString("worlds.mine.world", "mine");
        if (!event.getBlock().getWorld().getName().equalsIgnoreCase(world)) return;

        Player player = event.getPlayer();
        if (!Items.hasTag(player.getInventory().getItemInMainHand(), "vupe_tool", "drill")) {
            event.setCancelled(true);
            player.sendActionBar(Text.component("<red>Use a Vupe Drill. <gray>/mining buydrill"));
            return;
        }

        double value = plugin.configs().get("mining").getDouble("mining.blocks." + event.getBlock().getType().name(), 0);
        if (value <= 0) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        int capacity = backpackCapacity(data);
        if (data.miningBlocks >= capacity) {
            event.setCancelled(true);
            player.sendActionBar(Text.component("<red>Backpack full. <gray>/mining sell"));
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        Material old = event.getBlock().getType();
        Location loc = event.getBlock().getLocation();
        event.getBlock().setType(Material.BEDROCK, false);

        double appliedValue = value;
        int transfuse = enchant(data.miningEnchants, "transfuse");
        if (rollPercent(transfuse * miningChance("transfuse"))) {
            appliedValue *= 1.75;
            player.sendActionBar(Text.component("<#22D3EE>Transfuse <gray>boosted this block's value."));
        }

        int prosperity = enchant(data.miningEnchants, "prosperity");
        if (rollPercent(prosperity * miningChance("prosperity"))) appliedValue *= 2.0;

        data.miningBlocks++;
        data.miningValue += appliedValue;
        plugin.data().markDirty(player.getUniqueId());
        plugin.modules().events().progress(player, "mine", 1);
        plugin.modules().progression().addXp(player, 1);

        if (rollPercent(enchant(data.miningEnchants, "intelligence") * miningChance("intelligence"))) {
            plugin.modules().progression().addXp(player, java.util.concurrent.ThreadLocalRandom.current().nextInt(3, 12));
        }
        if (rollPercent(enchant(data.miningEnchants, "income") * miningChance("income"))) {
            plugin.modules().economy().addMoney(player.getUniqueId(), java.util.concurrent.ThreadLocalRandom.current().nextLong(1000, 15001));
        }
        if (rollPercent(enchant(data.miningEnchants, "treasurefinder") * miningChance("treasurefinder"))) {
            giveActivityBox(player, Math.random() < 0.6 ? "crystals" : "money", "uncommon");
        }
        if (rollPercent(enchant(data.miningEnchants, "generosity") * miningChance("generosity"))) {
            long amount = java.util.concurrent.ThreadLocalRandom.current().nextLong(25, 101);
            for (Player online : Bukkit.getOnlinePlayers()) plugin.modules().economy().addCrystals(online.getUniqueId(), amount);
        }
        if (rollPercent(enchant(data.miningEnchants, "crystalextractor") * miningChance("crystalextractor"))) {
            plugin.modules().economy().addCrystals(player.getUniqueId(), java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 6));
        }
        if (rollPercent(enchant(data.miningEnchants, "librarian") * miningChance("librarian"))) {
            plugin.modules().progression().addXp(player, Math.max(10, data.level * 100L));
        }
        int momentum = enchant(data.miningEnchants, "momentum");
        if (momentum > 0 && Math.random() < 0.02 * momentum) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 60, momentum - 1));
        }
        int vitals = enchant(data.miningEnchants, "vitals");
        if (vitals > 0 && Math.random() < 0.01) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 60, 0));
        }
        int superbreaker = enchant(data.miningEnchants, "superbreaker");
        if (superbreaker > 0 && Math.random() < 0.02 * superbreaker) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HASTE, 80, superbreaker - 1));
        }

        int fracture = enchant(data.miningEnchants, "fracture");
        if (rollPercent(fracture * miningChance("fracture"))) {
            fractureNearby(player, loc, data);
        }

        if (enchant(data.miningEnchants, "autosell") > 0 && data.miningBlocks >= backpackCapacity(data)) {
            double finalValue = data.miningValue * plugin.modules().shop().effectiveSellMultiplier(player);
            data.miningBlocks = 0;
            data.miningValue = 0;
            plugin.modules().economy().addMoney(player.getUniqueId(), finalValue);
            plugin.modules().events().progress(player, "sell", finalValue);
            Text.send(player, "<gray>Drill Autosell sold your backpack for <green>$" + Text.format(finalValue) + "<gray>.");
        }

        int crystalRoll = plugin.configs().get("mining").getInt("mining.crystal-chance-per-thousand", 10);
        if (ThreadLocalRandom.current().nextInt(1000) < crystalRoll) {
            long amount = 1;
            if (data.crystalBoosterUntil > System.currentTimeMillis()) {
                amount = Math.max(1, Math.round(data.crystalBoosterMultiplier));
            }
            plugin.modules().economy().addCrystals(player.getUniqueId(), amount);
        }

        long delay = Math.max(20L, plugin.configs().get("mining").getLong("mining.block-respawn-seconds", 4) * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (loc.getWorld() != null && loc.getBlock().getType() == Material.BEDROCK) {
                loc.getBlock().setType(old, false);
            }
        }, delay);
    }

    private void fractureNearby(Player player, Location center, PlayerData data) {
        if (data.miningBlocks >= backpackCapacity(data)) return;
        org.bukkit.block.BlockFace[] faces = {
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
        };
        int processed = 0;
        for (org.bukkit.block.BlockFace face : faces) {
            if (processed >= 3 || data.miningBlocks >= backpackCapacity(data)) break;
            Block block = center.getBlock().getRelative(face);
            if (block.getType() == Material.BEDROCK) continue;
            double value = plugin.configs().get("mining").getDouble("mining.blocks." + block.getType().name(), 0);
            if (value <= 0) continue;

            Material old = block.getType();
            Location loc = block.getLocation();
            block.setType(Material.BEDROCK, false);
            data.miningBlocks++;
            data.miningValue += value;
            processed++;

            long delay = Math.max(20L, plugin.configs().get("mining").getLong("mining.block-respawn-seconds", 4) * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getBlock().getType() == Material.BEDROCK) loc.getBlock().setType(old, false);
            }, delay);
        }
        if (processed > 0) {
            plugin.modules().events().progress(player, "mine", processed);
            plugin.modules().progression().addXp(player, processed);
            player.sendActionBar(Text.component("<#F472B6>Fracture <gray>mined " + processed + " nearby block(s)."));
        }
    }

    private ItemStack drill() {
        Material material = Material.matchMaterial(plugin.configs().get("mining").getString("mining.drill.material", "DIAMOND_PICKAXE"));
        if (material == null) material = Material.DIAMOND_PICKAXE;
        ItemStack stack = Items.tagged(
            material,
            plugin.configs().get("mining").getString("mining.drill.name", "<#22D3EE><bold>Vupe Drill</bold>"),
            List.of("<gray>Mined blocks go directly into your backpack."),
            "vupe_tool",
            "drill"
        );
        var meta = stack.getItemMeta();
        if (meta instanceof Damageable damageable) damageable.setUnbreakable(true);
        int model = plugin.configs().get("mining").getInt("mining.drill.custom-model-data", 91001);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "model"),
            org.bukkit.persistence.PersistentDataType.INTEGER,
            model
        );
        meta.setCustomModelData(model);
        stack.setItemMeta(meta);
        Enchantment efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        if (efficiency != null) stack.addUnsafeEnchantment(efficiency, 5);
        return stack;
    }

    private int backpackCapacity(PlayerData data) {
        int base = plugin.configs().get("mining").getInt("mining.backpack.base-capacity", 250);
        int per = plugin.configs().get("mining").getInt("mining.backpack.capacity-per-level", 250);
        return base + Math.max(0, data.backpackLevel - 1) * per;
    }

    private boolean farmingCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.farming", true)) {
            Text.send(sender, "<red>Farming is disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return playerOnly(sender);

        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length == 0) {
            Text.raw(player, "<gradient:#8B5CF6:#22D3EE><bold>VUPE FARMING</bold></gradient>");
            Text.raw(player, "<gray>Tool: " + (data.ownsFarmerHoe ? "<green>owned" : "<red>not owned"));
            var enchants = plugin.configs().get("farming").getConfigurationSection("farming.enchants");
            if (enchants != null) {
                for (String id : enchants.getKeys(false)) {
                    int level = data.farmingEnchants.getOrDefault(id, 0);
                    int max = enchants.getInt(id + ".max", 1);
                    Text.raw(player, " <dark_gray>• " + enchants.getString(id + ".display", prettify(id))
                        + ": <white>" + level + "<dark_gray>/<white>" + max
                        + " <gray>— " + enchants.getString(id + ".description", ""));
                }
            }
            Text.raw(player, "<gray>Commands: <white>/farming buy<gray>, <white>/farming tool<gray>, <white>/farming upgrade <enchant><gray>, <white>/farming warp");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "warp" -> {
                String farmWorld = plugin.configs().get("worlds").getString("worlds.farm.world", "farm");
                World world = Bukkit.getWorld(farmWorld);
                if (world == null) {
                    Text.send(player, "<red>Farm world is not loaded.");
                    return true;
                }
                Location loc = Locations.deserialize(plugin.data().server().locations.get("farm"));
                if (loc == null) loc = world.getSpawnLocation();
                player.teleportAsync(loc);
            }
            case "buy" -> {
                if (data.ownsFarmerHoe) {
                    Text.send(player, "<red>You already own the Vupe Harvester. Use <white>/farming tool<red> if you lost it.");
                    return true;
                }
                double price = plugin.configs().get("farming").getDouble("farming.hoe.price", 1000);
                if (!plugin.modules().economy().takeMoney(player.getUniqueId(), price)) {
                    Text.send(player, "<red>You need <green>$" + Text.format(price) + "<red>.");
                    return true;
                }
                data.ownsFarmerHoe = true;
                plugin.data().markDirty(player.getUniqueId());
                player.getInventory().addItem(farmingHoe(player));
                Text.send(player, "<green>You purchased the Vupe Harvester.");
            }
            case "tool" -> {
                if (!data.ownsFarmerHoe) {
                    Text.send(player, "<red>You do not own the Vupe Harvester yet. Use <white>/farming buy<red>.");
                    return true;
                }
                if (player.getInventory().all(Material.DIAMOND_HOE).values().stream()
                    .anyMatch(item -> Items.hasTag(item, "vupe_tool", "farming"))) {
                    Text.send(player, "<red>You already have your Vupe Harvester.");
                    return true;
                }
                player.getInventory().addItem(farmingHoe(player));
                Text.send(player, "<green>Your Vupe Harvester was restored.");
            }
            case "upgrade" -> {
                if (args.length < 2) {
                    Text.send(player, "<red>Usage: /farming upgrade <enchant>");
                    return true;
                }
                upgradeFarmingEnchant(player, args[1].toLowerCase(Locale.ROOT));
            }
            default -> Text.send(player, "<gray>/farming <buy|tool|upgrade|warp>");
        }
        return true;
    }

    private ItemStack farmingHoe(Player player) {
        Material material = Material.matchMaterial(plugin.configs().get("farming").getString("farming.hoe.material", "DIAMOND_HOE"));
        if (material == null) material = Material.DIAMOND_HOE;

        PlayerData data = plugin.data().player(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Harvest mature crops in the Vupe farm.");
        lore.add("");
        var enchants = plugin.configs().get("farming").getConfigurationSection("farming.enchants");
        if (enchants != null) {
            for (String id : enchants.getKeys(false)) {
                lore.add(enchants.getString(id + ".display", prettify(id))
                    + " <dark_gray>• <white>" + data.farmingEnchants.getOrDefault(id, 0)
                    + "<dark_gray>/<white>" + enchants.getInt(id + ".max", 1));
            }
        }
        lore.add("");
        lore.add("<gray>Upgrade with <white>/farming upgrade <enchant><gray>.");

        ItemStack hoe = Items.tagged(
            material,
            plugin.configs().get("farming").getString("farming.hoe.name", "<#8B5CF6><bold>Vupe Harvester</bold>"),
            lore,
            "vupe_tool",
            "farming"
        );
        var meta = hoe.getItemMeta();
        if (meta instanceof Damageable damageable && plugin.configs().get("farming").getBoolean("farming.hoe.unbreakable", true)) {
            damageable.setUnbreakable(true);
        }
        hoe.setItemMeta(meta);
        return hoe;
    }

    private void refreshFarmingHoe(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (Items.hasTag(stack, "vupe_tool", "farming")) {
                player.getInventory().setItem(slot, farmingHoe(player));
            }
        }
    }

    private void upgradeFarmingEnchant(Player player, String id) {
        var cfg = plugin.configs().get("farming");
        String path = "farming.enchants." + id;
        if (!cfg.contains(path)) {
            Text.send(player, "<red>Unknown farming enchant.");
            return;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        int current = data.farmingEnchants.getOrDefault(id, 0);
        int max = cfg.getInt(path + ".max", 1);
        if (current >= max) {
            Text.send(player, "<#F472B6>That farming enchant is max level.");
            return;
        }

        long cost;
        if (cfg.contains(path + ".fixed-cost")) {
            cost = Math.max(1, cfg.getLong(path + ".fixed-cost"));
        } else {
            double base = cfg.getDouble(path + ".base-cost", 200);
            double growth = cfg.getDouble(path + ".growth", 1.05);
            cost = Math.max(1L, Math.round(base * Math.pow(growth, current)));
        }

        if (!plugin.modules().economy().takeCrystals(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You need <#8B5CF6>" + cost + " Crystals<red>.");
            return;
        }

        data.farmingEnchants.put(id, current + 1);
        plugin.data().markDirty(player.getUniqueId());
        refreshFarmingHoe(player);
        Text.send(player, "<green>Upgraded " + cfg.getString(path + ".display", prettify(id))
            + " <green>to <white>" + (current + 1) + "<green>.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.farming", true)) return;
        String world = plugin.configs().get("farming").getString("farming.world", "farm");
        if (!event.getBlock().getWorld().getName().equalsIgnoreCase(world)) return;
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) return;

        Player player = event.getPlayer();
        if (plugin.configs().get("farming").getBoolean("farming.require-vupe-hoe", true)
            && !Items.hasTag(player.getInventory().getItemInMainHand(), "vupe_tool", "farming")) {
            event.setCancelled(true);
            Text.send(player, plugin.configs().get("farming").getString("farming.messages.need-hoe",
                "<red>You need the Vupe Harvester."));
            return;
        }

        if (plugin.configs().get("farming").getBoolean("farming.protect-unripe-crops", true)
            && ageable.getAge() < ageable.getMaximumAge()) {
            event.setCancelled(true);
            player.sendActionBar(Text.component(plugin.configs().get("farming").getString(
                "farming.messages.unripe", "<red>That crop is not fully grown."
            )));
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        PlayerData data = plugin.data().player(player.getUniqueId());
        Material crop = event.getBlock().getType();
        double baseMoney = plugin.configs().get("farming").getDouble("farming.crops." + crop.name() + ".money", 0);
        int baseXp = plugin.configs().get("farming").getInt("farming.crops." + crop.name() + ".xp", 1);
        if (baseMoney <= 0) return;

        int fertility = data.farmingEnchants.getOrDefault("fertility", 0);
        double moneyPer = plugin.configs().get("farming").getDouble("farming.enchants.fertility.money-per-level", 0.10);
        double money = baseMoney * (1.0 + fertility * moneyPer);

        int harvesting = data.farmingEnchants.getOrDefault("harvesting", 0);
        int harvestedExtra = 0;
        if (harvesting > 0 && rollPercent(plugin.configs().get("farming").getDouble(
            "farming.enchants.harvesting.chance-percent", 5.0))) {
            int radius = Math.max(1, plugin.configs().get("farming").getInt("farming.enchants.harvesting.radius", 3));
            harvestedExtra = harvestNearbyCrops(player, event.getBlock().getLocation(), crop, radius);
            money += baseMoney * harvestedExtra * (1.0 + fertility * moneyPer);
        }

        int knowledge = data.farmingEnchants.getOrDefault("knowledge", 0);
        double xpPer = plugin.configs().get("farming").getDouble("farming.enchants.knowledge.xp-per-level", 0.05);
        long xp = Math.max(1, Math.round(baseXp * (1.0 + knowledge * xpPer)));

        plugin.modules().economy().addMoney(player.getUniqueId(), money);
        plugin.modules().progression().addXp(player, xp);

        int crystal = data.farmingEnchants.getOrDefault("crystalfinder", 0);
        if (rollPercent(crystal * plugin.configs().get("farming").getDouble(
            "farming.enchants.crystalfinder.chance-per-level-percent", 0.10))) {
            long min = plugin.configs().get("farming").getLong("farming.enchants.crystalfinder.min-crystals", 16);
            long max = Math.max(min, plugin.configs().get("farming").getLong("farming.enchants.crystalfinder.max-crystals", 64));
            long amount = java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max + 1);
            plugin.modules().economy().addCrystals(player.getUniqueId(), amount);
            Text.send(player, "<gray>Crystal Finder found <#8B5CF6>" + amount + " Crystals<gray>.");
        }

        int crateFinder = data.farmingEnchants.getOrDefault("cratefinder", 0);
        if (rollPercent(crateFinder * plugin.configs().get("farming").getDouble(
            "farming.enchants.cratefinder.chance-per-level-percent", 0.002))) {
            String crate = plugin.configs().get("farming").getString("farming.enchants.cratefinder.reward-crate", "event");
            int keys = plugin.configs().get("farming").getInt("farming.enchants.cratefinder.reward-keys", 1);
            plugin.modules().crates().addKeys(player.getUniqueId(), crate, keys);
            Text.send(player, "<gray>Crate Finder discovered <white>" + keys + " " + crate + " key(s)<gray>.");
        }

        plugin.modules().events().progress(player, "farm", 1 + harvestedExtra);
        plugin.data().markDirty(player.getUniqueId());

        if (plugin.configs().get("farming").getBoolean("farming.instant-replant", true)) {
            replant(event.getBlock(), crop);
        }

        player.sendActionBar(Text.component("<green>+$" + Text.format(money)
            + " <dark_gray>• <#22D3EE>+" + xp + " XP"
            + (harvestedExtra > 0 ? " <dark_gray>• <#F472B6>Harvest +" + harvestedExtra : "")));
    }

    private int harvestNearbyCrops(Player player, Location center, Material crop, int radius) {
        int harvested = 0;
        World world = center.getWorld();
        if (world == null) return 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() != crop) continue;
                    if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) continue;

                    harvested++;
                    replant(block, crop);
                    if (harvested >= 24) return harvested; // hard safety cap per event
                }
            }
        }
        return harvested;
    }

    private void replant(Block block, Material crop) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            block.setType(crop, false);
            if (block.getBlockData() instanceof Ageable fresh) {
                fresh.setAge(0);
                block.setBlockData(fresh, false);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (!plugin.configs().get("farming").getBoolean("farming.keep-farmland-moist", true)) return;
        String world = plugin.configs().get("farming").getString("farming.world", "farm");
        if (event.getBlock().getWorld().getName().equalsIgnoreCase(world)) {
            event.setCancelled(true);
        }
    }

    private boolean playerOnly(CommandSender sender) {
        Text.send(sender, "<red>Player-only.");
        return true;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }

    private static String prettify(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}

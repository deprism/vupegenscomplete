package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class GeneratorModule extends VupeModule {
    public record GeneratorType(
        String id, Material block, Material drop, String display, String color,
        double sell, double upgrade, String next
    ) {}

    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private BukkitTask cycleTask;
    private int cursor = 0;

    public GeneratorModule(VupeCore plugin) {
        super(plugin, "generators");
    }

    @Override
    protected void onEnable() {
        loadTypes();
        plugin.commands().register("gens", this::command);
        plugin.commands().register("genlist", this::command);
        plugin.commands().register("givegen", this::giveGenCompatibility);

        long period = Math.max(20L, plugin.configs().main().getLong("server.generator-cycle-ticks", 40));
        cycleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, period, period);
    }

    @Override
    protected void onDisable() {
        if (cycleTask != null) cycleTask.cancel();
        cycleTask = null;
    }

    private void loadTypes() {
        types.clear();
        ConfigurationSection section = plugin.configs().get("generators").getConfigurationSection("generators.types");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "generators.types." + id;
            Material block = Material.matchMaterial(plugin.configs().get("generators").getString(path + ".block", "STONE"));
            Material drop = Material.matchMaterial(plugin.configs().get("generators").getString(path + ".drop", "COBBLESTONE"));
            if (block == null || drop == null) {
                plugin.getLogger().warning("Skipping generator " + id + ": invalid material.");
                continue;
            }
            types.put(id.toLowerCase(Locale.ROOT), new GeneratorType(
                id.toLowerCase(Locale.ROOT),
                block,
                drop,
                plugin.configs().get("generators").getString(path + ".display", id),
                plugin.configs().get("generators").getString(path + ".color", "#8B5CF6"),
                plugin.configs().get("generators").getDouble(path + ".sell", 1),
                plugin.configs().get("generators").getDouble(path + ".upgrade", 0),
                plugin.configs().get("generators").getString(path + ".next", "")
            ));
        }
    }

    public GeneratorType type(String id) {
        if (id == null) return null;
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GeneratorType> types() {
        return Collections.unmodifiableCollection(types.values());
    }

    public ItemStack item(String id, int amount) {
        GeneratorType type = type(id);
        if (type == null) return null;
        ItemStack stack = Items.tagged(
            type.block(),
            "<" + type.color() + "><bold>" + type.display() + " Core</bold>",
            List.of(
                "<gray>Sell: <green>$" + Text.format(type.sell()),
                type.upgrade() > 0 ? "<gray>Upgrade: <green>$" + Text.format(type.upgrade()) : "<#F472B6>MAX TIER",
                "",
                "<dark_gray>Place on your plot."
            ),
            "generator_type",
            type.id()
        );
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    public boolean give(Player player, String typeId, int amount) {
        GeneratorType type = type(typeId);
        if (type == null || amount <= 0) return false;
        int left = amount;
        while (left > 0) {
            int stackAmount = Math.min(left, type.block().getMaxStackSize());
            ItemStack stack = item(type.id(), stackAmount);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack value : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), value);
            }
            left -= stackAmount;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String breakTier = Items.tag(event.getItemInHand(), "break_generator_tier");
        if (breakTier != null && plugin.configs().modules().getBoolean("modules.breakable-generators", false)) {
            String path = "breakable-generators.tiers." + breakTier;
            Material expected = Material.matchMaterial(plugin.configs().get("mining").getString(path + ".material", ""));
            if (expected == null || event.getBlockPlaced().getType() != expected) {
                event.setCancelled(true);
                return;
            }
            if (plugin.configs().modules().getBoolean("modules.plots", true)
                && !plugin.modules().plots().canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
                event.setCancelled(true);
                return;
            }
            ServerData.BreakGeneratorRecord record = new ServerData.BreakGeneratorRecord();
            record.owner = event.getPlayer().getUniqueId().toString();
            record.tier = breakTier;
            record.location = Locations.serialize(event.getBlockPlaced().getLocation());
            plugin.data().server().breakGenerators.put(Locations.blockKey(event.getBlockPlaced().getLocation()), record);
            plugin.data().markServerDirty();
            return;
        }

        String typeId = Items.tag(event.getItemInHand(), "generator_type");
        if (typeId == null) return;
        GeneratorType type = type(typeId);
        if (type == null || event.getBlockPlaced().getType() != type.block()) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>That generator item is invalid.");
            return;
        }

        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        long owned = plugin.data().server().generators.values().stream()
            .filter(r -> event.getPlayer().getUniqueId().toString().equals(r.owner))
            .count();

        if (owned >= data.generatorSlots) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You have reached your generator-slot limit (<white>" + data.generatorSlots + "<red>).");
            return;
        }

        if (plugin.configs().modules().getBoolean("modules.plots", true)
            && !plugin.modules().plots().canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You can only place generators in an area you can build in.");
            return;
        }

        ServerData.GeneratorRecord record = new ServerData.GeneratorRecord();
        record.owner = event.getPlayer().getUniqueId().toString();
        record.type = type.id();
        record.location = Locations.serialize(event.getBlockPlaced().getLocation());
        record.placedAt = System.currentTimeMillis();
        plugin.data().server().generators.put(Locations.blockKey(event.getBlockPlaced().getLocation()), record);
        plugin.data().markServerDirty();

        plugin.modules().events().progress(event.getPlayer(), "generators", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String key = Locations.blockKey(event.getBlock().getLocation());

        ServerData.BreakGeneratorRecord breakRecord = plugin.data().server().breakGenerators.get(key);
        if (breakRecord != null && plugin.configs().modules().getBoolean("modules.breakable-generators", false)) {
            if (!breakRecord.owner.equals(event.getPlayer().getUniqueId().toString()) && !event.getPlayer().hasPermission("vupe.admin")) {
                event.setCancelled(true);
                Text.send(event.getPlayer(), "<red>You do not own this breakable generator.");
                return;
            }
            String path = "breakable-generators.tiers." + breakRecord.tier;
            double earnings = plugin.configs().get("mining").getDouble(path + ".earnings", 0);
            event.setCancelled(true);
            event.getBlock().setType(Material.BEDROCK, false);
            plugin.modules().economy().addMoney(event.getPlayer().getUniqueId(), earnings);
            event.getPlayer().sendActionBar(Text.component("<green>+$" + Text.format(earnings)));

            Location loc = event.getBlock().getLocation();
            Material restore = Material.matchMaterial(plugin.configs().get("mining").getString(path + ".material", "COAL_ORE"));
            long delay = Math.max(20L, plugin.configs().get("mining").getLong("breakable-generators.respawn-seconds", 5) * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getBlock().getType() == Material.BEDROCK && restore != null
                    && plugin.data().server().breakGenerators.containsKey(Locations.blockKey(loc))) {
                    loc.getBlock().setType(restore, false);
                }
            }, delay);
            return;
        }

        ServerData.GeneratorRecord record = plugin.data().server().generators.get(key);
        if (record == null) return;

        boolean owner = record.owner.equals(event.getPlayer().getUniqueId().toString());
        if (!owner && !event.getPlayer().hasPermission("vupe.admin")) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You do not own this generator.");
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        plugin.data().server().generators.remove(key);
        plugin.data().markServerDirty();
        give(event.getPlayer(), record.type, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        if (!plugin.configs().get("generators").getBoolean("generators.prevent-explosions", true)) return;
        event.blockList().removeIf(block -> plugin.data().server().generators.containsKey(Locations.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.configs().get("generators").getBoolean("generators.prevent-explosions", true)) return;
        event.blockList().removeIf(block -> plugin.data().server().generators.containsKey(Locations.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            && event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        String key = Locations.blockKey(block.getLocation());

        ServerData.BreakGeneratorRecord breakRecord = plugin.data().server().breakGenerators.get(key);
        if (breakRecord != null && plugin.configs().modules().getBoolean("modules.breakable-generators", false)) {
            event.setCancelled(true);
            if (!breakRecord.owner.equals(event.getPlayer().getUniqueId().toString()) && !event.getPlayer().hasPermission("vupe.admin")) {
                Text.send(event.getPlayer(), "<red>You do not own this breakable generator.");
                return;
            }
            String path = "breakable-generators.tiers." + breakRecord.tier;

            if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                Material material = Material.matchMaterial(plugin.configs().get("mining").getString(path + ".material", "COAL_ORE"));
                if (material != null) {
                    ItemStack item = Items.tagged(
                        material,
                        plugin.configs().get("mining").getString(path + ".display", "Break Core"),
                        List.of("<gray>Break for direct cash.", "<gray>Sneak-right-click to upgrade."),
                        "break_generator_tier", breakRecord.tier
                    );
                    event.getPlayer().getInventory().addItem(item);
                }
                plugin.data().server().breakGenerators.remove(key);
                block.setType(Material.AIR, false);
                plugin.data().markServerDirty();
                Text.send(event.getPlayer(), "<green>Breakable generator picked up.");
                return;
            }
            String next = plugin.configs().get("mining").getString(path + ".next", "");
            double cost = plugin.configs().get("mining").getDouble(path + ".upgrade", 0);
            if (next.isBlank() || cost <= 0) {
                Text.send(event.getPlayer(), "<#F472B6>This breakable generator is max tier.");
                return;
            }
            if (!plugin.modules().economy().takeMoney(event.getPlayer().getUniqueId(), cost)) {
                Text.send(event.getPlayer(), "<red>You need <green>$" + Text.format(cost) + "<red>.");
                return;
            }
            Material nextMaterial = Material.matchMaterial(plugin.configs().get("mining").getString("breakable-generators.tiers." + next + ".material", ""));
            if (nextMaterial == null) return;
            block.setType(nextMaterial, false);
            breakRecord.tier = next;
            plugin.data().markServerDirty();
            Text.send(event.getPlayer(), "<green>Breakable generator upgraded.");
            return;
        }

        ServerData.GeneratorRecord record = plugin.data().server().generators.get(key);
        if (record == null) return;

        event.setCancelled(true);
        if (!record.owner.equals(event.getPlayer().getUniqueId().toString())
            && !event.getPlayer().hasPermission("vupe.admin")) {
            Text.send(event.getPlayer(), "<red>You do not own this generator.");
            return;
        }

        GeneratorType current = type(record.type);
        if (current == null || current.next().isBlank() || current.upgrade() <= 0) {
            Text.send(event.getPlayer(), "<#F472B6>This generator is already max tier.");
            return;
        }
        GeneratorType next = type(current.next());
        if (next == null) {
            Text.send(event.getPlayer(), "<red>The configured next generator is missing.");
            return;
        }

        if (!plugin.modules().economy().takeMoney(event.getPlayer().getUniqueId(), current.upgrade())) {
            Text.send(event.getPlayer(), "<red>You need <green>$" + Text.format(current.upgrade()) + "<red>.");
            return;
        }

        block.setType(next.block(), false);
        record.type = next.id();
        plugin.data().markServerDirty();
        Text.send(event.getPlayer(), "<gray>Upgraded to <" + next.color() + ">" + next.display() + " Core<gray>.");
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
    }

    private void cycle() {
        if (plugin.data().server().generators.isEmpty()) return;
        List<Map.Entry<String, ServerData.GeneratorRecord>> list =
            new ArrayList<>(plugin.data().server().generators.entrySet());

        int maximum = Math.min(750, list.size());
        if (cursor >= list.size()) cursor = 0;

        for (int i = 0; i < maximum; i++) {
            if (cursor >= list.size()) cursor = 0;
            ServerData.GeneratorRecord record = list.get(cursor++).getValue();
            generate(record);
        }
    }

    private void generate(ServerData.GeneratorRecord record) {
        GeneratorType type = type(record.type);
        Location location = Locations.deserialize(record.location);
        if (type == null || location == null || location.getWorld() == null) return;
        if (!location.getChunk().isLoaded()) return;
        if (location.getBlock().getType() != type.block()) return;

        String mode = plugin.configs().get("generators").getString("generators.output-mode", "PHYSICAL").toUpperCase(Locale.ROOT);
        UUID owner;
        try { owner = UUID.fromString(record.owner); }
        catch (IllegalArgumentException ex) { return; }

        PlayerData ownerData = plugin.data().player(owner);
        Player onlineOwner = Bukkit.getPlayer(owner);
        if (plugin.configs().modules().getBoolean("modules.autosell", true)
            && ownerData.autosellEnabled
            && onlineOwner != null
            && (!plugin.configs().get("shops").getBoolean("autosell.require-player-not-afk", true) || !ownerData.afk)) {
            double value = type.sell() * plugin.modules().shop().effectiveSellMultiplier(onlineOwner);
            plugin.modules().economy().addMoney(owner, value);
            plugin.modules().events().progress(onlineOwner, "sell", value);
            return;
        }

        if (mode.equals("VIRTUAL")) {
            PlayerData data = plugin.data().player(owner);
            data.virtualGeneratorStorage.merge(type.id(), 1, Integer::sum);
            plugin.data().markDirty(owner);
            return;
        }

        Location dropLoc = location.clone().add(0.5, 1.15, 0.5);
        ItemStack drop = Items.tagged(
            type.drop(),
            "<" + type.color() + ">" + type.display() + " Fragment",
            List.of("<gray>Sell value: <green>$" + Text.format(type.sell())),
            "generator_drop_type",
            type.id()
        );

        double mergeRadius = plugin.configs().main().getDouble("server.item-merge-radius", 2.5);
        if (plugin.configs().get("generators").getBoolean("generators.merge-nearby-drops", true)) {
            for (var nearby : dropLoc.getWorld().getNearbyEntities(dropLoc, mergeRadius, mergeRadius, mergeRadius)) {
                if (!(nearby instanceof Item entity)) continue;
                String existing = Items.tag(entity.getItemStack(), "generator_drop_type");
                if (type.id().equals(existing)) {
                    ItemStack current = entity.getItemStack();
                    if (current.getAmount() < current.getMaxStackSize()) {
                        current.setAmount(current.getAmount() + 1);
                        entity.setItemStack(current);
                        return;
                    }
                }
            }
        }

        int cap = plugin.configs().main().getInt("server.maximum-generated-items-per-chunk", 70);
        int generated = 0;
        for (var entity : location.getChunk().getEntities()) {
            if (entity instanceof Item item && Items.tag(item.getItemStack(), "generator_drop_type") != null) {
                generated++;
                if (generated >= cap) {
                    PlayerData data = plugin.data().player(owner);
                    data.virtualGeneratorStorage.merge(type.id(), 1, Integer::sum);
                    plugin.data().markDirty(owner);
                    return;
                }
            }
        }

        Item entity = location.getWorld().dropItem(dropLoc, drop);
        entity.setPickupDelay(10);
        entity.setUnlimitedLifetime(false);
    }

    private boolean giveGenCompatibility(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /givegen <player> <type> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(sender, "<red>Player not found.");
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException ignored) {}
        }
        if (!give(target, args[1], amount)) {
            Text.send(sender, "<red>Unknown generator type.");
            return true;
        }
        Text.send(sender, "<green>Gave <white>" + amount + " " + args[1] + " <green>generator(s) to <white>" + target.getName() + "<green>.");
        return true;
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("breakgive")) {
            if (!sender.hasPermission("vupe.admin") || args.length < 3) {
                Text.send(sender, "<red>Usage: /gens breakgive <player> <tier> [amount]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            String tier = args[2];
            String path = "breakable-generators.tiers." + tier;
            Material material = Material.matchMaterial(plugin.configs().get("mining").getString(path + ".material", ""));
            if (target == null || material == null) {
                Text.send(sender, "<red>Invalid player or tier.");
                return true;
            }
            int amount = 1;
            if (args.length >= 4) try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
            ItemStack stack = Items.tagged(material,
                plugin.configs().get("mining").getString(path + ".display", "Break Core"),
                List.of("<gray>Break for direct cash.", "<gray>Sneak-right-click to upgrade."),
                "break_generator_tier", tier);
            stack.setAmount(Math.min(stack.getMaxStackSize(), amount));
            target.getInventory().addItem(stack);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("vupe.admin")) {
                Text.send(sender, "<red>No permission.");
                return true;
            }
            if (args.length < 3) {
                Text.send(sender, "<red>Usage: /gens give <player> <type> [amount]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Text.send(sender, "<red>Player not found.");
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Math.max(1, Integer.parseInt(args[3])); }
                catch (NumberFormatException ignored) {}
            }
            if (!give(target, args[2], amount)) {
                Text.send(sender, "<red>Unknown generator type.");
                return true;
            }
            Text.send(sender, "<green>Gave <white>" + amount + " " + args[2] + " <green>generator(s).");
            return true;
        }

        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Usage: /gens give <player> <type> [amount]");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        long placed = plugin.data().server().generators.values().stream()
            .filter(r -> player.getUniqueId().toString().equals(r.owner)).count();
        Text.raw(player, "<gradient:#8B5CF6:#22D3EE><bold>VUPE GENERATORS</bold></gradient>");
        Text.raw(player, "<gray>Placed: <white>" + placed + "<dark_gray>/<white>" + data.generatorSlots);
        Text.raw(player, "<gray>Sell multiplier: <white>" + Text.format(1 + data.sellMultiplierBonus) + "x");
        Text.raw(player, "<gray>Types: <white>" + String.join(", ", types.keySet()));
        Text.raw(player, "<gray>Sneak-right-click a placed core to upgrade it.");
        return true;
    }
}

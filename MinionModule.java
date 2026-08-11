package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class MinionModule extends VupeModule {
    private BukkitTask task;

    public MinionModule(VupeCore plugin) {
        super(plugin, "minions");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("minions", this::command);
        rebuildEntities();
        long seconds = Math.max(2, plugin.configs().get("minions").getLong("minions.cycle-seconds", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, seconds * 20L, seconds * 20L);
    }

    @Override
    protected void onDisable() {
        if (task != null) task.cancel();
        task = null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String type = Items.tag(event.getItemInHand(), "minion_type");
        if (type == null) return;
        if (!plugin.configs().get("minions").contains("minions.types." + type)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.configs().modules().getBoolean("modules.plots", true)
            && !plugin.modules().plots().canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You can only place a minion where you can build.");
            return;
        }

        long count = plugin.data().server().minions.values().stream()
            .filter(m -> event.getPlayer().getUniqueId().toString().equals(m.owner)).count();
        int max = plugin.configs().get("minions").getInt("minions.max-per-player", 20);
        if (count >= max) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You reached your minion limit.");
            return;
        }

        Location loc = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);
        event.getBlockPlaced().setType(Material.AIR, false);

        ServerData.MinionRecord record = new ServerData.MinionRecord();
        record.id = UUID.randomUUID().toString().substring(0, 8);
        record.owner = event.getPlayer().getUniqueId().toString();
        record.type = type;
        record.location = Locations.serialize(loc);
        record.createdAt = System.currentTimeMillis();
        record.stored = 0;
        spawnEntity(record);
        plugin.data().server().minions.put(record.id, record);
        plugin.data().markServerDirty();
        Text.send(event.getPlayer(), "<green>Placed " + display(type) + "<green> minion. ID <white>" + record.id + "<green>.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        String id = event.getRightClicked().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "minion_id"), PersistentDataType.STRING
        );
        if (id == null) return;
        event.setCancelled(true);
        ServerData.MinionRecord record = plugin.data().server().minions.get(id);
        if (record == null) return;
        if (!record.owner.equals(event.getPlayer().getUniqueId().toString())
            && !event.getPlayer().hasPermission("vupe.admin")) {
            Text.send(event.getPlayer(), "<red>You do not own this minion.");
            return;
        }

        if (event.getPlayer().isSneaking()) {
            pickup(event.getPlayer(), record);
        } else {
            collect(event.getPlayer(), record);
        }
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) return true;
            Text.raw(player, "<gradient:#8B5CF6:#22D3EE><bold>VUPE MINIONS</bold></gradient>");
            plugin.data().server().minions.values().stream()
                .filter(m -> player.getUniqueId().toString().equals(m.owner))
                .forEach(m -> Text.raw(player, " <dark_gray>• <white>" + m.id + " <gray>"
                    + display(m.type) + " <dark_gray>• <gray>stored: <white>" + m.stored));
            Text.raw(player, "<gray>Right-click a minion to collect; sneak-right-click to pick it up.");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("vupe.admin") || args.length < 3) {
                Text.send(sender, "<red>/minions give <player> <type> [amount]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            String type = args[2].toLowerCase(Locale.ROOT);
            if (target == null || !plugin.configs().get("minions").contains("minions.types." + type)) {
                Text.send(sender, "<red>Invalid player or minion type.");
                return true;
            }
            int amount = 1;
            if (args.length > 3) try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
            giveItem(target, type, amount);
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        if (args.length < 2) {
            Text.send(player, "<red>/minions <collect|pickup> <id>");
            return true;
        }
        ServerData.MinionRecord record = plugin.data().server().minions.get(args[1]);
        if (record == null || (!record.owner.equals(player.getUniqueId().toString()) && !player.hasPermission("vupe.admin"))) {
            Text.send(player, "<red>Unknown minion.");
            return true;
        }
        if (args[0].equalsIgnoreCase("collect")) collect(player, record);
        else if (args[0].equalsIgnoreCase("pickup")) pickup(player, record);
        return true;
    }

    private void cycle() {
        for (ServerData.MinionRecord record : plugin.data().server().minions.values()) {
            int amount = Math.max(1, plugin.configs().get("minions").getInt("minions.types." + record.type + ".amount-per-cycle", 1));
            record.stored = Math.min(1_000_000L, record.stored + amount);
        }
        if (!plugin.data().server().minions.isEmpty()) plugin.data().markServerDirty();
    }

    private void collect(Player player, ServerData.MinionRecord record) {
        if (record.stored <= 0) {
            Text.send(player, "<gray>This minion has nothing stored yet.");
            return;
        }
        Material material = Material.matchMaterial(plugin.configs().get("minions").getString("minions.types." + record.type + ".output", "COAL"));
        if (material == null) return;
        long left = record.stored;
        while (left > 0 && player.getInventory().firstEmpty() >= 0) {
            int amount = (int) Math.min(left, material.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(material, amount));
            left -= amount;
        }
        long collected = record.stored - left;
        record.stored = left;
        plugin.data().markServerDirty();
        Text.send(player, "<gray>Collected <white>" + collected + " " + material.name().toLowerCase(Locale.ROOT) + "<gray>.");
    }

    private void pickup(Player player, ServerData.MinionRecord record) {
        if (record.stored > 0) collect(player, record);
        Entity entity = entity(record);
        if (entity != null) entity.remove();
        giveItem(player, record.type, 1);
        plugin.data().server().minions.remove(record.id);
        plugin.data().markServerDirty();
        Text.send(player, "<green>Minion picked up.");
    }

    private void giveItem(Player player, String type, int amount) {
        Material material = Material.matchMaterial(plugin.configs().get("minions").getString("minions.types." + type + ".material", "COAL_BLOCK"));
        if (material == null) material = Material.COAL_BLOCK;
        ItemStack item = Items.tagged(material, display(type),
            List.of("<gray>Place on your plot to activate.", "<gray>Right-click to collect; sneak-right-click to pick up."),
            "minion_type", type);
        item.setAmount(Math.min(item.getMaxStackSize(), amount));
        player.getInventory().addItem(item);
    }

    private Entity spawnEntity(ServerData.MinionRecord record) {
        Location loc = Locations.deserialize(record.location);
        if (loc == null) return null;
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setVisible(true);
        stand.setSmall(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.customName(Text.component(display(record.type)));
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "minion_id"), PersistentDataType.STRING, record.id);
        Material helmet = Material.matchMaterial(plugin.configs().get("minions").getString("minions.types." + record.type + ".material", "COAL_BLOCK"));
        if (helmet != null) stand.getEquipment().setHelmet(new ItemStack(helmet));
        record.entityUuid = stand.getUniqueId().toString();
        return stand;
    }

    private Entity entity(ServerData.MinionRecord record) {
        try {
            if (record.entityUuid != null && !record.entityUuid.isBlank()) {
                Entity found = Bukkit.getEntity(UUID.fromString(record.entityUuid));
                if (found != null) return found;
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private void rebuildEntities() {
        // Clean orphaned Vupe minion entities first.
        NamespacedKey key = new NamespacedKey(plugin, "minion_id");
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                String id = stand.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (id != null && !plugin.data().server().minions.containsKey(id)) stand.remove();
            }
        }

        for (ServerData.MinionRecord record : plugin.data().server().minions.values()) {
            if (entity(record) == null) spawnEntity(record);
        }
    }

    private String display(String type) {
        return plugin.configs().get("minions").getString("minions.types." + type + ".display", prettify(type) + " Minion");
    }

    private static String prettify(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}

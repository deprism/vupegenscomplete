package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class NativeNpcModule extends VupeModule {
    private final Map<UUID, String> openMenus = new HashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> menuAnimations = new HashMap<>();

    public NativeNpcModule(VupeCore plugin) {
        super(plugin, "native-npcs");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("menu", this::menuCommand);
        plugin.commands().register("warps", this::warpsCommand);
        Bukkit.getScheduler().runTaskLater(plugin, this::indexExistingNpcs, 40L);
    }

    public void createNpc(Player creator, String role) {
        String path = "npcs.roles." + role;
        if (!plugin.configs().get("npcs").contains(path)) {
            Text.send(creator, "<red>Unknown NPC role. Configure it in npcs.yml first.");
            return;
        }

        removeNpc(role);

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(plugin.configs().get("npcs").getString("npcs.defaults.entity-type", "VILLAGER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            entityType = EntityType.VILLAGER;
        }

        Entity entity = creator.getWorld().spawnEntity(creator.getLocation(), entityType);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "npc_role"), PersistentDataType.STRING, role);
        entity.customName(Text.component(plugin.configs().get("npcs").getString(path + ".display", role)));
        entity.setCustomNameVisible(true);
        entity.setPersistent(plugin.configs().get("npcs").getBoolean("npcs.defaults.persistent", true));
        entity.setInvulnerable(plugin.configs().get("npcs").getBoolean("npcs.defaults.invulnerable", true));
        entity.setSilent(plugin.configs().get("npcs").getBoolean("npcs.defaults.silent", true));

        if (entity instanceof Mob mob) {
            mob.setAI(plugin.configs().get("npcs").getBoolean("npcs.defaults.ai", false));
            mob.setAware(false);
            mob.setRemoveWhenFarAway(false);
        }

        plugin.data().server().npcLocations.put(role, Locations.serialize(entity.getLocation()));
        plugin.data().markServerDirty();
        Text.send(creator, "<green>Created native Vupe NPC role <white>" + role + "<green>.");
    }

    private void removeNpc(String role) {
        NamespacedKey key = new NamespacedKey(plugin, "npc_role");
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String existing = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (role.equalsIgnoreCase(existing)) entity.remove();
            }
        }
    }

    private void indexExistingNpcs() {
        NamespacedKey key = new NamespacedKey(plugin, "npc_role");
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String role = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (role != null) {
                    plugin.data().server().npcLocations.put(role, Locations.serialize(entity.getLocation()));
                }
            }
        }
        plugin.data().markServerDirty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        String role = event.getRightClicked().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "npc_role"), PersistentDataType.STRING
        );
        if (role == null) return;
        event.setCancelled(true);

        String command = plugin.configs().get("npcs").getString("npcs.roles." + role + ".command", "");
        if (!command.isBlank()) event.getPlayer().performCommand(command);
    }

    private boolean menuCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        var cfg = plugin.configs().get("menus");
        int size = sanitizeInventorySize(cfg.getInt("main-menu.size", 45));
        Inventory inv = Bukkit.createInventory(null, size, Text.component(
            cfg.getString("main-menu.title", "<gradient:#8B5CF6:#22D3EE><bold>VUPE MENU</bold></gradient>")
        ));
        var items = cfg.getConfigurationSection("main-menu.items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                int slot = items.getInt(id + ".slot", -1);
                Material material = Material.matchMaterial(items.getString(id + ".material", "PAPER"));
                if (slot < 0 || slot >= size || material == null) continue;
                String command = items.getString(id + ".command", "");
                inv.setItem(slot, Items.tagged(
                    material,
                    items.getString(id + ".name", id),
                    items.getStringList(id + ".lore"),
                    "menu_command",
                    command
                ));
            }
        }
        openMenus.put(player.getUniqueId(), "menu");
        player.openInventory(inv);
        plugin.effects().open(player);
        animateMenu(player, inv);
        return true;
    }

    private boolean warpsCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length > 0) {
            teleportWarp(player, args[0]);
            return true;
        }
        var cfg = plugin.configs().get("menus");
        int size = sanitizeInventorySize(cfg.getInt("warps-menu.size", 27));
        Inventory inv = Bukkit.createInventory(null, size, Text.component(
            cfg.getString("warps-menu.title", "<#F472B6><bold>VUPE WARPS</bold>")
        ));
        var items = cfg.getConfigurationSection("warps-menu.items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                int slot = items.getInt(id + ".slot", -1);
                Material material = Material.matchMaterial(items.getString(id + ".material", "COMPASS"));
                if (slot < 0 || slot >= size || material == null) continue;
                inv.setItem(slot, Items.tagged(
                    material,
                    items.getString(id + ".name", id),
                    items.getStringList(id + ".lore"),
                    "warp",
                    items.getString(id + ".warp", id)
                ));
            }
        }
        openMenus.put(player.getUniqueId(), "warps");
        player.openInventory(inv);
        plugin.effects().open(player);
        animateMenu(player, inv);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        plugin.effects().click(player);
        String command = Items.tag(clicked, "menu_command");
        if (command != null) {
            player.closeInventory();
            player.performCommand(command);
            return;
        }

        String warp = Items.tag(clicked, "warp");
        if (warp != null) {
            player.closeInventory();
            teleportWarp(player, warp);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openMenus.remove(uuid);
        org.bukkit.scheduler.BukkitTask task = menuAnimations.remove(uuid);
        if (task != null) task.cancel();
    }

    private void teleportWarp(Player player, String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (plugin.modules().pvp().isCombatTagged(player) && List.of("spawn", "plot", "fishing", "mine", "farm", "crates").contains(id)) {
            Text.send(player, "<red>You cannot warp while combat-tagged.");
            return;
        }

        if (id.equals("plot") || id.equals("plots")) {
            plugin.modules().plots().claimOrTeleport(player);
            return;
        }

        Location location = Locations.deserialize(plugin.data().server().locations.get(id));
        if (location == null && id.equals("crates")) {
            location = Locations.deserialize(plugin.data().server().locations.get("crates"));
        }
        if (location == null) {
            Text.send(player, "<red>Warp <white>" + id + " <red>is not configured.");
            return;
        }
        player.teleportAsync(location);
    }

    private void animateMenu(Player player, Inventory inv) {
        org.bukkit.scheduler.BukkitTask previous = menuAnimations.remove(player.getUniqueId());
        if (previous != null) previous.cancel();

        final Material[] frames = {
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE
        };
        final int[] frame = {0};

        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                org.bukkit.scheduler.BukkitTask current = menuAnimations.remove(player.getUniqueId());
                if (current != null) current.cancel();
                return;
            }
            Material material = frames[(frame[0]++) % frames.length];
            int rows = inv.getSize() / 9;
            for (int slot = 0; slot < inv.getSize(); slot++) {
                int row = slot / 9;
                int col = slot % 9;
                if (row != 0 && row != rows - 1 && col != 0 && col != 8) continue;
                ItemStack current = inv.getItem(slot);
                if (current == null || current.getType().name().endsWith("STAINED_GLASS_PANE")) {
                    inv.setItem(slot, Items.item(material, " ", List.of()));
                }
            }
        }, 1L, 8L);
        menuAnimations.put(player.getUniqueId(), task);
    }

    private int sanitizeInventorySize(int requested) {
        int size = Math.max(9, Math.min(54, requested));
        int remainder = size % 9;
        return remainder == 0 ? size : size + (9 - remainder);
    }

    private ItemStack menuItem(Material material, String name, String command) {
        return Items.tagged(material, name, List.of("<yellow>Click to open."), "menu_command", command);
    }

    private ItemStack warpItem(Material material, String name, String warp) {
        return Items.tagged(material, name, List.of("<yellow>Click to teleport."), "warp", warp);
    }
}

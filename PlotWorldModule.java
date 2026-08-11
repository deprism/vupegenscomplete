package dev.vupe.core.module.impl;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class PlotWorldModule extends VupeModule {
    public PlotWorldModule(VupeCore plugin) {
        super(plugin, "plots");
    }

    @Override
    protected void onEnable() {
        // /plot and all PlotSquared subcommands intentionally belong to PlotSquared.
        plugin.commands().register("hopperlimit", this::hopperLimitCommand);
        plugin.commands().register("chestlimit", this::chestLimitCommand);
    }

    public boolean plotSquaredAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlotSquared");
    }

    public String plotWorldName() {
        return plugin.configs().get("worlds").getString("worlds.plots.world", "vupeplots");
    }

    /**
     * Used by Vupe custom-object modules before they create persistent records.
     * PlotSquared remains the authoritative protection system.
     */
    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("vupe.admin")) return true;
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(plotWorldName())) {
            return true;
        }
        if (!plotSquaredAvailable()) return false;

        try {
            com.plotsquared.core.location.Location psLocation = BukkitUtil.adapt(location);
            if (psLocation.getPlotArea() == null) return false;

            Plot plot = psLocation.getOwnedPlot();
            return plot != null && plot.isAdded(player.getUniqueId());
        } catch (Throwable error) {
            plugin.getLogger().warning("PlotSquared build check failed at " + Locations.blockKey(location) + ": " + error.getMessage());
            return false;
        }
    }

    /**
     * Vupe's /start and plot warp route here. Existing owners go home;
     * new players are moved into the PlotSquared world and /plot auto is run.
     */
    public void claimOrTeleport(Player player) {
        if (!plugin.configs().modules().getBoolean("modules.plots", true)) {
            Text.send(player, "<red>Plots are disabled.");
            return;
        }
        if (!plotSquaredAvailable()) {
            Text.send(player, "<red>PlotSquared is not loaded. Ask an administrator to install/configure it.");
            return;
        }

        String worldName = plotWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Text.send(player, "<red>The PlotSquared world <white>" + worldName + " <red>is not loaded.");
            Text.send(player, "<gray>An admin must create it with <white>/plot setup<gray>.");
            return;
        }

        try {
            var plotPlayer = BukkitUtil.adapt(player);
            if (plotPlayer.getPlotCount(worldName) > 0) {
                dispatchConfigured(player, "worlds.plots.home-command", "plot home");
                return;
            }
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not read PlotSquared plots for " + player.getName() + ": " + error.getMessage());
            Text.send(player, "<red>PlotSquared is still initializing. Try again in a moment.");
            return;
        }

        Runnable autoClaim = () -> dispatchConfigured(player, "worlds.plots.auto-claim-command", "plot auto");
        if (player.getWorld().getName().equalsIgnoreCase(worldName)) {
            autoClaim.run();
            return;
        }

        player.teleportAsync(world.getSpawnLocation().add(0.5, 1, 0.5)).thenAccept(success -> {
            if (success) Bukkit.getScheduler().runTask(plugin, autoClaim);
            else Bukkit.getScheduler().runTask(plugin, () -> Text.send(player, "<red>Could not enter the plot world."));
        });
    }

    private void dispatchConfigured(Player player, String path, String fallback) {
        String command = plugin.configs().get("worlds").getString(path, fallback);
        if (command == null || command.isBlank()) command = fallback;
        if (command.startsWith("/")) command = command.substring(1);
        player.performCommand(command);
    }

    /*
     * Container limits are still VupeCore features. The HIGH handler validates;
     * the MONITOR handler records only successful PlotSquared-approved changes.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerPlaceValidate(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (!isTrackedContainer(type)) return;
        if (!plugin.configs().modules().getBoolean("modules.container-limits", true)) return;

        if (!canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        boolean hopper = type == Material.HOPPER;
        Set<String> owned = hopper ? data.ownedHoppers : data.ownedChests;
        int limit = hopper ? data.hopperLimit : data.chestLimit;

        if (owned.size() >= limit && !event.getPlayer().hasPermission("vupe.admin")) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You reached your " + (hopper ? "hopper" : "chest")
                + " limit (<white>" + owned.size() + "<dark_gray>/<white>" + limit + "<red>).");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerPlaceTrack(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (!isTrackedContainer(type)) return;
        if (!plugin.configs().modules().getBoolean("modules.container-limits", true)) return;

        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        Set<String> owned = type == Material.HOPPER ? data.ownedHoppers : data.ownedChests;
        owned.add(Locations.blockKey(event.getBlockPlaced().getLocation()));
        plugin.data().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerBreakValidate(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!isTrackedContainer(type)) return;
        if (!plugin.configs().modules().getBoolean("modules.container-limits", true)) return;

        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        Set<String> owned = type == Material.HOPPER ? data.ownedHoppers : data.ownedChests;
        String key = Locations.blockKey(event.getBlock().getLocation());

        if (plugin.configs().get("limits").getBoolean("limits.ownership-protection", true)
            && !owned.contains(key) && !event.getPlayer().hasPermission("vupe.admin")) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>This container is not registered to you.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBreakTrack(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!isTrackedContainer(type)) return;
        if (!plugin.configs().modules().getBoolean("modules.container-limits", true)) return;

        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        Set<String> owned = type == Material.HOPPER ? data.ownedHoppers : data.ownedChests;
        owned.remove(Locations.blockKey(event.getBlock().getLocation()));
        plugin.data().markDirty(event.getPlayer().getUniqueId());
    }

    private boolean isTrackedContainer(Material type) {
        return type == Material.HOPPER || type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    private boolean hopperLimitCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        return limitCommand(player, true, args);
    }

    private boolean chestLimitCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        return limitCommand(player, false, args);
    }

    private boolean limitCommand(Player player, boolean hopper, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.container-limits", true)) {
            Text.send(player, "<red>Container limits are disabled.");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        int current = hopper ? data.hopperLimit : data.chestLimit;
        int count = hopper ? data.ownedHoppers.size() : data.ownedChests.size();
        String path = "limits.per-player." + (hopper ? "hoppers" : "chests");
        int max = plugin.configs().get("limits").getInt(path + ".max", hopper ? 100 : 250);

        if (args.length == 0) {
            Text.send(player, "<gray>" + (hopper ? "Hopper" : "Chest") + " limit: <white>" + count
                + "<dark_gray>/<white>" + current + " <gray>(max " + max + ")");
            Text.send(player, "<gray>Use <white>/" + (hopper ? "hopperlimit" : "chestlimit") + " upgrade<gray>.");
            if (!hopper) Text.send(player, "<gray>Optional: <white>/chestlimit compress<gray> compresses generator fragments.");
            return true;
        }

        if (args[0].equalsIgnoreCase("upgrade")) {
            if (current >= max) {
                Text.send(player, "<#F472B6>That limit is maxed.");
                return true;
            }
            double per = plugin.configs().get("limits").getDouble(path + ".upgrade-cost-per-current-slot", 10000);
            double cost = current * per;
            if (!plugin.modules().economy().takeMoney(player.getUniqueId(), cost)) {
                Text.send(player, "<red>You need <green>$" + Text.format(cost) + "<red>.");
                return true;
            }
            if (hopper) data.hopperLimit++;
            else data.chestLimit++;
            plugin.data().markDirty(player.getUniqueId());
            Text.send(player, "<green>Limit upgraded by 1.");
            return true;
        }

        if (!hopper && args[0].equalsIgnoreCase("compress")) {
            compressOwnedChests(player);
            return true;
        }

        Text.send(player, "<red>Unknown limit action.");
        return true;
    }

    private void compressOwnedChests(Player player) {
        if (!plugin.configs().get("limits").getBoolean("limits.generator-drop-compression.enabled", true)) {
            Text.send(player, "<red>Compression is disabled.");
            return;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        int minimum = Math.max(2, plugin.configs().get("limits").getInt("limits.generator-drop-compression.minimum-stack", 64));
        int compressed = 0;

        for (String encoded : new HashSet<>(data.ownedChests)) {
            Location location = locationFromBlockKey(encoded);
            if (location == null || !(location.getBlock().getState() instanceof org.bukkit.block.Container container)) continue;
            Inventory inventory = container.getInventory();

            Map<String, Integer> totals = new HashMap<>();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                String type = Items.tag(stack, "generator_drop_type");
                if (type == null) continue;
                totals.merge(type, stack.getAmount(), Integer::sum);
                inventory.setItem(slot, null);
            }

            for (Map.Entry<String, Integer> entry : totals.entrySet()) {
                int count = entry.getValue();
                int bundles = count / minimum;
                int remainder = count % minimum;
                GeneratorModule.GeneratorType type = plugin.modules().generators().type(entry.getKey());
                if (type == null) continue;

                if (bundles > 0) {
                    ItemStack compressedItem = Items.tagged(
                        type.drop(),
                        "<" + type.color() + "><bold>" + type.display() + " Fragment Bundle</bold>",
                        List.of("<gray>Contains <white>" + minimum + " fragments<gray>.", "<gray>Sell value scales automatically."),
                        "compressed_generator_drop",
                        type.id() + ":" + minimum
                    );
                    compressedItem.setAmount(Math.min(64, bundles));
                    inventory.addItem(compressedItem);
                    compressed += bundles * minimum;
                }

                if (remainder > 0) {
                    ItemStack normal = Items.tagged(
                        type.drop(),
                        "<" + type.color() + ">" + type.display() + " Fragment",
                        List.of("<gray>Sell value: <green>$" + Text.format(type.sell())),
                        "generator_drop_type",
                        type.id()
                    );
                    normal.setAmount(Math.min(remainder, normal.getMaxStackSize()));
                    inventory.addItem(normal);
                }
            }
        }

        Text.send(player, "<gray>Compressed <white>" + compressed + " <gray>generator fragments in your owned chests.");
    }

    private Location locationFromBlockKey(String key) {
        String[] split = key.split(":");
        if (split.length != 4) return null;
        World world = Bukkit.getWorld(split[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

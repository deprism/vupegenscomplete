package dev.vupe.core.setup;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SetupService {
    private final VupeCore plugin;

    public SetupService(VupeCore plugin) {
        this.plugin = plugin;
    }

    public boolean isComplete() {
        if (!plugin.data().server().locations.containsKey("spawn")) return false;
        for (String key : plugin.configs().main().getStringList("setup.required-points")) {
            if (!plugin.data().server().locations.containsKey(key)) return false;
        }
        return true;
    }

    public void command(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            status(sender);
            return;
        }

        if (args[0].equalsIgnoreCase("worlds")) {
            createWorlds(sender);
            return;
        }

        if (args[0].equalsIgnoreCase("auto")) {
            createWorlds(sender);
            Text.send(sender, "<green>Vupe-owned world creation requested.");
            Text.send(sender, "<gray>PlotSquared owns the plot world. Create <white>"
                + plugin.configs().get("worlds").getString("worlds.plots.world", "vupeplots")
                + " <gray>with <white>/plot setup<gray>, then continue Vupe setup.");
            return;
        }

        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>This setup action must be run in-game.");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "goto" -> {
                if (args.length < 2) {
                    Text.send(player, "<red>Usage: /vupe setup goto <spawn|plots|fishing|pvp|farm|mine>");
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                String worldName = plugin.configs().get("worlds").getString("worlds." + id + ".world", id);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    if (id.equals("plots")) {
                        Text.send(player, "<red>The PlotSquared world is not loaded. Create it with <white>/plot setup<red>.");
                    } else {
                        Text.send(player, "<red>That setup world is not loaded. Use <white>/vupe setup worlds<red> first.");
                    }
                    return;
                }
                player.teleportAsync(world.getSpawnLocation().add(0.5, 1, 0.5));
                Text.send(player, "<gray>Teleported to setup world <white>" + worldName + "<gray>.");
            }
            case "point" -> {
                if (args.length < 2) {
                    Text.send(sender, "<red>Usage: /vupe setup point <spawn|fishing|mine|crates|pvp|farm>");
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                savePoint(player, id);
            }
            case "npc" -> {
                if (args.length < 2) {
                    Text.send(sender, "<red>Usage: /vupe setup npc <shop|plots|warps|start|fisher|skipper>");
                    return;
                }
                plugin.modules().nativeNpc().createNpc(player, args[1].toLowerCase(Locale.ROOT));
            }
            case "leaderboard" -> {
                if (args.length < 2) {
                    Text.send(sender, "<red>Usage: /vupe setup leaderboard <money|crystals|prestige|kills|deaths>");
                    return;
                }
                plugin.modules().stats().setLeaderboardLocation(args[1], player.getLocation());
                Text.send(sender, "<green>Leaderboard location saved.");
            }
            case "supply" -> {
                if (args.length < 2) {
                    Text.send(sender, "<red>Usage: /vupe setup supply <1-8>");
                    return;
                }
                plugin.data().server().supplyLocations.put(args[1], Locations.serialize(player.getLocation().getBlock().getLocation()));
                plugin.data().markServerDirty();
                Text.send(sender, "<green>Supply-drop location #" + args[1] + " saved.");
            }
            case "minegen" -> {
                int radius = plugin.configs().get("mining").getInt("mining.generation.radius", 12);
                if (args.length >= 2) {
                    try { radius = Math.max(4, Math.min(32, Integer.parseInt(args[1]))); }
                    catch (NumberFormatException ignored) {}
                }
                generateMine(player, radius);
            }
            case "finish" -> {
                if (isComplete()) Text.send(sender, "<green>Core Vupe setup is complete. Run /vupe doctor for launch checks.");
                else Text.send(sender, "<red>Setup is still incomplete. Use /vupe setup status.");
            }
            default -> Text.send(sender, "<red>Unknown setup action.");
        }
    }

    public void savePoint(Player player, String id) {
        plugin.data().server().locations.put(id.toLowerCase(Locale.ROOT), Locations.serialize(player.getLocation()));
        if (id.equalsIgnoreCase("spawn")) player.getWorld().setSpawnLocation(player.getLocation());
        plugin.data().markServerDirty();
        Text.send(player, "<green>Saved setup point <white>" + id + "<green>.");
    }

    public void status(CommandSender sender) {
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>VUPE SETUP</bold></gradient>");
        for (String key : List.of("spawn", "fishing", "mine", "crates", "pvp", "farm")) {
            boolean set = plugin.data().server().locations.containsKey(key);
            Text.raw(sender, " <dark_gray>• <gray>" + key + ": " + (set ? "<green>set" : "<red>missing"));
        }
        Text.raw(sender, " <dark_gray>• <gray>Native Crates: <green>VupeCore");
        Text.raw(sender, " <dark_gray>• <gray>npcs: <white>" + plugin.data().server().npcLocations.size() + " configured");
        Text.raw(sender, " <dark_gray>• <gray>supply points: <white>" + plugin.data().server().supplyLocations.size());
        Text.raw(sender, " <dark_gray>• <gray>PlotSquared: "
            + (Bukkit.getPluginManager().isPluginEnabled("PlotSquared") ? "<green>loaded" : "<red>missing"));
        Text.raw(sender, " <dark_gray>• <gray>plot world: "
            + (Bukkit.getWorld(plugin.configs().get("worlds").getString("worlds.plots.world", "vupeplots")) != null
                ? "<green>loaded" : "<red>missing — run /plot setup"));
    }

    public void doctor(CommandSender sender) {
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>VUPE DOCTOR</bold></gradient>");
        check(sender, "Setup points", isComplete());
        check(sender, "Spawn world", Bukkit.getWorld(plugin.configs().get("worlds").getString("worlds.spawn.world", "spawn")) != null);
        check(sender, "PlotSquared loaded", Bukkit.getPluginManager().isPluginEnabled("PlotSquared"));
        check(sender, "FastAsyncWorldEdit loaded", Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit"));
        check(sender, "LuckPerms loaded", Bukkit.getPluginManager().isPluginEnabled("LuckPerms"));
        check(sender, "Vault loaded", Bukkit.getPluginManager().isPluginEnabled("Vault"));
        check(sender, "PlaceholderAPI loaded", Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        check(sender, "Native economy module", plugin.modules().economy() != null && plugin.modules().economy().enabled());
        check(sender, "Native shop module", plugin.modules().nativeShop() != null && plugin.modules().nativeShop().enabled());
        check(sender, "Native crates module", plugin.modules().crates() != null && plugin.modules().crates().enabled());
        check(sender, "Native auction module", plugin.modules().nativeAuction() != null && plugin.modules().nativeAuction().enabled());
        check(sender, "Multiverse-Core loaded", Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core"));
        check(sender, "TAB loaded", Bukkit.getPluginManager().isPluginEnabled("TAB"));
        check(sender, "Vault economy provider", plugin.vault() != null && plugin.vault().available());
        check(sender, "PlotSquared world",
            Bukkit.getWorld(plugin.configs().get("worlds").getString("worlds.plots.world", "vupeplots")) != null);
        check(sender, "Fishing world", Bukkit.getWorld(plugin.configs().get("worlds").getString("worlds.fishing.world", "fishing")) != null);
        check(sender, "Mine world", Bukkit.getWorld(plugin.configs().get("worlds").getString("worlds.mine.world", "mine")) != null);

        boolean placeholders = false;
        for (String file : List.of("branding", "discord")) {
            String text = plugin.configs().get(file).saveToString();
            if (text.contains("CHANGE_ME_")) placeholders = true;
        }
        check(sender, "No CHANGE_ME placeholders", !placeholders);

        if (plugin.configs().modules().getBoolean("modules.discord", false)) {
            String token = plugin.configs().get("discord").getString("discord.token", "");
            check(sender, "Discord token configured", token != null && !token.contains("CHANGE_ME") && token.length() > 20);
        }

        Text.raw(sender, "<gray>Loaded players: <white>" + plugin.data().loadedPlayers().size());
        Text.raw(sender, "<gray>Placed generators: <white>" + plugin.data().server().generators.size());
    }

    private void generateMine(Player player, int radius) {
        Location center = Locations.deserialize(plugin.data().server().locations.get("mine"));
        if (center == null) {
            Text.send(player, "<red>Set the mine point first: <white>/vupe setup point mine");
            return;
        }

        int depth = Math.max(4, Math.min(40, plugin.configs().get("mining").getInt("mining.generation.depth", 12)));
        int perTick = Math.max(100, plugin.configs().get("mining").getInt("mining.generation.blocks-per-tick", 500));
        var paletteSection = plugin.configs().get("mining").getConfigurationSection("mining.generation.palette");
        if (paletteSection == null) {
            Text.send(player, "<red>Mining palette is missing.");
            return;
        }

        List<Material> materials = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String key : paletteSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            double weight = paletteSection.getDouble(key, 0);
            if (material != null && weight > 0) {
                materials.add(material);
                weights.add(weight);
            }
        }
        if (materials.isEmpty()) return;

        List<Location> blocks = new ArrayList<>();
        int baseY = center.getBlockY() - 1;
        for (int y = baseY; y >= baseY - depth + 1; y--) {
            for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
                for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                    blocks.add(new Location(center.getWorld(), x, y, z));
                }
            }
        }

        Text.send(player, "<gray>Generating <white>" + blocks.size() + " <gray>mine blocks in batches...");
        final int[] cursor = {0};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int changed = 0;
            while (cursor[0] < blocks.size() && changed < perTick) {
                Location loc = blocks.get(cursor[0]++);
                loc.getBlock().setType(weightedMaterial(materials, weights), false);
                changed++;
            }
            if (cursor[0] >= blocks.size()) {
                task[0].cancel();
                Text.send(player, "<green>Vupe mine generation complete.");
            }
        }, 1L, 1L);
    }

    private Material weightedMaterial(List<Material> materials, List<Double> weights) {
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double roll = Math.random() * total;
        double current = 0;
        for (int i = 0; i < materials.size(); i++) {
            current += weights.get(i);
            if (roll <= current) return materials.get(i);
        }
        return materials.getLast();
    }

    public void createWorlds(CommandSender sender) {
        var cfg = plugin.configs().get("worlds");
        for (String id : List.of("spawn", "fishing", "pvp", "farm", "mine")) {
            String path = "worlds." + id;
            String name = cfg.getString(path + ".world", id);
            if (Bukkit.getWorld(name) != null) {
                Text.send(sender, "<gray>World <white>" + name + " <gray>already exists.");
                continue;
            }
            World.Environment environment;
            try {
                environment = World.Environment.valueOf(cfg.getString(path + ".environment", "NORMAL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                environment = World.Environment.NORMAL;
            }
            WorldCreator creator = new WorldCreator(name).environment(environment);
            if (id.equals("mine") || id.equals("farm")) {
                creator.type(WorldType.FLAT);
            }
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                world.setPVP(cfg.getBoolean(path + ".pvp", false));
                Text.send(sender, "<green>Created world <white>" + name + "<green>.");
                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    // The world is already loaded; Multiverse can track/import it through its command layer.
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + name + " normal"), 2L);
                }
            } else {
                Text.send(sender, "<red>Could not create world " + name + ".");
            }
        }
    }

    public boolean backup() {
        try {
            plugin.data().saveAllSync();
            Path source = plugin.getDataFolder().toPath().resolve("data");
            if (!Files.exists(source)) return true;
            Path target = plugin.getDataFolder().toPath().resolve("backups")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            Files.createDirectories(target);

            try (var stream = Files.walk(source)) {
                stream.forEach(path -> {
                    try {
                        Path rel = source.relativize(path);
                        Path dest = target.resolve(rel);
                        if (Files.isDirectory(path)) Files.createDirectories(dest);
                        else Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Backup failed: " + ex.getMessage());
            return false;
        }
    }

    private void check(CommandSender sender, String name, boolean ok) {
        Text.raw(sender, " <dark_gray>• <gray>" + name + ": " + (ok ? "<green>PASS" : "<red>FAIL"));
    }
}

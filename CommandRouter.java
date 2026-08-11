package dev.vupe.core.command;

import dev.vupe.core.VupeCore;
import dev.vupe.core.module.ModuleRegistry;
import dev.vupe.core.util.Text;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandRouter implements CommandExecutor, TabCompleter {
    @FunctionalInterface
    public interface Handler {
        boolean handle(CommandSender sender, String label, String[] args);
    }

    private final VupeCore plugin;
    private final TabCompletionService tabCompletions;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public CommandRouter(VupeCore plugin) {
        this.plugin = plugin;
        this.tabCompletions = new TabCompletionService(plugin);
    }

    public void register() {
        for (String name : plugin.getDescription().getCommands().keySet()) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        register("vupe", this::handleVupe);
        register("setspawn", (s,l,a) -> setupPoint(s, "spawn"));
        register("setlake", (s,l,a) -> setupPoint(s, "fishing"));
        register("setmine", (s,l,a) -> setupPoint(s, "mine"));
        register("setcrates", (s,l,a) -> setupPoint(s, "crates"));
        register("setfishtravel", this::setFishTravelCompatibility);
        register("setlb", this::setLeaderboardCompatibility);
        register("reloadlb", this::reloadLeaderboardCompatibility);
        register("deletealllb", this::deleteLeaderboardCompatibility);
    }

    public void register(String command, Handler handler) {
        handlers.put(command.toLowerCase(Locale.ROOT), handler);
    }

    public void unregister(String command) {
        handlers.remove(command.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Handler handler = handlers.get(command.getName().toLowerCase(Locale.ROOT));
        if (handler == null) {
            Text.send(sender, "<red>This command is not available because its module is disabled.");
            return true;
        }
        try {
            return handler.handle(sender, label, args);
        } catch (Exception ex) {
            plugin.getLogger().severe("Command /" + command.getName() + " failed: " + ex.getMessage());
            ex.printStackTrace();
            Text.send(sender, "<red>An internal Vupe error occurred. It has been logged.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompletions.complete(sender, command.getName(), args);
    }

    private boolean setupPoint(CommandSender sender, String id) {
        if (!(sender instanceof Player player) || !sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>Player/admin only.");
            return true;
        }
        plugin.setup().savePoint(player, id);
        return true;
    }

    private boolean setFishTravelCompatibility(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>Player/admin only.");
            return true;
        }
        if (args.length < 1 || (!args[0].equalsIgnoreCase("ship") && !args[0].equalsIgnoreCase("beach"))) {
            Text.send(sender, "<red>Usage: /setfishtravel <ship|beach>");
            return true;
        }
        plugin.setup().savePoint(player, "fishtravel:" + args[0].toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean setLeaderboardCompatibility(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>Player/admin only.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /setlb <money|balance|crystal|crystals|prestige|kills|deaths>");
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        if (type.equals("balance")) type = "money";
        if (type.equals("crystal")) type = "crystals";
        plugin.modules().stats().setLeaderboardLocation(type, player.getLocation());
        Text.send(sender, "<green>Leaderboard saved.");
        return true;
    }

    private boolean reloadLeaderboardCompatibility(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        plugin.modules().stats().forceRefreshLeaderboards();
        Text.send(sender, "<green>Leaderboards refreshed.");
        return true;
    }

    private boolean deleteLeaderboardCompatibility(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        plugin.modules().stats().deleteAllLeaderboards();
        Text.send(sender, "<green>Leaderboard locations/entities deleted.");
        return true;
    }

    private boolean handleVupe(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }

        if (args.length == 0) {
            Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>VupeCore</bold></gradient> <gray>v" + plugin.getDescription().getVersion());
            Text.raw(sender, "<gray>/vupe setup status <dark_gray>• <gray>/vupe modules <dark_gray>• <gray>/vupe doctor <dark_gray>• <gray>/vupe reload");
            Text.raw(sender, "<gray>/vupe luckperms bootstrap <dark_gray>• <gray>/vupe integrations");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.configs().reload();
                plugin.modules().reload();
                Text.send(sender, "<green>Configs and modules reloaded.");
            }
            case "modules" -> {
                Text.raw(sender, "<#8B5CF6><bold>VUPE MODULES</bold>");
                for (var module : plugin.modules().all()) {
                    Text.raw(sender, " <dark_gray>• <gray>" + module.id() + ": " +
                        (module.enabled() ? "<green>enabled" : "<red>disabled"));
                }
            }
            case "doctor" -> plugin.setup().doctor(sender);
            case "luckperms" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("bootstrap")) {
                    plugin.luckPerms().bootstrap(sender);
                } else {
                    Text.send(sender, "<gray>Use <white>/vupe luckperms bootstrap<gray> to create/update donor and staff groups.");
                }
            }
            case "integrations" -> {
                Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>VUPE INTEGRATIONS</bold></gradient>");
            Text.raw(sender, " <dark_gray>• <gray>Native Economy: <green>VupeCore");
            Text.raw(sender, " <dark_gray>• <gray>Native Shop: <green>VupeCore");
            Text.raw(sender, " <dark_gray>• <gray>Native Crates: <green>VupeCore");
            Text.raw(sender, " <dark_gray>• <gray>Native Auction House: <green>VupeCore");
                for (String name : List.of("PlotSquared", "FastAsyncWorldEdit", "LuckPerms", "Vault",
                    "PlaceholderAPI", "Multiverse-Core", "TAB")) {
                    boolean loaded = org.bukkit.Bukkit.getPluginManager().isPluginEnabled(name);
                    Text.raw(sender, " <dark_gray>• <gray>" + name + ": " + (loaded ? "<green>loaded" : "<red>missing"));
                }
                Text.raw(sender, " <dark_gray>• <gray>Vault provider: <white>" + plugin.vault().providerName());
            }
            case "save" -> {
                plugin.data().saveAllSync();
                Text.send(sender, "<green>All Vupe data saved.");
            }
            case "backup" -> {
                if (plugin.setup().backup()) Text.send(sender, "<green>Created a Vupe backup.");
                else Text.send(sender, "<red>Backup failed. Check console.");
            }
            case "setup" -> {
                String[] sub = Arrays.copyOfRange(args, 1, args.length);
                plugin.setup().command(sender, sub);
            }
            case "version" -> Text.send(sender, "<gray>VupeCore <white>" + plugin.getDescription().getVersion() + " <gray>for Paper 1.21.11.");
            default -> Text.send(sender, "<red>Unknown Vupe admin command.");
        }
        return true;
    }

}

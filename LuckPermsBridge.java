package dev.vupe.core.integration;

import dev.vupe.core.VupeCore;
import dev.vupe.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public final class LuckPermsBridge {
    private final VupeCore plugin;

    public LuckPermsBridge(VupeCore plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    private void console(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public void syncDonor(Player player, String oldRank, String newRank) {
        syncDonor(player.getName(), oldRank, newRank);
        Bukkit.getScheduler().runTaskLater(plugin, player::updateCommands, 2L);
    }

    public void syncDonor(String playerName, String oldRank, String newRank) {
        if (!available() || playerName == null || playerName.isBlank()) return;
        if (oldRank != null && !oldRank.isBlank()) {
            console("lp user " + playerName + " parent remove " + normalize(oldRank));
        }
        if (newRank != null && !newRank.isBlank()) {
            console("lp user " + playerName + " parent add " + normalize(newRank));
        }
    }

    public void setStaffRank(Player target, String group) {
        if (!available()) return;
        ConfigurationSection section = plugin.configs().get("staff").getConfigurationSection("staff-ranks");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                console("lp user " + target.getName() + " parent remove " + normalize(id));
            }
        }
        if (group != null && !group.equalsIgnoreCase("none")) {
            console("lp user " + target.getName() + " parent add " + normalize(group));
        }
        Bukkit.getScheduler().runTaskLater(plugin, target::updateCommands, 2L);
    }

    public String staffPrefix(Player player) {
        ConfigurationSection section = plugin.configs().get("staff").getConfigurationSection("staff-ranks");
        if (section == null) return "";
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> section.getInt(id + ".weight", 0)).reversed());
        for (String id : ids) {
            if (player.hasPermission("group." + normalize(id)) || player.hasPermission("vupe.staffrank." + normalize(id))) {
                return section.getString(id + ".prefix", "");
            }
        }
        return "";
    }

    public String staffGroup(Player player) {
        ConfigurationSection section = plugin.configs().get("staff").getConfigurationSection("staff-ranks");
        if (section == null) return "none";
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> section.getInt(id + ".weight", 0)).reversed());
        for (String id : ids) {
            if (player.hasPermission("group." + normalize(id)) || player.hasPermission("vupe.staffrank." + normalize(id))) return id;
        }
        return "none";
    }

    public void bootstrap(CommandSender sender) {
        if (!available()) {
            Text.send(sender, "<red>LuckPerms is not installed.");
            return;
        }

        ConfigurationSection donors = plugin.configs().get("ranks").getConfigurationSection("donor-ranks");
        if (donors != null) {
            for (String id : donors.getKeys(false)) {
                int weight = donors.getInt(id + ".weight", 100);
                int priority = donors.getInt(id + ".prefix-priority", 100 + weight);
                String legacyPrefix = plainPrefix(donors.getString(id + ".plain-prefix", "[" + id + "] "));
                console("lp creategroup " + normalize(id));
                console("lp group " + normalize(id) + " setweight " + weight);
                console("lp group " + normalize(id) + " meta setprefix " + priority + " \"" + legacyPrefix + "\"");
                for (String permission : donors.getStringList(id + ".permissions")) {
                    console("lp group " + normalize(id) + " permission set " + permission + " true");
                }
                int vaults = donors.getInt(id + ".vaults", 0);
                if (vaults > 0) {
                    console("lp group " + normalize(id) + " permission set vupe.vaults." + vaults + " true");
                }
                int plots = donors.getInt(id + ".plot-limit", 1);
                console("lp group " + normalize(id) + " permission set plots.plot." + Math.max(1, plots) + " true");
            }
        }

        ConfigurationSection staff = plugin.configs().get("staff").getConfigurationSection("staff-ranks");
        if (staff != null) {
            for (String id : staff.getKeys(false)) {
                int weight = staff.getInt(id + ".weight", 500);
                int priority = staff.getInt(id + ".prefix-priority", 500 + weight);
                String prefix = plainPrefix(staff.getString(id + ".plain-prefix", "[" + id + "] "));
                console("lp creategroup " + normalize(id));
                console("lp group " + normalize(id) + " setweight " + weight);
                console("lp group " + normalize(id) + " meta setprefix " + priority + " \"" + prefix + "\"");
                console("lp group " + normalize(id) + " permission set vupe.staff true");
                console("lp group " + normalize(id) + " permission set vupe.staffrank." + normalize(id) + " true");
                for (String permission : staff.getStringList(id + ".permissions")) {
                    console("lp group " + normalize(id) + " permission set " + permission + " true");
                }
            }
        }

        Text.send(sender, "<green>LuckPerms groups/prefixes/permissions were bootstrapped from Vupe configs.");
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replace("+", "plus").replace(" ", "");
    }

    private static String plainPrefix(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"");
    }
}

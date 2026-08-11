package dev.vupe.core.config;

import dev.vupe.core.VupeCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ConfigManager {
    private final VupeCore plugin;
    private final Map<String, YamlConfiguration> configs = new HashMap<>();

    public ConfigManager(VupeCore plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        configs.clear();
        String[] names = {
            "config", "modules", "branding", "economy", "ranks", "generators",
            "crates", "worlds", "fishing", "mining", "farming", "pvp",
             "moderation", "events", "discord", "messages", "social", "shops", "npcs", "kits", "voting", "boxes", "crystalshop", "limits", "minions", "menus", "scoreboard", "tablist",
            "effects", "staff", "store", "autosellchests", "levels", "auctionhouse"
        };
        for (String name : names) {
            configs.put(name, YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), name + ".yml")
            ));
        }
    }

    public YamlConfiguration get(String name) {
        YamlConfiguration cfg = configs.get(name);
        if (cfg == null) throw new IllegalArgumentException("Unknown config: " + name);
        return cfg;
    }

    public YamlConfiguration main() { return get("config"); }
    public YamlConfiguration modules() { return get("modules"); }
}

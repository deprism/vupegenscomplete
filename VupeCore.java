package dev.vupe.core;

import dev.vupe.core.command.CommandRouter;
import dev.vupe.core.config.ConfigManager;
import dev.vupe.core.data.DataStore;
import dev.vupe.core.module.ModuleRegistry;
import dev.vupe.core.integration.VaultHook;
import dev.vupe.core.integration.VupePlaceholderExpansion;
import dev.vupe.core.integration.LuckPermsBridge;
import dev.vupe.core.ui.UiEffects;
import dev.vupe.core.setup.SetupService;
import dev.vupe.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class VupeCore extends JavaPlugin {
    private static VupeCore instance;
    private ConfigManager configs;
    private DataStore data;
    private ModuleRegistry modules;
    private SetupService setup;
    private CommandRouter commands;
    private VaultHook vault;
    private LuckPermsBridge luckPerms;
    private UiEffects effects;
    private VupePlaceholderExpansion placeholders;

    public static VupeCore get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultResources();

        configs = new ConfigManager(this);
        configs.reload();

        data = new DataStore(this);
        data.load();

        effects = new UiEffects(this);
        luckPerms = new LuckPermsBridge(this);
        vault = new VaultHook(this);
        if (!vault.hook()) {
            getLogger().warning("Vault compatibility is unavailable, but Vupe's native economy remains active.");
        }

        setup = new SetupService(this);
        commands = new CommandRouter(this);
        modules = new ModuleRegistry(this);

        modules.load();
        commands.register();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new VupePlaceholderExpansion(this);
            placeholders.register();
            getLogger().info("Registered %vupe_*% PlaceholderAPI expansion.");
        }

        long saveTicks = Math.max(20L, configs.main().getLong("server.save-interval-seconds", 120L) * 20L);
        Bukkit.getScheduler().runTaskTimer(this, data::saveDirty, saveTicks, saveTicks);

        getLogger().info("VupeCore enabled with " + modules.enabledCount() + " modules.");
        if (!setup.isComplete()) {
            getLogger().warning("Vupe setup is incomplete. Use /vupe setup status.");
        }
    }

    @Override
    public void onDisable() {
        if (placeholders != null) placeholders.unregister();
        if (vault != null) vault.unhook();
        if (modules != null) modules.unload();
        if (data != null) data.saveAllSync();
        instance = null;
    }

    public ConfigManager configs() { return configs; }
    public DataStore data() { return data; }
    public ModuleRegistry modules() { return modules; }
    public SetupService setup() { return setup; }
    public CommandRouter commands() { return commands; }
    public VaultHook vault() { return vault; }
    public LuckPermsBridge luckPerms() { return luckPerms; }
    public UiEffects effects() { return effects; }

    private void saveDefaultResources() {
        String[] resources = {
            "config.yml", "modules.yml", "branding.yml", "economy.yml", "ranks.yml",
            "generators.yml", "crates.yml", "worlds.yml", "fishing.yml", "mining.yml",
            "farming.yml", "pvp.yml", "moderation.yml", "events.yml", "discord.yml",
             "messages.yml", "social.yml", "shops.yml", "npcs.yml", "kits.yml", "voting.yml", "boxes.yml", "crystalshop.yml", "limits.yml", "minions.yml", "menus.yml", "scoreboard.yml", "tablist.yml",
            "effects.yml", "staff.yml", "store.yml", "autosellchests.yml", "levels.yml", "auctionhouse.yml"
        };
        for (String resource : resources) {
            if (!new java.io.File(getDataFolder(), resource).exists()) {
                saveResource(resource, false);
            }
        }
    }
}

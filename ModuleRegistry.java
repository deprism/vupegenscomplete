package dev.vupe.core.module;

import dev.vupe.core.VupeCore;
import dev.vupe.core.module.impl.*;

import java.util.*;

public final class ModuleRegistry {
    private final VupeCore plugin;
    private final List<VupeModule> modules = new ArrayList<>();

    private EconomyModule economy;
    private GeneratorModule generators;
    private CrateModule crates;
    private ShopModule shop;
    private NativeShopModule nativeShop;
    private NativeAuctionModule nativeAuction;
    private ProgressionModule progression;
    private UtilityModule utilities;
    private PlotWorldModule plots;
    private MarketModule market;
    private ActivityModule activities;
    private SocialModule social;
    private PvpModule pvp;
    private ModerationModule moderation;
    private StatsModule stats;
    private EventModule events;
    private NativeNpcModule nativeNpc;
    private DiscordModule discord;
    private MinionModule minions;
    private CommerceModule commerce;
    private AutoSellChestModule autosellChests;
    private ProgressionGuiModule progressionGui;
    private StaffGuiModule staffGui;

    public ModuleRegistry(VupeCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        modules.clear();
        economy = add(new EconomyModule(plugin));
        generators = add(new GeneratorModule(plugin));
        crates = add(new CrateModule(plugin));
        shop = add(new ShopModule(plugin));
        nativeShop = add(new NativeShopModule(plugin));
        nativeAuction = add(new NativeAuctionModule(plugin));
        progression = add(new ProgressionModule(plugin));
        utilities = add(new UtilityModule(plugin));
        plots = add(new PlotWorldModule(plugin));
        market = add(new MarketModule(plugin));
        activities = add(new ActivityModule(plugin));
        social = add(new SocialModule(plugin));
        pvp = add(new PvpModule(plugin));
        moderation = add(new ModerationModule(plugin));
        stats = add(new StatsModule(plugin));
        events = add(new EventModule(plugin));
        nativeNpc = add(new NativeNpcModule(plugin));
        discord = add(new DiscordModule(plugin));
        minions = add(new MinionModule(plugin));
        commerce = add(new CommerceModule(plugin));
        autosellChests = add(new AutoSellChestModule(plugin));
        progressionGui = add(new ProgressionGuiModule(plugin));
        staffGui = add(new StaffGuiModule(plugin));

        applyEnabledStates();
    }

    public void reload() {
        for (VupeModule module : modules) module.disable();
        applyEnabledStates();
    }

    public void unload() {
        ListIterator<VupeModule> it = modules.listIterator(modules.size());
        while (it.hasPrevious()) it.previous().disable();
    }

    private void applyEnabledStates() {
        for (VupeModule module : modules) {
            if (shouldEnable(module.id())) module.enable();
        }
    }

    private boolean shouldEnable(String id) {
        return switch (id) {
            case "economy" -> plugin.configs().modules().getBoolean("modules.economy", true);
            case "generators" -> plugin.configs().modules().getBoolean("modules.generators", true);
            case "crates" -> plugin.configs().modules().getBoolean("modules.crates", true);
            case "shop" -> plugin.configs().modules().getBoolean("modules.shop", true);
            case "native-shop" -> plugin.configs().modules().getBoolean("modules.shop", true);
            case "native-auction" -> plugin.configs().modules().getBoolean("modules.auction-house", true);
            case "progression" -> plugin.configs().modules().getBoolean("modules.progression", true);
            case "utilities" -> plugin.configs().modules().getBoolean("modules.player-utilities", true);
            case "plots" -> plugin.configs().modules().getBoolean("modules.plots", true)
                || plugin.configs().modules().getBoolean("modules.container-limits", true);
            case "market" -> plugin.configs().modules().getBoolean("modules.auction-house", true)
                || plugin.configs().modules().getBoolean("modules.coinflips", true);
            case "activities" -> plugin.configs().modules().getBoolean("modules.fishing", true)
                || plugin.configs().modules().getBoolean("modules.mining", true)
                || plugin.configs().modules().getBoolean("modules.farming", true);
            case "social" -> plugin.configs().modules().getBoolean("modules.chat", true)
                || plugin.configs().modules().getBoolean("modules.teams", true)
                || plugin.configs().modules().getBoolean("modules.afk", true);
            case "pvp" -> plugin.configs().modules().getBoolean("modules.pvp-stats", true)
                || plugin.configs().modules().getBoolean("modules.combat-tag", true);
            case "moderation" -> plugin.configs().modules().getBoolean("modules.moderation", true);
            case "stats" -> plugin.configs().modules().getBoolean("modules.stats", true)
                || plugin.configs().modules().getBoolean("modules.scoreboard", true)
                || plugin.configs().modules().getBoolean("modules.tab-list", true)
                || plugin.configs().modules().getBoolean("modules.leaderboards", true);
            case "events" -> plugin.configs().modules().getBoolean("modules.missions", true)
                || plugin.configs().modules().getBoolean("modules.supply-drops", true)
                || plugin.configs().modules().getBoolean("modules.ads", true);
            case "native-npcs" -> plugin.configs().modules().getBoolean("modules.native-npcs", true)
                || plugin.configs().modules().getBoolean("modules.warps", true);
            case "discord" -> plugin.configs().modules().getBoolean("modules.discord", false);
            case "minions" -> plugin.configs().modules().getBoolean("modules.minions", false);
            case "commerce" -> plugin.configs().modules().getBoolean("modules.commerce", true);
            case "autosell-chests" -> plugin.configs().modules().getBoolean("modules.autosell-chests", true);
            case "progression-guis" -> plugin.configs().modules().getBoolean("modules.progression-guis", true);
            case "staff-guis" -> plugin.configs().modules().getBoolean("modules.staff-guis", true);
            default -> true;
        };
    }

    private <T extends VupeModule> T add(T module) {
        modules.add(module);
        return module;
    }

    public int enabledCount() { return (int) modules.stream().filter(VupeModule::enabled).count(); }
    public List<VupeModule> all() { return Collections.unmodifiableList(modules); }

    public EconomyModule economy() { return economy; }
    public GeneratorModule generators() { return generators; }
    public CrateModule crates() { return crates; }
    public ShopModule shop() { return shop; }
    public NativeShopModule nativeShop() { return nativeShop; }
    public NativeAuctionModule nativeAuction() { return nativeAuction; }
    public ProgressionModule progression() { return progression; }
    public UtilityModule utilities() { return utilities; }
    public PlotWorldModule plots() { return plots; }
    public MarketModule market() { return market; }
    public ActivityModule activities() { return activities; }
    public SocialModule social() { return social; }
    public PvpModule pvp() { return pvp; }
    public ModerationModule moderation() { return moderation; }
    public StatsModule stats() { return stats; }
    public EventModule events() { return events; }
    public NativeNpcModule nativeNpc() { return nativeNpc; }
    public DiscordModule discord() { return discord; }
    public MinionModule minions() { return minions; }
    public CommerceModule commerce() { return commerce; }
    public AutoSellChestModule autosellChests() { return autosellChests; }
    public ProgressionGuiModule progressionGui() { return progressionGui; }
    public StaffGuiModule staffGui() { return staffGui; }
}

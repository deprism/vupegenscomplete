package dev.vupe.core.module;

import dev.vupe.core.VupeCore;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class VupeModule implements Listener {
    protected final VupeCore plugin;
    private final String id;
    private boolean enabled;

    protected VupeModule(VupeCore plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public final void enable() {
        if (enabled) return;
        enabled = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        onEnable();
    }

    public final void disable() {
        if (!enabled) return;
        onDisable();
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public String id() { return id; }
    public boolean enabled() { return enabled; }
}

package dev.vupe.core.ui;

import dev.vupe.core.VupeCore;
import dev.vupe.core.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;

public final class UiEffects {
    private final VupeCore plugin;

    public UiEffects(VupeCore plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get("effects");
    }

    public void sound(Player player, String id) {
        String path = "sounds." + id + ".";
        String raw = cfg().getString(path + "sound", "");
        if (raw == null || raw.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound,
                (float) cfg().getDouble(path + "volume", 0.8),
                (float) cfg().getDouble(path + "pitch", 1.0));
        } catch (IllegalArgumentException ignored) {}
    }

    public void success(Player player) { sound(player, "success"); }
    public void error(Player player) { sound(player, "error"); }
    public void click(Player player) { sound(player, "click"); }
    public void open(Player player) { sound(player, "open"); }
    public void purchase(Player player) { sound(player, "purchase"); }

    public void title(Player player, String title, String subtitle) {
        player.showTitle(Title.title(
            Text.component(title),
            Text.component(subtitle),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(450))
        ));
    }

    public void celebrate(Player player) {
        sound(player, "level-up");
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
            player.getLocation().add(0, 1, 0), 60, 0.7, 1.0, 0.7, 0.08);
        player.getWorld().spawnParticle(Particle.END_ROD,
            player.getLocation().add(0, 1, 0), 35, 0.8, 0.8, 0.8, 0.03);

        if (!cfg().getBoolean("celebration.firework", true)) return;
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
            .with(FireworkEffect.Type.BURST)
            .withColor(Color.fromRGB(139, 92, 246), Color.fromRGB(34, 211, 238))
            .withFade(Color.fromRGB(244, 114, 182))
            .trail(true).flicker(true).build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 2L);
    }

    public void broadcast(String message, String soundId) {
        Bukkit.broadcast(Text.component(message));
        for (Player player : Bukkit.getOnlinePlayers()) sound(player, soundId);
    }
}

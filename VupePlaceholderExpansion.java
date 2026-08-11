package dev.vupe.core.integration;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class VupePlaceholderExpansion extends PlaceholderExpansion {
    private final VupeCore plugin;

    public VupePlaceholderExpansion(VupeCore plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "vupe"; }
    @Override public @NotNull String getAuthor() { return "Vupe"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        PlayerData data = plugin.data().player(player.getUniqueId());

        return switch (identifier.toLowerCase(Locale.ROOT)) {
            case "money" -> Text.format(plugin.modules().economy().money(player.getUniqueId()));
            case "money_raw" -> Double.toString(plugin.modules().economy().money(player.getUniqueId()));
            case "crystals" -> Long.toString(data.crystals);
            case "gold" -> Long.toString(data.gold);
            case "donor_rank" -> data.donorRank == null || data.donorRank.isBlank() ? "None" : data.donorRank;
            case "rank" -> data.progressionRank == null ? "rookie" : data.progressionRank;
            case "level" -> Integer.toString(data.level);
            case "prestige" -> Integer.toString(data.prestige);
            case "xp" -> Long.toString(data.xp);
            case "genslots" -> Integer.toString(data.generatorSlots);
            case "sellmulti" -> Text.format(plugin.modules().shop().effectiveSellMultiplier(player));
            case "kills" -> Long.toString(data.kills);
            case "deaths" -> Long.toString(data.deaths);
            case "team" -> data.teamId == null || data.teamId.isBlank() ? "None" : data.teamId;
            case "playtime" -> Long.toString(data.playtimeSeconds);
            case "afk" -> Boolean.toString(data.afk);
            case "bounty" -> Text.format(data.bounty);
            case "fishing_level" -> Integer.toString(data.fishingLevel);
            case "backpack_level" -> Integer.toString(data.backpackLevel);
            default -> null;
        };
    }
}

package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class StatsModule extends VupeModule {
    private BukkitTask scoreboardTask;
    private BukkitTask tabTask;
    private BukkitTask leaderboardTask;
    private final Map<String, UUID> leaderboardEntities = new HashMap<>();
    private final Set<UUID> profileSessions = new HashSet<>();

    public StatsModule(VupeCore plugin) {
        super(plugin, "stats");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("stats", this::statsCommand);

        // TAB is the visual authority in MAX by default. Only create Vupe's
        // legacy visual tasks when those modules are explicitly enabled.
        if (plugin.configs().modules().getBoolean("modules.scoreboard", false)) {
            scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshScoreboards, 40L,
                Math.max(20L, plugin.configs().main().getLong("server.scoreboard-refresh-ticks", 60)));
        }
        if (plugin.configs().modules().getBoolean("modules.tab-list", false)) {
            tabTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTabLists, 20L,
                Math.max(20L, plugin.configs().get("tablist").getLong("tablist.refresh-ticks", 40L)));
        }
        if (plugin.configs().modules().getBoolean("modules.leaderboards", true)) {
            long leaderboardPeriod = Math.max(20L,
                plugin.configs().main().getLong("server.leaderboard-refresh-seconds", 60) * 20L);
            leaderboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboards, 60L, leaderboardPeriod);
            Bukkit.getScheduler().runTaskLater(plugin, this::loadLeaderboards, 40L);
        }
    }

    @Override
    protected void onDisable() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (tabTask != null) tabTask.cancel();
        if (leaderboardTask != null) leaderboardTask.cancel();
        scoreboardTask = null;
        tabTask = null;
        leaderboardTask = null;
        if (plugin.configs().modules().getBoolean("modules.tab-list", false)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playerListName(Component.text(player.getName()));
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.configs().modules().getBoolean("modules.scoreboard", false)) {
                refreshScoreboard(event.getPlayer());
            }
            if (plugin.configs().modules().getBoolean("modules.tab-list", false)) {
                refreshTabLists();
            }
        }, 20L);
    }

    private boolean statsCommand(CommandSender sender, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Text.send(sender, "<red>Player not found.");
                return true;
            }
        } else if (sender instanceof Player player) {
            openProfile(player);
            return true;
        } else {
            Text.send(sender, "<red>Usage: /stats <player>");
            return true;
        }

        PlayerData data = plugin.data().player(target.getUniqueId());
        long play = data.playtimeSeconds + (data.lastJoinEpoch > 0 ? Math.max(0, (System.currentTimeMillis() - data.lastJoinEpoch) / 1000L) : 0);
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>" + target.getName() + " — VUPE STATS</bold></gradient>");
        Text.raw(sender, " <dark_gray>• <gray>Money: <green>$" + Text.format(plugin.modules().economy().money(data.uuid)));
        Text.raw(sender, " <dark_gray>• <gray>Crystals: <#8B5CF6>" + data.crystals);
        Text.raw(sender, " <dark_gray>• <gray>Gold: <gold>" + data.gold);
        Text.raw(sender, " <dark_gray>• <gray>Rank: " + plugin.modules().progression().donorDisplay(data.donorRank));
        Text.raw(sender, " <dark_gray>• <gray>Progression: <white>" + data.progressionRank);
        Text.raw(sender, " <dark_gray>• <gray>Level/Prestige: <white>" + data.level + "<dark_gray>/<#8B5CF6>" + data.prestige);
        Text.raw(sender, " <dark_gray>• <gray>Kills/Deaths: <white>" + data.kills + "<dark_gray>/<white>" + data.deaths);
        Text.raw(sender, " <dark_gray>• <gray>Gen slots: <white>" + data.generatorSlots);
        Text.raw(sender, " <dark_gray>• <gray>Playtime: <white>" + formatPlaytime(play));
        return true;
    }

    private void openProfile(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        long play = data.playtimeSeconds
            + (data.lastJoinEpoch > 0 ? Math.max(0, (System.currentTimeMillis() - data.lastJoinEpoch) / 1000L) : 0);
        long gens = plugin.data().server().generators.values().stream()
            .filter(g -> player.getUniqueId().toString().equals(g.owner)).count();

        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#8B5CF6:#22D3EE><bold>YOUR VUPE PROFILE</bold></gradient>"));
        ItemStack filler = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i=0;i<inv.getSize();i++) {
            int row=i/9,col=i%9;
            if(row==0||row==5||col==0||col==8) inv.setItem(i,filler);
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.displayName(Text.component("<white><bold>" + player.getName() + "</bold>"));
            skull.lore(List.of(
                Text.component("<gray>Donor: " + plugin.modules().progression().donorDisplay(data.donorRank)),
                Text.component("<gray>Progression: <white>" + data.progressionRank),
                Text.component("<gray>Level: <white>" + data.level + " <dark_gray>• <gray>Prestige: <#8B5CF6>" + data.prestige),
                Text.component("<gray>Playtime: <white>" + formatPlaytime(play))
            ));
            head.setItemMeta(skull);
        }
        inv.setItem(4, head);

        inv.setItem(10, Items.item(Material.EMERALD, "<green><bold>ECONOMY</bold>",
            List.of("<gray>Money: <green>$" + Text.format(plugin.modules().economy().money(data.uuid)),
                "<gray>Crystals: <#8B5CF6>" + data.crystals + " ✦",
                "<gray>Gold: <gold>" + data.gold)));
        inv.setItem(12, Items.item(Material.RESPAWN_ANCHOR, "<#22D3EE><bold>GENERATORS</bold>",
            List.of("<gray>Placed: <white>" + gens + "<dark_gray>/<white>" + data.generatorSlots,
                "<gray>Sell multiplier: <#F472B6>" + Text.format(plugin.modules().shop().effectiveSellMultiplier(player)) + "x")));
        inv.setItem(14, Items.item(Material.DIAMOND_SWORD, "<#FB7185><bold>PVP</bold>",
            List.of("<gray>Kills: <white>" + data.kills,
                "<gray>Deaths: <white>" + data.deaths,
                "<gray>K/D: <white>" + Text.format(data.deaths <= 0 ? data.kills : data.kills/(double)data.deaths),
                "<gray>Bounty: <green>$" + Text.format(data.bounty))));
        inv.setItem(16, Items.item(Material.FISHING_ROD, "<#67E8F9><bold>ACTIVITIES</bold>",
            List.of("<gray>Fishing Level: <white>" + data.fishingLevel,
                "<gray>Backpack Level: <white>" + data.backpackLevel,
                "<gray>Team: <white>" + (data.teamId == null || data.teamId.isBlank() ? "None" : data.teamId))));

        inv.setItem(28, Items.tagged(Material.COMPASS, "<#22D3EE><bold>RANK PATH</bold>",
            List.of("<yellow>Click to open your rank tree."), "profile_action", "rankup"));
        inv.setItem(30, Items.tagged(Material.EXPERIENCE_BOTTLE, "<#38BDF8><bold>LEVELS</bold>",
            List.of("<yellow>Click to open levels/prestige."), "profile_action", "levels"));
        inv.setItem(32, Items.tagged(Material.CHEST, "<#FBBF24><bold>REWARDS</bold>",
            List.of("<yellow>Click to claim milestones."), "profile_action", "rewards"));
        inv.setItem(34, Items.tagged(Material.NAME_TAG, "<#A78BFA><bold>RANK PERKS</bold>",
            List.of("<yellow>Click to compare donor ranks."), "profile_action", "perks"));

        inv.setItem(39, Items.tagged(Material.GRASS_BLOCK, "<#34D399><bold>YOUR PLOT</bold>",
            List.of("<yellow>Click to teleport."), "profile_action", "warps plot"));
        inv.setItem(40, Items.tagged(Material.CHEST, "<gold><bold>AUCTION HOUSE</bold>",
            List.of("<yellow>Click to browse."), "profile_action", "auction"));
        inv.setItem(41, Items.tagged(Material.NETHER_STAR, "<gradient:#F472B6:#8B5CF6><bold>STORE</bold></gradient>",
            List.of("<yellow>Click to browse."), "profile_action", "store"));

        profileSessions.add(player.getUniqueId());
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProfileClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !profileSessions.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        String action = Items.tag(event.getCurrentItem(), "profile_action");
        if (action == null) return;
        plugin.effects().click(player);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, action));
    }

    @EventHandler
    public void onProfileClose(InventoryCloseEvent event) {
        profileSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onProfileDrag(InventoryDragEvent event) {
        if (profileSessions.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void refreshTabLists() {
        if (!plugin.configs().modules().getBoolean("modules.tab-list", true)) return;
        if (!plugin.configs().get("tablist").getBoolean("tablist.enabled", true)) return;

        // The name component belongs to the listed player and is visible to all viewers.
        String nameFormat = plugin.configs().get("tablist").getString("tablist.player-name-format", "%rank% <white>%player%");
        for (Player listed : Bukkit.getOnlinePlayers()) {
            listed.playerListName(Text.component(render(nameFormat, listed)));
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            List<String> headerLines = plugin.configs().get("tablist").getStringList("tablist.header");
            List<String> footerLines = plugin.configs().get("tablist").getStringList("tablist.footer");

            String header = headerLines.stream().map(line -> render(line, viewer)).collect(java.util.stream.Collectors.joining("\\n"));
            String footer = footerLines.stream().map(line -> render(line, viewer)).collect(java.util.stream.Collectors.joining("\\n"));
            viewer.sendPlayerListHeaderAndFooter(Text.component(header), Text.component(footer));
        }
    }

    private String render(String input, Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        String rendered = input == null ? "" : input;
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("online", Integer.toString(Bukkit.getOnlinePlayers().size()));
        placeholders.put("max_players", Integer.toString(Bukkit.getMaxPlayers()));
        placeholders.put("ping", Integer.toString(Math.max(0, player.getPing())));
        placeholders.put("money", Text.format(plugin.modules().economy().money(data.uuid)));
        placeholders.put("crystals", Long.toString(data.crystals));
        placeholders.put("gold", Long.toString(data.gold));
        placeholders.put("rank", shortRank(data));
        placeholders.put("level", Integer.toString(data.level));
        placeholders.put("prestige", Integer.toString(data.prestige));
        placeholders.put("server_address", plugin.configs().get("branding").getString("brand.server-address", "play.vupe"));

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return rendered;
    }

    private void refreshScoreboards() {
        if (!plugin.configs().modules().getBoolean("modules.scoreboard", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.data().player(player.getUniqueId()).options.getOrDefault("scoreboard", true)) continue;
            refreshScoreboard(player);
        }
    }

    private void refreshScoreboard(Player player) {
        if (!plugin.configs().get("scoreboard").getBoolean("scoreboard.enabled", true)) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
            "vupe",
            Criteria.DUMMY,
            Text.component(plugin.configs().get("scoreboard").getString(
                "scoreboard.title",
                "<gradient:#8B5CF6:#22D3EE><bold>VUPE</bold></gradient>"
            ))
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerData data = plugin.data().player(player.getUniqueId());
        long gens = plugin.data().server().generators.values().stream()
            .filter(g -> player.getUniqueId().toString().equals(g.owner)).count();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("money", Text.format(plugin.modules().economy().money(data.uuid)));
        placeholders.put("crystals", Long.toString(data.crystals));
        placeholders.put("gold", Long.toString(data.gold));
        placeholders.put("rank", shortRank(data));
        placeholders.put("level", Integer.toString(data.level));
        placeholders.put("prestige", Integer.toString(data.prestige));
        placeholders.put("gens", Long.toString(gens));
        placeholders.put("genslots", Integer.toString(data.generatorSlots));
        placeholders.put("online", Integer.toString(Bukkit.getOnlinePlayers().size()));
        placeholders.put("server_address", plugin.configs().get("branding").getString("brand.server-address", "play.vupe"));

        List<String> lines = plugin.configs().get("scoreboard").getStringList("scoreboard.lines");
        int score = Math.min(15, lines.size());
        for (String line : lines) {
            if (score <= 0) break;
            String rendered = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            setLine(objective, rendered, score--);
        }

        player.setScoreboard(board);
    }

    private void setLine(Objective objective, String line, int score) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .serialize(Text.component(line));
        if (legacy.length() > 40) legacy = legacy.substring(0, 40);
        objective.getScore(legacy + ChatColor.values()[Math.floorMod(score, ChatColor.values().length)]).setScore(score);
    }

    public void setLeaderboardLocation(String type, Location location) {
        plugin.data().server().leaderboardLocations.put(type.toLowerCase(Locale.ROOT), Locations.serialize(location));
        plugin.data().markServerDirty();
        createOrMoveLeaderboard(type.toLowerCase(Locale.ROOT), location);
    }

    private void loadLeaderboards() {
        for (Map.Entry<String, String> entry : plugin.data().server().leaderboardLocations.entrySet()) {
            Location location = Locations.deserialize(entry.getValue());
            if (location != null) createOrMoveLeaderboard(entry.getKey(), location);
        }
        refreshLeaderboards();
    }

    private void createOrMoveLeaderboard(String type, Location location) {
        TextDisplay display = null;
        UUID existingId = leaderboardEntities.get(type);
        if (existingId != null && Bukkit.getEntity(existingId) instanceof TextDisplay found) {
            display = found;
            display.teleport(location);
        }
        if (display == null) {
            display = location.getWorld().spawn(location, TextDisplay.class);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(new NamespacedKey(plugin, "leaderboard_type"), PersistentDataType.STRING, type);
            leaderboardEntities.put(type, display.getUniqueId());
        }
    }

    public void forceRefreshLeaderboards() {
        refreshLeaderboards();
    }

    public void deleteAllLeaderboards() {
        NamespacedKey key = new NamespacedKey(plugin, "leaderboard_type");
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) display.remove();
            }
        }
        leaderboardEntities.clear();
        plugin.data().server().leaderboardLocations.clear();
        plugin.data().markServerDirty();
    }

    private void refreshLeaderboards() {
        if (!plugin.configs().modules().getBoolean("modules.leaderboards", true)) return;

        for (Map.Entry<String, String> entry : plugin.data().server().leaderboardLocations.entrySet()) {
            String type = entry.getKey();
            Location location = Locations.deserialize(entry.getValue());
            if (location == null) continue;
            createOrMoveLeaderboard(type, location);

            UUID id = leaderboardEntities.get(type);
            if (!(Bukkit.getEntity(id) instanceof TextDisplay display)) continue;

            List<PlayerData> list = new ArrayList<>();
            for (String uuidText : plugin.data().server().knownPlayers) {
                try { list.add(plugin.data().player(UUID.fromString(uuidText))); }
                catch (IllegalArgumentException ignored) {}
            }
            list.sort(comparator(type).reversed());

            StringBuilder text = new StringBuilder("<gradient:#8B5CF6:#22D3EE><bold>")
                .append(type.toUpperCase(Locale.ROOT)).append(" TOP</bold></gradient>");
            int rank = 1;
            for (PlayerData data : list.stream().limit(10).toList()) {
                text.append("\n<gray>").append(rank++).append(". <white>")
                    .append(data.lastName == null || data.lastName.isBlank() ? data.uuid : data.lastName)
                    .append(" <dark_gray>• <#22D3EE>").append(metric(type, data));
            }
            display.text(Text.component(text.toString()));
        }
    }

    private Comparator<PlayerData> comparator(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "crystals" -> Comparator.comparingLong(d -> d.crystals);
            case "prestige" -> Comparator.comparingInt(d -> d.prestige);
            case "kills" -> Comparator.comparingLong(d -> d.kills);
            case "deaths" -> Comparator.comparingLong(d -> d.deaths);
            default -> Comparator.comparingDouble(d -> plugin.modules().economy().money(d.uuid));
        };
    }

    private String metric(String type, PlayerData data) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "crystals" -> Long.toString(data.crystals);
            case "prestige" -> Integer.toString(data.prestige);
            case "kills" -> Long.toString(data.kills);
            case "deaths" -> Long.toString(data.deaths);
            default -> "$" + Text.format(plugin.modules().economy().money(data.uuid));
        };
    }

    private String shortRank(PlayerData data) {
        if (data.donorRank != null && !data.donorRank.isBlank()) return plugin.modules().progression().donorDisplay(data.donorRank);
        return "<white>" + data.progressionRank;
    }

    private String formatPlaytime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        return days + "d " + hours + "h " + minutes + "m";
    }
}

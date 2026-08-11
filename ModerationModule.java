package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import dev.vupe.core.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.*;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ModerationModule extends VupeModule {
    private final Map<UUID, Integer> captchaAnswers = new HashMap<>();
    private final Map<UUID, Long> captchaDeadlines = new HashMap<>();
    private boolean globalChatMuted;

    public ModerationModule(VupeCore plugin) {
        super(plugin, "moderation");
    }

    @Override
    protected void onEnable() {
        globalChatMuted = plugin.configs().get("moderation").getBoolean("moderation.chat.muted", false);
        plugin.commands().register("punish", this::punishCommand);
        plugin.commands().register("report", this::reportCommand);
        plugin.commands().register("staffchat", this::staffChatCommand);
        plugin.commands().register("vanish", this::vanishCommand);
        plugin.commands().register("mute", (s,l,a) -> directPunish(s, "mute", a));
        plugin.commands().register("unmute", (s,l,a) -> directPunish(s, "unmute", a));
        plugin.commands().register("ban", (s,l,a) -> directPunish(s, "ban", a));
        plugin.commands().register("unban", (s,l,a) -> directPunish(s, "unban", a));
        plugin.commands().register("kick", (s,l,a) -> directPunish(s, "kick", a));
        plugin.commands().register("punishments", this::punishmentsCompatibility);
        plugin.commands().register("reports", (s,l,a) -> punishCommand(s, l, new String[]{"reports"}));
        plugin.commands().register("mutechat", (s,l,a) -> punishCommand(s, l, new String[]{"chatmute"}));
        plugin.commands().register("broadcast", this::broadcastCommand);
        plugin.commands().register("clearchat", this::clearChatCommand);
    }

    public boolean chatMuted() {
        return globalChatMuted;
    }

    public boolean isMuted(UUID uuid) {
        long now = System.currentTimeMillis();
        synchronized (plugin.data().server().punishments) {
            for (ServerData.PunishmentRecord record : plugin.data().server().punishments.values()) {
                if (!record.active || !record.target.equals(uuid.toString()) || !record.type.equals("MUTE")) continue;
                if (record.expiresAt > 0 && record.expiresAt <= now) {
                    record.active = false;
                    plugin.data().markServerDirty();
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    public boolean consumeCaptchaMessage(Player player, String plain) {
        Integer answer = captchaAnswers.get(player.getUniqueId());
        if (answer == null) return false;

        if (captchaDeadlines.getOrDefault(player.getUniqueId(), 0L) < System.currentTimeMillis()) {
            captchaAnswers.remove(player.getUniqueId());
            captchaDeadlines.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(Text.component("<red>Captcha timed out. Rejoin to try again.")));
            return true;
        }

        try {
            if (Integer.parseInt(plain.trim()) == answer) {
                captchaAnswers.remove(player.getUniqueId());
                captchaDeadlines.remove(player.getUniqueId());
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.captchaVerified = true;
                plugin.data().markDirty(player.getUniqueId());
                Text.send(player, "<green>Captcha passed. Welcome to Vupe.");
            } else {
                Text.send(player, "<red>Wrong captcha answer. Try again.");
            }
        } catch (NumberFormatException ex) {
            Text.send(player, "<red>Type only the number answer in chat.");
        }
        return true;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String ban = activeBanMessage(uuid);
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Text.component(ban));
            return;
        }

        if (!plugin.configs().modules().getBoolean("modules.anti-alt", true)
            || !plugin.configs().get("moderation").getBoolean("moderation.anti-alt.enabled", true)) return;

        String ipHash = hash(event.getAddress());
        int max = plugin.configs().get("moderation").getInt("moderation.anti-alt.max-accounts-per-ip", 3);
        synchronized (plugin.data().server().ipAccounts) {
            Set<String> accounts = plugin.data().server().ipAccounts.getOrDefault(ipHash, Collections.emptySet());
            if (!accounts.contains(uuid.toString()) && accounts.size() >= max) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Text.component("<red>Too many accounts have joined from this connection."));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (player.getAddress() != null) {
            String ipHash = hash(player.getAddress().getAddress());
            data.lastIpHash = ipHash;
            synchronized (plugin.data().server().ipAccounts) {
                plugin.data().server().ipAccounts
                    .computeIfAbsent(ipHash, ignored -> new HashSet<>())
                    .add(player.getUniqueId().toString());
            }
            plugin.data().markDirty(player.getUniqueId());
            plugin.data().markServerDirty();
        }

        boolean captcha = plugin.configs().modules().getBoolean("modules.captcha", true)
            && plugin.configs().get("moderation").getBoolean("moderation.captcha.enabled", true);
        boolean onlyNew = plugin.configs().get("moderation").getBoolean("moderation.captcha.only-new-players", true);
        if (captcha && !data.captchaVerified && (!onlyNew || !player.hasPlayedBefore())) {
            int a = 2 + new Random().nextInt(8);
            int b = 2 + new Random().nextInt(8);
            captchaAnswers.put(player.getUniqueId(), a + b);
            long timeout = Math.max(30, plugin.configs().get("moderation").getLong("moderation.captcha.timeout-seconds", 120));
            captchaDeadlines.put(player.getUniqueId(), System.currentTimeMillis() + timeout * 1000L);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                Text.send(player, "<yellow>Captcha: type <white>" + a + " + " + b + " <yellow>in chat."), 20L);
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) continue;
            if (plugin.data().player(viewer.getUniqueId()).vanished && !player.hasPermission("vupe.staff")) {
                player.hidePlayer(plugin, viewer);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        captchaAnswers.remove(event.getPlayer().getUniqueId());
        captchaDeadlines.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHiddenCommands(PlayerCommandPreprocessEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.plugin-command-hiding", false)) return;
        if (event.getPlayer().hasPermission("vupe.admin")) return;
        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (List.of("plugins", "pl", "version", "ver", "about", "?").contains(cmd)) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<gray>This server runs <gradient:#8B5CF6:#22D3EE><bold>VupeCore</bold></gradient><gray>.");
        }
    }

    private boolean directPunish(CommandSender sender, String action, String[] args) {
        List<String> forwarded = new ArrayList<>();
        forwarded.add(action);
        forwarded.addAll(Arrays.asList(args));
        return punishCommand(sender, action, forwarded.toArray(String[]::new));
    }

    private boolean punishmentsCompatibility(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /punishments <player>");
            return true;
        }
        return punishCommand(sender, label, new String[]{"history", args[0]});
    }

    private boolean broadcastCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /broadcast <message>");
            return true;
        }
        String safe = String.join(" ", args).replace("<", "\\<").replace(">", "\\>");
        plugin.effects().broadcast("<gradient:#8B5CF6:#22D3EE><bold>VUPE</bold></gradient> <dark_gray>» <white>" + safe, "broadcast");
        return true;
    }

    private boolean clearChatCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 80; i++) player.sendMessage(Component.empty());
            Text.send(player, "<gray>Chat was cleared by <white>" + sender.getName() + "<gray>.");
        }
        return true;
    }

    private boolean punishCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length == 0) {
            Text.send(sender, "<gray>/punish <mute|unmute|ban|unban|kick|history|reports|chatmute> ...");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "chatmute" -> {
                globalChatMuted = !globalChatMuted;
                Text.send(sender, "<gray>Global chat: " + (globalChatMuted ? "<red>muted" : "<green>unmuted"));
            }
            case "kick" -> {
                if (args.length < 2) { Text.send(sender, "<red>/punish kick <player> [reason]"); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Text.send(sender, "<red>Player not found."); break; }
                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Removed by staff";
                target.kick(Text.component("<red>" + reason));
            }
            case "mute", "ban" -> {
                if (args.length < 3) { Text.send(sender, "<red>/punish " + args[0] + " <player> <duration|permanent> [reason]"); break; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                long duration = TimeUtil.parseMillis(args[2]);
                String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason provided";
                createPunishment(sender, target, args[0].toUpperCase(Locale.ROOT), reason, duration);
                if (args[0].equalsIgnoreCase("ban")) {
                    Player online = target.getPlayer();
                    if (online != null) online.kick(Text.component("<red>Banned: " + reason));
                }
            }
            case "unmute", "unban" -> {
                if (args.length < 2) { Text.send(sender, "<red>/punish " + args[0] + " <player>"); break; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String type = args[0].equalsIgnoreCase("unmute") ? "MUTE" : "BAN";
                int count = deactivate(target.getUniqueId(), type);
                Text.send(sender, "<green>Deactivated " + count + " " + type.toLowerCase(Locale.ROOT) + " punishment(s).");
            }
            case "history" -> {
                if (args.length < 2) { Text.send(sender, "<red>/punish history <player>"); break; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Text.raw(sender, "<#8B5CF6><bold>PUNISHMENT HISTORY</bold>");
                plugin.data().server().punishments.values().stream()
                    .filter(r -> r.target.equals(target.getUniqueId().toString()))
                    .sorted(Comparator.comparingLong((ServerData.PunishmentRecord r) -> r.createdAt).reversed())
                    .limit(20)
                    .forEach(r -> Text.raw(sender, " <dark_gray>• <white>" + r.type + " <gray>" + r.reason
                        + " <dark_gray>[" + (r.active ? "<red>active" : "<green>expired") + "<dark_gray>]"));
            }
            case "reports" -> {
                Text.raw(sender, "<#F472B6><bold>OPEN REPORTS</bold>");
                plugin.data().server().reports.values().stream()
                    .filter(r -> r.status.equals("OPEN"))
                    .sorted(Comparator.comparingLong((ServerData.ReportRecord r) -> r.createdAt))
                    .limit(30)
                    .forEach(r -> Text.raw(sender, " <dark_gray>• <white>" + r.id + " <gray>target " + r.target + ": " + r.reason));
            }
            case "closereport" -> {
                if (args.length < 2) break;
                ServerData.ReportRecord report = plugin.data().server().reports.get(args[1]);
                if (report != null) {
                    report.status = "CLOSED";
                    report.handledBy = sender.getName();
                    plugin.data().markServerDirty();
                }
            }
            default -> Text.send(sender, "<red>Unknown punishment action.");
        }
        return true;
    }

    private boolean reportCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /report <player> <reason>");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        long ready = data.cooldowns.getOrDefault("report", 0L);
        if (ready > System.currentTimeMillis()) {
            Text.send(player, "<red>You can file another report in <white>"
                + TimeUtil.pretty(ready - System.currentTimeMillis()) + "<red>.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Text.send(player, "<red>You cannot report yourself.");
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ServerData.ReportRecord report = new ServerData.ReportRecord();
        report.id = UUID.randomUUID().toString().substring(0, 8);
        report.reporter = player.getUniqueId().toString();
        report.target = target.getUniqueId().toString();
        report.reason = reason;
        report.createdAt = System.currentTimeMillis();
        plugin.data().server().reports.put(report.id, report);
        plugin.data().markServerDirty();

        long cooldown = Math.max(10, plugin.configs().get("moderation").getLong("moderation.reports.cooldown-seconds", 60));
        data.cooldowns.put("report", System.currentTimeMillis() + cooldown * 1000L);
        plugin.data().markDirty(player.getUniqueId());

        Text.send(player, "<green>Report submitted. ID: <white>" + report.id);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("vupe.staff")) {
                Text.send(staff, "<#F472B6>New report <white>" + report.id + "<gray>: <white>" + player.getName()
                    + " <gray>reported <white>" + (target.getName() == null ? target.getUniqueId() : target.getName())
                    + " <gray>for <white>" + reason);
            }
        }
        return true;
    }

    private boolean staffChatCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length == 0) {
            Text.send(player, "<red>Usage: /staffchat <message>");
            return true;
        }
        String message = String.join(" ", args);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.hasPermission("vupe.staff")) {
                Text.raw(target, "<dark_gray>[<#34D399>STAFF<dark_gray>] <white>" + player.getName()
                    + " <dark_gray>» <gray>" + message.replace("<", "\\<").replace(">", "\\>"));
            }
        }
        return true;
    }

    private boolean vanishCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.vanished = !data.vanished;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player) || viewer.hasPermission("vupe.staff")) continue;
            if (data.vanished) viewer.hidePlayer(plugin, player);
            else viewer.showPlayer(plugin, player);
        }
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<gray>Vanish: " + (data.vanished ? "<green>enabled" : "<red>disabled"));
        return true;
    }

    private void createPunishment(CommandSender actor, OfflinePlayer target, String type, String reason, long duration) {
        ServerData.PunishmentRecord record = new ServerData.PunishmentRecord();
        record.id = UUID.randomUUID().toString();
        record.target = target.getUniqueId().toString();
        record.actor = actor.getName();
        record.type = type;
        record.reason = reason;
        record.createdAt = System.currentTimeMillis();
        record.expiresAt = duration <= 0 ? 0 : record.createdAt + duration;
        record.active = true;
        plugin.data().server().punishments.put(record.id, record);
        plugin.data().markServerDirty();
        Text.send(actor, "<green>" + type + " applied to <white>" + (target.getName() == null ? target.getUniqueId() : target.getName()) + "<green>.");
    }

    private int deactivate(UUID target, String type) {
        int count = 0;
        for (ServerData.PunishmentRecord record : plugin.data().server().punishments.values()) {
            if (record.active && record.target.equals(target.toString()) && record.type.equals(type)) {
                record.active = false;
                count++;
            }
        }
        if (count > 0) plugin.data().markServerDirty();
        return count;
    }

    private String activeBanMessage(UUID uuid) {
        long now = System.currentTimeMillis();
        synchronized (plugin.data().server().punishments) {
            for (ServerData.PunishmentRecord record : plugin.data().server().punishments.values()) {
                if (!record.active || !record.target.equals(uuid.toString()) || !record.type.equals("BAN")) continue;
                if (record.expiresAt > 0 && record.expiresAt <= now) {
                    record.active = false;
                    plugin.data().markServerDirty();
                    continue;
                }
                String expiry = record.expiresAt == 0 ? "Permanent" : TimeUtil.pretty(record.expiresAt - now);
                return "<red>You are banned from Vupe.\n<gray>Reason: <white>" + record.reason + "\n<gray>Remaining: <white>" + expiry;
            }
        }
        return null;
    }

    private String hash(InetAddress address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(address.getHostAddress().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return address.getHostAddress();
        }
    }
}

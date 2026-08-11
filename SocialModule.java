package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import dev.vupe.core.util.Items;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class SocialModule extends VupeModule {
    private BukkitTask afkTask;
    private final Map<UUID, UUID> lastMessagePartner = new HashMap<>();
    private final Map<UUID, String> socialGuiSessions = new HashMap<>();

    public SocialModule(VupeCore plugin) {
        super(plugin, "social");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("team", this::teamCommand);
        plugin.commands().register("afk", this::afkCommand);
        plugin.commands().register("playtime", this::playtimeCommand);
        plugin.commands().register("options", this::optionsCommand);
        plugin.commands().register("tags", this::tagsCommand);
        plugin.commands().register("chatcolor", this::chatColorCommand);
        plugin.commands().register("guide", this::guideCommand);
        plugin.commands().register("rules", this::rulesCommand);
        plugin.commands().register("ads", this::adsCommand);
        plugin.commands().register("msg", this::messageCommand);
        plugin.commands().register("reply", this::replyCommand);
        plugin.commands().register("ignore", this::ignoreCommand);
        plugin.commands().register("socialspy", this::socialSpyCommand);
        plugin.commands().register("chatcooldown", this::chatCooldownCommand);

        afkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAfk, 1200L, 1200L);
    }

    @Override
    protected void onDisable() {
        if (afkTask != null) afkTask.cancel();
        afkTask = null;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.chat", true)) return;
        Player player = event.getPlayer();

        if (plugin.modules().moderation().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            Text.send(player, "<red>You are muted.");
            return;
        }

        if (plugin.modules().moderation().chatMuted() && !player.hasPermission("vupe.staff")) {
            event.setCancelled(true);
            Text.send(player, "<red>Public chat is currently muted.");
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.modules().moderation().consumeCaptchaMessage(player, plain)) {
            event.setCancelled(true);
            return;
        }

        int chatCooldown = Math.max(0, plugin.data().server().globalChatCooldownSeconds);
        if (chatCooldown > 0 && !player.hasPermission("vupe.staff")) {
            PlayerData cooldownData = plugin.data().player(player.getUniqueId());
            long readyAt = cooldownData.cooldowns.getOrDefault("public-chat", 0L);
            if (readyAt > System.currentTimeMillis()) {
                event.setCancelled(true);
                long remaining = Math.max(1, (readyAt - System.currentTimeMillis() + 999L) / 1000L);
                Text.send(player, "<red>Please wait <white>" + remaining + "s <red>before chatting again.");
                return;
            }
            cooldownData.cooldowns.put("public-chat", System.currentTimeMillis() + chatCooldown * 1000L);
            plugin.data().markDirty(player.getUniqueId());
        }

        event.setCancelled(true);
        touch(player);

        String format = plugin.configs().get("social").getString(
            "chat.format",
            "%donor_prefix%%progression_prefix%<white>%player% <dark_gray>» <gray>%message%"
        );

        PlayerData data = plugin.data().player(player.getUniqueId());
        String displayName = data.nickname == null || data.nickname.isBlank() ? player.getName() : data.nickname;
        String tag = "";
        if (data.activeTag != null && !data.activeTag.isBlank()) {
            tag = plugin.configs().get("social").getString("tags." + data.activeTag + ".display", "");
            if (!tag.isBlank()) tag += " ";
        }

        String rendered = format
            .replace("%donor_prefix%", plugin.modules().progression().donorPrefix(player))
            .replace("%progression_prefix%", plugin.modules().progression().progressionPrefix(player))
            .replace("%player%", displayName)
            .replace("%message%", chatColorPrefix(data) + escapeMini(plain) + chatColorSuffix(data));
        if (!tag.isBlank()) rendered = tag + rendered;

        String finalRendered = rendered;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcast(Text.component(finalRendered));
            if (plugin.configs().modules().getBoolean("modules.discord", false)) {
                plugin.modules().discord().relayMinecraftChat(player, plain);
            }
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(player)) continue;
                if (!plugin.data().player(target.getUniqueId()).options.getOrDefault("mentions", true)) continue;
                if (plain.toLowerCase(Locale.ROOT).contains(target.getName().toLowerCase(Locale.ROOT))) {
                    target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
                }
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            touch(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer());
    }

    private void touch(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.lastActiveEpoch = System.currentTimeMillis();
        if (data.afk) {
            data.afk = false;
            if (plugin.configs().get("social").getBoolean("afk.announce", true)) {
                Bukkit.broadcast(Text.component(Text.prefix() + "<white>" + player.getName() + " <gray>is no longer AFK."));
            }
        }
        plugin.data().markDirty(player.getUniqueId());
    }

    private void checkAfk() {
        if (!plugin.configs().modules().getBoolean("modules.afk", true)) return;
        long after = Math.max(1, plugin.configs().get("social").getLong("afk.auto-after-minutes", 8)) * 60_000L;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().player(player.getUniqueId());
            if (!data.afk && now - data.lastActiveEpoch >= after) {
                data.afk = true;
                plugin.data().markDirty(player.getUniqueId());
                if (plugin.configs().get("social").getBoolean("afk.announce", true)) {
                    Bukkit.broadcast(Text.component(Text.prefix() + "<white>" + player.getName() + " <gray>is now AFK."));
                }
            }
        }
    }

    private boolean afkCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.afk", true)) { Text.send(sender, "<red>AFK is disabled."); return true; }
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.afk = !data.afk;
        data.lastActiveEpoch = System.currentTimeMillis();
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<gray>AFK: " + (data.afk ? "<green>enabled" : "<red>disabled"));
        return true;
    }

    private boolean playtimeCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        long total = data.playtimeSeconds;
        if (data.lastJoinEpoch > 0) total += Math.max(0, (System.currentTimeMillis() - data.lastJoinEpoch) / 1000L);
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long minutes = (total % 3600) / 60;
        Text.send(player, "<gray>Playtime: <white>" + days + "d " + hours + "h " + minutes + "m");
        return true;
    }

    private boolean teamCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.teams", true)) { Text.send(sender, "<red>Teams is disabled."); return true; }
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());

        if (args.length == 0) {
            ServerData.TeamRecord team = plugin.data().server().teams.get(data.teamId);
            if (team == null) {
                Text.send(player, "<gray>You are not in a team. <white>/team create <name>");
            } else {
                Text.raw(player, "<#8B5CF6><bold>" + team.name + "</bold>");
                Text.raw(player, "<gray>Members: <white>" + team.members.size());
                Text.raw(player, "<gray>Owner: <white>" + Bukkit.getOfflinePlayer(UUID.fromString(team.owner)).getName());
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!data.teamId.isBlank()) { Text.send(player, "<red>You are already in a team."); break; }
                if (args.length < 2) { Text.send(player, "<red>/team create <name>"); break; }
                String name = args[1].replaceAll("[^A-Za-z0-9_-]", "");
                if (name.length() < 2 || name.length() > 16) { Text.send(player, "<red>Team name must be 2-16 safe characters."); break; }
                String id = name.toLowerCase(Locale.ROOT);
                if (plugin.data().server().teams.containsKey(id)) { Text.send(player, "<red>That team already exists."); break; }
                ServerData.TeamRecord team = new ServerData.TeamRecord();
                team.id = id;
                team.name = name;
                team.owner = player.getUniqueId().toString();
                team.members.add(team.owner);
                team.createdAt = System.currentTimeMillis();
                plugin.data().server().teams.put(id, team);
                data.teamId = id;
                plugin.data().markDirty(player.getUniqueId());
                plugin.data().markServerDirty();
                Text.send(player, "<green>Created team <white>" + name + "<green>.");
            }
            case "invite" -> {
                ServerData.TeamRecord team = ownedTeam(player);
                if (team == null) break;
                if (args.length < 2) { Text.send(player, "<red>/team invite <player>"); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Text.send(player, "<red>Player not found."); break; }
                team.invites.add(target.getUniqueId().toString());
                plugin.data().markServerDirty();
                Text.send(target, "<gray>You were invited to <white>" + team.name + "<gray>. Use <white>/team join " + team.id);
            }
            case "join" -> {
                if (!data.teamId.isBlank()) { Text.send(player, "<red>Leave your current team first."); break; }
                if (args.length < 2) { Text.send(player, "<red>/team join <name>"); break; }
                ServerData.TeamRecord team = plugin.data().server().teams.get(args[1].toLowerCase(Locale.ROOT));
                if (team == null || !team.invites.remove(player.getUniqueId().toString())) {
                    Text.send(player, "<red>You do not have an invite to that team.");
                    break;
                }
                team.members.add(player.getUniqueId().toString());
                data.teamId = team.id;
                plugin.data().markDirty(player.getUniqueId());
                plugin.data().markServerDirty();
                Text.send(player, "<green>Joined <white>" + team.name + "<green>.");
            }
            case "leave" -> leaveTeam(player);
            case "kick" -> {
                ServerData.TeamRecord team = ownedTeam(player);
                if (team == null) break;
                if (args.length < 2) { Text.send(player, "<red>/team kick <player>"); break; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!team.members.remove(target.getUniqueId().toString())) { Text.send(player, "<red>That player is not in your team."); break; }
                plugin.data().player(target.getUniqueId()).teamId = "";
                plugin.data().markDirty(target.getUniqueId());
                plugin.data().markServerDirty();
            }
            case "disband" -> {
                ServerData.TeamRecord team = ownedTeam(player);
                if (team == null) break;
                for (String member : team.members) {
                    UUID uuid = UUID.fromString(member);
                    plugin.data().player(uuid).teamId = "";
                    plugin.data().markDirty(uuid);
                }
                plugin.data().server().teams.remove(team.id);
                plugin.data().markServerDirty();
                Text.send(player, "<green>Team disbanded.");
            }
            case "chat" -> {
                if (args.length < 2) { Text.send(player, "<red>/team chat <message>"); break; }
                ServerData.TeamRecord team = plugin.data().server().teams.get(data.teamId);
                if (team == null) break;
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                for (String member : team.members) {
                    Player target = Bukkit.getPlayer(UUID.fromString(member));
                    if (target != null) Text.raw(target, "<dark_gray>[<#8B5CF6>TEAM<dark_gray>] <white>" + player.getName() + " <dark_gray>» <gray>" + escapeMini(message));
                }
            }
            default -> Text.send(player, "<gray>/team <create|invite|join|leave|kick|disband|chat>");
        }
        return true;
    }

    private ServerData.TeamRecord ownedTeam(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        ServerData.TeamRecord team = plugin.data().server().teams.get(data.teamId);
        if (team == null || !team.owner.equals(player.getUniqueId().toString())) {
            Text.send(player, "<red>You are not the team owner.");
            return null;
        }
        return team;
    }

    private void leaveTeam(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        ServerData.TeamRecord team = plugin.data().server().teams.get(data.teamId);
        if (team == null) {
            data.teamId = "";
            plugin.data().markDirty(player.getUniqueId());
            return;
        }
        if (team.owner.equals(player.getUniqueId().toString())) {
            Text.send(player, "<red>The owner must disband the team.");
            return;
        }
        team.members.remove(player.getUniqueId().toString());
        data.teamId = "";
        plugin.data().markDirty(player.getUniqueId());
        plugin.data().markServerDirty();
        Text.send(player, "<green>You left the team.");
    }

    private boolean messageCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /msg <player> <message>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            Text.send(player, "<red>That player is unavailable.");
            return true;
        }
        return sendPrivateMessage(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    private boolean replyCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /reply <message>");
            return true;
        }
        UUID partnerId = lastMessagePartner.get(player.getUniqueId());
        Player target = partnerId == null ? null : Bukkit.getPlayer(partnerId);
        if (target == null) {
            Text.send(player, "<red>You have nobody online to reply to.");
            return true;
        }
        return sendPrivateMessage(player, target, String.join(" ", args));
    }

    private boolean ignoreCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /ignore <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Text.send(player, "<red>You cannot ignore yourself.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        String targetId = target.getUniqueId().toString();
        if (!data.ignoredPlayers.add(targetId)) {
            data.ignoredPlayers.remove(targetId);
            Text.send(player, "<gray>You no longer ignore <white>" + args[0] + "<gray>.");
        } else {
            Text.send(player, "<gray>You now ignore <white>" + args[0] + "<gray>.");
        }
        plugin.data().markDirty(player.getUniqueId());
        return true;
    }

    private boolean socialSpyCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.socialSpy = !data.socialSpy;
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<gray>Social spy: " + (data.socialSpy ? "<green>enabled" : "<red>disabled"));
        return true;
    }

    private boolean chatCooldownCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<gray>Current public chat cooldown: <white>" + plugin.data().server().globalChatCooldownSeconds + " seconds<gray>.");
            return true;
        }
        int seconds;
        try { seconds = Math.max(0, Math.min(300, Integer.parseInt(args[0]))); }
        catch (NumberFormatException ex) { Text.send(sender, "<red>Use a number of seconds."); return true; }
        plugin.data().server().globalChatCooldownSeconds = seconds;
        plugin.data().markServerDirty();
        Text.send(sender, "<green>Global chat cooldown set to <white>" + seconds + " seconds<green>.");
        return true;
    }

    private boolean sendPrivateMessage(Player sender, Player target, String message) {
        PlayerData targetData = plugin.data().player(target.getUniqueId());
        if (targetData.ignoredPlayers.contains(sender.getUniqueId().toString()) && !sender.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>That player is ignoring your private messages.");
            return true;
        }

        String safe = escapeMini(message);
        Text.raw(sender, "<dark_gray>[<#22D3EE>YOU <gray>→ <white>" + target.getName() + "<dark_gray>] <gray>" + safe);
        Text.raw(target, "<dark_gray>[<#22D3EE>" + sender.getName() + " <gray>→ <white>YOU<dark_gray>] <gray>" + safe);

        lastMessagePartner.put(sender.getUniqueId(), target.getUniqueId());
        lastMessagePartner.put(target.getUniqueId(), sender.getUniqueId());

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.equals(sender) || staff.equals(target)) continue;
            if (!plugin.data().player(staff.getUniqueId()).socialSpy) continue;
            Text.raw(staff, "<dark_gray>[<#34D399>SPY<dark_gray>] <white>" + sender.getName()
                + " <gray>→ <white>" + target.getName() + "<dark_gray>: <gray>" + safe);
        }
        return true;
    }

    private boolean optionsCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) {
            PlayerData data = plugin.data().player(player.getUniqueId());
            Text.raw(player, "<#8B5CF6><bold>PLAYER OPTIONS</bold>");
            for (String id : List.of("mentions", "scoreboard", "ads")) {
                Text.raw(player, " <dark_gray>• <gray>" + id + ": " + (data.options.getOrDefault(id, true) ? "<green>on" : "<red>off"));
            }
            Text.raw(player, "<gray>Use <white>/options <mentions|scoreboard|ads><gray>.");
            return true;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("mentions", "scoreboard", "ads").contains(id)) {
            Text.send(player, "<red>Unknown option.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.options.put(id, !data.options.getOrDefault(id, true));
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<gray>" + id + ": " + (data.options.get(id) ? "<green>on" : "<red>off"));
        return true;
    }

    private boolean tagsCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.tags", true)) { Text.send(sender, "<red>Tags is disabled."); return true; }
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        var tags = plugin.configs().get("social").getConfigurationSection("tags");
        if (tags == null) return true;

        if (args.length == 0) {
            openTagsGui(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            data.activeTag = "";
            plugin.data().markDirty(player.getUniqueId());
            Text.send(player, "<gray>Tag disabled.");
            return true;
        }

        String id = args[0].toLowerCase(Locale.ROOT);
        if (!tags.contains(id)) { Text.send(player, "<red>Unknown tag."); return true; }
        String permission = tags.getString(id + ".permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission) && !data.tags.contains(id)) {
            Text.send(player, "<red>You do not own that tag.");
            return true;
        }
        data.activeTag = id;
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Tag selected.");
        return true;
    }

    private boolean chatColorCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            openChatColorGui(player);
            return true;
        }
        selectChatColor(player, args[0].toLowerCase(Locale.ROOT));
        return true;
    }

    private void openTagsGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#F472B6:#8B5CF6><bold>VUPE TAGS</bold></gradient>"));
        decorateSocial(inv);
        var tags = plugin.configs().get("social").getConfigurationSection("tags");
        PlayerData data = plugin.data().player(player.getUniqueId());
        int[] slots = socialSlots();
        int i = 0;
        if (tags != null) {
            for (String id : tags.getKeys(false)) {
                if (i >= slots.length) break;
                String permission = tags.getString(id + ".permission", "");
                boolean owned = permission.isBlank() || player.hasPermission(permission) || data.tags.contains(id);
                List<String> lore = new ArrayList<>();
                lore.add(owned ? "<green>✓ Owned" : "<red>✗ Locked");
                lore.add(data.activeTag.equalsIgnoreCase(id) ? "<#22D3EE>◆ Currently equipped" : "");
                lore.add("");
                lore.add(owned ? "<yellow>Click to equip." : "<gray>Unlock from events/store/progression.");
                inv.setItem(slots[i++], Items.tagged(
                    owned ? Material.NAME_TAG : Material.GRAY_DYE,
                    tags.getString(id + ".display", id),
                    lore, "social_action", owned ? "tag:" + id : "noop"));
            }
        }
        inv.setItem(49, Items.tagged(Material.BARRIER, "<red><bold>DISABLE TAG</bold>",
            List.of("<gray>Remove your active chat tag."), "social_action", "tag:off"));
        socialGuiSessions.put(player.getUniqueId(), "tags");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    private void openChatColorGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45,
            Text.component("<gradient:#22D3EE:#F472B6><bold>CHAT COLORS</bold></gradient>"));
        decorateSocial(inv);
        var colors = plugin.configs().get("social").getConfigurationSection("chat-colors");
        PlayerData data = plugin.data().player(player.getUniqueId());
        int[] slots = socialSlots();
        int i = 0;
        if (colors != null) {
            for (String id : colors.getKeys(false)) {
                if (i >= slots.length) break;
                String perm = colors.getString(id + ".permission", "");
                boolean allowed = perm.isBlank() || player.hasPermission(perm);
                List<String> lore = List.of(
                    data.chatColor.equalsIgnoreCase(id) ? "<#22D3EE>◆ Currently selected" : allowed ? "<green>✓ Available" : "<red>✗ Locked",
                    "",
                    allowed ? "<yellow>Click to select." : "<gray>Requires: " + perm
                );
                inv.setItem(slots[i++], Items.tagged(allowed ? Material.INK_SAC : Material.GRAY_DYE,
                    colors.getString(id + ".display", id), lore, "social_action", allowed ? "color:" + id : "noop"));
            }
        }
        socialGuiSessions.put(player.getUniqueId(), "colors");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    private void selectChatColor(Player player, String id) {
        var colors = plugin.configs().get("social").getConfigurationSection("chat-colors");
        if (colors == null || !colors.contains(id)) {
            Text.send(player, "<red>Unknown chat color.");
            plugin.effects().error(player);
            return;
        }
        String perm = colors.getString(id + ".permission", "");
        if (!perm.isBlank() && !player.hasPermission(perm)) {
            Text.send(player, "<red>You do not have access to that chat color.");
            plugin.effects().error(player);
            return;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.chatColor = id;
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Chat color changed to " + colors.getString(id + ".display", id) + "<green>.");
        plugin.effects().success(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSocialGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !socialGuiSessions.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        String action = Items.tag(event.getCurrentItem(), "social_action");
        if (action == null || action.equals("noop")) return;
        plugin.effects().click(player);
        if (action.startsWith("tag:")) {
            String id = action.substring(4);
            player.closeInventory();
            tagsCommand(player, "tags", new String[]{id});
        } else if (action.startsWith("color:")) {
            selectChatColor(player, action.substring(6));
            openChatColorGui(player);
        }
    }

    @EventHandler
    public void onSocialGuiClose(InventoryCloseEvent event) {
        socialGuiSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSocialGuiDrag(InventoryDragEvent event) {
        if (socialGuiSessions.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private String chatColorPrefix(PlayerData data) {
        return plugin.configs().get("social").getString("chat-colors." + data.chatColor + ".format", "<gray>");
    }

    private String chatColorSuffix(PlayerData data) {
        return plugin.configs().get("social").getString("chat-colors." + data.chatColor + ".suffix", "");
    }

    private static void decorateSocial(Inventory inv) {
        ItemStack filler = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i=0;i<inv.getSize();i++) {
            int row=i/9,col=i%9;
            if(row==0||row==inv.getSize()/9-1||col==0||col==8) inv.setItem(i,filler);
        }
    }

    private static int[] socialSlots() {
        List<Integer> slots = new ArrayList<>();
        int rows = 6;
        for (int r=1;r<rows-1;r++) for(int c=1;c<=7;c++) slots.add(r*9+c);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean guideCommand(CommandSender sender, String label, String[] args) {
        for (String line : plugin.configs().get("social").getStringList("guide")) Text.raw(sender, line);
        return true;
    }

    private boolean rulesCommand(CommandSender sender, String label, String[] args) {
        for (String line : plugin.configs().get("social").getStringList("rules")) Text.raw(sender, line);
        return true;
    }

    private boolean adsCommand(CommandSender sender, String label, String[] args) {
        Text.send(sender, "<gray>Automatic Vupe tips are configured in <white>events.yml<gray>. Toggle them with <white>/options ads<gray>.");
        return true;
    }

    private static String escapeMini(String text) {
        return text.replace("\\", "\\\\")
            .replace("<", "\\<")
            .replace(">", "\\>");
    }
}

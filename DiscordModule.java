package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordModule extends VupeModule {
    private JDA jda;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();

    private record LinkCode(UUID player, long expiresAt) {}

    public DiscordModule(VupeCore plugin) {
        super(plugin, "discord");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("discord", this::command);

        if (!plugin.configs().get("discord").getBoolean("discord.enabled", false)) {
            plugin.getLogger().info("Discord module code is enabled but discord.yml has discord.enabled=false; JDA will not start.");
            return;
        }

        String token = plugin.configs().get("discord").getString("discord.token", "");
        if (token == null || token.isBlank() || token.contains("CHANGE_ME")) {
            plugin.getLogger().warning("Discord is enabled but no real token is configured. JDA was not started.");
            return;
        }

        try {
            jda = JDABuilder.createLight(token,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS)
                .addEventListeners(new DiscordListener())
                .setActivity(Activity.playing("Vupe"))
                .build();
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not start Discord integration: " + ex.getMessage());
            jda = null;
        }
    }

    @Override
    protected void onDisable() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
        linkCodes.clear();
    }

    public void relayMinecraftChat(Player player, String message) {
        if (jda == null || !feature("chat-relay")) return;
        TextChannel channel = channel("minecraft-chat");
        if (channel == null) return;
        String safe = message.replace("@everyone", "@ everyone").replace("@here", "@ here");
        channel.sendMessage("**" + player.getName().replace("*", "") + "** » " + safe).queue(
            ignored -> {},
            error -> plugin.getLogger().warning("Discord chat relay failed: " + error.getMessage())
        );
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }

        if (jda == null) {
            Text.send(player, "<gray>Discord integration is currently disabled. <white>"
                + plugin.configs().get("branding").getString("brand.discord", "CHANGE_ME_DISCORD_URL"));
            return true;
        }

        if (args.length == 0) {
            Text.send(player, "<gray>Discord: <white>"
                + plugin.configs().get("branding").getString("brand.discord", "CHANGE_ME_DISCORD_URL"));
            Text.send(player, "<gray>Commands: <white>/discord link<gray>, <white>/discord unlink<gray>, <white>/discord suggest <text><gray>, <white>/discord ticket <reason>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link" -> createLink(player);
            case "unlink" -> {
                PlayerData data = plugin.data().player(player.getUniqueId());
                data.discordId = "";
                plugin.data().markDirty(player.getUniqueId());
                Text.send(player, "<green>Your Discord link was removed.");
            }
            case "suggest" -> {
                if (!feature("suggestions")) { Text.send(player, "<red>Suggestions are disabled."); break; }
                if (args.length < 2) { Text.send(player, "<red>/discord suggest <suggestion>"); break; }
                TextChannel channel = channel("suggestions");
                if (channel == null) { Text.send(player, "<red>Suggestion channel is not configured."); break; }
                String suggestion = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                channel.sendMessage("💡 **Suggestion from " + player.getName() + "**\n" + suggestion).queue();
                Text.send(player, "<green>Suggestion sent.");
            }
            case "ticket" -> {
                if (!feature("tickets")) { Text.send(player, "<red>Tickets are disabled."); break; }
                String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason provided";
                createTicket(player, reason);
            }
            default -> Text.send(player, "<red>Unknown Discord action.");
        }
        return true;
    }

    private void createLink(Player player) {
        if (!feature("account-linking")) {
            Text.send(player, "<red>Account linking is disabled.");
            return;
        }
        linkCodes.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis());
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (linkCodes.containsKey(code));
        linkCodes.put(code, new LinkCode(player.getUniqueId(), System.currentTimeMillis() + 10 * 60_000L));
        Text.send(player, "<gray>In the Vupe Discord, send <white>!link " + code + "<gray>. The code expires in 10 minutes.");
    }

    private void createTicket(Player player, String reason) {
        Guild guild = guild();
        if (guild == null) {
            Text.send(player, "<red>Discord guild is not configured.");
            return;
        }
        String categoryId = plugin.configs().get("discord").getString("discord.channels.tickets-category", "");
        Category category = categoryId == null || categoryId.contains("CHANGE_ME") ? null : guild.getCategoryById(categoryId);
        if (category == null) {
            Text.send(player, "<red>Ticket category is not configured.");
            return;
        }

        String name = "ticket-" + player.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        category.createTextChannel(name).queue(channel -> {
            channel.sendMessage("**Minecraft user:** " + player.getName() + "\n**Reason:** " + reason).queue();
            Text.send(player, "<green>Discord ticket created: <white>#" + channel.getName());
        }, error -> Text.send(player, "<red>Could not create the Discord ticket."));
    }

    private boolean feature(String key) {
        return plugin.configs().get("discord").getBoolean("discord.features." + key, true);
    }

    private Guild guild() {
        if (jda == null) return null;
        String id = plugin.configs().get("discord").getString("discord.guild-id", "");
        if (id == null || id.contains("CHANGE_ME") || id.isBlank()) return null;
        return jda.getGuildById(id);
    }

    private TextChannel channel(String key) {
        Guild guild = guild();
        if (guild == null) return null;
        String id = plugin.configs().get("discord").getString("discord.channels." + key, "");
        if (id == null || id.contains("CHANGE_ME") || id.isBlank()) return null;
        return guild.getTextChannelById(id);
    }

    private final class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            String raw = event.getMessage().getContentRaw();

            if (raw.startsWith("!link ")) {
                String code = raw.substring(6).trim();
                LinkCode link = linkCodes.remove(code);
                if (link == null || link.expiresAt < System.currentTimeMillis()) {
                    event.getChannel().sendMessage("That link code is invalid or expired.").queue();
                    return;
                }

                PlayerData data = plugin.data().player(link.player);
                data.discordId = event.getAuthor().getId();
                plugin.data().markDirty(link.player);

                Guild guild = guild();
                if (guild != null) {
                    String roleId = plugin.configs().get("discord").getString("discord.roles.verified", "");
                    Role role = roleId == null || roleId.contains("CHANGE_ME") ? null : guild.getRoleById(roleId);
                    Member member = event.getMember();
                    if (role != null && member != null) guild.addRoleToMember(member, role).queue();
                }

                event.getChannel().sendMessage("Linked Discord to Minecraft UUID `" + link.player + "`.").queue();
                Player player = Bukkit.getPlayer(link.player);
                if (player != null) Bukkit.getScheduler().runTask(plugin, () -> Text.send(player, "<green>Discord account linked."));
                return;
            }

            if (!feature("chat-relay")) return;
            TextChannel minecraft = channel("minecraft-chat");
            if (minecraft == null || event.getChannel().getIdLong() != minecraft.getIdLong()) return;

            String display = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
            String message = raw.replace("<", "\\<").replace(">", "\\>");
            Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcast(Text.component("<dark_gray>[<#5865F2>DISCORD<dark_gray>] <white>" + display
                    + " <dark_gray>» <gray>" + message)));
        }

        @Override
        public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
            if (event.getUser() != null && event.getUser().isBot()) return;
            applyReactionRole(event.getGuild(), event.getMember(), event.getMessageId(), event.getEmoji().getFormatted(), true);
        }

        @Override
        public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
            if (event.getUser() != null && event.getUser().isBot()) return;
            applyReactionRole(event.getGuild(), event.getMember(), event.getMessageId(), event.getEmoji().getFormatted(), false);
        }
    }

    private void applyReactionRole(Guild guild, Member member, String messageId, String emoji, boolean add) {
        if (guild == null || member == null) return;
        if (!plugin.configs().get("discord").getBoolean("discord.reaction-roles.enabled", false)) return;
        var section = plugin.configs().get("discord").getConfigurationSection("discord.reaction-roles.mappings");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = id + ".";
            if (!messageId.equals(section.getString(path + "message-id", ""))) continue;
            if (!emoji.equals(section.getString(path + "emoji", ""))) continue;
            String roleId = section.getString(path + "role-id", "");
            if (roleId == null || roleId.contains("CHANGE_ME")) return;
            Role role = guild.getRoleById(roleId);
            if (role == null) return;
            if (add) guild.addRoleToMember(member, role).queue();
            else guild.removeRoleFromMember(member, role).queue();
            return;
        }
    }
}

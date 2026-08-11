package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class MarketModule extends VupeModule {
    private BukkitTask coinflipExpiryTask;

    public MarketModule(VupeCore plugin) {
        super(plugin, "market");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("coinflip", this::coinflipCommand);
        plugin.commands().register("cancelcoinflip", (s,l,a) -> coinflipCommand(s, l, new String[]{"cancel"}));
        coinflipExpiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expireCoinflips, 1200L, 1200L);
    }

    @Override
    protected void onDisable() {
        if (coinflipExpiryTask != null) coinflipExpiryTask.cancel();
        coinflipExpiryTask = null;
    }

    private boolean coinflipCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.coinflips", true)) {
            Text.send(sender, "<red>Coinflips are disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            Text.raw(player, "<#F472B6><bold>VUPE COINFLIPS</bold>");
            if (plugin.data().server().coinflips.isEmpty()) {
                Text.raw(player, "<gray>No active coinflips. Create one with <white>/coinflip <amount><gray>.");
                return true;
            }
            for (ServerData.CoinflipRecord record : plugin.data().server().coinflips.values()) {
                OfflinePlayer creator = Bukkit.getOfflinePlayer(UUID.fromString(record.creator));
                Text.raw(player, " <dark_gray>• <white>" + (creator.getName() == null ? record.creator : creator.getName())
                    + " <gray>for <green>$" + Text.format(record.amount)
                    + " <dark_gray>• <yellow>/coinflip accept " + record.id);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2) {
                Text.send(player, "<red>/coinflip accept <id>");
                return true;
            }
            acceptCoinflip(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            cancelCoinflip(player);
            return true;
        }

        double amount;
        try { amount = Double.parseDouble(args[0].replace(",", "")); }
        catch (NumberFormatException ex) {
            Text.send(player, "<red>Usage: /coinflip <amount>");
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            Text.send(player, "<red>Invalid amount.");
            return true;
        }

        if (plugin.data().server().coinflips.values().stream()
            .anyMatch(r -> r.creator.equals(player.getUniqueId().toString()))) {
            Text.send(player, "<red>You already have a coinflip.");
            return true;
        }

        if (!plugin.modules().economy().takeMoney(player.getUniqueId(), amount)) {
            Text.send(player, "<red>You do not have enough money.");
            return true;
        }

        ServerData.CoinflipRecord record = new ServerData.CoinflipRecord();
        record.id = UUID.randomUUID().toString().substring(0, 8);
        record.creator = player.getUniqueId().toString();
        record.amount = amount;
        record.createdAt = System.currentTimeMillis();
        plugin.data().server().coinflips.put(record.id, record);
        plugin.data().markServerDirty();

        plugin.effects().broadcast(Text.prefix() + "<white>" + player.getName()
            + " <gray>created a <green>$" + Text.format(amount)
            + " <gray>coinflip. <yellow>/coinflip accept " + record.id, "broadcast");
        return true;
    }

    private void acceptCoinflip(Player challenger, String id) {
        ServerData.CoinflipRecord record = plugin.data().server().coinflips.get(id);
        if (record == null) {
            Text.send(challenger, "<red>Coinflip not found.");
            return;
        }

        UUID creatorId = UUID.fromString(record.creator);
        if (creatorId.equals(challenger.getUniqueId())) {
            Text.send(challenger, "<red>You cannot accept your own coinflip.");
            return;
        }
        if (!plugin.modules().economy().takeMoney(challenger.getUniqueId(), record.amount)) {
            Text.send(challenger, "<red>You do not have enough money.");
            return;
        }

        plugin.data().server().coinflips.remove(id);
        plugin.data().markServerDirty();

        UUID winner = Math.random() < 0.5 ? creatorId : challenger.getUniqueId();
        plugin.modules().economy().addMoney(winner, record.amount * 2);
        OfflinePlayer winnerPlayer = Bukkit.getOfflinePlayer(winner);

        plugin.effects().broadcast(Text.prefix() + "<white>"
            + (winnerPlayer.getName() == null ? winner : winnerPlayer.getName())
            + " <gray>won <green>$" + Text.format(record.amount * 2)
            + " <gray>in a coinflip.", "broadcast");

        Player winnerOnline = Bukkit.getPlayer(winner);
        if (winnerOnline != null) plugin.effects().celebrate(winnerOnline);
    }

    private void cancelCoinflip(Player player) {
        ServerData.CoinflipRecord found = plugin.data().server().coinflips.values().stream()
            .filter(record -> record.creator.equals(player.getUniqueId().toString()))
            .findFirst().orElse(null);
        if (found == null) {
            Text.send(player, "<red>You do not have an active coinflip.");
            return;
        }
        plugin.data().server().coinflips.remove(found.id);
        plugin.modules().economy().addMoney(player.getUniqueId(), found.amount);
        plugin.data().markServerDirty();
        Text.send(player, "<green>Coinflip cancelled and refunded.");
        plugin.effects().success(player);
    }

    private void expireCoinflips() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ServerData.CoinflipRecord record : new ArrayList<>(plugin.data().server().coinflips.values())) {
            if (now - record.createdAt < 30 * 60_000L) continue;
            plugin.modules().economy().addMoney(UUID.fromString(record.creator), record.amount);
            plugin.data().server().coinflips.remove(record.id);
            changed = true;

            Player creator = Bukkit.getPlayer(UUID.fromString(record.creator));
            if (creator != null) {
                Text.send(creator, "<gray>Your coinflip expired after 30 minutes and was refunded.");
            }
        }
        if (changed) plugin.data().markServerDirty();
    }
}

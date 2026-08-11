package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public final class EconomyModule extends VupeModule {
    public EconomyModule(VupeCore plugin) {
        super(plugin, "economy");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("bal", this::balanceCommand);
        plugin.commands().register("balance", this::balanceCommand);
        plugin.commands().register("pay", this::payCommand);
        plugin.commands().register("eco", this::ecoCommand);
        plugin.commands().register("baltop", this::balanceTopCommand);
        plugin.commands().register("crystals", this::crystalsCommand);
        plugin.commands().register("gold", this::goldCommand);
        plugin.commands().register("crystaleco", this::crystalEcoCommand);
        plugin.commands().register("crystalpay", this::crystalPayCommand);
        plugin.commands().register("crystalstop", this::crystalTopCommand);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        data.lastName = event.getPlayer().getName();
        plugin.data().server().knownPlayers.add(event.getPlayer().getUniqueId().toString());
        plugin.data().markServerDirty();
        if (!data.economyInitialized) {
            data.money = Math.max(0.0, plugin.configs().get("economy").getDouble("economy.starting-money", 5000));
            data.economyInitialized = true;
            plugin.data().markDirty(data.uuid);
        }

        data.lastJoinEpoch = System.currentTimeMillis();
        data.lastActiveEpoch = System.currentTimeMillis();
        plugin.data().server().knownPlayers.add(data.uuid.toString());
        plugin.data().markServerDirty();
        plugin.data().markDirty(data.uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        long now = System.currentTimeMillis();
        if (data.lastJoinEpoch > 0) {
            data.playtimeSeconds += Math.max(0, (now - data.lastJoinEpoch) / 1000L);
        }
        data.lastJoinEpoch = 0;
        plugin.data().markDirty(data.uuid);
        plugin.data().unload(data.uuid);
    }

    public double money(UUID uuid) {
        return sanitize(plugin.data().player(uuid).money);
    }

    public long crystals(UUID uuid) {
        return plugin.data().player(uuid).crystals;
    }

    public long gold(UUID uuid) {
        return plugin.data().player(uuid).gold;
    }

    public void addMoney(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        PlayerData data = plugin.data().player(uuid);
        data.money = sanitize(data.money + amount);
        plugin.data().markDirty(uuid);
    }

    public boolean takeMoney(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return false;
        PlayerData data = plugin.data().player(uuid);
        if (data.money + 1.0E-9 < amount) return false;
        data.money = sanitize(data.money - amount);
        plugin.data().markDirty(uuid);
        return true;
    }

    public void setMoney(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return;
        PlayerData data = plugin.data().player(uuid);
        data.money = sanitize(amount);
        plugin.data().markDirty(uuid);
    }

    public void addCrystals(UUID uuid, long amount) {
        if (amount <= 0) return;
        PlayerData data = plugin.data().player(uuid);
        double multiplier = 1.0;
        if (data.crystalBoosterUntil > System.currentTimeMillis()) {
            multiplier *= Math.max(1.0, data.crystalBoosterMultiplier);
        }
        if (plugin.data().server().globalCrystalBoosterUntil > System.currentTimeMillis()) {
            multiplier *= Math.max(1.0, plugin.data().server().globalCrystalBoosterMultiplier);
        }
        long finalAmount = Math.max(1L, Math.round(amount * multiplier));
        data.crystals = Math.max(0, data.crystals + finalAmount);
        plugin.data().markDirty(uuid);
    }

    public boolean takeCrystals(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = plugin.data().player(uuid);
        if (data.crystals < amount) return false;
        data.crystals -= amount;
        plugin.data().markDirty(uuid);
        return true;
    }

    public void addGold(UUID uuid, long amount) {
        PlayerData data = plugin.data().player(uuid);
        data.gold = Math.max(0, data.gold + amount);
        plugin.data().markDirty(uuid);
    }

    public boolean takeGold(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = plugin.data().player(uuid);
        if (data.gold < amount) return false;
        data.gold -= amount;
        plugin.data().markDirty(uuid);
        return true;
    }

    private boolean balanceCommand(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("vupe.admin")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            PlayerData data = plugin.data().player(target.getUniqueId());
            Text.send(sender, "<white>" + target.getName() + "<gray>'s balance: <green>$" + Text.format(money(target.getUniqueId())));
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Usage from console: /bal <player>");
            return true;
        }
        Text.send(player, "<gray>Balance: <green>$" + Text.format(money(player.getUniqueId()))
            + " <dark_gray>• <gray>Crystals: <#8B5CF6>" + crystals(player.getUniqueId())
            + " <dark_gray>• <gray>Gold: <gold>" + gold(player.getUniqueId()));
        return true;
    }

    private boolean payCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /pay <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            Text.send(player, "<red>That player is unavailable.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(",", ""));
        } catch (NumberFormatException ex) {
            Text.send(player, "<red>Invalid amount.");
            return true;
        }
        double min = plugin.configs().get("economy").getDouble("economy.pay.minimum", 1);
        double max = plugin.configs().get("economy").getDouble("economy.pay.maximum", 1_000_000_000_000D);
        if (!Double.isFinite(amount) || amount < min || amount > max) {
            Text.send(player, "<red>Amount must be between " + Text.format(min) + " and " + Text.format(max) + ".");
            return true;
        }
        if (!takeMoney(player.getUniqueId(), amount)) {
            Text.send(player, "<red>You do not have enough money.");
            return true;
        }
        addMoney(target.getUniqueId(), amount);
        Text.send(player, "<gray>Paid <white>" + target.getName() + " <green>$" + Text.format(amount) + "<gray>.");
        Text.send(target, "<white>" + player.getName() + " <gray>paid you <green>$" + Text.format(amount) + "<gray>.");
        return true;
    }

    private boolean ecoCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /eco <set|give|take|reset> <player> [amount]");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (action.equals("reset")) {
            setMoney(target.getUniqueId(), 0);
        } else {
            if (args.length < 3) {
                Text.send(sender, "<red>Usage: /eco <set|give|take|reset> <player> [amount]");
                return true;
            }

            double amount;
            try { amount = Double.parseDouble(args[2].replace(",", "")); }
            catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid amount."); return true; }
            if (!Double.isFinite(amount) || amount < 0) {
                Text.send(sender, "<red>Invalid amount.");
                return true;
            }

            switch (action) {
                case "set" -> setMoney(target.getUniqueId(), amount);
                case "give", "add" -> addMoney(target.getUniqueId(), amount);
                case "take", "remove" -> {
                    double current = money(target.getUniqueId());
                    setMoney(target.getUniqueId(), Math.max(0, current - amount));
                }
                default -> {
                    Text.send(sender, "<red>Usage: /eco <set|give|take|reset> <player> [amount]");
                    return true;
                }
            }
        }

        Text.send(sender, "<green>Updated <white>" + (target.getName() == null ? target.getUniqueId() : target.getName())
            + "<green>'s money to <green>$" + Text.format(money(target.getUniqueId())) + "<green>.");
        return true;
    }

    private boolean crystalEcoCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 2) {
            Text.send(sender, "<red>Usage: /crystaleco <set|give|take|reset> <player> [amount]");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerData data = plugin.data().player(target.getUniqueId());

        if (action.equals("reset")) {
            data.crystals = 0;
        } else {
            if (args.length < 3) {
                Text.send(sender, "<red>Usage: /crystaleco <set|give|take|reset> <player> [amount]");
                return true;
            }

            long amount;
            try { amount = Long.parseLong(args[2]); }
            catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid amount."); return true; }
            if (amount < 0) {
                Text.send(sender, "<red>Invalid amount.");
                return true;
            }

            switch (action) {
                case "set" -> data.crystals = amount;
                case "give", "add" -> data.crystals = Math.max(0, data.crystals + amount);
                case "take", "remove" -> data.crystals = Math.max(0, data.crystals - amount);
                default -> {
                    Text.send(sender, "<red>Usage: /crystaleco <set|give|take|reset> <player> [amount]");
                    return true;
                }
            }
        }

        plugin.data().markDirty(target.getUniqueId());
        Text.send(sender, "<green>Crystal balance updated to <#8B5CF6>" + data.crystals + "<green>.");
        return true;
    }

    private boolean crystalPayCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /crystalpay <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            Text.send(player, "<red>That player is unavailable.");
            return true;
        }
        long amount;
        try { amount = Long.parseLong(args[1]); }
        catch (NumberFormatException ex) { Text.send(player, "<red>Invalid amount."); return true; }
        if (!takeCrystals(player.getUniqueId(), amount)) {
            Text.send(player, "<red>You do not have enough crystals.");
            return true;
        }
        addCrystals(target.getUniqueId(), amount);
        Text.send(player, "<gray>Paid <white>" + target.getName() + " <#8B5CF6>" + amount + " Crystals<gray>.");
        Text.send(target, "<white>" + player.getName() + " <gray>paid you <#8B5CF6>" + amount + " Crystals<gray>.");
        return true;
    }

    private boolean balanceTopCommand(CommandSender sender, String label, String[] args) {
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>BALANCE TOP</bold></gradient>");
        plugin.data().server().knownPlayers.stream()
            .map(raw -> {
                try { return plugin.data().player(UUID.fromString(raw)); }
                catch (IllegalArgumentException ex) { return null; }
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingDouble((PlayerData d) -> money(d.uuid)).reversed())
            .limit(10)
            .forEach(data -> Text.raw(sender, " <dark_gray>• <white>"
                + (data.lastName == null || data.lastName.isBlank() ? data.uuid : data.lastName)
                + " <dark_gray>— <green>$" + Text.format(money(data.uuid))));
        return true;
    }

    private boolean crystalTopCommand(CommandSender sender, String label, String[] args) {
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>CRYSTAL TOP</bold></gradient>");
        plugin.data().server().knownPlayers.stream()
            .map(raw -> {
                try { return plugin.data().player(UUID.fromString(raw)); }
                catch (IllegalArgumentException ex) { return null; }
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong((PlayerData d) -> d.crystals).reversed())
            .limit(10)
            .forEach(data -> Text.raw(sender, " <dark_gray>• <white>"
                + (data.lastName == null || data.lastName.isBlank() ? data.uuid : data.lastName)
                + " <dark_gray>— <#8B5CF6>" + data.crystals));
        return true;
    }

    private boolean crystalsCommand(CommandSender sender, String label, String[] args) {
        if (args.length >= 3 && sender.hasPermission("vupe.admin")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            long amount;
            try { amount = Long.parseLong(args[2]); }
            catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid amount."); return true; }
            PlayerData data = plugin.data().player(target.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> data.crystals = Math.max(0, amount);
                case "add" -> data.crystals = Math.max(0, data.crystals + amount);
                case "remove" -> data.crystals = Math.max(0, data.crystals - amount);
                default -> { Text.send(sender, "<red>Usage: /crystals <set|add|remove> <player> <amount>"); return true; }
            }
            plugin.data().markDirty(target.getUniqueId());
            Text.send(sender, "<green>Updated crystals for <white>" + target.getName() + "<green>.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Usage: /crystals <set|add|remove> <player> <amount>");
            return true;
        }
        Text.send(player, "<gray>Crystals: <#8B5CF6>" + crystals(player.getUniqueId()));
        return true;
    }

    private boolean goldCommand(CommandSender sender, String label, String[] args) {
        if (args.length >= 3 && sender.hasPermission("vupe.admin")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            long amount;
            try { amount = Long.parseLong(args[2]); }
            catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid amount."); return true; }
            PlayerData data = plugin.data().player(target.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> data.gold = Math.max(0, amount);
                case "add" -> data.gold = Math.max(0, data.gold + amount);
                case "remove" -> data.gold = Math.max(0, data.gold - amount);
                default -> { Text.send(sender, "<red>Usage: /gold <set|add|remove> <player> <amount>"); return true; }
            }
            plugin.data().markDirty(target.getUniqueId());
            Text.send(sender, "<green>Updated Gold for <white>" + target.getName() + "<green>.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Usage: /gold <set|add|remove> <player> <amount>");
            return true;
        }
        Text.send(player, "<gray>Gold: <gold>" + gold(player.getUniqueId()));
        return true;
    }

    private double sanitize(double value) {
        if (!Double.isFinite(value)) return 0;
        if (!plugin.configs().get("economy").getBoolean("economy.allow-negative", false)) {
            value = Math.max(0, value);
        }
        return Math.round(value * 100.0) / 100.0;
    }
}

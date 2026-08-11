package dev.vupe.core.integration;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.util.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class VaultHook {
    private final VupeCore plugin;
    private final Economy economy;

    public VaultHook(VupeCore plugin) {
        this.plugin = plugin;
        this.economy = new VupeEconomy();
    }

    public boolean hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault is not installed; Vupe Money still works, but external Vault compatibility is unavailable.");
            return false;
        }
        Bukkit.getServicesManager().unregister(Economy.class, economy);
        Bukkit.getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Highest);
        plugin.getLogger().info("Registered VupeCore as the Vault economy provider.");
        return true;
    }

    public void unhook() {
        Bukkit.getServicesManager().unregister(Economy.class, economy);
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }

    public String providerName() {
        return "VupeCore";
    }

    public double balance(UUID uuid) {
        return internalBalance(uuid);
    }

    public boolean has(UUID uuid, double amount) {
        return validAmount(amount) && internalBalance(uuid) + 1.0E-9 >= amount;
    }

    public boolean withdraw(UUID uuid, double amount) {
        return internalWithdraw(uuid, amount);
    }

    public boolean deposit(UUID uuid, double amount) {
        return internalDeposit(uuid, amount);
    }

    public boolean set(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        PlayerData data = plugin.data().player(uuid);
        data.money = sanitize(amount);
        plugin.data().markDirty(uuid);
        return true;
    }

    private double internalBalance(UUID uuid) {
        return sanitize(plugin.data().player(uuid).money);
    }

    private boolean internalDeposit(UUID uuid, double amount) {
        if (!validAmount(amount)) return false;
        PlayerData data = plugin.data().player(uuid);
        data.money = sanitize(data.money + amount);
        plugin.data().markDirty(uuid);
        return true;
    }

    private boolean internalWithdraw(UUID uuid, double amount) {
        if (!validAmount(amount)) return false;
        PlayerData data = plugin.data().player(uuid);
        double current = sanitize(data.money);
        if (current + 1.0E-9 < amount) return false;
        data.money = sanitize(current - amount);
        plugin.data().markDirty(uuid);
        return true;
    }

    private double sanitize(double value) {
        if (!Double.isFinite(value)) return 0.0;
        if (!plugin.configs().get("economy").getBoolean("economy.allow-negative", false)) value = Math.max(0.0, value);
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0.0;
    }

    private OfflinePlayer offline(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    private EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    private EconomyResponse failure(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE, message);
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "VupeCore does not implement Vault bank accounts.");
    }

    private final class VupeEconomy implements Economy {
        @Override public boolean isEnabled() { return plugin.isEnabled(); }
        @Override public String getName() { return "VupeCore"; }
        @Override public boolean hasBankSupport() { return false; }
        @Override public int fractionalDigits() { return 2; }
        @Override public String format(double amount) { return "$" + Text.format(amount); }
        @Override public String currencyNamePlural() { return "Vupe Money"; }
        @Override public String currencyNameSingular() { return "Vupe Money"; }

        @Override public boolean hasAccount(String playerName) { return hasAccount(offline(playerName)); }
        @Override public boolean hasAccount(OfflinePlayer player) {
            return player != null && (player.hasPlayedBefore() || plugin.data().server().knownPlayers.contains(player.getUniqueId().toString()));
        }
        @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
        @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

        @Override public double getBalance(String playerName) { return getBalance(offline(playerName)); }
        @Override public double getBalance(OfflinePlayer player) { return player == null ? 0.0 : internalBalance(player.getUniqueId()); }
        @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
        @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

        @Override public boolean has(String playerName, double amount) { return has(offline(playerName), amount); }
        @Override public boolean has(OfflinePlayer player, double amount) {
            return player != null && validAmount(amount) && internalBalance(player.getUniqueId()) + 1.0E-9 >= amount;
        }
        @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
        @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

        @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdrawPlayer(offline(playerName), amount); }
        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            if (player == null || !validAmount(amount)) return failure(amount, 0, "Invalid player or amount.");
            double before = internalBalance(player.getUniqueId());
            if (before + 1.0E-9 < amount) return failure(amount, before, "Insufficient funds.");
            internalWithdraw(player.getUniqueId(), amount);
            return success(amount, internalBalance(player.getUniqueId()));
        }
        @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
        @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

        @Override public EconomyResponse depositPlayer(String playerName, double amount) { return depositPlayer(offline(playerName), amount); }
        @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            if (player == null || !validAmount(amount)) return failure(amount, 0, "Invalid player or amount.");
            internalDeposit(player.getUniqueId(), amount);
            return success(amount, internalBalance(player.getUniqueId()));
        }
        @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
        @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

        @Override public EconomyResponse createBank(String name, String player) { return notImplemented(); }
        @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImplemented(); }
        @Override public EconomyResponse deleteBank(String name) { return notImplemented(); }
        @Override public EconomyResponse bankBalance(String name) { return notImplemented(); }
        @Override public EconomyResponse bankHas(String name, double amount) { return notImplemented(); }
        @Override public EconomyResponse bankWithdraw(String name, double amount) { return notImplemented(); }
        @Override public EconomyResponse bankDeposit(String name, double amount) { return notImplemented(); }
        @Override public EconomyResponse isBankOwner(String name, String playerName) { return notImplemented(); }
        @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notImplemented(); }
        @Override public EconomyResponse isBankMember(String name, String playerName) { return notImplemented(); }
        @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notImplemented(); }
        @Override public List<String> getBanks() { return Collections.emptyList(); }

        @Override public boolean createPlayerAccount(String playerName) { return createPlayerAccount(offline(playerName)); }
        @Override public boolean createPlayerAccount(OfflinePlayer player) {
            if (player == null) return false;
            PlayerData data = plugin.data().player(player.getUniqueId());
            if (!data.economyInitialized) {
                data.money = Math.max(0.0, plugin.configs().get("economy").getDouble("economy.starting-money", 5000));
                data.economyInitialized = true;
                plugin.data().markDirty(player.getUniqueId());
            }
            plugin.data().server().knownPlayers.add(player.getUniqueId().toString());
            plugin.data().markServerDirty();
            return true;
        }
        @Override public boolean createPlayerAccount(String playerName, String worldName) { return createPlayerAccount(playerName); }
        @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }
    }
}

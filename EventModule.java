package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class EventModule extends VupeModule {
    private BukkitTask supplyTask;
    private BukkitTask adsTask;
    private final Set<String> activeSupplyLocations = new HashSet<>();
    private final Set<UUID> missionSessions = new HashSet<>();

    public EventModule(VupeCore plugin) {
        super(plugin, "events");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("missions", this::missionsCommand);
        plugin.commands().register("supply", this::supplyCommand);
        plugin.commands().register("vote", this::voteCommand);
        plugin.commands().register("vupevote", this::voteBridgeCommand);
        plugin.commands().register("admsg", this::advertisementCommand);
        plugin.commands().register("buybroadcast", this::purchaseBroadcastCommand);
        plugin.commands().register("buybroadcasts", this::broadcastInfoCommand);

        long supplyPeriod = Math.max(5L, plugin.configs().get("events").getLong("supply-drops.interval-minutes", 45)) * 60L * 20L;
        supplyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoSupply, supplyPeriod, supplyPeriod);

        long adsPeriod = Math.max(30L, plugin.configs().get("events").getLong("ads.interval-seconds", 240)) * 20L;
        adsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastAd, adsPeriod, adsPeriod);
    }

    @Override
    protected void onDisable() {
        if (supplyTask != null) supplyTask.cancel();
        if (adsTask != null) adsTask.cancel();
        supplyTask = null;
        adsTask = null;
    }

    public void progress(Player player, String mission, double amount) {
        if (!plugin.configs().modules().getBoolean("modules.missions", true)) return;
        resetMissionsIfNeeded(player);

        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.completedMissions.contains(mission)) return;
        String path = "missions.definitions." + mission;
        if (!plugin.configs().get("events").contains(path)) return;

        data.missionProgress.merge(mission, amount, Double::sum);
        double target = plugin.configs().get("events").getDouble(path + ".target", 1);
        if (data.missionProgress.getOrDefault(mission, 0D) >= target) {
            data.completedMissions.add(mission);
            grantMissionReward(player, path + ".reward");
            plugin.effects().title(player, "<#22D3EE><bold>MISSION COMPLETE</bold>",
                "<gray>" + prettyMission(mission) + " reward delivered");
            plugin.effects().sound(player, "reward");
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0),
                20, .6, .6, .6, .03);
        }
        plugin.data().markDirty(player.getUniqueId());
    }

    private void resetMissionsIfNeeded(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (data.missionResetEpoch <= 0 || data.missionResetEpoch <= now) {
            data.missionProgress.clear();
            data.completedMissions.clear();
            long hours = Math.max(1, plugin.configs().get("events").getLong("missions.reset-hours", 24));
            data.missionResetEpoch = now + hours * 3_600_000L;
            plugin.data().markDirty(player.getUniqueId());
        }
    }

    private boolean missionsCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        resetMissionsIfNeeded(player);
        PlayerData data = plugin.data().player(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#8B5CF6:#22D3EE><bold>VUPE DAILY MISSIONS</bold></gradient>"));
        ItemStack filler = Items.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i=0;i<inv.getSize();i++) {
            int row=i/9,col=i%9;
            if(row==0||row==5||col==0||col==8) inv.setItem(i,filler);
        }

        ConfigurationSection defs = plugin.configs().get("events").getConfigurationSection("missions.definitions");
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int index = 0;
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                if (index >= slots.length) break;
                double target = defs.getDouble(id + ".target", 1);
                double current = Math.min(target, data.missionProgress.getOrDefault(id, 0D));
                boolean done = data.completedMissions.contains(id);
                double ratio = target <= 0 ? 1 : Math.min(1, current/target);
                Material icon = done ? Material.LIME_DYE : ratio >= .75 ? Material.LIGHT_BLUE_DYE :
                    ratio >= .4 ? Material.CYAN_DYE : Material.GRAY_DYE;

                String rewardType = defs.getString(id + ".reward.type","MONEY");
                String rewardValue = defs.getString(id + ".reward.value","");
                double rewardAmount = defs.getDouble(id + ".reward.amount",1);

                int bars = (int)Math.round(ratio*10);
                String progressBar = "<green>" + "■".repeat(Math.max(0,bars))
                    + "<dark_gray>" + "■".repeat(Math.max(0,10-bars));

                List<String> lore = new ArrayList<>();
                lore.add(done ? "<green>✓ COMPLETED" : "<gray>Progress: <white>" + Text.format(current)
                    + "<dark_gray>/<white>" + Text.format(target));
                lore.add(progressBar + " <white>" + Math.round(ratio*100) + "%");
                lore.add("");
                lore.add("<white><bold>REWARD</bold>");
                lore.add("<dark_gray>• <gray>" + prettyMission(rewardType) + " <white>" + Text.format(rewardAmount)
                    + (rewardValue.isBlank() ? "" : " <gray>(" + prettyMission(rewardValue) + ")"));
                lore.add("");
                lore.add(done ? "<green>Reward delivered automatically." : "<gray>Progress updates live from gameplay.");

                inv.setItem(slots[index++], Items.item(icon, "<#22D3EE><bold>" + prettyMission(id) + "</bold>", lore));
            }
        }

        long remaining = Math.max(0, data.missionResetEpoch - System.currentTimeMillis());
        inv.setItem(49, Items.item(Material.CLOCK, "<#FBBF24><bold>DAILY RESET</bold>",
            List.of("<gray>Next reset in: <white>" + dev.vupe.core.util.TimeUtil.pretty(remaining),
                "<gray>Completed: <green>" + data.completedMissions.size() + "<dark_gray>/<white>"
                    + (defs == null ? 0 : defs.getKeys(false).size()))));

        missionSessions.add(player.getUniqueId());
        player.openInventory(inv);
        plugin.effects().open(player);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMissionClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && missionSessions.contains(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.effects().click(player);
        }
    }

    @EventHandler
    public void onMissionClose(InventoryCloseEvent event) {
        missionSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMissionDrag(InventoryDragEvent event) {
        if (missionSessions.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void grantMissionReward(Player player, String path) {
        String type = plugin.configs().get("events").getString(path + ".type", "MONEY").toUpperCase(Locale.ROOT);
        double amount = plugin.configs().get("events").getDouble(path + ".amount", 1);
        String value = plugin.configs().get("events").getString(path + ".value", "");

        switch (type) {
            case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            case "CRYSTALS" -> plugin.modules().economy().addCrystals(player.getUniqueId(), Math.round(amount));
            case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Math.round(amount));
            case "CRATE_KEY" -> plugin.modules().crates().addKeys(player.getUniqueId(), value, Math.max(1, (int) Math.round(amount)));
            case "GENERATOR" -> plugin.modules().generators().give(player, value, Math.max(1, (int) Math.round(amount)));
        }
    }

    private void autoSupply() {
        if (!plugin.configs().modules().getBoolean("modules.supply-drops", true)) return;
        int minimum = plugin.configs().get("events").getInt("supply-drops.minimum-online", 2);
        if (Bukkit.getOnlinePlayers().size() < minimum) return;
        spawnSupply();
    }

    private boolean supplyCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Text.send(sender, "<gray>Supply drops: <white>" + activeSupplyLocations.size() + " active"
                + " <dark_gray>• <gray>configured points: <white>" + plugin.data().server().supplyLocations.size());
            return true;
        }
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            spawnSupply();
            Text.send(sender, "<green>Supply-drop attempt started.");
            return true;
        }
        Text.send(sender, "<gray>Configure supply points with <white>/vupe setup supply <number><gray>.");
        return true;
    }

    private void spawnSupply() {
        if (plugin.data().server().supplyLocations.isEmpty()) return;
        List<String> keys = new ArrayList<>(plugin.data().server().supplyLocations.keySet());
        String key = keys.get(new Random().nextInt(keys.size()));
        Location loc = Locations.deserialize(plugin.data().server().supplyLocations.get(key));
        if (loc == null) return;
        String locationKey = Locations.blockKey(loc);
        if (activeSupplyLocations.contains(locationKey)) return;

        loc.getBlock().setType(Material.CHEST, false);
        if (!(loc.getBlock().getState() instanceof Chest chest)) return;
        chest.getPersistentDataContainer().set(new NamespacedKey(plugin, "supply_drop"), PersistentDataType.BYTE, (byte) 1);
        chest.update(true);

        activeSupplyLocations.add(locationKey);
        plugin.effects().broadcast(Text.prefix() + "<#F472B6><bold>SUPPLY DROP</bold> <gray>A Vupe cache has spawned in <white>"
            + loc.getWorld().getName() + "<gray>.", "broadcast");

        int minutes = Math.max(1, plugin.configs().get("events").getInt("supply-drops.expire-minutes", 5));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!activeSupplyLocations.remove(locationKey)) return;
            if (loc.getBlock().getType() == Material.CHEST
                && loc.getBlock().getState() instanceof Chest staleChest
                && staleChest.getPersistentDataContainer().has(new NamespacedKey(plugin, "supply_drop"))) {
                loc.getBlock().setType(Material.AIR, false);
                Bukkit.broadcast(Text.component(Text.prefix() + "<gray>The unclaimed supply drop expired."));
            }
        }, minutes * 60L * 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyClick(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Chest chest)) return;
        if (!chest.getPersistentDataContainer().has(new NamespacedKey(plugin, "supply_drop"))) return;

        event.setCancelled(true);
        String key = Locations.blockKey(chest.getLocation());
        if (!activeSupplyLocations.remove(key)) return;

        chest.getBlock().setType(Material.AIR, false);
        grantSupplyReward(event.getPlayer());
    }

    private void grantSupplyReward(Player player) {
        List<Map<?, ?>> rewards = plugin.configs().get("events").getMapList("supply-drops.rewards");
        if (rewards.isEmpty()) return;

        double total = rewards.stream().mapToDouble(r -> number(r.get("weight"), 1)).sum();
        double roll = Math.random() * Math.max(1, total);
        Map<?, ?> selected = rewards.getLast();
        double current = 0;
        for (Map<?, ?> reward : rewards) {
            current += Math.max(0, number(reward.get("weight"), 1));
            if (roll <= current) { selected = reward; break; }
        }

        String type = String.valueOf(selected.containsKey("type") ? selected.get("type") : "MONEY").toUpperCase(Locale.ROOT);
        double amount = number(selected.get("amount"), 1);
        String value = String.valueOf(selected.containsKey("value") ? selected.get("value") : "");

        switch (type) {
            case "MONEY" -> plugin.modules().economy().addMoney(player.getUniqueId(), amount);
            case "CRYSTALS" -> plugin.modules().economy().addCrystals(player.getUniqueId(), Math.round(amount));
            case "GOLD" -> plugin.modules().economy().addGold(player.getUniqueId(), Math.round(amount));
            case "GENERATOR" -> plugin.modules().generators().give(player, value, Math.max(1, (int) Math.round(amount)));
            case "CRATE_KEY" -> plugin.modules().crates().addKeys(player.getUniqueId(), value, Math.max(1, (int) Math.round(amount)));
        }
        plugin.effects().broadcast(Text.prefix() + "<#22D3EE>" + player.getName() + " <gray>claimed a supply drop.", "reward");
    }

    private boolean advertisementCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (!plugin.configs().get("events").getBoolean("player-advertisements.enabled", true)) {
            Text.send(player, "<red>Player advertisements are disabled.");
            return true;
        }
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /admsg <message>");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        long readyAt = data.cooldowns.getOrDefault("advertisement", 0L);
        if (readyAt > System.currentTimeMillis()) {
            Text.send(player, "<red>Your next advertisement is available in <white>"
                + dev.vupe.core.util.TimeUtil.pretty(readyAt - System.currentTimeMillis()) + "<red>.");
            return true;
        }

        String message = String.join(" ", args);
        int max = Math.max(20, plugin.configs().get("events").getInt("player-advertisements.maximum-length", 120));
        if (message.length() > max) {
            Text.send(player, "<red>Your advertisement is too long (max " + max + " characters).");
            return true;
        }

        double price = plugin.configs().get("events").getDouble("player-advertisements.price", 250000);
        if (!plugin.modules().economy().takeMoney(player.getUniqueId(), price)) {
            Text.send(player, "<red>You need <green>$" + Text.format(price) + "<red>.");
            return true;
        }

        long cooldown = Math.max(5, plugin.configs().get("events").getLong("player-advertisements.cooldown-seconds", 120));
        data.cooldowns.put("advertisement", System.currentTimeMillis() + cooldown * 1000L);
        plugin.data().markDirty(player.getUniqueId());

        String prefix = plugin.configs().get("events").getString("player-advertisements.prefix", "<dark_gray>[<#F472B6>AD<dark_gray>] ");
        Bukkit.broadcast(Text.component(prefix + "<white>" + player.getName() + " <dark_gray>» <gray>"
            + message.replace("<", "\\<").replace(">", "\\>")));
        return true;
    }

    private boolean purchaseBroadcastCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (!plugin.configs().get("events").getBoolean("purchase-broadcast.enabled", true)) return true;
        if (args.length < 3) {
            Text.send(sender, "<red>Usage: /buybroadcast <player> <item> <price>");
            return true;
        }
        String price = args[args.length - 1];
        String item = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        String format = plugin.configs().get("events").getString("purchase-broadcast.format",
            "<#8B5CF6><bold>VUPE STORE</bold> <dark_gray>» <white>%player% <gray>bought <white>%item% <gray>for <green>%price%$");
        Bukkit.broadcast(Text.component(format
            .replace("%player%", args[0].replace("<", "\\<").replace(">", "\\>"))
            .replace("%item%", item.replace("<", "\\<").replace(">", "\\>"))
            .replace("%price%", price.replace("<", "\\<").replace(">", "\\>"))));
        return true;
    }

    private boolean broadcastInfoCommand(CommandSender sender, String label, String[] args) {
        double price = plugin.configs().get("events").getDouble("player-advertisements.price", 250000);
        long cooldown = plugin.configs().get("events").getLong("player-advertisements.cooldown-seconds", 120);
        Text.send(sender, "<gray>Player advertisements cost <green>$" + Text.format(price)
            + " <gray>and have a <white>" + cooldown + "s <gray>cooldown. Use <white>/admsg <message><gray>.");
        return true;
    }

    private void broadcastAd() {
        if (!plugin.configs().modules().getBoolean("modules.ads", true)) return;
        List<String> ads = plugin.configs().get("events").getStringList("ads.messages");
        if (ads.isEmpty()) return;
        String ad = ads.get(new Random().nextInt(ads.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.data().player(player.getUniqueId()).options.getOrDefault("ads", true)) continue;
            Text.raw(player, ad);
        }

        if (plugin.configs().modules().getBoolean("modules.bossbar-announcements", true)
            && !Bukkit.getOnlinePlayers().isEmpty()) {
            BossBar bar = BossBar.bossBar(Text.component(ad), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            for (Player player : Bukkit.getOnlinePlayers()) player.showBossBar(bar);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
            }, 100L);
        }
    }

    private boolean voteCommand(CommandSender sender, String label, String[] args) {
        Text.raw(sender, "<gradient:#8B5CF6:#22D3EE><bold>VOTE FOR VUPE</bold></gradient>");
        for (String link : plugin.configs().get("voting").getStringList("voting.links")) {
            Text.raw(sender, "<gray> • <white>" + link);
        }
        return true;
    }

    private boolean voteBridgeCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>Console/admin only.");
            return true;
        }
        if (!plugin.configs().get("voting").getBoolean("voting.allow-console-bridge", true)) return true;
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /vupevote <player> [service]");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String crate = plugin.configs().get("voting").getString("voting.per-vote.crate", "vote");
        int keys = plugin.configs().get("voting").getInt("voting.per-vote.keys", 1);
        double money = plugin.configs().get("voting").getDouble("voting.per-vote.money", 25000);

        plugin.modules().crates().addKeys(target.getUniqueId(), crate, keys);
        plugin.modules().economy().addMoney(target.getUniqueId(), money);
        plugin.data().server().votePartyProgress++;

        String service = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "a voting site";
        Bukkit.broadcast(Text.component(Text.prefix() + "<#22D3EE>" + (target.getName() == null ? args[0] : target.getName())
            + " <gray>voted on <white>" + service.replace("<", "\\<").replace(">", "\\>") + "<gray>."));

        int required = plugin.configs().get("voting").getInt("voting.party.required-votes", 25);
        if (plugin.data().server().votePartyProgress >= required) {
            plugin.data().server().votePartyProgress = 0;
            String partyCrate = plugin.configs().get("voting").getString("voting.party.reward-crate", "vote");
            int partyKeys = plugin.configs().get("voting").getInt("voting.party.reward-keys", 1);
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.modules().crates().addKeys(player.getUniqueId(), partyCrate, partyKeys);
            }
            plugin.effects().broadcast(Text.prefix() + "<#F472B6><bold>VOTE PARTY!</bold> <gray>Everyone online received a key.", "reward");
        }

        plugin.data().markServerDirty();
        return true;
    }

    private static String mapString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String prettyMission(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).replace('_',' ').split(" ")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }
}

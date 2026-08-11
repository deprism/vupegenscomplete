package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class PvpModule extends VupeModule {
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private BukkitTask kothTask;
    private UUID kothCapturer;
    private int kothSeconds;

    public PvpModule(VupeCore plugin) {
        super(plugin, "pvp");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("bounty", this::bountyCommand);
        plugin.commands().register("koth", this::kothCommand);
        plugin.commands().register("bosses", this::bossCommand);
    }

    @Override
    protected void onDisable() {
        if (kothTask != null) kothTask.cancel();
        kothTask = null;
        combatUntil.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (!(event.getEntity() instanceof Player victim) || attacker == null || attacker.equals(victim)) return;

        if (sameTeam(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.configs().modules().getBoolean("modules.combat-tag", true)) {
            long duration = Math.max(1, plugin.configs().get("pvp").getLong("combat-tag.duration-seconds", 15)) * 1000L;
            long until = System.currentTimeMillis() + duration;
            combatUntil.put(attacker.getUniqueId(), until);
            combatUntil.put(victim.getUniqueId(), until);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        PlayerData victimData = plugin.data().player(victim.getUniqueId());
        victimData.deaths++;
        combatUntil.remove(victim.getUniqueId());

        if (killer != null && !killer.equals(victim)) {
            PlayerData killerData = plugin.data().player(killer.getUniqueId());
            killerData.kills++;
            combatUntil.remove(killer.getUniqueId());

            if (victimData.bounty > 0) {
                double reward = victimData.bounty;
                victimData.bounty = 0;
                plugin.modules().economy().addMoney(killer.getUniqueId(), reward);
                Bukkit.broadcast(Text.component(Text.prefix() + "<#F472B6>" + killer.getName()
                    + " <gray>claimed <white>" + victim.getName() + "<gray>'s bounty of <green>$" + Text.format(reward) + "<gray>."));
            }
            plugin.data().markDirty(killer.getUniqueId());
        }
        plugin.data().markDirty(victim.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Long until = combatUntil.remove(event.getPlayer().getUniqueId());
        if (until == null || until <= System.currentTimeMillis()) return;
        if (!plugin.configs().get("pvp").getBoolean("combat-tag.logout-kill", true)) return;
        event.getPlayer().setHealth(0.0);
        Bukkit.broadcast(Text.component(Text.prefix() + "<red>" + event.getPlayer().getName() + " combat-logged."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isCombatTagged(event.getPlayer())) return;
        String base = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (plugin.configs().get("pvp").getStringList("combat-tag.blocked-commands").stream().anyMatch(base::equalsIgnoreCase)) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You cannot use that command while combat-tagged.");
        }
    }

    public boolean isCombatTagged(Player player) {
        return combatUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean bountyCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!plugin.configs().modules().getBoolean("modules.bounties", true)) {
            Text.send(player, "<red>Bounties are disabled.");
            return true;
        }

        if (args.length == 0) {
            Text.raw(player, "<#F472B6><bold>VUPE BOUNTIES</bold>");
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.data().player(p.getUniqueId()).bounty > 0)
                .sorted(Comparator.comparingDouble((Player p) -> plugin.data().player(p.getUniqueId()).bounty).reversed())
                .limit(10)
                .forEach(p -> Text.raw(player, " <dark_gray>• <white>" + p.getName() + " <gray>→ <green>$"
                    + Text.format(plugin.data().player(p.getUniqueId()).bounty)));
            return true;
        }

        if (args.length < 2) {
            Text.send(player, "<red>Usage: /bounty <player> <amount>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Text.send(player, "<red>You cannot bounty yourself.");
            return true;
        }
        double amount;
        try { amount = Double.parseDouble(args[1].replace(",", "")); }
        catch (NumberFormatException ex) { Text.send(player, "<red>Invalid amount."); return true; }

        double min = plugin.configs().get("pvp").getDouble("bounties.minimum", 1000);
        double max = plugin.configs().get("pvp").getDouble("bounties.maximum", 1_000_000_000);
        if (!Double.isFinite(amount) || amount < min || amount > max) {
            Text.send(player, "<red>Bounty must be between $" + Text.format(min) + " and $" + Text.format(max) + ".");
            return true;
        }
        if (!plugin.modules().economy().takeMoney(player.getUniqueId(), amount)) {
            Text.send(player, "<red>You do not have enough money.");
            return true;
        }
        PlayerData targetData = plugin.data().player(target.getUniqueId());
        targetData.bounty += amount;
        plugin.data().markDirty(target.getUniqueId());
        Bukkit.broadcast(Text.component(Text.prefix() + "<white>" + player.getName() + " <gray>placed <green>$"
            + Text.format(amount) + " <gray>on <#F472B6>" + (target.getName() == null ? target.getUniqueId() : target.getName()) + "<gray>."));
        return true;
    }

    private boolean kothCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.koth", false)) {
            Text.send(sender, "<red>KOTH is disabled in modules.yml.");
            return true;
        }

        if (args.length == 0) {
            Text.send(sender, "<gray>KOTH: " + (kothTask == null ? "<red>inactive" : "<green>active")
                + (kothCapturer == null ? "" : " <dark_gray>• <gray>capturer: <white>" + Bukkit.getOfflinePlayer(kothCapturer).getName()));
            return true;
        }

        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            startKoth();
            Text.send(sender, "<green>KOTH started.");
            return true;
        }

        if (sender instanceof Player player && args[0].equalsIgnoreCase("set") && args.length >= 2) {
            if (args[1].equalsIgnoreCase("1") || args[1].equalsIgnoreCase("2")) {
                plugin.data().server().locations.put("koth" + args[1], Locations.serialize(player.getLocation()));
                plugin.data().markServerDirty();
                Text.send(player, "<green>KOTH corner " + args[1] + " set.");
            }
        }
        return true;
    }

    private void startKoth() {
        if (kothTask != null) kothTask.cancel();
        kothCapturer = null;
        kothSeconds = 0;
        kothTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location a = Locations.deserialize(plugin.data().server().locations.get("koth1"));
            Location b = Locations.deserialize(plugin.data().server().locations.get("koth2"));
            if (a == null || b == null || a.getWorld() != b.getWorld()) return;

            List<Player> inside = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld() == a.getWorld() && inside(online.getLocation(), a, b)) {
                    inside.add(online);
                }
            }

            if (inside.size() != 1) {
                kothCapturer = null;
                kothSeconds = 0;
                return;
            }

            Player player = inside.getFirst();
            if (!player.getUniqueId().equals(kothCapturer)) {
                kothCapturer = player.getUniqueId();
                kothSeconds = 0;
            }

            kothSeconds++;
            player.sendActionBar(Text.component("<#F472B6>KOTH <gray>" + kothSeconds + "/"
                + plugin.configs().get("pvp").getInt("koth.capture-seconds", 180)));

            if (kothSeconds >= plugin.configs().get("pvp").getInt("koth.capture-seconds", 180)) {
                rewardKoth(player);
                if (kothTask != null) kothTask.cancel();
                kothTask = null;
                kothCapturer = null;
                kothSeconds = 0;
            }
        }, 20L, 20L);
    }

    private void rewardKoth(Player player) {
        double money = plugin.configs().get("pvp").getDouble("koth.reward.money", 500000);
        long crystals = plugin.configs().get("pvp").getLong("koth.reward.crystals", 100);
        String crate = plugin.configs().get("pvp").getString("koth.reward.crate", "event");
        int keys = plugin.configs().get("pvp").getInt("koth.reward.keys", 1);
        plugin.modules().economy().addMoney(player.getUniqueId(), money);
        plugin.modules().economy().addCrystals(player.getUniqueId(), crystals);
        plugin.modules().crates().addKeys(player.getUniqueId(), crate, keys);
        plugin.effects().broadcast(Text.prefix() + "<#F472B6>" + player.getName() + " <gray>captured KOTH.", "reward");
    }

    private boolean bossCommand(CommandSender sender, String label, String[] args) {
        if (!plugin.configs().modules().getBoolean("modules.bosses", false)) {
            Text.send(sender, "<red>Bosses are disabled in modules.yml.");
            return true;
        }
        if (args.length == 0) {
            Text.send(sender, "<gray>Use <white>/bosses spawn <id><gray> or <white>/bosses setspawn<gray>.");
            return true;
        }
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("setspawn") && sender instanceof Player player) {
            plugin.data().server().locations.put("boss", Locations.serialize(player.getLocation()));
            plugin.data().markServerDirty();
            Text.send(player, "<green>Boss spawn saved.");
            return true;
        }
        if (args[0].equalsIgnoreCase("spawn") && args.length >= 2) {
            spawnBoss(sender, args[1]);
        } else if (args[0].equalsIgnoreCase("mob") && args.length >= 2) {
            spawnCustomMob(sender, args[1]);
        }
        return true;
    }

    private void spawnCustomMob(CommandSender sender, String id) {
        if (!plugin.configs().modules().getBoolean("modules.custom-mobs", false)) {
            Text.send(sender, "<red>Custom mobs are disabled in modules.yml.");
            return;
        }
        String path = "custom-mobs.definitions." + id;
        if (!plugin.configs().get("pvp").contains(path)) {
            Text.send(sender, "<red>Unknown custom mob.");
            return;
        }
        Location loc = sender instanceof Player player ? player.getLocation()
            : Locations.deserialize(plugin.data().server().locations.get("pvp"));
        if (loc == null) return;

        EntityType type;
        try { type = EntityType.valueOf(plugin.configs().get("pvp").getString(path + ".entity", "ZOMBIE")); }
        catch (IllegalArgumentException ex) { Text.send(sender, "<red>Invalid entity type."); return; }

        Entity entity = loc.getWorld().spawnEntity(loc, type);
        if (entity instanceof LivingEntity living) {
            double health = plugin.configs().get("pvp").getDouble(path + ".max-health", 40);
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                living.setHealth(health);
            }
            living.customName(Text.component(plugin.configs().get("pvp").getString(path + ".display", id)));
            living.setCustomNameVisible(true);
            living.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_mob_id"),
                org.bukkit.persistence.PersistentDataType.STRING, id);
        }
    }

    private void spawnBoss(CommandSender sender, String id) {
        String path = "bosses.definitions." + id;
        if (!plugin.configs().get("pvp").contains(path)) {
            Text.send(sender, "<red>Unknown boss.");
            return;
        }
        Location loc = Locations.deserialize(plugin.data().server().locations.get("boss"));
        if (loc == null) {
            Text.send(sender, "<red>Set the boss spawn first.");
            return;
        }

        EntityType type;
        try { type = EntityType.valueOf(plugin.configs().get("pvp").getString(path + ".entity", "WITHER_SKELETON")); }
        catch (IllegalArgumentException ex) { Text.send(sender, "<red>Invalid boss entity type."); return; }

        Entity entity = loc.getWorld().spawnEntity(loc, type);
        if (entity instanceof LivingEntity living) {
            double health = plugin.configs().get("pvp").getDouble(path + ".max-health", 500);
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                living.setHealth(health);
            }
            living.customName(Text.component(plugin.configs().get("pvp").getString(path + ".display", id)));
            living.setCustomNameVisible(true);
            living.setPersistent(true);
            living.getPersistentDataContainer().set(new NamespacedKey(plugin, "boss_id"),
                org.bukkit.persistence.PersistentDataType.STRING, id);
        }
        Bukkit.broadcast(Text.component(Text.prefix() + "<#F472B6>A boss has spawned: "
            + plugin.configs().get("pvp").getString(path + ".display", id)));
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        String customMob = event.getEntity().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "custom_mob_id"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
        if (customMob != null) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                String mobPath = "custom-mobs.definitions." + customMob;
                plugin.modules().economy().addMoney(killer.getUniqueId(), plugin.configs().get("pvp").getDouble(mobPath + ".reward-money", 0));
                plugin.modules().economy().addCrystals(killer.getUniqueId(), plugin.configs().get("pvp").getLong(mobPath + ".reward-crystals", 0));
            }
            return;
        }

        String id = event.getEntity().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "boss_id"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
        if (id == null) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String path = "bosses.definitions." + id;
        plugin.modules().economy().addMoney(killer.getUniqueId(), plugin.configs().get("pvp").getDouble(path + ".reward-money", 0));
        plugin.modules().economy().addCrystals(killer.getUniqueId(), plugin.configs().get("pvp").getLong(path + ".reward-crystals", 0));
        String crate = plugin.configs().get("pvp").getString(path + ".reward-crate", "");
        if (!crate.isBlank()) plugin.modules().crates().addKeys(killer.getUniqueId(), crate, 1);
        plugin.effects().broadcast(Text.prefix() + "<white>" + killer.getName() + " <gray>defeated the boss.", "reward");
    }

    private boolean sameTeam(Player a, Player b) {
        String teamA = plugin.data().player(a.getUniqueId()).teamId;
        return teamA != null && !teamA.isBlank() && teamA.equals(plugin.data().player(b.getUniqueId()).teamId);
    }

    private static boolean inside(Location p, Location a, Location b) {
        if (p.getWorld() != a.getWorld() || a.getWorld() != b.getWorld()) return false;
        double minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        return p.getX() >= minX && p.getX() <= maxX
            && p.getY() >= minY && p.getY() <= maxY
            && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}

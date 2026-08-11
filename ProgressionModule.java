package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.util.*;

public final class ProgressionModule extends VupeModule {
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final List<Map<?, ?>> progression = new ArrayList<>();

    public ProgressionModule(VupeCore plugin) {
        super(plugin, "progression");
    }

    @Override
    protected void onEnable() {
        progression.clear();
        progression.addAll(plugin.configs().get("ranks").getMapList("progression"));

        plugin.commands().register("rankup", this::rankupCommand);
        plugin.commands().register("rank", this::rankAdminCommand);
        plugin.commands().register("kit", this::kitCommand);
        plugin.commands().register("start", this::startCommand);
        plugin.commands().register("daily", this::dailyCommand);
        plugin.commands().register("reclaim", this::reclaimCommand);
        plugin.commands().register("starterboost", this::starterBoostCommand);
        plugin.commands().register("level", this::levelCommand);
        plugin.commands().register("prestige", this::prestigeCommand);
        plugin.commands().register("resetreclaim", this::resetReclaimCommand);
        plugin.commands().register("resetfreeranks", this::resetStarterBoostCommand);

        for (Player player : Bukkit.getOnlinePlayers()) refreshPermissions(player);
    }

    @Override
    protected void onDisable() {
        for (PermissionAttachment attachment : attachments.values()) {
            try { attachment.remove(); } catch (Exception ignored) {}
        }
        attachments.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshPermissions(event.getPlayer()));
    }

    public String donorDisplay(String rank) {
        if (rank == null || rank.isBlank()) return "<gray>None";
        return plugin.configs().get("ranks").getString("donor-ranks." + rank.toLowerCase(Locale.ROOT) + ".display", rank);
    }

    public String donorPrefix(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.donorRank == null || data.donorRank.isBlank()) return "";
        return plugin.configs().get("ranks").getString("donor-ranks." + data.donorRank + ".prefix", "");
    }

    public String progressionPrefix(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.progressionRank == null || data.progressionRank.equalsIgnoreCase("starter")) {
            return "<dark_gray>[<gray>Starter<dark_gray>] ";
        }
        for (Map<?, ?> row : progression) {
            if (data.progressionRank.equalsIgnoreCase(String.valueOf(row.get("id")))) {
                return "<dark_gray>[" + String.valueOf(row.containsKey("display") ? row.get("display") : data.progressionRank) + "<dark_gray>] ";
            }
        }
        return "";
    }

    public void setDonorRank(Player player, String rank) {
        if (setDonorRank((OfflinePlayer) player, rank)) {
            refreshPermissions(player);
            plugin.effects().title(player,
                "<gradient:#8B5CF6:#F472B6><bold>RANK UPGRADE</bold></gradient>",
                "<gray>You are now " + donorDisplay(rank));
            plugin.effects().celebrate(player);
        }
    }

    public boolean setDonorRank(OfflinePlayer target, String rank) {
        if (target == null) return false;
        rank = rank.toLowerCase(Locale.ROOT).replace("+", "plus");
        ConfigurationSection section = plugin.configs().get("ranks").getConfigurationSection("donor-ranks." + rank);
        if (section == null) return false;

        PlayerData data = plugin.data().player(target.getUniqueId());
        String oldDonorRank = data.donorRank;

        data.generatorSlots = Math.max(0, data.generatorSlots - data.donorGrantedSlots);
        data.sellMultiplierBonus = Math.max(0, data.sellMultiplierBonus - data.donorGrantedSellBonus);

        int slotBonus = section.getInt("slots-bonus", 0);
        double sellBonus = section.getDouble("sell-multiplier-bonus", 0);

        data.donorRank = rank;
        data.donorGrantedSlots = slotBonus;
        data.donorGrantedSellBonus = sellBonus;
        data.generatorSlots += slotBonus;
        data.sellMultiplierBonus += sellBonus;

        plugin.data().markDirty(target.getUniqueId());
        plugin.luckPerms().syncDonor(target.getName(), oldDonorRank, rank);
        return true;
    }

    public void refreshPermissions(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { old.remove(); } catch (Exception ignored) {}
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);

        PlayerData data = plugin.data().player(player.getUniqueId());
        // Donor permissions/prefixes are owned by LuckPerms in Vupe MAX.
        // This transient attachment is reserved for progression-derived permissions
        // such as the effective PlotSquared plot count.

        // VupeCore does not require LuckPerms. When PlotSquared integration is enabled,
        // grant its official basic permission pack and the plot-count node through
        // the same Bukkit attachment used for Vupe donor permissions.
        if (plugin.configs().modules().getBoolean("modules.plots", true)
            && plugin.configs().get("worlds").getBoolean("worlds.plots.grant-basic-permissions", true)) {
            attachment.setPermission("plots.permpack.basic", true);

            int plotLimit = Math.max(1, plugin.configs().get("worlds").getInt("worlds.plots.default-plot-limit", 1));

            if (data.donorRank != null && !data.donorRank.isBlank()) {
                ConfigurationSection donor = plugin.configs().get("ranks").getConfigurationSection("donor-ranks." + data.donorRank);
                if (donor != null) plotLimit = Math.max(plotLimit, donor.getInt("plot-limit", plotLimit));
            }

            if (data.progressionRank != null && !data.progressionRank.isBlank()) {
                for (Map<?, ?> row : progression) {
                    if (!data.progressionRank.equalsIgnoreCase(String.valueOf(row.get("id")))) continue;
                    plotLimit = Math.max(plotLimit, (int) Math.round(number(row.get("plot-limit"), plotLimit)));
                    break;
                }
            }

            attachment.setPermission("plots.plot." + plotLimit, true);
        }

        if (player.hasPermission("vupe.admin")) {
            attachment.setPermission("vupe.staff", true);
        }
        player.recalculatePermissions();
        player.updateCommands();
    }

    private boolean rankupCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        int currentIndex = -1;
        if (!data.progressionRank.equalsIgnoreCase("starter")) {
            for (int i = 0; i < progression.size(); i++) {
                if (String.valueOf(progression.get(i).get("id")).equalsIgnoreCase(data.progressionRank)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex = currentIndex + 1;
        if (nextIndex >= progression.size()) {
            Text.send(player, "<#F472B6>You are already at the highest progression rank.");
            return true;
        }

        Map<?, ?> next = progression.get(nextIndex);
        String id = String.valueOf(next.get("id"));
        String display = String.valueOf(next.containsKey("display") ? next.get("display") : id);
        double cost = number(next.get("cost"), 0);

        if (args.length == 0) {
            plugin.modules().progressionGui().openRankTree(player);
            return true;
        }

        if (!args[0].equalsIgnoreCase("confirm")) {
            Text.send(player, "<red>Usage: /rankup [confirm]");
            return true;
        }

        if (!plugin.modules().economy().takeMoney(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You need <green>$" + Text.format(cost) + "<red>.");
            return true;
        }

        int slots = (int) Math.round(number(next.get("gen-slots"), 0));
        double sellBonus = number(next.get("sell-bonus"), 0);
        data.progressionRank = id;
        data.progressionGrantedSlots += slots;
        data.progressionGrantedSellBonus += sellBonus;
        data.generatorSlots += slots;
        data.sellMultiplierBonus += sellBonus;

        int voteKeys = (int) Math.round(number(next.get("vote-keys"), 0));
        if (voteKeys > 0) plugin.modules().crates().addKeys(player.getUniqueId(), "vote", voteKeys);

        double wand = number(next.get("sellwand"), 0);
        if (wand > 0) {
            ItemStack sellwand = dev.vupe.core.util.Items.tagged(
                Material.BLAZE_ROD,
                "<gold><bold>" + Text.format(wand) + "x Sellwand</bold>",
                List.of("<gray>Right-click a container to sell its contents."),
                "sellwand", Double.toString(wand)
            );
            player.getInventory().addItem(sellwand);
        }

        Object configuredRewards = next.get("rewards");
        if (configuredRewards instanceof List<?> rewardList) {
            for (Object object : rewardList) {
                if (!(object instanceof Map<?, ?> reward)) continue;
                plugin.modules().commerce().grant(
                    player,
                    mapString(reward, "type", ""),
                    mapString(reward, "value", ""),
                    number(reward.get("amount"), 1)
                );
            }
        }

        plugin.data().markDirty(player.getUniqueId());
        refreshPermissions(player);
        plugin.effects().broadcast(Text.prefix() + "<#22D3EE><bold>" + player.getName()
            + "</bold> <gray>advanced to " + display + "<gray>!", "broadcast");
        plugin.effects().title(player,
            "<gradient:#22D3EE:#8B5CF6><bold>RANK UP!</bold></gradient>",
            "<gray>You are now " + display);
        plugin.effects().celebrate(player);
        return true;
    }

    private boolean rankAdminCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            plugin.modules().progressionGui().openDonorRanks(player);
            return true;
        }
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            Text.send(sender, "<red>Usage: /rank give <player> <echo|cipher|phantom|titan|vupe+>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String rank = args[2].toLowerCase(Locale.ROOT).replace("+", "plus");
        if (!setDonorRank(target, rank)) {
            Text.send(sender, "<red>Unknown donor rank.");
            return true;
        }
        Text.send(sender, "<green>Set <white>" + args[1] + "<green>'s donor rank to " + donorDisplay(rank) + "<green>.");
        if (target.isOnline() && target.getPlayer() != null) {
            Player online = target.getPlayer();
            refreshPermissions(online);
            Text.send(online, "<gray>Your donor rank is now " + donorDisplay(rank) + "<gray>.");
            plugin.effects().celebrate(online);
        }
        return true;
    }

    private boolean kitCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /kit <echo|cipher|phantom|titan|vupeplus>");
            return true;
        }
        String kit = args[0].toLowerCase(Locale.ROOT).replace("+", "plus");
        if (!player.hasPermission("vupe.kit." + kit) && !player.hasPermission("vupe.admin")) {
            Text.send(player, "<red>You do not own that kit.");
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        long cooldownHours = plugin.configs().get("kits").getLong("kits." + kit + ".cooldown-hours", 24);
        long readyAt = data.cooldowns.getOrDefault("kit:" + kit, 0L);
        if (readyAt > System.currentTimeMillis()) {
            Text.send(player, "<red>That kit is still on cooldown for <white>"
                + dev.vupe.core.util.TimeUtil.pretty(readyAt - System.currentTimeMillis()) + "<red>.");
            return true;
        }

        if (!giveKit(player, kit)) {
            Text.send(player, "<red>Unknown kit.");
            return true;
        }
        data.cooldowns.put("kit:" + kit, System.currentTimeMillis() + cooldownHours * 3_600_000L);
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Kit claimed.");
        return true;
    }

    public boolean giveKit(Player player, String kit) {
        List<Map<?, ?>> items = plugin.configs().get("kits").getMapList("kits." + kit + ".items");
        if (items.isEmpty()) return false;
        for (Map<?, ?> map : items) {
            Material material = Material.matchMaterial(String.valueOf(map.get("material")));
            if (material == null) continue;
            int amount = Math.max(1, (int) Math.round(number(map.get("amount"), 1)));
            ItemStack stack = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
            Object enchantObj = map.get("enchants");
            if (enchantObj instanceof Map<?, ?> enchantMap) {
                for (Map.Entry<?, ?> entry : enchantMap.entrySet()) {
                    Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT)));
                    if (enchant != null) stack.addUnsafeEnchantment(enchant, (int) Math.round(number(entry.getValue(), 1)));
                }
            }
            player.getInventory().addItem(stack);
        }
        return true;
    }

    private boolean startCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.started) {
            Text.send(player, "<red>You already started your Vupe progression.");
            return true;
        }

        if (plugin.modules().plots().enabled()) {
            plugin.modules().plots().claimOrTeleport(player);
        }

        data.started = true;
        double money = plugin.configs().get("kits").getDouble("starter.money", 5000);
        plugin.modules().economy().addMoney(player.getUniqueId(), money);

        ConfigurationSection gens = plugin.configs().get("kits").getConfigurationSection("starter.generators");
        if (gens != null) {
            for (String id : gens.getKeys(false)) {
                plugin.modules().generators().give(player, id, gens.getInt(id, 1));
            }
        }

        for (Map<?, ?> map : plugin.configs().get("kits").getMapList("starter.items")) {
            Material material = Material.matchMaterial(String.valueOf(map.get("material")));
            if (material != null) {
                player.getInventory().addItem(new ItemStack(material, Math.max(1, (int) Math.round(number(map.get("amount"), 1)))));
            }
        }

        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Your Vupe journey has started.");
        return true;
    }

    private boolean dailyCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.donorRank == null || data.donorRank.isBlank()) {
            Text.send(player, "<red>Daily donor rewards require a donor rank.");
            return true;
        }
        long readyAt = data.cooldowns.getOrDefault("daily", 0L);
        if (readyAt > System.currentTimeMillis()) {
            Text.send(player, "<red>Your daily reward is ready in <white>"
                + dev.vupe.core.util.TimeUtil.pretty(readyAt - System.currentTimeMillis()) + "<red>.");
            return true;
        }

        ConfigurationSection cfg = plugin.configs().get("kits").getConfigurationSection("daily." + data.donorRank);
        if (cfg == null) return true;
        String crate = cfg.getString("crate", "vote");
        int keys = cfg.getInt("keys", 1);
        plugin.modules().crates().addKeys(player.getUniqueId(), crate, keys);
        data.cooldowns.put("daily", System.currentTimeMillis() + 86_400_000L);
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Daily claimed: <white>" + keys + " " + crate + " key(s)<green>.");
        return true;
    }

    private boolean reclaimCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.donorRank == null || data.donorRank.isBlank()) {
            Text.send(player, "<red>You need a donor rank.");
            return true;
        }
        String key = "reclaim:season:" + plugin.data().server().season;
        if (data.cooldowns.containsKey(key)) {
            Text.send(player, "<red>You already claimed this season's reclaim.");
            return true;
        }
        ConfigurationSection cfg = plugin.configs().get("kits").getConfigurationSection("reclaim." + data.donorRank);
        if (cfg == null) return true;
        data.generatorSlots += cfg.getInt("slots", 0);
        data.sellMultiplierBonus += cfg.getDouble("sell-bonus", 0);
        data.cooldowns.put(key, Long.MAX_VALUE);
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Season reclaim claimed.");
        return true;
    }

    private boolean starterBoostCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        PlayerData data = plugin.data().player(player.getUniqueId());
        String key = "starterboost:season:" + plugin.data().server().season;
        if (data.cooldowns.containsKey(key)) {
            Text.send(player, "<red>You already claimed the starter boost this season.");
            return true;
        }
        ConfigurationSection cfg = plugin.configs().get("kits").getConfigurationSection("starterboost");
        if (cfg == null) return true;
        plugin.modules().economy().addMoney(player.getUniqueId(), cfg.getDouble("money", 25000));
        data.generatorSlots += cfg.getInt("slots", 3);
        plugin.modules().crates().addKeys(player.getUniqueId(), cfg.getString("crate", "vote"), cfg.getInt("keys", 1));
        data.cooldowns.put(key, Long.MAX_VALUE);
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Starter boost claimed.");
        return true;
    }

    private boolean resetReclaimCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /resetreclaim <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        PlayerData data = plugin.data().player(target.getUniqueId());
        data.cooldowns.remove("reclaim:season:" + plugin.data().server().season);
        plugin.data().markDirty(target.getUniqueId());
        Text.send(sender, "<green>Reset this season's reclaim for <white>" + args[0] + "<green>.");
        return true;
    }

    private boolean resetStarterBoostCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length < 1) {
            Text.send(sender, "<red>Usage: /resetfreeranks <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        PlayerData data = plugin.data().player(target.getUniqueId());
        data.cooldowns.remove("starterboost:season:" + plugin.data().server().season);
        plugin.data().markDirty(target.getUniqueId());
        Text.send(sender, "<green>Reset this season's starter boost for <white>" + args[0] + "<green>.");
        return true;
    }

    private boolean levelCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        plugin.modules().progressionGui().openLevels(player);
        return true;
    }

    private boolean prestigeCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            plugin.modules().progressionGui().openPrestige(player);
            return true;
        }

        PlayerData data = plugin.data().player(player.getUniqueId());
        int requirement = plugin.configs().get("levels").getInt("prestige.requirement-level",
            plugin.configs().get("levels").getInt("leveling.max-level", 2500));
        int maxPrestige = plugin.configs().get("levels").getInt("prestige.max", 100);

        if (data.level < requirement) {
            Text.send(player, "<red>You need level <white>" + requirement + "<red> to prestige.");
            plugin.effects().error(player);
            return true;
        }
        if (data.prestige >= maxPrestige) {
            Text.send(player, "<#F472B6>You are at maximum prestige.");
            return true;
        }

        data.prestige++;
        if (plugin.configs().get("levels").getBoolean("prestige.reset-level", true)) {
            data.level = 1;
            data.xp = 0;
        }

        double crystalBase = plugin.configs().get("levels").getDouble("prestige.rewards.crystals-base", 25000);
        double crystalGrowth = plugin.configs().get("levels").getDouble("prestige.rewards.crystals-growth", 1.18);
        long crystals = Math.max(1L, Math.round(crystalBase * Math.pow(crystalGrowth, Math.max(0, data.prestige - 1))));
        plugin.modules().economy().addCrystals(player.getUniqueId(), crystals);

        double sell = plugin.configs().get("levels").getDouble("prestige.rewards.sell-multiplier-per-prestige", 0.025);
        int slots = plugin.configs().get("levels").getInt("prestige.rewards.gen-slots-per-prestige", 2);
        data.sellMultiplierBonus += sell;
        data.generatorSlots += slots;

        if (data.prestige % 5 == 0) {
            String crate = plugin.configs().get("levels").getString("prestige.rewards.crate-every-5", "vupe");
            plugin.modules().crates().addKeys(player.getUniqueId(), crate, 1);
        }

        plugin.data().markDirty(player.getUniqueId());
        plugin.effects().broadcast(Text.prefix() + "<#F472B6><bold>" + player.getName()
            + "</bold> <gray>reached <#8B5CF6>Prestige " + data.prestige + "<gray>!", "broadcast");
        plugin.effects().title(player,
            "<gradient:#F472B6:#8B5CF6><bold>PRESTIGE " + data.prestige + "</bold></gradient>",
            "<gray>+" + crystals + " Crystals • +" + Text.format(sell) + "x Sell • +" + slots + " Slots");
        plugin.effects().celebrate(player);
        return true;
    }

    public void addXp(Player player, long amount) {
        if (amount <= 0) return;
        PlayerData data = plugin.data().player(player.getUniqueId());
        int maxLevel = plugin.configs().get("levels").getInt("leveling.max-level", 2500);
        data.xp += amount;

        boolean leveled = false;
        int old = data.level;
        while (data.level < maxLevel && data.xp >= xpNeeded(data.level)) {
            data.xp -= xpNeeded(data.level);
            data.level++;
            leveled = true;
        }

        if (leveled) {
            plugin.effects().title(player,
                plugin.configs().get("levels").getString("leveling.level-up.title",
                    "<gradient:#22D3EE:#8B5CF6><bold>LEVEL UP!</bold></gradient>"),
                plugin.configs().get("levels").getString("leveling.level-up.subtitle",
                    "<gray>You reached level <white>%level%").replace("%level%", Integer.toString(data.level)));
            plugin.effects().sound(player, "level-up");
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0,1,0), 35, .7, .8, .7, .03);

            int every = Math.max(1, plugin.configs().get("levels").getInt("leveling.level-up.broadcast-every", 50));
            if (data.level / every > old / every) {
                plugin.effects().broadcast(Text.prefix() + "<#22D3EE>" + player.getName()
                    + " <gray>reached <white>Level " + data.level + "<gray>!", "broadcast");
            }
        }
        plugin.data().markDirty(player.getUniqueId());
    }

    private long xpNeeded(int level) {
        double base = plugin.configs().get("levels").getDouble("leveling.xp-base", 125);
        double growth = plugin.configs().get("levels").getDouble("leveling.xp-growth", 1.035);
        return Math.max(1L, Math.round(base * Math.pow(growth, Math.max(0, level - 1))));
    }

    private static String mapString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(value.toString()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}

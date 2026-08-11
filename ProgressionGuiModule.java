package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class ProgressionGuiModule extends VupeModule {
    private final Map<UUID, String> sessions = new HashMap<>();

    public ProgressionGuiModule(VupeCore plugin) {
        super(plugin, "progression-guis");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("levels", (s,l,a) -> {
            if (s instanceof Player p) openLevels(p); else Text.send(s, "<red>Player-only.");
            return true;
        });
        plugin.commands().register("perks", (s,l,a) -> {
            if (s instanceof Player p) openDonorRanks(p); else Text.send(s, "<red>Player-only.");
            return true;
        });
        plugin.commands().register("rewards", (s,l,a) -> {
            if (s instanceof Player p) openRewards(p, 0); else Text.send(s, "<red>Player-only.");
            return true;
        });
    }

    public void openRankTree(Player player) {
        List<Map<?, ?>> ranks = plugin.configs().get("ranks").getMapList("progression");
        PlayerData data = plugin.data().player(player.getUniqueId());
        int current = currentRankIndex(data, ranks);

        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#22D3EE:#8B5CF6><bold>VUPE RANK PATH</bold></gradient>"));
        decorate(inv);

        int start = Math.max(0, current - 2);
        if (start + 28 > ranks.size()) start = Math.max(0, ranks.size() - 28);
        int[] slots = contentSlots();

        for (int i = 0; i < Math.min(slots.length, ranks.size() - start); i++) {
            int index = start + i;
            Map<?, ?> row = ranks.get(index);
            String id = string(row, "id", "rank");
            String display = string(row, "display", id);
            double cost = number(row.get("cost"), 0);

            boolean completed = index <= current;
            boolean next = index == current + 1;
            Material icon = completed ? Material.LIME_DYE : next ? Material.LIGHT_BLUE_DYE : Material.GRAY_DYE;

            List<String> lore = new ArrayList<>();
            lore.add(completed ? "<green>✓ COMPLETED" : next ? "<#22D3EE>◆ NEXT RANK" : "<gray>◆ LOCKED");
            lore.add("");
            lore.add("<gray>Cost: <green>$" + Text.format(cost));
            lore.add("<gray>Generator slots: <#22D3EE>+" + Math.round(number(row.get("gen-slots"), 0)));
            lore.add("<gray>Sell multiplier: <#F472B6>+" + Text.format(number(row.get("sell-bonus"), 0)) + "x");
            lore.add("<gray>Plot limit: <#A78BFA>" + Math.round(number(row.get("plot-limit"), 1)));
            double wand = number(row.get("sellwand"), 0);
            if (wand > 0) lore.add("<gray>Sellwand: <gold>" + Text.format(wand) + "x");
            Object rewards = row.get("rewards");
            if (rewards instanceof List<?> list && !list.isEmpty()) {
                lore.add("");
                lore.add("<white><bold>RANK REWARDS</bold>");
                for (Object obj : list) {
                    if (obj instanceof Map<?, ?> reward) {
                        lore.add("<dark_gray>• <gray>" + pretty(string(reward, "type", "reward"))
                            + " <white>" + Text.format(number(reward.get("amount"), 1))
                            + (string(reward, "value", "").isBlank() ? "" : " <gray>(" + pretty(string(reward,"value","")) + ")"));
                    }
                }
            }
            if (next) {
                lore.add("");
                lore.add(plugin.modules().economy().money(player.getUniqueId()) >= cost
                    ? "<green><bold>CLICK TO RANK UP</bold>" : "<red>You cannot afford this yet.");
            }

            String action = next ? "rank-confirm" : "noop";
            inv.setItem(slots[i], Items.tagged(icon, display, lore, "progression_action", action));
        }

        inv.setItem(4, Items.item(Material.NETHER_STAR, "<white><bold>YOUR PROGRESSION</bold>",
            List.of("<gray>Current: " + currentDisplay(data, ranks),
                "<gray>Money: <green>$" + Text.format(plugin.modules().economy().money(player.getUniqueId())),
                "<gray>Level: <white>" + data.level + " <dark_gray>• <gray>Prestige: <#8B5CF6>" + data.prestige)));
        inv.setItem(49, Items.tagged(Material.EXPERIENCE_BOTTLE, "<#22D3EE><bold>LEVELS & REWARDS</bold>",
            List.of("<yellow>Click to open."), "progression_action", "levels"));

        sessions.put(player.getUniqueId(), "rank");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    public void openRankConfirm(Player player) {
        List<Map<?, ?>> ranks = plugin.configs().get("ranks").getMapList("progression");
        PlayerData data = plugin.data().player(player.getUniqueId());
        int next = currentRankIndex(data, ranks) + 1;
        if (next < 0 || next >= ranks.size()) return;
        Map<?, ?> row = ranks.get(next);

        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<#22D3EE><bold>CONFIRM RANKUP</bold>"));
        decorate(inv);
        inv.setItem(13, Items.item(Material.NETHER_STAR, string(row,"display","Next Rank"),
            List.of("<gray>Cost: <green>$" + Text.format(number(row.get("cost"),0)),
                "<gray>This permanently advances your Vupe rank.")));
        inv.setItem(11, Items.tagged(Material.LIME_CONCRETE, "<green><bold>CONFIRM</bold>",
            List.of("<gray>Purchase this rank now."), "progression_action", "rank-buy"));
        inv.setItem(15, Items.tagged(Material.RED_CONCRETE, "<red><bold>CANCEL</bold>",
            List.of("<gray>Return to the rank path."), "progression_action", "rank-tree"));
        sessions.put(player.getUniqueId(), "rankconfirm");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    public void openDonorRanks(Player player) {
        ConfigurationSection donors = plugin.configs().get("ranks").getConfigurationSection("donor-ranks");
        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#F472B6:#8B5CF6><bold>VUPE RANKS & PERKS</bold></gradient>"));
        decorate(inv);
        if (donors != null) {
            int slot = 20;
            for (String id : donors.getKeys(false)) {
                if (slot > 24) slot += 4;
                String path = id + ".";
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Generator slots: <#22D3EE>+" + donors.getInt(path+"slots-bonus",0));
                lore.add("<gray>Sell multiplier: <#F472B6>+" + Text.format(donors.getDouble(path+"sell-multiplier-bonus",0)) + "x");
                lore.add("<gray>Vaults: <white>" + donors.getInt(path+"vaults",0));
                lore.add("<gray>Plots: <#A78BFA>" + donors.getInt(path+"plot-limit",1));
                lore.add("");
                lore.add("<white><bold>PERMISSIONS</bold>");
                for (String permission : donors.getStringList(path+"permissions")) lore.add("<dark_gray>• <gray>" + permission);
                lore.add("");
                lore.add(plugin.data().player(player.getUniqueId()).donorRank.equalsIgnoreCase(id)
                    ? "<green>✓ YOUR CURRENT RANK" : "<yellow>View in /store or /crystalshop");
                inv.setItem(slot++, Items.tagged(Material.NETHER_STAR,
                    donors.getString(path+"display",id), lore, "progression_action", "store"));
            }
        }
        inv.setItem(49, Items.tagged(Material.COMPASS, "<#22D3EE><bold>IN-GAME RANK PATH</bold>",
            List.of("<yellow>Click to view progression ranks."), "progression_action", "rank-tree"));
        sessions.put(player.getUniqueId(), "donors");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    public void openLevels(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        long needed = xpNeeded(data.level);
        double progress = needed <= 0 ? 1 : Math.min(1.0, data.xp / (double) needed);

        Inventory inv = Bukkit.createInventory(null, 45,
            Text.component("<gradient:#22D3EE:#8B5CF6><bold>LEVEL PROGRESSION</bold></gradient>"));
        decorate(inv);
        inv.setItem(4, Items.item(Material.EXPERIENCE_BOTTLE, "<#22D3EE><bold>LEVEL " + data.level + "</bold>",
            List.of("<gray>XP: <white>" + data.xp + "<dark_gray>/<white>" + needed,
                "<gray>Prestige: <#8B5CF6>" + data.prestige,
                "<gray>Maximum level: <white>" + maxLevel())));

        for (int i = 0; i < 9; i++) {
            Material mat = i < Math.round(progress * 9) ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            inv.setItem(9+i, Items.item(mat, "<gray>XP Progress <white>" + Math.round(progress*100) + "%", List.of()));
        }

        inv.setItem(29, Items.tagged(Material.CHEST, "<#FBBF24><bold>MILESTONE REWARDS</bold>",
            List.of("<gray>Claim level rewards in the Reward Center.", "", "<yellow>Click to browse."),
            "progression_action", "rewards"));
        inv.setItem(31, Items.tagged(Material.NETHER_STAR, "<#F472B6><bold>PRESTIGE</bold>",
            List.of("<gray>Requirement: <white>Level " + prestigeRequirement(),
                "<gray>Your prestige: <white>" + data.prestige,
                "", "<yellow>Click to view prestige rewards."),
            "progression_action", "prestige"));
        inv.setItem(33, Items.tagged(Material.COMPASS, "<#22D3EE><bold>RANK PATH</bold>",
            List.of("<yellow>Click to browse progression ranks."), "progression_action", "rank-tree"));

        sessions.put(player.getUniqueId(), "levels");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    public void openPrestige(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        int next = data.prestige + 1;
        double crystals = prestigeCrystals(next);
        boolean ready = data.level >= prestigeRequirement()
            && data.prestige < plugin.configs().get("levels").getInt("prestige.max",100);

        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<#F472B6><bold>PRESTIGE</bold>"));
        decorate(inv);
        inv.setItem(13, Items.item(Material.NETHER_STAR,
            "<gradient:#F472B6:#8B5CF6><bold>PRESTIGE " + next + "</bold></gradient>",
            List.of("<gray>Requirement: <white>Level " + prestigeRequirement(),
                "<gray>Crystal reward: <#8B5CF6>" + Text.format(crystals),
                "<gray>Permanent sell bonus: <#F472B6>+"
                    + Text.format(plugin.configs().get("levels").getDouble("prestige.rewards.sell-multiplier-per-prestige",0.025)) + "x",
                "<gray>Generator slots: <#22D3EE>+"
                    + plugin.configs().get("levels").getInt("prestige.rewards.gen-slots-per-prestige",2),
                "",
                ready ? "<green>✓ READY TO PRESTIGE" : "<red>✗ Requirement not met")));

        inv.setItem(11, Items.tagged(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            ready ? "<green><bold>CONFIRM PRESTIGE</bold>" : "<gray><bold>LOCKED</bold>",
            List.of(ready ? "<yellow>Click to prestige." : "<gray>Reach the required level first."),
            "progression_action", ready ? "prestige-buy" : "noop"));
        inv.setItem(15, Items.tagged(Material.RED_CONCRETE, "<red><bold>BACK</bold>",
            List.of(), "progression_action", "levels"));
        sessions.put(player.getUniqueId(), "prestige");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    public void openRewards(Player player, int page) {
        ConfigurationSection rewards = plugin.configs().get("levels").getConfigurationSection("leveling.milestone-rewards");
        if (rewards == null) return;
        List<Integer> levels = rewards.getKeys(false).stream().map(id -> {
            try { return Integer.parseInt(id); } catch (NumberFormatException ex) { return -1; }
        }).filter(i -> i > 0).sorted().toList();

        int pageSize = 28;
        int maxPage = Math.max(0, (levels.size()-1)/pageSize);
        page = Math.max(0, Math.min(maxPage, page));
        Inventory inv = Bukkit.createInventory(null,54,Text.component("<#FBBF24><bold>LEVEL REWARDS</bold> <gray>• " + (page+1) + "/" + (maxPage+1)));
        decorate(inv);
        PlayerData data = plugin.data().player(player.getUniqueId());
        int[] slots = contentSlots();
        for (int i=0; i<pageSize; i++) {
            int index=page*pageSize+i;
            if (index>=levels.size()) break;
            int lvl=levels.get(index);
            String key=data.prestige+":"+lvl;
            boolean claimed=data.claimedLevelRewards.contains(key);
            boolean unlocked=data.level>=lvl;
            String path="leveling.milestone-rewards."+lvl+".";
            String type=plugin.configs().get("levels").getString(path+"type","MONEY");
            String value=plugin.configs().get("levels").getString(path+"value","");
            double amount=plugin.configs().get("levels").getDouble(path+"amount",1);
            Material icon=claimed?Material.LIME_DYE:unlocked?Material.CHEST:Material.GRAY_DYE;
            inv.setItem(slots[i],Items.tagged(icon,
                "<#FBBF24><bold>LEVEL "+lvl+"</bold>",
                List.of("<gray>Reward: <white>"+pretty(type)+" "+Text.format(amount)
                        +(value.isBlank()?"":" <gray>("+pretty(value)+")"),
                    "",claimed?"<green>✓ CLAIMED":unlocked?"<yellow>Click to claim!":"<red>Locked"),
                "progression_action", unlocked&&!claimed?"claim:"+lvl:"noop"));
        }
        if(page>0) inv.setItem(45,Items.tagged(Material.ARROW,"<white>← Previous",List.of(),"progression_action","rewardpage:"+(page-1)));
        if(page<maxPage) inv.setItem(53,Items.tagged(Material.ARROW,"<white>Next →",List.of(),"progression_action","rewardpage:"+(page+1)));
        inv.setItem(49,Items.tagged(Material.EXPERIENCE_BOTTLE,"<#22D3EE>Back to Levels",List.of(),"progression_action","levels"));
        sessions.put(player.getUniqueId(),"rewards");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    @EventHandler(ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !sessions.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        String action=Items.tag(event.getCurrentItem(),"progression_action");
        if(action==null||action.equals("noop")) return;
        plugin.effects().click(player);
        if(action.equals("rank-confirm")) openRankConfirm(player);
        else if(action.equals("rank-buy")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player,"rankup confirm");
        } else if(action.equals("rank-tree")) openRankTree(player);
        else if(action.equals("levels")) openLevels(player);
        else if(action.equals("rewards")) openRewards(player,0);
        else if(action.equals("prestige")) openPrestige(player);
        else if(action.equals("prestige-buy")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player,"prestige confirm");
        } else if(action.equals("store")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player,"store");
        } else if(action.startsWith("rewardpage:")) {
            try { openRewards(player,Integer.parseInt(action.substring(11))); } catch(NumberFormatException ignored){}
        } else if(action.startsWith("claim:")) {
            try { claim(player,Integer.parseInt(action.substring(6))); } catch(NumberFormatException ignored){}
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event){ sessions.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled=true) public void onDrag(InventoryDragEvent event){
        if(sessions.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    private void claim(Player player,int level){
        PlayerData data=plugin.data().player(player.getUniqueId());
        String key=data.prestige+":"+level;
        if(data.level<level||data.claimedLevelRewards.contains(key)){ plugin.effects().error(player); return; }
        String path="leveling.milestone-rewards."+level+".";
        String type=plugin.configs().get("levels").getString(path+"type","MONEY");
        String value=plugin.configs().get("levels").getString(path+"value","");
        double amount=plugin.configs().get("levels").getDouble(path+"amount",1);
        if(!plugin.modules().commerce().grant(player,type,value,amount)){
            Text.send(player,"<red>This milestone reward is misconfigured.");
            plugin.effects().error(player); return;
        }
        data.claimedLevelRewards.add(key);
        plugin.data().markDirty(player.getUniqueId());
        plugin.effects().title(player,"<#FBBF24><bold>REWARD CLAIMED</bold>","<gray>Level "+level+" milestone");
        plugin.effects().celebrate(player);
        openRewards(player,0);
    }

    private int currentRankIndex(PlayerData data,List<Map<?,?>> ranks){
        if(data.progressionRank==null||data.progressionRank.equalsIgnoreCase("starter")) return -1;
        for(int i=0;i<ranks.size();i++) if(data.progressionRank.equalsIgnoreCase(string(ranks.get(i),"id",""))) return i;
        return -1;
    }
    private String currentDisplay(PlayerData data,List<Map<?,?>> ranks){
        int i=currentRankIndex(data,ranks); return i<0?"<gray>Starter":string(ranks.get(i),"display","Starter");
    }
    private long xpNeeded(int level){
        double base=plugin.configs().get("levels").getDouble("leveling.xp-base",125);
        double growth=plugin.configs().get("levels").getDouble("leveling.xp-growth",1.035);
        return Math.max(1L,Math.round(base*Math.pow(growth,Math.max(0,level-1))));
    }
    private int maxLevel(){return plugin.configs().get("levels").getInt("leveling.max-level",2500);}
    private int prestigeRequirement(){return plugin.configs().get("levels").getInt("prestige.requirement-level",maxLevel());}
    private double prestigeCrystals(int next){
        double base=plugin.configs().get("levels").getDouble("prestige.rewards.crystals-base",25000);
        double growth=plugin.configs().get("levels").getDouble("prestige.rewards.crystals-growth",1.18);
        return base*Math.pow(growth,Math.max(0,next-1));
    }
    private static void decorate(Inventory inv){
        ItemStack glass=Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<inv.getSize();i++){int r=i/9,c=i%9;if(r==0||r==inv.getSize()/9-1||c==0||c==8)inv.setItem(i,glass);}
    }
    private static int[] contentSlots(){
        List<Integer> out=new ArrayList<>(); for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)out.add(r*9+c);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
    private static String string(Map<?,?> map,String key,String fallback){Object v=map.get(key);return v==null?fallback:String.valueOf(v);}
    private static double number(Object v,double fallback){if(v instanceof Number n)return n.doubleValue();try{return Double.parseDouble(String.valueOf(v));}catch(Exception e){return fallback;}}
    private static String pretty(String s){if(s==null)return"";StringBuilder b=new StringBuilder();for(String p:s.toLowerCase(Locale.ROOT).replace('_',' ').split(" ")){if(p.isBlank())continue;if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
}

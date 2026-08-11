package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.ServerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import dev.vupe.core.util.TimeUtil;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class StaffGuiModule extends VupeModule {
    private final Map<UUID, String> sessions = new HashMap<>();

    public StaffGuiModule(VupeCore plugin) {
        super(plugin, "staff-guis");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("staff", this::staffCommand);
        plugin.commands().register("staffrank", this::staffRankCommand);
    }

    private boolean staffCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("vupe.staff")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length > 0) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) openPlayer(player, target);
            else Text.send(player, "<red>That player is not online.");
        } else openMain(player);
        return true;
    }

    private boolean staffRankCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("vupe.admin")) {
            Text.send(sender, "<red>No permission.");
            return true;
        }
        if (args.length == 0) {
            openPlayerSelector(player, "rankselect");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(player, "<red>Player must be online.");
            return true;
        }
        openStaffRanks(player, target);
        return true;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#22D3EE:#F472B6><bold>VUPE STAFF CENTER</bold></gradient>"));
        decorate(inv);

        inv.setItem(10, action(Material.PLAYER_HEAD, "<#22D3EE><bold>PLAYER MANAGEMENT</bold>",
            List.of("<gray>Inspect online players, inventories,", "<gray>teleport, economy and punishments.", "", "<yellow>Click to browse."),
            "players"));
        inv.setItem(12, action(Material.IRON_AXE, "<#F472B6><bold>PUNISHMENTS</bold>",
            List.of("<gray>Choose a player and apply preset", "<gray>mutes/bans with confirmation GUIs.", "", "<yellow>Click to browse."),
            "punishselect"));
        inv.setItem(14, action(Material.WRITABLE_BOOK, "<#FBBF24><bold>REPORT QUEUE</bold>",
            List.of("<gray>Open reports with reporter, target,", "<gray>reason, age and resolution controls.", "", "<yellow>Click to open."),
            "reports"));
        inv.setItem(16, action(Material.NAME_TAG, "<#A78BFA><bold>STAFF RANKS</bold>",
            List.of("<gray>LuckPerms-backed staff rank manager.", "<gray>Admin permission required.", "", "<yellow>Click to browse."),
            "rankselect"));

        inv.setItem(29, action(Material.ENDER_EYE, "<#8B5CF6><bold>VANISH</bold>",
            List.of("<gray>Toggle your Vupe vanish state.", "", "<yellow>Click to toggle."),
            "vanish"));
        inv.setItem(31, action(Material.EMERALD, "<green><bold>ECONOMY ADMIN</bold>",
            List.of("<gray>Use Vupe's native economy administration", "<gray>with full /eco tab completion.", "", "<yellow>Click for economy guidance."),
            "econative"));
        inv.setItem(33, action(Material.BELL, "<#67E8F9><bold>STAFF CHAT</bold>",
            List.of("<gray>Use <white>/staffchat <message>", "<gray>for private staff communication."),
            "noop"));

        long reports = plugin.data().server().reports.values().stream().filter(r -> "OPEN".equals(r.status)).count();
        inv.setItem(4, Items.item(Material.NETHER_STAR, "<white><bold>STAFF OVERVIEW</bold>",
            List.of("<gray>Your staff group: <white>" + pretty(plugin.luckPerms().staffGroup(player)),
                "<gray>Online staff: <white>" + Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("vupe.staff")).count(),
                "<gray>Open reports: <#FBBF24>" + reports,
                "<gray>Active punishments: <#F472B6>" + plugin.data().server().punishments.values().stream().filter(r -> r.active).count())));

        sessions.put(player.getUniqueId(), "main");
        player.openInventory(inv);
        plugin.effects().open(player);
    }

    private void openPlayerSelector(Player staff, String action) {
        Inventory inv = Bukkit.createInventory(null, 54, Text.component("<#22D3EE><bold>SELECT A PLAYER</bold>"));
        decorate(inv);
        int[] slots = contentSlots();
        int i = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (i >= slots.length) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Text.component("<white><bold>" + target.getName() + "</bold>"));
            meta.lore(List.of(
                Text.component("<gray>World: <white>" + target.getWorld().getName()),
                Text.component("<gray>Money: <green>$" + Text.format(plugin.modules().economy().money(target.getUniqueId()))),
                Text.component("<gray>Rank: <white>" + plugin.data().player(target.getUniqueId()).progressionRank),
                Text.component(""),
                Text.component("<yellow>Click to select.")
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "staff_action"),
                org.bukkit.persistence.PersistentDataType.STRING, action + ":" + target.getUniqueId());
            head.setItemMeta(meta);
            inv.setItem(slots[i++], head);
        }
        inv.setItem(49, action(Material.ARROW, "<gray>Back", List.of(), "main"));
        sessions.put(staff.getUniqueId(), "selector");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    private void openPlayer(Player staff, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<gradient:#22D3EE:#8B5CF6><bold>MANAGE " + target.getName() + "</bold></gradient>"));
        decorate(inv);
        String id = target.getUniqueId().toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Text.component("<white><bold>" + target.getName() + "</bold>"));
        meta.lore(List.of(
            Text.component("<gray>UUID: <dark_gray>" + id),
            Text.component("<gray>World: <white>" + target.getWorld().getName()),
            Text.component("<gray>Money: <green>$" + Text.format(plugin.modules().economy().money(target.getUniqueId()))),
            Text.component("<gray>Crystals: <#8B5CF6>" + plugin.data().player(target.getUniqueId()).crystals),
            Text.component("<gray>Progression: <white>" + plugin.data().player(target.getUniqueId()).progressionRank),
            Text.component("<gray>Donor: <white>" + plugin.data().player(target.getUniqueId()).donorRank)
        ));
        head.setItemMeta(meta);
        inv.setItem(4, head);

        inv.setItem(10, action(Material.ENDER_PEARL, "<#22D3EE><bold>TELEPORT TO PLAYER</bold>",
            List.of("<gray>Teleport to " + target.getName() + "."), "tp:" + id));
        inv.setItem(12, action(Material.CHEST, "<#67E8F9><bold>VIEW INVENTORY</bold>",
            List.of("<gray>Open read-only inventory view."), "invsee:" + id));
        inv.setItem(14, action(Material.ENDER_CHEST, "<#A78BFA><bold>EDIT INVENTORY</bold>",
            List.of("<gray>Open editable staff inventory view."), "editinv:" + id));
        inv.setItem(16, action(Material.IRON_AXE, "<#F472B6><bold>PUNISH PLAYER</bold>",
            List.of("<gray>Open preset punishment menu."), "punish:" + id));

        inv.setItem(29, action(Material.BOOK, "<#FBBF24><bold>PUNISHMENT HISTORY</bold>",
            List.of("<gray>Browse all historical records."), "history:" + id));
        inv.setItem(31, action(Material.EMERALD, "<green><bold>ECONOMY</bold>",
            List.of("<gray>Suggested: <white>/eco give " + target.getName() + " <amount>",
                "<gray>Tab completion fills actions/players/amounts."), "noop"));
        inv.setItem(33, action(Material.NAME_TAG, "<#A78BFA><bold>STAFF RANK</bold>",
            List.of("<gray>Assign a LuckPerms staff group."), "ranks:" + id));

        inv.setItem(49, action(Material.ARROW, "<gray>Back to players", List.of(), "players"));
        sessions.put(staff.getUniqueId(), "player");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    public void openPunishPresets(Player staff, OfflinePlayer target) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Text.component("<#F472B6><bold>PUNISH " + safeName(target) + "</bold>"));
        decorate(inv);

        ConfigurationSection presets = plugin.configs().get("staff").getConfigurationSection("punishment-presets");
        if (presets != null) {
            int[] slots = contentSlots();
            int i = 0;
            for (String id : presets.getKeys(false)) {
                if (i >= slots.length) break;
                Material material = Material.matchMaterial(presets.getString(id + ".icon", "PAPER"));
                if (material == null) material = Material.PAPER;
                String action = presets.getString(id + ".action", "MUTE");
                String duration = presets.getString(id + ".duration", "15m");
                String reason = presets.getString(id + ".reason", id);
                inv.setItem(slots[i++], Items.tagged(material,
                    presets.getString(id + ".display", pretty(id)),
                    List.of("<gray>Action: <white>" + action,
                        "<gray>Duration: <white>" + duration,
                        "<gray>Reason: <white>" + reason,
                        "", "<yellow>Click for confirmation."),
                    "staff_action", "punishconfirm:" + target.getUniqueId() + ":" + id));
            }
        }
        inv.setItem(49, action(Material.ARROW, "<gray>Back", List.of(), "players"));
        sessions.put(staff.getUniqueId(), "punish");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    private void openPunishConfirm(Player staff, OfflinePlayer target, String preset) {
        ConfigurationSection cfg = plugin.configs().get("staff").getConfigurationSection("punishment-presets." + preset);
        if (cfg == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, Text.component("<red><bold>CONFIRM PUNISHMENT</bold>"));
        decorate(inv);
        inv.setItem(13, Items.item(Material.IRON_AXE, cfg.getString("display", pretty(preset)),
            List.of("<gray>Player: <white>" + safeName(target),
                "<gray>Action: <white>" + cfg.getString("action","MUTE"),
                "<gray>Duration: <white>" + cfg.getString("duration","15m"),
                "<gray>Reason: <white>" + cfg.getString("reason",preset))));
        inv.setItem(11, Items.tagged(Material.LIME_CONCRETE, "<green><bold>CONFIRM</bold>",
            List.of("<red>This action is logged."), "staff_action",
            "punishapply:" + target.getUniqueId() + ":" + preset));
        inv.setItem(15, Items.tagged(Material.RED_CONCRETE, "<red><bold>CANCEL</bold>",
            List.of(), "staff_action", "punish:" + target.getUniqueId()));
        sessions.put(staff.getUniqueId(), "punishconfirm");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    public void openHistory(Player staff, OfflinePlayer target, int page) {
        List<ServerData.PunishmentRecord> rows = plugin.data().server().punishments.values().stream()
            .filter(r -> r.target.equals(target.getUniqueId().toString()))
            .sorted(Comparator.comparingLong((ServerData.PunishmentRecord r) -> r.createdAt).reversed())
            .toList();
        int pageSize = 28, maxPage = Math.max(0, (rows.size()-1)/pageSize);
        page = Math.max(0, Math.min(maxPage, page));

        Inventory inv = Bukkit.createInventory(null, 54, Text.component("<#A78BFA><bold>PUNISHMENT HISTORY</bold> <gray>• " + safeName(target)));
        decorate(inv);
        int[] slots = contentSlots();
        for (int i=0;i<pageSize;i++) {
            int index=page*pageSize+i;
            if(index>=rows.size()) break;
            ServerData.PunishmentRecord r=rows.get(index);
            Material icon=r.type.equals("BAN")?Material.BARRIER:r.type.equals("MUTE")?Material.PAPER:Material.IRON_AXE;
            String remaining = !r.active ? "<green>Inactive" :
                r.expiresAt<=0 ? "<red>Permanent" : r.expiresAt<=System.currentTimeMillis() ? "<gray>Expired" :
                    "<yellow>"+TimeUtil.pretty(r.expiresAt-System.currentTimeMillis());
            inv.setItem(slots[i],Items.item(icon,
                (r.active?"<red>":"<gray>")+r.type+" <dark_gray>• <white>"+r.reason,
                List.of("<gray>Actor: <white>"+r.actor,
                    "<gray>Status: "+remaining,
                    "<gray>Created: <white>"+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.createdAt)),
                    "<dark_gray>ID: "+r.id)));
        }
        if(page>0) inv.setItem(45,Items.tagged(Material.ARROW,"<white>← Previous",List.of(),"staff_action","historypage:"+target.getUniqueId()+":"+(page-1)));
        if(page<maxPage) inv.setItem(53,Items.tagged(Material.ARROW,"<white>Next →",List.of(),"staff_action","historypage:"+target.getUniqueId()+":"+(page+1)));
        inv.setItem(49,Items.tagged(Material.IRON_AXE,"<#F472B6>New Punishment",List.of(),"staff_action","punish:"+target.getUniqueId()));
        sessions.put(staff.getUniqueId(),"history");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    public void openReports(Player staff) {
        List<ServerData.ReportRecord> rows = plugin.data().server().reports.values().stream()
            .filter(r -> "OPEN".equals(r.status))
            .sorted(Comparator.comparingLong((ServerData.ReportRecord r) -> r.createdAt))
            .toList();
        Inventory inv=Bukkit.createInventory(null,54,Text.component("<#FBBF24><bold>OPEN REPORTS</bold>"));
        decorate(inv);
        int[] slots=contentSlots();
        for(int i=0;i<Math.min(rows.size(),slots.length);i++){
            ServerData.ReportRecord r=rows.get(i);
            OfflinePlayer reporter=Bukkit.getOfflinePlayer(UUID.fromString(r.reporter));
            OfflinePlayer target=Bukkit.getOfflinePlayer(UUID.fromString(r.target));
            inv.setItem(slots[i],Items.tagged(Material.WRITABLE_BOOK,
                "<#FBBF24><bold>REPORT</bold> <white>"+safeName(target),
                List.of("<gray>Reporter: <white>"+safeName(reporter),
                    "<gray>Target: <white>"+safeName(target),
                    "<gray>Reason: <white>"+r.reason,
                    "<gray>Age: <white>"+TimeUtil.pretty(System.currentTimeMillis()-r.createdAt),
                    "", "<yellow>Left click: manage target", "<green>Right click: close report"),
                "staff_action","report:"+r.id));
        }
        inv.setItem(49,action(Material.ARROW,"<gray>Back",List.of(),"main"));
        sessions.put(staff.getUniqueId(),"reports");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    public void openStaffRanks(Player staff, Player target) {
        Inventory inv=Bukkit.createInventory(null,54,Text.component("<#A78BFA><bold>STAFF RANK • "+target.getName()+"</bold>"));
        decorate(inv);
        ConfigurationSection section=plugin.configs().get("staff").getConfigurationSection("staff-ranks");
        if(section!=null){
            int[] slots=contentSlots(); int i=0;
            List<String> ids=new ArrayList<>(section.getKeys(false));
            ids.sort(Comparator.comparingInt(id->section.getInt(id+".weight",0)));
            for(String id:ids){
                if(i>=slots.length)break;
                inv.setItem(slots[i++],Items.tagged(Material.NAME_TAG,
                    section.getString(id+".display",pretty(id)),
                    List.of("<gray>Weight: <white>"+section.getInt(id+".weight",0),
                        "<gray>Permissions: <white>"+section.getStringList(id+".permissions").size(),
                        "", target.hasPermission("group."+id)?"<green>✓ CURRENT / INHERITED":"<yellow>Click to assign"),
                    "staff_action","staffrank:"+target.getUniqueId()+":"+id));
            }
        }
        inv.setItem(48,Items.tagged(Material.BARRIER,"<red><bold>REMOVE STAFF RANK</bold>",List.of(),"staff_action","staffrank:"+target.getUniqueId()+":none"));
        inv.setItem(49,action(Material.ARROW,"<gray>Back",List.of(),"players"));
        sessions.put(staff.getUniqueId(),"ranks");
        staff.openInventory(inv);
        plugin.effects().open(staff);
    }

    @EventHandler(ignoreCancelled=true)
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player staff)||!sessions.containsKey(staff.getUniqueId()))return;
        event.setCancelled(true);
        String action=Items.tag(event.getCurrentItem(),"staff_action");
        if(action==null||action.equals("noop"))return;
        plugin.effects().click(staff);

        if(action.equals("main"))openMain(staff);
        else if(action.equals("players"))openPlayerSelector(staff,"manage");
        else if(action.equals("punishselect"))openPlayerSelector(staff,"punish");
        else if(action.equals("rankselect")){
            if(staff.hasPermission("vupe.admin"))openPlayerSelector(staff,"rank");
            else {Text.send(staff,"<red>Admin permission required.");plugin.effects().error(staff);}
        }
        else if(action.equals("reports"))openReports(staff);
        else if(action.equals("vanish")){staff.closeInventory();Bukkit.dispatchCommand(staff,"vanish");}
        else if(action.equals("econative")){
            staff.closeInventory();
            Text.raw(staff, "<gradient:#22D3EE:#8B5CF6><bold>VUPE ECONOMY ADMIN</bold></gradient>");
            Text.raw(staff, "<gray>/eco <set|give|take|reset|add|remove> <player> [amount]");
            Text.raw(staff, "<gray>/baltop <dark_gray>• <gray>/balance <player>");
        }
        else handleEncoded(staff,action,event.getClick());
    }

    private void handleEncoded(Player staff,String action,ClickType click){
        String[] p=action.split(":",4);
        try{
            switch(p[0]){
                case "manage" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null)openPlayer(staff,target);}
                case "punish" -> openPunishPresets(staff,Bukkit.getOfflinePlayer(UUID.fromString(p[1])));
                case "punishconfirm" -> openPunishConfirm(staff,Bukkit.getOfflinePlayer(UUID.fromString(p[1])),p[2]);
                case "punishapply" -> applyPreset(staff,Bukkit.getOfflinePlayer(UUID.fromString(p[1])),p[2]);
                case "history" -> openHistory(staff,Bukkit.getOfflinePlayer(UUID.fromString(p[1])),0);
                case "historypage" -> openHistory(staff,Bukkit.getOfflinePlayer(UUID.fromString(p[1])),Integer.parseInt(p[2]));
                case "rank","ranks" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null)openStaffRanks(staff,target);}
                case "staffrank" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null)assignRank(staff,target,p[2]);}
                case "tp" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null){staff.closeInventory();staff.teleportAsync(target.getLocation());}}
                case "invsee" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null){staff.closeInventory();Bukkit.dispatchCommand(staff,"invsee "+target.getName());}}
                case "editinv" -> {Player target=Bukkit.getPlayer(UUID.fromString(p[1]));if(target!=null){staff.closeInventory();Bukkit.dispatchCommand(staff,"editinv "+target.getName());}}
                case "report" -> handleReport(staff,p[1],click);
            }
        }catch(Exception ex){plugin.getLogger().warning("Staff GUI action failed: "+action+" -> "+ex.getMessage());plugin.effects().error(staff);}
    }

    private void applyPreset(Player staff,OfflinePlayer target,String preset){
        ConfigurationSection cfg=plugin.configs().get("staff").getConfigurationSection("punishment-presets."+preset);
        if(cfg==null)return;
        String action=cfg.getString("action","MUTE").toLowerCase(Locale.ROOT);
        String duration=cfg.getString("duration","15m");
        String reason=cfg.getString("reason",preset);
        staff.closeInventory();
        Bukkit.dispatchCommand(staff,"punish "+action+" "+safeName(target)+" "+duration+" "+reason);
        plugin.effects().sound(staff,"staff-alert");
    }

    private void assignRank(Player staff,Player target,String rank){
        if(!staff.hasPermission("vupe.admin")){plugin.effects().error(staff);return;}
        plugin.luckPerms().setStaffRank(target,rank);
        plugin.effects().title(staff,"<#A78BFA><bold>STAFF RANK UPDATED</bold>","<gray>"+target.getName()+" → "+pretty(rank));
        plugin.effects().success(staff);
        openStaffRanks(staff,target);
    }

    private void handleReport(Player staff,String id,ClickType click){
        ServerData.ReportRecord report=plugin.data().server().reports.get(id);
        if(report==null)return;
        if(click.isRightClick()){
            Bukkit.dispatchCommand(staff,"punish closereport "+id);
            plugin.effects().success(staff);
            openReports(staff);
        }else{
            OfflinePlayer target=Bukkit.getOfflinePlayer(UUID.fromString(report.target));
            openPunishPresets(staff,target);
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event){sessions.remove(event.getPlayer().getUniqueId());}
    @EventHandler(ignoreCancelled=true) public void onDrag(InventoryDragEvent event){if(sessions.containsKey(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}

    private static ItemStack action(Material material,String name,List<String> lore,String action){
        return Items.tagged(material,name,lore,"staff_action",action);
    }
    private static void decorate(Inventory inv){
        ItemStack glass=Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<inv.getSize();i++){int r=i/9,c=i%9;if(r==0||r==inv.getSize()/9-1||c==0||c==8)inv.setItem(i,glass);}
    }
    private static int[] contentSlots(){
        List<Integer> slots=new ArrayList<>();for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)slots.add(r*9+c);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
    private static String safeName(OfflinePlayer p){return p.getName()==null?p.getUniqueId().toString():p.getName();}
    private static String pretty(String s){if(s==null)return"";StringBuilder b=new StringBuilder();for(String p:s.replace('_',' ').split(" ")){if(p.isBlank())continue;if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));}return b.toString();}
}

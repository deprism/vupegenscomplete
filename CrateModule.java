package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import dev.vupe.core.util.TimeUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateModule extends VupeModule {
    public record Reward(String id,double weight,String type,double amount,String value,String display,String command){}
    public record Crate(String id,String display,Material keyMaterial,List<String> keyLore,String animationStyle,List<Reward> rewards){}

    private record View(String mode,String crate,int page){}
    private final Map<String,Crate> crates=new LinkedHashMap<>();
    private final Map<UUID,View> views=new HashMap<>();
    private final Set<UUID> opening=new HashSet<>();
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Map<UUID,BukkitTask> animations=new HashMap<>();

    public CrateModule(VupeCore plugin){super(plugin,"crates");}

    @Override protected void onEnable(){
        loadCrates();
        plugin.commands().register("crates",this::command);
        rebuildHolograms();
        Bukkit.getScheduler().runTaskLater(plugin,()->Bukkit.getOnlinePlayers().forEach(this::deliverPending),20L);
    }

    @Override protected void onDisable(){
        animations.values().forEach(BukkitTask::cancel);
        animations.clear();opening.clear();views.clear();cooldowns.clear();
    }

    public Collection<Crate> crates(){return Collections.unmodifiableCollection(crates.values());}
    public Crate crate(String id){return id==null?null:crates.get(id.toLowerCase(Locale.ROOT));}

    private void loadCrates(){
        crates.clear();
        ConfigurationSection definitions=plugin.configs().get("crates").getConfigurationSection("crates.definitions");
        if(definitions==null)return;
        for(String raw:definitions.getKeys(false)){
            String id=raw.toLowerCase(Locale.ROOT),path="crates.definitions."+raw;
            String display=plugin.configs().get("crates").getString(path+".display",pretty(id));
            Material key=Material.matchMaterial(plugin.configs().get("crates").getString(path+".key-material","PRISMARINE_SHARD"));
            if(key==null)key=Material.PRISMARINE_SHARD;
            List<String> lore=plugin.configs().get("crates").getStringList(path+".key-lore");
            if(lore.isEmpty())lore=List.of("<gray>Use this at the matching Vupe crate.");
            String style=plugin.configs().get("crates").getString(path+".animation-style","ROULETTE").toUpperCase(Locale.ROOT);
            List<Reward> rewards=new ArrayList<>();
            for(Map<?,?> row:plugin.configs().get("crates").getMapList(path+".rewards")){
                rewards.add(new Reward(
                    string(row.get("id"),UUID.randomUUID().toString().substring(0,8)),
                    Math.max(0,number(row.get("weight"),1)),
                    string(row.get("type"),"MONEY").toUpperCase(Locale.ROOT),
                    number(row.get("amount"),1),
                    string(row.get("value"),""),
                    string(row.get("display"),string(row.get("id"),"Reward")),
                    string(row.get("command"),"")
                ));
            }
            crates.put(id,new Crate(id,display,key,lore,style,rewards));
        }
    }

    public void addKeys(UUID uuid,String crateId,int amount){
        Crate crate=crate(crateId);if(crate==null||amount==0)return;
        PlayerData data=plugin.data().player(uuid);
        data.addKeys(crate.id(),amount);
        plugin.data().markDirty(uuid);
    }

    public ItemStack physicalKey(String crateId,int amount){
        Crate c=crate(crateId);if(c==null)return null;
        ItemStack item=Items.tagged(c.keyMaterial(),c.display()+" <gray>Key",c.keyLore(),"crate_key",c.id());
        item.setAmount(Math.max(1,Math.min(amount,item.getMaxStackSize())));
        return item;
    }

    @EventHandler(ignoreCancelled=true)
    public void onCrateClick(PlayerInteractEvent event){
        if(event.getClickedBlock()==null)return;
        String crateId=crateAt(event.getClickedBlock().getLocation());
        if(crateId==null)return;
        if(event.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK){
            event.setCancelled(true);openPreview(event.getPlayer(),crateId,0);return;
        }
        if(event.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){
            event.setCancelled(true);open(event.getPlayer(),crateId,1);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event){Bukkit.getScheduler().runTaskLater(plugin,()->deliverPending(event.getPlayer()),10L);}

    private String crateAt(Location location){
        String key=Locations.blockKey(location);
        for(Map.Entry<String,String> e:plugin.data().server().crateLocations.entrySet()){
            Location loc=Locations.deserialize(e.getValue());
            if(loc!=null&&Locations.blockKey(loc).equals(key))return e.getKey().toLowerCase(Locale.ROOT);
        }return null;
    }

    public void openFree(Player player,String crateId){
        Crate c=crate(crateId);if(c==null){Text.send(player,"<red>Unknown reward table.");return;}
        Reward reward=choose(c);if(reward==null){Text.send(player,"<red>This crate has no valid rewards.");return;}
        grant(player,c,reward);
    }

    public void open(Player player,String crateId,int count){
        Crate c=crate(crateId);
        if(c==null){Text.send(player,"<red>Unknown crate.");plugin.effects().error(player);return;}
        count=Math.max(1,Math.min(count,plugin.configs().get("crates").getInt("crates.max-mass-open",25)));
        long now=System.currentTimeMillis(),cd=Math.max(0,plugin.configs().get("crates").getLong("crates.open-cooldown-ms",900));
        long ready=cooldowns.getOrDefault(player.getUniqueId(),0L);
        if(ready>now){Text.send(player,"<red>You can open another crate in <white>"+TimeUtil.pretty(ready-now)+"<red>.");return;}
        if(opening.contains(player.getUniqueId())){Text.send(player,"<red>You are already opening a crate.");return;}

        int available=keyCount(player,c);
        if(available<count){
            Text.send(player,"<red>You need <white>"+count+"× "+c.display()+" <red>key(s). You have <white>"+available+"<red>.");
            plugin.effects().error(player);return;
        }

        List<Reward> selected=new ArrayList<>();
        for(int i=0;i<count;i++){Reward r=choose(c);if(r!=null)selected.add(r);}
        if(selected.size()!=count){Text.send(player,"<red>This crate has no valid rewards.");return;}
        if(!consumeKeys(player,c,count)){Text.send(player,"<red>Your keys changed; try again.");return;}

        PlayerData data=plugin.data().player(player.getUniqueId());
        for(Reward r:selected)data.pendingCrateRewards.add(c.id()+"|"+r.id());
        plugin.data().markDirty(player.getUniqueId());
        cooldowns.put(player.getUniqueId(),now+cd);

        if(count>1){
            opening.add(player.getUniqueId());
            runMassAnimation(player,c,selected);
        }else{
            opening.add(player.getUniqueId());
            runAnimation(player,c,selected.getFirst());
        }
    }

    private int keyCount(Player player,Crate c){
        int virtual=plugin.data().player(player.getUniqueId()).keyCount(c.id());
        int physical=0;
        for(ItemStack item:player.getInventory().getContents()){
            if(item!=null&&c.id().equalsIgnoreCase(Items.tag(item,"crate_key")))physical+=item.getAmount();
        }
        return virtual+physical;
    }

    private boolean consumeKeys(Player player,Crate c,int amount){
        int left=amount;
        boolean virtualFirst=plugin.configs().get("crates").getBoolean("crates.virtual-keys-first",true);
        if(virtualFirst){
            PlayerData data=plugin.data().player(player.getUniqueId());
            int take=Math.min(left,data.keyCount(c.id()));if(take>0){data.addKeys(c.id(),-take);left-=take;plugin.data().markDirty(player.getUniqueId());}
        }
        if(left>0)left=consumePhysical(player,c.id(),left);
        if(left>0&&!virtualFirst){
            PlayerData data=plugin.data().player(player.getUniqueId());
            int take=Math.min(left,data.keyCount(c.id()));if(take>0){data.addKeys(c.id(),-take);left-=take;plugin.data().markDirty(player.getUniqueId());}
        }
        return left==0;
    }

    private int consumePhysical(Player player,String id,int needed){
        int left=needed;
        ItemStack[] contents=player.getInventory().getContents();
        for(int i=0;i<contents.length&&left>0;i++){
            ItemStack item=contents[i];if(item==null||!id.equalsIgnoreCase(Items.tag(item,"crate_key")))continue;
            int take=Math.min(left,item.getAmount());item.setAmount(item.getAmount()-take);left-=take;
            if(item.getAmount()<=0)contents[i]=null;
        }
        player.getInventory().setContents(contents);
        return left;
    }

    private void runAnimation(Player player,Crate c,Reward winner){
        boolean enabled=plugin.configs().get("crates").getBoolean("crates.animation.enabled",true);
        if(!enabled){finish(player,c,List.of(winner));return;}
        int duration=Math.max(20,plugin.configs().get("crates").getInt("crates.animation.duration-ticks",70));
        int step=Math.max(1,plugin.configs().get("crates").getInt("crates.animation.step-ticks",3));
        Inventory inv=Bukkit.createInventory(null,27,Text.component(c.display()+" <dark_gray>• <white>"+c.animationStyle()));
        for(int i=0;i<27;i++)inv.setItem(i,Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(4,Items.item(c.keyMaterial(),c.display(),List.of("<gray>Rolling your reward...")));
        player.openInventory(inv);

        views.put(player.getUniqueId(),new View("animation",c.id(),0));

        BukkitTask task=new BukkitRunnable(){
            int elapsed=0;
            @Override public void run(){
                if(!player.isOnline()){opening.remove(player.getUniqueId());animations.remove(player.getUniqueId());cancel();return;}
                if(elapsed>=duration){
                    inv.setItem(13,rewardIcon(c,winner,true));
                    try{player.playSound(player.getLocation(),Sound.valueOf(plugin.configs().get("crates").getString("crates.animation.finish-sound","UI_TOAST_CHALLENGE_COMPLETE")),1f,1f);}catch(Exception ignored){}
                    if(plugin.configs().get("crates").getBoolean("crates.animation.particles",true)){
                        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,player.getLocation().add(0,1,0),45,.6,.8,.6,.08);
                    }
                    Bukkit.getScheduler().runTaskLater(plugin,()->finish(player,c,List.of(winner)),12L);
                    animations.remove(player.getUniqueId());cancel();return;
                }
                Reward random=c.rewards().isEmpty()?winner:c.rewards().get(ThreadLocalRandom.current().nextInt(c.rewards().size()));
                int center=13;
                inv.setItem(center,rewardIcon(c,random,false));
                if(c.animationStyle().equals("WHEEL")||c.animationStyle().equals("CASINO")){
                    for(int slot=10;slot<=16;slot++){
                        Reward roll=c.rewards().get(ThreadLocalRandom.current().nextInt(c.rewards().size()));
                        inv.setItem(slot,rewardIcon(c,roll,false));
                    }
                    inv.setItem(center,rewardIcon(c,random,false));
                }
                if(c.animationStyle().equals("COSMIC")){
                    player.sendActionBar(Text.component("<gradient:#8B5CF6:#F472B6>"+random.display()+"</gradient>"));
                    if(elapsed%9==0)player.getWorld().spawnParticle(Particle.END_ROD,player.getLocation().add(0,1,0),8,.4,.5,.4,.02);
                }
                try{
                    Sound sound=Sound.valueOf(plugin.configs().get("crates").getString("crates.animation.sound","BLOCK_NOTE_BLOCK_PLING"));
                    float pitch=Math.min(2f,.7f+(elapsed/(float)duration)*1.2f);player.playSound(player.getLocation(),sound,.5f,pitch);
                }catch(Exception ignored){}
                elapsed+=step;
            }
        }.runTaskTimer(plugin,0L,step);
        animations.put(player.getUniqueId(),task);
    }

    private void runMassAnimation(Player player,Crate c,List<Reward> rewards){
        Inventory inv=Bukkit.createInventory(null,54,Text.component(c.display()+" <dark_gray>• <white>MASS OPEN ×"+rewards.size()));
        for(int i=0;i<54;i++)inv.setItem(i,Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of()));
        player.openInventory(inv);views.put(player.getUniqueId(),new View("animation",c.id(),0));
        final int[] index={0};
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!player.isOnline()){BukkitTask cur=animations.remove(player.getUniqueId());if(cur!=null)cur.cancel();opening.remove(player.getUniqueId());return;}
            if(index[0]>=rewards.size()){
                BukkitTask cur=animations.remove(player.getUniqueId());if(cur!=null)cur.cancel();
                Bukkit.getScheduler().runTaskLater(plugin,()->finish(player,c,rewards),10L);return;
            }
            int[] slots=contentSlots();
            Reward r=rewards.get(index[0]);
            if(index[0]<slots.length)inv.setItem(slots[index[0]],rewardIcon(c,r,true));
            plugin.effects().sound(player,"crate");index[0]++;
        },0L,5L);
        animations.put(player.getUniqueId(),task);
    }

    private void finish(Player player,Crate c,List<Reward> rewards){
        if(!player.isOnline()){opening.remove(player.getUniqueId());return;}
        for(Reward r:rewards)grant(player,c,r);
        opening.remove(player.getUniqueId());
        views.remove(player.getUniqueId());
        player.closeInventory();
        if(rewards.size()==1){
            plugin.effects().title(player,"<gradient:#8B5CF6:#F472B6><bold>CRATE REWARD</bold></gradient>",rewards.getFirst().display());
        }else{
            plugin.effects().title(player,"<gradient:#8B5CF6:#F472B6><bold>MASS OPEN COMPLETE</bold></gradient>",
                "<gray>"+rewards.size()+" rewards delivered");
        }
    }

    public void grant(Player player,Crate c,Reward reward){
        boolean success;
        if(reward.type().equals("COMMAND")){
            String cmd=reward.command().replace("%player%",player.getName()).replace("%uuid%",player.getUniqueId().toString());
            success=!cmd.isBlank();if(success)Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);
        }else if(reward.type().equals("ITEM")){
            Material material=Material.matchMaterial(reward.value());success=material!=null;
            if(success)giveItem(player,new ItemStack(material,Math.max(1,Math.min(64,(int)Math.round(reward.amount())))));
        }else{
            success=plugin.modules().commerce().grant(player,reward.type(),reward.value(),reward.amount());
        }
        if(!success){
            removePending(player,c.id(),reward.id());
            addKeys(player.getUniqueId(),c.id(),1);
            Text.send(player,"<red>A crate reward was invalid, so a "+c.display()+" <red>key was refunded.");
            return;
        }
        removePending(player,c.id(),reward.id());
        Text.send(player,"<gray>You won "+reward.display()+" <gray>from "+c.display()+"<gray>!");
        plugin.effects().sound(player,"reward");
    }

    private void giveItem(Player player,ItemStack item){
        Map<Integer,ItemStack> overflow=player.getInventory().addItem(item);
        overflow.values().forEach(drop->player.getWorld().dropItemNaturally(player.getLocation(),drop));
    }

    private void removePending(Player player,String crate,String reward){
        PlayerData data=plugin.data().player(player.getUniqueId());
        data.pendingCrateRewards.remove(crate+"|"+reward);plugin.data().markDirty(player.getUniqueId());
    }

    private void deliverPending(Player player){
        PlayerData data=plugin.data().player(player.getUniqueId());
        if(data.pendingCrateRewards.isEmpty())return;
        List<String> pending=new ArrayList<>(data.pendingCrateRewards);
        for(String token:pending){
            String[] p=token.split("\\|",2);if(p.length!=2){data.pendingCrateRewards.remove(token);continue;}
            Crate c=crate(p[0]);Reward r=c==null?null:c.rewards().stream().filter(x->x.id().equals(p[1])).findFirst().orElse(null);
            if(c==null||r==null){
                data.pendingCrateRewards.remove(token);
                if(c!=null)data.addKeys(c.id(),1);
                continue;
            }
            grant(player,c,r);
        }
        plugin.data().markDirty(player.getUniqueId());
        if(!pending.isEmpty())Text.send(player,"<#22D3EE>Recovered pending crate reward(s) from your previous session.");
    }

    private Reward choose(Crate c){
        double total=c.rewards().stream().mapToDouble(r->Math.max(0,r.weight())).sum();if(total<=0)return null;
        double roll=ThreadLocalRandom.current().nextDouble(total),cursor=0;
        for(Reward r:c.rewards()){cursor+=Math.max(0,r.weight());if(roll<cursor)return r;}
        return c.rewards().isEmpty()?null:c.rewards().getLast();
    }

    public void openRoot(Player player){
        Inventory inv=Bukkit.createInventory(null,54,Text.component("<gradient:#8B5CF6:#22D3EE><bold>VUPE CRATES</bold></gradient>"));
        decorate(inv);int[] slots=contentSlots();int i=0;
        PlayerData data=plugin.data().player(player.getUniqueId());
        for(Crate c:crates.values()){
            if(i>=slots.length)break;
            int keys=data.keyCount(c.id());int physical=physicalCount(player,c.id());
            inv.setItem(slots[i++],Items.tagged(c.keyMaterial(),c.display(),
                List.of("<gray>Virtual keys: <white>"+keys,"<gray>Physical keys: <white>"+physical,
                    "<gray>Rewards: <white>"+c.rewards().size(),"","<yellow>Left click: preview","<#22D3EE>Right click: open"),
                "crate_action","crate:"+c.id()));
        }
        inv.setItem(49,Items.item(Material.NETHER_STAR,"<white><bold>VUPE CRATES</bold>",
            List.of("<gray>Physical blocks: left-click preview,","<gray>right-click with a key or virtual key to open.")));
        player.openInventory(inv);views.put(player.getUniqueId(),new View("root","",0));plugin.effects().open(player);
    }

    public void openPreview(Player player,String crateId,int page){
        Crate c=crate(crateId);if(c==null)return;
        List<Reward> rewards=new ArrayList<>(c.rewards());rewards.sort(Comparator.comparingDouble(Reward::weight).reversed());
        int pageSize=Math.max(7,plugin.configs().get("crates").getInt("crates.preview.page-size",28));
        int maxPage=Math.max(0,(rewards.size()-1)/pageSize);page=Math.max(0,Math.min(maxPage,page));
        Inventory inv=Bukkit.createInventory(null,54,Text.component(c.display()+" <dark_gray>• <white>REWARDS"));
        decorate(inv);int[] slots=contentSlots();double total=c.rewards().stream().mapToDouble(r->Math.max(0,r.weight())).sum();
        int from=page*pageSize;
        for(int i=0;i<Math.min(pageSize,rewards.size()-from)&&i<slots.length;i++){
            Reward r=rewards.get(from+i);double chance=total<=0?0:r.weight()/total*100.0;
            inv.setItem(slots[i],rewardIcon(c,r,false,List.of("<gray>Chance: <#22D3EE>"+String.format(Locale.US,"%.3f%%",chance),
                "<gray>Weight: <white>"+Text.format(r.weight()),"<dark_gray>ID: "+r.id())));
        }
        inv.setItem(45,Items.tagged(Material.ARROW,"<white>← Crates",List.of(),"crate_action","root"));
        if(page>0)inv.setItem(48,Items.tagged(Material.SPECTRAL_ARROW,"<white>← Previous",List.of(),"crate_action","preview:"+c.id()+":"+(page-1)));
        inv.setItem(49,Items.tagged(c.keyMaterial(),"<#22D3EE><bold>OPEN "+strip(c.display())+"</bold>",
            List.of("<gray>You have <white>"+keyCount(player,c)+" <gray>total key(s).","<yellow>Click to open."),
            "crate_action","open:"+c.id()+":1"));
        if(page<maxPage)inv.setItem(50,Items.tagged(Material.SPECTRAL_ARROW,"<white>Next →",List.of(),"crate_action","preview:"+c.id()+":"+(page+1)));
        player.openInventory(inv);views.put(player.getUniqueId(),new View("preview",c.id(),page));plugin.effects().open(player);
    }

    private ItemStack rewardIcon(Crate c,Reward r,boolean winner){return rewardIcon(c,r,winner,List.of());}
    private ItemStack rewardIcon(Crate c,Reward r,boolean winner,List<String> extra){
        Material mat=switch(r.type()){
            case "MONEY"->Material.EMERALD;case "CRYSTALS"->Material.AMETHYST_SHARD;case "GOLD"->Material.GOLD_INGOT;
            case "GENERATOR"->Material.RESPAWN_ANCHOR;case "GEN_SLOTS"->Material.HOPPER;case "SELL_MULTIPLIER"->Material.BEACON;
            case "CRATE_KEY"->c.keyMaterial();case "AUTOSELL_CHEST"->Material.CHEST;case "SELLWAND"->Material.BLAZE_ROD;
            case "RANK"->Material.NETHER_STAR;case "TAG"->Material.NAME_TAG;case "LOOTBOX"->Material.SHULKER_BOX;
            default->Material.CHEST;
        };
        List<String> lore=new ArrayList<>();if(winner)lore.add("<green><bold>★ WINNER ★</bold>");lore.addAll(extra);
        return Items.item(mat,r.display(),lore);
    }

    private int physicalCount(Player player,String crate){
        int count=0;for(ItemStack item:player.getInventory().getContents())if(item!=null&&crate.equalsIgnoreCase(Items.tag(item,"crate_key")))count+=item.getAmount();
        return count;
    }

    @EventHandler(ignoreCancelled=true)
    public void onGuiClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)||!views.containsKey(player.getUniqueId()))return;
        event.setCancelled(true);
        if(event.getClickedInventory()==null||event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
        if(views.get(player.getUniqueId()).mode().equals("animation"))return;
        String action=Items.tag(event.getCurrentItem(),"crate_action");if(action==null)return;
        plugin.effects().click(player);
        if(action.equals("root")){openRoot(player);return;}
        String[] p=action.split(":");
        if(p[0].equals("crate")&&p.length>=2){if(event.isRightClick())open(player,p[1],1);else openPreview(player,p[1],0);}
        else if(p[0].equals("preview")&&p.length>=3)openPreview(player,p[1],parseInt(p[2],0));
        else if(p[0].equals("open")&&p.length>=3)open(player,p[1],parseInt(p[2],1));
    }

    @EventHandler public void onGuiClose(InventoryCloseEvent event){
        View view=views.get(event.getPlayer().getUniqueId());
        if(view!=null&&!view.mode().equals("animation"))views.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(ignoreCancelled=true)public void onDrag(InventoryDragEvent event){if(views.containsKey(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}

    private boolean command(CommandSender sender,String label,String[] args){
        if(args.length==0){if(sender instanceof Player p)openRoot(p);else help(sender);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(sender instanceof Player player){
            if(sub.equals("open")){if(args.length<2){Text.send(player,"<red>/crates open <crate> [amount]");return true;}open(player,args[1],args.length>=3?parseInt(args[2],1):1);return true;}
            if(sub.equals("preview")){if(args.length<2){Text.send(player,"<red>/crates preview <crate>");return true;}openPreview(player,args[1],0);return true;}
        }
        if(!sender.hasPermission("vupe.admin")){Text.send(sender,"<red>No permission.");return true;}
        switch(sub){
            case "list"->Text.send(sender,"<gray>Crates: <white>"+String.join(", ",crates.keySet()));
            case "reload"->{loadCrates();rebuildHolograms();Text.send(sender,"<green>Crates reloaded.");}
            case "setloc"->{
                if(!(sender instanceof Player p)||args.length<2||crate(args[1])==null){Text.send(sender,"<red>/crates setloc <crate>");break;}
                Block block=p.getTargetBlockExact(6);if(block==null){Text.send(p,"<red>Look at a block.");break;}
                plugin.data().server().crateLocations.put(args[1].toLowerCase(Locale.ROOT),Locations.serialize(block.getLocation()));
                plugin.data().markServerDirty();rebuildHolograms();Text.send(p,"<green>Set "+args[1]+" crate location.");
            }
            case "give","key"->{
                if(args.length<4||crate(args[1])==null){Text.send(sender,"<red>/crates give <crate> <player> <amount>");break;}
                OfflinePlayer target=Bukkit.getOfflinePlayer(args[2]);int amount=Math.max(1,parseInt(args[3],1));
                addKeys(target.getUniqueId(),args[1],amount);Text.send(sender,"<green>Gave <white>"+amount+"× "+args[1]+" <green>virtual key(s) to <white>"+args[2]+"<green>.");
                if(target.getPlayer()!=null)plugin.effects().success(target.getPlayer());
            }
            case "physicalkey"->{
                if(args.length<4||crate(args[1])==null){Text.send(sender,"<red>/crates physicalkey <crate> <player> <amount>");break;}
                Player target=Bukkit.getPlayerExact(args[2]);if(target==null){Text.send(sender,"<red>Player must be online.");break;}
                int amount=Math.max(1,parseInt(args[3],1));int left=amount;
                while(left>0){ItemStack item=physicalKey(args[1],Math.min(left,64));giveItem(target,item);left-=item.getAmount();}
                Text.send(sender,"<green>Gave physical keys.");
            }
            case "keyall","giveall"->{
                if(args.length<3||crate(args[1])==null){Text.send(sender,"<red>/crates keyall <crate> <amount>");break;}
                int amount=Math.max(1,parseInt(args[2],1));startKeyall(sender,args[1],amount);
            }
            case "openfree"->{
                if(args.length<3){Text.send(sender,"<red>/crates openfree <crate> <player>");break;}
                Player target=Bukkit.getPlayerExact(args[2]);if(target==null||crate(args[1])==null){Text.send(sender,"<red>Invalid player/crate.");break;}
                openFree(target,args[1]);
            }
            default->help(sender);
        }return true;
    }

    private void startKeyall(CommandSender sender,String crateId,int amount){
        Crate c=crate(crateId);if(c==null)return;
        List<Integer> marks=plugin.configs().get("crates").getIntegerList("crates.keyall.countdown-seconds");
        if(marks.isEmpty())marks=List.of(15,5,2,1);
        int total=marks.stream().max(Integer::compareTo).orElse(15);
        for(int mark:marks){
            long delay=Math.max(0,total-mark)*20L;
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                plugin.effects().broadcast(Text.prefix()+"<#FBBF24><bold>KEY ALL</bold> <gray>"+amount+"× "+c.display()+" <gray>in <white>"+mark+"s<gray>!","countdown");
            },delay);
        }
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            for(Player p:Bukkit.getOnlinePlayers()){addKeys(p.getUniqueId(),c.id(),amount);plugin.effects().sound(p,"reward");}
            plugin.effects().broadcast(Text.prefix()+"<green>Everyone received <white>"+amount+"× "+c.display()+" <green>key(s)!","broadcast");
        },total*20L);
        Text.send(sender,"<green>Keyall scheduled.");
    }

    private void help(CommandSender sender){
        Text.raw(sender,"<gradient:#8B5CF6:#22D3EE><bold>VUPE CRATES</bold></gradient>");
        Text.raw(sender,"<gray>/crates <dark_gray>• <white>GUI");
        Text.raw(sender,"<gray>/crates preview <crate>");
        Text.raw(sender,"<gray>/crates open <crate> [amount]");
        if(sender.hasPermission("vupe.admin")){
            Text.raw(sender,"<gray>/crates setloc <crate>");
            Text.raw(sender,"<gray>/crates give <crate> <player> <amount>");
            Text.raw(sender,"<gray>/crates physicalkey <crate> <player> <amount>");
            Text.raw(sender,"<gray>/crates keyall <crate> <amount>");
            Text.raw(sender,"<gray>/crates reload");
        }
    }

    private void rebuildHolograms(){
        NamespacedKey key=new NamespacedKey(plugin,"crate_hologram");
        for(World world:Bukkit.getWorlds())for(TextDisplay display:world.getEntitiesByClass(TextDisplay.class)){
            if(display.getPersistentDataContainer().has(key,PersistentDataType.STRING))display.remove();
        }
        if(!plugin.configs().get("crates").getBoolean("crates.holograms.enabled",true))return;
        double height=plugin.configs().get("crates").getDouble("crates.holograms.height",1.65);
        for(Map.Entry<String,String> e:plugin.data().server().crateLocations.entrySet()){
            Crate c=crate(e.getKey());Location loc=Locations.deserialize(e.getValue());if(c==null||loc==null||loc.getWorld()==null)continue;
            TextDisplay d=loc.getWorld().spawn(loc.clone().add(.5,height,.5),TextDisplay.class);
            d.setBillboard(Display.Billboard.CENTER);d.setSeeThrough(true);d.setShadowed(true);
            d.text(Text.component(c.display()+"\n<gray>Left-click <white>Preview <dark_gray>• <gray>Right-click <white>Open"));
            d.getPersistentDataContainer().set(key,PersistentDataType.STRING,c.id());
        }
    }

    private static void decorate(Inventory inv){
        ItemStack pane=Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());int rows=inv.getSize()/9;
        for(int i=0;i<inv.getSize();i++){int r=i/9,c=i%9;if(r==0||r==rows-1||c==0||c==8)inv.setItem(i,pane);}
    }
    private static int[] contentSlots(){List<Integer> list=new ArrayList<>();for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)list.add(r*9+c);return list.stream().mapToInt(Integer::intValue).toArray();}
    private static String string(Object v,String d){return v==null?d:String.valueOf(v);}
    private static double number(Object v,double d){if(v instanceof Number n)return n.doubleValue();try{return Double.parseDouble(String.valueOf(v));}catch(Exception e){return d;}}
    private static int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static String pretty(String raw){StringBuilder b=new StringBuilder();for(String p:raw.toLowerCase(Locale.ROOT).replace('_',' ').split(" ")){if(p.isBlank())continue;if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
    private static String strip(String raw){return raw==null?"Crate":raw.replaceAll("<[^>]+>","").replace("&l","").replace("&n","");}
}

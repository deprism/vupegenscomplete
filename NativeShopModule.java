package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.Items;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;

public final class NativeShopModule extends VupeModule {
    private record Session(String category, int page, String mode, String material, int amount) {}
    private final Map<UUID, Session> sessions = new HashMap<>();

    public NativeShopModule(VupeCore plugin) {
        super(plugin, "native-shop");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("shop", this::command);
    }

    @Override
    protected void onDisable() {
        sessions.clear();
    }

    private boolean command(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Player-only.");
            return true;
        }
        if (args.length == 0) {
            openRoot(player);
            return true;
        }
        String category = args[0].toLowerCase(Locale.ROOT);
        if (category.equals("gens") || category.equals("gen")) category = "generators";
        if (categories() == null || !categories().contains(category)) {
            Text.send(player, "<red>Unknown shop category. Use <white>/shop<red>.");
            return true;
        }
        openCategory(player, category, 0);
        return true;
    }

    private void openRoot(Player player) {
        ConfigurationSection root = plugin.configs().get("shops").getConfigurationSection("native-shop");
        if (root == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, Text.component(root.getString("title",
            "<gradient:#8B5CF6:#22D3EE><bold>VUPE SHOP</bold></gradient>")));
        fill(inv);

        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                int slot = cats.getInt(id + ".slot", -1);
                Material icon = Material.matchMaterial(cats.getString(id + ".icon","CHEST"));
                if (slot < 0 || slot >= inv.getSize() || icon == null) continue;
                List<String> lore = new ArrayList<>(cats.getStringList(id + ".lore"));
                lore.add("");
                lore.add("<yellow>Click to browse →");
                inv.setItem(slot, Items.tagged(icon, cats.getString(id+".display",pretty(id)), lore,
                    "native_shop_action","category:"+id+":0"));
            }
        }

        inv.setItem(22, Items.tagged(Material.EMERALD,
            "<green><bold>SELL INVENTORY</bold>",
            List.of("<gray>Sell all supported normal items and", "<gray>generator output using your current multiplier.",
                "", "<gray>Multiplier: <white>"+Text.format(plugin.modules().shop().effectiveSellMultiplier(player))+"x",
                "<yellow>Click to sell."),
            "native_shop_action","sellall"));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(), new Session("",0,"","","".length()));
        plugin.effects().open(player);
    }

    private void openCategory(Player player, String category, int page) {
        ConfigurationSection cat = plugin.configs().get("shops").getConfigurationSection("native-shop.categories."+category);
        if (cat == null) { openRoot(player); return; }

        List<Offer> offers = offers(category, cat);
        int pageSize = Math.max(7, plugin.configs().get("shops").getInt("native-shop.page-size",28));
        int maxPage = Math.max(0,(offers.size()-1)/pageSize);
        page = Math.max(0,Math.min(maxPage,page));

        String title = plugin.configs().get("shops").getString("native-shop.category-title",
            "<gradient:#8B5CF6:#22D3EE><bold>VUPE SHOP</bold></gradient> <dark_gray>• <white>%category%")
            .replace("%category%", strip(cat.getString("display",pretty(category))));
        Inventory inv=Bukkit.createInventory(null,54,Text.component(title));
        decorate(inv);

        int[] slots=contentSlots();
        int from=page*pageSize;
        for(int i=0;i<Math.min(pageSize,offers.size()-from)&&i<slots.length;i++){
            Offer o=offers.get(from+i);
            List<String> lore=new ArrayList<>();
            if(o.generator){
                lore.add("<gray>Generator core: <white>"+o.display);
                lore.add("<gray>Drop value: <green>$"+Text.format(o.sell));
                lore.add("<gray>Buy: <green>$"+Text.format(o.buy));
                lore.add("");
                lore.add("<yellow>Left click: buy 1");
                lore.add("<yellow>Shift-left: buy 64");
            }else{
                lore.add("<gray>Buy: <green>$"+Text.format(o.buy)+" <dark_gray>/ each");
                lore.add(o.sell>0?"<gray>Sell: <green>$"+Text.format(o.sell)+" <dark_gray>/ each":"<gray>Sell: <red>Not sellable here");
                lore.add("");
                lore.add("<yellow>Left click: buy quantity");
                lore.add(o.sell>0?"<yellow>Right click: sell quantity":"<dark_gray>Right click: unavailable");
                lore.add("<gray>Shift-click: quick 64");
            }
            inv.setItem(slots[i],Items.tagged(o.material,
                o.generator?"<#22D3EE><bold>"+o.display+" Core</bold>":"<white><bold>"+pretty(o.material.name())+"</bold>",
                lore,"native_shop_action","offer:"+category+":"+page+":"+o.key));
        }

        inv.setItem(45,Items.tagged(Material.ARROW,"<#67E8F9><bold>← CATEGORIES</bold>",List.of(),"native_shop_action","root"));
        if(page>0) inv.setItem(48,Items.tagged(Material.SPECTRAL_ARROW,"<white>← Previous Page",List.of(),"native_shop_action","category:"+category+":"+(page-1)));
        inv.setItem(49,Items.item(Material.PAPER,"<white><bold>PAGE "+(page+1)+" / "+(maxPage+1)+"</bold>",
            List.of("<gray>"+offers.size()+" products in this category.")));
        if(page<maxPage) inv.setItem(50,Items.tagged(Material.SPECTRAL_ARROW,"<white>Next Page →",List.of(),"native_shop_action","category:"+category+":"+(page+1)));
        inv.setItem(53,Items.tagged(Material.EMERALD,"<green><bold>SELL INVENTORY</bold>",
            List.of("<gray>Multiplier: <white>"+Text.format(plugin.modules().shop().effectiveSellMultiplier(player))+"x",
                "<yellow>Click to sell all supported items."),"native_shop_action","sellall"));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(),new Session(category,page,"","",0));
        plugin.effects().open(player);
    }

    private void openQuantity(Player player,String category,int page,Offer offer,String mode,int amount){
        amount=Math.max(1,Math.min(2304,amount));
        String rawTitle=plugin.configs().get("shops").getString("native-shop.quantity-title",
            "<gradient:#22D3EE:#8B5CF6><bold>%mode%</bold></gradient> <dark_gray>• <white>%item%");
        String title=rawTitle.replace("%mode%",mode.equals("buy")?"BUY":"SELL").replace("%item%",offer.display);
        Inventory inv=Bukkit.createInventory(null,45,Text.component(title));
        decorate(inv);

        int[] addSlots={10,11,12,13,14};
        int[] deltas={1,16,32,64,128};
        for(int i=0;i<deltas.length;i++){
            inv.setItem(addSlots[i],Items.tagged(Material.LIME_STAINED_GLASS_PANE,
                "<green><bold>+"+deltas[i]+"</bold>",List.of(),"native_shop_action",
                "qty:"+category+":"+page+":"+offer.key+":"+mode+":"+(amount+deltas[i])));
            inv.setItem(addSlots[i]+18,Items.tagged(Material.RED_STAINED_GLASS_PANE,
                "<red><bold>-"+deltas[i]+"</bold>",List.of(),"native_shop_action",
                "qty:"+category+":"+page+":"+offer.key+":"+mode+":"+(amount-deltas[i])));
        }

        double unit=mode.equals("buy")?offer.buy:offer.sell;
        double total=unit*amount;
        ItemStack preview=Items.item(offer.material,
            "<white><bold>"+offer.display+"</bold>",
            List.of("<gray>Mode: "+(mode.equals("buy")?"<green>BUY":"<gold>SELL"),
                "<gray>Amount: <white>"+amount,
                "<gray>Unit: <green>$"+Text.format(unit),
                "<gray>Total: <green>$"+Text.format(total)));
        inv.setItem(22,preview);
        inv.setItem(25,Items.tagged(Material.LIME_CONCRETE,
            "<green><bold>CONFIRM "+mode.toUpperCase(Locale.ROOT)+"</bold>",
            List.of("<gray>"+amount+" × "+offer.display,
                "<gray>Total: <green>$"+Text.format(total),"","<yellow>Click to confirm."),
            "native_shop_action","confirm:"+category+":"+page+":"+offer.key+":"+mode+":"+amount));
        inv.setItem(36,Items.tagged(Material.ARROW,"<white>← Back",List.of(),"native_shop_action","category:"+category+":"+page));

        player.openInventory(inv);


        sessions.put(player.getUniqueId(),new Session(category,page,mode,offer.key,amount));
        plugin.effects().open(player);
    }

    @EventHandler(ignoreCancelled=true)
    public void onClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)||!sessions.containsKey(player.getUniqueId()))return;
        event.setCancelled(true);
        if(event.getClickedInventory()==null||event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
        String action=Items.tag(event.getCurrentItem(),"native_shop_action");
        if(action==null)return;
        plugin.effects().click(player);

        if(action.equals("root")){openRoot(player);return;}
        if(action.equals("sellall")){
            double sold=plugin.modules().shop().sellInventory(player,player.getInventory());
            if(sold>0){Text.send(player,"<gray>Sold inventory for <green>$"+Text.format(sold)+"<gray>.");plugin.effects().purchase(player);}
            else {Text.send(player,"<red>You have nothing sellable.");plugin.effects().error(player);}
            openRoot(player);return;
        }

        String[] p=action.split(":");
        try{
            if(p[0].equals("category")&&p.length>=3){openCategory(player,p[1],Integer.parseInt(p[2]));return;}
            if(p[0].equals("offer")&&p.length>=4){
                String category=p[1];int page=Integer.parseInt(p[2]);Offer offer=findOffer(category,p[3]);
                if(offer==null)return;
                int quick=Math.max(1,plugin.configs().get("shops").getInt("native-shop.quick-amount",64));
                if(event.isShiftClick()){
                    if(event.isLeftClick()) transact(player,offer,"buy",quick);
                    else if(event.isRightClick()&&offer.sell>0) transact(player,offer,"sell",quick);
                    Bukkit.getScheduler().runTask(plugin,()->openCategory(player,category,page));
                }else if(event.isLeftClick()) openQuantity(player,category,page,offer,"buy",1);
                else if(event.isRightClick()&&offer.sell>0) openQuantity(player,category,page,offer,"sell",1);
                return;
            }
            if(p[0].equals("qty")&&p.length>=6){
                Offer offer=findOffer(p[1],p[3]); if(offer==null)return;
                openQuantity(player,p[1],Integer.parseInt(p[2]),offer,p[4],Integer.parseInt(p[5]));
                return;
            }
            if(p[0].equals("confirm")&&p.length>=6){
                Offer offer=findOffer(p[1],p[3]);if(offer==null)return;
                int amount=Integer.parseInt(p[5]);
                transact(player,offer,p[4],amount);
                Bukkit.getScheduler().runTask(plugin,()->openCategory(player,p[1],Integer.parseInt(p[2])));
            }
        }catch(Exception ex){
            plugin.getLogger().warning("Native shop action failed: "+action+" -> "+ex.getMessage());
            plugin.effects().error(player);
        }
    }

    private void transact(Player player,Offer offer,String mode,int amount){
        if(amount<=0||amount>2304){plugin.effects().error(player);return;}
        if(offer.generator){
            if(!mode.equals("buy"))return;
            double total=offer.buy*amount;
            if(!plugin.modules().economy().takeMoney(player.getUniqueId(),total)){
                Text.send(player,"<red>You need <green>$"+Text.format(total)+"<red>.");plugin.effects().error(player);return;
            }
            if(!plugin.modules().generators().give(player,offer.key,amount)){
                plugin.modules().economy().addMoney(player.getUniqueId(),total);
                Text.send(player,"<red>Generator delivery failed; your money was refunded.");plugin.effects().error(player);return;
            }
            Text.send(player,"<gray>Bought <white>"+amount+"× "+offer.display+" Core <gray>for <green>$"+Text.format(total)+"<gray>.");
            plugin.effects().purchase(player);return;
        }

        if(mode.equals("buy")){
            double total=offer.buy*amount;
            if(offer.buy<=0||!plugin.modules().economy().takeMoney(player.getUniqueId(),total)){
                Text.send(player,"<red>You need <green>$"+Text.format(total)+"<red>.");plugin.effects().error(player);return;
            }
            ItemStack base=new ItemStack(offer.material);
            int left=amount;
            while(left>0){
                ItemStack stack=base.clone();int each=Math.min(left,stack.getMaxStackSize());stack.setAmount(each);
                Map<Integer,ItemStack> overflow=player.getInventory().addItem(stack);
                overflow.values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));
                left-=each;
            }
            Text.send(player,"<gray>Bought <white>"+amount+"× "+pretty(offer.material.name())+" <gray>for <green>$"+Text.format(total)+"<gray>.");
            plugin.effects().purchase(player);
        }else{
            if(offer.sell<=0){plugin.effects().error(player);return;}
            int available=countSellable(player,offer.material);
            if(available<amount){
                Text.send(player,"<red>You only have <white>"+available+" <red>sellable "+pretty(offer.material.name())+"<red>.");
                plugin.effects().error(player);return;
            }
            removeSellable(player,offer.material,amount);
            double total=offer.sell*amount*plugin.modules().shop().effectiveSellMultiplier(player);
            plugin.modules().economy().addMoney(player.getUniqueId(),total);
            plugin.modules().events().progress(player,"sell",total);
            Text.send(player,"<gray>Sold <white>"+amount+"× "+pretty(offer.material.name())+" <gray>for <green>$"+Text.format(total)+"<gray>.");
            plugin.effects().purchase(player);
        }
    }

    private int countSellable(Player player,Material material){
        int count=0;for(ItemStack item:player.getInventory().getStorageContents()){
            if(item!=null&&item.getType()==material&&plain(item))count+=item.getAmount();
        }return count;
    }
    private void removeSellable(Player player,Material material,int amount){
        int left=amount;ItemStack[] contents=player.getInventory().getStorageContents();
        for(int i=0;i<contents.length&&left>0;i++){
            ItemStack item=contents[i];if(item==null||item.getType()!=material||!plain(item))continue;
            int take=Math.min(left,item.getAmount());item.setAmount(item.getAmount()-take);left-=take;
            if(item.getAmount()<=0)contents[i]=null;
        }
        player.getInventory().setStorageContents(contents);
    }
    private boolean plain(ItemStack item){
        if(!item.hasItemMeta())return true;
        PersistentDataContainer pdc=item.getItemMeta().getPersistentDataContainer();
        return pdc.getKeys().stream().noneMatch(key->key.getNamespace().equalsIgnoreCase(plugin.getName()));
    }

    private record Offer(String key,Material material,double buy,double sell,boolean generator,String display){}
    private List<Offer> offers(String category,ConfigurationSection cat){
        List<Offer> list=new ArrayList<>();
        if(cat.getBoolean("dynamic-generators",false)){
            for(GeneratorModule.GeneratorType type:plugin.modules().generators().types()){
                double price=Math.max(1,type.upgrade()>0?type.upgrade():type.sell()*100);
                list.add(new Offer(type.id(),type.block(),price,type.sell(),true,type.display()));
            }
            return list;
        }
        for(Map<?,?> row:cat.getMapList("items")){
            Material material=Material.matchMaterial(String.valueOf(row.get("material")));if(material==null)continue;
            double buy=number(row.get("buy"),0),sell=number(row.get("sell"),0);
            list.add(new Offer(material.name().toLowerCase(Locale.ROOT),material,buy,sell,false,pretty(material.name())));
        }
        return list;
    }
    private Offer findOffer(String category,String key){
        ConfigurationSection cat=plugin.configs().get("shops").getConfigurationSection("native-shop.categories."+category);
        if(cat==null)return null;
        return offers(category,cat).stream().filter(o->o.key.equalsIgnoreCase(key)).findFirst().orElse(null);
    }
    private Set<String> categories(){
        ConfigurationSection c=plugin.configs().get("shops").getConfigurationSection("native-shop.categories");
        return c==null?Set.of():c.getKeys(false);
    }
    @EventHandler public void onClose(InventoryCloseEvent event){sessions.remove(event.getPlayer().getUniqueId());}
    @EventHandler(ignoreCancelled=true) public void onDrag(InventoryDragEvent event){
        if(sessions.containsKey(event.getWhoClicked().getUniqueId()))event.setCancelled(true);
    }
    private static void fill(Inventory inv){
        ItemStack pane=Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());
        for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,pane);
    }
    private static void decorate(Inventory inv){
        ItemStack pane=Items.item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());int rows=inv.getSize()/9;
        for(int i=0;i<inv.getSize();i++){int r=i/9,c=i%9;if(r==0||r==rows-1||c==0||c==8)inv.setItem(i,pane);}
    }
    private static int[] contentSlots(){
        List<Integer> out=new ArrayList<>();for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)out.add(r*9+c);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
    private static double number(Object v,double d){if(v instanceof Number n)return n.doubleValue();try{return Double.parseDouble(String.valueOf(v));}catch(Exception e){return d;}}
    private static String pretty(String raw){
        StringBuilder out=new StringBuilder();for(String s:raw.toLowerCase(Locale.ROOT).replace('_',' ').split(" ")){
            if(s.isBlank())continue;if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }return out.toString();
    }
    private static String strip(String raw){return raw==null?"Shop":raw.replaceAll("<[^>]+>","").replace("&l","").replace("&n","");}
}

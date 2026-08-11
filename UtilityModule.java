package dev.vupe.core.module.impl;

import dev.vupe.core.VupeCore;
import dev.vupe.core.data.PlayerData;
import dev.vupe.core.module.VupeModule;
import dev.vupe.core.util.InventoryCodec;
import dev.vupe.core.util.Locations;
import dev.vupe.core.util.Text;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class UtilityModule extends VupeModule {
    private final Map<UUID, Integer> openVault = new HashMap<>();
    private final Set<UUID> invsee = new HashSet<>();
    private final Set<UUID> editInv = new HashSet<>();
    private final Set<UUID> suppressBack = new HashSet<>();
    private final Map<UUID, UUID> seats = new HashMap<>();

    public UtilityModule(VupeCore plugin) {
        super(plugin, "utilities");
    }

    @Override
    protected void onEnable() {
        plugin.commands().register("sethome", this::setHome);
        plugin.commands().register("home", this::home);
        plugin.commands().register("back", this::back);
        plugin.commands().register("pv", this::vault);
        plugin.commands().register("fly", this::fly);
        plugin.commands().register("nick", this::nick);
        plugin.commands().register("trash", this::trash);
        plugin.commands().register("ec", this::enderChest);
        plugin.commands().register("workbench", this::workbench);
        plugin.commands().register("speed", this::speed);
        plugin.commands().register("skull", this::skull);
        plugin.commands().register("invsee", this::invseeCommand);
        plugin.commands().register("editinv", this::editInvCommand);
        plugin.commands().register("flyspeed", this::flySpeedCommand);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (suppressBack.remove(event.getPlayer().getUniqueId())) return;
        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        data.backLocation = Locations.serialize(event.getFrom());
        plugin.data().markDirty(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSit(PlayerInteractEvent event) {
        if (!plugin.configs().modules().getBoolean("modules.sitting", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!event.getClickedBlock().getType().name().endsWith("_STAIRS")) return;
        if (event.getPlayer().isSneaking()) return;
        if (!event.getClickedBlock().getRelative(org.bukkit.block.BlockFace.UP).getType().isAir()) return;
        if (seats.containsKey(event.getPlayer().getUniqueId())) return;

        event.setCancelled(true);
        Location loc = event.getClickedBlock().getLocation().add(0.5, 0.1, 0.5);
        org.bukkit.entity.ArmorStand seat = loc.getWorld().spawn(loc, org.bukkit.entity.ArmorStand.class);
        seat.setVisible(false);
        seat.setGravity(false);
        seat.setSmall(true);
        seat.setMarker(true);
        seat.setInvulnerable(true);
        seat.setPersistent(false);
        seat.addPassenger(event.getPlayer());
        seats.put(event.getPlayer().getUniqueId(), seat.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeSeat(event.getPlayer().getUniqueId()), 120L * 20L);
    }

    @EventHandler
    public void onSitQuit(PlayerQuitEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
    }

    private void removeSeat(UUID playerId) {
        UUID seatId = seats.remove(playerId);
        if (seatId == null) return;
        org.bukkit.entity.Entity seat = Bukkit.getEntity(seatId);
        if (seat != null) seat.remove();
    }

    private boolean setHome(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.home") && !player.hasPermission("vupe.admin")) return noPermission(player);
        PlayerData data = plugin.data().player(player.getUniqueId());
        data.homes.put("home", Locations.serialize(player.getLocation()));
        plugin.data().markDirty(player.getUniqueId());
        Text.send(player, "<green>Home set.");
        return true;
    }

    private boolean home(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.home") && !player.hasPermission("vupe.admin")) return noPermission(player);
        String encoded = plugin.data().player(player.getUniqueId()).homes.get("home");
        Location loc = Locations.deserialize(encoded);
        if (loc == null) {
            Text.send(player, "<red>You have not set a home.");
            return true;
        }
        player.teleportAsync(loc);
        return true;
    }

    private boolean back(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.back") && !player.hasPermission("vupe.admin")) return noPermission(player);
        PlayerData data = plugin.data().player(player.getUniqueId());
        Location loc = Locations.deserialize(data.backLocation);
        if (loc == null) {
            Text.send(player, "<red>No previous location is stored.");
            return true;
        }
        data.backLocation = Locations.serialize(player.getLocation());
        suppressBack.add(player.getUniqueId());
        player.teleportAsync(loc);
        plugin.data().markDirty(player.getUniqueId());
        return true;
    }

    private boolean vault(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /pv <number>");
            return true;
        }
        int number;
        try { number = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) { Text.send(player, "<red>Invalid vault number."); return true; }

        int max = maxVaults(player);
        if (number < 1 || number > max) {
            Text.send(player, "<red>Your rank allows vaults 1-" + max + ".");
            return true;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, Text.component("<#8B5CF6><bold>Vupe Vault #" + number + "</bold>"));
        String encoded = plugin.data().player(player.getUniqueId()).vaults.get(number);
        if (encoded != null) {
            try { InventoryCodec.decodeInto(encoded, inventory); }
            catch (Exception ex) { plugin.getLogger().warning("Could not load vault " + number + " for " + player.getName()); }
        }
        openVault.put(player.getUniqueId(), number);
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Integer vault = openVault.remove(player.getUniqueId());
        if (vault != null) {
            PlayerData data = plugin.data().player(player.getUniqueId());
            data.vaults.put(vault, InventoryCodec.encode(event.getInventory()));
            plugin.data().markDirty(player.getUniqueId());
        }
        invsee.remove(player.getUniqueId());
        editInv.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && invsee.contains(player.getUniqueId())
            && !editInv.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvseeDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && invsee.contains(player.getUniqueId())
            && !editInv.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean fly(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.fly") && !player.hasPermission("vupe.admin")) return noPermission(player);
        boolean next = !player.getAllowFlight();
        player.setAllowFlight(next);
        if (!next) player.setFlying(false);
        Text.send(player, "<gray>Flight: " + (next ? "<green>enabled" : "<red>disabled"));
        return true;
    }

    private boolean nick(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.nick") && !player.hasPermission("vupe.admin")) return noPermission(player);
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (args.length == 0 || args[0].equalsIgnoreCase("off")) {
            data.nickname = "";
            player.displayName(Text.component(player.getName()));
            player.playerListName(Text.component(player.getName()));
            Text.send(player, "<gray>Nickname removed.");
        } else {
            String nick = String.join(" ", args);
            if (net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Text.component(nick)).length() > 24) {
                Text.send(player, "<red>Nickname is too long.");
                return true;
            }
            data.nickname = nick;
            player.displayName(Text.component(nick));
            player.playerListName(Text.component(nick));
            Text.send(player, "<green>Nickname updated.");
        }
        plugin.data().markDirty(player.getUniqueId());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.data().player(event.getPlayer().getUniqueId());
        if (data.nickname != null && !data.nickname.isBlank()) {
            event.getPlayer().displayName(Text.component(data.nickname));
            event.getPlayer().playerListName(Text.component(data.nickname));
        }
    }

    private boolean trash(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        player.openInventory(Bukkit.createInventory(null, 54, Text.component("<red><bold>Trash</bold> <gray>• Items are deleted on close")));
        return true;
    }

    private boolean enderChest(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        player.openInventory(player.getEnderChest());
        return true;
    }

    private boolean workbench(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.workbench") && !player.hasPermission("vupe.admin")) return noPermission(player);
        player.openWorkbench(player.getLocation(), true);
        return true;
    }

    private boolean speed(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.speed") && !player.hasPermission("vupe.admin")) return noPermission(player);
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /speed <1-10>");
            return true;
        }
        int n;
        try { n = Integer.parseInt(args[0]); } catch (NumberFormatException ex) { n = 0; }
        if (n < 1 || n > 10) {
            Text.send(player, "<red>Speed must be 1-10.");
            return true;
        }
        float value = Math.min(1f, n / 10f);
        player.setWalkSpeed(value);
        player.setFlySpeed(value);
        Text.send(player, "<gray>Speed set to <white>" + n + "<gray>.");
        return true;
    }

    private boolean skull(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.skull") && !player.hasPermission("vupe.admin")) return noPermission(player);
        OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0]) : player;
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Text.component("<white>" + (target.getName() == null ? "Player" : target.getName()) + "'s Head"));
        skull.setItemMeta(meta);
        player.getInventory().addItem(skull);
        return true;
    }

    private boolean editInvCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.staff") && !player.hasPermission("vupe.admin")) return noPermission(player);
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /editinv <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(player, "<red>Player not found.");
            return true;
        }
        invsee.add(player.getUniqueId());
        editInv.add(player.getUniqueId());
        player.openInventory(target.getInventory());
        Text.send(player, "<gray>Editing <white>" + target.getName() + "<gray>'s inventory.");
        return true;
    }

    private boolean flySpeedCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.fly") && !player.hasPermission("vupe.speed") && !player.hasPermission("vupe.admin")) {
            return noPermission(player);
        }
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /flyspeed <1-10>");
            return true;
        }
        int speed;
        try { speed = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) { speed = 0; }
        if (speed < 1 || speed > 10) {
            Text.send(player, "<red>Fly speed must be 1-10.");
            return true;
        }
        player.setFlySpeed(Math.min(1f, speed / 10f));
        Text.send(player, "<gray>Flight speed set to <white>" + speed + "<gray>.");
        return true;
    }

    private boolean invseeCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!player.hasPermission("vupe.invsee") && !player.hasPermission("vupe.staff") && !player.hasPermission("vupe.admin")) return noPermission(player);
        if (args.length < 1) {
            Text.send(player, "<red>Usage: /invsee <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(player, "<red>Player not found.");
            return true;
        }
        invsee.add(player.getUniqueId());
        player.openInventory(target.getInventory());
        Text.send(player, "<gray>Viewing <white>" + target.getName() + "<gray>'s inventory read-only.");
        return true;
    }

    private int maxVaults(Player player) {
        PlayerData data = plugin.data().player(player.getUniqueId());
        if (data.donorRank == null || data.donorRank.isBlank()) return 0;
        return plugin.configs().get("ranks").getInt("donor-ranks." + data.donorRank + ".vaults", 0);
    }

    private boolean playerOnly(CommandSender sender) {
        Text.send(sender, "<red>Player-only.");
        return true;
    }

    private boolean noPermission(CommandSender sender) {
        Text.send(sender, "<red>No permission.");
        return true;
    }
}

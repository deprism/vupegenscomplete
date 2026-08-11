package dev.vupe.core.command;

import dev.vupe.core.VupeCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

final class TabCompletionService {
    private static final List<String> AMOUNTS = List.of("1", "10", "100", "1000", "10000", "100000", "1000000");
    private static final List<String> DURATIONS = List.of("10m", "30m", "1h", "6h", "1d", "7d", "30d", "permanent");

    private final VupeCore plugin;

    TabCompletionService(VupeCore plugin) {
        this.plugin = plugin;
    }

    List<String> complete(CommandSender sender, String command, String[] args) {
        String name = command.toLowerCase(Locale.ROOT);

        return switch (name) {
            case "vupe" -> vupe(sender, args);

            case "eco", "crystaleco" -> economyAdmin(args);
            case "crystals", "gold" -> balanceAdmin(sender, args);
            case "pay", "crystalpay", "bounty" -> playerThenAmount(args);
            case "bal", "balance", "stats", "playtime" -> at(args, 1, players());
            case "baltop", "crystalstop", "boosters" -> none();

            case "gens" -> gens(sender, args);
            case "givegen" -> giveGen(args);
            case "genlist" -> none();

            case "crates" -> crates(sender, args);
            case "box" -> boxes(sender, args);

            case "shop" -> shop(sender, args);
            case "sellwandgive" -> sellWandGive(args);
            case "sell", "autosell", "store", "crystalshop" -> none();

            case "ah" -> auction(args);
            case "coinflip" -> coinflip(args);
            case "cancelcoinflip" -> none();

            case "team" -> team(args);
            case "msg", "ignore", "report" -> at(args, 1, players());
            case "reply", "staffchat", "broadcast", "admsg" -> textFallback(args);
            case "socialspy", "clearchat", "mutechat", "buybroadcasts" -> none();
            case "chatcooldown" -> at(args, 1, List.of("0", "1", "2", "3", "5", "10", "30"));

            case "lake", "mine", "menu", "start", "back", "trash", "ec", "workbench",
                 "daily", "reclaim", "starterboost", "missions", "vote", "guide", "rules",
                 "ads", "afk", "supply" -> simple(name, sender, args);

            case "cooler" -> at(args, 1, List.of("sell", "upgrade", "rod"));
            case "rod" -> rod(args);
            case "fishtravel", "setfishtravel" -> at(args, 1, List.of("ship", "beach"));
            case "mining" -> at(args, 1, List.of("buydrill", "sell", "upgrade"));
            case "drill" -> drill(args);
            case "farming" -> farming(args);

            case "rankup" -> at(args, 1, List.of("confirm"));
            case "rank" -> rank(args);
            case "kit" -> at(args, 1, keys("kits", "kits"));
            case "level", "levels", "perks", "rewards" -> none();
            case "prestige" -> at(args, 1, List.of("confirm"));

            case "warps" -> at(args, 1, List.of("spawn", "plot", "plots", "fishing", "pvp", "farm", "mine", "crates"));

            case "minions" -> minions(sender, args);
            case "hopperlimit" -> at(args, 1, List.of("upgrade"));
            case "chestlimit" -> at(args, 1, List.of("upgrade", "compress"));

            case "sethome", "home" -> home(args);
            case "pv" -> at(args, 1, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            case "fly", "vanish" -> at(args, 1, players());
            case "nick" -> nick(args);
            case "speed", "flyspeed" -> speed(args);
            case "skull", "invsee", "editinv" -> at(args, 1, players());

            case "punish" -> punish(args);
            case "staff" -> at(args, 1, players());
            case "staffrank" -> at(args, 1, players());
            case "autosellchest" -> autosellChest(sender, args);
            case "vupegrant" -> vupeGrant(sender, args);
            case "mute", "ban" -> directTimedPunishment(args);
            case "unmute", "unban", "kick", "punishments" -> at(args, 1, players());
            case "reports" -> at(args, 1, List.of("open"));

            case "discord" -> discord(args);
            case "vupevote" -> voteBridge(args);
            case "koth" -> koth(sender, args);
            case "bosses" -> bosses(sender, args);

            case "options" -> at(args, 1, List.of("mentions", "scoreboard", "ads"));
            case "tags" -> tags(args);
            case "chatcolor" -> at(args, 1, keys("social", "chat-colors"));

            case "setlb" -> at(args, 1, List.of("money", "balance", "crystal", "crystals", "prestige", "kills", "deaths"));
            case "setspawn", "setlake", "setmine", "setcrates", "reloadlb", "deletealllb" -> none();

            case "buybroadcast" -> buyBroadcast(args);
            case "resetreclaim", "resetfreeranks" -> at(args, 1, players());

            default -> defaultPlayers(args);
        };
    }

    private List<String> vupe(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filtered(args[0], List.of("setup", "reload", "modules", "doctor", "integrations", "luckperms", "save", "backup", "version"));
        }
        if (eq(args, 0, "luckperms")) {
            if (args.length == 2) return filtered(args[1], List.of("bootstrap"));
            return none();
        }
        if (!eq(args, 0, "setup")) return none();

        if (args.length == 2) {
            return filtered(args[1], List.of("status", "auto", "worlds", "goto", "point", "minegen", "npc", "leaderboard", "supply", "finish"));
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "goto" -> filtered(args[2], List.of("spawn", "plots", "fishing", "pvp", "farm", "mine"));
                case "point" -> filtered(args[2], List.of("spawn", "fishing", "mine", "crates", "pvp", "farm"));
                case "minegen" -> filtered(args[2], List.of("8", "10", "12", "16", "20"));
                case "npc" -> filtered(args[2], keys("npcs", "npcs.roles"));
                case "leaderboard" -> filtered(args[2], List.of("money", "crystals", "prestige", "kills", "deaths"));
                case "supply" -> filtered(args[2], List.of("1", "2", "3", "4", "5", "6", "7", "8"));
                default -> none();
            };
        }
        return none();
    }

    private List<String> economyAdmin(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("set", "give", "take", "reset", "add", "remove"));
        if (args.length == 2) return filtered(args[1], players());
        if (args.length == 3 && !eq(args, 0, "reset")) return filtered(args[2], AMOUNTS);
        return none();
    }

    private List<String> balanceAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vupe.admin")) return none();
        if (args.length == 1) return filtered(args[0], List.of("set", "add", "remove"));
        if (args.length == 2) return filtered(args[1], players());
        if (args.length == 3) return filtered(args[2], AMOUNTS);
        return none();
    }

    private List<String> playerThenAmount(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) return filtered(args[1], AMOUNTS);
        return none();
    }

    private List<String> gens(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> first = new ArrayList<>();
            if (sender.hasPermission("vupe.admin")) first.addAll(List.of("give", "breakgive"));
            return filtered(args[0], first);
        }
        if (!sender.hasPermission("vupe.admin")) return none();
        if ((eq(args,0,"give") || eq(args,0,"breakgive")) && args.length == 2) return filtered(args[1], players());
        if (eq(args,0,"give") && args.length == 3) return filtered(args[2], keys("generators", "generators.types"));
        if (eq(args,0,"breakgive") && args.length == 3) return filtered(args[2], keys("mining", "breakable-generators.tiers"));
        if ((eq(args,0,"give") || eq(args,0,"breakgive")) && args.length == 4) return filtered(args[3], List.of("1","2","4","8","16","32","64"));
        return none();
    }

    private List<String> giveGen(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) return filtered(args[1], keys("generators", "generators.types"));
        if (args.length == 3) return filtered(args[2], List.of("1","2","4","8","16","32","64"));
        return none();
    }

    private List<String> crates(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> first = new ArrayList<>(List.of("open", "preview", "list"));
            if (sender.hasPermission("vupe.admin")) {
                first.addAll(List.of("give", "physicalkey", "keyall", "setloc", "openfree", "reload"));
            }
            return filtered(args[0], first);
        }

        if (List.of("open","preview","give","physicalkey","keyall","setloc","openfree").contains(lower(args,0))
            && args.length == 2) {
            return filtered(args[1], keys("crates","crates.definitions"));
        }

        if ((eq(args,0,"give") || eq(args,0,"physicalkey") || eq(args,0,"openfree")) && args.length == 3) {
            return filtered(args[2], players());
        }
        if ((eq(args,0,"give") || eq(args,0,"physicalkey")) && args.length == 4) {
            return filtered(args[3], List.of("1","2","5","10","25","64"));
        }
        if (eq(args,0,"keyall") && args.length == 3) {
            return filtered(args[2], List.of("1","2","5","10","25"));
        }
        if (eq(args,0,"open") && args.length == 3) {
            return filtered(args[2], List.of("1","2","5","10","25"));
        }
        return none();
    }

    private List<String> boxes(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vupe.admin")) return none();
        if (args.length == 1) return filtered(args[0], List.of("give"));
        if (args.length == 2) return filtered(args[1], players());
        if (args.length == 3) return filtered(args[2], List.of("money","crystals","lootbox"));
        if (args.length == 4) {
            if (eq(args,2,"lootbox")) return filtered(args[3], keys("boxes","lootboxes.definitions"));
            return filtered(args[3], keys("boxes","boxes.types." + args[2].toLowerCase(Locale.ROOT)));
        }
        if (args.length == 5) return filtered(args[4], List.of("1","2","4","8","16"));
        return none();
    }

    private List<String> shop(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filtered(args[0], keys("shops", "native-shop.categories"));
        }
        return none();
    }

    private List<String> sellWandGive(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) return filtered(args[1], List.of("1.5","2","2.5","3","5"));
        return none();
    }

    private List<String> auction(String[] args) {
        if (args.length == 1) {
            return filtered(args[0], List.of("sell", "search", "mine", "claim", "expired", "page", "bid", "end", "help"));
        }
        if (eq(args,0,"sell")) {
            if (args.length == 2) return filtered(args[1], List.of("1000","10000","100000","1000000","10000000"));
            if (args.length == 3) return filtered(args[2], List.of("1","2","3","6","12"));
        }
        if ((eq(args,0,"page") || eq(args,0,"bid") || eq(args,0,"end")) && args.length == 2) {
            return filtered(args[1], List.of("1","2","3","4","5"));
        }
        if (eq(args,0,"search") && args.length == 2) {
            return filtered(args[1], List.of("diamond","generator","pickaxe","armor","shulker","beacon"));
        }
        return none();
    }

    private List<String> coinflip(String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("accept","cancel"));
            values.addAll(AMOUNTS);
            return filtered(args[0], values);
        }
        return none();
    }

    private List<String> team(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("create","invite","join","leave","kick","disband","chat"));
        if ((eq(args,0,"invite") || eq(args,0,"kick")) && args.length == 2) return filtered(args[1], players());
        if (eq(args,0,"join") && args.length == 2) return filtered(args[1], plugin.data().server().teams.keySet());
        return none();
    }

    private List<String> simple(String name, CommandSender sender, String[] args) {
        return switch (name) {
            case "supply" -> at(args,1, sender.hasPermission("vupe.admin") ? List.of("info","start") : List.of("info"));
            default -> none();
        };
    }

    private List<String> rod(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("upgrade"));
        if (eq(args,0,"upgrade") && args.length == 2) return filtered(args[1], keys("fishing","fishing.enchants"));
        return none();
    }

    private List<String> drill(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("upgrade"));
        if (eq(args,0,"upgrade") && args.length == 2) return filtered(args[1], keys("mining","mining.enchants"));
        return none();
    }

    private List<String> farming(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("buy","tool","upgrade","warp"));
        if (eq(args,0,"upgrade") && args.length == 2) return filtered(args[1], keys("farming","farming.enchants"));
        return none();
    }

    private List<String> rank(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("give"));
        if (eq(args,0,"give") && args.length == 2) return filtered(args[1], players());
        if (eq(args,0,"give") && args.length == 3) {
            List<String> ranks = new ArrayList<>(keys("ranks","donor-ranks"));
            ranks.replaceAll(v -> v.equalsIgnoreCase("vupeplus") ? "vupe+" : v);
            return filtered(args[2], ranks);
        }
        return none();
    }

    private List<String> minions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> first = new ArrayList<>(List.of("collect","pickup"));
            if (sender.hasPermission("vupe.admin")) first.add("give");
            return filtered(args[0], first);
        }
        if (eq(args,0,"give")) {
            if (args.length == 2) return filtered(args[1], players());
            if (args.length == 3) return filtered(args[2], keys("minions","minions.types"));
            if (args.length == 4) return filtered(args[3], List.of("1","2","4","8"));
        }
        if ((eq(args,0,"collect") || eq(args,0,"pickup")) && args.length == 2 && sender instanceof Player player) {
            return filtered(args[1], plugin.data().server().minions.values().stream()
                .filter(m -> player.getUniqueId().toString().equals(m.owner))
                .map(m -> m.id).filter(Objects::nonNull).toList());
        }
        return none();
    }

    private List<String> home(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("home"));
        return none();
    }

    private List<String> nick(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("off"));
        return none();
    }

    private List<String> speed(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("1","2","3","4","5","6","7","8","9","10"));
        return none();
    }

    private List<String> punish(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("mute","unmute","ban","unban","kick","history","reports","closereport","chatmute"));
        if (List.of("mute","unmute","ban","unban","kick","history").contains(lower(args,0)) && args.length == 2) return filtered(args[1], players());
        if ((eq(args,0,"mute") || eq(args,0,"ban")) && args.length == 3) return filtered(args[2], DURATIONS);
        return none();
    }

    private List<String> directTimedPunishment(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) return filtered(args[1], DURATIONS);
        return none();
    }

    private List<String> autosellChest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filtered(args[0], sender.hasPermission("vupe.admin") ? List.of("give") : List.of());
        }
        if (eq(args,0,"give") && sender.hasPermission("vupe.admin")) {
            if (args.length == 2) return filtered(args[1], players());
            if (args.length == 3) return filtered(args[2], List.of("1","2","3","5","10","25"));
        }
        return none();
    }

    private List<String> vupeGrant(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission("vupe.admin")) return none();
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) {
            List<String> grants = new ArrayList<>();
            for (String rank : keys("ranks","donor-ranks")) grants.add("rank:" + rank);
            for (String tag : keys("social","tags")) grants.add("tag:" + tag);
            for (String bundle : keys("store","store.grant-bundles")) grants.add("bundle:" + bundle);
            ConfigurationSection cats = plugin.configs().get("store").getConfigurationSection("store.categories");
            if (cats != null) {
                for (String cat : cats.getKeys(false)) {
                    ConfigurationSection offers = cats.getConfigurationSection(cat + ".offers");
                    if (offers != null) for (String offer : offers.getKeys(false)) grants.add("offer:" + offer);
                }
            }
            grants.addAll(List.of(
                "money:100000","money:1000000",
                "crystals:25000","crystals:75000","crystals:200000","gold:1000",
                "genslots:5","sellmulti:0.05","autosellchest:1","sellwand:2.0",
                "crate:vote:1","crate:cipher:1","crate:phantom:1","crate:titan:1","crate:event:1","crate:vupe:1"
            ));
            for (String gen : keys("generators","generators.types")) grants.add("generator:" + gen + ":1");
            return filtered(args[1], grants);
        }
        return none();
    }

    private List<String> discord(String[] args) {
        if (args.length == 1) return filtered(args[0], List.of("link","unlink","suggest","ticket"));
        return none();
    }

    private List<String> voteBridge(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length == 2) return filtered(args[1], List.of("Minehut","PlanetMinecraft","MinecraftServers","TopG"));
        return none();
    }

    private List<String> koth(CommandSender sender, String[] args) {
        if (args.length == 1) return filtered(args[0], sender.hasPermission("vupe.admin") ? List.of("start","set") : List.of());
        if (eq(args,0,"set") && args.length == 2) return filtered(args[1], List.of("1","2"));
        return none();
    }

    private List<String> bosses(CommandSender sender, String[] args) {
        if (args.length == 1) return filtered(args[0], sender.hasPermission("vupe.admin") ? List.of("setspawn","spawn","mob") : List.of());
        if (eq(args,0,"spawn") && args.length == 2) return filtered(args[1], keys("pvp","bosses.definitions"));
        if (eq(args,0,"mob") && args.length == 2) return filtered(args[1], keys("pvp","custom-mobs.definitions"));
        return none();
    }

    private List<String> tags(String[] args) {
        if (args.length != 1) return none();
        List<String> values = new ArrayList<>(keys("social","tags"));
        values.add("off");
        return filtered(args[0], values);
    }

    private List<String> buyBroadcast(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        if (args.length >= 3 && args.length <= 4) return filtered(args[args.length - 1], AMOUNTS);
        return none();
    }

    private List<String> textFallback(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        return none();
    }

    private List<String> defaultPlayers(String[] args) {
        if (args.length == 1) return filtered(args[0], players());
        return none();
    }

    private List<String> players() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    private List<String> keys(String config, String path) {
        ConfigurationSection section = plugin.configs().get(config).getConfigurationSection(path);
        if (section == null) return List.of();
        return section.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> at(String[] args, int position, Collection<String> values) {
        if (args.length != position) return none();
        return filtered(args[position - 1], values);
    }

    private List<String> filtered(String input, Collection<String> values) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(Objects::nonNull)
            .distinct()
            .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .limit(100)
            .toList();
    }

    private boolean eq(String[] args, int index, String value) {
        return index < args.length && args[index].equalsIgnoreCase(value);
    }

    private String lower(String[] args, int index) {
        return index < args.length ? args[index].toLowerCase(Locale.ROOT) : "";
    }

    private List<String> none() {
        return Collections.emptyList();
    }
}

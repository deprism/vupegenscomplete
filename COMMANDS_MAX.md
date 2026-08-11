# VupeCore 2.1 command reference

VupeCore declares **119 top-level commands**.

## Native commerce command trees

```text
/bal [player]
/balance [player]
/pay <player> <amount>
/eco <set|give|take|reset|add|remove> <player> [amount]
/baltop

/shop [category]

/crates
/crates preview <crate>
/crates open <crate> [amount]
/crates list
/crates setloc <crate>
/crates give <crate> <player> <amount>
/crates physicalkey <crate> <player> <amount>
/crates keyall <crate> <amount>
/crates openfree <crate> <player>
/crates reload

/ah
/ah sell <starting-bid> [hours]
/ah search <text>
/ah mine
/ah claim
/ah expired
/ah page <page>
/ah bid <id>
/ah end <id>
/ah help
```

All of these command trees have Vupe tab completion.

## All Vupe commands

| Command | Aliases | Description |
|---|---|---|
| `/admsg` | — | Send an advertisement |
| `/ads` | — | Advertisement menu |
| `/afk` | — | Toggle AFK |
| `/ah` | `/auction`, `/auctionhouse` | Open the native Vupe Auction House |
| `/autosell` | — | Toggle generator autosell |
| `/autosellchest` | `/asc`, `/sellchest` | Vupe autosell chest system |
| `/back` | — | Previous location |
| `/bal` | `/money` | View a Vupe Money balance |
| `/balance` | — | View a Vupe Money balance |
| `/baltop` | `/balancetop` | Vupe Money leaderboard |
| `/ban` | — | Ban a player |
| `/boosters` | — | Active boosters |
| `/bosses` | — | Boss system |
| `/bounty` | `/bounties` | Bounty system |
| `/box` | — | Vupe boxes and lootboxes |
| `/broadcast` | `/bc` | Staff broadcast |
| `/buybroadcast` | — | Staff purchase broadcast |
| `/buybroadcasts` | — | View/buy broadcast information |
| `/cancelcoinflip` | — | Admin/player coinflip cancellation |
| `/chatcolor` | `/chatcolour` | Chat color selector |
| `/chatcooldown` | — | Set global chat cooldown |
| `/chestlimit` | — | Chest limit |
| `/clearchat` | — | Clear public chat |
| `/coinflip` | `/coinflips`, `/cf` | Coinflip system |
| `/cooler` | — | Fishing cooler |
| `/crates` | `/crate` | Open/manage native Vupe crates |
| `/crystaleco` | — | Admin crystal economy |
| `/crystalpay` | — | Pay crystals |
| `/crystals` | `/crystal` | Crystal balance |
| `/crystalshop` | — | Crystal shop |
| `/crystalstop` | `/ctop` | Crystal leaderboard |
| `/daily` | — | Donor daily |
| `/deletealllb` | — | Delete leaderboard entities/locations |
| `/discord` | — | Discord integration |
| `/drill` | — | Vupe drill enchantments |
| `/ec` | `/enderchest` | Ender chest |
| `/eco` | `/economy` | Administer Vupe Money |
| `/editinv` | — | Edit an online player's inventory |
| `/farming` | — | Farming menu |
| `/fishtravel` | — | Fishing travel menu |
| `/fly` | — | Flight toggle |
| `/flyspeed` | — | Compatibility flight speed |
| `/genlist` | — | Generator list compatibility |
| `/gens` | `/generators`, `/genslist` | Generator menu |
| `/givegen` | — | Compatibility generator grant |
| `/gold` | — | Gold balance |
| `/guide` | — | Server guide |
| `/home` | — | Home |
| `/hopperlimit` | — | Hopper limit |
| `/ignore` | — | Ignore private messages |
| `/invsee` | — | View inventory |
| `/kick` | — | Kick a player |
| `/kit` | — | Donor kits |
| `/koth` | — | KOTH system |
| `/lake` | — | Fishing lake |
| `/level` | — | Level information |
| `/levels` | `/levelmenu` | Level progression GUI |
| `/menu` | — | Main menu |
| `/mine` | — | Mine teleport |
| `/mining` | `/backpack` | Mining/backpack menu |
| `/minions` | — | Optional Vupe minions |
| `/missions` | `/dailies`, `/quests` | Daily missions |
| `/msg` | `/tell`, `/message`, `/whisper`, `/w` | Private messages |
| `/mute` | — | Mute a player |
| `/mutechat` | — | Toggle global chat mute |
| `/nick` | — | Nickname |
| `/options` | — | Player options |
| `/pay` | — | Pay another player Vupe Money |
| `/perks` | — | Rank/perk browser |
| `/playtime` | — | Playtime |
| `/prestige` | — | Prestige progression |
| `/punish` | — | Punishment command |
| `/punishments` | — | Punishment history |
| `/pv` | `/vault`, `/playervault` | Player vault |
| `/rank` | — | Donor-rank administration |
| `/rankup` | `/ranks` | Progression ranks |
| `/reclaim` | — | Season reclaim |
| `/reloadlb` | — | Refresh leaderboards |
| `/reply` | `/r` | Reply to private message |
| `/report` | — | Report a player |
| `/reports` | — | Staff report list |
| `/resetfreeranks` | — | Admin starter-boost reset |
| `/resetreclaim` | — | Admin reclaim reset |
| `/rewards` | — | Reward center |
| `/rod` | — | Vupe fishing rod and enchantments |
| `/rules` | — | Server rules |
| `/sell` | — | Sell items |
| `/sellwandgive` | — | Compatibility sellwand grant |
| `/setcrates` | — | Compatibility crate-warp setup |
| `/setfishtravel` | — | Compatibility fishing travel setup |
| `/sethome` | — | Set home |
| `/setlake` | — | Compatibility fishing setup |
| `/setlb` | — | Compatibility leaderboard setup |
| `/setmine` | — | Compatibility mine setup |
| `/setspawn` | — | Compatibility spawn setup |
| `/shop` | — | Open the native Vupe category shop |
| `/skull` | — | Player skull |
| `/socialspy` | — | Staff private-message spy |
| `/speed` | — | Walk/fly speed |
| `/staff` | — | Vupe staff control center |
| `/staffchat` | `/sc` | Staff chat |
| `/staffrank` | — | LuckPerms staff-rank manager |
| `/start` | — | Start your Vupe progression |
| `/starterboost` | `/freerank` | Starter boost |
| `/stats` | — | Player statistics |
| `/store` | — | Gold store |
| `/supply` | — | Supply-drop system |
| `/tags` | — | Chat tags |
| `/team` | `/teams` | Team system |
| `/trash` | `/disposal` | Trash inventory |
| `/unban` | — | Unban a player |
| `/unmute` | — | Unmute a player |
| `/vanish` | — | Vanish |
| `/vote` | `/voting` | Voting links |
| `/vupe` | — | Vupe administration and setup |
| `/vupegrant` | — | Console-safe Vupe store fulfillment |
| `/vupevote` | — | Console vote-reward bridge |
| `/warps` | `/warp` | Warp menu |
| `/workbench` | `/craft`, `/craftingtable` | Crafting table |

## Retained external command owners

```text
/plot -> PlotSquared
/lp   -> LuckPerms
/mv   -> Multiverse-Core
/tab  -> TAB
/papi -> PlaceholderAPI
```
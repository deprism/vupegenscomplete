# Vupe MAX 2.1 — complete fresh-server setup

Target:

```text
Paper 1.21.11
Java 21
VupeCore 2.1.0
```

## 1. Install the required plugins

Upload:

```text
VupeCore-2.1.0.jar
PlotSquared
FastAsyncWorldEdit
LuckPerms
Vault
PlaceholderAPI
Multiverse-Core
TAB
```

Optional:

```text
ViaVersion
ViaBackwards
ViaRewind
CoreProtect
Tebex
Votifier-compatible receiver
```

Do NOT install a second economy/shop/crate/AH plugin.

VupeCore itself owns:

```text
Money
/shop
/crates
/ah
```

## 2. First boot

Keep setup private:

```text
whitelist on
whitelist add YOURNAME
op YOURNAME
```

Start once so every plugin generates its folders.

Stop.

Copy/merge the Vupe configs from the server setup package into:

```text
plugins/VupeCore/
```

Install the retained external configs using:

```text
docs/EXTERNAL_CONFIG_INSTALL.md
```

## 3. PlotSquared

Create the PlotSquared world:

```text
/plot setup
```

Default Vupe world name:

```text
vupeplots
```

Test:

```text
/plot auto
/plot home
/plot info
```

Vupe uses PlotSquared for generator/AutoSell placement protection and `/start`
routing.

## 4. Vupe gameplay worlds

Run:

```text
/vupe setup worlds
```

Vupe creates/loads:

```text
spawn
fishing
pvp
farm
mine
```

Multiverse remains the external world-management layer.

Verify:

```text
/mv list
```

## 5. Set core locations

Spawn:

```text
/vupe setup goto spawn
/vupe setup point spawn
```

Fishing:

```text
/vupe setup goto fishing
/vupe setup point fishing
```

PvP:

```text
/vupe setup goto pvp
/vupe setup point pvp
```

Farm:

```text
/vupe setup goto farm
/vupe setup point farm
```

Mine:

```text
/vupe setup goto mine
/vupe setup point mine
/vupe setup minegen 18
```

Crate-room arrival point:

```text
/vupe setup point crates
```

## 6. LuckPerms

Bootstrap Vupe groups:

```text
/vupe luckperms bootstrap
```

Then assign your owner account:

```text
/lp user YOURNAME parent add owner
```

Staff groups are configured in `staff.yml`.

Donor ranks are configured in `ranks.yml`.

## 7. Native economy

No external economy provider is installed.

Test:

```text
/bal
/eco give YOURNAME 1000000
/bal
/pay OTHERPLAYER 100
/baltop
```

Then:

```text
/vupe integrations
```

Vupe should report native Money and Vault integration.

## 8. Native shop

Open:

```text
/shop
```

Categories:

```text
Blocks
Tools & Armor
Foraging
Generators
Redstone
Ores
Miscellaneous
```

Test:

```text
buy quantity
sell quantity
shift-buy 64
shift-sell 64
sell inventory
page navigation
generator purchases
```

Prices/catalog live in:

```text
plugins/VupeCore/shops.yml
```

## 9. Native crates

Set each physical block.

Look directly at the crate block and run:

```text
/crates setloc vote
/crates setloc cipher
/crates setloc phantom
/crates setloc titan
/crates setloc event
/crates setloc vupe
```

Give test keys:

```text
/crates give vote YOURNAME 10
/crates physicalkey cipher YOURNAME 3
```

Test:

```text
/crates
/crates preview vote
/crates open vote
/crates open vote 5
```

At physical blocks test:

```text
left-click  = preview
right-click = open
```

Test a keyall:

```text
/crates keyall vote 2
```

Crate definitions, rewards, holograms and animations are all in:

```text
plugins/VupeCore/crates.yml
```

## 10. Native Auction House

Use two test accounts.

Seller:

```text
hold an item stack
/ah sell 1000 1
```

Buyer:

```text
/ah
/ah bid ID
```

Then test an outbid with another player.

Expected:

```text
new bidder pays full bid
previous bidder gets exact refund
winner receives item through /ah claim
seller receives winning bid
```

Also test:

```text
/ah search diamond
/ah mine
/ah end ID
/ah expired
/ah claim
```

AH configuration:

```text
plugins/VupeCore/auctionhouse.yml
```

## 11. Main Vupe UI

Test:

```text
/menu
/warps
/stats
/missions
/rankup
/levels
/perks
/rewards
/crystalshop
/store
```

The main menu should route to native:

```text
/shop
/crates
/ah
```

rather than external plugins.

## 12. AutoSell Chests

```text
/autosellchest give YOURNAME 2
```

Place one on your PlotSquared plot.

Test:

```text
storage
automatic selling
stored earnings
collection
tier upgrades
hologram
safe pickup
restart persistence
```

The same native Money and sell-value layer is used by `/shop`, `/sell`,
sellwands and AutoSell Chests.

## 13. Activities

Fishing:

```text
/lake
/cooler rod
/rod
/fishtravel
```

Mining:

```text
/mine
/mining buydrill
/drill
```

Farming:

```text
/farming
/farming buy
```

## 14. Progression

```text
/rankup
/levels
/rewards
/prestige
/perks
```

Verify GUI confirmations and rewards.

## 15. Staff

```text
/staff
/staffrank
/punish
/reports
```

Use a non-admin staff rank as well as owner to test permission boundaries.

## 16. TAB

Merge the supplied TAB config.

Reload TAB using its own command after installing/merging its generated config.

Verify:

```text
header/footer
LuckPerms rank prefixes
player ordering
Vupe placeholders
scoreboard
```

VupeCore's old internal player-list/sidebar modules stay disabled when TAB is
the presentation authority.

## 17. Via stack — optional

If installed, keep ViaVersion/ViaBackwards/ViaRewind versions compatible with
one another.

Restart after changing their JARs/configs.

Test every client version you intend to advertise.

## 18. Diagnostics

Run:

```text
/vupe integrations
/vupe doctor
/vupe modules
/vupe setup status
/vupe save
/vupe backup
```

## 19. Non-OP test

Use a fresh normal account and verify:

```text
/start
/plot info
/shop
/crates
/ah
/rankup
/levels
/crystalshop
/autosellchest
/lake
/mine
/farming
```

## 20. Production test

Complete:

```text
docs/TEST_PLAN_2_1.md
```

before:

```text
whitelist off
```

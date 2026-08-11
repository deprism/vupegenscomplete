# Upgrade Vupe MAX 2.0 -> 2.1 Native Commerce

## 1. Backup

Stop the server.

Back up:

```text
plugins/
plugins/VupeCore/
world folders
PlotSquared data
LuckPerms data
```

## 2. Remove the four external commerce plugins

Remove their JARs:

```text
EzEconomy
EconomyShopGUI
CrazyCrates
EzAuction
```

Do not remove:

```text
PlotSquared
FAWE
LuckPerms
Vault
PlaceholderAPI
Multiverse-Core
TAB
Via stack if used
```

## 3. Replace VupeCore

Build/download:

```text
VupeCore-2.1.0.jar
```

Remove every older VupeCore JAR.

Upload only 2.1.

## 4. Copy the new Vupe configs

Important changed/new files:

```text
economy.yml
shops.yml
crates.yml
auctionhouse.yml
config.yml
modules.yml
menus.yml
store.yml
crystalshop.yml
plugin.yml is inside the JAR
```

## 5. Money migration

Vupe 2.1 makes Vupe's `PlayerData.money` authoritative again.

If your server never launched the 2.0 EzEconomy conversion in production, your
existing Vupe player JSON balances already remain suitable.

If real production balances were moved into an external economy, export those
balances before removing that plugin and migrate them intentionally.

Do not just delete the only copy of real balances.

## 6. Crate migration

CrazyCrates block bindings are not used.

Set each Vupe block natively:

```text
/crates setloc vote
/crates setloc cipher
/crates setloc phantom
/crates setloc titan
/crates setloc event
/crates setloc vupe
```

Look at the intended crate block when running each command.

## 7. Shop

Open:

```text
/shop
```

The old source-based Vupe catalog now lives in:

```text
plugins/VupeCore/shops.yml
```

No external shop config is needed.

## 8. Auction House

Open:

```text
/ah
```

Test:

```text
/ah sell 1000 1
/ah mine
/ah search diamond
/ah claim
/ah expired
```

Use two non-OP accounts to test real bidding/refunds.

## 9. Vault

Run:

```text
/vupe integrations
/vupe doctor
```

Vupe should report its native economy.

Vault remains installed, but there should be no second economy provider plugin.

## 10. Final test

Follow:

```text
docs/TEST_PLAN_2_1.md
```

before deleting backups.

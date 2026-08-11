# VupeCore 2.1.0 — MAX Native Commerce

VupeCore 2.1 reverses the four 2.0 external commerce dependencies.

These systems are Vupe-native again:

```text
Money / economy
/shop
/crates
/ah
```

They are rebuilt from the supplied Elestra/Vute Skripts rather than from the
simplified VupeCore 1.x implementations.

## Historical source used

```text
economy/commands.sk
economy/shop.sk
economy/selling.sk
economy/autosell.sk
economy/crates.sk
economy/auctionhouse.sk
```

The old interaction/economy behavior is preserved where it was good and
modernized where Java/Paper can make it safer or richer.

## Specialist plugins still retained

Vupe intentionally keeps systems that are not worth replacing badly:

```text
PlotSquared + FAWE
LuckPerms
Vault
PlaceholderAPI
Multiverse-Core
TAB
```

Optional:

```text
ViaVersion + ViaBackwards + ViaRewind
CoreProtect
Tebex
Votifier-compatible receiver
```

Not required anymore:

```text
EzEconomy
EconomyShopGUI
CrazyCrates
EzAuction
Skript/addons
MongoDB
EssentialsX
```

## Native commerce highlights

### Economy

Vupe Money is stored in Vupe player data again.

VupeCore also registers itself as the Vault economy provider, so Vault remains
a compatibility API without requiring a second economy plugin.

Commands:

```text
/bal
/balance
/pay
/eco
/baltop
```

### Shop

The shop catalog was generated from the old Elestra `shop.sk`.

It currently contains 142 historical configured products plus the dynamic Vupe
generator category.

The original quantity workflow is preserved and improved:

```text
+1 / +16 / +32 / +64 / +128
-1 / -16 / -32 / -64 / -128
confirmation
quick shift-click
pagination
buy + sell
sell-inventory shortcut
```

### Crates

The native crate engine retains:

```text
physical crate blocks
physical keys
virtual keys
left-click reward preview
weighted rewards
keyall countdowns
admin crate locations
```

and adds:

```text
animated roulette/wheel/casino/cosmic styles
mass-open
normalized chance display
TextDisplay holograms
sound/particle feedback
pending-reward recovery across reconnects
safe invalid-reward key refunds
```

### Auction House

The native AH is based on the old bidding system:

```text
minimum starting bid: 1,000
maximum bid: 1 quadrillion
maximum duration: 12 hours
maximum listings/player: 14
first bid = starting bid
next minimum bid = previous bid + 20%
previous bidder is automatically refunded
winning item goes to claim queue
unsold item goes to expired queue
```

Vupe 2.1 adds:

```text
precise timestamps
anti-snipe extension
search
sort modes
paginated GUI
safe claim-all
full-inventory protection
persistent bid escrow
restart-safe settlement
early-end confirmation
```

## Start here

```text
docs/PLUGINS_NEEDED.md
docs/UPGRADE_TO_2_1_NATIVE.md
docs/FULL_SETUP_GUIDE.md
docs/NATIVE_COMMERCE.md
docs/TEST_PLAN_2_1.md
```

Build with Java 21 / Maven using the included GitHub Actions workflow.

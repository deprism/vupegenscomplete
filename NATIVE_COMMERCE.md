# Vupe 2.1 native commerce systems

This document describes the four systems moved back into VupeCore.

## 1. Native Money

Source basis:

```text
Elestra/economy/commands.sk
```

Storage:

```text
PlayerData.money
```

Commands:

```text
/bal [player]
/balance [player]
/pay <player> <amount>

/eco set <player> <amount>
/eco give <player> <amount>
/eco add <player> <amount>
/eco take <player> <amount>
/eco remove <player> <amount>
/eco reset <player>

/baltop
```

All native Vupe systems use the same Money methods:

```text
rankups
shop
auction house
autosell
sellwands
AutoSell Chests
coinflips
paid rewards
```

VupeCore registers the same balance through Vault for interoperability.

## 2. Native `/shop`

Source basis:

```text
Elestra/economy/shop.sk
Elestra/economy/selling.sk
Elestra/economy/autosell.sk
```

The parser imported the historical item/pricing catalog into `shops.yml`.

Native categories:

```text
Blocks
Tools & Armor
Foraging
Generators
Redstone
Ores
Miscellaneous
```

The generator category is dynamic and reads `generators.yml`.

The old quantity GUI pattern is retained:

```text
+1
+16
+32
+64
+128

-1
-16
-32
-64
-128
```

Player flow:

```text
/shop
-> category
-> item
-> BUY or SELL quantity
-> confirmation
-> transaction
```

Shortcuts:

```text
left click       -> buy quantity
right click      -> sell quantity
shift-left       -> quick-buy 64
shift-right      -> quick-sell 64
```

The quick amount is configurable.

Custom Vupe PDC items are not accidentally sold as their vanilla base material.

## 3. Native crates

Source basis:

```text
Elestra/economy/crates.sk
```

Crate IDs:

```text
vote
cipher
phantom
titan
event
vupe
```

Player commands:

```text
/crates
/crates preview <crate>
/crates open <crate> [amount]
```

Admin:

```text
/crates list
/crates setloc <crate>
/crates give <crate> <player> <amount>
/crates physicalkey <crate> <player> <amount>
/crates keyall <crate> <amount>
/crates openfree <crate> <player>
/crates reload
```

Physical block behavior:

```text
left-click  -> reward preview
right-click -> open
```

Keys can be:

```text
virtual
physical PDC-tagged keys
```

The engine consumes keys before selecting/delivering the animation result.

To protect against disconnects/crashes, selected rewards are persisted as
pending rewards until delivery succeeds.

If a configured reward cannot be delivered, Vupe removes the pending reward
and refunds one matching crate key.

Reward previews calculate the normalized probability from configured weights.

## 4. Native Auction House

Source basis:

```text
Elestra/economy/auctionhouse.sk
```

Old economics intentionally preserved:

```text
min starting bid: 1,000
max bid: 1,000,000,000,000,000
max duration: 12h
max active listings/player: 14
first bid: starting bid
subsequent minimum: top bid * 1.20
```

Commands:

```text
/ah
/ah sell <starting-bid> [hours]
/ah search <material/name/seller>
/ah mine
/ah claim
/ah expired
/ah page <page>
/ah bid <id>
/ah end <id>
/ah help
```

### Escrow

When a bidder bids:

1. Vupe withdraws the new full bid.
2. Vupe refunds the previous highest bidder.
3. The new bid remains represented by the persisted auction record.
4. When the auction settles, the seller receives the top bid.
5. The winning item is placed into the bidder's persisted claim queue.

This makes a restart during an active auction recoverable.

### No-bid expiry

If an auction ends with no bidder:

```text
item -> seller.auctionExpiredItems
```

The seller claims it through:

```text
/ah expired
```

### Full inventory safety

Claiming checks whether the entire stack fits.

`Claim All` only removes items from the persisted queue after they fit and are
actually given.

### Anti-snipe

By default a bid within the last 30 seconds extends the auction by 30 seconds.

Configure this in:

```text
auctionhouse.yml
```

### Sorting/search

Sort modes:

```text
ENDING_SOON
NEWEST
HIGHEST_BID
MOST_BIDS
```

Search:

```text
/ah search diamond
/ah search generator
/ah search PLAYERNAME
```

## Configuration files

```text
economy.yml       # Money baseline / selling
shops.yml         # native shop catalog
crates.yml        # crate definitions/animations/rewards
auctionhouse.yml  # AH economics/UI/anti-snipe
```

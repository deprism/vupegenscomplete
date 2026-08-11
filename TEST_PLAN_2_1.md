# Vupe 2.1 production test plan

Do not open a real server publicly until every enabled system relevant to your
server has passed.

## Boot/dependencies

- [ ] Paper 1.21.11 / Java 21 starts.
- [ ] VupeCore 2.1.0 enables.
- [ ] PlotSquared + FAWE enable.
- [ ] LuckPerms enables.
- [ ] Vault enables.
- [ ] PlaceholderAPI enables.
- [ ] Multiverse-Core enables.
- [ ] TAB enables.
- [ ] No second economy provider is installed.
- [ ] No external shop/crate/AH plugin shadows Vupe.
- [ ] `/vupe integrations` is correct.
- [ ] `/vupe doctor` has no unexpected failure.

## Native Money

Use two normal accounts plus one admin.

- [ ] starting balance only initializes once
- [ ] `/bal`
- [ ] `/balance PLAYER`
- [ ] `/pay PLAYER AMOUNT`
- [ ] insufficient `/pay`
- [ ] `/eco give`
- [ ] `/eco take`
- [ ] `/eco set`
- [ ] `/eco reset`
- [ ] `/baltop`
- [ ] no NaN/infinite/negative exploit
- [ ] balance survives three restarts
- [ ] Vupe rankup uses same balance
- [ ] Vupe coinflip uses same balance
- [ ] Vupe AH uses same balance

## Native Shop

Test every category:

- [ ] Blocks
- [ ] Tools & Armor
- [ ] Foraging
- [ ] Generators
- [ ] Redstone
- [ ] Ores
- [ ] Miscellaneous

Transactions:

- [ ] buy 1
- [ ] buy 16
- [ ] buy 32
- [ ] buy 64
- [ ] buy 128
- [ ] sell 1
- [ ] sell 16
- [ ] quantity decrement
- [ ] confirm screen
- [ ] shift-buy quick amount
- [ ] shift-sell quick amount
- [ ] insufficient balance
- [ ] insufficient inventory quantity
- [ ] full inventory overflow behavior
- [ ] paginated block catalog
- [ ] generator purchase
- [ ] custom PDC item is not sold as vanilla material
- [ ] sell multiplier applies once
- [ ] Sell Inventory works

## Native Crates

For:

```text
vote cipher phantom titan event vupe
```

verify:

- [ ] `/crates`
- [ ] `/crates preview`
- [ ] normalized chance display
- [ ] `/crates setloc`
- [ ] left-click physical block preview
- [ ] right-click physical block open
- [ ] virtual keys
- [ ] physical keys
- [ ] no-key denial
- [ ] open cooldown shows remaining time
- [ ] single animation
- [ ] mass-open
- [ ] sound
- [ ] particles
- [ ] hologram
- [ ] keyall 15/5/2/1 countdown
- [ ] Money reward
- [ ] Crystal reward
- [ ] generator reward
- [ ] AutoSell Chest reward
- [ ] sellwand reward
- [ ] crate-key reward
- [ ] rank/tag reward where configured

Disconnect safety:

1. Start an animated open.
2. Disconnect before completion.
3. Rejoin.
4. Confirm pending reward is delivered once.
5. Rejoin again.
6. Confirm it is not duplicated.

Broken-reward test on a private test copy:

- [ ] invalid reward refunds exactly one key
- [ ] pending marker is removed
- [ ] repeated reconnect does not duplicate refund

## Native Auction House

Use three accounts: seller, bidder A, bidder B.

Listing:

- [ ] `/ah sell 1000 1`
- [ ] held stack removed exactly once
- [ ] max 14 active listings
- [ ] minimum starting bid 1000
- [ ] max 12-hour duration
- [ ] invalid/NaN prices denied

Browsing:

- [ ] `/ah`
- [ ] page controls
- [ ] `ENDING_SOON`
- [ ] `NEWEST`
- [ ] `HIGHEST_BID`
- [ ] `MOST_BIDS`
- [ ] `/ah search diamond`
- [ ] search by display name
- [ ] search by seller

Bidding:

- [ ] seller cannot bid own listing
- [ ] first bid equals starting bid
- [ ] second minimum = top bid × 1.20
- [ ] current highest bidder cannot rebid themselves
- [ ] bidder A pays full bid
- [ ] bidder B outbids
- [ ] bidder A receives exact refund
- [ ] bidder B remains escrowed
- [ ] insufficient balance denied
- [ ] anti-snipe extends near-expiry listing

Settlement:

- [ ] winner item enters `/ah claim`
- [ ] seller receives exactly winning bid
- [ ] item does not duplicate
- [ ] no-bid item enters `/ah expired`
- [ ] claim refuses if whole stack does not fit
- [ ] Claim All leaves non-fitting items queued
- [ ] seller early-end without bid returns item
- [ ] seller early-end with bid finalizes current winner
- [ ] restart while bid escrow exists preserves settlement

## Store/Crystal rewards

- [ ] store bundles that grant crate keys use native keys
- [ ] Crystal Shop crate-key purchases use native keys
- [ ] `/vupegrant PLAYER crate:vote:5`
- [ ] no old external command is dispatched

## AutoSell Chests

- [ ] place only where PlotSquared allows
- [ ] automatic selling
- [ ] exact native Money payout
- [ ] upgrade
- [ ] stored earnings
- [ ] collection
- [ ] hologram
- [ ] safe pickup
- [ ] restart persistence

## Rank/level/store/staff systems

- [ ] `/rankup`
- [ ] `/levels`
- [ ] `/rewards`
- [ ] `/prestige`
- [ ] `/perks`
- [ ] `/crystalshop`
- [ ] `/store`
- [ ] `/staff`
- [ ] `/punish`
- [ ] `/reports`
- [ ] LuckPerms donor sync
- [ ] LuckPerms staff hierarchy

## PlotSquared

- [ ] `/start`
- [ ] `/warps plot`
- [ ] generator own-plot placement
- [ ] unauthorized plot denial
- [ ] trusted-player behavior
- [ ] AutoSell Chest same checks

## Tab completion

- [ ] `/eco <TAB>`
- [ ] `/eco give <TAB>`
- [ ] `/pay <TAB>`
- [ ] `/shop <TAB>`
- [ ] `/crates <TAB>`
- [ ] `/crates give <TAB>`
- [ ] `/ah <TAB>`
- [ ] `/rank <TAB>`
- [ ] `/gens give <TAB>`
- [ ] `/staff <TAB>`
- [ ] `/punish <TAB>`
- [ ] `/vupe setup <TAB>`

## Restart/load

Restart at least three times after transactions.

- [ ] Money stable
- [ ] AH escrow stable
- [ ] AH claim queues stable
- [ ] crate pending rewards stable
- [ ] generators stable
- [ ] AutoSell Chests stable
- [ ] progression stable
- [ ] PlotSquared stable
- [ ] LuckPerms stable

Then profile real load before public launch.

# External config installation — Vupe 2.1

Vupe 2.1 only ships external configuration support for plugins that remain
external.

The `external-configs/` folder contains:

```text
TAB
LuckPerms
Vault
PlaceholderAPI
Multiverse-Core
Via
CoreProtect
Tebex
```

There are intentionally no external folders for:

```text
EzEconomy
EconomyShopGUI
CrazyCrates
EzAuction
```

because VupeCore now owns all four systems natively.

## Safe merge rule

For retained plugins:

1. Install the JAR.
2. Start once.
3. Stop.
4. Back up the generated plugin folder.
5. Merge the supplied Vupe overlay/notes.
6. Start again.
7. Verify with `/vupe integrations`.

Do not blindly replace an entire newer plugin config with an older partial
overlay.

## TAB

Use the supplied Vupe TAB configuration to provide:

```text
header/footer
LuckPerms prefixes
player-list sorting
sidebar
Vupe PlaceholderAPI values
```

Important Vupe placeholders include:

```text
%vupe_money%
%vupe_crystals%
%vupe_gold%
%vupe_rank%
%vupe_level%
%vupe_prestige%
%vupe_genslots%
%vupe_sellmulti%
```

## LuckPerms

Vupe groups are generated from Vupe configs:

```text
/vupe luckperms bootstrap
```

Do not edit Vupe's player JSON to grant staff authority.

## Vault

Vault is retained as the economy compatibility API.

There is no second economy-provider plugin.

VupeCore itself registers the Vupe Money implementation.

## PlaceholderAPI

VupeCore registers `%vupe_*%` automatically when PlaceholderAPI is loaded.

## Multiverse-Core

Keep it for world administration.

PlotSquared still owns `vupeplots`.

## Via

Only install the Via stack if cross-version support is wanted.

Merge the supplied version-family configs and restart.

## CoreProtect

Optional independent rollback/audit layer.

## Tebex

Optional real-money store integration.

Tebex fulfillment should call:

```text
/vupegrant PLAYER <grant>
```

rather than editing Vupe files directly.

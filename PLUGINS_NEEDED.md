# Vupe 2.1 — plugin list

## Required

```text
VupeCore 2.1.0
PlotSquared
FastAsyncWorldEdit
LuckPerms
Vault
PlaceholderAPI
Multiverse-Core
TAB
```

That is the complete required gameplay stack for the intended Vupe MAX build.

## Optional

```text
ViaVersion
ViaBackwards
ViaRewind
CoreProtect
Tebex
Votifier-compatible receiver
```

Use the Via stack only if you want cross-version Java clients.

CoreProtect is useful as a separate rollback/audit layer.

Tebex is only needed if `/store` is connected to real purchases.

A Votifier-compatible receiver is only needed for public vote-site callbacks.

## Explicitly NOT needed

Vupe 2.1 no longer needs:

```text
EzEconomy
EconomyShopGUI
CrazyCrates
EzAuction
Skript
SkBee
skript-gui
MongoDB / MongoSK
EssentialsX
Vault economy provider plugin
Citizens
PlayerVaultsX
```

### Command ownership

```text
/bal /balance /pay /eco /baltop -> VupeCore
/shop                           -> VupeCore
/crates                         -> VupeCore
/ah /auction                    -> VupeCore

/plot                           -> PlotSquared
/lp                             -> LuckPerms
/mv                             -> Multiverse-Core
/tab                            -> TAB
/papi                           -> PlaceholderAPI
```

Vault remains required as the common API bridge. VupeCore itself provides the
Money economy to Vault.

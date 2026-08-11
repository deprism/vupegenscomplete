# Vupe MAX 2.1 feature ownership

| System | Owner |
|---|---|
| Money / Vault economy | **VupeCore** |
| Full `/shop` | **VupeCore** |
| Crates / keys / animations | **VupeCore** |
| Auction House / escrow / claims | **VupeCore** |
| Generators | VupeCore |
| Crystals / Gold | VupeCore |
| Crystal Emporium | VupeCore |
| Store preview/fulfillment | VupeCore |
| AutoSell Chests | VupeCore |
| Rank progression / levels / prestige | VupeCore |
| Fishing / mining / farming | VupeCore |
| Teams / social / coinflips | VupeCore |
| Staff GUI / punishments / reports | VupeCore |
| Missions / events | VupeCore |
| Plot world/protection | PlotSquared + FAWE |
| Permission/group authority | LuckPerms |
| World administration | Multiverse-Core |
| Placeholder bridge | PlaceholderAPI |
| TAB/nametags/sidebar | TAB |
| Cross-version protocol translation | Via stack (optional) |
| Independent rollback audit | CoreProtect (optional) |

## Native-commerce source baseline

Vupe 2.1 explicitly uses the supplied historical Skripts as the behavioral
baseline:

```text
economy/commands.sk
economy/shop.sk
economy/selling.sk
economy/autosell.sk
economy/crates.sk
economy/auctionhouse.sk
```

The four commerce systems are no longer simplified wrappers around external
plugins.

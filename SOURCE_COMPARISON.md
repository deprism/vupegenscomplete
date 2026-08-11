# Source comparison basis

Vupe MAX 2.1 was redesigned using the supplied historical server archives as
the feature-depth baseline rather than using VupeCore 1.x as the baseline.

## Archives inspected

- `elestra (1).zip` — 61 files, 51 Skript files, 10 YAML files
- `download-1786147779749.zip` — 58 files, 58 Skript files, 0 YAML files
- `lilygens.zip` — 0 files, 0 Skript files, 0 YAML files

## Elestra/Vute patterns explicitly restored or exceeded

The source review covered, among other systems:

- deep category-based Crystal Shop
- full material shop
- paginated auction browsing/claims/history/bids
- physical AutoSell Chest management
- rank/perk browser
- level/prestige confirmation and feedback
- crate reward previews
- punishment browsing/history
- GUI tags/chat color/player menus
- title/actionbar/sound feedback
- generators and generator upgrades
- fishing/mining/farming enchant progression
- teams/social systems
- vote/event rewards
- staff/moderation workflows

Vupe MAX does not blindly clone old implementation details. For the four
commerce systems requested here—Money, Shop, Crates and Auction House—the old
Skripts are now the behavioral baseline and the implementations are native
VupeCore Java. Plot/world/permissions/presentation systems remain delegated to
specialists such as PlotSquared, LuckPerms, Multiverse and TAB.

## LilyGens limitation

The resent `lilygens.zip` is a valid but empty ZIP containing **0 files**.

Therefore this project does **not** claim to have reverse-engineered LilyGens
mechanics, GUIs, economy values or configurations from that upload. Any
statement that it does would be fabricated.

The MAX design can still be compared against LilyGens during live testing if a
non-empty archive, screenshots, command list or playable server reference is
provided later.

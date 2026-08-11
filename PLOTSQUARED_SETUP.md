# Vupe MAX 2.1 — PlotSquared setup

PlotSquared is the only plot backend in Vupe MAX.

VupeCore does not implement a competing `/plot` command or internal island/plot
database.

## Required plot stack

```text
PlotSquared 7.5.13
FastAsyncWorldEdit 2.15.3
LuckPerms
VupeCore 2.1.0
```

## Fresh world

Install PlotSquared + FAWE, start the server, then run:

```text
/plot setup
```

Create the Vupe plot world as:

```text
vupeplots
```

VupeCore expects that name by default:

```yaml
worlds:
  plots:
    world: vupeplots
    backend: PLOTSQUARED
```

Change both sides if you deliberately use another world name.

## Existing world warning

If an older VupeCore build created a normal/internal `vupeplots/` folder, do not
simply install PlotSquared over it.

For an empty test server:

1. Stop the server.
2. Back up the old world.
3. Remove the obsolete internal plot world.
4. Start.
5. Create the new PlotSquared world with `/plot setup`.

For a world containing real player builds, migrate/back it up separately before
changing the generator/backend.

## Vupe integration

Vupe uses PlotSquared for:

- `/start`
- `/warps plot`
- plot NPC routing
- generator placement authorization
- AutoSell Chest placement authorization
- custom container protection
- donor/progression plot limits

VupeCore grants the calculated:

```text
plots.plot.<limit>
```

permission through LuckPerms bootstrap/rank synchronization.

The effective plot limit is the maximum of:

- default plot limit
- donor rank plot limit
- progression rank plot limit

## Player flow

Fresh player:

```text
/start
```

If the player owns no plot, Vupe routes them to PlotSquared auto-claim.

If the player already owns a plot, Vupe routes them to their plot home.

Players can still use PlotSquared directly:

```text
/plot auto
/plot home
/plot info
/plot trust PLAYER
/plot untrust PLAYER
/plot merge
/plot clear
```

The exact command tree/permissions are owned by the installed PlotSquared
version.

## Verification

With a NON-OP account:

```text
/start
/plot info
/warps plot
```

Then test:

1. Place a Vupe generator on your own plot.
2. Try placing one on a plot you cannot build on.
3. Trust a second account and retest.
4. Place an AutoSell Chest on the owned plot.
5. Confirm unauthorized players cannot manage it.

If Vupe says PlotSquared is missing:

```text
/plugins
/vupe integrations
/vupe doctor
```

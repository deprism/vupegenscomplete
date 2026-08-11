# Vupe MAX hosting profile

Vupe MAX intentionally removes the previous 1 GB design target.

The supplied defaults assume a materially larger server plan and prioritize
visual quality, active GUIs, holograms, external systems, faster generator
cycles and normal modern view/simulation distances.

## Starting point

```text
Java 21
view-distance 10
simulation-distance 8
100-player configured cap
20-tick Vupe generator cycle
500 generated-item-per-chunk Vupe safety ceiling
```

These are starting values, not a promise that any arbitrary host can run 100
active players.

## Still keep safety boundaries

“Infinite RAM” is not a real JVM/server condition. MAX removes artificial
1 GB compromises, but retains configurable limits for:

- generated entities
- AutoSell Chest count
- hopper/chest ownership
- minion count
- GUI task cleanup
- batch mine generation
- mission/event intervals
- persistence

Those prevent accidental runaway systems and exploits.

## Profile the actual server

Use Paper's supported profiling tooling under realistic load and measure:

- tick time
- entity count
- loaded chunks
- GC behavior
- PlotSquared/FAWE operations
- generator output
- AutoSell network activity
- auction load
- TAB/placeholder refresh
- scheduled tasks

Increase quality/concurrency from measured headroom, not guesses.

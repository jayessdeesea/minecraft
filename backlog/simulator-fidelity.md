---
status: ready
priority: P1
---

# A simulator faithful enough to design in, and told when it isn't

## Why
The operator (2026-11-20): testing a scenario in Minecraft is expensive. The squad simulation
(`SimServer`) should be cheap and faithful enough to try algorithms in - opening chests, the
cheapest route, a denser farm - and to work out the best layout or drill there, leaving the live game
only to confirm it. And where the two part - fine in the simulation, poor in the game - the force
should say so, not leave it to be noticed.

A gap between them is fixed before work that leans on it: a design proven in a simulation that gets the
world wrong is proven wrong.

## What
1. **Known gaps, fixed in both the simulation and the soldier's world model** where the model is wrong too:
   - A torch on a fence post: the server takes it; the world model reads the cell over a fence as solid
     (a fence's 1.5-high collision), so none is ever planned there (backlog/renewables.md).
   - The gaps in backlog/physics-fidelity.md, each when it costs something.
2. **A prediction ledger.** Each routine that plans states what it expects, and the shell logs what it
   got, under one key per kind of act: a route's ticks per block and the blocks it places, a dig's time,
   a placement accepted or refused, a chest's open and what it held, a harvest's yield, a task's yield
   for the time spent (docs/conventions.md, Check the outcome). The same ledger runs in the squad
   simulation. Its rows are the comparison.
3. **A divergence check.** Per kind of act, the live ledger set against the simulation's over the same
   kind of ground: a rate off by more than a set factor (say 1.5x), or an outcome the simulation never
   produces (a refusal, a block that came back), is logged loud (`[Fidelity]`) with its examples, and
   the dawn's after-action review lists the worst. Each is a lead for item 1.
4. **Deterministic.** A scenario passes or fails the same alone as under the full suite's load: the
   simulated clock, not the machine's speed, sets what happens. Seen: `aChunkFarmsTenderWithLogsAndNoIronStillFencesIt`
   passed alone and failed in a full verify, its chest not yet stood.
5. **Recorded worlds.** A live failure's ground - the chunks round it, as the client decoded them -
   saved so the same situation is replayed in the simulation, not rebuilt by hand.

## Done when
- A torch is put on a fence post in the simulation and live, and the chunk farm plans them.
- The ledger runs live and in the simulation, and a `[Fidelity]` line names each act whose live rate is
  off the simulation's - shown first on a gap planted in the simulation on purpose.
- A live failure's chunks replay in the squad simulation.

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
   the gaps in backlog/physics-fidelity.md, each when it costs something.
2. **A prediction ledger.** Built for routes (`app.Ledger`, `[Ledger]` every ten minutes: a route's ticks a step
   planned, from its first route to the goal to standing there). Left, the other kinds: the blocks a route places, a
   dig's time,
   a placement accepted or refused, a chest's open and what it held, a harvest's yield, a task's yield
   for the time spent (docs/conventions.md, Check the outcome). The same ledger runs in the squad
   simulation. Its rows are the comparison.
3. **A divergence check.** Built for routes: a spell's ticks a step planned, over all its routes, more than 1.5x off
   the simulation's flat walk (`bot.Trip.FLAT_TICKS_A_STEP`, measured by `DeterministicSoldierTest`) is logged
   `[Fidelity]`. Left: the same for the other kinds, against the simulation over the same kind of ground. Per kind of act, the live ledger set against the simulation's over the same
   kind of ground: a rate off by more than a set factor (say 1.5x), or an outcome the simulation never
   produces (a refusal, a block that came back), is logged loud (`[Fidelity]`) with its examples, and
   the dawn's after-action review lists the worst. Each is a lead for item 1.
4. **Deterministic.** A scenario passes or fails the same alone as under the full suite's load: the
   simulated clock, not the machine's speed, sets what happens. Every executor is the simulated clock's, on its
   test's thread; the clock reads left on the wall are chat's de-duplication and arrows' flight. Seen once:
   `aChunkFarmsTenderWithLogsAndNoIronStillFencesIt` failed under load, a builder's stand not got to in its walk's
   allowance and its cell skipped for good - the skip now lapses, but what made the walk slower under load is open.
   Seen: RoutingSimulationTest's gallery with a gap, the surface 12 up, passes with or without
   `Recovery.waitingOnABlock` - in it the block for the way comes due before the recovery drill lays a stair up;
   live (field533), the stair came first and held the miner's hands. What sets that order live is a lead.
   Seen on Eli's own ground (`RoutingSimulationTest.eliOnHisOwnGroundGetsToHisBranch`, field535): live he couldn't
   walk to a stand two blocks off, and the server's blocks there, probed, are the model's; replayed, he walks it. Off
   the probed cells, the two part in what the soldier does, not in the ground - a lead. And replayed he reaches his
   branch by quarrying 6 stone, a pillar 6 high and a stair up - not by bridging his gap; the cleaner way is left.
   And live (field536) with 62 logs he gave up his stone sword "short of COBBLESTONE=2", and his quarry order got
   no hands; replayed with the same kit, he digs the cobblestone and makes the sword. His squad's orders - a
   miner's duty, its mine - aren't in the replay: a recorded world wants the soldier's orders kept with it.
5. **Recorded worlds.** Built: a soldier stuck a minute has the ground round it - 49 across, 33 high, each cell's
   state as the client decoded it - kept under `garrison-state/worlds/` (`app.WorldRecords`, `[World]`), and a
   scenario replays one as its terrain (`SimWorlds.replay`). Left: a scenario per live failure kept that way.

## Done when
- The ledger runs live and in the simulation, and a `[Fidelity]` line names each act whose live rate is
  off the simulation's - shown first on a gap planted in the simulation on purpose.
- A live failure's kept ground replays in a squad-simulation scenario that shows the failure.

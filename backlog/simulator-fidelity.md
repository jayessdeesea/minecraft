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
   the gaps in backlog/physics-fidelity.md, each when it costs something. The squad simulation times a dig by the tool in hand, as the
   server does (`SimItems.dugTooFast`); left of the hotbar: a task keeping the right tool ready - the hoe or an empty
   hand to harvest, the sword within reach - measured in the time it costs.
   A lead, the bucket: live (field562-563) a pour at the chunk farm at (1, 3) failed "expected water at (20, 67,
   60)", and flowing water stood at y=68-69 over its farmland at 67. The simulation traces the pour's ray as the
   server does (`SimBuckets`), stopping at the first block that's neither air nor water - wheat and grass included.
   Whether the server's ray stops at a crop's outline the same way, and puts the water in front of it or in its
   place, decides where a pour over a planted farm lands; probed live, either way it's a scenario.
   `stuck/flooded-farm-olga-field563` is a soldier caught in that water.
2. **A prediction ledger.** Built for routes, harvests and sowing (`app.Ledger`, `[Ledger]` every ten minutes; a
   farmer's kinds from `Farmer.takeYields`). Left, the other kinds: felling and quarrying, the blocks a route places,
   a dig's time, a placement accepted or refused, a chest's open and what it held, a task's yield
   for the time spent (docs/conventions.md, Check the outcome). The same ledger runs in the squad
   simulation. Its rows are the comparison.
3. **A divergence check.** Built for the ledger's kinds: a spell's rate more than 1.5x off the simulation's
   (`Ledger.BASELINES`) is logged `[Fidelity]`. Left: the same for the other kinds, against the simulation over the same kind of ground. Per kind of act, the live ledger set against the simulation's over the same
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
5. **Recorded worlds.** Built: a soldier stuck a minute, or killed, has the ground round it - 49 across, 33 high, each cell's
   state as the client decoded it - kept under `garrison-state/worlds/` (`app.WorldRecords`, `[World]`), and a
   scenario replays one as its terrain (`SimWorlds.replay`); a stuck soldier's case in the registry
   (`StuckEvalsTest`, docs/architecture.md) is one. The live record drafts the case beside it (`app.StuckCase`). Left: the
   harness gives the soldier its own order, not a platoon leader's objective: replayed with an objective, `stuck/cave-eli-field552` (a gallery broken into a cave) had it
   put on the cave's floor 17 below by the night rules, so it can't yet show the miner's failure.

## Done when
- The ledger runs live and in the simulation, and a `[Fidelity]` line names each act whose live rate is
  off the simulation's - shown first on a gap planted in the simulation on purpose.
- A live failure's kept ground replays in a squad-simulation scenario that shows the failure.

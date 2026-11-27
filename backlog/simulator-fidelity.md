---
status: ready
priority: P1
---

# A simulator faithful enough to design in, and told when it isn't

## Why
The operator (2026-11-20): a live scenario is expensive, so the squad simulation (`SimServer`) should be faithful
enough to design layouts and drills in, and the force should say where the two part. Today the prediction ledger
(`app.Ledger`) covers three kinds of act - routes, harvests, sowing - its baselines are constants, not a ledger run in
the simulation, and a divergence is only a rate; a recorded world replays as terrain (`SimWorlds.replay`,
`StuckEvalsTest`) but without the soldier's orders. A design proven in a simulation that gets the world wrong is
proven wrong, so a gap is fixed before work that leans on it.

## What's left
1. **The ledger's other kinds**: felling and quarrying, the blocks a route places, a dig's time, a placement accepted
   or refused, a chest's open and what it held, a task's yield for the time spent (docs/conventions.md, Check the
   outcome).
2. **The same ledger run in the squad simulation**, its rows the comparison per kind of act over the same kind of
   ground, in place of the fixed `Ledger.BASELINES`.
3. **The divergence check widened**: `[Fidelity]` for the new kinds; for an outcome the simulation never produces (a
   refusal, a block that came back), with its examples; and the dawn's after-action review listing the worst. Each is
   a lead for item 5.
4. **Recorded worlds with their orders**: a record keeps the soldier's order and its squad's (a miner's duty, its
   mine), and the replay harness gives the soldier that order rather than a platoon leader's objective - replayed with
   an objective, `stuck/cave-eli-field552` was put on the cave's floor 17 below by the night rules, so it can't show
   the miner's failure. The order's shape is backlog/soldier-order.md's.
5. **Known gaps**, each when it costs something (the physics ones are docs/echelons/soldier/capabilities.md's, "What it approximates"):
   - The hotbar: a task keeping the right tool ready - the hoe or an empty hand to harvest, the sword within reach -
     measured in the time it costs.
   - The bucket's ray (field562-563: a pour at the chunk farm at (1, 3) failed "expected water at (20, 67, 60)",
     flowing water over its farmland): `SimBuckets` stops the ray at the first block that's neither air nor water,
     wheat and grass included. Whether the server's ray stops at a crop's outline, and puts the water in front of it
     or in its place, is to probe live; either way it's a scenario.
6. **Deterministic under load**: a scenario passes or fails the same alone as under the full suite. Leads:
   - `aChunkFarmsTenderWithLogsAndNoIronStillFencesIt` failed once under load, a builder's stand not reached in its
     walk's allowance; what made the walk slower is open.
   - `RoutingSimulationTest`'s gallery with a gap passes with or without `Recovery.waitingOnABlock`: in it the block
     for the way comes due before the recovery drill lays a stair up; live (field533) the stair came first and held
     the miner's hands. What sets that order live is open.
   - `RoutingSimulationTest.eliOnHisOwnGroundGetsToHisBranch` (field535): live he couldn't walk to a stand two blocks
     off on ground the probe shows is the model's; replayed, he walks it - the two part in what the soldier does, not
     in the ground. Replayed he reaches his branch by quarrying, a pillar and a stair, not by bridging his gap.
7. **The server's facts from a source of their own.** `SimItems.dugTooFast` times a dig with the bot's own
   `Hardness.ticks`, so a wrong dig time is the same wrong time on both sides, and the sim passes it. It refuses a
   dig under 70% of that time and never notices one too slow. `bot`'s fakes (`FakeCraftingServer`,
   `FakeChestServer`, `Battle`) are a second model of the server, checked against neither `SimServer` nor live play
   (docs/test-map.md). Wanted: tables of what the real server answers, measured live and checked in. A dig's ticks
   come first, per block and tool. Both simulators read those tables, and the bot's estimate is held to them in both
   directions: never early, never far late.

## Done when
- The ledger runs live and in the simulation, and a `[Fidelity]` line names each act whose live rate is off the
  simulation's - shown first on a gap planted in the simulation on purpose.
- A recorded world replayed with its soldier's own order shows the miner's failure of `stuck/cave-eli-field552`.

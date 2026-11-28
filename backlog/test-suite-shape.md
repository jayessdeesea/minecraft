---
status: ready
priority: P2
---

# The test suite: shared fixtures, one home for the body's measures, outcomes not log lines

## Why
[docs/test-map.md](../docs/test-map.md) maps the suite. A read of the farming tests in depth - `FarmerTest`,
`FarmerFixturesTest`, `ChunkFarmTest`, `FarmPlanTest`, `FarmMissionsTest`, `CampFarmTest`,
`FarmDutiesSimulationTest`, `FoodSimulationTest`, `FarmMissionSimulationTest` - found each level mostly asserting what
only it can show. Repeated assertions were about a tenth of the methods. The larger cost is setup: perhaps a third of
the squad sims' lines. A test that is mostly setup is one a smaller model can't change with confidence
(docs/conventions.md, "The measure is whether a smaller model can make the change").

## What's left
1. **One squad builder for the sims.** `new ChainOfCommand(...)` with its scripted leaders is written out in 23 `app`
   test files, 19 of them with `new SoldierRunner(...)` beside it: the squad sims, `StuckEvalsTest`, `StuckTriageIT`
   and `PavingRateIT` among them. One builder takes the ground, the kit and the squad.
   `FarmDutiesSimulationTest.farmSquad` is a start, unused by three of its own tests. So is the `squadLeader(Position)`
   stub, copied in `FarmDutiesSimulationTest`, `RoutingSimulationTest` and `WoodDutiesSimulationTest` beside
   `SquadSimulationTest`'s, and the pond terrains each sim file defines for itself.
2. **The soldier tier's ground through its fixtures.** `FarmerTest` builds about 15 `WorldView`s inline, and
   `FarmPlanTest`, `FarmerFixturesTest`, `CampFarmTest` and `FarmMissionsTest` each have their own. `SyntheticWorld`
   exists for this. The `item(...)`, `soldier(...)`, `world(...)` and `flat(...)` helpers are redefined in 7 to 13
   files each.
3. **The body's measures in one home.** Eye height (1.62) is written out again in `craft/Crafter`,
   `engineer/Clearing` (`EYES`), `supply/Builder`, `supply/Miner`, `supply/WaterCarrier`, `supply/Farmer` and
   `SoldierRunner`; body height (1.8) in the tests' `scenario/Battle.PLAYER_HEIGHT` and
   `SoldierScenarioTest.BODY_HEIGHT`. Each reads `BodyMeasures` instead.
4. **One known-failure mechanism.**
   `FarmDutiesSimulationTest.aBakerPulledOffByAFightIsShortForThatCauseNotReordered` pins a bug by hand
   (`assertTrue(!correct)`), passing while it's broken. It becomes a `knownFailure` mark, as `StuckEvalsTest` has
   them, so the fix takes the mark off instead of inverting an assertion.
5. **A sim asserts an outcome.** `FarmDutiesSimulationTest` asserts DUTY wording, "Dana drew WHEAT" and report
   strings, and `FoodSimulationTest` counts `[Register]` and "no ground to farm" lines. Each assertion is moved to the
   world or the registers where those can show it.
6. **The repeats folded.**
   - `FoodSimulationTest`'s two farm-growth cases leave the rule to `FarmPlanTest`, except the three-pond case, which
     moves there.
   - `FarmMissionSimulationTest`'s five "its N steps, each DONE" blocks become one helper.
   - The four `hungrySquadFarms` cases become one parameterised test.
7. **The other areas read the same way.** Only farming was read in depth. Each other area of docs/test-map.md is read
   for the same faults, and its fixes added here.

## Done when
- A new squad sim is its world, its squad and its outcome, with no wiring of its own.
- No class but `BodyMeasures` holds a body measure.
- No test asserts the inverse of what is correct.

## Notes
- Each of 1-3 is behaviour-preserving and one refactor pass's extraction (docs/conventions.md, "Refactor every
  sixteenth commit, between profiles").
- The two simulators' shared facts about the server, and checking them against the real one, are
  backlog/simulator-fidelity.md's (item 7), not this item's.

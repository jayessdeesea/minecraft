---
status: ready
priority: P1
---

# The verify's time: a budget by test, and the simulations that prove the same thing twice

## Why
A commit's verify takes about 28 minutes, and it grows with every simulation added (operator, 2026-11-23: are
duplicate tests increasing the test time?). The time isn't spread: of 222 suites and 1,935 tests summing to about
4,570 s, eleven slow simulation classes are 90% - `FarmMissionSimulationTest` alone 1,081 s for 8 tests (135 s each),
then `TaskOrganisationSimulationTest` 612 s, `FlattenTopsSimulationTest` 441 s, `CampLightSimulationTest` 343 s,
`WallSimulationTest` 341 s, `MineSimulationTest` 307 s, `KeepSimulationTest` 270 s, `FoodSimulationTest` 205 s,
`CombatConditionMatrixTest` 184 s, `SquadSimulationTest` 175 s, `FarmDutiesSimulationTest` 166 s. The other 1,800
tests are under a tenth. So the cost isn't tests duplicated word for word; it's one behaviour proved at two levels - a
mission simulation runs a flatten, a floor and a template end to end, and the flatten, wall, light and farm-duties
simulations run those phases again - and a mission test that flattens a chunk before reaching the step it's about.

## Done when
- **The verify says where its time went, against the run before.** Built: `tools/ops/hotspots.sh` lists the last
  verify's slowest fifteen and the sum, and `commit.sh` prints them after each verify. Left: each one's change against
  the run before, so growth shows in the commit that caused it.
- **A test's time is capped**, a ratchet in `ArchitectureTest` like the line caps: a simulation over the cap is
  justified or cut down, and a cap only comes down.
- **Each of the eleven slowest classes has been gone through once**: for every test, the one behaviour only it proves
  is named; a test whose behaviour a mission-level test already proves is dropped, or cut to a unit test of the pure
  decision. What was dropped and what it was covered by is said in the commit.
- **A mission test starts where its subject starts**: one about a template's step begins on ground already flat and
  floored (a prepared `SimServer` world), not by flattening for an hour of simulated time.
- The verify is measurably shorter, and the number is in DEVELOPMENT.md where the verify's time is stated.

## Notes
- Pulled forward (operator, 2026-11-24). On that day's verify: 1,961 tests, 8,234 s summed across the parallel forks;
  `FarmMissionSimulationTest`'s twelve were ten of the fifteen slowest, 225 to 535 s each - every one flattens or
  floors a chunk before its subject. The prepared ground is the first cut.
- Not wanted: a mechanical detector of duplicate tests (same setup, same assertions). These overlap by scenario, not by
  text, and it would find little.
- The stuck evals aren't a cost: `StuckEvalsTest` is 12 s, and `StuckTriageIT` runs by name, outside the suite.
- The timing report and the cap are small and can ride with any commit; the pass over the classes and the prepared
  ground are a session of their own.

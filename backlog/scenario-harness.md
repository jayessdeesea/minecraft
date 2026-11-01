---
status: ready
priority: P1
---

# Scenario harness: standard situations, many variations

## Why
Soldier and squad leader behaviour is tested one situation at a time (unit tests) or not at all
(live runs). Behaviour should be judged across many variations of a standard situation instead:
terrain types, battle drills, numbers of hostiles, start positions. It should be reproducible from
a seed, and it shouldn't need a live server.

## Done when
- A `Scenario` generator in `bot`'s tests builds a seeded synthetic `WorldView`:
  - Terrain families: flat, rolling hills, forest, pond or lake, ravine, walled compound,
    multi-storey building.
  - Placements for the squad and the objective.
- JUnit `@TestFactory` dynamic tests run each family across N seeds. Each case is named with its
  family and seed, so a failure reproduces exactly. A failing seed worth keeping is pinned as an
  explicit regression case.
- Soldier properties checked across all variants, for example:
  - It reaches a reachable objective within a tick budget.
  - It reports CANTCO, rather than `Moving` forever, when the objective is unreachable.
  - It never enters a hazard cell.
  - Its body never overlaps a solid block.
- A squad-leader evaluation, separate from the unit test suite, runs the same scenarios through
  the real `SpringAiSquadLeader` against Ollama and reports a pass rate per model and per prompt
  version.

## Notes
- **Decision: no jqwik.** It was considered for property-based testing and rejected:
  - From 1.10 its maintainer asks that it not be used by AI coding agents. Pinning a pre-1.10
    release would sidestep that request rather than respect it, and freeze us on an unmaintained
    version.
  - It's a separate JUnit Platform engine, and compatibility with our JUnit 6 line isn't a given.
  - What these scenarios need is seeded generation plus reproducibility, which takes a few
    hundred lines here. Automatic shrinking matters less, because a synthetic world is easier to
    read than a minimised random input.
- The squad-leader evaluation is statistical by nature, so it reports a rate rather than
  asserting pass/fail, and it doesn't belong in `mvn test`. The scratchpad benchmark from the
  model comparison (AGENTS.md § Behavior modeling, "Measured") is the prototype:
  4 situations x 3 runs, scored on reachable objective, correct height, direction and CANTCO
  judgement.
- Depends on nothing, but mob-simulation.md extends it.

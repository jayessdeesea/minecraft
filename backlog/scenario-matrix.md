---
status: ready
priority: P1
---

# A scenario matrix: the evals we run regularly

## Why
The offline suites and the live harness (`app`'s `ChainScenarioLiveIT`, seven scenarios) are listed with how to run
them in DEVELOPMENT.md. What they don't cover: variation in biome, time of day, difficulty and force size; most
mission kinds; the bow; and two live misses of the squad leader that no variant reproduces. A regression in any of
those shows up only if someone happens to run the case by hand.

## What's left
- **A FAR variant of `SquadLeaderScenarioIT`** (docs/prompting.md, a far objective is not an unreachable one): the
  objective beyond the surveyed area, so `<objective_ground>` says "outside the surveyed area". Live (2026-11-13) the
  squad leader sent its squad to the highest reachable hill and it stood there a day. Needs the synthetic world wide
  enough for an objective past the survey's radius, and the progress check scored toward it.
- **The squad leader's false CANTCO, as live** (field54-55: CANTCO many times an hour, often with "but the squad can
  still proceed"). The `STUCK`, `STRANDED` and `STRUGGLING` variants pass, so they don't reproduce it. What live has
  that they don't: two or three soldiers reporting "no route" at once - for the 20 reports before the recovery drill
  has each - and tasks other than a move (FORTIFY, SECURE, a patrol base). When a CANTCO is right is
  backlog/authority-and-missions.md's.
- **The combat matrices' gaps** (`CombatMatrixTest`, `CombatPairsMatrixTest`, `CombatSquadMatrixTest`, each with its
  `KNOWN` cells): the bow, which isn't in the battle simulation's fights; slopes and caves as grounds; and each known
  cell's fix - the creeper on hills and in a pond, where a sprint doesn't get clear; a pillager on hills with a wooden
  sword or a sword and shield; the drill that kills a creeper (a bow, or a blow before it lights) rather than kiting
  it at 7.5 for as long as it follows; two soldiers against a zombie and a skeleton on hills.
- **Live scenarios to join the harness's list**, each a mission with a measurable end state: move to an objective
  across each terrain type; gather wood or stone until a stockpile target is met; escort a player across terrain
  without the player taking damage; react to contact - one squad engaged, the other supports; survive with no rations
  and food nearby.
- **Dimensions to vary**, as seeded variation on each scenario:

| Dimension | Values |
|---|---|
| Terrain | flat, hills, forest, water crossing, ravine, abrupt cliffs and drops, caves underfoot |
| Biome | plains, forest, desert (no trees), snow, swamp, jungle; Nether later |
| Time | morning, an hour before dusk, night |
| Difficulty | peaceful, easy, normal, hard (zombies break doors on hard) |
| Force | squads: 1 to 4; soldiers per squad: 2 to 4 (the roster is four squads of four) |
| Kit | bare-handed, wooden, iron; with or without rations, blocks, arrows |

- **Pinned orders for a reflex scenario**: the chain of command reacts to the setup itself (making it night to stage
  a skeleton fight had the platoon leader order a patrol base, and the soldier walked away). Either an operator order
  that holds a squad in place, or a harness mode with the platoon leader and squad leaders stood down.

## Done when
- The FAR and false-CANTCO variants run in `SquadLeaderScenarioIT` with a pass rate each.
- Each live scenario above is in `ChainScenarioLiveIT`'s list, set up from the console (`difficulty`, `time set`,
  `locate`, `spreadplayers`, `give`), judged from the log and server queries, and reported as a pass rate.
- The combat matrices have a bow column and slope and cave grounds, and no `KNOWN` cell left.

## Notes
- **Decided:** the live tier runs targeted scenarios when a change touches them, and the full matrix only before a
  pin bump or a model change. Nothing runs on a schedule: each live scenario takes minutes and holds the GPU.
- A scenario is a test fixture, so it lives in code (`bot`'s `scenario` package offline, `app`'s harness live), and
  this item becomes docs when it lands.

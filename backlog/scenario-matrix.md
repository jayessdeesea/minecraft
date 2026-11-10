---
status: ready
priority: P1
---

# A scenario matrix: the evals we run regularly

## Why
The live runs that verify each feature are evals, but ad hoc: a hand-written mission, a console
command or two, reading the log. Three offline suites exist:
- `SoldierScenarioTest`: terrain families, seeded.
- `CombatScenarioTest`: simulated zombie, skeleton, creeper, and a pack.
- `SquadLeaderScenarioIT`: the real squad leader model against those scenarios, as a pass rate.
- `GeneralScenarioIT`: the real general model on fixed policy situations, as a pass rate.

Nothing covers variation in biome, time of day, difficulty, squad size or number of squads.

A regression in one of those shows up only if someone happens to run that case by hand.

## Done when
The live tier has a harness (`app`'s `ChainScenarioLiveIT`, DEVELOPMENT.md § 2) and seven
scenarios: `patrol-base-before-dark`, `hold-base-through-the-night`, `rearm-to-iron`,
`rearm-to-chestplate`, `shield-against-skeletons`, `climb-soil-terrace` and `climb-stone-terrace`. What's left is below;
scenarios join the harness's list.

A named list of scenarios, each a fixed setup plus seeded variation, runs in two tiers:
- **Offline, every build:** simulated world, scripted or faked model. Cheap and deterministic.
  This is the existing suites, extended.
- **Live, on demand or nightly:** dev server plus the real models.
  - Set up from the console: `difficulty`, `time set`, a biome found with `locate`, players
    placed with `spreadplayers`, items with `give`.
  - Judged from the log and server queries, as this session's checks were (blocks placed, items
    held, deaths).
  - Reported as pass rates per scenario, since the models are nondeterministic.

Dimensions to vary:

| Dimension | Values |
|---|---|
| Terrain | flat, hills, forest, water crossing, ravine, abrupt cliffs and drops, caves underfoot |
| Biome | plains, forest, desert (no trees), snow, swamp, jungle; Nether later |
| Time | morning, an hour before dusk, night |
| Difficulty | peaceful, easy, normal, hard (zombies break doors on hard) |
| Force | squads: 1, 2, 3; soldiers per squad: 2 to 4 (the roster is 2 squads of 4) |
| Kit | bare-handed, wooden, iron; with or without rations, blocks, arrows |

Scenarios, each a mission with a measurable end state:
- Move to an objective across each terrain type.
- Hold a position through a night.
- Build a patrol base before dark: the hut complete with everyone inside at dusk.
- Gather wood or stone until a stockpile target is met.
- Escort a player across terrain without the player taking damage.
- React to contact: one squad engaged, the other supports.
- Resupply and rearm from a bare-handed start.
- Survive: no rations, with food nearby.
- **The squad leader's false CANTCO, as live.** Live (field54-55) the squad leaders reported their
  task CANTCO many times an hour, often adding "but the squad can still proceed", and the general
  moved the squads on for it. `SquadLeaderScenarioIT`'s `stuck`, `stranded` and `struggling`
  variants (one stuck soldier; one recovering; one refusing work and one starving) all pass 14 of
  14, so they don't reproduce it. What live has that they don't: two or three soldiers reporting
  "no route" at once - for the 20 reports before the recovery drill has each - and objectives other
  than a move (FORTIFY, SECURE, a patrol base).

## Notes
- **Decided:** the live tier runs targeted scenarios when a change touches them, and the full
  matrix only before a pin bump or a model change. Nothing runs on a schedule: each live scenario
  takes minutes and holds the GPU.
- **A live scenario that tests a soldier's reflex has to pin the squad's orders.** The chain of
  command reacts to the setup itself. Making it night to stage a skeleton fight had the general
  order a patrol base, and the soldier walked away from the skeleton. Options: an operator order
  that holds a squad in place, or a harness mode with the general and squad leaders stood down.
- A scenario is a test fixture, so it lives in code (`bot`'s `scenario` package for offline, a
  live harness in `app` for live) and this item becomes docs when it lands.

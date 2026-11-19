---
status: ready
priority: P1
depends-on: []
doctrine: docs/conventions.md (source files under 1000 lines); docs/echelons/tenets.md
---

# Split the source files over 1000 lines

## Why
The operator's (2026-11-18): source files stay under 1000 lines. `ArchitectureTest.OVER_THE_LIMIT`
lists the files over it, each capped at its length then, to shrink to under.

## How
- **`app.SoldierRunner`** is the bulk. The split follows the tenets, not the line count: what it
  decides for a soldier - which chest to draw from, what to craft, hunt or store, the bed, the
  logs, the food - moves up to the squad leader's drills as orders (docs/echelons/tenets.md, the
  soldier owns how, never what; backlog/echelon-contracts.md, the runner keeps only the
  connections). What it does - the routines' wiring, the world model, the depot and its chests,
  the keep, the mine - moves into classes of their own beside it, one responsibility each.
- Begun: `app.SoldierErrands` holds the errands the soldier still decides for itself (its food, logs
  and a bed's string from the stockpile, what its pack keeps) - each to become the squad leader's
  order, the class to empty. `app.SiteGeometry` holds the pure geometry of the flatten and its ground
  orders' names.
- **`bot.DeterministicSoldier`**: its fight, its moves and its survival as separate pure parts. Begun: its body
  against the world - whether it fits, hazards and solids under its footprint, a drop, the cells cut to step
  up, what it can see - is `bot.Body`. Left: the fight out of `tick`, the moves out of `navigate`.
- **`app.ChainOfCommand`**: the platoon leader's guardrails apart from the wiring of the echelons. Begun: the
  Done: the day's guardrails are `DayGuardrails` and the night's `NightRules`, each over the day or night the
  chain hands it; the keep's mine sites `MineSites`. It's under the limit; the operator's orders are what's
  left of its own beside the wiring.
- The tests are all under: `SquadSimulationTest` split into a class a scenario family beside it
  (docs/architecture.md, the squad simulation), `DeterministicSoldierTest`'s fight into
  `DeterministicSoldierCombatTest`, the sim server's items into `SimItems`.

## Done when
`OVER_THE_LIMIT` is empty.

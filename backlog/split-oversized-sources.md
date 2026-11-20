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
  orders' names. Each squad's state is `app.SquadState`. What's left is two methods: `orderSquad` (1299
  lines, the squad loop) and `onJoined` (1187, each soldier's tick) - to be cut along the tenets above.
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

## The plan (agreed by the operator, 2026-11-20)
Cut along the tenets, each step a commit of its own with the ratchet lowered behind it. Begun: the walk's physics
- where the body is, how it falls, swims and steps - is `bot.Gait` (DeterministicSoldier 1453 -> 1338).

**`bot.DeterministicSoldier` (1453 lines)** - the soldier's body and mind in one tick. Three parts out, the class left
a façade whose `tick` asks each in turn and says the report:
1. `bot.Moves` - the route: `navigate` (202 lines), `pillar`, `assessPlan`, `priceOfARiskierRoute`, and their state
   (the route, its legs, `PlanMemo`, the snags) over `Gait`, which has the walk's physics.
2. `bot.Fight` - the threat chosen, closed with, struck, evaded, covered from (`tick`'s fight half, `evade`,
   `coverFrom`, `sweepHitsSquadmate`, the shield), and its state (the target, under fire). It hands `Moves` a goal; it
   never walks itself.
3. `bot.Survival` - wet and afloat, the fall, stuck in a block, a bed, what it does about each before its order.

**`app.SoldierRunner` (8254 lines)** - what's left is two methods.
- `onJoined` (1193 lines, each soldier's tick): the routines and their hands (`Hands.Routine`) into a class per
  soldier, `SoldierTick`, built once a connection. What it decides each tick - its tools wanted, its deposit's chest,
  its draws, its food (lines 7060-7280) - into one pure function, `TickPlan.of(status, inventory, views)`, a value the
  routines read. The routines' turns into four families beside it, each a class: supply (`SUPPLY_RUN`, `DEPOSITOR`,
  `HANDOVER`, `RATIONS`), works (`CLEARER`, `FILLER`, `MASON`, `PLACING`, `BUILDER`, `MINER`), field (`FARMER`,
  `FORESTER`, `GATHERER`, `HUNTER`, `SCAVENGER`, `LAMPLIGHTER`, `TORCHMAKER`), survival (`DOORWAY`, `SEALER`, `ARMOURER`).
- `orderSquad` (1300 lines, the squad loop) into the steps it already takes in order, each a class over the squad's
  state (`SquadState`) and a `SquadView` value (its statuses, the world, the chain), returning orders and the state it
  changes: the fight and cross-levelling (5079-5150); the base - breaches, lights, its torchbearer (5150-5190); production -
  its farm, grove, wall, the wood's and farm's duties (5288-5480); support - stairs, the second level, the workshop, its
  cutters (5474-5700); the lights and patrol round (5706-5740); and the orders handed down (the rest).
- Then the decisions the tenets give the squad leader - which chest, what to craft, hunt or store - move up into
  `command.DrillSquadLeader` as orders, as `SoldierErrands` already plans.

**Order of work**: `Moves`, `Fight`, `Survival`; then `TickPlan` and the routine families; then `orderSquad`'s steps.
Each step keeps every test and squad-simulation scenario passing unchanged - a split, no behaviour change - and lowers
its file's cap in `ArchitectureTest.OVER_THE_LIMIT`.

## Done when
`OVER_THE_LIMIT` is empty.

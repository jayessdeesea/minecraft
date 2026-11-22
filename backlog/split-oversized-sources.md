---
status: ready
priority: P1
depends-on: []
doctrine: docs/conventions.md (source files under 1000 lines); docs/echelons/tenets.md
---

# Split the source files over 1000 lines

## Why
The operator's (2026-11-18): source files stay under 1000 lines. `ArchitectureTest.OVER_THE_LIMIT` lists the files
over it, each capped at its length then, to shrink to under. One is left: `app.SoldierRunner`, 8070 lines, most of it
two methods - `orderSquad` (the squad loop, about 1300 lines) and `onJoined` (each soldier's tick, about 1200). Three
more sit at the limit, where the next edit fails the build: `bot.DeterministicSoldier` (989), `app.ChainOfCommand`
(998) and the test `app.SimServer` (1000 - the ratchet fails a file past 1000 that isn't listed).

## What's left
The plan agreed by the operator (2026-11-20): cut along the tenets, not the line count (docs/echelons/tenets.md - the
soldier owns how, never what; backlog/echelon-contracts.md - the runner keeps only the connections). Each step is a
split with no behaviour change - every test and squad-simulation scenario passing unchanged - a commit of its own,
with the file's cap in `OVER_THE_LIMIT` lowered behind it.

1. **`onJoined`, each soldier's tick.** The routines and their hands (`Hands.Routine`) into a class per soldier,
   `SoldierTick`, built once a connection. What it decides each tick - its tools wanted, its deposit's chest, its
   draws, its food - into one pure function, `TickPlan.of(status, inventory, views)`, a value the routines read. The
   routines' turns into four families beside it, each a class: supply (`SUPPLY_RUN`, `DEPOSITOR`, `HANDOVER`,
   `RATIONS`), works (`CLEARER`, `FILLER`, `MASON`, `PLACING`, `BUILDER`, `MINER`), field (`FARMER`, `FORESTER`,
   `GATHERER`, `HUNTER`, `SCAVENGER`, `LAMPLIGHTER`, `TORCHMAKER`), survival (`DOORWAY`, `SEALER`, `ARMOURER`). Each
   family takes its per-name maps into the soldier's state as it goes (`app.SoldierState`;
   backlog/soldier-state-machine.md).
2. **`orderSquad`, the squad loop,** into the steps it already takes in order, each a class over the squad's state
   (`app.SquadState`) and a `SquadView` value (its statuses, the world, the chain), returning orders and the state it
   changes: the fight and cross-levelling; the base - breaches, lights, its torchbearer; production - its farm, grove,
   wall, the wood's and the farm's duties; support - stairs, the second level, the workshop, its cutters; the lights
   and the patrol round; and the orders handed down. **With or after backlog/authority-and-missions.md step 4**
   (sectors): that step replaces the loop's per-soldier assignment rules - mine crew, keep crew, lamplighter, camp
   upkeep, forester, errands - so they're removed there, not moved into classes here first.
3. **The decisions the tenets give the squad leader** - which chest, what to craft, hunt or store - move up into
   `command.DrillSquadLeader` as orders, `app.SoldierErrands` emptying as they go (backlog/soldier-order.md).
4. **`bot.DeterministicSoldier`,** 11 lines under the limit:
   - `bot.Fight` - the threat chosen, closed with, struck, evaded, covered from (`tick`'s fight half, `evade`,
     `coverFrom`, `sweepHitsSquadmate`, the shield), and its state (the target, under fire). It hands the mover a goal;
     it never walks itself.
   - `bot.Survival` - wet and afloat, the fall, stuck in a block, a bed, what it does about each before its order.
5. **`app.ChainOfCommand`,** 2 lines under: the operator's orders (`onOperatorFarm`, `onOperatorCancel`,
   `onOperatorMission`, `onOperatorEscort`, `onOperatorFlatten`) into a class of their own beside the echelons'
   wiring. Its guardrails and night rules are folded into the platoon leader's one decision by
   backlog/authority-and-missions.md step 5 - not split further here.
6. **`app.SimServer`** (test), at the limit: its next growth goes into a class beside it, as its items went into
   `SimItems`.

**Order of work**: 1, then 2 (see its note), then 3; 4 to 6 whenever the file is next touched - before it, not after.

## Done when
`OVER_THE_LIMIT` is empty.

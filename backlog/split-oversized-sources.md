---
status: ready
priority: P1
depends-on: []
doctrine: docs/conventions.md (source files under 1000 lines); docs/echelons/tenets.md
---

# Split the source files over 1000 lines

## Why
The operator's (2026-11-18): source files stay under 1000 lines. `ArchitectureTest.OVER_THE_LIMIT` lists the files
over it, each capped at its length then, to shrink to under. One is left: `app.SoldierRunner`, most of it
two methods - `orderSquad` (the squad loop, about 1300 lines) and `onJoined` (each soldier's tick, about 1200). Three
more sit at the limit, where the next edit fails the build: `bot.DeterministicSoldier` (971), `app.ChainOfCommand`
(999) and the test `app.SimServer` (998 - the ratchet fails a file past 1000 that isn't listed).

## How it's worked
One step a refactor pass (docs/conventions.md, "Refactor every sixteenth commit, between profiles"), chosen by
`tools/ops/hotspots.sh`. On 2026-11-24 that read: `SoldierRunner` 31 changes in 64 commits at 8,042 lines, twelve times
the next file's score; `FarmMissions` (22 changes, 896 lines) next, and the two changed together in 15 of the 64. The
first three passes, in that order:
0. **The cells a body's box overlaps**, one function with one rule for an edge (operator, 2026-11-24, commit 1177): position
   and motion stay doubles, as the server's are; turning one into block cells is written out in `Body.anySolid` twice and
   by hand in the mover, the builder and the pathfinder, and a hair's overlap on one side only held a soldier a day.
1. **Where a soldier stands for a chest**: the search and a depositor's stand are out (`app.ChestStand`, its table
   `ChestStandTest`). Left: with no stand found, the two callers disagree - a depositor's goes to the mine's landing or
   the chest's cell (`SoldierRunner.noStandFor`), the routine's stand a few lines up (`standOf(...)` for a `DEPOSITOR`)
   to its workshop's stand, else stays if in reach, else the chest's own cell, never the landing. Which is right is
   to decide, then one fallback; and `ChestStand` still reads the squad's soldiers through the runner's wrapper.
2. **The keep's work by ground**, out of the runner with its state (backlog/named-work.md, the flatten): the level
   plan, its shares, its foreman and pace - a class a squad leader's process will own.
3. **A mission's measures**, out of `FarmMissions` (its drift check is out: `app.TemplateDrift`, 2026-11-24): what each step counts as left, one function a kind, shared by the
   count that's said and the orders that are given - they've disagreed three times (the flatten's columns, the wheat
   farm's posts, the tree farm's paving).

And with each file passed through, its comments cut down to the rule and its reason (docs/conventions.md, "A comment
is the rule and its reason").

**Toward processes.** The cut lines below are the echelons', and the processes to come are theirs: sixteen soldiers,
four squad leaders, one platoon leader (operator, 2026-11-24). Each step leaves its two sides talking only in
`command`'s value types - `Order`, `Report`, `DutyOrder`, `Objective` - so that moving a side into its own process
is a transport, not a redesign; `ArchitectureTest` gains the rule as each seam is made.

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

## What a smaller model needs that isn't there yet (operator, 2026-11-24)
docs/conventions.md's refactor section gives the measure: a smaller model can make the change. Not met today:
- **No package says what it owns**: there is no `package-info.java` in `bot`, `command` or `app`. One a package, a few
  lines: what it decides, what it never does, its way in.
- **`ArchitectureTest`'s failures don't all say where the rule lives**: each message names its doc section and what to
  do instead.
- **The caps are met by packing** (backlog/one-home.md, B10): the ratchets count declarations and statements, not lines.
- **The first trial**: one of backlog/engagement-rules.md's changes - a row group flipped, its rule added to
  `bot.Contact` - given to a smaller model with only that item for a brief; what it gets wrong, and what it had to read
  to get it right, written here.

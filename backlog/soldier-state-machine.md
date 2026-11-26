---
status: in-progress
priority: P1
depends-on: []
doctrine: docs/architecture.md (functional core, imperative shell); docs/echelons/tenets.md
---

# The soldier's state as one value, its activity a state machine, its changes logged

## Why
The soldier is the echelon that should be least ambiguous: no model, plain Java every tick. Its
state doesn't say so. In `app.SoldierRunner` a soldier is a row across about 130 maps and sets keyed
by its name, written by three clocks - its own tick, the squad loop, the net:
- **States held as flags.** A dozen name sets (`mining`, `drawingStone`, `stoneFilling`,
  `recovering`, `sealToClimb`, the keep's `fetching` and `drawing`) each mean "this soldier is doing
  that", and nothing stops a soldier being in two. One state's data lies over several maps
  (`felling`, `fellingLeft`, `fellingSince`, now held in `app.TreeFelling`).
- **It outlives the soldier.** A reconnect builds a new `DeterministicSoldier`; the maps keep the
  old one's rows.
- **The activity is read off, not decided.** `bot.SoldierActivity` names what a soldier is doing -
  a fight, a recovery, its task, none - from its report and the task the squad's register has it
  on, and each change is a `[State]` line (`bot.ActivityTrack`). The task itself is still worked out
  by the squad loop from which assignment map holds the soldier's name (`SoldierRunner.register`),
  so a change's cause isn't known where it's logged: the line gives the report and the order beside
  it instead.

Where the code goes is backlog/split-oversized-sources.md (`SoldierTick`, `TickPlan`, the routine
families); what a soldier is told is backlog/soldier-order.md (one order, a stack for
interruptions). This item is the state those two leave behind, and the evals it allows.

## Design
- **One state object a soldier** (`app.SoldierState`, begun: its task and its activity), owned by
  its tick and built with its connection, so a reconnect starts it empty: the per-name maps become
  its fields, a family at a time, as `SoldierTick` takes each routine family. What the squad loop
  writes for a soldier is its order (backlog/soldier-order.md) - the task is the first of that, and
  the assignment maps `register` reads it from go as the order takes them. What's shared by all -
  claims on trees, torches, wall and clear cells, what the chests hold - is the garrison's, one
  ledger for the claims with an owner each.
- **The activity is the task, not its step.** Neither the report nor the routine with the hands
  says what a soldier is on: a farmer reports farming, moving and in position by turns between
  cells, and a miner's routine takes its hands for a step and lets go for the walk to the next -
  either read as the activity changed hundreds of times a scenario with nothing amiss. So the
  activity is the fight, the recovery or the task, and the hands and the report are kept beside it.
- **The activity decides.** Today's sealed type is read off the report and the register. Next it's
  what the tick switches on: each state a record carrying that state's data
  (`Felling(tree, left, since)` for the three maps), `bot.Hands` reading which routine is under way
  from it, and a change made in one place with its cause. Inside a state a routine is still an
  expect script whose progress is in the world and the inventory (docs/architecture.md).
- **A pure transition.** `(state, observation, order) -> (state, effects)`: the observation is the
  tick's reading of the world, the inventory and the clock; the effects are what the shell does -
  session calls, claims, log lines. It lives in `bot` and `command`, with `app` the wiring
  (operator, 2026-11-22).
- **The `[State]` line replaces prose lines as their readers move to it** (operator, 2026-11-22):
  `[Register] ... taken off` first, which counts the same churn from the squad's side.

## Done when
- One registry of the soldiers' states is the only collection in `app` keyed by a soldier's name; a
  reconnected soldier starts with empty state, in the squad simulation.
- A change of activity is made in one place and its `[State]` line gives its cause.
- The squad simulations cap churn: changes of activity a soldier-hour, and returns to an activity
  just left, each scenario under a stated budget.
- Property tests over the pure transition, on generated observations, with no `SimServer`: one
  activity at a time; every claim has a live owner; every state is left by its end, a guard or a
  deadline.
- The same way, over small sites once a level is shared out by sector (backlog/authority-and-missions.md step 4), the
  properties the operator wanted checked, not shown by examples (2026-11-14): no builder digs below the site's
  level; a block put down is cleared before the level falls past it; with reports getting through, the site reaches
  its floor; a report lost delays the work and never deadlocks it.
- A live soldier's observations are recorded at the tick's boundary and replayed through the
  transition to the same states: any misbehaviour becomes a case as a stuck soldier does
  (`StuckEvalsTest`).

## Notes
The keep's state is out of the runner as a holder (`app.KeepState`, 55 fields); its works
(`keepwork`, `foremansNet`) follow it. Then the families in backlog/split-oversized-sources.md's
order, each taking its maps into the soldier's state as it goes.

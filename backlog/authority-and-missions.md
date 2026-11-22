---
status: proposed
priority: P1
doctrine: ADP 1-02 and FM 6-0 (mission, operation, phase, task, objective; OPORD/FRAGO; end state, NLT); ADP 3-90 (areas of operations, boundaries); ATP 3-21.8 (the squad leader's sectors)
---

# One authority per echelon: missions with end states, squads in their own ground, soldiers in their own sectors

## Why
The operator (2026-11-22): the platoon leader is the authority for what the force accomplishes; the operator may tweak
it; the squad leader takes high-level orders and decides which soldier does what; the soldier executes, and says CANTCO
with why when it can't. Live, that's not how orders flow: over 20 minutes (field681) the platoon leader decided Alpha's
order 6 times, but standing rules rewrote orders 25 times after it decided (the night rule 17, the farm guardrail 6,
fighting each other), and the runner's scattered per-soldier rules re-tasked Alpha's four soldiers 62 times - Bob
taken off the flatten after 24 s, Eli's order flipping 134 times in a run. Two squads worked one chunk (Alpha's flatten
and Delta's stale farm at chunk (1, 3); all four squads down the camp's one mine by night; the camp's tree chunk sited
on the farm mission's chunk), and nothing records how a soldier's work ties to the mission it serves.

## The terms
One home: docs/echelons/platoon-leader/decisions.md, "Terms". A mission is what the operator gives the platoon; its
operation runs in phases; each phase - and each assignment the platoon leader makes by its own decision - is a task to
a squad; an objective is a place. The code's `Objective` type and `objective` table hold a squad's task: renamed here.

## The plan, in order - each sim-first, its own commit
1. **Schema and keys** (operator: UUIDs; "don't worry about the migration"). Tables: `mission` (UUID, given by, given
   at, intent, end state, NLT, outcome, why), `task` (the objective table renamed: UUID, mission FK, phase, the task,
   objective chunk, squad, state, given at, why, reported at/by), `order` (OPORD/FRAGO: UUID, issuer, recipient, task
   or mission FK, parent order), `verification`, `fault`, `estimate` (kind of work, estimated, actual). States:
   PLANNED, ACTIVE, ON_HOLD, REPORTED, VERIFYING, ACCOMPLISHED (done and verified), FAILED (time - with what was and
   wasn't done), CANCELLED (the issuer rescinded), SUPERSEDED (a newer order replaced it), CANTCO - each with its
   reason. The short form (first 8 hex) in logs and chat. Java types renamed with the table.
2. **Reports against task keys**: a soldier says "WORKING 3f9a2c1b (mission 7d01e4aa, phase 1): 33 left"; its squad
   leader tracks against the key.
3. **Areas of operations**: every squad's task comes with an AO of whole chunks; concurrent worksites (a chunk and its
   slope ring) never touch; a big dig runs in waves - chunk coordinates stepped by 2, at most four waves, a later
   chunk's slope flags off toward finished neighbours; chunks chosen by work (blocks to cut and fill) plus walking
   distance to the depot, the farms and the mine head. A mine site per squad by night. Traversal is free anywhere;
   mutation (placing, breaking, a path cut) only in the squad's own AO or unheld ground, else CANTCO. Read-only work
   (recon, verification) goes anywhere. Service points (depot chests, tables, furnaces) are free to all. Night
   shelter may be shared; building it is owned work. A squad waits for the next wave if it starts within about a
   minute (one named constant), else it gets other work.
4. **Sectors**: the squad leader splits its AO into 3D boxes per soldier - xz rectangles on a y band (two on y=32,
   two on y=-28), one or more each, fresh at each level - one theme per soldier, held till done or a stated cause.
   A path cut is one soldier's (the breacher); the rest follow. This replaces the runner's scattered per-soldier
   assignment rules (mine crew, keep crew, lamplighter, camp upkeep, forester, errands).
5. **One platoon-leader decision that changes only on events** - a task reported done and verified, a CANTCO, dusk
   and dawn, the operator's tweak - with the night rule and the guardrails folded into how the order is made, once,
   not rewriting it each cycle. By night daytime missions go ON_HOLD (their clock runs; nobody works them) and the
   squads go to their mine sites; by day they're ACTIVE again. The operator can hold or resume a mission from the chat.
6. **Trust but verify**: a squad's DONE is REPORTED; the platoon leader tasks another squad with an area
   reconnaissance of the objective - on site, so its own connection streams the chunks fresh, and inspecting by the
   task's standard with a different check from the reporter's; confirmed, ACCOMPLISHED; else a `[Fault]` naming
   both measures and the cells that differ. Phases of the operator's missions and tasks that gate others are verified;
   recurring work spot-checked; the next phase waits.
7. **Missions' times** (operator: the operator always takes priority; missions end on an exit criterion): each mission,
   phase and task carries an expected duration - computed from the measured pace of its kind of work, the squads on
   it and the walking, known holds counted, calibrated by past actual/estimate - and a maximum. The deadline extends
   only while progress holds (the stall check on active time), never past the operator's ceiling - by default one day
   (built as a real day; a game day is 20 minutes - the operator hasn't said which). At an overrun, an `[Overrun]`
   line with the computed breakdown (working / on hold / idle, CANTCOs and reasons, contacts, reassignments, pace
   against the estimate's), and a deep dive: an exceptional event becomes a sim cell and a fix; a wrong expectation
   corrects the estimate.

## Left from the flatten work (2026-11-22)
- **Water in the dig**, operator: fill the water block with dirt, then rip it down. Half built, out of main: the work
  in progress is `C:/mc-worktrees/wip/flatten-tops-water-wip.patch` (with `FlattenTopsSimulationTest.java.wip`, its
  `aWaterSourceInTheDigIsFilledThenDug` cell). Found so far: the clear breaks nothing beside water
  (`Clearing.letsInWater`), the fill counts water as floor (`Clearing.toFill`), the keep gives out fill orders only
  once nothing is left to dig, and a fill order's cells come from `Clearing.toFill` (SoldierRunner, `filling.put`).
  With water scanned (`Clearing.waterIn` in `SiteClears`), counted in the level, the share's water given out to fill
  while digging and put first in a fill order's cells, the water is seen ("water in the dig: 1 cell(s) 40,65,8") and
  still nobody fills it - the next thing to trace is the soldier's filler taking that order. Don't ship the level held
  at the water without the fill: it stalls the flatten for good.
- **The dig/place loop** on a raised chunk's last layer: builders dig a cell and put it back (FarmMissionSimulationTest's
  stale-farm world, cells (37,64,5), (43,64,5)) - its own case, then the fix.
- **A flatten crew's deposit to a mine's stash** (Bob, field681: the camp mine's chest at y=8, 59 below; his pack then
  threw out seeds, 67 throw-aways in Alpha): a surface crew's deposit skips a chest that's a mine's - eval: a squad that
  mined by night flattening by day with full packs, its loads in the depot, nothing thrown away.
- **Gus's stands in the keep's dig with no way in** (field681; the retry back-off, commit 1122, took the CPU but not the
  stall).
- **The camp's tree chunk sited on a mission's chunk** (chunk (1, 3)): CampTrees' siting skips chunks a mission holds -
  part of step 3.
- **The chat**: a command's computed confirmation shown word for word on the net; cancel for missions of any kind; no
  reprioritise command - the platoon leader coordinates.

## Done when
- Each step's eval passes; `OperatorChatScenarioIT` and the live chat probe stay at full marks.
- Live: no two squads' worksites touch; no soldier re-tasked without a cause; every mission closes ACCOMPLISHED,
  FAILED, CANCELLED or SUPERSEDED with its reason; every `[Overrun]` and `[Fault]` has its deep dive written up.

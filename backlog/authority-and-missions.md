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

**Who may change an order** (operator, 2026-11-22): its issuer. A squad leader or a soldier doesn't rewrite the order it
was given, or make one up, except in specific circumstances: to defend against hostiles - then back to the same order
(backlog/soldier-order.md, the stack for interruptions) - and when it can't be carried out, said up as CANTCO with why,
for the issuer to answer. Anything else that changes what a soldier or squad is on is a rule to remove, not to keep.

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
   With the renames, the keep's task and log tag become the flatten's (`Objective.Task.Keep`, `[Keep]`): what it does is
   flatten ground (backlog/flatten-campaign.md).
   **What the operator has ordered is the platoon leader's to keep, not the command line's** (operator, 2026-11-22):
   today the mission, the camp, the ground to flatten and the standing rules are start arguments
   (`minecraft.platoon-leader.mission`, `minecraft.camp`, `minecraft.flatten`, `shelter-at-night`, `keep-by-day`,
   `minecraft.farm-squad`, `minecraft.supply.point`), and a mission given in the chat is held in memory
   (`ChainOfCommand.onOperatorMission`): a restart goes back to the arguments. Each is an order of the operator's -
   given in the chat, a row with who gave it, when and its words, in force until the operator replaces or rescinds
   it, read back at start. The command line keeps only what the deployment is: the operator's name, the roster, the
   models and prompts, the ports, where state is kept. Done when a garrison restarted with none of those arguments
   carries on with the mission, camp and rules it had, and the chat says what's in force and who ordered it.
   The platoon leader is the single-threaded leader (operator, 2026-11-22): priority and locations are its to decide
   and to keep - where the camp is, where a farm or a supply dump goes, what comes first - from the operator's
   mission and its end state. A location or a priority fixed on the command line is split brain, and static: it
   can't change as the ground and the supply do. So `minecraft.camp` and `minecraft.flatten` don't become the
   operator's orders kept as rows; they become the platoon leader's decisions, kept with why, which the operator
   may override in the chat.
   Requests are tasks going up: `app.RequestLedger` folds into the task register - a squad's request (a task finished
   or stalled, Class I RED, Class IV) is a row with its issuer, recipient, given-at and state like any other, overdue
   by the same rule, so there is one register and one review of it, not two.
2. **Reports against task keys**: a soldier says "WORKING 3f9a2c1b (mission 7d01e4aa, phase 1): 33 left"; its squad
   leader tracks against the key.
   Eval: a report names the task it's about, so a CANTCO from a side job - lighting, making a tool - never reads as
   its task's, and the register (`TaskRegister`) leaves the soldier on it: in the squad simulation, a builder whose
   torch-lighting fails every try stays on its dig and its count falls (live, field165: Hal put on `dig the keep` and
   taken off a second later for "can't light the ground: 4 tries in a row failed"). Today the report chain gates each
   routine's CANTCO case by case (`SoldierRunner`, the lamplighter's, the mason's, the depositor's).
   The same churn from the soldier's side is its `[State]` line (`bot.ActivityTrack`): in the squad simulation a farm
   squad in a fight goes keep the farm -> fighting -> no task -> keep the farm 124 times - the register drops the task
   for the fight and gives it again. A fight is an interruption, not a change of task: the count to cap.
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
   The platoon leader sees its open tasks when it decides: each squad's open task one line in its decision prompt - the
   short key, the task, the squad, state, age and last report - and the same lines in the dawn `[AAR]`; today the
   register (`app.ObjectiveBook`) is read only by the operator's chat and the `[Objectives]` log line.
   The decision prompt shows each squad's summary, not each soldier's report: live (field35x-369), four times in a
   morning the platoon leader answered one objective a soldier - Alpha four times over, each from one soldier's line -
   or left a squad out, and the decision was dropped ("[PL] no decision - keeping the current objectives: two
   objectives for squad Alpha"). Eval: a `PlatoonLeaderScenarioIT` situation with four soldiers reporting differently
   in one squad gets one task for that squad, 5 of 5.
6. **Trust but verify**: a squad's DONE is REPORTED; the platoon leader tasks another squad with an area
   reconnaissance of the objective - on site, so its own connection streams the chunks fresh, and inspecting by the
   task's standard with a different check from the reporter's; confirmed, ACCOMPLISHED; else a `[Fault]` naming
   both measures and the cells that differ. Phases of the operator's missions and tasks that gate others are verified;
   recurring work spot-checked; the next phase waits.
7. **Missions' times** (operator: the operator always takes priority; missions end on an exit criterion): each mission,
   phase and task carries an expected duration - computed from the measured pace of its kind of work, the squads on
   it and the walking, known holds counted, calibrated by past actual/estimate - and a maximum. The deadline extends
   only while progress holds (the stall check on active time), never past the operator's ceiling - by default one real
   day (24 hours), which the platoon leader (from its estimate, said in its WILCO) or the operator (in the chat, when
   giving the mission or after) may override; the operator's limit stands over the platoon leader's. At an overrun, an `[Overrun]`
   line with the computed breakdown (working / on hold / idle, CANTCOs and reasons, contacts, reassignments, pace
   against the estimate's), and a deep dive: an exceptional event becomes a sim cell and a fix; a wrong expectation
   corrects the estimate.

## Left from the flatten work (2026-11-22)
- **Water in the dig beyond one source.** A source in the dig is filled, then dug (docs/military-doctrine.md § 17,
  `FlattenTopsSimulationTest`). Not yet a case: a pond's worth, where the fill of one cell is flowed into from the
  next; water the flatten's own cut lets in from beside the site; and the old farm's holes at chunk (1, 3) live.
- **The dig/place loop** on a raised chunk's last layer: builders dig a cell and put it back (FarmMissionSimulationTest's
  stale-farm world, cells (37,64,5), (43,64,5)) - its own case, then the fix.
- **A flatten crew's deposit to a mine's stash** (Bob, field681: the camp mine's chest at y=8, 59 below; his pack then
  threw out seeds, 67 throw-aways in Alpha): a surface crew's deposit skips a chest that's a mine's - eval: a squad that
  mined by night flattening by day with full packs, its loads in the depot, nothing thrown away.
- **Weapons cross-levelled into a full pack** (Ivy, field685): Jack and Kate cross-levelled a weapon to her 14 times
  in two minutes while she held a stone sword and spares, her pack full and throwing seeds and granite away to make
  room - the cross-level rule counts her short as fast as her pack sheds them. Eval: an armed soldier with a full pack
  is cross-levelled nothing, and nothing handed her is thrown away.
- **A soldier taken off a stalled task is left with nothing** (Ivy, field685: "reports Working ... but 414 left hasn't
  fallen in 120 s", then idle with two alerts) - the squad leader's to re-split, step 4.
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

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

**A soldier does nothing without a command** (operator, 2026-11-22): it stands, defends itself and eats - no work of its
own. The platoon leader is to be giving commands shortly, so a soldier idle is the platoon leader's fault, and counted
against it: the measure of the platoon leader is soldier-time without a task (`[Idle]`, the chain of command's share;
the status page's "no task"). The errands a soldier still makes up for itself (`app.SoldierErrands`) and the runner's
per-soldier rules go as backlog/soldier-order.md and step 4 take them. Begun: on a mission's task a soldier hauls
nothing from a mine's stash or an old camp's chest and draws logs only from a chest in its reach
(docs/echelons/soldier/capabilities.md, "What it can't do"); its deposits, its stone and metal draws and its making of
its own kit are left, and nothing yet says its shortage up to its squad leader in their place. Live (field692): Eli
lost under the old camp's hill a whole run on "dig the camp's ground", 3,758 changes of activity - the camp upkeep's
rule, a recovery nobody ordered.

**There is always a mission** (operator, 2026-11-22). The standing one, in force whenever the operator has given no
other: go back to the ground round x=0, z=0 at sea level and fortify the permanent structure there; if there is none
yet, build one; if the force isn't yet strong and supplied enough to build one, build renewable farms and mine by
night until it is. Later: troops sent out to remote permanent structures - to build them first, then to mine from
them and look for other villages. The platoon leader works out what to do under it - farming, whatever it takes - and
gives the commands: "no mission" is never a reason for a squad to stand idle. In the register it's the first row,
given by the operator's standing order, superseded when the operator gives another and in force again when that one
ends.

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
   **The templated farms are the platoon leader's to order** (operator, 2026-11-22: why hasn't it ordered tree farms
   using the template?). Live (field690) it couldn't: a wheat chunk is a mission only the operator gives, in the chat
   (`giveFarmMission`); the tree chunk is sited by a standing rule (`app.CampTrees`: chunk (5, 5), 64 blocks from the
   camp, its stage still GROUND) and built by nobody's order; the only tree task it can give is the old grove of
   saplings round a point (`TREE_FARM`), and its prompt doesn't say a template exists. And nothing asked for wood:
   994 logs stood against a reorder point of 64. Built since: a tree chunk is a mission as a wheat chunk is, given by
   the operator in the chat (`giveTreeFarmMission`), worked alongside a wheat farm's. Left: the platoon leader gives
   either itself under the mission in force, choosing the chunk, when the supply it's shown says the reserves want it
   (backlog/renewables.md, backlog/sites.md's levels) - its decision reply names the mission to start, as the chat's
   command does; and a supply dump the same way, whose template isn't built (backlog/renewables.md, the supply point).
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

## What a soldier still starts itself (operator, 2026-11-22: get rid of the soldiers' free will)
The freedom left to a soldier: fight or flee, eat what it carries, take the tool for the block in front of it, get itself
out of a hole or out of water, and say what it can't do or is short of. Built: the kit ladder and the surplus trip are
the squad leader's leave (`SquadOrder.rearm`, a squad holding); a trip that moved nothing isn't made again. Each of the
rest becomes a report up and a task down - the soldier short of a thing says so, its squad leader gives the draw, the
haul or the making to somebody as a task - or goes:
- **`DEPOSITOR`'s own trips** (`SoldierRunner`, the depositor's turn): ore to pool, a full pack (stock trips are
  the squad leader's leave's or a duty's now, `SoldierErrands.stocks`); and its draws - food, seeds, dirt, logs, cobblestone, metal, string
  for a bed, a mine's stash hauled up. A full pack is "can't go on" and stays; the rest are logistics, the squad
  leader's or the platoon sergeant's to order.
- **`ARMOURER`'s walks**: to quarry stone, to dig ore, to a workshop 40 blocks off for one sword. With leave it still
  chooses where; it should be told.
- **`SUPPLY_RUN`**, **`HUNTER`**, **`TORCHMAKER`**, **`SCAVENGER`**, **`GATHERER`** started for its own tools.
- **The rules that give work no one ordered**: by night every squad's objective becomes the camp's mine
  (`app.NightRules`), and "fill the camp's ground", "dig the keep" and the harvester's and baker's duties are handed
  out by rule. They become the platoon leader's standing orders, kept with its missions (the plan below, step 1).
- **Rearm as an order of its own**: today it rides on a squad holding. The platoon leader should be able to order a
  refit - buckets, armour - for a squad, by name.
- An idle soldier is then the signal: its echelon above gave it nothing.

## The platoon leader keeps the stores (operator, 2026-11-23)
"Really the platoon leader should be helping the soldiers by looking at the inventory in the stockpiles and telling
the soldiers what to throw out and what to keep." Live (field698): 33,479 cobblestone in the chests, 5,380 seeds, 841
logs, 490 copper ingots, 215 iron ingots - and every chest by the camp full, soldiers failing to put loads in and
going round each other. Built: the counts (`GarrisonStatus.stores`, the chat's `stores` tool), and most-and-least
levels the depositor reads (`StockLevels`, `ProductionLevels`). To build:
- **Cobblestone and dirt capped at 4,096 each - next to build** (operator, 2026-11-23: way too much cobblestone; a max of
  4,096 for both; miners keep a bit extra on them for backfilling veins, the stockpiles don't need so much). A most
  exists already - `bot.supply.StockLevels.DEPOT`, 256 of each - and didn't hold: the chests have 34,696 cobblestone.
  First, why: which deposits pass it by (`SoldierErrands.surplusKinds` covers a flatten builder's load at a dump; a
  miner's stockpile trip and a chest counted alone are the suspects), reproduced in a test. Then the most at 4,096,
  counted over every chest known, on every path that puts either in a chest; what's over it thrown away where it's dug;
  and what's held past it taken out (the cleanout below).
- **A stock policy the platoon leader gives**, by kind: keep up to so many, throw out past it - cobblestone and dirt
  past a few stacks a chest row, seeds past 1,024 (operator), and so on - computed from what the stores hold against
  what the missions ordered will use, said as an order and kept with its missions.
- **Chests cleaned out to it**: what's past the policy taken out and thrown at a dump, as an ordered task - "clean out
  the chests" - not each soldier's guess at its own pack.
- **The soldier's ladder when a chest is full** (docs/conventions.md, "A retry changes something"): another chest; the
  next chest of the row put up; ask; and for what the policy says is past its most, throw it out where it stands.
- **A farmer's seeds**: its surplus past a stack or two into the chest up to the policy's most, and drawn back when it
  has none (the draw is built; the squad on a template step never got to sow - see below).

## Left from the flatten work (2026-11-22)
- **A flatten's crew, what's left** (field696, the tree farm at chunk (2, 3): nine Minecraft days, its flatten not
  done). Built: a mission's step worked by night, and its squad's detachments called in. Left: a mission's squad made up
  to strength from squads with no mission; a soldier with no route to its squad's work (Hal, on the old hill's top, 80
  blocks off) said up and fetched; a soldier taken off a stalled task given other work at once; a site's cost in
  blocks - this one at a hill's foot, its slopes cut 10 levels up and 11 out - said before it's ordered; and what a
  squad working the surface by night needs: light over the work, a watch.
- **A digger's pack, the rest of it** (operator, 2026-11-22): with room in its pack it digs on, and its trip takes what's
  no part of digging (built). Left: dirt thrown out at the site when the force has enough - today it's dumped only at a
  depot's dump, and only once every chest is full or the stores are at their most; a chest put up by the dig when the
  nearest is far (a mission's depot is its chunk's west or north edge, never nearer); and the squad's point not sent
  into a pit of the ground it's flattening (field696: Bravo's point at y=59 in the tree farm's chunk, Fran "no route").
- **Depot chests standing in a template's ground**: outposts sited there before the template was (the chest at
  (17, 64, 56) in the wheat farm) aren't taken up; no new one is sited on a template's ground (`FarmMissions.templateAt`).
  The review counts what's missing, not what's extra.
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

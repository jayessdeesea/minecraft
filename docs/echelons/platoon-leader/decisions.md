# Platoon leader: what it decides

One instance, the slowest-thinking echelon. A model call (`command.ai.SpringAiPlatoonLeader`, any Spring
AI `ChatModel`) gives each squad one objective. The operator's standing rules then apply on top,
loudly ([operator-rules.md](operator-rules.md)).

## What it's given
In its user prompt (`command-ai/src/main/resources/prompts/platoon-leader-user-prompt.md`):
- **The mission**, and anything the operator said since.
- **The time of day.**
- **The supply point and stockpile**, when there is one.
- **The foothold:** the camp's standards phase by phase, each met or not and how far along, and the
  earliest unmet phase named (`command.Foothold`;
  [military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)).
- **Each squad's status:** its report, soldiers, resources seen near it, its current objective,
  whether it holds a patrol base and how many blocks that base has left. A squad every soldier of
  which has a standing role of its own, none of them its task's, reports CANTCO "no soldier free
  for ..." with each soldier's role (`SquadCommand.report`): nobody goes with it on the task. A
  patrol base and a plain move are exempt - everyone comes in for the night. No soldier is held
  back from a role for the task (the operator's): one at work turns to fight as soon as it sees a
  hostile, so moving the task to a squad with soldiers free is the platoon leader's call.
- **Each squad's Class I** (`command.Subsistence`): GREEN, AMBER, or RED with the request for
  food the squad can't get itself, which the platoon leader answers with a farm or game
  ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward)).
- **The day's work** (`command.DayWork`, `<day_work>`): what the force is short of, each with the task that
  answers it, the stockpile as last seen, and the ground to flatten with its level and blocks left - from
  which it assigns who farms, works a grove, cuts, mines and flattens (operator, 2026-11-17).
- **A squad's Class IV request** (`PlatoonSergeant.classFour`): the keep's builders short of logs, between
  them and the stockpile, for the depot's chest, pickaxes and torches - answered with a `WOOD` task
  at trees a squad has reported, after food (same section).
- **Each task's progress** (`command.Progress`), the squad's answer to the task, measured rather
  than claimed:
  - **DONE**, where the world shows it: the wall closed, every cell lit, the stairs cut.
  - **STALLED** once no soldier has worked at it for a minute, with the soldiers' CANTCO reasons
    counted and how many stand with nothing to do.
  - **Under way** otherwise, with what's left where that's measured.

  The prompt tells it a DONE or STALLED task is a reason to change the order. A patrol base is the
  exception: a sealed one is kept through the night. Each time it keeps such an order anyway, the
  log says so (`[Feedback]`).

It keeps nothing between calls but each squad's current objective (backlog/operations-map.md).

## Its registers
The platoon leader's books are kept by code, never asked of the model (operator, 2026-11-21): an embedded H2 database
(`app.Registers`, docs/stack.md) of two tables.
- **The chunk register**, keyed by the chunk's x and z: what the force has made of it (flattened, floored, wheat,
  trees, the supply point), the level its ground is made to, and its stage as last measured - a wheat chunk's
  `FarmSite` stage, a tree chunk's `TreeChunk` stage. Kept as the world shows it each foothold check, a row written
  when it changes and said (`app.ChunkLedger`): `[Chunks] chunk (1, 3): WHEAT at y=63, SURVEYED`. A farm mission's
  chunk is written FLATTENED (stage `CUT`) and FLOORED (`PAVED`) at the level below the farm's as each of those
  steps is measured done, then WHEAT once the camp's farm holds its template (below).
- **The objective register**: each objective a mission's step on a chunk - its task, the squad on it, how it stands
  (planned, under way, done, CANTCO, relieved), when and why. Written when the platoon leader gives a squad an
  objective - its open one, if another, relieved - or holds one no row is open for (the first decision after a
  restart); and each squad's SITREP pushed into its row as it comes, every second (operator, 2026-11-21: the squad
  leaders push updates to the platoon leader): its squad leader's CANTCO with why, its task's progress as said, done
  with what was measured (`app.ObjectiveBook`; `[Objectives] Charlie: Flatten ... CANTCO - no route to ...`). A farm
  mission's steps are written PLANNED, no squad on them, when it's given; a squad given a step takes up that step's
  row - under way, its squad's - rather than a new one, and taken off it (relieved) the step stays open, taken up
  again when it's given again; it's DONE only when the mission measures it done.

The garrison's file is `minecraft.registers` (`C:/mc-state/registers`), outside a cloud-sync folder since it's held
open; a simulation's is its state directory's own. The operator's chat is shown them (below); the platoon leader's
decision prompt isn't yet.

## What it decides: `command.Objective`, one per squad
- **Where:** a location. Its height is the platoon leader's guess, and is put on the ground there
  (`NightRules.grounded`), except for `MINE`, where it's the depth to reach.
- **What, in words:** the mission and the intent, logged and passed to the squad leader.
- **The task**, the only part that reaches the soldiers' hands:

| Task | The squad's work |
|---|---|
| `NONE` | Move there and hold. |
| `WOOD`, `STONE` | Gather it near the objective. |
| `PATROL_BASE` | Build a sealed, lit hut round the squad and stay in it. |
| `FARM` | Till, plant and harvest round the water there. |
| `FORAGE` | Each soldier but the farms' gets its rations off the land: the ripe wheat within 100 blocks of the camp, harvested and replanted ([§ 4](../../military-doctrine.md#4-reports-upward)). |
| `TREE_FARM` | Plant saplings there. |
| `SECURE` | Light the ground within 12 blocks. |
| `FORTIFY` | Wall in the ground 8 blocks out each way. |
| `MINE` | Cut a spiral staircase down to the given depth. |
| `PATROL` | Walk the round inside the wall line, on watch ([military-doctrine.md § 15](../../military-doctrine.md#15-securing-the-camp)). |
| `KEEP` | Flatten the ground set to flatten, a level at a time to its floor, wherever the objective points ([military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)). |
| `FLOOR` | Pave a layer in cobblestone, cell by cell: a farm mission's, never the model's to give (below). |

- **The work area**, not the platoon leader's to decide: for `FARM`, `TREE_FARM` and `WOOD` the chain of
  command attaches the ground and the dump to the order it gives (`command.WorkArea`, `WorkArea.attach`).
  The ground is whole chunks on the server's grid: for a farm or a grove the chunk its point is in, for a
  wood every chunk the woodcutters' reach (24 each way) touches. The dump is the camp's supply point (`Foothold.dump`): the configured one, else the camp
  workshop's chest, else the depot's. The OPORD line and the
  standing order in the platoon leader's prompt both say it ("in 1 box over x 0..15, z 0..15, the
  dump at (3, 64, 2)").

## The operator's farm mission
"A farm at chunk (x, z)" - at any height, sea level (63) by default (operator, 2026-11-21) - is the operator's
(`!farm`, DEVELOPMENT.md; or the chat's FARM, below) and the platoon leader carries it out by doctrine, computed,
never the model's (`command.FarmMission`, `app.FarmMissions`): three steps, each an objective, each started when the
one before is measured done. The level is the operator's alone: the chat's FARM keeps a level only if the operator's own words say it, else
sea level (`OperatorChat.levelAsSaid`; live, field668, told "at sea level", the model gave y=67). And the latest order
for a chunk is the one worked: a farm given there at another level relieves the open one, its steps RELIEVED with why.
1. **Flatten** the chunk to a block below the farm's level: the `KEEP` task, the ground to flatten the mission's
   chunk while the step's under way (`ChainOfCommand.flatten`), and the dig the columns of `FlattenPlan.reach` - the
   chunk, and a slope a block a block out on each side whose `command.Slopes` flag is on, off beside a chunk the
   force has made a template of (`Slopes.around`), never into a template's ground, its shared fence line included.
   Done when nothing stands over the level in the chunk or its slopes (`FlattenPlan.cut`, over the world's tops).
2. **Floor** that layer in cobblestone, every cell, natural stone included (`FLOOR`, [military-doctrine.md
   § 17](../../military-doctrine.md#17-the-lodgment)). Done when every cell of it is cobblestone.
3. **The template**: the chunk farm on the floor at the farm's level, taken into the camp's farm (`CampFarm.take`) and
   worked by a squad on `FARM` at the chunk. Done when its ground is laid - its `FarmSite` stage past SURVEYED.

The step under way has one squad on it, by a guardrail after the platoon leader's decision and the day's own
(`FarmMissions.guarded`): the squad already on it, else one given its task already (the flatten's squad, say), else
the last in the order of battle but the farm squad - said, `[Guardrail] the operator's farm - Alpha put on ...`. Each
foothold check measures it (`[Mission] ... - up to 48 of 256 cells to pave`); done, its row is DONE with what was
measured, its chunk's row written, the next step started and the platoon leader woken. One mission is worked at a
time, in the order given; one open in the register is carried on after a restart, at its first step not done.
Code-sited chunk farms are gone: the camp's farm takes in only the chunk farms its missions give it, and keeps those it
holds.

## When it decides
When woken, never on a clock (backlog/fleet-command.md): at once on an operator's mission or
remark, dawn, dusk and dusk near, a squad with no objective, and an escort attached or ended; and,
if a minute has passed since its last decision, on a squad's task finished or stalled, its Class I
RED, or its squad leader's CANTCO. Contact wakes nobody: it's the squad's to handle, and live, a new
contact every few seconds at night asked the platoon leader every ten. Each wake is a log line with
its cause, `[PL] wakes: dusk near`; a day with nothing asked has only dawn's. At dusk and dawn it's shown
no standing order to keep: the last phase's work lapses, and every squad is given the next
(`ChainOfCommand.forThePlatoonLeader`; live, at each dawn it kept the night's mining for every squad). Its
prompt ends counting and naming the reply asked for - one objective for each squad, in the order listed, the
foothold's standards shared out among them (`SpringAiPlatoonLeader.roster`; live, it gave every squad an
objective for each unmet standard, twelve in all, and the reply was refused). A failed call (a malformed
reply, the model down) is logged loudly, and the squads keep their last objectives - but a squad named twice
or left out is asked about again, alone, through the same call (operator, 2026-11-20: every squad its objective, a
defect if not), a squad not listed dropped; one still without one fails alone, keeping its own - or with none, holding where it is till the next decision - while the
others' objectives stand (`SpringAiPlatoonLeader.toObjectives`) - put through the same
rules as a decision's (the night rule, the guardrails), so a reply refused at dusk still sends them down the
mines.

## Talking with the operator
The operator can also talk with it in a conversation (`app.OperatorChat`, opened as DEVELOPMENT.md § 3 says). Each
turn is a model call through the same `ChatClient` (`command.ai.PlatoonLeaderChat`), timed in the models' queue
(`[Model] Operator chat`) on the chat server's own threads, never a soldier's or the platoon leader's decision thread.
- **What it's shown:** its own fixed chat system prompt
  (`command-ai/src/main/resources/prompts/platoon-leader-chat-system-prompt.md`), the earlier turns as the
  conversation, and in the turn's own prompt (`platoon-leader-chat-prompt.md`): `<now>` - the Minecraft time, day or
  night, and the ticks and minutes to the next dusk and dawn, worked out from the overworld clock
  (`command.TimeOfDay`), and the ground to flatten; `<registers>`, its books as they stand now - each squad's open
  objective with how it stands, for how long and why, the farm missions' steps still planned, and every chunk kept
  (`ObjectiveBook.describe`), so "what's
  under way" is answered whenever it last decided (live, field661, asked after a restart it said nothing was in
  flight) - and `<last_decision>`, the user prompt of its last decision
  word for word (`SpringAiPlatoonLeader.lastShown`): the mission, each squad's standing order, task and progress,
  the day's work and the camp's phases, as they stood then. Before its first decision it's told it has been shown
  nothing about the squads.
- **What it may do** (`command.ai.ChatReply`): answer in words, and take at most one action from a closed set -
  `MISSION`, `ESCORT`, `FLATTEN` or `FARM`, each given through the operator's own order (`ChainOfCommand.onOperatorMission`,
  `onOperatorEscort`, `onOperatorFlatten`, `onOperatorFarm`: the same WILCO and order trace as from game chat). `FARM`'s
  argument is a point and a level, "x z [y]", the farm mission on the chunk the point is in, its steps added to the
  reply as computed (above).
- **Every turn** is logged and on the net, `[Operator -> PL]` and `[PL -> Operator]`, with `[Chat] the platoon
  leader is shown` logging the turn's prompt. An action it can't read (a flatten that isn't `x1 z1 x2 z2 y`) fails
  that turn, loud: nothing is said or given, and the page shows why.

# Platoon leader: what it decides

One instance, the slowest-thinking echelon. A model call (`command.ai.SpringAiGeneral`, any Spring
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
| `TREE_FARM` | Plant saplings there. |
| `SECURE` | Light the ground within 12 blocks. |
| `FORTIFY` | Wall in the ground 8 blocks out each way. |
| `MINE` | Cut a spiral staircase down to the given depth. |
| `PATROL` | Walk the round inside the wall line, on watch ([military-doctrine.md § 15](../../military-doctrine.md#15-securing-the-camp)). |
| `KEEP` | Flatten the ground set to flatten, a level at a time to its floor, wherever the objective points ([military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)). |

- **The work area**, not the platoon leader's to decide: for `FARM`, `TREE_FARM` and `WOOD` the chain of
  command attaches the ground and the dump to the order it gives (`command.WorkArea`, `WorkArea.attach`).
  The ground is whole chunks on the server's grid: for a farm or a grove the chunk its point is in, for a
  wood every chunk the woodcutters' reach (24 each way) touches. The dump is the camp's supply point (`Foothold.dump`): the configured one, else the camp
  workshop's chest, else the depot's. The OPORD line and the
  standing order in the platoon leader's prompt both say it ("in 1 box over x 0..15, z 0..15, the
  dump at (3, 64, 2)").

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
reply, the model down) is logged loudly, and the squads keep their last objectives - put through the same
rules as a decision's (the night rule, the guardrails), so a reply refused at dusk still sends them down the
mines.

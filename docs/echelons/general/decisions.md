# General: what it decides

One instance, the slowest-thinking echelon. A model call (`command.ai.SpringAiGeneral`, any Spring
AI `ChatModel`) gives each squad one objective. The operator's standing rules then apply on top,
loudly ([operator-rules.md](operator-rules.md)).

## What it's given
In its user prompt (`command-ai/src/main/resources/prompts/general-user-prompt.md`):
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
  patrol base and a plain move are exempt - everyone comes in for the night.
- **Each squad's Class I** (`command.Subsistence`): GREEN, AMBER, or RED with the request for
  food the squad can't get itself, which the general answers with a farm or game
  ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward)).
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
- **Where:** a location. Its height is the general's guess, and is put on the ground there
  (`ChainOfCommand.grounded`), except for `MINE`, where it's the depth to reach.
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

## When it decides
Every 2 minutes, at once on a new mission from the operator or a squad with no objective, and on a
squad's new CANTCO or contact if a minute has passed since the last - contact is the squad's to
handle, and live, a new contact every few seconds at night asked the general every ten. A failed call (a malformed
reply, the model down) is logged loudly, and the squads keep their last objectives.

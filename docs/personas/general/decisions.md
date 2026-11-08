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
  whether it holds a patrol base and how many blocks that base has left.

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

## When it decides
Every 2 minutes, at once on a new mission from the operator or a squad with no objective, and on a
squad's new CANTCO or contact if 8 seconds have passed since the last. A failed call (a malformed
reply, the model down) is logged loudly, and the squads keep their last objectives.

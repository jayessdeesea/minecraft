# Squad leader: what it decides

One per squad. A decision-making role, not a player: it has no connection and no body. A model
call (`command.ai.SpringAiSquadLeader`, any Spring AI `ChatModel`) decides; deterministic drills
then turn the decision into each soldier's order ([drills.md](drills.md)).

## What it's given
- **The objective** from the general: a place, the mission and intent in words, and the `Task`.
- **Each soldier's status:** position, order, report, health, food and kit.
- **The terrain round the objective, surveyed:** which ground is reachable from where the squad
  stands (`bot.nav.TerrainSurveyor`), so it never has to read a grid.
- **The time of day.**

## What it decides: `command.SquadOrder`
- **Where:** the objective point the squad moves on.
- **How:** formation (`WEDGE`, `FILE`, `LINE`) and interval (`TIGHT`, `NORMAL`, `EXTENDED`;
  [military-doctrine.md § 2](../../military-doctrine.md#2-formations-and-intervals)).
- **Vertical tolerance:** how far from the point's height the squad may end up.
- **Risk:** the squad's, and per soldier (`soldierRisk`)
  ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
- **Rearm:** whether soldiers may stop to make kit past their basic load, a stone sword, which they keep on their own.
- **Intent**, in words, logged with the order.
- **Task organisation:** changes to its soldiers' standing plans, usually none - `roleChanges`, a
  soldier's role and site; `detached`, a soldier sent off on a job of his own: `GO_TO`, `FETCH`, `LEVEL`, or
  `CANCEL` ([military-doctrine.md § 18](../../military-doctrine.md#18-task-organisation)).

The work is its soldiers' roles': a rifleman's is the general's `Task`, any other role's that
role's task at its site (docs/echelons/soldier/routines.md). It sees each soldier's plan at the end
of his line.

## When it decides
Every 20 seconds while the squad has an objective, at once on a new objective, and at once on a
contact report. Never with no objective. Soldiers keep their last order between decisions.

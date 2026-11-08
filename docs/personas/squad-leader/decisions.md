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
- **How:** formation (`WEDGE`, `FILE`, `LINE`) and interval (`TIGHT` 3, `NORMAL` 6, `EXTENDED` 10
  blocks).
- **Vertical tolerance:** how far from the point's height the squad may end up.
- **Risk:** the squad's, and per soldier (`soldierRisk`), so one stuck soldier is freed at MEDIUM
  without the rest taking falls.
- **Rearm:** whether soldiers may stop to make their kit.
- **Intent**, in words, logged with the order.

It doesn't decide the work. The task's routines start from the general's `Task`
(docs/personas/soldier/routines.md), whatever the squad leader orders.

## When it decides
Every 20 seconds while the squad has an objective, at once on a new objective, and at once on a
contact report. Never with no objective. Soldiers keep their last order between decisions.

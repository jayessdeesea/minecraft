# Squad leader: what it decides

One per squad. A decision-making role, not a player: it has no connection and no body. Standing
rules decide (`command.DrillSquadLeader`; `minecraft.squad-leader.decides-with: drills`), or, set to
`model`, a model call (`command.ai.SpringAiSquadLeader`, any Spring AI `ChatModel`); deterministic
drills then turn the decision into each soldier's order ([drills.md](drills.md)). The rules are the
model's instructions, followed always; `DrillSquadLeaderTest` runs them through every situation of
the model's evaluation (`SquadLeaderScenarios`). The two that ask for a detachment in the mission's
words - "detail one soldier to mine", "post Bob as a lookout" - the rules don't read: those are the
platoon leader's to give as data.

## What it's given
- **The objective** from the platoon leader: a place, the mission and intent in words, and the `Task`.
- **Each soldier's status:** position, order, report, health, food and kit.
- **The terrain round the objective, surveyed:** which ground is reachable from where the squad
  stands (`bot.nav.TerrainSurveyor`), so it never has to read a grid.
- **The time of day.**

## What it decides: `command.SquadOrder`
- **Where:** the objective point the squad moves on. By the rules: the surveyed spot nearest the
  objective, counting height (a MINE objective's is a depth), that the point man can walk to, or failing any climb to, asked again each decision from
  where he stands, so a far objective is reached spot by spot - the objective itself beyond the
  survey is an order nobody can reach behind a cliff or a ravine. Always the objective itself on the
  keep, where each soldier's strip is assigned at the site
  ([military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)). The rules' formation
  is WEDGE, FILE once the point man reports his route blocked; interval TIGHT at night and in a
  patrol base, NORMAL by day; risk LOW, MEDIUM for one soldier whose CANTCO says a MEDIUM route
  exists; rearm by day, out of contact, not building a patrol base; CANTCO only with no surveyed
  ground reachable.
- **How:** formation (`WEDGE`, `FILE`, `LINE`) and interval (`TIGHT`, `NORMAL`, `EXTENDED`;
  [military-doctrine.md § 2](../../military-doctrine.md#2-formations-and-intervals)).
- **Vertical tolerance:** how far from the point's height the squad may end up.
- **Risk:** the squad's, and per soldier (`soldierRisk`)
  ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
- **Rearm:** whether soldiers may stop to make kit past their basic load - which runs through copper or iron armour, and they keep on their own - to an iron sword and up.
- **Intent**, in words, logged with the order.
- **Task organisation:** changes to its soldiers' standing plans, usually none - `roleChanges`, a
  soldier's role and site; `detached`, a soldier sent off on a job of his own: `GO_TO`, `FETCH`, `LEVEL`, or
  `CANCEL` ([military-doctrine.md § 18](../../military-doctrine.md#18-task-organisation)). By the rules: plans as they stand, but with every
  soldier detached on a role, the one nearest the objective back to the squad as a rifleman.

The work is its soldiers' roles': a rifleman's is the platoon leader's `Task`, any other role's that
role's task at its site (docs/echelons/soldier/routines.md). It sees each soldier's plan at the end
of his line.

## When it decides
Every 20 seconds while the squad has an objective, at once on a new objective, and at once on a
contact report. Never with no objective. Soldiers keep their last order between decisions.

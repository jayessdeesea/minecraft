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
  stands (`bot.nav.TerrainSurveyor`), so it never has to read a grid - from the soldier nearest the
  objective within 16 blocks of its height, else the one at the squad's middle height (live, a squad
  three-quarters down the mine was surveyed from y=6 and sent to caves under the camp, out of reach
  of the one on top).
- **The time of day.**

## What it decides: `command.SquadOrder`
- **Where:** the objective point the squad moves on. By the rules: the surveyed spot nearest the
  objective, counting height (a MINE objective's is a depth), that the point man can walk to, or failing any climb to, asked again each decision from
  where he stands, so a far objective is reached spot by spot - the objective itself beyond the
  survey is an order nobody can reach behind a cliff or a ravine. Always the objective itself on the
  keep, where each soldier's strip is assigned at the site
  ([military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)). The rules' formation
  is WEDGE, FILE once the point man reports his route blocked and till he reports anything but moving, or the point man or the
  objective is another; down a mine - from the first soldier that reports mining till the objective is another - the
  order stands as it was, in FILE: there each soldier is placed by the mine's own rules, and a point read from the
  gallery last surveyed places nobody; interval TIGHT at night and in a
  patrol base, NORMAL by day; risk LOW, MEDIUM for one soldier whose CANTCO says a MEDIUM route
  exists; rearm by day, out of contact, with no task - the squad holding; CANTCO only with no surveyed
  ground reachable, and the squad then holds at one place - where its point man stood when none was found -
  till some is. The squad's contact lasts from its first soldier's till none has been in one for 10 seconds
  (`command.SquadContact`; [military-doctrine.md § 8](../../military-doctrine.md#8-actions-on-contact-and-the-react-to-contact-drill)):
  rearming waits on that, and a soldier's contact is a contact report once in it (to a model in its place, as
  before: a soldier in contact who wasn't at its last decision).
- **How:** formation (`WEDGE`, `FILE`, `LINE`) and interval (`TIGHT`, `NORMAL`, `EXTENDED`;
  [military-doctrine.md § 2](../../military-doctrine.md#2-formations-and-intervals)).
- **Vertical tolerance:** how far from the point's height the squad may end up.
- **Risk:** the squad's, and per soldier (`soldierRisk`)
  ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
- **Rearm:** whether soldiers may climb the kit ladder - armour, spares, buckets, the tool load - and make a trip for
  their load's surplus. Given only to a squad holding with no task (operator, 2026-11-22: soldiers do nothing without a
  command); without it a soldier makes only the weapon or the pickaxe it has none of, and armour as
  [military-doctrine.md § 11](../../military-doctrine.md#11-pre-combat-checks) says - the platoon leader's armour check's.
- **Intent**, in words, logged with the order.
- **Detachments called in** on a mission's ground work - a flatten, a floor, a supply point's template: every soldier
  on a role from an older order comes back to the squad (`DrillSquadLeader.changes`).
- **Task organisation:** changes to its soldiers' standing plans, usually none - `roleChanges`, a
  soldier's role and site; `detached`, a soldier sent off on a job of his own: `GO_TO`, `FETCH`, `LEVEL`, or
  `CANCEL` ([military-doctrine.md § 18](../../military-doctrine.md#18-task-organisation)). By the rules: plans as they stand, but with every
  soldier detached on a role, the one nearest the objective back to the squad as a rifleman.

The work is its soldiers' roles': a rifleman's is the platoon leader's `Task`, any other role's that
role's task at its site (docs/echelons/soldier/routines.md). It sees each soldier's plan at the end
of his line.

## Its producers' duty orders, and their yield
Standing rules only, never the model (`command.FarmDuties`, `command.WoodDuties`, `app.DutyBook`). Each producer's order
carries its duty, the ground it works and the dump its yield goes to, and a baker's its makings: the chest it draws from,
the table it bakes at and its batch - the soldier executes it and picks none of them
([military-doctrine.md](../../military-doctrine.md), "the farm by duty"). Each spell of three minutes it holds each share to
the yield expected of what's in it to work (`command.DutyYield`), against what the errand ledger counted its duty's routine
making, and a spell ended short is behind - `[Alarm] [Alpha] Dana behind its share: ..., made 0 of 8 expected in 180 s -
CANTCO` - answered by a rule per cause, said once while the cause holds (`command.MissAnswer`):

| Cause | Answer |
|---|---|
| The soldier's CANTCO at its duty - its table gone | The order re-pointed to the next standing table (the camp's, then the keep's), else up the chain: `[Alpha -> PL] Dana, baker, CANTCO: ... - no table standing to re-point it to` |
| Its source ran dry | The next chest the stores book knows holding its makings, else up the chain: `idle: no wheat stored` |
| Its ground worked out - a woodcutter's strip | That woodcutter given half of the strip with the most standing; the others' left as they are |
| Its hands other work's - a fight (`CONTACT`), a ration run - half the spell or more | Said as the cause; not re-ordered |
| None found | `[Gap] [Alpha -> PL]`: the duty, the yield short, and what was checked |

A share with nothing in it to work is no spell and isn't behind; a baker idle for want of wheat is said up the chain once
(`[Alpha -> PL] Dana, baker, idle: no wheat stored`) and shown in the squad's progress, the platoon leader's view, till a
spell with work in it has run.

## Its soldiers' inventories, passed up
For the platoon leader's armour check, each soldier's inventory - its armour and shield, the copper and iron it carries, its pack's
room - is read and said in the squad leader's name, and the order that comes back goes to the soldier; no `SquadOrder`
carries either
([platoon-leader/decisions.md](../platoon-leader/decisions.md#armour-the-pre-combat-check)).

## When it decides
Every SITREP while the squad has an objective - each second - so a soldier's CANTCO and a contact report are
answered in the SITREP that carries them, and a new objective in the next, once the ground is surveyed for it.
Never with no objective, nor while the squad
holds its sealed patrol base. An order equal to the standing one isn't given again: it's said, and logged,
when it changes. The terrain it reads is the last survey taken, on the survey's own clock - 20 seconds, or
sooner once the point man has moved 16 blocks or the objective is another - not one a decision. A model in
its place decides on a slower clock
([architecture.md](../../architecture.md#latency-architecture--why-the-hierarchy-is-stratified-by-timescale-not-just-rank)),
and the soldiers keep their last order between its decisions.

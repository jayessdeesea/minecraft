# Platoon leader: the operator's standing rules

Rules the operator sets in configuration, applied to the platoon leader's objectives after it decides
(`app.ChainOfCommand.sheltered`). Every override is logged as a WARN beside what the platoon leader had
ordered.

## Shelter at night (`minecraft.platoon-leader.shelter-at-night`)
From nightfall until dawn - a hut goes up in a minute or two, faster than the night's first
monsters spawn and walk in, so the day is worked to its end. The platoon leader is still asked 90 seconds
before dark, to plan the night:
- **Every squad is on the night shift** (operator, 2026-11-13): whatever the platoon leader orders, a
  patrol base included, it's sent down the mine, every squad by the one stairway. Under the keep
  rule it's the keep's own mine, off its north-west corner (`ChainOfCommand.KEEP_MINE`): from the
  camp's, the climb out and the walk were two-fifths of the day. Otherwise the camp's: 4 blocks
  east and 4 north of its centre, inside the wall. Cut to the iron at y=16
  (`ChainOfCommand.nightShift`); a mine off the camp is kept on disk by where it was ordered, and
  cut on down after a restart. The shift ends 30 seconds before sunrise
  (`ChainOfCommand.SURFACE_LEAD_TICKS`): the climb out and the walk to the day's work, so the
  squads come up as the sun burns the undead off the surface, and the day's rule - the keep - takes
  them from there. How a squad works the mine at night is in
  [military-doctrine.md § 16](../../military-doctrine.md#16-mining-down).
- **With no camp measured yet, a squad shelters.** An order that isn't a patrol base is replaced
  by the patrol base it was already ordered to build, or with none, by one where its point man
  stands. A squad sealed in is never ordered out.
- **A squad's detachments come in with it**, but for its miners, who work on underground: a
  soldier with any other role, and no order of its own, is in its squad's main body by night, and
  shelters with it (`ChainOfCommand.sheltering`).

## The keep by day (`minecraft.platoon-leader.keep-by-day`)
By day the platoon leader assigns the day's work (operator, 2026-11-17): its `FARM`, `TREE_FARM`, `WOOD`,
`MINE` and `KEEP` orders stand, request or not; any other order by day is put on the keep (task `KEEP`,
[military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)), or, past the squads the keep's level
wants, sent to what the force is short of. Over all of it, guardrails that act loudly ("[Guardrail]",
`ChainOfCommand.guarded`): no squad on the flatten, one is put on it - one not on a shortfall, its standing
flattener first - but never while the force is short of food or a soldier is short of a sword or a full set of
armour: food and arms come first, and nobody flattens (operator, 2026-11-18; `PlatoonSergeant.unarmed`, which also
raises WOOD for swords and IRON for armour); something short with nobody on it, a
squad doubling up elsewhere is moved to it. The farm squad keeps the camp's farm. The night rule still comes first,
but for the night shift's last 30 seconds, when the squads climb out to it. A squad's miner, with no
order of its own, builds the keep with it by day: the whole squad mines by night.

## The camp (`minecraft.camp`)
The x,z the foothold's standards are measured round (docs/echelons/platoon-leader/decisions.md).

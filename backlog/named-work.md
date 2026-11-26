---
status: ready
priority: P1
---

# Named work, not "the" ground: each squad works its own mission's flatten at the same time

## Why
Each squad should be able to work an independent flatten at the same time (operator, 2026-11-23). Today one flatten is
worked at a time, whatever the squads free: live (field701), a tree farm's flatten had one squad on it, and a second
tree farm, a supply point and a mineshaft waited behind it. The rule isn't a choice of the missions' - `FarmMissions.worked`
skips a second flatten because there is one ground to flatten in the whole garrison:
- `ChainOfCommand.flatten()` is one `Flatten` - the first flattening mission's, else the configured site; a squad's own
  is `FarmMissions.flatten(objective)` (`SoldierRunner.flatten(squad)`), which has one answer while one flatten is worked.
- `SoldierRunner.flattenWorks` is a map of grounds to `FlattenWork` (the ground's state; `keepState` holds the platoon's
  wood party and each builder's fights) with one entry, re-keyed to the ground asked: one level plan and its boxes (`FlattenWork.Plan`), one set of hands and
  where each works, one depot and workshop and chest row, one foreman, one pace and run, one haul road, one table of
  column tops. Every squad on a `Keep` task is a share of that one ground ("the keep: 572 blocks to dig ... in its
  share x=4..43").
Since 2026-11-23 a squad with no step of its own joins the one flatten under way (`FarmMissions.atTheFarms`): more
hands, still one ground.

**The abstraction is what's wrong** (operator, 2026-11-23: there may be an objective to flatten something, but there
is no global "what are we flattening"; everything is named - docs/conventions.md, "Work is named"). The same shape, one
field for "the" thing where the force has many, in `SoldierRunner` and `ChainOfCommand`: the ground to flatten
(`flatten`, `flattenWorks`, `keepLevel`); the camp's mine and the keep's (`campStairs`, `campMine`, `campMineSite`,
`keepMine` - a mineshaft mission near the camp was taken for the camp's and never cut, backlog/mining.md); the camp's
farm and tree chunk (`campFarm`, `campFarms`, `campTrees` - a mission's tree chunk has to "take" the camp's); the camp's
workshop, wall, ground and level (`campWorkshop`, `campWalled`, `campGround`, `campLevel`); the farm squad.

## Done when
- **By name**: what a squad levels, mines, farms or plants is its objective's mission and step, and the state of that
  work is kept under that name. No field answers "the current" ground, mine, farm or grove. The camp is a named site
  like any other, with its own missions.
- A squad on a mission's flatten works that mission's ground: `FarmMissions.worked` gives every open mission's flatten,
  each to its squad, and the ground a squad levels is its objective's (`flatten(squad)`), not the garrison's.
- The keep's state is kept by ground: the level plan and its shares among the soldiers on that ground only, its hands,
  its depot and salvage, its foreman, its pace, its column tops, its road. What is the platoon's stays one: the fight,
  the wood party, the stores.
- Two squads on one ground still share it, as now (`atTheFarms`).
- The eval: two squads, two missions on chunks apart; both flattens' column counts fall in the same minutes, no soldier
  of one squad is given a box of the other's ground, each mission's flatten DONE. And the simulations that have one
  ground pass as they are.
- Live: the tree farm's flatten and the next mission's fall together, a squad each.

## Notes
- `SoldierRunner` is at its line cap (7369): the per-ground state is a class of its own (`FlattenWork`, the keep's work
  lifted out with it) behind `FlattenWorks`, not a map threaded through the runner. backlog/split-oversized-sources.md.
- The flatten first, since it's what holds the base back; then the mines; then the farms and groves; the camp's own
  works last. Each kind is its own item's worth of work.
- Left for the flatten, in order: the one-at-a-time rule dropped (`FarmMissions.worked`; `FlattenWorks` then keeps an entry
  a ground, and the any-ground readers - `keepwork`'s squads on the keep, the wood party, `onFlattened`, the grove's
  avoid box - ask for every ground), then the eval - each a commit with the simulations green. The ground a squad works
  (`flatten(squad)`) and the entry kept for it (`FlattenWorks.of`) are in.
- Not over-extending is the plan's to hold (`BasePlan`, missions open against squads), not this rule's.

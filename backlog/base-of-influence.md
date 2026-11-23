---
status: in-progress
priority: P1
---

# The platoon leader's strategy: a base that expands influence, worked by rules

## Why
The platoon leader's model and its rules give conflicting orders (operator, 2026-11-23): on field700 the model decided
19 times and the rules overrode it 41 times, 40 of them to put a squad back on an operator's mission; the one squad no
rule covered was sent to mine stone with 34,648 held; and a 257-character mission in the model's words dropped every
order for a day. The operator: get rid of the agentic commands other than the operator's - soldier, squad leader and
platoon leader fully deterministic; the only model at run time is the one the operator talks to, and at development
time the loop that watches the garrison.

And the strategy the rules carry out (operator, 2026-11-23): **expand influence**.
- A large permanent structure over x=-64..64, z=-64..64 at sea level: all of it flattened, then a template applied at
  each of its chunks.
- Never over-extended: renewable resources - tree farms, wheat farms, supply dumps, mineshafts - are built as the work
  goes, so the force sustains itself while it builds.
- When the base is done, smaller permanent structures further out, to explore from, to go on mining, and to trade with
  villagers.

## Built
The platoon leader decides by rules (`minecraft.platoon-leader.decides-with: drills`, `command.DrillPlatoonLeader`,
`app.RulesPlatoonLeader`): no model call in its cycle; the model stays the one the operator talks to, told what the
platoon is told. The base is `command.BasePlan.ROUND_THE_ORIGIN` - chunks -4..3 each way at y=63, the camp's own left -
in sixteen clusters two chunks by two (operator, 2026-11-23: a mineshaft, a supply dump next to it, a wheat farm and a
tree farm nearby, then another mineshaft two chunks away): 15 mineshafts, 16 supply points, 16 wheat farms, 16 tree
farms. `FarmMissions.fromThePlan` gives its next mission at a platoon decision when fewer are open than squads but the
farm squad: a cluster begun is finished first, from its mineshaft, the one nearest the origin first; then the cluster
beside what's built nearest the origin. Live that is the mineshaft at chunk (0, 2), the supply point at (1, 2), the tree
farm at (0, 3); then (2, 2) and (3, 2); then the camp's own cluster. A chunk with a mission already keeps it. Food and wood first:
no wheat farm, the next is one; no tree farm, one; nothing else begun until one of each is built; and the farms' steps
get squads before the others'. docs/echelons/platoon-leader/decisions.md.

## Done when
- **Expand, hold, contract by measure - the experiment** (operator, 2026-11-23: where do we contract, where do we
  expand, where is it safe to take risks; flattening it all at once we'd be over-exposed - out of food, wood, iron).
  Today the gate is "a wheat farm and a tree farm built". Wanted: "reliable" measured - a farm's yield against what the
  force eats and burns over days, stores of food, wood and iron against a reserve - the next mission begun only with
  those above their reserve, and work pulled back to the farms when one falls under: contracting, said and logged.
- **The next mission by what the force is short of**: today it's the cluster's order. Wanted: within it, the platoon
  sergeant's shortfalls first (food, wood, stores room, ore), and what's built counted against what the force eats.
- **The plan seen and changed by the operator**: the chat shows the base - each chunk, its template, its state - and
  changes a chunk's template; a chunk's state kept in the chunk register (as it lies, flattened, slabbed, paved,
  templated).
- **What's built is tended by more than the farm squad**: a squad to each producing farm by its crop's clock, and the
  conformance pass (backlog/renewables.md) before it. Today a squad with no mission is given the wheat or the trees to
  keep, turn about.
- **A mineshaft mined dry is given a farm's template** (operator, 2026-11-22).
- The outposts: a later item, once the base stands.

## Notes
- **The missions already given at chunks (1, 4) and (2, 4)** - the supply point and the mineshaft - are at z=64..79,
  one row south of the base, and are worked as they are (operator, 2026-11-23: continue building where we are
  building). They hold two squads; whether to keep them or cancel them for the plan's own at (1, 2) and (0, 2) is the
  operator's.
- **A tree farm ordered at chunk (3, 3)** stands where the plan has a wheat farm; it keeps its chunk. The wheat farm at
  (1, 3) and the tree farm at (2, 3) match the plan.
- One squad's objective the register can't take still drops the whole decision (`ChainOfCommand.askPlatoonLeader`):
  by rules the missions' words are short, but the fault stands.
- Supersedes, where they differ: backlog/flatten-campaign.md (the ground to flatten is this area), the siting by bids in
  backlog/renewables.md (a chunk's template comes from the plan), backlog/model-routing.md for the platoon leader.

---
status: ready
priority: P1
depends-on: []
doctrine: FM 6-0 (task organisation, priorities of work); ATP 3-21.8 (basic load, rest plan)
---

# Task organisation: what's left

## Why
Roles, detachments and directives are built (docs/military-doctrine.md § 18): the squad leader
makes a soldier its miner, posts one at a point, sends one for wood or to level ground, and the
plan outlives a restart. What's left draws the line between what a soldier owns and what its
squad leader decides:

- **The soldier owns its upkeep.** It eats, fights back and goes back to work, routes itself and
  asks for help when out of ways (docs/echelons/soldier/capabilities.md). Its kit isn't on that
  list yet: rearming waits on the squad leader's `rearm`.
- **The squad leader owns the plan.** Today the runner still fills a soldier's idle time on its
  own - camp upkeep, the farm for a hungry squad, the night shift - so an idle soldier is never
  the squad leader's miss, and nobody sees it.

## Done when
Each a squad-simulation scenario, then seen live:
- **Work orders, tracked.** Every directive is a work order the squad leader's drills track:
  ISSUED, UNDER_WAY (the world shows progress), DONE, CANTCO, TIMED_OUT. It times out when the
  world shows no progress for a while, or past a deadline from its estimated cost; the soldier goes
  back to its role, the squad leader sees TIMED_OUT and why, and the same order to the same soldier
  backs off longer each time. `TaskRegister`'s STALLED and its setbacks are the start of it.
- **Liveness: time back to the requester.** Every queue - model calls waiting, a soldier's
  request unanswered, a work order ISSUED and not UNDER_WAY - has an age alarm (`[Alert]`, as a
  soldier stuck a minute has), and each dawn's AAR says the slowest answer of each kind. A request
  is answered when the squad leader's next decision after it addresses it, not when it's read.
- **The farm cycle as a drill.** The farms in a box surveyed from the loaded world - each plot bare,
  growing (its age) or ripe - with no soldier sent to look unless the box isn't loaded. Ripe plots
  become harvest-and-replant work, bare ones till-and-plant, and a farmer with its farm all growing
  is free for other work. The model sees one line a farm and decides only allocation: another farm
  in a box while Class I is short, who farms. Decided (operator): one FARMER role, the cycle the
  drill's, to keep the model's round trips few - revisited if that proves too coarse.
- **CLEAR(box).** Everything in a box dug out - an encampment's ground, so nothing hides in shade by
  day - reaching high blocks from pillars it takes down after. Bamboo scaffolding later.
- **A basic load per role.** Each role carries a standard load - a miner two pickaxes, torches and
  bread, say - and the soldier keeps itself at it as a standing duty, from the stockpile or by
  crafting, without a `rearm`. "Out of supplies" is a soldier below its basic load that can't make
  it up: a request to its squad leader saying what's short (docs/military-doctrine.md § 4). Live,
  a miner whose pickaxe wore out, with no logs within 48 blocks, came up and stood reporting CANTCO.
- **Awaiting orders.** A soldier whose plan leaves it nothing to do reports so, and the squad
  leader's view and the idle alert say whose plan ran dry - rather than the runner's upkeep
  filling it. Camp upkeep and the hungry squad's farm become the squad's drills writing plans
  (a FARMER role by day while Class I is short), not the runner's maps.
- **Assembly.** Soldiers sent to one point report closed on it (`InPosition` today says it
  for one); the squad leader sees the squad closed before the next phase.
- **More directives:** `Deposit(items, at)` and `Withdraw(items, at)` at a chest; `Craft(item, n)`;
  `Fetch` of what a kit doesn't count yet (raw iron, wheat).
- **Outpost standards.** A mining outpost's checklist - a chest, a table and furnace, beds, a farm,
  wood - checked from the world and turned into directives by a drill, the model only choosing the
  template (backlog/echelon-contracts.md's engineer).
- **The general's tasking.** The general can ask a squad for a detachment ("one soldier on the
  mine"); SquadLeaderScenarioIT's MINER and POST variants are that case.
- **Overruling self-preservation.** An order that says hold under fire (`risk` HIGH, or its own
  flag) keeps a soldier on its work in contact.

## Notes
- **Decided (operator): one role per soldier.** Lighting a dark place it works in is every
  soldier's duty, not a role.
- **Decided (operator): realistic mining** - tunnels from what the soldier can see, not ore read
  through stone from the chunk data. The branch pattern is built (§ 16: branches every 3 blocks).
  Deeper levels are backlog/mining.md's.
- **Decided (operator): beds for the surface roles; miners work through the night**
  (backlog/rest-plan.md).
- **Decided (operator): self-preservation wins unless the squad leader overrules it** - the
  farmer attacked by a pillager draws its sword (Hands, docs/echelons/soldier/routines.md).

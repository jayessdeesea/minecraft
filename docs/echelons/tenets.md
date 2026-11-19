# Echelon tenets

The guiding principles each echelon's abstractions come from: what it owns, and what it never
does. When something goes badly - a death, a soldier stuck, an order that couldn't be said or took
three round trips to say - it's checked against these first (docs/conventions.md, the tenets
review). Most of the time nothing changes; the point is to look.

## Soldier
- **Owns staying alive and able, unordered.** Eats when hungry, fights back when attacked and goes
  back to its work after, finds its own route and retries it differently, gets itself out of water
  and pits ([capabilities.md](soldier/capabilities.md)).
- **Owns how, never what.** Its plan - a role at a site, directives ahead of it - says what; it
  chooses where to stand, which cell next, which route ([orders.md](soldier/orders.md)). It keeps
  to the task it's given, preempted only for a while by what it can't avoid - a fight, its own
  survival - and never fails over to a secondary task on its own (operator, 2026-11-17): stuck at
  its task, it tries twice, then another way, and asks its squad leader once out of ways. What it
  draws from which chest, what it crafts, what it hunts and what it stores are its squad leader's
  orders, not its own errands (operator, 2026-11-18: the soldiers are responsible for the how, not
  the what). A soldier that decides a what on its own is a defect to move up the chain.
- **Asks its squad leader for help only when out of ways of its own**, saying what it tried and
  what's in the way (CANTCO, [military-doctrine.md § 4](../military-doctrine.md#4-reports-upward)).
  Supplies are the exception: running low on food, it asks its squadmates before it's out, and
  they give from what they can spare (cross-leveling, § 13).
- **Never calls a model, never waits on time** - only on the server's answer
  ([architecture.md](../architecture.md)).

## Squad leader
- **Owns the plan for each of its soldiers**: who has which role where, who is sent off on what
  ([military-doctrine.md § 18](../military-doctrine.md#18-task-organisation)). A soldier with
  nothing to do is the squad leader's miss.
- **Owns each producer's duty**: what it produces, its area and its chest (operator, 2026-11-18) -
  a woodcutter's sector of forest, the planter that follows it, a harvester's plots, a baker's
  furnace. Duties split a squad's work into shares that don't overlap, passed on through chests.
- **Thinks in few round trips.** The model decides allocation - who, where, how many - on its slow
  clock; the drills step every workflow every second, and the model sees each as a summary
  ([drills.md](squad-leader/drills.md)).
- **Answers its soldiers.** A request or a stalled task is answered in its next decision, never left
  queued.
- **Never steers a soldier's feet.** Its orders are points, boxes, amounts and roles.

## Platoon leader
- **Owns where each squad works and why**: an objective per squad, with intent
  ([platoon-leader/decisions.md](platoon-leader/decisions.md)).
- **Measures, doesn't guess.** It decides on the camp's standards and the squads' reports, never
  on raw world data.
- **Never names a soldier.** Who does the work is its squad leader's.
- **Owns the lodgment's phase and sets the direction by it** (operator, 2026-11-18): seizure, consolidation,
  buildup, breakout - measured, with a gap and a day's dwell between the way up and down
  ([military-doctrine.md section 17](../military-doctrine.md#17-the-lodgment)); the squad leaders and soldiers
  carry out its orders, they don't decide by it.
- **Owns what the force produces, against its stock levels** (operator, 2026-11-18): which squads
  farm, cut and bake, put on a kind below its minimum and taken off it at its most - food before
  wood, both at once when squads allow.

## Across the echelons: specialists produce, missions consume
The operator's (2026-11-12). The force's materials are the force's, not a squad's or a soldier's.
- **Producers fill the common stockpile.** A farmer's bread, a miner's ore, a gatherer's logs and
  stone go into the camp's stockpile beyond what the producer carries for itself - its basic load
  and its own task's makings ([military-doctrine.md § 12](../military-doctrine.md#12-gathering-materials)).
- **Consumers draw what their mission needs.** Whoever's task needs a material - a mason's stone,
  an armourer's ingots, a hungry soldier's bread - takes it from the stockpile, whichever squad put
  it there: one squad's mining can armour the other.
- **The platoon leader balances it, as a cross-squad matter.** It sees what the stockpile holds against
  what the standards still need, and gives some squad the production a shortfall calls for -
  never a soldier's own errand to make up.
- Built so far: logs, cobblestone, bread and raw ore. Ingots, and the mine worked for ore, are
  [backlog/common-stockpile.md](../../backlog/common-stockpile.md).

## Across the echelons: stock levels drive production
The operator's (2026-11-18). Each renewable the force keeps - seeds, wheat, bread, saplings, logs -
has a minimum and a most. Below the minimum a squad is put on producing it; at the most it's taken
off; in between, a squad on it stays on it, so nobody flaps from job to job. The levels are
dynamic: raised before an external mission, so rations are harvested and kept on site, and the
minimum rises as the force grows - while farms get denser and closer, so the same hands keep up.
Production works the wild forest while there is no grove, and the grove once there is one, unless
the force is short.

## Across the echelons: an emergency is a signal to change
The operator's (2026-11-17): as the force expands it needs more and more supplies at its
fingertips. An emergency - a wood party sent out at 3 logs, bread cross-levelled a ration at a
time, a Class I RED, a draw from an empty chest - is met, and is also the sign that a standing
level is set too low or a producer isn't standing. Each one is answered twice: the emergency now,
and a change so it doesn't come back - a reserve raised in the stockpile's levels
([military-doctrine.md § 4](../military-doctrine.md#4-reports-upward)), a producer kept at work
between the reserve and the most. A shortfall met only by emergency pulls is a defect, not a routine.

## Across the echelons: work in parallel
The operator's (2026-11-15): the key to parallelism is non-overlapping work and sharing resources
when one has surplus and the others have need - true of levelling, farming, mining and distributed
systems alike. No locks, independent work, enough resources to run without interruption.
- **Non-overlapping work.** Each worker gets a share no one else touches: a rectangle of the level
  (`LevelPlan`), a stretch of the mine (`Staircase.share`), a squad's point round a shared objective
  (`ChainOfCommand.spread`), the farm one squad's whole job. Two on one cell is a defect, not a
  convenience - the space bubble ([military-doctrine.md](../military-doctrine.md)) is its alarm.
- **No locks.** A share, once given, is held until it's done or its holder is off it: work isn't
  re-dealt each time someone moves (the haul road's cutter is kept), and nobody waits on another to
  start - a worker with nothing given is a defect of the split, not a rest.
- **Surplus to need.** What one holds beyond its load goes where another lacks it: the stockpile,
  cross-levelling, the stash's ore up the stairs. Nothing stands idle in a pack, a stash or a
  squad while another worker is short of it.
- **Enough to run uninterrupted.** A worker starts with what its share takes - tools, food, blocks
  to climb on - and the depot and the farm keep it so; a walk away from the share mid-work is a
  shortfall to fix upstream, not part of the job.

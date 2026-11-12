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
  chooses where to stand, which cell next, which route ([orders.md](soldier/orders.md)).
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
- **Thinks in few round trips.** The model decides allocation - who, where, how many - on its slow
  clock; the drills step every workflow every second, and the model sees each as a summary
  ([drills.md](squad-leader/drills.md)).
- **Answers its soldiers.** A request or a stalled task is answered in its next decision, never left
  queued.
- **Never steers a soldier's feet.** Its orders are points, boxes, amounts and roles.

## General
- **Owns where each squad works and why**: an objective per squad, with intent
  ([general/decisions.md](general/decisions.md)).
- **Measures, doesn't guess.** It decides on the camp's standards and the squads' reports, never
  on raw world data.
- **Never names a soldier.** Who does the work is its squad leader's.

## Across the echelons: specialists produce, missions consume
The operator's (2026-11-12). The force's materials are the force's, not a squad's or a soldier's.
- **Producers fill the common stockpile.** A farmer's bread, a miner's ore, a gatherer's logs and
  stone go into the camp's stockpile beyond what the producer carries for itself - its basic load
  and its own task's makings ([military-doctrine.md § 12](../military-doctrine.md#12-gathering-materials)).
- **Consumers draw what their mission needs.** Whoever's task needs a material - a mason's stone,
  an armourer's ingots, a hungry soldier's bread - takes it from the stockpile, whichever squad put
  it there: one squad's mining can armour the other.
- **The general balances it, as a cross-squad matter.** It sees what the stockpile holds against
  what the standards still need, and gives some squad the production a shortfall calls for -
  never a soldier's own errand to make up.
- Built so far: logs, cobblestone and bread. Ore and ingots are
  [backlog/common-stockpile.md](../../backlog/common-stockpile.md).

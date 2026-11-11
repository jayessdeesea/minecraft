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
- **Asks for help only when out of ways of its own**, saying what it tried and what's in the way
  (CANTCO, [military-doctrine.md § 4](../military-doctrine.md#4-reports-upward)).
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

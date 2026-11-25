---
status: ready
priority: P2
depends-on: []
---

# A squad scouts for animals and leads them back

## Why
The operator's (2026-11-25): a squad goes out, finds animals of a kind and brings them back - chicken scouting, sheep
scouting, cow scouting. The order: look here for {animal} and bring them back to {location}. The force has no stock
but what it meets by chance; a sheep farm's template stands empty until something is led into it.

## Done when
- **The order.** An operator mission with an animal's kind, where to look and where to bring them, given by the
  platoon leader to one squad as a task.
- **The food is the kind's.** What leads the animal is decided by its kind (wheat for sheep and cows, seeds for
  chickens), one table, and drawn before the squad leaves: a prerequisite, as a mine's shift is drawn for.
- **The search.** The squad may split to cover several directions out from the place given, each soldier a sector of
  its own (the sectors rule: soldiers' rectangles disjoint).
- **Where the land ends.** A soldier that meets water reports where the land stops; its squad leader says it up to
  the platoon leader. No animal is led over water: sheep are very hard to move across it.
- **The lead.** A soldier holding the kind's food walks to {location} at a pace the animals keep, and the count that
  arrives is checked against the count picked up; a miss is dug into.
- **The eval: the animals follow.** In the squad simulation, a led animal stays within following distance of its
  soldier all the way to {location}.

## Notes
- Fences and gates on the way are for later (operator): the first version brings them to open ground at {location}.
- The simulated server has to model an animal following a held food for the eval to mean anything; what it models
  today is the first thing to check.
- Terms: the place searched is an objective, the squad's part a task (docs/military-doctrine.md).

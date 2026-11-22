---
status: proposed
priority: P2
doctrine: ATP 3-21.8 (weapons employment; direct fire)
---

# Ranged weapons: the crossbow, a moving target, the hostile on a ledge

## Why
A soldier shoots a bow at full draw at a hostile beyond its sword or out of reach, and makes its arrows from
flint and feathers (docs/military-doctrine.md § 8, § 11). It can't fire a crossbow, it aims where a moving
target was, and no eval shows the case the bow is for: a skeleton on a ledge it can't climb to.

When to use which weapon is backlog/engagement-doctrine.md's (the exchange); the bow and arrows in each load
are backlog/load-plans.md's.

## What's left
- **The crossbow:** load, then fire.
- **Aim leading a moving target**, and a shorter draw for a close one.
- **The ledge, measured in `CombatScenarioTest`.**
- **String on purpose.** A bow is made only from string already carried (`bot.craft.Ladder`); string comes
  from spiders, or cobwebs broken with a sword.

## Done when
- Against a skeleton on an unreachable ledge, a soldier with a bow kills it; one without reports it can't
  engage, rather than standing below taking arrows.
- A soldier carrying a crossbow and arrows loads and fires it.
- Hits on a hostile crossing its front at range, in the battle simulation, against today's rate.

## Notes
- Arrows are entities: a missed arrow can be picked back up.
- Squad level: ranged soldiers give supporting fire while others close - the fire-and-movement half of
  react-to-contact.

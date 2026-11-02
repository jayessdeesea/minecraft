---
status: proposed
priority: P2
doctrine: FM 4-0 (Class I supply)
---

# Subsistence: soldiers eat

## Why
The LOGSTAT counts hungry soldiers (§ 4), and the squad leader weighs food in its risk cost, but no
soldier ever eats. A soldier below food 18 doesn't regenerate health, so every hit is permanent
until it dies. Class I is the one class of supply the chain of command reports but can do nothing
about.

## Done when
- A soldier carrying food eats it when its food level drops below 18, out of contact. It holds the
  item and uses it, which in this client means Use Item plus Release.
- Squads can get food: kill passive animals and pick up the drops. Cooking can wait, since raw meat
  feeds, only less.
- Food shows in LACE and LOGSTAT, and can be cross-levelled (backlog/cross-leveling.md).

## Notes
- **Hunting:**
  - The soldier reuses the melee reflex against a passive mob instead of a hostile one.
  - Passive mobs are already in `EntityTracker`; they're just not targeted.
  - The general orders it like gathering: an objective with `gather: FOOD`.
- **Biome dependence** (see backlog/renewables.md):
  - Plains have cows and sheep.
  - Deserts have almost nothing.
  - The Nether has hoglins, which fight back.

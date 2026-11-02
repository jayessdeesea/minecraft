---
status: proposed
priority: P2
doctrine: FM 4-0 (Class I supply)
---

# Subsistence: finding food

## Why
Soldiers eat the rations they carry (docs/military-doctrine.md § 4), but nothing gets them food:
once the rations run out, a soldier below food 18 stops regenerating health for good. Class I is
the one class of supply the chain of command reports but can't replenish.

## Done when
- Squads can get food: kill passive animals and pick up the drops. Cooking can wait, since raw meat
  feeds, only less.
- Rations show in the LOGSTAT (they're in each soldier's LACE as `Kit.rations`), and can be
  cross-levelled (backlog/cross-leveling.md).

## Notes
- **Hunting:**
  - The soldier reuses the melee reflex against a passive mob instead of a hostile one.
  - Passive mobs are already in `EntityTracker`; they're just not targeted.
  - The general orders it like gathering: an objective with `gather: FOOD`.
- **Biome dependence** (see backlog/renewables.md):
  - Plains have cows and sheep.
  - Deserts have almost nothing.
  - The Nether has hoglins, which fight back.

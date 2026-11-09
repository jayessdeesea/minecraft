---
status: in-progress
priority: P1
depends-on: [echelon-contracts]
doctrine: ATP 3-37.10 (base camp standard designs, capabilities by echelon); ATP 4-90 (sustainment, Class II/IV/VIII); FM 4-0 (logistics estimates)
---

# Sites: a base is a set of capabilities, with a bill of materials

## Why
A player who mines sets up a working base at the level they mine: water, a table, a furnace,
chests for what doesn't fit in the inventory, light, and somewhere to eat. They carry down what
it needs, or win it on the spot. Where a base goes and what it holds is the base's knowledge,
not the soldier's: a soldier moves, fights, digs, places, carries, and crafts at a table it's
sent to.

## Built so far
Workshops (docs/military-doctrine.md § 11): the engineer sites the camp's, a hasty one at an
objective, and the mine base's at the landing; a soldier crafts only at one, and fits a missing
table or furnace into the cell the workshop names.

## The mine base, by phase
A phase starts when the one before it is standing: phase 2 needs iron, which phase 1's furnace
smelts.

| Capability | Fixture | Bill of materials | Phase | |
|---|---|---|---|---|
| Craft, smelt | table and furnace in the landing's recesses | 4 planks; 8 cobblestone | 1 | built |
| Store | 2 chests, recesses beside the main tunnel's second cell | 16 planks | 1 | the surface workshops' chest built (docs/military-doctrine.md § 12); the mine base's not |
| Light | torches down the stairs and at the landing's stand | coal or charcoal, sticks | 1 | built; the tunnels unlit |
| Water | an infinite source: two buckets | 3 iron a bucket | 2 | |
| Food | 3x3 wheat round the water, under torches (light 9+) | seeds, a hoe | 3 | |

- **Decided (operator):** water waits for the first iron, as phase 2; wheat grows at the mine
  base; no tree farm underground - wood is hauled down from the surface (backlog/renewables.md).
- **Logistics works out the loads:** the blueprint's materials, less what's at the site and what
  the squad carries, become loads - "bring 20 logs down", "smelt 6 iron here" - carried if the
  squad has them, gathered if not. A squad going down carries a stack of wood, and the cutter a
  spare pickaxe, since the nearest workshop above the landing is the top of the stairs.

- **Stores by class of supply.** Doctrine stores classes apart: ammunition (Class V) separated from
  fuel (Class III) and from everything else, food (Class I) kept clean and dry, each storage area
  marked (FM 4-0; ATP 4-42 on Class I storage). Here: one chest per class at a site - food, kit
  (weapons and armour), materials (logs, cobblestone), ore and ingots - so a soldier drawing one
  doesn't open, scan and hold the others, and a full chest of one class doesn't block another.
- **Signs on the chests.** A sign above each chest naming its class, for the operator: a soldier
  needs no sign, since the site's blueprint says which chest holds what - but the operator walking
  the camp reads the signs, and a sign that disagrees with what's inside is a fault to see.

## Done when
- In the squad simulation, a MINE squad builds the mine base's phase 1 from the blueprint; raw iron
  is smelted at the landing, and what doesn't fit in the inventories goes into the site's chests.
- The dawn review reports each site's capabilities, standing and missing.

## Notes
- **Naming:** `command.Foothold` today is the camp's phased standards. Once a site has a type, it
  becomes a site of the type "camp", and the mine base one of the type "mine".

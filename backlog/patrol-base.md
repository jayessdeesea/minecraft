---
status: proposed
priority: P1
depends-on: []
doctrine: ATP 3-21.8 (patrol base, priorities of work); ATP 3-37.34 (survivability)
---

# Patrol base: shelter and light for the night

## Why
Squads stand in the open all night. Hostiles spawn around them and they fight until morning. Real
units facing the same problem stop in a *patrol base*: a defensible position held for a set time,
improved continuously while they're in it. Doing that here tests whether an LLM echelon can sequence
a multi-step build against a hard deadline, sunset, the way a platoon leader sets priorities of work.

## Doctrine
- **Patrol base** (ATP 3-21.8, ch. 7):
  - Occupied only as long as needed.
  - Security is set first and stays continuous.
  - The leader then directs *priorities of work*: security, then weapons maintenance, resupply,
    rest and so on, adjusted to METT-TC.
- **Priorities of work need a standard** (FM 3-21.8, 2007; same principle carried forward). Each
  priority is "a task, a given time, and a measurable performance standard", not a laundry list.
  That maps directly onto an order the squad leader can check.
- **Survivability** (ATP 3-37.34):
  - Cover (protection from fire) and obstacles, improved over time.
  - A first position is hasty; it's improved toward a deliberate one as time allows.
  - Overhead cover is the improvement that matters most.

## Mapping to Minecraft
| Doctrine | Minecraft |
|---|---|
| Security first, continuous | **Light.** In the overworld a monster spawns only at block light 0 (`monster_spawn_block_light_limit: 0` in the server's `dimension_type/overworld.json`). A torch every few blocks denies all spawn ground inside the perimeter. Monsters still spawn 24+ blocks out in the dark and walk in, so light alone isn't the whole perimeter. |
| Obstacles, cover | **Walls 2 high, with a lip** so spiders can't climb over. A door or a gap the squad blocks behind itself. |
| Overhead cover | **A roof:** covers against skeleton arrows and the light-level edge cases. |
| Hasty → deliberate | **First night:** a hut just big enough for the squad, with one torch inside, built from whatever it has (dirt, logs, cobblestone). **Second night:** a walled, lit compound around the supply point. |
| Task, time, standard | An order like "every walkable block within 8 of the base lit, walls closed, by day-time 12000". The squad leader checks the standard rather than trusting a WILCO. |

**Torches, built from what's at hand:**
- Recipe: 1 coal or charcoal plus 1 stick makes 4 torches. It fits the 2x2 inventory grid, so no
  table is needed.
- Coal: coal ore digs with the wooden pickaxe soldiers already make, and it's often exposed on the
  surface.
- Charcoal: smelt `#logs_that_burn` in a furnace. A furnace takes 8 cobblestone, or blackstone or
  cobbled deepslate, and 10 s of burning per charcoal, using planks or logs as fuel.
- Which path a squad takes depends on what the survey (§ 12) shows nearby.

**Any wood type works.** Every recipe here uses tags (`#planks`, `#logs_that_burn`), and the item
registry classifies all logs as LOG.

**Nether:**
- Light doesn't do the job. The block light limit is 15, and zombified piglins and ghasts have
  their own spawn rules, so walls and a roof carry the whole load.
- Nether stems aren't in `#logs_that_burn`, so there's no charcoal there. Torches have to be
  carried in.

## Done when
The hasty hut, the general's PATROL_BASE task and the time of day are live (docs/military-doctrine.md
§ 14). What's left:
- **Squad leaders see how long until dark,** as the general already does (`command.TimeOfDay`).
- **Torches.** Soldiers make torches from coal they dig or charcoal they smelt, and light the base
  so block light > 0 on every floor block.
- **Deliberate base on a later night.** The squad improves the base with a wider perimeter and more
  light. The improvement is measured by a test that counts lit, walled floor blocks.

## Notes
- **The order needs a standard.** A patrol base order carries its standard as data (radius, light,
  deadline). Then "done" is computed, not reported. That reuses the existing pattern: a pure check
  in `command`, executed by `bot`.
- **Mapping lightness:** we receive block light per section in chunk data (docs/world-data-spec.md)
  but don't store it. Either keep it, or compute torch coverage ourselves from torch positions
  (light falls off by 1 per block). Prefer the server's data: it's ground truth.
- **Uneven ground:** the floor goes on the ground at the site's centre. A wall cell over a dip has
  nothing under it, and is skipped if no neighbour is solid either.

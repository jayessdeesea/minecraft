---
status: proposed
priority: P2
---

# The operator's building guide: what's adopted, what's left

## Why
The operator wrote a building style guide for AI builders in survival (2026-11-09). Its order of
precedence is the project's own: survival rules over layout rules, layout over style; function
first, and style only where function allows. This item keeps the rules worth building, measured
against what's built, each pointing at the item it belongs to.

## Already true
- **Light beats walls:** nothing inside at block light 0 (docs/military-doctrine.md § 14-15).
- **Spider overhang** on perimeter walls: the camp wall's lip (§ 15).
- **No spawns within 24 blocks of a player**, so the dangerous ring while home is 24-128 out: the
  night shift down the mine relies on it (§ 16).
- **Cleared sky** over the camp, inside the wall line (§ 15) - the guide wants it 32 blocks out.
- **Chunk-aligned cells** that can each be lost alone: backlog/cells.md.

## Gaps, by where they go
- **No dirt, grass, sand or gravel in walls** - endermen take them and open holes. The camp wall's
  dirt is replaced with stone (§ 15); the hasty hut is still built of dirt. backlog/durable-shelter.md.
- **Openings: fence gates or iron doors, never wooden doors** - zombies break wooden doors on hard
  difficulty, vindicators in raids. backlog/durable-shelter.md.
- **Clear sky 32 blocks out, not just inside the wall,** and a lit band beyond it extended ring by
  ring; then a trench 2 deep and 2 wide (or water) round the lit core, crossed only at gated
  bridges. backlog/cells.md (the moat).
- **Phase exit criteria**, all measurable, as foothold standards (command.Foothold) the general
  already reads: to leave the foothold, a bed, stone pickaxe, axe and sword, 16 food, 32 torches
  (or their coal and sticks), 2 stacks of cobblestone; to leave the base, iron tools and armour, a
  renewable food source inside the base, storage sorted by category, the moat closed with gated
  crossings, 3 stacks of torches. backlog/roadmap.md.
- **Outposts:** a single-chunk cell at each resource site, surface or mining level, each with
  walls, roof, full light, a table and furnace, an **emergency stash chest** (food, a weapon, a
  pickaxe, torches) and storage for its output; no work site more than about 200 blocks from a
  shelter. Retired by removing the stash and leaving the light. backlog/sites.md.
- **Respawn:** a bed only in a cell that meets every invariant, and checked before sleeping there,
  since the bed sets the respawn point; after a death, the nearest stash, and the kit recovered
  only within its 5-minute despawn. backlog/rest-plan.md.
- **No shared flammable roof or wall across cells; no wood within a block of lava or fire.**
  backlog/cells.md.
- **A cell holds what its daily work needs,** so work doesn't cross gates: the workshop and chest
  per site already lean this way (backlog/sites.md).

## Left for later
The architectural vocabulary - Alexander's light on two sides and a sheltered entrance, Kahn's
served and servant spaces, Wright's horizontals and low overhangs, Japanese module and veranda,
Ando's stone and slots of light underground, Palladio's symmetry, vernacular by biome. It's a
style guide for the engineer's blueprints (backlog/echelon-contracts.md) once there's a
deliberate base to style; avoid curves (Gaudí) and bare minimalism (Mies) at block scale.

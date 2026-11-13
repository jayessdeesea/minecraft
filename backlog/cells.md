---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camp layout, perimeter sectors); FM 3-90 (sectors of responsibility)
---

# Cells: the camp as a grid of chunk-aligned, fenced, lit compounds

## Why
The camp today is one ring of dirt wall eight blocks out from its centre (docs/military-doctrine.md
§ 15), plus each squad's hut. It doesn't grow, and outside the ring is dark ground that spawns
monsters right up to it. The operator expected something else: the ground divided along chunk
boundaries into cells, each levelled, fenced, gated and lit, with open sky over the moat between
them. Monsters don't spawn on the lit ground, and any that do burn at dawn. The camp grows by
adding cells outward.

## The main base (decided, operator 2026-11-12)
- **The keep is a raised platform**: chunks (-1,-1) to (0,0), x and z from -16 to 15, centred on
  the origin. Solid cobblestone from y=64 to 67, all four layers - cobblestone because endermen
  can't take it - standing on ground at y=63, so its floor is walked at feet y=68. Nothing over it:
  the sky above it is cleared.
- **Its edge is the wall.** A 4-block lip that zombies, skeletons and creepers can't climb; a
  fence along it against spiders. Ramps down to the fields, a gate at the top of each. Lit, like
  any cell, so nothing spawns on it.
- **The fields round it are at sea level**: ground top y=63, walked at feet 64, fenced and gated on
  the chunk boundaries. Farmland there needs water carried in - buckets and a two-by-two source -
  since levelled ground has none.
- **The height is the plan's, never measured.** Built for the keep's slab (docs/military-doctrine.md
  § 17, task `KEEP`). Left: the same for the fields round it, at sea level.
- **Before committing to the origin, survey it** from the heightmaps in the chunk data: the cut
  and fill it costs, reported to the general.
- **Built while the foothold at (-48, 44) stands as the temporary base**, and moved into once
  the keep has shelter and the stockpile. Order: the keep (shelter, chests, furnaces); one chunk
  of fields with water; then the ring of 12 chunks round the keep, from the ramps outward.
- **Scaffolding of bamboo** (operator, 2026-11-12: preferred over dirt for climbing). Left: bamboo
  scaffolding is climbed inside and sneaked down, not stood on, so it wants its own moves in the
  route planner, and bamboo from a jungle and string to make it.
- **The simulation's server answers a block placed at once** - live, its update comes a tick
  later - so a soldier's cobblestone stand-in for a step it built is written over the update, and
  a dirt step stays cobblestone in its model for good. Live it's corrected a tick on. Written only
  over an open cell, the slope wall, pit-fill and farm-in-a-hollow sims fail: they pass on dirt
  steps read as stone. Left: the sim's server answering a tick later, as the real one does, and
  those three made to pass on what's there.
- **Idle strips** (operator, 2026-11-13, later): a soldier whose strip has nothing within the
  keep's layer window - the hill's flank, a hollow - is free today; it could farm, or build a row
  of chests outside the dig site.
- **A soldier per chunk, long term** (operator, 2026-11-13): 16 soldiers, four squads, each its
  own chunk of the ground, all in parallel - a mountain flattened quickly - then each building up
  what its section's requirements say: the keep's slab, a field, a wall line. The general gives
  squads sections, the squad's tasking gives each soldier its chunk, the layer window keeps
  neighbours level. Left: more squads, and strips cut on chunk boundaries (today a share of 16 x 32
  splits four ways into 16 x 8).
- Cost: 4,096 cobblestone for the keep alone, against 385 in the stockpile today - so the mine's
  spoil is its supply.

## Design sketch
- **Cells are chunk-aligned.** Fences run along chunk boundaries, the unit the server loads and
  the chunk cache keys on. A cell is one chunk or a block of them. The cell at the camp's centre is
  the keep; the ring round it is the lodgment.
- **Lit with the fewest torches, on fence posts.** A torch on every few fence posts along the
  boundary, and one on a single fence post in the middle of each cell. That's the least light that
  keeps every floor cell above block light 0, the only light monsters spawn at.
- **The moat:** the lit, open ground between and round the cells, under clear sky, so anything
  that does spawn in the night burns at dawn. Nothing is built over it.
- **Level at the plan's height.** The fields at sea level, the keep raised on its platform (above):
  cut high ground, fill low ground. That makes the moat easy to keep open and the ground easy to walk.
- **Fenced and gated:** fence on each cell's edges, with a fence gate on each side that has a
  neighbouring cell. Spiders still climb fences; the keep's lip is its wall.
- **A purpose for each cell:** keep, barracks (beds), farm, pumpkins and melons, a pen (sheep for
  wool and beds, cows for food), tree farm, mine head. The general picks the purpose and the
  style; the cell is a blueprint the engineer lays out and inspects (backlog/echelon-contracts.md).
- **Ownership:** each squad owns cells. The general assigns them; the engineer turns each cell's
  next unmet standard into concrete commands - dig these, place those - for the squad leader to
  allocate. The soldiers never know there's a cell.
- **Checked from the world**, like the patrol base, and reported cell by cell in the dawn AAR.

## What it costs
- **Wood** is the bottleneck. 64 fence posts round a chunk, at 3 fences per 4 planks and 2 sticks,
  is about 30 logs a cell. The tree farm (§ 12) has to supply it.
- **Torches:** coal or charcoal.
- **Levelling** to the plan's heights on uneven ground can be thousands of blocks a chunk.
- At the main base it replaces the ring wall as FORTIFY's work. The foothold keeps its wall.

## Done when
- The keep and the 8 cells round it are lit, fenced, gated and level at that one height, each with a purpose,
  and the dawn AAR reports them cell by cell.
- No monster spawns inside the fence line through a night, measured over a multi-day garrison run.

## Notes
- **Direction (operator, 2026-11-11):** getting about a camp is a measure of whether its
  abstractions are right. Every base - foothold, outpost, main base - has a level floor at one
  height, of stone, not dirt, and an ideal route round it with no jumping; within about 90% of
  that is good, and it's iterated on continuously. A floor isn't left with potholes: it's
  inspected like the wall, and a crater (a creeper's) is filled and paved again. Sizes: a
  foothold minimal, while the squads arm and armour; an outpost one chunk, 16x16, fenced and gated
  on the chunk's boundary with a light moat round it; the main base the same, 2x2 chunks.
  Height: the main base's is set above; an outpost's sea level, y=63, unless there's a practical
  reason not to - a mountain, a valley, a village to keep. Measured so far: the walks from the camp's centre to its sites
  (`RouteEffort`, docs/military-doctrine.md § 17).
- **Direction (operator, 2026-11-12): the base is flattened to one height slightly above sea
  level**, set by the plan, not by the local terrain: the nearby chunks all flattened to it,
  fenced and gated, and lit. Built so far, as a step: the foothold at (-48, 44) is cleared of
  what was put on it, cobblestone included (§ 15's ground work), and paved - its soil replaced
  with stone inside the wall line, a standard of phase 3 (§ 17) - at its measured ground. Left:
  - The height fixed, not measured. Live, the camp at (-48, 44) is heaped with old cobblestone at
    y=70-72; soldiers stand on the heaps, and a restart measured the ground two blocks up (feet
    73 against 71), where the cobblestone heaps read as a paved floor.
  - The flattening: cut every column within the nearby chunks to the floor height and fill below
    it, from the camp's centre outward, at a cost of thousands of blocks a chunk on a hillside.
  - A pothole filled with dirt by the ground work and then paved over again; a fill of stone
    straight away would save the second trip.
- **The floor's inspection keeps every way down open.** Filling and paving a cell's potholes never
  fills a mine head or a stairwell in it: they're kept structures every dig and fill skips, taken
  from the one list the ground work already uses (`SoldierRunner.groundExcept` - the camp's kept
  structures, its kept mine, every staircase being cut, the columns round each mine head), not a
  list of the inspection's own. Left: a mine away from the camp is kept on disk only while it's the
  camp's - after a restart, another's cells aren't protected; a mine head in any cell has to be.
- **Expected (operator, 2026-11-08): the foothold gets the same treatment, smaller.** Fences, clear
  sky and flatter ground at the camp: clear sky so the zombies left at dawn burn, flat ground so
  the squads see across it. FORTIFY digs everything over the camp's ground inside the wall line -
  trees, dirt, stone, gravel, cobblestone put down - and then fills under it with the squad's own
  dirt (docs/military-doctrine.md § 15). Left: planks and other placed blocks over the camp; then
  a fence on the wall's line.
- Depends on the engineer and concrete commands (backlog/echelon-contracts.md), wood that grows back (backlog/renewables.md), and on torches made from charcoal or
  mined coal (backlog/mining.md).

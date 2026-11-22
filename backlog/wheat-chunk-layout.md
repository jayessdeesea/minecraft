---
status: proposed
priority: P2
depends-on: [renewables]
---

# Wheat chunk layout: an eval for the best structure

## Why
The wheat chunk's layout (`bot.farm.ChunkFarm`: one level at sea level, four water holes, a chest, field torches) was
designed, not measured. The operator thinks it's close (2026-11-21), and sees two changes that may pay: a **crafting
table** in the chunk, and **several levels - four - stacked, with a ladder between them**. Live, the farm's tender
walked to the camp's table and back to bake (field617: some 50 blocks for one loaf), and a 15x15 field one level deep
spreads a tender's crops 20 blocks across. Which layout wins is a number the squad simulation can give before anything is
built.

## Done when
- A farm eval in the squad simulation runs a tender on each candidate layout for some game days and scores it:
  **wheat a tender-hour**, the walk a wheat (blocks per crop brought in, per loaf baked, per load put away), crop-ticks a
  crop beside the squad simulation's rate for today's layout (`Farmer.SIM_TICKS_A_CROP`, 87.7 - the harvest's rate in
  `app.Ledger`), and the **build's cost** - blocks, water sources, ladders,
  torches, the table - amortised over a stated life.
- Candidates, at least: today's layout; today's with a table (and the chest) at its centre or by its gate; two and four
  levels stacked, each its own farmland, water and light, a ladder shaft between them.
- The winner is written into the chunk farm's template, and the eval kept as a regression - a layout change that scores
  worse fails it.

## Notes
- **Levels.** Each level is farmland, two cells of air for a crop and a body, and the next level's floor: four blocks a
  level. Under a floor there's no sky light, so each level's light is its torches - wheat grows at light 9 or more, and
  the dark under a roof spawns mobs on any block a torch doesn't reach; a level is lit to both. Each level needs its own
  water within four blocks of every cell - its holes, each a bucket's fill.
- **Ladders aren't a move yet** (`bot.nav.Pathfinder`: "no ladders"). The route planner needs a climb and a descent on
  a ladder, the battle and squad simulations to move a body on one, and the builder to place them - a capability before
  the layout can be tried. Bamboo scaffolding is the other way up (backlog/cells.md), climbed inside.
- **Growth's own rules**, from the pinned server's `CropBlock`: the chance a random tick grows a crop rises with
  hydrated farmland round it and falls when the same crop stands on both sides of a row - the layout of rows matters, and
  the eval's growth model should be the server's, read from the jar, not assumed.
- **A table in the chunk** moves the bake to the field. Where a baker bakes is data on its order now - the table its
  squad leader names (`DutyOrder.table`, written by `app.DutyBook`) - so a chunk's table is tried by standing one in the
  template and naming it, with no soldier rule to change. With the bake a batch at a time (`Subsistence.BAKE_BATCH`, 8),
  the table saves one walk a batch; the eval says whether that's worth a table.
- Shared with the tree chunk and the supply point: the same eval frame - a template, a tender, a score a soldier-hour
  less the build - is what the platoon leader's bids are costed by (backlog/renewables.md).

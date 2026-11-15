---
status: ready
priority: P1
depends-on: []
doctrine: FM 4-0 (supply point distribution; Class IV); ATP 3-34.40 (general engineering: site preparation before works)
---

# The keep as a campaign: depot first, a record that outlives the day

## Why
The keep's dig runs with no supply behind it (operator, 2026-11-14). Live:
- No wood within reach of the keep: no chest at its depot (two logs), two builders with no pickaxe
  and two with no shovel (half a log a handle), stone dug by hand twelve times as slow.
- No iron in any pack: the mine stopped at its planned depth, so no iron pickaxe, and the keep's
  emerald ore stands set aside for one.
- Excess thrown away for want of a chest; hundreds of cobblestone carried that the slab will want.
- The Class IV request (`PlatoonSergeant.classFour`) asks only for logs, only under four, and nothing asks
  for iron.
- Nothing remembers the campaign: each dawn the squads are put back on the keep by the operator's
  rule, and what was prepared, what's short and what phase it's in is worked out again or not at all.

## Done when
- **A campaign record** (`command`, pure; kept on disk under `minecraft.state-dir` as the wall, the
  farm and the mine are): its phase - DEPOT, DIG, SLAB - and each phase's standard measured from
  the world, not from reports. Shown to the squad leader and the general in their prompts, as the
  foothold is; never a model's memory.
- **The depot's standard**, sized from the dig: chests enough for the slab's cobblestone (the
  fill's cell count over a chest's 1,728) and one more for wood and tools; a crafting table and a
  furnace beside them; a starting stock of logs; every builder a pickaxe and a shovel.
- **Requests from the record**: what the campaign lacks goes up as the squad's Class IV line -
  chests, logs, iron for one iron pickaxe - and the general answers with WOOD or MINE as it answers
  the log request now.
- **The foreman's errands**: a builder with a full pack unloads at the depot; one short of a tool
  makes it at the depot's table or draws it; one with two pickaxes hands one to a builder with none
  (`CrossLevel.TOOLS_LOW` keeps too much with the holder). Tool-aware rectangles: stone to builders
  with pickaxes, soil and gravel to those with shovels.
- Simulation: a keep with no chest and trees 40 blocks off - two builders fetch logs, the chest and
  table go up at the depot, a builder with a full pack unloads, the rest never stop digging.
- Live: the depot's chests stand, nothing useful is thrown away, and every builder holds the tool
  for its block.

## Notes
- **The depot's row of chests is built** (docs/military-doctrine.md § 4, "The depot is a row of
  chests"): six cells, filled in turn. What's left of it:
  - Sized from the counts, not a fixed six: eight builders carry about 9,000 blocks, the dig had
    5,500 to go, and the slab wants about 4,000 cobblestone back - three chests of it.
  - Selective stowing: cobblestone, logs, ore, spare tools, the lumberjacks' axes and saplings go in
    (operator, 2026-11-14: they aren't needed all the time); dirt past a couple of stacks
    for scaffolding is thrown away - it would take a dozen chests and nothing wants it. `Depositor`
    stows only a full load of logs or cobblestone, so a pack full of dirt and odd blocks never
    empties.
  - Which chest is being filled isn't kept: after a restart the first is tried again, a failed trip
    for each full chest. It belongs in the campaign record.
  - Draws (logs, cobblestone for the slab) look in the chest being filled only, not along the row.
- **The wood party and the keep's grove are built** (docs/military-doctrine.md § 4, "The keep's
  wood party"). What's left: a lumberjack who draws its axe and saplings from the depot's chest and
  puts them back, rather than making an axe each time; the grove grown to 16-32 trees with more than
  one plot once the first is planted; the general's whole-squad WOOD answer to Class IV RED dropped
  for the keep's squads, now the foreman sends a party.
- The depot's spot exists (`SoldierRunner.siteWorkshop`, the keep's branch): stand, table, furnace
  and chest cells on the depot's surface.
- `Depositor` stows at the squad's stockpile and reports what the chest holds; `MetalPool` pools
  ore through it. The keep's depot should be a stockpile like the camp's, not a second mechanism.
- Iron's path is backlog/common-stockpile.md and backlog/mining.md: a tunnel to ore seen from the
  stairs, then the first two ingots to one iron pickaxe for the miner.

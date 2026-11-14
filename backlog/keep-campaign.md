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
- The Class IV request (`Keep.classFour`) asks only for logs, only under four, and nothing asks
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
- **Not a gate.** The dig goes on while the depot comes up: a wood party of two is detached at the
  campaign's start, as the foreman's errand in place of a rectangle (`command.LevelPlan`), to where
  the squads have seen trees; the rest dig.
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
- **One chest isn't a depot (field183, 2026-11-14).** The chest went up two minutes after the depot
  was sited with room for one, and filled with the first builder's load: 27 stacks, against 13 of
  cobblestone and 5 of dirt in one pack. Eight builders carry about 9,000 blocks and the dig has
  5,500 to go; the slab wants about 4,000 cobblestone back. So: a row of chests sized from those
  counts, cobblestone, logs, ore and spare tools stowed, and dirt past a couple of stacks for
  scaffolding thrown away - it would take a dozen chests and nothing wants it. `Depositor` and
  `Workshop` know one chest's cell; the stockpile becomes a list.
- The depot's spot exists (`SoldierRunner.siteWorkshop`, the keep's branch): stand, table, furnace
  and chest cells on the depot's surface. Nothing is built at it for want of logs.
- `Depositor` stows at the squad's stockpile and reports what the chest holds; `MetalPool` pools
  ore through it. The keep's depot should be a stockpile like the camp's, not a second mechanism.
- Iron's path is backlog/common-stockpile.md and backlog/mining.md: a tunnel to ore seen from the
  stairs, then the first two ingots to one iron pickaxe for the miner.

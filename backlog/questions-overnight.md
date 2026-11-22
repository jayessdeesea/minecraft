---
status: needs-decision
priority: P1
---

# Questions for the operator

## Why
Four behaviours were built on a default while the operator was away; each stands until the operator confirms it or
picks the other way. Two more are nearly settled by later decisions and want only a confirming word.

## What's left
Open - the default is built and in docs/; the other way is work:
- **Rations handed only to a squadmate within 8 blocks** (docs/military-doctrine.md, cross-levelling; field511:
  soldiers walked single rations across the farm to each other 75 times in six minutes). Default: 8 blocks, one at a
  time; buddy aid has no reach. Other way: a different reach.
- **A gallery broken into a cave** (backlog/mining.md; `stuck/cave-eli-field552`). Default: floored across at its own
  level with the cobblestone the miner carries. Other way: the branch ends at the cave and is reported.
- **A chest in a mine's stairs** (`Staircase.Cut.toCut`; field585: a chest in Bravo's stair head held half the
  platoon). Default: cut like any block, its contents spilt for the cutter or a scavenger. Other way: emptied first,
  its contents carried off before it's cut.
- **A farm's earthwork heap out of reach** (`Farmer.heapOf`; field579: a 7-high heap held up a pillar 27 blocks off
  for hours). Default: a heap at a time, nearest first, one whose top no stand reaches set aside a while. Other way:
  scaffold up to it (farmers carry no blocks for it now).

Nearly settled - confirm:
- **The night's shelter, a hut each or one shared roof**: backlog/authority-and-missions.md step 3 ("night shelter may
  be shared; building it is owned work") and step 5 (by night the squads go to their mine sites) answer it; beds
  (backlog/rest-plan.md) end the phantoms.
- **How far the camp's ground is flattened** (`app.CampGround`: rings of 4 out to 24 blocks, heaps dug down only, no
  fill past the wall line): backlog/camp-to-sea-level.md (a new camp at sea level, the old hill cut down) and
  backlog/authority-and-missions.md step 4 (camp upkeep is no longer a standing rule) make further out, or filling
  hollows, moot.

## Done when
Each line above is answered and removed - a changed default with its own item or its work done - and this file is
deleted.

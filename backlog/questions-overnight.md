---
status: open
priority: P1
---

# Questions for the operator

- **Sea level: which ground after the farm-and-grove site?** (backlog/soldier-order.md) Taken by default:
  once a site is flat, the next is the nearest box of the squads' farm and grove areas off its level, at
  the same level (y=62), soil. The camp inside its wall stays at its own height (the earlier rule: its
  floor paved, not cut), and wood ground isn't flattened. Should the camp itself be cut to sea level too,
  and the routes between the work areas? That changes whether the wall and its floor get rebuilt.

- **Merged: how a base grows** (backlog/sites.md). site-standards, maturity-model and roadmap are sections of sites now, each kept verbatim - sites kept as the home, being the one in progress and holding readiness. Confirm the home, or name another; the sections are to be reconciled into one design once you have.

- **Merged: the flatten** (backlog/flatten-campaign.md). keep-campaign and keep-pace are sections of flatten-campaign now, each kept verbatim - the flatten kept as the home, being the campaign the keep is one site of. Confirm, or name another.

- **Seed in a farm chest: 512 a chest, or the force's 1024?** (backlog/renewables.md, More farmland) You said a chunk farm's chest holds its excess seed, max 512. The force already caps seed at 1024 stocked across every chest (`ProductionLevels`), and a tender's seed past its pack goes into its farm's chest under that cap. Taken by default: the force-wide cap, one fact in one place. A per-chest 512 would add a second cap counted chest by chest; say if you want it.

- **Ground-work shares narrower than the crew** (camp and keep: `Tasking.allocate`, `Tasking.keep`; docs/military-doctrine.md
  § 17, the rules of a clearing). When fewer strips are left than hands, the hands wrap onto the same strips. Live (field494),
  the camp's last block, one cell 12 over its ground, was all three of Bravo's share, and they stood crowded a block from it.
  A hand to a strip, the rest free, was tried and backed out: in the squad simulation the spare hands flipped between the dig
  and the wall eight times, each task taken off unfinished, and the camp on a slope left its last block undug. It needs the
  drill to keep a spare hand on its next work, not to bounce it back as the strips change. Taken by default: the wrap stays.
  Should spare hands go free (and to what), or stay doubled up?

- **The chunk grid's coordinates** (backlog/renewables.md, More farmland). Your prototype's fence is on x = -81 and -65 and
  z = 96 and 112: z is on the chunk lines (z%16 == 0) and x one block off them (x%16 == 15). A block's coordinate is the
  floor of a position - what F3 shows - and the chunk grid is the server's own, chunk = floor(x / 16): chunk -1 is x
  -16..-1, chunk 0 is 0..15, and the lines x%16 == 0 (taken as floor mod) are ..., -16, 0, 16, ... - uniform through 0,
  no double-width fence there. (A double width at 0 comes only from truncating division, where -1/16 is 0.) The code lays
  the fence on x%16 == 0 and z%16 == 0 by floor mod (`ChunkFarm`). Taken by default: as built. Say if you'd rather the
  line be x%16 == 15, as your prototype's x - one block in, so a farm's fence is all in its own chunk.

- **A cover over a water hole** (the prototype). Each hole has a grass block over it at the farmland's level + 1, a torch
  on it: nothing falls in, the field is lit. A soldier can't place a block over water - there's no face to place against;
  the ray passes through water. How did you put them there? Taken by default: the holes left open, torches on the posts.

- **The split plan** (backlog/split-oversized-sources.md, "A plan for the operator to agree"). DeterministicSoldier into Moves, Fight and Survival behind a façade, Moves first; SoldierRunner's onJoined into a per-soldier tick with one pure TickPlan and four routine families; orderSquad into its six steps over SquadState and a SquadView. Each step a split with no behaviour change, the cap lowered behind it. Agree, or cut it otherwise?

- **A miner in a cave with no blocks** (docs/military-doctrine.md § 16, the rules of a mine). Live (field492-495), Eli, Bravo's miner at y=20, has stood at (-20, 20, 91) for hours: each tunnel's stand has no route across the cave, the mine leaves a branch "until it carries blocks to floor the cave", and he carries none, having taken no ore and no stone since. A soldier stuck in water already cuts a block from the bank. Should a miner cut its flooring from the cave's rock it stands beside, or climb out to draw cobblestone? Taken by default: neither, as the rules are yours. In field496 he lost 31 health in a day where he stood, the most of anyone,
  most of the force's health lost while reporting CANTCO (23 of 79) - the food report's hurt tally.

Answered 2026-11-19, each now with its item:
- **Food:** more farmland - trench farms off ponds before the bucket, chunk farms with four water holes after;
  and a food report first, to see why the soldiers eat so much (backlog/renewables.md, More farmland).

Answered 2026-11-18, each now with its item:
- **The depot** off the farm-and-grove ground's north edge (built, docs/military-doctrine.md § 17).
- **Arms before the flatten:** yes - food and arming come before flattening; all soldiers should have swords and
  armour (built: docs/echelons/platoon-leader/operator-rules.md).
- **Shooting creepers:** soldiers with bows shoot far-away enemies, creepers included (built:
  docs/military-doctrine.md).
- **A safe spawn:** beds (backlog/rest-plan.md). The flatten of the keep's own ground at the origin is a ways off yet.

Earlier answers (2026-11-17) are with their items: the grove in backlog/renewables.md, the night's ore and the
far faces in backlog/mining.md, endermen, the keep's site at dusk and the dark's errands in
backlog/engagement-doctrine.md, Eli's rescue in backlog/long-routes.md.

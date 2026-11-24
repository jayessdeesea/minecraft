---
status: proposed
priority: P1
doctrine: ATP 3-37.10 (base camps); FM 4-0, ATP 4-90 (supply points)
---

# Renewable resources: tree and food farms, and the supply point

## Why
Wheat and wood are farmed by duty on whole chunks (docs/military-doctrine.md, the farm by duty, wood by duty, the
camp's farm; `bot.farm.ChunkFarm`, `TreeChunk`, `FarmSite`), but the supply doesn't yet hold a force for days: the
farm's yield, not its duties, is what's short (field448: about 300 wheat a day baked and eaten as it came, the stores
at 0-3); a grove is felled as fast as it's planted (field570: 34 to 42 of 48 cells, then back to 34); producers break
grass and leaves for seed and saplings the chests already hold; and the platoon leader doesn't yet choose sites.

## What's left

### The layout, once
Every production chunk - wheat, trees, the supply point - is one chunk of the server's grid, flat at sea level, fenced
on its x%16==0 and z%16==0 lines (shared with the next chunk), a post at each corner and a gate on each line beside
each corner (eight a chunk), a torch on every fourth post. Wheat and tree chunks alternate on the camp's chunk grid,
inside the area of influence. The wheat chunk's inside is `ChunkFarm`'s, the tree chunk's `TreeChunk`'s; whether the
wheat layout is the best one is backlog/wheat-chunk-layout.md.

### Sites by bids, once (operator, 2026-11-20 and 2026-11-21)
One cycle for wheat chunks, tree chunks and the supply point; only the template and its stages differ.
- The platoon leader, short of food or wood, asks for bids on sites within 64 blocks of the camp (4 chunks). Each
  squad leader hands it to its soldiers, who cost the chunks nearest them from the world model (a walk only for a
  chunk beyond it): blocks to dig and fill to the level, seeds (216 a wheat chunk) or saplings, coal for torches, iron
  for a bucket on land, logs for the fence, the walk from the camp.
- Flat is the requirement (`ChunkFarm.MIN_CELLS` at one level), sea level the preference, costed as the earthwork to
  it, then the nearer. `ChunkFarm.site` exists and is unused till the bids choose one; `TreeChunk.site` is the tree
  chunk's.
- The platoon leader takes the cheapest bids in turn while the supply to Producing covers them and the need wants
  more; a fence waits for its logs and doesn't hold a site back from Producing. The winning squad gets the build order
  with the template, its bid the plan it's measured by.
- A wheat chunk on land has its four holes dug and filled by bucket; over shallow water the ground is built up round
  the water and the holes left as the water already there - no bucket, no ore. The platoon leader chooses among bids.
- **The soldier's planner**: its order carries the template; it compares the template with the world, knows its
  stage, and plans the cheapest sequence of digs, fills, floors, pours, tills, sowings and placements from what it
  carries - a pure planner over the world model, as the route planner is. The same planner run from a site's surveyed
  ground is the bid's cost.
- Which chunks a squad may work, several sites at once, and the measure of work against its estimate are
  backlog/authority-and-missions.md's (steps 3 and 7), as is a tree chunk never sited on a mission's chunk (step 3).

### Wheat
- A gate worked on a route - opened, walked through, shut - so the gates can stand shut against mobs.
- Holed farmland mended by the tender; the baker drawing from farm chests (it draws from the nearest chest known to
  hold wheat).
- A harvester out of seed draws it from the depot before it breaks grass for it.
- The stock levels raised for a mission by the platoon leader (`ProductionLevels.with`, called from nowhere yet).
- Before the bucket: the stream dammed to a block deep with dirt and farmed along its banks; trenches for a squad's
  FARM order's plot, which is sited round its water alone.
- A fenced site's Fenced stage brought forward when it loses cells to trampling.
- Post torches on a chunk farm's fence: a torch's cell is over a post's 1.5-high collision (`solidHere`, not
  `isSolid`), and a torch builder takes such a torch as placed. Nothing high left beside a fence - zombies came in
  off a tree (operator's prototype, 2026-11-19). A cover over each water hole is skipped (operator, 2026-11-20).
- Live bug (field506): farmers reported "no route from (-6, 63, 98) to within 0 of (-6, 61, 98)" 25 times in ten
  minutes at the camp's farm - a stand two under its farmland, likely `Farmer.trenchStand`'s; probe with
  `TerrainProbeIT` with the garrison stopped.
- Live bug (field495): Ivy jumped 123 times from (-12, 65, 71), a one-block step climbed and come back down while a
  crop at (-12, 63, 87) was started three times and never done - a routine walking it out and back.

### One place for a template's chest (operator, 2026-11-24)
Standardise where a farm's chest stands: its north-east corner, a little in from the gates so it isn't blocked - say
local (13, 3) - for the wheat farm (local (13, 13) today), the tree farm (local (1, 1)), and the animal farms ((13, 13)).
A built farm's old chest is emptied into the new one and taken up.

### A template's fixtures put up in one go (operator, 2026-11-24)
Watching a fence go up: a post, then the squad together again, then another post. The fixtures' routine gives the hands
back after each cell and the squad's formation order takes the soldier to its point before the next. Wanted: a soldier
putting a kind up keeps at it along the line - the next cell from where it stands - till it's out of that kind.

### Animals
- **Built (2026-11-24): the template and its mission** - `bot.farm.PastureChunk`, one layout for sheep, cows, pigs and
  chickens: the slab one under its level, dirt at its level, cobblestone under its fence, gates, torches and chest;
  fenced on the chunk's lines with a gate each side of each corner, put from across its line; a torch on every fourth
  post and nine inside; a chest for its feed. Ordered through the chat (`giveAnimalFarmMission`), three phases.
- **Left**: the animals - found, lured with their feed (a pig's has no kind yet) through a gate, bred, counted, culled
  for food, wool and leather; the base plan siting them; a built one reviewed and kept as a wheat farm is.

### Trees
- **Every template's ground, one rule** (operator, 2026-11-23): cobblestone one under the level, all of it; and
  cobblestone at the level too, except where it can't be - a wheat farm's field cells are dirt, but not under its fence
  or its torches; a tree farm's are dirt under its sixteen trees and nowhere else. Built: the slab under a wheat farm, a
  tree farm and a supply point; a supply point's level paved; a tree farm's level paved but for the dirt under its
  trees; a wheat farm's footings - under its fence, gates, field torches and chest - paved in cobblestone by its tenders,
  built or building (`ChunkFarm.footingsToPave`), and what stands in a fixture's place dug out (`ChunkFarm.obstructions`).
  Left: a gate standing a quarter turn round is found and put again (the world's view can't say which way one faces
  yet; new ones are put from across their line); a mineshaft's head given its slab and its level paved round the shaft; the same two lists for the tree farm, the
  supply point and the mineshaft's head - the conformance pass below.
- **Birch first** (operator, 2026-11-23: wood that grows predictably): a planter takes birch if it carries any, then
  spruce, then any other (docs/military-doctrine.md § 12). Left: a tree template never planted with oak or acacia
  while a birch or spruce sapling is in the squad or the stores.
- **The rotation** (operator, 2026-11-14 and 2026-11-21): one pass fells every tree of an area whole and replants
  each stump at once, under its own decaying leaves (`TreeFarm.plantable` lets them be); then back a couple of minutes
  on - most leaves decayed, inside the 5 minutes a drop lies - to sweep up the saplings and sticks. Dropped saplings
  are taken before any leaf is broken for one. Several groves, so one grows while another is cut.
- **Saplings supplied** (field630: "no sapling, and no leaves within 32 blocks" 117 times in a run while soldiers off
  the grove carried 6 and 11): saplings in the kit's report; a forester with none draws from a squadmate's spares, the
  tree chunk's chest or the depot; a squad with no saplings and no leaves in reach isn't put on planting, its
  resources line saying so.
- **The tree chunk's chest** at local (1, 1): two axes, a reserve of 64 saplings, spare torches; saplings past the
  tender's pack go in it, to 256.
- **Its fence** (stage 5, Fenced, when wood isn't short), with the post torches.
- **Remembered**: the platoon leader keeps every tree site and its stage across restarts and sends a squad to fell and
  replant one that's producing.
- **The grove's turn**: the squad on the grove once it's planted, the wild forest only when the force is short;
  whether woodcutters fell a grove's own trees faster than they're planted.
- **The keep's grove**: a second grove sited once the first is planted, so the wood standard's ceiling grows with the
  groves; the keep's grove planted only by its building crew's wood party (`PlatoonSergeant.woodParty`), none while
  the keep has nothing to build. A grove sited for a camp the force has left is given up for the camp's nearest
  (field630: foresters walked 200 to 330 blocks to one at (-30, -73)).
- 16 to 32 trees growing at once, so wood is never short (operator, 2026-11-14).
- Who works a grove, and the standing rule that puts a spare squad on planting (`DayGuardrails`), are
  backlog/authority-and-missions.md's (steps 4 and 5).

### The supply point: a chunk of stores (operator, 2026-11-21)
The camp's stores on a chunk of their own, on the layout above. The name is the Army's for where supplies are held
and issued (FM 4-0, ATP 4-90); the stockpile is what's held (`ProductionLevels`). Today "supply point" in the code is
one chest (`Foothold.dump`).
- **Settled for the first one** (operator, 2026-11-22): ordered by the platoon leader as a farm's mission is, not by
  bids - at chunk (1, 4), south of the wheat farm; its ground cobblestone at sea level; the mission done when it's
  fenced and lit, its workshop in - a table and four furnaces - and the food and Class IV rows up, a later row a
  follow-on order; what has no row of its own (diamonds, minerals, buckets, string, beds) in the Classes II and IX row;
  the old chests emptied into it by a haul order of its own, after.
- **Built** (docs/echelons/platoon-leader/decisions.md, the operator's farm mission): its mission - flatten, floor,
  template - its chat command, its layout, and the routine that makes and places a template's fixtures from a layout
  (`bot.supply.Fixtures`). **Left**: loads put in by class, a row a class (`StoresChunk.Row.of`), and drawn from there -
  today its chests are only chests; the haul order that empties the old chests into it; its later rows, a follow-on
  order; and the camp's stockpile and workshop pointed at it.
- **Chests by class of supply**: rows of double chests along x, six to a half-row, a cross-aisle at local x=8, two
  aisles between rows, each chest reached from both sides - local z=3 Class I (food), z=6 Class IV (cobblestone, dirt,
  logs, planks), z=9 Classes II and IX (tools, armour, ore, ingots), z=12 Classes III and V (coal, torches, arrows);
  24 double chests in all. Nothing solid over a chest (a torch is fine).
- **Seeds and saplings stay with their farms**, and come here only while there's no farm of theirs to hold them.
- **Not filled at first**: a row goes up when its class's stock nears what its chests hold - food and Class IV first.
- **Ground torches** at local x=2, 7, 12 on z=2, 7, 11; spare torches in its Class III chest.
- **A workshop** by a gate along local z=14: a crafting table and a bank of furnaces.
- **Stages, the fence before the stores**: (1) ground, (2) fenced and lit, (3) stocked - the first row up, (4) grown, a
  row a class as it needs. A creeper among the chests is the loss that sends a site to RECONSTITUTE (backlog/sites.md).
- **Sited** by the bids above, costed by its trips as much as its ground - the farms, groves and mine head bring, the
  camp and keep draw: the middle of the ring of farm and tree chunks, rougher ground there before flatter far out.
- **The ad hoc chests consolidated into it** once it's stocked: the workshop's row, a keep's depot, chests found full
  - looked through from the world model's known chests, their contents hauled by class, a chest emptied before it's
  dug up; a haul is a task measured by what arrived against what was taken.
- **Moved, as any template can be** (wheat chunk, tree chunk, supply point): the new one built through its stages
  first, then the old one run down - its crop or trees harvested and not replanted, its chest's contents hauled, its
  fixtures taken up, its ground given back to the flatten. Ordered like a build, by bids, the run-down costed in.

### Upkeep left (operator, 2026-11-22)
Built: the review that reopens a built template whose structure has gone (docs/echelons/platoon-leader/decisions.md).
Left: a wheat farm's fence, gates and torches gone after its mission is done are its tender's alone, and only with a
squad on its farm - they should count as drift, once the template's own done-measure counts them; a mineshaft's head;
and each repair kept in the chunk's history.

### A site made to match its template, first (operator, 2026-11-23)
"When a soldier is asked to work the field, maybe the first step is to fix the construction site so it matches the
template: remove the extra chests, get rid of the cobblestone on the fences, hoe the field." One pass, not a rule a
fault: every cell of the template's box compared with what its layout says is there, and the differences - what's
extra, what's missing, what's the wrong block - worked by whoever is sent to the site, before its crop.
- **Why it was needed, each time** (operator, 2026-11-23: when we have to run the anti-entropy we should know why,
  and see if there is an underlying defect): each difference found is logged with what stood there, what the layout
  has, and what last changed that cell where the force did it (the placement and digging audits name the routine and
  the soldier). Differences are counted by cause; one that comes back after being put right, or that the force's own
  routine made - a depot's chest sited in a field, a step left on a fence line, a torch a block low - is a defect to fix
  in that routine. The repair is the net, not the cure (docs/loop.md, "Anti-entropy, and why it ran").
- **Every template, one mechanism** (operator, 2026-11-23: this should apply to all templates): the wheat farm, the tree
  farm, the supply point and the mineshaft's head each say, cell by cell over their box and the slab under it, what
  stands there (`ChunkFarm`, `TreeChunk`, `StoresChunk`, `MineHead` - one "what the layout has at this cell" each), and
  one comparison yields the differences. A soldier sent to work a built site runs it first, and the review runs the same
  comparison, so "DONE" means matches the template. Next to build after the mineshaft's own shaft.
- **What isn't a violation** (operator): on a tree farm a sapling at a trunk, or the tree it has grown into, leaves
  and all; on a wheat farm tilled ground, bare or sown or with wheat at any growth. **What is**: ground at a field's
  cell not tilled; ground missing; a block that isn't the layout's - a chest but the farm's own, cobblestone where a
  fence post goes or on top of one, a step left standing; a torch where the layout has none or a block under its level;
  a post, a gate, a torch or the chest missing.
- Wanted of the world model first: what a block is, cell by cell - a fence told from any other solid block
  (`WorldView` says only "solid here"); today cobblestone on the fence's line counts as its post.
- Built towards it: missing ground laid and what stands in a wheat template's way dug by its squad (`TemplateGround`);
  a torch where ground goes taken up (`Builder`); a tree template's ground laid in dirt on its slab; the review
  that reopens a built template (`FarmMissions.review`).

### A template cleared of what isn't its own: what's left (operator, 2026-11-22)
Built: a wheat template's squad digs the blocks in its way, a depot's chest among them
(docs/echelons/platoon-leader/decisions.md) - the chest's contents fall out for the digger; emptying it first, as a
flatten's salvage does, is left. Left: torches where the layout has none - five fence posts running
with one each on the wheat farm's south line, one on a stray block over a field torch's cell - and blocks more than three
over a template's ground; neither is counted, so neither is anyone's. The review counts what's missing still, not
what's extra: a finished farm that gains a stray block is reopened only once that block makes its stage fall.

### Gates kept shut: what's left (operator, 2026-11-22)
Built: the habit, in every soldier's walk (docs/echelons/soldier/capabilities.md). Left: its eval against a mob - a
squad working a fenced chunk through a night, a zombie outside the fence never getting in; and the simulated server
letting a body through a shut gate, which the real one doesn't (backlog/simulator-fidelity.md).

### Sugar cane (operator, 2026-11-24: a template, cobblestone one under its level, the most efficient layout, lit)
- **Layout** (`bot.farm.CaneChunk`, to write): cane grows on dirt beside water, so a trench serves the row each side of
  it - by local x, cane | water | cane, five times over 1..15: water at x=2, 5, 8, 11, 14, cane on the ten columns
  between, 150 cells less its chest's (backlog's one place, local (13, 3)); the slab one under its level as every
  template has, dirt at its level under the cane, the trench cut to its level with the slab its bed, cobblestone under
  its fence; lit from its fence - a torch on every fourth post - since no cell inside is free for one and none is
  eight from the line.
- **Its mission**: flatten, floor, then its template - ground laid as an animal farm's is, trenches filled from
  buckets (every other cell a source, the rest fill themselves), cane planted, its fence, gates and torches.
- **Wanted first**: cane as a kind the force knows (`ItemClass`), found where it grows and brought back; water carried
  (`WATER_BUCKET` is a kind; nobody fills a trench yet); a harvest that cuts from the second block up and leaves the foot.

### Other farms
- Nether wart and animal pens - a chicken pen first: feathers are the one part of an arrow with no other source but
  skeletons (backlog/load-plans.md).
- The platoon leader's choice follows the terrain (table in Notes).

## Done when
- A live day with logs and bread both under their minimum puts one squad on each, every producer working only its
  duty's share, and both stocks rising to their most.
- A live run sees a grove grow, cut in one pass, and planted again from its own drops.
- A squad-simulation scenario: bids from two squads, the cheapest site built to Producing from its template.
- A scenario with no water nearby never yields a wheat order.
- The supply point stands at stage 3 live, the ad hoc chests emptied into it.

## Notes
- What the ground supports:

| Farm | Needs | Not possible where |
|---|---|---|
| Tree farm | Saplings, dirt or grass, light 9+ | Nether: fungi need nylium and bone meal, and their stems make no charcoal |
| Wheat | Seeds, a hoe, farmland within 4 blocks of water, light 9+ | Nether: water evaporates |
| Nether wart | Soul sand; wart found only in fortresses | Overworld: grown there, but has to be brought back |
| Animal pen | Two of a kind, their breeding food, a fenced pen | Where no passive mobs spawn: deserts, much of the Nether |

- Growth is random ticks: live verification needs patience, or a test server with a higher `randomTickSpeed`.
- Measure before adding farms: a farm's ripe cells an hour, and where the food goes (`[Food]`, `app.FoodLedger`) -
  if healing dominates, fewer falls and fights save more than more farmland.

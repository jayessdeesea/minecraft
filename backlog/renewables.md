---
status: proposed
priority: P1
depends-on: [patrol-base, subsistence]
doctrine: ATP 3-37.10 (base camps)
---

# Renewable resources: tree and food farms

## Why
Gathering strips what's near and moves on. A force that holds ground for days needs supply that
grows back where it stands. Which kind is possible depends entirely on the terrain. That's a real
planning decision for the general: read the survey, pick what the ground supports.

## Production duties (operator, 2026-11-18)
The platoon leader puts a squad or two at the camp on renewables by need (docs/echelons/tenets.md,
stock levels drive production); the squad leader gives each soldier a duty, an area and a chest; the
soldier does only the how.
- **Wood: woodcutters and a planter.** Built for the wild forest (docs/military-doctrine.md § 12, wood
  by duty). Left: the grove's turn - the squad on the grove once it's planted, the wild forest only
  when the force is short; the planter drawing saplings from the depot before it breaks leaves; the
  squad leader (DrillSquadLeader) giving the duties as its plans, so they show in its view, rather
  than the runner deriving them from the objective.
- **Wheat and bread: harvesters and a baker.** Built (docs/military-doctrine.md § 12, the farm by
  duty). Left: a harvester drawing seed from the depot when it's out, before it breaks grass for
  it.
- **Stock levels**: built (`command.ProductionLevels`, shown to the platoon leader, logged as
  `[Stores]`; a producing squad kept on it to its most, docs/echelons/platoon-leader/operator-rules.md).
  Left: the levels raised for a mission (`ProductionLevels.with`) by the platoon leader.
- **Seen live (field448):** two farm squads harvested about 300 wheat in a day, and it was baked and
  eaten as it came - the harvesters under their basic load bake for themselves, the stockpile held
  0-3 wheat at each of the baker's four draws, and rations were cross-levelled one at a time. The
  farm's yield, not its duties, is what's short: more ground and denser farms.

Done when: a live day with logs and bread both under their minimum puts one squad on each, every
producer working only its duty's share, and both stocks rising to their most.

## Where they go: production chunks (operator, 2026-11-17)
The long-term shape: whole chunks set aside at sea level for production - one or more for wheat,
one or more for trees - each levelled, fenced round, and entered by gates, so the work is laid
out on a grid the force can plan by the chunk (yield a chunk a day, hands a chunk) rather than
fitted round whatever ground is near the camp. Until then: the camp's farm by its water, and the
keep's grove, cleared for if the ground near the depot is wooded.

Built so far: the keep's grove sited round its depot, any depth under it, and its ground cleared by
the wood party when none qualifies; trees felled whole and replanted (docs/military-doctrine.md § 4); a
farm's, a grove's and a wood's ground whole chunks on the server's grid (`command.WorkArea`).
Left: levelling production chunks; gates worked on a route (see More farmland below).

## The keep's grove (operator, 2026-11-17)
No site near the depot holds 16 cells: several groves, each what the ground holds, and the search
for them travels farther. Built: a grove of 8 cells or more, sought out to 96 blocks
(`SoldierRunner.GROVE_MIN_CELLS`, `GROVE_RINGS`). Left: a second grove sited once the first is
planted - the keep holds one (`keepGrove`).

## Doctrine
**Base camps** (ATP 3-37.10): a camp that is held longer develops its own sustainment instead of
living off resupply runs. What gets built depends on the site.

## Mapping to Minecraft
What the ground supports:

| Farm | Needs | Not possible where |
|---|---|---|
| Tree farm | Saplings (they drop from leaves when a tree is cut), dirt or grass, light 9+ | Nether: crimson and warped fungi need nylium and bone meal, and their stems make no charcoal |
| Wheat | Seeds (from breaking grass), a wooden hoe (2 planks and 2 sticks), farmland within 4 blocks of water, light 9+ | Nether: water evaporates (`water_evaporates` in its dimension type) |
| Nether wart | Soul sand. Wart is found only in fortresses | Overworld: it can be grown there, but has to be brought back |
| Animal pen | Two of a kind plus their breeding food, and a fenced pen | Wherever no passive mobs spawn: deserts, much of the Nether |

The survey (§ 12) reports what's nearby. The general picks the farm the ground supports, the squad
builds it beside the patrol base (docs/military-doctrine.md section 14), and the base's light already meets the
light requirement.

## Done when
Built: wheat (docs/military-doctrine.md § 12, FARM) and a tree farm planted from saplings it breaks
leaves for (§ 12, TREE_FARM). What's left:
- **The grove's harvest cycle (operator, 2026-11-14).** A pass that cuts every log in the grove, a
  wait for the leaves to decay (leaves with no log within six blocks drop their saplings over the
  next random ticks - a minute or two), then a pass that picks the dropped saplings up off the ground
  and plants them back on the grid. The planter's forester breaks leaves for a sapling today, and
  outranks the scavenger that would pick dropped ones up: it should take the ones lying in the grove
  first. Large groves, or several, so one is grown while another is cut and nobody waits on a crop.
  Done when: a live run sees the keep's grove grow, cut in one pass, and planted again from its own
  drops.
- **A flat grove at sea level (operator, 2026-11-14).** The grove's ground flattened before it's
  planted, as the keep's is - the foreman's level-by-level plan over a second site - at sea level
  where the depot's ground allows. Today the grove is sited on the flattest ground near the depot as
  it stands.
- 16 to 32 trees growing at once, so wood is never short (operator, 2026-11-14): one grove of
  radius 8 on the four-block grid is 16.
- Nether wart and animal pens - a chicken pen first: feathers are the one part of an arrow with no other
  source but skeletons, and the bow joins every load plan at BUILDUP (backlog/load-plans.md).
- The general's choice follows the terrain. A scenario test with no water nearby never yields a
  wheat order.

## Notes
- Saplings are a side product of the tree gathering we already do. Picking them up is the cheap
  first slice.
- Growth is slow and random (random ticks), so live verification needs patience, or a test
  server with a higher `randomTickSpeed`.

## More farmland: the farm by phase (operator, 2026-11-19)
Overnight (field480-489) the farm fed about a third of the force: some 85 harvests a game day, about 28 loaves,
stores at 0 bread and wheat. The answer is more farmland, gradually flattened to sea level, and denser farms as
the force can build them:
- **Airhead:** ad hoc farms round the ponds by the camp, as now.
- **Before the bucket:** trenches off a pond, built (docs/military-doctrine.md, the camp's farm). Left: the
  stream dammed to a block deep with dirt and farmed along its banks; trenches for a squad's FARM order's plot,
  which is sited round its water alone.
- **With a bucket (three iron, the buildup):** built - the bucket, its carrying (`bot.supply.WaterCarrier`), and the camp's
  first chunk farm sited, its holes dug and filled, its fence, gates and chest put up, its ground tilled
  (docs/military-doctrine.md, the camp's farm). Left: a gate worked on a route - opened, walked through, shut - so the
  gates can stand shut against mobs; its chunk flattened to one level - sea level in time; the next chunk farm
  once one is tended; the design as the operator gave it: chunk farms, built one at a time. Each a chunk, flat at sea level,
  fenced on its x%16==0 and z%16==0 lines (shared with the next farm), gates beside the corners; tilled but for
  four water holes at local (4, 4), (4, 12), (12, 4), (12, 12) - each hydrates the 9x9 round it - in the farmland's
  own layer (water a block below hydrates nothing); and a chest at local (13, 13) for its seed (to 512) and wheat.
  In steady state one soldier tends one farm - harvesting, replanting and mending holed ground as it goes - so the
  farm stays within a player's simulation distance and grows. Built: the tender's duty order, its chunk its ground and
  its chest its dump (`FarmDuties.Tended`). Left: its seed (to 512) and wheat put in its chest; holed ground mended; the
  baker drawing from farm chests.
The first food report (field490, a game day): the force burnt 176 hunger - jumping 87, healing 45, sprinting 37,
fighting 7 - about 16 loaves at 11 hunger and saturation a loaf, against 15 made from 38 harvests and 10 eaten.
Jumping is half of it: some 7,000 jumps among fourteen soldiers, about one every two seconds walking. Halving
it would put the farm in surplus as it is. Left: where those jumps are - real step-ups on rough ground, or
soldiers jumping in place - from the report's jumped-most cells, and the fix for whichever it is. field491's day: 85 of
235 burnt jumping, the hotspots on the keep's rough ground (x -44..-57, z 38..50, y 72-77), one soldier 51 times
from one cell while cutting logs there; each cell now says the order and report it was jumped from at, to tell a
route's step-ups from a routine's jumps in place.
Measure first: a farm's ripe cells an hour before deciding how many the force needs (a solid block of one crop
may grow slower than rows); and where the food goes (`[Food]`, `app.FoodLedger`) - if healing dominates, fewer
falls and fights save more than more farms.

## The wild forest near the camp is cut out (field470)
A full day on wood duty felled 12 trees: the woodcutters' strips held no trees ("no LOG within 144 blocks of
the objective", Fran, again and again), Dana alone filled a load of 16, and the stockpile stayed at 0 logs.
A wood whose every strip its woodcutters found cut out is now dropped from the sightings for a day
(`WoodSightings.spent`), so the next squad goes to the nearest other seen. Left: the survey looking farther
once every wood it sees is spent - it shows six stands within 64 blocks (the nearest, then the biggest), and
with all of them cut there's nowhere to send anyone. Saplings are 0, so the grove (the lasting answer) waits on the planter's drops.


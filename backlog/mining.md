---
status: proposed
priority: P1
depends-on: []
doctrine: ATP 3-34.81 (engineer reconnaissance); FM 4-0 (Class IV)
---

# Mining: ore at depth, and bases on the way down



## The mineshaft's head: what's left of the operator's pattern (P1, 2026-11-24)
Built: its layout (`bot.farm.MineHead` - two rows of double chests by kind, `MineHead.Bank`; its mouth ringed, gated
and lit; a table and two furnaces by its north fence).
- **A load goes to its kind's chest**: `Bank.of` names it; the depositor's choice of chest doesn't read it yet
  (backlog/depot-placement.md, the same choice by row).
- **A gate across the stair at its first landing**, so what spawns below stays below.
- **Its stairs kept** - anti-entropy, as a template's: each review, every step has its floor, its headroom and its
  walls where the well's layout has them; no water or lava beside one; its torches lit every so many steps; its ring
  and gates stand. What's found is repaired by its squad and said with what changed it (`[Dig]`, `[Place]`).
- **A vein met in the stairs is followed** as one met in a tunnel is: mined out along its run, the stair's floor and
  walls put back behind it (miners keep stone for that), then the cut carried on.

## No diamonds yet: the shafts are shallow (operator, 2026-11-24)
No diamond ore has been seen or dug in any run's log. A well's first landing is y=12 and it is deepened twelve at a
time only as its levels dry (`Staircase.deeper`, to `DEEPEST` -58); the deepest a soldier stood that day was about
y=-8, and diamonds are thick only under y=-40. Not a tool's refusal: iron pickaxes are on the kit ladder and the
stores hold iron.
- A mineshaft mission may be given its depth, or a rule sends one squad's well straight to y=-52 once the force has
  iron pickaxes; its levels worked from the bottom up.

## The mineshaft's stair, as it stands and as it should (operator, 2026-11-24)
The operator: the staircase is cut wrong - not the pattern agreed - and doesn't go to a useful depth; it should follow
the same pattern all the way down to the bottom of the world unless the digger hits water or lava, and then that is a
conversation. A snapshot of the well at chunk (0, 2), x 5..10, z 37..42, y=63 down to 40: no four by four with its
pillar and ring - a flight three wide at y=61..63, then open hollows six across at y=54..60 and y=44..48 where older
digs and caves cross it, single cells at y=49..53 and y=40..43.
- Built: a mineshaft's objective is the bottom (`Staircase.DEEPEST`; its landing y=-48), not y=16.
- **Its well made to its pattern, top to bottom**: each level of `Staircase`'s well has its twelve-cell ring open, its
  pillar and its walls standing, its step's floor; what is open that shouldn't be is filled, what is rock that
  shouldn't be is cut - the stair's upkeep, and the first thing a squad given the shaft does. A well cut before keeps
  the spiral it was cut to (`Staircase`, "mines cut before"): the one at (0, 2) is re-sited on its own pattern.
- **Water or lava met**: the cut stops there, the squad comes up, and it is said to the operator with where and what -
  no well sited elsewhere, no way round dug (today `Miner.hazard` leads to another site).
- Eval: a mineshaft mission on rock with a cave across its well at one level - the well cut to its pattern through
  it, floored and walled, to its landing; and one with lava at a level - stopped, said, the squad at its head.

## A mining shift: drawn for, cached at its floor, closed out (operator, 2026-11-24)
The operator: supplies go down the mine, and having them is a prerequisite - as it is when tending the sheep. Torches,
pickaxes, shovels; in time buckets, gravel, specialised pickaxes. The excess is dropped in a chest at the floor being
worked; the miners mine, coming back to it with ore worth keeping and for more torches; and when the platoon leader
says stop, they go back to that chest, take what is useful, get rid of the cobblestone and dirt, come up, and put the
goods in the stores. And the digger lights as it goes: nobody is sent to light the way.

This is a task with an end (backlog/chunk-tasks.md): the same phases a tend has, named for a mine.

- **DRAW - the prerequisite, before anyone goes down.** A mine's load is a list by kind and count for a shift: torches,
  pickaxes (one in hand, one spare), a shovel, food; later a water bucket, gravel or other fill, a pickaxe of the tier
  the level's ore needs. Drawn at the head's rows (`MineHead.Bank`), made at its workshop where the stores are short,
  and what can't be had said up as a shortfall before the shift starts - not found out at the face
  (backlog/kit-readiness.md). The load is one table, by task: a shepherd's is the same table with other rows.
- **CACHE - a chest at the floor.** At the foot of the stair on the level being worked, in the pattern's place (a recess
  off the landing): the squad's first act on reaching its floor is to put it up and put down what it carries beyond a
  working load - the spare tools, the torches past a stack, the fill. A level's chest is the level's: named by its
  mine and level, found again by the next shift.
- **WORK - the face, in trips.** The digger sets a torch as part of the cut, at the pattern's places (a stair's pillar
  every so many steps, a gallery's wall every so many cells), from the torches it carries. A pack filling, or torches
  or a pickaxe running out, is a trip to the floor's chest and back, not to the surface: ore and what is worth keeping
  in, torches and a tool out. Cobblestone and dirt past what the shift's own steps and fills need are not carried.
- **CLOSE - on the platoon leader's word.** Mining stops when it is told to (a squad works till it is told
  differently): each miner back to the floor's chest; what is useful taken up - ore, ingots, flint, the tools with
  wear left; cobblestone and dirt past the stores' cap thrown away there or left for the level's fills; up the stair;
  the goods put in their rows at the head; and DONE said with what was brought up, what was used, and what the level
  still holds.

Built on what is there: the head's banks by kind (`MineHead.Bank.of`), the stores' caps on cobblestone and dirt
(`SoldierErrands.capped`, `Pack`), the workshop at the head. Not there: a load by task; a chest at a level; a torch in
`Miner` at all; a stop that is an order rather than dusk; a close-out.

Evals:
- A squad ordered to mine with no torches in the stores and logs and coal to make them: torches made and drawn before
  the first step down; with nothing to make them of: CANTCO said at the head, nobody down.
- A level reached: its chest up at the landing's recess, spares in it, each miner down to a working load.
- 64 cells of gallery cut: lit to the pattern, no cell of it dark enough to spawn on, no soldier ever given "light".
- A pack full at the face: the trip is to the floor's chest and back, its length that of the gallery, not the stair.
- "Stop" given mid-shift: every miner up within the stair's climb and the gallery's walk, ore in the head's ore row,
  no cobblestone past the cap put in, the level's chest holding what was left for it, DONE said with the counts.
- A miner cut off from the floor's chest at "stop": said, and the squad's recovery is its own task, not silence.

Doctrine: a load drawn and checked before the line of departure (pre-combat checks and inspections, ATP 3-21.8); a
cache forward of the base for what the element can't carry at work (the patrol's cache); consolidation and
reorganisation on the word to cease work - account for people and kit, redistribute, report.

Decided by this: the mine keeps its levels ("once we get to our floor") - a stair to its pattern, and floors off it.
Open: how far a level's galleries run from its stair before another stair is worth cutting (today 192 blocks of main
tunnel; Kate and Liam were 120 to 180 from the head).

## A mine's ground, covered whole, and its ledger (operator, 2026-11-24)
The operator: mines are on alternate chunks, so the pattern should get all the blocks; and the soldiers say when a
floor is complete - both sides - so the platoon leader marks it done and orders the next day's work from it.

**A mine's ground is its cluster.** `command.BasePlan` puts a mineshaft where chunk x and z are both even, one a
two-by-two cluster: so each mine owns the 32 by 32 columns of its cluster, from its floor's top level down, and the
mines' grounds tile the base with no column shared and none left out. A gallery never leaves its mine's ground (today
a main tunnel runs up to 192 blocks under other clusters).

**A floor, so that every block of its ground is seen** - cut or bared on a face, so no ore is passed:
- The stair's landing is at the well, in the cluster's north-west chunk. A floor is two high: y and y + 1.
- The **main tunnel** runs east-west through the landing's row, from the ground's west edge to its east: 32 long.
  West of the stair is the floor's west side, east its east side.
- **Branches** run north and south off it to the ground's edges, every second column: a branch, one column of rock,
  a branch. Every block of the two layers is then a branch's cell or a branch's wall.
- **Floors every three**: y, y + 1 cut, y + 2 left - and the branches of alternate floors one column over. The layer
  left is the ceiling of the floor under it over that floor's branches, and the floor of the one over it under its
  branches: between them every column of it.
- So every block of the ground from the top floor to the bottom is seen, for a third of it cut: about 1,060 cells a
  floor (32 by 2 of main tunnel, 16 branches of 31 by 2), 530 a side.
- The cheaper pattern, today's spacing kept inside the ground (a branch every third column, floors every fourth):
  about 750 cells a floor, a sixth of the ground cut, the two layers cut all seen and a third of the layers between -
  a vein of two or more is still met, a single block can be passed. To choose by depth: the every-block pattern where
  diamonds are, the cheaper above.
- The top floor is no nearer the surface than the templates' footings and the caves under them allow (a floor under
  a template's slab is how three soldiers came up through one): a depth to settle, and no gallery above it.
- Water or lava met in a branch: that branch stops there, is walled, and is said with its place; the side goes on.

**The ledger: a mine's floors, by side.** One register the platoon leader keeps (`command`'s registers, saved with the
missions), a row a mine's floor and side:
- its state - NOT BEGUN, STAIR DOWN TO IT, CACHED (its chest up), BEING CUT (with the next branch to cut: the checkpoint,
  said at each branch's end), COMPLETE, or STOPPED AT (a place, and water or lava);
- what it yielded, by kind, counted at each trip to the floor's chest and at the close;
- who said it and when.
A soldier's report of a branch done and of a side complete is a report's type with its mine, floor and side - never a
sentence read back. The squad leader passes it up; the platoon leader marks it, and "complete" is checked once by the
next soldier through (a side said complete with a branch uncut is reopened: check the outcome of what is ordered).
Each morning the platoon leader orders from the ledger: the shallowest floor not complete, a side to a pair of
miners, the stair cut on to the next floor when both sides of the one above are complete or stopped. A shift ended by
"stop" leaves its checkpoint; the next takes it up at that branch.

Evals: a mine's ground cut to the pattern in the simulation - every block of it a cell cut or the face of one; no
cell cut outside the ground; a shift stopped at branch 5 of a side - the next begins at branch 5; a side said complete
with a branch left - reopened; lava in a branch - that branch STOPPED AT, the side complete without it, the operator
told; four squads, two mines, three mornings - no floor ordered twice, none skipped.

## A mineshaft's shaft that is never cut (P1, live 2026-11-24)
The operator expected the mineshaft's head to have a hole at its centre and stairs going down. The template's head is
its fence, gates, torches and chests; the hole and the stairs are its last step, "mine the shaft ... till it's dry".
At chunk (0, 2) that step began at 07:33 and no stair was cut: "MINE: no chunk at or round x=8 z=40 a well can be cut
down clear of water and lava - there itself, from (6, 64, 39) to y=12: no floor under step 2 at (7, 61, 38) - an open
cave below" (`bot.supply.Miner.hazard`). A hollow under the slab ruled the whole stair out, the squad went underground
some other way - found at y=5..55 under the chunk beside - and a hasty workshop was sited at (8, 64, 40), the shaft's mouth.
- Built: a hollow under a step no longer rules a well out (`Staircase.siteWell`, `wet`) - its miner floors the step as
  it floors a tunnel over a cave (`Miner`'s bridge; operator, 2026-11-24: you can go bottom up). Left: a hollow too
  deep or wide for that bridge filled from its bottom up, as a deep footing is.
- The shaft's mouth is the chunk's middle, in its template: its cells kept clear of workshops and chests, and the head
  not done till its first steps are cut, so a head with no hole is seen.
- A stair that can't be cut says so up the chain, and the squad waits at the head - it doesn't go down elsewhere.
- Eval: a mineshaft mission over a cave a block under its slab - the stair cut through it, floored, its squad down it.

## Why
A mine squad cuts the stairwell down, sets a level every four blocks, and runs a main tunnel and lit branches out from
it (docs/military-doctrine.md § 16). The ore it brings up is still thin and uneven - nights of 0 ore of the 16 wanted
with every level crewed (field443) - because every level works the same slab of rock, a gallery that meets a cave
stops there, and a mine's base has one furnace and nothing to re-arm a respawn with. Iron is commonest around y=16 and
diamonds near y=-55, in veins a few blocks off any one shaft.

## What's left
**A mineshaft mission's shaft isn't its own yet - P1, found live 2026-11-23 (field699-700).** "A mineshaft at chunk
(2, 4)" had its head built, and then:
- Its squad never cut a shaft under its chunk. A mine ordered near the camp is taken for the camp's (`SoldierRunner`'s
  staircase lookup, `nearCamp`): Alpha went down the camp's staircase at (73, 46, 56) and worked its levels - "the
  stairs 48 of 48 steps down" was the camp's mine.
- After a restart the site was looked at afresh, no well could be sited ("no floor under step 4 at (38, 63, 71) - an
  open cave below"), and a site no well can go down is told as dried up (`mineDriedUp`, "no well, no head: the site
  ordered") - which `FarmMissions.mineDry` took as the mission's shaft mined dry: DONE a minute after the restart.
- Its step isn't in the stall alarm (`FarmMissions.alarmStalled` leaves a shaft out: its measure says nothing of progress).
Wanted: a mission's shaft sited in its own chunk whatever the camp's is, kept on disk by its chunk and cut on after a
restart; dry only when its levels are worked out, an unsiteable chunk said as that and the mission held, not done; a
measure of its progress (levels cut of those to cut) so it's alarmed when it stalls; the eval - a mineshaft mission
beside a camp with a mine, restarted half way - and the live mission at chunk (2, 4) opened again.

**The strip mine as agreed** (operator, 2026-11-22). Built already: levels at y % 4 == 0, tunnels three high, a strip
every third block of the tunnel it comes off, ore taken in reach and its hole put back in the wall, the pickaxe for the job
and ore beyond it left and noted (docs/military-doctrine.md § 16). To build, in this order:
- **Every soldier its own tunnel.** Two a level, in opposite directions from the landing, each its own main tunnel and
  its own strips - none waits on another's. A squad of four works two levels at once, all four cutting; today two of
  four cut, the second's strips waiting on the first's tunnel. `bot.supply.Miner` is 985 lines: this is its split.
- **The axis fixed, and turned level by level**: a level's tunnels along x, east and west, their strips along z at
  x % 3 == 0; the next level down along z, its strips at z % 3 == 0 - so levels four apart work different rock (the
  "unworked rock" below).
- **A vein followed to its end**, then its pocket filled back with cobblestone or what's carried: floors and walls kept
  whole, nobody falls through, and a mob has one way in. Today a vein is followed two blocks.
- **A strip that breaks into a cave is walled off**, not bridged: the mine stays a closed set of corridors.
- **Two bands**: iron's levels from y=16 down first; with iron pickaxes carried, a mine straight down to about y=-56
  for diamonds, not seventeen levels crept through. The ore noted for want of a pickaxe (`app.OreNotes`) is gone back for.
- **The mineshaft as a template** - the fourth, after wheat, trees and the supply point (operator, 2026-11-22). Built
  (docs/echelons/platoon-leader/decisions.md): its mission - the head fenced, lit and its chests up, then its shaft a
  MINE for one squad till it's dried up - and its chat command. Left: everything below. A chunk
  fenced as theirs are, its shaft's head in it, its workings filled back in cobblestone. One every two chunks or so; a
  squad goes down one and mines it till dry, a different squad down one each night until each is mined out. A chunk
  mined dry is then free to be given a wheat or a tree template. A mission as a farm's is (`command.FarmMission`), the
  platoon leader's to give - and **the chunk's history is the platoon leader's**: what each chunk has been (mined dry on
  which night, a farm since), kept durably with its missions (backlog/authority-and-missions.md), so it sites the next
  shaft and the next farm from the record, not from a rule in a soldier.
  Settled (operator, 2026-11-22):
  - *Where*: chunks whose two coordinates are both even, but one a farm or the supply point holds. *Down*: the
    staircase, as now; a ladder shaft once soldiers climb them (backlog/ladders.md). *Levels*: every fourth from y=56 to
    y=-56, an iron pickaxe carried below y=16.
  - *Dry* is everything cut, out to where its tunnels would run into the next mineshaft's ground.
  - *Backfill* is what solid rock would have left: an ore pocket or a cave broken into filled back to the corridor's
    line. Tunnels and shaft aren't closed off, and are kept lit. Mined dry, the top of the shaft is filled: the ground
    over it is a farm's next, or another resource's.
  - *One squad at a time to a shaft*, and each squad may have its own (operator, 2026-11-22). By night as a rule; by
    need - the force short of ore - the platoon leader sends a squad down full time.
  - *Where the mineshafts are is the platoon leader's to keep, in its registers*: built as far as its mission's rows
    (chunk, squad, head done, shaft under way or dry). Left: a row a shaft in the chunk register - the levels cut - and
    the mine's stairs read from it; today they're a file of the runner's (`WallStore` "stairs").
  - *Chests at its head and at each level*, a bank of them, for what's worth keeping and won't fit a pack: ore, ingots,
    diamonds and the other minerals, tools - everything but dirt and cobblestone, which never go in them. Built: the
    head's four. Left: a level's; and the miners' loads going to them - today a mine's stash is its own chest at its
    landing, which takes cobblestone too.
  - *A cave a strip opens into is walled off* - first of what's left: live (field697) Dana, on the night shift, went
    from the mine into a cave with a creeper, two zombies and a spider, and was killed there.
  - *Dirt and cobblestone past a load are thrown out* down the mine: carried, they fill the pack that the ore wants
    and help nothing. A stack or two kept for steps and for filling back a vein's pocket; the rest dumped where it's dug.

**Where the squads mine, and who says so**
- **By night, a mine site per squad** - backlog/authority-and-missions.md step 3; today every squad goes down the
  camp's one shaft. To measure over a garrison night once that stands: whether hostiles follow a squad down from the
  surface, whether the stairs need torches and a door at the top, and whether a site cut fresh yields the night's 16
  ore.
- **A mine ordered by day, and the next mine cut ahead of the last one's end** (operator, 2026-11-17), are the platoon
  leader's tasks, given in its one decision - authority-and-missions.md step 5 - not a standing rule's (today a squad
  released from the flatten with nothing short cuts the next site's stairs, and the night shift cuts a mine whatever
  was ordered). Its picture wants the mine head in it to ask for one (backlog/cells.md).
- **The squad quarrying the stone round the landing** - the keep's 4,096 cobblestone - each soldier but the cutters:
  the squad leader's split of its ground, authority-and-missions.md step 4. Its squad simulation wants a mine with the
  tunnels already cut; in a fresh one the miner's own tunnels keep every soldier busy and nothing tells the two apart.
- The rest of the mine base: backlog/sites.md. Faces 100 blocks off with "no route" each report (field458):
  backlog/long-routes.md.

**The rock worked**
- **Unworked rock** (operator, 2026-11-17). Every level's main tunnel runs straight out from its last step, the same
  way on every level (`Staircase.outward`), so levels four apart have all worked one slab and the rock round the
  shaft the other ways is untouched. Turning a level's tunnel at the landing would cut through the spiral's own
  steps, and mines on disk keep the shape they were cut to: a heading per level off a short spur clear of the ring
  wants designing, not a flag. First, a probe (console `execute if block`) of a branch wall cut in field443 for ore
  the model doesn't show - the seeing worked that night (ore mined beside Delta's stairs), so spent faces are the
  likelier cause and the headings the fix.
- **A gallery that breaks into a cave** (`stuck/cave-eli-field552`; field535-552, Eli at y=20): its next stand is over
  the void with no floor, and the miner has no route to it for runs. Decided (operator, 2026-11-22): floored
  across at its level with the cobblestone it carries. First a squad simulation that holds a scenario's objective at
  its height underground - today the objective is put on the surface (`NightRules.grounded`), the camp's ground work
  and the recovery drill take the soldier up to it, and the cave is never crossed. Then the bridge level with the
  goal (`Pathfinder.stepBuild`'s ground-beyond rule relaxed), unproven without that scenario.
- **A cutter at a cave with nothing to floor it** (field462). It keeps a stack of cobblestone when it stores
  (`SoldierErrands.MINE_CARRIED`) and gives the waiting tunnels up after an idle minute. Left: short of blocks it says
  so and is supplied from the mine's stash (backlog/soldier-order.md, running low is a LOGSTAT), or takes the stone
  from its own tunnel's wall - so the cave is floored, not the tunnel lost.
- **The walls kept whole** in the main tunnel and the branches - holes filled, wall ore put back - as the stairwell's
  are. And live, a mine sited to the stairwell standard and cut to its landing.
- **The LOGSTAT counting raw iron and diamonds.**

**The base and the crew**
- **Furnaces sized to the shift.** `Workshop` holds one furnace and a furnace is one soldier's at a time
  (`Crafter.usable`): field395, five soldiers set about smelting at the keep mine's base within ten seconds and the
  rest waited, about two runs cut off a night. Surplus to need, no locks: the workshop, its landing, fitter and
  crafter each taking several.
- **A re-arming cache at each mine head.** field427-435: five soldiers killed by night respawned, were sent down
  their squad's mine and stood the night at its head's workshop - "can't make wooden pickaxe and wooden sword: no LOG
  within 48 blocks". The head's stash chest holds a pickaxe and a sword's worth of logs (three), kept back from the
  depot and topped up by day like the mine's torches; a respawn short of a pickaxe draws from it before it goes down.
- **Two cutters never sent to stand on one stair cell** (field465: Mia and Ned on one cell all night, 0 ore). Their
  waits stand a step apart (`Miner`); their cuts don't.
- **A wait at step 1 past a few minutes said loudly**: a waiting miner reports `Mining[step=1]` and raises no
  `[Alert]`.

**Read as in the mine, or out of it, wrongly**
- **The climb-out reads a hillside as the mine.** field440: Liam on a slope under a hill, 38 blocks from Charlie's
  mine head at y=96, was sent "climbs out of the mine to its head first" every 2 s, in turn with his armourer's
  errand, and stood. The climb-out (in `SoldierRunner.orderSquad`) asks only that the soldier is underground, below
  the head by `MINE_BELOW` and within `CLIMB_OUT_NEAR` of it; it wants the soldier in the mine's galleries - on the
  mine's cells - as `SoldierRunner.cutOff` reads a mine (`MINE_GALLERY_REACH`).
- **The shaft's bottom read as a pocket** (field172; field408). In a mine's galleries only the pocket rule strands a
  soldier now. To check: whether that rule alone still fires at a 1x1 spiral's bottom - whether the flood
  (`Pathfinder.reachable`) climbs the stair, or the cutter's work below the landing breaks it. The log line is "cut
  off in a pocket".
- **Watch for branch flipping**: "cuts branch" counts a level; starts in the hundreds a night mean a cutter's branch
  is changing under it again (field410-412).

## Done when
- A garrison night's 16 ore, each squad at its own site, with no cutter standing a night at a cave, a furnace or
  another's stair cell.
- A respawn by night goes back down armed.
- No "climbs out of the mine" for a soldier on the surface, and no "cut off in a pocket" for one at a shaft's bottom.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches one from the iron the
  stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or platforms every 8
  blocks is how a player makes one safe. Ladders need a climbing move the soldiers don't have; stairs came first
  because they're walked.
- Bows against archers at the far faces: backlog/ranged-combat.md.

---
status: proposed
priority: P1
---

# Mobility: swimming, water lifts, and stairs into a tall face

## Why
A soldier steps up one, drops three, pillars, cuts and builds steps, and is dug out of a pocket or off a ledge by the
recovery drill (docs/echelons/soldier/capabilities.md, docs/military-doctrine.md § 7). What it still can't do: move
through water anywhere but the surface, climb a tall face with nothing carried, or build steps toward a place further
than one search reaches. The platoon leader still picks ground a squad can't get to.

## What's left
- **Swim as moves.** The planner knows a swimmer only at the surface (water at the feet, air at the head,
  `Pathfinder.swimmable`): a soldier wholly underwater has no footing in its plan. Wanted: rise a block through water
  (jump held), sink a block, and swim forward into a step at the surface - each a move at a swim's cost, so a flooded
  stair or a pool with a bank is swum, not stranded in. (Live, three soldiers floated for hours in the flooded spiral
  stair of the camp's mine; the stuck-in-water drill got them out by filling the water into steps.)
- **Water columns as lifts.** A 1x1 shaft of water sources is a ladder to swim up and drop down without damage; soul
  sand at its foot makes a bubble column that lifts, magma one that pulls down. For the mine (backlog/mining.md:
  "ladders down a 1x1 shaft"), a water elevator beside the staircase is the fast way between levels. The engineer
  lays it out at the mine base - the shaft cut, filled from water buckets (`Recipe.BUCKET` exists; carrying water
  needs the bucket's use), a plain column until there's Nether access for soul sand - and a soldier's moves include
  riding it.
- **A staircase into a face taller than three blocks, with nothing carried.** A step is cut only where it comes out
  under open air, so the planner never tunnels and the survey's flood fill stays bounded.
- **Built steps toward an objective beyond one search.** Offered on a partial route they led the scenario soldier
  astray (lakes and ravines), so a soldier builds only when the plan reaches.
- **A stair up whose cut stalls midway** - a cell that won't break, water found - is laid again from where the soldier
  stands. Today it's laid again only once the soldier is more than 6 off every cell of it (docs/military-doctrine.md
  § 7); check whether a stalled cut is covered before building anything.
- **The survey takes the top ground in each column.** In a valley or under an overhang that's often far above the
  squad, which is part of why the high ground dominates its picture.
- **The respawn platform, live.** Replacements respawn on a patch of glass at y=102 near (-5, 9), 17 blocks over the
  ground. The recovery drill's stair passes the squad simulation's copy of it; it wants seeing live, the next time a
  replacement lands there.

## Done when
- Squad simulation: a soldier in a flooded stair swims up and out; one across a pool with a bank swims to it.
- Squad simulation: a soldier with nothing carried reaches ground four or more blocks up a face by steps it cuts.
- A soldier sent past one search's reach builds toward it without being led into water or a ravine, in the scenario
  that failed.
- A water lift at a mine base is laid out, filled and ridden both ways, in the simulation.
- Live: a replacement on the respawn platform is down and linked up without help.

## Notes
- If something unseen stops a soldier again as at the mine's bottom (field161-168: Bob stuck three times a minute short
  of (-42, 31, 42), the cell air on the server), run this from the console while it's stuck:
  `execute as @e[x=-42,y=31,z=42,distance=..1,type=!player] run say PROBE` with the cell's own coordinates.

## Steps put up on the way and left (P1; operator, 2026-11-24: the camp is getting messy again)
Live (field715, one hour): 97 blocks placed, most of them a walk's steps (`[Place] ... route step`), dirt 5 to 13 over
sea level. Most are the keep's crew climbing what it is taking down, a step put up and dug again.
- **The audit can't say a walk was spared**: its "a walk round N steps" (`Mover.walkRound`) lets the walk dig, which
  the route's own search refuses a walk for, and reads "0 steps" where the walk's search takes the soldier's cell for
  the goal already while its route has a step to put - the two searches disagree on where it stands. Make it the
  search `Pathfinder.find` itself weighs (plain, from the route's own start), then count the steps a walk would spare.
- **A step is taken down by who put it up**, once across - the debt `TAKEDOWN_COST` prices is paid by nobody yet: a
  register of steps by soldier and route, each dug as its walker leaves it or on its way back, and what's left counted.
- **The camp's hill itself**: most of these climb it; backlog/camp-to-sea-level.md ends the reason for them.
- Built: a fence is never crossed by a step beside it - nothing stands on a fence's top (`Pathfinder.standable`).

## A cut-off soldier's stair, the haul road and the gates: one change (P1, 2026-11-24)
Pete stood a day at the head of its own half-cut stair up, given a stand on the open ground past its last four cells
(`Clearing.nextInTunnel` through `standFor`). The fix - a cell in reach is cut from where the digger stands - is
written with its test (the patch kept beside the watch log, `pete-road-gate.patch`), and can't go in alone:
- **The haul road cuts a gate**: `engineer.AccessRoute` lists a shut gate on its way as rock to cut. It never was cut
  only because no stand was found for it; with the stair's fix, or with `nav.Footprint`'s two sides made one rule, one
  is, and the farm beside loses its gate (`FarmMissionSimulationTest.aTreeFarmBuiltBesideAFarmLeavesTheFarmAsItWas`).
  The road goes round a gate, never through or over one.
- **A gate opened and not gone through**: `GateHabit` opens any shut gate within reach it moves toward, and one it has
  stood still beside for ten ticks; a soldier in position beside a gate, or stopped in its cell with a CANTCO, keeps it
  open. Opened only on its line of travel; a soldier never stops in a gate's cell or the cell before it; one held up
  there steps clear.
- **`Footprint`'s low side** takes the epsilon its high side has, with these.
- Evals first: a road planned past a farm's gate; a soldier in position a block from a gate for a minute; Pete's pocket.

## Unreachable is a fault to find, not a state to report (operator, 2026-11-23)
"Mobility-wise, everything should be reachable." The garrison has drafted 1,069 stuck cases with their ground
(`garrison-state/worlds/`); a handful were ever registered. `StuckTriageIT` replays the newest forty, each soldier
alone on its recorded ground: 22 reach their goal - held live by something else, a crowd or their tasking - 3 have a
goal beyond the ground kept, and 15 are stuck alone. Of those, as first read:
- **The simulated server's own gaps** stop a replay before the soldier's fault shows: it swung only the one kind of
  gate it places (fixed), and it opens only the one chest state it places - a chest facing another way, or half of a
  double one, fails the tick of whoever uses it (Kate, Bob by the camp's chests). Next: any chest state opened.
- **A step of a block or two with a heap in the way** (Fran on the old hill's top among chests and tables, 2 blocks;
  Pete at y=41 under the camp, 3 up; Olga, Mia and Carol on the mine's stairs, 2 to 3): "no route" a body's length
  from the goal. Each to be read in its replay and registered under `stuck/`.
- **A stand in its own way** (Dana, rearming at a table: ended 1.7 off the stand it started on).
To build: the triage's STUCK cases read one by one, the fault of each fixed and its case registered; a goal beyond
the recorded ground recorded wider (the snapshot is 24 each way of the soldier, the goal often 30 off).

## Three soldiers in a cave under a finished template (live, 2026-11-24)
Alice, Bob and Carol were sent, one after another, to take back a step left in a cave under a placeholder's floor, were
stranded there, and their recovery stair was cut up through the floor - which the mission then called done. Four
things, each a test first:
- **A step left is taken back only where a walk reaches it.** `PoliceCall`'s reach is 16 across and 4 down, measured
  through a floor: a step under one is another level's, and nobody goes underground for it.
- **A recovery stair asks what a mission keeps before it cuts** - a template's floor is kept (backlog/kept-ground.md:
  the stair's cut asks `bars`, with the templates' columns) - and where the only way up is through one, it is said,
  cut, and paved behind the last soldier out.
- **A template's review reads its floor**: a placeholder's drift is its fixtures and sky; its floor is settled at the
  FLOOR step and never read again (the templates' one drift check).
- **The walk out that is refused**: a plain search over the snapshot reaches the surface from (10, 61, 25); the
  pathfinder says no route, as it does for Pete at (33, 41, 34). Not traced.

## A strip whose soldier can't work it is nobody's (live, 2026-11-24)
A floor's cells are dealt in strips by a soldier's place in its squad (`FarmMissions.share`). The origin's paving held
at 14 of 256 for two hours: eleven of them in the strip of a soldier stranded elsewhere, two squadmates' strips empty
and they at "no task". Wanted: strips dealt among the soldiers who can work them, a soldier dealt back in when it is at
the ground again, without re-dealing each second. And `Mason` says "paves" once the cell is paved, not before the walk.

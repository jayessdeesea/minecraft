---
status: proposed
priority: P1
depends-on: []
doctrine: ATP 3-34.81 (engineer reconnaissance); FM 4-0 (Class IV)
---

# Mining: ore at depth, and bases on the way down


## Why
A MINE squad cuts a staircase down (docs/military-doctrine.md § 16) and mines the ore beside it,
but that's only the ore that happens to lie against the shaft. Iron is commonest around y=16 and diamonds near
y=-55, in veins a few blocks off any one shaft. A player at a camp near spawn digs down, sets up a
working base at each mining level - y=32 and y=0, say - and runs tunnels out from it.

## Done when
- **The stairwell standard and the cutter's upkeep are built** (docs/military-doctrine.md § 16):
  the 4x4 well in chunk-local 6..9, its north-east corner at every y = 12k, holes filled and wall
  ore put back. What's left: live, a mine sited to it and cut to its landing; the main tunnel's
  and branches' walls kept whole the same way.
- **Branch tunnels, the rest.** The tunnels are cut and lit (docs/military-doctrine.md § 16). What's
  left: the LOGSTAT counting raw iron and diamonds; and a gallery that breaks into a cave - its next stand over the
  cave's void with no floor, the miner with no route to it for runs (`stuck/cave-eli-field552`; live, Eli at y=20,
  field535-552, and a thirteenth of the garrison's time in commit 944's profile). Default taken: floored across at
  its level with the cobblestone it carries (backlog/questions-overnight.md). First, a squad simulation that holds a
  scenario's objective at its height underground: the platoon leader's objective is put on the ground
  (`NightRules.grounded`, as it should be for a model's guessed height), the camp's ground work and the recovery
  drill then take the soldier up to it, and the cave is never crossed - tried with a gallery at y=20 meeting a
  void, and a bridge allowed level with the goal (`Pathfinder.stepBuild`'s ground-beyond rule relaxed) went
  unproven so.
- **Levels.** Built: a squad to a level at y%4==0, two cutters to a level, a second level for a
  squad of four, levels cut on down while ore is wanted, and a spent mine - the keep's or the camp's - moved to a
  fresh site (docs/military-doctrine.md § 16). What's left: the rest of the mine base (backlog/sites.md); and
  the squad quarrying the stone round the landing - the keep's 4,096 cobblestone - with an
  `Order.Gather(STONE)` round it for each soldier but the cutters. Tried; its squad simulation
  can't tell it from the miner's own tunnels, which keep every soldier busy there in a fresh mine,
  so it wants a scenario with the tunnels already cut.

- **The next mine cut ahead (operator, 2026-11-17).** Built: a squad released from the flatten
  with nothing short cuts the next site's stairs by day. Left: whether that's enough to have it cut
  before the keep's is spent, live; and a squad of its own for it if not.
- **The general never orders a mine by day.** 0 of 256 orders in a night's run, 244 of them
  patrol bases: its picture asks for one only through the AAR's "no mine yet". The night shift
  cuts it regardless. A mine head in its own cell (backlog/cells.md) would put it in that picture.
- **The night shift, live.** Every squad now mines the camp's shaft at night
  (docs/military-doctrine.md § 16). Still to measure over a garrison night: whether hostiles
  follow the squad down from the surface, whether the stairs need torches and a door at the top, and
  whether the camp's mine at its next site - a well past the wall, cut fresh - yields the night's 16 ore.

- **The shaft's bottom reads as a pocket.** Live (field172), a soldier at the keep mine's bottom
  (-24, 19, -23) was taken for cut off - no more than 128 cells to walk by the planner's moves - and
  the recovery drill laid out for it twice in two seconds as it walked on. A 1x1 spiral of 200 steps
  should count as hundreds of cells; whether the flood fill climbs the stair, or the cutter's work
  below the landing breaks it, wants a look (`SoldierRunner.cutOff`, `Pathfinder.reachable`).
  Live (field408), with the drill's order made to outrank the mine's, Hal at work in his tunnel was
  found stranded and given a stair up 26 times in one night; three crews stood empty, no ore.
  Built: in a mine's galleries only the pocket rule strands a soldier, not the buried or below ones
  (`SoldierRunner.cutOff`, `MINE_GALLERY_REACH`). Left: whether the pocket rule alone still fires on
  the 1x1 spiral's bottom - watch "cut off in a pocket" in the mine.
- **A cutter's share flipping.** Live (field410-411), Dana started branches 47 and 49 in turn every
  1-3 s, 188 times a night, her level's tunnels not growing, as a stranded squadmate came in and out
  of the mine's crew. Leaving the drill's soldiers out of the crew didn't stop it (field412, 112
  starts); built: a cutter keeps its branch till it's cut, blocked or waiting (`Miner.keptTunnel`).
  Watch "cuts branch" counts per level: a level whose starts run to hundreds a night is flipping again.
- **The mine base has one furnace for the night shift.** Live (field395, 15:48), five soldiers set
  about smelting at the keep mine's base within ten seconds - copper leggings, boots, a chestplate,
  two iron pickaxes - and a furnace is one soldier's at a time (`Crafter.usable`), so the rest waited
  on it: Ivy's run was cut off when another took it, and she stood beside it a minute, twice. Surplus
  to need, no locks (work in parallel): a base's furnaces sized to its shift - `Workshop` holds one
  furnace, so its landing, fitter and crafter would each take several. About two cut-off runs a night.
- **The climb-out reads a hillside as the mine.** Live (field440), Liam on a slope at (18, 74, -6) under a hill,
  38 blocks from Charlie's mine head at y=96, was sent "climbs out of the mine to its head first" every 2 s,
  alternating with his armourer's copper errand, and stood. `SoldierRunner`'s climb-out wants the soldier
  in the mine's galleries (as `cutOff`'s `inAMine` reads it: under the head by `MINE_BELOW`, within the
  branches' reach of the head's column and on the mine's cells), not under any rock near the head.
- **A respawn at the mine head has nothing to re-arm with.** Live (field427-435), five soldiers killed
  by night respawned, were sent down their squad's mine (LinkUp's shelter), and stood the night at its
  head's workshop: "can't make wooden pickaxe and wooden sword: no LOG within 48 blocks", the depot's
  logs at none. Wanted: a re-arming cache at each mine head - its stash chest holding a pickaxe and a
  sword's worth of logs (three) kept back from the depot, topped up by day like the mine's torches -
  and a respawn short of a pickaxe drawing from it before it goes down.

- **Unworked rock (operator, 2026-11-17).** The night's ore thin at the levels worked (field399-413,
  one ore block in 70-100 cut): new branch headings off the main tunnel into rock no branch has
  passed. The keep's mine still doesn't move on yield. Where it is: every level's one main tunnel runs
  straight out from its last step, away from the shaft (`Staircase.outward`), the same way on every
  level - so the levels four apart have all worked one slab, and the rock round the shaft the other
  ways is untouched. Turning a level's tunnel another way at the landing would cut through the
  spiral's own steps, and mines on disk keep the shape they were cut to: a heading per level off a
  short spur clear of the ring wants designing, not a flag. The night's ore came back once the branch
  flipping and the deaths were fixed (63, 83 ore, field419-421), so it waited - and now it doesn't:
  field443's two nights took 0 ore of 16 each, every level crewed, 34 branches cut and no ore in any, the
  miners armed (weapons and two or three pickaxes each). But not one "mines the ore" line in either night -
  none beside the stairs either - against 21 and 13 the nights before (field440-441): an end that sudden
  looks more like ore not seen (`Miner.oreInSight` / `WorldView.isOre`) than rock spent. First, probe a
  branch wall field443 cut (console `execute if block`) for ore the model doesn't show; then the headings.
  When: ore last seen in field441 (13 on its night, 743 running); field442 (744) 0 in 7 cuts, field443 (745)
  0 over three nights and the days between. Neither 744 (an enderman back-off) nor 745 (a log under the sky)
  touches `Miner`; the cut is at 744's deploy, 2026-11-18 03:15. And the seeing works: at 04:26 Mia mined ore
  beside Delta's stairs at (64, -2, 108) - so the faces worked are the likelier cause after all, the probe to
  confirm it, and the headings the fix.
- **The far faces (operator, 2026-11-17).** Skeletons and creepers kill miners at faces 90 blocks
  out: soldiers learn to make bows and arrows (`Recipe.BOW`, `Recipe.ARROWS` - string from spiders,
  feathers from chickens) so an archer across a cave is answered, not closed on.

## Notes
- Diamonds need an iron pickaxe: the equipment ladder (docs/military-doctrine.md § 11) reaches
  one from the iron the stairs open.
- Lava lakes are common below y=-54; the stairs stop at lava beside a cell, and so should a
  tunnel.
- Ladders down a 1x1 shaft are faster to climb than stairs, and a vertical drop with water or
  platforms every 8 blocks is how a player makes one safe. Ladders need a climbing move the
  soldiers don't have; stairs came first because they're walked.

## Nobody waits on another's cut
Built: a miner cuts its strips as the spine passes their roots (docs/military-doctrine.md, the rules of a
mine). Left: a wait at step 1 past a few minutes said loudly - a waiting miner reports `Mining[step=1]`
and raises no `[Alert]`.

## Faces 100 blocks off, and a level straight below
Built: a cut's stand with no route has its tunnel given up (docs/military-doctrine.md, the rules of a mine).
Built too: a cutter far up its own stairs walks them down in legs. Left, seen (field458): Hal and Carol for
faces 100 blocks off, "no route" each report all night (backlog/long-routes.md).

## A cutter at a cave with no blocks to floor it (field462)
Its level's main tunnel waiting "for blocks" - a cave to floor - with none carried (Alice, stone 0, dirt 0),
and its partner waiting on that main tunnel. Built: a cutter keeps a stack of cobblestone when it stores
(`SoldierErrands.MINE_CARRIED`). Built too: idle a minute with none, the tunnels waiting are given up. Built too: a miner in a closed cave none of
it by the mine's stairs digs its way out by the recovery drill's stair up (operator, 2026-11-20). Left: one in a
cave its galleries still reach draws a load from the mine's stash or the stockpile, or takes the stone from its own
tunnel's wall, so the cave is floored rather than the tunnel lost.

## A dead well's levels still worked, and two cutters on one stair cell (field465)
The mine at (57, 96, -10) was declared a danger area at 16:03 (762's rule, three killed by its stairs) - the
keep's mine moved on - and through the next two nights Mia, Ned and Olga of Delta still worked their levels
off its shaft: Mia and Ned on one cell, (57.5, -36, -9.5), steps 941 and 983, unchanged all night, 0 ore.
Built: a dead well's staircase let go by every squad (`SoldierRunner`, the kept stairs). Left: two cutters
never sent to stand on one stair cell (their waits stand a step apart, `Miner` place - their cuts don't).


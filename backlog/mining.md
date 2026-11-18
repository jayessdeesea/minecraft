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
  left: the LOGSTAT counting raw iron and diamonds.
- **Levels.** Built: a squad to a level at y%4==0, two cutters to a level, a second level for a
  squad of four, levels cut on down while ore is wanted, and a dried-up mine moved to a fresh site
  (docs/military-doctrine.md § 16). What's left: the rest of the mine base (backlog/sites.md); and
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
  follow the squad down from the surface, and whether the stairs need torches and a door at the top.

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

## A cut's stand two blocks off, and no route all night (field458)
The second night on parallel strips took 7 ore of 16: seven soldiers reported "no route" all night
instead of waiting. Alice and Bob stood at (151, -24, -7), their cut's stand (151, -24, -9) two blocks
off; Ivy and Jack at (55, 1, -11) for (57, 1, -10), on Charlie's stairs. Nothing broke through - no
snag was marked, no breacher ran: `Miner.firstUncut` skips a cut whose stand is solid, but not one whose
stand is open and walled off from the soldier. Wanted: a cut's stand with no route from beside it is
cut to - the cells between dug as the cut's own - or the cut skipped, as a blocked tunnel is. Also seen:
Eli at the surface for his level 118 blocks straight below, and Hal and Carol for faces 100 blocks off,
"no route" each report (backlog/long-routes.md).


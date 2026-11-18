---
status: ready
priority: P1
depends-on: []
doctrine: FM 3-21.8 (movement: legs between checkpoints)
---

# A soldier far from where it's sent has no route there, and stays put

## Why
Live (field108), Eli and Dana stood a day 80 blocks south of the camp at (-80, 64, 121) -
most of the day's 24% CANTCO: "no route from (-80, 64, 122) to within 3 of (-36, 16, 54)",
the camp's mine bottom 70 blocks across and 48 down. One search (`DeterministicSoldier`'s
`PLAN_BUDGET`) doesn't find the way round, and a partial route ends where it can get no nearer - for
them, about where they stood. They
got there gathering wood: with nothing near, a gather looks out to 48 blocks round its
objective, which is where the squad last saw some.

## Done when
- Live, no soldier reports "no route" for a goal it could walk to. (Built: once its cheap plans
  gain no ground, a soldier searches 15 times wider, and goes on searching that wide for that
  goal - `DeterministicSoldier.LONG_PLAN_BUDGET`, `aGoalBeyondOneSearchIsReachedRoundTheLongWay`.)
- A goal beyond even the wide search - farther than about a hundred blocks round - walked in legs.
- A soldier on the surface is never routed down into a cave toward a goal there's no walk to.
  Live (field332-347), Jack, gathering wood, went from y=73 into the caves to y=-19 in a minute,
  and then walked cave to cave: each time the recovery drill laid his stair up (which now digs him
  out, field346), the next order sent him walking off it, down through the caves again toward a goal
  on the surface - y=-52, -22, -29, -26 in turn. A route's leg that goes far below both its start
  and its goal, with no known way up at the far end, is the cave's, not the goal's.
- A leg planned but not walked is planned again a different way, not the same one. Live (field428),
  Eli at (-86, 62, -15), sent to his level at y=-48 below, planned the same 214-step leg 69 times in
  2.5 minutes and stood: the walker couldn't take its first move and the planner gave it again.

## Notes
- Fran and Bob died gathering 50-60 blocks out (a creeper, pillagers). A gather that far from the
  camp is its own question: the general's tree farm is meant to bring the wood in.

- **A stair up laid and never dug (field393-397, Eli).** Sent down to a dried-up mine's base for a
  chestplate (fixed: a surface soldier crafts at its squad's workshop), Eli was stranded in a pocket
  at (-86, -43, -18) beside a waterfall. Plugging the water (fixed) let the engineer lay a stair up,
  304 blocks, at 16:06 and again at 16:15 and 16:25 - and he stood on, his order a MoveTo to the
  dig box's top corner (-101.5, 64, -29.5), "no route". That order is the stair-up branch's fallback
  when the clearer has no stand (`SoldierRunner`, "until it has one, toward the stair's foot" - the
  box's min corner at its top, not its foot). Two suspects, not yet told apart: his unit flips
  Bravo/Bravo-miner every ~10 min, each flip a new `Recovery` and a window in which his armourer
  takes his hands for a walk to a workshop he can't reach; and the clearer finding no stand for the
  stair's lowest cut from inside the pocket. Built: the soldier now holds where it is, not the box's
  corner, and "has its stair up but its clearer no stand yet" logs its first cells, hands and the
  clearer's stuck reason once. Found by it (field402): hands free, clearer not stuck - his supply
  run, short of rations, took his hands for the supply point 115 up whenever they came free (fixed:
  none for one the drill has); then by night, Bravo/miner's mine orders sent him to the dead mine's
  stairs over his own. The drill's order outranking the mine's was tried and taken back: the drill
  reads miners in their tunnels as stranded, and with it a night took no ore (field408) - wants the
  drill's stranded reading fixed for the mine first (backlog/mining.md). Left (field408): his unit
  still flips Bravo/Bravo-miner at dusk and dawn, each a new `Recovery` that takes reports to find
  him stranded again, and in that gap his armourer and supply run take his hands for walks with no
  route; and his pocket holds hostiles within 4 - a skeleton most of the time - that hold the cut
  (`STAIR_THREAT_RADIUS`) when he could fight them. Wanted: a stranded soldier kept in its unit, its
  episode carried across, till it's out; and one held by a hostile at hand fights it, then cuts.
- **Eli's rescue (operator, 2026-11-17, asked whether to send everyone down).** One fetcher, not the
  squad - the way down is 117 blocks of cave with skeletons and endermen in it: a stranded soldier kept
  in one unit till it's out (built: `SoldierRunner.heldInUnit`), and - if it's still stuck past ten
  minutes - its nearest squadmate with a pickaxe and blocks sent to it (`Recovery`'s fetcher).
- **Stranded with no pickaxe (field360, 06:29).** Kate, routed into a cave at y=47 on her way to the
  mine, was pulled out by recovery 35 under the ground at y=62 with two shovels, no pickaxe, no
  cobblestone, one log and no bread: her 88-block stair up is dug by hand, 150 ticks a stone block
  against 30 with a stone pickaxe - about eleven minutes. Never routing a surface soldier into a
  cave is the fix; a stranded soldier with a carried table and two logs could make a pickaxe first.

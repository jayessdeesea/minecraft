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
  stair's lowest cut from inside the pocket. Next: log the clearer's first cell and stand for a
  recovery dig, and the stair's first cells.
- **Stranded with no pickaxe (field360, 06:29).** Kate, routed into a cave at y=47 on her way to the
  mine, was pulled out by recovery 35 under the ground at y=62 with two shovels, no pickaxe, no
  cobblestone, one log and no bread: her 88-block stair up is dug by hand, 150 ticks a stone block
  against 30 with a stone pickaxe - about eleven minutes. Never routing a surface soldier into a
  cave is the fix; a stranded soldier with a carried table and two logs could make a pickaxe first.

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

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
`PLAN_BUDGET`) doesn't reach that far, and a route that doesn't reach the goal isn't walked. They
got there gathering wood: with nothing near, a gather looks out to 48 blocks round its
objective, which is where the squad last saw some.

## Done when
- A soldier sent to a point beyond one search walks there in legs - each leg a search that
  reaches its end, toward the point - and gets there, or says where it stopped and why.
- A scenario: a soldier 90 blocks from its objective on open ground gets there.
- Live, no soldier reports "no route" for a goal it could walk to.

## Notes
- Fran and Bob died gathering 50-60 blocks out (a creeper, pillagers). A gather that far from the
  camp is its own question: the general's tree farm is meant to bring the wood in.

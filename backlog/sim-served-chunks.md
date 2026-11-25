---
status: ready
priority: P2
depends-on: []
---

# The simulated server serves chunks as a soldier moves

## Why
`app.SimServer` sends a soldier the 7 by 7 chunks round its spawn when it joins (`VIEW = 3`, in `join`) and none
after. Ground outside them is never in a soldier's world, and unknown ground reads as solid (`ChunkCache`): a soldier
sent there stops at the edge with "no route" for good. The farm simulations spawn at x=68 and script each squad's own
objective at (10.5, 2.5), a chunk west of what is served. Most pass because the mission's work keeps the squad east;
`aTreeFarmBuiltBesideAFarmLeavesTheFarmAsItWas` had a soldier stop in the farm's west gate, holding two gates open,
and its objective is now at its camp.

A live server sends chunks as a player moves and drops the ones behind. The simulation meets neither a chunk arriving
late nor one unloaded under a soldier's route.

## Done when
- `SimServer` sends the chunks that come into view as a soldier crosses a chunk line, and unloads the ones that leave
  it, through the same listeners a connection uses.
- A simulation walks a soldier more than 3 chunks from its spawn and back.
- The farm simulations' scripted objective at (10.5, 2.5) is either reachable ground or moved: said in each.

## Notes
`SimServer` is at the 1000-line cap: this goes in a class beside it, as its items went into `SimItems`
(backlog/split-oversized-sources.md).

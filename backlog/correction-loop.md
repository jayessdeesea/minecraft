---
status: ready
priority: P1
depends-on: []
---

# A soldier the server keeps putting back tries the same step for ever

## Why
A soldier whose every move the server refuses stands where it is, sending the same step ten times a second, with
nothing changed between tries (docs/conventions.md, "A retry changes something") and nothing said. Live, field750,
2026-11-27 UTC, from the server's log (`docker compose logs --timestamps minecraft`, "<name> moved wrongly!"): 1,164 in
the day - Ned 649, Fran 299, Dana 94, Hal 59, Olga 24, Pete 22, Eli 11, Kate 3, Carol 2, Mia 1. The two worst were in
contact with a hostile below: Ned put back to (56.27, 14.0, 38.69) from 18:44:59 to 18:46:05, fighting a zombie 5.8
below in the mine; Fran put back to (47.31, 63.02, 69.76) 297 times from 19:25:58 to 19:26:28, on a cliff lip with
three pillagers 4-5 below her, and shot dead at the end of it. The garrison's log says only "Player Position received:
... - accepting", once a correction.

The chain, read from the code:
- `PlayClientHandler` (~173-183) accepts every teleport and calls `onPlayerPosition`; `SoldierRunner` (~5845) passes
  it to `DeterministicSoldier.correctPosition`.
- `DeterministicSoldier.tick` (~433-447), on a correction, calls `stuckShortOf` if it has a route, then sets the gait
  to the server's position, zeroes the vertical velocity, clears `onGround` and the route, and returns without moving.
  The next tick steps from that point; the server refuses it and puts the soldier back: a 100 ms loop.
- In contact nothing counts it: the fight clears the route every tick, so `stuckShortOf` and `takeSnag` (which act
  only with one) never fire, and the contact's boxed-in test is never reached on a correction tick
  (backlog/engagement-rules.md, CHANGE 12). Out of contact a replan once the soldier lands brings `stuckShortOf` back,
  at most once per `AVOID_TICKS` for the cell.

## Done when
- The trigger is known: each correction is logged with the move it answered (its delta, `onGround`, the cells round
  the soldier as the model holds them), and the server's blocks at a refused spot are probed (`WorldProbeLiveIT`, the
  garrison stopped). A refused step the model can't explain is a world-model fault, for backlog/world-model-stale.md;
  one it explains is the mover's.
- A correction loop changes something: corrections to one point counted as the soldier's and logged as `[Retry]` at
  the first repeat and at each doubling; after a second of them the soldier stops stepping (waits out of step), then
  tries another heading or a planned route, then says CANTCO with the count. A bot test pins it first, with a server
  that answers every move with a teleport to one point - today the loop runs unbounded.
- The garrison watch reads the server's "moved wrongly" count by soldier, and each cluster is explained.

## Notes
- Not established: why the server refuses. Vanilla lets a vertical difference under 0.5 pass, so a missing floor block
  is unlikely; the likelier triggers are a horizontal difference over 0.25, a collision with a block the model lacks
  beside the soldier (the model has air at (48, 62..63, 69..70), beside Fran's footprint), or the move's own fields
  (onGround, rotation) sent straight after a correction. Both clusters had a hostile below and a fight going on.
- The server also said "Player Fran standing on air - force-sending blocks below" at 19:25:24 and 19:25:38, on the
  leaf canopy at y 67-68 - a model out of step with the server there, which may or may not be the same fault.

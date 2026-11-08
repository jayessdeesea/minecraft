---
status: ready
priority: P3
---

# Confirm placements and digs by the server's acknowledgement

## Why
Every routine is an expect script (docs/architecture.md § Behavior modeling), and each waits on the server's
answer. One answer we don't decode yet: the server acknowledges every `useItemOn` and dig with
Acknowledge Block Change, carrying the action's sequence number. `Builder` counts a placement with no
block update within `PLACE_TICKS` as a try that didn't land, and `Digger`/`Breacher` count a break the
same way. With the acknowledgement, "processed, and the block isn't there" fails the step at once.

## Done when
- Block placements and digs are confirmed by the server's acknowledgement, not a deadline alone.

## Notes
- The gain is small: a refused placement fails about 8 ticks sooner than its deadline. Nothing
  live has been slowed down by the wait.
- Check the packet order on the wire before relying on it. If the acknowledgement can arrive
  before the Block Update that carries the new block, "processed, and the block isn't there"
  would fail placements that landed.

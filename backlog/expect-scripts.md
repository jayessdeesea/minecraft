---
status: ready
priority: P2
---

# One arbiter over the routines

## Why
Every routine in `bot.supply` and `bot.craft` is an expect script (AGENTS.md § Behavior modeling).
Two gaps remain:

- **Hand-wired arbitration.** Who gets the soldier's hands is decided in `SoldierRunner`'s tick by
  a hand-maintained web of `!x.active()` conditions, one per pair of routines. Every new routine
  has to be threaded through all the others, and the priorities aren't written down anywhere. Each
  routine's condition for running is a guard. What's missing is one ordered list saying which
  routine outranks which: contact first, then eating, then the rest.
- **A refused placement is only known by its deadline.** `Builder` counts a placement with no
  block update within `PLACE_TICKS` as a try that didn't land. The server says so outright: it
  answers every `useItemOn` and dig with Acknowledge Block Change (the action's sequence number),
  which we don't decode. With it, "processed, and the block isn't there" fails the try at once.

## Done when
- `SoldierRunner` asks a priority arbiter which routine runs this tick, instead of hand-wiring
  `!active()` pairs. The priority order is written once, in one place, and covered by a test.
- Block placements and digs are confirmed by the server's acknowledgement, not a deadline alone.

## Notes
- The routines don't yet share one shape: each has its own `tick` signature (what it's given) and
  its own outcome type (`Builder.Progress`, `Crafter.Outcome`, `active()` alone). The arbiter wants
  one (wanted, tick, outcome) shape first.

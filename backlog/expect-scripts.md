---
status: ready
priority: P2
---

# Routines as expect scripts, under one arbiter

## Why
Crafting (`Torchmaker` and `Woodcraft`, through `bot.craft.Crafter`) follows the expect-script
rules in AGENTS.md § Behavior modeling: confirming events, deadlines, guards that interrupt,
resumable runs. Two gaps remain:

- **Fixed delays.** The other routines still wait a fixed `SETTLE_TICKS` and read the inventory
  afterwards, hoping the server has answered by then. A slow answer makes them act on stale state,
  and a lost one surfaces later as an unrelated-looking failure. They are `Builder`, `Depositor`,
  `Digger`, `Doorway`, `Handover`, `Rations` and `SupplyRun`.
- **Hand-wired arbitration.** Who gets the soldier's hands is decided in `SoldierRunner`'s tick by
  a hand-maintained web of `!x.active()` conditions, one per pair of routines. Every new routine
  has to be threaded through all the others, and the priorities aren't written down anywhere. Each
  routine's condition for running is a guard. What's missing is one ordered list saying which
  routine outranks which: contact first, then eating, then the rest.

## Done when
- No routine in `bot.supply` waits a fixed number of ticks for the server. Each wait is a
  `Script.Step` with a confirming expectation and a deadline derived from server timing (a dig's
  break time, eating's 32 ticks), and `SETTLE_TICKS` is gone.
- Each ported routine has a unit test against a fake server, including one where the server never
  answers.
- `SoldierRunner` asks a priority arbiter which routine runs this tick, instead of hand-wiring
  `!active()` pairs. The priority order is written once, in one place, and covered by a test.

## Notes
- Port one routine per change and verify each live. `Rations` is the smallest. `Builder` and
  `Digger` are loops of short scripts, one per block, rather than one long script.
- The arbiter wants the routines to share one shape (wanted, guards, tick, outcome) first, so port
  them before building it.

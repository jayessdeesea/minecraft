---
status: ready
priority: P2
---

# Routines as expect scripts

## Why
`Torchmaker` waits on the server through `bot.script.Script`: every step names what confirms it
and a deadline, and a miss fails loudly with both. The other routines still wait a fixed
`SETTLE_TICKS` and read the inventory afterwards, hoping the server has answered by then. A slow
answer makes them act on stale state, and a lost one only surfaces later as an unrelated-looking
failure. They are `Builder`, `Depositor`, `Digger`, `Doorway`, `Handover`, `Rations`, `SupplyRun`
and `Woodcraft`.

## Done when
- No routine in `bot.supply` waits a fixed number of ticks for the server. Each wait is a
  `Script.Step` with a confirming expectation and a deadline derived from server timing (a dig's
  break time, eating's 32 ticks, a smelt's 200, each plus a margin).
- Each ported routine has a unit test that drives it against a fake server, plus one where the
  server never answers, asserting the failure names the step and what was expected.
- `SETTLE_TICKS` is gone.

## Notes
- Port one routine per change, verify it live, then move to the next. `Woodcraft` is the closest
  in shape to `Torchmaker`.
- Continuous behaviours (`Builder` placing a cell list, `Digger` mining a vein) are loops of
  short scripts, one per block, rather than one long script.

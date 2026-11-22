---
status: blocked
priority: P2
depends-on: [renewables]
---

# Operator chat: a farm placed from the conversation, a transcript, reasons on request

## Why
The operator's conversation with the platoon leader (docs/echelons/platoon-leader/decisions.md § Talking with the
operator) measures its capability and shows what it was shown (operator, 2026-11-21). Three parts of it aren't built.

## Done when
- *Place a farm at x = .., z = ..* puts a chunk farm site laid out in that chunk, at sea level unless a level is given
  (`bot.farm.ChunkFarm`), on the platoon leader's sites through its bids and build order (backlog/renewables.md) - the
  same path an order from chat takes, not a second one. Today the `FARM` action answers the chunk farm it would be and
  CANTCO.
- The conversation's transcript is kept under `minecraft.state-dir`, so a session can be read back; today the page
  holds it, and the log has each turn.
- The platoon leader gives its own reasoning for a decision on request ("why is Bravo on the farm?"): today it's shown
  its last decision's prompt, not why it chose.

## Notes
- Needs from elsewhere: the site and bid cycle (backlog/renewables.md).

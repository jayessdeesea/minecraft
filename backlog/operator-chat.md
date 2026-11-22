---
status: proposed
priority: P2
---

# Operator chat: a transcript, reasons on request

## Why
The operator's conversation with the platoon leader (docs/echelons/platoon-leader/decisions.md § Talking with the
operator) measures its capability and shows what it was shown (operator, 2026-11-21). Two parts of it aren't built.

## Done when
- The conversation's transcript is kept under `minecraft.state-dir`, so a session can be read back; today the page
  holds it, and the log has each turn.
- The platoon leader gives its own reasoning for a decision on request ("why is Bravo on the farm?"): today it's shown
  its last decision's prompt, not why it chose.

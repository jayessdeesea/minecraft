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

## Notes
- **Reasons on request is best done after backlog/authority-and-missions.md step 1.** Its `task` and `mission` rows'
  `why`, and its `order` rows, are the home for a decision's reason. The reason exists at decision time already -
  `command.ai.PlatoonLeaderReply`'s `reason` (why a squad's task changes) and `intent` - and isn't kept: the chat's
  `lastDecision` tool (`command.ai.PlatoonLeaderTools`) returns the prompt the platoon leader was shown, not its reply.
- The transcript doesn't wait on that: `app.OperatorChat` takes the earlier turns from the page with each request
  (`ChatRequest.turns`) and writes none of them down.
- **A wheat farm's mission is named "a farm at chunk ..."** and the operator reads it as no kind of farm (2026-11-22). Its
  intent says "wheat farm" now (`FarmMission.what`); the name is the key its register rows are kept under, quoted in
  the chat's prompt, its tools and thirty tests - renamed with a register migration, in one change.

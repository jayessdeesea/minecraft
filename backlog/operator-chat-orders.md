---
status: needs-decision
priority: P3
---

# Operator orders through game chat

## Why
The radio net is mirrored to game chat. `ChainOfCommand` sends orders and CANTCOs as `/me` lines
over the point man's connection. It only goes one way: the operator's mission still comes from
`minecraft.general.mission` in `application.yml` and can't change during a run. Letting a human
in a Minecraft client give the general a new mission through chat would make live experiments
much faster to steer.

## Done when
- A chat line from a human player in a recognised form (for example `!mission <text>`) reaches
  the general as a new mission, and the general re-plans.

## Notes
- This needs decoding clientbound Player Chat, which carries a signed message body and a chat
  type from the registry. That's a larger decode than anything in Play so far.
- Decision needed: whether in-game orders should be allowed to override the configured mission.
  If so, who is allowed to give them? In offline mode any username can join, so any connected
  player could.

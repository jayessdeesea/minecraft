---
status: proposed
priority: P1
---

# Operator chat: talk to the platoon leader over HTTP

## Why
The operator talks to the force only through in-game chat orders (`app.OperatorOrders`: `!mission`, `!escort`,
`!flatten`, remarks), one way: the platoon leader never answers except by what the force does. A conversation lets
the operator ask it what it knows and why it decided, and give it requests in plain words - a way to measure the
platoon leader's capability (operator, 2026-11-21), and a debugging aid: what it was shown, what it made of it.
It will be iterated on over time; it starts multi-turn.

## Done when
- An HTTP endpoint bound to **127.0.0.1 only** (port in `application.yml`) takes a message in a conversation and
  returns the platoon leader's reply; the conversation's earlier turns go with each new one, so follow-ups work.
  Reachable from the machine, never off it.
- The examples answer correctly, checked against the computed state:
  - *What time of day is it in Minecraft?* - from the overworld clock (`command.TimeOfDay`), never worked out.
  - *Place a farm at x = .., z = ..* - a chunk farm site laid out in that chunk, at sea level unless a level is
    given (`bot.farm.ChunkFarm`), put on the platoon leader's sites through its bids and build order
    (backlog/renewables.md) - the same path an order from chat takes, not a second one.
  - *What objectives are you tracking and what is their status?* - each squad's objective, task and progress,
    and each site's stage, as its prompt already holds them.
- Every turn is on the radio net (`[Operator -> PL]`, `[PL -> Operator]`) and in the log, with what the platoon
  leader was shown for it.
- A test with a scripted `ChatModel` drives a three-turn conversation through the endpoint.

## Notes
- **Standard library**: the JDK's `com.sun.net.httpserver.HttpServer`, JSON through the Jackson the app already
  carries - no web framework added (docs/stack.md). The model call goes through Spring AI's `ChatClient` as every
  other does, through `ModelCalls`' queue, never on a soldier's tick.
- **What it may do** is a closed set of actions the platoon leader can take, each one the operator's chat orders
  already have or the bids will (a mission, a flatten, a site to build), offered to the model as tools; anything
  else is an answer in words. Questions answer from the computed state it's already shown - the model is never asked
  to work state out (AGENTS.md).
- **Iterated on**: the conversation's own transcript kept under `minecraft.state-dir`, so a session can be read back;
  later, the platoon leader's own reasoning for a decision on request ("why is Bravo on the farm?").
- Needs from elsewhere: the site and bid cycle for "place a farm" (backlog/renewables.md); until it lands, the farm
  request answers what it would do and that it can't yet.

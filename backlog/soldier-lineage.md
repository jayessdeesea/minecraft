---
status: proposed
priority: P2
depends-on: [authority-and-missions]
doctrine: FM 6-0 (the unit journal and the after-action review)
---

# Lineage: what each soldier was told, did, and why - kept for analysis

## Why
Optimising the hierarchy needs the record, not the log (operator, 2026-11-13): for each soldier, the orders it
received and from whom, what it did under each, and the reports it sent back, as data that can be queried
after the run - how long each order took, where time went, what each CANTCO cost. Today that is prose spread
over the garrison log, readable one soldier at a time and not at all across a day. One part is there: each
change of a soldier's activity is a `[State]` line (`bot.ActivityTrack`). The dawn review counts outcomes, not
causes (`app.Scoreboard`, `app.CommandScorecard`).

## What's left
- **A record an event, machine-readable**, written through a standard library, never hand-built strings, each
  with the tick and the in-game time: the order received (from which echelon, with its intent); each change of
  activity (the `[State]` line's content) and of the routine holding its hands; each block dug or placed with
  its tool; each report sent; each alert.
- **Each order's life, as events:** issued, acknowledged (WILCO, or CANTCO and why), interrupted (contact,
  hunger, death), finished or abandoned - each with the order's key, its kind and its parameters.
- **The chain of command's decisions join it:** what the platoon leader and the squad leader were shown and
  what they answered.
- **A query over a day**, per soldier: time on each task, blocks an hour, time stuck and where, orders never
  done and why.

## Done when
- Each soldier's run is on disk as above, and the query answers those four questions for a day's run.
- It replaces the hand-read of the log for the live check after a deploy.

## Notes
- **Decided (operator, 2026-11-22): kept by the platoon leader** - rows in its registers, beside the `mission`,
  `task`, `order` and `fault` tables of backlog/authority-and-missions.md step 1, joined to them by key and read
  from the chat. What goes in a row is what was ordered, answered, reported and decided - an order's life, a task's
  state, a fault, a change of task - which is small. What a soldier does each second - a block dug, a routine
  taking its hands - stays the log's, a line each, kept its seven days (DEVELOPMENT.md, the log), with its counts
  rolled up onto the task's row when the task ends.
- Don't record what the world model holds each tick: too much, and its staleness is a separate study
  (backlog/world-model-stale.md). Recording a soldier's observations for replay is
  backlog/soldier-state-machine.md's.

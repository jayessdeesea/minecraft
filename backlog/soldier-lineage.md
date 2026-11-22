---
status: needs-decision
priority: P2
depends-on: []
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
- **The decision (operator): where it's kept.** As first written: JSON lines, one file a run and soldier under
  the state directory. Or rows in the H2 registers beside the `order`, `task` and `fault` tables that
  backlog/authority-and-missions.md step 1 creates. JSON lines can start now and need their own query tool;
  rows wait on step 1, join to missions and tasks by key, and are queried in SQL - but a row a block dug is a
  far bigger table than the registers hold today.
- Don't record what the world model holds each tick: too much, and its staleness is a separate study
  (backlog/world-model-stale.md). Recording a soldier's observations for replay is
  backlog/soldier-state-machine.md's.

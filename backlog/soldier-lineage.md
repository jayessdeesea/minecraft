---
status: proposed
priority: P2
depends-on: []
doctrine: FM 6-0 (the unit journal and the after-action review)
---

# Lineage: what each soldier was told, did, and why - kept for analysis

## Why
Optimising the hierarchy needs the record, not the log (operator, 2026-11-13): for each soldier,
the orders it received and from whom, what it did under each - the routine that had its hands,
the blocks dug and placed, the trips, the waits - and the reports it sent back, as data that can be
queried after the run: how long each order took, where time went, what each CANTCO cost. Today
that is spread over the garrison log as prose (`[Register]`, `[Request]`, `[Keep]`, the
per-soldier status dumps, `[Alert]`), readable by hand one soldier at a time and not at all across a
day. The daily score (`[Score]`, `app.Scoreboard`) counts outcomes, not causes.

## Done when
- Each soldier's run is a journal on disk, one record per event, machine-readable (a standard
  format through a standard library - JSON lines or CSV, never hand-built strings): the order
  received (from which echelon, with its intent), each change of the routine holding its hands,
  each block dug or placed with its tool, each report sent, each alert, with the tick and the
  in-game time. The chain of command's decisions join it: what the general and the squad leader
  were shown and what they answered.
- A query over a day answers, per soldier: time on each task, blocks per hour, time stuck and
  where, orders that were never done and why.
- Replaces the hand-read of the log for the live check after a deploy.

## Notes
- Candidate shape: an `app.Journal` the runner writes to from where it already logs, one file per
  run and soldier under the state directory (`plans/`'s neighbour), rotated with the run name.
- Don't log what the world model holds each tick: too much, and the model's own staleness is a
  separate study (backlog/world-model-stale.md).

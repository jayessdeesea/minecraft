---
status: proposed
priority: P2
doctrine: TC 25-20 (after-action review: sustain and improve); AR 11-33 (lessons learned)
---

# A scorecard for each kind of command, measured in the field

## Why
The soldier's commands will change over time (backlog/echelon-contracts.md). Without numbers, a
command that keeps failing looks the same as one that works. The order trace (`app.OrderTrace`)
already follows an order from the operator down to the soldiers; nothing scores what became of it.

## Built
The dawn review's scorecard: soldier-minutes per task and order kind, the share under each
report, CANTCO reasons ranked, and deaths (docs/military-doctrine.md § 17).

## What's left
- **Each order's life, as events:** issued, acknowledged (WILCO, or CANTCO and why), interrupted
  (contact, hunger, death), finished or abandoned. Each event carries the trace id, the command
  kind and its parameters. They're written as JSON lines under `minecraft.state-dir`.
- **Completion and time:** each command's completion rate, and its time to complete against what
  the command expected - which need the events above.
- **Gaps in the vocabulary:** the signal that a command is missing. A squad leader or general
  wanted something the commands couldn't say: intent prose that contradicts the order (a "gather
  wood" objective with task NONE), the same order reissued over and over, or work done by accident.
  Each is counted.
- **A verdict in the dawn review:** what to keep and what to improve, beside the scores.
- **Closing the loop:** a bad score becomes a squad simulation scenario (docs/architecture.md).
  The command changes when a scenario can't pass without the change.

## Done when
- A garrison's dawn review lists each command kind's completion rate and time, and the gaps.

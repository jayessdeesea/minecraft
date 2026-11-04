---
status: proposed
priority: P2
---

# The general holds a completed order

## Why
The general sees each squad's standing order and must keep it or give a reason to change it
(docs/military-doctrine.md § 5). With `qwen3:14b` as general it still pulls squads out of finished
patrol bases. The reasons it gives contradict what it was shown: "unable to complete the patrol base
due to lack of materials", for a squad whose standing order reads sealed with the squad inside.
`patrol-base-before-dark` (`ChainScenarioLiveIT`) passed 1 of 2 runs judged at dusk.

## Done when
- `patrol-base-before-dark` passes most runs, with the general on the local model or on a frontier
  model, and the difference between the two is recorded.
- A scenario that holds the base through the night, not only until dusk.

## Notes
- Whether to close the gap with the model (a frontier general) or with code (a deterministic rule
  that a sealed base is held until dawn unless the mission changes) is the experiment's call, not
  a default: a rule hides the model's judgement, which is what's being measured.

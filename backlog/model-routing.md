---
status: proposed
priority: P2
doctrine: FM 6-0 (staff estimates: one staff section per function, the commander deciding from them)
---

# Model calls: triage first, then a prompt made for the case

## Why
Each echelon makes one call with one general prompt: the general's system prompt explains every
task, the doctrine and the coordinates every time, and the squad leader's does the same. The
models are small open-weight ones (docs/stack.md). A small model given one long prompt covering
many cases does worse on each case than one given a short prompt for the case it's in, and live
it has shown it: the general re-issued "light the camp" with task NONE, and kept tasks its squads
reported finished.

## The pattern
Anthropic's guidance on agent workflows ("Building effective agents", 2024) names it **routing**:
a first step classifies the input, and each class goes to a prompt (or model) made for it. It
helps where the classes are distinct and handled better apart, and the classification itself is
easy, so a small, fast model can do it. Its neighbour is **prompt chaining**: one call's output
feeds the next, each step simple. The same guidance says to start from the simplest thing that
works and add steps only where they measurably help.

Here:
- **Triage** (one small call, or a deterministic rule where one exists): what kind of decision is
  this squad's - night shelter, a finished or stalled task, a Class I request, contact, the next
  build phase, nothing new?
- **Then a prompt for that kind**, with only what it needs: a squad whose wall is done is asked
  "what next, from these unmet standards", not handed the whole doctrine.
- Many of the triage outcomes are already deterministic - the night rule, the drills - so the
  model is asked only where judgement is needed.

## Done when
- Measured, not assumed: a scenario set (backlog/scenario-matrix.md) run against the one-prompt
  general and the routed one, the routed one making fewer bad orders (task NONE with work prose,
  kept DONE tasks, `[Feedback]` warnings) at no more latency per decision.
- The squad leader's orders are what a soldier runs: the concrete commands of
  backlog/echelon-contracts.md, as structured output with one schema per command - function
  calls, in effect. A vague order is one that maps to no command, and is counted.

## Notes
- Latency matters as much as quality: the request ledger (docs/military-doctrine.md § 4) times
  each echelon's answers, so a routed general that answers later is seen.
- To verify against the current guidance before building: the routing and prompt-chaining
  sections of Anthropic's "Building effective agents", and its prompt-engineering docs on long
  versus focused prompts.

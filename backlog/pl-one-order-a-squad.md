---
status: ready
priority: P1
---

# The platoon leader names every squad, once

## Why
Live (field533-537, qwen3:14b), nearly every decision named a squad twice or left one out: field537 had 6 such
errors in 5 decisions, a reply reading Alpha, Alpha, Charlie, Delta for Alpha, Bravo, Charlie, Delta. Each fails
alone, as it should (`SpringAiPlatoonLeader.toObjectives`: the squad keeps its standing order, or holds), but the
squad it misses gets no new order that decision - and some squads got none all run. The operator (2026-11-20): the
platoon leader needs to give better orders. The roster line already says "exactly one for each of ..., in that
order".

## Options
- **A schema with one required entry a squad**, built per request: the model server enforces it at decoding, so the
  reply can't miss or double one. Spring AI 2.0.2's structured output takes its schema from the reply's class; a
  per-request schema means a provider's own option (Ollama's `format`), which the stack rules keep out of decision
  logic (docs/stack.md) - unless Spring AI offers it through its abstraction.
- **One call a squad**: each asked for its squad's objective alone, the platoon's picture shared. Four times the
  calls; on the local model, slower decisions.
- **Mapping by order**: the roster asks for the listed order, so a reply of the right length could be read by
  position. A guess where names and order disagree - a fallback, which the conventions rule out.

## Done when
- `PlatoonLeaderScenarioIT` over its situations shows no reply missing or doubling a squad, 3 runs each, and live a
  day's decisions show none.

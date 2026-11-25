@AGENTS.md

# Claude Code only

`AGENTS.md` and `docs/` hold every rule of the project and name no tool, so another assistant or editor reads the same
rules. What is wired for Claude Code alone lives under `.claude/` and is listed here; none of it states a rule.

| What | Where | For |
|---|---|---|
| Backlog pass | `.claude/skills/work-on-backlog/` | The procedure of [backlog/README.md](backlog/README.md), as a skill. |
| Garrison watch | `.claude/loop.md` | A bare `/loop`: points at [docs/loop.md](docs/loop.md). |
| `refactor-implementer` | `.claude/agents/` | Writes one behaviour-preserving refactor in its own worktree; never commits. |
| `field-tracer` | `.claude/agents/` | Read-only: one symptom, live or in a failed simulation, down to its cause and the test that would pin it. |
| `equivalence-reviewer` | `.claude/agents/` | Read-only: looks for the input that breaks what a diff claims - no change of behaviour, or the rule it states. |
| `garrison-checker` | `.claude/agents/` | Read-only: one read of the running garrison, a page back. |
| `rule-designer` | `.claude/agents/` | Read-only: the rule to set for a behaviour with none, its rows and its one home. |
| Agent ledger | `C:/mc-state/garrison/agent-ledger.md` | A line a run: what each agent cost and whether it held. |
| `adversary-tester` | `.claude/agents/` | Tests only, in its own worktree: hunts the input whose answer breaks a written rule, pinned as a row with today's answer. |
| Denied edits | `.claude/settings.json` | The operator's `garrison.args`. |

## Delegating
- One writer to the main checkout: the session that runs `tools/ops/commit.sh`. An agent that writes does it in its own
  worktree and reports; its diff is reviewed, brought over when no verify is running, and committed alone.
- Every diff is reviewed by `equivalence-reviewer` before commit, given the diff and its claims part by part, not the
  reasoning behind them. A diff of some size is saved first (`git diff > <scratchpad>/prereview-<commit>.patch`): the
  same diff given to another model is how the reviewer's model is chosen.
- A live check is `garrison-checker`'s; what the force should do where it has no rule is `rule-designer`'s to
  propose and the operator's to say.
- Every agent run is a line in the agent ledger when it reports: the date, the agent, its model, tokens, tool calls
  and minutes as its completion gives them, what it was asked, and the outcome - a reviewer's findings confirmed of
  findings raised, whether a tracer's cause held, whether an implementer's diff passed review and verify first time.
- `field-tracer` takes one symptom each; two or three at once at most.
- `adversary-tester` is given a target and where its rule is written; what it finds with no rule to judge by is a
  question for the operator, not a test.
- No agent commits, pushes, deploys, or sends the garrison an order.
- Models (operator, 2026-11-25): the session that orchestrates runs on Opus; `refactor-implementer`, `adversary-tester`
  and a symptom's first `field-tracer` on Sonnet; `equivalence-reviewer` on the top model, and so a second trace of a
  symptom the first didn't explain, or the design of a new rule, as an agent with a written brief (`model: fable`).
  One task a session, started from the handoff.

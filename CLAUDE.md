@AGENTS.md

# Claude Code only

`AGENTS.md` and `docs/` hold every rule of the project and name no tool, so another assistant or editor reads the same
rules. What is wired for Claude Code alone lives under `.claude/` and is listed here; none of it states a rule.

| What | Where | For |
|---|---|---|
| Backlog pass | `.claude/skills/work-on-backlog/` | The procedure of [backlog/README.md](backlog/README.md), as a skill. |
| Garrison watch | `.claude/loop.md` | A bare `/loop`: points at [docs/loop.md](docs/loop.md). |
| `refactor-implementer` | `.claude/agents/` | Writes one behaviour-preserving refactor in its own worktree; never commits. |
| `field-tracer` | `.claude/agents/` | Read-only: one live symptom down to its cause and the test that would pin it. |
| `equivalence-reviewer` | `.claude/agents/` | Read-only: looks for the input on which a "no behaviour change" diff answers differently. |
| `adversary-tester` | `.claude/agents/` | Tests only, in its own worktree: hunts the input whose answer breaks a written rule, pinned as a row with today's answer. |
| Denied edits | `.claude/settings.json` | The operator's `garrison.args`. |

## Delegating
- One writer to the main checkout: the session that runs `tools/ops/commit.sh`. An agent that writes does it in its own
  worktree and reports; its diff is reviewed, brought over when no verify is running, and committed alone.
- A refactor is reviewed by `equivalence-reviewer` given the diff and the claim, not the reasoning behind it.
- `field-tracer` takes one symptom each; two or three at once at most.
- `adversary-tester` is given a target and where its rule is written; what it finds with no rule to judge by is a
  question for the operator, not a test.
- No agent commits, pushes, deploys, or sends the garrison an order.

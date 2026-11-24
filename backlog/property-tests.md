---
status: ready
priority: P2
---

# Invariants checked as properties, on every build

## Why
A row in a table pins one input; an invariant holds for all of them, and a hand-written case for it is the one input
somebody thought of. The operator (2026-11-24) asked for evals over every combination of combat, and for an adversary
that hunts wrong answers: what that hunt can state for every input belongs here, checked by a property-based test
library on generated inputs, not by an agent's cases.

## Done when
- A property-based test library on `bot`'s and `command`'s test classpath (jqwik, if its engine runs on the JUnit
  platform the build is on; its version pinned in the parent POM, docs/stack.md's table given its row).
- Properties, each pure, the quick tier running them in seconds with a fixed seed said on failure:
  - `Kept`: `a.and(b)` bars whatever `a` or `b` bars; `but(cell)` un-keeps that cell and no other; `holds` is `bars` with
    no rule by kind; `NONE` holds nothing.
  - `Footprint`: the columns of a position always include its floor cell; a position moved by under the body's
    half-width changes its columns by at most one each way.
  - `Contact.respond`: total over every hostile the registry knows, every kit and health; more armour, more health or
    a better weapon never turns a fight into a withdrawal against the same hostile; a hostile moved further off never
    turns a let-be into a fight; the answer reads nothing outside its `Situation` and `Ground`.
  - `AccessRoute.plan`: a route's feet never pass a cell its `Kept` holds but the goal.
  - `Pathfinder`: every step of a route is standable when it was planned; a route is no longer than the search's bound.
  - A template's layout (once they share one interface): a world built exactly to a layout has no drift; one cell of
    it changed has drift naming that cell.
- A property that fails is shrunk to its smallest input and that input added as a row to the table it belongs to
  (docs/conventions.md, "A contact met live is a row first"), before the fix.

## Notes
- The build is on JUnit 6: whether jqwik's engine runs on its platform is the first thing to check; if it doesn't, a
  seeded generator behind JUnit's own parameterized tests, with the seed said on failure, does the same job less well.
- Monotonicity in `Contact.respond` may not hold today (a shield raised stops a sprint): a property found false is a
  finding, pinned as it stands and listed in backlog/engagement-rules.md, not loosened to pass.
- `.claude/agents/adversary-tester.md` names the invariants it meets for this item rather than writing cases for them.

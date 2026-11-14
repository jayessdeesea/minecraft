---
status: proposed
priority: P3
depends-on: []
---

# A model of the flattening protocol, checked

## Why
The keep's foreman (`command.Foreman`, docs/military-doctrine.md § 17) paces the builders in lock
step: one level for the site, the highest top any strip reports; only strips with ground at it dig;
the level falls when every strip is under it. The properties wanted of it are claimed in prose and
tested by example (`ForemanTest`, the squad simulation). The operator wants them shown, and the
alternatives compared, by something stronger than examples (2026-11-14): not necessarily Lean, but
a model a checker explores.

## Done when
- A small formal model of the protocol - strips, tops, reports, clearances, the net's loss and
  delay, a block placed into a strip already done - in a checker that runs in the build or beside
  it (TLA+ with TLC, Alloy, or a property-based exhaustive search in Java over small sites).
- Safety, checked: no builder digs below the site's level; two neighbouring strips never differ by
  more than a step while anyone works; a placed block is cleared before the level falls past it.
- Liveness, checked under fair reporting: the site reaches its floor; a lost line delays and never
  deadlocks (the 30-second repeat).
- A cost model beside it, so "optimal" has a meaning: builder-seconds idle, blocks walked, blocks
  dug per level, for lock step against the alternatives tried (a strip a step under its neighbours'
  edges; a keep-wide window of two) and against the lower bound of every builder digging all day.
  The answer may be that lock step is the safe one and not the fast one; say by how much.

## Notes
- The protocol is small and pure, which is what makes it checkable: `Foreman.decide` is a function
  of the tops heard, and a builder's dig is a function of its clearance and its strip.
- What the model leaves out is where the live failures were: routes that build steps in a
  neighbour's strip, a builder off its strip for food or a fight. Each wants a fairness or an
  adversary assumption stated, not hidden.

---
status: questions
priority: P1
depends-on: []
---

# Questions for the operator, from the overnight loop (2026-11-15)

Each names its backlog item and what the answer changes. Built meanwhile on the default stated.

1. **What a squad released from a flatten does** (backlog/flatten-campaign.md). A sparse level needs
   one or two builders, not sixteen; the squads it doesn't need are released and called back as the
   levels widen. Default built: the platoon leader's own objective for that squad (the mission's
   camp and lodgment work). The alternative is production the campaign wants - wood for its
   depot, the mine for iron. The answer decides who picks the released squads' work: the platoon
   leader, or the foreman.

2. **Whether a builder done with its rectangles takes part of a slow one's before its timeout**
   (backlog/flatten-campaign.md, the foreman's protocol). As built - the operator's rule, wait for
   completion or timeout - one done waits for the level: on the big site's hilltop levels (field239),
   builders done and waiting were about 40% of the crew's ticks, and those never dealt in 19%. The
   alternative is work-stealing: a done builder takes half of the most that's left of one builder's,
   as one come late does now. The answer decides whether held work is ever split but for a newcomer.

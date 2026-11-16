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

3. **Whether the level below may be dug while a spire over it comes down** (the clearing rules,
   highest block first). Field264: ore at (18, 84, -28), set aside till a builder carried the
   pickaxe for it, left a pillar of ground under it six blocks over the level - ground isn't dug
   from under a block held back. Once taken, the pillar was the site's highest ground: one builder
   cut it a level at a time, 84, 83, 82..., about 70 s each, while 1,027 blocks at y=78 waited and
   the rest of the crew stood (80% of its ticks). As built, the rule holds: no level is dealt until
   everything over it is down. The alternative: a column standing over the level by more than a
   step is a spire, cut by the nearest builder, and the level under it dealt to the rest
   meanwhile. The answer decides whether "highest first" is site-wide or column by column.

4. **What becomes of the dirt and cobblestone a flatten digs past what the force can use**
   (backlog/flatten-campaign.md, the depots). A builder's load is mostly dirt and cobblestone -
   300 to 700 of each - and a chest holds about 1,700 blocks: the main depot's six chests hold
   about 10,000, and x=-16..16 z=-64..64 has about 40,000 to dig. Field266 filled four of the six
   within minutes of the restart. As built, once a depot's row is full its loads fail and the
   builder carries on full. The alternatives: (a) the row grows - more chests, wider round the
   stand, logs for each; (b) past a working stock (say a stack each), dirt and cobblestone are
   left where they're dug - not picked up, or dropped in the pit - and only what the force uses
   is carried (ore, coal, logs always). Your earlier word - minerals before dirt and cobblestone
   in the stockpile - reads as (b), but it throws blocks away, so it's yours to say.

5. **How far the flatten's side is cut back where the site is cut into a hill** (the clearing
   rules, rule 6: the bench, 3 rows at 1:1, 29 CFR 1926 Subpart P Table B-1). Field269, 30
   minutes: 323 steps put, three quarters of them on the east bench (x=16..19, z=-40..-50) at
   y=85-86, where the hill beyond the site stands to about y=90 and the floor is at 77. Three rows
   at 1:1 slope only the lowest 3 blocks of a cut 13 deep; above them the hill is a wall, and a
   builder who ends up on it - an errand's route over the hill, or a high cell reached from it -
   is 8 or more over the floor with no walk down, and builds steps to get off, each one the
   level's to dig. As built: 3 rows. The alternative: the bench is as wide as the cut is deep -
   the hill cut back at 1:1 all the way up, or stepped benches every 4 feet (Appendix B's
   benching) - so the side is a slope a soldier walks; it's a great deal more to dig on a hill
   site. The answer decides the bench's width: fixed, or the cut's depth.

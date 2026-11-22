---
status: needs-decision
priority: P1
---

# Huts stacked on huts at the camp's night points

## Why
The operator (2026-11-20): weird raised structures make the camp hard to get about. field539: Olga and Pete stood
stranded at y=72 in the camp (its ground y=64), the column at (23, 89) solid at y=64-68, 71, 76 and 78-79 with air
between - huts on huts, a night's patrol base sited on the last one's roof. The camp's ground work now digs a hut
over the camp's level down with the ground, a ring at a time (`app.CampGround`), so old stacks go as it reaches them.
What made them stack was never found - an intact old hut with its squad on the roof doesn't reproduce it in the squad
simulation (the site search reuses the hut's floor) - so a squad that shelters at the camp can stack them again.

## What's left
- Live, the rings reaching the stacks at (23, 89), and the camp's ground there at 64 again.
- If huts at the camp stay (Notes): the site search never puts a hut on a hut. There is no recording of a stack to
  start from - one is kept first (`app.WorldRecords`), then replayed as a stuck soldier's ground is
  (`StuckEvalsTest`). Candidates for the cause: a doorway cut, a roof broken, two squads at one point.

## Done when
- A recorded camp point with a stack, replayed, gets its hut on the ground.
- Live, a week of nights adds no storey.

## Notes
- **The decision (the operator's): does a squad still shelter in a hut at the camp by night?** Before the buildup it
  does (`NightRules`: shelter in place in the camp); after it, the squads go down the mine. Under
  backlog/authority-and-missions.md by night the squads go to their mine sites (step 5), and a night shelter may be
  shared while building it is owned work (step 3).
  - *No hut at the camp at any phase*: this item is the one live check above, then deleted.
  - *Huts at the camp before the buildup stay*: the second bullet is the work, and the cause is still to find - small
    once a stack is recorded, unbounded until then.

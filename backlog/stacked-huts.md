---
status: ready
priority: P1
---

# Huts stacked on huts at the camp's night points

## Why
The operator (2026-11-20): weird raised structures make the camp hard to get about. Live (field539) Olga and Pete
stood stranded at y=72 in the camp (its ground y=64) with "no route" to points at y=77-78; console probes of the
column at (23, 89) read solid at y=64-68, air 69-70, solid 71, air 72-75, solid 76, air 77, solid 78-79 - huts on
huts, a night's patrol base sited on the last one's roof. The camp's ground itself is now read at y=69
(`SoldierRunner.campGround`: the median surface round the camp, the roofs among it).

A squad-simulation scenario of an intact old hut, its squad on the roof, didn't reproduce it: the site search
reuses the hut's floor there. What makes it stack live - a doorway cut, a roof broken, two squads at one point - is
what's left to find.

## What
Since (2026-11-20): the camp's ground is set at y=64 (`minecraft.camp`'s ground), not read off its roofs; at night the
squads go down the mine, not into huts at the camp; and the camp's ground work goes out a ring at a time to 24 blocks
once its box is level (`app.CampGround`), a hut no squad holds no exception to it - so the stacks within 24 are dug
down as it goes. Left:
1. Live, the rings reaching the stacks at (23, 89) and the camp's ground there at 64 again.
2. The site search: a hut never on a hut, shown in a scenario first (`app.WorldRecords` keeps a stuck soldier's
   ground and `StuckEvalsTest` replays it) - for the night a squad does shelter at the camp.

## Done when
- A recorded camp point with a stack, replayed, gets its hut on the ground; live, a week of nights adds no storey.

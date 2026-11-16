---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.8 ch. 2 (troop leading procedures); FM 6-0 ch. 8 (running estimates); ATP 3-34.40 (general engineering - production rates, site preparation before works)
---

# Flattening as a campaign: resource, survey, plan, work it level by level, reassess

## Why
A flatten (command.Flatten) is run by the foreman one level at a time, but nothing coordinates it
as a whole (operator, 2026-11-15): there's no check it has what it needs before it starts, no
survey of the work, no plan of who it needs when, and no look back while it runs. Every squad on
the site is its crew whatever the level holds, so a sparse level at the top leaves most of them
standing - "waiting, no rectangle" was a third of the crew's ticks on the keep - while a full level
at the bottom wants every hand there is. The coordinator decides where parallelism pays and where
it gets in the way (docs/echelons/tenets.md, "work in parallel").

## The campaign, as troop leading runs it
A state machine the foreman runs (command, pure; its record kept on disk under
`minecraft.state-dir` as the wall, the farm and the mine are), each phase's standard measured from
the world, not from reports, and shown to the squad leader and the platoon leader as the foothold is:
1. **Resource** - the depot's standard met before the dig is staffed: chests enough for what the dig
   will stow, a table and furnace, logs, food, a pickaxe and a shovel for every builder it will
   need (the depot's detail is backlog/keep-campaign.md). What's short goes up as the squad's
   Class IV line, and the platoon leader answers with WOOD or MINE.
2. **Survey** - the site as it stands: the blocks to dig at each level, top down (a profile), and
   what they are (soil, stone, ore, trees) - from the world model, before anyone walks onto it.
3. **Plan** - the crew each level needs, from its blocks: enough builders that each has about a
   standard share (the first figure: 24 blocks a builder, so a 30-block level wants one or two and a
   1,089-block level wants the whole force), never more than the site's columns allow. The plan
   says which levels are the campaign's critical path.
4. **Work it level by level** - the foreman shares each level among that level's crew (LevelPlan,
   built); hands the level doesn't need are released - their squads to the platoon leader's own
   objective for them - and called back as the levels widen.
5. **Reassess** - a running estimate each squad cycle: blocks left, the crew's pace against best
   (LevelPace, built), what the depot and the packs still hold; the plan made again when any of
   them leaves its standard - a shortfall goes back to Resource, a level slower than planned gets
   more hands.

## Done when
- The campaign record and its phases, with the measured standard for each.
- A sparse level's crew is sized to it and the released squads do other work (Question below);
  a full level has everyone.
- Simulation: a site with a spire and then a full floor - one builder takes the spire while the
  other squad works elsewhere, and both are on the floor.
- Live: `[Keep] pace` shows "waiting, no rectangle" under 5% across a flatten, and the dig so far
  within 90% of its best (backlog/keep-pace.md).

## Notes
- Built: the foreman (`SoldierRunner.foremansNet`), its levels (`LevelPlan`), the pace telemetry
  (`LevelPace`), the haul road (`AccessRoute`), the depot's row of chests, the wood party, and
  `Flatten` as the site.
- **Question for the operator:** what a released squad does - the platoon leader's own objective
  for it (the mission: the camp, the lodgment), or production the campaign itself wants (wood for
  its depot, the mine for its iron).

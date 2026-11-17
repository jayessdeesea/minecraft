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
- **Built:** the crew sized to the level under way, squads released and called back
  (docs/military-doctrine.md § 17, "Its crew is sized to its level").
- The campaign record and its phases, with the measured standard for each.
- The survey ahead: the next levels' blocks, so squads are called back before a wide level starts,
  not when it has.
- Simulation: a site with a spire and then a full floor - one builder takes the spire while the
  other squad works elsewhere, and both are on the floor.
- Live: `[Keep] pace` shows "waiting, no rectangle" under 5% across a flatten, and the dig so far
  within 90% of its best (backlog/keep-pace.md).

## Notes
- Built: the foreman (`SoldierRunner.foremansNet`), its levels (`LevelPlan`), the pace telemetry
  (`LevelPace`), the haul road (`AccessRoute`), the depot's row of chests, the wood party, and
  `Flatten` as the site.
- **Built (2026-11-15):** the foreman's protocol - rectangles held till done or due, reassigned on
  timeout, split only for a builder come late; supplies between levels.
- **Why Resource comes first (field246, the big site):** builders left their rectangles mid-level
  for tools and timed out, four of five on one level - one with no pickaxe and "no LOG within 48
  blocks" to make one, one short of a stone sword with "no STONE within 48 blocks" it could walk to,
  one sent for a shovel to the camp's stockpile 70 blocks off and underground, because the depot's
  chest wasn't standing (11 logs between the builders and the stockpile, 16 wanted). The protocol
  handed each rectangle on and one builder finished them all; the crew's time went on the errands.
  Done when: no level is dealt until the depot's chest stands and each builder on it carries a
  pickaxe and a shovel - the depot's logs and tools stocked before the crew is sent.
- **Depots by the site's size - what's left:** built, a depot every 48 blocks along the west edge,
  loads into the nearest, draws from the main one (docs/military-doctrine.md). Left: draws from
  any depot that holds the thing wanted, surplus moved to where it's short (docs/echelons/tenets.md,
  "work in parallel"); each outpost its haul road; Resource checks every depot's standard before
  the crew is sent.
- **Flatten, not the keep, in the code's names:** `keepwork`, `foremansNet`'s keep fields, the
  `[Keep]` log tag and `Objective.Task.Keep` are the flatten's; the keep is one use of it
  (operator, 2026-11-15). The task's name in the platoon leader's prompt changes with it.
- **Errands held till a builder's rectangles are done:** a depot trip with surplus, a draw of food
  while fed, a tool made with one still in hand - none mid-assignment; hunger first, always.
- **Traversal in the split:** blocks weighted by the walk and climb to them, so a builder with a
  hard reach gets fewer (operator, 2026-11-15: about the same blocks each, or fewer if traversal is
  challenging).
- **Released squads, the rest (operator, 2026-11-16):** farm, wood and iron are built
  (docs/military-doctrine.md § 4). Left: hunting - no squad task for it yet - and the mine's stash
  of raw iron, not counted with the surface chests'.
- **A graduated slope where the site is cut into a hill.** Built: the bench is twelve rows, 1:1,
  and the depots and pits stand past it (docs/military-doctrine.md, clearing rule 6). Left: a hill
  higher than twelve over the floor still stands as a wall past the twelfth row - whether that wants
  rows to the full depth, live, once the big site's east side is cut down to it.

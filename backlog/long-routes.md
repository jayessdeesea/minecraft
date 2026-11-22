---
status: ready
priority: P1
depends-on: []
doctrine: FM 3-21.8 (movement: legs between checkpoints)
---

# Routes that still fail: legs planned over and over, ore with no walk to it, one mine to another

## Why
A soldier searches wider once its cheap plans gain no ground, walks a goal beyond that in legs, and from the
surface takes no leg down into a cave (`bot.Mover`: `LONG_PLAN_BUDGET`, `isLeg`, `LEG_AVOID_CELLS`). What still
costs nights is below: each is "no route", or a route walked without arriving.

## What's left
- **A leg planned again and again.** `Mover`'s guard (the WARN "plans the same leg again", the next plan going
  round the leg's first cells) doesn't end it: live (field454), Eli planned one 206-step leg 109 times in a
  night toward his mine's tunnel, and the cutters waiting on his cut waited with him; (field433) Alice planned
  a 69-step leg 31 times, each ending one block short of the goal. Find whether it's the walker or the goal,
  from the WARN's positions; then the fix.
- **The armourer's ore is only ore the planner can route to.** `Armourer.spotFor` takes a stand from
  `Pathfinder.reachable`'s flood, which finds some the planner then can't route to (drops are the likely
  difference): live (field428-438), soldiers were sent for ore 51 and 73 blocks under them, "no route" till
  the dig timed out. Either the flood and the planner agree on what's walkable, or the spot is routed to
  before it's chosen. Only the soldier with no task still digs its makings (backlog/soldier-order.md).
- **From one mine to another: by the stairs, over the top.** Live (field458-463), soldiers down one mine were
  sent to a level of another 97 across with no tunnel between, "no route" all night. A soldier underground
  whose goal is far across (say 48 or more) goes first up its own stairs to their head, then over the top to
  the goal's mine and down its stairs: the legs are the runner's waypoints (the known mine heads), the planner
  each leg's walk. Today only a soldier coming off the night shift climbs out first, within
  `SoldierRunner.CLIMB_OUT_NEAR` of its last mine's head.
- **A stranded soldier held by a hostile fights it, then cuts.** A hostile within
  `SoldierRunner.STAIR_THREAT_RADIUS` holds the stair's cut for as long as it stands there: live (field408), a
  skeleton in Eli's pocket most of the time.
- **A stranded soldier with no pickaxe makes one first**, from a carried table and two logs: live (field360),
  Kate dug an 88-block stair up by hand, about eleven minutes.

## Done when
- Live, no soldier reports "no route" for a goal it could walk to, and none plans one leg more than a few
  times from one cell.
- A simulation for each of the above: the leg (from the recorded ground, as `StuckEvalsTest`'s cases are); ore
  the flood reaches and the planner doesn't, not gone for; a soldier down one mine reaching a level of another;
  a stranded soldier with a hostile at hand out by its own stair; one with no pickaxe making it first.

## Notes
- The recovery drill reading miners in their tunnels as stranded is backlog/mining.md's.
- A gather 50-60 blocks from the camp is where soldiers were lost (a creeper, pillagers): how far a squad is
  sent for wood is the platoon leader's (backlog/authority-and-missions.md, step 3, areas of operations).

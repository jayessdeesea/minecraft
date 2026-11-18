---
status: ready
priority: P2
depends-on: []
doctrine: docs/conventions.md (profile every sixteenth commit)
---

# Route planning is a third of the garrison's time

## Why
The profile at commit 768 (three minutes of the live garrison, 11,662 execution samples): 35% of them
in `ChunkCache.rawStateIdAt`, nearly all of it the route planner asking cells whether they're solid
and a hazard (`Pathfinder.open < clear < standable`). By the caller that planned:
- `DeterministicSoldier.navigate` - 2,304 samples: soldiers planning their routes, some again and again
  (backlog/long-routes.md: a leg planned 109 times in a night).
- `SoldierRunner.cutOff` - 753: a reachability flood each squad cycle for every soldier with a recent
  "no route" or an idle alarm, whether or not it has moved.
- `DeterministicSoldier.priceOfARiskierRoute` - 322.

The lookup itself is already memoised by chunk. Asking each cell once instead of up to three times (a
fused `isOpen`) was tried and measured (10,933 samples, the lookup still 39%): the cost is how often
routes are planned, not what a cell costs.

## Done when
- A soldier that hasn't moved, with the world round it unchanged, doesn't plan the same route again:
  `cutOff`'s flood kept per soldier while its cell holds, a route replanned only when its goal, its
  cell or the blocks on it change.
- The next profile shows the route planner's share halved, both sample counts in the commit.

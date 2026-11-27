---
status: needs-decision
priority: P1
---

# A CANTCO belongs to the work it came from, and a repeated one changes something

## Why
A soldier whose staircase stops over a hollow the bridge can't floor loops for hours: live (run field749, 2026-11-26)
Ivy and Kate of Charlie alternated "cut the mine" and "dig the camp's ground" every 1-2 minutes for 11 hours, about 550
CANTCOs each on one reason ("no floor under step 22 ... not floored: 24 blocks put under it already"), with no
`[Retry]`, no `[Charlie -> PL] CANTCO` and no change of order. Four causes, each read in the code at 1cee93e3:
- The tick reports the stairs' CANTCO whatever the soldier is doing (`SoldierRunner` report chain, the stairs branch
  ahead of the clearer's), and `TaskRegister.next` files a soldier's report against whatever task it holds this cycle -
  so the mine's CANTCO is filed against the dig too. The register is also given the cycle's snapshot, so at any change
  of task the report is the one made under the task before.
- Only a STALLED tasking reaches `relieved` (`Setbacks`); a CANTCO tasking is removed and given afresh, never counted
  or barred, against "A retry changes something" (docs/conventions.md).
- What hands the soldier the dig is `PoliceCall`: a soldier stood a minute on a CANTCO is given a step to take back,
  never dug while the mine holds it at the head, passed over after `GIVE_UP_MILLIS`, and back to the mine.
- A stop is told from another by its words: only "water or lava beside step" closes a shaft (one-home.md A2), so a
  hollow never does. `SoldierRequests.track` reads another kind of order, or a move past `ANSWER_MOVE`
  (`DayWorkRules.answers`), as the squad leader's answer - the police call's dig among them.

## Done when
- A `TaskRegisterTest` row: a CANTCO raised on the task before isn't filed against the new one.
- A `MineSimulationTest` scenario: a stair stopped over a hollow it can't floor - today it loops; after, the soldier's
  CANTCO is counted with `[Retry]`, the shaft is dealt with as decision 1 says, and no task is CANTCO'd on another's
  reason.
- Live: `tools/ops/soldier.sh <name>` shows no CANTCO reason filed against two tasks.

## Notes
The design, its rows and commit sequence: `C:/mc-state/garrison/design-cantco-register.md` (2026-11-26). Commits 0, 1
and 3 touch only `command`, `bot` and tests; 2 and 4 edit `SoldierRunner` (the dead-well test, the stairs report line,
`register()`'s relief loop) and are sequenced after the parallel-flattens work there lands. `SoldierRunner` net zero.

The operator's decisions (the design's recommendation in brackets):
1. A stair stopped over a hollow it can't floor: (a) closes the shaft as water does - a mission's shaft paused, the
   camp's or keep's a dead well and the next site; (b) the squad waits at the head and says so up the chain. [(a)] -
   but mining.md already states (b): "A stair that can't be cut says so up the chain, and the squad waits at the head -
   it doesn't go down elsewhere."
2. A well made dead by a hollow: (a) dead for good; (b) kept with its kind, reopened when bottom-up fill is built.
   [(a) now, (b) with mining.md's bottom-up fill]
3. A CANTCO'd tasking relieved as a STALLED one is (5, 10, 20, 40 min) on dig, fill, wall, stone wall and lights. [yes,
   the last commit]
4. After three reliefs on one task: (a) the count and `[Retry]` logged, nothing more; (b) the squad's own CANTCO to the
   platoon leader. [(a): (b) lets one soldier's CANTCO take a squad off a mission's step]
5. A request answered only once its CANTCO has cleared under a changed order. [yes]
6. The register's rule now, `Report.Cantco` typed by its work left to one-home.md A2; or the typed work first. [now]
7. The police call to a soldier held at the stairs: (a) a backlog row; (b) into this change. [(a)]

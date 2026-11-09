---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (troop leading procedures, step 8 "supervise and refine"); FM 6-0 (running estimates, tracking tasks and suspenses); ATP 6-0.5 (command post battle tracking)
---

# A task register at each echelon: tasks followed to completion, trust but verify

## Why
The general and the squad leaders decide from what's in front of them on each call, and keep
almost nothing between calls: each squad's current objective, and the request ledger's open
requests. Nobody holds the list of what was asked, of whom, since when, and whether it's really
happening. Live, that's how a soldier spent a night opening a chest that wasn't there: nobody
above it was checking that the stockpiling it was doing ever worked. The operator's expectation:
an echelon that gives a task keeps track of it to completion - who's on it, their regular updates,
a check of its own now and then that the work is real - and on completion either remembers it (a
base) or forgets it (a tree felled).

## Doctrine
- **Supervise and refine** is the last step of troop leading procedures (ATP 3-21.8): the leader
  who gave the order checks it's being carried out, on the ground, not only by report.
- **Battle tracking** (ATP 6-0.5): a command post keeps each task with its owner, its suspense (when
  it's due) and its status, and updates it from reports and from its own observation.
- **Running estimates** (FM 6-0): each line of effort's status against what was planned, kept
  between decisions, so the commander continues a plan rather than starting again.

## Design sketch
- **One register type in `command`, pure**, held by each echelon that tasks: the general over its
  squads, the squad leader (its drills, and its model) over its soldiers. An entry is a tasking:
  what (the order or command, `backlog/echelon-contracts.md`), who, given at, its suspense, the
  last report and when, the last verification and what it found, and its state - under way,
  done, stalled, failed, superseded.
- **Regular updates are the reports already sent** - each soldier's status every second, each
  squad's SITREP - recorded against the tasking they answer. A tasking with no update past its
  report interval alarms, as an overdue request does (`app.RequestLedger`, which the register
  absorbs: a request is a tasking going up).
- **Trust but verify:** each tasking type names a check from the world - the wall's open cells
  falling, the light plan shrinking, the logs over the camp going, the chest's contents seen. The
  echelon runs it on its own clock, not the soldier's say-so, and a report the world doesn't bear
  out ("Fortifying" with no block placed in two minutes) is a finding: the tasking is marked
  stalled and goes back to whoever allocates it. `command.Progress` is the first of these checks.
- **The ladder ends here.** A soldier that's out of ways of its own reports CANTCO
  (`bot.supply.Setbacks`); the register holds it open against the tasking until the echelon
  answers it - another task, another place, another soldier - and the next echelon up sees it if
  it isn't.
- **On completion, kept or forgotten by kind.** A tasking that leaves a site - a base, a wall, a
  workshop, a farm, a mine head - hands it to the operations map (`backlog/operations-map.md`),
  which persists. One that leaves nothing - a tree felled, a load carried - is dropped once done,
  after the dawn AAR has counted it.
- **What the models see:** each squad's open taskings, one line each with state and age - so the
  general continues its plan and sees what's stuck, rather than re-deciding from a snapshot.

## Done when
- Every tasking the general and the squad leaders give is in a register, with its owner and its
  last report, visible in the log and the dawn AAR.
- A tasking whose report the world doesn't bear out is marked stalled within its check interval,
  in a squad simulation scenario (a soldier reporting work on a cell it can't reach).
- Completed site taskings appear on the operations map; completed transient ones are gone from
  the prompt.

## Built so far
- The squad's register over its soldiers (docs/military-doctrine.md § 4): taskings recorded,
  verified against the world's measure, CANTCOs held, ends counted in the dawn review. Left: the
  general's register over its squads, a STALLED finding acted on (re-tasked by the squad's drills,
  or seen by the general), RequestLedger folded in, and completed sites handed to the map.

## Notes
- Built before the register, its pieces: `command.Progress` (a squad's task done or stalled, from the world),
  `app.RequestLedger` (requests timed and alarmed), `bot.supply.Setbacks` (a soldier's escalation
  to CANTCO), `app.WallStore` (one site, persisted).

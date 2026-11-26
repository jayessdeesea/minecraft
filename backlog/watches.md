---
status: needs-decision
priority: P1
depends-on: [landing-queue]
---

# Watches on a clock that spin off work

## Why
The operator (2026-11-26): the refactor pass should "wake up periodically, and see if/where a refactor should happen"
rather than fire on the commit count; Java Flight Recorder should run periodically to find runtime hotspots; the logs
should be read periodically "and find stuff like soldiers sitting idle, looping, getting hurt or killed in battle, or
being unable to accomplish missions. We want these loops to run and to spin of work"; and "I wonder if we need a
periodic task to re assess our evals, are they duplicate, overlapping." A watch's run is a stream of work like any
other: in a worktree of its own, serial within, landing through the queue ([landing-queue.md](landing-queue.md)).
Today only the garrison watch exists, run by hand, and the profile and refactor passes fire on the commit count.

## Done when
- The garrison watch is detectors on a clock, a model session started only on a trigger, with no model reading quiet
  hours (the operator, 2026-11-26: "Yes, detectors first").
- Refactor, profile, backlog and eval watches each run on a clock, decide from their own measures whether a pass is due,
  and file or land at most one thing a run.
- The commit-count triggers in docs/conventions.md are retired in the same change that puts the refactor and profile
  watches on their clocks, never before.
- Each watch's procedure is a file under docs/watches/, what they share is its README, and docs/ names no tool.

## Notes

### Decisions waiting
1. The name: "watch" for the duty, docs/watches/ for its home; the mechanism (a scheduler, a session's loop) only in
   CLAUDE.md and DEVELOPMENT.md. Recommended.
2. The garrison detector design's nine recommendations (C:/mc-state/garrison/design-garrison-detectors.md, section 11):
   the seam (prose and a contract test now, typed signals behind a ratchet); a Java `watch` module; a session started by
   an OS launcher when a brief exists if permitted, else briefs queued for the operator; one session at a time, 30 min
   apart, eight a day; attended deploys with the canary in shadow; no watch posts to /chat; one decision register in the
   state directory; a triggered session builds only where a written rule gives the answer; a squad with no task and no
   mission open is a finding at the platoon leader.
3. The eval watch's design, after the `watch` module exists, reusing its findings and fingerprints: duplicate rows,
   overlapping tests, knownFailure rows that now pass, tests nothing else needs, the slowest.

### The design so far
The detector design (summary; full text in the state directory until it's built): the app senses as today ([Alert],
[Idle], [Loop], [Retry], [Outcome] loop, [Fault]); a pure-Java `watch` module judges over windows - twelve detectors,
each a pure decision with its table and replay hours, emitting findings fingerprinted by cause so one cause is one
finding; `Trigger.decide` starts a session only for a new cause or one doubled since last briefed; state in
`C:/mc-state/garrison/watch/`; recorded incident hours replayed in `quick.sh`; a contract test on the log lines it reads;
a silent-detector check; a shadow period before any session is started for real.

What any watch design must hold, from a review of a first draft of shared rules:
- **Deploy from the checkout whose state the garrison keeps.** `deploy.sh` and `start-garrison.sh` start the garrison
  from their own repository's toplevel, and `minecraft.state-dir` (`garrison-state`) is relative to it: a deploy from a
  per-run worktree starts on empty state in a directory the run then removes.
- **Stuck triage reads `../garrison-state/worlds`** (`StuckTriageIT`) and is skipped where it isn't: a watch in a fresh
  worktree needs that path given to it.
- **One stop rule per watch.** "All the goals met" stopping the garrison watch durably contradicts reading what the force
  is sent to do whatever the goals say; goal-based stop conditions mean nothing to the refactor, profile or backlog
  watches.
- **What the garrison watch may build** is said once: the procedure's "fix it at the root" and a cap of "only a fix to a
  rule already written" can't both stand.
- **A "due" measure is since the watch's last pass**, kept in its own state: `hotspots.sh` windows on commits (8, 64), so
  two runs with no commit between read the same list and "returned to twice running" fires on no new evidence.
- **Nothing a dead run leaves blocks the next for long**: a lock held, a finding marked taken.
- **A priority is the operator's** (backlog/README.md): a triage watch proposes demotions, it doesn't make them - 35
  items are P1 today.
- **One home each** for a finding's expiry, the profile's due rule, the lock and stop files, each cadence.

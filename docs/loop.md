# The watch loop

How an agent watches the running garrison toward goals the operator has set, pass after pass, with nobody at the
keyboard (operator, 2026-11-23: watch over the runtime for consistent goals). Started with `/loop` and a prompt that
names the goals and points here; this file is the standing procedure, the prompt only what's particular to the run.

## What a run is given
- **The goals**, each with what "met" looks like in the world or the registers - "the tree farm mission at chunk
  (2, 3) DONE", not "improve farming". The loop stops when they're all met.
- **What it may order.** Orders to the garrison go through the operator's chat (`POST /chat`), and only for the goals.
- **What it may not touch.** `C:/mc-state/garrison/garrison.args` is the operator's: `.claude/settings.json` denies
  editing it, and any shell command that names it. It's read with the Read tool.

## State between passes
A pass may begin in a new context with nothing remembered, so what a run knows is on disk, and a pass starts by
reading it - not by rediscovering it:
- **The goals** - `C:/mc-state/garrison/watch-goals.json`: each goal, what "met" is, its status (`open`, `met`), since
  when, and the evidence. A pass changes a goal's status and evidence, never its wording: the goals are the operator's.
- **The watch log** - `C:/mc-state/garrison/watch-log.md`, its last entries: what the last pass found, changed and left.
- **The repository** - `git log --oneline -8` and `git status`: what's committed, what's pushed, what's still in the tree.
- **The running build** - the last line of `C:/mc-state/garrison/runs.log` against `origin/main`.
Then a health check before any new work: the garrison answers (`GET /clock`), the Minecraft server is up, and the
build running is the one last deployed. A pass that finds the last one's work broken mends that first.

## A pass
1. **Stats first.** `GET /status` and the latest hourly logs (`C:/mc-state/garrison/logs/`), against the last pass's
   numbers in the watch log:
   - **Deaths** - each soldier's `deaths` in its condition, and `[Casualty]` lines. A new death is dug into before
     anything else: who, where, what killed it, what it was ordered to do, whether a change of ours caused it.
   - **Each goal's progress and its rate** - `[Mission]` lines: columns, cells, fixtures left. A count that hasn't moved
     since the last pass is a stall, and a stall is dug into, whatever else is going on.
   - **Retries that aren't working** - `[Retry]` (a thing failed four times running at one target, said again at each
     doubling: `bot.supply.Setbacks`), `[Outcome] loop` (four trips running that moved nothing: `app.ErrandLedger`),
     `[Verify]` (a task's count not falling), `couldn't` and `gives up` lines, crowding alerts. Each is somebody not
     accomplishing what it was sent to do: dug into from its count and its reason, not waited on.
   - **Orders, followed or not.** An order is followed down the chain: what was given (`[PL -> squad]`, the squad
     leader's FRAGO, a soldier's `[Register] ... on <task>`), whether it was taken (WILCO, CANTCO, "no decision -
     keeping the current objectives", a `[Guardrail]` putting a squad on something else), and how long from given to
     worked. Three things are dug into, each as a blocker is: an order **not followed** - given and never worked, or
     overridden every cycle; one that **takes too long** - given at dawn and its squad on it an hour later, a mission
     opened and nobody on it; and one **mangled or unclear** - cut short, over-long, naming a place or a thing that
     isn't there, or reading differently at the echelon below. On the day this was written every platoon decision was
     dropped for an hour over one order 257 characters long, and nothing but the squads standing in the mine by day
     said so.
   - **Anti-entropy, and why it ran.** Whenever a built site is put back to its template - a review reopening one, the
     conformance pass fixing a cell - the log says what was different and, where it can be told, who or what made it
     so (`[Place]` and `[Dig]` name the routine). A difference that comes back, or one the force itself made, is a
     defect upstream of the repair: found and fixed there, not left to the repair to keep mending.
   - **Faults** (`[Fault]`), STALLED and CANTCO soldiers, squads with no task, the night's ore.
2. **The biggest blocker to the goals, one.** Found from the stats, not from a list of ideas. What doesn't serve the
   goals goes to `backlog/`, not into code.
3. **Reproduce it first: a goal that isn't moving gets an eval, in that pass.** Not a reading of the log and a guess:
   a simulation or a unit test of the thing the goal is waiting on, written to fail for the reason seen live - and
   more than one where the cause isn't sure, each a different guess at it. An eval that passes has ruled its guess
   out, which is worth as much: on the day this was written a tree farm had stood five hours; "a floor laid out over a
   pit" passed, "a trunk over a pit given ground" failed, and the second was the fault. The evals stay in the suite.
   **For a soldier that can't get somewhere, the eval is already half written.** Whenever a soldier stands stuck the
   garrison keeps the ground round it and drafts a case beside it (`garrison-state/worlds/<soldier>-<time>.json` and
   `.case.json`: where it stood, what it carried, where it was going). So the first move isn't a scenario built by
   hand: run `StuckTriageIT`, which replays the newest of those cases, each soldier alone on its recorded ground, and
   writes a table (`app/target/stuck-triage.txt`). REACHED: alone it gets there, so live it was held by something else -
   a crowd, its tasking, another soldier's block - and that is where to look. STUCK: a fault of the soldier's own, or a
   gap in the simulated server (both have been found this way); read its replay's log
   (`app/target/sim/triage-<case>.log`), fix it, and register the case under `app/src/test/resources/stuck/` so it
   holds the fix (docs/architecture.md, the stuck evals). BEYOND: its goal lay past the ground kept - no verdict. A
   thousand cases were drafted before any were read; a pass that sees "no route", CANTCO or a soldier stationary reads
   the newest ones.
4. **Fix it at the root**, by the project's rules (AGENTS.md). A retry that repeats what failed is not a fix
   (docs/conventions.md, "A retry changes something") - and that holds for the loop itself: two fixes for one blocker
   that didn't move its goal, and the third isn't another patch. What was learned is written in the watch log, and the
   blocker is looked at afresh - its eval and its log handed to a subagent with a clean context, told the facts and
   not the theory.
5. **Prove it before the verify.** `sh tools/ops/quick.sh`, then the slow simulations the change touches, by name: a
   verify takes 28 minutes, and one failed on a test that could have been run first is half an hour lost.
6. **Commit** with `sh tools/ops/commit.sh <message file>` - docs and backlog in the same commit. Serial: never two at
   once, and the tree isn't edited while a queued commit is about to stage it.
7. **Deploy** with `sh tools/ops/deploy.sh`, only a verified commit, and not more than once in 45 minutes: every
   restart takes the squads off their work. Fixes are batched into one restart where they can be.
8. **Canary.** Five to ten minutes after a deploy: faults, loops, retries, deaths and each goal's rate against before
   it. Clearly worse, the commit is reverted through `commit.sh`, redeployed, and why is written down.
9. **The watch log, and the goals.** One dated entry a pass, appended to `C:/mc-state/garrison/watch-log.md`: the
   stats, what was found, what was changed, what was deployed, deaths and their causes, and what needs the operator's
   decision - with the lines it rests on quoted, a test's pass or fail as its output said it, not as remembered. It's
   what the operator reads on coming back, so it says what's true, not what was tried. A goal is `met` in the goals
   file only on its own evidence - the register's row DONE and the thing seen standing in the world - never because
   progress is visible.

## Stopping
All the goals met; or three verifies failed in a row; or the garrison or the Minecraft server down and a restart not
bringing it back; or six passes running in which no goal's measure moved - the loop is then spending the operator's
time and money on nothing, and says what it tried. Stopped, the loop says why in the watch log and to the operator.

## Pacing
A pass ends by scheduling the next: when a commit's verify or a deploy's canary is due, else in 20 to 30 minutes. A
background job finishing wakes it sooner. The loop runs only while its session and the machine stay up, and a
self-paced loop isn't restored when a session is resumed: it's started again. A bare `/loop`, with no prompt, runs
`.claude/loop.md`, which points here and at the goals file; `/loop` with a prompt ignores that file, so a run's
prompt names this file itself.

## Where this comes from
The project's own failures, and Anthropic's guidance for long-running agents (its engineering notes on harnesses for
long-running agents, on building agents and on context engineering, and Claude Code's best practices and scheduled
tasks pages, read 2026-11-23): state kept in files and git and read at the start of each session; a structured goals
list whose status alone changes; a check that the last session's work still stands before new work; one thing an
iteration; ground truth from the environment at each step; done defined up front; a stop condition; and a fresh
context after two failed corrections.

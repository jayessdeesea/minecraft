---
status: ready
priority: P1
---

# Landing many streams of work: what is left of the local merge queue

## Why
The queue is built and is how a change lands (docs/conventions.md, "Landing a change"); the hook refuses any other
push to main on this machine. Only this machine pushes to main (operator, 2026-11-27), so the hook is the whole of the
refusal - a `--no-verify` push gets past it and only the audit names it, after the fact; GitHub's own rules are
long-term work, not this item's. What is left makes the queue sturdier, not more of a gate.

## Done when
- Every commit on main past the cutover carries one `Landing-Id` and the journal's `Verified` for it on its parent
  (`landing audit` clean, nothing noted as unchecked), and the items below are built.

## Decided (operator, 2026-11-26), not yet carried out
- No batching; two verifies side by side measured before any change to throughput.
- The journal is kept whole; compaction is later work (the audit asks for it past 100,000 records).

## Left, in order
1. **Thread dumps before a stall's kill** (operator, 2026-11-27: yes, the commit after the watchdog): `jcmd <pid>
   Thread.print` for each JVM in the job, appended to `verify.killed.log`, bounded at 20 s each, then the kill - what
   tells a deadlock from a wait.
2. **A reinstall that never waits for an empty queue** (operator, 2026-11-27: to design): with several streams the queue
   is rarely empty, and every waiting `land` holds the installed jar open, so "reinstall when nothing is landing" can
   starve. Install writes `landing-<sha>.jar` beside the old one and switches what starts it under the integrator's
   lock; a running waiter keeps the file it started from, nothing is overwritten in place, and a jar no process holds is
   pruned. A design first; best guesses (operator, 2026-11-27: take them) for its two open questions:
   - **What starts it switches by one file, so the task is not registered again.** The task's action and `land.sh` both
     run `java(w) @C:/mc-state/landing/landing.args`, a Java argument file naming `-jar landing-<sha>.jar`: the one home
     of which jar is installed. Install replaces it whole (a temporary file moved over it); the JVM reads it once at
     start and holds nothing. `land.sh` then never changes per install - it must not, since `sh` reads a running script
     as it goes. The task is registered again only when the template changes; Task Scheduler is expected to leave a
     running instance on its old definition, and the install checks that once: an instance running before the
     registration is still running after it, or the install says so.
   - **A waiter on an old jar that meets a line it cannot read answers 5, land again.** The codec stays strict (a line
     it would not write back is no record). A waiter that meets one, while the args file names another jar than its
     own, exits 5 saying landing was reinstalled; its entry is still queued, and the next `land` waits on it from the
     new jar - a retry that changes something. A newer jar reads every older line, as the journal is kept whole. Where
     the jar is still its own, the unreadable line is a fault, 6, as today. Pruning is a delete that Windows refuses
     while a JVM holds the jar: refused, kept for the next install's prune.

## Unsure
- A writer who runs plain `git rebase origin/main` on a branch whose landed change was since reverted re-adds it; only
  review of the squash diff catches that.
- Whether `deploy.sh`'s `mvn install` into the shared `~/.m2` during a verify can change what the verify resolves.
- A reinstall while a `land` runs from the installed jar may fail at the jar's replacement (not tried): reinstall when
  no session is landing, until the reinstall that never waits (Left, 2).
- Whether the stall limit (15m) and the cap's floor (90m) fit a verify at normal priority: each verify's `longest quiet`
  and its time against expected, on `landing-task.log`, say whether either should move.
- Whether replacing `verify-progress.json` can fail on Windows while a waiter has it open: a waiter that cannot read it
  takes it as no verify running for that look; a write that fails is said once.
- Nothing bounds `run.budget.minutes` to a day, while a pass is kept only up to 24h: past that, a passing verify would
  join no history, unsaid. Unreachable at the default 180m.
- An absent read of main's ref (rev-parse exit 1, likely git on Windows deleting then renaming a ref another process
  holds open) is read again, through `Gather.mainTip` alone; whether git can instead answer 128 mid-rename, which still
  fails at once, only a live symptom's text will say. `MainRefReadsTest` guards the one home by spelling, not by
  meaning, and `tools/ops/deploy.sh`'s bare `git rev-parse origin/main` is outside it.
- `job-check` gives a false NO when `JAVA_TOOL_OPTIONS` or `_JAVA_OPTIONS` is set (neither is, here).
- `commit.sh` says the commit count only when the landing exits 0 to its own waiter: one that ends 5 (more in the
  worktree, or still queued at `--timeout`), one whose session died while it waited, or one found landed by a later
  `commit.sh` that answers 2 does not say a profile or refactor pass fell due on it - `git rev-list --count
  origin/main` does. Gone when the passes come off the commit count (watches.md).
- The slowest tests `commit.sh` prints after a landing are read from the landing worktree's reports: with another
  entry queued, they may be that entry's verify, or one part-way through, not this landing's.

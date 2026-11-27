---
status: ready
priority: P1
---

# Landing many streams of work: what is left of the local merge queue

## Why
The queue is built and is how a change lands (docs/conventions.md, "Landing a change"). Until the cutover a push to main
from outside it - an old branch's `commit.sh`, a hand - is neither refused nor audited, and after it only this machine
refuses.

## Done when
- Every commit on main past the cutover carries one `Landing-Id` and the journal's `Verified` for it on its parent
  (`landing audit` clean, nothing noted as unchecked).
- A push to main from outside the queue is refused on this machine (the hook) and, after the operator's GitHub work, at
  GitHub.
- `C:/mc-worktrees/verify` is gone.

## Decided (operator, 2026-11-26), not yet carried out
- GitHub: a ruleset on main (no force push, no deletion, linear history) and updates restricted to a deploy key the
  integrator alone uses - the operator's hands.
- The hook at the cutover at once, announced.
- No batching; two verifies side by side measured before any change to throughput.
- The journal is kept whole; compaction is later work (the audit asks for it past 100,000 records).

## Left, in order
1. **Cutover**, the operator's timing: `landing cutover <sha>`; copy `tools/ops/landing-pre-push` to the shared hooks
   directory as `pre-push` and record its SHA-256 as `hook.hash` in `C:/mc-state/landing/config`, or I8 names it. Every
   old `commit.sh` then fails at its push with the hook's message naming `land.sh`; a session mid-verify loses one verify.
2. **GitHub** (C5): the ruleset and the deploy key; the integrator's remote switched in the config (`remote`), SSH by
   `GIT_SSH_COMMAND` in the task; a docs line for it.
3. **Retire `C:/mc-worktrees/verify`** once no branch runs the old `commit.sh`.

## Unsure
- Whether a personal-account repository's rulesets allow "restrict updates" with a deploy key as bypass actor.
- A writer who runs plain `git rebase origin/main` on a branch whose landed change was since reverted re-adds it; only
  review of the squash diff catches that.
- Whether `deploy.sh`'s `mvn install` into the shared `~/.m2` during a verify can change what the verify resolves.
- A reinstall while a `land` runs from the installed jar may fail at the jar's replacement (not tried): reinstall when
  no session is landing.
- `job-check` gives a false NO when `JAVA_TOOL_OPTIONS` or `_JAVA_OPTIONS` is set (neither is, here).
- `commit.sh` says the commit count only when the landing exits 0 to its own waiter: one that ends 5 (more in the
  worktree, or still queued at `--timeout`), one whose session died while it waited, or one found landed by a later
  `commit.sh` that answers 2 does not say a profile or refactor pass fell due on it - `git rev-list --count
  origin/main` does. Gone when the passes come off the commit count (watches.md).
- The slowest tests `commit.sh` prints after a landing are read from the landing worktree's reports: with another
  entry queued, they may be that entry's verify, or one part-way through, not this landing's.

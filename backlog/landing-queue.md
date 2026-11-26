---
status: ready
priority: P1
---

# Landing many streams of work: a local merge queue

## Why
The operator works in streams (2026-11-26): "I have a bunch of async ideas I wnat to queue up to work in parallel, just
like having a bunch of humans work together", and "I'm ok opening up a new session for every idea and working on them
all independently. This will be async work, just like the watchers will introduce async work. Within each stream of
async work, the stuff will be single threaded and serial." Today main has one writer (`tools/ops/commit.sh` verifies
in one shared worktree and pushes with no fetch and no lock), so streams can't land without stepping on each other.
The architecture is the one human teams use - a change a pull request, CI as the gate, a merge queue, branch
protection - built on this machine, since the verify needs local reference data. The operator asked for the controls,
not a patched script ("I want to figure out a stable architecture, don't just fix things, let's get the right
ocntrols in place").

## Done when
- A stream - an idea or a watch's run, in a session and worktree of its own, serial within - lands its change with one
  command and gets one of the exit table's answers; any number of streams submit at once and none touches another.
- Every commit on main past the cutover carries one `Landing-Id` and the journal's `Verified` for it on its parent
  (`landing audit` clean), with the crash-injection and contention tests in the suite.
- A push to main from outside the queue is refused on this machine (the hook) and, after the operator's GitHub work,
  at GitHub.
- docs/conventions.md has "Landing a change" (the invariants and the exit table), DEVELOPMENT.md the commands,
  CLAUDE.md's one-writer rule replaced, and the watches put on their schedules.

## Decided (operator, 2026-11-26: the ten recommendations accepted)
1. Landed is a `Landing-Id` trailer on main. 2. A Java module, `landing`, in the reactor. 3. The integrator started by a
Windows Task Scheduler task only (`schtasks /create` once). 4. GitHub: a ruleset on main (no force push, no deletion,
linear history) and updates restricted to a deploy key the integrator alone uses - the operator's hands. 5. A commit
from outside the queue: fault and carry on until adopted. 6. A failed entry leaves the author's commit in place.
7. Branches after landing: rebase only. 8. Tool updates by a deliberate reinstall. 9. The hook at cutover at once,
announced. 10. No batching; two verifies side by side measured before any change to throughput.

A stream has one landing in flight at a time; streams meet only at the queue. At about two landings an hour, many open
streams wait their turn: the journal records wait times.

## The design
### The three structural causes
Seven classes of fault - two at once, pushing what was not verified, retrying without end, a crash between push and
record, a stale branch landing a change twice, outcomes that do not mean what they say, nothing guarding main - come
from three choices. The design makes none of them.

| A choice that makes a queue fail | The faults it brings | Instead |
|---|---|---|
| Liveness of the lock holder inferred from a pid file, an mtime and a heartbeat | Mutual exclusion | A lock the kernel holds and releases: `FileChannel.tryLock`. No staleness, no breaking, no heartbeat. |
| "Landed" as a second record written after the push, then guessed back from a failed pick | Crash between push and record; double settlement | Landed is a fact of main: the commit carries its entry's id as a trailer. Nothing is written afterwards and nothing is inferred. |
| The thing pushed read back from a shared worktree; outcomes classified from error text | Pushing what wasn't verified; unbounded retry | The candidate commit is built with no worktree and held as a value. Outcomes are classified from observed state (fetch, then look). |

A fast-forward push of a commit whose parent is the main it was verified on is already an atomic compare-and-swap at the
remote. So "verified on the main it landed on" holds even if mutual exclusion failed, provided the pushed SHA is the
verified one and nobody else pushes. The lock protects throughput and the journal, not main's correctness.

### 1. The unit and its state
**Entry**: one request to land one squashed commit. Its id is `<UTC timestamp>-<8 random hex>`, chosen by the submitter.

| State | Home | Sole writer | Lives |
|---|---|---|---|
| Request: ref `refs/queue/<id>` pointing at the squash commit. Its message carries trailers `Landing-Id`, `Landing-Source` (the writer's head) and `Landing-By`. | The shared repo | The submitter creates it, create-only (`git update-ref <ref> <sha> ""`). The integrator deletes it. | Until terminal |
| Attempts, verify results, failed/bounced/withdrawn outcomes, faults, halts | `C:/mc-state/landing/journal.jsonl`, append-only JSON lines (Jackson), fsynced per record | The integrator, only while holding the lock | Rotated monthly |
| Landed | origin/main: a commit bearing `Landing-Id: <id>` | The integrator's push | Forever |
| Integrator exclusion | An OS lock on `C:/mc-state/landing/integrator.lock`; the file is never deleted | The kernel | The holder's process life |
| A writer's last submission (id, source head, base) | `<that worktree's git-dir>/landing.json` | That worktree's submit | Until its next sync |
| Verify worktree | `C:/mc-worktrees/landing`, the integrator's alone, not `C:/mc-worktrees/verify` | The integrator | Kept |
| Installed tool | `C:/mc-state/landing/landing.jar`, with the commit it was built from | `install-landing.sh`, which takes the same lock | Until reinstalled |

An entry's state is not stored. It is a pure fold: `Entries.state(id, journal, main)` returns QUEUED, VERIFYING(n),
LANDED(sha), FAILED, BOUNCED or WITHDRAWN. A trailer on main wins over everything else. Two states at once cannot be
represented.

The queue holds neither the author's worktree path nor head. Withdrawal is a request ref (`refs/queue-withdraw/<id>`)
that the integrator settles, so there is still one writer.

### 2. Who integrates: one role, started one way
- The integrator is whoever holds the OS lock. It runs until the queue is empty and exits.
- It is started only by a Windows Task Scheduler task, `mc-landing`: at logon and every 5 minutes, "do not start a new
  instance". A submitter pokes it with `schtasks /run /tn mc-landing`.
- It is not a child of any model session. A session ending cannot kill a verify or strand an entry, and scheduled
  watches can submit and leave.
- A second start exits 4 at `tryLock`. A dead integrator needs no cleanup; the next start reconciles.
- Waiters taking turns is rejected: safe under the OS lock, but it ties a 28-minute verify to a session's life.
- A hand-kept daemon is rejected: the scheduler is the keep-alive.

### 3. Shell or Java: Java
A Maven module `landing` in the root reactor, package `io.github.jayessdeesea.minecraft.landing`. Its tests run in every
verify. No Spring; Jackson for the journal.

It runs only from the installed jar, never from a writer's tree; otherwise each stale worktree brings its own
gatekeeper. `tools/ops/commit.sh` becomes a shim that execs the jar. An absolute-path copy,
`C:/mc-state/landing/land.sh`, serves branches that predate it.

Pure core, each with its table of evals:
- `Entries.state`: the fold above.
- `Integrator.next(snapshot)`: Reconcile, Settle, Attempt, Idle or Halt.
- `MergeOutcome.of(exit, tree, mainTree)`, from `git merge-tree --write-tree --merge-base=<squash^> <main> <squash>`:
  exit 0 and tree differs from main's: Candidate; exit 0 and tree equals main's: BOUNCED "nothing to land", never
  "landed"; exit 1: BOUNCED with the conflicting files; anything else: an integrator fault.
- `Stale.check`: if a commit landed after the request's base has a `Landing-Source` that is an ancestor of this
  request's source, BOUNCED "carries a landed change: sync". If `base..source` holds a merge commit, BOUNCED.
- `VerifyOutcome.of(output, timedOut, headBefore, headAfter, clean)`: BUILD SUCCESS, no BUILD FAILURE, HEAD equal to
  the candidate before and after, tracked tree clean: Verified. BUILD FAILURE: FAILED. No BUILD line: integrator fault.
  Timeout (45 min, process tree killed): counts against the entry, FAILED at the second. HEAD moved or tree dirty: a
  fault, nothing pushed.
- `PushOutcome.of(pushOk, fetchOk, idOnMain, mainMoved)`:

  | Push | Fetch | Id on main | Main moved | Answer |
  |---|---|---|---|---|
  | any | fails | unknown | unknown | Integrator fault. Nothing settled; reconciled on the next run. |
  | any | ok | yes | n/a | LANDED, including when the push's reply was lost. |
  | refused | ok | no | no | Integrator fault (guard or credential). Entry stays queued; three running writes `Halted`. |
  | refused | ok | no | yes | `[Fault]` outside push. Attempt again on the new main; FAILED at the third. |
  | ok | ok | no | n/a | Halt. |
- `Audit.check`: the violations of section 4.
- `ExitCode.of(state)`: 0 landed, 1 failed, 2 usage or nothing to land, 3 bounced, 4 halted, 5 still queued at
  `--timeout`. The one table; the docs quote it.

Shell: `Git` (ProcessBuilder, argument lists, exit codes), `Journal`, `IntegratorLock`, `Verifier`, `Clock`, all
injected; `main()` reads the environment once.

The integrator's step:
1. Fetch.
2. Reconcile every entry that has an attempt and no outcome: on main means landed, otherwise attempt again. Five
   attempts without an outcome is FAILED.
3. Take the oldest request and run the stale check.
4. Run merge-tree, then `git commit-tree <tree> -p <main>` with the squash's message: `candidate`, a value.
5. Journal `Attempt`.
6. `checkout -f --detach candidate` and `clean -fd` in the landing worktree. Verify.
7. Journal `Verified`.
8. `git push origin <candidate>:refs/heads/main`.
9. Fetch and classify.
10. Delete the ref.

A crash anywhere is covered by step 2. Entry faults settle the entry. Integrator faults settle nothing and back off (5,
10, 20 min). A halt is visible to every waiter as exit 4.

The submitter:
1. `git add -u` and commit.
2. If `landing.json` says the last submission landed as L from source S, and L is not an ancestor of HEAD:
   `git rebase --onto L S`. A conflict exits 3.
3. Squash against the merge-base, with trailers.
4. Create the ref, poke the task, poll `state`.
5. On landed, `git reset --keep`.
6. On failure the commit stays. Nothing is taken back.

### 4. Invariants, each checked by `landing audit`
K is the cutover commit, the journal's first record.

| # | Invariant | Check |
|---|---|---|
| I1 | Main is linear | `git rev-list --min-parents=2 K..origin/main` is empty |
| I2 | Every commit in `K..main` has exactly one `Landing-Id`, and the journal holds `Verified` for that id with that SHA, or `Adopted` | The SHA fixes the parent: "verified on the main it landed on" |
| I3 | No id is on two commits of main | Trailer scan |
| I4 | At most one settled record per id; none for an id on main; no `Attempt` after a terminal record | Journal fold |
| I5 | Records of two integrator runs never interleave | Each record carries its run's token |
| I6 | Main is never rewound | Each `SeenMain` is an ancestor of the next |
| I7 | `refs/queue/*` equals the non-terminal ids; none waits longer than 3 verifies times its position while the lock is free | Refs against the fold |
| I8 | The pre-push hook is installed with the installed hash; the jar's source commit is the last on main touching `landing/` | File hash, `git log -1 -- landing` |

### 5. Controls
Preventive:
- One integrator: the kernel lock.
- Pushing the unverified: the candidate is a value; the worktree is private; HEAD and cleanliness checked before and
  after the verify.
- A wrong push from this machine: a `pre-push` hook in the shared hooks directory (no `core.hooksPath`; all 27 worktrees
  share it) refuses any update of `refs/heads/main` unless `landing may-push <sha> <remote-sha>` finds the lock held and
  the journal's last record `Verified` for that SHA on that parent. `--no-verify` bypasses it: it stops accidents and
  the old `commit.sh`, not intent.
- A wrong push by anyone: a GitHub ruleset on main, by the operator's hands - block force pushes and deletions, require
  linear history; restrict updates, with a bypass list holding only a deploy key the integrator alone pushes with (SSH,
  `GIT_SSH_COMMAND` set in the task). Only this makes a push outside the queue impossible rather than unlikely; with one
  OS user the key is still readable, so it means "not without deliberately going around".
- Unbounded retry: attempt caps in the pure core.

Detective: `landing audit` at the start and end of every integrator run, and at the start of every watch. Each
violation is a `[Fault]` line in the journal and on stdout, and the exit is non-zero.

Corrective: a dead integrator - the next scheduled start, at most 5 min later. A crash mid-push - reconcile. A stranded
entry - I7 flags it and the scheduler drains it. Doubly settled - not representable; a corrupt journal: main wins and the
audit names it. An outside commit on main - a `[Fault]` at every audit until `landing adopt <sha> <reason>`; the queue
carries on, and the next verify covers the combined tree. Journal lost - landed entries survive on main; the rest are
verified again.

### 6. How the queue is tested, in the repo, in every verify
- Tables of evals for each pure function.
- Integration tests in `@TempDir`: a scratch bare origin, worktrees, a fake verify command - at least: two writers
  waiting at once; a bounce and its resolution; a failed verify; a branch left unmoved after landing, with a change
  since reverted on main; the same tree from two branches (bounced "nothing to land"); a push refused with main
  unmoved; an untracked file blocking the pick; a wait cut short and taken up again; two JVMs contending for the lock,
  the holder killed with `destroyForcibly` and the next taking it at once.
- Crash injection at each numbered step of the integrator, then a fresh run, then a clean audit; a randomised
  interleaving of submits, crashes and outside pushes under the same assertion.
- The fault classes as rows: push reply lost; push refused with main unmoved; main moved three times; verify timeout;
  HEAD moved during the verify; a branch carrying a landed, then reverted, change.
- Tagged `slow` only if a class exceeds ten seconds.

### 7. Migration
The old `commit.sh` and the new integrator coexist safely: different worktrees, and the remote's compare-and-swap means
an old push during a new verify only costs an attempt.
1. Land C1 to C3 with the old `commit.sh`: new files nothing calls.
2. `install-landing.sh`: builds the jar from origin/main, creates the task disabled. Audit only.
3. Enable the task. Land C4 through the new queue as its first real entry.
4. Cutover: journal `Cutover K`, install the hook. Every old `commit.sh` then fails at its push with the hook's message
   naming `sh C:/mc-state/landing/land.sh <message file>`; the old script leaves the commit in place on a push failure.
   A session mid-verify loses one verify.
5. The operator sets the ruleset and the deploy key; the integrator's remote is switched in `C:/mc-state/landing/config`.
6. Retire `C:/mc-worktrees/verify`; `hotspots.sh` and `field-tracer.md` move to the landing worktree.

### 8. Commit sequence
| # | Content | Size | Risk |
|---|---|---|---|
| C1 | Module `landing`: value types, the pure core, the tables of evals | ~400 main, 500 test | Low; nothing calls it. Adds a reactor module. |
| C2 | Shell, CLI, integration and crash tests | ~600 main, 700 test | Windows process and file semantics; the time the tests add to verify. |
| C3 | `install-landing.sh`, `land.sh`, the hook's source, the task definition, the two worktree-path readers | ~120 | None until installed. |
| C4 | `commit.sh` as a shim; "Landing a change" rewritten with the invariants and the exit table; DEVELOPMENT.md, CLAUDE.md, the AGENTS.md rule, the watches | ~150, mostly docs | The first entry through the new queue. |
| C5 | A docs line for the deploy-key remote | Small | After the operator's GitHub work. |

### Unsure (the designer's own words, condensed)
- Whether a personal-account repository's rulesets allow "restrict updates" with a deploy key as bypass actor.
- `git merge-tree --write-tree --merge-base` exit codes on git 2.45.1 - from the docs, not run.
- Whether `schtasks /run` works without elevation for a user's own task.
- A hole: a writer who runs plain `git rebase origin/main` on a branch whose landed change was since reverted re-adds it;
  only review of the squash diff catches that.
- Whether `deploy.sh`'s `mvn install` into the shared `~/.m2` during a verify can change what the verify resolves.

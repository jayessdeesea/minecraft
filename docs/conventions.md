# Conventions

The rules for changing this project, and why: how it stays legible, failing loud, standard formats,
docs and backlog, and working without guidance.

## How this project stays legible as it grows

Four tenets about maintaining the project's own self-model, not about Minecraft or AI specifically:

1. **Context economy: each fact gets exactly one authoritative home.** AGENTS.md is loaded whole
   on every interaction, so it holds only the objective, the few rules every change needs, and
   pointers to the docs that hold the rest - loaded when a change needs them. The enemy isn't
   length directly, it's *duplication*: the same fact living in two places that can silently drift
   apart. Where two docs both state a fact, one becomes its home and the other points to it.
2. **Every organizational boundary needs an explicit owner, not just a description.** Borrowed from
   RACI's spirit more than its letter — not every boundary needs all four roles, but every place two
   pieces of code could plausibly both claim responsibility for the same fact or decision needs an
   explicit answer to "which one actually owns this." The Maven module layout
   ([architecture.md](architecture.md#maven-module-layout)) and the protocol pipeline's plane/tier
   table ([protocol-pipeline.md](protocol-pipeline.md)) are both instances: each states
   not just what a piece does, but where the line is when two pieces seem like they could both do
   it. Prefer boundaries a build enforces over boundaries that only exist in prose — `ArchitectureTest`'s
   ArchUnit rules make the module boundaries real, not aspirational; a rule that only lives in
   a paragraph can rot silently the moment someone doesn't re-read it before writing code that
   crosses it.
3. **Revisit a structural decision when real pressure appears — not preemptively, and not never.**
   The test is always the same: is there a specific, present-tense thing forcing the change (a test
   that can't be written without it, a rule violated more than once for good reasons, a real reuse
   need *today*) — or does the new shape just sound cleaner in the abstract? Only the first is a
   reason to act. See the Maven module layout ([architecture.md](architecture.md#maven-module-layout)) for this
   test applied to a concrete case (when a package graduates into its own module).
4. **Record the lesson, not the expedition.** A doc should state what's true now and, only when
   the discovery method itself is reusable elsewhere, that method — not the blow-by-blow of the
   specific investigation that got there. Test before writing:
   delete every sentence describing a specific attempt or dead end — if a future reader loses
   nothing they'd act on, it was history, not knowledge, and shouldn't have been written down.

## Source files stay under 1000 lines

The operator's (2026-11-18). A file past 1000 lines holds more than one responsibility, and the
next change lands in it because it's already there - the way the soldier runner collected the
leaders' decisions. `ArchitectureTest.sourceFilesStayUnder1000Lines` enforces it as a ratchet: a
file already over the limit is listed with its present length, may only shrink, and comes off the
list once under; a new file over the limit fails the build. What's left to split is
[backlog/split-oversized-sources.md](../backlog/split-oversized-sources.md).

## No fallbacks — fail loud, not gracefully-wrong

Tenet, not a suggestion: when something is ambiguous, missing, or violates an invariant, the
correct response is to fail immediately and visibly — not to silently pick a plausible-looking
default and continue. Two reasons, from experience, not just principle:
1. A fallback path is by definition rarely exercised, which means it's rarely tested and often
   subtly broken by the time it actually triggers — "not well-paved" in exactly the way a
   rarely-driven road develops potholes nobody's noticed yet.
2. A fallback that "works" by picking something reasonable also *hides* the fact that a real
   decision needed to be made and wasn't. The underlying problem doesn't get fixed — it gets
   papered over, usually until it resurfaces somewhere much harder to diagnose than where it
   actually originated.

**Concretely, once `command-ai` has more than one Spring bean satisfying the same interface**
(multiple `ChatModel`s, multiple strategy implementations, etc.): never use `@Primary`. It
silently resolves ambiguity by picking one bean as the default, which means a bean added later can
silently steal precedence at every injection site that didn't ask for something specific — with no
compile-time or startup-time signal that anything changed. Use `@Qualifier("beanName")` at every
injection point instead: an ambiguous or missing qualifier fails to wire at all, loudly, at
startup — exactly the failure mode we want, and exactly the opposite of `@Primary`'s silent
default. `command-ai`'s two `ChatClient` beans (`squadLeaderChatClient`, `generalChatClient`)
are the live instance: each injection site names its bean.

**This is also why every resource-exhaustion guard in `protocol` terminates the connection rather
than skip-and-continue** (docs/protocol-spec.md § 8): an oversized frame, a compression bomb, or NBT
nested too deep is a violated invariant, not expected gaps. Skipping
one and hoping the byte stream resynchronizes isn't just against this tenet — for the frame-length
case specifically, it's *provably unreliable*: our framing has no resync mechanism, so guessing
how many bytes to discard risks silently misinterpreting everything that follows for the rest of
the connection. That's a fallback that "doesn't always work" in the most literal, dangerous sense.

**Contrast with `UnknownPacket`, which looks similar but isn't the same category of thing.** An
unrecognized packet ID is not a violated invariant — it's a known, catalogued gap in *our*
implementation (Play's catalog is deliberately incomplete right now), not evidence the connection
itself is broken. Continuing past it is safe because the frame decoder already isolated exactly
how many bytes it occupies — no guessing, unlike the frame-length case above — and it's loud, not
silent: every occurrence is logged (docs/protocol-spec.md § 3.4), specifically so the gap gets
noticed and closed, not forgotten. The question that actually matters isn't "did we continue past
an anomaly" — it's "is this an *expected, tracked* gap that's *provably* safe to skip, or a
violated invariant we're guessing our way past?" Only the first is legitimate to continue through.

**Loud is not dead: a request fails, the app recovers.** Think of a website: one request fails
the moment an assertion does, and the server goes on serving the next. Here the request is a tick
- one soldier's, one squad's orders, one model call. A periodic task that throws - the squad loop, a
soldier's tick - is cancelled by its executor for good, which is the quietest failure there is:
one line in the log, then soldiers working their last orders with no squad leader or platoon leader. So
every periodic task runs guarded (`app.Faults`): a fault is logged with its stack trace the first
time, counted after that and raised as an alarm once a minute while it lasts, its recovery said,
and every fault since the last dawn is in the AAR. Each squad's orders are guarded on their own,
so one squad's fault leaves the other its orders. The next tick starts from the world and the
chain of command as they stand, so carrying on guesses nothing - unlike a fallback. A JVM
`Error` isn't caught.

## No mutable global state

State lives in objects that own it, handed to whoever needs it - by Spring's constructor
injection in `app`, by constructor arguments everywhere else - never in a static field or a
singleton reached from anywhere. What a class depends on is then in its constructor, its state
is visible where it's made, and two of it can run side by side: two soldiers, two squads, two
tests. A static field is a constant. The one standing exception is the generated registries
(`BlockClassRegistry`, `ItemClassRegistry`, `HostileMobRegistry`): data generated from the pinned
server, filled once when their class loads and only read after, so they behave as constants.

## Work is named: nothing is "the" anything

Every piece of work is a named objective or mission, and what's known about it hangs off that name (operator,
2026-11-23: there may be an objective to flatten something, but there is no global "what are we flattening" - that is
the wrong abstraction; everything is named). A field that answers "the ground to flatten", "the camp's mine", "the
tree chunk", "the farm squad" is one thing where the force has many: it makes a second one wait for the first, or be
taken for it. Live, one ground to flatten held three missions behind a fourth, and a mineshaft near the camp was taken
for the camp's mine and never cut. So:
- State about work is kept by the work's name - its mission and step - and reached through it, never through a field
  for "the current one". Two of anything can then go on at once: two flattens, two mines, two farms.
- A squad's task says which named work it is on; what a soldier does is derived from that, not from where the squad
  stands or which site is nearest.
- The same holds for methods: one that needs state takes it from the named thing it's given, not from a static or a
  singleton (the section above). A static method with no state - a pure function over its arguments - is fine
  (operator, 2026-11-23: only static state).
What stands against this today, to be brought to it: backlog/named-work.md.

## Tests run in parallel

Every test class and method runs concurrently (`junit-platform.properties`, in `app` and `bot`),
four at a time - the dev server and the garrison share the machine, and at a thread a
core the server's watchdog killed it for a 60-second tick - so a test is written to share nothing
with another:
- **No mutable static state** (above), in tests or in the code they run.
- **Each test owns its world:** its own `SimClock`, `SimServer`, runner and chain of command, and a
  `@TempDir` for anything on disk - never a fixed path, a working-directory file or a system
  property.
- **Each scenario owns its log:** each simulation test class names the scenario in log4j's
  `ThreadContext`, and `log4j2-test.xml` routes its lines to `app/target/sim/<scenario>.log`. A
  scenario's whole run is on its test's thread (`SimClock`), so the name follows every line.

So the suite takes about as long as its slowest tests, not the sum of them. A test class that takes
ten seconds or more is tagged `@Tag("slow")`: the simulations, the stuck evals, the combat matrices.
While working on a change, run its own tests (`-Dtest=...`) and the quick tier - everything not
tagged slow, `tools/ops/quick.sh`; the whole suite gates every push (`tools/ops/commit.sh`).

## Check the outcome of what the force is sent to do

A task sent to produce something - a night's mining, a wood party's trip, a smelt, a draw for
armour - is checked when it ends against what it should have produced, and a miss is dug into
until its cause is known, not let stand (operator, 2026-11-16: "if we send people to do something,
like mine, and they don't return with results, we need to dig into why"). A task that can't
produce any more says so - a mine dried up is reported, not read as DONE night after night. Live,
the keep's mine read DONE for days on about 18 ores in all, and copper for four soldiers' armour
sat in chests no draw looked in; nothing said so until the operator saw soldiers without armour.
Each such task has a model of perfect - the flatten's level: its blocks over the crew's best dig
rate (`command.LevelPace`); the mine's night: ore for the tunnel cut at its depth; a farm: wheat per
plot per growth; a grove: logs per tree per regrowth - and its outcome is said as a share of it.
Under 90% it's SHORT, says where the time or the yield went, and the biggest loss is traced to a
soldier and a cause before it's taken for the ground's: live, the flatten's levels read 3% with
"waiting, no rectangle" 65% all evening, taken for sparse hilltop levels, while the one builder the
level was dealt to went back and forth between two steps for two minutes. A check is a logged
`[Outcome]` line (the flatten's is its `[Keep] pace` line), read in every live check with the
alerts.

## A contact met live is a row first

What a soldier does on contact is one pure function (`bot.Contact.respond`), and `bot`'s `ContactEvalTest` is the table
of its answers: a row a combination of weapon, armour, health, hostile and ground, with the response it gives. A
`[Contact]` line whose combination isn't in the table is added as a row, with what the code answers today, before
anything is fixed (operator, 2026-11-24: as more combinations are found they join the eval suite). The fix then flips
that row's expectation in its own commit, so the diff shows which answers a change moved and that no other did. A
combination that only a table row holds is also one a later change can't break unseen. What the answers should be is
backlog/engagement-rules.md's.

## A retry changes something
Trying again what just failed, the same way, expects a different outcome for no reason (operator, 2026-11-23). A
routine whose try fails does something different with the next, from a ladder it climbs in order:
1. **Wait, a little longer each time, and not in step with the others** - `bot.supply.Setbacks`: the target left
   alone twice as long at each failure; given a salt - whose it is - each wait is lengthened by up to a quarter, so
   several failed at one thing don't come back as one. A depositor's waits are salted by its name (`Depositor`).
2. **Another target** - the next cell, another chest, another stand, a different way round. A stand somebody's in is
   taken (`SoldierErrands.standTaken`); a trip that put nothing in isn't made again with the same load (`Depositor`).
3. **Another means** - what would make the try work: build the chest that's wanted, clear what's in the way, draw
   what's short, throw out what fills the pack.
4. **Ask for help** - CANTCO up the chain with the reason and the count (`Setbacks.stuck`), for its leader to give
   the work to someone else, or change it.
5. **Give it up, and say so** - work nobody reaches is left and named, not handed round: a flatten's slope columns
   out of reach (`FlattenMeasures.SLOPE_PATIENCE_MILLIS`).
And the count is said: four failures running at one target is a `[Retry]` line, again at each doubling; four trips
running that moved nothing an `[Outcome] loop`. Whoever watches the garrison digs into each
([watches/garrison.md](watches/garrison.md)). Live, on one day: a soldier dug a block and walked 38 blocks to a chest with it, 33 times; ten
soldiers went round each other at one cell by a full chest; nine columns of a slope nobody could reach were handed
from one soldier to another every two minutes for five hours.

## Profile every sixteenth commit

When the commit count on main (`git rev-list --count HEAD`) is a multiple of 16, the live
garrison is profiled before the next change is started (operator, 2026-11-15), and the worst
offenders are fixed where it makes sense to:
- **Record** three minutes of the running garrison with Java Flight Recorder, which attaches to the
  live JVM at about 1% cost: `sh tools/ops/profile.sh` (the recording under `GARRISON_RUNS/profiles/`).
- **Read** the self-time hot spots it prints: the samples counted by top frame, then each put to the
  project's own frame nearest the top - the JDK frame on top (a map lookup, a sort) is rarely where
  the fix goes. `profile.sh --read <file.jfr>` reads one already made, to compare against.
- **Fix** what dominates and has a cause in the code - a bad hash, a scan in a tick loop - not
  what's merely first. Measure again after, and put both sample counts in the commit message.
- A recording with almost no execution samples is a garrison doing nothing: check it's connected
  before reading anything into it.

## Refactor every sixteenth commit, between profiles

The operator's (2026-11-24): the code base is made easy to change - for whichever model is changing it - on a
schedule, as its speed is - tick and tock: a profile at every multiple of 16 commits, a refactor pass 8 past it. When
the commit count on main is 8 past a multiple of 16 (`tools/ops/commit.sh` says so), the next commit is a refactor
pass, before the next change is started:
- **Measure** with `sh tools/ops/hotspots.sh`: the main sources by churn x size over the last 64 commits; the ones
  returned to in three of the last eight; the pairs that change together; the long lines; the slowest tests of the
  last verify. The lists are counts, not judgements - they say where a change costs most to make and to check.
- **Take the top of it, one extraction a pass**, with no change of behaviour: every test and simulation passes as it
  was, and nothing is fixed in the same commit. What to cut and along which lines is
  [backlog/split-oversized-sources.md](../backlog/split-oversized-sources.md) - by echelon, the way the processes
  will divide: a soldier's tick, a squad leader's state and orders, the platoon's missions, talking only in
  `command`'s value types.
- **Bring the caps down behind it** - `ArchitectureTest`: a file's lines (`OVER_THE_LIMIT`), `SoldierRunner`'s
  declarations (`RUNNER_FIELDS`), the long lines (`LONG_LINES`). They only shrink, and a build that adds to one fails:
  the control between passes.

Not only on the count. Three things call for the pass at the next commit, whatever the number:
- **A fix touched one decision in three places.** It has one home after.
- **Two measures of one fact disagreed** - what's counted and what's worked. One is derived from the other after.
- **A file is in "returned to" twice running**: the fixes there aren't holding.
- **A change had to be made in two places to stay consistent** (operator, 2026-11-24) - a constant, a predicate, a
  ladder over the same kinds: that is the fault, not the two edits. The fact has one home after and the rest read
  it; where the copies had drifted apart, which is right is decided and said, not averaged
  ([backlog/one-home.md](../backlog/one-home.md) lists the ones found).

**A pass re-reads what its code assumes** (operator, 2026-11-24). What a comment, a name or a constant takes for
granted was true when it was written: one camp, one mine, a level, a model deciding, work ending at dusk, a guard for a
case that can't arise now. Each one in the files a pass opens is checked against the code as it is. One that no
longer holds isn't put right in the pass - that changes an answer - but written into `backlog/` with the line that
states it and the line that contradicts it, and fixed as its own change; a guard whose case is gone is deleted the
same way, with the test that shows it gone.

A pass makes the next change cheaper to hold in mind: a decision as a pure function over value types with its unit
test, in a file of one job, is what a smaller model can own end to end; a decision threaded through a file of
eight thousand lines is not.

**The measure is whether a smaller model can make the change** (operator, 2026-11-24: the refactoring should make it
easier for lower-tier models to work in the code base). A pass is aimed at what stops one:
- **One decision, one file, one table.** To change an answer it reads the function, its table of evals and the rule's
  doc section - not the runner. Where a change needs a second file to stay consistent, that is the next extraction.
- **Inputs as values, said in the signature.** No field read from the middle of a tick, no order of calls to know: what
  a decision depends on is its arguments.
- **A fast answer to "did I break it".** A decision's own tests in seconds and the quick tier in a minute; a failure
  that says which rule broke and where its home is.
- **Nothing to know that isn't written where the work is.** A rule a change must keep is in `AGENTS.md`'s index or the
  doc it points at, not in a comment three files off.

It is checked, not supposed: after a pass, a small real change in the area it touched - a row flipped, a rule added -
is given to a smaller model with only the backlog item for a brief. Where it goes wrong is the next pass's target, and
is written into [backlog/split-oversized-sources.md](../backlog/split-oversized-sources.md).

## A comment is the rule and its reason

The operator's (2026-11-24): the history is in git. A comment says what the code must do and why, in the present
tense, and no more: not the run it was seen in, the soldier it happened to, or what was tried first. Those go in the
commit that made the change, briefly - what changed and why; the rest is the diff. A comment that needs an incident
to be believed wants a test instead, and names it. Comments that tell a story are cut down as their file is passed
through, and each refactor pass takes its file's.

## Standard formats go through standard libraries

XML, JSON, CSV and other standard formats are written and read with a library, never marshalled
by hand with a `StringBuilder` or string concatenation: hand-built output escapes nothing, so the
first `<` or `"` in a value breaks it silently. Prefer the JDK's own (`javax.xml.stream` for XML);
use a third-party library where the JDK has none (Jackson for JSON). The `<tags>` in the models'
prompts aren't one: they're labels in a template, not a document anything parses. Our own line-oriented resource files
(`block-classes-777.txt`) aren't a standard format and stay as they are.

## Names are imported

A class is imported at the top of the file and used by its simple name - never written out with its
package in the code. Imports go in three sorted groups: other packages, then `java.*`, then static
imports. Where two classes would share a simple name in one file, one of them gets a more
descriptive name rather than a qualified one.

## Docs

**`docs/` describes the system as it is; `backlog/` holds what will or might happen.** A doc
under `docs/` states only what the code does today. A sentence about what "will", "might" or
"should" happen is a backlog item, not documentation, and moves to `backlog/` (format:
[backlog/README.md](../backlog/README.md)). Landing a backlog item deletes its file in the same change
and writes whatever stays true into `docs/` or the code. A reader never has to ask "is this built
yet?"

**Before every commit, check the docs against the code.** A targeted read of what the change
touched - `docs/`, README.md, DEVELOPMENT.md, AGENTS.md - for a prose claim it made untrue: a stale
example, a "doesn't do X yet" that now does. And the backlog: an item the change finished is
deleted, one it advanced is trimmed to what's left. A fact found in two places is collapsed to one
home, the other pointing at it.

**After every live run, review the tenets.** Each death, `[Alert]`, timeout and order that was
awkward to give is checked against [docs/echelons/tenets.md](echelons/tenets.md): a soldier duty
that didn't hold, a squad leader's miss, or something no echelon's API could say. A gap becomes a
change to the tenets or the API, or a backlog item. Finding nothing to change is a fine outcome;
skipping the look isn't.

**Before starting a backlog item, sanity-check it.** It was written against the architecture and
requirements of its day, and both drift. Re-read it against the current code and docs:
- Does its "Why" still hold?
- Have its dependencies landed, or been made unnecessary?
- Does the design sketch still fit the module boundaries and the doctrine as they are now?

Before writing code, update or delete the item, or raise the mismatch with the user.

## Working without guidance

When a set of independent tasks all needs doing and nothing says which comes first, pick one and
keep going until all are done - don't stop to ask about the order. Work found along the way that
plainly needs doing is done too, not offered. Stop for a question only when the answer changes what
gets built, not when it; a question that doesn't block the work goes in the report at the end.

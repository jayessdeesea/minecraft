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
one line in the log, then soldiers working their last orders with no squad leader or general. So
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

## Tests run in parallel

Every test class and method runs at once (`junit-platform.properties`, in `app` and `bot`), so
a test is written to share nothing with another:
- **No mutable static state** (above), in tests or in the code they run.
- **Each test owns its world:** its own `SimClock`, `SimServer`, runner and chain of command, and a
  `@TempDir` for anything on disk - never a fixed path, a working-directory file or a system
  property.
- **Each scenario owns its log:** `SquadSimulationTest` names the scenario in log4j's
  `ThreadContext`, and `log4j2-test.xml` routes its lines to `app/target/sim/<scenario>.log`. A
  scenario's whole run is on its test's thread (`SimClock`), so the name follows every line.

So the suite takes about as long as its slowest test, not the sum of them. While working on a
change, run its own tests (`-Dtest=...`); the whole suite before a deploy or push.

## Standard formats go through standard libraries

XML, JSON, CSV and other standard formats are written and read with a library, never marshalled
by hand with a `StringBuilder` or string concatenation: hand-built output escapes nothing, so the
first `<` or `"` in a value breaks it silently. Prefer the JDK's own (`javax.xml.stream` for XML);
use a third-party library where the JDK has none (Jackson for JSON). The `<tags>` in the models'
prompts aren't one: they're labels in a template, not a document anything parses. Our own line-oriented resource files
(`block-classes-777.txt`) aren't a standard format and stay as they are.

## Docs

**`docs/` describes the system as it is; `backlog/` holds what will or might happen.** A doc
under `docs/` states only what the code does today. A sentence about what "will", "might" or
"should" happen is a backlog item, not documentation, and moves to `backlog/` (format:
[backlog/README.md](../backlog/README.md)). Landing a backlog item deletes its file in the same change
and writes whatever stays true into `docs/` or the code. A reader never has to ask "is this built
yet?"

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

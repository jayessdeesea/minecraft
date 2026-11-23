# minecraft — project conventions

This file is loaded on every interaction, so it holds only the objective, the rules every change
needs, and pointers. Everything else lives in `docs/`, read when the work needs it.

## Objective

A Minecraft Java Edition client built from scratch - protocol implemented ourselves, no Minecraft
client/server libraries - that hosts a fleet of bots that works (operator, 2026-11-14): a force
that sustains itself and carries out the operator's missions.

- **Operator:** the one place judgement is needed, and the only one a model serves at run time: the
  operator talks to a model (the chat), which turns their words into missions and answers from the
  registers. It gives no order of its own.
- **Platoon leader:** one instance. Decides by standing rules (`command.DrillPlatoonLeader`), no model
  (operator, 2026-11-23); a model is selectable for comparison. Carries out a strategy: expand influence
  - build the base chunk by chunk from a plan (`command.BasePlan`), the renewables first, never more
  missions open than squads to work them - and the operator's missions above that.
- **Squad leader:** one per squad. Decides by standing rules (`command.DrillSquadLeader`), no
  model; a model is selectable for comparison. Not a Minecraft entity or connection: a role only.
- **Soldier:** a player bot with its own connection, in the world. Executes its squad leader's
  orders. Never calls a model.

The Minecraft client is the substrate; the subject is a force that works. Make tradeoffs that keep
the command layer legible, not ones that chase Minecraft feature completeness. The force is
organised and talks as an infantry platoon does (mission command, WILCO/CANTCO, SITREP,
OPORD/FRAGO), so the doctrine for each job comes from the manual for that job, and a model is shown
computed state, never asked to work it out. At development time a model also watches the running
garrison (docs/loop.md).

## Rules every change needs
Each points to its home, which has the reasons.
- **Soldiers run plain Java every tick; models think on slower clocks.** Never put a model call in
  a soldier's loop. [docs/architecture.md](docs/architecture.md#latency-architecture--why-the-hierarchy-is-stratified-by-timescale-not-just-rank)
- **Functional core, imperative shell.** Decisions are pure over value types; a routine acts, then
  waits for the server's confirming event, never for time. [docs/architecture.md](docs/architecture.md#behavior-modeling-functional-core-imperative-shell)
- **Module boundaries hold, enforced by `ArchitectureTest`:** Netty only in `protocol`; `command`
  free of Spring and Netty; `bot` and `command` never depend on `command-ai`. [docs/architecture.md](docs/architecture.md#maven-module-layout)
- **No fallbacks: fail loud - per request, never the whole app.** A tick fails like a web request
  does, logged and counted; the next one runs. Never `@Primary`; `@Qualifier` at every injection point.
  [docs/conventions.md](docs/conventions.md#no-fallbacks--fail-loud-not-gracefully-wrong)
- **Model calls go through Spring AI's abstraction only**, provider chosen in config; nothing
  Anthropic-specific in decision logic. [docs/stack.md](docs/stack.md#stack)
- **One pinned protocol version, offline mode only.** [docs/stack.md](docs/stack.md#target-environment)
- **Source files under 1000 lines**, a ratchet `ArchitectureTest` enforces.
  [docs/conventions.md](docs/conventions.md#source-files-stay-under-1000-lines)
- **No mutable global state: inject it.** State lives in objects passed in, never in statics or
  singletons, so tests run in parallel - each owns its clock, server, directory and log.
  [docs/conventions.md](docs/conventions.md#no-mutable-global-state)
- **Standard formats through standard libraries**, never hand-built strings. [docs/conventions.md](docs/conventions.md#standard-formats-go-through-standard-libraries)
- **`docs/` is what's built; `backlog/` is what's planned. Each fact has one home.**
  [docs/conventions.md](docs/conventions.md#docs)
- **Every commit closes its loop:** a backlog item it finishes is deleted, one it advances is
  trimmed to what's left, and the docs it touches are checked against the code and made current
  in the same commit. [docs/conventions.md](docs/conventions.md#docs)
- **Check the outcome of what the force is sent to do:** a task that should yield is checked
  against what it yielded, and a miss is dug into. [docs/conventions.md](docs/conventions.md#check-the-outcome-of-what-the-force-is-sent-to-do)
- **A retry changes something:** wait longer and out of step, then another target, another means, ask for help, give
  it up and say so - never the same try again; and the count is logged. [docs/conventions.md](docs/conventions.md#a-retry-changes-something)
- **Profile every sixteenth commit:** at a commit count that's a multiple of 16, record the live
  garrison with Flight Recorder and fix the worst offenders where it makes sense.
  [docs/conventions.md](docs/conventions.md#profile-every-sixteenth-commit)
- **Don't ask the order of work.** What needs doing, do; ask only what changes what gets built.
  [docs/conventions.md](docs/conventions.md#working-without-guidance)

## References
| Read | When |
|---|---|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Running the server, the models and the squad; building; live checks. |
| [docs/conventions.md](docs/conventions.md) | Before changing structure or docs: the legibility tenets, failing loud, docs and backlog rules, working without guidance. |
| [docs/architecture.md](docs/architecture.md) | Before changing how an echelon decides or acts, or where code lives: timescales, behaviour modelling, modules, the pipeline's rule, and the squad simulation that tests a change with no server. |
| [docs/stack.md](docs/stack.md) | Before touching dependencies, the protocol pin or the model providers. |
| [docs/echelons/](docs/echelons/README.md) | What the platoon leader, squad leader and soldier can each do, are told and report. Its [tenets](docs/echelons/tenets.md) after every live run, and before changing what an echelon owns. |
| [docs/prompting.md](docs/prompting.md) | Before changing a prompt or what goes into one. |
| [docs/military-doctrine.md](docs/military-doctrine.md) | The doctrine each behaviour emulates, with manual citations and how it maps onto code. |
| [docs/protocol-spec.md](docs/protocol-spec.md) | Wire protocol: packets, states, compression, encryption. |
| [docs/protocol-pipeline.md](docs/protocol-pipeline.md) | The Netty pipeline, stage by stage. |
| [docs/world-data-spec.md](docs/world-data-spec.md) | Chunk, block-state and light data the client decodes. |
| [docs/entry-docs.md](docs/entry-docs.md) | Before editing README.md or DEVELOPMENT.md. |
| [docs/loop.md](docs/loop.md) | Watching the running garrison toward the operator's goals, pass after pass: the watch loop. |
| [backlog/](backlog/README.md) | Choosing work. "Work on the backlog" is the `work-on-backlog` skill. |

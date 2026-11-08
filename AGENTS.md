# minecraft — project conventions

This file is loaded on every interaction, so it holds only the objective, the rules every change
needs, and pointers. Everything else lives in `docs/`, read when the work needs it.

## Objective

A Minecraft Java Edition client built from scratch - protocol implemented ourselves, no Minecraft
client/server libraries - that hosts a chain of command of AI-controlled agents:

- **General:** one instance, the smarter model. Long-horizon strategy, low call frequency. Gives
  each squad an objective.
- **Squad leader:** one per squad. A lighter model, tactical decisions, medium call frequency.
  Commands its soldiers. Not a Minecraft entity or connection: a decision-making role only.
- **Soldier:** a player bot with its own connection, in the world. Executes its squad leader's
  orders. Never calls a model.

The Minecraft client is the substrate. The subject is how a few AI agents at different levels of a
hierarchy coordinate: make tradeoffs that keep the coordination layer legible, not ones that chase
Minecraft feature completeness. How the echelons talk is borrowed from military doctrine on purpose
(mission command, WILCO/CANTCO, SITREP, OPORD/FRAGO), to see where doctrine built for humans under
stress overlaps with what works for LLM agents.

## Rules every change needs
Each points to its home, which has the reasons.
- **Soldiers run plain Java every tick; models think on slower clocks.** Never put a model call in
  a soldier's loop. [docs/architecture.md](docs/architecture.md#latency-architecture--why-the-hierarchy-is-stratified-by-timescale-not-just-rank)
- **Functional core, imperative shell.** Decisions are pure over value types; a routine acts, then
  waits for the server's confirming event, never for time. [docs/architecture.md](docs/architecture.md#behavior-modeling-functional-core-imperative-shell)
- **Module boundaries hold, enforced by `ArchitectureTest`:** Netty only in `protocol`; `command`
  free of Spring and Netty; `bot` and `command` never depend on `command-ai`. [docs/architecture.md](docs/architecture.md#maven-module-layout)
- **No fallbacks: fail loud.** Never `@Primary`; `@Qualifier` at every injection point.
  [docs/conventions.md](docs/conventions.md#no-fallbacks--fail-loud-not-gracefully-wrong)
- **Model calls go through Spring AI's abstraction only**, provider chosen in config; nothing
  Anthropic-specific in decision logic. [docs/stack.md](docs/stack.md#stack)
- **One pinned protocol version, offline mode only.** [docs/stack.md](docs/stack.md#target-environment)
- **Standard formats through standard libraries**, never hand-built strings. [docs/conventions.md](docs/conventions.md#standard-formats-go-through-standard-libraries)
- **`docs/` is what's built; `backlog/` is what's planned. Each fact has one home.**
  [docs/conventions.md](docs/conventions.md#docs)

## References
| Read | When |
|---|---|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Running the server, the models and the squad; building; live checks. |
| [docs/conventions.md](docs/conventions.md) | Before changing structure or docs: the legibility tenets, failing loud, docs and backlog rules, working without guidance. |
| [docs/architecture.md](docs/architecture.md) | Before changing how an echelon decides or acts, or where code lives: timescales, behaviour modelling, modules, the pipeline's rule. |
| [docs/stack.md](docs/stack.md) | Before touching dependencies, the protocol pin or the model providers. |
| [docs/personas/](docs/personas/README.md) | What the general, squad leader and soldier can each do, are told and report. |
| [docs/prompting.md](docs/prompting.md) | Before changing a prompt or what goes into one. |
| [docs/military-doctrine.md](docs/military-doctrine.md) | The doctrine each behaviour emulates, with manual citations and how it maps onto code. |
| [docs/protocol-spec.md](docs/protocol-spec.md) | Wire protocol: packets, states, compression, encryption. |
| [docs/protocol-pipeline.md](docs/protocol-pipeline.md) | The Netty pipeline, stage by stage. |
| [docs/world-data-spec.md](docs/world-data-spec.md) | Chunk, block-state and light data the client decodes. |
| [docs/entry-docs.md](docs/entry-docs.md) | Before editing README.md or DEVELOPMENT.md. |
| [backlog/](backlog/README.md) | Choosing work. "Work on the backlog" is the `work-on-backlog` skill. |

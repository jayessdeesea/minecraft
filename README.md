# minecraft

## What it is

A chain of command of AI agents, fighting and working in a Minecraft world:

- **The platoon leader** is a frontier model. It holds the mission, reads what each squad reports, and gives
  each squad an objective with its intent.
- **Squad Leaders**, one per squad, are an open-weight model run locally. Each turns the platoon leader's
  objective into orders for its soldiers: where to go, in what formation, at what interval.
- **Soldiers** are Minecraft player bots: deterministic Java that calls no model. Each runs every
  game tick (50 ms). It moves, fights, gathers, builds and crafts on its own initiative, within its
  last order.

Two squads of four are in the field. Out of the box, both the platoon leader and the squad leaders run on
the local model (Ollama), so the project runs without an API key. To put the platoon leader on a frontier
model, set one key, `minecraft.platoon-leader.chat-model: anthropic`
([DEVELOPMENT.md](DEVELOPMENT.md#1-start-the-dev-server-and-local-model)).

## Why it exists

To build a fleet of bots that works: a force that feeds, arms and shelters itself, and carries out
an operator's missions - a keep built, an outpost founded, a region surveyed - in a world that runs
in real time, is partly observable, and is hostile at night, so plans meet friction.

It began as a way to collect data on tiering AI agents, a strong model handing down to weaker ones.
What that taught shapes the fleet: the bad decisions came from a model doing arithmetic or working
out state it should have been shown, and the deterministic parts - the drills, the equipment
ladder, the foreman's share-out of a dig - do their jobs better than a model asked to. So judgement
goes to one model at the top, with the cost of a call low and everything it needs computed for it;
the rest is code organised as an infantry platoon is, so the doctrine for each job comes from the
manual for that job.

Questions it can answer:
- How much of a force's work can run on standing procedures, and what has to reach a commander?
- What does a commander have to be shown, computed, to choose well among missions?
- Where does a force overextend, and what gate, pause and fallback keep it within its supply?

Every order, acknowledgement and report is logged as a radio net (`[PL -> Alpha] OPORD`,
`[Bravo -> PL] CANTCO`). The squad leader and the platoon leader each have a repeatable evaluation
of their decisions, and whole-chain scenarios run the full hierarchy against the dev server.

## How it's built

**Military command structure and communication:**
- **Mission command:** orders carry intent, and subordinates use their own initiative within it.
- **Order formats:** OPORDs and FRAGOs.
- **Acknowledgements:** WILCO, CANTCO and SAY AGAIN, each operator order traced down to the soldiers at it.
- **Reports:** SITREPs and SALUTE reports.
- **Movement:** formations and intervals.

That doctrine was built for humans under stress, and part of the experiment is how well it fits
LLM agents. The doctrine, its manuals, and how each piece maps onto code:
[docs/military-doctrine.md](docs/military-doctrine.md).

**Intelligence is split by timescale as well as by rank.** A model call takes seconds and a game
tick is 50 ms:
- Soldiers never call a model.
- Squad leaders think every few seconds.
- The platoon leader thinks less often than that.

Why: [docs/architecture.md § Latency architecture](docs/architecture.md#latency-architecture--why-the-hierarchy-is-stratified-by-timescale-not-just-rank).

**The stack:**
- Java 25 and Maven.
- Spring AI for the model calls, with the provider picked in config.
- Netty for the network.
- No Minecraft libraries. The wire protocol, compression, chunk decoding and the connection state
  machine are all written here, pinned to one Minecraft version.

Module layout and the rules that keep it legible: [docs/architecture.md](docs/architecture.md) and [docs/conventions.md](docs/conventions.md).

## Start here

[DEVELOPMENT.md](DEVELOPMENT.md) covers how to:
- run the dev server and the local model;
- build;
- put the squads in the field;
- watch them from a real client.

## Docs

- [AGENTS.md](AGENTS.md): the rules every change needs, and where each reference lives.
- [docs/military-doctrine.md](docs/military-doctrine.md): the doctrine the chain of command
  emulates, and its mapping onto code.
- [docs/protocol-pipeline.md](docs/protocol-pipeline.md): how the `protocol` module's Netty pipeline
  works, including a walkthrough of a join.
- [docs/protocol-spec.md](docs/protocol-spec.md): the Minecraft wire protocol as implemented here.
- [docs/world-data-spec.md](docs/world-data-spec.md): the chunk, block and light formats the client
  decodes.
- [docs/entry-docs.md](docs/entry-docs.md): how to keep this file and DEVELOPMENT.md current.
- [backlog/](backlog/README.md): planned and open work.

## Building

```
mvn verify
```

This produces a runnable Spring Boot jar at `app/target/app.jar`.

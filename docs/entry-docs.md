# Keeping README.md and DEVELOPMENT.md current

The two root-level docs are the way in. Each has one reader and one job. Keeping them small and true
matters more than keeping them complete: everything else has a home of its own and they point to it.

## Who reads which

| Doc | Reader | Question it answers |
|---|---|---|
| `README.md` | Anyone landing on the repo: a visitor, a new contributor, someone weighing the results | What is this, why does it exist, how is it built? |
| `DEVELOPMENT.md` | Someone with a fresh clone who wants it running | What do I type, in what order, and how do I know it worked? |
| `AGENTS.md` | Anyone (human or agent) about to change the code | What are the rules, and why? |
| `docs/` | Someone who needs the detail of one subsystem | How does this part work, exactly? |
| `backlog/` | Someone choosing what to work on | What's planned or open? |

## README.md

Three sections, in this order, each a few short paragraphs at most:

1. **What it is.** The three echelons and what runs each one:
   - **General:** a frontier model.
   - **Squad leaders:** an open-weight model run locally.
   - **Soldiers:** deterministic Minecraft bots that call no model.

   Also say what runs by default and what one setting changes it. Name the config key; the values
   themselves live in `application.yml`.
2. **Why it exists.** The experiment, not the features. It collects data points on tiering AIs:
   - A highly capable leader breaks a problem down.
   - It socializes the result to less capable leaders, who break it down further.
   - Status comes back up the chain.

   State which questions the project can answer. Don't state which answers it has found. Findings
   belong in `docs/` with how they were measured, and the README links to them.
3. **How it's built.** Military command structure and communication: the chain of command, orders,
   acknowledgements and reports, the timescale split. Then the stack, which is Java 25, Maven, a
   protocol written from scratch, and no Minecraft libraries. Name each idea once and link to its home
   (`docs/military-doctrine.md`, `AGENTS.md`). Don't re-explain it.

Then a short **Start here** pointing to `DEVELOPMENT.md`, a **Docs** index (one line per doc), and
the one build command.

**Not in the README:**
- Setup steps (those go in `DEVELOPMENT.md`).
- Rules and rationale (`AGENTS.md`).
- Plans or "coming soon" (`backlog/`).
- A changelog (`git log`).
- Numbers that drift: test counts, per-call timings, entity counts.

## DEVELOPMENT.md

The first-run path, in the order someone follows it: prerequisites, start the server and the model,
build and test, run, watch, then the live checks. Rules for it:

- **Every command is copy-pasteable, and was run as written.** If a command needs a shell quirk (Git
  Bash path conversion, PowerShell process filtering), say so beside it.
- **Every step says how to tell it worked:** the log line to look for, or what the game shows.
- **Config is named, not copied.** Point at the key in `application.yml` and say what each value
  does. Don't paste the file.
- **Operational warnings stay here**, next to the step they bite: OneDrive locking `target/`, never
  killing every `java.exe`.

## One home per fact

When the same fact seems to belong in two of these docs, it lives in one and the other links to it.
The usual homes:

| Fact | Home |
|---|---|
| Which models run which echelon by default, and the keys that change it | `application.yml` comments; `DEVELOPMENT.md` § Choosing the model says how |
| Why the hierarchy is split by timescale | `AGENTS.md` § Latency architecture |
| Doctrine and how it maps onto code | `docs/military-doctrine.md` |
| Protocol pin and module layout | `AGENTS.md` |
| Run, build and test commands | `DEVELOPMENT.md` (the README keeps only `mvn verify`) |

## When to update

Both docs are checked in the pre-check-in doc pass (AGENTS.md § Docs). They need a change when:

- an echelon's model, provider or role changes, which touches the README's "What it is";
- the project's question changes, or a finding lands in `docs/` and the README should link it;
- a command, a config key, a prerequisite, or a log line a step tells you to look for changes, which
  touches `DEVELOPMENT.md`;
- a doc is added, renamed or removed under `docs/`, which touches the README's Docs index.

Before checking in, read the parts of both that the change touches. The likely drift is a sentence
that is no longer true, not a broken link.

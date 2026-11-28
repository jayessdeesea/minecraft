# How a change is made

The path a change takes from an idea to the running garrison, when many are under way at once. Each step's rules live
where the table points; this file only puts them in order.

## A day of it

Alice, the operator, has six ideas before lunch: a squad that idles at dusk, a slow pathfinder, a doc that's drifted,
three more. She opens an assistant session for each and gives it a sentence. She doesn't order them or wait on them:
each works alone in a worktree of its own, and what it can't decide comes back to her as a question, not a guess.

She reads answers, not logs. A session's last message lists what's outstanding and who acts next on each; one that
ends `Done: nothing outstanding.` she closes, with its worktree
([conventions.md, "Retiring a stream"](conventions.md#retiring-a-stream)). A new idea is a new session
(["Landing a change"](conventions.md#landing-a-change)), or a backlog item if it isn't for now.

## The path of one change

| Stage | Where | Beside other streams' changes? | Its rules |
|---|---|---|---|
| Design, for a change of behaviour that touches shared state | A design file, the operator's decisions taken on it | No | the assistant's instructions; what to ask, [conventions.md, "Working without guidance"](conventions.md#working-without-guidance) |
| Build | The stream's worktree | No | [architecture.md](architecture.md), [conventions.md](conventions.md) |
| Prove | The worktree: its own tests and the quick tier, then the slow tests it touches, by name | No | [conventions.md, "Tests run in parallel"](conventions.md#tests-run-in-parallel); [watches/garrison.md](watches/garrison.md), step 5 |
| Review | The diff and its claims | No | the assistant's instructions |
| Integrate and verify | The queue: the change built on main as main is then, the whole suite run on that | Yes, with every change landed before it | [conventions.md, "Landing a change"](conventions.md#landing-a-change) |
| Land | The queue pushes the commit it verified | Yes | the same |
| Deploy | The garrison restarted on main | Yes | [watches/garrison.md](watches/garrison.md), step 7 |
| Watch | The canary, then pass after pass | Yes | [watches/garrison.md](watches/garrison.md), step 8 on |

Integration and the long validation are one step: the whole suite runs on the combined result, so a change that passes
alone but breaks beside another isn't landed. Until the cutover ([backlog/landing-queue.md](../backlog/landing-queue.md))
nothing stops a push to main from outside the queue; what this file says of main holds for what the queue landed.

## The verify is the bottleneck

Everything before the queue runs in parallel, one stream beside another; the queue runs one verify at a time, so changes land
at most one a verify's length, however many streams are open. That length is measured at each verify that passes
(the expected time in ["Landing a change"](conventions.md#landing-a-change)). That is why a change is proven in its
worktree before it's queued.

## When it fails

Before main there is nothing to back out: a candidate that fails is thrown away and main doesn't move. What the stream
does next is its exit's row in [the table](conventions.md#landing-a-change). After a deploy, a change that's clearly
worse is reverted through the queue like any other change ([watches/garrison.md](watches/garrison.md), step 8).

## The queue builds itself

The queue lands changes to its own code but runs from its installed jar, so a change under `landing/` can't alter the
queue that lands it; it takes effect at the reinstall, the queue's own deploy
([DEVELOPMENT.md](../DEVELOPMENT.md), `install-landing.sh`). The gate itself is the exception: the verify runs the candidate's own tree - `tools/ops/verify.sh`, the poms, the tests
and their tags - so a change to any of them is judged by itself.

## What runs by hand today

Deploying and watching: [docs/watches/](watches/README.md).

---
name: work-on-backlog
description: Work through backlog/ item by item - drop what's obsolete or done, build what's still relevant and move what's true into docs/, and save questions for the human until the end. Use when the user says "work on the backlog" or similar.
---

# Work on the backlog

`docs/` describes the code as it is, with links to other references. `backlog/` holds ideas that
may or may not be built - and that other ideas may make obsolete. The rules for both are in
docs/conventions.md § Docs; this is the loop over them.

For each file in `backlog/` (not `README.md`), highest priority first (`priority:` in its front
matter, P1 before P3; an item whose `depends-on` hasn't landed waits for its dependency):

1. **Is it still relevant?** Re-read it against the current code and `docs/` (docs/conventions.md § Docs,
   "Before starting a backlog item, sanity-check it"):
   - Already built? Check the code, not just the docs.
   - Made unnecessary, or overtaken by another idea?
   - Does its design still fit the module boundaries and the doctrine as they are now?
2. **Obsolete or done:** delete the file. If part of it is built, write what's true into `docs/`
   and keep only what's left in the item.
3. **Relevant:** build it, test it (unit tests; a live run where the item's "Done when" asks for
   one), write what's now true into `docs/`, and delete the item - or trim it to what's left.
   Commit each item on its own, with the doc pass (CLAUDE.md, "Pre-Checkin Doc Check").
4. **A question only the human can answer** (a design choice, a priority, something to spend
   money or another person's time on): note it with the item, and go on to the next item. Don't
   stop to ask.

When every item has had its turn, end with:
- what was removed, what was built, what was trimmed;
- the questions, each naming its backlog item and what the answer would change.

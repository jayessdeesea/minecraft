# Backlog

What will or might happen: one Markdown file per item. `docs/` describes only what is built,
so anything aspirational lives here instead. The rules - including the sanity check before
starting an item - are in docs/conventions.md § Docs.

## Item format

File name: a short kebab-case slug (`wider-doctrine.md`). The slug is the item's ID; refer to items
by file path.

```markdown
---
status: proposed        # proposed | ready | in-progress | blocked | needs-decision
priority: P1 | P2 | P3  # P1 next up, P2 soon, P3 someday
depends-on: [other-slug]   # optional; items that must land first
doctrine: ATP 3-21.8    # optional; the manual this emulates (docs/military-doctrine.md)
---

# Title

## Why
The problem or gap, and why it matters to the coordination experiment.

## Done when
Observable, testable outcomes - what a test or live run shows.

## Notes
Design sketch, open questions, constraints. Optional.
```

- `ready` means the design is settled enough to start; `proposed` still needs thinking.
  `needs-decision` names the decision in Notes - it's the user's call, not an agent's.
- Priority lives only in each item's front matter, never in a list here, so it can't drift.
  To see the queue: `grep -l "^priority: P1$" backlog/*.md`.

## Lifecycle

- **Landing an item deletes its file** in the same commit that lands the work. Anything still
  true afterwards (how it works, why) moves into `docs/`, `DEVELOPMENT.md` or the code. Git
  history keeps the item itself.
- A partial landing edits the item down to what's left.

## Working through it

"Work on the backlog" is this pass. For each item, highest priority first (P1 before P3; an item whose `depends-on`
hasn't landed waits for it):

1. **Is it still relevant?** Re-read it against the code and `docs/` (docs/conventions.md § Docs, the sanity check):
   already built - check the code, not only the docs; made unnecessary or overtaken by another item; its design still
   fitting the module boundaries and the doctrine as they are now.
2. **Obsolete or done:** delete the file. Where part is built, write what's true into `docs/` and keep what's left.
3. **Relevant:** build it, test it (unit tests; a live run where its "Done when" asks for one), write what's now true
   into `docs/`, and delete the item or trim it to what's left. Each item its own commit, its docs made current in it.
4. **A question only the operator can answer** (a design choice, a priority, money or another person's time): note it
   with the item and go on to the next. Don't stop to ask.

When every item has had its turn, end with what was removed, built and trimmed, and the questions - each naming its
item and what the answer would change.

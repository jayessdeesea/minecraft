# Backlog

What will or might happen: one Markdown file per item. `docs/` describes only what is built,
so anything aspirational lives here instead. The rules - including the sanity check before
starting an item - are in AGENTS.md § Docs.

## Item format

File name: a short kebab-case slug (`carol-trapped.md`). The slug is the item's ID; refer to items
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

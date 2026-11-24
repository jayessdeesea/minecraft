---
status: ready
priority: P3
---

# What's left of the wood shared again

## Why
Built (2026-11-24): a squad's strips are dealt once and kept; a woodcutter given other ground looks it over before
anything is said of it; a strip cut out moves only its own woodcutter, onto half of the strip with the most standing;
a woodcutter with no strip stands by; the wood is spent when no log stands, or a second deal of it is cut out too
(docs/military-doctrine.md, the wood duties). A depositor's waits are salted and its walk is to a stand by its chest.

Two things `WoodDutiesSimulationTest` still shows:
- **A tree begun goes with the half it stands in.** A woodcutter takes a tree's foot and leaves the rest to its clearer
  while it starts the next (`[Dig] ... - GATHERER`, then `- CLEARER`); a share of its strip handed on in between takes
  that tree with it, and two fell one tree. The test allows two such trees.
- **A strip is rated by logs nobody can reach.** `DutyBook.logsIn` counts a column with any log in it, so a strip of
  trunks left out of reach reads as the richest, is shared, and is cut out again within seconds.

## Done when
- A strip's half is cut so every tree its woodcutter has begun stays in the half it keeps; the test allows none.
- A strip is rated by the trees a woodcutter can still fell from the ground or its steps.

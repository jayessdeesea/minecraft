---
status: proposed
priority: P2
---

# The general holds a completed order

## Why
Once a squad finishes its task, the general re-plans it away, even against a mission that says to
stay. Live: the moment Bravo's patrol base sealed, the general moved Bravo to "nearby high ground",
then to gathering wood. The mission said "stay in it". The squad cut a doorway and left the shelter
it had just built.

The general is re-deciding from scratch every cycle, and a squad reporting InPosition reads to it
as idle. Doctrine expects the opposite: an order stands until changed, and a leader changes it for
a reason (a FRAGO that says why).

## Done when
- The general sees each squad's standing objective, and how it's going (done, under way, CANTCO),
  in its prompt. It keeps the objective unless the mission or the situation calls for a change.
- A change of objective carries a reason in the OPORD.
- Measured: a live run where a squad finishes a patrol base at noon under a "stay in it" mission
  keeps it until dark and through the night.

## Notes
- This also makes live evals pin-able (backlog/scenario-matrix.md): a mission the general holds is
  a scenario that holds still.

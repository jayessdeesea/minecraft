---
status: proposed
priority: P2
---

# Simulated hostiles for the scenario harness

## Why
Combat behaviour (fighting withdrawal, contact reports, the squad leader's reaction) can
currently be exercised only live, at night, against whatever the server spawns. That's slow,
unrepeatable, and impossible to sweep across variations. A simulated hostile that's
approximately right is enough to check that soldiers break contact, report it, and that the squad
leader responds.

## Done when
- The scenario harness (`bot`'s `scenario` test package) can place simulated hostiles, each stepped every tick against the same
  synthetic world. Three archetypes:
  - **Melee chaser:** closes to contact at about walking speed and hits on contact.
  - **Ranged:** keeps at a standoff distance and deals damage over time while it has line of
    sight.
  - **Exploder:** closes in, pauses, then deals area damage.
- Soldier health and deaths are simulated, so scenarios can assert outcomes such as "the squad
  breaks contact with three melee chasers without a death in N seeds".
- Each archetype's numbers (speed, reach, damage) are checked once against the live server with
  `summon`, and the source is recorded.

## Notes
- These are deliberate approximations, not vanilla mob AI, in keeping with AGENTS.md's framing:
  the subject is coordination, so the simulation only has to make soldiers' choices matter. The
  live server stays the ground truth for real combat.
- `WorldView.nearbyHostiles` is already the seam: the simulation implements it.

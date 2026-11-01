---
status: proposed
priority: P3
doctrine: ATP 3-21.8 (various)
---

# The wider doctrine catalogue

## Why
Most of infantry small-unit doctrine has a plausible Minecraft analogue. The experiment is to see
which pieces carry over to LLM-led squads. This item is the list of candidates; each graduates to
its own backlog item when it's picked up.

## Candidates
- **Consolidation and reorganisation after contact.** Re-establish security, redistribute,
  report LACE, re-form the chain of command.
- **Security halts.** At night, or after a long move, the squad stops in a perimeter with
  sectors of fire rather than in a line.
- **Rules of engagement.** When soldiers may attack: hostiles only, never players, never
  passive mobs. This is an explicit, testable policy rather than whatever `HostileMobRegistry`
  happens to contain.
- **Squad column and movement techniques** (ATP 3-21.8 ¶3-53; traveling, traveling overwatch,
  bounding overwatch). Bounding means one element moves while another watches, so it needs more
  than one fire team. Weapons placement within the formation falls under this item too.
- **Battle drills** (react to contact, break contact). These are standardised responses, which
  fits the scenario harness (`bot`'s `scenario` test package) as fixed test cases.
- **Casualty reporting.** Deaths roll up as a casualty report rather than a CANTCO.

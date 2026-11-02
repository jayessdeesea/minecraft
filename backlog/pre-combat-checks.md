---
status: proposed
priority: P3
doctrine: pre-combat checks/inspections (troop leading procedures)
---

# Pre-combat checks: soldiers equip themselves

## Why
A soldier that respawns empty-handed could make a wooden sword before rejoining, if the squad
isn't waiting on it. Doctrine calls this pre-combat checks: be equipped before you're employed.
Doing it on its own initiative, within the squad's intent, is mission command.

## Done when
- A soldier can gather wood, craft planks, sticks and a wooden sword, and hold it. All
  deterministic, no LLM.
- The decision to do it fits the hierarchy: the soldier's routine runs when its LACE shows it
  unarmed and its order allows it, for example while holding at a rally point. The squad leader
  can forbid it, for example with "rejoin immediately".

## Notes
- This is the largest pure-Minecraft lift so far:
  - Block breaking: Player Action start/finish digging, break timing, item pickup. None of it
    exists.
  - Crafting: clicks on the inventory's 2x2 grid, or the recipe book. Container clicks and the
    equip step exist already, for the supply point (`bot.supply`).
- The work builds the substrate more than the coordination layer, so it stays behind the
  reporting items (AGENTS.md: favour legibility of coordination over feature completeness).

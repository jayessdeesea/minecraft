---
status: ready
priority: P1
depends-on: []
doctrine: the operator's engagement doctrine for the bot fleet (2026-11-16)
---

# Engagement doctrine: engage, bypass, avoid or break contact

## Why
The mission comes first; a fight is worth having only when it serves the mission or protects the force (operator,
2026-11-16). A soldier that drops its task to chase or watch a threat it needn't or can't fight has been fixed by it.
What a soldier does on contact is built as reflexes and one pure decision over the exchange (`bot.Engagement`;
docs/military-doctrine.md § 8), each kind of contact held to what correct is by the combat matrices. But the order
carries none of it - no weapons control, no criteria, no budget - a melee hostile in contact is closed on whatever
it's doing, and nothing of a contact goes up the chain.

## The doctrine, as given
- **Soldier tier is deterministic**: it checks the criteria its order carries; judgement is the order-writing tier's
  (squad leader, platoon sergeant). A fight is the one thing a soldier leaves its order for unasked, and it goes back
  to the order as it was (backlog/authority-and-missions.md; backlog/soldier-order.md, the stack for interruptions).
- **Order fields**, defaults in brackets: `movement_mode` MOVE | ATTACK_MOVE | HOLD_POSITION [MOVE]; `weapons_control`
  WEAPONS_FREE | WEAPONS_TIGHT | WEAPONS_HOLD [WEAPONS_TIGHT]; `engagement_criteria` [blocking the path or targeting
  this bot]; `bypass_criteria` [neither]; `disengagement_criteria` [health below a threshold or outnumbered past
  one]; `report_contacts` [true].
- **Reachability matrix**, asked first: can it reach me, can I reach it.
  - Both: apply engagement and disengagement criteria.
  - It can, I can't: passive defense - cover, carry on if possible - report contact and the capability gap.
  - I can, it can't: engage only if the criteria are met and the fight serves the task; else ignore.
  - Neither: ignore, keep working, re-check when things change.
- **On contact, in order**: self-defense (attacked and can't break contact: fight, over WEAPONS_HOLD); disengage if a
  criterion is met; classify by the matrix; engage if weapons control and criteria permit, within a time or distance
  budget; else bypass - carry on, routing round a hostile near the path, keep moving; report.
- **Bypassed threats** get a follow-on disposition: outpace, hand off, or re-evaluate after N seconds or within M
  blocks of the destination [re-evaluate].
- **Reports**: contact (type, position, count, matrix cell, action, pursuing?) and capability gap (e.g.
  RANGED_WEAPON) - the platoon sergeant turns repeated gaps into production or loadout.
- **Prevention beats engagement**: sleep against phantoms, lighting and walls against ground mobs.
- **The exchange, not thresholds** (operator, 2026-11-21): one pure decision over time to kill against time to die -
  the approach under fire, armour and each piece's wear, both sides' damage a second - and what breaking off costs.
  Its states: engage (melee, or the bow where that trades better), hold or cover, withdraw, re-engage, decline (not
  prepared - said up the chain, not fought).
- **Deaths are a check on the model, not a case each** (operator, 2026-11-21): a death is read for the dimension the
  battle simulation lacks, and that dimension is added and the matrix grown by it. Not every battle is won: the
  measure is the loss rate an activity carries against its yield (backlog/sites.md).

## What's left
- **The matrix's ignore cells.** A hostile not on the path and not targeting the soldier is carried past; today a
  melee hostile in contact is closed on whatever it's doing. And an archer across a cave at the soldier's own height
  (one more than 4 below is covered from, one in sight within 16 closed on by a soldier armed for melee).
- **The order fields and their defaults** through `Order`, the squad leader and the soldier; the both-reach cell's
  criteria and weapons control read from the order; the engage budget in time or distance.
- **Reports up the chain.** A contact is logged at the soldier (`bot.ContactLog`, `[Contact]`); the contact and
  capability-gap reports to the squad leader, and the platoon sergeant's answer to repeated gaps, aren't built.
- **The rest of the fight on the exchange.** A health line still gates closing on an archer
  (`ContactThresholds.HURT_HEALTH`). Creepers, several hostiles at once and the bow aren't on the exchange. A
  pillager is reckoned as a skeleton and wants to be its own archer - 6 off, following at a walk, a crossbow's 8.
- **The matrices' known cells** (`CombatMatrixTest`, `CombatConditionMatrixTest`, `CombatPairsMatrixTest`,
  `CombatPairsConditionMatrixTest` - each one's `KNOWN`): the creeper short on every ground but the flat and the
  trees, by condition; the pillager cells (wooden sword or sword and shield on hills, 75%; met at 6 on hills, among
  trees, afloat); a soldier met at 6 in a pond by a skeleton (33% - it can't swim clear); a zombie and a skeleton
  together against bare hands in the ravine, the cave and, met hurt, the miner's branch (75-83%).
- **Cover made, not walked to.** `RecordedContactTest.miaGetsOutOfTheDrownedsReach` (`contact/mia-field604`) is a
  known failure: field604, Mia held her post 10.8 from a drowned 9 below in water and was killed by its trident. No
  walk reaches any cover cell from where she stood; what's left is a block put between her and the thrower, or a
  cell dug down out of its line.
- **Two hostiles' rules flipping.** field663 and field666: Eli mining in caves, unarmoured, a stone sword, at 8
  health shot dead in one cell, his answer flipping every two seconds between the zombie ("fights hand to hand") and
  the skeleton ("takes cover"). No built ground reproduces it: his kept ground and the hostiles as logged replayed in
  `RecordedContactTest` first, then the flip taken out once that fails.
- **Replays with moving hostiles**: the battle simulation's hostiles on a kept ground, not only standing where they
  were logged.
- **To verify first**: phantom spawning, and whether the server counts time since rest for these clients; mob follow
  ranges and speeds per type, for outpace and the re-evaluate thresholds.

## Done when
- No soldier is held a minute by a hostile in the matrix's ignore cells, and none chases past its order's budget.

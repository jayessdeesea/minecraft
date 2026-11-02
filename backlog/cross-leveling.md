---
status: ready
priority: P1
doctrine: ATP 3-21.8 (consolidation and reorganization); FM 4-0 (cross-leveling)
---

# Cross-leveling: soldiers pass materials to each other

## Why
Each soldier's kit is its own. A soldier with no logs and no trees in reach can't replace a
worn-out pickaxe, even while the squadmate beside it carries 16. Soldiers with no weapon, no
arrows, or low food can't be helped by the ones with spares. Real squads fix this by
*redistributing*. It's also the first time soldiers act on each other's behalf, which exercises
the coordination layer directly.

## Doctrine
- **Reorganization** (ATP 3-21.8, consolidation and reorganization): after contact the leader
  redistributes ammunition and equipment, so that no one fighter runs dry while others have plenty.
  This is driven by the LACE reports the squad already sends (§ 4).
- **Cross-leveling** (FM 4-0 and unit SOPs): moving supplies laterally between units at the same
  echelon, instead of waiting for resupply from above. Doctrine describes it at company level and
  up. The squad version is the same idea one level down.

## Mapping to Minecraft
- **Giving:** the giver walks next to the receiver, faces it, and throws the stack. That's a
  Container Click in THROW mode on the inventory slot, which drops an item entity in front of it.
- **Receiving:** the receiver walks onto the item entity, and the server picks it up. A dropped
  item has a pickup delay, then goes to the nearest player in reach. Nobody else is in reach once
  the giver steps back.
- **What can move:**
  - logs (pickaxe and sword material)
  - a spare weapon
  - arrows
  - armour
  - food, once soldiers eat (backlog/subsistence.md)

## Done when
- A soldier needing logs for a tool, with no trees in reach, gets them from a squadmate carrying a
  surplus. It then makes its tool. Live: a stone-quarrying squad keeps quarrying past a worn
  pickaxe.
- A bare-handed soldier gets a squadmate's spare weapon, when one exists.
- Each transfer is visible to the chain of command:
  - the giver's LACE drops by what it gave
  - the receiver's rises by the same amount
  - the squad leader's prompt says who gave what to whom

## Notes
- **Who decides:**
  - The squad leader authorises it. This is an order flag, like `rearm`.
  - The pairing (who gives to whom) is a deterministic drill in `command`, like `Resupply`: the
    nearest squadmate with a surplus above its own needs.
  - That keeps the model out of bookkeeping.
- **Surplus:** what the giver doesn't need for its own next tool. For example, 3 logs is a
  pickaxe, so a giver keeps 3 and gives the rest.
- **Wire work:**
  - Container Click with mode THROW.
  - Item entities in `EntityTracker`. It already tracks spawns; the receiver needs to find the
    dropped stack by type and position.
- **The stockpile is the other source.** Drawing logs back out of the supply point's chest is the
  same idea with a chest instead of a squadmate, using `SupplyRun`'s machinery.

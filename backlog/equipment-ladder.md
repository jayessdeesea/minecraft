---
status: proposed
priority: P1
depends-on: [patrol-base]
---

# The equipment ladder: what to make next, and what's worth it

## Why
Soldiers know two recipes, a wooden sword and a wooden pickaxe. Live, squads fight at night with
no armour, wooden or no weapons, and too little food to regenerate. In this session Bravo bled
down to 2-3 health. `CombatScenarioTest` shows what kit is worth: bare-handed against a skeleton
loses 94 in 100, a wooden sword wins 100 in 100.

The materials for much better kit are often already in hand. Squads carry stacks of cobblestone
from quarrying.

## Done when
A soldier, when its squad leader allows rearming, climbs a fixed ladder. Each step is ranked by
survival gained per resource spent, and taken when the materials are in hand or near:
1. **Stone sword and stone pickaxe.** Made from cobblestone and sticks, which the squads already
   carry. More damage (wooden 4 → stone 5) and about twice the durability.
2. **Furnace** (8 cobblestone). It's the gateway to charcoal, and so to torches
   (backlog/patrol-base.md), and to smelting iron.
3. **Iron sword.**
4. **Shield** (6 planks, 1 iron). Vanilla's real answer to arrows, worth more than any dodging
   (backlog/evasion-and-stamina.md).
5. **Iron armour,** chestplate first since it carries the most protection.
6. **Iron pickaxe and axe.**
7. **Diamond, only after iron armour is complete.** Never a luxury tool (a diamond hoe) while the
   armour is leather or missing.

Each step is measured in `CombatScenarioTest` (weapon damage from `WeaponStats`; armour needs
adding to the simulator) and seen live.

## Notes
- **The ladder is a fixed table, not a model decision.** The trade-offs are known; a model
  shouldn't spend calls rediscovering them. Who decides what: the squad leader decides *whether*
  there's time (the existing `rearm` flag); the table decides *what*. Same split as `Formations`.
- **Iron needs mining and smelting:** iron ore (stone pickaxe or better), then the furnace. Ore is
  a survey target like trees and stone (`ResourceSurveyor`).
- **Durability:** tools wear out. The ladder includes re-making a broken tool, not just upgrading.

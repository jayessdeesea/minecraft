---
status: proposed
priority: P3
---

# Decode more item components

## Why
Inventories are decoded into each soldier's `Kit`: weapon in hand, arrows, armour worn. Item
stacks carrying a component `ItemStacks` doesn't know stop the decode for the rest of that
packet, and the type is logged (docs/protocol-spec.md § 3.5, item stacks). Live, an enchanted
sword (enchantments, type 13) was the first case. Enchantments also matter beyond completeness:
Feather Falling and Protection reduce fall damage, which `Risk` currently assumes nothing does.

## Done when
- Enchantments (type 13) decode, and the `Kit` reports protective enchantments where a soldier's
  fall allowance can use them.
- Other component types that soldiers meet in live runs are added as the log names them.

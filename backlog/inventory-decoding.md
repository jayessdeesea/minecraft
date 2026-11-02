---
status: proposed
priority: P3
---

# Decode more item components

## Why
Inventories are decoded into each soldier's `Kit`. Item stacks carrying a component `ItemStacks`
doesn't know stop the decode for the rest of that packet, and the type is logged
(docs/protocol-spec.md § 3.5, item stacks). Named, renamed or otherwise customised items (custom
data, item name, lore) are the likely next cases.

## Done when
- Component types that soldiers meet in live runs are added as the log names them.

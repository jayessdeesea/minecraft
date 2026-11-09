---
status: proposed
priority: P2
depends-on: [rest-plan]
doctrine: ATP 3-37.34 (survivability: hasty to deliberate positions, overhead cover, observation); ATP 3-21.8 (patrol base)
---

# A durable shelter: doors, observation, beds

## Why
Each night every squad builds a hasty hut and seals itself in, and each morning cuts its way out.
A player's base has a door, and lasts. The hasty hut is right for the first night at a foothold;
after that a squad should come back to a shelter that stands.

## Design sketch
- **Hasty, then deliberate** (ATP 3-37.34): the first night's hut stays as it is. From the second,
  the engineer lays out a deliberate shelter at the camp - cobblestone, roofed, lit inside - with a
  **door**. A wooden door is 6 planks for 3. A squad goes in at nightfall
  (docs/echelons/general/operator-rules.md), closes the door behind it, and opens it at dawn.
  Soldiers can't open doors yet (docs/echelons/soldier/capabilities.md): a door is used by a click
  on it, like a table, and the server's block update says whether it's open.
- **Windows - observation, not light.** A fighting position has firing and observation ports,
  so its occupants see the threat before it's at the wall. Glass, smelted from sand, keeps mobs out
  and lets a soldier see out; light inside comes from torches either way. Worth it in the deliberate
  shelter, where a sentry watches the approach at night; not in the hasty hut.
- **Beds**, from backlog/rest-plan.md, go in the deliberate shelter: they set the respawn point,
  end the phantom draw and let the night be skipped.

## Done when
- From the second night, a squad goes into a standing shelter by its door, and out by it at dawn,
  with nothing dug or rebuilt.

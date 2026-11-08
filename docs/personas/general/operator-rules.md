# General: the operator's standing rules

Rules the operator sets in configuration, applied to the general's objectives after it decides
(`app.ChainOfCommand.sheltered`). Every override is logged as a WARN beside what the general had
ordered.

## Shelter at night (`minecraft.general.shelter-at-night`)
From 90 seconds before dark until dawn - a hut takes minutes, more with dirt to dig:
- **The first squad in the roster holds the camp.** An order that isn't a patrol base is replaced
  by the patrol base it was already ordered to build, or with none, by one where its point man
  stands. A squad sealed in is never ordered out.
- **Every other squad is on the night shift:** whatever the general orders, a patrol base included,
  it's sent down the camp's mine. The head is 4 blocks east and 4 north of the camp's centre,
  inside the wall, cut to the iron at y=16 (`ChainOfCommand.nightShift`). With no camp measured
  yet, it shelters like the first. How the squad works the mine at night is in
  [military-doctrine.md § 16](../../military-doctrine.md#16-mining-down).

## The camp (`minecraft.camp`)
The x,z the foothold's standards are measured round (docs/personas/general/decisions.md).

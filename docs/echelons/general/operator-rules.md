# General: the operator's standing rules

Rules the operator sets in configuration, applied to the general's objectives after it decides
(`app.ChainOfCommand.sheltered`). Every override is logged as a WARN beside what the general had
ordered.

## Shelter at night (`minecraft.general.shelter-at-night`)
From nightfall until dawn - a hut goes up in a minute or two, faster than the night's first
monsters spawn and walk in, so the day is worked to its end. The general is still asked 90 seconds
before dark, to plan the night:
- **The first squad in the roster holds the camp.** An order that isn't a patrol base is replaced
  by the patrol base it was already ordered to build, or with none, by one at the camp's centre,
  behind its wall and in its light, for a squad within 48 blocks of it - or where its point man
  stands, further off. A patrol base the general sites near the camp but outside its wall, for a
  squad not yet sealed in, is moved into the camp too. A squad sealed in is never ordered out.
  The hut is sited off the camp's workshop (stand, table, furnace, chest) and the 3x3 round its
  mine head, so the night's base never seals the stockpile in or the way down shut.
- **A squad's detachments come in with it**, but for its miners, who work on underground: a
  soldier with any other role, and no order of its own, is in its squad's main body by night, and
  shelters with it (`ChainOfCommand.sheltering`).
- **Every other squad is on the night shift:** whatever the general orders, a patrol base included,
  it's sent down the camp's mine. The head is 4 blocks east and 4 north of the camp's centre,
  inside the wall, cut to the iron at y=16 (`ChainOfCommand.nightShift`). With no camp measured
  yet, it shelters like the first. How the squad works the mine at night is in
  [military-doctrine.md § 16](../../military-doctrine.md#16-mining-down).

## The keep by day (`minecraft.general.keep-by-day`)
By day, every squad builds the main base's keep (task `KEEP`, [military-doctrine.md § 17](../../military-doctrine.md#17-the-lodgment)),
whatever the general orders; a squad already on it holds it. The night rule still comes first.

## The camp (`minecraft.camp`)
The x,z the foothold's standards are measured round (docs/echelons/general/decisions.md).

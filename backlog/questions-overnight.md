---
status: open
priority: P1
---

# Questions for the operator (2026-11-17)

## Move the mine on its yield, not only its length? (backlog/mining.md)
The keep's mine at (-26, 70, -24) is nearly worked out: its main tunnels have run on to 168 of 192
blocks, every crew working, and the nights' ore has fallen 66, 29, 29, 44, 24 - then 30, 25, 7, 0, 4,
6, 4 (the 7 and 0 nights had causes since fixed: no logs, no pickaxes). Today a mine moves only once
it's dried up by its tunnels' length (`Staircase.MAX_STRETCHES`, 192). Should it move - to the next
untried site, cut ahead by a released squad - once a night's ore is under half of what's expected
two nights running? What it changes: the mine rules, so not changed without you.

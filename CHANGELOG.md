# Changelog

## 1.2.0

### New
- **No-stack areas**: places where no mob is stacked, not even the ones on the WildStacker whitelist like chickens and armadillos. Useful to keep a farm fully vanilla.
  - areas are rectangles from two opposite X Z corners and go from the bottom to the top of the world
  - a merge is blocked when either of the two mobs is inside an area
- **Area commands in game**, like a WorldEdit selection:
  - `/sa area pos1` and `/sa area pos2` set the two corners where you stand
  - `/sa area add <name>` saves the area, it works right away
  - `/sa area remove <name>`, `/sa area list`, `/sa area here`
  - from the console: `/sa area add <name> <world> <x1> <z1> <x2> <z2>`
- Areas can also be written in the config under `no-stack-areas`.
- `/sa unstack` also splits the stacks already inside an area.
- `/sa status` lists the areas. Wrong areas and unknown worlds are reported in the log.

## 1.1.0

### New
- `/sa unstack` splits the stacks of mobs WildStacker no longer stacks, for example after the whitelist was changed:
  - each copy keeps the mob data, like color, variant and age
  - copies are spread at most 8 per block, so vanilla cramming doesn't kill them
  - the stack name (like `x30 Cow`) is removed, names from name tags are kept
  - stacks in chunks loaded later are split too, until the next restart, and logged

### Fixes
- Entities WildStacker can't stack, like armor stands, mannequins, players and NPCs, are skipped everywhere. Before, `/sa unstack` stopped at the first armor stand.

## 1.0.1

### Fixes
- If WildStacker fails to enable, for example because of a tab in its config, the plugin turns itself off with a clear message instead of throwing an exception every few ticks.
- If WildStacker is disabled while the server runs, the plugin turns itself off too.
- An unreadable WildStacker config is reported as such on `/sa reload`.
- Warns when `entities.stack-interval` is 0 in the WildStacker config, since armadillos moved into a farm are then never stacked.

## 1.0.0

First release.
- **Infested**: a stack gets one Infested roll per armadillo on every hit, with the vanilla rules (10% chance, 1 or 2 silverfish).
- **Cramming**: armadillos inside stacks count for entity cramming. A 25th armadillo pushed into a block with a stack of 24 dies like in vanilla.
- **Stacking**: stacks never grow past 24 and only merge armadillos in the same block, so farm stations never mix.
- **Scutes**: a stack drops one scute per armadillo. Brushing still gives one scute.
- Warnings in the log when the WildStacker config is missing the needed settings.

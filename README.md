# StackedArmadillos

A Paper plugin that lets armadillo farms use [WildStacker](https://github.com/BG-Software-LLC/WildStacker) without changing how they work. A stack of 24 armadillos becomes one entity for the server, but it keeps behaving like 24 armadillos: it spawns the same silverfish, drops the same scutes and follows the same cramming rule.

## Why it exists

It was made for the survival server where we play. The goal of the server is to keep the gameplay as vanilla as possible while keeping the lag under control. Players build farms the way they would in singleplayer, and the server must not change what those farms produce.

The heaviest farms on the server are silverfish XP farms built on armadillos:

1. Up to 24 armadillos are packed into one block on top of a campfire.
2. A dispenser throws a splash potion of Infestation on them.
3. The campfire hits every armadillo about twice a second. Armadillos roll up when hurt, and rolled up they take no damage from a campfire, so they never die.
4. Every hit has a 10% chance to spawn 1 or 2 silverfish, which players kill for XP.

A farm with 12 stations of 24 has 288 armadillos, all scared, all rolled up and all hit by a campfire all the time. Each of them runs its own AI, pushes the others in the same block and goes through the damage code twice a second. Together with the silverfish they spawn, they were among the biggest causes of lag on the server.

## The three plugins together

The lag is handled by three plugins, each doing one job:

| Plugin | What it does |
| --- | --- |
| [WildStacker](https://github.com/BG-Software-LLC/WildStacker) | Merges the 24 armadillos of a station into one entity with a stack size of 24. The server runs the AI, collisions and damage of one armadillo instead of 24. |
| StackedArmadillos | Makes that stack behave like 24 armadillos, so the farm produces exactly what it produced before. |
| [FrozenSilverfish](https://github.com/ghiacciolodev/FrozenSilverfish) | Turns off the AI of the silverfish the farm spawns, so the same number of silverfish costs much less. |

WildStacker alone is not enough. For the game a stack is one armadillo, so a station of 24 would:

- roll Infested once per hit instead of 24 times, and spawn about 1/24 of the silverfish
- shed one scute every 5 to 10 minutes instead of 24 (WildStacker 2026.2 doesn't multiply armadillo scutes)
- never kill a 25th armadillo pushed into the block, because entity cramming counts entities and the stack is only one
- take armadillos from the station next to it, because WildStacker merges armadillos up to 10 blocks apart by default

StackedArmadillos fixes all four.

## Why 24

24 is the vanilla limit. The `max_entity_cramming` gamerule is 24 by default: when more than 24 mobs share a block, cramming hurts them until they are 24 again. That's why farms on the server already use 24 armadillos per station, and why a 25th one pushed in dies.

StackedArmadillos keeps that rule:

- a stack never grows past 24
- if a 25th armadillo is pushed into a block with a stack of 24, it dies of cramming like in vanilla, while the stack stays as it is

The number can be changed. It is set in three places, which should match:

| Where | Setting | What it does |
| --- | --- | --- |
| `plugins/StackedArmadillos/config.yml` | `max-stack-size: 24` | Largest stack this plugin allows. |
| `plugins/WildStacker/config.yml` | `entities.limits.ARMADILLO: 24` | Largest stack WildStacker builds. |
| The world | `/gamerule max_entity_cramming 24` | How many armadillos fit in a block before cramming. StackedArmadillos follows the gamerule unless `cramming.limit` is set. |

## What it does

### Infested

When a stack with Infested is hurt, vanilla rolls Infested once, as for any single mob. StackedArmadillos rolls it once more for every other armadillo in the stack, so a stack of 24 gets 24 rolls per hit like 24 separate armadillos.

It copies the vanilla rules of Minecraft 26.2: 10% chance per roll, 1 or 2 silverfish, spawned in the middle of the armadillo and thrown towards where it looks. It rolls on the same hits vanilla does: after a hit a mob can't be hurt again for half a second, and that applies to the stack like to a single armadillo. The silverfish are spawned the same way as vanilla ones, so FrozenSilverfish and any other plugin see them as normal Infested silverfish.

### Cramming

Every 4 ticks the plugin counts the armadillos in each block, counting a stack as the number of armadillos in it. When a block has more than the limit, it keeps the biggest stacks up to the limit and kills the rest:

- a single armadillo over the limit takes cramming damage like in vanilla and dies
- if two stacks share a block, for example 13 and 12, the smaller one loses the armadillos over the limit
- blocks without any stack are left to vanilla

### Stacking

Two armadillos or stacks are merged only when:

- the result is not bigger than `max-stack-size`
- both are in the same block, so an armadillo never moves into the stack of another station

### Scutes

Every armadillo drops a scute every 5 to 10 minutes. When a stack drops its scute, the plugin multiplies it by the stack size, so a stack of 24 drops 24.

Brushing is not multiplied, whether a player or a dispenser does it. In vanilla a brush takes one scute from one armadillo, so it still gives one.

### What it doesn't touch

Single armadillos, chickens and every other mob are left to vanilla. The plugin only changes armadillos that WildStacker has stacked.

## Performance

Without stacking, a station costs 24 armadillos on every tick: 24 AIs, 24 armadillos pushing each other in the same block, 24 campfire hits every half second, and 24 entities to send to nearby players and to save with the chunk. With a stack it costs one.

StackedArmadillos adds little on top: 23 random rolls each time a stack is hit, and a check of the loaded armadillos every 4 ticks, where a station counts as one entity. The silverfish are as many as in vanilla, on purpose. FrozenSilverfish is what makes them cheap.

These savings follow from how the game works, but they haven't been measured with a benchmark yet.

## Installation

StackedArmadillos is a normal plugin and is installed like any other. It works on top of WildStacker, so WildStacker must be installed too, or the server won't load it. FrozenSilverfish is not required, but it is meant to be used with it.

Requirements:

- Paper 26.2
- Java 25
- WildStacker 2026.2

Steps:

1. Put `StackedArmadillos-<version>.jar` in the `plugins` folder, next to WildStacker.
2. Change the WildStacker config as described below.
3. Restart the server.
4. Run `/sa status` and check that there are no warnings.

### WildStacker settings

These go in `plugins/WildStacker/config.yml`, in the `entities` section. The first two are needed:

```yaml
entities:
  merge-radius:
    ARMADILLO: 1
  limits:
    ARMADILLO: 24
```

- **`merge-radius ARMADILLO: 1`**: WildStacker looks for armadillos to merge within this many blocks. With a bigger number, armadillos of two stations close to each other get merged together, and one station empties into the other. StackedArmadillos blocks those merges, but WildStacker doesn't look for another armadillo after a merge is blocked, so the station would simply stop stacking.
- **`limits ARMADILLO: 24`**: the largest stack WildStacker builds. Without it, WildStacker keeps trying to add armadillos to a full stack, StackedArmadillos refuses, and the new armadillos stay unstacked.

These are optional, but help keep the server vanilla:

```yaml
entities:
  # Only the farm mobs are stacked, every other mob stays as it is.
  whitelist:
  - CHICKEN
  - ARMADILLO
  # A station is stacked only once it has at least 15 armadillos.
  # Smaller groups stay as single armadillos.
  minimum-required:
    ARMADILLO: 15
  stack-checks:
    # Babies and adults are never merged.
    AGE: true
    # A mob with a name tag is never merged, so its name is never lost.
    NAME_TAG: true
spawners:
  # Spawners placed next to each other stay separate spawners.
  enabled: false
```

If a later WildStacker version has the option `multiply-armadillo-scutes`, set it to `false`. Otherwise both plugins multiply the scutes and a stack of 24 drops 576.

StackedArmadillos reads the WildStacker config when it starts and on `/sa reload`, and writes a warning in the log for each of these problems it finds.

## Configuration

The config file is `plugins/StackedArmadillos/config.yml`. The defaults are the values used on our server and copy vanilla, so most servers don't need to change anything. Run `/sa reload` after editing it.

```yaml
# Worlds where the plugin works. [] means every world.
worlds: []

# The most armadillos one stack can hold.
max-stack-size: 24

# Only merge armadillos standing in the same block.
same-block-only: true

infested:
  # Give a stack one Infested chance for each armadillo in it.
  enabled: true
  # Chance of spawning silverfish on each hit, 0.1 = 10%.
  chance: 0.1
  # How many silverfish spawn when the chance hits.
  min-silverfish: 1
  max-silverfish: 2

cramming:
  # Count the armadillos inside stacks for entity cramming.
  enabled: true
  # The most armadillos in one block. -1 = use the max_entity_cramming gamerule.
  limit: -1
  # Damage dealt to the armadillos over the limit.
  damage: 6.0
  # How often to check, in ticks. 20 ticks = 1 second.
  interval-ticks: 4

scutes:
  # A stack drops one scute for each armadillo in it.
  enabled: true
```

| Setting | Default | What it means |
| --- | --- | --- |
| `worlds` | `[]` | The worlds where the plugin works, for example `[world, world_nether]`. `[]` means every world. World names must be written exactly as they are, capital letters included. |
| `max-stack-size` | `24` | The most armadillos one stack can hold. See [Why 24](#why-24). `0` means no limit from this plugin. |
| `same-block-only` | `true` | Armadillos are merged only if they stand in the same block, so two stations next to each other never mix. |
| `infested.enabled` | `true` | A stack hit by a campfire gets one Infested chance for each armadillo in it. With `false`, a stack spawns silverfish like a single armadillo. |
| `infested.chance` | `0.1` | The chance of spawning silverfish on each hit. `0.1` is 10%, the vanilla value. |
| `infested.min-silverfish` and `max-silverfish` | `1` and `2` | How many silverfish spawn when the chance hits. 1 or 2 is the vanilla value. |
| `cramming.enabled` | `true` | Armadillos inside a stack count for entity cramming, so a 25th armadillo dies like in vanilla. With `false`, any number of armadillos can be pushed into a block with a stack. |
| `cramming.limit` | `-1` | The most armadillos in one block. `-1` uses the `max_entity_cramming` gamerule of the world, which is 24 unless changed. `0` turns cramming off for stacks. |
| `cramming.damage` | `6.0` | The damage an armadillo over the limit takes on each hit. `6` is the vanilla value. A rolled up armadillo takes less, `(damage - 1) / 2`, so it dies after a few hits. |
| `cramming.interval-ticks` | `4` | How often the plugin checks the blocks. 20 ticks are one second, so 4 means 5 times a second, about as fast as vanilla cramming. |
| `scutes.enabled` | `true` | A stack drops one scute for each armadillo in it. Brushing always gives one scute. |

## Commands and permissions

| Command | What it does |
| --- | --- |
| `/sa status` | Shows the settings, how many armadillos and stacks are loaded, how many armadillos the stacks hold, and any warning about the WildStacker config. |
| `/sa reload` | Reloads the config and checks the WildStacker config again. |

`/sa` is short for `/stackedarmadillos`. Both commands need the permission `stackedarmadillos.admin`, which operators have by default.

## Test results

The plugin was tested on a local Paper 26.2 server (build 129) with WildStacker 2026.2, using the WildStacker config of our server with the settings above. The tests are in the [`testing`](testing) folder. All 19 checks passed.

**Infested.** One station with 24 separate armadillos next to one with a stack of 24, campfires lit for 90 seconds:

| | 24 separate armadillos | Stack of 24 |
| --- | --- | --- |
| Without StackedArmadillos | 664 silverfish | 20 silverfish |
| With StackedArmadillos | 645 silverfish | 651 silverfish |

The vanilla value is 24 armadillos × 2 hits per second × 10% × 1.5 silverfish × 90 seconds = 648.

**Stacking and cramming:**

| Case | Result |
| --- | --- |
| 25 armadillos pushed into one block | One stack of 24, the 25th dies of cramming |
| Stacks of 13 and 12 in one block | 13 and 11 |
| Two stacks of 24 in one block | One stack of 24 is left |
| Stack of 24 with `max_entity_cramming` at 10 | Cut to 10 |
| `cramming.limit: 0`, a stack of 24 and one more armadillo | Both stay alive |
| 24 separate armadillos, no stack | Left to vanilla |
| Two stations 2 blocks apart, 20 armadillos each | 20 and 20 |
| Same, with WildStacker merge radius 10 | 20 and 20, but the second station doesn't stack |
| Same, with merge radius 10 and `same-block-only: false` | 24 and 8: the second station emptied into the first, and the armadillos over 24 died of cramming |

**Scutes**, stack of 24:

| Case | Scutes |
| --- | --- |
| Dropped over time, with StackedArmadillos | 24 |
| Dropped over time, without StackedArmadillos | 1 |
| Brushed by a dispenser | 1 |
| A scute item dropped next to the stack | stays 1 |

Brushing by a player wasn't tested, because the tests run without players. It is handled the same way as the dispenser.

## Building from source

```
./gradlew build
```

The jar is written to `build/libs`. The build downloads the Paper API from the PaperMC repository and the WildStacker API from the BG-Software repository.

## License

MIT, see [LICENSE](LICENSE).

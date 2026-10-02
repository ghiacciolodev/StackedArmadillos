"""Functional tests for StackedArmadillos, run against a live Paper test server.

Needs WildStacker and the SaTest helper on the server. Every test builds its
own station around x 0 to 210, z 0 to 20 and removes its mobs at the end. It
rewrites the config of StackedArmadillos and the armadillo lines in the
merge-radius and limits sections of the WildStacker config. See
testing/README.md.
"""
import argparse
import re
import time

from rcon import Rcon

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--host', default='127.0.0.1')
parser.add_argument('--port', type=int, default=25575, help='RCON port (default 25575)')
parser.add_argument('--password', required=True, help='RCON password')
parser.add_argument('--server', required=True, help='folder of the test server')
parser.add_argument('--seconds', type=int, default=60, help='length of each Infested phase (default 60)')
parser.add_argument('--only', help='run only the tests whose name contains this text')
args = parser.parse_args()

rc = Rcon(args.host, args.port, args.password)
CONFIG = f'{args.server}/plugins/StackedArmadillos/config.yml'
WILDSTACKER_CONFIG = f'{args.server}/plugins/WildStacker/config.yml'
results = []


def check(name, ok, detail=''):
    results.append(ok)
    print(f'{"PASS" if ok else "FAIL"}  {name}  {detail}', flush=True)


def write_config(max_stack=24, same_block=True, infested=True, cramming=True, limit=-1, scutes=True):
    with open(CONFIG, 'w', encoding='utf-8') as f:
        f.write(f'worlds: []\n'
                f'max-stack-size: {max_stack}\n'
                f'same-block-only: {str(same_block).lower()}\n'
                f'infested:\n  enabled: {str(infested).lower()}\n  chance: 0.1\n'
                f'  min-silverfish: 1\n  max-silverfish: 2\n'
                f'cramming:\n  enabled: {str(cramming).lower()}\n  limit: {limit}\n'
                f'  damage: 6.0\n  interval-ticks: 4\n'
                f'scutes:\n  enabled: {str(scutes).lower()}\n')
    rc('sa reload')


def wildstacker(radius, limit):
    """Sets the armadillo merge radius and stack limit in WildStacker. None removes the line.
    Also sets the entities stack-interval to 100, as the README asks."""
    with open(WILDSTACKER_CONFIG, encoding='utf-8') as f:
        text = f.read()
    start = text.index('\nentities:')
    head, entities = text[:start], text[start:]
    values = {'merge-radius': radius, 'limits': limit}
    out = []
    section = None
    for line in entities.split('\n'):
        if line.startswith('  ') and not line.startswith('   ') and line.strip().endswith(':'):
            section = line.strip()[:-1]
        # Only the armadillo lines of these two sections, minimum-required is left alone.
        if section in values and line.startswith('    ARMADILLO:'):
            continue
        out.append(line)
        if section in values and line == f'  {section}:' and values[section] is not None:
            out.append(f'    ARMADILLO: {values[section]}')
            values[section] = None
    entities = re.sub(r'\n  stack-interval: \d+', '\n  stack-interval: 100', '\n'.join(out), count=1)
    with open(WILDSTACKER_CONFIG, 'w', encoding='utf-8') as f:
        f.write(head + entities)
    rc('stacker reload')
    # WildStacker applies the new settings shortly after the command returns.
    time.sleep(2)


def block(x, z):
    """Returns (entities, total armadillos) in the block on top of the campfire."""
    m = re.search(r'entities=(\d+) total=(\d+)', rc(f'satest block {x} -60 {z}'))
    return int(m.group(1)), int(m.group(2))


def stats():
    out = rc('satest stats')
    infested = dict((k, int(v)) for k, v in re.findall(r'(-?\d+,-?\d+)=(\d+)', out.split('armadilloDeaths')[0]))
    deaths = dict((k, int(v)) for k, v in re.findall(r'(\w+)=(\d+)', out.split('armadilloDeaths=')[1]))
    return infested, deaths


def items(x, z):
    m = re.search(r'amount=(\d+)', rc(f'satest items {x + 0.5} -59 {z + 0.5} 3 armadillo_scute'))
    return int(m.group(1))


def station(x, z, lit=False):
    """A campfire in a glass box one block high, like a farm station."""
    rc(f'fill {x - 1} -60 {z - 1} {x + 1} -58 {z + 1} glass')
    rc(f'setblock {x} -59 {z} air')
    rc(f'setblock {x} -60 {z} campfire[lit={str(lit).lower()}]')


def summon(x, z, count, tag, stackable=True, delay=0.05):
    tags = f'"{tag}"' + ('' if stackable else ',"nostack"')
    for _ in range(count):
        rc(f'summon armadillo {x + 0.5} -59.5 {z + 0.5} {{Tags:[{tags}],PersistenceRequired:1b}}')
        time.sleep(delay)


def clear():
    rc('satest clear')
    rc('kill @e[type=silverfish]')
    rc('kill @e[type=item]')
    time.sleep(1)


def wait_for(condition, seconds):
    end = time.time() + seconds
    while time.time() < end:
        if condition():
            return True
        time.sleep(0.5)
    return condition()


def selected(name):
    return not args.only or args.only in name


# Setup
rc('gamerule spawn_monsters false')
rc('gamerule max_entity_cramming 24')
rc('forceload add 0 0 224 32')
rc('satest cancelsilverfish true')
wildstacker(1, 24)
write_config()
clear()

if selected('stack limit'):
    station(0, 0)
    rc('satest resetstats')
    summon(0, 0, 25, 'limit', delay=0.3)
    ok = wait_for(lambda: block(0, 0) == (1, 24), 10)
    deaths = stats()[1]
    check('stack limit: 25 armadillos in a block end as one stack of 24', ok, f'block={block(0, 0)}')
    check('stack limit: the 25th dies of cramming', deaths.get('cramming') == 1, f'deaths={deaths}')
    clear()

if selected('max-stack-size'):
    write_config(max_stack=10, cramming=False)
    station(16, 0)
    summon(16, 0, 25, 'max', delay=0.3)
    time.sleep(4)
    out = rc('satest block 16 -60 0')
    sizes = [int(s.split('/')[0]) for s in out.split('list=')[1].split()]
    check('max-stack-size 10: no stack is bigger than 10, nothing dies with cramming off',
          max(sizes) <= 10 and sum(sizes) == 25, f'stacks={sorted(sizes, reverse=True)}')
    write_config()
    clear()

if selected('cramming split'):
    station(32, 0)
    summon(32, 0, 1, 's13', stackable=False)
    summon(32, 0, 1, 's12', stackable=False)
    rc('satest setstack s13 13')
    rc('satest setstack s12 12')
    ok = wait_for(lambda: block(32, 0)[1] == 24, 5)
    out = rc('satest block 32 -60 0')
    check('cramming: stacks of 13 and 12 in a block are cut to 24 in total', ok, out)
    clear()

if selected('cramming whole stack'):
    station(48, 0)
    rc('satest resetstats')
    summon(48, 0, 1, 'a24', stackable=False)
    summon(48, 0, 1, 'b24', stackable=False)
    rc('satest setstack a24 24')
    rc('satest setstack b24 24')
    ok = wait_for(lambda: block(48, 0) == (1, 24), 15)
    check('cramming: of two stacks of 24 in a block only one is left', ok,
          f'block={block(48, 0)} deaths={stats()[1]}')
    clear()

if selected('cramming gamerule'):
    station(64, 0)
    rc('gamerule max_entity_cramming 10')
    summon(64, 0, 1, 'g', stackable=False)
    rc('satest setstack g 24')
    ok = wait_for(lambda: block(64, 0)[1] == 10, 5)
    check('cramming: follows the max_entity_cramming gamerule (10)', ok, f'block={block(64, 0)}')
    rc('gamerule max_entity_cramming 24')
    clear()

if selected('cramming off'):
    write_config(limit=0)
    station(80, 0)
    summon(80, 0, 1, 'off', stackable=False)
    rc('satest setstack off 24')
    summon(80, 0, 1, 'off1', stackable=False)
    time.sleep(5)
    check('cramming: limit 0 turns it off, 24 + 1 stay alive', block(80, 0) == (2, 25), f'block={block(80, 0)}')
    write_config()
    clear()

if selected('vanilla cramming'):
    # Without stacks the plugin leaves cramming to vanilla.
    station(96, 0)
    rc('satest resetstats')
    summon(96, 0, 24, 'v', stackable=False)
    time.sleep(5)
    check('cramming: 24 armadillos without stacks are left alone', block(96, 0) == (24, 24),
          f'block={block(96, 0)}')
    clear()

if selected('infested'):
    # V: 24 separate armadillos, vanilla. S: one stack of 24.
    vx, sx, z = 112, 136, 20
    station(vx, z)
    station(sx, z)
    summon(vx, z, 24, 'inf_v', stackable=False)
    summon(sx, z, 24, 'inf_s')
    wait_for(lambda: block(sx, z) == (1, 24), 10)
    print(f'      stations: vanilla={block(vx, z)} stacked={block(sx, z)}', flush=True)
    rc('effect give @e[type=armadillo] infested infinite 0 true')
    rc('effect give @e[type=armadillo] regeneration infinite 4 true')

    def phase(label):
        rc('satest resetstats')
        rc(f'setblock {vx} -60 {z} campfire[lit=true]')
        rc(f'setblock {sx} -60 {z} campfire[lit=true]')
        time.sleep(args.seconds)
        rc(f'setblock {vx} -60 {z} campfire[lit=false]')
        rc(f'setblock {sx} -60 {z} campfire[lit=false]')
        infested = stats()[0]
        v, s = infested.get(f'{vx},{z}', 0), infested.get(f'{sx},{z}', 0)
        print(f'      {label}: silverfish in {args.seconds}s vanilla={v} stacked={s}', flush=True)
        return v, s

    write_config(infested=False)
    v, s = phase('infested off')
    check('infested off: the stack spawns far fewer silverfish than vanilla', s * 5 < v, f'vanilla={v} stacked={s}')

    write_config(infested=True)
    v, s = phase('infested on')
    check('infested on: the stack spawns as many silverfish as vanilla (within 15%)',
          v > 0 and abs(s - v) / v < 0.15, f'vanilla={v} stacked={s}')
    check('infested: the armadillos are still alive', block(vx, z) == (24, 24) and block(sx, z) == (1, 24),
          f'vanilla={block(vx, z)} stacked={block(sx, z)}')
    clear()

if selected('scutes'):
    x, z = 160, 0
    station(x, z)

    def shed(tag):
        rc('kill @e[type=item]')
        rc(f'data modify entity @e[type=armadillo,tag={tag},limit=1] scute_time set value 1')
        time.sleep(2)
        return items(x, z)

    def dispenser_brush():
        # A dispenser under the glass roof, pointing down at the stack.
        rc('kill @e[type=item]')
        rc(f'setblock {x} -58 {z} dispenser[facing=down]{{Items:[{{Slot:0b,id:"minecraft:brush",count:1}}]}}')
        rc(f'setblock {x} -57 {z} redstone_block')
        time.sleep(2)
        found = items(x, z)
        rc(f'setblock {x} -57 {z} air')
        rc(f'setblock {x} -58 {z} glass')
        return found

    def dropped_item():
        rc('kill @e[type=item]')
        rc(f'summon item {x + 0.5} -59.4 {z + 0.5} {{Item:{{id:"minecraft:armadillo_scute",count:1}}}}')
        time.sleep(2)
        return items(x, z)

    summon(x, z, 24, 'sc')
    wait_for(lambda: block(x, z) == (1, 24), 10)

    found = shed('sc')
    check('scutes: a stack of 24 sheds 24 scutes', found == 24, f'scutes={found}')
    found = dispenser_brush()
    check('scutes: a dispenser brush gives 1 scute, like vanilla', found == 1, f'scutes={found}')
    found = dropped_item()
    check('scutes: a scute dropped next to the stack stays 1', found == 1, f'scutes={found}')

    # WildStacker 2026.2 alone doesn't multiply armadillo scutes.
    write_config(scutes=False)
    found = shed('sc')
    check('scutes: without the plugin a stack of 24 sheds 1 scute', found == 1, f'scutes={found}')

    write_config()
    clear()

if selected('warnings'):
    wildstacker(None, None)
    out = rc('sa status')
    check('warnings: missing armadillo limit and merge radius in WildStacker are pointed out',
          'entities.limits.ARMADILLO' in out and 'merge-radius' in out, out.replace('\n', ' | '))
    wildstacker(1, 24)
    out = rc('sa status')
    check('warnings: none with the recommended WildStacker settings', 'Warning' not in out, out.replace('\n', ' | '))

if selected('neighbors'):
    def neighbors():
        """Two stations 2 blocks apart, filled one armadillo at a time with 20 each."""
        station(200, 20)
        station(202, 20)
        for _ in range(20):
            summon(200, 20, 1, 'na', delay=0.2)
            summon(202, 20, 1, 'nb', delay=0.2)
        time.sleep(4)
        a, b = block(200, 20), block(202, 20)
        clear()
        return a, b

    a, b = neighbors()
    check('neighbors: two stations 2 blocks apart keep 20 armadillos each', a == (1, 20) and b == (1, 20),
          f'a={a} b={b}')

    # With the WildStacker default radius of 10, only same-block-only keeps them apart.
    wildstacker(None, 24)
    a, b = neighbors()
    check('neighbors: with merge radius 10, same-block-only still keeps 20 in each station',
          a[1] == 20 and b[1] == 20, f'a={a} b={b}')
    write_config(same_block=False)
    a, b = neighbors()
    print(f'      radius 10 without same-block-only: a={a} b={b}', flush=True)
    write_config()
    wildstacker(1, 24)

print(f'\n{sum(results)}/{len(results)} passed', flush=True)
raise SystemExit(0 if all(results) else 1)

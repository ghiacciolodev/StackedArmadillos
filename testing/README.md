# Testing

These are the scripts used to test StackedArmadillos on a real Paper server. They are not unit tests: they send commands to a running test server through RCON and check what happens to real armadillos stacked by WildStacker.

Don't run them on a server with players. They build stations, summon and remove armadillos, change gamerules and rewrite the config files of StackedArmadillos and WildStacker.

## What's here

- `suite.py`: the functional tests. It checks the stack limit, cramming in its different cases, Infested next to vanilla armadillos, scutes, the warnings about the WildStacker settings and stations next to each other.
- `helper/`: a small plugin used only by the tests. Commands can't show the stack size of an entity, so the helper reads it. It also counts the silverfish spawned by Infested in each block and cancels them so they don't pile up, counts how armadillos die, keeps armadillos tagged `nostack` from stacking, sets stack sizes and removes armadillos without killing them. Never install it on a real server.
- `rcon.py`: the RCON client used by the script.

## Setting up the test server

1. Use a fresh Paper 26.2 server with a flat world. The script expects the ground at y -61, which is the default flat world. In `server.properties`:

   ```
   level-type=minecraft\:flat
   enable-rcon=true
   rcon.password=<something>
   ```

2. Build the plugin and the helper from the root of the repository:

   ```
   ./gradlew build
   ./gradlew -p testing/helper build
   ```

3. Put `build/libs/StackedArmadillos-<version>.jar`, `testing/helper/build/libs/SaTest.jar` and WildStacker 2026.2 in the server's `plugins` folder, then start the server once so WildStacker writes its config.

The script needs Python 3.10 or newer, with no extra packages.

## Running the tests

From the `testing` folder:

```
python suite.py --port <rcon port> --password <rcon password> --server <server folder>
```

It takes about 5 minutes and prints PASS or FAIL for each check, with the numbers behind it. The exit code is 0 when everything passed. Use `--seconds` to change the length of each Infested phase (60 by default, the main README uses 90) and `--only <text>` to run only the tests whose name contains that text, for example `--only cramming`.

The tests use the area from x 0 to 210, z 0 to 20, which the script force loads.

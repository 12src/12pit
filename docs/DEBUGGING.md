# Running and debugging 12pit

## Prepare the environment

Use JDK 21 to run Gradle. The Minecraft client uses the project's Java 8 toolchain; Gradle can find or download it. Node.js and npm are only needed to build the bundled web interface. CI uses Node.js 24.

In IntelliJ IDEA, open the repository as a Gradle project and set Gradle's JVM to JDK 21. In VS Code, install the recommended extensions. `Ctrl+Shift+B` runs `assemble`.

## Run the development client

From the repository root on Windows, run:

```powershell
.\gradlew.bat runClient
```

On Linux or macOS, run:

```sh
./gradlew runClient
```

Gradle builds the bundled web interface with the mod. The development game directory is `run/`. Check `run/logs/latest.log` for client errors and the Web UI address.

## Work on the web interface

Start the game first. Press Right Shift to open the bundled interface. The log prints its loopback URL. The interface tries `http://127.0.0.1:60916/` first and uses another free port if that one is occupied. If the key does not open a browser, open the URL from the log.

For live frontend changes, start Vite in a second terminal.

```powershell
npm ci --prefix web-ui
npm run dev --prefix web-ui
```

Open the URL printed by Vite. Its `/api` proxy uses port `60916` by default. If the game uses another port, set the target before starting Vite.

```powershell
$env:WEB_UI_TARGET = 'http://127.0.0.1:<game-port>'
npm run dev --prefix web-ui
```

Replace `<game-port>` with the port from the log. The frontend needs a running game; it has no sample-data mode. To update the bundled page, rebuild and restart the mod.

## Run checks

From the repository root on Windows, run these commands. On Linux or macOS, use `./gradlew` instead of `./gradlew.bat`.

```powershell
.\gradlew.bat check
.\gradlew.bat assemble
.\gradlew.bat spotlessCheck
npm run format:check --prefix web-ui
```

`check` runs Java tests, including `ArchitectureTest`. `assemble` builds the web interface and puts the jar in `build/libs/`. The formatting commands only check files; they do not change them.

## Troubleshoot

When Gradle sync or a toolchain step fails, check that Gradle uses JDK 21 and can find or download Java 8. If the Vite page loads but requests fail, make sure the game is running and `WEB_UI_TARGET` matches its current address. If the Web UI cannot start, search the game log for `Web UI is unavailable`. Include relevant log lines and steps to reproduce when reporting a failure.

Profiles are stored under `12pit/config/` in the Minecraft game directory. Relations are stored in `12pit/relations.json`. If you edit these files to diagnose a load error, make a backup first. Do not remove saved data to clear a temporary session problem.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth) is included in the development runtime. To test with an authenticated account, enable the `devauth.enabled` JVM property and follow the [DevAuth configuration guide](https://github.com/DJtheRedstoner/DevAuth#configuration). You do not need it to build the jar.

# Running and debugging 12pit

## Setup

The [README requirements](../README.md#requirements) list the required JDK and Node.js versions.

## Run the development client

On Windows, run:

```powershell
.\gradlew.bat runClient
```

On Linux/macOS, run:

```sh
./gradlew runClient
```

The development game directory is `run/`. Look into `run/logs/latest.log` for any client errors and Web UI address.

## Develop the web interface

Start the game, then press Right Shift (the default key) to open the web interface. If the key does not open the browser, use the URL from the log.

By default, the server listens to `http://127.0.0.1:60916/`. In case the port is already occupied, the server uses some other available port. The current address is shown in the log.

For live changes in the frontend, launch Vite in a separate terminal window:

```powershell
npm ci --prefix web-ui
npm run dev --prefix web-ui
```

Launch the URL printed by Vite. By default, the `/api` proxy listens to port `60916`. In case the game runs on another port, set the `target` variable before starting Vite.

```powershell
$env:WEB_UI_TARGET = 'http://127.0.0.1:<game-port>'
npm run dev --prefix web-ui
```

Replace `<game-port>` with the port from the log. Rebuild and restart the mod to update the bundled page.

## Run checks

On Windows, run the following commands from the repository root. On Linux or macOS, use `./gradlew` instead of `./gradlew.bat`.

```powershell
.\gradlew.bat check
.\gradlew.bat assemble
.\gradlew.bat spotlessCheck
npm run format:check --prefix web-ui
```

The command `check` runs the Java tests, including `ArchitectureTest`. The command `assemble` builds the web interface and produces the jar file in `build/libs/`.

Install dependencies with `npm ci --prefix web-ui` before running the npm check.

## Troubleshooting

If Gradle sync fails or reports a toolchain error, check that Gradle uses JDK 21 and can find or download the Java 8 toolchain.

In case the Vite page is opened, but the requests fail, make sure that the game is launched and the `WEB_UI_TARGET` matches the current address of the game. If the Web UI fails to launch, look for the "Web UI is unavailable" message in the game log.

While reporting a failure, provide the relevant log messages and steps to reproduce it.

The saved data is in `12pit/` directory in the Minecraft game directory. Profiles are stored in `12pit/config/`, relations are stored in `12pit/relations.json`, and swap bindings are stored in `12pit/swap-bindings.json`.

Create a backup copy of these files before changing them in order to diagnose a load error. Do not delete the saved data in order to reset a temporary session issue.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth) is provided in the development environment. To test with an authenticated account, enable the `devauth.enabled` JVM property and follow the [DevAuth configuration guide](https://github.com/DJtheRedstoner/DevAuth#configuration).

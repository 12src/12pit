# Running and debugging 12pit

## Setup

As per [README requirements](../README.md#requirements), the versions of JDK and Node.js are listed. The different Java versions are used by Gradle and Minecraft client: JDK 21 for Gradle and Java 8 toolchain for the client.

In IntelliJ IDEA, open the repository as Gradle project and configure JDK 21 for Gradle. In VS Code, install the recommended extensions. `Ctrl+Shift+B` will run `assemble`.

## Run the development client

From the repository root on Windows, run:

```powershell
.\gradlew.bat runClient
```

From the repository root on Linux or macOS, run:

```sh
./gradlew runClient
```

`runClient` builds the bundled web interface, therefore, Node.js and npm should be available.

The development game directory is `run/`. Look into `run/logs/latest.log` for any client errors and Web UI address.

## Develop the web interface

Firstly, launch the game. Then press the Right Shift key (default key) to open the bundled web interface in the browser. If the key does not open the browser, open the URL from the log.

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

Replace `<game-port>` with the port from the log. The frontend does not have the sample-data mode. Update the bundled page by rebuilding and restarting the mod.

## Run checks

From the repository root on Windows, run the following commands. From the repository root on Linux or macOS, use `./gradlew` instead of `./gradlew.bat`.

```powershell
.\gradlew.bat check
.\gradlew.bat assemble
.\gradlew.bat spotlessCheck
npm run format:check --prefix web-ui
```

The command `check` runs the Java tests, including `ArchitectureTest`. The command `assemble` builds the web interface and produces the jar file in `build/libs/`.

The formatting commands just check the files. They don't modify the files. Install the required dependencies with `npm ci --prefix web-ui` before running the npm check.

## Troubleshooting

In case of Gradle sync or toolchain error, make sure that Gradle uses JDK 21 and finds/download the Java 8.

In case the Vite page is opened, but the requests fail, make sure that the game is launched and the `WEB_UI_TARGET` matches the current address of the game. If the Web UI fails to launch, look for the "Web UI is unavailable" message in the game log.

While reporting a failure, provide the relevant log messages and steps to reproduce it.

The saved data is in `12pit/` directory in the Minecraft game directory. Profiles are stored in `12pit/config/`, relations are stored in `12pit/relations.json`, and swap bindings are stored in `12pit/swap-bindings.json`.

Create a backup copy of these files before changing them in order to diagnose a load error. Do not delete the saved data in order to reset a temporary session issue.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth) is provided in the development environment. To test with an authenticated account, enable the `devauth.enabled` JVM property and follow the [DevAuth configuration guide](https://github.com/DJtheRedstoner/DevAuth#configuration). You don't need DevAuth to build the jar.

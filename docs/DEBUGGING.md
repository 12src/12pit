# Running and debugging 12pit

## Setup

Install the JDK and Node.js versions mentioned in [Requirements](../README.md#requirements).

## Run the development client

On Windows:

```powershell
.\gradlew.bat runClient
```

On Linux or macOS:

```sh
./gradlew runClient
```

The development client launches in `run/`. See `run/logs/latest.log` for any errors in the client and the local URL of the configuration interface.

To launch Ornithe, run `./gradlew :ornithe:runClient` or `.\gradlew.bat :ornithe:runClient`. Its working directory is `ornithe/run/`, and its log is `ornithe/run/logs/latest.log`.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth) is included in the Forge development environment. For testing with a logged-in Minecraft account, set the `devauth.enabled` JVM property and configure it as explained in the [DevAuth documentation](https://github.com/DJtheRedstoner/DevAuth#configuration).

`./gradlew assemble` builds both targets and puts `12pit.jar` and `12pit-ornithe.jar` in `build/libs/`. Use `./gradlew remapJar` to build Forge alone, or `./gradlew :ornithe:remapJar` to build Ornithe alone. The latter writes to `ornithe/build/libs/`.

## Develop the configuration interface

After launching the game, press the default key bind (Right Shift) to open the HUD editor. With no HUD element selected (click empty space to clear the selection), press the same key again to open the configuration interface in your browser window. If your browser didn't launch automatically, copy the URL from `run/logs/latest.log`.

By default, the local server is listening to `http://127.0.0.1:60916/`, or a random port if `60916` is already occupied.

For developing the frontend part with the live reload, you need to launch Vite in a different terminal:

```powershell
npm ci --prefix web-ui
npm run dev --prefix web-ui
```

Open the URL printed by Vite. By default, the `/api` proxy will forward requests to port `60916`. In case the game uses another port, set the `WEB_UI_TARGET` variable before starting Vite:

```powershell
$env:WEB_UI_TARGET = 'http://127.0.0.1:<game-port>'
npm run dev --prefix web-ui
```

After you are done, rebuild and relaunch the game to refresh the bundled page.

## Run checks

Run the tests and formatting checks in the repository root (using `./gradlew` on Linux and macOS):

```powershell
.\gradlew.bat check
npm ci --prefix web-ui
npm run format:check --prefix web-ui
```

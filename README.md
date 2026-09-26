# 12pit

> [!WARNING]
> 12pit is currently a work in progress and not yet playable.
> The first stable version is expected around October 2026.

12pit is a free and open-source mod for The Pit, aiming to stay lightweight with minimal performance impact.

## Requirements

Run Gradle with JDK 21. The project targets Java 8 via the Gradle toolchain, which will detect your local Java 8 installation or download it automatically if missing.
Node.js and npm are needed to build the bundled web interface. They are not needed to run the mod.

## Web Interface

The mod serves its bundled interface on `http://127.0.0.1:60916/` when available, or on another free loopback port if that port is in use. The configured keybind (Right Shift by default) opens the correct address, which is also printed in the game log. Settings and profiles use the existing config system; friends and enemies are shared across profiles in `12pit/relations.json`. The HUD Editor remains separate.

To test frontend changes:

1. Run the mod in Minecraft (for local development, use `./gradlew runClient`, or `.\gradlew.bat runClient` on Windows).
2. In `web-ui/`, run `npm ci` once, then `npm run dev`.
3. Open the local URL printed by Vite. It proxies `/api` to the running game at `127.0.0.1:60916`, and Vue/CSS changes update live. If the game used another port, set `WEB_UI_TARGET` to the address printed in the game log before starting Vite.

For the bundled page, use the keybind or open the address printed in the game log. Rebuild and restart the mod to see frontend edits there. The frontend has no sample-data mode and needs a running game. `npm run build` creates static HTML, JS, and CSS in `web-ui/dist/`; Gradle packages them in the mod jar.

## Developing with VS Code

Install the workspace's recommended extensions when VS Code prompts. The Java extension pack includes Java language support, debugging, testing, and Gradle integration; EditorConfig keeps basic whitespace settings consistent across file types.

Use `Ctrl+Shift+B` (`Cmd+Shift+B` on macOS) to run the default `assemble` task. Run `Tasks: Run Test Task` for `check`, or open `Tasks: Run Task` for the other pre-configured tasks.

## Developing with IntelliJ IDEA

Open the repository as a Gradle project and set the Gradle JVM to JDK 21. After Gradle sync finishes, use the Gradle tool window to run `assemble`, `check`, `spotlessApply`, or `runClient`.

## Testing

Run `./gradlew check` for project verification and `./gradlew runClient` for in-game testing. On Windows, use `gradlew.bat` instead.

[DevAuth](https://github.com/DJtheRedstoner/DevAuth)  is included in the development runtime.
If you need to test with an authenticated account, set the `devauth.enabled` JVM property to `true` and follow the [upstream documentation](https://github.com/DJtheRedstoner/DevAuth#configuration) to set up your account.

## Contributing

Don't worry if CI fails, the build breaks, or you run into weird bugs. Don't let that stop you from opening a PR.
As long as the PR is relevant and you're still interested in contributing, we're happy to help you fix things.

## Third-Party Notices

12pit bundles the following third-party components:

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin): MIT License. License text: [`META-INF/third-party-licenses/MIXIN-MIT.txt`](src/main/resources/META-INF/third-party-licenses/MIXIN-MIT.txt).
- [Bouncy Castle](https://github.com/bcgit/bc-java): MIT License. License text: [`META-INF/third-party-licenses/BOUNCY-CASTLE-MIT.md`](src/main/resources/META-INF/third-party-licenses/BOUNCY-CASTLE-MIT.md).
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket): MIT License. License text: [`META-INF/third-party-licenses/JAVA-WEBSOCKET-MIT.txt`](src/main/resources/META-INF/third-party-licenses/JAVA-WEBSOCKET-MIT.txt).
- [SLF4J](https://github.com/qos-ch/slf4j): MIT License. License text: [`META-INF/third-party-licenses/SLF4J-MIT.txt`](src/main/resources/META-INF/third-party-licenses/SLF4J-MIT.txt).
- [Vue](https://vuejs.org/): MIT License; [Lucide](https://lucide.dev/): ISC License. Their license texts are packaged with the web interface in `META-INF/third-party-licenses/`.
- [Noto Sans SC](https://fonts.google.com/noto/specimen/Noto+Sans+SC): SIL Open Font License 1.1. Font: [`assets/pit12/fonts/noto-sans-sc.otf`](src/main/resources/assets/pit12/fonts/noto-sans-sc.otf) (modified and compressed GB 2312 subset). License text: [`META-INF/third-party-licenses/NOTO-SANS-SC-OFL.txt`](src/main/resources/META-INF/third-party-licenses/NOTO-SANS-SC-OFL.txt).
- [Montserrat](https://github.com/JulietaUla/Montserrat): SIL Open Font License 1.1. Font: [`assets/pit12/fonts/montserrat-regular.ttf`](src/main/resources/assets/pit12/fonts/montserrat-regular.ttf). License text: [`META-INF/third-party-licenses/MONTSERRAT-OFL.txt`](src/main/resources/META-INF/third-party-licenses/MONTSERRAT-OFL.txt).

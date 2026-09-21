# 12pit

> [!WARNING]
> 12pit is currently a work in progress and not yet playable.
> The first stable version is expected around October 2026.

12pit is a free and open-source mod for The Pit, aiming to stay lightweight with minimal performance impact.

## Requirements

Run Gradle with JDK 21. The project targets Java 8 via the Gradle toolchain, which will detect your local Java 8 installation or download it automatically if missing.

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
- [Font Awesome Free](https://fontawesome.com/) PNG icons: CC BY 4.0. Assets: [`assets/pit12/textures/gui/clickgui/`](src/main/resources/assets/pit12/textures/gui/clickgui/). License text: [`META-INF/third-party-licenses/FONT-AWESOME-FREE-LICENSE.txt`](src/main/resources/META-INF/third-party-licenses/FONT-AWESOME-FREE-LICENSE.txt).
- [Noto Sans SC](https://fonts.google.com/noto/specimen/Noto+Sans+SC): SIL Open Font License 1.1. Font: [`assets/pit12/fonts/noto-sans-sc.otf`](src/main/resources/assets/pit12/fonts/noto-sans-sc.otf) (modified and compressed GB 2312 subset). License text: [`META-INF/third-party-licenses/NOTO-SANS-SC-OFL.txt`](src/main/resources/META-INF/third-party-licenses/NOTO-SANS-SC-OFL.txt).
- [Montserrat](https://github.com/JulietaUla/Montserrat): SIL Open Font License 1.1. Font: [`assets/pit12/fonts/montserrat-regular.ttf`](src/main/resources/assets/pit12/fonts/montserrat-regular.ttf). License text: [`META-INF/third-party-licenses/MONTSERRAT-OFL.txt`](src/main/resources/META-INF/third-party-licenses/MONTSERRAT-OFL.txt).

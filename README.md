# 12pit

> [!WARNING]
> 12pit is currently a work in progress and not yet playable.
> The first stable version is expected around October 2026.

12pit is a free and open-source mod for The Pit, aiming to stay lightweight with minimal performance impact.

## Requirements

Run Gradle with JDK 21. The project targets Java 8 via the Gradle toolchain, which will detect your local Java 8 installation or download it automatically if missing.

## Developing with VS Code

Install the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) and [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) extensions.
Press `Ctrl+Shift+P` (`Cmd+Shift+P` on macOS), select `Tasks: Run Task`, and choose one of the pre-configured tasks:

- `assemble` builds the distributable JAR.
- `build` runs verification and builds the project.
- `check` runs verification without assembling the JAR.
- `spotlessApply` formats Java sources.
- `spotlessCheck` checks Java formatting.
- `runClient` starts the development client.

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

12pit bundles [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), which is licensed under the MIT License. The complete license text is included at [`META-INF/third-party-licenses/MIXIN-LICENSE.txt`](src/main/resources/META-INF/third-party-licenses/MIXIN-LICENSE.txt).

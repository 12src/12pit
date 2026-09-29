# 12pit

> [!WARNING]
> 12pit is under development and is not yet playable.

12pit is a free, open-source Forge mod for The Pit on Minecraft 1.8.9. It is designed to stay lightweight.

Join us on [Discord](https://discord.gg/e9PRKMUenc). Development happens in the `#developer` channel.

## Requirements

Gradle needs JDK 21. The project builds against Java 8 through Gradle's toolchain. Node.js and npm are only needed to build the bundled web interface.

## Building

On Windows, run the Gradle wrapper:

```powershell
.\gradlew.bat assemble
```

On Linux or macOS, run:

```sh
./gradlew assemble
```

The built jar goes in `build/libs/`.

## Development

### IntelliJ IDEA

Open the repository as a Gradle project and set Gradle's JVM to JDK 21. Use the Gradle tool window to run `assemble`, `check`, or `runClient`.

### VS Code

Install the recommended extensions when prompted. `Ctrl+Shift+B` runs `assemble`. Use the Command Palette to run `check` or `runClient` as Gradle tasks. On macOS, use `Cmd+Shift+B` for the build task.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](docs/CONTRIBUTING.md)
- [Issues](docs/ISSUES.md)
- [Running, testing, and debugging](docs/DEBUGGING.md)

## Third-Party Notices

12pit bundles the following third-party components.

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), MIT. [License text](src/main/resources/META-INF/third-party-licenses/MIXIN-MIT.txt).
- [Vue](https://vuejs.org/), MIT, and [Lucide](https://lucide.dev/), ISC. Their license texts are packaged with the web interface in `META-INF/third-party-licenses/`.
- [Montserrat](https://github.com/JulietaUla/Montserrat), SIL Open Font License 1.1. [Font](src/main/resources/assets/pit12/fonts/montserrat-regular.otf) and [license text](src/main/resources/META-INF/third-party-licenses/MONTSERRAT-OFL.txt).

12pit is licensed under [GPL-3.0-or-later](LICENSE).

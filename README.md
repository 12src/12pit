# 12pit

English | [简体中文](docs/zh-CN/README.md)

12pit is a lightweight, open-source Forge mod for The Pit on Minecraft 1.8.9, with its own control interface and [OneConfig](https://github.com/Polyfrost/OneConfig) support.

You can join our Discord [here](https://discord.gg/e9PRKMUenc) and seek help or discuss development related matters on the `#developer` channel.

## Requirements

Use JDK 21 to run Gradle. Compiling the mod requires a Java 8 toolchain. Gradle will find or download it automatically. The Minecraft client also runs on Java 8.

Node.js and npm are required for `assemble` and `runClient` because of the bundled web interface that those tasks build. Java tests require it as well. Node.js 24 is used in CI.

## Building

On Windows, run:

```powershell
.\gradlew.bat assemble
```

On Linux/macOS, run:

```sh
./gradlew assemble
```

Output jar file will be located at `build/libs/`.

## Development

### IntelliJ IDEA

Load the project via Gradle and specify JDK 21 as Gradle's JVM. Use Gradle window to run `assemble`, `check` or `runClient`.

### VS Code

Install the recommended extensions if prompted. `Ctrl+Shift+B` runs `assemble` task. Use Command Palette to run `check` or `runClient` as Gradle tasks. On macOS, `Cmd+Shift+B` should be used for build task.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Implementation](docs/IMPLEMENTATION.md)
- [Contributing](docs/CONTRIBUTING.md)
- [Translating](docs/TRANSLATING.md)
- [Issues](docs/ISSUES.md)
- [Running, testing, and debugging](docs/DEBUGGING.md)

## Third-Party Notices

12pit includes the following third-party components:

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), MIT. License text: [MIXIN-MIT.txt](src/main/resources/META-INF/third-party-licenses/MIXIN-MIT.txt).
- [Vue](https://vuejs.org/), MIT. License text: [VUE-MIT.txt](src/main/resources/META-INF/third-party-licenses/VUE-MIT.txt).
- [Lucide](https://lucide.dev/), ISC. License text: [LUCIDE-ISC.txt](src/main/resources/META-INF/third-party-licenses/LUCIDE-ISC.txt).
- [Monocraft](https://github.com/IdreesInc/Monocraft), SIL Open Font License 1.1. Font: [monocraft.otf](src/main/resources/assets/pit12/fonts/monocraft.otf). License text: [MONOCRAFT-OFL.txt](src/main/resources/META-INF/third-party-licenses/MONOCRAFT-OFL.txt).

12pit is licensed under [GPL-3.0-or-later](LICENSE).

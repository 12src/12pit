# 12pit

[![Release](https://img.shields.io/github/v/release/12src/12pit)](https://github.com/12src/12pit/releases/latest) [![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE) [![Build](https://github.com/12src/12pit/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/12src/12pit/actions/workflows/build.yml?query=branch%3Amain+event%3Apush) [![Crowdin](https://badges.crowdin.net/12pit/localized.svg)](https://crowdin.com/project/12pit)

English | [简体中文](docs/zh-CN/README.md)

12pit is a lightweight and open-source mod for The Pit on Minecraft 1.8.9, with builds for Forge and Ornithe. It features a configuration interface. The Forge build also supports [OneConfig](https://github.com/Polyfrost/OneConfig).

For Forge, we recommend running 12pit with [PolyPatcher](https://modrinth.com/mod/patcher) and [OptiFine](https://optifine.net/home).

For help and discussion regarding development, please join our [Discord](https://discord.gg/e9PRKMUenc) and visit `#developer`.

## Requirements

The build process requires JDK 25. The mod requires Java 8, which is what Minecraft 1.8.9 uses, and Gradle detects and installs it.

Also required are Node.js and npm because they are needed for `assemble` and `runClient` tasks which compile the configuration interface. CI is built with Node.js 24.

## Build Process

On Windows:

```powershell
.\gradlew.bat assemble
```

On Linux or macOS:

```sh
./gradlew assemble
```

The Forge artifact is `build/libs/12pit.jar`. The Ornithe artifact is `build/libs/12pit-ornithe.jar`. Both are built from the shared sources on the same branch. See [running and debugging](docs/DEBUGGING.md) for platform-specific commands.

## Development

In order to test new changes, run the game with PolyPatcher and OptiFine installed and make sure your changes work with their default settings.

### IntelliJ IDEA

Import the project as a Gradle project and set the Gradle JVM to JDK 25. Run `assemble`, `check`, and `runClient` tasks using the Gradle tool window.

### VS Code

Install the recommended extensions when prompted. Press `Ctrl+Shift+B` (`Cmd+Shift+B` on macOS) to run `assemble`, or use the Command Palette to run `check` and `runClient` as Gradle tasks.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](docs/CONTRIBUTING.md)
- [Translating](docs/TRANSLATING.md)
- [Issues](docs/ISSUES.md)
- [Running and debugging](docs/DEBUGGING.md)

## Third-Party Notices

12pit includes the following third-party components:

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin), MIT. License text: [MIXIN-MIT.txt](src/main/resources/META-INF/third-party-notices/MIXIN-MIT.txt).
- [ASM](https://asm.ow2.io/), bundled with Mixin, BSD 3-Clause. License text: [ASM-BSD-3-CLAUSE.txt](src/main/resources/META-INF/third-party-notices/ASM-BSD-3-CLAUSE.txt).
- [Vue](https://vuejs.org/), MIT. License text: [VUE-MIT.txt](src/main/resources/META-INF/third-party-notices/VUE-MIT.txt).
- [Vite](https://vite.dev/), used for module preloading in the configuration interface, MIT. License text: [VITE-MIT.txt](src/main/resources/META-INF/third-party-notices/VITE-MIT.txt).
- [Rolldown](https://rolldown.rs/), used for module preloading in the configuration interface, MIT. License text: [ROLLDOWN-MIT.txt](src/main/resources/META-INF/third-party-notices/ROLLDOWN-MIT.txt).
- [Lucide](https://lucide.dev/), ISC, with Feather icons under MIT. License text: [LUCIDE-ISC.txt](src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt).

12pit is licensed under [GPL-3.0-or-later](LICENSE).

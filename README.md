# 12pit

[![Release](https://img.shields.io/github/v/release/12src/12pit)](https://github.com/12src/12pit/releases/latest) [![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE) [![Build](https://github.com/12src/12pit/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/12src/12pit/actions/workflows/build.yml?query=branch%3Amain+event%3Apush) [![Crowdin](https://badges.crowdin.net/12pit/localized.svg)](https://crowdin.com/project/12pit)

English | [简体中文](docs/zh-CN/README.md)

12pit is a free and open-source mod for The Pit. It has its own configuration interface and also supports [OneConfig](https://github.com/Polyfrost/OneConfig). It is also compatible with Lunar Client.

12pit is recommended to be run with [PolyPatcher](https://modrinth.com/mod/patcher) and [OptiFine](https://optifine.net/home) for better performance.

For help and discussion regarding development, please join our [Discord](https://discord.gg/e9PRKMUenc) and visit `#developer`.

## Requirements

The build process requires at least JDK 21. The mod requires Java 8, which is what Minecraft 1.8.9 uses, and Gradle detects and installs it.

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

The jar produced is put into `build/libs/`.

## Development

In order to test new changes, run the game with PolyPatcher and OptiFine installed and make sure your changes work with their default settings.

### IntelliJ IDEA

Import the project as a Gradle project and set the Gradle JVM to JDK 21 or later. Run `assemble`, `check`, and `runClient` tasks using the Gradle tool window.

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

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) is licensed under MIT. License text: [MIXIN-MIT.txt](src/main/resources/META-INF/third-party-notices/MIXIN-MIT.txt).
- [ASM](https://asm.ow2.io/) is licensed under BSD 3-Clause. License text: [ASM-BSD-3-CLAUSE.txt](src/main/resources/META-INF/third-party-notices/ASM-BSD-3-CLAUSE.txt). It is bundled with Mixin.
- [Vue](https://vuejs.org/) is licensed under MIT. License text: [VUE-MIT.txt](src/main/resources/META-INF/third-party-notices/VUE-MIT.txt).
- [Vite](https://vite.dev/) is licensed under MIT. License text: [VITE-MIT.txt](src/main/resources/META-INF/third-party-notices/VITE-MIT.txt). It provides module preload support for the configuration interface.
- [Rolldown](https://rolldown.rs/) is licensed under MIT. License text: [ROLLDOWN-MIT.txt](src/main/resources/META-INF/third-party-notices/ROLLDOWN-MIT.txt). Third-party notices: [ROLLDOWN-THIRD-PARTY-LICENSE.txt](src/main/resources/META-INF/third-party-notices/ROLLDOWN-THIRD-PARTY-LICENSE.txt). It provides module preload support for the configuration interface.
- [Lucide](https://lucide.dev/) is licensed under ISC. License text: [LUCIDE-ISC.txt](src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt).
- [Feather](https://github.com/feathericons/feather) is licensed under MIT. License text: [LUCIDE-ISC.txt](src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt). Its icons are included through Lucide.

12pit is licensed under [GPL-3.0-or-later](LICENSE).

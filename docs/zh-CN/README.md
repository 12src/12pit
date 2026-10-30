<!-- Source: README.md; Based on: 9457cda8e22008b21d9b83abbbde8cbbc0f5638a -->

# 12pit

[![Release](https://img.shields.io/github/v/release/12src/12pit)](https://github.com/12src/12pit/releases/latest) [![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](../../LICENSE) [![Build](https://github.com/12src/12pit/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/12src/12pit/actions/workflows/build.yml?query=branch%3Amain+event%3Apush) [![Crowdin](https://badges.crowdin.net/12pit/localized.svg)](https://crowdin.com/project/12pit)

[English](../../README.md) | 简体中文

12pit 是适用于 The Pit 的免费开源模组，自带配置界面，同时支持 [OneConfig](https://github.com/Polyfrost/OneConfig)，也兼容 Lunar Client。

建议配合 [PolyPatcher](https://modrinth.com/mod/patcher) 和 [OptiFine](https://optifine.net/home) 一起使用，以获得更好的性能。

如果遇到问题或想交流开发，欢迎加入我们的 [Discord](https://discord.gg/e9PRKMUenc)，在 `#developer` 频道讨论。

## 环境要求

运行 Gradle 至少需要 JDK 21。模组本身与 Minecraft 1.8.9 一样运行在 Java 8 上，编译时 Gradle 会自动查找或下载 Java 8 工具链。

此外，`assemble` 和 `runClient` 任务会构建网页配置界面，因此还需要安装 Node.js 和 npm（CI 使用 Node.js 24）。

## 构建

Windows：

```powershell
.\gradlew.bat assemble
```

Linux 或 macOS：

```sh
./gradlew assemble
```

构建好的 jar 文件会输出到 `build/libs/`。

## 开发

测试改动时，请同时安装 PolyPatcher 和 OptiFine 并运行游戏，确认改动在两者的默认设置下正常工作。

### IntelliJ IDEA

通过 Gradle 导入项目，并将 Gradle JVM 设为 JDK 21 或更高版本。之后可以直接在 Gradle 工具窗口中运行 `assemble`、`check` 和 `runClient`。

### VS Code

按提示安装推荐扩展后，按 `Ctrl+Shift+B`（macOS 为 `Cmd+Shift+B`）即可运行 `assemble`，也可以在命令面板中运行 `check` 和 `runClient` 任务。

## 文档

- [架构](ARCHITECTURE.md)
- [贡献指南](CONTRIBUTING.md)
- [翻译指南](TRANSLATING.md)
- [议题](ISSUES.md)
- [运行与调试](DEBUGGING.md)

## 第三方声明

12pit 包含以下第三方开源组件：

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 使用 MIT 许可证。许可证文本：[MIXIN-MIT.txt](../../src/main/resources/META-INF/third-party-notices/MIXIN-MIT.txt)。
- [ASM](https://asm.ow2.io/) 使用 BSD 3-Clause 许可证。许可证文本：[ASM-BSD-3-CLAUSE.txt](../../src/main/resources/META-INF/third-party-notices/ASM-BSD-3-CLAUSE.txt)。随 Mixin 打包。
- [Vue](https://vuejs.org/) 使用 MIT 许可证。许可证文本：[VUE-MIT.txt](../../src/main/resources/META-INF/third-party-notices/VUE-MIT.txt)。
- [Vite](https://vite.dev/) 使用 MIT 许可证。许可证文本：[VITE-MIT.txt](../../src/main/resources/META-INF/third-party-notices/VITE-MIT.txt)。用于配置界面的模块预加载。
- [Rolldown](https://rolldown.rs/) 使用 MIT 许可证。许可证文本：[ROLLDOWN-MIT.txt](../../src/main/resources/META-INF/third-party-notices/ROLLDOWN-MIT.txt)。第三方声明：[ROLLDOWN-THIRD-PARTY-LICENSE.txt](../../src/main/resources/META-INF/third-party-notices/ROLLDOWN-THIRD-PARTY-LICENSE.txt)。用于配置界面的模块预加载。
- [Lucide](https://lucide.dev/) 使用 ISC 许可证。许可证文本：[LUCIDE-ISC.txt](../../src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt)。
- [Feather](https://github.com/feathericons/feather) 使用 MIT 许可证。许可证文本：[LUCIDE-ISC.txt](../../src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt)。图标通过 Lucide 包含在模组中。

12pit 基于 [GPL-3.0-or-later](../../LICENSE) 协议开源。

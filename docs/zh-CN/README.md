<!-- Source: README.md; Based on: fddb5b4f0f5e68619c5b5b26101166901b09b72d -->

# 12pit

[English](../../README.md) | 简体中文

12pit 是一款基于 Forge、专为 Minecraft 1.8.9 The Pit 打造的轻量级开源模组，自带控制界面，并兼容 [OneConfig](https://github.com/Polyfrost/OneConfig)。

你可以点[这里](https://discord.gg/e9PRKMUenc)加入我们的 Discord，并在 `#developer` 频道求助或讨论开发相关话题。

## 环境要求

请使用 JDK 21 运行 Gradle。编译模组需要 Java 8 工具链，Gradle 会自动查找或下载。Minecraft 客户端也使用 Java 8。

此外，由于需要构建内置的网页界面，执行 `assemble`、`runClient` 以及相关 Java 测试时均依赖 Node.js 和 npm（CI 环境使用的是 Node.js 24）。

## 构建

在 Windows 上运行：

```powershell
.\gradlew.bat assemble
```

在 Linux/macOS 上运行：

```sh
./gradlew assemble
```

输出的 jar 文件位于 `build/libs/`。

## 开发

### IntelliJ IDEA

以 Gradle 项目形式导入本项目，并将 Gradle JVM 指定为 JDK 21。随后即可在 Gradle 工具窗口中直接运行 `assemble`、`check` 或 `runClient`。

### VS Code

打开项目时若弹出提示，请安装推荐的扩展。可直接按 `Ctrl+Shift+B`（macOS 为 `Cmd+Shift+B`）执行 `assemble` 构建任务；如需运行 `check` 或 `runClient`，可通过命令面板调用对应的 Gradle 任务。

## 文档

- [架构](ARCHITECTURE.md)
- [实现](IMPLEMENTATION.md)
- [贡献指南](CONTRIBUTING.md)
- [翻译指南](TRANSLATING.md)
- [议题](ISSUES.md)
- [运行、测试和调试](DEBUGGING.md)

## 第三方声明

12pit 使用了以下第三方开源组件:

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin)，MIT。许可证文本：[MIXIN-MIT.txt](../../src/main/resources/META-INF/third-party-notices/MIXIN-MIT.txt)。
- [ASM](https://asm.ow2.io/)，随 Mixin 打包，BSD 3-Clause。许可证文本：[ASM-BSD-3-CLAUSE.txt](../../src/main/resources/META-INF/third-party-notices/ASM-BSD-3-CLAUSE.txt)。
- [Vue](https://vuejs.org/)，MIT。许可证文本：[VUE-MIT.txt](../../src/main/resources/META-INF/third-party-notices/VUE-MIT.txt)。
- [Vite](https://vite.dev/)，用于 Web UI 模块预加载，MIT。许可证文本：[VITE-MIT.txt](../../src/main/resources/META-INF/third-party-notices/VITE-MIT.txt)。
- [Rolldown](https://rolldown.rs/)，用于 Web UI 模块预加载，MIT。许可证文本：[ROLLDOWN-MIT.txt](../../src/main/resources/META-INF/third-party-notices/ROLLDOWN-MIT.txt)。
- [Lucide](https://lucide.dev/)，ISC，包含采用 MIT 许可证的 Feather 图标。许可证文本：[LUCIDE-ISC.txt](../../src/main/resources/META-INF/third-party-notices/LUCIDE-ISC.txt)。
- [Monocraft](https://github.com/IdreesInc/Monocraft)，SIL Open Font License 1.1。字体：[monocraft.otf](../../src/main/resources/assets/pit12/fonts/monocraft.otf)。许可证文本：[MONOCRAFT-OFL.txt](../../src/main/resources/META-INF/third-party-notices/MONOCRAFT-OFL.txt)。

12pit 基于 [GPL-3.0-or-later](../../LICENSE) 协议开源。

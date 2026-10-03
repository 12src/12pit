<!-- Source: README.md; Based on: f293ac9e7ef68fc0145c6c72f93b5c3f30dbf7c5 -->

# 12pit

[English](../../README.md)

> [!WARNING]
> 12pit 仍处于初始开发阶段。

12pit 是一款基于 Forge、专为 Minecraft 1.8.9 The Pit 打造的轻量级开源模组。

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

- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin)，MIT。许可证文本：[MIXIN-MIT.txt](../../src/main/resources/META-INF/third-party-licenses/MIXIN-MIT.txt)。
- [Vue](https://vuejs.org/)，MIT。许可证文本：[VUE-MIT.txt](../../src/main/resources/META-INF/third-party-licenses/VUE-MIT.txt)。
- [Lucide](https://lucide.dev/)，ISC。许可证文本：[LUCIDE-ISC.txt](../../src/main/resources/META-INF/third-party-licenses/LUCIDE-ISC.txt)。
- [Montserrat](https://github.com/JulietaUla/Montserrat)，SIL Open Font License 1.1。字体：[montserrat-regular.otf](../../src/main/resources/assets/pit12/fonts/montserrat-regular.otf)。许可证文本：[MONTSERRAT-OFL.txt](../../src/main/resources/META-INF/third-party-licenses/MONTSERRAT-OFL.txt)。

12pit 基于 [GPL-3.0-or-later](../../LICENSE) 协议开源。

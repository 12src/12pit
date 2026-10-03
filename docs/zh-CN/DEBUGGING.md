<!-- Source: docs/DEBUGGING.md; Based on: f293ac9e7ef68fc0145c6c72f93b5c3f30dbf7c5 -->

# 运行和调试 12pit

## 环境设置

[README 环境要求](README.md#环境要求)列出了 JDK 和 Node.js 的版本。Gradle 使用 JDK 21。编译模组需要 Java 8 工具链，客户端也使用 Java 8。

在 IntelliJ IDEA 中，将仓库作为 Gradle 项目打开，并为 Gradle 配置 JDK 21。在 VS Code 中，安装推荐的扩展。可以使用 `Ctrl+Shift+B` 运行 `assemble`。

## 运行开发客户端

在 Windows 上运行：

```powershell
.\gradlew.bat runClient
```

在 Linux 或 macOS 上运行：

```sh
./gradlew runClient
```

`runClient` 会构建内置的网页界面，因此应确保 Node.js 和 npm 可用。

开发游戏目录为 `run/`。查看 `run/logs/latest.log`，获取客户端错误和 Web UI 地址。

## 开发网页界面

先启动游戏。然后按右 Shift 键（默认按键），在浏览器中打开内置的网页界面。如果该按键没有打开浏览器，请打开日志中的 URL。

默认情况下，服务器监听 `http://127.0.0.1:60916/`。如果端口已被占用，服务器会使用其他可用端口。当前地址会显示在日志中。

要实时查看前端改动，在单独的终端窗口中启动 Vite：

```powershell
npm ci --prefix web-ui
npm run dev --prefix web-ui
```

打开 Vite 输出的 URL。默认情况下，`/api` 代理监听端口 `60916`。如果游戏运行在其他端口，在启动 Vite 前设置 `target` 变量。

```powershell
$env:WEB_UI_TARGET = 'http://127.0.0.1:<game-port>'
npm run dev --prefix web-ui
```

将 `<game-port>` 替换为日志中的端口。前端没有示例数据模式。通过重新构建并重启模组来更新内置页面。

## 运行检查

在 Windows 上，从仓库根目录运行以下命令。在 Linux 或 macOS 上，使用 `./gradlew` 替代 `./gradlew.bat`。

```powershell
.\gradlew.bat check
.\gradlew.bat assemble
.\gradlew.bat spotlessCheck
npm run format:check --prefix web-ui
```

`check` 命令运行 Java 测试，包括 `ArchitectureTest`。`assemble` 命令构建网页界面，并在 `build/libs/` 中生成 jar 文件。

格式检查命令只检查文件，不修改文件。在运行 npm 检查前，使用 `npm ci --prefix web-ui` 安装所需依赖。

## 故障排查

如果发生 Gradle 同步或工具链错误，检查 Gradle 是否使用 JDK 21，并能找到或下载 Java 8 工具链。

如果 Vite 页面已打开，但请求失败，确保游戏已启动，且 `WEB_UI_TARGET` 与游戏当前地址一致。如果 Web UI 无法启动，在游戏日志中查找 "Web UI is unavailable" 消息。

报告故障时，提供相关日志消息和复现步骤。

保存的数据位于 Minecraft 游戏目录中的 `12pit/` 目录。配置方案存储在 `12pit/config/`，关系存储在 `12pit/relations.json`，切换绑定存储在 `12pit/swap-bindings.json`。

为诊断加载错误而修改这些文件前，先创建备份。不要为了重置临时会话问题而删除已保存的数据。

开发客户端基于 [DevAuth](https://github.com/DJtheRedstoner/DevAuth)。要使用正版账号测试，启用 `devauth.enabled` JVM 属性，并遵循 [DevAuth 配置指南](https://github.com/DJtheRedstoner/DevAuth#configuration)。

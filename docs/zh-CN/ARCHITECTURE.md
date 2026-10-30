<!-- Source: docs/ARCHITECTURE.md; Based on: 36ca2550d013e1d87fb0a788636e9e5b151b126d -->

# 12pit 架构

## 组件

`Pit12` 是 Forge 入口类，负责将 Forge 生命周期事件转发给 `ClientBootstrap`。`ClientBootstrap` 创建顶层组件、组装依赖，并管理应用的启动和关闭。

功能按 `feature.client`、`feature.combat`、`feature.render`、`feature.misc` 和 `feature.patch` 分组。每个功能包对应一项具体功能，管理自己的配置、逻辑、命令、渲染、持久化数据，以及该功能专用的 Forge 监听器。

`runtime` 存放有独立生命周期或多个功能共享的实时状态与服务，例如客户端会话、游戏状态跟踪器、命令注册表、配置目录和 HUD 注册表。

`shared` 提供线程、监听器、渲染和文件写入等通用辅助接口与工具类，不保存实时游戏状态。

`platform` 负责对接外部框架、客户端扩展和原生 API，处理注册、协议细节和兼容钩子，并通过 `feature`、`runtime` 或 `shared` 定义的接口把外部调用转交给对应模块。适配器按所对接的外部系统分包。

命令由各功能自己定义和实现，`runtime.command` 负责路由、帮助信息、补全和可用性判断，`platform.forge` 负责向 Forge 注册。

`feature.client.webui` 管理本地 HTTP 服务器，负责把浏览器里的配置界面（`web-ui`）与配置及功能 API 连接起来。

## 依赖

`Pit12` 只依赖 `bootstrap` 和 `shared.build.BuildConfig` 中的编译期常量。`bootstrap` 可以依赖 `platform`、`runtime`、`feature` 和 `shared`，并直接把依赖传给需要的组件。

`feature` 可以依赖 `runtime` 和 `shared`。不同功能之间交互时，只能通过对方公开的 `api` 包访问。`runtime` 只依赖 `shared`，而 `shared` 不依赖项目中的任何其他层。

`platform` 可以依赖 `runtime`、`feature` 和 `shared`，但反过来这些层不依赖 `platform` 的类，而是通过自己定义的接口接收平台服务或绑定钩子。原生 API 和可选的外部框架类型都留在 `platform` 内部，`runtime.command` 也不依赖 Forge。

`runtime`、`shared` 和 `platform` 下的直接子包各自构成模块。`feature.<category>.<feature>` 下的每个包构成一个功能模块。同层模块之间避免循环依赖。所有 Mixin 都放在 `platform.mixin` 下，其中功能专用的 Mixin 放在 `platform.mixin.feature.<category>.<feature>`，同样遵守功能边界约束。

## 状态和资源

每份可变状态都有唯一的所有者，每个组件各自负责清理自己创建的工作线程、订阅和渲染资源。

各功能自行定义配置项。`ConfigCatalog` 保存已注册的实时配置，配置方案（`profile`）功能负责配置快照及其磁盘存储，其余需要持久化的数据由对应的功能自己管理。

## 架构检查

[ArchitectureTest](../../src/test/java/pit12/architecture/ArchitectureTest.java) 负责检查包边界、依赖方向、模块循环依赖、生命周期归属和 Mixin 注册；[BootstrapLayoutTest](../../src/test/java/pit12/architecture/BootstrapLayoutTest.java) 负责检查 `ClientBootstrap` 中的分组注释和组件注册顺序。

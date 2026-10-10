<!-- Source: docs/ARCHITECTURE.md; Based on: d04a7ca1216285f12f66c013303f500f98080369 -->

# 12pit 架构

[实现](IMPLEMENTATION.md)介绍开发功能时需要的共用接口和调用约定。

## 组件

`Pit12` 是 Forge 入口。它将 Forge 生命周期事件转发给 `ClientBootstrap`。Bootstrap 构建顶层组件，连接它们的依赖，并负责应用的启动和关闭。

`feature` 的每个直接子包都应拥有一项面向用户的功能。该功能可以拥有自己的设置、规则、命令、渲染和存储等。只面向指定功能的 Forge 监听器也可以由该功能拥有。

`runtime` 拥有具备独立生命周期或由多个功能使用的实时数据和服务。它们包括客户端会话、游戏状态跟踪器、命令注册表、配置目录和 HUD 注册表。

`shared` 拥有用于线程、监听器、渲染和文件写入的小型辅助接口和工具。它不拥有实时游戏状态。

`platform` 适配 Forge 命令和 Mixin 等外部机制。适配器通过范围有限的接口，将外部请求转发给其所有者。适配器可以实现 `ClientLifecycle`，管理自己持有的监听和注册资源。

命令定义和操作由功能拥有。`runtime.command` 拥有命令的路由、帮助、补全和可用性。`platform.command` 拥有 Forge 注册。

`feature.webui` 拥有本地 HTTP 服务器，并将浏览器界面连接到配置和功能 API。`web-ui` 拥有浏览器页面。

## 依赖

`Pit12` 依赖 Bootstrap，并读取 `shared.build.BuildConfig` 中的编译期构建常量。Bootstrap 可以依赖 `platform`、`runtime`、`feature` 和 `shared`。它连接组件之间的依赖；不存在全局服务查找。

功能可以依赖 `runtime` 和 `shared`。一个功能只能通过另一个功能的公开 `api` 包使用该功能。`runtime` 可以依赖 `shared`。`shared` 不依赖项目的其他层。

`platform` 可以依赖 `runtime`、`feature` 和 `shared`。功能和 runtime 代码使用绑定接口，而不依赖 platform 类。命令 runtime 代码不依赖 Forge。

项目层的直接子包构成模块。模块之间的依赖不存在循环。功能专用的 Mixin 属于对应功能的边界，不依赖其他功能的内部实现。

## 状态和资源

`ClientSession` 拥有连接和世界的身份标识。跟踪器拥有与这些身份标识相关的状态。断开连接会结束会话，替换世界会结束旧世界的状态。这两个事件都不影响模组的生命周期。

Minecraft 拥有当前的 Tab 条目、已加载实体、记分板和物品栏状态。runtime 跟踪器拥有 Minecraft 不保留的数据，以及功能之间共享的派生数据。每份可变数据都有一个所有者。显示快照是这些数据的视图。

组件拥有自己分配的资源，例如工作线程、订阅和渲染资源。临时操作负责自己的进度、取消和恢复。功能的启动状态和启用标志是两个独立的状态。

各功能定义自己的设置。`ConfigCatalog` 拥有已注册的实时配置，配置方案功能拥有配置快照及其存储。其他保存的数据由使用它的功能拥有。用户数据在断开连接后仍然保留。

## 架构检查

[ArchitectureTest](../../src/test/java/pit12/architecture/ArchitectureTest.java) 检查包的归属、依赖方向、模块循环依赖、生命周期归属和 Mixin 注册。

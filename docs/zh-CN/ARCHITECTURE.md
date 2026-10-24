<!-- Source: docs/ARCHITECTURE.md; Based on: f59d0c6d77b5f37fe9637bea07f2965f1fbdd33c -->

# 12pit 架构

## 组件

`Pit12` 是 Forge 入口，将 Forge 生命周期事件转发给 `ClientBootstrap`。Bootstrap 创建顶层组件，连接它们的依赖，并负责应用的启动和关闭。

`feature` 的每个直接子包负责一项面向用户的功能，管理该功能的设置、规则、命令、渲染和存储。功能专用的 Forge 监听器也可以放在其中。

`runtime` 管理有独立生命周期，或由多个功能共用的实时数据和服务，包括客户端会话、游戏状态跟踪器、命令注册表、配置目录和 HUD 注册表。

`shared` 提供线程、监听器、渲染和文件写入所需的小型辅助接口和工具。实时游戏状态由 runtime 和功能组件管理。

`platform` 适配外部框架、客户端扩展和原生服务，负责注册、协议细节和兼容处理。适配器按目标系统分包，通过功能、runtime 或 shared 定义的接口转发外部请求，也可以实现 `ClientLifecycle`，管理自己的订阅和注册。

功能负责定义命令和操作。`runtime.command` 负责命令路由、帮助、补全和可用性。`platform.forge` 负责向 Forge 注册命令。

`feature.webui` 管理本地 HTTP 服务器，将配置界面连接到配置和功能 API。`web-ui` 存放浏览器页面。

## 依赖

`Pit12` 依赖 Bootstrap，并读取 `shared.build.BuildConfig` 中的编译期构建常量。Bootstrap 可以依赖 `platform`、`runtime`、`feature` 和 `shared`，直接向组件传入所需依赖。

`runtime` 和 `shared` 可供功能使用。功能之间通过对方公开的 `api` 包交互。`runtime` 可以依赖 `shared`。`shared` 独立于项目的其他层。

`platform` 可以依赖 `runtime`、`feature` 和 `shared`。功能、runtime 和 shared 通过各自的接口使用平台服务并绑定钩子，与 platform 类解耦。原生 API 和可选框架类型的使用集中在平台适配器中。命令 runtime 代码与 Forge 解耦。

项目层的直接子包构成模块，模块依赖应保持无环。功能专用的 Mixin 归属对应功能，通过公开 API 访问其他功能。

## 状态和资源

`ClientSession` 管理连接和世界的身份标识。跟踪器管理与这些标识相关的状态。断开连接时结束会话，更换世界时结束旧世界的状态。模组在这两种情况下都继续运行。

Minecraft 管理当前的 Tab 条目、已加载实体、记分板和物品栏状态。runtime 跟踪器管理额外记录的数据，以及多个功能共享的派生数据。每份可变数据都有一个所有者，显示快照提供这些数据的视图。

组件负责自己创建的资源，例如工作线程、订阅和渲染资源。临时操作管理自己的进度、取消和恢复。功能分别记录启动状态和启用标志。

各功能定义自己的设置。`ConfigCatalog` 管理已注册的实时配置，配置方案功能管理配置快照及其存储。其他保存数据由使用它的功能管理。断开连接后仍会保留用户数据。

## 架构检查

[ArchitectureTest](../../src/test/java/pit12/architecture/ArchitectureTest.java) 检查包的归属、依赖方向、模块循环依赖、生命周期归属和 Mixin 注册。

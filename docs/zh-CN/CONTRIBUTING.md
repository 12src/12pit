<!-- Source: docs/CONTRIBUTING.md; Based on: d04a7ca1216285f12f66c013303f500f98080369 -->

# 为 12pit 贡献

翻译贡献请参阅[翻译指南](https://github.com/12src/12pit/blob/main/docs/zh-CN/TRANSLATING.md)。

## 开始贡献

欢迎贡献缺陷修复、文档、测试、维护和新功能。

寻找带有 <https://github.com/12src/12pit/labels/confirmed> 和 <https://github.com/12src/12pit/labels/help%20wanted> 标签的未关闭议题。刚接触本项目的贡献者可以从带有 <https://github.com/12src/12pit/labels/confirmed> 和 <https://github.com/12src/12pit/labels/good%20first%20issue> 标签的议题开始。先查看负责人、评论和引用的拉取请求。

如果贡献是较大的功能，或是需要作出设计决定的改动，请在实现前创建议题。[议题指南](https://github.com/12src/12pit/blob/main/docs/zh-CN/ISSUES.md)概述了你需要包含的所有内容。如果改动范围不明确，请在议题中或 [Discord](https://discord.gg/e9PRKMUenc) 的 `#developer` 频道请求澄清。

## 认领议题

开始处理议题时，请留下评论。如果你的贡献可能与其他贡献者的工作重叠，请提交草稿拉取请求，让他人知道你正在处理它。

## 开始工作

每个提交应只代表一项改动。推荐使用符合[约定式提交格式](https://www.conventionalcommits.org/)的提交信息：`fix: correct profile loading`、`docs: clarify build steps` 等。拉取请求通常应只有一个目标。如果少量改动便于一起审查，也可以包含它们。

[架构指南](https://github.com/12src/12pit/blob/main/docs/zh-CN/ARCHITECTURE.md)概述了包的职责和依赖。[实现指南](https://github.com/12src/12pit/blob/main/docs/zh-CN/IMPLEMENTATION.md)介绍开发功能时需要的共用接口和调用约定。[调试指南](https://github.com/12src/12pit/blob/main/docs/zh-CN/DEBUGGING.md)概述了开发客户端和检查。

阅读相关代码，并在适当时使用现有辅助工具。将所有新行为保留在其功能内。只有确实必要时才提取共享代码。不要在拉取请求中加入无关的代码清理。

[BootstrapLayoutTest](../../src/test/java/pit12/architecture/BootstrapLayoutTest.java) 会创建配置对象，检查它们的分类。不需要注入依赖的配置使用 public 无参构造器。新配置需要注入依赖时，更新该测试中对应的构造方式。

项目中的所有说明文字都使用短句。使用常用词，并在有助于清晰、自然、简洁地解释内容时使用项目术语。

## 编码规范

实现功能当前需要的代码。只有存在第二个实现或测试等实际需求时，才添加接口或包装器。版权声明由 Spotless 添加。

如果提取方法、常量或变量能提高代码可读性，或它在多处使用，就提取它。如果它只在一处使用，且内联写法清楚，就保留在原处。不要保留两个做相同事情的方法，也不要保留只是调用另一个方法的方法。

在用户输入、文件、网络数据和可能缺失的 Minecraft 值进入项目时检查它们。内部调用信任调用者，避免重复检查。内部不变量被破坏时，按缺陷就地处理，不用回退逻辑掩盖。`stop()` 可能在初始化失败后调用，必须能处理尚未创建的资源。

只在其他使用者可能修改集合或集合跨线程传递时复制。

只有在能够对异常采取行动、处理它或向调用者提供更多信息时，才捕获异常。应删除仅重新抛出异常或忽略异常的 `catch` 块。

避免明显的浪费，例如每帧解析或扫描整个世界。不要为了未经测量的收益添加缓存或特殊情况处理。如果收益很小而代码复杂，请采用更简单的方法。

清理你改动中未使用的代码，包括方法、字段、参数、导入和类。删除公开成员前，先在整个仓库中搜索它，包括测试、文档和 Mixin 配置。

使用语言提供的简单形式。依赖自动装箱，而不自行执行装箱操作，并优先使用导入，而非全限定名。

## 注释

站在初次阅读者的角度看代码。如果用途、行为或契约看不出或容易误解，就写简短注释。如果代码已经表达清楚，就不需要注释。随代码一起更新注释。

## 提交合并请求

使用 `./gradlew spotlessApply` 和 `npm run format --prefix web-ui` 格式化代码，运行与你的改动相关的检查，并在提交拉取请求前构建一次。说明你运行了什么，以及任何已知限制。如果行为发生变化，请提交有针对性的测试。

如果包的职责或依赖发生变化，更新架构指南和相关测试。如果基本接口、调用约定或注册步骤发生变化，更新实现指南。

说明改动及其原因，并报告失败的检查。工作完成后，将草稿拉取请求标记为可供审查。

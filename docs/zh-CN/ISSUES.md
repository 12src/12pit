<!-- Source: docs/ISSUES.md; Based on: 9e3455a4346e4997be2d01c6d78e031334c73fd5 -->

# 议题

提交前先查看现有议题。如果已有相同问题，请在该议题中补充有用的信息。

## 创建议题

如果是缺陷，请说明预期行为、实际结果和复现方法。提供模组版本、Minecraft 环境，并尽可能提供一些日志。注意请先隐去日志的敏感信息。

功能建议或改进请说明问题和预期效果，可附简短示例。也欢迎提出问题或初步想法。

## 初步评估

调查议题时，维护者可能添加以下标签：https://github.com/12src/12pit/labels/needs%20triage、https://github.com/12src/12pit/labels/needs%20info 或 https://github.com/12src/12pit/labels/needs%20reproduction。

在开展工作前需要做出产品或技术决策时，使用 https://github.com/12src/12pit/labels/needs%20decision 标签；如果议题是一场讨论，则使用 https://github.com/12src/12pit/labels/discussion 标签。如果议题是一个提问，应使用 https://github.com/12src/12pit/labels/question 标签。https://github.com/12src/12pit/labels/invalid 标签用于基于错误假设或与项目无关的议题。

议题一经确认（可复现的问题或已接受的请求），就为议题添加 https://github.com/12src/12pit/labels/confirmed 标签，然后按类型、领域、优先级和环境分类。

多数议题会伴随一个或多个已合并的 Pull Request 关闭（completed）。部分议题在得到解答或讨论结束后关闭。其他议题在关闭时通常会带有以下标签：https://github.com/12src/12pit/labels/duplicate、https://github.com/12src/12pit/labels/cannot%20reproduce、https://github.com/12src/12pit/labels/upstream 或 https://github.com/12src/12pit/labels/wontfix。

## 标签

新功能与优化建议使用 https://github.com/12src/12pit/labels/feature 或 https://github.com/12src/12pit/labels/enhancement 标签。

程序缺陷及各类故障使用 https://github.com/12src/12pit/labels/bug、https://github.com/12src/12pit/labels/crash、https://github.com/12src/12pit/labels/regression、https://github.com/12src/12pit/labels/compatibility 或 https://github.com/12src/12pit/labels/performance 标签。

若涉及破坏性变更，可附带 https://github.com/12src/12pit/labels/breaking%20change 标签。其他日常维护任务使用 https://github.com/12src/12pit/labels/documentation、https://github.com/12src/12pit/labels/refactor、https://github.com/12src/12pit/labels/chore 或 https://github.com/12src/12pit/labels/clean 标签。

领域标签用于标明议题涉及的项目模块或范畴：https://github.com/12src/12pit/labels/domain%3A%20CI、https://github.com/12src/12pit/labels/domain%3A%20build、https://github.com/12src/12pit/labels/domain%3A%20dependencies、https://github.com/12src/12pit/labels/domain%3A%20release、https://github.com/12src/12pit/labels/domain%3A%20testing、https://github.com/12src/12pit/labels/domain%3A%20architecture、https://github.com/12src/12pit/labels/domain%3A%20tooling、https://github.com/12src/12pit/labels/domain%3A%20localization、https://github.com/12src/12pit/labels/domain%3A%20licensing、https://github.com/12src/12pit/labels/domain%3A%20governance 和 https://github.com/12src/12pit/labels/domain%3A%20UI%2FUX。

优先级标签用于标明处理任务的紧迫程度：https://github.com/12src/12pit/labels/priority%3A%20P0、https://github.com/12src/12pit/labels/priority%3A%20P1、https://github.com/12src/12pit/labels/priority%3A%20P2 和 https://github.com/12src/12pit/labels/priority%3A%20P3。

环境标签用于标明议题发生或适用的运行环境：https://github.com/12src/12pit/labels/env%3A%20Windows、https://github.com/12src/12pit/labels/env%3A%20Linux、https://github.com/12src/12pit/labels/env%3A%20macOS、https://github.com/12src/12pit/labels/env%3A%20mod%20conflict 和 https://github.com/12src/12pit/labels/env%3A%20server-specific。

此外，议题还可附带阻塞相关的 https://github.com/12src/12pit/labels/blocked 或 https://github.com/12src/12pit/labels/release%20blocker 标签。

## 选择议题

https://github.com/12src/12pit/labels/help%20wanted 标签表示欢迎社区贡献。你必须寻找同时带有 https://github.com/12src/12pit/labels/confirmed 标签的未关闭议题才能开始工作。如果想从小型贡献入手，请寻找带有 https://github.com/12src/12pit/labels/good%20first%20issue 标签的议题。确保尚无人认领，并遵循[贡献指南](CONTRIBUTING.md)。

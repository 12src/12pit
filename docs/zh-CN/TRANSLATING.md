<!-- Source: docs/TRANSLATING.md; Based on: 08d27d075f5f28e811ea97e76a4ec893603d64f5 -->

# 贡献翻译

## 通用规则

添加新语言时：

- 如果语言有 [ISO 639-1 两字母代码](https://www.loc.gov/standards/iso639-2/php/code_list.php)，可以直接添加，无需先创建议题。
- 如果语言没有 ISO 639-1 代码，添加前须取得维护者同意。语言标签仍须符合 [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html)。可以在议题中询问，或在 [Discord](https://discord.gg/e9PRKMUenc) 中 @维护者。
- 建议只翻译成自己的母语。

例如，`nl-NL` 使用 ISO 639-1 代码 `nl`。

## 贡献界面翻译

Web UI、配置文字、命令回显和 HUD 标签使用同一套语言文件。OneConfig 设置界面使用英文原文。核对设置的译文时，使用 Web UI。

使用 Python 3，在仓库根目录运行以下命令。

### 准备语言文件

先查看已有语言及其进度：

```sh
python scripts/languages.py status
```

更新已有语言时，直接编辑语言文件，由维护者按需运行 `sync`。

添加新语言时，使用 `add`，指定语言标签和本地名称：

```sh
python scripts/languages.py add nl-NL --name Nederlands
```

脚本会将语言登记到 `languages.json`，并创建文本文件。

如果添加新语言期间英文原文发生变化，只同步正在添加的语言：

```sh
python scripts/languages.py sync --language nl-NL
```

同步会保留仍在使用的译文，新条目的译文行用 `=` 标记，不再使用的条目会被移除。

### 填写译文

打开 `src/main/resources/assets/pit12/languages/nl-NL.txt`。每个条目包含英文原文和译文行。用 `=` 和一个空格开头，表示所有使用位置共用一个译文：

```text
Player List
= Spelerslijst

Bound {0} to {1} ({2})
= {0} gekoppeld aan {1} ({2})

Settings
=
```

同一英文需要不同译文时，用 `==` 开头，在一行内用 `|` 分隔：

```text
// src/main/java/pit12/feature/eventlist/EventListSnapshot.java:106, web-ui/src/ProfilesPage.vue:290:21
Active
== Actief | Huidig
```

译文依次对应注释中的使用位置，数量须与位置数量一致。空段使用英文，例如 `== Actief |` 表示第二个位置未翻译。每段两侧的空白会被去掉。分开的译文中用 `\|` 表示竖线。`=` 后的竖线仍属于完整译文。

只保留 `=` 时，所有位置使用英文。用正则表达式 `^= ?$` 搜索未填写的共用译文。不要修改英文原文和位置注释。Web UI 的位置包含列号，用于区分同一行的调用。每个译文都须保留 `{0}` 等编号参数及其出现次数，可以调整顺序。保留 `§a` 等 Minecraft 格式代码。

同步会按每个文件内的顺序保留分开的译文。如果某个文件增加或减少了该原文的使用位置，该文件对应的译文段会留空，等待核对。移动或调整调用顺序后，也须核对分开的译文。状态报告会单独统计每个译文段。

用 `\n` 表示换行，`\t` 表示制表符，`\\` 表示反斜杠。文件保存为 UTF-8。

### 核对译文

查看该语言的进度和文件问题：

```sh
python scripts/languages.py status --language nl-NL
```

状态会显示翻译进度，并报告条目缺失或过时、原文重复、非法转义、参数错误，以及分开译文的位置格式错误或位置变化。

结合使用位置通读译文。需要在 Mod 中核对文字时，构建包含修改后资源的版本，再到 Settings 页面选择语言。

### 提交译文

拉取请求聚焦一种语言，包含 TXT 文件；新增语言时，也包含 `languages.json` 中的条目。说明贡献的语言、修改的文字和核对方式。可以提交部分译文，并说明尚未完成的部分。

修改代码中的英文原文时，参阅[实现指南的语言部分](IMPLEMENTATION.md#语言)。

## 贡献文档翻译

### 路径和链接

译文放在 `docs/<语言标签>/` 下。保留原文的文件名和它在 `docs/` 内的子目录。

| 英文原文 | 译文示例 |
| --- | --- |
| `README.md` | `docs/nl-NL/README.md` |
| `docs/CONTRIBUTING.md` | `docs/nl-NL/CONTRIBUTING.md` |

添加新语言时，先翻译 README。在根目录 README 的语言链接旁添加入口，使用该语言自己的名称。译文中的语言链接也用 `|` 分隔，与根目录 README 保持一致。

链接到的页面已有译文时，指向译文；否则指向英文原文。翻译后检查相对路径和标题锚点。命令、代码、标识符、文件路径和外部 URL 保持不变。

### 源文件页头

每篇译文都以 HTML 注释开头：

```markdown
<!-- Source: docs/CONTRIBUTING.md; Based on: <原文提交号> -->
```

`Source` 填写英文原文相对于仓库根目录的路径。`Based on` 填写已核对的原文版本对应的完整提交号。两个字段名都使用英文。README 译文的源文件路径是 `README.md`。

### 检查译文是否过时

在仓库根目录，将原文与页头记录的提交比较。把 `<原文提交号>` 替换为页头中的提交号，文件路径使用 `Source` 的值：

```sh
git diff <原文提交号> -- docs/CONTRIBUTING.md
```

核对原文在该提交之后的所有改动，并按需更新译文。确认译文与原文一致后，记录原文最近一次提交的完整提交号：

```sh
git log -1 --format=%H -- docs/CONTRIBUTING.md
```

### 提交译文

每个拉取请求尽量只包含一种语言的翻译，并列出新增或更新的文件。通读译文，检查链接、标题、示例和源文件页头。

如果同一拉取请求也需要新增或更新原文，应分成两个提交：先提交原文，再提交译文。译文的 `Based on` 填写原文提交的完整提交号。

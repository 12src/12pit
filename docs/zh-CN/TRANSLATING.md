<!-- Source: docs/TRANSLATING.md; Based on: 5306c0d49b3ab6dd3c265e892f723d3cb92a81a2 -->

# 贡献翻译

## 通用规则

添加新语言时：

- 如果语言有 [ISO 639-1 两字母代码](https://www.loc.gov/standards/iso639-2/php/code_list.php)，可以直接添加，无需先创建议题。
- 如果语言没有 ISO 639-1 代码，添加前须取得维护者同意。语言标签仍须符合 [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html)。可以在议题中询问，或在 [Discord](https://discord.gg/e9PRKMUenc) 中 @维护者。
- 建议只翻译成自己的母语。

添加前是否需要讨论，取决于语言标签。例如，`zh-CN` 使用 ISO 639-1 代码 `zh`，因此无需事先讨论。

## 贡献界面翻译

Web UI、配置文字、命令回显和 HUD 标签使用同一套语言文件。使用 Python 3，在仓库根目录运行以下命令。

### 准备语言文件

先查看已有语言及其进度：

```sh
python scripts/languages.py status
```

更新已有语言时，先同步文件。将 `zh-CN` 替换为你选择的语言标签：

```sh
python scripts/languages.py sync --language zh-CN
```

同步会保留已有译文，补充新的英文原文。已删除原文的译文会保存到 `.obsolete.txt` 文件，供后续参考。

添加新语言时，使用 `add`，指定语言标签和本地名称：

```sh
python scripts/languages.py add fr-FR --name Français
```

脚本会将语言登记到 `languages.json`，并创建文本文件。已有语言的设置值保持不变，无需修改 Java 或前端代码。

### 填写译文

打开 `src/main/resources/assets/pit12/languages/<语言标签>.txt`。每个条目有两行：第一行是英文原文，第二行是译文。在第二行填写翻译：

```text
Player List
玩家列表

Bound {0} to {1} ({2})
已将{0}绑定到{1}（{2}）
```

译文行留空时使用英文。不要修改英文原文。`//` 开头的行标明文字的使用位置。保留 `{0}` 等编号参数及其出现次数，可以调整顺序。保留 `§a` 等 Minecraft 格式代码。

用 `\n` 表示换行，`\t` 表示制表符，`\\` 表示反斜杠。文件保存为 UTF-8。如果翻译期间英文原文发生变化，再次运行 `sync --language <语言标签>`，并核对新增条目。

### 核对译文

查看该语言的进度和文件问题：

```sh
python scripts/languages.py status --language zh-CN
```

状态会显示已翻译数量和完成比例，空译文不计入已翻译数量。它也会报告缺失或过时条目、重复原文、非法转义和参数错误。不带 `--language` 时，`status` 显示所有语言，`sync` 更新所有翻译文件。

结合使用位置通读译文。状态检查文件结构，不判断用词是否合适。需要在 Mod 中核对文字时，构建包含修改后资源的版本，再到 Settings 页面选择语言。构建会打包语言文件，但不会运行脚本。

### 提交译文

拉取请求聚焦一种语言，包含 TXT 文件；新增语言时，也包含 `languages.json` 中的条目。说明贡献的语言、修改的文字和核对方式。未完成的条目留空，并在拉取请求中说明尚未完成的部分。

修改代码中的英文原文时，参阅[实现指南的语言部分](IMPLEMENTATION.md#语言)。

## 贡献文档翻译

### 路径和链接

译文放在 `docs/<语言标签>/` 下。保留原文的文件名和它在 `docs/` 内的子目录。

| 英文原文 | 译文示例 |
| --- | --- |
| `README.md` | `docs/zh-CN/README.md` |
| `docs/CONTRIBUTING.md` | `docs/zh-CN/CONTRIBUTING.md` |

添加新语言时，先翻译 README。在根目录 README 的语言链接旁添加入口，使用该语言自己的名称。译文中的语言链接也用 `|` 分隔，与根目录 README 保持一致。

其他文档可以分批提交。链接到的页面已有译文时，指向译文；否则指向英文原文。确保所有文档链接都指向已有文件。翻译后检查相对路径和标题锚点。命令、代码、标识符、文件路径和外部 URL 保持不变。

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

没有输出，表示原文未变。有差异，表示需要核对译文；其中部分改动可能已经翻译。其他文件的改动不会使这篇译文过时。

核对原文在该提交之后的所有改动，并按需更新译文。确认译文与原文一致后，记录原文最近一次提交的完整提交号：

```sh
git log -1 --format=%H -- docs/CONTRIBUTING.md
```

### 提交译文

每个拉取请求尽量只包含一种语言的翻译，并列出新增或更新的文件。通读译文，检查链接、标题、示例和源文件页头。

如果同一拉取请求也需要新增或更新原文，应分成两个提交：先提交原文，再提交译文。译文的 `Based on` 填写原文提交的完整提交号。

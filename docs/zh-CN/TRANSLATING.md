<!-- Source: docs/TRANSLATING.md; Based on: f293ac9e7ef68fc0145c6c72f93b5c3f30dbf7c5 -->

# 翻译 12pit

## 通用规则

添加新语言时：

- 如果语言有 [ISO 639-1 两字母代码](https://www.loc.gov/standards/iso639-2/php/code_list.php)，可以直接添加，无需先创建议题。
- 如果语言没有 ISO 639-1 代码，添加前须取得维护者同意。语言标签仍须符合 [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html)。可以在议题中询问，或在 [Discord](https://discord.gg/e9PRKMUenc) 中 @维护者。
- 建议只翻译成自己的母语。

添加前是否需要讨论，取决于语言标签。例如，`zh-CN` 使用 ISO 639-1 代码 `zh`，因此无需事先讨论。

## 文档翻译

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

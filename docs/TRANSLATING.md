# Contributing translations

## General rules

When adding a new language:

- If the language has an [ISO 639-1 two-letter code](https://www.loc.gov/standards/iso639-2/php/code_list.php), you can add it without opening an issue first.
- If the language has no ISO 639-1 code, get a maintainer's approval before adding it. Its language tag must still follow [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html). Ask in an issue or mention a maintainer on [Discord](https://discord.gg/e9PRKMUenc).
- We recommend translating only into your native language.

The language tag determines whether prior discussion is needed. For example, `zh-CN` uses the ISO 639-1 code `zh`, so it does not need prior discussion.

## Contribute interface translations

The same files include translations for the Web UI, configuration text, command messages, and HUD labels. Use Python 3 and execute the commands below from the repository root.

### Prepare a language

First, view the list of languages with their status:

```sh
python scripts/languages.py status
```

If the language already exists, update its file before translating. Substitute `zh-CN` with the language tag that you have chosen:

```sh
python scripts/languages.py sync --language zh-CN
```

That will preserve existing translations and add the new source English text. Deleted translations are stored in the .obsolete.txt files.

For a new language, use `add` with the language tag and its native name:

```sh
python scripts/languages.py add fr-FR --name Français
```

That adds the language to languages.json and creates the text file for it. Leave existing language names untouched. No changes in Java code and frontend are required.

### Write translations

Open src/main/resources/assets/pit12/languages/<language-tag>.txt. Every entry has two lines: the English source and the translation. Put the translation on the second line:

```text
Player List
玩家列表

Bound {0} to {1} ({2})
已将{0}绑定到{1}（{2}）
```

Keep the translation line empty if you want to use the English text. Do not modify the English source. Lines starting with // indicate the usage of the text. The numbered parameters like {0} should be left intact, along with their number of occurrences; their order may change. Preserve the Minecraft color codes like §a.

Use \n for a line break, \t for a tab character, and \\ for an escape backslash. Save the file in UTF-8. If the English source text has changed while you were working on the translation, rerun `sync --language <language-tag>`.

### Review your work

Check the status of the language and the file issues:

```sh
python scripts/languages.py status --language zh-CN
```

The report displays the number of translated entries and the percentage of completeness. Empty translations do not count as translated. It also points out missing entries, obsolete entries, duplicated sources, invalid escape sequences, and parameter issues. Without --language, status reports on all languages and sync updates all translation files.

Examine the translation in context. The script checks the file integrity, not the meaning of the words. To review the text in the mod, build the mod with the changed resources and pick the language on the Settings page. The build includes the language files but not the translation script.

### Submit a translation

Keep the pull request limited to one language. Attach the TXT file, and if that is a new language, attach the languages.json too. Mention the language, the text that you have changed and how you have checked it. Partial translations are OK; leave the untranslatable entries blank.

For changes to the English source text in the code, refer to [Languages in the implementation guide](IMPLEMENTATION.md#languages).

## Contribute documentation translations

### Paths and links

Put translations in `docs/<language-tag>/`. Keep the source filename and any subdirectories within `docs/`.

| English source | Translation example |
| --- | --- |
| `README.md` | `docs/zh-CN/README.md` |
| `docs/CONTRIBUTING.md` | `docs/zh-CN/CONTRIBUTING.md` |

For a new language, translate the README first. Add a link beside the language links in the root README, using the language's own name. Separate language links with `|` in translated documents as well.

Other documents can follow in separate pull requests. Link to a translated page when it exists; otherwise, link to the English page. Make sure all document links point to existing files. Check relative paths and heading anchors after translating. Keep commands, code, identifiers, file paths, and external URLs unchanged.

### Source header

Start each translated file with an HTML comment:

```markdown
<!-- Source: docs/CONTRIBUTING.md; Based on: <source-commit> -->
```

`Source` is the English file's path from the repository root. `Based on` is the full hash of a commit containing the source version you reviewed. Keep these field names in English. For a translated README, the source path is `README.md`.

### Check for outdated translations

From the repository root, compare the source with the commit in the header. Replace `<source-commit>` with that hash and use the file path from `Source`:

```sh
git diff <source-commit> -- docs/CONTRIBUTING.md
```

No output means the source has not changed. A diff means the translation needs review; it may already cover some of those changes. Changes to other files do not make this translation outdated.

Review every source change since the recorded commit, then update the translation as needed. Once they match, record the latest committed source version:

```sh
git log -1 --format=%H -- docs/CONTRIBUTING.md
```

### Submit a translation

Make sure that each pull request contains translations for only one language and mentions the files that have been modified. Examine the entire translation and validate all links, headings, examples, and the source header.

If the pull request not only modifies the translation but also modifies the source, make sure that the pull request is made up of two separate commits. Make the commit for the source and then for the translation.

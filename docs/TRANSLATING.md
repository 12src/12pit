# Contributing translations

## General rules

When adding a new language:

- If the language has an [ISO 639-1 two-letter code](https://www.loc.gov/standards/iso639-2/php/code_list.php), you can add it without opening an issue first.
- If the language has no ISO 639-1 code, get a maintainer's approval before adding it. Its language tag must still follow [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html). Ask in an issue or mention a maintainer on [Discord](https://discord.gg/e9PRKMUenc).
- We recommend translating only into your native language.

For example, `nl-NL` uses the ISO 639-1 code `nl`, so it does not need prior discussion.

## Contribute interface translations

The same files include translations for the Web UI, configuration text, command messages, and HUD labels. The OneConfig settings view uses the original English text. Check translated settings in the Web UI.

Use Python 3 and execute the commands below from the repository root.

### Prepare a language

First, view the list of languages with their status:

```sh
python scripts/languages.py status
```

If the language already exists, edit its file directly. Leave `sync` to maintainers, who run it when needed.

For a new language, use `add` with the language tag and its native name:

```sh
python scripts/languages.py add nl-NL --name Nederlands
```

That adds the language to languages.json and creates its text file.

If the English source changes while you are adding a new language, run `sync` for that language only:

```sh
python scripts/languages.py sync --language nl-NL
```

Sync keeps translations for existing source text, marks new translation lines with `=`, and removes unused entries.

### Write translations

Open `src/main/resources/assets/pit12/languages/nl-NL.txt`. Every entry has two lines: the English source, then `=` followed by a space and the translation:

```text
Player List
= Spelerslijst

Bound {0} to {1} ({2})
= {0} gekoppeld aan {1} ({2})

Settings
=
```

Leave only `=` on the second line to use the English text. To find untranslated entries, search with the regular expression `^= ?$`. Do not remove the prefix or modify the English source. Lines starting with // indicate the usage of the text. The numbered parameters like {0} should be left intact, along with their number of occurrences; their order may change. Preserve the Minecraft color codes like §a.

Use \n for a line break, \t for a tab character, and \\ for an escape backslash. Save the file in UTF-8.

### Review your work

Check the status of the language and the file issues:

```sh
python scripts/languages.py status --language nl-NL
```

The report shows translation progress and flags missing or obsolete entries, duplicate sources, invalid escapes, and parameter errors.

Review the wording in context. To check it in the mod, build with the changed resources and select the language on the Settings page.

### Submit a translation

Keep the pull request limited to one language. Attach the TXT file, and if that is a new language, attach the languages.json too. Mention the language, the text that you have changed and how you have checked it. Partial translations are OK.

For changes to the English source text in the code, refer to [Languages in the implementation guide](IMPLEMENTATION.md#languages).

## Contribute documentation translations

### Paths and links

Put translations in `docs/<language-tag>/`. Keep the source filename and any subdirectories within `docs/`.

| English source | Translation example |
| --- | --- |
| `README.md` | `docs/nl-NL/README.md` |
| `docs/CONTRIBUTING.md` | `docs/nl-NL/CONTRIBUTING.md` |

For a new language, translate the README first. Add a link beside the language links in the root README, using the language's own name. Separate language links with `|` in translated documents as well.

Link to a translated page when it exists; otherwise, link to the English page. Check relative paths and heading anchors after translating. Keep commands, code, identifiers, file paths, and external URLs unchanged.

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

No output means the source has not changed. If the diff shows changes, review the translation.

Review every source change since the recorded commit, then update the translation as needed. Once they match, record the latest committed source version:

```sh
git log -1 --format=%H -- docs/CONTRIBUTING.md
```

### Submit a translation

Keep each pull request to one language and list the changed files. Review the translation, links, headings, examples, and source header.

If a pull request changes both the source and its translation, use two commits: source first, then translation.

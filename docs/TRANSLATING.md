# Translating 12pit

## General rules

When adding a new language:

- If the language has an [ISO 639-1 two-letter code](https://www.loc.gov/standards/iso639-2/php/code_list.php), you can add it without opening an issue first.
- If the language has no ISO 639-1 code, get a maintainer's approval before adding it. Its language tag must still follow [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html). Ask in an issue or mention a maintainer on [Discord](https://discord.gg/e9PRKMUenc).
- We recommend translating only into your native language.

The language tag determines whether prior discussion is needed. For example, `zh-CN` uses the ISO 639-1 code `zh`, so it does not need prior discussion.

## Documentation

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

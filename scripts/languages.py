import argparse
import ast
from collections import Counter
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / "src/main/resources/assets/pit12/languages"
PARAMETER = re.compile(r"\{([0-9]+)\}")
TOKEN = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|[A-Za-z_$][\w$]*|[^\s]')
WEB_STRING = r'''(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')'''
PO_STRING = re.compile(r'"(?:[^"\\\x00-\x1f]|\\(?:[abfnrtv"\\]|[0-7]{1,3}))*"')
LOCATION = re.compile(r"(?:src/main/java/.+\.java:[1-9][0-9]*"
                      r"|web-ui/src/.+\.(?:vue|ts):[1-9][0-9]*(?::[1-9][0-9]*)?"
                      r"|src/main/resources/assets/pit12/languages/languages\.json:"
                      r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*)")


def write_changed(path, content):
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def read_catalog(text, name, entry_ranges=None):
    lines = text.lstrip("\ufeff").splitlines()
    catalog = {}
    usages = set()
    fields, locations, flags = {}, [], []
    current = None
    start = 0

    def finish(end):
        if not fields:
            return
        if "msgid" not in fields or "msgstr" not in fields:
            raise ValueError(f"{name}: incomplete PO entry")
        source, target = fields["msgid"], fields["msgstr"]
        if not source and "msgctxt" not in fields:
            return  # The empty msgid holds the PO header.
        context = fields.get("msgctxt", "")
        if not context or not source or not locations:
            raise ValueError(f"{name}: PO entries need a context, source, and usage location")
        key = (source, context)
        if key in catalog:
            raise ValueError(f"{name}: duplicate PO entry: {source!r} ({context})")
        for location in locations:
            if not LOCATION.fullmatch(location) or (source, location) in usages:
                raise ValueError(f"{name}: invalid or duplicate usage location: {location}")
            usages.add((source, location))
        if target and "fuzzy" not in flags and Counter(PARAMETER.findall(source)) != Counter(PARAMETER.findall(target)):
            raise ValueError(f"{name}: {source!r}: translation parameters do not match the source")
        catalog[key] = {"translation": target, "flags": flags}
        if entry_ranges is not None:
            entry_ranges.append((source, start, end))

    for number, line in enumerate(lines, 1):
        line = line.strip()
        if not line or ("msgstr" in fields and line.startswith(("#", "msgctxt", "msgid"))):
            next_start = number - 1 if line else number
            finish(next_start)
            start = next_start
            fields, locations, flags = {}, [], []
            current = None
        if not line:
            continue
        if line.startswith("#"):
            current = None
            if line.startswith("#:"):
                locations.extend(line[2:].split())
            elif line.startswith("#,"):
                flags.extend(flag.strip() for flag in line[2:].split(",") if flag.strip())
            continue
        if line.startswith('"'):
            value = line
            if current is None:
                raise ValueError(f"{name}:{number}: unexpected PO continuation")
        else:
            match = re.fullmatch(r'(msgctxt|msgid|msgstr)\s+(".*")', line)
            if not match:
                raise ValueError(f"{name}:{number}: unsupported PO line")
            current, value = match.groups()
            if current in fields:
                raise ValueError(f"{name}:{number}: duplicate PO field: {current}")
            fields[current] = ""
        if not PO_STRING.fullmatch(value):
            raise ValueError(f"{name}:{number}: invalid PO string")
        fields[current] += ast.literal_eval(value)
    finish(len(lines))
    return catalog


def compact_catalog(text, name):
    entry_ranges = []
    catalog = read_catalog(text, name, entry_ranges)
    # A missing location can turn the remaining translation into a shared one.
    translated_sources = {
        source for (source, _), entry in catalog.items()
        if entry["translation"] and entry["translation"] != source and "fuzzy" not in entry["flags"]
    }
    lines = text.splitlines(keepends=True)
    kept = []
    start = 0
    for source, first, end in entry_ranges:
        if source not in translated_sources:
            kept.extend(lines[start:first])
            start = end
    kept.extend(lines[start:])
    return "".join(kept)


def write_catalog(path, catalog):
    def quote(text):
        escapes = {"\\": r"\\", '"': r'\"', "\n": r"\n", "\r": r"\r", "\t": r"\t"}
        return '"' + "".join(escapes.get(character, f"\\{ord(character):03o}"
                                       if ord(character) < 32 else character) for character in text) + '"'

    headers = ["Project-Id-Version: 12pit\n", "MIME-Version: 1.0\n",
               "Content-Type: text/plain; charset=UTF-8\n", "Content-Transfer-Encoding: 8bit\n"]
    lines = ['msgid ""', 'msgstr ""', *(quote(header) for header in headers), ""]
    for (source, context), locations in catalog.items():
        lines.append("#: " + " ".join(locations))
        lines.extend(["msgctxt " + quote(context), "msgid " + quote(source), 'msgstr ""', ""])
    write_changed(path, "\n".join(lines))


def read_languages():
    entries = json.loads((DIRECTORY / "languages.json").read_text(encoding="utf-8"))
    locales = set()
    for entry in entries:
        locale, name = entry["locale"], entry["name"]
        display_name = entry["displayName"]
        if not re.fullmatch(r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*", locale) or locale.lower() in locales:
            raise ValueError(f"languages.json: invalid or duplicate language tag: {locale}")
        if not isinstance(name, str) or not name.strip():
            raise ValueError(f"languages.json: missing language name: {locale}")
        if not isinstance(display_name, str) or not display_name.strip():
            raise ValueError(f"languages.json: missing English display name: {locale}")
        locales.add(locale.lower())
    if "en-us" not in locales:
        raise ValueError("languages.json: English is missing")
    return entries


def select_languages(entries, locale):
    if locale is None:
        return entries
    for entry in entries:
        if entry["locale"].lower() == locale.lower():
            return [entry]
    raise ValueError(f"unknown language: {locale}")


def sources():
    found = {}
    for path in sorted((ROOT / "src/main/java").rglob("*.java")):
        content = path.read_text(encoding="utf-8")
        tokens = [match for match in TOKEN.finditer(content)
                  if not match.group().startswith(("//", "/*"))]
        for index, match in enumerate(tokens[:-2]):
            if match.group() not in {"source", "translate", "format"} or tokens[index + 1].group() != "(":
                continue
            cursor = index + 2
            if not tokens[cursor].group().startswith('"'):
                continue
            source = json.loads(tokens[cursor].group())
            while cursor + 2 < len(tokens) and tokens[cursor + 1].group() == "+" and tokens[cursor + 2].group().startswith('"'):
                cursor += 2
                source += json.loads(tokens[cursor].group())
            if tokens[cursor + 1].group() not in {",", ")"}:
                raise ValueError(f"{path}: language methods need a complete source string")
            if match.group() == "format" and not PARAMETER.search(source):
                continue
            line = content.count("\n", 0, match.start()) + 1
            if source:
                found.setdefault(source, []).append(f"{path.relative_to(ROOT).as_posix()}:{line}")
    for path in sorted((ROOT / "web-ui/src").rglob("*")):
        if path.suffix not in {".vue", ".ts"} or "generated" in path.parts:
            continue
        content = path.read_text(encoding="utf-8")
        for match in re.finditer(r"\bt\s*\(\s*(" + WEB_STRING + r"(?:\s*\+\s*" + WEB_STRING + r")*)\s*(?=[,)])", content):
            source = "".join(ast.literal_eval(part.group())
                             for part in re.finditer(WEB_STRING, match.group(1)))
            if source:
                line = content.count("\n", 0, match.start()) + 1
                # Match the compiler's UTF-16 columns.
                prefix = content[content.rfind("\n", 0, match.start()) + 1:match.start()]
                column = len(prefix.encode("utf-16-le")) // 2 + 1
                found.setdefault(source, []).append(f"{path.relative_to(ROOT).as_posix()}:{line}:{column}")
    for entry in read_languages():
        location = f"src/main/resources/assets/pit12/languages/languages.json:{entry['locale']}"
        found.setdefault(entry["displayName"], []).append(location)
    return found


def source_catalog(found):
    catalog = {}
    for source, locations in found.items():
        positions = Counter()
        if len(set(locations)) != len(locations):
            raise ValueError(f"{source!r}: put separate Java uses on separate lines")
        for location in locations:
            file, position = location.split(":", 1)
            positions[file] += 1
            context = position if file.endswith("languages.json") else positions[file]
            catalog[(source, f"{file}#{context}")] = [location]
    return catalog


class Crowdin:
    def __init__(self):
        project_id = os.getenv("CROWDIN_PROJECT_ID", "").strip()
        token = os.getenv("CROWDIN_PERSONAL_TOKEN", "").strip()
        if not project_id or not token:
            raise ValueError("Set CROWDIN_PROJECT_ID and CROWDIN_PERSONAL_TOKEN")
        if not re.fullmatch(r"[1-9][0-9]*", project_id):
            raise ValueError("CROWDIN_PROJECT_ID must be a positive integer")
        self.project = f"projects/{project_id}"
        self.headers = {"Authorization": f"Bearer {token}", "Accept": "application/json",
                        "Content-Type": "application/json", "User-Agent": "12pit"}

    def request(self, path, method="GET", data=None):
        body = json.dumps(data).encode("utf-8") if data is not None else None
        request = Request(f"https://api.crowdin.com/api/v2/{path}", data=body,
                          headers=self.headers, method=method)
        try:
            with urlopen(request, timeout=60) as response:
                if response.status == 204:
                    raise ValueError(f"Crowdin {method} {path}: no content was exported")
                return json.load(response)["data"]
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace").strip()
            raise ValueError(f"Crowdin {method} {path}: HTTP {error.code}: {detail or error.reason}") from error

    def list(self, path):
        rows = []
        while True:
            page = self.request(f"{path}?limit=500&offset={len(rows)}")
            rows.extend(item["data"] for item in page)
            if len(page) < 500:
                return rows

    def download(self, file_id, language_id, locale):
        # Keep untranslated PO entries empty.
        result = self.request(f"{self.project}/translations/builds/files/{file_id}", "POST",
                              {"targetLanguageId": language_id, "skipUntranslatedStrings": True,
                               "skipUntranslatedFiles": False})
        with urlopen(result["url"], timeout=60) as response:
            text = response.read().decode("utf-8-sig").rstrip("\r\n") + "\n"
        header = re.match(r'\Amsgid ""\r?\nmsgstr ""\r?\n((?:".*"\r?\n)*)(?:\r?\n)*', text)
        if header:
            metadata = "".join(ast.literal_eval(line) for line in header[1].splitlines())
            lines = [json.dumps(line + "\n", ensure_ascii=False) for line in metadata.splitlines()
                     if line.startswith(("MIME-Version:", "Content-Type:", "Content-Transfer-Encoding:",
                                         "Language:", "Plural-Forms:"))]
            text = 'msgid ""\nmsgstr ""\n' + "\n".join(lines) + "\n\n" + text[header.end():]
        path = DIRECTORY / (locale + ".po")
        catalog = read_catalog(text, path.name)
        if not catalog:
            raise ValueError(f"{path.name}: Crowdin returned an empty catalog")
        write_changed(path, text)
        return len(catalog)


def find_language(languages, locale):
    for language in languages:
        if locale.lower() in {language["id"].lower(), language["locale"].lower()}:
            return language
    raise ValueError(f"Crowdin does not support language: {locale}")


def status(entries):
    rows, notes = [], []
    failed = False
    for entry in entries:
        locale, name = entry["locale"], entry["name"]
        if locale.lower() == "en-us":
            rows.append((locale, "Source", "-", name))
            continue
        path = DIRECTORY / (locale + ".po")
        try:
            catalog = read_catalog(path.read_text(encoding="utf-8-sig"), path.name)
        except (OSError, ValueError) as error:
            rows.append((locale, "Invalid", "-", name))
            notes.append(f"{locale}: {error}")
            failed = True
            continue
        translated = sum(bool(entry["translation"]) and "fuzzy" not in entry["flags"]
                         for entry in catalog.values())
        slots = len(catalog)
        progress = translated / slots if slots else 1
        rows.append((locale, f"{translated}/{slots}", f"{progress:.1%}", name))

    width = max(len("Language"), *(len(row[0]) for row in rows))
    count_width = max(len("Translated"), *(len(row[1]) for row in rows))
    print(f"{'Language':<{width}}  {'Translated':>{count_width}}  {'Progress':>8}  Name")
    for locale, count, progress, name in rows:
        print(f"{locale:<{width}}  {count:>{count_width}}  {progress:>8}  {name}")
    if notes:
        print()
        print("\n".join(notes))
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description="Manage translation files")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("extract", help="Generate build/generated/languages/12pit.pot for CI")
    compact = commands.add_parser("compact", help="Write smaller PO files for a release")
    compact.add_argument("output", type=Path, help="Output directory")
    add = commands.add_parser("add", help="Enable a Crowdin language, download its PO, and register it locally")
    add.add_argument("language", help="Language tag, for example nl-NL")
    add.add_argument("--name", required=True, help="Native name, for example Nederlands")
    add.add_argument("--display-name", help="English display name (default: Crowdin language name)")
    for name, help_text in (("sync", "Download PO files from Crowdin"),
                            ("status", "Show local translation progress and file errors")):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("--language", help="Limit to one language")
    args = parser.parse_args()
    try:
        if args.command == "extract":
            found = sources()
            catalog = source_catalog(found)
            write_catalog(ROOT / "build/generated/languages/12pit.pot", catalog)
            print(f"Extracted {len(found)} source texts, {len(catalog)} usages")
            return 0
        if args.command == "compact":
            if args.output.resolve() == DIRECTORY.resolve():
                raise ValueError("Choose an output directory outside the source catalogs")
            for path in sorted(DIRECTORY.glob("*.po")):
                text = path.read_text(encoding="utf-8")
                write_changed(args.output / path.name, compact_catalog(text, path.name))
            return 0
        entries = read_languages()
        if args.command == "status":
            return status(select_languages(entries, args.language))
        if args.command == "sync":
            selected = select_languages(entries, args.language)
            if args.language is not None and selected[0]["locale"].lower() == "en-us":
                raise ValueError("Use extract to generate the English source POT")
            selected = [entry for entry in selected if entry["locale"].lower() != "en-us"]
        else:
            if not re.fullmatch(r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*", args.language):
                raise ValueError(f"invalid language tag: {args.language}")
            if not args.name.strip():
                raise ValueError("language name cannot be empty")
            if args.display_name is not None and not args.display_name.strip():
                raise ValueError("English display name cannot be empty")

        api = Crowdin()
        languages = api.list("languages")
        project = api.request(api.project)
        files = [file for file in api.list(f"{api.project}/files") if file["name"] == "12pit.pot"]
        if len(files) != 1:
            raise ValueError("Crowdin project must contain one 12pit.pot file. Upload sources through CI")
        file_id = files[0]["id"]
        if args.command == "add":
            language = find_language(languages, args.language)
            locale = language["locale"]
            if not re.fullmatch(r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*", locale):
                raise ValueError(f"Crowdin returned an invalid language tag: {locale}")
            if language["id"] == project["sourceLanguageId"]:
                raise ValueError("The source language cannot be added as a translation")
            if any(entry["locale"].lower() == locale.lower() for entry in entries):
                raise ValueError(f"Use sync to update the existing language: {locale}")
            targets = project["targetLanguageIds"]
            if language["id"] not in targets:
                api.request(api.project, "PATCH", [{"op": "replace", "path": "/targetLanguageIds",
                                                    "value": targets + [language["id"]]}])
            count = api.download(file_id, language["id"], locale)
            entry = {"locale": locale, "name": args.name.strip(),
                     "displayName": args.display_name.strip() if args.display_name is not None else language["name"]}
            entries.append(entry)
            entries.sort(key=lambda item: (item["locale"].lower() != "en-us",
                                           item["displayName"].lower(), item["locale"].lower()))
            content = "[\n" + ",\n".join("  { " + json.dumps(entry, ensure_ascii=False)[1:-1] + " }"
                                      for entry in entries) + "\n]\n"
            write_changed(DIRECTORY / "languages.json", content)
            print(f"Added {locale} ({entry['name']}): {count} entries")
            return 0
        downloads = []
        for entry in selected:
            language = find_language(languages, entry["locale"])
            if language["id"] not in project["targetLanguageIds"]:
                raise ValueError(f"Enable {entry['locale']} in the Crowdin project before syncing")
            downloads.append((entry, language["id"]))
        for entry, language_id in downloads:
            count = api.download(file_id, language_id, entry["locale"])
            print(f"Synced {entry['locale']}: {count} entries")
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Manage language files and show translation progress."""

import argparse
import ast
from collections import Counter
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / "src/main/resources/assets/pit12/languages"
PARAMETER = re.compile(r"\{([0-9]+)\}")
TOKEN = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|[A-Za-z_$][\w$]*|[^\s]')
WEB_STRING = r'''(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')'''


def write_changed(path, content):
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return
    path.write_text(content, encoding="utf-8", newline="\n")


def decode_line(line, path, number):
    escapes = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\"}
    result = []
    index = 0
    while index < len(line):
        character = line[index]
        index += 1
        if character == "\\":
            if index == len(line) or line[index] not in escapes:
                raise ValueError(f"{path}:{number}: invalid escape")
            character = escapes[line[index]]
            index += 1
        result.append(character)
    return "".join(result)


def encode_line(text):
    return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")


def read_pairs(path):
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    pairs = {}
    index = 0
    while index < len(lines):
        source_line = lines[index]
        index += 1
        if not source_line or source_line.startswith("//"):
            continue
        number = index
        source = decode_line(source_line, path, number)
        if index == len(lines):
            raise ValueError(f"{path}:{number}: missing translation line")
        target_line = lines[index]
        if target_line != "=" and not target_line.startswith("= "):
            raise ValueError(f"{path}:{index + 1}: translation line must be '=' or start with '= '")
        target = decode_line(target_line[2:], path, index + 1)
        index += 1
        if source in pairs:
            raise ValueError(f"{path}:{number}: duplicate source: {source}")
        if target and Counter(PARAMETER.findall(source)) != Counter(PARAMETER.findall(target)):
            raise ValueError(f"{path}:{number + 1}: translation parameters do not match the source")
        pairs[source] = target
    return pairs


def read_languages():
    entries = json.loads((DIRECTORY / "languages.json").read_text(encoding="utf-8"))
    values, locales = set(), set()
    for entry in entries:
        value, locale, name = entry["value"], entry["locale"], entry["name"]
        if type(value) is not int or value < 0 or value in values:
            raise ValueError("languages.json: language values must be unique nonnegative integers")
        if not re.fullmatch(r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*", locale) or locale.lower() in locales:
            raise ValueError(f"languages.json: invalid or duplicate language tag: {locale}")
        if not isinstance(name, str) or not name.strip():
            raise ValueError(f"languages.json: missing language name: {locale}")
        if (value == 0) != (locale.lower() == "en-us"):
            raise ValueError("languages.json: English must keep value 0")
        values.add(value)
        locales.add(locale.lower())
    if 0 not in values:
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
        for match in re.finditer(r"\bt\(\s*(" + WEB_STRING + r"(?:\s*\+\s*" + WEB_STRING + r")*)\s*(?=[,)])", content):
            source = "".join(ast.literal_eval(part.group())
                             for part in re.finditer(WEB_STRING, match.group(1)))
            if source:
                line = content.count("\n", 0, match.start()) + 1
                found.setdefault(source, []).append(f"{path.relative_to(ROOT).as_posix()}:{line}")
    return found


def sync(found, entry):
    path = DIRECTORY / (entry["locale"] + ".txt")
    previous = read_pairs(path) if path.exists() else {}
    lines = []
    for source, locations in found.items():
        target = previous.get(source, "")
        lines.extend(["// " + ", ".join(locations), encode_line(source),
                      "= " + encode_line(target) if target else "=", ""])
    write_changed(path, "\n".join(lines) + "\n")
    return len(found.keys() - previous.keys()), len(previous.keys() - found.keys())


def status(found, entries):
    total = len(found)
    rows, notes = [], []
    failed = False
    for entry in entries:
        locale, name = entry["locale"], entry["name"]
        if entry["value"] == 0:
            rows.append((locale, "Source", "-", name))
            continue
        path = DIRECTORY / (locale + ".txt")
        try:
            pairs = read_pairs(path) if path.exists() else {}
        except (OSError, ValueError) as error:
            rows.append((locale, "Invalid", "-", name))
            notes.append(f"{locale}: {error}")
            failed = True
            continue
        translated = sum(bool(pairs.get(source)) for source in found)
        progress = translated / total if total else 1
        rows.append((locale, f"{translated}/{total}", f"{progress:.1%}", name))
        missing = len(found.keys() - pairs.keys())
        stale = len(pairs.keys() - found.keys())
        if not path.exists():
            notes.append(f"{locale}: file missing")
        elif missing or stale:
            notes.append(f"{locale}: {missing} missing, {stale} obsolete")

    width = max(len("Language"), *(len(row[0]) for row in rows))
    count_width = max(len("Translated"), len(f"{total}/{total}"))
    print(f"{total} source texts\n")
    print(f"{'Language':<{width}}  {'Translated':>{count_width}}  {'Progress':>8}  Name")
    for locale, count, progress, name in rows:
        print(f"{locale:<{width}}  {count:>{count_width}}  {progress:>8}  {name}")
    if notes:
        print()
        print("\n".join(notes))
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    add = commands.add_parser("add", help="Add a language")
    add.add_argument("language", help="Language tag, for example nl-NL")
    add.add_argument("--name", required=True, help="Native name, for example Nederlands")
    for name, help_text in (("sync", "Update source text"), ("status", "Show translation progress")):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("--language", help="Limit to one language")
    args = parser.parse_args()
    try:
        entries = read_languages()
        if args.command == "add":
            if not re.fullmatch(r"[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*", args.language):
                raise ValueError(f"invalid language tag: {args.language}")
            if any(entry["locale"].lower() == args.language.lower() for entry in entries):
                raise ValueError(f"language already exists: {args.language}")
            if not args.name.strip():
                raise ValueError("language name cannot be empty")
            entry = {"value": max(entry["value"] for entry in entries) + 1,
                     "locale": args.language, "name": args.name.strip()}
            found = sources()
            sync(found, entry)
            entries.append(entry)
            write_changed(DIRECTORY / "languages.json", json.dumps(entries, ensure_ascii=False, indent=2) + "\n")
            print(f"Added {entry['locale']} ({entry['name']}): {len(found)} texts")
            return 0
        selected = select_languages(entries, args.language)
        found = sources()
        if args.command == "status":
            return status(found, selected)
        for entry in selected:
            if entry["value"] == 0:
                if args.language:
                    print(f"{entry['locale']}: source language; nothing to sync")
                continue
            added, removed = sync(found, entry)
            print(f"Synced {entry['locale']}: {len(found)} texts ({added} added, {removed} removed)")
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

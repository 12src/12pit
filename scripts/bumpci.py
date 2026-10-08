from functools import cache
import json
import os
from pathlib import Path
import re
from shutil import which
from subprocess import check_output
from urllib.parse import quote
from urllib.request import Request, urlopen

GH = which("gh")
HEADERS = {"Accept": "application/vnd.github+json", "User-Agent": "12pit"}
if token := os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN"):
    HEADERS["Authorization"] = f"Bearer {token}"

USES = re.compile(
    r"^([ \t]*(?:-[ \t]*)?uses:[ \t]*)(['\"]?)"
    r"([\w-]+/[\w./-]+)@[^\s#'\"]+\2[ \t]*(?:#[^\r\n]*)?",
    re.MULTILINE,
)


def github(path):
    if GH:
        return json.loads(check_output([GH, "api", f"repos/{path}", "--hostname", "github.com"], timeout=30))
    request = Request(f"https://api.github.com/repos/{path}", headers=HEADERS)
    with urlopen(request, timeout=30) as response:
        return json.load(response)


@cache
def latest(repo):
    tag = github(f"{repo}/releases/latest")["tag_name"]
    sha = github(f"{repo}/commits/{quote(tag, safe='')}")["sha"]
    return sha, tag


def replace_action(match):
    prefix, quote_mark, action = match.groups()
    repo = "/".join(action.split("/")[:2])
    sha, tag = latest(repo)
    return f"{prefix}{quote_mark}{action}@{sha}{quote_mark} # {tag}"


if __name__ == "__main__":
    directory = Path(__file__).resolve().parents[1] / ".github/workflows"
    for path in sorted(directory.iterdir()):
        if path.suffix not in {".yml", ".yaml"}:
            continue
        content = path.read_bytes().decode("utf-8")
        updated = USES.sub(replace_action, content)
        if updated != content:
            path.write_bytes(updated.encode("utf-8"))

#!/usr/bin/env python3
"""
Generate Fastlane Play Store changelog files from CHANGELOG.md.

Only entries with an explicit target versionCode are exported. The generated
files stay under Google Play's 500-character "What's new" limit.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHANGELOG = ROOT / "CHANGELOG.md"
FASTLANE_DIR = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
MAX_CHARS = 499

HEADING_RE = re.compile(r"^##\s+(v?\d+(?:\.\d+)+).*", re.MULTILINE)
CODE_ARROW_RE = re.compile(r"versionCode\s+(\d+)\s*(?:->|→)\s*(\d+)")
CODE_RE = re.compile(r"versionCode\s+(\d+)")


def split_releases(changelog: str) -> list[tuple[str, str]]:
    matches = list(HEADING_RE.finditer(changelog))
    releases: list[tuple[str, str]] = []
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(changelog)
        releases.append((match.group(1).lstrip("v"), changelog[start:end].strip()))
    return releases


def target_version_code(body: str) -> int | None:
    arrow_matches = list(CODE_ARROW_RE.finditer(body))
    if arrow_matches:
        return int(arrow_matches[-1].group(2))
    matches = list(CODE_RE.finditer(body))
    if matches:
        return int(matches[-1].group(1))
    return None


def normalize(text: str) -> str:
    replacements = {
        "—": "-",
        "→": "->",
        "“": '"',
        "”": '"',
        "’": "'",
        "`": "",
        "**": "",
    }
    for source, target in replacements.items():
        text = text.replace(source, target)
    return re.sub(r"\s+", " ", text).strip()


def release_bullets(body: str) -> list[str]:
    bullets: list[str] = []
    current: list[str] = []
    for raw_line in body.splitlines():
        line = raw_line.strip()
        if line.startswith("- "):
            if current:
                bullets.append(normalize(" ".join(current)))
            current = [line[2:]]
            continue
        if current and line and not line.startswith("#"):
            current.append(line)
    if current:
        bullets.append(normalize(" ".join(current)))

    filtered: list[str] = []
    for bullet in bullets:
        lower = bullet.lower()
        if lower.startswith("bumped runtime metadata"):
            continue
        if lower.startswith("verification:"):
            continue
        if "versioncode" in lower and "versionname" in lower:
            continue
        filtered.append(bullet)
    return filtered


def release_title(body: str) -> str | None:
    for line in body.splitlines():
        stripped = line.strip()
        if stripped.startswith("### "):
            return normalize(stripped[4:])
    return None


def truncate_entry(lines: list[str]) -> str:
    output: list[str] = []
    length = 0
    for line in lines:
        entry = f"- {line}"
        projected = length + len(entry) + (1 if output else 0)
        if projected <= MAX_CHARS:
            output.append(entry)
            length = projected
            continue
        remaining = MAX_CHARS - length - (1 if output else 0) - 2
        if remaining > 24:
            output.append(f"- {line[:remaining - 3].rstrip()}...")
        break
    return "\n".join(output).strip() + "\n"


def changelog_text(body: str) -> str:
    lines: list[str] = []
    title = release_title(body)
    if title:
        lines.append(title)
    lines.extend(release_bullets(body))
    if not lines:
        lines.append("ClearCut release maintenance and stability updates.")
    return truncate_entry(lines)


def generated_changelogs() -> dict[int, str]:
    changelog = CHANGELOG.read_text(encoding="utf-8")
    generated: dict[int, str] = {}
    for _version_name, body in split_releases(changelog):
        version_code = target_version_code(body)
        if version_code is None:
            continue
        generated[version_code] = changelog_text(body)
    return generated


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if generated files are stale")
    args = parser.parse_args()

    generated = generated_changelogs()
    if not generated:
        print("No versionCode-backed changelog entries found.", file=sys.stderr)
        return 1

    FASTLANE_DIR.mkdir(parents=True, exist_ok=True)
    stale: list[Path] = []
    for version_code, contents in sorted(generated.items()):
        path = FASTLANE_DIR / f"{version_code}.txt"
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != contents:
                stale.append(path)
            continue
        path.write_text(contents, encoding="utf-8")

    if stale:
        for path in stale:
            print(f"stale fastlane changelog: {path.relative_to(ROOT)}", file=sys.stderr)
        return 1

    action = "verified" if args.check else "wrote"
    print(f"{action} {len(generated)} fastlane changelog files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

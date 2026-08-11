#!/usr/bin/env python3
"""Run the local compatibility gate for one manually staged dependency pin.

This probe never edits ``gradle/libs.versions.toml``. The caller stages the
candidate there, runs this command, and keeps the catalog change only if the
full QA test/lint/assemble lane passes. A successful probe records its exact
candidate and date in the freshness snapshot.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
SNAPSHOT = ROOT / "scripts" / "dependency_freshness_snapshot.json"
TASKS = [":app:testQaUnitTest", ":app:lintQa", ":app:assembleQa"]


def parse_catalog() -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    assignment = re.compile(r'^\s*([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"')
    for line in CATALOG.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped == "[versions]":
            in_versions = True
            continue
        if stripped.startswith("[") and stripped != "[versions]":
            in_versions = False
            continue
        if in_versions:
            match = assignment.match(line)
            if match:
                versions[match.group(1)] = match.group(2)
    return versions


def numeric_version(value: str) -> tuple[int, ...]:
    match = re.match(r"^(\d+(?:\.\d+)*)", value)
    if not match:
        return ()
    return tuple(int(part) for part in match.group(1).split("."))


def wrapper_gradle_version() -> tuple[int, ...]:
    properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    match = re.search(r"gradle-(\d+(?:\.\d+)+)-", properties)
    if not match:
        raise RuntimeError("could not determine the Gradle wrapper version")
    return numeric_version(match.group(1))


def preflight(dependency: str, candidate: str, catalog: dict[str, str]) -> None:
    if dependency == "room":
        catalog_text = CATALOG.read_text(encoding="utf-8")
        if numeric_version(candidate) < (3, 0, 0) or "androidx-room3-runtime" not in catalog_text:
            raise RuntimeError(
                "Room 3 is a coordinate/API migration (androidx.room3); migrate the Room "
                "sources and catalog aliases before running this probe."
            )
    if dependency == "agp":
        required_gradle = {
            (9, 2): (9, 4, 1),
            (9, 3): (9, 5, 0),
        }.get(numeric_version(candidate)[:2])
        if required_gradle and wrapper_gradle_version() < required_gradle:
            required = ".".join(str(part) for part in required_gradle)
            actual = ".".join(str(part) for part in wrapper_gradle_version())
            raise RuntimeError(f"AGP {candidate} requires Gradle {required}; wrapper is {actual}")
    if dependency == "lifecycle" and numeric_version(candidate) >= (2, 11, 0):
        agp = numeric_version(catalog.get("agp", ""))
        if agp < (9, 2, 0):
            raise RuntimeError(
                f"Lifecycle {candidate} requires AGP 9.2.0 for the Compose compileSdk path; catalog has {catalog.get('agp')}"
            )


def write_probe_result(snapshot: dict, dependency: str, candidate: str, command: list[str]) -> None:
    entry = snapshot["dependencies"][dependency]
    entry["pinnedVersion"] = candidate
    entry["compatibilityProbe"] = {
        "status": "passed",
        "version": candidate,
        "verifiedOn": dt.date.today().isoformat(),
        # Keep the recorded command directly rerunnable through this gate;
        # retain the exact Gradle subprocess separately for audit evidence.
        "command": f"python scripts/probe_dependency_upgrade.py --dependency {dependency} --version {candidate}",
        "gradleCommand": " ".join(command),
    }
    if entry.get("latestStable") == candidate:
        entry["state"] = "current"
        entry["reason"] = "The catalog pin matches the latest stable version after a passing local compatibility probe."
        entry["unblockCondition"] = "Run `python scripts/refresh_dependency_freshness.py` before changing this pin."
    temporary = SNAPSHOT.with_suffix(".tmp")
    temporary.write_text(json.dumps(snapshot, indent=2) + "\n", encoding="utf-8")
    temporary.replace(SNAPSHOT)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependency", required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    try:
        catalog = parse_catalog()
        snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
        if args.dependency not in snapshot["dependencies"]:
            raise RuntimeError(f"dependency is not tracked by the freshness snapshot: {args.dependency}")
        actual = catalog.get(args.dependency)
        if actual != args.version:
            raise RuntimeError(
                f"stage {args.version} in gradle/libs.versions.toml first; catalog currently has {actual}"
            )
        preflight(args.dependency, args.version, catalog)
    except (OSError, json.JSONDecodeError, KeyError, RuntimeError) as exc:
        print(f"dependency compatibility probe blocked: {exc}", file=sys.stderr)
        return 2

    display_command = [
        "gradlew.bat",
        *TASKS,
        "--no-daemon",
        "--max-workers=1",
        "-Dorg.gradle.jvmargs=-Xmx1024m",
        "-Dorg.gradle.workers.max=1",
    ]
    process_command = [str(ROOT / "gradlew.bat"), *display_command[1:]]
    print(f"running compatibility probe for {args.dependency} {args.version}")
    result = subprocess.run(process_command, cwd=ROOT, check=False)
    if result.returncode != 0:
        print(f"dependency compatibility probe failed with exit code {result.returncode}", file=sys.stderr)
        return result.returncode or 1
    write_probe_result(snapshot, args.dependency, args.version, display_command)
    print(f"recorded passing compatibility probe for {args.dependency} {args.version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

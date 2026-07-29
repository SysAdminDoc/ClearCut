#!/usr/bin/env python3
"""Verify that published GitHub Releases carry trust sidecars for every APK.

The local release gate (`verify_release_artifacts.py`) checks the APKs in the
build directory. Nothing checked what was actually *uploaded*, which is how
v3.76.0 shipped a lone 366 MB APK with neither its SHA-256 nor its signing
certificate fingerprint — leaving sideloaders no way to tell a genuine build
from a repackaged one.

Usage:
    python scripts/verify_published_release_sidecars.py            # every release
    python scripts/verify_published_release_sidecars.py v3.76.0    # one tag
    python scripts/verify_published_release_sidecars.py --latest
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys

REPO = "SysAdminDoc/ClearCut"
REQUIRED_SUFFIXES = (".sha256", ".signing-cert-sha256")


class SidecarError(RuntimeError):
    pass


def gh(*args: str) -> str:
    executable = shutil.which("gh")
    if not executable:
        raise SidecarError("the GitHub CLI ('gh') is required to inspect published releases")
    result = subprocess.run(
        [executable, *args],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise SidecarError(f"gh {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def release_tags(limit: int) -> list[str]:
    payload = gh("release", "list", "--repo", REPO, "--limit", str(limit), "--json", "tagName")
    return [entry["tagName"] for entry in json.loads(payload)]


def asset_names(tag: str) -> list[str]:
    payload = gh("release", "view", tag, "--repo", REPO, "--json", "assets")
    return [asset["name"] for asset in json.loads(payload).get("assets", [])]


def missing_sidecars(assets: list[str]) -> list[str]:
    present = set(assets)
    missing: list[str] = []
    for name in assets:
        if not name.endswith(".apk"):
            continue
        for suffix in REQUIRED_SUFFIXES:
            if f"{name}{suffix}" not in present:
                missing.append(f"{name}{suffix}")
    return missing


def verify(tags: list[str]) -> None:
    failures: list[str] = []
    for tag in tags:
        assets = asset_names(tag)
        apks = [name for name in assets if name.endswith(".apk")]
        if not apks:
            print(f"{tag}: no APK assets, nothing to verify")
            continue
        missing = missing_sidecars(assets)
        if missing:
            failures.append(f"{tag} is missing: {', '.join(sorted(missing))}")
        else:
            print(f"{tag}: {len(apks)} APK(s), all sidecars present")
    if failures:
        raise SidecarError(
            "published releases are missing trust sidecars:\n  " + "\n  ".join(failures)
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tag", nargs="?", help="verify a single tag instead of the recent list")
    parser.add_argument("--latest", action="store_true", help="verify only the most recent release")
    parser.add_argument("--limit", type=int, default=20, help="how many recent releases to check")
    args = parser.parse_args()

    try:
        if args.tag:
            tags = [args.tag]
        elif args.latest:
            tags = release_tags(1)
        else:
            tags = release_tags(args.limit)
        verify(tags)
    except SidecarError as error:
        print(f"published release verification failed: {error}", file=sys.stderr)
        return 1
    print("Published release trust sidecars verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

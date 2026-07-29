#!/usr/bin/env python3
"""Report per-device download sizes from the release app bundle.

A universal APK's size says nothing about what a user downloads. Google Play
serves configuration splits and refuses anything above a 200 MB download, so the
number that matters is the compressed per-ABI set bundletool computes from the
`.aab` — not the ~350 MB universal artifact.

Requires bundletool. Point at it with --bundletool or $BUNDLETOOL_JAR; without
it the check reports that it was skipped rather than passing silently.

Usage:
    python scripts/report_bundle_sizes.py
    python scripts/report_bundle_sizes.py --bundletool ~/repos/bundletool.jar
"""
from __future__ import annotations

import argparse
import csv
import io
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"

# Google Play refuses an install whose download exceeds this.
PLAY_DOWNLOAD_CEILING_BYTES = 200 * 1024 * 1024


class BundleSizeError(RuntimeError):
    pass


def locate_java() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / "java.exe"
        if candidate.is_file():
            return str(candidate)
        candidate = Path(java_home) / "bin" / "java"
        if candidate.is_file():
            return str(candidate)
    found = shutil.which("java")
    if not found:
        raise BundleSizeError("java was not found; set JAVA_HOME or put java on PATH")
    return found


def locate_bundletool(explicit: str | None) -> Path | None:
    for candidate in (explicit, os.environ.get("BUNDLETOOL_JAR"), str(Path.home() / "repos" / "bundletool.jar")):
        if candidate and Path(candidate).is_file():
            return Path(candidate)
    return None


def measure(bundletool: Path) -> list[tuple[str, int, int]]:
    if not BUNDLE.is_file():
        raise BundleSizeError(
            f"missing release bundle: {BUNDLE.relative_to(ROOT)} — run ':app:bundleRelease' first"
        )
    java = locate_java()
    with tempfile.TemporaryDirectory() as temp:
        apks = Path(temp) / "release.apks"
        build = subprocess.run(
            [java, "-jar", str(bundletool), "build-apks",
             f"--bundle={BUNDLE}", f"--output={apks}", "--overwrite"],
            capture_output=True, text=True, check=False,
        )
        if build.returncode != 0:
            raise BundleSizeError(f"bundletool build-apks failed: {build.stderr.strip()}")

        sizes = subprocess.run(
            [java, "-jar", str(bundletool), "get-size", "total",
             f"--apks={apks}", "--dimensions=ABI"],
            capture_output=True, text=True, check=False,
        )
        if sizes.returncode != 0:
            raise BundleSizeError(f"bundletool get-size failed: {sizes.stderr.strip()}")

    rows: list[tuple[str, int, int]] = []
    reader = csv.reader(io.StringIO(sizes.stdout.strip()))
    header = next(reader, None)
    if header is None or header[0].upper() != "ABI":
        raise BundleSizeError(f"unexpected bundletool output: {sizes.stdout.strip()[:200]}")
    for row in reader:
        if len(row) < 3:
            continue
        rows.append((row[0], int(row[1]), int(row[2])))
    if not rows:
        raise BundleSizeError("bundletool reported no per-ABI sizes")
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundletool", help="path to bundletool.jar")
    parser.add_argument(
        "--require-bundletool",
        action="store_true",
        help="fail instead of skipping when bundletool is unavailable",
    )
    args = parser.parse_args()

    bundletool = locate_bundletool(args.bundletool)
    if bundletool is None:
        message = "bundletool not found; per-device download sizes were not measured"
        if args.require_bundletool:
            print(f"bundle size check failed: {message}", file=sys.stderr)
            return 1
        print(f"SKIPPED: {message} (pass --bundletool or set BUNDLETOOL_JAR)")
        return 0

    try:
        rows = measure(bundletool)
    except BundleSizeError as error:
        print(f"bundle size check failed: {error}", file=sys.stderr)
        return 1

    over = [row for row in rows if row[2] > PLAY_DOWNLOAD_CEILING_BYTES]
    print("Per-device download sizes from the release bundle:")
    for abi, minimum, maximum in sorted(rows):
        print(f"  - {abi}: {minimum / 1024 / 1024:.1f}-{maximum / 1024 / 1024:.1f} MB")
    if over:
        names = ", ".join(f"{abi} ({maximum} bytes)" for abi, _, maximum in over)
        print(
            f"bundle size check failed: above the {PLAY_DOWNLOAD_CEILING_BYTES}-byte "
            f"Play download ceiling: {names}",
            file=sys.stderr,
        )
        return 1
    print(f"All per-device sets are within the {PLAY_DOWNLOAD_CEILING_BYTES // 1024 // 1024} MB Play ceiling.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

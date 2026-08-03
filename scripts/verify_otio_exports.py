#!/usr/bin/env python3
"""Validate ClearCut OTIO output with the official OpenTimelineIO adapter.

Usage:
    python scripts/verify_otio_exports.py app/build/exports
    python scripts/verify_otio_exports.py path/to/one.otio path/to/another.otio
    python scripts/verify_otio_exports.py --self-test

The Android unit suite validates the Kotlin round-trip contract. This small
release-side gate proves the generated JSON is also consumable by the official
ASWF/Python adapter, without making OpenTimelineIO an Android runtime
dependency.
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path
from typing import Iterable


def load_otio():
    try:
        import opentimelineio as otio
    except ImportError as exc:  # pragma: no cover - environment-dependent
        raise SystemExit(
            "OpenTimelineIO is required for this gate. Install the maintained "
            "Python package with: python -m pip install opentimelineio"
        ) from exc
    return otio


def candidate_files(inputs: Iterable[str]) -> list[Path]:
    paths: list[Path] = []
    for raw in inputs:
        path = Path(raw)
        if path.is_dir():
            paths.extend(sorted(path.rglob("*.otio")))
        elif path.is_file():
            paths.append(path)
        else:
            raise SystemExit(f"OTIO input does not exist: {path}")
    return list(dict.fromkeys(paths))


def validate_file(otio, path: Path) -> None:
    # Parse the raw JSON first so the error names malformed JSON separately
    # from an OTIO schema/adapter failure.
    root = json.loads(path.read_text(encoding="utf-8"))
    if root.get("OTIO_SCHEMA", "") != "Timeline.1":
        raise ValueError(f"{path}: root OTIO_SCHEMA must be Timeline.1")
    timeline = otio.adapters.read_from_file(str(path), media_linker_name=None)
    if not isinstance(timeline, otio.schema.Timeline):
        raise ValueError(f"{path}: adapter returned {type(timeline).__name__}, not Timeline")
    if timeline.tracks is None:
        raise ValueError(f"{path}: Timeline.tracks is missing")


def self_test(otio) -> None:
    fixture = {
        "OTIO_SCHEMA": "Timeline.1",
        "name": "ClearCut OTIO gate fixture",
        "metadata": {},
        "tracks": {
            "OTIO_SCHEMA": "Stack.1",
            "name": "tracks",
            "children": [],
        },
    }
    with tempfile.TemporaryDirectory(prefix="clearcut-otio-") as directory:
        path = Path(directory) / "fixture.otio"
        path.write_text(json.dumps(fixture, indent=2), encoding="utf-8")
        validate_file(otio, path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", help="OTIO files or directories to validate")
    parser.add_argument("--self-test", action="store_true", help="validate a built-in minimal OTIO fixture")
    args = parser.parse_args()
    otio = load_otio()
    if args.self_test:
        self_test(otio)
        print("OTIO official-adapter self-test passed")
        return 0

    inputs = args.paths or ["app/build"]
    files = candidate_files(inputs)
    if not files:
        raise SystemExit("No .otio files found; pass an export file or directory")
    for path in files:
        validate_file(otio, path)
        print(f"OTIO valid: {path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, json.JSONDecodeError) as exc:
        print(f"OTIO validation failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc

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

OTIO_ROOT_SCHEMA = "Timeline.1"
SUPPORTED_OTIO_SCHEMA_VERSIONS = {"0.15", "0.16"}
OTIO_ADAPTER_RANGE = "0.15-0.16"
OTIO_SCHEMA_METADATA_KEY = "clearcut_otio_schema_version"
OTIO_ADAPTER_METADATA_KEY = "clearcut_otio_adapter_range"


def load_otio():
    try:
        import opentimelineio as otio
    except ImportError as exc:  # pragma: no cover - environment-dependent
        raise SystemExit(
            "OpenTimelineIO is required for this gate and is not installed, so the "
            "gate cannot vouch for anything. Install the pinned version with: "
            "python -m pip install -r scripts/requirements.txt"
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
    if root.get("OTIO_SCHEMA", "") != OTIO_ROOT_SCHEMA:
        raise ValueError(f"{path}: root OTIO_SCHEMA must be {OTIO_ROOT_SCHEMA}")
    validate_contract_metadata(root, path)
    timeline = otio.adapters.read_from_file(str(path), media_linker_name=None)
    if not isinstance(timeline, otio.schema.Timeline):
        raise ValueError(f"{path}: adapter returned {type(timeline).__name__}, not Timeline")
    if timeline.tracks is None:
        raise ValueError(f"{path}: Timeline.tracks is missing")
    validate_structure(otio, timeline, root, path)


def timeline_shape(timeline) -> list:
    """The content a ClearCut export promises, reduced to comparable values.

    Deliberately not a JSON diff: the adapter is free to reorder keys, normalise
    numbers and add defaults. What must survive is the edit itself — how many tracks,
    what sits on each one, where it sits, at what rate, and what media it points at.
    """
    shape = []
    for track in timeline.tracks:
        children = []
        for child in track:
            source_range = getattr(child, "source_range", None)
            timing = None
            if source_range is not None:
                timing = (
                    source_range.start_time.value,
                    source_range.duration.value,
                    source_range.start_time.rate,
                )
            children.append(
                (
                    type(child).__name__,
                    child.name,
                    timing,
                    len(getattr(child, "effects", []) or []),
                    getattr(getattr(child, "media_reference", None), "target_url", None),
                )
            )
        shape.append((track.name, track.kind, children))
    return shape


def validate_structure(otio, timeline, root: dict, path: Path) -> None:
    """Round-trip a ClearCut export through the adapter and prove nothing was lost.

    Comparing the parsed timeline against the same file's JSON would be a tautology:
    both sides come from the same bytes, so any file agrees with itself. The real
    question is whether the official adapter preserves ClearCut's edit through a
    write and re-read, which is what an interchange claim actually rests on. Only
    ClearCut's own exports are round-tripped, so third-party OTIO stays readable.
    """
    metadata = root.get("metadata") or {}
    if OTIO_SCHEMA_METADATA_KEY not in metadata:
        return

    before = timeline_shape(timeline)
    reserialized = otio.adapters.write_to_string(timeline, "otio_json")
    after = timeline_shape(otio.adapters.read_from_string(reserialized, "otio_json"))
    if before != after:
        raise ValueError(
            f"{path}: the official adapter did not preserve this timeline across a "
            f"write and re-read.\n  wrote: {before}\n  read:  {after}"
        )


def validate_contract_metadata(root: dict, path: Path) -> None:
    """Validate ClearCut's optional metadata without rejecting third-party OTIO."""
    metadata = root.get("metadata") or {}
    declared_version = metadata.get(OTIO_SCHEMA_METADATA_KEY)
    declared_range = metadata.get(OTIO_ADAPTER_METADATA_KEY)
    if declared_version is None and declared_range is None:
        return
    if declared_version not in SUPPORTED_OTIO_SCHEMA_VERSIONS:
        raise ValueError(
            f"{path}: ClearCut OTIO schema version metadata must be one of "
            f"{sorted(SUPPORTED_OTIO_SCHEMA_VERSIONS)}, got {declared_version!r}"
        )
    if declared_range != OTIO_ADAPTER_RANGE:
        raise ValueError(
            f"{path}: ClearCut OTIO adapter range metadata must be {OTIO_ADAPTER_RANGE!r}, "
            f"got {declared_range!r}"
        )


def _rational_time(frames: float, rate: float) -> dict:
    return {"OTIO_SCHEMA": "RationalTime.1", "value": frames, "rate": rate}


def _time_range(start: float, duration: float, rate: float) -> dict:
    return {
        "OTIO_SCHEMA": "TimeRange.1",
        "start_time": _rational_time(start, rate),
        "duration": _rational_time(duration, rate),
    }


def clearcut_export_fixture(rate: float = 24.0) -> dict:
    """A timeline shaped like a real ClearCut export.

    The gate used to self-test against a Timeline whose stack had no children, which
    proved the adapter could load but nothing about ClearCut's actual output. This
    mirrors what `TimelineExchangeEngine.buildOtioStack` emits: a video track carrying
    a clip with an external reference, an intentional gap, a speed effect and a
    transition, plus a separate audio track.
    """
    def clip(name: str, effects: list) -> dict:
        return {
            "OTIO_SCHEMA": "Clip.1",
            "name": name,
            "effects": effects,
            "markers": [],
            "enabled": True,
            "source_range": _time_range(0, 48, rate),
            "media_reference": {
                "OTIO_SCHEMA": "ExternalReference.1",
                "name": name,
                "target_url": f"content://media/external/video/media/{name}",
                "available_range": _time_range(0, 240, rate),
                "metadata": {},
            },
            "metadata": {"clearcut_clip_id": name},
        }

    speed = {
        "OTIO_SCHEMA": "LinearTimeWarp.1",
        "name": "Speed 2.0x",
        "effect_name": "LinearTimeWarp",
        "time_scalar": 2.0,
        "metadata": {},
    }
    gap = {
        "OTIO_SCHEMA": "Gap.1",
        "name": "ClearCut gap",
        "effects": [],
        "markers": [],
        "enabled": True,
        "source_range": _time_range(0, 24, rate),
    }
    transition = {
        "OTIO_SCHEMA": "Transition.1",
        "name": "Cross Dissolve",
        "transition_type": "SMPTE_Dissolve",
        "in_offset": _rational_time(12, rate),
        "out_offset": _rational_time(12, rate),
        "metadata": {"clearcut_transition_role": "tail"},
    }

    return {
        "OTIO_SCHEMA": "Timeline.1",
        "name": "ClearCut OTIO gate fixture",
        "metadata": {
            "clearcut_version": "3.0.0",
            "export_format": "otio",
            OTIO_SCHEMA_METADATA_KEY: "0.15",
            OTIO_ADAPTER_METADATA_KEY: OTIO_ADAPTER_RANGE,
            "clearcut_timebase_numerator": 24,
            "clearcut_timebase_denominator": 1,
        },
        "tracks": {
            "OTIO_SCHEMA": "Stack.1",
            "name": "tracks",
            "children": [
                {
                    "OTIO_SCHEMA": "Track.1",
                    "name": "Track 1",
                    "kind": "Video",
                    "children": [clip("opening", []), gap, transition, clip("closing", [speed])],
                    "metadata": {"clearcut_track_type": "VIDEO"},
                },
                {
                    "OTIO_SCHEMA": "Track.1",
                    "name": "Track 2",
                    "kind": "Audio",
                    "children": [clip("voiceover", [])],
                    "metadata": {"clearcut_track_type": "AUDIO"},
                },
            ],
        },
    }


def self_test(otio) -> None:
    with tempfile.TemporaryDirectory(prefix="clearcut-otio-") as directory:
        path = Path(directory) / "fixture.otio"
        path.write_text(json.dumps(clearcut_export_fixture(), indent=2), encoding="utf-8")
        validate_file(otio, path)

        # Prove the round-trip comparison can actually fail. A gate that only ever
        # sees matching input is indistinguishable from one that asserts nothing, so
        # drop a track from the parsed side and require the shapes to disagree.
        timeline = otio.adapters.read_from_file(str(path), media_linker_name=None)
        intact = timeline_shape(timeline)
        del timeline.tracks[-1]
        if timeline_shape(timeline) == intact:
            raise AssertionError(
                "timeline_shape did not notice a dropped track, so the round-trip "
                "comparison would accept adapter data loss"
            )


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

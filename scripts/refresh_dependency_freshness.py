#!/usr/bin/env python3
"""Refresh the offline dependency-freshness snapshot from release metadata.

The version catalog remains deliberately pinned. This command only refreshes
the committed record of what the configured upstream repositories currently
publish and never edits ``gradle/libs.versions.toml``.

Usage:
    python scripts/refresh_dependency_freshness.py
    python scripts/refresh_dependency_freshness.py --reviewed-on 2026-08-03
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
SNAPSHOT = ROOT / "scripts" / "dependency_freshness_snapshot.json"
USER_AGENT = "ClearCut-dependency-freshness/1"
STABLE_VERSION = re.compile(r"^\d+(?:\.\d+)*$")


SOURCE_SPECS: dict[str, dict[str, str]] = {
    "agp": {
        "coordinate": "com.android.tools.build:gradle",
        "metadataSource": "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml",
        "source": "https://developer.android.com/build/releases/agp-9-3-0-release-notes",
    },
    "kotlin": {
        "coordinate": "org.jetbrains.kotlin:kotlin-gradle-plugin",
        "metadataSource": "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml",
        "source": "https://kotlinlang.org/docs/releases.html",
    },
    "ksp": {
        "coordinate": "com.google.devtools.ksp:symbol-processing-gradle-plugin",
        "metadataSource": "https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml",
        "source": "https://github.com/google/ksp/releases",
    },
    "composeBom": {
        "coordinate": "androidx.compose:compose-bom",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml",
        "source": "https://developer.android.com/develop/ui/compose/bom/bom-mapping",
    },
    "media3": {
        "coordinate": "androidx.media3:media3-exoplayer",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/media3",
    },
    "hilt": {
        "coordinate": "com.google.dagger:hilt-android",
        "metadataSource": "https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml",
        "source": "https://dagger.dev/releases/",
    },
    "room": {
        "coordinate": "androidx.room3:room3-runtime",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/room3/room3-runtime/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/room3",
    },
    "sqlite": {
        "coordinate": "androidx.sqlite:sqlite-framework",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-framework/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/sqlite",
    },
    "coreKtx": {
        "coordinate": "androidx.core:core-ktx",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/core",
    },
    "activity": {
        "coordinate": "androidx.activity:activity-compose",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/activity",
    },
    "coroutines": {
        "coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-android",
        "metadataSource": "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml",
        "source": "https://github.com/Kotlin/kotlinx.coroutines/releases",
    },
    "lifecycle": {
        "coordinate": "androidx.lifecycle:lifecycle-runtime-ktx",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    },
    "navigation": {
        "coordinate": "androidx.navigation:navigation-compose",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/navigation",
    },
    "coil": {
        "coordinate": "io.coil-kt.coil3:coil-compose",
        "metadataSource": "https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml",
        "source": "https://github.com/coil-kt/coil/releases",
    },
    "okhttp": {
        "coordinate": "com.squareup.okhttp3:okhttp",
        "metadataSource": "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml",
        "source": "https://square.github.io/okhttp/changelogs/changelog/",
    },
    "lottieCompose": {
        "coordinate": "com.airbnb.android:lottie-compose",
        "metadataSource": "https://repo1.maven.org/maven2/com/airbnb/android/lottie-compose/maven-metadata.xml",
        "source": "https://github.com/airbnb/lottie-android/releases",
    },
    "onnxruntime": {
        "coordinate": "com.microsoft.onnxruntime:onnxruntime-android",
        "metadataSource": "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/maven-metadata.xml",
        "source": "https://github.com/microsoft/onnxruntime/releases",
    },
    "mediapipe": {
        "coordinate": "com.google.mediapipe:tasks-vision",
        "metadataSource": "https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-vision/maven-metadata.xml",
        "source": "https://github.com/google-ai-edge/mediapipe/releases",
    },
    "protobufJavalite": {
        "coordinate": "com.google.protobuf:protobuf-javalite",
        "metadataSource": "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-javalite/maven-metadata.xml",
        "source": "https://github.com/protocolbuffers/protobuf/releases",
    },
    "robolectric": {
        "coordinate": "org.robolectric:robolectric",
        "metadataSource": "https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml",
        "source": "https://github.com/robolectric/robolectric/releases",
    },
    "androidxBenchmark": {
        "coordinate": "androidx.benchmark:benchmark-junit4",
        "metadataSource": "https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-junit4/maven-metadata.xml",
        "source": "https://developer.android.com/jetpack/androidx/releases/benchmark",
    },
}

LINT_SENSITIVE = {"agp", "kotlin", "ksp", "composeBom", "lifecycle"}

FACTS = {
    "agp_9_2_0": {
        "version": "9.2.0",
        "status": "stable",
        "source": "https://developer.android.com/build/releases/agp-9-2-0-release-notes",
        "requiresGradle": "9.4.1",
        "note": "AGP 9.2.0 is a stable release; its release notes require Gradle 9.4.1.",
    },
    "lifecycle_2_11_0": {
        "version": "2.11.0",
        "status": "stable",
        "source": "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        "requiresAgp": "9.2.0",
        "note": "Lifecycle 2.11.0 is stable and its release notes require AGP 9.2.0 for the Compose compileSdk update.",
    },
    "room3_0_0": {
        "version": "3.0.0",
        "status": "stable",
        "source": "https://developer.android.com/jetpack/androidx/releases/room3",
        "coordinate": "androidx.room3:room3-runtime",
        "note": "Room 3.0.0 is stable on the new androidx.room3 coordinate and is a migration, not a version-only edit to Room 2.x.",
    },
}


def parse_catalog() -> dict[str, str]:
    if not CATALOG.is_file():
        raise RuntimeError(f"Missing version catalog: {CATALOG}")
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
    missing = [key for key in SOURCE_SPECS if key not in versions]
    if missing:
        raise RuntimeError(f"Version catalog is missing tracked keys: {', '.join(missing)}")
    return versions


def stable_versions(metadata_url: str) -> list[str]:
    request = urllib.request.Request(metadata_url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            root = ET.fromstring(response.read())
    except Exception as exc:  # pragma: no cover - network failures are CLI errors
        raise RuntimeError(f"Could not read authoritative metadata {metadata_url}: {exc}") from exc
    values = {
        version.text.strip()
        for version in root.findall(".//version")
        if version.text and STABLE_VERSION.fullmatch(version.text.strip())
    }
    if not values:
        raise RuntimeError(f"Authoritative metadata has no stable versions: {metadata_url}")
    return sorted(values, key=lambda value: tuple(int(part) for part in value.split(".")))


def load_previous() -> dict[str, Any]:
    if not SNAPSHOT.is_file():
        return {}
    try:
        return json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Existing snapshot is invalid JSON: {SNAPSHOT}: {exc}") from exc


def probe_command(key: str, candidate: str) -> str:
    return f"python scripts/probe_dependency_upgrade.py --dependency {key} --version {candidate}"


def default_hold(key: str, pinned: str, latest: str) -> tuple[str, str]:
    command = probe_command(key, latest)
    if key == "agp":
        reason = (
            f"The catalog remains on AGP {pinned}; authoritative metadata reports {latest}, "
            "but the current wrapper/toolchain lane is not ready for the candidate's Gradle requirement."
        )
        unblock = (
            f"Update the wrapper and toolchain for {latest}, then run `{command}`; keep the "
            "catalog change only if the full local probe passes."
        )
    elif key == "lifecycle":
        reason = (
            f"Lifecycle {latest} is stable, but its Compose compileSdk path requires AGP 9.2.0 "
            "or newer and the candidate has not passed the local lint/build probe."
        )
        unblock = f"Move the AGP lane to the required version, then run `{command}` before changing this pin."
    elif key == "room":
        reason = (
            f"Room {latest} is on the new androidx.room3 coordinate with breaking API changes; "
            "a version-only catalog edit would not be a valid migration."
        )
        unblock = f"Complete the Room 3 coordinate/API migration, then run `{command}` before updating the catalog."
    elif key == "onnxruntime":
        reason = (
            f"ONNX Runtime {latest} is newer than the pinned {pinned}; the prior 1.28 evaluation increased the "
            "native payload and still lacked on-device Whisper, upscale, and inpaint comparison fixtures."
        )
        unblock = (
            f"Complete the per-ABI size and model-output review, then run `{command}` and retain the candidate "
            "only when the APK budget and on-device fixtures stay green."
        )
    elif key == "protobufJavalite":
        reason = (
            f"The pin stays on protobuf {pinned}; authoritative metadata reports {latest}, but MediaPipe's "
            "generated classes and the advisory floor have not been validated against that runtime."
        )
        unblock = (
            f"Move MediaPipe's generated-code floor first, then run `{command}` and the protobuf/MediaPipe "
            "fixtures before changing this pin."
        )
    else:
        reason = (
            f"The catalog pin {pinned} is below the {latest} stable version reported by authoritative metadata; "
            "no compatibility probe has approved the change."
        )
        unblock = f"Run `{command}` and retain the new pin only after the local compatibility probe passes."
    return reason, unblock


def state_for(key: str, pinned: str, latest: str) -> str:
    if key == "room" and "androidx-room3-runtime" not in CATALOG.read_text(encoding="utf-8"):
        return "migration-required"
    if pinned == latest:
        return "current"
    if key == "androidxBenchmark" and not STABLE_VERSION.fullmatch(pinned):
        return "pre-release"
    return "held"


def candidate_decision(key: str, pinned: str, latest: str, state: str) -> dict[str, str]:
    command = probe_command(key, latest)
    if key == "androidxBenchmark" and state == "pre-release":
        return {
            "action": "retain-beta",
            "candidateVersion": pinned,
            "releaseChannel": "beta",
            "stableAlternative": latest,
            "probe": command,
        }
    if state == "current":
        return {
            "action": "adopt",
            "candidateVersion": pinned,
            "releaseChannel": "stable",
            "probe": command,
        }
    return {
        "action": "hold",
        "candidateVersion": latest,
        "releaseChannel": "stable",
        "probe": command,
    }


def refresh(reviewed_on: str) -> dict[str, Any]:
    catalog = parse_catalog()
    previous = load_previous()
    previous_dependencies = previous.get("dependencies", {})
    dependencies: dict[str, Any] = {}
    for key, spec in SOURCE_SPECS.items():
        pinned = catalog[key]
        latest = stable_versions(spec["metadataSource"])[-1]
        state = state_for(key, pinned, latest)
        old = previous_dependencies.get(key, {})
        old_probe = old.get("compatibilityProbe", {})
        candidate_command = probe_command(key, latest)
        if old.get("pinnedVersion") == pinned and old_probe.get("version") == pinned:
            probe = dict(old_probe)
            probe["command"] = probe.get("command", candidate_command)
        else:
            probe = {
                "status": "baseline" if state == "current" else "not-run",
                "version": pinned if state == "current" else None,
                "command": candidate_command,
            }
        if old.get("pinnedVersion") == pinned and old.get("state") == state:
            reason = old.get("reason", "")
            unblock = old.get("unblockCondition", "")
        else:
            reason, unblock = default_hold(key, pinned, latest)
        if state == "current":
            reason = "The catalog pin matches the latest stable version returned by authoritative metadata."
            unblock = f"Run `python scripts/refresh_dependency_freshness.py` before changing this pin."
        elif state == "pre-release":
            reason = old.get("reason") or (
                f"The catalog intentionally uses pre-release {pinned}; stable metadata stops at {latest}."
            )
            unblock = old.get("unblockCondition") or (
                f"Wait for the stable 1.5.x benchmark line, then run `{candidate_command}` before changing this pin."
            )
        entry: dict[str, Any] = {
            "coordinate": spec["coordinate"],
            "pinnedVersion": pinned,
            "latestStable": latest,
            "source": spec["source"],
            "metadataSource": spec["metadataSource"],
            "reviewedOn": reviewed_on,
            "state": state,
            "reason": reason,
            "unblockCondition": unblock,
            "compatibilityProbe": probe,
            "candidateDecision": candidate_decision(key, pinned, latest, state),
        }
        if "latestCoordinate" in spec:
            entry["latestCoordinate"] = spec["latestCoordinate"]
        if key in LINT_SENSITIVE:
            entry["lintReviewRequired"] = True
        dependencies[key] = entry
    return {
        "schemaVersion": 1,
        "reviewedOn": reviewed_on,
        "refreshCommand": "python scripts/refresh_dependency_freshness.py",
        "probeCommand": "python scripts/probe_dependency_upgrade.py --dependency <key> --version <candidate>",
        "policy": {
            "catalogChangesRequireProbe": True,
            "probeTasks": [":app:testQaUnitTest", ":app:lintQa", ":app:assembleQa"],
            "dependencyVerification": "strict",
            "offlineGate": "DependencyFreshnessTest and LintDetectorRatchetTest",
        },
        "facts": {
            key: {**value, "reviewedOn": reviewed_on}
            for key, value in FACTS.items()
        },
        "dependencies": dependencies,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--reviewed-on",
        default=dt.date.today().isoformat(),
        help="ISO review date to write (default: today)",
    )
    args = parser.parse_args()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.reviewed_on):
        parser.error("--reviewed-on must be YYYY-MM-DD")
    try:
        snapshot = refresh(args.reviewed_on)
    except RuntimeError as exc:
        print(f"dependency freshness refresh failed: {exc}", file=sys.stderr)
        return 1
    temporary = SNAPSHOT.with_suffix(".tmp")
    temporary.write_text(json.dumps(snapshot, indent=2) + "\n", encoding="utf-8")
    temporary.replace(SNAPSHOT)
    print(f"refreshed {len(snapshot['dependencies'])} dependency lanes in {SNAPSHOT.relative_to(ROOT)}")
    for key, entry in snapshot["dependencies"].items():
        print(f"{key}: {entry['pinnedVersion']} (latest stable {entry['latestStable']}, {entry['state']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

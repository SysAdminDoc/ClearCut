#!/usr/bin/env python3
"""Generate ClearCut's baseline profile and record the managed-device metrics."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


DEFAULT_DEVICE = "pixel6Api37"
PROFILE_OUTPUT = Path("app/src/main/baseline-prof.txt")
METRICS_OUTPUT = Path("scripts/baseline_profile_metrics.json")


class BaselineProfileError(RuntimeError):
    """Raised when the managed profile/benchmark lane does not produce artifacts."""


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def resolve_gradle(root: Path, configured: str | None) -> Path:
    if configured:
        candidate = Path(configured)
        if not candidate.is_absolute():
            candidate = root / candidate
    else:
        candidate = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not candidate.is_file():
        raise BaselineProfileError(f"Gradle wrapper not found: {candidate}")
    return candidate


def run_gradle(
    root: Path,
    gradle: Path,
    device: str,
    log_path: Path,
    connected_serial: str | None,
) -> None:
    if connected_serial:
        tasks = [
            ":baselineprofile:connectedNonMinifiedReleaseAndroidTest",
            ":baselineprofile:connectedBenchmarkReleaseAndroidTest",
        ]
    else:
        tasks = [
            f":baselineprofile:{device}NonMinifiedReleaseAndroidTest",
            f":baselineprofile:{device}BenchmarkReleaseAndroidTest",
            ":baselineprofile:collectNonMinifiedReleaseBaselineProfile",
        ]
    command = [
        str(gradle),
        *tasks,
        "--no-daemon",
        "--max-workers=1",
        "--console=plain",
        "--rerun-tasks",
    ]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        completed = subprocess.run(
            command,
            cwd=root,
            stdout=log,
            stderr=subprocess.STDOUT,
            check=False,
            creationflags=creationflags,
            env={**os.environ, **({"ANDROID_SERIAL": connected_serial} if connected_serial else {})},
        )
    if completed.returncode != 0:
        raise BaselineProfileError(
            f"Gradle baseline lane failed with exit code {completed.returncode}; see {log_path}"
        )


def newest_artifact(root: Path, search_root: Path, pattern: str, started_at: float) -> Path:
    candidates = [
        path
        for path in search_root.rglob(pattern)
        if path.is_file() and path.stat().st_mtime >= started_at - 5
    ]
    if not candidates:
        raise BaselineProfileError(
            f"No fresh {pattern} artifact under {search_root}; the device test may not have run"
        )
    return max(candidates, key=lambda path: path.stat().st_mtime)


def metric_summary(metrics: dict[str, object]) -> dict[str, object]:
    summary: dict[str, object] = {}
    scalar_keys = ("minimum", "median", "maximum", "coefficientOfVariation", "P50", "P90", "P95", "P99")
    for name, value in metrics.items():
        if not isinstance(value, dict):
            continue
        selected = {key: value[key] for key in scalar_keys if key in value}
        if selected:
            summary[name] = selected
    return summary


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def collect_artifacts(
    root: Path,
    profile_source: Path,
    benchmark_source: Path,
    *,
    device: str,
    generated_at: str,
) -> None:
    profile_output = root / PROFILE_OUTPUT
    profile_output.parent.mkdir(parents=True, exist_ok=True)
    temporary_profile = profile_output.with_name(f".{profile_output.name}.tmp")
    shutil.copyfile(profile_source, temporary_profile)
    temporary_profile.replace(profile_output)

    profile_bytes = profile_output.read_bytes()
    benchmark = json.loads(benchmark_source.read_text(encoding="utf-8"))
    context = benchmark.get("context", {})
    build = context.get("build", {}) if isinstance(context, dict) else {}
    version = build.get("version", {}) if isinstance(build, dict) else {}
    compact_benchmarks = []
    for entry in benchmark.get("benchmarks", []):
        if not isinstance(entry, dict):
            continue
        compact_benchmarks.append(
            {
                "name": entry.get("name"),
                "iterations": entry.get("repeatIterations"),
                "metrics": metric_summary(entry.get("metrics", {})),
                "sampledMetrics": metric_summary(entry.get("sampledMetrics", {})),
            }
        )

    metrics = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "device": {
            "deviceName": device,
            "model": build.get("model"),
            "fingerprint": build.get("fingerprint"),
            "sdk": version.get("sdk"),
            "cpuCoreCount": context.get("cpuCoreCount"),
            "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
            "memTotalBytes": context.get("memTotalBytes"),
        },
        "profile": {
            "path": str(PROFILE_OUTPUT).replace("\\", "/"),
            "lineCount": len(profile_output.read_text(encoding="utf-8").splitlines()),
            "sha256": hashlib.sha256(profile_bytes).hexdigest(),
        },
        "benchmarks": compact_benchmarks,
    }
    write_json(root / METRICS_OUTPUT, metrics)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", default=DEFAULT_DEVICE, help=f"managed device name (default: {DEFAULT_DEVICE})")
    parser.add_argument(
        "--connected-serial",
        help="run the connected-device tasks for one already-booted AVD serial instead of the managed device",
    )
    parser.add_argument("--gradle", help="Gradle wrapper path, relative to the repository root")
    args = parser.parse_args()

    root = repository_root()
    gradle = resolve_gradle(root, args.gradle)
    started_at = time.time()
    log_path = root / "build" / "baseline-profile" / "gradle.log"
    try:
        run_gradle(root, gradle, args.device, log_path, args.connected_serial)
        output_root = root / "baselineprofile" / "build" / "outputs"
        additional_output = output_root / (
            "connected_android_test_additional_output" if args.connected_serial else "managed_device_android_test_additional_output"
        )
        profile_source = newest_artifact(
            root,
            additional_output,
            "*-baseline-prof.txt",
            started_at,
        )
        benchmark_source = newest_artifact(
            root,
            additional_output,
            "*benchmarkData.json",
            started_at,
        )
        generated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        collect_artifacts(
            root,
            profile_source,
            benchmark_source,
            device=args.connected_serial or args.device,
            generated_at=generated_at,
        )
    except (BaselineProfileError, OSError, json.JSONDecodeError) as error:
        print(f"baseline profile generation failed: {error}", file=sys.stderr)
        return 2

    print(f"baseline profile written to {PROFILE_OUTPUT}")
    print(f"metrics written to {METRICS_OUTPUT}")
    print(f"Gradle output captured in {log_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

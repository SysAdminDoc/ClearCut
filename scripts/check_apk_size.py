#!/usr/bin/env python3
"""Check generated APK sizes against a committed budget baseline."""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_ROOT = ROOT / "app" / "build" / "outputs" / "apk"
BASELINE = ROOT / "scripts" / "apk_size_baseline.json"
SCHEMA = "com.novacut.apk-size-budget.v1"


class SizeBudgetError(RuntimeError):
    pass


def read_baseline(path: Path = BASELINE) -> dict:
    if not path.is_file():
        raise SizeBudgetError(f"missing APK size baseline: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != SCHEMA:
        raise SizeBudgetError(f"{path} has unexpected schema")
    if not isinstance(data.get("outputs"), dict):
        raise SizeBudgetError(f"{path} must contain an outputs object")
    return data


def output_path(relative: str, apk_root: Path = APK_ROOT) -> Path:
    return apk_root / relative


def check_sizes(baseline_path: Path = BASELINE, apk_root: Path = APK_ROOT) -> list[str]:
    baseline = read_baseline(baseline_path)
    max_growth = int(baseline.get("maxGrowthBytes", 0))
    if max_growth < 0:
        raise SizeBudgetError("maxGrowthBytes must be non-negative")
    report: list[str] = []
    for relative, spec in sorted(baseline["outputs"].items()):
        apk = output_path(relative, apk_root)
        if not apk.is_file():
            raise SizeBudgetError(f"missing APK output: {apk}")
        baseline_bytes = int(spec.get("baselineBytes", -1))
        if baseline_bytes <= 0:
            raise SizeBudgetError(f"{relative} baselineBytes must be positive")
        actual = apk.stat().st_size
        max_allowed = baseline_bytes + max_growth
        delta = actual - baseline_bytes
        report.append(f"{relative}: {actual} bytes ({delta:+} vs baseline, max {max_allowed})")
        if actual > max_allowed:
            raise SizeBudgetError(
                f"{relative} is {actual} bytes, above budget {max_allowed} "
                f"(baseline {baseline_bytes}, allowed growth {max_growth})"
            )
    return report


def update_baseline(baseline_path: Path = BASELINE, apk_root: Path = APK_ROOT) -> None:
    baseline = read_baseline(baseline_path)
    for relative, spec in sorted(baseline["outputs"].items()):
        apk = output_path(relative, apk_root)
        if not apk.is_file():
            raise SizeBudgetError(f"missing APK output: {apk}")
        spec["baselineBytes"] = apk.stat().st_size
    baseline_path.write_text(json.dumps(baseline, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        apk_root = root / "apk"
        baseline = root / "baseline.json"
        apk = apk_root / "release" / "app-release.apk"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"0" * 100)
        baseline.write_text(
            json.dumps(
                {
                    "schema": SCHEMA,
                    "maxGrowthBytes": 5,
                    "outputs": {"release/app-release.apk": {"baselineBytes": 100}},
                }
            ),
            encoding="utf-8",
        )
        check_sizes(baseline, apk_root)
        apk.write_bytes(b"0" * 106)
        try:
            check_sizes(baseline, apk_root)
        except SizeBudgetError:
            pass
        else:
            raise SizeBudgetError("self-test expected oversized APK to fail")
        update_baseline(baseline, apk_root)
        check_sizes(baseline, apk_root)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--update-baseline", action="store_true", help="rewrite baselineBytes from current APK outputs")
    parser.add_argument("--self-test", action="store_true", help="run built-in size-budget fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("APK size budget self-tests passed.")
            return 0
        if args.update_baseline:
            update_baseline()
            print(f"updated APK size baseline: {BASELINE.relative_to(ROOT)}")
            return 0
        report = check_sizes()
    except SizeBudgetError as error:
        print(f"APK size budget check failed: {error}", file=sys.stderr)
        return 1
    print("APK size budget verified.")
    for line in report:
        print(f"  - {line}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

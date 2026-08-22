#!/usr/bin/env python3
"""Run and classify ClearCut's API 37 managed-device QA baseline."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

BASELINE_NAME = "api37-qa"
DEVICE_NAME = "clearCutApi37"
GRADLE_TASK = ":app:clearCutApi37QaAndroidTest"

EXPECTED_TESTS = frozenset(
    {
        "com.novacut.editor.ClearCutSmokeTest.highContrastPhoneAndDesktopEditorSurfacesRender",
        "com.novacut.editor.ClearCutSmokeTest.projectEditorExportAndSettingsSurfacesOpen",
        "com.novacut.editor.ClearCutSmokeTest.pseudoLocalesRenderExpandedAndRtlExportSurfaces",
        "com.novacut.editor.LargeTextLayoutTest.fontScale200_desktopSurfacesRemainUsable",
        "com.novacut.editor.LargeTextLayoutTest.fontScale200_largeScreenSurfacesRemainUsable",
        "com.novacut.editor.LargeTextLayoutTest.fontScale200_phoneSurfacesRemainUsable",
        "com.novacut.editor.LargeTextLayoutTest.fontScale300_desktopSurfacesRemainUsable",
        "com.novacut.editor.LargeTextLayoutTest.fontScale300_largeScreenSurfacesRemainUsable",
        "com.novacut.editor.LargeTextLayoutTest.fontScale300_phoneSurfacesRemainUsable",
        "com.novacut.editor.engine.AudioOnlyExportContractTest.audioOnlyExportProducesTrackVerifiedM4a",
        "com.novacut.editor.engine.AudioOnlyExportContractTest.verifierRejectsRequestedVideoCodecMismatch",
        "com.novacut.editor.engine.AudioOnlyExportContractTest.verifierRejectsVideoOutputWhenAudioOnlyExpected",
        "com.novacut.editor.engine.AudioRenderGoldenInstrumentationTest.panAndDspRemainDeterministicAcrossMedia3Buffers",
        "com.novacut.editor.engine.FailureClassDeviceAcceptanceTest.failureClassFixturesStayWithinDeviceContracts",
        "com.novacut.editor.engine.InpaintingDeviceAcceptanceTest.stillAndAudioVideoObjectRemovalProduceUsableOutputs",
        "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.batchSourceRangeExportsOnlyTheQueuedSourceInterval",
        "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.constantFrameRateExportNormalizesVariableInputCadence",
        "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.speedExportRemainsValidAndDoesNotAdvertiseBalloonedFrameRate",
        "com.novacut.editor.engine.Media3TrimOptimizationInstrumentationTest.pureTrimOutputPassesTheExistingVerifier",
        "com.novacut.editor.engine.OnnxSessionFactoryInstrumentationTest.xnnpackRegistrationProbeIsSafeAndReportsCapability",
        "com.novacut.editor.engine.PreviewCompositionPlayerTest.gapOnlyCompositionReplacesStaleVisualContent",
        "com.novacut.editor.engine.PreviewCompositionPlayerTest.trimmedAndExtendedCompositionsDecodeTheirBoundaryFrames",
        "com.novacut.editor.engine.PreviewCompositionPlayerTest.twoVisualSequencesPrepareAndSeekOnOneAbsoluteTimeline",
        "com.novacut.editor.engine.StabilizationDeviceAcceptanceTest.analysisCancellationStopsWithoutProducingAResult",
        "com.novacut.editor.engine.StabilizationDeviceAcceptanceTest.analysisProducesReversibleMotionDataAndSharedKeyframes",
        "com.novacut.editor.engine.db.ProjectDatabaseMigrationTest.committedSchemaVersionsMigrateToCurrentWithoutProjectLoss",
        "com.novacut.editor.ui.editor.MediaStoreExportClassificationInstrumentationTest.publishedMp4IsVideoWithDurationAndTimeMetadata",
    }
)


@dataclass(frozen=True)
class KnownAssumption:
    classification: str
    assumption: str
    required_fragments: tuple[str, ...]


KNOWN_FAILURES = {
    "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.batchSourceRangeExportsOnlyTheQueuedSourceInterval": KnownAssumption(
        "emulator-codec",
        "The API 37 goldfish H.264 decoder fails while Media3 reads the generated AVC fixture.",
        ("Codec exception", "c2.goldfish.h264.decoder"),
    ),
    "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.constantFrameRateExportNormalizesVariableInputCadence": KnownAssumption(
        "emulator-codec",
        "The API 37 managed image's MediaCodec encoder rejects the CFR pre-render frame.",
        (
            "Constant frame-rate normalization failed",
            "c2.android.avc.encoder",
            "Error submitting video frame to the encoder",
            "Error flushing encoder: Try again",
        ),
    ),
    "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest.speedExportRemainsValidAndDoesNotAdvertiseBalloonedFrameRate": KnownAssumption(
        "emulator-codec",
        "The API 37 goldfish H.264 decoder fails while Media3 reads the generated AVC fixture.",
        ("Codec exception", "c2.goldfish.h264.decoder"),
    ),
    "com.novacut.editor.engine.Media3TrimOptimizationInstrumentationTest.pureTrimOutputPassesTheExistingVerifier": KnownAssumption(
        "emulator-codec",
        "The API 37 goldfish H.264 decoder fails before the trim verifier can inspect its output.",
        ("Codec exception", "c2.goldfish.h264.decoder"),
    ),
    "com.novacut.editor.engine.PreviewCompositionPlayerTest.trimmedAndExtendedCompositionsDecodeTheirBoundaryFrames": KnownAssumption(
        "emulator-preview",
        "The API 37 goldfish player cannot decode the preview fixture's requested boundary frame.",
        ("trimmed preview failed", "error from player 0"),
    ),
    "com.novacut.editor.engine.PreviewCompositionPlayerTest.twoVisualSequencesPrepareAndSeekOnOneAbsoluteTimeline": KnownAssumption(
        "emulator-preview",
        "The API 37 goldfish player reports three preview errors while preparing two visual sequences.",
        ("expected:<0> but was:<3>",),
    ),
}

KNOWN_SKIPS = {
    "com.novacut.editor.engine.InpaintingDeviceAcceptanceTest.stillAndAudioVideoObjectRemovalProduceUsableOutputs": (
        "The optional 174 MiB LaMa model is not installed in the clean managed-device image."
    )
}


@dataclass(frozen=True)
class TestResult:
    test_id: str
    status: str
    detail: str


def parse_results(path: Path) -> list[TestResult]:
    root = ElementTree.parse(path).getroot()
    results: list[TestResult] = []
    for testcase in root.findall(".//testcase"):
        class_name = testcase.attrib.get("classname", "")
        test_name = testcase.attrib.get("name", "")
        test_id = f"{class_name}.{test_name}"
        failure = testcase.find("failure")
        error = testcase.find("error")
        skipped = testcase.find("skipped")
        problem = failure if failure is not None else error
        if problem is not None:
            detail_parts = [
                part.strip()
                for part in (
                    problem.attrib.get("message", ""),
                    "".join(problem.itertext()),
                )
                if part.strip()
            ]
            logcat_path = path.parent / f"logcat-{class_name}-{test_name}.txt"
            if logcat_path.is_file():
                detail_parts.append(
                    logcat_path.read_text(encoding="utf-8", errors="replace")
                )
            detail = " ".join(detail_parts)
            status = "failed"
        elif skipped is not None:
            detail = " ".join(skipped.itertext()).strip()
            status = "skipped"
        else:
            detail = ""
            status = "passed"
        results.append(TestResult(test_id=test_id, status=status, detail=detail))
    return results


def evaluate(
    results: list[TestResult],
    *,
    expected_tests: frozenset[str] = EXPECTED_TESTS,
    known_failures: dict[str, KnownAssumption] = KNOWN_FAILURES,
    known_skips: dict[str, str] = KNOWN_SKIPS,
) -> dict[str, object]:
    observed = {result.test_id for result in results}
    missing = sorted(expected_tests - observed)
    duplicate_ids = sorted(
        test_id
        for test_id in observed
        if sum(result.test_id == test_id for result in results) > 1
    )
    regressions: list[dict[str, str]] = []
    classified: list[dict[str, str]] = []

    for result in sorted(results, key=lambda candidate: candidate.test_id):
        entry = {"id": result.test_id, "status": result.status}
        if result.status == "failed":
            assumption = known_failures.get(result.test_id)
            if assumption is not None and all(
                fragment in result.detail for fragment in assumption.required_fragments
            ):
                entry.update(
                    status="known-assumption",
                    classification=assumption.classification,
                    assumption=assumption.assumption,
                )
            else:
                entry["detail"] = result.detail[:2000]
                regressions.append(entry.copy())
        elif result.status == "skipped":
            reason = known_skips.get(result.test_id)
            if reason is not None:
                entry.update(
                    status="known-skip",
                    classification="optional-fixture",
                    assumption=reason,
                )
            else:
                entry["detail"] = result.detail[:2000]
                regressions.append(entry.copy())
        elif result.test_id in known_failures:
            entry["classification"] = "resolved-assumption"
        classified.append(entry)

    if missing:
        regressions.append(
            {
                "id": "baseline-manifest",
                "status": "missing-tests",
                "detail": ", ".join(missing),
            }
        )
    if duplicate_ids:
        regressions.append(
            {
                "id": "result-integrity",
                "status": "duplicate-tests",
                "detail": ", ".join(duplicate_ids),
            }
        )

    known_count = sum(
        entry["status"] in {"known-assumption", "known-skip"} for entry in classified
    )
    outcome = (
        "regression"
        if regressions
        else "pass-with-assumptions"
        if known_count
        else "green"
    )
    return {
        "schemaVersion": 1,
        "baseline": BASELINE_NAME,
        "device": {
            "name": DEVICE_NAME,
            "apiLevel": 37,
            "profile": "Pixel 6",
            "systemImageSource": "google",
        },
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "outcome": outcome,
        "summary": {
            "observed": len(results),
            "passed": sum(result.status == "passed" for result in results),
            "knownAssumptions": sum(
                entry["status"] == "known-assumption" for entry in classified
            ),
            "knownSkips": sum(entry["status"] == "known-skip" for entry in classified),
            "regressions": len(regressions),
        },
        "missingBaselineTests": missing,
        "tests": classified,
        "regressions": regressions,
    }


def write_report(
    report: dict[str, object], report_directory: Path, source: Path
) -> None:
    report_directory.mkdir(parents=True, exist_ok=True)
    json_path = report_directory / f"{BASELINE_NAME}.json"
    text_path = report_directory / f"{BASELINE_NAME}.txt"
    payload = dict(report)
    payload["source"] = str(source.resolve())
    json_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    summary = report["summary"]
    assert isinstance(summary, dict)
    lines = [
        f"ClearCut API 37 QA baseline: {str(report['outcome']).upper()}",
        f"Observed: {summary['observed']}",
        f"Passed: {summary['passed']}",
        f"Known assumptions: {summary['knownAssumptions']}",
        f"Known skips: {summary['knownSkips']}",
        f"Regressions: {summary['regressions']}",
        "",
    ]
    tests = report["tests"]
    assert isinstance(tests, list)
    for entry in tests:
        assert isinstance(entry, dict)
        line = f"[{str(entry['status']).upper()}] {entry['id']}"
        if entry.get("assumption"):
            line += f": {entry['assumption']}"
        lines.append(line)
    text_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(text_path.read_text(encoding="utf-8"), end="")
    print(f"JSON report: {json_path}")


def find_result(root: Path) -> Path:
    result_directory = (
        root
        / "app"
        / "build"
        / "outputs"
        / "androidTest-results"
        / "managedDevice"
        / "qa"
        / DEVICE_NAME
    )
    candidates = list(result_directory.glob("TEST-*.xml"))
    if not candidates:
        raise FileNotFoundError(
            f"managed-device JUnit result not found under {result_directory}"
        )
    return max(candidates, key=lambda candidate: candidate.stat().st_mtime_ns)


def java_major(home: Path) -> int | None:
    release_file = home / "release"
    if not release_file.is_file():
        return None
    for line in release_file.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("JAVA_VERSION="):
            value = line.partition("=")[2].strip().strip('"')
            first = value.split(".", maxsplit=1)[0]
            return int(first) if first.isdigit() else None
    return None


def build_environment() -> dict[str, str]:
    environment = os.environ.copy()
    java_candidates: list[Path] = []
    if environment.get("JAVA_HOME"):
        java_candidates.append(Path(environment["JAVA_HOME"]))
    if os.name == "nt":
        java_candidates.extend(
            sorted(
                Path("C:/Program Files/Eclipse Adoptium").glob("jdk-21*"),
                reverse=True,
            )
        )
    java_home = next(
        (candidate for candidate in java_candidates if java_major(candidate) == 21),
        None,
    )
    if java_home is None:
        raise RuntimeError("JDK 21 not found; set JAVA_HOME to a JDK 21 installation")
    environment["JAVA_HOME"] = str(java_home)

    if not environment.get("ANDROID_HOME") and not environment.get("ANDROID_SDK_ROOT"):
        default_sdk = Path.home() / "AppData" / "Local" / "Android" / "Sdk"
        if default_sdk.is_dir():
            environment["ANDROID_HOME"] = str(default_sdk)
    return environment


def run_gradle(root: Path) -> int:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [
        str(wrapper),
        GRADLE_TASK,
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
    ]
    return subprocess.run(
        command, cwd=root, env=build_environment(), check=False
    ).returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-run",
        action="store_true",
        help="classify the latest existing JUnit result",
    )
    parser.add_argument(
        "--root", type=Path, default=Path(__file__).resolve().parents[1]
    )
    parser.add_argument("--results", type=Path, help="classify this JUnit XML result")
    parser.add_argument(
        "--report-dir", type=Path, help="override the generated report directory"
    )
    args = parser.parse_args()

    root = args.root.resolve()
    if not args.skip_run and args.results is None:
        prior_directory = (
            root
            / "app"
            / "build"
            / "outputs"
            / "androidTest-results"
            / "managedDevice"
            / "qa"
            / DEVICE_NAME
        )
        for prior in prior_directory.glob("TEST-*.xml"):
            prior.unlink()
        run_gradle(root)

    try:
        result_path = args.results.resolve() if args.results else find_result(root)
        report = evaluate(parse_results(result_path))
    except (FileNotFoundError, ElementTree.ParseError, OSError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    report_directory = (
        args.report_dir or root / "app" / "build" / "reports" / "connected-qa"
    )
    write_report(report, report_directory, result_path)
    return 1 if report["outcome"] == "regression" else 0


if __name__ == "__main__":
    raise SystemExit(main())

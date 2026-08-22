from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import run_api37_qa


def write_results(directory: str, cases: str) -> Path:
    path = Path(directory) / "TEST-device.xml"
    path.write_text(f'<testsuite tests="1">{cases}</testsuite>', encoding="utf-8")
    return path


class Api37QaClassifierTest(unittest.TestCase):
    def test_baseline_includes_large_text_viewport_matrix(self) -> None:
        large_text_tests = {
            test_id
            for test_id in run_api37_qa.EXPECTED_TESTS
            if ".LargeTextLayoutTest." in test_id
        }

        self.assertEqual(len(run_api37_qa.EXPECTED_TESTS), 27)
        self.assertEqual(len(large_text_tests), 6)

    def test_known_failure_is_reported_without_becoming_a_regression(self) -> None:
        test_id = "example.DeviceTest.codecFixture"
        assumption = run_api37_qa.KnownAssumption(
            classification="emulator-codec",
            assumption="The emulator codec rejects this fixture.",
            required_fragments=("Codec exception", "goldfish"),
        )
        with tempfile.TemporaryDirectory() as directory:
            results = run_api37_qa.parse_results(
                write_results(
                    directory,
                    '<testcase classname="example.DeviceTest" name="codecFixture">'
                    '<failure message="Codec exception">goldfish decoder</failure>'
                    "</testcase>",
                )
            )

        report = run_api37_qa.evaluate(
            results,
            expected_tests=frozenset({test_id}),
            known_failures={test_id: assumption},
            known_skips={},
        )

        self.assertEqual(report["outcome"], "pass-with-assumptions")
        self.assertEqual(report["summary"]["regressions"], 0)
        self.assertEqual(report["tests"][0]["status"], "known-assumption")

    def test_changed_failure_signature_is_a_regression(self) -> None:
        test_id = "example.DeviceTest.codecFixture"
        assumption = run_api37_qa.KnownAssumption(
            classification="emulator-codec",
            assumption="Known decoder limitation.",
            required_fragments=("goldfish",),
        )
        with tempfile.TemporaryDirectory() as directory:
            results = run_api37_qa.parse_results(
                write_results(
                    directory,
                    '<testcase classname="example.DeviceTest" name="codecFixture">'
                    '<failure message="New crash">unexpected renderer failure</failure>'
                    "</testcase>",
                )
            )

        report = run_api37_qa.evaluate(
            results,
            expected_tests=frozenset({test_id}),
            known_failures={test_id: assumption},
            known_skips={},
        )

        self.assertEqual(report["outcome"], "regression")
        self.assertEqual(report["summary"]["regressions"], 1)

    def test_cfr_assumption_requires_the_recorded_encoder_fingerprint(self) -> None:
        test_id = (
            "com.novacut.editor.engine.Media3ExportRobustnessInstrumentationTest."
            "constantFrameRateExportNormalizesVariableInputCadence"
        )
        with tempfile.TemporaryDirectory() as directory:
            result_path = write_results(
                directory,
                '<testcase classname="com.novacut.editor.engine.'
                'Media3ExportRobustnessInstrumentationTest" '
                'name="constantFrameRateExportNormalizesVariableInputCadence">'
                '<failure message="CFR export reported an error">'
                "Constant frame-rate normalization failed"
                "</failure></testcase>",
            )
            (Path(directory) / (
                "logcat-com.novacut.editor.engine."
                "Media3ExportRobustnessInstrumentationTest-"
                "constantFrameRateExportNormalizesVariableInputCadence.txt"
            )).write_text(
                "c2.android.avc.encoder\n"
                "Error submitting video frame to the encoder\n"
                "Error flushing encoder: Try again\n",
                encoding="utf-8",
            )
            results = run_api37_qa.parse_results(result_path)

        report = run_api37_qa.evaluate(
            results,
            expected_tests=frozenset({test_id}),
            known_failures={test_id: run_api37_qa.KNOWN_FAILURES[test_id]},
            known_skips={},
        )

        self.assertEqual(report["outcome"], "pass-with-assumptions")
        self.assertEqual(report["summary"]["knownAssumptions"], 1)

    def test_missing_baseline_test_is_a_regression(self) -> None:
        report = run_api37_qa.evaluate(
            [],
            expected_tests=frozenset({"example.DeviceTest.requiredFixture"}),
            known_failures={},
            known_skips={},
        )

        self.assertEqual(report["outcome"], "regression")
        self.assertEqual(
            report["missingBaselineTests"], ["example.DeviceTest.requiredFixture"]
        )


if __name__ == "__main__":
    unittest.main()

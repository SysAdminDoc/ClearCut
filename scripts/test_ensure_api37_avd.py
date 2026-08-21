from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from subprocess import CompletedProcess
from unittest.mock import patch

import ensure_api37_avd


class ConfigureDataPartitionTest(unittest.TestCase):
    def test_replaces_small_partition_and_removes_duplicate_settings(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            avd_root = Path(directory)
            config_dir = avd_root / "test-device.avd"
            config_dir.mkdir()
            config_path = config_dir / "config.ini"
            config_path.write_text(
                "hw.ramSize=1536M\n"
                "disk.dataPartition.size=800M\n"
                "disk.dataPartition.size=1G\n",
                encoding="utf-8",
            )

            with patch.dict(os.environ, {"ANDROID_AVD_HOME": directory}):
                ensure_api37_avd.configure_data_partition("test-device")

            contents = config_path.read_text(encoding="utf-8")
            self.assertIn(f"hw.ramSize={ensure_api37_avd.RAM_SIZE_MB}M\n", contents)
            self.assertNotIn("hw.ramSize=1536M", contents)
            self.assertEqual(
                contents.count(
                    f"disk.dataPartition.size={ensure_api37_avd.DATA_PARTITION_SIZE_MB}M"
                ),
                1,
            )
            self.assertNotIn("disk.dataPartition.size=800M", contents)

    def test_connected_serial_matches_the_requested_avd(self) -> None:
        responses = {
            (
                "adb",
                "devices",
            ): "List of devices attached\nemulator-5554\tdevice\nemulator-5556\tdevice\n",
            ("adb", "-s", "emulator-5554", "emu", "avd", "name"): "other-avd\nOK\n",
            (
                "adb",
                "-s",
                "emulator-5556",
                "emu",
                "avd",
                "name",
            ): "clearcut-api37-ps16k\nOK\n",
        }

        def fake_run(command: list[str], **_: object) -> CompletedProcess[str]:
            key = tuple(command)
            return CompletedProcess(command, 0, responses[key], "")

        with patch.object(ensure_api37_avd, "run", side_effect=fake_run):
            serial = ensure_api37_avd.connected_serial(
                Path("adb"), "clearcut-api37-ps16k"
            )

        self.assertEqual(serial, "emulator-5556")

    def test_device_ready_requires_settings_provider(self) -> None:
        successful = CompletedProcess(["adb"], 0, "Service activity: found\n", "")
        missing_settings = CompletedProcess(
            ["adb"], 20, "", "Can't find service: settings"
        )

        with (
            patch.object(ensure_api37_avd, "boot_completed", return_value=True),
            patch.object(
                ensure_api37_avd.subprocess,
                "run",
                side_effect=[successful, successful, missing_settings],
            ),
        ):
            self.assertFalse(
                ensure_api37_avd.device_ready(Path("adb"), "emulator-5554")
            )


if __name__ == "__main__":
    unittest.main()

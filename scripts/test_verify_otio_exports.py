import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify_otio_exports.py")
SPEC = importlib.util.spec_from_file_location("verify_otio_exports", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class VerifyOtioExportsTest(unittest.TestCase):
    def test_third_party_otio_without_clearcut_metadata_is_allowed(self):
        MODULE.validate_contract_metadata(
            {"OTIO_SCHEMA": MODULE.OTIO_ROOT_SCHEMA, "metadata": {}},
            Path("third-party.otio"),
        )

    def test_clearcut_contract_metadata_is_exact(self):
        MODULE.validate_contract_metadata(
            {
                "metadata": {
                    MODULE.OTIO_SCHEMA_METADATA_KEY: "0.16",
                    MODULE.OTIO_ADAPTER_METADATA_KEY: MODULE.OTIO_ADAPTER_RANGE,
                }
            },
            Path("clearcut.otio"),
        )

    def test_future_clearcut_schema_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "schema version"):
            MODULE.validate_contract_metadata(
                {
                    "metadata": {
                        MODULE.OTIO_SCHEMA_METADATA_KEY: "0.17",
                        MODULE.OTIO_ADAPTER_METADATA_KEY: MODULE.OTIO_ADAPTER_RANGE,
                    }
                },
                Path("future.otio"),
            )

    def test_mismatched_adapter_range_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "adapter range"):
            MODULE.validate_contract_metadata(
                {
                    "metadata": {
                        MODULE.OTIO_SCHEMA_METADATA_KEY: "0.15",
                        MODULE.OTIO_ADAPTER_METADATA_KEY: "0.15-0.15",
                    }
                },
                Path("mismatch.otio"),
            )


if __name__ == "__main__":
    unittest.main()

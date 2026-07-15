#!/usr/bin/env python3
"""
Validate ClearCut release inputs and generated APK metadata.

This script intentionally uses only repository files plus AGP output metadata
so CI does not rely on local-only signing files or workstation state.
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_ROOT = ROOT / "app" / "build" / "outputs" / "apk"


class VerificationError(RuntimeError):
    pass


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_gradle_version() -> tuple[int, str]:
    text = read_text(ROOT / "app" / "build.gradle.kts")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise VerificationError("Could not parse versionCode/versionName from app/build.gradle.kts")
    return int(code_match.group(1)), name_match.group(1)


def verify_repository_metadata(version_code: int, version_name: str) -> None:
    from release_identity import ReleaseIdentityError, verify_release_identity

    del version_code, version_name
    try:
        verify_release_identity(ROOT)
    except ReleaseIdentityError as error:
        raise VerificationError(str(error)) from error

    gitignore = read_text(ROOT / ".gitignore")
    for private_input in ("keystore.properties", "*.jks", "*.keystore"):
        if private_input not in gitignore:
            raise VerificationError(f".gitignore must keep {private_input} out of the repository")


def verify_github_tag(version_name: str) -> None:
    ref_type = os.environ.get("GITHUB_REF_TYPE")
    ref_name = os.environ.get("GITHUB_REF_NAME")
    if ref_type == "tag" and ref_name != f"v{version_name}":
        raise VerificationError(f"Tag {ref_name!r} does not match Gradle version v{version_name}")


def read_output_metadata(variant_path: Path) -> dict:
    metadata_path = variant_path / "output-metadata.json"
    if not metadata_path.is_file():
        raise VerificationError(f"Missing APK output metadata: {metadata_path.relative_to(ROOT)}")
    return json.loads(read_text(metadata_path))


def verify_variant_apk(variant: str, version_code: int, version_name: str) -> Path:
    variant_path = APK_ROOT / variant
    metadata = read_output_metadata(variant_path)
    elements = metadata.get("elements")
    if not isinstance(elements, list) or len(elements) != 1:
        raise VerificationError(f"{variant} metadata must contain exactly one APK element")

    element = elements[0]
    if element.get("versionCode") != version_code:
        raise VerificationError(f"{variant} versionCode mismatch: {element.get('versionCode')} != {version_code}")
    if element.get("versionName") != version_name:
        raise VerificationError(f"{variant} versionName mismatch: {element.get('versionName')!r} != {version_name!r}")

    apk_name = element.get("outputFile")
    if not isinstance(apk_name, str) or not apk_name.endswith(".apk"):
        raise VerificationError(f"{variant} metadata does not point at an APK output")

    apk_path = variant_path / apk_name
    if not apk_path.is_file():
        raise VerificationError(f"Missing {variant} APK: {apk_path.relative_to(ROOT)}")
    if apk_path.stat().st_size <= 0:
        raise VerificationError(f"{variant} APK is empty: {apk_path.relative_to(ROOT)}")
    return apk_path


def verify_android_test_apk() -> Path:
    variant_path = APK_ROOT / "androidTest" / "debug"
    metadata = read_output_metadata(variant_path)
    elements = metadata.get("elements")
    if not isinstance(elements, list) or len(elements) != 1:
        raise VerificationError("debugAndroidTest metadata must contain exactly one APK element")
    apk_name = elements[0].get("outputFile")
    if not isinstance(apk_name, str) or not apk_name.endswith(".apk"):
        raise VerificationError("debugAndroidTest metadata does not point at an APK output")
    apk_path = variant_path / apk_name
    if not apk_path.is_file() or apk_path.stat().st_size <= 0:
        raise VerificationError(f"debugAndroidTest APK missing or empty: {apk_path.relative_to(ROOT)}")
    return apk_path


def main() -> int:
    try:
        version_code, version_name = parse_gradle_version()
        verify_repository_metadata(version_code, version_name)
        verify_github_tag(version_name)
        outputs = [
            verify_variant_apk("debug", version_code, version_name),
            verify_variant_apk("release", version_code, version_name),
            verify_android_test_apk(),
        ]
    except VerificationError as error:
        print(f"release verification failed: {error}", file=sys.stderr)
        return 1

    print(f"ClearCut v{version_name} (versionCode {version_code}) release metadata verified.")
    for apk in outputs:
        print(f"  - {apk.relative_to(ROOT)} ({apk.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

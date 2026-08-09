#!/usr/bin/env python3
"""Validate ClearCut release inputs, generated APKs, and local trust sidecars."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
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


def verify_repository_metadata() -> None:
    from release_identity import ReleaseIdentityError, verify_release_identity

    try:
        verify_release_identity(ROOT)
    except ReleaseIdentityError as error:
        raise VerificationError(str(error)) from error

    gitignore = read_text(ROOT / ".gitignore")
    for private_input in ("keystore.properties", "*.jks", "*.keystore"):
        if private_input not in gitignore:
            raise VerificationError(f".gitignore must keep {private_input} out of the repository")


def read_output_metadata(variant_path: Path) -> dict:
    metadata_path = variant_path / "output-metadata.json"
    if not metadata_path.is_file():
        raise VerificationError(f"Missing APK output metadata: {metadata_path.relative_to(ROOT)}")
    return json.loads(read_text(metadata_path))


# ABI splits are enabled, so a variant produces one universal APK plus one per
# supported ABI. Every one of them is a publishable artifact and every one has to
# carry the same release identity.
EXPECTED_ABIS = ("armeabi-v7a", "arm64-v8a", "x86", "x86_64")


def verify_variant_apk(variant: str, version_code: int, version_name: str) -> list[Path]:
    variant_path = APK_ROOT / variant
    metadata = read_output_metadata(variant_path)
    elements = metadata.get("elements")
    if not isinstance(elements, list) or not elements:
        raise VerificationError(f"{variant} metadata must contain at least one APK element")

    apk_paths: list[Path] = []
    seen_abis: set[str] = set()
    universal_count = 0
    for element in elements:
        if element.get("versionCode") != version_code:
            raise VerificationError(
                f"{variant} versionCode mismatch: {element.get('versionCode')} != {version_code}"
            )
        if element.get("versionName") != version_name:
            raise VerificationError(
                f"{variant} versionName mismatch: {element.get('versionName')!r} != {version_name!r}"
            )

        apk_name = element.get("outputFile")
        if not isinstance(apk_name, str) or not apk_name.endswith(".apk"):
            raise VerificationError(f"{variant} metadata does not point at an APK output")

        apk_path = variant_path / apk_name
        if not apk_path.is_file():
            raise VerificationError(f"Missing {variant} APK: {apk_path.relative_to(ROOT)}")
        if apk_path.stat().st_size <= 0:
            raise VerificationError(f"{variant} APK is empty: {apk_path.relative_to(ROOT)}")
        apk_paths.append(apk_path)

        abis = [
            entry.get("value")
            for entry in element.get("filters", [])
            if isinstance(entry, dict) and entry.get("filterType") == "ABI"
        ]
        if abis:
            seen_abis.update(abi for abi in abis if isinstance(abi, str))
        else:
            universal_count += 1

    if universal_count != 1:
        raise VerificationError(
            f"{variant} must produce exactly one universal APK, found {universal_count}"
        )
    missing_abis = [abi for abi in EXPECTED_ABIS if abi not in seen_abis]
    if missing_abis:
        raise VerificationError(f"{variant} is missing per-ABI APKs for: {', '.join(missing_abis)}")

    return apk_paths


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


def run_python_script(*args: str) -> None:
    command = [sys.executable, str(ROOT / "scripts" / args[0]), *args[1:]]
    result = subprocess.run(
        command,
        cwd=ROOT,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if result.returncode != 0:
        output = result.stdout.strip()
        detail = f": {output}" if output else ""
        raise VerificationError(f"{' '.join(command)} failed{detail}")
    output = result.stdout.strip()
    if output:
        print(output)


def verify_local_trust_controls(apks: list[Path]) -> None:
    run_python_script("write_release_checksums.py", "--root", str(APK_ROOT), "--check")
    run_python_script("write_apk_signing_fingerprints.py", "--root", str(APK_ROOT), "--check")
    run_python_script("check_apk_size.py")
    run_python_script("validate_play_listing_assets.py", "--quiet")
    run_python_script("validate_package_identity.py")
    run_python_script("validate_distribution_readiness.py")
    # These two shipped as workflow steps before the local migration; they are
    # release gates (unearned capability claims / audio API policy), not lint.
    run_python_script("validate_public_claims.py")
    run_python_script("validate_android_audio_api_policy.py")
    # Local sidecars prove nothing about what was actually uploaded. v3.76.0
    # shipped a lone APK with no checksum and no certificate fingerprint while
    # every local gate was green.
    run_python_script("verify_published_release_sidecars.py")
    for apk in apks:
        run_python_script("check_16kb_alignment.py", str(apk))


def write_fixture(root: Path, relative: str, content: str | bytes) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, bytes):
        path.write_bytes(content)
    else:
        path.write_text(content, encoding="utf-8")
    return path


def write_output_metadata(root: Path, variant: str, apk_name: str, version_code: int, version_name: str) -> Path:
    write_fixture(root, f"app/build/outputs/apk/{variant}/{apk_name}", b"placeholder-apk")
    write_fixture(
        root,
        f"app/build/outputs/apk/{variant}/output-metadata.json",
        json.dumps(
            {
                "elements": [
                    {
                        "versionCode": version_code,
                        "versionName": version_name,
                        "outputFile": apk_name,
                    }
                ]
            }
        ),
    )
    return root / "app" / "build" / "outputs" / "apk" / variant / apk_name


def write_split_output_metadata(
    root: Path, variant: str, version_code: int, version_name: str
) -> list[Path]:
    """Mirror the real ABI-split layout: one universal APK plus one per ABI."""
    elements = [
        {
            "versionCode": version_code,
            "versionName": version_name,
            "outputFile": f"app-universal-{variant}.apk",
            "filters": [],
        }
    ]
    paths = [write_fixture(root, f"app/build/outputs/apk/{variant}/app-universal-{variant}.apk", b"placeholder-apk")]
    for abi in EXPECTED_ABIS:
        name = f"app-{abi}-{variant}.apk"
        elements.append(
            {
                "versionCode": version_code,
                "versionName": version_name,
                "outputFile": name,
                "filters": [{"filterType": "ABI", "value": abi}],
            }
        )
        paths.append(write_fixture(root, f"app/build/outputs/apk/{variant}/{name}", b"placeholder-apk"))
    write_fixture(
        root,
        f"app/build/outputs/apk/{variant}/output-metadata.json",
        json.dumps({"elements": elements}),
    )
    return paths


def run_self_tests() -> None:
    original_root = ROOT
    original_apk_root = APK_ROOT
    original_runner = run_python_script

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        try:
            globals()["ROOT"] = root
            globals()["APK_ROOT"] = root / "app" / "build" / "outputs" / "apk"

            version_code = 242
            version_name = "3.74.109"
            write_fixture(root, "app/build.gradle.kts", f'versionCode = {version_code}\nversionName = "{version_name}"\n')
            version_strings = write_fixture(
                root,
                "app/src/main/res/values/strings.xml",
                '<resources><string name="app_name">ClearCut</string></resources>\n',
            )
            write_fixture(root, ".gitignore", "keystore.properties\n*.jks\n*.keystore\n")

            debug_apks = write_split_output_metadata(root, "debug", version_code, version_name)
            release_apks = write_split_output_metadata(root, "release", version_code, version_name)
            test_apk = write_output_metadata(
                root,
                "androidTest/debug",
                "app-debug-androidTest.apk",
                version_code,
                version_name,
            )

            parsed_code, parsed_name = parse_gradle_version()
            if (parsed_code, parsed_name) != (version_code, version_name):
                raise VerificationError("self-test version parsing mismatch")
            verify_repository_metadata()
            outputs = [
                *verify_variant_apk("debug", version_code, version_name),
                *verify_variant_apk("release", version_code, version_name),
                verify_android_test_apk(),
            ]
            if sorted(outputs) != sorted([*debug_apks, *release_apks, test_apk]):
                raise VerificationError("self-test APK output discovery mismatch")

            version_strings.write_text(
                '<resources><string name="app_version">v3.74.108</string></resources>\n',
                encoding="utf-8",
            )
            try:
                verify_repository_metadata()
            except VerificationError:
                pass
            else:
                raise VerificationError("self-test expected stale duplicate runtime version to fail")
            version_strings.write_text('<resources/>\n', encoding="utf-8")

            calls: list[tuple[str, ...]] = []

            def capture_runner(*args: str) -> None:
                calls.append(args)

            globals()["run_python_script"] = capture_runner
            verify_local_trust_controls([*debug_apks, *release_apks, test_apk])
            expected = [
                ("write_release_checksums.py", "--root", str(APK_ROOT), "--check"),
                ("write_apk_signing_fingerprints.py", "--root", str(APK_ROOT), "--check"),
                ("check_apk_size.py",),
                ("validate_play_listing_assets.py", "--quiet"),
                ("validate_package_identity.py",),
                ("validate_distribution_readiness.py",),
                ("validate_public_claims.py",),
                ("validate_android_audio_api_policy.py",),
                ("verify_published_release_sidecars.py",),
                *[("check_16kb_alignment.py", str(apk)) for apk in debug_apks],
                *[("check_16kb_alignment.py", str(apk)) for apk in release_apks],
                ("check_16kb_alignment.py", str(test_apk)),
            ]
            if calls != expected:
                raise VerificationError(f"self-test local trust command mismatch: {calls!r}")
        finally:
            globals()["ROOT"] = original_root
            globals()["APK_ROOT"] = original_apk_root
            globals()["run_python_script"] = original_runner


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true", help="run built-in fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("release artifact verifier self-tests passed.")
            return 0
        version_code, version_name = parse_gradle_version()
        verify_repository_metadata()
        outputs = [
            *verify_variant_apk("debug", version_code, version_name),
            *verify_variant_apk("release", version_code, version_name),
            verify_android_test_apk(),
        ]
        verify_local_trust_controls(outputs)
    except VerificationError as error:
        print(f"release verification failed: {error}", file=sys.stderr)
        return 1

    print(f"ClearCut v{version_name} (versionCode {version_code}) local release trust verified.")
    for apk in outputs:
        print(f"  - {apk.relative_to(ROOT)} ({apk.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

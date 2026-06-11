#!/usr/bin/env python3
"""Validate NovaCut distribution-channel readiness copy and metadata."""
from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FASTLANE = ROOT / "fastlane" / "metadata" / "android" / "en-US"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


class DistributionError(RuntimeError):
    pass


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    if not path.is_file():
        raise DistributionError(f"missing required file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def parse_gradle_version(root: Path = ROOT) -> tuple[int, str]:
    text = read_text(root / "app" / "build.gradle.kts")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise DistributionError("could not parse Gradle versionCode/versionName")
    return int(code_match.group(1)), name_match.group(1)


def require_terms(path: Path, terms: tuple[str, ...]) -> None:
    text = read_text(path).lower()
    missing = [term for term in terms if term.lower() not in text]
    if missing:
        raise DistributionError(f"{rel(path)} is missing distribution term(s): {', '.join(missing)}")


def require_no_unearned_channel_claims(path: Path) -> None:
    text = read_text(path)
    denied = (
        r"\bavailable on f-droid\b",
        r"\bdownload from f-droid\b",
        r"\bf-droid release is live\b",
        r"\bdeveloper verification complete\b",
        r"\bverified developer account is complete\b",
    )
    for pattern in denied:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            raise DistributionError(f"{rel(path)} contains unearned distribution claim: {match.group(0)!r}")


def verify_readme(root: Path = ROOT) -> None:
    path = root / "README.md"
    require_terms(
        path,
        (
            "GitHub Releases",
            "Google Play",
            "F-Droid",
            "Android developer verification",
            "September 2026",
            "certified Android devices",
            "verified developer",
            "package names",
            "not complete",
            "AllowedAPKSigningKeys",
        ),
    )
    require_no_unearned_channel_claims(path)


def verify_fastlane(root: Path = ROOT) -> None:
    fastlane = root / FASTLANE.relative_to(ROOT)
    full_description = fastlane / "full_description.txt"
    require_terms(
        full_description,
        (
            "Distribution status",
            "GitHub Releases",
            "Google Play",
            "F-Droid metadata",
            "verified-developer registration",
            "not complete",
        ),
    )
    require_no_unearned_channel_claims(full_description)

    title = read_text(fastlane / "title.txt").strip()
    short = read_text(fastlane / "short_description.txt").strip()
    full = read_text(full_description).strip()
    if not title or len(title) > 30:
        raise DistributionError("fastlane title must be 1-30 characters")
    if not short or len(short) > 80:
        raise DistributionError("fastlane short description must be 1-80 characters")
    if not full or len(full) > 4000:
        raise DistributionError("fastlane full description must be 1-4000 characters")


def verify_latest_changelog(root: Path = ROOT) -> None:
    version_code, _version_name = parse_gradle_version(root)
    changelog = root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs" / f"{version_code}.txt"
    text = read_text(changelog).strip()
    if not text:
        raise DistributionError(f"{rel(changelog)} must not be empty")
    if len(text) > 500:
        raise DistributionError(f"{rel(changelog)} is {len(text)} characters, expected <= 500")


def verify_workflow(root: Path = ROOT) -> None:
    workflow = read_text(root / WORKFLOW.relative_to(ROOT))
    for required in (
        "scripts/validate_distribution_readiness.py --self-test",
        "scripts/validate_distribution_readiness.py",
        "scripts/write_release_checksums.py --check",
        "app/build/outputs/apk/release/*.sha256",
    ):
        if required not in workflow:
            raise DistributionError(f"{rel(root / WORKFLOW.relative_to(ROOT))} is missing {required}")


def validate(root: Path = ROOT) -> None:
    verify_readme(root)
    verify_fastlane(root)
    verify_latest_changelog(root)
    verify_workflow(root)


def write_fixture(root: Path, relative: str, text: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_minimal_repo(root: Path, readme: str, full_description: str, workflow: str) -> None:
    write_fixture(
        root,
        "app/build.gradle.kts",
        'android { defaultConfig { versionCode = 200\nversionName = "3.74.63" } }\n',
    )
    write_fixture(root, "README.md", readme)
    write_fixture(root, "fastlane/metadata/android/en-US/title.txt", "NovaCut\n")
    write_fixture(
        root,
        "fastlane/metadata/android/en-US/short_description.txt",
        "Professional video editor with AI tools and GLSL effects.\n",
    )
    write_fixture(root, "fastlane/metadata/android/en-US/full_description.txt", full_description)
    write_fixture(root, "fastlane/metadata/android/en-US/changelogs/200.txt", "- Distribution readiness gate\n")
    write_fixture(root, ".github/workflows/build.yml", workflow)


def run_self_tests() -> None:
    valid_readme = (
        "GitHub Releases are the direct APK channel. Google Play metadata is packaged. "
        "F-Droid source metadata is present. Android developer verification starts in September 2026 "
        "for certified Android devices and requires a verified developer to register package names. "
        "The account step is not complete. AllowedAPKSigningKeys is pending final signing key policy."
    )
    valid_full = (
        "Distribution status: GitHub Releases carry direct APK builds. Google Play metadata is ready. "
        "F-Droid metadata is present, but verified-developer registration is not complete."
    )
    valid_workflow = (
        "python3 scripts/validate_distribution_readiness.py --self-test\n"
        "python3 scripts/validate_distribution_readiness.py\n"
        "python3 scripts/write_release_checksums.py --check\n"
        "app/build/outputs/apk/release/*.sha256\n"
    )

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_minimal_repo(root, valid_readme, valid_full, valid_workflow)
        validate(root)

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_minimal_repo(root, "F-Droid release is live.", valid_full, valid_workflow)
        try:
            validate(root)
        except DistributionError:
            pass
        else:
            raise DistributionError("self-test expected unearned F-Droid claim to fail")

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_minimal_repo(root, valid_readme, valid_full, "python3 scripts/write_release_checksums.py --check\n")
        try:
            validate(root)
        except DistributionError:
            pass
        else:
            raise DistributionError("self-test expected missing workflow gate to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true", help="run built-in fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("distribution readiness self-tests passed.")
            return 0
        validate()
    except DistributionError as error:
        print(f"distribution readiness validation failed: {error}", file=sys.stderr)
        return 1
    print("Distribution readiness metadata verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

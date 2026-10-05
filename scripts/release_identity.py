#!/usr/bin/env python3
"""Enforce Gradle as ClearCut's only runtime release-version source.

Also refuses a CHANGELOG.md version section that has no git tag, other than the
version Gradle is about to release, so the public record never lists a release
nobody can download.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_VERSION_RESOURCE = re.compile(r'<string\s+name=["\']app_version["\']')
CHANGELOG_VERSION = re.compile(r"^## v(\d+\.\d+\.\d+)\s*$", re.MULTILINE)
GRADLE_VERSION_NAME = re.compile(r'^\s*versionName\s*=\s*"([^"]+)"', re.MULTILINE)


class ReleaseIdentityError(RuntimeError):
    pass


def duplicate_runtime_version_resources(root: Path) -> list[Path]:
    resource_root = root / "app" / "src" / "main" / "res"
    if not resource_root.is_dir():
        return []
    duplicates: list[Path] = []
    for strings_path in resource_root.glob("values*/strings.xml"):
        text = strings_path.read_text(encoding="utf-8")
        if APP_VERSION_RESOURCE.search(text):
            duplicates.append(strings_path)
    return sorted(duplicates)


def git_tags(root: Path) -> set[str]:
    try:
        listed = subprocess.run(
            ["git", "-C", str(root), "tag", "--list", "v*"],
            capture_output=True,
            text=True,
            check=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise ReleaseIdentityError(f"cannot list git tags to check CHANGELOG.md: {error}") from error
    return {line.strip() for line in listed.stdout.splitlines() if line.strip()}


def untagged_changelog_versions(root: Path, tags: set[str] | None = None) -> list[str]:
    changelog = root / "CHANGELOG.md"
    if not changelog.is_file():
        return []
    versions = CHANGELOG_VERSION.findall(changelog.read_text(encoding="utf-8"))
    if not versions:
        return []
    gradle = root / "app" / "build.gradle.kts"
    releasing = None
    if gradle.is_file():
        match = GRADLE_VERSION_NAME.search(gradle.read_text(encoding="utf-8"))
        releasing = match.group(1) if match else None
    known = git_tags(root) if tags is None else tags
    return [version for version in versions if f"v{version}" not in known and version != releasing]


def verify_release_identity(root: Path = ROOT, tags: set[str] | None = None) -> None:
    duplicates = duplicate_runtime_version_resources(root)
    if duplicates:
        rendered = ", ".join(path.relative_to(root).as_posix() for path in duplicates)
        raise ReleaseIdentityError(
            "runtime version labels must derive from BuildConfig.VERSION_NAME; "
            f"remove duplicate app_version resources: {rendered}"
        )
    untagged = untagged_changelog_versions(root, tags)
    if untagged:
        raise ReleaseIdentityError(
            "CHANGELOG.md lists versions with no git tag; publish them or fold them into a tagged "
            f"release: {', '.join('v' + version for version in untagged)}"
        )


def run_self_test() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        strings = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
        strings.parent.mkdir(parents=True)
        strings.write_text('<resources><string name="app_name">ClearCut</string></resources>', encoding="utf-8")
        verify_release_identity(root, tags=set())

        # The version Gradle is releasing may be untagged; an older section may not.
        gradle = root / "app" / "build.gradle.kts"
        gradle.write_text('android {\n    defaultConfig {\n        versionName = "1.2.0"\n    }\n}\n', encoding="utf-8")
        changelog = root / "CHANGELOG.md"
        changelog.write_text("## Unreleased\n\n## v1.2.0\n\n## v1.1.0\n\n## v1.0.0\n", encoding="utf-8")
        verify_release_identity(root, tags={"v1.0.0", "v1.1.0"})
        if untagged_changelog_versions(root, tags={"v1.0.0"}) != ["1.1.0"]:
            raise AssertionError("self-test expected the untagged v1.1.0 section to be reported")
        try:
            verify_release_identity(root, tags={"v1.0.0"})
        except ReleaseIdentityError:
            pass
        else:
            raise AssertionError("self-test expected an untagged CHANGELOG version to fail")
        changelog.write_text("## v1.2.0\n", encoding="utf-8")

        strings.write_text(
            '<resources><string name="app_version">v0.0.0-stale</string></resources>',
            encoding="utf-8",
        )
        try:
            verify_release_identity(root, tags=set())
        except ReleaseIdentityError:
            return
        raise AssertionError("self-test expected a stale duplicate runtime version to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        run_self_test()
        print("release identity self-test passed.")
        return 0
    try:
        verify_release_identity()
    except ReleaseIdentityError as error:
        parser.error(str(error))
    print(
        "release identity verified: runtime labels derive from Gradle BuildConfig metadata "
        "and every CHANGELOG.md version is tagged."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

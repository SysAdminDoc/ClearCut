#!/usr/bin/env python3
"""Enforce Gradle as ClearCut's only runtime release-version source."""
from __future__ import annotations

import argparse
import re
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_VERSION_RESOURCE = re.compile(r'<string\s+name=["\']app_version["\']')


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


def verify_release_identity(root: Path = ROOT) -> None:
    duplicates = duplicate_runtime_version_resources(root)
    if duplicates:
        rendered = ", ".join(path.relative_to(root).as_posix() for path in duplicates)
        raise ReleaseIdentityError(
            "runtime version labels must derive from BuildConfig.VERSION_NAME; "
            f"remove duplicate app_version resources: {rendered}"
        )


def run_self_test() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        strings = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
        strings.parent.mkdir(parents=True)
        strings.write_text('<resources><string name="app_name">ClearCut</string></resources>', encoding="utf-8")
        verify_release_identity(root)

        strings.write_text(
            '<resources><string name="app_version">v0.0.0-stale</string></resources>',
            encoding="utf-8",
        )
        try:
            verify_release_identity(root)
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
    print("release identity verified: runtime labels derive from Gradle BuildConfig metadata.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

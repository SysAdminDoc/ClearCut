#!/usr/bin/env python3
"""Write or verify SHA-256 sidecar files for generated ClearCut APKs."""
from __future__ import annotations

import argparse
import hashlib
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_ROOT = ROOT / "app" / "build" / "outputs" / "apk"
APK_VARIANTS = (
    APK_ROOT / "debug",
    APK_ROOT / "release",
    APK_ROOT / "androidTest" / "debug",
)


class ChecksumError(RuntimeError):
    pass


def sha256_hex(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def checksum_path(apk: Path) -> Path:
    return apk.with_name(f"{apk.name}.sha256")


def checksum_line(apk: Path) -> str:
    return f"{sha256_hex(apk)}  {apk.name}\n"


def apk_paths(root: Path = APK_ROOT) -> list[Path]:
    variants = (
        root / "debug",
        root / "release",
        root / "androidTest" / "debug",
    )
    paths: list[Path] = []
    for variant in variants:
        paths.extend(sorted(variant.glob("*.apk")))
    if not paths:
        raise ChecksumError(f"no APK files found under {root}")
    return paths


def write_checksums(root: Path = APK_ROOT) -> list[Path]:
    written: list[Path] = []
    for apk in apk_paths(root):
        sidecar = checksum_path(apk)
        sidecar.write_text(checksum_line(apk), encoding="utf-8")
        written.append(sidecar)
    return written


def verify_checksums(root: Path = APK_ROOT) -> list[Path]:
    verified: list[Path] = []
    for apk in apk_paths(root):
        sidecar = checksum_path(apk)
        if not sidecar.is_file():
            raise ChecksumError(f"missing checksum sidecar: {sidecar}")
        expected = checksum_line(apk)
        actual = sidecar.read_text(encoding="utf-8")
        if actual != expected:
            raise ChecksumError(f"stale checksum sidecar: {sidecar}")
        verified.append(sidecar)
    return verified


def run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        apk = root / "release" / "app-release.apk"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"clearcut-test-apk")
        write_checksums(root)
        verify_checksums(root)
        apk.write_bytes(b"clearcut-test-apk-tampered")
        try:
            verify_checksums(root)
        except ChecksumError:
            return
        raise ChecksumError("self-test expected tampered APK checksum verification to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        default=APK_ROOT,
        help="directory to search recursively for APK files",
    )
    parser.add_argument("--check", action="store_true", help="verify existing .sha256 sidecars without rewriting them")
    parser.add_argument("--self-test", action="store_true", help="run built-in checksum fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("release checksum self-tests passed.")
            return 0
        sidecars = verify_checksums(args.root) if args.check else write_checksums(args.root)
    except ChecksumError as error:
        print(f"release checksum validation failed: {error}", file=sys.stderr)
        return 1

    action = "verified" if args.check else "wrote"
    print(f"{action} {len(sidecars)} release checksum file(s).")
    for sidecar in sidecars:
        print(f"  - {sidecar.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

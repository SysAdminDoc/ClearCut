#!/usr/bin/env python3
"""Write or verify APK signing-certificate SHA-256 fingerprint sidecars."""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_ROOT = ROOT / "app" / "build" / "outputs" / "apk"
FINGERPRINT_RE = re.compile(
    r"(?:Signer #\d+\s+|V\d+ Signer:\s*)certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)"
)
# The signer every shipped GitHub Release APK carries (verified against
# v3.74.117's asset). Sidecar freshness alone cannot catch a wrong key —
# it compares the APK against a sidecar derived from the same APK — so
# release APKs are additionally pinned to this identity. If the key ever
# legitimately rotates, update this constant deliberately.
EXPECTED_RELEASE_FINGERPRINT = "76cf4c647590f1dc17b37175b9dd65cac6d0a1e7ffff6a86f8ea8a29f67f75f6"


class FingerprintError(RuntimeError):
    pass


def apk_paths(root: Path = APK_ROOT) -> list[Path]:
    paths = sorted(path for path in root.rglob("*.apk") if path.is_file())
    if not paths:
        raise FingerprintError(f"no APK files found under {root}")
    return paths


def fingerprint_path(apk: Path) -> Path:
    return apk.with_name(f"{apk.name}.signing-cert-sha256")


def normalize_fingerprint(raw: str) -> str:
    compact = raw.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", compact):
        raise FingerprintError(f"invalid SHA-256 fingerprint: {raw!r}")
    return compact


def parse_fingerprints(apksigner_output: str) -> list[str]:
    fingerprints: list[str] = []
    seen: set[str] = set()
    for match in FINGERPRINT_RE.finditer(apksigner_output):
        fingerprint = normalize_fingerprint(match.group(1))
        if fingerprint in seen:
            continue
        fingerprints.append(fingerprint)
        seen.add(fingerprint)
    if not fingerprints:
        raise FingerprintError("apksigner output did not include a signer certificate SHA-256 digest")
    return fingerprints


def locate_apksigner(explicit: str | None) -> Path:
    if explicit:
        candidate = Path(explicit)
        if candidate.is_file():
            return candidate
        raise FingerprintError(f"apksigner not found: {candidate}")

    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        raise FingerprintError("set ANDROID_HOME or pass --apksigner")

    build_tools = Path(android_home) / "build-tools"
    names = ("apksigner", "apksigner.bat")
    candidates = [
        path / name
        for path in sorted(build_tools.iterdir(), reverse=True)
        if path.is_dir()
        for name in names
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FingerprintError(f"apksigner not found under {build_tools}")


def apksigner_fingerprints(apk: Path, apksigner: Path) -> list[str]:
    result = subprocess.run(
        [str(apksigner), "verify", "--print-certs", str(apk)],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if result.returncode != 0:
        raise FingerprintError(f"apksigner failed for {apk}: {result.stdout.strip()}")
    return parse_fingerprints(result.stdout)


def is_release_apk(apk: Path) -> bool:
    return "release" in {part.lower() for part in apk.parts}


def enforce_release_identity(apk: Path, fingerprints: list[str]) -> None:
    if not is_release_apk(apk):
        return
    if EXPECTED_RELEASE_FINGERPRINT not in fingerprints:
        raise FingerprintError(
            f"{apk.name} is not signed by the pinned release key "
            f"(expected {EXPECTED_RELEASE_FINGERPRINT}, got {fingerprints})"
        )


def fingerprint_line(apk: Path, apksigner: Path) -> str:
    fingerprints = apksigner_fingerprints(apk, apksigner)
    enforce_release_identity(apk, fingerprints)
    return "\n".join(f"signer{index}={fingerprint}  {apk.name}" for index, fingerprint in enumerate(fingerprints, 1)) + "\n"


def write_fingerprints(root: Path, apksigner: Path) -> list[Path]:
    written: list[Path] = []
    for apk in apk_paths(root):
        sidecar = fingerprint_path(apk)
        sidecar.write_text(fingerprint_line(apk, apksigner), encoding="utf-8")
        written.append(sidecar)
    return written


def verify_fingerprints(root: Path, apksigner: Path) -> list[Path]:
    verified: list[Path] = []
    for apk in apk_paths(root):
        sidecar = fingerprint_path(apk)
        if not sidecar.is_file():
            raise FingerprintError(f"missing signing fingerprint sidecar: {sidecar}")
        expected = fingerprint_line(apk, apksigner)
        actual = sidecar.read_text(encoding="utf-8")
        if actual != expected:
            raise FingerprintError(f"stale signing fingerprint sidecar: {sidecar}")
        verified.append(sidecar)
    return verified


def run_self_tests() -> None:
    sample = """
Verifies
Verified using v1 scheme (JAR signing): true
Signer #1 certificate SHA-256 digest: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99
Signer #2 certificate SHA-256 digest: 00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff
V2 Signer: certificate SHA-256 digest: 00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff
"""
    parsed = parse_fingerprints(sample)
    if parsed != [
        "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
        "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
    ]:
        raise FingerprintError("self-test fingerprint parsing mismatch")

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        apk = root / "release" / "app-release.apk"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"placeholder")
        if apk_paths(root) != [apk]:
            raise FingerprintError("self-test APK discovery failed")

    release_apk_path = Path("app/build/outputs/apk/release/app-release.apk")
    enforce_release_identity(release_apk_path, [EXPECTED_RELEASE_FINGERPRINT])
    try:
        enforce_release_identity(release_apk_path, ["0" * 64])
    except FingerprintError:
        pass
    else:
        raise FingerprintError("self-test expected wrong release signer to fail")
    enforce_release_identity(Path("app/build/outputs/apk/debug/app-debug.apk"), ["0" * 64])

    try:
        parse_fingerprints("Signer #1 certificate SHA-256 digest: bad")
    except FingerprintError:
        return
    raise FingerprintError("self-test expected malformed fingerprint to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        default=APK_ROOT,
        help="directory to search recursively for APK files",
    )
    parser.add_argument("--apksigner", help="path to Android build-tools apksigner")
    parser.add_argument("--check", action="store_true", help="verify existing sidecars without rewriting them")
    parser.add_argument("--self-test", action="store_true", help="run built-in parser and discovery checks")
    args = parser.parse_args()

    try:
        if args.self_test:
            run_self_tests()
            print("APK signing fingerprint self-tests passed.")
            return 0
        apksigner = locate_apksigner(args.apksigner)
        sidecars = verify_fingerprints(args.root, apksigner) if args.check else write_fingerprints(args.root, apksigner)
    except FingerprintError as error:
        print(f"APK signing fingerprint validation failed: {error}", file=sys.stderr)
        return 1

    action = "verified" if args.check else "wrote"
    print(f"{action} {len(sidecars)} APK signing fingerprint file(s).")
    for sidecar in sidecars:
        try:
            display = sidecar.relative_to(ROOT)
        except ValueError:
            display = sidecar
        print(f"  - {display}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
check_16kb_alignment.py — Verify every native library in a built ClearCut
APK or AAB declares 16 KB (0x4000) or larger LOAD-segment alignment.

Why: Google Play blocks uploads of apps targeting Android 15+ (API 35+)
that bundle native libraries whose ELF LOAD segments are not 16 KB aligned.
ClearCut targets API 36; non-compliance is a hard upload gate.

Usage:
    python scripts/check_16kb_alignment.py app/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a
    python scripts/check_16kb_alignment.py path/to/app.apk
    python scripts/check_16kb_alignment.py path/to/release.aab

Exit codes:
    0  — all libraries are 16 KB aligned
    1  — at least one library is misaligned
    2  — bad arguments or unexpected error

Notes:
- Only checks arm64-v8a, x86_64, and riscv64 ABIs (the architectures that
  use 16 KB pages). armeabi-v7a and x86 are 4 KB only and are skipped.
- Pure Python: no NDK readelf required. Parses the ELF program header
  directly. Works on Windows, macOS, and Linux CI runners.
- Mirrors Google's guidance to check that every LOAD segment reports an
  alignment value of at least 2**14. Nonzero virtual addresses are valid when
  p_offset and p_vaddr stay congruent modulo p_align.
- Inspired by the Google sample at
  https://developer.android.com/guide/practices/page-sizes#test
"""
from __future__ import annotations

import os
import struct
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable, Iterator, NamedTuple

REQUIRED_ALIGNMENT = 0x4000  # 16 KB

ABIS_THAT_NEED_16KB = {"arm64-v8a", "x86_64", "riscv64"}

# Program header type for loadable segments
PT_LOAD = 1


class LoadSegment(NamedTuple):
    offset: int
    vaddr: int
    align: int


class AlignmentFailure(NamedTuple):
    display: str
    segment: LoadSegment
    reason: str


def _read_elf_load_segments(data: bytes) -> Iterator[LoadSegment]:
    """Yield (offset, vaddr, align) for every PT_LOAD segment in an ELF blob."""
    if len(data) < 64 or data[:4] != b"\x7fELF":
        return
    is_64 = data[4] == 2  # EI_CLASS: 1 = ELF32, 2 = ELF64
    is_le = data[5] == 1  # EI_DATA:  1 = little-endian
    endian = "<" if is_le else ">"
    if is_64:
        # Elf64_Ehdr fields we need
        e_phoff = struct.unpack(endian + "Q", data[32:40])[0]
        e_phentsize = struct.unpack(endian + "H", data[54:56])[0]
        e_phnum = struct.unpack(endian + "H", data[56:58])[0]
        # Elf64_Phdr: p_type(4) p_flags(4) p_offset(8) p_vaddr(8) p_paddr(8)
        #             p_filesz(8) p_memsz(8) p_align(8) = 56 bytes
        for i in range(e_phnum):
            base = e_phoff + i * e_phentsize
            if base + 56 > len(data):
                return
            p_type = struct.unpack(endian + "I", data[base : base + 4])[0]
            if p_type != PT_LOAD:
                continue
            p_offset = struct.unpack(endian + "Q", data[base + 8 : base + 16])[0]
            p_vaddr = struct.unpack(endian + "Q", data[base + 16 : base + 24])[0]
            p_align = struct.unpack(endian + "Q", data[base + 48 : base + 56])[0]
            yield LoadSegment(p_offset, p_vaddr, p_align)
    else:
        e_phoff = struct.unpack(endian + "I", data[28:32])[0]
        e_phentsize = struct.unpack(endian + "H", data[42:44])[0]
        e_phnum = struct.unpack(endian + "H", data[44:46])[0]
        # Elf32_Phdr: p_type(4) p_offset(4) p_vaddr(4) p_paddr(4)
        #             p_filesz(4) p_memsz(4) p_flags(4) p_align(4) = 32 bytes
        for i in range(e_phnum):
            base = e_phoff + i * e_phentsize
            if base + 32 > len(data):
                return
            p_type = struct.unpack(endian + "I", data[base : base + 4])[0]
            if p_type != PT_LOAD:
                continue
            p_offset = struct.unpack(endian + "I", data[base + 4 : base + 8])[0]
            p_vaddr = struct.unpack(endian + "I", data[base + 8 : base + 12])[0]
            p_align = struct.unpack(endian + "I", data[base + 28 : base + 32])[0]
            yield LoadSegment(p_offset, p_vaddr, p_align)


def _abi_from_path(rel_path: str) -> str | None:
    """Return the ABI segment of an APK lib path or None if not under lib/."""
    parts = rel_path.replace("\\", "/").split("/")
    if "lib" in parts:
        i = parts.index("lib")
        if i + 1 < len(parts):
            return parts[i + 1]
    return None


def _iter_native_libs(target: Path) -> Iterator[tuple[str, bytes]]:
    """Yield (display_name, ELF bytes) for every .so in target.

    Accepts: a directory tree of .so files, an .apk, or an .aab.
    """
    if target.is_dir():
        for root, _, files in os.walk(target):
            for name in files:
                if name.endswith(".so"):
                    full = Path(root) / name
                    rel = full.relative_to(target).as_posix()
                    yield rel, full.read_bytes()
        return
    if target.suffix.lower() in {".apk", ".aab", ".zip"}:
        with zipfile.ZipFile(target) as zf:
            for info in zf.infolist():
                if info.filename.endswith(".so"):
                    yield info.filename, zf.read(info)
        return
    if target.suffix == ".so":
        yield target.name, target.read_bytes()
        return
    raise SystemExit(f"Unsupported input: {target}")


def check(target: Path) -> int:
    misaligned: list[AlignmentFailure] = []
    skipped: list[str] = []
    ok_count = 0

    for display, blob in _iter_native_libs(target):
        abi = _abi_from_path(display)
        if abi is not None and abi not in ABIS_THAT_NEED_16KB:
            skipped.append(f"{display} (ABI {abi} does not require 16 KB pages)")
            continue
        segments = list(_read_elf_load_segments(blob))
        if not segments:
            # Fail closed: a shared library that is not parseable ELF (or has
            # no PT_LOAD at all) is a corrupt artifact, not an exemption. This
            # gate exists precisely to catch bad artifacts.
            misaligned.append(
                AlignmentFailure(
                    display,
                    LoadSegment(0, 0, 0),
                    "not parseable ELF / no PT_LOAD segments — corrupt native library",
                )
            )
            continue
        for seg in segments:
            if seg.align < REQUIRED_ALIGNMENT:
                misaligned.append(
                    AlignmentFailure(
                        display,
                        seg,
                        f"LOAD segment alignment is below 0x{REQUIRED_ALIGNMENT:x}",
                    )
                )
                break
            if seg.align > 0 and ((seg.vaddr - seg.offset) % seg.align) != 0:
                misaligned.append(
                    AlignmentFailure(
                        display,
                        seg,
                        "p_offset and p_vaddr are not congruent modulo p_align",
                    )
                )
                break
        else:
            ok_count += 1

    print(f"Checked target: {target}")
    print(f"  ABIs requiring 16 KB alignment: {sorted(ABIS_THAT_NEED_16KB)}")
    print(f"  OK:        {ok_count}")
    print(f"  Skipped:   {len(skipped)}")
    print(f"  MISALIGNED: {len(misaligned)}")

    for note in skipped:
        print(f"  - skip {note}")
    if misaligned:
        print()
        print("MISALIGNED LIBRARIES — Play Store will reject this AAB/APK:")
        for display, seg, reason in misaligned:
            print(
                f"  - {display}: PT_LOAD at offset=0x{seg.offset:x} "
                f"vaddr=0x{seg.vaddr:x} align=0x{seg.align:x} "
                f"(required >= 0x{REQUIRED_ALIGNMENT:x}; {reason})"
            )
        return 1

    return 0


def _minimal_elf64(load_alignment: int) -> bytes:
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2
    header[5] = 1
    struct.pack_into("<H", header, 18, 62)
    struct.pack_into("<Q", header, 32, 64)
    struct.pack_into("<H", header, 52, 64)
    struct.pack_into("<H", header, 54, 56)
    struct.pack_into("<H", header, 56, 1)

    program_header = bytearray(56)
    struct.pack_into("<I", program_header, 0, PT_LOAD)
    struct.pack_into("<I", program_header, 4, 5)
    struct.pack_into("<Q", program_header, 8, 0)
    struct.pack_into("<Q", program_header, 16, 0)
    struct.pack_into("<Q", program_header, 32, 1)
    struct.pack_into("<Q", program_header, 40, 1)
    struct.pack_into("<Q", program_header, 48, load_alignment)
    return bytes(header + program_header + b"\0")


def run_self_tests() -> None:
    aligned = list(_read_elf_load_segments(_minimal_elf64(REQUIRED_ALIGNMENT)))
    if len(aligned) != 1 or aligned[0].align != REQUIRED_ALIGNMENT:
        raise AssertionError("self-test failed to parse aligned ELF segment")

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        ok_apk = root / "ok.apk"
        bad_apk = root / "bad.apk"
        with zipfile.ZipFile(ok_apk, "w") as zf:
            zf.writestr("lib/arm64-v8a/libok.so", _minimal_elf64(REQUIRED_ALIGNMENT))
        with zipfile.ZipFile(bad_apk, "w") as zf:
            zf.writestr("lib/arm64-v8a/libbad.so", _minimal_elf64(0x1000))
        if check(ok_apk) != 0:
            raise AssertionError("self-test expected aligned APK to pass")
        if check(bad_apk) == 0:
            raise AssertionError("self-test expected misaligned APK to fail")

        corrupt_apk = root / "corrupt.apk"
        with zipfile.ZipFile(corrupt_apk, "w") as zf:
            zf.writestr("lib/arm64-v8a/libcorrupt.so", b"not an elf at all")
        if check(corrupt_apk) == 0:
            raise AssertionError("self-test expected unparseable .so to fail closed")


def main(argv: list[str]) -> int:
    if len(argv) == 2 and argv[1] == "--self-test":
        run_self_tests()
        print("16 KB alignment self-tests passed.")
        return 0
    if len(argv) != 2:
        print(__doc__)
        return 2
    target = Path(argv[1])
    if not target.exists():
        print(f"error: path does not exist: {target}", file=sys.stderr)
        return 2
    return check(target)


if __name__ == "__main__":
    sys.exit(main(sys.argv))

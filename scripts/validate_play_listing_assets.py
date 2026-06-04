#!/usr/bin/env python3
"""Validate NovaCut's committed Google Play listing package."""
from __future__ import annotations

import argparse
import json
import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCALE = ROOT / "fastlane" / "metadata" / "android" / "en-US"
IMAGES = LOCALE / "images"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


class ListingError(RuntimeError):
    pass


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def read_text(path: Path) -> str:
    if not path.is_file():
        raise ListingError(f"missing required file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def png_info(path: Path) -> tuple[int, int, int, int]:
    if not path.is_file():
        raise ListingError(f"missing image: {rel(path)}")
    with path.open("rb") as handle:
        signature = handle.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise ListingError(f"{rel(path)} is not a PNG")
        length = struct.unpack(">I", handle.read(4))[0]
        chunk_type = handle.read(4)
        if chunk_type != b"IHDR" or length != 13:
            raise ListingError(f"{rel(path)} has no valid IHDR chunk")
        width, height, bit_depth, color_type = struct.unpack(">IIBB", handle.read(10))
    return width, height, bit_depth, color_type


def require_png(path: Path, width: int, height: int, color_type: int | None = None, max_bytes: int | None = None) -> None:
    actual_width, actual_height, bit_depth, actual_color_type = png_info(path)
    if (actual_width, actual_height) != (width, height):
        raise ListingError(
            f"{rel(path)} dimensions are {actual_width}x{actual_height}, expected {width}x{height}"
        )
    if bit_depth != 8:
        raise ListingError(f"{rel(path)} must be 8-bit PNG, got bit depth {bit_depth}")
    if color_type is not None and actual_color_type != color_type:
        raise ListingError(f"{rel(path)} PNG color type is {actual_color_type}, expected {color_type}")
    if max_bytes is not None and path.stat().st_size > max_bytes:
        raise ListingError(f"{rel(path)} is {path.stat().st_size} bytes, expected <= {max_bytes}")


def require_text_bounds() -> None:
    title = read_text(LOCALE / "title.txt").strip()
    short = read_text(LOCALE / "short_description.txt").strip()
    full = read_text(LOCALE / "full_description.txt").strip()
    if not title or len(title) > 30:
        raise ListingError("title.txt must be 1-30 characters")
    if not short or len(short) > 80:
        raise ListingError("short_description.txt must be 1-80 characters")
    if not full or len(full) > 4000:
        raise ListingError("full_description.txt must be 1-4000 characters")


def require_screenshots(kind: str, expected_size: tuple[int, int], minimum: int = 4) -> list[Path]:
    directory = IMAGES / kind
    if not directory.is_dir():
        raise ListingError(f"missing screenshot directory: {rel(directory)}")
    files = sorted(directory.glob("*.png"))
    if len(files) < minimum:
        raise ListingError(f"{rel(directory)} must contain at least {minimum} PNG screenshots")
    for path in files:
        require_png(path, expected_size[0], expected_size[1], color_type=2, max_bytes=8 * 1024 * 1024)
    return files


def require_inventory(image_paths: list[Path]) -> None:
    path = IMAGES / "asset_inventory.json"
    inventory = json.loads(read_text(path))
    if inventory.get("schema") != "com.novacut.play-listing-assets.v1":
        raise ListingError("asset_inventory.json has an unexpected schema")
    entries = inventory.get("assets")
    if not isinstance(entries, list):
        raise ListingError("asset_inventory.json must contain an assets list")

    by_path = {entry.get("path"): entry for entry in entries if isinstance(entry, dict)}
    expected = {str(image.relative_to(IMAGES)).replace("\\", "/") for image in image_paths}
    missing = sorted(expected - set(by_path))
    if missing:
        raise ListingError(f"asset_inventory.json is missing entries for: {', '.join(missing)}")
    for relative_path in sorted(expected):
        entry = by_path[relative_path]
        for field in ("altText", "caption", "source"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ListingError(f"{relative_path} inventory entry must include {field}")
        source = IMAGES / entry["source"]
        if not source.is_file():
            raise ListingError(f"{relative_path} source SVG is missing: {entry['source']}")


def require_privacy_docs() -> None:
    privacy_url = read_text(LOCALE / "privacy_policy_url.txt").strip()
    if not privacy_url.startswith("https://"):
        raise ListingError("privacy_policy_url.txt must contain an https URL")

    privacy = read_text(ROOT / "docs" / "privacy-policy.md").lower()
    for required in ("novacut", "contact", "deletion", "retention"):
        if required not in privacy:
            raise ListingError(f"docs/privacy-policy.md must mention {required}")

    data_safety = read_text(ROOT / "docs" / "play-data-safety.md")
    manifest = read_text(MANIFEST)
    permissions = sorted(set(re.findall(r'android:name="(android\.permission\.[A-Z_]+)"', manifest)))
    missing = [permission for permission in permissions if permission not in data_safety]
    if missing:
        raise ListingError(f"docs/play-data-safety.md is missing manifest permissions: {', '.join(missing)}")
    for required in (
        "Project content",
        "Media metadata",
        "Downloaded ML models",
        "Diagnostic logs",
        "Crash records",
        "Cloud generative video calls",
        "AI usage ledger",
    ):
        if required not in data_safety:
            raise ListingError(f"docs/play-data-safety.md is missing dashboard category: {required}")


def validate() -> None:
    require_text_bounds()
    icon = IMAGES / "icon.png"
    feature = IMAGES / "featureGraphic.png"
    require_png(icon, 512, 512, color_type=6, max_bytes=1024 * 1024)
    require_png(feature, 1024, 500, color_type=2)
    screenshots = []
    screenshots.extend(require_screenshots("phoneScreenshots", (1080, 1920)))
    screenshots.extend(require_screenshots("tenInchScreenshots", (1920, 1080)))
    require_inventory([icon, feature, *screenshots])
    require_privacy_docs()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quiet", action="store_true", help="suppress success output")
    args = parser.parse_args()
    try:
        validate()
    except ListingError as error:
        print(f"play listing validation failed: {error}", file=sys.stderr)
        return 1
    if not args.quiet:
        print("Play listing assets, privacy policy, and Data safety worksheet verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
Generate the committed Google Play listing PNG assets from deterministic SVGs.

This script is intentionally optional for CI. The generated PNGs are committed,
while scripts/validate_play_listing_assets.py performs the release gate with
only the Python standard library.
"""
from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
IMAGES = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"
SOURCE = IMAGES / "_source"


PALETTE = {
    "bg": "#14161f",
    "panel": "#1f2430",
    "panel2": "#273044",
    "line": "#384052",
    "text": "#f4f7fb",
    "muted": "#a9b2c3",
    "cyan": "#89dceb",
    "coral": "#fab387",
    "rose": "#f5c2e7",
    "green": "#a6e3a1",
    "amber": "#f9e2af",
    "blue": "#8aadf4",
    "red": "#f38ba8",
}


def esc(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def text(x: int, y: int, value: str, size: int, fill: str = "text", weight: int = 600) -> str:
    return (
        f'<text x="{x}" y="{y}" fill="{PALETTE[fill]}" '
        f'font-family="Inter,Segoe UI,Arial,sans-serif" font-size="{size}" '
        f'font-weight="{weight}">{esc(value)}</text>'
    )


def rect(x: int, y: int, w: int, h: int, fill: str, rx: int = 0, stroke: str | None = None) -> str:
    stroke_attr = f' stroke="{PALETTE[stroke]}" stroke-width="2"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{PALETTE[fill]}"{stroke_attr}/>'


def line(x1: int, y1: int, x2: int, y2: int, fill: str = "line", width: int = 3) -> str:
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{PALETTE[fill]}" stroke-width="{width}"/>'


def logo_mark(cx: int, cy: int, scale: float = 1.0) -> str:
    x = cx - 118 * scale
    y = cy - 118 * scale
    return f"""<g transform="translate({x:.2f},{y:.2f}) scale({scale:.4f})">
      <circle cx="118" cy="118" r="112" fill="#273044"/>
      <circle cx="118" cy="108" r="82" fill="#1889dceb"/>
      <rect x="52" y="44" width="38" height="148" rx="12" fill="#f5e0dc"/>
      <rect x="148" y="44" width="38" height="148" rx="12" fill="#e6eeff"/>
      <path d="M92 44 H126 C134 44 141 49 144 57 L190 178 C194 190 185 202 172 202 H137 C128 202 121 196 118 188 L72 67 C68 55 78 44 92 44 Z" fill="#fab387"/>
      <path d="M108 34 H132 C142 34 150 42 151 52 L168 184 C170 197 160 208 147 208 H123 C113 208 105 200 104 190 L87 58 C85 45 95 34 108 34 Z" fill="#89dceb"/>
    </g>"""


def frame_svg(width: int, height: int, body: str, background: str = "bg") -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <rect width="{width}" height="{height}" fill="{PALETTE[background]}"/>
  {body}
</svg>
"""


def phone_chrome(title: str, body: str) -> str:
    return "\n".join(
        [
            rect(46, 56, 988, 1808, "bg", 54, "line"),
            rect(46, 56, 988, 104, "panel", 54),
            text(86, 124, "ClearCut", 34, "text", 700),
            text(816, 124, title, 24, "muted", 500),
            rect(502, 92, 76, 10, "line", 5),
            body,
        ]
    )


def tablet_chrome(title: str, body: str) -> str:
    return "\n".join(
        [
            rect(42, 42, 1836, 996, "bg", 38, "line"),
            rect(42, 42, 1836, 86, "panel", 38),
            text(82, 98, "ClearCut", 30, "text", 700),
            text(1520, 98, title, 22, "muted", 500),
            body,
        ]
    )


def gallery_phone() -> str:
    cards = []
    for i, (name, color) in enumerate(
        [("Travel cut", "cyan"), ("Creator reel", "coral"), ("Client review", "green")]
    ):
        y = 338 + i * 260
        cards += [
            rect(86, y, 908, 214, "panel", 24, "line"),
            rect(118, y + 34, 220, 146, color, 18),
            text(372, y + 82, name, 34, "text", 700),
            text(372, y + 128, "12 clips - 4K timeline - autosaved", 22, "muted", 500),
            rect(372, y + 152, 150, 26, "panel2", 13),
            rect(544, y + 152, 120, 26, "panel2", 13),
        ]
    return frame_svg(
        1080,
        1920,
        phone_chrome(
            "Projects",
            "\n".join(
                [
                    text(86, 252, "Projects", 58, "text", 750),
                    text(86, 302, "Local editing, no account required", 25, "muted", 500),
                    *cards,
                    rect(734, 1632, 260, 78, "coral", 28),
                    text(772, 1684, "New project", 28, "bg", 800),
                ]
            ),
        ),
    )


def editor_phone() -> str:
    timeline = []
    for i, color in enumerate(["cyan", "coral", "green", "rose", "blue"]):
        x = 118 + i * 166
        timeline += [rect(x, 1300, 144, 96, color, 14), rect(x, 1430, 144, 42, "panel2", 12)]
    return frame_svg(
        1080,
        1920,
        phone_chrome(
            "Editor",
            "\n".join(
                [
                    rect(86, 208, 908, 620, "panel", 28),
                    rect(132, 254, 816, 462, "panel2", 20),
                    logo_mark(540, 485, 1.05),
                    text(132, 770, "Preview ready - 4K HEVC", 25, "muted", 500),
                    rect(86, 884, 908, 328, "panel", 24, "line"),
                    text(122, 938, "Tools", 30, "text", 700),
                    rect(122, 974, 152, 66, "cyan", 22),
                    rect(298, 974, 152, 66, "coral", 22),
                    rect(474, 974, 152, 66, "green", 22),
                    rect(650, 974, 152, 66, "rose", 22),
                    text(146, 1017, "Cut", 24, "bg", 800),
                    text(326, 1017, "Color", 24, "bg", 800),
                    text(500, 1017, "Audio", 24, "bg", 800),
                    text(674, 1017, "Titles", 24, "bg", 800),
                    rect(86, 1250, 908, 298, "panel", 24),
                    *timeline,
                    line(504, 1246, 504, 1536, "red", 5),
                ]
            ),
        ),
    )


def export_phone() -> str:
    return frame_svg(
        1080,
        1920,
        phone_chrome(
            "Export",
            "\n".join(
                [
                    rect(86, 214, 908, 392, "panel", 28),
                    text(126, 292, "Export confidence", 42, "text", 750),
                    rect(126, 338, 230, 58, "green", 20),
                    text(154, 377, "Ready", 25, "bg", 800),
                    text(126, 454, "HEVC 4K - HDR metadata preserved", 27, "text", 650),
                    text(126, 502, "Audio loudness and codec checks passed", 23, "muted", 500),
                    rect(86, 674, 908, 646, "panel", 28, "line"),
                    text(126, 746, "Format", 30, "text", 700),
                    rect(126, 786, 240, 62, "cyan", 18),
                    text(158, 828, "H.265", 26, "bg", 800),
                    text(126, 930, "Resolution", 30, "text", 700),
                    rect(126, 970, 240, 62, "coral", 18),
                    text(158, 1012, "4K", 26, "bg", 800),
                    text(126, 1114, "Destinations", 30, "text", 700),
                    rect(126, 1154, 280, 62, "panel2", 18),
                    rect(430, 1154, 280, 62, "panel2", 18),
                    text(158, 1196, "Gallery", 24, "text", 650),
                    text(462, 1196, "Share sheet", 24, "text", 650),
                    rect(734, 1588, 260, 82, "green", 28),
                    text(802, 1642, "Export", 28, "bg", 800),
                ]
            ),
        ),
    )


def privacy_phone() -> str:
    rows = []
    for i, (name, color) in enumerate(
        [("Project content", "cyan"), ("Crash records", "coral"), ("ML models", "rose"), ("Cloud tools off", "green")]
    ):
        y = 370 + i * 180
        rows += [
            rect(86, y, 908, 132, "panel", 24, "line"),
            rect(124, y + 38, 56, 56, color, 16),
            text(208, y + 72, name, 28, "text", 700),
            text(208, y + 108, "Export - delete - local controls", 20, "muted", 500),
        ]
    return frame_svg(
        1080,
        1920,
        phone_chrome(
            "Privacy",
            "\n".join(
                [
                    text(86, 254, "Privacy dashboard", 48, "text", 750),
                    text(86, 304, "What ClearCut stores and how to clear it", 24, "muted", 500),
                    *rows,
                    rect(86, 1212, 908, 256, "panel", 24),
                    text(126, 1280, "Model downloads are optional", 32, "text", 700),
                    text(126, 1328, "Core editing stays local. Optional network prompts.", 23, "muted", 500),
                ]
            ),
        ),
    )


def tablet_editor() -> str:
    return frame_svg(
        1920,
        1080,
        tablet_chrome(
            "Tablet editor",
            "\n".join(
                [
                    rect(82, 164, 330, 804, "panel", 24, "line"),
                    text(116, 220, "Media", 30, "text", 700),
                    rect(116, 258, 250, 120, "cyan", 18),
                    rect(116, 404, 250, 120, "coral", 18),
                    rect(116, 550, 250, 120, "green", 18),
                    rect(456, 164, 954, 568, "panel", 24),
                    rect(514, 218, 838, 440, "panel2", 18),
                    logo_mark(933, 438, 1.1),
                    rect(456, 766, 1380, 202, "panel", 24),
                    rect(500, 820, 252, 76, "cyan", 14),
                    rect(774, 820, 252, 76, "coral", 14),
                    rect(1048, 820, 252, 76, "green", 14),
                    rect(1322, 820, 252, 76, "rose", 14),
                    line(960, 752, 960, 964, "red", 4),
                    rect(1448, 164, 388, 568, "panel", 24, "line"),
                    text(1488, 224, "Inspector", 30, "text", 700),
                    text(1488, 280, "Transform", 24, "muted", 500),
                    text(1488, 338, "Color", 24, "muted", 500),
                    text(1488, 396, "Audio", 24, "muted", 500),
                ]
            ),
        ),
    )


def tablet_media() -> str:
    return frame_svg(
        1920,
        1080,
        tablet_chrome(
            "Media manager",
            "\n".join(
                [
                    text(82, 190, "Relink, verify, and organize sources", 42, "text", 750),
                    rect(82, 246, 1756, 660, "panel", 26, "line"),
                    text(126, 318, "Source", 26, "muted", 600),
                    text(734, 318, "Status", 26, "muted", 600),
                    text(1134, 318, "Action", 26, "muted", 600),
                    *[
                        rect(126, 372 + i * 120, 540, 72, "panel2", 16)
                        + rect(734, 372 + i * 120, 220, 72, color, 16)
                        + rect(1134, 372 + i * 120, 300, 72, "bg", 16, "line")
                        for i, color in enumerate(["green", "amber", "cyan", "coral"])
                    ],
                    text(762, 418, "Verified", 24, "bg", 800),
                    text(762, 538, "Missing", 24, "bg", 800),
                    text(762, 658, "Local", 24, "bg", 800),
                    text(762, 778, "Proxy", 24, "bg", 800),
                ]
            ),
        ),
    )


def tablet_captions() -> str:
    return frame_svg(
        1920,
        1080,
        tablet_chrome(
            "Captions and AI",
            "\n".join(
                [
                    rect(82, 164, 830, 804, "panel", 24, "line"),
                    text(126, 226, "Caption review", 34, "text", 750),
                    rect(126, 280, 724, 110, "panel2", 18),
                    rect(126, 420, 724, 110, "panel2", 18),
                    rect(126, 560, 724, 110, "panel2", 18),
                    text(166, 346, "Speaker 1: tighten intro timing", 25, "text", 600),
                    text(166, 486, "Speaker 2: keep SDH tag separate", 25, "text", 600),
                    text(166, 626, "Review translated target row", 25, "text", 600),
                    rect(956, 164, 880, 804, "panel", 24, "line"),
                    text(1000, 226, "AI tools", 34, "text", 750),
                    rect(1000, 286, 330, 92, "cyan", 22),
                    rect(1362, 286, 330, 92, "coral", 22),
                    rect(1000, 422, 330, 92, "green", 22),
                    rect(1362, 422, 330, 92, "rose", 22),
                    text(1040, 344, "Auto captions", 25, "bg", 800),
                    text(1402, 344, "Smart crop", 25, "bg", 800),
                    text(1040, 480, "Denoise", 25, "bg", 800),
                    text(1402, 480, "Model gate", 25, "bg", 800),
                    text(1000, 626, "Every model shows source, license, size, and install state.", 25, "muted", 500),
                ]
            ),
        ),
    )


def tablet_export() -> str:
    return frame_svg(
        1920,
        1080,
        tablet_chrome(
            "Export review",
            "\n".join(
                [
                    rect(82, 164, 520, 804, "panel", 24, "line"),
                    text(126, 226, "Presets", 34, "text", 750),
                    rect(126, 282, 360, 76, "cyan", 20),
                    rect(126, 386, 360, 76, "panel2", 20),
                    rect(126, 490, 360, 76, "panel2", 20),
                    text(158, 330, "YouTube 4K", 25, "bg", 800),
                    text(158, 434, "TikTok 1080p", 25, "text", 650),
                    text(158, 538, "Audio only", 25, "text", 650),
                    rect(646, 164, 1190, 804, "panel", 24, "line"),
                    text(694, 232, "Release checks", 40, "text", 750),
                    rect(694, 300, 306, 76, "green", 20),
                    rect(1030, 300, 306, 76, "green", 20),
                    rect(1366, 300, 306, 76, "amber", 20),
                    text(734, 348, "Codec ready", 24, "bg", 800),
                    text(1070, 348, "HDR safe", 24, "bg", 800),
                    text(1406, 348, "Review AI", 24, "bg", 800),
                    rect(694, 446, 900, 90, "panel2", 18),
                    rect(694, 568, 900, 90, "panel2", 18),
                    rect(694, 690, 900, 90, "panel2", 18),
                    text(734, 502, "No missing media references", 25, "text", 600),
                    text(734, 624, "Loudness and bitrate fit target platform", 25, "text", 600),
                    text(734, 746, "AI disclosure sidecar and manual platform reminders", 25, "text", 600),
                ]
            ),
        ),
    )


def feature_graphic() -> str:
    return frame_svg(
        1024,
        500,
        "\n".join(
            [
                rect(0, 0, 1024, 500, "bg"),
                '<circle cx="810" cy="88" r="176" fill="#123848"/>',
                '<circle cx="104" cy="430" r="180" fill="#35263d"/>',
                logo_mark(188, 250, 0.92),
                text(352, 176, "ClearCut", 60, "text", 800),
                text(356, 230, "Private Android video editing", 28, "cyan", 700),
                rect(356, 274, 164, 52, "cyan", 18),
                rect(540, 274, 188, 52, "coral", 18),
                rect(748, 274, 150, 52, "green", 18),
                text(386, 309, "Timeline", 21, "bg", 800),
                text(570, 309, "AI tools", 21, "bg", 800),
                text(780, 309, "Export", 21, "bg", 800),
                rect(356, 364, 534, 6, "line", 3),
                rect(356, 388, 402, 6, "line", 3),
            ]
        ),
    )


def icon_svg() -> str:
    return """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1f2430"/>
      <stop offset="1" stop-color="#11131b"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="512" height="512" rx="112" fill="url(#bg)"/>
  <circle cx="256" cy="226" r="168" fill="#1889dceb"/>
  <rect x="128" y="112" width="56" height="288" rx="20" fill="#f5e0dc"/>
  <rect x="328" y="112" width="56" height="288" rx="20" fill="#e6eeff"/>
  <path d="M184 112 H252 C266 112 278 121 282 134 L360 366 C367 388 351 410 328 410 H260 C245 410 232 400 228 386 L151 154 C144 132 160 112 184 112 Z" fill="#fab387"/>
  <path d="M222 88 H265 C282 88 296 101 298 118 L326 394 C328 416 311 434 289 434 H246 C229 434 215 421 213 404 L185 128 C183 106 200 88 222 88 Z" fill="#89dceb"/>
</svg>
"""


ASSETS = [
    ("icon.png", (512, 512), icon_svg, "PNG32", "ClearCut app icon", "High-resolution Play Store icon."),
    ("featureGraphic.png", (1024, 500), feature_graphic, "PNG24", "ClearCut feature graphic", "Feature graphic for the main store listing."),
    ("phoneScreenshots/01_project_gallery.png", (1080, 1920), gallery_phone, "PNG24", "Project gallery screen", "Project gallery with local autosaved projects."),
    ("phoneScreenshots/02_editor_timeline.png", (1080, 1920), editor_phone, "PNG24", "Editor timeline screen", "Preview, editing tools, and multi-track timeline."),
    ("phoneScreenshots/03_export_confidence.png", (1080, 1920), export_phone, "PNG24", "Export settings screen", "Export confidence and platform settings."),
    ("phoneScreenshots/04_privacy_models.png", (1080, 1920), privacy_phone, "PNG24", "Privacy dashboard screen", "Local data controls and optional model downloads."),
    ("tenInchScreenshots/01_tablet_editor.png", (1920, 1080), tablet_editor, "PNG24", "Tablet editor layout", "Three-pane tablet editor layout."),
    ("tenInchScreenshots/02_media_manager.png", (1920, 1080), tablet_media, "PNG24", "Media manager table", "Media relink and verification workflow."),
    ("tenInchScreenshots/03_captions_ai.png", (1920, 1080), tablet_captions, "PNG24", "Caption and AI tools", "Caption review and model-gated AI tools."),
    ("tenInchScreenshots/04_export_review.png", (1920, 1080), tablet_export, "PNG24", "Tablet export review", "Export readiness and disclosure review."),
]


def render(source: Path, target: Path, png_type: str) -> None:
    magick = shutil.which("magick")
    if not magick:
        raise SystemExit("ImageMagick 'magick' was not found; committed PNGs cannot be regenerated here.")
    target.parent.mkdir(parents=True, exist_ok=True)
    output = f"{png_type}:{target}"
    subprocess.run([magick, "-background", "none", str(source), output], check=True)


def main() -> int:
    SOURCE.mkdir(parents=True, exist_ok=True)
    inventory = {
        "schema": "com.clearcut.play-listing-assets.v1",
        "updated": "2026-06-04",
        "assets": [],
    }

    for relative, dimensions, svg_factory, png_type, alt, caption in ASSETS:
        svg_path = SOURCE / (relative.replace("/", "_").removesuffix(".png") + ".svg")
        png_path = IMAGES / relative
        svg_path.write_text(svg_factory(), encoding="utf-8")
        render(svg_path, png_path, png_type)
        inventory["assets"].append(
            {
                "path": relative,
                "width": dimensions[0],
                "height": dimensions[1],
                "altText": alt,
                "caption": caption,
                "source": str(svg_path.relative_to(IMAGES)).replace("\\", "/"),
            }
        )

    (IMAGES / "asset_inventory.json").write_text(
        json.dumps(inventory, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"generated {len(ASSETS)} Play listing PNG assets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

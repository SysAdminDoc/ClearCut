#!/usr/bin/env python3
"""Validate public release copy against ClearCut's shipped capability gates."""
from __future__ import annotations

import argparse
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AI_REQUIREMENTS = ROOT / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "AiToolRequirements.kt"
C2PA_ENGINE = ROOT / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "C2paExportEngine.kt"
DIRECT_PUBLISH_ENGINE = ROOT / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "DirectPublishEngine.kt"


class ClaimError(RuntimeError):
    pass


@dataclass(frozen=True)
class PublicSurface:
    path: Path
    text: str

    @property
    def label(self) -> str:
        try:
            return str(self.path.relative_to(ROOT)).replace("\\", "/")
        except ValueError:
            return str(self.path)


@dataclass(frozen=True)
class ClaimRule:
    rule_id: str
    category: str
    expected_gate: str
    replacement_copy: str


@dataclass(frozen=True)
class ClaimViolation:
    rule: ClaimRule
    surface: str
    line: int
    text: str

    def explain(self) -> str:
        return (
            f"{self.rule.rule_id} [{self.rule.category}] {self.surface}:{self.line}: "
            f"{self.text.strip()}\n"
            f"  expected: {self.rule.expected_gate}\n"
            f"  replacement: {self.rule.replacement_copy}"
        )


RULES = {
    "ai_caption_language_gate": ClaimRule(
        "ai_caption_language_gate",
        "AI_ACTIVE",
        "Auto Captions public copy must match the active whisper.tiny.en.onnx registry entry.",
        "Auto Captions: ONNX Runtime Whisper tiny.en (English; multilingual path gated).",
    ),
    "dependency_missing_ai_tools": ClaimRule(
        "dependency_missing_ai_tools",
        "AI_PLANNED",
        "Dependency-missing AI tools must be qualified as planned, gated, or unavailable.",
        "AI-assisted tools list only ready/downloadable tools; planned tools stay labeled.",
    ),
    "content_credentials_draft_gate": ClaimRule(
        "content_credentials_draft_gate",
        "DRAFT_CREDENTIAL",
        "Content Credentials wording must say draft/unsigned/not verifiable unless signing is ready.",
        "Unsigned C2PA draft sidecar or manual disclosure reminder.",
    ),
    "share_handoff_gate": ClaimRule(
        "share_handoff_gate",
        "API_UPLOAD",
        "Publishing copy must describe platform presets or share handoff until API upload is implemented.",
        "Platform presets and Android share handoff with manual disclosure reminders.",
    ),
    "offline_network_boundary": ClaimRule(
        "offline_network_boundary",
        "NETWORK_OPTIONAL",
        "Offline/no-internet copy must keep optional model, provider, cloud, signing, LAN, and share paths explicit.",
        "Core editing works offline. Network use is explicit for optional model downloads, provider-backed assets, cloud-capable tools, remote signing, LAN streaming, and user-directed share/export handoff.",
    ),
    "screenshot_disclosure_gate": ClaimRule(
        "screenshot_disclosure_gate",
        "PLATFORM_PRESET",
        "Screenshot disclosure copy must not imply structured platform API fields are shipped.",
        "AI disclosure sidecar and manual platform reminders.",
    ),
}

DEPENDENCY_MISSING_TERMS = {
    "video stabilization": "AI_STABILIZE",
    "stabilization": "AI_STABILIZE",
    "style transfer": "AI_STYLE",
    "upscaling": "AI_UPSCALE",
    "frame interpolation": "FRAME_INTERP",
    "tap-to-segment": "TAP_SEGMENT",
    "offline tts": "TTS_OFFLINE",
    "piper tts": "TTS_OFFLINE",
    "stem separation": "STEM_SEPARATION",
    "voice clone": "VOICE_CLONE",
    "caption translation": "CAPTION_TRANSLATE",
}
QUALIFIERS = ("planned", "future", "gated", "requires", "dependency", "not available", "unavailable", "stub")
OFFLINE_REQUIRED_TERMS = (
    "optional model downloads",
    "provider-backed assets",
    "cloud-capable tools",
    "remote signing",
    "lan streaming",
    "share/export handoff",
)


def read_text(path: Path) -> str:
    if not path.is_file():
        raise ClaimError(f"missing required file: {path}")
    return path.read_text(encoding="utf-8")


def public_surfaces(root: Path = ROOT) -> list[PublicSurface]:
    fastlane = root / "fastlane" / "metadata" / "android" / "en-US"
    images = fastlane / "images"
    paths = [
        root / "README.md",
        fastlane / "title.txt",
        fastlane / "short_description.txt",
        fastlane / "full_description.txt",
        images / "asset_inventory.json",
        root / "scripts" / "generate_play_listing_assets.py",
    ]
    paths.extend(sorted((images / "_source").glob("*.svg")))
    return [PublicSurface(path, read_text(path)) for path in paths if path.is_file()]


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def context_line(text: str, offset: int) -> str:
    start = text.rfind("\n", 0, offset) + 1
    end = text.find("\n", offset)
    return text[start : len(text) if end == -1 else end]


def has_qualifier(text: str) -> bool:
    lowered = text.lower()
    return any(qualifier in lowered for qualifier in QUALIFIERS)


def tool_availability(source: str, tool: str) -> str | None:
    marker = f"Tool.{tool} to ToolRequirement("
    start = source.find(marker)
    if start == -1:
        return None
    next_start = source.find("\n        Tool.", start + len(marker))
    block = source[start:] if next_start == -1 else source[start:next_start]
    match = re.search(r"availability\s*=\s*Availability\.([A-Z_]+)", block)
    return match.group(1) if match else None


def active_caption_model(source: str) -> str | None:
    marker = "Tool.AUTO_CAPTIONS to ToolRequirement("
    start = source.find(marker)
    if start == -1:
        return None
    next_start = source.find("\n        Tool.", start + len(marker))
    block = source[start:] if next_start == -1 else source[start:next_start]
    match = re.search(r'modelRegistryId\s*=\s*"([^"]+)"', block)
    return match.group(1) if match else None


def c2pa_signing_ready(source: str) -> bool:
    return "SigningAvailability.READY" in source and "No C2PA signing library is bundled" not in source


def direct_api_upload_ready(source: str) -> bool:
    return "Method.SHARE_INTENT" not in source or "Today only the share-intent fallback is wired" not in source


def validate(root: Path = ROOT) -> list[ClaimViolation]:
    ai_source = read_text(root / AI_REQUIREMENTS.relative_to(ROOT))
    c2pa_source = read_text(root / C2PA_ENGINE.relative_to(ROOT))
    direct_publish_source = read_text(root / DIRECT_PUBLISH_ENGINE.relative_to(ROOT))
    caption_model = active_caption_model(ai_source)
    signing_ready = c2pa_signing_ready(c2pa_source)
    api_upload_ready = direct_api_upload_ready(direct_publish_source)
    violations: list[ClaimViolation] = []

    for surface in public_surfaces(root):
        lowered = surface.text.lower()
        if caption_model == "whisper.tiny.en.onnx":
            for match in re.finditer(r"auto captions[^\n]*(multilingual|99 languages)", surface.text, re.IGNORECASE):
                line = context_line(surface.text, match.start())
                if not has_qualifier(line):
                    violations.append(ClaimViolation(RULES["ai_caption_language_gate"], surface.label, line_number(surface.text, match.start()), line))

        for term, tool in DEPENDENCY_MISSING_TERMS.items():
            if tool_availability(ai_source, tool) != "DEPENDENCY_MISSING":
                continue
            for match in re.finditer(re.escape(term), lowered):
                line = context_line(surface.text, match.start())
                if not has_qualifier(line):
                    violations.append(ClaimViolation(RULES["dependency_missing_ai_tools"], surface.label, line_number(surface.text, match.start()), line))

        if not signing_ready:
            for match in re.finditer(r"content credentials", lowered):
                line = context_line(surface.text, match.start())
                if not any(word in line.lower() for word in ("draft", "unsigned", "not verifiable", "unavailable", "planned", "future")):
                    violations.append(ClaimViolation(RULES["content_credentials_draft_gate"], surface.label, line_number(surface.text, match.start()), line))

        if not api_upload_ready:
            for pattern in (r"direct publish", r"direct upload", r"upload to (youtube|tiktok|instagram|threads|x|linkedin)", r"publish to (youtube|tiktok|instagram|threads|x|linkedin)", r"one-tap upload"):
                for match in re.finditer(pattern, lowered):
                    line = context_line(surface.text, match.start())
                    if not any(word in line.lower() for word in ("share", "handoff", "preset", "manual", "planned", "future")):
                        violations.append(ClaimViolation(RULES["share_handoff_gate"], surface.label, line_number(surface.text, match.start()), line))

        for phrase in ("no internet", "works offline", "editing works offline", "core editing works offline"):
            for match in re.finditer(re.escape(phrase), lowered):
                paragraph_start = surface.text.rfind("\n\n", 0, match.start()) + 2
                paragraph_end = surface.text.find("\n\n", match.start())
                paragraph = surface.text[paragraph_start : len(surface.text) if paragraph_end == -1 else paragraph_end].lower()
                if any(term not in paragraph for term in OFFLINE_REQUIRED_TERMS):
                    violations.append(ClaimViolation(RULES["offline_network_boundary"], surface.label, line_number(surface.text, match.start()), context_line(surface.text, match.start())))

        for match in re.finditer(r"disclosure metadata ready for share targets", lowered):
            violations.append(ClaimViolation(RULES["screenshot_disclosure_gate"], surface.label, line_number(surface.text, match.start()), context_line(surface.text, match.start())))

    deduped: dict[tuple[str, str, int, str], ClaimViolation] = {}
    for violation in violations:
        deduped[(violation.rule.rule_id, violation.surface, violation.line, violation.text)] = violation
    return list(deduped.values())


def write_fixture(root: Path, relative: str, text: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_minimal_sources(root: Path, readme: str, full_description: str, svg_text: str = "") -> None:
    write_fixture(root, "README.md", readme)
    write_fixture(root, "fastlane/metadata/android/en-US/title.txt", "ClearCut\n")
    write_fixture(root, "fastlane/metadata/android/en-US/short_description.txt", "Private Android video editor.\n")
    write_fixture(root, "fastlane/metadata/android/en-US/full_description.txt", full_description)
    write_fixture(root, "fastlane/metadata/android/en-US/images/asset_inventory.json", '{"assets": []}\n')
    write_fixture(root, "fastlane/metadata/android/en-US/images/_source/export.svg", svg_text)
    write_fixture(root, "scripts/generate_play_listing_assets.py", svg_text)
    write_fixture(
        root,
        "app/src/main/java/com/novacut/editor/engine/AiToolRequirements.kt",
        '''
        Tool.AUTO_CAPTIONS to ToolRequirement(modelRegistryId = "whisper.tiny.en.onnx", availability = Availability.MODEL_DOWNLOAD_REQUIRED)
        Tool.AI_STABILIZE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.AI_STYLE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.AI_UPSCALE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.FRAME_INTERP to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.TAP_SEGMENT to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.TTS_OFFLINE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.STEM_SEPARATION to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.VOICE_CLONE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        Tool.CAPTION_TRANSLATE to ToolRequirement(availability = Availability.DEPENDENCY_MISSING)
        ''',
    )
    write_fixture(root, "app/src/main/java/com/novacut/editor/engine/C2paExportEngine.kt", '"Content Credentials are unavailable because no C2PA signing library is bundled."\n')
    write_fixture(root, "app/src/main/java/com/novacut/editor/engine/DirectPublishEngine.kt", "Today only the share-intent fallback is wired. Method.SHARE_INTENT\n")


def run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_minimal_sources(
            root,
            readme="| **Auto Captions** | ONNX Runtime Whisper (multilingual, 99 languages) | Yes |\n",
            full_description="- On-device AI tools: video stabilization, style transfer\nClearCut requires no internet connection for editing.\n",
            svg_text="<text>Disclosure metadata ready for share targets</text>\n",
        )
        rule_ids = {violation.rule.rule_id for violation in validate(root)}
        expected = {"ai_caption_language_gate", "dependency_missing_ai_tools", "offline_network_boundary", "screenshot_disclosure_gate"}
        missing = expected - rule_ids
        if missing:
            raise ClaimError(f"self-test missing expected violations: {sorted(missing)}")

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_minimal_sources(
            root,
            readme="| **Auto Captions** | ONNX Runtime Whisper tiny.en (English; multilingual path gated) | Yes |\n",
            full_description=(
                "AI-assisted tools with clear gates: auto captions, background removal, object removal, scene detection, "
                "smart crop, motion tracking, auto color, and audio denoise; planned stabilization and style transfer "
                "stay labeled until dependency review.\n\n"
                "Core editing works offline. Network use is explicit for optional model downloads, provider-backed assets, "
                "cloud-capable tools, remote signing, LAN streaming, and user-directed share/export handoff.\n"
            ),
            svg_text="<text>AI disclosure sidecar and manual platform reminders</text>\n",
        )
        violations = validate(root)
        if violations:
            raise ClaimError("self-test expected no violations:\n" + "\n".join(v.explain() for v in violations))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--explain", action="store_true", help="print detailed violation context")
    parser.add_argument("--self-test", action="store_true", help="run built-in fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("public claim validator self-tests passed.")
            return 0
        violations = validate()
    except ClaimError as error:
        print(f"public claim validation failed: {error}", file=sys.stderr)
        return 1
    if violations:
        print(f"public claim validation failed: {len(violations)} issue(s)", file=sys.stderr)
        for violation in violations:
            print(violation.explain() if args.explain else f"- {violation.rule.rule_id}: {violation.surface}:{violation.line}: {violation.text.strip()}", file=sys.stderr)
        return 1
    print("Public release claims verified against shipped capability gates.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

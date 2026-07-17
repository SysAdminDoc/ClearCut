#!/usr/bin/env python3
"""Validate Android 17 background-audio hardening assumptions."""
from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


class AudioPolicyError(RuntimeError):
    pass


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    if not path.is_file():
        raise AudioPolicyError(f"missing required file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def service_block(manifest: str, service_name: str) -> str:
    pattern = re.compile(r"<service\b(?=[^>]*android:name=\"" + re.escape(service_name) + r"\")[\s\S]*?/>")
    match = pattern.search(manifest)
    if not match:
        raise AudioPolicyError(f"AndroidManifest.xml is missing service {service_name}")
    return match.group(0)


def require_before(source: str, earlier: str, later: str, path: Path) -> None:
    earlier_index = source.find(earlier)
    later_index = source.find(later)
    if earlier_index == -1 or later_index == -1 or earlier_index > later_index:
        raise AudioPolicyError(f"{rel(path)} must call {earlier!r} before {later!r}")


def verify_export_service_has_no_background_audio(root: Path = ROOT) -> None:
    path = root / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "ExportService.kt"
    source = read_text(path)
    forbidden_tokens = (
        "TextToSpeech",
        "MediaRecorder",
        "AudioRecord",
        "AudioTrack",
        "TtsEngine",
        "VoiceoverRecorder",
        "requestAudioFocus(",
        "speak(",
        "synthesizeToFile(",
        "setAudioSource(",
    )
    violations = [token for token in forbidden_tokens if token in source]
    if violations:
        raise AudioPolicyError(f"{rel(path)} must not perform background audio APIs: {', '.join(violations)}")


def verify_manifest_foreground_service_type(root: Path = ROOT) -> None:
    manifest_path = root / MANIFEST.relative_to(ROOT)
    manifest = read_text(manifest_path)
    block = service_block(manifest, ".engine.ExportService")
    allowed_types = (
        'android:foregroundServiceType="mediaProcessing"',
        # dataSync is the API <= 34 fallback: mediaProcessing is an API 35+
        # type, and Android 14 refuses an untyped startForeground.
        'android:foregroundServiceType="mediaProcessing|dataSync"',
    )
    if not any(declaration in block for declaration in allowed_types):
        raise AudioPolicyError(
            "ExportService must declare foregroundServiceType=\"mediaProcessing\" "
            "(optionally with the dataSync API<=34 fallback)"
        )
    if 'android:exported="false"' not in block:
        raise AudioPolicyError("ExportService must remain non-exported")
    if "FOREGROUND_SERVICE_MEDIA_PROCESSING" not in manifest:
        raise AudioPolicyError("manifest must declare FOREGROUND_SERVICE_MEDIA_PROCESSING")
    forbidden = ("FOREGROUND_SERVICE_MICROPHONE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK", "microphone", "mediaPlayback", "shortService")
    present = [token for token in forbidden if token in block or token in manifest]
    if present:
        raise AudioPolicyError(f"ExportService manifest must not claim background audio service types: {', '.join(present)}")


def verify_visible_audio_paths_request_focus(root: Path = ROOT) -> None:
    tts_path = root / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "TtsEngine.kt"
    voiceover_path = root / "app" / "src" / "main" / "java" / "com" / "novacut" / "editor" / "engine" / "VoiceoverRecorder.kt"
    tts = read_text(tts_path)
    voiceover = read_text(voiceover_path)
    require_before(tts, "requestPreviewAudioFocus()", "engine.speak(", tts_path)
    require_before(voiceover, "requestVoiceoverAudioFocus()", "setAudioSource(MediaRecorder.AudioSource.MIC)", voiceover_path)
    if "AudioManager.AUDIOFOCUS_REQUEST_GRANTED" not in tts or "AudioManager.AUDIOFOCUS_REQUEST_GRANTED" not in voiceover:
        raise AudioPolicyError("TTS and voiceover focus paths must check AUDIOFOCUS_REQUEST_GRANTED")


def verify_documented_smoke(root: Path = ROOT) -> None:
    readme = read_text(root / "README.md")
    required = (
        "Android 17 audio hardening",
        "adb shell cmd audio set-enable-hardening throw",
        "AudioHardening",
        "TTS preview",
        "voiceover",
        "export",
    )
    missing = [term for term in required if term.lower() not in readme.lower()]
    if missing:
        raise AudioPolicyError(f"README.md is missing Android 17 audio hardening smoke term(s): {', '.join(missing)}")


def validate(root: Path = ROOT) -> None:
    verify_export_service_has_no_background_audio(root)
    verify_manifest_foreground_service_type(root)
    verify_visible_audio_paths_request_focus(root)
    verify_documented_smoke(root)


def write_fixture(root: Path, relative: str, text: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_valid_fixture(root: Path, export_service: str = "class ExportService { fun run() = Unit }\n") -> None:
    write_fixture(root, "app/src/main/java/com/novacut/editor/engine/ExportService.kt", export_service)
    write_fixture(
        root,
        "app/src/main/AndroidManifest.xml",
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />
          <application>
            <service
              android:name=".engine.ExportService"
              android:foregroundServiceType="mediaProcessing|dataSync"
              android:exported="false" />
          </application>
        </manifest>
        """,
    )
    write_fixture(
        root,
        "app/src/main/java/com/novacut/editor/engine/TtsEngine.kt",
        "fun preview() { if (!requestPreviewAudioFocus()) return; engine.speak(text, queue, params, id) }\n"
        "AudioManager.AUDIOFOCUS_REQUEST_GRANTED\n",
    )
    write_fixture(
        root,
        "app/src/main/java/com/novacut/editor/engine/VoiceoverRecorder.kt",
        "fun startRecording() { if (!requestVoiceoverAudioFocus()) return; setAudioSource(MediaRecorder.AudioSource.MIC) }\n"
        "AudioManager.AUDIOFOCUS_REQUEST_GRANTED\n",
    )
    write_fixture(
        root,
        "README.md",
        "Android 17 audio hardening: run `adb shell cmd audio set-enable-hardening throw`, "
        "then exercise TTS preview, voiceover, and export while watching AudioHardening logs.\n",
    )


def run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_valid_fixture(root)
        validate(root)

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_valid_fixture(root, export_service="class ExportService { val t = TextToSpeech(context) }\n")
        try:
            validate(root)
        except AudioPolicyError:
            pass
        else:
            raise AudioPolicyError("self-test expected ExportService TextToSpeech use to fail")

    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_valid_fixture(root)
        write_fixture(
            root,
            "app/src/main/AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />
              <application>
                <service
                  android:name=".engine.ExportService"
                  android:foregroundServiceType="mediaProcessing|microphone"
                  android:exported="false" />
              </application>
            </manifest>
            """,
        )
        try:
            validate(root)
        except AudioPolicyError:
            pass
        else:
            raise AudioPolicyError("self-test expected microphone foreground-service type to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true", help="run built-in fixture checks")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_tests()
            print("Android audio API policy self-tests passed.")
            return 0
        validate()
    except AudioPolicyError as error:
        print(f"Android audio API policy validation failed: {error}", file=sys.stderr)
        return 1
    print("Android audio API policy verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

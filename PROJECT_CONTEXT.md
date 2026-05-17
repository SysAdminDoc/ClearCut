# NovaCut Project Context

Last consolidated: 2026-05-17.

This file is the committed project memory for future work. It reconciles the live repository, local instruction files, prior memory, current roadmap, and the deep research run in [.ai/research/2026-05-17](.ai/research/2026-05-17/).

## Identity and Product Direction

NovaCut is an Android video editor under package `com.novacut.editor`. The repo positions it as a privacy-first, offline-capable, mobile nonlinear editor with professional export, captioning, AI-assisted editing, media management, templates, and interoperability features.

Current live version evidence:

- [app/build.gradle.kts](app/build.gradle.kts): `compileSdk = 36`, `targetSdk = 36`, `versionCode = 146`, `versionName = "3.74.9"`.
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml): `app_version` is `v3.74.9`.
- [README.md](README.md) and [ROADMAP.md](ROADMAP.md) both describe the v3.74.x line.
- Current local `HEAD` during this consolidation was `ece3340` on `master`, 33 commits ahead of `origin/master`.

## Source of Truth

Use these files first:

- [README.md](README.md): product summary, install/build notes, feature list, and tech stack.
- [ROADMAP.md](ROADMAP.md): current prioritized roadmap and research-backed implementation queue.
- [CHANGELOG.md](CHANGELOG.md): shipped release history.
- [docs/models.md](docs/models.md): model registry, licensing, privacy, Play Asset Delivery, F-Droid, and activation gates.
- [docs/templates.md](docs/templates.md): template/plugin format and animation compatibility matrix.
- [app/build.gradle.kts](app/build.gradle.kts) and [gradle/libs.versions.toml](gradle/libs.versions.toml): build and dependency truth.

Tool-specific instruction files:

- [AGENTS.md](AGENTS.md) delegates to repo/global Claude instructions and is not tracked in Git.
- [CLAUDE.md](CLAUDE.md) and [.claude/CLAUDE.md](.claude/CLAUDE.md) contain useful workflow notes but include stale version/build claims. Treat them as local working notes, not current architectural truth.

## Architecture Snapshot

Primary stack:

- Kotlin Android app with Jetpack Compose and Material 3.
- Gradle wrapper 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.1.0.
- Media3 1.10.1 for playback, effects, export, and transformer flows.
- Room for metadata, DataStore for preferences, Hilt for dependency injection.
- ONNX Runtime Android 1.17.0 and MediaPipe Tasks Vision 0.10.14 are present for on-device model paths.
- WorkManager is available for background jobs.

High-level modules and patterns:

- `MainActivity` and Compose screens under `app/src/main/java/com/novacut/editor/screen`.
- Large editor state/control surface in `EditorViewModel`; expect this file to be broad and state-heavy.
- Engine classes under `app/src/main/java/com/novacut/editor/engine` are the main seam for roadmap features. Many rows are intentionally scaffolded with availability checks, pure planning helpers, or fallback behavior before native/model activation.
- Model and privacy posture are centered on explicit downloads, checksum metadata, local processing, and F-Droid-compatible alternatives.
- Project persistence combines Room metadata with JSON autosave/project files, depending on the feature surface.

## Current Strengths

- The roadmap is unusually detailed and maps many features to concrete touch points.
- Many future features already have model, policy, or planner scaffolds rather than only TODO comments.
- The repository has broad unit-test coverage around engine helpers, planners, and metadata models.
- The privacy posture is coherent: local-first by default, opt-in cloud paths, explicit model downloads, and F-Droid awareness.
- Cross-editor interoperability is already a first-class goal through FCPXML/OTIO/EDL-style planning.

## Current Gaps to Treat as Highest Leverage

1. 16 KB native library compliance and Android 16 release readiness.
   - `targetSdk = 36` makes native dependency alignment a release gate.
   - ONNX Runtime, MediaPipe, and future native libraries need repeatable page-size verification.

2. Diagnostic export UX.
   - Engine-level diagnostic export exists, but Settings needs a polished local-only flow.
   - This is the fastest trust/recovery improvement because media export failures are device- and source-specific.

3. Model registry closure.
   - `docs/models.md` still has `SHA TBD` rows and unresolved activation details.
   - Finish checksums, licenses, PAD/F-Droid posture, and validation tests before adding more large model payloads.

4. Dependency stabilization.
   - Media3 1.10.1 is current.
   - Compose BOM, Room, WorkManager, Hilt, ONNX Runtime, OkHttp, and Lottie have newer lines available.
   - Kotlin and AGP latest metadata points at pre-release lines and should be handled intentionally.

5. FFmpeg 16 KB and license decision.
   - A future FFmpeg path unlocks concat demuxer, reverse export, libass subtitle burn-in, loudnorm, and mixed copy/re-encode.
   - The decision must document GPL/LGPL flavor, F-Droid implications, ABI coverage, and Play Store 16 KB evidence.

## Build and Verification Notes

Expected local commands:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Local SDK gotcha:

- `local.properties` is ignored and machine-specific.
- During this consolidation it pointed at `C:\Users\Xray\.codex\android-sdk`, which did not exist in this workspace.
- The local file was corrected to `sdk.dir=C:\\Users\\--\\AppData\\Local\\Android\\Sdk` for verification. Do not commit `local.properties`.

Git gotchas:

- `master` was already 33 commits ahead of `origin/master` before this research run.
- Preserve unrelated local work and do not rewrite the ahead history.
- `AGENTS.md`, `CLAUDE.md`, and `.claude/` are intentionally ignored local instruction files.

## External Research Summary

The 2026-05-17 research pass covered:

- Official Android 16 / 16 KB page-size guidance.
- AndroidX Media3 release notes and Transformer/Composition/Lottie effect documentation.
- Maven metadata for AGP, Kotlin, Media3, Compose BOM, Room, WorkManager, Hilt, ONNX Runtime, OkHttp, and Lottie.
- Commercial mobile/pro editors: CapCut, DaVinci Resolve, PowerDirector, KineMaster, LumaFusion, VN, Premiere Rush end-of-life, and Clipchamp mobile retirement.
- Open-source editors and adjacent projects: OpenCut, devhyper/open-video-editor, OpenShot, Kdenlive, Shotcut, LosslessCut, OpenTimelineIO, Gyroflow, gl-transitions.
- Model and integration candidates: sherpa-onnx, whisper.cpp, SAM 2, DeepFilterNet, MADLAD/Bergamot-style translation paths, MediaPipe, ONNX Runtime, and FFmpeg 16 KB forks.

The detailed source index is [SOURCE_REGISTER.md](.ai/research/2026-05-17/SOURCE_REGISTER.md).

## Working Rules for Future Sessions

- Start with [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ROADMAP.md](ROADMAP.md), and the latest `.ai/research/<date>/CHANGESET_SUMMARY.md`.
- Verify memory-derived claims against live files and `git log`.
- Prefer completing existing scaffolded engine/UI surfaces over adding new speculative features.
- Keep privacy, offline operation, F-Droid viability, and explicit user consent in every AI/cloud/model decision.
- For UI work, preserve the existing Material 3 design system and avoid pill/capsule backdrops unless the element is a true icon circle, color dot, avatar, knob, or similarly semantic shape.
- For release work, treat Android 16 target SDK, 16 KB native libraries, model delivery size, signing, and Play/F-Droid channel differences as one release-readiness checklist.

# NovaCut Project Context

Last consolidated: 2026-05-17. Last implementation update: 2026-05-17.

This file is the committed project memory for future work. It reconciles the live repository, local instruction files, prior memory, current roadmap, and the deep research run in [.ai/research/2026-05-17](.ai/research/2026-05-17/).

## Identity and Product Direction

NovaCut is an Android video editor under package `com.novacut.editor`. The repo positions it as a privacy-first, offline-capable, mobile nonlinear editor with professional export, captioning, AI-assisted editing, media management, templates, and interoperability features.

Current live version evidence:

- [app/build.gradle.kts](app/build.gradle.kts): `compileSdk = 36`, `targetSdk = 36`, `versionCode = 146`, `versionName = "3.74.9"`.
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml): `app_version` is `v3.74.9`.
- [README.md](README.md) and [ROADMAP.md](ROADMAP.md) both describe the v3.74.x line.
- The 2026-05-17 continuation pushes each completed roadmap batch back to `origin/master`; verify `git status --short --branch` before assuming branch sync.

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
- ONNX Runtime Android 1.26.0 and MediaPipe Tasks Vision 0.10.35 are present for on-device model paths.
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
   - ONNX Runtime and MediaPipe now pass the local ELF + APK zip-alignment gates; future native libraries still need repeatable verification before activation.

2. Future model activation gates.
   - Active model rows in `docs/models.md` now have exact source locators and SHA-256 pins.
   - Future §3 model targets still need public locators, checksums, licenses, PAD/F-Droid posture, and runtime checksum wiring before moving into active engine paths.

3. Dependency stabilization.
   - Media3 1.10.1 is current.
   - The AGP-8.7-compatible train is current through Compose BOM 2026.05.00, Hilt 2.58, AndroidX Hilt 1.3.0, Room 2.7.2, WorkManager 2.11.2, Lifecycle 2.10.0, DataStore 1.2.1, Coroutines 1.11.0, ONNX Runtime 1.26.0, MediaPipe 0.10.35, OkHttp 5.3.2, and Lottie Compose 6.7.1.
   - Kotlin, AGP, Core KTX, Activity Compose, Navigation Compose, Hilt 2.59.x, and Room 2.8.x need a deliberate toolchain branch rather than a blind catalog bump.

4. FFmpeg 16 KB and license decision.
   - A future FFmpeg path unlocks concat demuxer, reverse export, libass subtitle burn-in, loudnorm, and mixed copy/re-encode.
   - The decision must document GPL/LGPL flavor, F-Droid implications, ABI coverage, and Play Store 16 KB evidence.

5. Diagnostic export follow-up.
   - The Settings diagnostic ZIP workflow is now implemented.
   - Future diagnostics work should focus on emulator/UI validation and any additional redaction tests discovered from real support bundles.

## Recent Implementation Notes

2026-05-17 autonomous continuation:

- Restored `:app:testDebugUnitTest` to a green baseline by letting `AutoSaveState.deserialize()` accept an injectable URI parser for JVM tests while keeping `Uri.parse()` as the production default.
- Completed R5.5d / R7.1. Settings now exposes the local-only diagnostic ZIP workflow, with busy/success/error state, saved-file summary, FileProvider share action, and diagnostics path scoping in `file_paths.xml`.
- Completed the active-model slice of R7.2. `docs/models.md` now pins Whisper, MediaPipe selfie segmentation, and LaMa inpainting to exact source locators and SHA-256 values; `ModelDownloadManager.ModelFile(checksumRequired = true)` blocks unpinned active downloads; Settings refreshes model state through checksum verification and renders failures as "Needs attention"; `ModelRegistryDocumentationTest` prevents active registry rows from returning to placeholder hashes or floating source URLs.
- Completed the AGP-8.7-compatible slice of R7.3. Version catalog updates: Compose BOM 2026.05.00, Dagger Hilt 2.58, AndroidX Hilt 1.3.0, Room 2.7.2, Coroutines 1.11.0, Lifecycle 2.10.0, DataStore 1.2.1, WorkManager 2.11.2, ONNX Runtime 1.26.0, MediaPipe Tasks Vision 0.10.35, OkHttp 5.3.2, and Lottie Compose 6.7.1. Deferred because of toolchain gates: Dagger Hilt 2.59.x requires AGP 9.0+, Core/Activity/Navigation latest requires AGP 8.9.1+, and Room 2.8.x fails the current Kotlin 2.1.0 / KSP 2.1.0 schema export path. The Hilt Compose import moved to `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`, Project Card swipe-to-delete no longer uses the deprecated `confirmValueChange` veto callback, and the 16 KB checker now follows Google's `LOAD ... align 2**14` criterion.
- Closed R8.3 for the routed app shell. `MainActivity` keeps `enableEdgeToEdge()`, disables the Android 10+ navigation-bar contrast scrim, and pads the NavHost root with `safeDrawingPadding()` so projects, settings, editor, bottom sheets, and display cutouts share one safe drawing boundary.
- Closed R8.4 / R8.10 by evidence. Manifest already enables `OnBackInvokedCallback`, the only Compose `BackHandler` is gated on transient editor state, and no `KeyboardType.Password` / password visual transformation fields opt out of Compose foundation's default stylus handwriting support.

## Build and Verification Notes

Expected local commands:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
python scripts\check_16kb_alignment.py app\build\outputs\apk\debug\app-debug.apk
& 'C:\Users\--\AppData\Local\Android\Sdk\build-tools\36.0.0\zipalign.exe' -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk
```

Local SDK gotcha:

- `local.properties` is ignored and machine-specific.
- During this consolidation it pointed at `C:\Users\Xray\.codex\android-sdk`, which did not exist in this workspace.
- The local file was corrected to `sdk.dir=C:\\Users\\--\\AppData\\Local\\Android\\Sdk` for verification. Do not commit `local.properties`.

Git gotchas:

- Preserve unrelated local work and do not rewrite history.
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

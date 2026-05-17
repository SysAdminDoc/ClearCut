# State of Repo - 2026-05-17

## Executive Snapshot

NovaCut is an Android/Kotlin video editor on `master` at local `HEAD` `ece3340`, with `master` 33 commits ahead of `origin/master` at the time of this reconnaissance. The live version is `v3.74.9` / `versionCode 146`.

The repository is not a small prototype. It has a broad Compose app surface, a large engine layer, shipped roadmap history, extensive scaffolded future capabilities, and a strong privacy/offline product philosophy. The highest-leverage next work is not another broad feature brainstorm; it is closing release-readiness gates and turning already-scaffolded systems into polished user workflows.

## Git and Branch State

Commands:

```powershell
git status --short --branch
git log -10 --oneline --decorate
git remote -v
git branch -vv
git tag --sort=-creatordate
```

Observed state:

- Branch: `master`.
- Upstream: `origin/master`.
- Ahead count: `master...origin/master [ahead 33]`.
- Remote: `https://github.com/SysAdminDoc/NovaCut.git`.
- Latest local commit before this research run: `ece3340 docs(changelog): record Round 6 Next/Later tier engine + docs pass`.
- Latest visible tags: `v3.73.2`, `v3.72.0`, `v3.71.0`, `v3.69.0`; no `v3.74.x` tag was visible in the latest tag sample.

Recent commits:

```text
ece3340 docs(changelog): record Round 6 Next/Later tier engine + docs pass
96a1ecc feat(engines): pre-flight helpers for stock / camera / NLE import stubs
68456d7 feat(compound): nested-sequence navigation stack (C.13)
681b7c4 feat(adjlayer): planForClip helper for export-pipeline integration (C.11)
57070ef feat(autocut): multi-word filler detection + proposal merge (C.2)
2525002 feat(keyframes): keyframe bezier graph data scaffold (C.12)
cea8de2 feat(privacy): add PrivacyDashboard data model (R5.5c)
35b3e8a feat(captions): locale-aware Noto font fallback policy (R5.4d)
9ea131a feat(captions): caption translation editor data model (R5.4a + R6.7)
41402b5 feat(plugins): unified PluginRegistry + OpenFX descriptor + compat matrix (R5.7)
```

## Version and Build Truth

Evidence:

- `app/build.gradle.kts`
  - `compileSdk = 36`
  - `minSdk = 26`
  - `targetSdk = 36`
  - `versionCode = 146`
  - `versionName = "3.74.9"`
- `app/src/main/res/values/strings.xml`
  - `app_version` is `v3.74.9`.
- `app/src/main/java/com/novacut/editor/NovaCutApp.kt`
  - Runtime version string is derived from `BuildConfig.VERSION_NAME`.
- `README.md`
  - Presents the v3.74.x feature surface and Media3 1.10.1 stack.
- `ROADMAP.md`
  - Was already at v3.74.9 before this run and now carries the 2026-05-17 Round 7 synthesis.

## Repository Inventory

Commands:

```powershell
rg --files
rg --files app/src/main/java app/src/test/java | Measure-Object
```

Observed metrics:

- Main Kotlin files under `app/src/main/java`: 204.
- Test Kotlin files under `app/src/test/java`: 52.
- Combined Kotlin line count across main and test: 74,751.
- Tracked files sample count: 301.

Notable top-level files:

- `README.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `CROSS-PROJECT-ROADMAP.md`
- `docs/models.md`
- `docs/templates.md`
- `gradle/libs.versions.toml`
- `.github/dependabot.yml`
- `.github/workflows/build.yml`
- `AGENTS.md` and `CLAUDE.md` exist locally but are ignored/untracked.

## Instruction and Memory Files Found

Search patterns included:

- `AGENTS.md`
- `CLAUDE.md`
- `.claude/**`
- `.cursor/rules/**`
- `.cursorrules`
- `.windsurfrules`
- `GEMINI.md`
- `COPILOT_INSTRUCTIONS.md`
- `.github/copilot-instructions.md`
- `.ai/**`
- `memory*.md`
- `context*.md`
- `project*.md`
- `notes*.md`
- `TODO*`
- `ROADMAP*`
- `CHANGELOG*`
- `ARCHITECTURE*`
- `CONTRIBUTING*`

Found:

- `AGENTS.md`
- `CLAUDE.md`
- `.claude/CLAUDE.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `CROSS-PROJECT-ROADMAP.md`
- `README.md`
- `docs/models.md`
- `docs/templates.md`

No tracked nested Cursor, Windsurf, Gemini, Copilot, or alternate AGENTS files were found in this pass.

## Current Architecture

Observed from file layout and source search:

- Single Android app module: `app`.
- Jetpack Compose UI in `app/src/main/java/com/novacut/editor/screen`.
- State and command orchestration centered around `EditorViewModel`.
- Engine-style feature scaffolds under `app/src/main/java/com/novacut/editor/engine`.
- Data layer includes Room entities/DAOs and JSON/autosave/project export helpers.
- Dependency injection uses Hilt.
- Media playback/export/effects use Media3.
- ML-related paths use ONNX Runtime and MediaPipe today, with future model policy in `docs/models.md`.

Representative high-value files:

- `app/src/main/java/com/novacut/editor/viewmodel/EditorViewModel.kt`
- `app/src/main/java/com/novacut/editor/screen/EditorScreen.kt`
- `app/src/main/java/com/novacut/editor/screen/SettingsScreen.kt`
- `app/src/main/java/com/novacut/editor/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/novacut/editor/engine/DiagnosticsExportEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/ModelDownloadManager.kt`
- `app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/CutAssistantEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/CompoundNavStack.kt`
- `app/src/main/java/com/novacut/editor/engine/KeyframeBezierGraph.kt`

## Build Environment

Observed:

- `java` was not available on PATH.
- `JAVA_HOME` works when set to `C:\Program Files\Android\openjdk\jdk-21.0.8`.
- `.\gradlew.bat --version` works with Gradle 8.9 after `JAVA_HOME` is set.
- `ANDROID_HOME` was not set.
- `local.properties` initially pointed at `C:\Users\Xray\.codex\android-sdk`, which does not exist in this workspace.
- Existing Android SDK path: `C:\Users\--\AppData\Local\Android\Sdk`.

Local-only fix made for verification:

```properties
sdk.dir=C:\\Users\\--\\AppData\\Local\\Android\\Sdk
```

`local.properties` is ignored and should not be committed.

## Dependency Snapshot

Current versions from `gradle/libs.versions.toml`:

| Dependency | Current |
|---|---:|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| KSP | 2.1.0-1.0.29 |
| Compose BOM | 2024.12.01 |
| Media3 | 1.10.1 |
| Hilt | 2.53.1 |
| Room | 2.6.1 |
| Coroutines | 1.9.0 |
| Lifecycle | 2.8.7 |
| Navigation | 2.8.5 |
| Activity | 1.9.3 |
| Core KTX | 1.15.0 |
| Coil | 2.7.0 |
| DataStore | 1.1.1 |
| WorkManager | 2.10.0 |
| ONNX Runtime Android | 1.17.0 |
| MediaPipe Tasks Vision | 0.10.14 |
| OkHttp | 4.12.0 |
| Lottie Compose | 6.6.2 |
| JUnit | 4.13.2 |
| org.json | 20240303 |

Maven metadata checked during this run indicates Media3 is current at 1.10.1, while Compose BOM, Room, WorkManager, Hilt, ONNX Runtime, OkHttp, and Lottie have newer available release trains. AGP and Kotlin latest metadata points to pre-release lines and needs a deliberate toolchain policy.

## High-Value Local Findings

1. `DiagnosticsExportEngine` exists, but Settings does not yet expose the diagnostic ZIP as a polished user workflow.
2. `docs/models.md` still contains unresolved checksum rows and activation gates. These should be closed before new large-model activation.
3. Several engines are intentionally scaffolded with availability checks or pure planning helpers. This is good architecture, but future work should complete the UI/export integration for existing scaffolds before adding more placeholders.
4. The repo targets Android 16 (`targetSdk = 36`). This makes 16 KB native-library compliance a release gate for ONNX Runtime, MediaPipe, FFmpeg, Sherpa-ONNX, OpenCV/NCNN, and future native deps.
5. Some local instruction files contain stale technical claims. `PROJECT_CONTEXT.md`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `README.md`, and `ROADMAP.md` should be treated as current.

## Self-Audit Result

Local reconnaissance covered:

- Git state.
- Recent commits.
- Version files.
- Build configuration.
- Dependency catalog.
- Agent/memory/instruction files.
- Roadmap/changelog/docs.
- Engine scaffolds and TODO/stub patterns.
- External source classes in multiple passes.

Verification follow-up:

- The first full unit-test attempt exposed two compile blockers in current source: missing `BERGAMOT_PER_PAIR` handling in `CaptionTranslationEngine.getSupportedLanguages()` and a compound navigation breadcrumb reference to a clip name field that did not exist. Both were fixed.
- Subsequent test runs exposed several recent scaffold assertion failures. The obvious source/test-contract issues in keyframe bezier evaluation, speaker-switch initial angle selection, compound-nav test fixtures, and adjustment-layer no-layer behavior were fixed.
- The final `:app:testDebugUnitTest` run compiled and executed 389 tests with 388 passing and one remaining JVM test failure in `AutoSaveStateTest.deserialize_capsPathologicalRecoveredCollections`.

Remaining limitation:

- This pass did not do a full UI manual run in an emulator. It was a research/planning pass, not a runtime QA pass.
- The remaining unit-test failure should be handled in a focused follow-up because it involves JVM-unit-test behavior around Android `Uri.parse()` and autosave clip recovery.

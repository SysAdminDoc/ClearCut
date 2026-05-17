# Changeset Summary - 2026-05-17

## Files Created

| File | Purpose |
|---|---|
| `PROJECT_CONTEXT.md` | Canonical committed project memory for future sessions. |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | Local reconnaissance memo. |
| `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` | Instruction/memory/doc reconciliation. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Local and external source index. |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | Search strategy, query classes, saturation notes, and limitations. |
| `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` | Commercial and open-source competitor comparison. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Raw harvested opportunity backlog. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Scored and tiered roadmap candidates. |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | Dependency, native, release, model integrity, and privacy review. |
| `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` | Model, dataset, integration, packaging, and evaluation review. |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | Summary of this research artifact changeset. |

## Files Modified

| File | Purpose |
|---|---|
| `ROADMAP.md` | Updated last refresh to 2026-05-17 and added Round 7 deep-research synthesis plus new Now-priority rows. |
| `app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt` | Fixed the supported-language switch for the new Bergamot model variant. |
| `app/src/main/java/com/novacut/editor/model/Project.kt` | Added optional clip display name storage needed by compound-clip breadcrumbs. |
| `app/src/main/java/com/novacut/editor/engine/KeyframeBezierGraph.kt` | Fixed cubic-bezier easing evaluation to solve x(time) before reading y(value). |
| `app/src/main/java/com/novacut/editor/engine/SpeakerSwitchPlanner.kt` | Fixed initial speaker-angle selection when explicit angle assignments reserve the default angle. |
| `app/src/test/java/com/novacut/editor/engine/CompoundNavStackTest.kt` | Switched JVM test fixtures from `Uri.EMPTY` to the repo's `FakeUri`. |
| `app/src/test/java/com/novacut/editor/engine/AdjustmentLayerEngineTest.kt` | Aligned the no-layer plan expectation with the engine's documented whole-clip segment behavior. |

## Local-Only Configuration

`local.properties` was corrected locally so Gradle can find the Android SDK in this workspace:

```properties
sdk.dir=C:\\Users\\--\\AppData\\Local\\Android\\Sdk
```

This file is ignored and intentionally not committed.

## Verification

- `git diff --check`: no whitespace errors; Git reported the normal Windows line-ending warning that `ROADMAP.md` LF will be replaced by CRLF the next time Git touches it.
- `.\gradlew.bat :app:compileDebugKotlin --dry-run`: successful after correcting ignored local `local.properties`.
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon`: successful.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`: compiles and executes 389 tests, with 388 passing and 1 remaining failure:
  - `AutoSaveStateTest > deserialize_capsPathologicalRecoveredCollections`
  - Failure: `java.util.NoSuchElementException at AutoSaveStateTest.kt:287`
  - Notes: the remaining failure occurs after source compile/test-compile succeeds. Earlier compile blockers and unrelated assertion clusters found during verification were repaired in this changeset.

## Commit

This file is part of the final local commit for the 2026-05-17 research pass.

## Autonomous Continuation Addendum — DeepFilterNet A.2

After the research pass, the roadmap continuation activated the next Now-table
native engine:

- Added `io.github.kaleyravideo:android-deepfilternet:0.0.8` to the Gradle
  catalog and app dependency graph.
- Verified Maven metadata, tag `v0.0.8` / commit
  `42ea9b786babf7d67008a81cf25257b4735e4127`, AAR SHA-256
  `6566a208fe476a71b20558f92d93a1c0db49fd93b36fcdaea17a10260189d167`,
  bundled model SHA-256
  `5600b6857117ecc7cf460b8ec4841963bfa6d718921d424d42dea5d3d37a8c32`,
  Apache-2.0 license posture, 16 KB preflight for arm64-v8a/x86_64
  `libdf.so`, and final debug APK alignment with 32 OK native libs, 40 skipped
  32-bit libs, and 0 misaligned libs.
- Updated `NoiseReductionEngine` to decode source audio through `FFmpegEngine`
  to 48 kHz mono PCM, process frames with `NativeDeepFilterNet` on Android,
  re-encode cleaned PCM to M4A, and retain pass-through fallback when native
  dependencies are unavailable.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `LICENSE`, and
  `docs/models.md` with the current dependency, source, license, checksum, and
  verification state.

## Autonomous Continuation Addendum — Media3 Lottie R7.5 / R6.10a

The next Now-table item adopted the official Media3 Lottie renderer where it
preserves NovaCut output semantics:

- Added `androidx.media3:media3-effect-lottie:1.10.1` to the Gradle catalog and
  app dependency graph.
- Verified Google Maven metadata latest/release `1.10.1`, AAR SHA-256
  `83b26f6f25e785b949263fc52cb7c0fb5f0e371445fa1d7b9a0ed0b71c05e69d`, source
  JAR SHA-256
  `5352b09469f485270d44e5bcf264ff30d7bee757049e8c79553bdfe7f7ee6e60`, and
  Apache-2.0 license posture from official AndroidX/Google Maven sources.
- Added `Media3LottieTextureOverlay`, which delegates Lottie frame drawing to
  Media3's `androidx.media3.effect.lottie.LottieOverlay` while keeping
  overlay-relative timing, alpha gating, TextDelegate substitution, and
  full-frame canvas sizing.
- Updated `VideoEngine` to choose the Media3 path for non-HDR finite-window
  Lottie overlays and retain `LottieOverlayEffect` for HDR exports or title
  windows longer than the composition duration.
- Added focused JVM tests for backend selection, timeline alpha gating, and
  full-frame scale math.
- Updated `ROADMAP.md`, `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `docs/models.md`,
  `SOURCE_REGISTER.md`, and `SECURITY_AND_DEPENDENCY_REVIEW.md` with the
  adoption decision, sources, fallback constraints, and verification notes.
- Verification after the final layering adjustment:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.novacut.editor.engine.Media3LottieTextureOverlayTest --no-daemon`,
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`,
  `git diff --check`,
  `python scripts\check_16kb_alignment.py app\build\outputs\apk\debug\app-debug.apk`,
  and SDK Build Tools `zipalign -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk`.

## Autonomous Continuation Addendum — Media3 Compose Player R6.10b

The next R6.10 item was closed as a documented non-adoption:

- Evaluated `androidx.media3:media3-ui-compose-material3:1.10.1` against
  NovaCut's actual preview contract.
- Verified Google Maven metadata latest/release `1.10.1`, lastUpdated
  `20260512123518`, and resolved AAR SHA-256
  `0e0789cef85d948f924c0cec365021a56f6cc63b8c9888cacd05357f83e00112`.
- Added `PreviewPanelMedia3ComposePolicy` and focused JVM tests to codify that
  full `Player` replacement is unsafe while `ProgressSlider` remains
  player-timeline based and performs `Player.seekTo` internally.
- Added `docs/preview-media3-compose.md` and updated `ROADMAP.md`,
  `PROJECT_CONTEXT.md`, `CHANGELOG.md`, `SOURCE_REGISTER.md`,
  `RESEARCH_LOG.md`, `SECURITY_AND_DEPENDENCY_REVIEW.md`, and
  `DATASET_MODEL_INTEGRATION_REVIEW.md`.
- Verification:
  `.\gradlew.bat :app:testDebugUnitTest --tests com.novacut.editor.ui.editor.PreviewPanelMedia3ComposePolicyTest --no-daemon`,
  `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`,
  `git diff --check`,
  `python scripts\check_16kb_alignment.py app\build\outputs\apk\debug\app-debug.apk`,
  and SDK Build Tools `zipalign -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk`.

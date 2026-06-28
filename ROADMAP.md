# ClearCut Roadmap

Current version: **v3.74.108** (`versionCode` 241). Last drained:
2026-06-27.

`ROADMAP.md` contains only work that can be implemented in the local build
environment. Completed work belongs in git history and `CHANGELOG.md`; research
context belongs in `RESEARCH.md`; blocked or operator-gated work belongs in
`Roadmap_Blocked.md`.

## Active Queue

No actionable roadmap items remain.

## Blocked Queue

Blocked items were moved to `Roadmap_Blocked.md`. Move an item back into this
file only after the blocker clears and the next implementation step can be
verified locally.

## Research-Driven Additions

- [ ] P0 — Gate FFmpeg CVE-2026-8461 exposure in native export paths
  Why: ClearCut bundles FFmpegKit 16 KB 6.1.1 and allows AVI inputs without codec-aware screening, while FFmpeg fixed a MagicYUV parsing CVE in 8.1.2.
  Evidence: NVD CVE-2026-8461; `gradle/libs.versions.toml`; `app/src/main/java/com/novacut/editor/engine/NativeProcessingPolicy.kt`; `app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt`; LosslessCut issue #2943.
  Touches: `NativeProcessingPolicy.kt`, `FFmpegEngine.kt`, media-health/preflight engines, export incident logging, JVM tests.
  Acceptance: Known-dangerous FFmpeg decoder/container combinations are rejected or surfaced before FFmpeg execution, every typed FFmpeg entry point goes through the policy, user-safe diagnostics are recorded, and policy tests plus `testDebugUnitTest` pass.
  Complexity: M

- [ ] P0 — Restore local release-trust verification after GitHub Actions removal
  Why: `.github/workflows` was removed, but README and release/distribution validators still require CI workflow attestations and workflow file contents.
  Evidence: `README.md`; `scripts/verify_release_artifacts.py`; `scripts/validate_distribution_readiness.py`; `scripts/validate_android_audio_api_policy.py`; commits `edfed9b` and `a7ac923`; Open Video Editor issue #113.
  Touches: release verification scripts, README distribution copy, distribution/audio policy validators, script self-tests.
  Acceptance: A single local release-check command verifies checksums, APK signing fingerprints, 16 KB native alignment, size budget, and Play metadata without `.github`; README no longer claims CI attestations; all script self-tests pass.
  Complexity: M

- [ ] P1 — Replace stale Dependabot/CI dependency policy with a local dependency truth gate
  Why: Dependency-maintenance docs still describe Dependabot and workflow grouping even though repo policy now forbids Dependabot, Renovate, and GitHub Actions.
  Evidence: `docs/dependency-maintenance.md`; `app/src/test/java/com/novacut/editor/TrackedFilesAuditTest.kt`; `gradle/libs.versions.toml`; Dagger, Coil, Media3, KSP, ONNX Runtime release feeds.
  Touches: dependency maintenance docs, a local dependency-audit script or test fixture, `libs.versions.toml` hold metadata if needed.
  Acceptance: Local tooling reports current core dependency versions, latest verified upstream versions, and explicit hold reasons; docs remove Dependabot/CI claims; tracked-file audit and dependency self-test pass.
  Complexity: M

- [ ] P1 — Ratchet disabled lint detectors under the current Kotlin/AGP stack
  Why: Five Compose/lifecycle lint detectors are globally disabled, leaving regressions invisible if the original detector crashes have cleared.
  Evidence: `app/build.gradle.kts`; Android lint policy in recent changelog; current Kotlin/KSP/Dagger release scans.
  Touches: `app/build.gradle.kts`, lint baseline, focused lint regression tests or a detector-by-detector validation script.
  Acceptance: Each disabled detector is re-tested independently; any detector that no longer crashes is re-enabled or baseline-ratcheted; remaining disables include current failure evidence; `lintDebug` and unit tests pass.
  Complexity: M

- [ ] P1 — Finish semantic theme-token migration for editor/export/mediapicker surfaces
  Why: High-contrast and future light-mode correctness are limited while dozens of feature panels import raw `Mocha` colors instead of semantic `LocalClearCutColors`.
  Evidence: `docs/appearance-policy.md`; 68 raw `Mocha` imports under `app/src/main/java/com/novacut/editor/ui/editor`, `ui/export`, and `ui/mediapicker`; OpenCut RTL/CJK accessibility demand.
  Touches: shared theme tokens, editor/export/mediapicker composables, `ClearCutAppearancePolicyTest`, visual smoke artifacts.
  Acceptance: Feature panels use semantic colors or approved accent helpers; contrast tests cover high-contrast critical states; desktop and phone visual smoke shows no contrast regressions.
  Complexity: L

- [ ] P1 — Repair import-package documentation truth for `.ncstyle` style packs
  Why: Docs still say `.ncstyle` installation is blocked even though style-pack import landed and is user-facing.
  Evidence: `docs/templates.md`; `docs/incoming-document-imports.md`; `CHANGELOG.md` v3.74.86; `StylePackManager` and incoming-document router source.
  Touches: package/import docs, README feature table if stale, import-router/package tests.
  Acceptance: Docs and README describe the current `.ncstyle` behavior exactly; tests verify style-pack router/install coverage; no stale "pending loader" claims remain.
  Complexity: S

- [ ] P2 — Add canonical saved/dirty-state fingerprint and undo-clean tests
  Why: Save trust depends on accurately returning to clean state after save/undo, and mature editors show user-visible modified-indicator failures.
  Evidence: Shotcut issue #1820; `AutoSaveIndicator.kt`; `ProjectAutoSave.kt`; `EditorViewModel.undo()`.
  Touches: editor state/save indicator model, `ProjectAutoSave`, undo/redo tests, edit-confidence status tests.
  Acceptance: Saving establishes a clean baseline, undoing back to that baseline clears unsaved/dirty status, new edits mark the project dirty, save-error state remains assertive, and JVM tests cover the transitions.
  Complexity: M

- [ ] P2 — Expose codec/frame diagnostics in media health and timeline navigation
  Why: Competitor issue trackers repeatedly request timestamp, frame/keyframe, track-language, and codec transparency for export correctness debugging.
  Evidence: LosslessCut issues #2805, #2792, and #2930; Open Video Editor issues #103 and #116; Shotcut issue #1826; `MediaHealthReport` and `Timeline` sources.
  Touches: media import/health probes, timeline keyframe navigation, export preflight copy, media manager UI, tests.
  Acceptance: Media health shows container, codec, HDR/color confidence, track language where available, and nearest sync-frame/keyframe navigation; export preflight references diagnostics when timestamp/color risk is detected.
  Complexity: M

- [ ] P3 — Add local metadata sidecar export for GPS/subtitle diagnostic tracks
  Why: LosslessCut users ask for GPS and subtitle extraction, and ClearCut can support local diagnostic exports without map services or cloud dependencies.
  Evidence: LosslessCut issues #2072 and #2851; `MetadataScrubEngine.kt`; `TimelineImportEngine.kt`; local-first privacy policy.
  Touches: metadata probe/export engine, media manager diagnostics, export/share affordance, tests.
  Acceptance: When embedded GPS/subtitle-like tracks are detected, ClearCut can export a local GPX/CSV/VTT/SRT diagnostic sidecar or explain unsupported tracks without network access.
  Complexity: L

## Research-Driven Additions

- [ ] P1 - Add paste/import cut-list and marker parser
  Why: Precision editors reduce repetitive trimming by accepting pasted cut ranges and imported marker definitions, and ClearCut already has timeline markers plus text-based edit foundations.
  Evidence: LosslessCut issues #2947 and #2945; `app/src/main/java/com/novacut/editor/engine/TextBasedEditEngine.kt`; `app/src/main/java/com/novacut/editor/ui/editor/MarkerListPanel.kt`; `app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt`.
  Touches: `TextBasedEditEngine.kt`, `TimelineImportEngine.kt`, marker/cut-assistant panels, parser tests, import-preview copy.
  Acceptance: Users can paste or import time ranges with optional labels, preview parsed cuts/markers, reject invalid rows with precise messages, apply accepted entries undoably, and JVM parser tests cover colon/dot/minute-second variants.
  Complexity: M

- [ ] P1 - Harden CJK and RTL caption preview/export rendering
  Why: Community issues in adjacent editors show CJK and Arabic captions fail when font fallback, bidi policy, and export text rendering diverge.
  Evidence: OpenCut issues #817 and #744; `app/src/main/java/com/novacut/editor/engine/CaptionFontFallbackPolicy.kt`; `app/src/main/java/com/novacut/editor/engine/BidiTextPolicy.kt`; `app/src/main/java/com/novacut/editor/ui/editor/CaptionPreviewOverlay.kt`; `app/src/main/java/com/novacut/editor/engine/ExportTextOverlay.kt`.
  Touches: caption preview overlay, export text overlay, font fallback policy, bidi policy, caption style tests, string/sample fixtures.
  Acceptance: CJK and Arabic sample captions render with non-tofu fallback in preview and export bitmap paths, mixed-direction text keeps readable ordering, and unit/golden-style JVM tests cover fallback selection and export layout decisions.
  Complexity: M

- [ ] P2 - Add gallery-visible export output contract tests
  Why: Android editor users lose trust when exports have missing extensions, missing time metadata, invalid custom names, or gallery apps classify videos as files/images.
  Evidence: Open Video Editor issues #96, #98, and #95; `app/src/main/java/com/novacut/editor/engine/ExportOutputVerifier.kt`; `app/src/main/java/com/novacut/editor/engine/FileNaming.kt`; `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`.
  Touches: export verifier, filename template sanitizer, MediaStore save/share paths, export incident logging, JVM tests.
  Acceptance: Export tests assert extension, MIME, duration/time metadata, sanitized custom names, MediaStore-visible video classification, and user-safe diagnostics for unverifiable outputs.
  Complexity: M

- [ ] P2 - Make waveform and proxy background jobs timeline-safe during edits
  Why: Mature editors report data loss and stale displays when proxy or waveform generation completes while the user is editing the same media.
  Evidence: LosslessCut issue #2807; Shotcut issue #1145; `app/src/main/java/com/novacut/editor/engine/ProxyWorkflowEngine.kt`; `app/src/main/java/com/novacut/editor/engine/AudioEngine.kt`; `app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt`.
  Touches: proxy workflow state, waveform cache, timeline clip identity/versioning, edit delegates, race-focused tests.
  Acceptance: Completing a waveform/proxy job after split/trim/delete/reorder cannot mutate the wrong clip, stale job results are discarded by clip/media version, and tests simulate job completion after timeline edits.
  Complexity: M

- [ ] P2 - Add multi-asset auto-sequence import review
  Why: Competitor users request selecting multiple assets and automatically arranging them into a starter timeline, which fits ClearCut's local media import and storyboard surfaces.
  Evidence: OpenCut issue #828; `app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt`; `app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt`; `app/src/main/java/com/novacut/editor/ui/editor/StoryboardPanel.kt`.
  Touches: media picker multi-select flow, project-create/import route, storyboard panel, clip insertion delegate, tests.
  Acceptance: Multi-selected media can open a review screen ordered by capture time/name/manual drag, creates an undoable starter sequence without sample/fake media, and preserves existing single-import behavior.
  Complexity: M

- [ ] P3 - Add portable edit-decision JSON schema preview
  Why: OSS editor users want portable project/edit descriptions that external local tools can generate without locking projects inside one app store.
  Evidence: OpenCut issue #719; LosslessCut issue #2947; `app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt`; `app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt`; `app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt`.
  Touches: timeline import engine, project archive/import preview, schema docs inside existing docs, validation tests.
  Acceptance: A documented local JSON edit-decision schema can be exported from a project, imported through the existing preview-first document router, rejects schema-too-new files, reports missing media, and maps clips/markers/captions without mutating until accepted.
  Complexity: L

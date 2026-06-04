# NovaCut Project Context

Last consolidated: 2026-06-04. Last implementation update: 2026-06-04.

This file is the committed project memory for future work. It reconciles the live repository, local instruction files, prior memory, current roadmap, and the deep research run in [.ai/research/2026-05-17](.ai/research/2026-05-17/).

## Identity and Product Direction

NovaCut is an Android video editor under package `com.novacut.editor`. The repo positions it as a privacy-first, offline-capable, mobile nonlinear editor with professional export, captioning, AI-assisted editing, media management, templates, and interoperability features.

Current live version evidence:

- [app/build.gradle.kts](app/build.gradle.kts): `compileSdk = 36`, `targetSdk = 36`, `versionCode = 162`, `versionName = "3.74.25"`.
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml): `app_version` is `v3.74.25`.
- [README.md](README.md) and [ROADMAP.md](ROADMAP.md) both describe the v3.74.x line.
- The 2026-05-17 continuation pushes each completed roadmap batch back to `origin/master`; verify `git status --short --branch` before assuming branch sync.

## Source of Truth

Use these files first:

- [README.md](README.md): product summary, install/build notes, feature list, and tech stack.
- [ROADMAP.md](ROADMAP.md): current prioritized roadmap and research-backed implementation queue.
- [COMPLETED.md](COMPLETED.md): shipped roadmap summary.
- [CHANGELOG.md](CHANGELOG.md): full shipped release history.
- [RESEARCH_REPORT.md](RESEARCH_REPORT.md): product, platform, and ecosystem research synthesis.
- [docs/archive](docs/archive/): historical detailed roadmaps and research plans.
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
- The repo now has a repeatable Compose instrumentation smoke harness for the
  project gallery, blank-project editor open, media picker, export sheet,
  Settings, and privacy dashboard surfaces.
- The release workflow now packages debug, release, and instrumentation APKs,
  verifies version/changelog/artifact metadata, checks APK signatures and ZIP
  alignment, and runs APK-based 16 KB native-library checks.
- Editor project opens now use the schema-aware recovery outcome loader and
  preserve corrupt or newer-schema autosaves by blocking subsequent autosave
  writes until the recovery file is safe to replace.
- Project-open media checks now run `MediaRelinkProbe`, store reports on
  `EditorState`, and route missing/unverified sources into Media Manager's
  existing relink workflow.
- Compound clip navigation is now reachable from the editor: timeline
  long-press opens compound clips through `EditorViewModel.openCompoundClip`,
  selected compound clips expose an Open radial action, and the breadcrumb chip
  renders from `EditorState` while predictive back pops the compound level.
- Caption translation is now reachable from the Captions panel: selecting a
  target language builds translation rows from the selected clip's captions,
  and row edits/regeneration route through `EditorViewModel`.
- Mixed-render export is now reachable behind conservative runtime gates:
  eligible `MixedRenderComposer` plans pass stream-copy runs through
  `StreamCopyExportEngine`, render modified runs through Media3 Transformer,
  and stitch run outputs through `FFmpegEngine.concat`.
- Filler cleanup has a single editor route through Cut Assistant Review; the
  deleted standalone FillerRemovalPanel no longer carries panel state, a
  bottom-sheet slot, or obsolete ViewModel analyze/apply methods.
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

4. FFmpeg 16 KB integration follow-through.
   - The default app now pins `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`, and `FFmpegEngine` bridges its existing helper methods through FFmpegKit async sessions.
   - Debug APK verification after the pin showed 30 required 16 KB-aligned native libraries and 0 misaligned libraries.
   - License posture is deliberately conservative: the Maven POM declares LGPL-3.0, but the resolved AAR/APK includes GPLv3 license/source-offer resources, so redistributed builds that include this dependency should be treated as GPLv3-obligation builds unless a future flavor swaps to a verified LGPL-only/no-FFmpeg variant.
   - Remaining value is downstream routing: reverse export, libass burn-in, exact two-pass loudnorm analysis, sidechain ducking, AV1 software fallback, and mixed copy/re-encode stitching.

5. DeepFilterNet activation follow-through.
   - A.2 now pins `io.github.kaleyravideo:android-deepfilternet:0.0.8`; the bundled-model AAR was inspected for tag/version/source metadata, Apache-2.0 license posture, model SHA-256, and 16 KB readiness before wiring.
   - Debug APK verification after the pin showed 32 required 16 KB-aligned native libraries, 40 skipped 32-bit libraries, and 0 misaligned libraries.
   - `NoiseReductionEngine` decodes source audio to 48 kHz mono PCM through `FFmpegEngine`, processes fixed-size direct `ByteBuffer` frames with `NativeDeepFilterNet` on Android, and re-encodes cleaned PCM to M4A. Plain JVM and missing-native flavors return unavailable and keep pass-through behavior.
   - Future work: emulator/device QA with real noisy clips, better SNR reporting from measured audio instead of the current stub profile, and a real file-level spectral-gate fallback if FFmpeg is available but DeepFilterNet fails.

6. Media3 Lottie adoption follow-through.
   - R7.5 / R6.10a now pins `androidx.media3:media3-effect-lottie:1.10.1` from Google Maven.
   - `Media3LottieTextureOverlay` routes eligible non-HDR finite-window title overlays through Media3's official `LottieOverlay` while preserving overlay-relative timing, alpha gating, `TextDelegate` substitution, and full-frame canvas sizing.
   - `VideoEngine` deliberately keeps the legacy `LottieOverlayEffect` shader for HDR exports and title windows longer than the composition duration because Media3 `OverlayEffect` treats non-text `BitmapOverlay` overlays as Ultra HDR gainmap overlays on HDR input, and the official Lottie overlay loops where NovaCut's shader holds the final frame.
   - The repo currently has no committed `app/src/main/assets/lottie_templates/` payloads, so future visual QA should add real template fixtures or golden-frame renders before deleting the fallback shader.

7. Diagnostic export follow-up.
   - The Settings diagnostic ZIP workflow is now implemented.
   - Future diagnostics work should focus on emulator/UI validation and any additional redaction tests discovered from real support bundles.

## Recent Implementation Notes

2026-06-04 autonomous continuation:

- Completed the P0 UI test harness bootstrap in v3.74.11. Added shared
  `NovaCutTestTags`, stable tags on the project gallery, template picker,
  editor, media picker, export sheet, Settings, privacy dashboard, and
  first-run tutorial, plus `NovaCutSmokeTest` under `app/src/androidTest`.
- The smoke opens a blank project through the real UI, dismisses the first-run
  tutorial when present, verifies the empty-editor Add Media path, opens/closes
  the media picker, opens/closes the export sheet, returns to projects, and
  verifies Settings -> Privacy dashboard.
- Verification passed: `git diff --check`,
  `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks`,
  and `:app:testDebugUnitTest :app:assembleDebugAndroidTest`. Connected
  instrumentation could not run because ADB reported `emulator-5554` as
  `offline` even after restarting the ADB server.

2026-06-04 release-pipeline continuation:

- Completed the P0 Release pipeline reactivation in v3.74.12. The GitHub
  Actions APK workflow now packages debug, release, and androidTest APKs,
  runs `scripts/verify_release_artifacts.py`, verifies debug/release APK
  signatures, checks ZIP alignment for all packaged APKs, runs the 16 KB gate
  against debug/release APKs directly, and uploads the instrumentation APK.
- `scripts/verify_release_artifacts.py` checks Gradle version metadata,
  `strings.xml`, ROADMAP/COMPLETED/CHANGELOG version sync, private signing
  ignore rules, tag/version consistency, and generated APK metadata.
- `NullSafeMutableLiveData` is disabled in the Android lint block because the
  androidx.lifecycle detector crashes under the current Kotlin 2.1 / AGP 8.7
  lint stack and NovaCut has no `LiveData`/`MutableLiveData` call sites for it.

2026-06-04 recovery-open continuation:

- Completed the P0 Recovery open path in v3.74.13. `MainActivity` now routes
  dynamic Resume/Open shortcuts into the editor route with `expectRecovery`,
  and `EditorViewModel` reads that flag from `SavedStateHandle`.
- Editor startup now calls `ProjectAutoSave.loadRecoveryDataWithOutcome`.
  Loaded autosaves preserve the prior state-restore/dialog behavior; expected
  missing recovery shows a warning only for recovery-intent opens; future-schema
  and corrupt files show error toasts.
- Future-schema/corrupt recovery outcomes block timed autosave and the manual
  autosave write inside `saveProject()`, preserving the on-disk recovery file
  instead of overwriting it with the Room/opened project state.
- `RecoveryDialogTest` covers recovery feedback and autosave-blocking decisions.

2026-06-04 media-relink continuation:

- Completed the P0 Media relink editor integration in v3.74.14.
  `EditorViewModel` now injects `MediaRelinkProbe`, stores per-clip
  `ClipRelinkReport` values on `EditorState`, and runs the probe after a loaded
  project recovery/open.
- Project-open scans auto-open Media Manager and warn when missing or
  unverified sources are found, so users get a relink path before edit/export.
- Media Manager now consumes probe reports, renders Online/Missing/Unverified
  asset states, and reuses the existing replacement-picker relink flow.
- Relink/add-media callbacks refresh the probe report so the media-health status
  is updated after source replacement.
- Next roadmap item after this batch was P0 Compound clip gesture closure.

2026-06-04 compound-gesture continuation:

- Completed the P0 Compound clip gesture closure in v3.74.15. Timeline
  long-press now dispatches compound clips to `EditorViewModel.openCompoundClip`
  and keeps regular clips on the existing multi-select path, with unit coverage
  for successful compound opens and fallback behavior.
- The preview radial menu now adds an Open action when the selected clip is a
  compound clip and routes that action through the same ViewModel method.
- `CompoundNavBreadcrumb` now renders from `EditorState.compoundBreadcrumbText`
  above the timeline, and `openCompoundClip` clears clip selection, multi-select,
  and the active tool before publishing depth so the existing BackHandler's
  compound gate exits the nested level first.
- Next roadmap item after this batch was P0 Caption translation panel call site.

2026-06-04 caption-translation continuation:

- Completed the P0 Caption translation panel call site in v3.74.16.
  `CaptionEditorPanel` now hosts `CaptionTranslationPanel` inside the Captions
  bottom sheet and receives rows, language state, target choices, edit callbacks,
  and regenerate callbacks from `EditorScreen`.
- Added `CaptionTranslationBridge` to convert selected-clip `Caption` rows into
  `SherpaAsrEngine.TranscriptionSegment` values and to rebuild source segments
  for per-row regeneration. `EditorViewModel.runCaptionTranslation(target)`
  now uses that bridge plus the existing stub translation engine to populate
  `captionTranslationRows`.
- `regenerateCaptionTranslation(rowIndex)` now marks the row pending and
  self-completes through the same stub engine, so the panel's regenerate action
  does not leave rows permanently pending while real model activation is still
  gated.
- Next roadmap item after this batch was P0 Mixed-render export orchestrator.

2026-06-04 mixed-render continuation:

- Completed the P0 Mixed-render export orchestrator in v3.74.17.
  `MixedRenderExportPlanner` now gates mixed rendering to the safe envelope
  that can preserve edits: one visible video track, no separate audio,
  overlay, or adjustment tracks, no export-side overlays/sidecars, and no
  target-size bitrate request.
- `VideoEngine.exportMixed(plan)` consumes `MixedRenderComposer` plans, preflights
  each stream-copy run with `StreamCopyExportEngine.analyze`, writes
  pass-through runs through stream-copy, writes re-encode runs through the
  existing Media3 Transformer composition path, and stitches run outputs with
  `FFmpegEngine.concat` when FFmpeg is available on Android runtime.
- `ExportDelegate` now tries the mixed-render path after the pure stream-copy
  shortcut and before the whole-timeline Transformer fallback, while sharing the
  existing notes, subtitle, AI-disclosure, C2PA, state, and toast finalization
  path.
- Next roadmap item after this batch was P1 FillerRemovalPanel final deletion.

2026-06-04 filler-removal deletion continuation:

- Completed the P1 FillerRemovalPanel final deletion in v3.74.18. The
  `filler_removal` tool action already routes to Cut Assistant Review, so the
  legacy bottom-sheet slot, `PanelId.FILLER_REMOVAL`, `FillerRemovalPanel.kt`,
  `EditorState.fillerRegions`, `EditorState.isAnalyzingFillers`, and the
  `show/hide/analyze/apply` ViewModel methods were removed.
- Removed the old standalone `AiFeatures.detectFillerAndSilence` path and
  `RemovalRegion` / `RemovalType` model that were only used by that panel.
  Active silence and filler proposals remain owned by `CutAssistantEngine` and
  `SilenceDetectionEngine`.
- Removed panel-only filler-removal strings and the deleted slot's deprecation
  suppression. The menu label `tool_remove_fillers` remains because it still
  opens Cut Assistant Review.
- Next roadmap item after this batch was P1 Per-tool AI requirement adoption.

2026-06-04 per-tool AI requirement adoption continuation:

- Completed the P1 per-tool AI requirement adoption in v3.74.19. Strict
  model/dependency-gated AI tools (`frame_interp`, `object_remove`,
  `video_upscale`, `ai_background`, `ai_stabilize`, and `ai_style_transfer`)
  now resolve their `AiToolRequirements` entry after clip compatibility checks
  and before `aiProcessingTool` is set.
- `AiModelRequirementSheet` now has a real accepted-run path:
  `EditorScreen` dispatches through `EditorViewModel.runAiToolAfterRequirement`
  so ready or consented tools do not reopen their own preflight sheet.
- The legacy `aiRequirementPrompt` helper now tries the registry first and
  falls back to the old generic prompt only when a tool ID has no registry
  entry. Known model-gated call sites pass their tool ID through that helper.
- `AiToolRequirements` now uses the live editor dispatch IDs for denoise,
  Real-ESRGAN upscale, and advanced style transfer (`denoise`,
  `video_upscale`, `ai_style_transfer`), and
  `AiToolRequirementsTest` locks the active model-aware IDs.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.engine.AiToolRequirementsTest` passed.
- Next roadmap item after this batch was P1 Editor state decomposition.

2026-06-04 editor state decomposition continuation:

- Completed the first P1 editor state decomposition batch in v3.74.20 by
  adding `EditorDomainState` plus typed panel, caption, compound, export, AI,
  and media slices under `EditorDomainStates`.
- Added the `EditorState.domainStates` projection so existing state storage and
  `copy(...)` call sites remain stable while downstream hosts can adopt typed
  slices incrementally.
- Added `EditorDomainStateTest` to lock the six-domain enumeration and verify
  representative fields from each domain are projected without drift.
- The active roadmap item was narrowed to storage migration: move constructor
  fields and `copy(...)` call sites onto the new slices domain by domain.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, full debug-unit/debug
  APK/release APK/androidTest Gradle matrix, release metadata, signature,
  zipalign, and APK-based 16 KB gates passed.
- Next roadmap item after this batch was P1 Editor state storage migration.

2026-06-04 AI editor state storage migration continuation:

- Completed the AI slice of P1 editor state storage migration in v3.74.21.
  `EditorState` now stores AI-related fields in `ai: EditorAiState` instead
  of flat constructor properties.
- Added read-only compatibility accessors for existing UI/delegate reads:
  `aiRequirementPrompt`, `aiModelRequirement`, `aiProcessingTool`,
  `aiSuggestion`, `aiUsageLedger`, `cutAssistantReview`, smart-reframe,
  auto-edit, TTS, and noise-analysis flags.
- Converted AI mutation call sites in `AiToolsDelegate` and `EditorViewModel`
  to write through `copyAi` / nested `ai.copy(...)`, including requirement
  sheets, suggestions, usage ledger, Cut Assistant review, smart reframe,
  auto edit, TTS, and noise reduction.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, full debug-unit/debug
  APK/release APK/androidTest Gradle matrix, release metadata, signature,
  zipalign, and APK-based 16 KB gates passed.
- Next roadmap item after this batch was P1 Editor state storage migration for
  panel, caption, compound, export, and media slices.

2026-06-04 export editor state storage migration continuation:

- Completed the export slice of P1 editor state storage migration in v3.74.22.
  `EditorState` now stores export-related fields in
  `export: EditorExportDomainState` instead of flat constructor properties.
- Added read-only compatibility accessors for existing UI/delegate reads:
  `exportConfig`, `exportProgress`, `exportState`, `lastExportedFilePath`,
  `exportErrorMessage`, `exportStartTime`, `renderSegments`, `renderSummary`,
  `batchExportQueue`, and `savedExportConfig`.
- Converted export mutation call sites in `EditorViewModel` and
  `ExportDelegate` to write through `copyExport` / nested `export.copy(...)`,
  including export engine progress/state collection, export sheet defaults,
  smart reframe export aspect sync, frame capture, contact-sheet/GIF/video
  export outcomes, batch export, render preview, and quick-preview config.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, full debug-unit/debug
  APK/release APK/androidTest Gradle matrix, release metadata, signature,
  zipalign, and APK-based 16 KB gates passed.
- Next roadmap item after this batch was P1 Editor state storage migration for
  panel, caption, compound, and media slices.

2026-06-04 media editor state storage migration continuation:

- Completed the media/trust-report slice of P1 editor state storage migration
  in v3.74.23. `EditorState` now stores media-related feedback in
  `media: EditorMediaState` instead of flat constructor properties.
- Added read-only compatibility accessors for existing UI reads:
  `backupImportFeedback`, `timelineExchangeFeedback`, and `mediaRelinkReports`.
- Converted media mutation call sites in `EditorViewModel` to write through
  `copyMedia` / nested `media.copy(...)`, including backup import feedback,
  relink reports, OTIO/FCPXML feedback, and dismissal helpers.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, full debug-unit/debug
  APK/release APK/androidTest Gradle matrix, release metadata, signature,
  zipalign, and APK-based 16 KB gates passed.
- Next roadmap item after this batch was P1 Editor state storage migration for
  panel, caption, and compound slices.

2026-06-04 compound editor state storage migration continuation:

- Completed the compound slice of P1 editor state storage migration in
  v3.74.24. `EditorState` now stores compound navigation in
  `compound: EditorCompoundState` instead of flat constructor properties.
- Added read-only compatibility accessors for existing UI reads:
  `compoundNavDepth` and `compoundBreadcrumbText`.
- Converted compound mutation in `publishCompoundNavState()` to write through
  nested `compound.copy(...)` while preserving selection-clearing behavior on
  compound entry.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest` passed.
- Next roadmap item after this batch was P1 Editor state storage migration for
  panel and caption slices.

2026-06-04 caption editor state storage migration continuation:

- Completed the caption slice of P1 editor state storage migration in v3.74.25.
  `EditorState` now stores caption translation state in
  `caption: EditorCaptionState` instead of flat constructor properties.
- Added read-only compatibility accessors for existing UI reads:
  `captionTranslationRows`, `captionTranslationSourceLang`,
  `captionTranslationTargetLang`, `captionTranslationQuality`, and
  `captionTranslationVariant`.
- Converted caption translation target, rows, edit, regenerate, and
  complete-regenerate mutation paths to write through `copyCaption` / nested
  `caption.copy(...)`.
- Verification: `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, full debug-unit/debug
  APK/release APK/androidTest Gradle matrix, release metadata, signature,
  zipalign, and APK-based 16 KB gates passed.
- Next roadmap item: P1 Editor state storage migration for panel slice.

2026-05-17 autonomous continuation:

- Restored `:app:testDebugUnitTest` to a green baseline by letting `AutoSaveState.deserialize()` accept an injectable URI parser for JVM tests while keeping `Uri.parse()` as the production default.
- Completed R5.5d / R7.1. Settings now exposes the local-only diagnostic ZIP workflow, with busy/success/error state, saved-file summary, FileProvider share action, and diagnostics path scoping in `file_paths.xml`.
- Completed the active-model slice of R7.2. `docs/models.md` now pins Whisper, MediaPipe selfie segmentation, and LaMa inpainting to exact source locators and SHA-256 values; `ModelDownloadManager.ModelFile(checksumRequired = true)` blocks unpinned active downloads; Settings refreshes model state through checksum verification and renders failures as "Needs attention"; `ModelRegistryDocumentationTest` prevents active registry rows from returning to placeholder hashes or floating source URLs.
- Completed the AGP-8.7-compatible slice of R7.3. Version catalog updates: Compose BOM 2026.05.00, Dagger Hilt 2.58, AndroidX Hilt 1.3.0, Room 2.7.2, Coroutines 1.11.0, Lifecycle 2.10.0, DataStore 1.2.1, WorkManager 2.11.2, ONNX Runtime 1.26.0, MediaPipe Tasks Vision 0.10.35, OkHttp 5.3.2, and Lottie Compose 6.7.1. Deferred because of toolchain gates: Dagger Hilt 2.59.x requires AGP 9.0+, Core/Activity/Navigation latest requires AGP 8.9.1+, and Room 2.8.x fails the current Kotlin 2.1.0 / KSP 2.1.0 schema export path. The Hilt Compose import moved to `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`, Project Card swipe-to-delete no longer uses the deprecated `confirmValueChange` veto callback, and the 16 KB checker now follows Google's `LOAD ... align 2**14` criterion.
- Closed R8.3 for the routed app shell. `MainActivity` keeps `enableEdgeToEdge()`, disables the Android 10+ navigation-bar contrast scrim, and pads the NavHost root with `safeDrawingPadding()` so projects, settings, editor, bottom sheets, and display cutouts share one safe drawing boundary.
- Closed R8.4 / R8.10 by evidence. Manifest already enables `OnBackInvokedCallback`, the only Compose `BackHandler` is gated on transient editor state, and no `KeyboardType.Password` / password visual transformation fields opt out of Compose foundation's default stylus handwriting support.
- Completed R8.8. `MainActivity` explicitly opts into resizable activity behavior and activity-embedding split readiness; `AdaptiveEditorLayoutPolicy` provides unit-tested compact/medium/expanded/tabletop decisions; expanded 840dp+ windows now get the existing desktop sidebar layout without requiring a mouse/DeX signal; `EditorScreen` applies roomier preview/timeline sizing on medium/expanded windows; `MediaPickerSheet` accepts external drag/drop media URIs through the same local-copy path as picker imports; AndroidX WindowManager now feeds half-open horizontal `FoldingFeature` posture into tabletop mode; 1000dp+ three-pane windows render ExportSheet as an embedded side pane while preserving the single live editor ViewModel. A real ActivityEmbedding `SplitPairRule` should wait until export is intentionally extracted into a separate Activity.
- Advanced R8.9 from ledger-only to a user-visible disclosure workflow. `AiUsageLedger` is autosave-persisted and exposed through `EditorState`; `ExportSheet` defaults "Disclose AI use" on for any non-empty ledger, writes a `.ai-use.json` declaration sidecar, and confirms ledger clearing; `DirectPublishEngine` includes AI disclosure text for YouTube/TikTok-style targets; `PrivacyDashboard` has a local AI ledger row; active AI edit surfaces now record usage for background removal/replacement, style transfer, upscale assist, Auto Edit, and synthesized TTS audio via `AiUsageRecordFactory`; `ExportDelegate` writes an unsigned `.c2pa-manifest.json` from `C2paExportEngine` beside AI-disclosed exports. Remaining R8.9 work is signed C2PA MP4 embedding, MP4 `udta` metadata, and future real platform-upload checkbox handling.
- Advanced R8.5 from pure policy to foreground-service monitoring. `ExportService` now registers `addThermalStatusListener` on Android 10+, polls `getThermalHeadroom(30)` once per second on Android 11+, feeds `ThermalHeadroomPolicy`, adds compact thermal status text to the export progress notification, raises debounced warning notifications, clears thermal state on terminal export/service paths, and cancels only on Android's shutdown-level thermal status. Remaining R8.5 work requires real `VideoEngine` control hooks for encoder throttling/proxy downgrade and resumable pause markers; do not fake throttling by delaying the progress poller.
- Completed R7.4 / R6.5a / R6.5c. `gradle/libs.versions.toml` and `app/build.gradle.kts` now include `ffmpeg-kit-16kb:6.1.1`; `FFmpegEngine` executes raw commands and structured helper commands through FFmpegKit async sessions, handles SAF content input, cancels sessions with coroutine cancellation, and keeps JVM tests native-free by returning unavailable off Android runtime. `LICENSE` and `docs/models.md` document the GPLv3/source-offer posture discovered from the resolved AAR resources. Remaining R6.5b work is to route specific product features through the now-active engine.
- Completed A.2 / R6.6b. `gradle/libs.versions.toml` and `app/build.gradle.kts` now include `io.github.kaleyravideo:android-deepfilternet:0.0.8`; `docs/models.md` records the Maven Central AAR, tag commit, AAR SHA-256, bundled `deep_filter_mobile_model` SHA-256, Apache-2.0 posture, and preflight 16 KB status. `NoiseReductionEngine` now loads `NativeDeepFilterNet` only on Android runtime, converts source audio to the required 48 kHz mono PCM through `FFmpegEngine`, processes fixed-size direct frames, re-encodes to M4A, and falls back to pass-through if FFmpeg or DeepFilterNet is unavailable. `FFmpegEngine` gained URI-safe raw PCM extraction and PCM-to-M4A encode helpers for this and future audio pipelines.
- Completed R7.5 / R6.10a where Media3 preserves output semantics. `gradle/libs.versions.toml` and `app/build.gradle.kts` now include `androidx.media3:media3-effect-lottie:1.10.1`; `Media3LottieTextureOverlay` adapts the official `androidx.media3.effect.lottie.LottieOverlay` renderer for non-HDR finite-window Lottie exports; `VideoEngine` selects that path per overlay and falls back to `LottieOverlayEffect` for HDR or over-long windows. `docs/models.md` records the AAR URL, SHA-256, Apache-2.0 license, and no-native status.
- Completed R6.10b by evaluation rather than adoption. `PreviewPanelMedia3ComposePolicy` and `docs/preview-media3-compose.md` record that `media3-ui-compose-material3:1.10.1` is not a safe full replacement for NovaCut's bespoke `PreviewPanel` because the official `Player` / `ProgressSlider` model is player-timeline based and `ProgressSlider` seeks internally. The current preview must remain edited-timeline aware and preserve gap recovery, still-image fallback, clip transform gestures, scopes, and Mocha chrome. Future targeted adoption can revisit lower-level `ContentFrame` / `PlayerSurface` or individual playback buttons.
- Completed R6.10c as a migration audit. `FrameExtractionPolicy` and `docs/frame-extraction-media3-inspector.md` record the `media3-inspector-frame` split, the required `androidx.media3.inspector.frame.FrameExtractor` import, and current local frame extraction paths. Small cached SDR thumbnails and current JPEG freeze frames stay on `MediaMetadataRetriever`; HDR/effect-aware/custom-decoder frame extraction should migrate to `media3-inspector-frame`.
- Completed R6.10d as a tested non-adoption. `TimelineProgressSliderPolicy` and `docs/progress-slider-media3-compose.md` record that Media3 1.10.1 `ProgressSlider` should not replace NovaCut's timeline ruler or `MiniPlayerBar`: the ruler needs edited-project time, zoom/scroll-window mapping, markers, snapping, clip hit targets, and scrub lifecycle callbacks; the mini-player already uses a standard Material3 `Slider` driven by NovaCut's external `playheadMs / totalDurationMs` state.
- Completed R6.11b. APV remains ingest-only: `EncoderCapabilityProbe.MIME_APV` is `video/apv`, `ExportColorConfidenceEngine.summarizeSources()` counts distinct inspected APV sources from persisted `SourceColorMetadata.mimeType`, and ExportSheet's existing color-confidence chip row now surfaces `Source is APV` with a large-file warning detail.
- Completed R6.12a/c. Android 16 still-image gainmap imports now distinguish `Gainmap` direction: legacy/Android 14-15 gainmaps and Android 16 `SDR_TO_HDR` remain `SourceHdrFormat.ULTRA_HDR_GAIN_MAP`, while Android 16 `HDR_TO_SDR` persists as `SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP`; `docs/models.md` records the ISO 21496-1 v1/v2 distinction. R6.12b HEIC/JPEG gain-map still-frame encoding remains open.

## Build and Verification Notes

Expected local commands:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon
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

- Start with [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ROADMAP.md](ROADMAP.md), [COMPLETED.md](COMPLETED.md), [RESEARCH_REPORT.md](RESEARCH_REPORT.md), and the latest `.ai/research/<date>/CHANGESET_SUMMARY.md` when available.
- Verify memory-derived claims against live files and `git log`.
- Prefer completing existing scaffolded engine/UI surfaces over adding new speculative features.
- Keep privacy, offline operation, F-Droid viability, and explicit user consent in every AI/cloud/model decision.
- For UI work, preserve the existing Material 3 design system and avoid pill/capsule backdrops unless the element is a true icon circle, color dot, avatar, knob, or similarly semantic shape.
- For release work, treat Android 16 target SDK, 16 KB native libraries, model delivery size, signing, and Play/F-Droid channel differences as one release-readiness checklist.

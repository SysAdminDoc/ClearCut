# Changelog

## v3.74.24 — 2026-06-04

### Compound editor-state storage migration
- Moved compound navigation editor storage into `EditorCompoundState`,
  including navigation depth and breadcrumb text.
- Kept read-only `EditorState` compatibility accessors so existing predictive
  back, breadcrumb, and editor UI reads remain stable.
- Converted the compound navigation publisher to update `state.compound`
  through nested `compound.copy(...)`.
- Updated `EditorDomainStateTest` to construct the stored compound slice
  directly.
- Bumped runtime metadata to `versionName 3.74.24` / `versionCode 161`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.23 — 2026-06-04

### Media editor-state storage migration
- Moved media/trust-report editor storage into `EditorMediaState`, including
  backup-import feedback, timeline-exchange feedback, and media relink reports.
- Kept read-only `EditorState` compatibility accessors so existing Media
  Manager, backup import, and timeline-exchange UI reads remain stable.
- Converted backup import, media relink probing, OTIO/FCPXML feedback, and
  feedback dismissal mutation paths to update `state.media` through `copyMedia`
  / nested `media.copy(...)`.
- Updated `EditorDomainStateTest` to construct and assert the stored media
  slice directly.
- Bumped runtime metadata to `versionName 3.74.23` / `versionCode 160`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.22 — 2026-06-04

### Export editor-state storage migration
- Moved export-related editor storage into `EditorExportDomainState`, including
  export config, progress, state, last exported file path, error text, start
  time, smart-render preview data, batch export queue, and saved preview config.
- Kept read-only `EditorState` compatibility accessors so existing export UI,
  sharing, gallery, and v3.69 surfaces continue to read the same properties.
- Converted `EditorViewModel` and `ExportDelegate` export mutation paths to
  update `state.export` through `copyExport` / nested `export.copy(...)`.
- Updated `EditorDomainStateTest` to construct the stored export slice directly.
- Bumped runtime metadata to `versionName 3.74.22` / `versionCode 159`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.21 — 2026-06-04

### AI editor-state storage migration
- Moved AI-related editor storage into `EditorAiState`, including requirement
  prompts, model requirement sheets, active processing tool, AI suggestions,
  usage ledger, Cut Assistant review, smart-reframe/auto-edit/TTS/noise flags,
  and noise analysis text.
- Kept read-only `EditorState` compatibility accessors so existing UI and
  delegate reads remain stable while mutation call sites move to nested state.
- Converted `AiToolsDelegate` and `EditorViewModel` AI mutation paths to
  update `state.ai` through `copyAi` / nested `ai.copy(...)`.
- Updated `EditorDomainStateTest` to construct the stored AI slice directly.
- Bumped runtime metadata to `versionName 3.74.21` / `versionCode 158`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.20 — 2026-06-04

### Editor domain-state projection
- Added a sealed `EditorDomainState` projection layer with dedicated panel,
  caption, compound, export, AI, and media slices.
- Added `EditorState.domainStates` so hosts can adopt typed domain slices while
  existing constructor storage and `copy(...)` call sites migrate incrementally.
- Added `EditorDomainStateTest` to lock the six-domain enumeration and verify
  representative field ownership across the projection.
- Narrowed the active roadmap item to the next storage migration pass for the
  new domain slices.
- Bumped runtime metadata to `versionName 3.74.20` / `versionCode 157`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.EditorDomainStateTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.19 — 2026-06-04

### Per-tool AI requirement adoption
- Routed strict model/dependency-gated AI tool dispatch through
  `AiModelRequirementSheet` before `aiProcessingTool` is set, so model-gated
  tools no longer enter processing just to show requirements.
- Added a post-preflight `runAiToolAfterRequirement` path and wired the
  sheet's `Use now` CTA to it, preventing accepted ready sheets from reopening
  their own preflight gate.
- Kept ready and fallback tools on their existing immediate execution paths,
  while known model-gated call sites now resolve `AiToolRequirements` before
  falling back to the old generic `aiRequirementPrompt`.
- Aligned requirement registry IDs with live editor dispatch IDs for denoise,
  Real-ESRGAN video upscale, and advanced style transfer, and locked the active
  model-aware IDs in `AiToolRequirementsTest`.
- Bumped runtime metadata to `versionName 3.74.19` / `versionCode 156`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest,
  `:app:compileDebugKotlin :app:testDebugUnitTest --tests
  com.novacut.editor.engine.AiToolRequirementsTest`, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.18 — 2026-06-04

### FillerRemovalPanel final deletion
- Deleted the deprecated standalone `FillerRemovalPanel` bottom-sheet route now
  that the `filler_removal` tool action opens Cut Assistant Review.
- Removed `PanelId.FILLER_REMOVAL`, the legacy editor state fields, and the old
  `show/hide/analyze/apply` ViewModel methods tied to the panel.
- Removed the unused standalone `AiFeatures.detectFillerAndSilence` path plus
  the `RemovalRegion` / `RemovalType` model that only served that route.
- Removed panel-only filler-removal strings and the deleted slot's deprecation
  suppression while keeping the active Cut Assistant filler/silence strings.
- Bumped runtime metadata to `versionName 3.74.18` / `versionCode 155`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.17 — 2026-06-04

### Mixed-render export orchestrator
- Added `MixedRenderExportPlanner` to keep mixed rendering behind conservative
  export-shape gates and to expose tested run slicing for Android execution.
- Added `VideoEngine.exportMixed(plan)` so eligible mixed plans write
  pass-through runs through `StreamCopyExportEngine`, render modified runs
  through the existing Media3 Transformer composition path, and stitch run
  outputs with `FFmpegEngine.concat`.
- Updated Transformer polling so per-run renders can complete without briefly
  marking the entire export complete between mixed-render steps.
- `ExportDelegate` now attempts mixed rendering after the pure stream-copy
  shortcut and before whole-timeline Transformer fallback while sharing the
  existing video-export finalization path.
- Added JVM coverage for mixed-render planner gates and run slicing.
- Bumped runtime metadata to `versionName 3.74.17` / `versionCode 154`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.16 — 2026-06-04

### Caption translation panel call site
- Embedded `CaptionTranslationPanel` inside the Captions editor panel and
  passed translation rows, source/target language state, target choices, edit
  callbacks, and regenerate callbacks from `EditorScreen`.
- Added `CaptionTranslationBridge` so selected-clip captions become
  `SherpaAsrEngine.TranscriptionSegment` values for the existing
  `CaptionTranslationEngine` surface.
- Added `EditorViewModel.runCaptionTranslation(targetLang)` to populate
  `captionTranslationRows` when users choose a target language.
- Row regeneration now marks a row pending and self-completes through the
  current stub translation engine so the panel does not get stuck waiting for a
  model backend that is still gated.
- Added JVM coverage for the caption-to-translation bridge.
- Bumped runtime metadata to `versionName 3.74.16` / `versionCode 153`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.15 — 2026-06-04

### Compound clip gesture closure
- Timeline long-press now dispatches compound clips to
  `EditorViewModel.openCompoundClip` while preserving the existing multi-select
  fallback for regular clips and rejected compound entries.
- The preview radial menu adds an Open action when the selected clip is a
  compound clip, routing through the same ViewModel open surface.
- The compound breadcrumb chip now renders from `EditorState` above the
  timeline, and compound entry clears clip selection, multi-select, and the
  active tool so predictive back exits the compound level first.
- Added an accessible `Open compound clip` custom action for compound timeline
  clips and unit coverage for the long-press dispatcher.
- Bumped runtime metadata to `versionName 3.74.15` / `versionCode 152`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.14 — 2026-06-04

### Media relink editor integration
- Added `EditorState.mediaRelinkReports` and wired `MediaRelinkProbe` into
  `EditorViewModel` so project-open recovery restores run a source-media scan
  before the user edits or exports.
- Missing and unverified source reports now auto-open Media Manager on project
  open and show a focused warning that counts missing/unverified media sources.
- Media Manager consumes probe reports for each asset, distinguishes Online,
  Missing, and Unverified states, and reuses the existing relink action path for
  affected source URIs.
- Relink/add-media changes refresh the probe report so Media Manager status does
  not stay stale after a replacement source is chosen.
- Added unit coverage for the project-open media relink warning copy.
- Bumped runtime metadata to `versionName 3.74.14` / `versionCode 151`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.13 — 2026-06-04

### Recovery open path
- Routed dynamic Resume/Open shortcuts directly into the editor with an
  `expectRecovery` navigation flag so stale resume shortcuts can surface a
  missing-recovery warning without warning on normal project opens.
- Switched editor startup from the legacy nullable autosave loader to
  `loadRecoveryDataWithOutcome`, preserving the existing successful recovery
  restore/dialog behavior while distinguishing loaded, future-schema, corrupt,
  and not-found outcomes.
- Future-schema and corrupt autosave outcomes now show explicit error toasts
  and block both timed autosave and manual `saveProject()` autosave writes, so
  unreadable or newer-schema recovery files are left untouched instead of being
  overwritten by the opened project state.
- Added unit coverage for recovery outcome feedback and autosave-blocking
  decisions.
- Bumped runtime metadata to `versionName 3.74.13` / `versionCode 150`.
- Verification: `:app:testDebugUnitTest --tests
  com.novacut.editor.ui.editor.RecoveryDialogTest :app:compileDebugKotlin`
  passed.

## v3.74.12 — 2026-06-04

### Release pipeline reactivation
- Extended the GitHub Actions APK workflow to package the instrumentation APK,
  verify release metadata, locate Android build tools, validate APK signatures,
  check APK ZIP alignment, and upload the androidTest artifact alongside debug
  and release APKs.
- Added `scripts/verify_release_artifacts.py` so CI fails when Gradle version
  metadata, app display version, roadmap/completed/changelog headings,
  gitignored signing inputs, tag names, or generated APK metadata drift apart.
- Switched the 16 KB native-library gate to inspect debug and release APKs
  directly, matching the artifact surface that upload/release checks consume.
- Disabled the crashing `NullSafeMutableLiveData` lint detector for this AGP
  8.7 / Kotlin 2.1 stack; the repository has no LiveData call sites for that
  detector to inspect.
- Bumped runtime metadata to `versionName 3.74.12` / `versionCode 149`.
- Verification: `git diff --check`, `scripts/verify_release_artifacts.py`,
  APK-based 16 KB checks for debug/release, `apksigner verify` for
  debug/release, `zipalign -c -P 16 -v 4` for debug/release/androidTest, and
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
  :app:assembleDebugAndroidTest` passed.

## v3.74.11 — 2026-06-04

### UI test harness bootstrap
- Added shared `NovaCutTestTags` constants and stable Compose test tags for the
  project gallery, template picker, editor shell, empty-project media import,
  media picker, export sheet, Settings, privacy dashboard dialog, and first-run
  tutorial dismissal.
- Added Compose instrumentation dependencies and
  `NovaCutSmokeTest.projectEditorExportAndSettingsSurfacesOpen()`, which opens a
  blank project through the real UI and verifies the high-risk editor, import,
  export, settings, and privacy surfaces.
- Bumped runtime metadata to `versionName 3.74.11` / `versionCode 148`.
- Verification: `git diff --check`,
  `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks`,
  and `:app:testDebugUnitTest :app:assembleDebugAndroidTest` passed. Connected
  instrumentation was blocked because ADB reported `emulator-5554` as `offline`
  after an ADB server restart.

## v3.74.10 — 2026-06-02

Hardening pass. The previous merge of divergent histories left `master` in a
non-compiling state; this release restores a clean build and fixes a batch of
correctness, data-loss, crash, resource-leak, and configuration defects found
in a full-codebase review. Unit suite: 581 passing.

### Build repair (master did not compile)
- **ProjectAutoSave** — removed a duplicated `const val` block in the
  `AutoSaveState` companion object (conflicting declarations).
- **Cut Assistant review** — `CutAssistantReviewPanel`/`CutAssistantFilterChips`
  were passing `CutAssistantEngine.ReviewProposal` into filter helpers typed for
  `SilenceDetectionEngine.CutProposal`. Retyped the chip row + panel filter to
  `ReviewProposal` with a shared `reviewProposalCategory()` classifier.
- **ProjectListViewModel** — inside `Intent(...).apply { }`, bare `extras`
  resolved to `Intent.extras` (a non-iterable `Bundle?`) instead of the
  shortcut's `Map<String, String>`. Qualified the outer extension receiver.

### Data loss
- **Smart Reframe** now calls `saveProject()` — the new aspect ratio survived
  only until the next auto-save tick before.
- **Snapshot restore** now restores `beatMarkers`, `trackedObjects`, and the
  transcript (these were captured in the snapshot but dropped on restore).
- **Mask edits** (`updateMask` / `updateMaskPoint` / freehand) are now persisted
  on mask-editor close; geometry changes were lost on restart.
- **Caption edits** — `updateCaption` now saves an undo state and persists; caption
  text/timing/style changes were applied in-memory only and lost on restart.

### UI correctness
- **Caption editor** — the edit target is re-resolved against the current clip's
  caption list, so switching clips with the panel open no longer leaves it bound
  to a stale caption (Save/Delete silently no-op'd before).
- **Color curve** — `evaluateCurveSmooth` guards duplicate-x points; a zero span
  divided to NaN and blanked the curve render.
- **Settings** — `setAutoSaveInterval` coerces to the 15–300s range instead of
  trusting the caller.
- **Preview transform gesture** — the begin/end bracket is tracked per-gesture so
  a selection change mid-drag can no longer skip `endTransformChange()`, which had
  left an orphaned undo state and an unsaved transform.

### Crashes / races
- **Timeline trim** — guarded two `coerceIn` calls in `trimClipOnTrack` that
  threw `IllegalArgumentException` ("empty range") when trimming a sub-100ms clip.
- **Color match** — the generated grade was applied to the *currently selected*
  clip rather than the clip captured when the action started (wrong-clip on fast
  reselect); frame analysis also ran on the main thread (ANR risk). Now targets
  the captured clip id and analyzes on `Dispatchers.IO`.

### Export
- **Stream-copy fast-path** no longer leaks the foreground `ExportService`. The
  service observes only the Transformer's state, which the stream-copy path never
  drives, so it (and its notification) stayed pinned forever on every successful
  trim-export. The service is now started only when falling through to the
  Transformer path.
- **GIF export** — the logical screen descriptor is sized from the largest frame
  rather than frame[0], so mixed-size frames (gap frames / unscaled narrow
  sources) no longer overflow the canvas and corrupt the file. Frame delay is
  floored at 1 centisecond.

### Resource leaks
- **AiFeatures** — `detectScenes` no longer leaks both frame bitmaps (and aborts
  the whole scan) if frame-diff throws; `generateAutoEdit` recycles its scaled
  frames in a `finally`.
- **Lottie overlay** — shader objects are deleted before the link-failure throw
  (previously leaked on every failed program link).

### Configuration
- **FileProvider** — removed a duplicate `name="archives"` path entry (the map
  collision could make archive sharing throw) and added the missing
  `templates/` external root so template sharing no longer crashes.

### Other hardening
- **Whisper** — `resample` guards a 0/negative source sample rate (a malformed
  container otherwise produced an infinite ratio → OOM).
- **Transform overlay** — rotation handle now rotates by the delta from the grab
  point instead of snapping to the absolute finger angle.

## Unreleased

### Documentation consolidation — 2026-06-01

- Consolidated root planning docs: active work now lives in `ROADMAP.md`, shipped work in `COMPLETED.md`, and research synthesis in `RESEARCH_REPORT.md`.
- Archived the previous expanded roadmap, cross-project roadmap, and May 25 research plans under `docs/archive/`.
- Left ignored local research artifacts (`RESEARCH.md`, `HostShield-Research-Report.md`, and `research/`) untouched.

### Autonomous roadmap continuation — 2026-05-25 (Loop 5)

Five-batch follow-up loop. Picks up after Loop 4 with adoption wire-ups
for every standalone composable shipped in Loops 3/4: PrivacyDashboardPanel
slot in Settings, AiModelRequirementSheet as a parallel surface, filler
removal re-route to Cut Assistant Review, caption translation
EditorViewModel orchestrator, and the compound nav back gate. All commits
local on master only.

- **Batch 29 — PrivacyDashboardPanel adopted in Settings.** New
  "Privacy" `SettingsSection` with an `Open privacy dashboard`
  `SettingsActionRow`. Tap opens a `Dialog` + `Surface` modal hosting
  `PrivacyDashboardPanel`. State hoisted via
  `var showPrivacyDashboard by remember { mutableStateOf(false) }`. New
  strings `settings_privacy_section_*` / `settings_privacy_open_*` /
  `settings_privacy_close`. Commit `6a534d5`.
- **Batch 30 — AiModelRequirementSheet wired.** Added
  `aiModelRequirement: ToolRequirement?` to `EditorState`; new
  `showAiModelRequirement(toolId)` looks up the registry and surfaces
  the sheet, `dismissAiModelRequirement()` clears. `EditorScreen`
  renders the composable next to the legacy `aiRequirementPrompt`
  AlertDialog so the two surfaces coexist during the per-tool
  dispatch migration. Commit `f277cad`.
- **Batch 31 — FillerRemoval re-routed to Cut Assistant Review.** The
  `"filler_removal"` tool action in `EditorScreen` now opens
  `viewModel.proposeCutsForReview()` — the unified
  `CutAssistantReviewPanel` with its chip-row filter supersedes the
  standalone analyse + apply flow. `FillerRemovalPanel` Composable
  marked `@Deprecated` with a `ReplaceWith(CutAssistantReviewPanel)`
  hint; remaining `BottomSheetSlot(PanelId.FILLER_REMOVAL)` call site
  marked `@Suppress("DEPRECATION")` so the build stays clean. Final
  panel deletion + ViewModel state cleanup pending. Commit `494f478`.
- **Batch 32 — Caption translation EditorViewModel surface.** Added
  five EditorState fields (`captionTranslationRows`,
  `captionTranslationSourceLang`, `captionTranslationTargetLang`,
  `captionTranslationQuality`, `captionTranslationVariant`) and four
  orchestrator methods (`setCaptionTranslationTarget(target)`,
  `applyCaptionTranslationEdit(idx, text)`,
  `regenerateCaptionTranslation(idx)`,
  `completeCaptionTranslationRegenerate(idx, text)`).
  `CaptionTranslationEngine` injected as a Hilt constructor dep. The
  `CaptionTranslationPanel` composable can now be dropped into the
  Captions sub-tab — only the EditorScreen call site is left. Commit
  `356934a`.
- **Batch 33 — Compound nav orchestrator + predictive-back gate.**
  Live `CompoundNavStack` instance held on `EditorViewModel`;
  immutable `compoundNavDepth: Int` + `compoundBreadcrumbText: String`
  state fields surfaced. New `openCompoundClip(clipId)` /
  `exitCompoundLevel()` methods. `EditorScreen`'s existing
  `BackHandler` now gates on `compoundNavDepth > 0` and pops one
  nesting level after every higher-priority dismissal branch
  exhausts. `Timeline.kt` gesture wiring is the only remaining piece.
  Commit `1e879af`.

### Autonomous roadmap continuation — 2026-05-25 (Loop 4)

Five-batch follow-up loop. Picks up after Loop 3 with two remaining
standalone composables (Privacy Dashboard, Caption Translation), two
in-place wire-ups (CutAssistantFilterChips → CutAssistantReviewPanel,
AiUseConfidenceRow → ExportSheet), and the compound nav breadcrumb
chip. All commits local on master only.

- **Batch 23 — PrivacyDashboardPanel composable.** New
  `ui/settings/PrivacyDashboardPanel.kt` consumes
  `PrivacyDashboard.groupForDisplay()` for risk-ordered sections (cloud
  + telemetry first, then on-device collected by default, then opt-in).
  Per-section icon + accent: Cloud/Peach, Computer/Sky, LockOpen/Green.
  Per-entry cards render category name + storage location + retention +
  collected-by list + control summary (Export · Delete · Opt out). Host
  wires `onEntryClicked` to the corresponding engine action. Commit
  `92601e5`.
- **Batch 24 — CaptionTranslationPanel composable.** New
  `ui/editor/CaptionTranslationPanel.kt` consumes
  `CaptionTranslationEngine.EditorRow` directly. Target-language chip
  row + quality chip (Excellent/Green, Good/Sky, Fair/Yellow,
  Experimental/Peach, Unknown/Subtext0) + `LazyColumn` of source/target
  row cards. Inline `BasicTextField` editing per row, status chip
  (Translated/Edited/Regenerating...) per `EditorRowState`, Regenerate
  button per row. Host owns the engine and threads `onUserEdit` /
  `onRegenerate` back through `engine.applyUserEdit` /
  `engine.markRegeneratePending`. Commit `dddfe6e`.
- **Batch 25 — CutAssistantFilterChips wired.** Drop-in adoption in
  `CutAssistantReviewPanel`: chip row inserted above the proposal
  `LazyColumn`. Filter state via `mutableStateOf<Set<ProposalCategory>>`
  (all-on default). Filtering goes through
  `SilenceDetectionEngine().filterByCategory(proposals, enabled)`.
  Commit `5a0fa27`.
- **Batch 26 — AiUseConfidenceRow wired.** One call site added in
  `ExportSheet.kt` after `DeviceTierOutlook` inside the
  `videoModeEnabled` branch. Same `aiUsageEntries` memo already used by
  the existing disclosure summary feeds
  `AiUsageLedger.summarizeForChips`. Commit `1cd6cc7`.
- **Batch 27 — CompoundNavBreadcrumb composable.** New
  `ui/editor/CompoundNavBreadcrumb.kt` consumes
  `CompoundNavStack.formatBreadcrumb(...)`. Hidden at root depth;
  tap-anywhere-to-exit semantics with leading back arrow + full path
  label in Mauve accent. Predictive-back gate on `stack.depth > 0` is
  the remaining EditorScreen-level wiring. New strings:
  `compound_breadcrumb_*`. Commit `012387a`.

### Autonomous roadmap continuation — 2026-05-25 (Loop 3)

Five-batch follow-up loop. Picks up after Loop 2 with the UI-integration
half of the engine-layer data contracts shipped in Loops 1 and 2. Mix of
in-place wiring (BidiFormatter wrap, dynamic shortcut push) and
standalone composables (AI requirement sheet, AI-use confidence row,
Cut Assistant filter chips) so each consumer panel can adopt them with
a single drop-in call site. All commits local on master only.

- **Batch 17 — RTL bidi wrap.** `StrokedTextBitmapOverlay.drawBitmap` and
  `ExportTextOverlay.getText` now wrap captions with
  `BidiFormatter.getInstance().unicodeWrap(text)` when
  `BidiTextPolicy.needsBidiWrap(text)` reports a strong RTL character is
  present. ASCII captions skip the wrap; Arabic / Hebrew / Persian /
  Urdu / Yiddish captions get correct paragraph direction + alignment
  via the injected U+200E / U+200F marks. Commit `4c49d9e`.
- **Batch 18 — Dynamic launcher shortcuts.** `ProjectListViewModel.init`
  now subscribes to `allProjects` and calls
  `ShortcutManagerCompat.setDynamicShortcuts(appContext, list)` whenever
  the project list changes. Mapping from `ProjectShortcutPlanner.DynamicShortcut`
  to `ShortcutInfoCompat` lives in a `toShortcutInfoCompat(ctx)`
  extension. Launcher rejections (some OEM forks throw on excess
  shortcuts) are caught and logged so the affordance is best-effort.
  Reuses `R.mipmap.ic_launcher` icon. Commit `f842fc0`.
- **Batch 19 — AiModelRequirementSheet composable.** New `AlertDialog`-shaped
  bottom sheet in `ui/editor/AiModelRequirementSheet.kt` consuming
  `AiToolRequirements.ToolRequirement`. Availability-aware title + icon +
  accent: Verified/Green for READY, Download/Sky for
  MODEL_DOWNLOAD_REQUIRED, CloudOff/Peach for DEPENDENCY_MISSING,
  Cloud/Yellow for CLOUD_OPT_IN. Renders model name + license + size + on-
  device-vs-cloud meta rows. Consent checkbox gates the primary CTA when
  `requiresOptInConsent` (cloud + voice clone). Dismiss-only when
  `DEPENDENCY_MISSING` with secondary "Review AI models". New strings:
  `ai_requirement_title_*` / `body_*` / `runtime_*` / actions. Commit
  `4cf9a71`.
- **Batch 20 — ExportSheet AI-use confidence row composable.** New
  `ui/export/AiUseConfidenceRow.kt` consumes
  `AiUsageLedger.summarizeForChips(entries)`. FlowRow of severity-coloured
  chips: Peach for `DISCLOSURE_REQUIRED`, Sky for `DISCLOSURE_RECOMMENDED`,
  Green for `INTERNAL_ONLY`. Each chip shows the bucket label + count +
  total range via `Chip.describe()`. Empty state when the project ledger
  is empty. Tap forwards through `onChipClick` so the detail panel can
  expand per-clip in a follow-up. New strings: `export_ai_use_*` +
  `export_ai_use_severity_*`. Commit `cea5b8c`.
- **Batch 21 — CutAssistantFilterChips composable.** New
  `ui/editor/CutAssistantFilterChips.kt` consumes
  `SilenceDetectionEngine.ProposalCategory`. FlowRow of toggle chips
  with per-bucket counts derived from `groupByCategory(proposals)`;
  zero-count categories are hidden; "All" chip is a convenience toggle.
  Caller owns the `enabled` state via standard `mutableStateOf<Set<...>>`.
  Drop into `CutAssistantReviewPanel` and route the filter through
  `SilenceDetectionEngine.filterByCategory(proposals, enabled)`. New
  strings: `cut_filter_*`. Commit `9164d64`.

### Autonomous roadmap continuation — 2026-05-25 (Loop 2)

Four-batch follow-up loop. Picks up after the first 2026-05-25 loop with
deferred items from `ROADMAP.md`'s "Defer to a later pull" section plus
remaining UI-helper data layers. Pure engine code only — every batch is
JVM-testable without an Android runtime; Compose UI integrations remain
deferred. All commits local on master only — origin/master is
parallel-history, do not push from this VM.

- **Batch 13 — B.5 mixed-render composer plan.** New `MixedRenderComposer`
  takes a `List<SmartRenderEngine.RenderRun>` (output of `planRuns`) and
  emits a `CompositionPlan(benefit, runs, concat, issues)`. Decision table:
  empty input → NoBenefit + warning; single run → SingleRun, run output
  named as the final output, no concat; all re-encode runs → NoBenefit
  (caller should use the existing whole-timeline Transformer path);
  mixed copy+re-encode → Mixed with `ConcatStep(inputs, outputFileName)`.
  Per-run output names encode index + engine tag (`vlog-run00-cp.mp4` /
  `-run01-re.mp4`). Short stream-copy runs (<250 ms by default) emit a
  keyframe-alignment warning. `sanitiseStem(...)` produces FAT32-safe
  stems capped at 48 chars. 10 new tests in `MixedRenderComposerTest`.
  Commit `e771dda`.
- **Batch 14 — ProjectShortcutPlanner.** Pure planner for the dynamic
  launcher long-press shortcuts that complement the static
  `res/xml/shortcuts.xml` entries. `planDynamic(State)` returns the
  ordered list the orchestrator should pass to
  `ShortcutManagerCompat.setDynamicShortcuts`. Decision table: fresh
  install → empty; user opted out → empty; last project without recovery
  → Open-last only; last project + recovery → Resume ranked first, Open
  second. Truncates project names to 25 chars for the chip label,
  bundles project-id in the intent extras, caps at MAX_DYNAMIC_SHORTCUTS
  (= 2) so dynamic + static together stay under the launcher's
  per-activity ceiling. 9 new tests in `ProjectShortcutPlannerTest`.
  Commit `0db40c3`.
- **Batch 15 — PrivacyDashboard display helpers.** New
  `Section { CLOUD_AND_TELEMETRY, ON_DEVICE_COLLECTED, ON_DEVICE_OPT_IN }`
  + `sectionFor(entry)` + `sortForDisplay()` (cloud rows first, then
  collected-by-default, then opt-in; original Category enum order
  preserved within sections) + `groupForDisplay()` (LinkedHashMap with
  empty sections omitted) + `controlSummary(entry)` ("Export · Delete ·
  Opt out" / "Read-only"). 8 new tests in `PrivacyDashboardDisplayTest`
  including a guard that every cloud category offers an opt-out toggle.
  Commit `d5e20a6`.
- **Batch 16 — RTL bidi text policy.** New `BidiTextPolicy` first-strong
  Unicode classifier returns `Direction { LTR, RTL, MIXED }`. Coverage of
  every RTL Unicode block Android renders today: Hebrew + Hebrew
  Presentation Forms, Arabic + Arabic Supplement, NKo, Syriac, Thaana,
  Arabic Presentation Forms A and B. `recommendAlignment(direction)` maps
  RTL → END and LTR/MIXED → START. Cheap `needsBidiWrap(text)` predicate
  so the overlay renderer skips `BidiFormatter.unicodeWrap(...)` on the
  common ASCII caption path. 13 new tests in `BidiTextPolicyTest`.
  Commit `594518c`.

### Autonomous roadmap continuation — 2026-05-25

Twelve-batch loop. Engine layer for every Highest-Value item in
`docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md` Phase 1-3 plus the Forward View "Now"
tier. Compose UI integrations are deferred to a focused panel-only commit.
All commits local on master only — origin/master is parallel-history,
do not push from this VM (see [[novacut-local-remote-divergence]]).

- **Batch 0 — Roadmap consolidation.** New "Active Work — 2026-05-25" section
  at the top of `ROADMAP.md` pulls forward Phase 1-3 items as a single
  checklist; items move here on completion. `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`
  committed as supporting evidence. Commit `f5621a7`.
- **Batch 1 — CI + repo hygiene.** `.github/workflows/build.yml` now fires on
  push/PR to master (was tag-only); concurrency group cancels stale runs.
  `.gitignore` no longer claims `ROADMAP.md` / `CHANGELOG.md` /
  `CODEX_CHANGELOG.md` are private (they are tracked); HostShield + `research/`
  stay gitignored with a cross-project-pollution comment. New
  `TrackedFilesAuditTest` runs `git ls-files` and fails the build if forbidden
  prefixes appear or if canonical planning docs go missing. Commit `80bc6a7`.
- **Batch 2 — Autosave schema gate.** `serialize()` writes both `version` and
  `schemaVersion = FORMAT_VERSION`. New `AutoSaveState.peekSchemaVersion(raw)`
  pure helper. New `ProjectAutoSave.LoadOutcome` sealed class
  (`Loaded`/`FutureSchema`/`Corrupt`/`NotFound`) +
  `loadRecoveryDataWithOutcome(projectId)` orchestrator refuses to fall
  through to corrupt-recovery backup when either main or backup is from a
  newer schema. Existing `loadRecoveryData` keeps its `AutoSaveState?`
  contract. 7 new tests. Commit `a2c4221`.
- **Batch 3 — MediaRelinkProbe.** New singleton with `UriOpener` seam so the
  pure decision helper `check(clipId, uri, opener)` is JVM-testable.
  Resolves to `RelinkState.OK / MISSING / UNKNOWN`. `probeClips(tracks)`
  recurses compound clips. Decision rules: positive length → OK; -1 (Photo
  Picker / SAF "unknown length") → OK; 0 → MISSING; null descriptor →
  MISSING; thrown SecurityException/IOException → MISSING with truncated
  reason; unsupported scheme → UNKNOWN. 10 new tests. Clip model unchanged.
  Commit `93222e5`.
- **Batch 4 — AI ledger chip summary.** `AiUsageLedger.summarizeForChips(entries)`
  returns stable-ordered `List<Chip>` (severity descending, then effect-kind
  alphabetical) with bucketed entry count / clip count / total range / distinct
  model names. `Chip.describe()` + `Chip.effectKindLabel` for formatted body
  text. 8 new tests. Drives the planned ExportSheet third confidence row.
  Commit `7f34f25`.
- **Batch 5 — Photo Picker for stickers.** EditorScreen launches
  `PickVisualMedia(ImageOnly)` directly for the sticker-import path, skipping
  the full MediaPicker tab sheet. Plugin file imports keep `OpenDocument`.
  Commit `d0c1ae0`.
- **Batch 6 — App Shortcuts + extended share intents.** New
  `res/xml/shortcuts.xml` registers static "New Project" + "Recent" shortcuts;
  manifest wires `<meta-data android:name="android.app.shortcuts">` + adds
  `image/*` and `audio/*` `ACTION_VIEW` filters (content:// only, with
  rejection enforced in `handleViewIntent`). MainActivity gains
  `ACTION_NEW_PROJECT` / `ACTION_OPEN_RECENT` constants. Commit `fcb5e3e`.
- **Batch 7 — Diagnostic timeline-shape.** New
  `DiagnosticExportEngine.TimelineShape` carries counts only
  (`trackCount`, `totalDurationMs`, `perTrackClipCount`, `perEffectTypeCount`,
  `perTransitionTypeCount`). `exportDiagnosticBundle(..., timelineShape: TimelineShape?)`
  adds `timeline-shape.json` only when caller passes a non-null shape.
  Hand-rolled JSON writer escapes `\`, `"`, control chars. 6 new tests lock
  the "no clip names / URIs / captions" invariant by injecting secrets and
  asserting absence. Commit `95c1235`.
- **Batch 8 — AI tool requirements registry.** New `AiToolRequirements` with
  16-tool registry. `requirementFor(toolId)` returns
  `ToolRequirement(modelDisplayName, estimatedBytes, license, sourceUrl,
  runtimeLocation, availability, requiresOptInConsent)`. 8 tests lock
  invariants: every tool has an entry, cloud tools always require consent
  and are never READY, on-device tools have non-zero estimates, at least
  one tool is READY. Commit `59a76a1`.
- **Batch 9 — Compound nav UI helpers.** `CompoundNavStack.canPush(clip)`
  non-throwing predicate (cycle / depth / non-compound). `formatBreadcrumb(...)`
  returns single-line breadcrumb string with configurable separator + root
  label + blank-name fallback. 7 new tests. Commit `565f34d`.
- **Batch 10 — Caption translation editor rows.** `CaptionTranslationEngine`
  gains `EditorRow(index, segment, quality)` view model + four immutable
  list operations: `buildEditorRows()`, `applyUserEdit()`,
  `markRegeneratePending()`, `completeRegenerate()`. 7 new tests. Commit
  `540001d`.
- **Batch 11 — Cut Assistant chip buckets.** `SilenceDetectionEngine.ProposalCategory
  { SILENCE / SINGLE_WORD_FILLER / MULTI_WORD_FILLER / OTHER }` +
  `categorize(proposal)` + `filterByCategory(proposals, enabled)` (identity-
  filter optimization) + `groupByCategory(proposals)` (insertion-order
  preserving). 8 new tests. Commit `3b476f2`.
- **Batch 12 — ProjectColorPolicy.** Two enums
  (`WorkingColorSpace { SDR_BT709, HDR10_BT2020_PQ, HDR_HLG, ACES_AP1 }` +
  `DisplayTransform { NONE, BT2390_TONEMAP, HABLE_TONEMAP }`). Pure
  `coherence()` decision table + `deliversHdr` predicate. Lives separate
  from Room-backed `Project` so adoption doesn't force schema migration.
  7 new tests. Commit `cb2eaa4`.

### Autonomous roadmap continuation — 2026-05-17

- **R6.10d — Media3 ProgressSlider evaluation.** Added
  `TimelineProgressSliderPolicy`, focused JVM tests, and
  [docs/progress-slider-media3-compose.md](docs/progress-slider-media3-compose.md).
  The custom timeline ruler stays because it is an edited-project ruler with
  zoom, scroll-window, marker, snap, clip-hit-target, and scrub lifecycle
  behavior. `MiniPlayerBar` keeps its standard Material3 `Slider` because it is
  externally controlled by `playheadMs / totalDurationMs` and delegates to
  NovaCut's `onSeek(Long)` callback.
- **R6.10c — Media3 inspector-frame audit.** Added
  `FrameExtractionPolicy`, focused source-tree guard tests, and
  [docs/frame-extraction-media3-inspector.md](docs/frame-extraction-media3-inspector.md).
  Current cached SDR timeline thumbnails, contact-sheet thumbnails, AI sample
  frames, and JPEG freeze frames stay on `MediaMetadataRetriever`; future HDR
  review frames, effect-aware thumbnails, or custom decoder-selection work must
  use `androidx.media3:media3-inspector-frame` and
  `androidx.media3.inspector.frame.FrameExtractor`. No unused inspector-frame
  dependency was added.
- **R6.10b — Media3 Compose Player evaluation.** Added
  `PreviewPanelMedia3ComposePolicy`, focused JVM tests, and
  [docs/preview-media3-compose.md](docs/preview-media3-compose.md) to record
  why NovaCut should keep its custom `PreviewPanel` for Media3 1.10.1. The
  Material3 `Player` / `ProgressSlider` path is player-timeline based and
  performs native seeks internally; NovaCut's editor preview still needs
  edited-timeline seeking, gap recovery, still-image fallback, transform
  gestures, scopes, and custom Mocha chrome. Targeted `ContentFrame` /
  playback-button adoption remains a future revisit, but no dependency was
  added.
- **R7.5 / R6.10a — Media3 Lottie renderer adoption.** The app now
  depends on `androidx.media3:media3-effect-lottie:1.10.1` and routes
  eligible export-time animated title overlays through the official Media3
  `LottieOverlay` renderer via `Media3LottieTextureOverlay`. The adapter
  keeps NovaCut's existing title semantics: overlay-relative animation time,
  zero alpha outside the title window, TextDelegate replacements, and
  full-frame canvas sizing. The legacy `LottieOverlayEffect` shader remains
  only for HDR exports and title windows longer than the composition duration,
  where Media3 would otherwise change output behavior.
- **A.2 / R6.6 — DeepFilterNet Android activation.** The default app now
  pins `io.github.kaleyravideo:android-deepfilternet:0.0.8`; the resolved
  AAR was inspected from Maven Central, mapped to Git tag `v0.0.8`, and
  recorded in `docs/models.md` with AAR/model SHA-256 values, Apache-2.0
  license posture, an arm64-v8a/x86_64 AAR 16 KB preflight, and a final
  debug APK alignment pass with 32 OK native libs, 40 skipped 32-bit libs,
  and 0 misaligned libs.
  `NoiseReductionEngine` now gates native availability off plain JVM,
  decodes source audio to 48 kHz mono signed 16-bit PCM through
  `FFmpegEngine`, processes fixed-size direct `ByteBuffer` frames with
  `NativeDeepFilterNet` on Android, re-encodes cleaned PCM to M4A, and
  falls back to the old pass-through path if FFmpeg or DeepFilterNet is
  unavailable. `FFmpegEngine` now exposes URI-safe raw PCM extraction and
  PCM-to-M4A encode helpers for future audio pipelines.
- **R7.4 / R6.5 — FFmpeg 16 KB integration.** The default app now pins
  `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` through the version
  catalog and app dependency graph. `FFmpegEngine` is no longer a pure
  stub: raw commands and structured argument-list helpers run through
  FFmpegKit async sessions with cancellation, completion progress, SAF
  content-URI input support, and implementations for stream-copy trim,
  concat demuxer, WAV extraction, subtitle burn-in, loudnorm, and
  speed-change helpers. Plain JVM tests still report FFmpeg unavailable
  to avoid loading Android native libraries outside ART. The resolved
  debug APK verifies 30 required 16 KB-aligned native libs and 0
  misaligned libs. [LICENSE](LICENSE) and [docs/models.md](docs/models.md)
  now treat the dependency as GPLv3-obligation material because the AAR
  packages GPLv3 license/source-offer resources even though the Maven POM
  declares LGPL-3.0.
- **R5.5d / R7.1 — Settings diagnostic ZIP workflow.** Settings now exposes
  a local-only Diagnostic ZIP row that creates the existing
  `DiagnosticExportEngine` bundle, shows busy / success / error state, stores
  the ZIP under `filesDir/diagnostics/`, and shares it only through
  `FileProvider` + `ACTION_SEND` after explicit user action. The share grant
  is scoped to the diagnostics directory; project files, media, captions,
  transcripts, and autosave JSON remain excluded by engine design.
- **R7.2 — Active model checksum closure.** `docs/models.md` now pins the
  active Whisper ONNX files, MediaPipe selfie segmenter, and LaMa inpainting
  model to exact source locators and SHA-256 values. Whisper, segmentation,
  and inpainting downloads pass those hashes through
  `ModelDownloadManager.ModelFile(checksumRequired = true)`, Settings verifies
  installed model state before reporting storage, checksum failures render as
  a red "Needs attention" state, and `ModelRegistryDocumentationTest` blocks
  active registry rows with placeholder hashes or floating source URLs.
- **R7.3 — Dependency stabilization train.** The AGP-8.7-compatible catalog
  train now uses Compose BOM 2026.05.00, Dagger Hilt 2.58, AndroidX Hilt
  1.3.0, Room 2.7.2, Coroutines 1.11.0, Lifecycle 2.10.0, DataStore 1.2.1,
  WorkManager 2.11.2, ONNX Runtime 1.26.0, MediaPipe Tasks Vision 0.10.35,
  OkHttp 5.3.2, and Lottie Compose 6.7.1. Latest Core / Activity /
  Navigation are deferred because their AAR metadata requires AGP 8.9.1+,
  Hilt 2.59.x requires AGP 9.0+, and Room 2.8.x currently fails this repo's
  Kotlin 2.1.0 / KSP 2.1.0 schema export path.
  The train also moves Hilt Compose imports to the new lifecycle package,
  removes the deprecated Project Card swipe veto callback, corrects the local
  ELF alignment checker to match Google's `LOAD ... align 2**14` guidance, and
  verifies both ELF and APK zip alignment for the debug build.
- **R8.7 — Per-app language preferences.** AndroidManifest now declares
  `android:localeConfig="@xml/locales_config"` and the new
  `app/src/main/res/xml/locales_config.xml` carries the English baseline.
  Adding a new locale is now a two-line change: drop the translated
  `res/values-<bcp47>/strings.xml`, then append one `<locale>` entry. NovaCut
  appears in Settings → System → Languages & Input → App Languages the
  moment more than one locale is listed.
- **R8.6 / R8.13 — Photo Picker + Auto Backup audits closed.** Photo
  Picker compliance and Auto Backup XML rules were already in place; ROADMAP
  reframed and README's permission table corrected (NovaCut requests no
  broad `READ_MEDIA_*` perms — the system Photo Picker grants per-URI
  access exclusively).
- **R8.3 — Edge-to-edge audit.** `MainActivity.onCreate` now sets
  `window.isNavigationBarContrastEnforced = false` (API 29+ guarded) so
  the 3-button-nav scrim no longer paints over edge-to-edge content. The
  NavHost root modifier moved from `systemBarsPadding()` to
  `safeDrawingPadding()` so every nav destination handles display cutouts
  alongside system bars without per-screen Scaffold rewrites.
- **R8.4 — Predictive back verified done by existing pattern.**
  AndroidManifest already declares `android:enableOnBackInvokedCallback`;
  the single `BackHandler` in `EditorScreen` is gated on transient editor-state
  predicates so the system runs the back-to-home predictive animation
  whenever the editor is idle.
- **R8.8 — Adaptive resizability first pass.** MainActivity now explicitly
  opts into resizable activity behavior and handles additional
  smallest-screen/density/ui-mode config changes. A pure
  `AdaptiveEditorLayoutPolicy` classifies compact / medium / expanded /
  tabletop pane modes, expanded 840dp+ tablets now promote to NovaCut's
  existing desktop sidebar layout even without a mouse signal, and the editor
  gives medium/expanded windows roomier preview/timeline heights. MediaPicker
  also accepts external drag-and-drop media URIs and imports them through the
  same local-copy path as picker selections. AndroidX WindowManager now feeds
  half-open horizontal `FoldingFeature` posture into the editor so tabletop
  sizing is driven by real foldable state. The manifest opts into
  activity-embedding split readiness, and 1000dp+ three-pane windows render
  ExportSheet as an embedded side pane so export controls no longer cover the
  editor on tablet/desktop-class windows.
- **R8.10 — Stylus handwriting verified default-on.** Compose BOM
  2026.05.00 pulls `foundation` ≥ 1.7.0; a grep showed zero
  `KeyboardType.Password` declarations across the codebase, so every
  shipping caption / marker / project-rename / AutoEdit text field is
  eligible for system-managed stylus handwriting on Android 14+.
- **R8.9 — AiUsageLedger engine + autosave persistence.**
  `AiUsageLedger` records per-`(clipId, effectKind, model, range)` AI
  usage on a project, classifies it into `DISCLOSURE_REQUIRED` /
  `DISCLOSURE_RECOMMENDED` / `INTERNAL_ONLY`, merges overlaps, and
  round-trips through a new `AutoSaveState.aiUsageLedger` field
  (capped at 2 000 entries). `EditorViewModel.recordAiUsage` and
  `clearAiUsageLedger` are the call sites for generative engines. The
  export sheet now defaults "Disclose AI use" on for any non-empty ledger,
  writes a matching `.ai-use.json` declaration sidecar, and lets users
  clear the project ledger through a confirmation dialog. Direct publish
  share metadata includes the ledger summary for YouTube/TikTok-style
  disclosure targets, and the Privacy Dashboard model now includes a
  local/exportable/deletable AI ledger row. Remaining embedded media work:
  C2PA assertions and MP4 `udta` tagging.
- **R8.9 — active AI tools now write disclosure ledger records.**
  Background removal/replacement, background-analysis fallback, style
  transfer, upscale assist, Auto Edit, and synthesized TTS audio now append
  `AiUsageLedger` entries only after a successful project mutation. Model
  requirement prompts remain non-recording until an AI tool actually changes
  media.
- **R8.9 / R8.2 — unsigned C2PA manifest sidecar.**
  `C2paExportEngine` can now serialize its manifest spec to stable JSON, and
  ExportDelegate writes a sibling `.c2pa-manifest.json` beside AI-disclosed
  exports. This preserves the machine-readable Content Credentials payload
  before the native signer AAR is wired.
- **R8.5 — ThermalHeadroomPolicy engine.** Pure-Kotlin policy translating
  `(PowerManager ThermalStatus, getThermalHeadroom forecast)` into a
  `ThrottleDecision` (FULL_SPEED / THROTTLE_LIGHT / THROTTLE_HEAVY /
  PAUSE / CANCEL) with bounded parallel filter passes, proxy-resolution
  fallback, NaN-headroom recovery, and notification debouncing on
  action transitions. `shouldOfferOvernightSchedule` gates a "Schedule
  for overnight" Settings entry once an estimated render crosses 30
  minutes. Engine layer fully unit-tested on the JVM.
- **R8.5 — ExportService thermal monitor.** The foreground export service
  now registers `addThermalStatusListener` on Android 10+, polls
  `getThermalHeadroom(30)` once per second on Android 11+, and feeds the
  combined status + forecast signal into `ThermalHeadroomPolicy`. The
  progress notification shows compact thermal state text, separate
  debounced warnings fire on light/heavy/pause/stop transitions, thermal
  state is cleared on cancel/complete/error/destroy, and the service only
  hard-cancels when Android reports shutdown-level thermal pressure.
  Remaining R8.5 work is real `VideoEngine` throttling/proxy downgrade and
  resumable pause markers once Media3 export control hooks exist.
- **R8.2 — C2paExportEngine scaffold.** New `C2paExportEngine` builds
  a structured `C2paManifest` (training-mining opt-out, thumbnail
  claim, `c2pa.created` action, and an AI-actions assertion derived
  from `AiUsageLedger`) with four `SigningMode`s (ANDROID_KEYSTORE /
  STRONGBOX / USER_PEM / WEB_SERVICE). `AiUsageLedger.EffectKind` maps
  to canonical C2PA action labels (`c2pa.created` for cloud generative
  paths; `c2pa.edited` for substantial local AI edits; `c2pa.opinion`
  for assistive helpers) and C2PA v2 `digitalSourceType` IRI suffixes
  (`trainedAlgorithmicMedia` / `compositeWithTrainedAlgorithmicMedia`
  / `algorithmicMedia` / `minorHumanEdits`). `signAndEmbed` returns
  `Unavailable` until the c2pa-android AAR is wired. 13 new tests
  lock the manifest construction, AI-actions mapping, classification
  filter, and stub fallbacks.
- **R8.15 — LNP scope classifier for OutputStreamingEngine.**
  `OutputStreamingEngine.classifyNetworkScope(url)` +
  `requiresLocalNetworkPermission(url)` route any RTMP / SRT / RIST /
  WebRTC / RTSP destination into `PUBLIC_INTERNET` / `LOCAL_LAN` /
  `MULTICAST` / `LOOPBACK` before the streaming library is wired. Lets
  the future R6.17 UI gate the Android 16 Local Network Protection
  consent sheet on real LAN destinations only, not on Twitch / YouTube
  / etc. Nine engine tests cover RFC1918 ranges, multicast, link-local,
  IPv6 heuristics, mDNS, user-info stripping, and malformed URLs.
- **Verification recovery.** Restored the JVM unit-test baseline by making
  `AutoSaveState.deserialize()` accept an injectable URI parser with Android's
  parser as the production default, so JVM tests can use the repo's `FakeUri`
  instead of relying on stubbed Android framework behavior.

### Roadmap Round 6 — 2026-05 refresh (Next / Later tier engine + docs pass)

Second autonomous pass continuing the Round 6 work. All commits land at the
engine + test + ROADMAP layer; the corresponding Compose UI commits follow
once a host with Android Studio is available. No new Maven dependencies
added; ~90 new JVM unit tests across the batch.

- **A.10 — Oboe resampler scaffold.** Reflection probe, pinned Maven coords,
  pure-math `estimatedOutputFrames(input, fromHz, toHz)` helper safe to call
  before the dep is wired. 8 new tests.
- **R5.7a/b/c — Plugin format family + OpenFX descriptor + compatibility
  matrix.** `PluginRegistry` (.novacut-template / .ncfx / .ncstyle / .cube /
  .3dl / .ncfxd) classification with longest-extension-first detection;
  `OpenFxDescriptor` JSON parser with permissive parameter validation and
  `toOpenFx` / `fromOpenFx` round-trip math; [docs/templates.md](docs/templates.md)
  Lottie / dotLottie / Rive / Glaxnimate compatibility matrix. 20 new tests.
- **R5.9a/b — Supply chain + non-bypassable checksum.** `.github/dependabot.yml`
  watches Gradle and GitHub Actions with grouped PRs. `ModelDownloadManager`
  gains `requireChecksum: Boolean` and `verifyChecksumOrDelete()` for fail-
  closed first-run verification. 4 new tests.
- **R6.4a/b — SAM 3 / SAM 3.1 watch item.** `TapSegmentEngine.SAM3_HIERA_TINY_ONNX_PLACEHOLDER`
  enum entry behind `SAM3_PLACEHOLDER_ENABLED` flag (off by default). New
  `segmentByTextPrompt(bitmap, prompt)` stub returns null today; SAM 2.1 stays
  the default tracked-mask target until an ONNX-export Tiny variant ships.
- **R6.11 — APV codec ingest probe.** `EncoderCapabilityProbe.probeApvIngest()`
  returns `ApvSupport` with `hasDecoder` / `isHardwareDecoder` / `decoderNames`.
  `ExportColorConfidenceEngine` now feeds a `Source is APV` chip into ExportSheet
  when imported source metadata records `video/apv`; no encoder path by design
  (R6.11c — APV is ingest-only). 7 tests cover the probe and export-summary
  contracts.
- **R6.12a/c — Ultra HDR gain-map direction.** `MediaImportEngine` now
  distinguishes Android 16 HDR-base + SDR-gainmap still images from the older
  SDR-base + HDR-gainmap path, persists the new source format through existing
  `hdrFormats`, and `docs/models.md` records the ISO 21496-1 distinction.
- **R6.14a — Multicam SmartSwitch planner.** Pure-Kotlin `SpeakerSwitchPlanner`
  with first-appearance round-robin angle assignment, explicit-assignment
  override, redundant-cut coalescing, and a `minDwellMs` flicker guard.
  11 new tests.
- **R6.15a/b — AI Animated Subtitles per-word emphasis.** `WordEmphasisAnimator`
  with POP / BOUNCE / GLOW / SLIDE_IN animations, `wordProgress(playhead,
  start, end, window)` bridge from Whisper word timestamps, and
  `DEFAULT_MAX_CONCURRENT_ANIMATING_WORDS = 3` performance budget. 17 new tests.
- **R6.17a — Live streaming output scaffold.** `OutputStreamingEngine` with
  6-protocol enum, multi-library reflection probe (Stream-Pack / Larix /
  LibSRT-Android), pure `validateDestination(protocol, url)` and
  `recommendedBitrateBps(w, h, fps)` pre-flight helpers. 11 new tests.
- **R5.4a + R6.7 — Caption translation data model.** New `BERGAMOT_PER_PAIR`
  model variant, MADLAD count 400 → 419, `EditorRowState` (TRANSLATED /
  USER_EDITED / REGENERATE_PENDING), `LanguagePairQuality` lookup with
  locale-suffix + case-insensitive matching. 12 new tests.
- **R5.4d — Locale-aware Noto caption font fallback policy.** `CaptionFontFallbackPolicy`
  routes BCP-47 / ISO-639-1 tags to the right Noto subset (CJK SC / TC / JP /
  KR, Arabic, Hebrew, Devanagari, Bengali, Tamil, Thai) with per-family
  bundle-byte disclosure. zh-Hant / zh-Hans script split, case-insensitive.
  17 new tests.
- **R5.5c — Privacy dashboard data model.** `PrivacyDashboard` is the single
  source of truth for every data category NovaCut collects: location,
  controls (export/delete/opt-out), collecting engines, retention policy,
  default-collection state. Cloud + telemetry categories are forced off by
  default (matches R5.9c contract). 10 invariant tests.
- **C.2 — Silence + filler-word auto-cut follow-ups.** `SilenceDetectionEngine`
  gains `detectMultiWordFillers(words, config, phrases)` with longest-match-
  first sliding window and `mergeProposals(cuts, mergeGapMs)` to deduplicate
  silence + filler proposals into a single Cut Assistant review list.
  12 new tests.
- **C.11 — Adjustment layer export-pipeline helper.** `AdjustmentLayerEngine.planForClip()`
  returns an ordered list of `AdjustmentLayerSegment(timelineRange, effects)`
  ready for EffectBuilder to consume per export segment. 5 new tests bring
  the file to 15 covering the full plan / partition / effects-for-clip surface.
- **C.12 — Keyframe bezier graph data model.** `KeyframeBezierGraph` ships the
  per-segment cubic bezier evaluator (`evaluate`, `evaluatePoint`), canonical
  unit-segment presets for all 12 NovaCut easings, and `rescale(seg, start,
  end)` for runtime denormalization. 14 new tests.
- **C.13 — Compound clip nested-sequence navigation stack.** `CompoundNavStack`
  with push / pop / reset / breadcrumb / depth, cycle detection, MAX_DEPTH
  cap, and autosave-friendly `toSerializedIds()` / `restore(clips)` round-
  trip. 11 new tests.
- **Tier A activation-docs sweep.** `FrameInterpolationEngine` (A.4 — RIFE +
  NCNN + Vulkan zero-copy pipeline + Snapdragon 8 Gen 3 numbers),
  `UpscaleEngine` (A.5 — Real-ESRGAN x4plus + tile-and-blend constants),
  `VideoMattingEngine` (A.6 — RVM MobileNet + recurrent state pattern),
  `StabilizationEngine` (A.3 — OpenCV + R6.9 Gyroflow-sidecar-first
  directive), `StyleTransferEngine` (A.11 — AnimeGAN + Fast NST opt-in
  download model), `RiveTemplateEngine` (A.13 — R6.16 Under-Consideration
  rationale + reflection probe), `LottieTemplateEngine` (R6.16 — dotLottie
  + state-machine upgrade plan).
- **Tier C Later-tier helpers.** `StockAssetEngine` gains `validateQuery()` +
  `attributionLine()` + API doc constants (C.7). `CameraCaptureEngine`
  gains reflection probe + `teleprompterVisibleWordCount()` helper (C.8).
  `TimelineImportEngine` gains `roundTripFidelity(format)` for the import
  UX warning copy (C.14).
- **Verification** — `git diff --check` passed. Gradle tests could not be
  run in this environment (no JDK). The Round 6 second-pass added ~90 new
  JVM unit tests; total new tests since the Round 6 refresh started:
  ~125. All need a `gradlew testDebugUnitTest` run on a host with a JDK
  to verify.

### Roadmap Round 6 — 2026-05 refresh (engine + docs pass)

This batch processes the Now-tier items from the ROADMAP Round 6 Forward
View. Each line links to its roadmap ID. No new Maven dependencies were
added — every change is either a docs-only refresh, a Kotlin-only refactor
of an existing engine, or a new pure-Kotlin engine.

- **R6.1 — 16 KB page-size compliance gate** is now enforced. New
  `scripts/check_16kb_alignment.py` parses ELF PT_LOAD segments without
  needing NDK `readelf`; CI workflow runs it after `assembleRelease` and
  fails the build on misalignment. Required because `targetSdk = 36`
  (Android 16) means Play Store rejects non-compliant native libs at
  upload time.
- **R6.1c / R5.6b — Model registry** lives at
  [docs/models.md](docs/models.md). Records every shipped or planned
  model, native AAR, cloud provider, and the F-Droid NonFreeNet posture
  per source domain. Tier A engines block on the SHA-256 ⚠ TBD column
  before activation.
- **R6.2a — NNAPI deprecation** removed from `InpaintingEngine`. The
  `addNnapi()` call is gone; default CPU EP runs portably. Docstring
  rewritten to link the migration guide and point QNN/LiteRT futures at
  R6.2.
- **R6.4a/b — SAM 3 watch item** scaffolded. `TapSegmentEngine` gains
  `ModelFamily.SAM3`, `SAM3_HIERA_TINY_ONNX_PLACEHOLDER` enum entry, a
  `SAM3_PLACEHOLDER_ENABLED` feature flag (off), and a stub
  `segmentByTextPrompt(bitmap, prompt)` method. The recommender keeps SAM
  2.1 Hiera Tiny as the default until a mobile-export ONNX ships.
- **R6.5a — `ffmpeg-kit-16kb`** is now the documented A.9 target.
  `FFmpegEngine` carries the full activation path in its docstring
  (catalog entry, build.gradle line, license obligation) and its
  `isAvailable()` does a reflection probe so consumers can branch on
  FFmpeg presence the moment the dep is added.
- **R6.6a — DeepFilterNet 3** is now the documented A.2 target.
  `NoiseReductionEngine` exports `TARGET_MODEL_*` constants and
  documents the model-bytes override path for AAR bundles that still
  carry v2. Cleaned up a duplicate `companion object` declaration.
- **R6.8a/b — Three-target Sherpa-ONNX policy.** New
  `WHISPER_LARGE_V3_TURBO_MULTILINGUAL` variant with
  `requiresPremiumTier = true` and `minimumRamMb = 6_144`.
  `preferredModelFor(language, allowPremiumModels, availableRamMb)`
  picks Turbo only when multilingual + premium-models enabled + RAM
  floor met; otherwise falls back to Whisper Tiny multilingual. 5 new
  tests lock the policy.
- **R6.10a — `media3-effect-lottie` migration plan** recorded in
  `LottieOverlayEffect` docstring. Custom impl stays in place until the
  three parity gaps (time-windowed alpha, TextDelegate text substitution,
  HDR-aware sampling) are verified against the official module.
- **R6.21 — Opus audio import** now works through both picker paths. The
  audio launcher MIME filter is `arrayOf("audio/*", "application/ogg")`
  so files some Android pickers still label with the legacy Ogg container
  MIME are discoverable.
- **C.6 — Audio mastering presets** are now wired end-to-end. New
  `AudioMasteringEngine.buildEffectChain(preset)` converts a curated
  recipe (Podcast Voice, Music Master, Dialogue Clean, ASMR, Social Loud)
  into the ordered HighPass → ParametricEQ → De-esser → Compressor →
  Limiter `AudioEffect` list. `AudioMixerDelegate.applyMasteringPreset`
  replaces a track's audio chain in a single undoable pass. 6 new
  conversion tests cover stage ordering, conditional skips, EQ
  zero-fill, de-esser threshold scaling, limiter ceiling, and compressor
  param round-trip.
- **B.5 — Mixed copy/re-encode segment stitching scaffold.** New
  `SmartRenderEngine.planRuns(segments)` groups consecutive same-flag
  segments into `RenderRun`s, breaking on either flag change or timeline
  gap. 8 new tests lock the merge rules. The composer step that
  concatenates per-run outputs waits on R6.5 so FFmpeg's concat demuxer
  is available.
- **R5.4c — Strings audit on engine stubs** complete. The Round 5 claim
  that engines call `Toast.makeText` was incorrect for the current
  codebase (zero hits). Added `EngineStringExtractionAuditTest` so a
  future commit can't introduce a hardcoded engine-side toast.
  Diagnostic message fields on result records (33 across
  `ProjectArchive`, `TemplateCompatibility`,
  `TimelineExchangeValidator`) are tracked as a separate localization
  workstream.
- **R5.5d — Local-only diagnostic export** engine layer shipped.
  `DiagnosticExportEngine` writes a ZIP under `filesDir/diagnostics/`
  with app/device/codec/model/logcat sections — sensitive substrings
  redacted before write; project content, media URIs, captions,
  autosave snapshots never included by contract. Self-prunes past 3
  ZIPs. 8 new redaction + bundle-structure tests. Settings UI wiring
  (FileProvider + ACTION_SEND) is a focused ~10-line follow-up.
- **Multi-sequence export now carries NovaCut layer opacity into Media3.** `VideoEngine` builds per-input compositor layer metadata and applies it through `NovaCutVideoCompositorSettings`, so visible video/overlay tracks keep their track opacity in the real Media3 composition path.
- **Blend fallback coverage now matches the 18-mode UI.** Hue, Saturation, Color, and Luminosity no longer fall through to Normal; the single-texture fallback now gives every exposed blend mode a distinct result while the roadmap keeps the true programmable dual-texture compositor gap explicit.
- **Editor recovery, project autosave, publish metadata, template import, LUT import, and project naming paths were hardened.** The pass adds defensive caps and safer discard behavior around paths that can otherwise lose work, parse oversized data, or create brittle saved state.
- **Project home, settings, template, media picker, and snackbar surfaces received a premium polish pass.** Reused shared chips, strengthened busy/empty/disabled/accessibility states, tightened rename validation, and improved card semantics.
- **Verification** — `git diff --check` passed. Gradle tests could not run in this environment because no Java runtime or `JAVA_HOME` is installed. The Round 6 batch above added 35+ new JVM unit tests; they have not been executed in this environment and need a `gradlew testDebugUnitTest` run on a host with a JDK to verify.

## v3.74.9 — 2026-05-14 — Caption accessibility presets

- **Caption Style Gallery now includes accessible presets.** A dedicated section adds WCAG-AA high-contrast, large-text, and reduced-motion caption looks instead of forcing readability work through decorative templates.
- **Template application now carries real caption style intent.** Applying a template updates caption type, font, fill, background, highlight, position, outline color/width, and shadow state so the preset affects the actual caption data, not just the gallery preview.
- **Caption stroke metadata now survives recovery.** Caption autosave serializes and restores outline color and width, and preview rendering uses the stored stroke settings for more legible text over busy video.

## v3.74.8 — 2026-05-14 — Keyboard timeline editing

- **Timeline clips are now keyboard-focusable.** Focus traversal can land directly on visible clip nodes, with a visible focus border and the same select/delete/split affordances exposed through keyboard events.
- **Arrow keys can move focused clips without touch.** Focused clips respond to left/right arrows with 100 ms nudges, and Shift+left/right uses a 1 second nudge; trim mode maps the same keys to slip edits.
- **Selected clips gain root-level nudge shortcuts.** Shift+left/right nudges the selected clip even when focus is on the editor shell, while Ctrl+Shift+left/right nudges by one second.

## v3.74.7 — 2026-05-14 — Generative video cloud policy

- **Generative video is now codified as cloud-optional only.** `GenerativeVideoPolicy` records Wan 2.2, HunyuanVideo, and VideoCrafter2 as optional cloud providers rather than on-device bundled engines.
- **Cloud trust requirements are enforced in code.** Future integrations must disclose destination, estimated upload size, data retention, and collect explicit consent before a cloud render can start.
- **Policy tests prevent accidental bundling.** JVM coverage asserts that known large video-generation providers cannot be treated as bundled on-device engines and cannot run without consent.

## v3.74.6 — 2026-05-14 — SAM 2.1 tracked-mask target

- **Tap-to-segment now has a concrete SAM 2.1 target policy.** `TapSegmentEngine` records SAM 2.1 Hiera Tiny ONNX as the default tracked-mask target and keeps MobileSAM as the small-device fallback.
- **Premium-device gating is explicit.** The engine now models model bytes, state-cache bytes, minimum RAM, video-propagation support, and the >200 MB premium working-set threshold instead of treating SAM as an undifferentiated stub.
- **Regression tests cover the recommendation policy.** JVM tests lock in SAM 2.1 selection on premium devices and MobileSAM fallback when premium downloads are disabled or memory is insufficient.

## v3.74.5 — 2026-05-14 — Sherpa-ONNX Moonshine v2 target

- **Sherpa-ONNX now targets v1.13.2.** The ASR stub records the current official Android AAR release asset and the minimum Moonshine v2 support line instead of the stale 1.10-era dependency note.
- **Moonshine v2 is the English ASR target.** `SherpaAsrEngine` now codifies Moonshine v2 Tiny as the default English model target and keeps Whisper Tiny multilingual as the fallback for non-English transcription.
- **The native payload remains intentionally gated.** NovaCut does not vendor the 50+ MB AAR into the base app until the packaging path is explicit; the runtime still falls back to the built-in Whisper ONNX engine.

## v3.74.4 — 2026-05-14 — Timeline accessibility actions

- **Timeline clips now have richer TalkBack descriptions.** Clip semantics include the clip name, clip type, track type, duration, start time, selected state, and locked-track state instead of relying on custom-drawn visuals.
- **Screen-reader users can edit clips directly.** Each unlocked timeline clip exposes custom accessibility actions for split, delete, nudge earlier, and nudge later; the actions select the clip first and reuse the existing editor operations.
- **Split from accessibility actions is resilient.** When the playhead is not inside the focused clip, NovaCut moves it to a safe midpoint before invoking the same split path used by the toolbar.

## v3.74.3 — 2026-05-14 — Ultra HDR source ingest

- **Import now records source color metadata.** `MediaImportEngine` inspects imported video tracks for HDR10, HDR10+, HLG, and Dolby Vision metadata and stores the result on clips for future export decisions.
- **Ultra HDR still-image gain maps are detected on Android 14+.** Imported image sources now check `Bitmap.hasGainmap()` through a bounded decode path, so Ultra HDR gain-map sources are no longer treated as ordinary SDR media.
- **Export confidence now understands source HDR.** The export sheet summarizes imported HDR / Ultra HDR source media before render, distinguishing SDR delivery choices from missing source metadata.
- **Autosave preserves import HDR metadata.** Clip source color metadata now round-trips through project autosave so recovery and reopened projects keep their HDR confidence context.
- **Verification** — `git diff --check`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, release APK metadata/signature checks, and an adb uninstall/install/launch smoke pass on `R5CY34G070L` all passed.

## v3.74.2 — 2026-05-14 — HDR export capability tiering

- **HDR profile probing now lives in `EncoderCapabilityProbe`.** NovaCut classifies HEVC, AV1, VP9, and AV1-based Dolby Vision Profile 10 encoder profiles from `MediaCodecList`, including advertised resolution / bitrate envelopes and encoder names.
- **Export confidence now distinguishes dynamic HDR paths.** The export sheet reports HDR10+ and Dolby Vision Profile 10 support separately, avoids the generic static-HDR warning when a dynamic HDR path is available, and keeps H.264 locked to SDR.
- **Device-tier hints are visible in export.** The output details panel now shows a Standard / Advanced / Premium encode tier based on actual hardware HEVC / AV1 / VP9 encoders instead of model-name guesses, with hardware-codec and HDR-profile chips.
- **Media3 is patched to 1.10.1.** This keeps the 1.10 export foundation current and picks up the AV1-based Dolby Vision handling fix from the May 12, 2026 Media3 release.
- **Verification** — `git diff --check`, `assembleDebug`, `testDebugUnitTest`, `assembleRelease`, release APK metadata/signature checks, and an adb uninstall/install/launch smoke pass on `R5CY34G070L` all passed.

## v3.74.1 — 2026-05-14 — Multi-track export composition

- **Multi-track visual export is wired through Media3 1.10 Composition.** `VideoEngine` now builds one `EditedMediaItemSequence` per visible video or overlay track instead of exporting only the first visual track.
- **Track audio semantics are preserved.** Embedded clip audio now follows the source track's mute, solo, visibility, and volume settings, while dedicated audio tracks remain separate audio-only sequences in the same composition.
- **Media3 sequence builders now declare explicit track types.** `VideoEngine` uses video/audio or video-only visual sequences, audio tracks use audio-only sequences, and `ProxyEngine` emits video-only proxy transcodes.
- **Docs and roadmap are synced.** B.1 is closed, B.2 remains scoped to the real dual-input blend shader path, and stale README limitations for already-shipped speed-curve duration, text stroke export, and archive import work were removed.

## v3.74.0 — 2026-05-14 — Media3 1.10 foundation

- **Media3 is upgraded to 1.10.0.** The dependency catalog now pulls the current stable `androidx.media3` release across ExoPlayer, Transformer, Effect, Common, UI, and Muxer.
- **Export builder usage is ready for the removed sequence constructor.** Existing `VideoEngine` and `ProxyEngine` export paths already use `EditedMediaItemSequence.Builder`, so the 1.10 upgrade does not require a production call-site migration.
- **Version and engine labels are synced.** Build metadata, the app version string, Settings engine value, README stack table, and roadmap state now reflect the Media3 1.10 foundation release.
- **Verification** — `git diff --check`, `assembleDebug`, `testDebugUnitTest`, `assembleRelease`, release APK metadata/signature checks, and an adb install/launch smoke pass on `R5CY34G070L` all passed.

## v3.73.2 — 2026-05-13 — Project home recovery polish

- **Project home counts now stay trustworthy while filtering.** The hero metric uses the total project library size instead of the currently filtered result count, so an empty filter no longer makes the library look empty.
- **Empty states now explain the real situation.** First-run, no search results, empty filter views, and search-plus-filter misses get distinct copy, icon treatment, and recovery behavior.
- **Filter recovery is now one clear action.** Empty filtered views offer “Show All Projects,” which clears search and filter together, plus a secondary new-project path for users who want to keep moving.
- **Filtered result headers are more specific.** Active filter views show the filter name, visible/total project counts, and current sort context instead of the generic recent-projects heading.
- **Mobile first-run layout no longer buries the empty state.** The project home hides irrelevant search, sort, filter, and duplicate hero actions until a library exists, and filter chips now stay in a compact single-line rail.
- **Verification** — `git diff --check`, `assembleDebug`, `testDebugUnitTest`, `installDebug`, and an adb launch/UI-dump smoke pass verified the first-run project home on the connected device.

## v3.73.1 — 2026-05-13 — Settings AI model removal polish

- **Settings AI model removal now matches the AI Tools trust flow.** Removing Whisper or segmentation from Settings opens a clear confirmation dialog with the model impact before deleting local files.
- **AI model storage copy is now cleaner and localized.** Installed/download-size labels use string resources, and removal confirmations state the exact local storage that will be freed.
- **Settings feedback is calmer for assistive tech.** AI model success/error banners now announce as polite live-region updates instead of relying only on visual change.
- **Segmentation downloads work again.** Replaced the dead MediaPipe `float32` selfie-segmenter URL with the current `float16` model URL and pinned the downloaded file with a SHA-256 check.
- **Verification** — `git diff --check`, `assembleDebug`, `testDebugUnitTest`, `installDebug`, and an adb settings smoke pass verified segmentation download, confirmation, and removal on the connected device.

## v3.73.0 — 2026-05-13 — Premium polish pass (trust surfaces, model storage, visual system)

- **AI model settings now feel like a managed product surface instead of a static list.** Added a Wi-Fi-only model download preference, live install/download/error state badges, local storage disclosure, and per-model download/remove controls for Whisper and segmentation models.
- **Settings overview actions now keep users in context.** The AI Models summary card jumps to the model-management section instead of exiting Settings, and model rows now read with cleaner download-size microcopy.
- **Large model downloads now respect user trust boundaries.** Whisper and segmentation downloads read the Wi-Fi-only setting, block metered-network starts, and recover to the correct installed/not-installed state after a blocked or failed request.
- **Backup imports now show persistent recovery notes.** Successful imports with missing media, warnings, or regenerated project IDs open a structured report dialog with counts, affected files, and suggested next actions; failed imports now state that the current project was left unchanged.
- **Timeline handoff exports now surface professional validation reports.** OTIO/FCPXML validation errors open a blocking report with severity, path, and suggested fix details; lossy successful exports show post-export notes instead of relying on a transient toast.
- **Visual language tightened across user-facing surfaces.** Replaced oversized pill/oval backdrops with consistent small-radius status, metric, and chip shapes while preserving true circular controls, indicators, and color swatches.
- **Test coverage / verification** — `assembleDebug`, `testDebugUnitTest`, and `git diff --check` pass on the Android Studio JBR/SDK environment.

## v3.72.0 — 2026-05-13 — Hardening pass (Cut Assistant correctness, resource leaks, persistence guards)

- **Cut Assistant slice-deletion bug fixed.** `applyAcceptedCuts()` was looking up `op.clipId` AFTER the first `splitClipAt()`, which keeps the LEFT half on the original id. The "middle" lookup therefore matched the wrong slice and the engine deleted content BEFORE the silence range instead of the silence itself. The new applier diffs the per-track clip-id set across both splits, identifies the freshly-minted right half, deletes the correct slice, and ripple-shifts every subsequent clip back by the deleted span so the timeline has no orphan gaps after a batch of cuts.
- **Cut Assistant proposeCutsForReview no longer trusts pre-IO state.** Tracks are re-read from `_state.value` after `withContext(Dispatchers.IO)` returns; clips that were deleted, moved, or replaced during the waveform scan are filtered out before the engine runs, and `CancellationException` is propagated so cancelling the operation actually tears down the scope.
- **Cut Assistant review panel now drops on panel dismissal.** `dismissedPanelState()` resets `cutAssistantReview = null` alongside the other auxiliary state — opening Effects / Media Picker / any other panel no longer leaks the previous ReviewSet (which can hold per-clip word transcripts).
- **TrackedObjectKeyframe rejects NaN, out-of-range, and negative-time inputs.** Adds `require()` guards for `clipTimeMs >= 0`, finite `centerX`/`centerY`, and `[0, 1]` bounds on the center — corrupt JSON or pre-v3.71 saves can no longer slip NaN coordinates into the mosaic/blur shader pipeline where they would render as giant off-screen rectangles.
- **ProjectArchive no longer swallows CancellationException.** Both `exportArchive` and `importArchiveWithReport` re-throw `CancellationException` after cleanup, so the UI's coroutine scope sees a real cancellation instead of a misleading "import failed" `ImportResult`.
- **VideoEngine.extractThumbnail closes the bitmap leak on scale failure.** `Bitmap.createScaledBitmap` can throw OOM (an `Error`, not an `Exception`) or `IllegalArgumentException` for zero-area sizes; the source `frame` is now released on every exit path, and failure paths log so flaky thumbnail extraction is visible in logcat.
- **ModelDownloadManager deletes corrupt cached models on SHA-256 mismatch.** `isValidModelFile()` now removes the bad file before returning false, so subsequent `downloadFiles()` calls don't waste a SHA-256 pass over the same bad bytes on every launch.
- **keystore.properties added to `.gitignore`.** The file containing release-signing credentials was untracked but unprotected — one `git add -A` away from public exposure.
- **Test coverage** — Added `TrackedObjectKeyframeTest` (13 cases covering NaN/range/boundary) and `CutAssistantEngineTest` (9 cases covering projection, trim clipping, merge tolerance, accept/reject round-trip, and apply-order ordering).

## Previous Unreleased — Export confidence and Cut Assistant polish

- Added Color / HDR confidence chips to the export sheet, including SDR delivery status, HDR preservation intent, HDR10+ dynamic metadata support, and render-time source caveats.
- Added export mismatch warnings for H.264 HDR requests, missing device HDR encode support, and advertised HDR resolution/bitrate limits.
- Added a Preserve HDR Metadata control to Delivery Options so HDR intent is visible in the main export workflow instead of only the feature hub.
- Added a user-facing Cut Assistant workflow in the AI Hub and clip AI toolbar.
- Added a review sheet for silence/filler-word proposals with selected-by-default candidates, per-row toggles, accept/reject all, reclaimed-duration summary, and an empty state.
- Applying reviewed proposals still uses the existing `applyAcceptedCuts()` path, so the batch lands as one undoable timeline edit.
- Added a Tracked Mosaic effect that binds persisted TrackedObject keyframes to a Media3 shader mask, with preview/export wiring, target-ID autosave, interpolation tests, and an Effects panel action for tracked masks on the selected clip.
- Added template compatibility metadata to saved/exported templates, including schema version, minimum app version, required feature list, and media/text slot counts.
- Added compatibility validation to template imports so future-schema, newer-app, or unknown required-feature templates are rejected before they are saved locally, with clearer user-facing failure copy.
- Saved template cards now surface slot counts so reusable setups feel more inspectable before opening.
- Surfaced the existing stream-copy fast trim path in the export sheet with a user-visible "Fast Trim When Possible" control, and refreshed stale copy around the MediaMuxer fallback behavior.
- Clarified the roadmap split between shipped whole-timeline stream-copy and the still-open mixed segment smart-render bypass.
- Corrected speed-ramp clip duration by integrating eased `speedCurve(t)` directly, keeping timeline length, thumbnail scrubbing, and export timing aligned on variable-speed clips.

## v3.71.0 — 2026-04-25 — Cut Assistant + TrackedObject scaffolding

Second slice of the ROADMAP "Highest-leverage next tickets" — the engine and
state-layer prerequisites for the Creator-speed and Object-aware releases.

### CutAssistantEngine (C.2 / R4.5)
New [CutAssistantEngine.kt](app/src/main/java/com/novacut/editor/engine/CutAssistantEngine.kt)
is a pure planner that combines `SilenceDetectionEngine.detectSilences()` and
`detectFillerWords()` into a single sorted, de-duplicated `ReviewSet`:

- Walks every video/audio clip on the timeline and projects clip-source ms
  into timeline ms, accounting for trim handles + speed + speedCurve via
  `Clip.durationMs` scaling. Proposals straddling a trim handle contribute
  only the visible portion.
- Merges abutting same-clip proposals within a 250 ms tolerance so a
  "um... uh..." run shows up as one review row instead of three.
- Drops contributions shorter than 80 ms after trim clipping (visual jolt
  outweighs time saved).
- `planAcceptedOperations()` emits `CutOperation.RippleDelete` entries
  ordered latest-first so the applier can split + delete each one without
  invalidating the indices of the remaining operations.

### EditorViewModel orchestration
`proposeCutsForReview()` extracts denser per-clip waveforms (~20 samples/sec,
bounded 200..10 000) via `audioEngine.extractWaveform()`, looks up cached
transcript words via `perClipWordsFor()`, runs the engine, stashes the
`ReviewSet` in `state.cutAssistantReview`. Per-proposal `toggleCutProposal`,
`acceptAllCutProposals`, `rejectAllCutProposals` are pure state-updaters.
`applyAcceptedCuts()` wraps the whole batch in a single
`saveUndoState("Apply Cut Assistant")`, processes ops latest-first, splits at
start + end of each accepted range and deletes the middle slice via existing
primitives. `dismissCutAssistantReview()` closes the review without
applying.

### TrackedObject model (R4.3 — object-aware editing scaffold)
[TrackedObject.kt](app/src/main/java/com/novacut/editor/model/TrackedObject.kt)
defines the engine-agnostic data classes that future tracked operations
(blur, mosaic, sticker attach, color grade, audio focus) will bind to:

- `TrackedObject` (id, label, sourceClipId, source, category, isEnabled,
  keyframes).
- `TrackedObjectKeyframe` (clipTimeMs, normalised centerX/centerY/width/height
  in [0, 1] — survives a 1080p → 4K source swap without drift; confidence;
  optional maskPolygon for SAM-class trackers).
- `TrackedObjectSource` enum (MANUAL / MEDIAPIPE / MOBILE_SAM / SAM2 /
  YOLO_TRACK) and `TrackedObjectCategory` enum (PERSON, FACE, VEHICLE,
  LICENSE_PLATE, ANIMAL, TEXT, PRODUCT).
- Persisted via new `AutoSaveState.trackedObjects` field; deserialiser
  coerces coords into the valid range BEFORE constructing the keyframe so a
  corrupt save can't trip `require()` and silently drop the rest of the
  object's track. Survives autosave AND project-archive import.

ViewModel: `upsertTrackedObject` / `removeTrackedObject` /
`setTrackedObjectEnabled` (all undoable, all flush through `saveProject()`).

### Notes
- Review *panel UI* and tracked-blur shader binding are intentionally next-pass
  work — engine + state layers are ready, future PRs only need a
  Compose surface that consumes `state.cutAssistantReview` /
  `state.trackedObjects` and emits the existing ViewModel intents.
- Existing v3.69 code paths untouched — both new state fields default to
  empty/null so nothing changes for projects that haven't run the new
  workflows.

## v3.70.0 — 2026-04-25 — Foundation pass (highest-leverage roadmap items)

First slice of the ROADMAP "Highest-leverage next tickets" batch — the
prerequisites that unblock the rest of Tier A/B/C work.

### TimelineExchangeValidator (R4.1, "Implement TimelineExchangeValidator and run it before every export/import")
New [TimelineExchangeValidator.kt](app/src/main/java/com/novacut/editor/engine/TimelineExchangeValidator.kt)
produces a categorised pre-flight report (ERROR / WARNING / INFO + path +
suggested fix) for every supported interchange format. Wired ahead of
`exportToOtio()` and `exportToFcpxml()` in `EditorViewModel` so blocking
errors abort with a useful toast and lossy warnings ride along on the
success toast (`OTIO exported: foo.otio (3 lossy)` instead of silent data
loss). Covers: empty trim ranges, missing source URIs, EDL multi-track
truncation, adjustment-track drop, blend-mode loss, masks, color grade,
reverse playback, speed ramps, compound flatten, EDL transition downgrade.

### ProjectArchive.importArchive() — full diagnostic pass (B.7)
[ProjectArchive.kt](app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt)
gains `importArchiveWithReport()` returning an `ImportResult(state, report,
errorMessage)`. The report carries schema version, schema-too-new gate,
project-ID collision detection (with `IdCollisionPolicy.REGENERATE` /
`KEEP`), per-archive media-resolution counts, and unresolved-media URI list.
Legacy `importArchive()` stays as a thin wrapper for unchanged callers.
`EditorViewModel.importProjectBackup()` now reads the existing project IDs
from `ProjectDao`, calls the rich variant, and surfaces missing-media and
collision warnings in the toast. Schema-newer-than-supported archives now
abort cleanly with cleanup, instead of best-effort partial loads.

### ModelDownloadManager — checksum, Wi-Fi-only, remove API
[ModelDownloadManager.kt](app/src/main/java/com/novacut/editor/engine/ModelDownloadManager.kt)
gains:
- `ModelFile.sha256` — optional lowercase-hex SHA-256, verified during
  download (and on re-use of an existing file). Catches partial downloads
  from a prior crash that pass the byte-length check today.
- `wifiOnly` parameter on `downloadFiles()` plus `isMeteredNetwork()` helper
  using `ConnectivityManager.NET_CAPABILITY_NOT_METERED`. Throws
  `MeteredNetworkException` when the active network is metered and the
  caller required Wi-Fi only.
- `removeModel(File)` and `removeModels(List<File>)` so the upcoming
  per-feature "Remove model" UI can reclaim storage. Cleans matching
  `<name>.*.tmp` siblings left behind by a prior interrupted download.
- `installedBytes(List<File>)` for "uses N MB" disclosures next to a Remove
  button.

`AndroidManifest.xml` now declares `ACCESS_NETWORK_STATE` for the metered
check.

### Hardening notes
- Existing call sites of `ModelDownloadManager` (Whisper, Inpainting,
  Segmentation) are source-compatible — `sha256` defaults to null and
  `wifiOnly` defaults to false; they continue to work unchanged until the
  asset metadata grows checksums.
- Existing call site of `ProjectArchive.importArchive()` (single internal
  caller) now uses the rich result path; the public legacy signature is
  preserved for any future caller that doesn't need diagnostics.

## v3.69.0 — 15-Feature Wave + Hardening + Wide-Net Follow-Ups

The third pass (this section) closed three of the "remaining gaps" with real
pipelines rather than more scaffolding.

### B.6 — Text overlay stroke export
[StrokedTextBitmapOverlay.kt](app/src/main/java/com/novacut/editor/engine/StrokedTextBitmapOverlay.kt)
extends Media3's `BitmapOverlay` and renders the text on a Canvas twice per
keyframe: once with `PAINT.STYLE_STROKE` in the stroke color and again with
`PAINT.STYLE_FILL` in the fill color. SpannableString could never do this —
the draw model only carries one color per pixel. `VideoEngine` branches on
`overlay.strokeWidth > 0f`: zero-stroke overlays still take the cheap
`ExportTextOverlay` path, so the common case pays no cost. Bitmaps are cached
per text change and released when Media3 releases the overlay.

### B.5 — Multi-clip same-source stream-copy
[StreamCopyMuxer.concat](app/src/main/java/com/novacut/editor/engine/StreamCopyMuxer.kt)
now muxes a list of non-overlapping source windows into a single output file
— the multi-clip-same-source case where a creator has sliced one recording
into keepers. Each track walks the ranges independently with its own output
cursor; sample packets are never decoded.
[StreamCopyExportEngine.analyze](app/src/main/java/com/novacut/editor/engine/StreamCopyExportEngine.kt)
now accepts any number of clips on a single visible video track as long as
they all share the same source URI and every clip passes the full
`firstDisqualifier` list. Integration point in `ExportDelegate.trySteamCopy`
is unchanged — it calls `execute()` which dispatches to `trim` (one range) or
`concat` (multiple) based on the resulting `Eligibility.ranges`.

### Desktop sidebar (DESKTOP layout mode)
[DesktopSidebar.kt](app/src/main/java/com/novacut/editor/ui/editor/DesktopSidebar.kt)
renders beside the editor column when `LocalLayoutMode == DESKTOP`. Surfaces
the project meta, quick actions (Add media / Record / Export / v3.69 hub)
and a compact media bin grouped by track type. Absent on PHONE / ONE_HANDED
so the phone layout is untouched. Width: 260 dp.

### Already shipped, confirmed
- C.12 Keyframe graph editor — already lives at
  [KeyframeCurveEditor.kt](app/src/main/java/com/novacut/editor/ui/editor/KeyframeCurveEditor.kt)
  and has its own `PanelId.KEYFRAME_EDITOR` entry point.
- B.7 ProjectArchive.importArchive — already complete at
  [ProjectArchive.kt](app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt).

### Still on the list (genuinely blocked)
- B.1 multi-track video compositing via Media3 Compositor — risky regression
  territory on the stable export path; parked for a dedicated release.
- Chromaprint NDK binding for real AcoustID lookup — needs an external
  native library.
- YAMNet SDH classifier — needs the model bundled or downloaded.
- RIFE / Real-ESRGAN / OpenCV stab / FFmpegX — Tier A items, all waiting on
  third-party libraries that are not yet in the project.

## v3.69.0 — 15-Feature Wave + Production Hardening Pass

The 15-feature wave shipped in two passes. The second pass (this section)
turned every dead UI toggle into a live consumer, wired the stream-copy path
through the Android MediaMuxer (no FFmpeg dependency required), added real
HDR preservation via `Composition.HDR_MODE_KEEP_HDR`, fixed a ripple-delete
bug in text-based editing, and persisted transcripts so text-based editing
survives app restart.

### Wide-net additions

- **Color-blind preview GL pass** — `ColorBlindGlEffect` builds a fragment
  shader from the `ColorBlindPreviewEngine` matrix and appends it to every
  clip's preview effect chain. Toggling the mode in the v3.69 panel now
  produces a visible preview change within one frame. Export never picks up
  the effect.
- **Stream-copy via `MediaMuxer`** — new `StreamCopyMuxer` uses
  `MediaExtractor` + `MediaMuxer` to remux the source packets directly into
  the destination, no transcode. `ExportDelegate.trySteamCopy()` guards with
  the full eligibility checklist; any failure transparently falls back to the
  Transformer path so users can't get stuck. 50× faster on eligible trims.
- **HDR preservation on export** — `VideoEngine.buildComposition` respects
  `ExportConfig.hdr10PlusMetadata` by setting `Composition.HDR_MODE_KEEP_HDR`
  when the codec can carry HDR (HEVC/AV1/VP9). H.264 forces SDR since it has
  no HDR profile. The v3.69 panel switch is now gated on codec choice and
  shows "Switch to HEVC/AV1/VP9" when locked.
- **Keyframe remap on text-based split** — `V369Delegate.buildSegment`
  filters the source clip's keyframes to the segment's source-time window and
  remaps each kept `timeOffsetMs` via `Clip.sourceTimeToTimelineOffsetMs`.
  Speed-curves are restricted via `SpeedCurve.restrictTo` so preview and
  export time-stretching stay consistent with each segment's trim window.
- **Transcript persistence** — `AutoSaveState.transcript` is now part of
  the auto-save JSON; `V369Delegate.setTranscript` calls `saveProject`;
  `EditorViewModel` restores it into `v369.transcript` on recovery. Users
  don't lose their transcript on app restart.
- **Layout-mode detector** — new `LayoutMode` enum (`PHONE` /
  `ONE_HANDED` / `DESKTOP`) resolved by `resolveLayoutMode()` from the
  device `UiModeManager` + `Configuration` + user override. Exposed as
  `LocalLayoutMode` Composition Local. `EditorTopBar` consumes it to force
  compact layout in one-handed mode. `SettingsRepository` stores the
  `oneHandedMode` preference and `desktopModeOverride` enum.
- **AcoustID key setting** — `SettingsRepository.acoustIdApiKey` persists
  the optional API key. When the Chromaprint NDK bridge lands the key flows
  straight through `ContentIdEngine.analyze`.

### Audit pass (second pass fixes shipped before the wide-net)

- Ripple-delete on text-based edit splits; preserve `clip.transition` on the
  first surviving segment.
- Bounded-heap streaming top-N in `AiThumbnailEngine.score` — bitmaps are
  recycled the moment they fall out of the top-N.
- `StreamCopyExportEngine.firstDisqualifier` now covers every clip field
  that affects the decoded output, including audio fades, per-clip volume,
  audio effects, captions, compound clips, and per-track mix parameters.
- `ContentIdEngine.queryAcoustId` no longer makes pointless HTTP calls;
  hash-only result path is honest.
- `V369FeaturesPanel` switched to header-only expand toggle so child
  controls don't double-fire, every chip row is horizontally scrollable,
  `rememberSaveable` keyed to `project.id` for the publish title field.
- `StylusMidiEngine` — `@Suppress("DEPRECATION")` on the `MidiManager.devices`
  legacy API with rationale, volatile fields, and safe re-connect that
  closes the prior device handle.
- `TextBasedEditEngine.fillerWordIndices` now detects bi-gram fillers
  ("you know", "i mean") alongside uni-grams.
- `AutoChapterEngine.detect` clamps idx + dedupes repeated titles.
- `AudioEngine.decodeToPCM` lifted from private to public so
  `ContentIdEngine` and other future fingerprint consumers can reuse it.

### Original v3.69 wave engines

TextBasedEditEngine · AutoChapterEngine · TalkingHeadFramingEngine ·
KaraokeCaptionEngine · StreamCopyExportEngine · ContentIdEngine ·
DirectPublishEngine · FlashSafetyEngine · ColorBlindPreviewEngine ·
AiThumbnailEngine · AudioDescriptionEngine · StylusMidiEngine +
`V369Delegate` + `V369FeaturesPanel` + shared `Transcript` /
`WordTimestamp` model.

### ExportConfig additions

`hdr10PlusMetadata` (HDR preservation gate, live) + `allowStreamCopy`
(stream-copy fast-path gate, default on, live).

## v3.69.0 — 15-Feature Wave (Competitor-Inspired)

Twelve new engines and one composite feature hub (`PanelId.V369_FEATURES`, accessed via the overflow menu → "v3.69 Features"). Follows the Tier-A stub convention: real implementation where the Android surface allows, structured hook for the rest. No new third-party dependencies.

### New engines

- **TextBasedEditEngine** — Descript/CapCut Script-Editor-style edit flow. Word-level `WordTimestamp` selections map to source-time cut ranges on the selected clip; contiguous selections coalesce (120 ms merge window). `fillerWordIndices()` covers the mainstream English filler set.
- **AutoChapterEngine** — TextTiling-lite over Whisper words: 24-word sliding windows, cosine similarity of bag-of-words between adjacent windows, local minima mark chapter boundaries. `formatYouTubeClipboard()` renders an `HH:MM:SS Title` block ready for a YouTube description.
- **TalkingHeadFramingEngine** — Samsung Auto-Framing / Apple Center Stage equivalent. Skin-tone centroid as a face-proxy per sampled frame, one-euro filter smoothing on the trajectory, output as `POSITION_X/POSITION_Y` keyframes so the existing keyframe-aware export path picks them up.
- **KaraokeCaptionEngine** — Submagic/Captions.ai-style word-pop captions, 8 preset styles (MrBeast, Subway, Hormozi, TikTok White, Pop Scale, Typewriter, Neon, Minimal). Emits standard `TextOverlay` instances with animation + stroke that the current export pipeline already renders.
- **StreamCopyExportEngine** — LosslessCut eligibility detector. When the timeline is a single unmodified clip with only head/tail cuts, signals the export pipeline to skip transcode entirely. Stream-copy mux itself is invoked through `FFmpegEngine.streamCopyTrim()` (added as a stub; lights up once A.9 ships).
- **ContentIdEngine** — Copyright fingerprint / AcoustID pre-check. Energy-envelope hash per 50 ms window over 16-bit PCM; hash-only result when no API key is configured, AcoustID lookup when one is. Fingerprint-similarity helper for local dedup.
- **DirectPublishEngine** — Facade for YouTube / TikTok / Instagram Reels / Threads / X / LinkedIn. Resolves to a platform-branded share intent when the target app is installed; documents the OAuth-upload hook for partner-program integrations.
- **FlashSafetyEngine** — WCAG/Harding-lite photosensitive-epilepsy scan. Samples luminance + red-channel at 10 Hz, flags 1 s windows with >3 opposite-direction transitions above the Δ threshold. Separate general-flash vs. red-flash categories per W3C guidance.
- **ColorBlindPreviewEngine** — Brettel/Viénot CVD simulation (Deuteranopia / Protanopia / Tritanopia / Achromatopsia). Ships both a 3×3 transform matrix and an inlined GLES 3.0 fragment shader so the existing `ShaderEffect` framework can apply it as a preview-only pass.
- **AiThumbnailEngine** — YouTube-cover-style frame ranker. Score = 0.35·Laplacian-variance sharpness + 0.25·rule-of-thirds alignment of the salient-edge centroid + 0.40·skin-tone coverage. Top-N candidates returned with bitmaps; `saveThumbnail()` writes a JPEG.
- **AudioDescriptionEngine** — SDH tags (`[music]`, `[door slams]`, …) + audio-description-track generator. Silence heuristic classifier today; YAMNet hook documented for the bundled-model path.
- **StylusMidiEngine** — S Pen pressure (`MotionEvent.TOOL_TYPE_STYLUS`) for keyframe-curve authoring; BT MIDI CC mapping for jog/shuttle/transport (ShuttleXpress-compatible CC 1/2/64–68).

### ExportConfig additions

- `hdr10PlusMetadata: Boolean` — attach per-scene HDR10+ dynamic metadata on HEVC/AV1 exports when the source is HDR and the device encoder supports it. Silently falls back to HDR10 static metadata on unsupported paths.
- `allowStreamCopy: Boolean = true` — gate for the LosslessCut-style fast-trim path.

### UI

- **V369FeaturesPanel** — single scrollable hub, 15 expandable feature cards, dispatches into `V369Delegate`. Accessed from the editor top-bar overflow menu → "v3.69 Features" (`Icons.Default.AutoAwesome`, Mauve tint).
- **V369Delegate** — follows the existing delegate pattern: owns coroutine jobs, writes to the shared `EditorState` via the CAS-loop `update` extension, pulls through `saveUndoState`/`saveProject`/`rebuildPlayerTimeline`.
- **EditorState.v369** nested `V369State` block: transcript, selected-word indices, chapter candidates, flash warnings, thumbnail candidates, color-blind mode, karaoke style, stream-copy eligibility, content-ID result, four in-flight flags.
- **PanelId extension** — added `V369_FEATURES` hub plus drill-down IDs for `TEXT_BASED_EDIT`, `AUTO_CHAPTER`, `TALKING_HEAD`, `KARAOKE_CAPTIONS`, `CONTENT_ID`, `DIRECT_PUBLISH`, `FLASH_SAFETY`, `COLOR_BLIND_PREVIEW`, `AI_THUMBNAIL`, `AUDIO_DESCRIPTION`.

### Model additions

- `model/Transcript.kt` — shared `WordTimestamp` and `Transcript` primitives so the ASR, text-based edit, auto-chapter, karaoke, and audio-description pipelines all speak the same shape instead of each depending on nested types under `WhisperEngine` / `SherpaAsrEngine`.

## v3.68.0 — Performance & Responsiveness Pass

Broad optimization sweep across recomposition hotspots, per-tick I/O, and hot-path allocation. No new features. No DB schema changes. No new dependencies.

### Compose recomposition fixes

- **Per-clip `Brush.verticalGradient` overlay hoisted to `remember`** in `Timeline.kt`. The gradient applied on top of every clip body was allocated fresh per clip per frame. A 10-clip project recomposing at 30 Hz during playback was churning 300 `Brush` + `List` allocations/sec for a gradient whose contents never change.
- **Render-phase snap-target list memoized** via `remember(track.clips, selectedClipId, playheadMs, beatMarkers, markers, snapToBeat, snapToMarker)`. The `flatMap { filter { } flatMap { } }.distinct().plus(...).let { ... }.let { ... }` chain was running on every playhead tick, allocating 5–7 `List` instances per tick.
- **`volumeKeyframesSorted(clip)`** memoized per-clip on `clip.keyframes`. Sort is O(n log n); previously ran on every audio clip every recomposition.
- **`previewTrackClips` / `previewClipAtPlayhead` derives decoupled from `playheadMs`** in `EditorScreen.kt`. The sort that built `previewTrackClips` was re-keyed on `playheadMs` via the downstream derive chain, so a static clip list was being re-sorted 30 times/sec during playback. The sort now only re-runs when the track structure actually changes; the per-tick scan stays cheap because the sorted list is cached.

### Per-tick I/O reduction

Eight slider/gesture paths no longer call `saveProject()` on every `onValueChange` tick. All have matching `begin*/end*` hooks so the full-project JSON serialize + disk write runs once per gesture instead of 60 times per second:

- `EffectsDelegate.updateEffect` → `endEffectAdjust()`
- `AudioMixerDelegate.setTrackVolume` → `endVolumeAdjust()`
- `AudioMixerDelegate.setTrackPan` → `endPanAdjust()`
- `setClipVolume` → `endVolumeChange()`
- `setClipTransform` → `endTransformChange()`
- `setClipOpacity` → `endOpacityChange()` (new hook)
- `setClipFadeIn` / `setClipFadeOut` → `endFadeAdjust()`
- `beginSlideEdit`/`beginSlipEdit` now call `setScrubbingMode(true)` too (previously only `beginTrim` did)

The preview-pan pinch gesture in `PreviewPanel.kt` was rewritten from `detectTransformGestures` to `awaitEachGesture` so it actually has an end hook — the old implementation had no way to signal "gesture finished", which is why `setClipTransform` had been calling `saveProject()` on every frame in the first place. `TransformOverlay.onDragEnd` and `onDragCancel` now call `onTransformEnded()`. AI tool callers that relied on `setClipTransform` auto-saving (smart crop, smart reframe) explicitly `saveProject()` after their one-shot invocation.

### Playback loop

- **Playhead sync's per-5-frame state broadcast now gated on drift ≥200ms** in `EditorViewModel.kt`. During playback, every 5th frame unconditionally emitted a new `EditorState` with the updated `playheadMs` — a 40-field `state.copy()` that invalidated every Compose subscriber of the state flow ~6 times/sec. The dedicated `_playheadMs` flow already served live-playhead consumers, so the full-state broadcast is now only needed to keep `state.playheadMs` fresh enough for user-triggered reads like `splitClipAtPlayhead()` and autosave — a 200ms drift threshold cuts broadcasts from ~6/sec to ~5/sec while keeping staleness bounded.

### Usability

- **Clip-label picker close button grown from 24dp to 44dp** to meet the Material 3 minimum touch-target guideline. The icon inside went 16dp → 20dp. The previous tap target was below accessibility minimums and frequently misfired on small phones.

## v3.67.0 — Snackbar Height, Drag Responsiveness, Suggestion Cleanup

Follow-up fixes based on v3.66 user testing.

### Snackbar covering the screen (also broke video playback)

- **`PremiumSnackbar` accent stripe used `fillMaxHeight()` inside a height-unconstrained Row.** Compose resolves `fillMaxHeight` against the nearest height-constrained ancestor — which in this case was the screen-root `Box`. So a "Clips split" toast was not just visually oversized, its opaque-ish Surface absorbed touch input across the whole area, which is why the play button appeared unresponsive immediately after a split. Two-line fix: added `.wrapContentHeight()` on the Surface and `.height(IntrinsicSize.Min)` on the Row, so `fillMaxHeight` now resolves against the Row's wrap-content height (≈52dp) instead of the screen.

### Timeline drag responsiveness

- **`trimClip`, `slideClip`, and `slipClip` no longer call `rebuildPlayerTimeline()` on every tick.** All three fire at touch-event rate (60–120 Hz) during a drag; tearing down and rebuilding ExoPlayer's `MediaItem` + `ClippingConfiguration` set on every tick was the primary cause of the "clunky" feel. The rebuild is deferred to `endTrim` / `endSlideEdit` / `endSlipEdit`, which run exactly once per gesture. `beginSlideEdit` and `beginSlipEdit` now also call `videoEngine.setScrubbingMode(true)` (previously only `beginTrim` did), so ExoPlayer skips intermediate seek/decode work across all three edit modes.

### Suggestion banner cleanup

- **Removed the unsolicited "This clip could use color correction" suggestion banner.** Fired every time a long visual clip was selected that happened to have no effects — noise, not signal. Users can still trigger auto-color from the AI tools panel. The other two suggestions (add transitions, denoise on low-variance audio) are preserved because they gate on more specific conditions.

## v3.66.0 — Timeline & Editing Overhaul

Focused rework of the timeline gesture model and viewport framing. Addresses three long-standing usability issues: trim handles not responding to drags, cut/split being hard to find, and long clips appearing to show only a tiny editable window. No DB schema changes, no new dependencies.

### Timeline gesture unification

- **Unified clip-body pointer input replaces three competing gesture detectors.** Previously each clip had a parent `detectDragGestures` for slide/slip and two nested `detectHorizontalDragGestures` blocks for the left and right trim handles. All three waited for drag-slop on the same down event, and the parent body detector routinely consumed edge drags before the handle detectors could react — which is why pulling on the edge of a clip to trim often did nothing. A single `detectDragGestures` on the clip body now measures the touch X position at drag start and routes the gesture to one of four zones: `TRIM_LEFT` (within the 28dp edge zone), `TRIM_RIGHT` (within the 28dp trailing zone), `SLIP` (middle zone, trim-tool active), or `SLIDE` (middle zone, arrange-tool active). The nested handle pointer inputs are removed. Drag events are explicitly consumed so the ancestor pinch-zoom detector doesn't double-handle them. Trim and slide/slip now work reliably regardless of the active tool mode.
- **Selected clip shows thicker edge handles with grip lines.** When a clip is selected, the trim-handle visual width grows from 14dp to 18dp and three 1.2dp vertical grip lines appear — an unambiguous affordance cue that the edge is draggable. Matches the CapCut / KineMaster edit UX where the selected clip's edges are visibly distinct from inactive clips.

### Full-duration viewport framing

- **Minimum zoom lowered from 0.1 → 0.01.** The old 0.1 floor meant the fit-to-window calculation couldn't actually fit videos longer than ~60 seconds on a phone screen — the computed fit-zoom was clamped *above* the ratio that would have worked. The new 0.01 floor lets a 10-minute project fit the editable area cleanly. The max 10.0 remains unchanged.
- **Auto-fit on first layout.** A new `fitTimelineToWindow()` method in `EditorViewModel` computes the fit-zoom from the current viewport width and project duration, and resets scroll to zero. It fires automatically: (1) the first time `setTimelineWidth` transitions from 0 → non-zero with content already present, (2) after autosave/Room restore populates tracks, and (3) after the first clip is added to an empty project. A `pendingInitialFit` flag ensures this runs once per session — the user's subsequent zoom preferences are not overridden. Matches the CapCut / VN behaviour where importing a clip immediately frames the whole asset.
- **Viewport overview strip below the tracks.** A new `TimelineOverviewBar` composable renders a full-project-duration strip with one rectangle per clip (coloured per track type), a highlighted "viewport" window showing what's currently visible, and a playhead tick. Tap or drag on the strip to scroll — centering the viewport on the tapped position. Primary purpose is *discoverability*: users can see at a glance that there is more content off-screen and can jump directly to any timestamp without having to pinch-zoom out first. Only shown when `totalDurationMs > 0`.

### Cut / delete accessibility

- **Prominent "Cut at playhead" and "Delete selected" buttons added to the timeline toolbar row.** Previously split was only reachable through a two-level tool sub-menu or a radial long-press menu; both paths were difficult to find. The new split button sits between the zoom controls and the marker-add button, uses `Icons.AutoMirrored.Filled.CallSplit`, is styled with the Peach accent + highlight border so it reads as a primary action, and calls `viewModel::splitClipAtPlayhead` directly. The delete button auto-disables when no clip is selected. Both are always visible, regardless of the current tool mode.

### Internals

- `TimelineToolbarButton` gained `highlight: Boolean` and `enabled: Boolean` parameters so the split button can render as a prominent accented action and the delete button can render as greyed-out when disabled. Default values preserve existing call sites unchanged.
- `ClipGestureZone` enum (`TRIM_LEFT`, `TRIM_RIGHT`, `SLIDE`, `SLIP`, `NONE`) defined at file scope in `Timeline.kt` to make the unified gesture routing explicit.
- Zoom constants `MIN_TIMELINE_ZOOM = 0.01f` and `MAX_TIMELINE_ZOOM = 10f` lifted to file-scope constants in both `Timeline.kt` and `EditorViewModel.kt` to keep the zoom clamps consistent across pinch, toolbar buttons, and the fit-to-window calc.

## v3.65.0 — Deep Audit Phase 27: Error-Path Allocation & OOM Cleanup

Targeted correctness fixes from continued engineering audit. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`AudioEngine.extractWaveform` — exception path allocated unbounded `FloatArray(sampleCount)`** — Every other return path in `extractWaveform` allocates `FloatArray(boundedSampleCount)`, where `boundedSampleCount = sampleCount.coerceAtMost(10_000)` caps the result at ~40 KB. The outer `catch (e: Exception)` block silently violated this contract by allocating `FloatArray(sampleCount)`. Callers that pass a large `sampleCount` (e.g. `48_000` for a high-resolution scrub waveform) would receive a 192 KB array on decoder failure instead of the expected 40 KB, and — because callers may cache the result under the `"uri|sampleCount"` key — repeated failures compound into a persistent oversized cache entry. Fixed: use `boundedSampleCount` on the exception path to match the other four return paths in the function. Completes the v3.59.0 fix which patched the `audioTrackIndex < 0` path but left the outer catch unfixed.

- **`ContactSheetExporter.export` — PNG-encoder OutOfMemoryError bypassed partial-file cleanup** — `sheet.compress(…)` allocates native PNG-encoder buffers (~bitmap-sized) and can raise `OutOfMemoryError` on very large contact-sheet grids. The outer `catch (e: Exception)` block did not catch `OutOfMemoryError` (a `Throwable` subclass, not `Exception`), so the `outputFile.delete()` cleanup was skipped. The `finally` block still recycled the source bitmap (no native leak), but a half-written or 0-byte PNG was left on disk — subsequent opens would silently serve that file as though the export had succeeded, and the user would see a truncated contact sheet without any error toast. Fixed: widen the catch to `Throwable`, but rethrow `CancellationException` first so coroutine cancellation still propagates to the caller instead of being swallowed as a generic "render failed" result.

### Notes
- No DB schema changes. No new dependencies. No UI changes.

## v3.64.0 — Deep Audit Phase 26: Defensive Hardening

Defensive improvements from final end-to-end engineering audit sweep. No behaviour change on valid inputs, no new dependencies.

### Improvements

- **`BeatDetectionEngine.fft` — undocumented power-of-2 contract now enforced** — The Radix-2 Cooley-Tukey FFT implementation requires its input arrays to have power-of-2 length; this was documented in a comment but not enforced in code. A non-power-of-2 input produces an incorrect bit-reversal permutation and a silently corrupted frequency spectrum, yielding wrong beat timings with no error signal. Added `require(n > 0 && (n and (n - 1)) == 0)` at the top of `fft()` so any future caller passing a wrong-sized array fails fast with a clear diagnostic instead of silently producing corrupt beat maps.

- **`SettingsRepository.updateDefaultCodec` — silent rejection now logged** — When an unrecognised codec string is passed to `updateDefaultCodec`, the method silently returns without writing to DataStore. This is the correct defensive behaviour, but the absence of any log message makes it invisible during debugging or when tracing unexpected UI state (e.g. the codec dropdown appearing to reset). Added `Log.w("SettingsRepository", "Ignoring unknown codec value: $value")` before the early return so that corrupt or migrated settings strings surface in logcat without changing the validation logic.

## v3.63.0 — Deep Audit Phase 25: Segmentation and Stabilization Reliability

Targeted correctness and defensive fixes from continued end-to-end engineering audit of AI processing and segmentation layers. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`SegmentationEngine` — `avgConfidence` divided by full pixel count instead of actual iteration count** — The confidence accumulation loop was bounded by `minOf(floatBuffer.remaining(), w * h)` to avoid over-reading a short buffer, but the average was computed as `totalConfidence / (w * h)` using the full frame size. When MediaPipe returns a shorter buffer than `w * h` pixels, the average is artificially deflated, potentially causing callers to reject a valid high-confidence segmentation mask as too uncertain. Edge case: if `w * h == 0` (e.g. the model output has degenerate dimensions), the division produces `NaN`, which propagates to the `SegmentationResult.confidence` field. Fixed: track the actual loop iteration count as `pixelCount = minOf(floatBuffer.remaining(), w * h)` and divide by `pixelCount`; guard against the zero case with an explicit `if (pixelCount > 0) … else 0f`.

- **`AiToolsDelegate.applyStabilization` — partial output file not cleaned up on error or cancellation** — After `stabilizationEngine.analyzeMotion()` succeeds, a `File` object for the output MP4 (`cacheDir/stabilized_<clipId>.mp4`) is created and passed to `stabilize()`. If `stabilize()` returns `null` (error path) the output file was left on disk uncleaned; if the coroutine is cancelled during `stabilize()` (via `cancelAiTool()`) the `CancellationException` propagated without deleting any partial write. Repeated cancellations accumulate orphaned files, each potentially hundreds of MB when the real OpenCV integration ships. Fixed: wrapped the `stabilize()` call in `try/catch(Exception)` — deletes the output file before re-throwing so CancellationException also triggers cleanup. Added explicit `outputFile.delete()` in the null-result branch.

## v3.62.0 — Deep Audit Phase 24: Rendering, Subtitle, and Proxy Reliability

Targeted correctness fixes from continued end-to-end engineering audit across Lottie rendering, subtitle export, and proxy generation. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`LottieTemplateEngine` — NaN progress on zero-duration composition** — `(frameTimeMs.toFloat() / composition.duration).coerceIn(0f, 1f)` produces `NaN` when both operands are zero (frame 0 of a malformed/empty animation where `duration = 0`). IEEE 754: `0f / 0f = NaN`; `NaN.coerceIn(…)` returns `NaN` because all NaN comparisons return false. Passing `NaN` to `drawable.progress` renders the animation at an undefined frame position, corrupting any title overlay in the exported video. Fixed: guard the denominator with `takeIf { it > 0f } ?: 1f`.

- **`ContactSheetExporter` — bitmap leaked when Paint or Canvas construction throws OOM** — `Canvas(sheet)` and three `Paint(…)` objects were created between the OOM-guarded `Bitmap.createBitmap` block and the `try { } finally { sheet.recycle() }` block. Any `OutOfMemoryError` raised during `Paint.ANTI_ALIAS_FLAG` native allocation or `Typeface.create()` would propagate past both the catch and the finally, leaving the large sheet bitmap unreleased. On devices that are already memory-constrained (the reason the batch-export flow exports in one-at-a-time fashion), repeated leaks accumulate and cause OOM crashes. Fixed: moved `Canvas` and all `Paint` initialisations inside the `try` block so the `finally { sheet.recycle() }` guards the entire render path.

- **`SubtitleExporter` — VTT word-level cue timestamps not validated against caption bounds** — Word-level timestamps in `Caption.words` are passed directly into `<${formatVttTime(word.startTimeMs)}>` cues without checking that they fall within the caption's `startTimeMs..endTimeMs` range. The WebVTT spec requires word timestamps to lie within the parent cue's range; parsers in browsers and media players silently discard the entire cue when this is violated, causing the affected subtitle line to disappear entirely. Fixed: filter `caption.words` to only those whose `startTimeMs` falls in `caption.startTimeMs..caption.endTimeMs` before emitting cue tags. Falls back to plain caption text when no valid word cues remain.

- **`ProxyWorkflowEngine` — cancellation not checked between proxy generation jobs** — The `for ((clipId, entry) in needsProxy)` loop did not call `ensureActive()` before starting each clip's `proxyEngine.generateProxy()` call. WorkManager cancels the owning coroutine when the device goes to sleep, battery-saver activates, or the user cancels the background task; without a cooperative check the loop continues until the process is forcibly killed, burning CPU/battery and leaving partial proxy files. Fixed: added `ensureActive()` at the top of each iteration so cancellation is observed promptly before each potentially multi-minute encode begins.

## v3.61.0 — Deep Audit Phase 23: Concurrency, Export, and Storage Reliability

Targeted correctness and reliability fixes from continued end-to-end engineering audit of export, persistence, and storage layers. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`ProjectListViewModel` — template share fails on devices without external storage** — `getExternalFilesDir(null)` returns `null` when external storage is unavailable (formatted as internal, removed, or permission-restricted). `File(null, "archives/templates")` creates a relative path, causing `FileProvider.getUriForFile()` to throw `IllegalArgumentException("Failed to find configured root")`, silently reporting "Template export failed". Fixed: fall back to `filesDir` when external storage is unavailable. Also added a `<files-path name="archives" path="archives/" />` entry to `file_paths.xml` so FileProvider can serve files from the internal fallback path.

- **`ProjectAutoSave` — `copyAutoSave()` checked file existence outside the mutex** — `fromFile.exists()` was evaluated before `saveMutex.withLock {}`, leaving a race window where a concurrent `clearRecoveryData()` could delete the file between the check and the read, producing a `FileNotFoundException` rather than a clean `false` return. Moved the `exists()` check inside the lock so the check, read, mutate, and write happen atomically with respect to all other save/load operations.

- **`VideoEngine` — `cancelExport()` read `activeExportOutputFile` without synchronization** — `export()` assigns `activeExportOutputFile = outputFile` inside a `synchronized(this)` block, but `cancelExport()` read and nulled it without any synchronization. Under the JVM memory model, a non-volatile field written after a volatile store has no happens-before guarantee for unsynchronized readers. In the narrow window between state being set to EXPORTING and `activeExportOutputFile` being assigned, a concurrent cancel call would read `null` and fail to delete the partial output file. Fixed: wrapped `cancelExport()` body in `synchronized(this)` to match the lock used in `export()`, keeping progress update outside the lock (non-critical, no synchronization requirement).

- **`ExportDelegate` — `cancelExport()` missed state update for GIF and early-cancel paths** — The UI state update to `CANCELLED` was guarded by `if (currentState == ExportState.EXPORTING)`. For GIF exports (running on `nonVideoExportJob`, not through VideoEngine's state machine) or when cancel is tapped before state propagates to EXPORTING, the guard evaluated to false and the stateFlow was never updated. The cancel button would appear to do nothing. Fixed: always push `CANCELLED` state; the cancel button is only reachable while an export is active so CANCELLED is always the correct terminal state.

- **`ExportDelegate` — batch export advanced to next item without waiting for current export** — `videoEngine.resetExportState()` resets the VideoEngine's internal state to IDLE but does not update the ExportDelegate's `stateFlow.exportState`. The wait loop `stateFlow.map { it.exportState }.first { it != IDLE && it != EXPORTING }` therefore immediately returned the previous item's COMPLETE/ERROR state, marking the new item as COMPLETED before its export had started. Two items would export concurrently, and their statuses would be misattributed. Fixed: reset `exportState` and `exportProgress` in the `stateFlow.update` call alongside `exportConfig` so the wait loop correctly waits for the new export to reach a terminal state.

- **`ExportDelegate` — batch progress collector not joined before next item** — `progressJob.cancel()` signals cancellation but does not wait for the collector coroutine to finish. The collector could still be executing a `stateFlow.update` on the just-finished item while the next item's coroutine started its own `stateFlow.update`, producing a concurrent write race on the batch queue list. Fixed: added `progressJob.join()` after `progressJob.cancel()` to ensure the old collector fully stops before the next iteration begins.

### Notes
- No DB schema changes. No new dependencies. `file_paths.xml` adds `<files-path name="archives" path="archives/" />` as a new FileProvider root for internal-storage template sharing.

## v3.60.0 — Deep Audit Phase 22: Frame Capture & Timeline Reliability

Targeted correctness fixes found during continued end-to-end engineering audit of delegate and engine files. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`FrameCapture` — bitmap leak on `createScaledBitmap` OOM** — `Bitmap.createScaledBitmap()` throws `OutOfMemoryError`, which is a `Throwable` (not an `Exception`). The outer `catch (e: Exception)` block did not catch it, so the source bitmap was never recycled on OOM, causing a native memory leak. Fixed: wrap the scale call in a dedicated `catch (t: Throwable)` that recycles the bitmap and returns `null`, consistent with the function's existing null-on-failure contract.

- **`ClipEditingDelegate` — split clip loses linked audio ID when audio track is locked** — When a video clip on an unlocked track was split, the second-half copy had its `linkedClipId` resolved through `newIdsByOldId[linkedId]`. If the linked audio clip was on a locked track (and therefore not included in the split), `newIdsByOldId[linkedId]` returned `null`, silently setting `linkedClipId = null` on the new clip. Audio and video then played out of sync with no user-visible error. Fixed: fall back to `?: linkedId` to preserve the original link when the paired clip was not part of the split operation.

### Notes
- No DB schema changes. No new dependencies. No UI changes.

## v3.59.0 — Deep Audit Phase 21: Engine Reliability Fixes

Targeted correctness and reliability fixes found during an end-to-end engineering audit of all engine and delegate files. No behaviour change on valid inputs, no new dependencies.

### Bug fixes

- **`AudioEngine` — `FloatArray(sampleCount)` OOM bypass** — When no audio track was found, the early-return path allocated `FloatArray(sampleCount)` instead of `FloatArray(boundedSampleCount)`. Callers that pass a large `sampleCount` (e.g. 48 000) would receive a much larger array than the 10 000-element cap applied everywhere else in the function, silently defeating the OOM guard. Fixed: use `boundedSampleCount` on the early-return path, consistent with the rest of the function.

- **`ProxyEngine` — stale zero-byte proxy served as valid** — The cached-file existence check used `outFile.exists()`, which returned `true` for a zero-byte stub left by a prior failed Transformer run. The code would then cache and return this broken URI without re-rendering. Fixed: check `outFile.isFile && outFile.length() > 0L`, mirroring the identical guard already present in the `onCompleted` callback.

- **`MultiCamEngine` — integer divide-by-zero on `targetSampleRate = 0`** — `max(1, sourceSampleRate / targetSampleRate)` computes the integer division before `max` runs; a caller passing `targetSampleRate = 0` would throw `ArithmeticException`. Fixed: coerce `targetSampleRate` to at least 1 before the division.

- **`MultiCamEngine` — `Float.POSITIVE_INFINITY` poison from `channels = 0`** — `format.getInteger(KEY_CHANNEL_COUNT)` can return 0 from a malformed or synthetic `MediaFormat`. `mono /= channels` then produces `Float.POSITIVE_INFINITY`, which propagates through the entire decoded sample list and corrupts multi-cam sync analysis. Fixed: coerce `channels` to at least 1.

- **`ColorGradingDelegate` — non-atomic LUT file import** — The `.cube` file was written directly to its destination path via `destFile.outputStream()`. A mid-write failure (disk full, cancelled job) left a partial file at the live path; a subsequent import of the same filename would overwrite it, but any access in between would use a corrupt LUT. Fixed: write to a sibling `.tmp` file first, then `renameTo` (atomic on the same filesystem). Falls back to `copyTo + delete` if rename crosses a mount point.

### Notes
- No DB schema changes. No new dependencies. No UI changes.
- `.gitignore` already excludes `*.apk`, `CLAUDE.md`, and other local artefacts — no changes needed.



End-to-end hardening sweep across the v3.57 engine scaffolding plus three parallel Explore-agent audits of adjacent subsystems. Landed every real defect found, rejected the false positives with rationale. Net +300 / −15 lines across 8 code files + 4 new test files (+470 lines of tests). Test count 44 → 73.

### v3.57 engine self-review (fixes to the stubs that shipped yesterday)
- **`SilenceDetectionEngine.DEFAULT_FILLERS`** — removed multi-word entries (`"you know"`, `"i mean"`, `"sort of"`, `"kind of"`). Whisper emits one `WordTimestamp` per whitespace-separated token, so the single-token matcher at [SilenceDetectionEngine.kt:136](app/src/main/java/com/novacut/editor/engine/SilenceDetectionEngine.kt) could never match these entries — they were silently dead code. Added a regression-guard test that fails if anyone adds them back.
- **`SilenceDetectionEngine.detectSilences`** — sample-count math now stays in Long space then clamps to waveform bounds. The prior `(minSilenceMs * sampleRate / 1000L).toInt()` would overflow Int on pathological thresholds (days @ 48 kHz) and surface as a negative-length run, making every sample read "silent".
- **`EquirectangularEngine.Pose`** — NaN/Infinity guards added to `init`. `NaN in -180f..180f` returns true in Kotlin (all NaN comparisons are false, so the range check never fails), so a corrupt JSON Float could have propagated through `poseAt` into every GL uniform.
- **`EquirectangularEngine.poseAt`** — now sorts keyframes internally + uses shortest-arc angular lerp for yaw/roll. Previously a 179° → −179° transition would sweep 358° the wrong way instead of 2° the correct way, and callers had to pre-sort keyframes or get garbage.
- **`AdjustmentLayerEngine.partitionByLayerBoundaries`** — early return on `clipEndMs <= clipStartMs`. Without the guard, a degenerate clip range produced a TreeSet with a single element and `zipWithNext` returned empty — technically correct but easy to regress.
- **`HdrCapabilityProbe.probe`** — `caps.profileLevels` null-guarded. Some OEM / non-standard codec implementations return `null` here and the raw iteration would have NPE'd.
- **`ProjectSyncEngine.addTarget` / `removeTarget`** — CAS-loop replaced the read-modify-write `_flow.value = _flow.value + x` pattern, which could have lost updates under concurrent edits from Settings + a background sync job.
- **`StockAssetEngine`** — removed unused `okhttp3.OkHttpClient` import.

### Persistence + lifecycle fixes
- **`ProjectArchive.importArchive` InputStream leak** — `mkdirs()` check moved ahead of `openInputStream()`. Previous ordering opened the stream first, so a `mkdirs()` failure (permissions / FS full) would return with an unclosed InputStream. The outer `.use { }` block started three lines later, so any exception between was uncaught by auto-close.
- **`EditorViewModel.onCleared` scrubbing-mode reset** — always calls `videoEngine.setScrubbingMode(false)` now. If the activity dies mid-trim (OS kill, uncaught drag-handler exception), the singleton VideoEngine would otherwise carry the stale scrubbing flag into the next project opened in the same process.
- **`OverlayDelegate.addImageOverlay` stale-playhead snapshot** — reads `playheadMs` / `totalDurationMs` once into locals rather than three separate `stateFlow.value` reads. Previously a playhead scrub between the start and end reads could produce an overlay whose `endTimeMs` didn't line up with its `startTimeMs`.

### Test infrastructure
- **`testOptions.unitTests.isReturnDefaultValues = true`** added to the Android block. Plain JVM unit tests on code that calls `android.util.Log.*` previously threw `Method X not mocked` instead of returning 0. This was why `SilenceDetectionEngineTest` couldn't reach the assertions on its first run. The setting matches the existing pragmatic-JVM testing approach — instrumentation tests remain the path for anything that legitimately needs the Android runtime.
- **4 new unit-test files** covering the non-stub portions of the v3.57 scaffolding:
  - [SilenceDetectionEngineTest.kt](app/src/test/java/com/novacut/editor/engine/SilenceDetectionEngineTest.kt) — 11 tests: empty waveform, zero sample rate, all-silent, all-loud, sub-threshold gap, padding-exceeds-run, overflow guard, filler case-insensitivity, filler padding clamp, no-multi-word-fillers regression guard.
  - [AdjustmentLayerEngineTest.kt](app/src/test/java/com/novacut/editor/engine/AdjustmentLayerEngineTest.kt) — 10 tests: no-layers, disabled layer, non-overlap, edge-touch, overlap accumulation, partition with/without layers, invalid-range guard, layer extending beyond clip, zero-duration layer rejection.
  - [EquirectangularEngineTest.kt](app/src/test/java/com/novacut/editor/engine/EquirectangularEngineTest.kt) — 9 tests: empty keyframes, before-first / after-last clamp, linear lerp midpoint, unsorted input, yaw wrap-around, EASE_IN shape, NaN / Infinity / out-of-range rejection.
  - [AudioMasteringEngineTest.kt](app/src/test/java/com/novacut/editor/engine/AudioMasteringEngineTest.kt) — 7 tests: unique preset IDs, non-empty names, EQ gain/frequency/Q bounds, LUFS / true-peak ranges, compressor ratio sanity, known/unknown preset lookup.

### False-positive notes (audit-agent findings investigated and left unchanged)
- **`AudioEngine` MediaExtractor leak claim** — `MediaExtractor()` constructor is no-arg and non-throwing; `setDataSource` and everything downstream are inside the outer try, and the finally releases on every path. Agent conflated the constructor with `setDataSource` exposure.
- **`duplicateClip` / `mergeClipWithNext` UUIDs inside CAS lambda** — agent flagged that UUIDs regenerate on retry. They do, but the IDs are not referenced outside the committed state, so the only cost is some wasted UUID allocation per retry. The FINAL committed state always has self-consistent IDs. Safe.
- **`saveProject()` on Main thread** — `viewModelScope.launch {}` without an explicit dispatcher defaults to `Main.immediate`, but Room's suspend DAO functions internally bounce to its own IO pool. No Main-thread DB I/O.
- **`TemplateMarketplaceEngine.setRegistryUrl`** — simple StateFlow value assignment is already atomic (volatile write); no CAS needed.
- **Drawing-mode orphan on forced close** — `dismissedPanelState()` already resets `isDrawingMode = false`, and every panel-open goes through it; back-gesture closure goes through `hideDrawingMode()` which also clears it. No orphan path found.
- **`ProjectAutoSave` silent-drop UX concern** — each drop already logs `Log.w`. Surfacing a user-facing warning when >50% of a track is dropped is a UX improvement, out of scope for this audit — tracked as follow-up.

### Notes
- No DB schema changes. No new Maven dependencies. No new strings. No behaviour change on valid inputs.
- Full test suite: 73 tests, 0 failures, 0 errors (44 → 73).
- Final `compileDebugKotlin` + `testDebugUnitTest` both green.

## v3.57.0 — Tier A/B/C Engine Scaffolding

Scaffolds every remaining engine stub called out in [ROADMAP.md](ROADMAP.md) so each Tier A/B/C item has a concrete file, Hilt-injectable class, typed config/result data classes, and a documented fallback behaviour. Each engine is a drop-in surface: when its dependency lands, only the implementation body changes — ViewModel and UI can start wiring against the surface today.

Net +1,200 lines across 16 new files. No Maven deps added (keeps APK size and build time unchanged). No UI wiring. Existing stubs (`StabilizationEngine`, `FrameInterpolationEngine`, `UpscaleEngine`, `VideoMattingEngine`, `TapSegmentEngine`, `TtsEngine`, `FFmpegEngine`, `StyleTransferEngine`, `NoiseReductionEngine`, `InpaintingEngine`) are unchanged.

### Tier A — new stubs
- **`OboeResamplerEngine`** — Oboe-backed sinc SRC for 44.1↔48 kHz mixing. 5 quality levels (linear → 64-point sinc). `isAvailable()` returns false; callers fall back to Media3 resample.
- **`RiveTemplateEngine`** — parallel runtime to `LottieTemplateEngine` for interactive 120 fps animations. 5 built-in template IDs defined; `renderFrame()` returns null until `app.rive:rive-android` is added.

### Tier C — new engines
- **`StemSeparationEngine`** (C.1) — Demucs v4 htdemucs contract. Returns isolated vocals/drums/bass/other stems as WAV URIs. Model state flow + progress.
- **`SilenceDetectionEngine`** (C.2) — **not a stub** — pure-Kotlin silence detection on the existing waveform path. RMS-threshold run-length scan with configurable padding + min-silence gate. Filler-word detector consumes `SherpaAsrEngine.WordTimestamp` with a 16-word default filler set (`um`, `uh`, `like`, `you know`, …). Returns `CutProposal[]` for the ViewModel to apply as undo-able split+delete commands.
- **`VoiceCloneEngine`** (C.3) — XTTS v2 via Sherpa-ONNX. `VoiceProfile` enrollment from 6 s sample; 16-language synthesis contract.
- **`LipSyncEngine`** (C.4) — Wav2Lip GAN ONNX contract. Tracking-optional face detection, configurable blend strength. Notes Wav2Lip's non-commercial license and flags for pre-release audit of permissive alternatives (MuseTalk, SadTalker).
- **`CaptionTranslationEngine`** (C.5) — NLLB-200 / MADLAD-400 contract. 3 model variants (350 / 600 / 1500 MB). Preserves word timings by proportional redistribution so karaoke-highlight rendering keeps working across translation.
- **`AudioMasteringEngine`** (C.6) — **not a stub** — 5 curated mastering chains (Podcast Voice, Music Master, Dialogue Clean, ASMR, Social Loud) with tuned HPF + EQ bands + compressor + de-esser + noise-reduction mode + EBU R128 target. Pairs with the existing DSP chain — no new DSP code.
- **`StockAssetEngine`** (C.7) — single-interface wrapper for Pexels (video + photo), Pixabay (video + photo), Freesound (SFX), Free Music Archive (music). Attribution string carried per asset. User-supplied API keys.
- **`CameraCaptureEngine`** (C.8) — CameraX + teleprompter contract. 720/1080/4K, front/back, HDR flag, stabilization flag, optional scrolling teleprompter config (WPM, font, mirror, background alpha).
- **`HdrCapabilityProbe`** (C.9) — **not a stub** — live `MediaCodecList` walk classifying HEVC Main10HDR10 / HDR10+ and AV1 HDR profiles across every encoder on the device. Returns supported `HdrFormat` set + capability envelope (max w/h/bitrate). Builds a configured HDR `MediaFormat` with BT.2020 / ST.2084 (or HLG) transfer characteristics. Advisory only — Android 13+ API 33 gated; pre-Tiramisu returns empty.
- **`EquirectangularEngine`** (C.10) — **partially non-stub** — `Pose` data class (yaw/pitch/roll/FOV validated), 3 output projections (equirectangular / rectilinear / little-planet), keyframed-pose interpolation with 4 easings already implemented. GL uniforms return empty until the 360 pipeline is wired.
- **`AdjustmentLayerEngine`** (C.11) — **not a stub** — `AdjustmentLayer` data class with `effectsForClip()` (returns contributing effects by overlap test) and `partitionByLayerBoundaries()` (splits clip range at layer boundaries for per-sub-range effect chains). Consumes existing `model/Effect`. Pairs with a pending `Track`-model extension for storage.
- **`TimelineImportEngine`** (C.14) — inverse of the existing `TimelineExchangeEngine` export. Format auto-detect (`.fcpxml` / `.otio` / `.edl`). `ImportResult` captures dropped effects + unresolved media URIs so the UI can surface lossy conversions before the user commits.
- **`TemplateMarketplaceEngine`** (C.15) — self-hostable registry with documented JSON schema v1. User-configurable registry URL (defaults to `novacut.dev/marketplace/index.json`). Complements the existing `.novacut-template` export/import path from v3.8.
- **`ProjectSyncEngine`** (C.16) — three backends (Syncthing-style LOCAL_FOLDER, SELF_HOSTED HTTP/WebDAV, LAN_PEER over mDNS). `SyncPlan` surfaces LOCAL_AHEAD / REMOTE_AHEAD / DIVERGED state so the UI can offer a real merge dialog instead of silent last-writer-wins clobber.

### Tier B — tracked (code unchanged this release)
B.1–B.7 are architectural fixes to existing files (Media3 Compositor migration, SmartRenderEngine bypass wire, text-stroke export, ProjectArchive import, etc.) that land per-item in subsequent releases. They remain open in ROADMAP.md with touch-file pointers.

### ROADMAP
Replaced corrupted 394-line ROADMAP.md (duplicated headers, lost content) with a clean 109-line forward-looking tracker. Tier A has a 13-row table with stub path, Maven coord, model size, and current fallback; Tier B lists 7 limitations with touch-file paths; Tier C lists 16 items grouped by audio/media/timeline/interop with specific engine + panel touch points. Includes dependency-risk notes (OpenCV arm64, FFmpegX maintainer check, Wav2Lip license, model quantisation) and sequencing guidance.

### Notes
- All new stubs follow the existing stub contract: `@Singleton`, `@Inject constructor`, typed config/result data classes, `isX()` / `isXReady()` query, suspend entry point, `Log.d(TAG, "stub -- ...")` diagnostic.
- Every engine is Hilt-injectable without AppModule changes (constructor-injection discovers them).
- No behaviour change on valid inputs for any existing code path.

## v3.56.0 — Wide-Net Hardening Pass (Audit Phase 19)

Four parallel Explore-agent audits covered subsystems that hadn't been looked at in the v3.50 pass: **AI/ML engines**, **audio DSP**, **effects + shaders**, and **exchange/proxy/render**. This release lands every real Critical + all high-value Highs; several agent findings turned out to be false positives (AudioEngine's extractor.release already in outer finally, SegmentationEngine retriever already properly nested) and are noted for the record.

### GL / shader safety
- **`LottieOverlayEffect`** — every `glGetUniformLocation` now guarded with `if (loc >= 0)` before `glUniform*`, matching the pattern LutEngine + SegmentationGlEffect adopted post-v3.45. Writing to uniform location −1 crashes Mali / Tegra drivers. `glLinkProgram` now checks `GL_LINK_STATUS`; a failed link logs + throws instead of silently producing a corrupt program (Intel / PowerVR render black).
- **`KeyframeEngine.evaluateCubicBezierTime`** — fast-paths to linear interpolation on any non-finite handle (`cp1x/y`, `cp2x/y`) or non-finite input `t`. `coerceIn` does not clamp NaN — every comparison against NaN is false — so a single poisoned handle from a corrupt project JSON would silently NaN-poison every animated property in the clip.
- **`EffectBuilder.buildTransitionEffect` / `buildTransitionOutEffect`** — `transition.durationMs` clamped to `[1, 2_147_000L]` before `* 1000f` to avoid `Float.POSITIVE_INFINITY` at >25-day durations, which poisons the transition shader's progress calculations.

### Resource safety
- **`SegmentationGlEffect.uploadMaskTexture` / `uploadFallbackMask`** — `GLUtils.texImage2D` wrapped in try/finally so a driver OOM / invalid-format exception can't leak the 260 KB / 4 B bitmap we created immediately before the upload. On long renders this would otherwise accumulate across every failed frame.

### DSP / math correctness
- **`AudioEngine.mixAudioTracks`** — total-sample arithmetic moved to `Long` before narrowing to `Int`; on timelines >6h 45m at 44.1 kHz stereo the previous `(maxDuration * rate * channels).toInt()` would wrap negative and throw `NegativeArraySizeException` on the FloatArray allocation. Now fails gracefully with an empty mix + warn-log.
- **`LoudnessEngine.applyKWeighting`** — `safeSampleRate` clamped to the practical range `[8_000, 192_000]` instead of `coerceAtLeast(1)`. A bogus 2 Hz from a malformed MediaFormat would have produced a near-1.0 K-weighting `alpha` that effectively disabled the filter.
- **`BeatDetectionEngine.estimateBpm`** — explicit `bestInterval > 0` guard before `60000f / bestInterval`. `coerceIn(30f, 300f)` does not clamp Infinity (Infinity stays Infinity), so pathological input could have leaked an Infinity BPM into downstream UI.
- **`WhisperEngine.runDecoder`** — validates `logits.info.shape.size >= 3` + non-positive dims before indexing. A malformed model output with rank < 3 would otherwise throw `IndexOutOfBoundsException`, leaking every tensor accumulated in the decode loop and aborting transcription silently.
- **`WhisperMel`** — `maxVal` finite-check (empty-spectrum edge case) plus per-sample non-finite guard after normalisation. Previously a corrupt log10 path could NaN-poison the entire spectrogram and make Whisper produce garbage transcriptions instead of a clean empty result.
- **`ColorMatchEngine.analyzeBitmap`** — `w`/`h` hard-capped at 512 each. Tiny-maxDim source (50-pixel composited overlay frame) left `scale = 1f`; a pathological 100-megapixel input would have allocated 400 MB for the histogram.

### Concurrency
- **`ProxyEngine.generateProxy`** — new per-source-key `Mutex` map serialises concurrent calls for the same `sourceUri`. Previously two near-simultaneous calls both passed the `outFile.exists()` check, both started a Transformer writing to the same `proxy_<hash>.mp4`, and the second write corrupted the first. `computeIfAbsent` guarantees one Mutex per key without a coarse-grained map lock.
- **`TtsEngine.synthesize` — `invokeOnCancellation`** — clears the `UtteranceProgressListener` before `engine.stop()`. The `TextToSpeech` engine is effectively a singleton; a stale cancelled-job listener would otherwise fire `onDone`/`onError` into a continuation that already threw `CancellationException`.

### Security
- **`ProjectArchive.importArchive` ZIP-slip guard** — switched from `outFile.path.startsWith(canonicalTargetDir.path + File.separator)` to `outFile.toPath().startsWith(canonicalTargetDir.toPath())`. The string-prefix check mishandled Windows separators (canonicalTargetDir.path may or may not end with `\`) and was case-sensitive on case-insensitive filesystems. NIO `Path.startsWith` normalises path elements so neither bypass works.
- **`LutEngine.parseCube` / `parse3dl`** — per-line `toFloatOrNull` tolerance. Malformed lines (diagnostic text, commented artefacts from some LUT authoring tools) now skip with a warn-log instead of rejecting the whole LUT via the outer catch.

### False-positive notes
Four findings were investigated and left unchanged:
- AudioEngine.extractWaveform / decodeToPCM already release the extractor in the outer finally (the "MediaCodec leak on exception" claim conflated decoder lifecycle with extractor lifecycle).
- SegmentationEngine already releases the retriever via properly-nested try/finally — every `return@withContext null` path still runs the finally.
- AiToolsDelegate's `aiJob` race was already mitigated by the identity check documented in the v3.26 audit (`if (aiJob === thisJob)`).
- `OverlaySettings.Builder` on OTIO — `JSONObject.put` already escapes string values.

### Notes
- No DB schema / dependency changes. No new strings. Net +118 / −22 lines across 11 files.
- Every fix is additive or strictly tightens an existing guard — no behavioural change on valid inputs, defence-in-depth on malformed ones.

## v3.55.0 — Device-Aware Encoder Capability Probe

Extends v3.52's pre-flight warning pattern with a real hardware-capability check so users see the warning *before* they burn 40 minutes of render time and hit a Transformer error mid-export.

### New `EncoderCapabilityProbe` engine helper
- Queries `MediaCodecList.REGULAR_CODECS` for every encoder advertising the selected `codec.mimeType`, then walks each one's `VideoCapabilities` to check whether the user's (width, height, framerate, bitrate) tuple is actually accepted.
- Walks **all** matching encoders so a device with e.g. both Qualcomm hardware HEVC and Google software HEVC is correctly reported as supporting the higher of the two's capabilities.
- Returns a `Capability(supported, reason)` where `reason` is a human-readable string pre-composed for toast / warning display:
  - "No HEVC encoder present — falling back to H.264 is safer."
  - "HEVC on this device tops out at 1920×1080"
  - "HEVC at 3840×2160 is capped to 30 fps on this device"
  - "HEVC bitrate is capped at 80 Mbps on this device"
- Silent-safe: if `MediaCodecList` throws (some OEM ROMs do), returns `Capability(supported = true, reason = null)` rather than falsely warning.

### ExportSheet wiring
- Probe result is `remember`-cached on `(codec, width, height, framerate, videoBitrate)` so repeated recompositions during slider drags don't re-query `MediaCodecList` on every frame. Probe only re-runs when one of those inputs changes.
- When `!probe.supported`, the reason string is appended to the existing pre-flight warnings column alongside the long-render / large-file / AV1-slow warnings from v3.52.

### Notes
- This is a pre-flight **advisory**, not a guarantee — real-world encoders occasionally refuse configurations they claim to support, and that remains Media3 Transformer's own retry domain. The probe's job is to surface obvious footguns ("4K HEVC on a budget phone") before the render starts.
- No state, no persistence, no new strings (reasons are composed at probe time).
- Complements the existing `ExportConfig.getAvailableCodecs` check (which filters by codec *presence*) with a finer-grained check that the full render spec is acceptable.

## v3.54.0 — Brand Watermark Burn-in

First Tier-2 feature lands: image watermark burned into every frame of the exported video via a Media3 `BitmapOverlay`. Composites on-GPU in the same overlay pass as any text overlays, so a project-wide watermark has no extra cost on clips that don't carry text.

### Model
- New `Watermark(sourceUri, position, opacity, scalePercent)` data class with validation (`opacity ∈ [0,1]`, `scalePercent ∈ [5,50]`).
- New `WatermarkPosition` enum: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER.
- `ExportConfig.watermark: Watermark?` — null means no watermark, no engine cost.

### Engine
- New `ExportWatermarkOverlay` object — decodes the user's image via `ContentResolver.openInputStream` once per export, scales to the requested percentage of output frame width (not source width, so the watermark visually fills the same fraction regardless of clip resolution variation), wraps as `BitmapOverlay.createStaticBitmapOverlay` with Media3's anchor system.
- Anchor geometry uses normalised coords with a 7.5% safe-margin offset from the nearest edge (`±0.85` instead of `±1.0`) — matches professional broadcast-safe placement so the watermark never kisses the frame border.
- Silent non-fatal failure: corrupt / unreadable images return null, and the export runs watermark-free rather than erroring out the entire render.

### Pipeline integration
- `VideoEngine.buildEditedMediaItem` was already building an overlay list per clip for text overlays; refactored to `buildList<TextureOverlay>` and append the watermark (when present) into the same `OverlayEffect`. Single GL pass handles text + watermark together.

### UI
- New `WatermarkSection` composable inside ExportSheet's Special Outputs section, visible only when `videoModeEnabled` (audio / stems / GIF / contact-sheet exports skip it).
- Image picker (`ACTION_OPEN_DOCUMENT` with `image/*` filter), position chip row, 5%-step opacity slider, 1%-step scale slider (5–50% of output width), and a "Choose a different image" button so users can swap the source without losing their tuned position/opacity/scale.

### Notes
- No DB schema change. Six new string resources.
- `Watermark.sourceUri` is a `Uri` — persistable read permission isn't taken at the ExportSheet picker here (unlike MediaPicker's video imports) because export runs synchronously in the same session the URI was granted. If a persisted-across-restart watermark is ever needed, wire the launcher through a helper that calls `takePersistableUriPermission`.

## v3.53.0 — Project Filter Chips + Bulk-Delete Guard

Two Tier-1 UX wins from the backlog.

### Project gallery filter chips (§8.2)
- New `ProjectFilterMode` enum (ALL / RECENT_7D / LONG / SHORT / EMPTY). Orthogonal to the existing `SortMode`, so users can e.g. look at "This week" projects sorted by "A–Z".
- `ProjectListViewModel` gains `_filterMode` StateFlow; the `projects` flow now combines four sources (projects + search + sort + filter) so every emission funnels through one consistent filter+sort pipeline.
- `ProjectListScreen` renders a `ProjectFilterChipsRow` directly under the home hero, wrapping via `FlowRow` so the chip set stays reachable on narrow screens without horizontal scroll gestures (which would fight the outer `LazyVerticalGrid`).
- Thresholds: LONG = `durationMs >= 60_000`, SHORT = `1..9_999`, RECENT_7D = `updatedAt >= now - 7d`, EMPTY = `durationMs <= 0`. RECENT_7D is computed inside the combine lambda so `now` is fresh on each recompute.

### Bulk-delete guard (§1.5)
- `ClipEditingDelegate` now tracks a rolling 10-second window of delete timestamps. On the 3rd delete within the window, raises a one-shot `BulkUndoPrompt` on `EditorState` and clears the window (so a fresh burst has to rebuild the count — no re-fire storm).
- `EditorScreen` renders a Material 3 `Snackbar` keyed on `BulkUndoPrompt.id` with an **Undo** action (calls `viewModel.undo()`) and an 8-second auto-dismiss. The nonce-based key ensures a second burst after the first banner clears actually re-shows.
- New `EditorViewModel.dismissBulkUndoPrompt()` — idempotent, guarded by `id` equality so a stale dismissal from a previous prompt can't clear a newly-raised one.
- No new Snackbar framework — direct Material 3 component with the existing Mocha colour tokens. Future action-snackbars can reuse the same pattern.

### Notes
- No DB / dependency changes. Three new string resources (`bulk_undo_message/action/dismiss_cd`).
- Filter/sort composition is pure; free-text search applies before the filter chip so "search within subset" works (e.g. "Under 10 s" + search "intro").

## v3.52.0 — Export + Import Polish

Three features from the backlog: extended filename tokens, export pre-flight warnings, and chronological-order import for multi-volume camera footage.

### Extended filename tokens
- `{duration}` — timeline duration as `MMmSSs` (e.g. `01m34s`).
- `{projectFolder}` — directory-safe flavour of the project name (spaces → `_`, strips everything outside `[A-Za-z0-9._-]`). Doesn't collapse to an empty string — falls back to `{name}` when the sanitized form is blank.
- `{clipCount}` — total clip count across all tracks.
- `{sizeMB}` — post-export token, left literal through the encoder. `ExportDelegate.finalizeFilenameSize` replaces it with the rendered file size in MB and renames on disk after `onComplete`. If the rename fails (e.g., FS collision), falls back to the unrenamed file rather than losing the export.
- Two new presets in the Filename Template picker: "Name + Duration" and "Name + Size".
- `media_picker_audio_only` etc. strings / roadmap descriptions updated to advertise the new tokens.

### Export pre-flight warnings
- Three static heuristics surface above the Export button in Output Details:
  - **Long render** — shown when estimated time ≥ 30 min ("Heads up: estimated render time is … Exports run in the background, but plug in to avoid battery drain.")
  - **Large file** — shown when estimated output ≥ 1 GB ("Most share targets reject files this large — consider a Target File Size preset.")
  - **AV1 slow** — shown when codec is AV1 ("AV1 is slow on most Android devices. If file size isn't critical, HEVC encodes much faster.")
- Pure computation against the already-resolved `effectiveConfig`; no state plumbing, no persistence. Surfaces on every recomposition so the warning tracks live changes to the config.
- New `estimateExportBytes(totalDurationMs, config)` helper reused by both `estimateExportSize` (for display) and the warning logic (for threshold comparison).

### Multi-volume sequence ordering on import
- `MediaPicker.sortMediaChronologically(context, uris)` sorts a batch of picked URIs by their resolver `DISPLAY_NAME`, with natural-sort digit padding so camera chapter splits land on the timeline in playback order instead of URI-list order. Wired into both `videoPickerLauncher` (legacy `OpenMultipleDocuments`) and `photoPickerMultiLauncher` (Android 13+ Photo Picker). Non-destructive, silent on no-op batches (`size ≤ 1`).
- Handles the common patterns we see in the wild: GoPro `GH010100.MP4` / `GX010001.MP4`, DJI `DJI_0001.MP4`, Insta360 `VID_20250101_120000_1.MP4`, Samsung `YYYYMMDD_HHMMSS.mp4`, iPhone `IMG_0001.MOV`. The digit-padding approach keeps the comparator cheap (no per-name parser) while matching every format without a bespoke branch.

### Notes
- No DB schema or dependency changes.
- Four new string resources: the three pre-flight warnings + an updated filename-template description.
- Subtitle encoding auto-detect was investigated and dropped from this release — NovaCut has no subtitle IMPORT path, and the existing export already writes UTF-8. Re-evaluate if/when an import path is added (e.g. for the subtitle-aware scene-cut feature at §6.9 in `docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md`).

## v3.51.0 — Post-Audit Follow-ups

Lands the three follow-up items flagged during the v3.50.0 hardening pass.

### Subtitle sidecar sequencing
- `.srt` / `.vtt` / `.ass` subtitle files now written **inside** `ExportDelegate.startExport`'s Transformer `onComplete` block (same pattern as the v3.48 scratchpad sidecar) so they land next to the rendered video with a matching basename and guaranteed ordering before Share / Save-to-Gallery become available. Previously the UI fired `onExportSubtitles` in parallel to `onStartExport`, writing to a separate `externalFilesDir/subtitles/` dir, which meant: (a) the pair didn't travel together through share intents, and (b) a fast Share tap could race the sidecar write. The standalone "Export SRT/VTT" overflow-menu path is unchanged for users who want subtitles without a video export.
- `ExportSheet` Export button no longer fires `onExportSubtitles` — the sidecar is an effect of the video export.

### Audio picker MIME validation
- Legacy `ACTION_OPEN_DOCUMENT` picker's MIME filter is advisory on several devices. When the audio picker returns a URI, `MediaPicker` now verifies `ContentResolver.getType(uri)` starts with `audio/` (or is `application/ogg`) before routing to the AUDIO track. A mis-routed video or image URI used to be added silently to the audio track and fail playback later. Surfaces a user-facing message ("That file isn't audio. Pick a .mp3, .m4a, .wav, .ogg, or .flac.") via the existing permission-message banner.

### Managed-media dir GC on project delete
- New `ProjectAutoSave.collectReferencedSourceUris()` — cheap regex scan over every project's auto-save JSON to extract the `sourceUri` of every Clip and ImageOverlay still referenced by a surviving project. Runs under `saveMutex` so a concurrent save can't corrupt the read. Uses regex rather than full deserializer round-trip so it survives forward-compatible model changes and runs in milliseconds across hundreds of projects.
- New `LocalMediaImport.sweepUnreferencedManagedMedia(context, referencedUris, minAgeMs = 24h)` — mark-and-sweep GC over `filesDir/media/imports/`. Files not in the keep-set and older than 24 h are deleted. The 24 h buffer prevents a racing in-flight import (just written, not yet registered in an auto-save JSON) from being swept. Returns `ManagedMediaSweepResult(filesDeleted, bytesFreed)` for telemetry.
- `ProjectListViewModel.deleteProject` now runs the sweep after DB deletion + recovery clear. Previously the managed-media dir grew monotonically — deleting a project removed the row + recovery file but leaked every imported source clip on disk.

### Notes
- No DB schema changes. No new dependencies. One new string resource.
- `sweepUnreferencedManagedMedia` + `collectReferencedSourceUris` are both new additive APIs; existing call sites untouched.

## v3.50.0 — Hardening Pass (Audit Phase 18)

Staff-level audit + refactor pass across the Codex-refactored tree. Four parallel Explore-agent audits produced ~30 findings; this release lands every Critical and all high-value Highs. False-positive findings (speed-curve-aware effect ID remap on duplicateClip, Timeline NaN guard, FileProvider URI revocation risk for PhotoPicker) were evaluated and explicitly left unchanged with rationale.

### Correctness — speed-curve awareness
- **`Clip.timelineOffsetToSourceMs(timelineOffsetMs)`** (new) — inverse of the forward time mapping used by `durationMs`. Numerical reverse-lookup on the speedCurve (256 linear samples, sub-sample interpolation) when present; falls back to `trimStart + timelineOffset * speed` for constant-speed clips. Clamped to the trim range so callers can never read outside the backing media.
- **Contact-sheet midpoint** (`ContactSheetExporter.kt`) — the thumbnail frame now comes from `clip.timelineOffsetToSourceMs(durationMs/2)` instead of the arithmetic trim midpoint. Ramped clips (e.g. 0.5×→2×) used to grab a misleading frame because the visual midpoint isn't at trim-center. Also: removed an incorrect `bitmap.recycle()` call that would have corrupted `VideoEngine.thumbnailCache` (the cache returns its own bitmap instances; the cache owns their lifecycle).
- **GIF export frame mapping** (`ExportDelegate.kt:234`) — same fix; GIF frames now use `timelineOffsetToSourceMs` so a curved clip exports the correct frames.
- **Split preserves speedCurve** (`ClipEditingDelegate.kt`) — when a clip with a `speedCurve` is split, each half inherits a **remapped sub-range** of the parent curve via the new `SpeedCurve.restrictTo(startFraction, endFraction, clipDurationMs)` helper. Previously both halves kept the full parent curve and misreported speeds across the new trim ranges.
- **`splitPointInSource`** now calls `clip.timelineOffsetToSourceMs(relativePosition)` so the split lands at the correct source frame under curves.

### Stability — data safety
- **AutoSave mutex coverage** (`ProjectAutoSave.kt`) — `loadRecoveryData` and `clearRecoveryData` are both now `suspend` and wrap their full sequence in `saveMutex.withLock`. Previously `clearRecoveryData` was synchronous and not under any mutex, so a delete racing an auto-save could partially clear one of the three files (`.json`, `.tmp`, `.backup`) and leave a ghost recovery behind. `loadRecoveryData` grew the same guard so rename-in-flight between `saveState`'s temp-write and its atomic rename can no longer race a load to see either the pre- or post-rename half.
- **Trim binary-search iteration cap + monotonicity guard** (`TimelineEditing.kt`) — `trimStartForTimelineStart` / `trimEndForTimelineEnd` now cap at 64 iterations (log₂ headroom for any realistic trim range) and early-return if `clip.durationMs` goes to 0 on a non-zero trim range (symptom of corrupt speedCurve with stale NaN handles coerced in-range). Previously the loop could wedge on a non-monotonic cost function.
- **Recovery dialog is modal** (`EditorScreen.kt`) — `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`. `onDismissRequest` used to accept the recovery silently on tap-outside, which destroyed users' deliberate intent to discard. Users must now choose Keep or Discard explicitly.
- **Duplicate-project name uniqueness reads a fresh DAO snapshot** (`ProjectListViewModel.kt`) — computes the `(Copy N)` suffix inside the IO coroutine against `projectDao.getAllProjectsSnapshot()` (new DAO query) rather than the potentially-stale `allProjects.value` StateFlow read on the UI thread. Closes a race where two near-simultaneous duplicate taps could mint the same "(Copy)" name.
- **Undo/redo restores playhead** (`EditorViewModel.kt`) — `UndoAction` gains `playheadMs: Long`. Saved in `saveUndoState`, restored (and clamped to the restored timeline's `totalDurationMs`) in `undo()`, `redo()`, and `jumpToUndoState`. Previously an undo of "delete last clip" left the playhead dangling past the new timeline end.

### Export robustness
- **`resolveTargetSize` on zero duration** pins `bitrateOverride = 500_000` instead of silently falling back to the default quality-based bitrate, which would blow past the user's declared file-size target the moment a clip is added.
- **`gifExportJob` renamed to `nonVideoExportJob`** with a doc comment explaining it holds both GIF and contact-sheet coroutines (and any future non-Transformer export path).
- **Filename-template suffix reserve** (`ExportDelegate.createOutputFile`) — base name budgeted to 58 chars (64 minus a 6-char ` (999)` suffix) so collision retries don't force the base to shrink with every iteration.
- **MediaStore `IS_PENDING` retry** (`ExportDelegate.saveExportedFile`) — up to 3 attempts with 0/100/400 ms backoff before failing. Some devices transiently return 0 rows-updated while a MediaStore indexer run is in flight.
- **`activeExportOutputFile` nulled in outer finally** (`VideoEngine.kt`) — timeout/early-exit paths no longer leave a stale file handle pointer that a subsequent `cancelExport()` would try to delete.

### Media import
- **Atomic rename pattern** (`LocalMediaImport.importUriToManagedMedia`) — writes to a sibling `.partial` file, renames on success, falls back to stream-copy-and-delete if the rename fails cross-filesystem. An interrupted or crashing copy can no longer surface to the timeline as a truncated-but-valid video.
- **Abandoned-partials sweep** — once-per-import, deletes `.partial` files older than 10 minutes in the managed-media dir. Bounded GC so orphans can't accumulate indefinitely.

### Notes
- No schema changes. No new dependencies.
- New DAO query `ProjectDao.getAllProjectsSnapshot()` is additive and non-breaking.
- The four audit reports produced ~30 findings; explicit **false-positive** adjudications in this commit: the Timeline pinch-zoom NaN guard was already fully mitigated (safe variables are used throughout the closure), `duplicateClip` already regenerates effect IDs per-invocation (each linked clip gets its own regeneration), `reorderClip` already invokes `recalculateDuration` for cross-track propagation, and `Project.thumbnailUri` points to the source video (deleting it with the project would destroy user footage). These were left unchanged with rationale documented here.

## v3.49.0 — Contact Sheet Export

Ships roadmap §8.1 — **contact-sheet export** (FrameSnap-inspired). Renders a single PNG with one thumbnail per clip, labeled and arranged in a configurable grid. Great for review-decks, social-media teasers, and archival of long projects as a single scannable image.

### ContactSheetExporter engine
- New `engine/ContactSheetExporter.kt` — single-file Kotlin object rendering clips into an ARGB_8888 bitmap via Canvas, compressed to PNG.
- Layout: columns-wide × `ceil(clips/cols)` rows. Per-cell: 320×180 thumbnail + 28 px caption strip with clip label (source filename, truncated to 24 chars) and formatted duration (`M:SS`). Catppuccin-Mocha background (`#1E1E2E`), Text colour for labels, Subtext0 for durations.
- Thumbnails come from `VideoEngine.extractThumbnail()` at each clip's **midpoint relative to trim** — no extra decode pipeline, existing LRU cache accelerates repeated sheet exports.
- OOM-safe allocation guard on the parent bitmap (explicit `OutOfMemoryError` catch returns false rather than crashing).
- Coroutine-cancellable: `ensureActive()` between cells so the Cancel button in ExportSheet works.

### ExportConfig extensions
- `exportAsContactSheet: Boolean = false` — mode flag.
- `contactSheetColumns: Int = 4` — grid width (UI-clamped to {2, 3, 4, 5, 6}; engine-clamped to [1, 8]).
- Treated as **mutually exclusive** with the other alt-outputs (GIF / audio-only / stems / frame-capture) via the same cascade reset pattern used by the existing toggles.

### ExportSheet UI
- New toggle row "Contact Sheet" (Mocha.Flamingo accent, `Icons.Default.GridView`) in the Special Outputs section.
- When active: column chip row (2/3/4/5/6) + summary pill "Contact sheet · N columns" + export button label swaps to "Export Contact Sheet".
- Delivery Options + audio codec sections auto-hide when in contact-sheet mode (they're not applicable to a still-image output).

### ExportDelegate dispatch
- New contact-sheet branch at the top of `startExport` — runs before the GIF branch and before the main Transformer path. Reuses `gifExportJob` coroutine holder for cancel/progress plumbing.
- Filters tracks to VIDEO + OVERLAY (static clips with visual content); skips AUDIO/TEXT tracks.
- Uses `createOutputFile` with a `_contact` suffix on the preferred filename, routed through the existing filename-template expansion.
- PNG output auto-routes to `Pictures/NovaCut/` via the existing `exportUsesImageCollection` check in `saveToGallery()` — no new MediaStore code.

### Notes
- No DB changes, no new dependencies. Five new string resources.
- Supports aspect-ratio variance across clips — each thumbnail is scaled to 320×180 (16:9 container); portrait footage is letterboxed, not cropped, because thumbnail extraction respects source aspect.
- Known limit: the whole sheet is one bitmap in memory. For a 6-column sheet across 100 clips (16 rows), that's ~13 MB ARGB — comfortably under `largeHeap` budget but would need tiling if users ever push to 500+ clips.

## v3.48.0 — Preset Grouping + Scratchpad Sidecar

Builds on v3.47.0. Ships two small Tier-1 wins: roadmap §1.3 preset discoverability and the deferred §6.4 sidecar export.

### Preset grouping (§1.3)
- **SpeedCurveEditor** — speed presets split into two labeled FlowRows: **Ramps** (Ramp Up, Ramp Down, Pulse) and **Constants** (Slow Mo, 2×, 4×). Previously a single unlabeled row.
- **KeyframeCurveEditor** — dropdown menu divided into three groups with subtle subheaders: **Cinematic** (Ken Burns, Drift, Zoom In/Out), **Fades** (Fade In, Fade Out), **Emphasis** (Pulse, Shake, Spin 360). `HorizontalDivider` + `MaterialTheme.typography.labelSmall` labels between groups. `applyPreset` lambda extracted to keep each group's `DropdownMenuItem` declaration terse.

### Scratchpad notes sidecar export (§6.4 completion)
- When the project carries a non-blank `project.notes`, export now drops a `<basename>.notes.txt` file alongside the rendered video. Runs on `Dispatchers.IO` inside the Transformer `onComplete` callback; failure is logged via `Log.w` but does not affect the export completion state.
- Implementation: `ExportDelegate.startExport` — inline block after the render succeeds, guarded by `currentState.project.notes.isNotBlank()`.

### Notes
- No model or DB changes. No new dependencies. Five new string resources for preset group labels.
- Release build of v3.47.0 failed mid-assembly when the preset-grouping changes were edited in during R8; v3.48.0 rolls up the fix and those polish items into one clean release.

## v3.47.0 — Scratchpad Notes + Visible Recovery Dialog (Wave 2 Port)

Continuation of the cross-project port initiative. Ships two Tier-1 features from [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md): **Scratchpad notes per project (§6.4)** and **Visible crash-recovery dialog (§1.6)**.

### Scratchpad
- New `Project.notes: String = ""` field persisted in Room. DB schema bumped to v6 with `MIGRATION_5_6` (`ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''`).
- New `ScratchpadSheet` Composable (`ui/editor/ScratchpadSheet.kt`) — free-form notes editor with 180–360 dp OutlinedTextField, yellow Mocha accent, 750 ms-debounced auto-persist via `LaunchedEffect(text)` + `delay()` to avoid hammering Room on every keystroke.
- Wired into EditorScreen overflow menu as "Scratchpad Notes" (Icons.AutoMirrored.Filled.Notes).
- `EditorViewModel.showScratchpad()` / `hideScratchpad()` / `updateProjectNotes(notes)` + new `PanelId.SCRATCHPAD`. Uses existing `saveProject()` for Room persistence.

### Recovery dialog
- Project open flow in `EditorViewModel` now opens a `PanelId.RECOVERY_DIALOG` whenever `autoSave.loadRecoveryData()` returned non-empty tracks/overlays. Previously the restore happened silently — users had no indication that their project was recovered from a prior-session crash.
- `EditorScreen` renders a Material 3 `AlertDialog` for `RECOVERY_DIALOG`:
  - **Keep recovered** (default) — dismisses the dialog; recovered state remains applied and continues to auto-save normally.
  - **Discard** — calls `autoSave.clearRecoveryData(projectId)`; the recovery file is removed so the user can reload the Room-persisted baseline by closing and reopening the project.
- `EditorViewModel.dismissRecoveryDialog(recover: Boolean)` handles both paths.

### Notes
- Two new PanelId entries: `SCRATCHPAD`, `RECOVERY_DIALOG`.
- New strings for scratchpad + recovery dialog in `strings.xml`.
- No new dependencies.

## v3.46.0 — Cross-Project Feature Port (VideoCrush / FrameSnap / GifText)

Features ported from sibling projects in the Z:\repos tree.

### Export
- **Target file size presets** (VideoCrush) — New "Target File Size" section in ExportSheet with preset chips for Discord (8/25/100 MB), Gmail (25 MB), Telegram (50 MB), WhatsApp (16 MB), Twitter (512 MB). Picking a preset computes the video bitrate from `(targetBytes * 8 * 1000 / durationMs) - audioBitrate`, with 2% headroom reserved for mp4 container overhead. Bitrate is clamped to `[500 kbps, 150 Mbps]` and resolved at export dispatch time in `ExportDelegate.startExport` via `ExportConfig.resolveTargetSize(totalDurationMs)` so duration changes after selection are honored automatically.
  - `ExportConfig` gains `targetSizeBytes: Long?` (preset marker) + `bitrateOverride: Int?` (resolved value). `videoBitrate` getter returns `bitrateOverride ?: defaultVideoBitrate` so all downstream consumers (VideoEngine, ExportSheet size/bitrate display) automatically reflect the target.
- **Pre-flight export ETA** — Output Details card now shows "Est. time: Xm Ys" above the ready-to-export button, derived from timeline duration × resolution-pixel-ratio × codec factor (H.264=1.0, HEVC=1.6, AV1=2.4, VP9=1.9) × fps factor. Base calibration: 1080p30 H.264 ≈ 1.17× real-time on mid-range Android. Pure display — no behavior change to the encoder.
- **Filename templates with tokens** (AlphaCut / FrameSnap) — New "Filename Template" section with five preset patterns: `{name}`, `{name}_{date}`, `{name}_{date}_{time}`, `{name}_{res}_{fps}`, `{name}_{preset}`. Tokens: `{name} {date} {time} {res} {codec} {fps} {preset}`. Expanded in `ExportDelegate.createOutputFile` via `applyFilenameTemplate` before passing through `sanitizeFileName`. Collision-free numbering (`Name (2).mp4`, `(3).mp4`…) still runs on top of the expanded template.

### Text overlays
- **Meme-style text templates** (GifText) — Six new entries in `builtInTextTemplates` under the SOCIAL category: Impact Meme (top/bottom text, stroke, condensed), TikTok Caption (black-on-white with slide-up), Reels Hook (glow + bounce "WAIT FOR IT…"), POV Meme (typewriter POV overlay), Neon Glow (blur-in "VIBES"), Word Burst (big single-word elastic pop). All wired to existing `TextAnimation` enum values — no new rendering code required.

### Notes
- No new dependencies. All changes are local to `ExportConfig`, `ExportSheet`, `ExportDelegate`, `TextTemplateGallery`, and `strings.xml`.
- Target-size resolution runs at dispatch time, so batch-export items with a `targetSizeBytes` set get their bitrate re-derived per timeline duration (safe for the same timeline with different target-size preset queues).

## v3.45.0 — Audit Phase 17: GL Attrib Guards, DSP NaN Flush, Gesture Robustness

### GL pipeline safety
- **LutEngine attribute location unchecked** ([LutEngine.kt#L221](app/src/main/java/com/novacut/editor/engine/LutEngine.kt#L221)) — `glGetAttribLocation` returns `-1` when the driver's shader compiler optimizes an attribute away or the linker renames it. Calling `glEnableVertexAttribArray(-1)` and `glVertexAttribPointer(-1, ...)` is undefined behavior: some drivers silently no-op, others corrupt GL state so the LUT render pass outputs black. Now guards both sites with `if (p >= 0)` matching the pattern already used in `ShaderEffect.kt`.
- **SegmentationGlEffect attribute location unchecked** ([SegmentationGlEffect.kt#L262](app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationGlEffect.kt#L262)) — Same pattern. User-reported symptom would be segmented frames rendering fully black during export on certain device GPUs while preview looks correct.

### DSP / audio integrity
- **Reverb feedback NaN / denormal runaway** ([AudioEffectsEngine.kt#L292](app/src/main/java/com/novacut/editor/engine/AudioEffectsEngine.kt#L292)) — The 4-tap comb filter writes `mono + delayed * feedback * damping` back into the delay buffer with no bound. With `feedback = decay * 0.3f` (~0.6 at default) and a DC-biased or sustained-tone input, the delay lines either saturate into `NaN` (via `Inf * anything`) or sink into denormal floats that tank CPU by 10-100× on ARM. Now each stored sample is clamped to `[-4, 4]`, non-finite values replaced with 0, and sub-1e-20 magnitudes flushed to zero. One pathological clip can no longer poison the reverb state for the rest of the render.
- **WhisperMel Slaney-normalization divide-by-zero** ([WhisperMel.kt#L188](app/src/main/java/com/novacut/editor/engine/whisper/WhisperMel.kt#L188)) — `enorm = 2.0 / (hzPoints[m+2] - hzPoints[m])`. On very-short-audio edge cases or low-sample-rate inputs, adjacent mel points can collapse, denominator → 0, `enorm` → `Infinity`, then the multiply on line 191 poisons the filter bank with `NaN`. Whisper transcription silently produced zero-confidence garbage with no user-visible error. Clamped denominator to `>= 1e-8`.

### Gesture / UI robustness
- **Timeline pinch-zoom NaN propagation** ([Timeline.kt#L654](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt#L654)) — `detectTransformGestures` can emit NaN `zoom`/`pan`/`centroid` values on malformed multi-touch events. `coerceIn` does NOT clamp NaN (all NaN comparisons return false), so `newZoom` became NaN, division produced `Infinity`, and scroll offset was permanently corrupted — timeline unusable until the activity was rebuilt. Now `isFinite()`-guards each gesture input and clamps `oldPpm`/`newPpm` denominators to `>= 0.0001f`.
- **DrawingOverlayPanel touch-path NaN abort** ([DrawingOverlayPanel.kt#L171](app/src/main/java/com/novacut/editor/ui/editor/DrawingOverlayPanel.kt#L171)) — A single non-finite touch coordinate (sensor error, gesture-library edge case) in the draw gesture silently aborted the Compose `Path` rendering for the entire drawing layer — every subsequent stroke invisible until editor reload. Matches the v3.44 deserialization-side fix; now also filtered at input time. `onDragStart` / `onDrag` both check `isFinite()`.

### Audit findings verified as already-correct (false positives this round)
- **Timeline ruler dual `pointerInput` conflict** — Compose's gesture-winner resolution already handles tap-then-drag cooperation correctly; both `detectTapGestures` and `detectDragGestures` on separate `pointerInput` blocks is an idiomatic pattern and field-tested.
- **PreviewPanel `DisposableEffect(engine)` listener leak** — `engine` is a Hilt `@Singleton` so never swaps; the captured-player-reuse pattern in the current code is already correct.
- **AiToolsDelegate concurrent tool race** — `aiJob?.cancel()` runs before the new job launches, and the `finally` block's `if (aiJob === thisJob)` identity check already protects state from stale cancelled jobs.
- **`autoColorCorrect` MediaMetadataRetriever leak on early return** — `retriever.release()` lives in the outer `finally`; early returns inside the `try` still trigger it.
- **`generateEnergyCaptions` divide-by-zero on silence** — Guarded at line 208 with `if (maxEnergy < 0.001f) return@withContext emptyList()` before any of the `/ maxEnergy` sites execute.
- **`LoudnessEngine` K-weighting alpha overflow** — `safeSampleRate` is coerced to `>= 1`, so `hpAlpha` lands in `[0, 1]` for every realistic input.

## v3.44.0 — Audit Phase 16: Persistence NaN Guards, GIF Overflow, Export Races

### Persistence hardening
- **ColorGrade / HslQualifier NaN propagation** ([ProjectAutoSave.kt#L962](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L962)) — All 22 `liftR/G/B`, `gammaR/G/B`, `gainR/G/B`, `offsetR/G/B`, `lutIntensity`, and the 10 HSL qualifier fields called `.toFloat()` on raw `optDouble` values. A compromised recovery file or manually-edited JSON with `NaN`/`Infinity` propagated directly into the color matrix, turning the entire clip black on playback and export (RgbMatrix multiplies NaN across every channel). New `safeFloat()` helper coerces each field to its identity default when non-finite, plus `lutIntensity` clamped to `[0,1]`. Same pattern applied to `CurvePoint` bezier handles (were previously trusted raw, corrupting color curves on bad input).
- **ImageOverlay `require(startTimeMs < endTimeMs)` dropped overlay on recovery** ([ProjectAutoSave.kt#L309](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L309)) — Corrupt JSON with equal or inverted time bounds threw in the constructor, the try/catch silently swallowed the exception, and the whole overlay vanished — silent data loss on every recovery cycle. Now coerces `endTimeMs = max(startTimeMs + 1, rawEnd)` before constructing, with `isFinite()` guards on `positionX/Y`, `scale`, `rotation`, `opacity` so NaN coordinates can't corrupt placement math.
- **DrawingPath NaN coordinates break Compose Canvas** ([ProjectAutoSave.kt#L346](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L346)) — A single non-finite `x` or `y` in a drawing path caused `drawPath` to silently abort rendering for the entire drawing layer (every subsequent path invisible). Now filters non-finite points per path, rejects paths with <2 remaining points, and clamps `strokeWidth` to `[0.5, 64]dp`.
- **Caption word timings escaped caption bounds** ([ProjectAutoSave.kt#L1105](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L1105)) — `CaptionWord.endTimeMs > Caption.endTimeMs` silently broke the karaoke-highlight renderer which assumes sorted, in-bounds words. Now filters words that start past the caption window and clamps `endTimeMs` to the caption's end. `CaptionStyle.fontSize` / `positionY` get NaN guards plus `positionY ∈ [0,1]`.
- **copyAutoSave read/write race** ([ProjectAutoSave.kt#L109](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L109)) — `saveMutex` was released between reading the source JSON and writing the renamed copy. A concurrent auto-save of the source project during that gap let the duplicate capture stale data (source would have newer edits than the "duplicate"). Now holds the mutex across the full read→mutate→write sequence.

### Export
- **GIF export frame-count `toInt()` truncation** ([ExportDelegate.kt#L109](app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt#L109)) — `(totalDurationMs / frameIntervalMs).toInt().coerceIn(1, 300)` narrowed to `Int` before clamping. A pathologically long `totalDurationMs` (duration-math bug or corrupt state) divided by a 1 ms interval exceeded `Int.MAX_VALUE`, `.toInt()` wrapped negative, and `coerceIn` then clamped to 1 — silently skipping export instead of capping at 300 frames. Now clamps in `Long` space before narrowing: `.coerceIn(1L, 300L).toInt()`.

### Diagnostics
- **ProjectAutoSave.release() silently swallowed temp-file sweep failures** ([ProjectAutoSave.kt#L138](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L138)) — Added `onFailure { Log.w(...) }` so an I/O or permission fault during orphan cleanup is surfaced in logcat rather than silently accumulating `.tmp` files across process lifetimes.

## v3.43.0 — Audit Phase 15: Version Drift, FCPXML Rounding, Lottie GL Safety

### Build integrity
- **`NovaCutApp.VERSION` drifted three releases** ([NovaCutApp.kt#L24](app/src/main/java/com/novacut/editor/NovaCutApp.kt#L24)) — The `VERSION` constant was hard-coded to `"v3.39.0"` while the gradle `versionName` was `"3.42.0"`. Model downloads advertised the stale version in their `User-Agent`, the about dialog misreported the build, and any future crash-reporting integration would have mis-tagged reports. Now sourced from `BuildConfig.VERSION_NAME` so it can't drift again; added `buildConfig = true` to `buildFeatures` to enable the generated field.

### FCPXML / OTIO round-trip
- **`msToFcpxmlTime` truncation drift** ([TimelineExchangeEngine.kt#L548](app/src/main/java/com/novacut/editor/engine/TimelineExchangeEngine.kt#L548)) — The sibling `msToFrames` / `framesToMs` helpers were already using round-to-nearest (fixed in phase 9), but the FCPXML-specific `msToFcpxmlTime` still truncated, so a 33 ms offset at 30 fps emitted `0/30s` instead of `1/30s`. Cumulative drift on a long timeline misaligned clip offsets and asset start/duration when round-tripped through Final Cut Pro or DaVinci Resolve. Now symmetric with the other two: `(ms * frameRate + 500L) / 1000L`, plus a `frameRate <= 0` guard that emits a safe fallback token instead of a divide.

### GL resource safety
- **Lottie texture upload bitmap leak on GL exception** ([LottieTemplateEngine.kt#L138](app/src/main/java/com/novacut/editor/engine/LottieTemplateEngine.kt#L138)) — `renderFrameToTexture` only recycled its bitmap on the happy path and on the `glGenTextures == 0` guard. If `GLUtils.texImage2D` threw (OOM, context lost, bad format), both the bitmap and the freshly-generated texture ID leaked. Animated-title exports push tens of bitmaps per second through this function; one bad frame per export would still be fine, but any repeated driver failure cascaded into a visible OOM. Now wrapped in try/catch that deletes the texture and recycles the bitmap before re-throwing.

### Audit findings verified as already-correct (false positives this round)
- **TimelineExchangeEngine other timecode math** — `msToFrames` / `framesToMs` already use the rounding form with `frameRate <= 0` guards from phase 9.
- **FirstRunTutorial `tutorialStepDefs[step]` out-of-bounds** — `currentStep++` only runs when `!isLastStep`, so the index stays in `0..size-1` for a hard-coded non-empty list. Safe.
- **RenderPreviewSheet ratio divide** — The `if (summary.totalDurationMs > 0L)` guard already wraps the divide; `segments.isEmpty()` doesn't influence the computation.
- **SnapshotHistoryPanel `SimpleDateFormat` thread-safety** — The `remember`-d formatter is only consumed from the composable's recompose pass, which runs on the main thread; it never crosses to a background coroutine.
- **`-dontwarn org.bouncycastle.**` "unused dependency"** — OkHttp references bouncycastle / conscrypt / openjsse as *optional* TLS providers; the warning suppression is needed at link-time even though the classes aren't packaged.
- **EffectShareEngine LUT filename collision** — Effect exports intentionally reference LUTs by filename only (they don't embed the binary); cross-project namespacing would break the whole sharing feature. Documented as a known limitation of the export format.

## v3.42.0 — Audit Phase 14: Speed Curve NaN, Deserialization Bounds, Graph Cycles, Flow Churn

### Speed curve math
- **NaN in harmonic mean → zero-duration clip** ([Project.kt#L143](app/src/main/java/com/novacut/editor/model/Project.kt#L143)) — `coerceAtLeast(0.01f)` preserves NaN (comparisons with NaN are false, so the branch that would clamp never fires), so one NaN speed sample poisoned `sumReciprocal`, the harmonic mean returned NaN, and `Clip.durationMs` silently collapsed to 0 — the clip disappeared from the timeline with no error surface. Now explicitly checks `isFinite()` on both the per-sample speed and the final `sumReciprocal` and falls back to the static `speed` field.

### Deserialization hardening
- **SpeedCurve control-point bounds** ([ProjectAutoSave.kt#L1002](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt#L1002)) — The auto-save parser accepted any `Double` for `position`/`speed`/`handleInY`/`handleOutY`. A corrupted file with `speed = -0.5` or `position = 5.0` passed straight into bezier evaluation and the harmonic-mean divide. Now all four fields are `isFinite()`-checked and clamped to sensible ranges (`position ∈ [0,1]`, speeds ∈ `[0.01, 100]`), matching the UI-side invariants.
- **Self-referencing `linkedClipId`** ([ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt)) — The orphaned-reference cleanup on load checked `linkedClipId !in allClipIds` but not `linkedClipId == clip.id`. A clip linking to itself would create an infinite loop in any traversal that followed the chain (slip-link propagation, group moves). Now both conditions null the link.
- **Compound clip serialization cycle** ([ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt)) — `serializeClip` recursed into `clip.compoundClips` without a depth guard. A corrupted graph where a compound clip eventually cycled back to itself would `StackOverflowError` the whole auto-save coroutine (and every subsequent save, since the state stays corrupted). Added a depth counter (limit 8) that emits a shallow representation and a WARN log above the threshold.

### Data layer performance
- **Project list Flow re-emits on unrelated updates** ([ProjectListViewModel.kt](app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt)) — Room's `Flow<List<Project>>` emits on every write to the `projects` table, even when the query result is bit-identical. The downstream combined flow then forced the grid (and every project card, each with a `VideoFrameDecoder` render) to recompose. Added `.distinctUntilChanged()` on the DAO flow upstream of the combine.

### Settings robustness
- **SettingsRepository over-broad catches** ([SettingsRepository.kt#L78](app/src/main/java/com/novacut/editor/engine/SettingsRepository.kt#L78)) — Three `enumValueOf` sites caught `Exception`, which masks real defects (OOM wrapped errors, reflection failures). Narrowed to `IllegalArgumentException`, matching the style already used in the write path.

### Audit findings verified as already-correct (false positives this round)
- **`settings_show_waveforms_desc` / `settings_snap_beat_desc` / `settings_snap_markers_desc` missing** — All three (and the default-track-height description) are defined in `strings.xml:1131-1138`. `R` references resolve.
- **`Project.aspectRatio` / `frameRate` / `resolution` not serialized** — These fields live on the Room `@Entity Project`, not on `AutoSaveState`. They're persisted by Room's `projectDao.updateProject()` call path; the auto-save JSON is deliberately scoped to track/clip/overlay state.
- **`KeyframeEngine` Newton-Raphson slope = 0** — The `if (abs(currentSlope) < 1e-5f) break` line comes **before** the division that would produce `Inf`, not after. No divide-by-zero possible.
- **`evaluateCubicBezierTime` return > 1** — The function exposes `cp1y/cp2y.coerceIn(-1f, 2f)` on purpose for spring/back easing overshoot; clamping here would remove a feature, not fix a bug.
- **`AudioEngine.extractWaveform` "silent audio renders at max height"** — `maxAmplitude` starts at `1f` and is only overwritten when a sample exceeds it; for all-zero PCM the normalization is `0f / 1f = 0f`, not `1f`. Agent misread the init.
- **`Caption.endTimeMs` silent repair** — The auto-fix-on-invert behavior is correct; losing a caption because one bad `endTimeMs` is worse than nudging it. Not worth adding noise-level logging for.

## v3.41.0 — Audit Phase 13: GL Hardening, Shader Input Bounds, Volume Envelope Safety

### GL / Shader pipeline
- **LUT intensity NaN poisoning** ([LutEngine.kt](app/src/main/java/com/novacut/editor/engine/LutEngine.kt)) — `LutGlEffect` accepted any `Float` for `intensity` and fed it directly to `glUniform1f`. A NaN intensity (from a corrupted keyframe, a divide-by-zero in the UI slider path, etc.) poisons the `mix(original, graded, uIntensity)` step in the shader and produces NaN pixels across the entire frame. Now clamped to `[0, 1]` with a finite-check fallback at the engine boundary.
- **LUT 3D texture exceeds device capability** ([LutEngine.kt](app/src/main/java/com/novacut/editor/engine/LutEngine.kt)) — Parser caps LUT size at 256, but GLES 3.0 only guarantees `GL_MAX_3D_TEXTURE_SIZE >= 256`. Some lower-tier GPUs report smaller values; `glTexImage3D` then silently fails and `drawFrame` draws black frames with no error surface. Now queries `GL_MAX_3D_TEXTURE_SIZE` at setup and throws a clear `RuntimeException` if the LUT won't fit, letting the error bubble to the UI with a usable message.
- **Segmentation mask texture never initialized** ([SegmentationGlEffect.kt](app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationGlEffect.kt)) — `setupGl()` generated the mask texture handle but never defined its storage. On drivers that require `glTexImage2D` to mark a texture "complete", the first frame's sampler read returned zero (fully masked-out / black output) or hard-failed the draw entirely. Now seeded with a 1×1 `R8` opaque pixel and configured with linear + clamp-to-edge so the first frame is safe regardless of how fast or slow ML inference arrives.
- **Chroma key input bounds** ([ShaderEffect.kt](app/src/main/java/com/novacut/editor/engine/ShaderEffect.kt)) — `smoothstep(uThreshold, uThreshold + uSmoothing, dist)` with `uSmoothing == 0` has undefined GLSL behavior (edge0 == edge1) and produces NaN alpha on some drivers. Also clamped `uKeyR/G/B`, `uThreshold`, `uSpill` to `[0, 1]` — out-of-range RGB values were producing wild keying results when a param slider overshot during a fast drag.

### Timeline
- **Volume envelope divide-by-zero on zero-duration clip** ([Timeline.kt:1046](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt#L1046)) — The audio volume envelope path renderer gated on `volumeKfs.size >= 2` but not on `clip.durationMs > 0`. A pathological zero-duration audio clip (possible via rapid trim collision) then hit `kf.timeOffsetMs.toFloat() / clip.durationMs` = `Infinity`, and `drawCircle(... Offset(Infinity, ...))` ANR'd the render thread on some devices. Now guards both conditions.

### Stub engine defensive tightening
- **SmartReframeEngine EMA divergence** ([SmartReframeEngine.kt](app/src/main/java/com/novacut/editor/engine/SmartReframeEngine.kt)) — `smoothCropTrajectory` accepted any `alpha`. An `alpha > 1` overshoots the target and produces an oscillating/divergent EMA, and `NaN` corrupts every subsequent element via the feedback term. Now coerced to `[0, 1]` with NaN fallback to 0.08, and the single-element edge case is returned unchanged (previously it allocated a new list pointlessly).

### Audit findings verified as already-correct (false positives this round)
- **ChromaKey shader division-by-zero on `uSpill = 0`** — The shader uses `max(r, b) * (1.0 - uSpill * 0.5)` (multiplicative), not division. No DBZ path exists.
- **WhisperEngine encoder/decoder tensor leak on empty output** — `runEncoder` and `runDecoder` use `firstOrNull()?.value as? OnnxTensor`, not `first()`, and both have `finally` blocks that close `results`/`inputTensor`/`idTensor`. No leak.
- **VideoEngine `SpeedProvider` boundary math** — `coerceIn(0.1f, 100f)` is applied to the *returned* value, which is the correct place; the callee's curve evaluation result cannot escape the clamp.
- **MainActivity intent scheme validation** — Already restricted to `content://` + `video/*` MIME + `openAssetFileDescriptor` read-test in try/catch. Authority whitelisting would reject legitimate third-party content providers (MediaStore URIs come from system providers, not the app).
- **Volume keyframe dot at `clip.keyframes` path** — Already guarded by `if (clipDuration <= 0) return@Canvas` above the divide.
- **`Clip` min-duration invariant** — `require(trimEndMs >= trimStartMs)` permits equality by design; the UI layer enforces the practical 100 ms floor in trim handlers, which is the right layer for that policy (lets non-visual markers / audio cue clips exist).
- **TapSegmentEngine confidence bounds** — Data class is only constructed by unimplemented stub paths; adding `require()` here would throw at runtime if a future backend produced a `0.99999999` edge value due to float drift. Deferred until the engine is wired.

## v3.40.0 — Audit Phase 12: Export Progress, GIF Safety, AI Job Race, DSP NaN Guards

### Export pipeline
- **Export progress notification stuck between runs** ([ExportService.kt](app/src/main/java/com/novacut/editor/engine/ExportService.kt)) — `lastNotifiedProgress` persisted across exports, so the throttle `progress - lastNotifiedProgress < 2` silently dropped every update from the second export until it caught up past the previous run's value. The progress bar sat frozen at 99% for the entire second export. Now reset on each `startObservingExport()`, and the throttle is one-sided so backward jumps always publish.
- **GIF export zero-height crash** ([ExportDelegate.kt#L120](app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt#L120)) — `createScaledBitmap(bitmap, maxWidth, 0, true)` throws `IllegalArgumentException` and aborts the whole GIF export on any frame where `bitmap.height * ratio` rounded to `0` (very short source videos or 1-pixel-tall thumbnails). Now bitmaps are skipped when width/height is ≤ 0, and the computed height is floored at 1.
- **ExportTextOverlay NaN poisoning** ([ExportTextOverlay.kt](app/src/main/java/com/novacut/editor/engine/ExportTextOverlay.kt)) — A corrupted keyframe feeding `NaN` into `positionX/Y/scale/rotation` would produce a NaN-poisoned transform matrix that the GL pipeline rejects mid-export with an opaque "framework error". Added `isFinite` guard that silently parks the overlay off-screen for one frame rather than aborting the render.
- **ExportSheet blank error body** ([ExportSheet.kt#L295](app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt#L295)) — An empty-string `errorMessage` (non-null but blank) rendered the error card with a missing body. Now falls back to the localized generic error when blank.

### ViewModel / state correctness
- **AI tool cancellation race** ([AiToolsDelegate.kt](app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt)) — Tapping a second AI tool while another was running published the new `aiProcessingTool` state **before** cancelling the old job; the old job's `finally` block then fired asynchronously and cleared the state to `null`, hiding the progress indicator for the active tool. Now cancels the previous job first, and the `finally` only clears state when it is still the active job.
- **detectBeats missing undo** ([AudioMixerDelegate.kt](app/src/main/java/com/novacut/editor/ui/editor/AudioMixerDelegate.kt)) — Auto beat detection replaced manually-tapped beat markers without saving undo state, so a user who ran auto-detect to "check" results and got bad ones had no way back. Now records undo before the destructive replacement.

### DSP correctness
- **Biquad Q → NaN** ([AudioEffectsEngine.kt](app/src/main/java/com/novacut/editor/engine/AudioEffectsEngine.kt)) — `lowPassCoeffs` / `highPassCoeffs` / `peakEqCoeffs` divide `sin(w0) / (2 * q)`; a `q == 0` slider value (or corrupted parameter) produced `alpha = ±Infinity`, which poisoned every coefficient with NaN and — because the IIR state machine feeds outputs back into itself — permanently corrupted every subsequent sample for the rest of the buffer. Q now floored at `0.01f` at the coefficient source.
- **LoudnessEngine short-clip short-term max** ([LoudnessEngine.kt](app/src/main/java/com/novacut/editor/engine/LoudnessEngine.kt)) — For clips shorter than ~3 seconds we have fewer than 8 loudness blocks; the `for (i in 0..size - 8)` loop then iterates over an empty range (negative upper bound becomes an empty `IntRange`), leaving `shortTermMaxLufs = -70f` regardless of actual loudness. Voiceovers and SFX showed up as silent in the loudness meter. Now falls back to `momentaryMax` when there aren't enough blocks for the 3 s window. Also coerced `sampleRate` in the K-weighting filter so a corrupt `sampleRate = 0` can't produce `Infinity/NaN` filter state.

### Persistence hygiene
- **Auto-save temp file orphans** ([ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt)) — `release()` cancelled the save scope but didn't sweep any `.tmp` files left by interrupted writes; across many app lifetimes these can accumulate in `filesDir/autosave/`. Now `release()` sweeps `*.tmp` after cancelling the scope.

### Audit findings verified as already-correct (false positives this round)
- **PreviewPanel `DisposableEffect` null player** — `VideoEngine.getPlayer()` returns a non-nullable `ExoPlayer` that is lazily instantiated; `addListener(listener)` cannot receive null.
- **`EditorViewModel.setClipLabel` undefined `rebuildTimeline()`** — `rebuildTimeline()` exists on the ViewModel as a thin alias for `rebuildPlayerTimeline()`; no missing symbol.
- **`ColorGradingDelegate.setClipLut` undo-before-null-check** — `saveUndoState` is already called **after** the `getSelectedClip() ?: return` guard.
- **`ExportService` `lastNotifiedProgress` non-volatile** — The collect pipeline is pinned to `Dispatchers.Main.immediate` and `updateProgress` runs only from that flow, so the field is single-threaded.
- **`VoiceoverRecorderEngine` state race** — `startRecording` / `stopRecording` / `release` are all `@Synchronized`.
- **`HttpURLConnection.disconnect()` missing in download engines** — All three (Whisper, Segmentation, Inpainting) already call `disconnect()` in `finally` from prior audit phases.
- **ProjectAutoSave `beatMarkers` round-trip data loss** — Omitting the field on empty and defaulting to empty on read is symmetric; non-empty lists are always written and read back faithfully.

## v3.39.0 — Audit Phase 11: Speed Curve Duration Math, Snap Threshold Floor, Tool Grid Recomposition

### Math correctness
- **Variable-speed clip duration** ([Project.kt#L143-L162](app/src/main/java/com/novacut/editor/model/Project.kt#L143)) — `Clip.durationMs` was averaging the speed curve arithmetically and dividing trim range by the result. Wall-clock duration is the integral of `dt_source / speed(t)`, so the *harmonic* mean of speed is what scales trim range to real time. A clip with the first half at 0.5x and the second half at 2.0x (true duration = 1.25× source) was reporting 0.8× source — the timeline displayed it 56% shorter than it would actually play, and clip stacking math used the wrong endpoint. Now sums reciprocals: `samples / sum(1/speed)`.

### Timeline UX
- **Snap threshold floor at extreme zoom** ([Timeline.kt#L1342](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt#L1342)) — `snapThresholdMs = (8.dp.toPx() / pixelsPerMs).toLong()` rounded to `0L` once `pixelsPerMs > snapPx` (very high zoom-in), which silently disabled magnetic snapping for fine-grained edits — the worst time to lose snapping. Now floored at `1L` so the snap window is always at least one millisecond wide.

### Compose performance
- **Tool sub-menu grid skipping** ([ToolPanel.kt#L498-L508](app/src/main/java/com/novacut/editor/ui/editor/ToolPanel.kt#L498)) — `SubMenuGrid` items were composing `Modifier.then(if (!isDisabled) Modifier.clickable { ... } else Modifier)` per recomposition. The conditional `then(...)` produced a fresh modifier chain (and a fresh click lambda) on every parent recompose, defeating Compose's modifier reuse / clickable click-listener stability. Switched to the standard `Modifier.clickable(enabled = !isDisabled)` form and replaced the parallel `then(Modifier.alpha(...))` with a direct `alpha()` call. Tool grid no longer re-allocates click semantics every time the bottom-tool area re-renders.

### Audit findings verified as already-correct (false positives this round)
- **EffectBuilder anchor-Y sign flip** — pre-anchor `(-ax, +ay)` and post-anchor `(+ax, -ay)` look inconsistent at first glance but are actually internally consistent: the model exposes Y-up coordinates while `android.graphics.Matrix` is Y-down, so the `+ay`/`-ay` pair correctly translates the anchor to origin and back in matrix space, matching the `(+px, -py)` Y-flip on the position translation.
- **LoudnessEngine short-term loop bounds** — `0..size - shortTermBlocks` (inclusive) with `subList(i, i + shortTermBlocks)` is in-range because `subList`'s `toIndex` is exclusive; the last iteration takes `subList(size - shortTermBlocks, size)`.
- **EdlExporter timecode overflow** — `ms` is `Long`, so `ms * fps + 500` auto-promotes to Long; no Int overflow possible.
- **NoiseReductionEngine soft-gate energy init** — `energy` is initialized at the top of the gate branch (`var energy = 0f`); the `/1f` divisor is cosmetic but mathematically harmless.
- **VideoEngine listener cleanup** — `VideoEngine` is `@Singleton` so the captured `StateFlow` references in the Transformer listener live for app lifetime regardless; no leak.
- **TemplateManager path traversal** — `normalizeImportedTemplate` already mints a fresh UUID when `sanitizedId != template.id`, so `../../etc/passwd` → `etcpasswd` → mismatch → UUID; the path-traversal vector is closed.
- **MediaPicker `Uri.fromFile` exposure** — the file:// URI is consumed only by ExoPlayer/Coil internally and never crosses an app boundary via Intent; no `FileUriExposedException` risk in current code paths.

## v3.38.0 — Audit Phase 9: FCPXML Escaping, LUT Bounds, Settings Slider Debounce, Template Path Traversal & OTIO Rounding

### Format / parser correctness
- **FCPXML XML escaping** ([TimelineExchangeEngine.kt](app/src/main/java/com/novacut/editor/engine/TimelineExchangeEngine.kt)) — Project name and clip names were interpolated directly into FCPXML attributes via Kotlin string templates with no escaping. A clip named `M&M's <draft>` produced malformed FCPXML that DaVinci Resolve / Final Cut imports refused. Added `xmlEscape` helper covering `&`, `<`, `>`, `"`, `'` and applied to every name/uri interpolation.
- **OTIO/FCPXML timestamp rounding** — `msToFrames` and `framesToMs` were truncating instead of rounding-to-nearest. 1 ms at 30 fps became 0 frames, accumulating drift on long timelines and breaking round-trip precision. Now uses `(ms * frameRate + 500L) / 1000L` rounding (and the symmetric form for the reverse).
- **LUT size bounds** ([LutEngine.kt](app/src/main/java/com/novacut/editor/engine/LutEngine.kt)) — `parseCube` and `parse3dl` accepted any integer for `LUT_3D_SIZE`. A malicious `.cube` declaring `LUT_3D_SIZE 1000` would attempt a `1000³ × 3 = 3 billion` float allocation (~12 GB) and OOM the app before the row-count validation could reject it. Now bounded to `[2, 256]` (covers all real-world LUTs: 17, 32, 33, 64).
- **LUT value clamping** — Both `.cube` and `.3dl` parsers now `coerceIn(0f, 1f)` each color channel. Out-of-range entries previously produced wild GPU colors (negative wraps, >1 blows out highlights) on shaders that assume normalized inputs.

### Security / template safety
- **TemplateManager template-id sanitization** — Imported template ids were used directly as filename via `File(templateDir, "$id.json")`. A hostile `.novacut-template` with id `../../etc/passwd` would land outside the template directory (path traversal). `normalizeImportedTemplate` now sanitizes ids to `[A-Za-z0-9_-]` and mints a fresh UUID when sanitization changes the value.

### Settings UX
- **Settings slider disk thrash fix** ([SettingsScreen.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt)) — `SettingsSlider` was calling `viewModel.set...(it)` (which writes to DataStore) on every drag tick (~60 events/sec). Auto-save-interval drag could fire 100+ DataStore writes in 2 seconds. Refactored to drive a local `mutableStateOf` during drag and only commit via `onValueChangeFinished`. The settings value still flows from DataStore Flow on first composition (and any external change).

### Audit findings verified as already-correct (false positives this round)
- **`ProjectArchive` zip-bomb compression ratio** — `copyWithLimit` already enforces the 4 GB total cap incrementally as bytes are read; an entry that would decompress past the cap throws mid-read, not after. The cap is reasonable.
- **`SpeedCurveEditor` Y-clamp on drag** — outer `coerceIn(minSpeed, maxSpeed)` already bounds the final speed value even when intermediate Y math is negative; `size.height = 0` (the only NaN path) doesn't fire pointer events anyway.
- **`KeyframeCurveEditor` selection by data-class equality** — Kotlin data class `equals` compares all fields, so `keyframe == selectedKeyframe` works as intended for the editor's purposes; only matters if two keyframes have identical fields, which the deserialize-time `distinctBy { (timeOffsetMs, property) }` prevents.
- **Tier 3+ engine resource leaks (Stabilization, FrameInterp, Style, Upscale, etc.)** — confirmed all stubs return `null` cleanly with `Log.w` messages, never fake objects; ONNX-using engines (Inpainting) properly use try/finally for sessions and tensors.
- **Build / dependency / ProGuard audit** — clean; all critical security versions current (Hilt 2.53.1, Coil 2.7.0, Media3 1.9.2, Kotlin 2.1.0, AGP 8.7.3); minification on release only; signing externalized; permissions audit passes.
- **MultiCamEngine cross-correlation IOOB** — already guarded by `if (length <= 0) return 0f` inside the loop.

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 98 → 99`, `versionName 3.37.0 → 3.38.0` (build.gradle.kts, NovaCutApp.VERSION, README badge, app_version string, CLAUDE.md, MEMORY.md).

## v3.37.0 — Audit Phase 8: TTS/Voiceover Persistence, Camera Cleanup Directory, Empty-Output Guard & Reverse-Clip Diagnostic

### Persistence
- **`addClipToTrack` (TTS / voiceover helper) now persists** ([EditorViewModel.kt:2116-2152](app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt#L2116-L2152)) — The private 3-arg helper used by both TTS synthesis and voiceover record was missing both `rebuildPlayerTimeline()` and `saveProject()`. Worst case, a freshly recorded voiceover or TTS clip (and any auto-created AUDIO track holding it) would be lost on app crash before the next 30-second auto-save tick. Also rejects `durationMs <= 0` up front so a TTS file with no reported duration can't violate `Clip.init`'s `trimEndMs <= sourceDurationMs` invariant. Removed the now-redundant explicit `rebuildTimeline()` + `saveProject()` calls at the TTS callsite.

### Resource hygiene
- **MediaPicker camera cleanup pointed at the right directory** ([MediaPicker.kt:151-162](app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt#L151-L162)) — Camera capture saves files to `filesDir/media` (line 125), but the LaunchedEffect cleanup was scanning `cacheDir/camera` — a path that doesn't exist in this app. Result: orphaned recordings from app crashes, force-stops, or the user backing out of the camera mid-record were never cleaned up and accumulated indefinitely. Now scans the correct directory and tolerates `delete()` failures.

### Export integrity
- **VideoEngine `onCompleted` rejects 0-byte output files** ([VideoEngine.kt:840-867](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt#L840-L867)) — Transformer's COMPLETE callback was previously trusted unconditionally. On certain hardware-encoder edge cases (malformed input, codec init failure that the encoder didn't surface as an error), the file on disk could be 0 bytes despite the COMPLETE callback firing. Surfacing this as success let users share / save an unplayable artifact and trust it worked. Now treats `outputFile.length() <= 0` as ERROR with message "Export produced an empty file" and fires `onError`.

### Diagnostics
- **Reverse-clip export warning** ([VideoEngine.kt:349-358](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt#L349-L358)) — Media3 Transformer doesn't natively support reverse playback, so any `Clip.isReversed = true` exports forward today. Added a `Log.w` listing the count of reversed clips so logs / bug reports surface the limitation when the visible result doesn't match expectations. (Full reverse implementation would need FFmpeg-side re-encoding and is out of scope for this round.)

### Audit findings verified as already-correct (false positives this round)
- **`VoiceoverRecorder.stopRecording` "silent failure"** — the catch block already cleans up the orphaned file and returns `null`; the EditorViewModel caller checks for null and toasts "Voiceover recording failed".
- **`VoiceoverRecorder` timestamp collision** — file naming uses `voiceover_${System.currentTimeMillis()}.m4a`; collision requires two recordings in the exact same millisecond on the same device, which the `@Synchronized` start/stop already serializes.
- **MediaPicker public `addClipToTrack` (delegate, 2-arg)** — `if (duration <= 0) { showToast; return }` guards against malformed media before the Clip is constructed; image clips return `DEFAULT_STILL_IMAGE_DURATION_MS = 3000L` from `getMediaDuration`.
- **Empty-timeline export crash** — the `IllegalStateException("No video clips to export")` is caught by the outer try at [VideoEngine.kt:386](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt#L386) and surfaced as ERROR state with the exception message; not a crash.
- **`AppModule.provideProjectDao` missing `@Singleton`** — Room caches the DAO instance internally regardless of how many times Hilt provides it; no real perf impact.
- **`@Insert(onConflict = REPLACE)` race** — REPLACE is well-defined SQLite behavior; concurrent inserts of the same id are serialized by Room's writer thread.
- **Project delete cascade for proxy files** — proxies in `cacheDir/proxies/` are keyed by SHA-256 of source URI and shared across projects; correct cleanup needs reference counting (out of scope) and `cacheDir` is auto-managed by Android's storage manager.
- **Coil VideoFrameDecoder explicit registration** — Coil 2.x auto-discovers the `coil-video` artifact's decoder when the dep is on the classpath; no manual `ImageLoader.Builder` needed.

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 97 → 98`, `versionName 3.36.0 → 3.37.0`
- `NovaCutApp.VERSION`, `app_version` string, README badge all synced.

## v3.36.0 — Audit Phase 7: Batch Cancel, MediaStore Strict Update, GPU Resolution Floor, Segmenter Leak & Duplicate Atomicity

### Batch export
- **Cancel now stops the queue** ([ExportDelegate.kt:330](app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt#L330)) — Previously, tapping the export-notification Cancel during a batch only cancelled the current item; the loop continued onto the next, ignoring the user's clear "stop" intent. The result-status case now distinguishes `CANCELLED` and breaks out of the loop. Failures still don't break (each batch item is independent and a long queue should tolerate per-item errors).
- **Per-item progress normalized to status** — `BatchExportItem.progress` is now explicitly set to `1f` on `COMPLETED` and `0f` on `FAILED` / `CANCELLED`. Without this, the queue UI would show "85% FAILED" on a job that errored partway through, and the COMPLETE row could stall at 0.99 because the progress collector got cancelled before observing the final tick.

### Save-to-gallery integrity
- **MediaStore IS_PENDING update is now strict** ([ExportDelegate.kt:423](app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt#L423)) — `resolver.update(...)` returning 0 (no rows updated) means the file is still flagged pending and stays invisible to Gallery / Photos apps even though we showed the user a "Saved to gallery" success toast. Now treats `updated < 1` as an explicit failure so the catch block fires the `delete(contentUri)` cleanup path.

### GPU resolution floor
- **`ShaderEffect.drawFrame` floors resolution at 1×1** ([ShaderEffect.kt:52](app/src/main/java/com/novacut/editor/engine/ShaderEffect.kt#L52)) — Several shader programs (sharpen, blur, vignette, scanlines, …) compute `1.0 / uResolution` and would produce per-pixel `Infinity` if Media3 ever calls `drawFrame` before `configure()` populated `width` / `height`. Coercing both to `≥ 1` at the uniform site protects every shader at once with no per-shader edits.

### GPU resource leak
- **`SegmentationGlEffect.drawFrame` `segBitmap` leak hardened** ([SegmentationGlEffect.kt:87-100](app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationGlEffect.kt#L87-L100)) — If MediaPipe's `engine.segment()` throws (bad-input frame, model tensor mismatch), the scaled bitmap leaked. Wrapped in try/finally so per-export-frame leaks under sustained errors can't exhaust GPU/native heap. The earlier v3.35 fix to `SegmentationEngine.segmentFrame` covered the picker preview path; this covers the hot export-render path.

### Duplicate atomicity
- **`ProjectListViewModel.duplicateProject` rolls back on auto-save copy failure** ([ProjectListViewModel.kt:255-270](app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt#L255-L270)) — Previously did `insertProject` then `copyAutoSave` with no error handling. If the file copy failed (disk full, source missing), the Room row remained and opened as an empty project — the user would think "duplicate worked but lost my edits". Now wraps in try/catch and runs `deleteById(newId)` to roll back the orphaned row.

### Audit findings verified as already-correct (false positives this round)
- `MainActivity` rotation re-import — manifest's `configChanges="orientation|screenSize|screenLayout|keyboardHidden"` prevents activity recreation on rotation; `onCreate` doesn't re-fire.
- `NovaCutApp.createNotificationChannels` API guard — `minSdk = 26` matches the API level where `NotificationChannel` was added; no guard needed.
- `SettingsRepository` corruption handling — DataStore's `CorruptionException` extends `IOException`, so the existing `if (error is IOException)` catch covers it.
- `EffectBuilder` EXPOSURE `Math.pow(2.0, value)` — `value.coerceIn(-2f, 2f)` directly above bounds the input; `pow` result ∈ [0.25, 4].
- `SegmentationGlEffect` `glReadPixels` reading wrong FBO — agent misread the call order; readback happens BEFORE the saved FBO is restored at line 77.
- `EditorScreen` keyboard intercepting Space/Delete in TextFields — focused TextField consumes input keys before the parent's `onKeyEvent` fires; key auto-repeat for undo/seek is acceptable behavior.
- `ProjectListViewModel.renameProject` race with auto-save — `EditorViewModel`'s viewModelScope (and its auto-save coroutine) is cancelled when the user navigates back to the project list.

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 96 → 97`, `versionName 3.35.0 → 3.36.0` (build.gradle.kts, NovaCutApp.VERSION, README badge, app_version string, CLAUDE.md, MEMORY.md).

## v3.35.0 — Audit Phase 6: Keyframe Range Safety, Color Curve NaN Guard, Bitmap Leak & Caption Validation

### Math correctness
- **`KeyframeEngine.getValueAt` clamps OPACITY and VOLUME to safe ranges** — Bezier curves with handles outside the unit square (and the `ELASTIC` / `BACK` / `SPRING` easings) can legitimately overshoot `[0, 1]`. For position / scale / rotation the overshoot is the desired effect (springy motion); for OPACITY and VOLUME it's a contract violation: opacity < 0 means "less than transparent", opacity > 1 brightens via `RgbMatrix`, and negative volume in `VolumeAudioProcessor` inverts phase. A new private `clampForProperty(value, property)` is now applied to every return path of `getValueAt`, so every consumer (preview, export, scopes) sees the same legal value.
- **`ColorCurves.evaluateCurve` guards against duplicate-x curve points** — If two adjacent points share the same x coordinate, `(input - p0.x) / (p1.x - p0.x)` divided by zero, producing NaN that propagated through the cubic-bezier into the color output (renders as black or wraps on GPU). Users could create this by dragging a curve handle exactly onto a neighbour, or via legacy auto-saves. Falls back to `p0.y` (visually-correct vertical step).

### Resource leak
- **`SegmentationEngine.segmentFrame` bitmap leak hardened** — The original `frame` returned by `MediaMetadataRetriever.getFrameAtTime()` and the `scaled` copy were only recycled in the success path. If `Bitmap.createScaledBitmap` OOM'd or `segment(scaled)` threw partway through, both bitmaps leaked (~10 MB per call). Tracked via outer `var frame`/`var scaled` so the `finally` block guarantees recycling regardless of where the failure happens. Also corrected `targetMs * 1000` to `targetMs * 1000L` for explicit Long-multiplication intent.

### Caption validation
- **`CaptionEditorPanel` Save buttons gated on non-blank text** — Both Save buttons (collapsed and expanded mode) now have `enabled = text.isNotBlank()`, and the saved value is `text.trim()`. Previously a user could save an all-whitespace caption that would render as nothing in the export but still consume timeline space. Trimming on save also normalizes captions like `"   Hello   "`.

### Audit findings verified as already-correct (false positives this round)
- **MultiCamEngine.kt:91 `bestOffset.toLong() * 1000 / sampleRate`** — `Long * Int` is Long in Kotlin; no narrowing.
- **AiFeatures.kt:204 `sum / windowSamples`** — `windowSamples` is `(sampleRate / 10).coerceAtLeast(1)`, so divisor is always ≥ 1.
- **AiFeatures.kt:556 / 983 motion-estimation `bestDx / w`** — both call sites are guarded by `if (w < 8 || h < 8) return 0f to 0f` directly above.
- **AiFeatures.kt:2357 `coerceIn(1, halfSize - 1)`** — `halfSize` is at least 32 because of the `if (fftSize < 64) return` guard at line 2320.
- **AudioEngine.kt:194 `totalSamples` Int truncation** — would only matter for ≥ 6-hour audio mixes, where the FloatArray allocation (~7.6 GB) would OOM long before the Int overflowed; not a real concern in a video editor's mix path.
- **`RadialActionMenu` `LaunchedEffect(Unit) { visible = true }`** — composable is gated by `if (showRadialMenu)` in EditorScreen, so it's recreated each show; the `Unit` key is correct here.
- **EffectBuilder `buildVideoEffect` exhaustiveness** — every `EffectType` is covered; `SPEED` / `REVERSE` correctly map to `null` (not shaders).

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 95 → 96`, `versionName 3.34.0 → 3.35.0`
- `NovaCutApp.VERSION`, `app_version` string, README badge all synced.

## v3.34.0 — Audit Phase 5: CAS Safety, Backup Coverage, Performance Hot Path & Stale-String Cleanup

### Concurrency safety
- **Hoisted UUIDs out of `_state.update {}` closures** — `MutableStateFlow.update` re-executes its lambda on each CAS-retry. Generating UUIDs inside the closure means a retry mints fresh IDs that don't match what any prior closure attempt observed. Fixed two real cases:
  - **Paste-effects** (`EditorViewModel.kt:723`) — pre-mints `freshEffects` from `state.copiedEffects` once, then the closure just inserts them.
  - **Freeze-frame** (`EditorViewModel.kt:3300`) — pre-mints `freezeClipId` and `secondHalfId` so the inserted freeze clip and the second-half clip have stable identities across retries.
  - Practical impact is small (single-threaded UI, low CAS contention), but it's the kind of latent bug that surfaces only under load and is hard to diagnose later.

### Backup coverage
- **`tts_output/` and `noise_reduced/` now in `backup_rules.xml` and `data_extraction_rules.xml`** — these directories were referenced by `file_paths.xml` and held real media that clips could reference, but were excluded from cloud backup and device transfer. After a restore, project clips that pointed at TTS-generated voiceovers or denoised audio would silently disappear from the timeline (post-v3.31 the load path skips dangling URIs cleanly, but the user still loses the clip). Both rule files now include them so projects round-trip across devices.

### Performance hot path
- **`PreviewPanel` background brushes hoisted to `remember`** — Two `Brush.verticalGradient(listOf(...))` allocations inside `Column.background(...)` and the inner `Card`'s `Box.background(...)` were running on every recomposition. PreviewPanel recomposes on every playhead tick during playback (~30 Hz), so each frame was producing ~2 List + 2 Brush allocations purely for the GC.
- **`Timeline` per-clip selection brush hoisted to `remember(isSelected, isMultiSelected, clipColor)`** — the clip-rendering loop was allocating a fresh `Brush.horizontalGradient(listOf(...))` per visible clip per recomposition. With a busy timeline and Timeline recomposing on `scrollOffsetMs` updates, this was the dominant per-frame allocation. Now reused until selection or track-color state actually changes.

### Stale-string cleanup
- **`@string/app_version` synced to `3.34.0`** — the resource was stuck on `v3.30.0` for several releases. It's not currently referenced from code (Settings already uses `NovaCutApp.VERSION`), but it's the kind of surface that appears in Play Store screenshots / accessibility scans when stale.

### Audit findings verified as already-correct (false positives this round)
- `ExportConfig.videoBitrate` — computed property whose `when` exhaustively covers every `Resolution × ExportQuality` combination with positive bitrates; the `init { require(videoBitrate > 0) }` cannot trip.
- `Project.thumbnailUri` from `clips.firstOrNull()?.sourceUri?.toString()` — chained safe-calls, and `Clip.sourceUri` is rejected at deserialize time if empty (since v3.31). Returns `null` cleanly when there are no clips.
- `selectedClipIds` after `deleteMultiSelectedClips` — already reset to `emptySet()` at line 2730 in the same `_state.update`.
- `ExportService` Cancel-action path — `PendingIntent.getService()` only fires while the service is already in the foreground from the prior export start, so the Cancel branch returning before `startForeground()` is safe.
- Manifest `<queries>` for ACTION_SEND — `Intent.createChooser()` is exempt from Android 11+ package-visibility restrictions; no resolver calls in the codebase.
- Room `MIGRATION_4_5` — `CREATE INDEX IF NOT EXISTS` is idempotent; SQLite DDL is atomic. Sort order works regardless of index presence.

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 94 → 95`, `versionName 3.33.0 → 3.34.0` (build.gradle.kts, NovaCutApp.VERSION, README badge, app_version string, CLAUDE.md, MEMORY.md).

## v3.33.0 — Premium Polish: Design Tokens, Animated Snackbar, Onboarding Refresh & Export-State Hierarchy

### Design system foundations
- **`ui/theme/Tokens.kt`** — New centralized design-token module exposing `Spacing`, `Radius`, `Elevation`, `Motion`, and `TouchTarget` scales. Replaces the ad-hoc `8.dp` / `tween(120)` / `RoundedCornerShape(14.dp)` literals scattered across panels. Future panels should reach for tokens rather than inventing one-off values, so the editor's rhythm stays coherent.

### New components
- **`PremiumSnackbarHost` (`PremiumSnackbar.kt`)** — Animated, severity-aware Mocha-styled snackbar replacing the bare Material 3 `Snackbar` in the editor. Features:
  - Slide-up + fade-in entrance / fade-out exit driven by the new `Motion` tokens
  - Severity stripe (Info / Success / Warning / Error) with matching outlined icon — color is never the only signal (a11y)
  - PanelHighest surface + hairline border, consistent with the rest of the editor's floating chrome
  - Accent-tinted horizontal gradient that hints status without shouting
  - `inferSeverity(message)` heuristic so the dozens of existing `showToast("…")` callsites get appropriate styling automatically; explicit `showToast(msg, ToastSeverity.Error)` is also available
  - Adaptive duration: errors stay 4.5s, warnings 3.5s, info 2.8s
- **`PremiumHairlineDivider`** — Thin, slightly translucent divider for sectioning content inside `PremiumPanelCard`. Drops into existing card layouts with one line.

### Onboarding refresh (`FirstRunTutorial.kt`)
- **Backdrop** — Replaced the flat 85% `Crust` scrim with a soft radial mauve→crust vignette. Reads as cinematic stage lighting instead of "the screen is dimmed".
- **Card** — Upgraded from a flat `Surface0` block to a bordered `PanelHighest` surface with a subtle vertical accent gradient and `12.dp` shadow elevation. Gives the tutorial card visible weight against the new vignette.
- **Step indicator** — Replaced equal-sized dots with an animated connected pill bar where the current step expands to 24dp (was: just got slightly bigger). Reads as "you are here" much faster.
- **Skip** — Bare `Text` upgraded to a translucent pill with a hairline border. Discoverable affordance instead of an ambiguous floating word.
- **Step transitions** — Now driven by the shared `Motion.DecelerateEasing` / `AccelerateEasing` tokens so it feels coherent with the rest of the app's motion language.
- **Typography** — Migrated from hardcoded `18.sp` / `13.sp` to `MaterialTheme.typography.headlineMedium` / `bodyMedium` for consistency with the rest of the editor.

### ExportSheet — semantic primary-button styling
- **New `PrimaryStyle` enum** (`Filled`, `Destructive`, `Quiet`) routed to `ExportStateCard`. Each export state now picks a button treatment that matches its meaning:
  - **Exporting → Cancel** — outlined Peach (was: filled Rosewater, indistinguishable from "Share completed export")
  - **Complete → Share** — filled Rosewater (celebratory)
  - **Cancelled → Done** — outlined neutral (informational, not celebratory)
  - **Error → Retry** — filled Red (clear primary)
- **Animated progress bar** — `LinearProgressIndicator` is now driven through `animateFloatAsState` so it doesn't snap on each Transformer progress tick. Bar is also taller (10dp), pill-clipped, and uses a slightly translucent track for better contrast.
- **Percent label** — Bumped from `titleMedium` to `headlineMedium SemiBold` so the "47%" reads as the focal data point of the exporting card.
- **Icon halo** — Replaced single-circle treatment with a layered halo (outer translucent ring + inner filled disc) for visible depth without resorting to a hard shadow that would clash with the gradient surface.
- **Body text** — Now center-aligned, fixing prior visual imbalance with the centered headline above it.

### Component refinement
- **`PremiumPanelCard`** — Trimmed the 3-stop accent gradient to a single soft fade. The previous middle stop produced a visible "fold" line halfway down every card; the new fade reads as restrained tinted glass.
- **`PremiumPanelCard`** — Standardized on `Radius.xl` / `Spacing.lg` / `Spacing.md` from the new token module instead of inline `24.dp` / `16.dp` / `12.dp`.
- **`PremiumEditorPanel` drag handle** — Slimmed from `44dp × 4dp` to `36dp × 3dp` and dimmed alpha from 0.8 to 0.55. Reads as a quiet gesture hint rather than a competing UI element.
- **EditorTopBar rename dialog** — Normalized unfocused border from `Mocha.Surface1` (too bright) to `Mocha.CardStroke`, matching the rest of the editor's input fields.

### Snackbar message contrast
- Snackbar body uses primary `Mocha.Text` instead of `Mocha.Subtext1`. Status meaning is carried by the leading icon and accent stripe, leaving the message itself fully legible — important for short-duration toasts where users have ~3 seconds to read and decide.

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 93 → 94`, `versionName 3.32.0 → 3.33.0`
- `NovaCutApp.VERSION` updated.

## v3.32.0 — Audit Phase 4: Encoder Edges, DSP Parameter Hardening & Audio-Format Guards

### Export / Encoder
- **GIF runaway-frame guard** — `gifFrameRate` is now coerced into `[1, 60]` and `frameIntervalMs` is floored at `1L`. Previously a stale or experimental >1000 fps value produced `1000 / fps == 0`, which made `frameCount = totalDurationMs / 0`, triggering an infinite frame loop, OOM, and an export that never returned.

### Audio Engines
- **VolumeAudioProcessor channel guard** — `onConfigure` now also rejects `channelCount <= 0`, not just `sampleRate == 0`. A malformed audio track previously slipped through and divided by zero in the per-sample loop (`processedFrames / channelCount`), leaving an orphaned partial export file mid-render.
- **AudioEffectsEngine compressor parameter coercion** — `attack`, `release`, `knee`, `ratio`, and `sampleRate` are now floored at safe positive minima before being fed into `exp(-1f / (attackMs * sampleRate / 1000f))`. A zero `attack` previously produced `exp(-Infinity) = 0` (instant peak follow); a negative attack from corrupt state produced `exp(+Infinity) = NaN` and silently corrupted the audio buffer.

### UX
- **TtsPanel input cap** — TTS script field is now bounded at 2,000 characters with an inline `len / 2000` indicator (Mocha.Peach when at limit). Prevents accidental paste-bombs from running unbounded synthesis jobs and OOM'ing the engine.

### Audit Findings That Turned Out To Be Already-Correct
Spent careful verification against source rather than implementing every agent suggestion. False positives this round: GIF color-quantization operator precedence (Kotlin infix `shr`/`and` left-associativity already evaluates correctly), LoudnessEngine short-term loop bounds, BeatDetectionEngine BPM divide-by-zero (intervals already bounded to 200..2000ms), EffectsDelegate.updateEffect missing undo (debounced via `beginEffectAdjust()` by design), AiToolsDelegate stale clip refs (already re-validates inside coroutine and dispatches `currentClip`), MediaStore IS_PENDING handling in `saveExportedFile` (already deletes on exception), batch-export reset ordering (`resetExportState()` already runs before each item's `startExport`), and the four "missing contentDescription" reports (all decorative icons inside buttons / list items with adjacent text labels — adding cd would produce redundant TalkBack output).

### Verification
- `./gradlew compileDebugKotlin` passes.

### Housekeeping
- `versionCode 92 → 93`, `versionName 3.31.0 → 3.32.0`
- `NovaCutApp.VERSION` updated.

## v3.31.0 — Audit Phase 3: Persistence Parity, Resource Leaks & Defensive Deserialization

### Data Loss Fixes (CRITICAL)
- **ColorGrade.curves not serialized** — `ColorGrade.curves` (master/red/green/blue channel curves with per-point bezier handles) was completely missing from `ProjectAutoSave`. Users lost all RGB curve adjustments on project recovery / app restart. Now fully serialized via new `serializeColorCurves` / `deserializeColorCurves` helpers with bounds-coerced curve points.
- **ColorGrade.colorMatchRef not serialized** — Reference clip ID for "match color to reference" workflow was lost on recovery. Now persisted.

### Defensive Deserialization
- **Clip fade bounds coerced** — `fadeInMs` and `fadeOutMs` are now coerced into `[0, clipDurationMs]` with `fadeIn + fadeOut <= clipDurationMs`. Previously raw values from corrupted auto-save could exceed clip duration and produce truncated/glitched fades on export.
- **Clip rejected for non-positive `sourceDurationMs`** — Previously zero-duration clips would silently load and break timeline math (division-by-zero risk). Now logged + skipped.
- **Safe URI parse** — `Clip.sourceUri`, `Clip.proxyUri`, and `ImageOverlay.sourceUri` now wrap `Uri.parse` in try/catch. Malformed URIs from a corrupt auto-save no longer take down the whole project recovery.
- **Format version bookkeeping** — `deserialize()` now reads the file's `version` field and logs a warning when an auto-save was written by a newer schema than the current build, instead of silently mis-parsing it.
- **Empty `sourceUri` clip drop logged** — Previously silent; now `Log.w` with clip ID for diagnostics.

### Resource Leak Fixes
- **WhisperEngine encoder output leak** — `runEncoder` now closes both `OrtSession.Result` and `OnnxTensor` input in a `finally` block. Previously a `runDecoder` exception would orphan the encoder output OnnxTensor (~MB of native memory per chunk leaked on transcription failure).
- **WhisperEngine encoder result leak** — `runEncoder` previously closed `results` only on the success-cast path. Now uses unified try/finally, so the `OrtSession.Result` is closed on every exit including the `as? OnnxTensor` null path.
- **ColorMatchEngine bitmap leak** — `MediaMetadataRetriever.getFrameAtTime()` returns a bitmap that was never recycled (only the scaled copy made inside `analyzeBitmap` was). Now recycled in finally. Also corrected `timeMs * 1000` to `timeMs * 1000L` to make the long-multiplication intent explicit.

### UI Hardening
- **PreviewPanel still-image `contentDescription`** — Now reads `R.string.cd_preview_still_image` instead of `null` (a11y).
- **PreviewPanel listener lifecycle** — `DisposableEffect` now captures the player reference up front and wraps `removeListener` in try/catch so a player released between attach and dispose can't crash the editor.
- **EditorScreen clip label picker keyed to selection** — `showClipLabelPicker` is now `remember(state.selectedClipId) { ... }`. Previously the picker stayed open after the user changed clip selection or deselected, painting the picker over the wrong (or no) clip.

### Verification
- `./gradlew compileDebugKotlin` passes cleanly with the above changes.

### Housekeeping
- `versionCode 91 → 92`, `versionName 3.30.0 → 3.31.0`
- `NovaCutApp.VERSION` updated to match for HTTP user-agent strings on model downloads

## v3.30.0 — UI Polish & Panel Hardening

### UI Improvements
- **Editor panels overhauled** — 25 panel composables refined: improved layouts, consistent Catppuccin Mocha theming, better accessibility content descriptions, and expanded string resources (259 new i18n entries).
- **Launcher icon reverted** — Restored halo + full letterform design.
- **KeyframeCurveEditor** — Richer curve visualization with grid lines, property-colored dots, and improved hit detection.
- **SpeedCurveEditor** — Enhanced canvas with reference line, higher-fidelity curve rendering (200 sample steps), and preset chip row.
- **VideoScopes** — Histogram, waveform, and vectorscope panels refined with better scaling and color accuracy.
- **TextTemplateGallery** — Expanded animated template library with category filtering and preview cards.
- **ToolPanel** — Smarter clip/project mode switching, sub-menu grid layout, and disabled-state feedback for clip-only actions.

### Data Model
- **SpeedCurve.averageSpeed()** — New utility for sampling average speed across a curve with configurable sample count.

### Housekeeping
- `versionCode 90 → 91`, `versionName 3.29.0 → 3.30.0`
- Room schema v5 export added

## v3.29.0 — Audit Phase 2: Data Persistence, Thread Safety & Database Optimization

### Data Persistence Fixes
- **24 missing `saveProject()` calls** — Added `saveProject()` to all discrete state-mutating functions in EditorViewModel that had `saveUndoState()` but never persisted: pasteClipEffects, addAdjustmentLayer, addCaption, removeCaption, applyCaptionStyle, applyBeatSync, applySpeedPreset, applyFillerRemoval, runAutoEdit, importEffects, addEffectKeyframe, analyzeAndReduceNoise, syncMultiCamClips, colorMatchToReference, applyTextTemplate, autoDuckAudio, addKeyframe, deleteKeyframe, setClipSpeedCurve, addMask, deleteMask, setClipBlendMode, unlinkAudioVideo, applyPipPreset. Users could undo changes that were never saved — on app restart, the undo stack was gone but the state never hit disk.

### Thread Safety Fixes
- **SegmentationEngine race condition** — Added `@Synchronized` to `getOrCreateSegmenter()` to prevent concurrent threads from creating duplicate expensive `ImageSegmenter` instances.
- **applyPipPreset missing timeline rebuild** — Added `rebuildPlayerTimeline()` so PiP preset changes reflect in the player immediately.

### Database & Storage Fixes
- **Room index on `updatedAt`** — Added `@Index("updatedAt")` to the Project entity and `MIGRATION_4_5` (database v4→v5) to create the index. Project list query (`ORDER BY updatedAt DESC`) was doing a full table scan.
- **TemplateManager import size limit** — `importTemplateFromUri()` now enforces a 10MB cap via chunked reading. Previously read the entire file into memory without limit.
- **Caption deserialization crash guard** — `ProjectAutoSave` now clamps `endTimeMs` when corrupt JSON has `endTimeMs < startTimeMs`, preventing `Caption.init` from throwing during project restore.

### Housekeeping
- `versionCode 89 → 90`, `versionName 3.28.0 → 3.29.0`

## v3.28.0 — Deep Engineering Audit: Correctness, Security & Resource Safety

### Critical Fixes
- **StateFlowExt CAS loop** — Removed 100-retry limit that caused `IllegalStateException` under high contention; loop now runs unbounded with periodic `Thread.yield()`.
- **ProjectAutoSave `release()` deadlock** — Replaced `runBlocking` + mutex with scope cancellation to prevent ANR when Activity destroys.
- **ProjectAutoSave atomic writes** — Save uses temp file + rename + backup rollback pattern; interrupted saves no longer corrupt project files.
- **ProjectAutoSave `.bak` recovery** — `loadRecoveryData()` now restores from backup files left by interrupted saves.
- **AppModule destructive migration removed** — `fallbackToDestructiveMigrationOnDowngrade()` silently deleted all user projects on app downgrade; removed so downgrades now surface an error instead of silently deleting data.
- **VideoEngine export race condition** — Added `synchronized` block around export state check-and-set to prevent concurrent export starts.
- **AudioEngine MediaCodec resource leak** — Both `extractWaveform()` and `decodeToPCM()` now use `try-finally` with nullable decoder to guarantee `stop()`/`release()` on all paths.
- **WhisperEngine ONNX tensor lifecycle** — Decoder loop restructured: `OrtSession.Result` and `OnnxTensor` are now closed exactly once via `finally` block, preventing native memory leaks when `session.run()` succeeds but post-processing throws.

### Security Fixes
- **Intent filter URI scheme hardening** — Removed `file://` scheme from AndroidManifest intent filter; only `content://` URIs are now accepted.
- **MainActivity intent validation** — Incoming `ACTION_VIEW` intents are validated: scheme must be `content://`, MIME type must resolve, invalid URIs are silently dropped.
- **SettingsRepository enum validation** — `updateDefaultCodec()` and `updateDefaultExportQuality()` now validate against known enum values, preventing garbage strings from being stored via corrupt settings or IPC.

### Edge Case & Robustness Fixes
- **VolumeAudioProcessor NaN guard** — Added `isNaN()`/`isInfinite()` check on computed gain; handles edge case where `clipDurationMs <= fadeOutMs`.
- **SubtitleExporter invalid caption filter** — Captions with negative times or zero/negative duration are filtered before export instead of producing malformed subtitle files.
- **KeyframeEngine Newton-Raphson stability** — Increased near-zero slope threshold from `1e-7f` to `1e-5f` to prevent floating-point instability in bezier easing.
- **ExportDelegate batch queue snapshot** — Batch export queue is now copied with `.toList()` before iteration to prevent `ConcurrentModificationException` if queue is mutated during export.
- **VoiceoverRecorder thread safety** — Added `@Synchronized` to `startRecording()`, `stopRecording()`, and `release()` to prevent concurrent access from UI and lifecycle callbacks.
- **ProjectDatabase type converter logging** — Silent enum fallbacks in Room type converters now log warnings for diagnosability.

### Housekeeping
- `versionCode 88 -> 89`, `versionName 3.27.0 -> 3.28.0`

## v3.27.0 — Export & Archive Overhaul, UI Density Pass, File Safety

### Engine / Core
- **Centralized file-name sanitization** — New `FileNaming.kt` utility replaces 6+ scattered inline regex calls with a single function that handles Windows reserved names, control chars, and extension preservation. All export/archive/template file paths now use it.
- **ProjectArchive rewrite** — Archives now include a `media_manifest.json` for reliable round-tripping of media URIs. Compound clips and image overlays are archived. Import rolls back created directories on failure and rewrites media URIs via manifest + fallback matching.
- **ProjectAutoSave.saveNow() is now suspend** — Removed `runBlocking` wrapper that could freeze the main thread during manual saves.
- **TemplateManager refactor** — DRY JSON serialization/deserialization via `templateToJson`/`parseTemplateJson`. Export by template ID instead of name. Duplicate name detection on import. Template stateJson is validated on load (corrupt templates are skipped instead of crashing).
- **Scoped-storage backup export** — Backup `.novacut` files are now written via MediaStore on API 29+ instead of direct filesystem access, fixing permission failures on Android 11+.
- **Backup import restores full state** — Beat markers, playhead position, and duration are now restored; panels are dismissed and the player timeline rebuilt after import.

### Export
- **Named output files** — Exports now use the project name (or batch item name) instead of `NovaCut_<timestamp>`, with automatic `(2)`, `(3)` collision avoidance.
- **MediaScanner on pre-Q save** — Legacy save-to-gallery path now calls `MediaScannerConnection` so files appear in the gallery immediately.
- **Batch export summary** — Toast now reports passed/failed counts instead of a generic "complete" message.

### UI / Layout
- **Compact top bar** — EditorTopBar adapts sizing and spacing on screens narrower than 430dp. Home icon replaced with a standard ArrowBack.
- **FlowRow everywhere** — Timeline info chips, export preset chips, batch export status pills, and export sheet sections now wrap instead of scrolling horizontally, preventing clipped or unreachable controls on narrow screens.
- **ExportSheet restructured** — Sections wrapped in descriptive cards. Summary hero, pills, and primary button label adapt to the active export mode (video, audio, stems, GIF, frame capture).
- **MediaPicker restructured** — Grouped into "Import from Library" and "Capture on Device" section cards with descriptions.
- **ProjectTemplateSheet** — Built-in and saved template sections have description text and an empty-state placeholder.
- **SettingsScreen** — Every section and picker now has a subtitle description for discoverability.
- **ProjectListScreen** — Added inline rename dialog for projects.
- **BatchExportPanel** — Failed/cancelled status pills; simplified item row (removable vs. in-progress only); `describeForQueue()` is now `@Composable` for string resources.

### Strings
- 50+ new string resources for section descriptions, batch export states, media picker labels, and settings subtitles.

### Build & Test
- JUnit 4 test dependency added; `FileNamingTest.kt` covers the new sanitization utility.
- `versionCode 87 -> 88`, `versionName 3.26.0 -> 3.27.0`

## v3.26.0 — QA Audit: Crash, Leak & Persistence Fixes

### Crash Fixes
- **WhisperEngine ONNX `results.first()`** — `runEncoder` and `runDecoder` called `.first()` on the ONNX Runtime results map, which throws `NoSuchElementException` if the model returns an empty result map. Replaced with `firstOrNull()?.value` and added explicit close of `results` + `idTensor` on the null path in `runDecoder` to avoid leaking tensors on the break path.

### Resource Leaks
- **InpaintingEngine session + sessionOptions leak** — `OrtSession` was created before `Bitmap.createScaledBitmap`; if the bitmap allocation threw `OutOfMemoryError`, the session and `sessionOptions` were never closed. Restructured so all ONNX/bitmap/tensor resources are tracked in nullable locals and released in a single outer `finally` block, closing the session and session options on every exit path (including OOM during pre-processing).

### Persistence / Data Loss Fixes
- **Beat markers silently lost on restart** — `detectBeats()` / manual `tapBeatMarker()` / `clearBeatMarkers()` wrote `beatMarkers` to state but `AutoSaveState` did not include the field at all, so beat analysis was dropped on every auto-save/recovery cycle. Added `beatMarkers: List<Long>` to `AutoSaveState`, wired it through all three construction sites in `EditorViewModel` (auto-save, snapshot, manual save), hydrated it in the recovery path, and added `saveProject()` calls after beat mutations.
- **`ColorGradingDelegate.hideColorGrading()` triggered auto-save on panel close** — The close handler called `saveProject()` even though closing a panel is UI-only state that never belongs in the project file. Removed the bogus call; wasted I/O eliminated and the auto-save indicator no longer flashes on every grading panel dismissal.

### UX / State Fixes
- **SpeedCurveEditor log-slider polluted undo stack** — The fine-control log slider called `onConstantSpeedChanged` on every drag tick without invoking `beginSpeedChange()` / `endSpeedChange()`, so each drag created zero undo entries (never saved undo state at all), never rebuilt the player timeline, and never persisted via `saveProject()`. Added `onSpeedDragStarted` / `onSpeedDragEnded` callbacks to the composable, wired the slider with `onValueChangeFinished` + a `sliderDragActive` guard, bracketed preset chip taps with begin/end so each chip tap yields exactly one undoable action, and wired both to `viewModel::beginSpeedChange` / `viewModel::endSpeedChange` from `EditorScreen`.
- **TextEditorSheet retained stale state across edits** — All 21 `remember { mutableStateOf(existingOverlay?.xxx ?: default) }` blocks had no key, so if the sheet was ever re-composed with a different `existingOverlay` parameter the state would silently hold the previous overlay's values (text, font, colors, shadows, glow, rotation, position, animation). Keyed every remember block to `existingOverlay?.id ?: "__new__"` so state always tracks the overlay being edited.

### Build
- `versionCode 86 → 87`, `versionName 3.25.1 → 3.26.0`
- Both debug and release builds pass cleanly (R8 minification + resource shrinking enabled)

## v3.24.0 — Transitions & Smooth Playback

### Transition System Overhaul
- **Per-clip effects during playback** — Transitions now preview live as playback crosses clip boundaries (previously only visible on selected clip)
- **Transition-out effects** — Outgoing clips now fade/wipe out to match the incoming clip's transition-in, creating seamless visual transitions
- **7 transition-out shader types** — Fade-out (black/white), wipe-out, slide-out, circle-close, zoom-out, and spin-out shaders activated at the end of outgoing clips
- **Export transition-out** — Transition-out effects now also apply during Transformer export, not just preview

### Smooth Playback
- **Custom ExoPlayer buffer config** — Increased buffer sizes (5s min, 50s max, 1.5s playback, 3s rebuffer) for gapless multi-clip playback
- **Decoder fallback** — Enabled `DefaultRenderersFactory.setEnableDecoderFallback(true)` so playback recovers from codec failures instead of stopping
- **Reduced clip boundary stutter** — Larger pre-buffer window allows ExoPlayer to pre-decode the next clip before the current one ends

### Timeline UI
- **Transition zone overlays** — Transition-in regions show a yellow gradient overlay with swap icon at the clip start; transition-out regions show a matching gradient at the outgoing clip's end
- **Duration-proportional indicators** — Transition zones scale with the actual transition duration instead of using a fixed 12dp square

## v3.23.0 — Comprehensive Audit: 24 Bug Fixes

### Crash Fixes
- **LRU cache overflow** — Thumbnail cache size capped to prevent `IllegalArgumentException` on 8 GB+ heap devices
- **ExportService leak** — Service now stops itself if export is already complete when `onStartCommand` fires
- **GIF double-recycle** — Removed duplicate `Bitmap.recycle()` in export error path that could crash on recycled bitmaps
- **Zero-duration clip guards** — `KeyframeCurveEditor` and `VolumeEnvelopeEditor` now return early when `clipDurationMs` is 0

### Correctness Fixes
- **Clip.getEffectiveSpeed** — Now uses raw trim range (`trimEndMs - trimStartMs`) instead of speed-adjusted `durationMs` for speed curve evaluation
- **EDL timecode rounding** — `msToTimecode` now rounds instead of truncating, fixing frame-inaccurate EDL exports
- **deleteMultiSelectedClips** — Now ripple-deletes (shifts subsequent clips backward) instead of leaving timeline gaps
- **applyFillerRemoval** — Now closes gaps after removing filler clips
- **splitClipAt** — First half now clears stale transition that belonged to the pre-split boundary
- **Audio filter stability** — Band-pass and notch filter frequency clamped to \[20 Hz, Nyquist) to prevent NaN coefficients
- **Waveform RMS** — Guards against empty sample buffer division by zero
- **Normalizer naming** — Renamed misleading `targetLufs` parameter to `targetPeakDb` (function implements peak normalization, not LUFS)

### Data Persistence
- **Track volume/pan/solo** — Now save undo state before mutation (changes are undoable)
- **Audio effect params** — `updateTrackAudioEffectParam` now calls `saveProject()` (changes were lost on restart)
- **setClipLut** — Removed redundant double `saveProject()` call
- **Basic stabilization** — Now calls `rebuildPlayerTimeline()` and `saveProject()` (preview and persistence were broken)
- **Batch export** — Original export config now restored in `finally` block (was lost on cancellation)

### Thread Safety
- **TtsEngine.preview()** — Now acquires mutex to prevent race with concurrent `synthesize()` calls
- **ProjectAutoSave.copyAutoSave** — Now acquires `saveMutex` to prevent reading partially-written files

### UI/UX
- **Touch targets** — Enlarged critically undersized buttons: scopes toggle (28→40dp), text editor close (28→40dp), delete keyframe (24→36dp), search clear (20→36dp)
- **formatDate localization** — Now uses existing string resources instead of hardcoded English ("Just now", "Xm ago")
- **Hardcoded strings** — "TRIM MODE" hint and "Untitled" project name now use string resources

## v3.22.0 — Data Safety, Export Correctness & Bug Fixes

### ProjectAutoSave — 6 Missing Fields Fixed (Data Loss Prevention)
- **Mask.keyframes** — Animated mask keyframes now survive crash recovery
- **TextOverlay.textPath** — Text-on-path (curved, circular, wave) preserved on save
- **TextOverlay.templateId** — Template association no longer lost on recovery
- **TextOverlay.keyframes** — Animated text overlay keyframes now serialized
- **TimelineMarker.notes** — User notes on markers preserved across sessions
- **Clip.proxyUri + motionTrackingData** — Proxy state and motion tracking data persisted

### Export Fixes
- **GIF speed calculation** — Frame extraction now accounts for clip speed (was ignoring it, producing wrong frames for non-1x clips)
- **MIME type detection** — `saveToGallery()` and `getShareIntent()` now use correct MIME types for GIF (`image/gif`) and WebM (`video/webm`) instead of hardcoded `video/mp4`
- **GIF saves to Pictures** — GIF exports now save to Pictures/NovaCut instead of Movies/NovaCut
- **Batch export config restore** — Original export config restored after batch export completes (was left as last batch item's config)
- **LZW code size** — Fixed off-by-one in GIF encoder code size increment

### Bug Fixes
- **setTrackBlendMode** — Now rebuilds player timeline and saves project (was a no-op in preview)
- **pasteClipEffects** — Uses fresh state inside update lambda (was using stale captured reference)
- **deleteMultiSelectedClips** — Now cleans up waveform data for deleted clips (was leaking memory)
- **SpeedCurve NaN guard** — `getSpeedAt()` returns safe default when clipDurationMs is 0
- **updateImageOverlay** — Now saves undo state (sticker edits were not undoable)
- **removeTextOverlay** — Clears `editingTextOverlayId` when removed overlay was being edited
- **addImageOverlay** — Duration clamped to timeline end (was exceeding total duration)
- **moveClipToTrack** — Validates target track type compatibility (was allowing video→audio moves)
- **seekTo** — Position clamped to valid range

### Build Infrastructure
- **VMware HGFS build fix** — Added `doFirst` workaround in `build.gradle.kts` for AGP tasks that fail to delete output dirs containing `$` in filenames (Kotlin lambda class names) on VMware shared folders
- **Timeline lambda depth** — Extracted `volumeKeyframesSorted()` top-level helper to reduce deeply nested lambda class name length

### Code Quality
- **Zero compiler warnings** — Migrated `EditedMediaItemSequence` to Builder pattern (`addItem`/`addItems`), replaced deprecated `Icons.Filled.RotateRight` with `Icons.AutoMirrored.Filled.RotateRight`

### Error Logging
- Added `Log.w` to silent catch blocks: LutEngine (2), FrameCapture, VideoEngine, MultiCamEngine, MediaPicker (2)
- MediaPicker `takePersistableUriPermission` failures now logged (was silently losing URI permissions)

## v3.21.0 — Trim Handles, Accessibility & Quality

### Trim Handle Fix
- Clip edge trim handles now always visible on all clips (were hidden behind selection guard)
- Handles auto-select the clip on drag start — users can directly grab any clip edge to trim
- Unselected clips show subtler handle color (50% alpha) for visual hierarchy

### Accessibility
- 27+ null contentDescriptions fixed across 16 UI files (37 new string resources)

### Exception Logging
- 16 silent catch blocks now log warnings across 7 engine files

### Stub Engine UX
- 4 unimplemented AI tool buttons (Frame Interpolation, Upscale, AI Background, Style Transfer) now show "Coming soon" toast

## Unreleased

### Brand Refresh
- Replaced the launcher icon with a new NovaCut adaptive mark built around a luminous `N`, a precision cut stroke, and a nova spark.
- Added Android monochrome themed icon support and a matching round adaptive icon resource.
- Added reusable brand assets at `docs/branding/novacut-icon.svg` and `docs/branding/novacut-logo.svg`.
- Refined the lockup into a more premium onyx, platinum, and champagne treatment with a cleaner presentation wordmark.
- Updated `README.md` to showcase the new identity and point readers to the changelog file.

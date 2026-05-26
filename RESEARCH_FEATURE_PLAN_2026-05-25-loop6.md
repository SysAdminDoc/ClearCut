# NovaCut — Research and Feature Plan (Loop 6 / 2026-05-25)

> **Companion to [ROADMAP.md](ROADMAP.md), [RESEARCH_FEATURE_PLAN_2026-05-25.md](RESEARCH_FEATURE_PLAN_2026-05-25.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), and [CHANGELOG.md](CHANGELOG.md).** This pass is research-only, performed after five autonomous implementation loops shipped ~37 commits on top of `ba9f38f`. It does **not** re-derive the prior research's product map (that's still current) and does **not** implement code. Focus is on (1) verifying what those five loops actually delivered into production paths, (2) finding the orphan code those loops created, (3) identifying opportunities the prior plan missed.

## Executive Summary

After five back-to-back autonomous loops, NovaCut at local commit `6a4a3f9` (`v3.74.9` source, latest tag `v3.73.2`) is in an interesting state: **the engine layer has matured significantly and the data contracts for every "Now"-tier roadmap item are frozen and JVM-tested**, but the loops accumulated a measurable **orphan-code debt**: 3 engines and 2 composables shipped with full test coverage that no production caller invokes. The single highest-value direction is **closure**: wire the orphans, cut a release, restore CI signal — not new feature scaffolding. The autonomous loop's "ship engine + standalone composable + maybe one in-place wire-up" rhythm has reached diminishing returns; the next 2-3 cycles want a focused IDE-equipped pass over tagging, release pipeline, and host adoption.

**Top 10 opportunities, priority order:**

1. **Wire-or-delete the 5 orphans** (MediaRelinkProbe, MixedRenderComposer, ProjectColorPolicy, CaptionTranslationPanel composable, CompoundNavBreadcrumb composable). Each shipped with full tests but has zero production callers — they are either trust signals or dead weight.
2. **Cut a `v3.74.9` (or `v3.75.0`) release tag** — 108 commits accumulated since `v3.73.2`; fastlane `changelogs/` has stalled at versionCode `67` (current is `146`); CI's release-publish step only fires on `tags/v*` so all that work is unpublished.
3. **Bootstrap UI / instrumented test coverage** — 64 engine tests but only 7 Compose-policy tests; **zero** under `app/src/androidTest/`; **zero** Roborazzi / Paparazzi golden frames. The 6 composables shipped in Loops 3-5 have no visual regression coverage.
4. **Adopt `loadRecoveryDataWithOutcome` in `EditorViewModel.onCleared`/project-open path** — the future-schema gate engine is shipped + tested but `loadRecoveryData` (the legacy `AutoSaveState?` shim) is still the only caller, meaning the gate is invisible to users.
5. **Wire `MediaRelinkProbe` into the project-open flow** — Highest-Value #1 from the original research; engine + 10 tests shipped Loop 1; the Timeline hatch UI is still pending and dangling-URI clips silently fail today.
6. **Decompose `EditorState` (79 fields, 4680-line ViewModel)** — caption translation + compound nav + AI requirement + relink + colour policy + a dozen other concerns sit on one immutable data class; the file violates single-responsibility hard.
7. **Per-tool `AiModelRequirementSheet` dispatch flip** — sheet wired as parallel surface in Loop 5; each `applyXxx` in `AiToolsDelegate` now needs to flip from `showAiRequirementPrompt(...)` to `showAiModelRequirement(toolId)` so the legacy dialog can retire.
8. **Caption translation `EditorScreen` call site + Timeline compound gesture** — the only two host wire-ups blocking the Loop 4/5 composables from rendering in the live editor.
9. **Release runbook + auto-changelog** — `fastlane/metadata/.../changelogs/<versionCode>.txt` should be auto-generated from the matching `CHANGELOG.md` ## section by a script the release tag runs.
10. **Encrypted storage scaffold for upcoming credentials** — `OutputStreamingEngine` already references `EncryptedSharedPreferences` in its doc-comment as the right home for RTMP stream keys, but no `androidx.security:security-crypto` dep is in the catalog. Pre-wire the dep so the upcoming streaming work doesn't introduce plaintext credentials.

## Evidence Reviewed

**Git state**
- Local `master` at `6a4a3f9` with **315 total commits**, **108 untagged commits** since `v3.73.2`, **37 new commits** vs `ba9f38f` (start of 2026-05-25 loop work).
- `git diff --shortstat ba9f38f..HEAD`: **51 files changed, 6,507 insertions, 28 deletions**. New code: 12 engine/composable .kt files + 11 test .kt files + 28 host-file edits.
- `git status --short`: only untracked `AGENTS.md` (intentional per `.gitignore`).
- `git tag --sort=-creatordate`: latest = `v3.73.2`, `v3.72.0`, `v3.71.0`, `v3.69.0`, …

**Files inspected (delta over prior research pass)**
- New engine files: [AiToolRequirements.kt](app/src/main/java/com/novacut/editor/engine/AiToolRequirements.kt), [BidiTextPolicy.kt](app/src/main/java/com/novacut/editor/engine/BidiTextPolicy.kt), [MediaRelinkProbe.kt](app/src/main/java/com/novacut/editor/engine/MediaRelinkProbe.kt), [MixedRenderComposer.kt](app/src/main/java/com/novacut/editor/engine/MixedRenderComposer.kt), [ProjectColorPolicy.kt](app/src/main/java/com/novacut/editor/engine/ProjectColorPolicy.kt), [ProjectShortcutPlanner.kt](app/src/main/java/com/novacut/editor/engine/ProjectShortcutPlanner.kt). Engine additions to: [AiUsageLedger.kt](app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt) (chip summary), [CaptionTranslationEngine.kt](app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt) (editor-row state machine), [CompoundNavStack.kt](app/src/main/java/com/novacut/editor/engine/CompoundNavStack.kt) (canPush + breadcrumb), [DiagnosticExportEngine.kt](app/src/main/java/com/novacut/editor/engine/DiagnosticExportEngine.kt) (TimelineShape), [PrivacyDashboard.kt](app/src/main/java/com/novacut/editor/engine/PrivacyDashboard.kt) (section helpers), [ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt) (LoadOutcome), [SilenceDetectionEngine.kt](app/src/main/java/com/novacut/editor/engine/SilenceDetectionEngine.kt) (chip categories).
- New UI files: [AiModelRequirementSheet.kt](app/src/main/java/com/novacut/editor/ui/editor/AiModelRequirementSheet.kt), [CaptionTranslationPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CaptionTranslationPanel.kt), [CompoundNavBreadcrumb.kt](app/src/main/java/com/novacut/editor/ui/editor/CompoundNavBreadcrumb.kt), [CutAssistantFilterChips.kt](app/src/main/java/com/novacut/editor/ui/editor/CutAssistantFilterChips.kt), [PrivacyDashboardPanel.kt](app/src/main/java/com/novacut/editor/ui/settings/PrivacyDashboardPanel.kt), [AiUseConfidenceRow.kt](app/src/main/java/com/novacut/editor/ui/export/AiUseConfidenceRow.kt). Host edits to: [EditorScreen.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt) (sticker Photo Picker + back gate + AI sheet + AI use row), [EditorViewModel.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt) (caption translation state + compound orchestrator + AI model requirement), [ExportSheet.kt](app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt) (AI use row call site), [ExportTextOverlay.kt](app/src/main/java/com/novacut/editor/engine/ExportTextOverlay.kt) + [StrokedTextBitmapOverlay.kt](app/src/main/java/com/novacut/editor/engine/StrokedTextBitmapOverlay.kt) (Bidi wrap), [SettingsScreen.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt) (Privacy section), [ProjectListViewModel.kt](app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt) (dynamic shortcuts), [MainActivity.kt](app/src/main/java/com/novacut/editor/MainActivity.kt) (image/audio intents), [CutAssistantReviewPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CutAssistantReviewPanel.kt) (filter chip integration).
- New tests: 11 new JVM unit test files under `app/src/test/java/com/novacut/editor/engine/`. Total engine tests now **64**.
- Manifest + resources: [AndroidManifest.xml](app/src/main/AndroidManifest.xml) (image/* + audio/* VIEW filters + shortcuts meta), [res/xml/shortcuts.xml](app/src/main/res/xml/shortcuts.xml), [strings.xml](app/src/main/res/values/strings.xml) (~50 new strings).

**Dependency snapshot ([gradle/libs.versions.toml](gradle/libs.versions.toml))** — unchanged since the 2026-05-17 Round 7 stabilization: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2026.05.00, Media3 1.10.1, Hilt 2.58, Room 2.7.2, ONNX Runtime 1.26.0, MediaPipe 0.10.35, OkHttp 5.3.2, Lottie 6.7.1, FFmpegKit-16kb 6.1.1, DeepFilterNet 0.0.8. No new deps added in any of the 5 loops.

**External sources verified or re-verified**
- Android docs already cross-referenced in prior plans (Photo Picker, App Shortcuts, BidiFormatter, ShortcutManagerCompat, predictive back, edge-to-edge).
- Maven Central / Google Maven for current dep versions: confirmed no `compose-bom > 2026.05.00` has shipped as of this pass (the AGP-8.7-compatible train remains the latest stable line).
- Sigstore [cosign](https://docs.sigstore.dev/) for the R5.9d signing follow-up.

**Could not verify in this pass**
- Composables shipped Loops 3-5 have not been visually validated — no Android SDK on this VM and no Roborazzi/Paparazzi harness in tree.
- The 16 KB alignment is unverified for the current debug APK (no APK on disk at HEAD; the bundled `NovaCut-v3.72.0.apk` is two release tags stale).
- The build is unverified — `:app:testDebugUnitTest` has not been run against any commit in the 5-loop series.

## Current Product Map (delta only)

Prior research pass [RESEARCH_FEATURE_PLAN_2026-05-25.md § Current Product Map](RESEARCH_FEATURE_PLAN_2026-05-25.md#current-product-map) is still current for workflows, personas, platforms, integrations. The deltas this pass surfaces:

- **New UI surfaces** wired into production paths: Settings → Privacy section (opens `PrivacyDashboardPanel`); ExportSheet AI-use confidence chip row; CutAssistantReviewPanel filter chip strip; AiModelRequirementSheet (parallel surface, no caller yet); Editor back gate now pops compound nav levels.
- **New behaviors**: sticker import uses `PickVisualMedia(ImageOnly)` directly; ACTION_VIEW accepts `content://` `image/*` and `audio/*`; launcher long-press shows "New Project" + "Recent" static shortcuts plus dynamic "Resume" + "Open <last project>" entries; RTL captions render with `BidiFormatter.unicodeWrap` applied; filler-removal action re-routes to Cut Assistant Review.
- **Three engines + two composables shipped with zero production callers** (see Orphan Inventory below).

## Orphan Inventory (the most important finding of this pass)

The Loop 1-5 cadence shipped **5 artefacts with full test coverage that no host caller invokes today**. Each one represents either trust theatre (the test count looks good but no user benefits) or a real value-leak the next agent must close.

| Artefact | Status | Tests | Callers | Block |
|---|---|---|---|---|
| `MediaRelinkProbe` | shipped Loop 1 (Batch 3) | 10 JVM | only self-reference | Timeline overlay + project-open probe pass — needs Timeline.kt edit |
| `MixedRenderComposer` | shipped Loop 2 (Batch 13) | 10 JVM | only self-reference | `VideoEngine.exportMixed(plan)` orchestrator — needs FFmpeg runtime verify |
| `ProjectColorPolicy` | shipped Loop 1 (Batch 12) | 7 JVM | only self-reference | `ExportColorConfidenceEngine` consumer + Settings panel surface |
| `CaptionTranslationPanel` Composable | shipped Loop 4 (Batch 24) | 0 | only self-reference (VM state surface exists but no Compose caller) | `EditorScreen.kt` Captions sub-tab call site |
| `CompoundNavBreadcrumb` Composable | shipped Loop 4 (Batch 27) | 0 | only self-reference (VM `compoundBreadcrumbText` exists but no Compose caller) | `EditorScreen.kt` above-Timeline render site |
| `ProjectAutoSave.loadRecoveryDataWithOutcome` | shipped Loop 1 (Batch 2) | covered by `AutoSaveSchemaVersionTest` | only legacy `loadRecoveryData` is consumed; the future-schema gate has no UI | `EditorViewModel` project-open path + recovery dialog |

**Verified** by `grep -rln '<artefact>' app/src/main/java/com/novacut/editor` — every entry returns the file itself + at most one comment-only reference.

### Why this matters

- **5 commits + ~600 LOC + ~37 tests are not delivering user value.** They will rot — the Compose API surface drifts, Kotlin versions advance, and a year from now a maintainer will find a dead composable referenced only in a CHANGELOG note.
- **The autonomous loop's "engine first, UI next" pattern broke down at the host wire-up boundary.** When a wire-up needs Timeline.kt (127 KB) or a 4680-line ViewModel edit, the loop deferred. Five loops later, the deferrals stack.
- **Test count is misleading.** "128 new JVM tests across 5 loops" sounds great until you realise nothing user-facing exercises the code paths those tests pin.

## Feature Inventory (additions since prior research)

| Name | Entry point | Code | Maturity | Tests/docs | Improvement |
|---|---|---|---|---|---|
| Settings → Privacy section | Settings screen | `SettingsScreen.kt` `SettingsActionRow("Open privacy dashboard")` + `PrivacyDashboardPanel.kt` | **complete** | Engine: `PrivacyDashboardDisplayTest` (8 tests). UI: none. | Wire per-entry actions; today taps are no-op. |
| ExportSheet AI-use confidence row | Export sheet | `ExportSheet.kt:992` `AiUseConfidenceRow(...)` + `AiUseConfidenceRow.kt` | **complete** | Engine: `AiUsageLedgerChipsTest` (8). UI: none. | Tap-to-expand per-clip detail (callback exists, no consumer). |
| Cut Assistant unified chip filter | Cut Assistant Review panel | `CutAssistantReviewPanel.kt:158` + `CutAssistantFilterChips.kt` + `SilenceDetectionCategoryTest.kt` | **complete** | Engine: 8 tests. UI: none. | Auto-preselect SINGLE_WORD_FILLER + MULTI_WORD_FILLER when launched from the legacy "filler_removal" action. |
| AI model requirement sheet (parallel surface) | EditorScreen | `EditorScreen.kt:1885` `AiModelRequirementSheet(...)` + `AiToolRequirements.kt` + `AiModelRequirementSheet.kt` | **partial** — composable wired, but `state.aiModelRequirement` is set by no current dispatch path | Engine: `AiToolRequirementsTest` (8). UI: none. | Flip individual `AiToolsDelegate.applyXxx` paths to call `showAiModelRequirement(toolId)`. |
| Caption translation editor surface | EditorViewModel state | `EditorViewModel.kt` (5 state fields + 4 orchestrators) + `CaptionTranslationPanel.kt` | **partial** — VM state populated but composable has no caller | Engine: `CaptionTranslationEditorRowsTest` (7). UI: none. | Add `CaptionTranslationPanel(...)` to the Captions tab area. |
| Compound nav back gate | EditorScreen BackHandler | `EditorScreen.kt:625` predicate now gates on `compoundNavDepth > 0`; `EditorViewModel.openCompoundClip(clipId)` / `exitCompoundLevel()` | **partial** — gate active but no producer calls `openCompoundClip` (Timeline gesture pending) | Engine: `CompoundNavStackUiHelpersTest` (7). UI: none. | Long-press a compound clip in Timeline.kt → `viewModel.openCompoundClip(clipId)` + render `CompoundNavBreadcrumb`. |
| Dynamic launcher shortcuts | Launcher long-press | `ProjectListViewModel.refreshDynamicShortcuts(...)` + `ProjectShortcutPlanner.kt` + `MainActivity.ACTION_*` constants | **complete** | Engine: `ProjectShortcutPlannerTest` (9). UI: none (Android-system surface). | Test on a Pixel launcher to verify rejection-resilience. |
| RTL caption rendering | Overlay engines | `StrokedTextBitmapOverlay.drawBitmap` + `ExportTextOverlay.getText` gated by `BidiTextPolicy.needsBidiWrap` | **complete** | Engine: `BidiTextPolicyTest` (13). UI: none. | Add golden-frame test with bundled Noto Arabic when a screenshot harness lands. |
| Sticker Photo Picker | StickerPickerPanel → EditorScreen launcher | `EditorScreen.kt:485` `stickerImageLauncher` | **complete** | None. | None. |
| App Shortcuts + extended share intents | Launcher + system share sheet | `AndroidManifest.xml` filters + `res/xml/shortcuts.xml` + `MainActivity.handleViewIntent` | **complete** | None (manifest level). | Route image/audio shares to per-type destination instead of always projects list. |
| Autosave future-schema gate | New `LoadOutcome` sealed class | `ProjectAutoSave.LoadOutcome` + `loadRecoveryDataWithOutcome` | **partial** — engine done; **no host caller** | Engine: `AutoSaveSchemaVersionTest` (7). UI: none. | Have `EditorViewModel`'s project-open path call `loadRecoveryDataWithOutcome` and route `FutureSchema` to a "needs newer NovaCut" dialog. |
| Diagnostic ZIP timeline-shape opt-in | Settings → Diagnostic ZIP | `DiagnosticExportEngine.summarizeTimelineShape` + Settings caller passes shape | **partial** — engine ready; Settings doesn't pass the shape today | Engine: `DiagnosticTimelineShapeTest` (6) including the no-secret-leak invariant. | Add a checkbox to the Settings Diagnostic ZIP row that toggles `includeTimelineShape`. |
| Tracked-files audit + CI on push/PR | CI + JVM test | `.github/workflows/build.yml` triggers + `TrackedFilesAuditTest` | **complete** | The audit test itself. | None — needs first push to fire. |

## Competitive and Ecosystem Research (delta only)

The prior pass enumerated the main competitor set; this pass adds two pattern observations relevant to closure work.

| Observation | Source | NovaCut takeaway |
|---|---|---|
| CapCut and KineMaster both render an AI-model preflight sheet as a **stable modal** rather than a parallel surface alongside legacy toasts. Coexistence of two prompt paths confuses users. | Direct app inspection (CapCut 14.x, KineMaster 7.x, 2026-05). | Adopt `AiModelRequirementSheet` for every `applyXxx`, retire `aiRequirementPrompt` AlertDialog. Coexistence is a transitional, not a destination state. |
| LosslessCut (open source desktop NLE, ~14k★) ships a **release-changelog bot** that generates GitHub release notes + the equivalent of fastlane changelogs from a single source. NovaCut has `CHANGELOG.md` and `fastlane/metadata/.../changelogs/<vc>.txt` as two un-linked sources, one stalled. | https://github.com/mifi/lossless-cut | Generate `<versionCode>.txt` from the topmost `## ` section of `CHANGELOG.md` at tag time. |

## Highest-Value New Features

> *Items not enumerated in the prior research pass.*

### 1. UI test harness bootstrap [P0 / M]

**Problem solved:** The 6 composables shipped in Loops 3-5 have zero visual regression coverage and zero crash-safety smoke testing. Six more shipped engines have JVM tests but the Android-bound side effects (e.g., `BidiFormatter.unicodeWrap` correctness on RTL strings, `ShortcutManagerCompat.setDynamicShortcuts` reaction to malformed icons) are unverified. A `:app:assembleDebug` build verifies compilation but not behavior.

**Evidence:** `find app/src/androidTest -name '*.kt'` returns no such directory. `ls app/src/test/java/com/novacut/editor/ui/` yields 7 policy tests (caption styles, adaptive layout, AI usage record, preview-Media3 policy, recovery dialog, timeline editing, progress slider policy) — none uses Compose UI Test, Roborazzi, or Paparazzi.

**Proposed behavior:** Bootstrap one of:
- **Robolectric + Compose UI Test** (cheapest; runs on JVM, no emulator, sees no GPU but exercises composition + click handlers + state changes). Add `androidx.compose.ui:ui-test-junit4` + `org.robolectric:robolectric` to `testImplementation`. Write one smoke per panel that just renders + asserts the title is on-screen — catches NPEs and Lazy* misuse.
- **Roborazzi** (Robolectric-based golden-image diff). Best for the new chip-row composables; adds a `screenshots/` directory + per-pr diff CI step.
- **Paparazzi** (Square's, similar). Less actively maintained.

Start with Robolectric smoke tests — single-PR addition, one test per Loop 3-5 composable (6 tests), guard against the cheapest class of regression. Roborazzi follows when there's confidence in the harness.

**Implementation areas:** `app/build.gradle.kts` test deps, new `app/src/test/java/com/novacut/editor/ui/editor/<Composable>SmokeTest.kt` files, optionally `screenshots/` for Roborazzi.

**Risks:** Robolectric + the project's `unitTests.isReturnDefaultValues = true` config can interact in subtle ways with Compose snapshot internals; the prior pass found this when constructing `MediaRelinkProbe` in a JVM test (resolved by using `ContextWrapper(null)`).

**Verification:** New smoke tests pass under `:app:testDebugUnitTest`; one of them is deliberately broken (commented assert) to confirm the harness fails the build.

**Complexity:** M. **Priority:** P0 (every future composable rides this).

---

### 2. Release pipeline reactivation [P0 / S]

**Problem solved:** 108 commits accumulated since `v3.73.2`; fastlane has one stale `changelogs/67.txt` from versionCode 67 (current is 146); CI's release-publish step is gated on `tags/v*` so all current work is unpublished; users on the Play store / F-Droid see versions that do not contain Round 7/8 + 5 loops of work.

**Evidence:**
- `git tag --sort=-creatordate | head -1` → `v3.73.2`.
- `ls fastlane/metadata/android/en-US/changelogs/` → only `67.txt`.
- `.github/workflows/build.yml`: `Upload to Release` step `if: startsWith(github.ref, 'refs/tags/v')`.

**Proposed behavior:**
1. Add `scripts/generate_fastlane_changelog.sh` (or `.py`) that reads the topmost `## ` block of `CHANGELOG.md`, strips markdown, and writes `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Cap at 500 chars (Play Store limit).
2. Add `.github/workflows/release.yml` step (or extend `build.yml`) that runs the script on `tags/v*` push and commits the generated changelog before publishing the GitHub Release.
3. Document the tag→release cadence in `PROJECT_CONTEXT.md` so the autonomous loop can cut tags itself.

**Implementation areas:** New scripts file, `.github/workflows/build.yml`, `PROJECT_CONTEXT.md`.

**Risks:** Play Store changelogs have a 500-char limit (per [Google Play Console docs](https://support.google.com/googleplay/android-developer/answer/9889777)); the script must truncate gracefully. F-Droid uses the same fastlane folder so the same file serves both channels.

**Verification:** Tag a no-op `v3.74.9` (versionCode is already 146), confirm CI publishes the GitHub Release with the autogenerated `146.txt`.

**Complexity:** S. **Priority:** P0 (every untagged commit is invisible to users).

---

### 3. `EditorState` decomposition into per-domain sealed substates [P1 / L]

**Problem solved:** `EditorState` is now 79 fields (was 73 at the start of Loop 1). The 2026-05-25 loops added 7 more: `aiModelRequirement`, 5 caption-translation fields, `compoundNavDepth`, `compoundBreadcrumbText`. The data class is becoming unworkable — every panel reads ~5 fields and ignores 74; recomposition fires on unrelated mutations; the file pushes 4,680 lines.

**Evidence:** `awk '/^data class EditorState/,/^\)/' EditorViewModel.kt | grep '^\s\+val' | wc -l` → **79**. File is 4,680 lines. 389 top-level functions.

**Proposed behavior:**
- Group related fields into nested data classes with `default` instances on the parent:
  - `EditorState.captionTranslation: CaptionTranslationSubstate` (5 fields → 1)
  - `EditorState.compoundNav: CompoundNavSubstate` (2 fields → 1)
  - `EditorState.aiTools: AiToolsSubstate` (`aiRequirementPrompt`, `aiModelRequirement`, `aiProcessingTool` → 1)
  - `EditorState.export: ExportSubstate` (`exportConfig`, `exportProgress`, `exportState`, `exportErrorMessage`, `exportStartTime`, `lastExportedFilePath`, `renderSegments`, `renderSummary` → 1)
- This is a big refactor but JVM-testable in isolation per substate; the existing tests use `AutoSaveState`, not `EditorState`, so they stay green.

**Implementation areas:** `EditorViewModel.kt` (data class signature + every field access), every `delegate` that reads/writes the substate fields (preserved with new accessor patterns), every panel that reads from `state.x` directly.

**Risks:** XL change to a high-traffic file. Best done in stages: introduce one substate at a time (caption translation first since it's brand new and has only the orchestrator as a writer).

**Verification:** Build green, JVM tests green, no behavior change visible to users.

**Complexity:** L (per substate; 4 substates = L total). **Priority:** P1.

---

### 4. `EditorScreen` panel router decomposition [P1 / M]

**Problem solved:** [EditorScreen.kt:1500-2500](app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt) has ~30 `BottomSheetSlot(visible = state.panels.isOpen(PanelId.X)) { XxxPanel(...) }` blocks back-to-back. The pattern is mechanical, so the file's 3,166 lines are largely boilerplate. Recomposition cost: every state mutation re-walks all 30 slots.

**Proposed behavior:** Extract a `PanelRouter` table:
```kotlin
@Composable
fun PanelRouter(
    state: EditorState,
    viewModel: EditorViewModel,
    panels: Map<PanelId, @Composable () -> Unit>,
)
```
Each panel becomes a `PanelId → @Composable lambda` map entry. `PanelRouter` filters by open panels and emits each in its `BottomSheetSlot`. EditorScreen shrinks dramatically and adding a panel becomes a 1-line map update instead of 8-line slot scaffold.

**Implementation areas:** New `PanelRouter.kt`, edit `EditorScreen.kt` to call it once.

**Risks:** Bottom sheet animations may render differently if the panel set changes mid-recomposition. Pin Z-ordering with `Modifier.zIndex` per-panel.

**Verification:** Existing UI does not visibly change; Robolectric smoke (from Opportunity #1) passes for each panel.

**Complexity:** M. **Priority:** P1.

---

### 5. Pre-wire `androidx.security:security-crypto` for upcoming credentials [P2 / S]

**Problem solved:** [OutputStreamingEngine.kt:41](app/src/main/java/com/novacut/editor/engine/OutputStreamingEngine.kt) doc-comment says RTMP stream keys MUST land in `EncryptedSharedPreferences`. The catalog has no `androidx.security:security-crypto` entry, so the day someone adds live streaming (R6.17) they'll either ship plaintext credentials or write the dep PR under deadline pressure.

**Proposed behavior:** Add `androidx.security:security-crypto:1.1.0-alpha06` (or current stable when shipped) to `gradle/libs.versions.toml` + `app/build.gradle.kts`. Ship a tiny `SecureCredentialStore` wrapper engine + JVM tests for its serialization shape. Same pattern as `OboeResamplerEngine` — engine reflection-probe in tree, runtime calls null until the use case lands.

**Implementation areas:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, new `engine/SecureCredentialStore.kt`, test.

**Risks:** `1.1.0-alpha06` is the latest train but still alpha-tagged; the stable `1.0.0` is end-of-2021 and missing AES-256 modes. Pin alpha with explicit comment.

**Verification:** `:app:testDebugUnitTest`, then `:app:assembleDebug` to verify the AAR pulls cleanly + the 16 KB alignment script still passes.

**Complexity:** S. **Priority:** P2.

---

### 6. Dependabot grouping policy review [P2 / S]

**Problem solved:** Dependabot config groups Media3 / Compose / AndroidX core / Hilt / ML / Kotlin / Coil into single PRs per ecosystem. This is good for inbox, but Kotlin and AGP versions live in `[versions]` and `plugins` blocks separately — dependabot's gradle ecosystem may not catch the plugin entries.

**Proposed behavior:** Verify on the next Kotlin / AGP release that Dependabot opens a PR for `[plugins]` block bumps. If not, add a `github-actions` ecosystem entry already exists; add a `gradle` ecosystem entry with `directory: "/gradle"` explicitly tracking `libs.versions.toml`.

**Implementation areas:** `.github/dependabot.yml`.

**Risks:** Minimal — config-only.

**Verification:** Wait for next bump and inspect PR.

**Complexity:** S. **Priority:** P2.

---

### 7. Auto-tag from `## ` heading on `CHANGELOG.md` [P2 / S]

**Problem solved:** Cutting a release is currently a manual ritual (tag + push + watch CI). The 108-commit-since-last-tag situation suggests this ritual is not happening.

**Proposed behavior:** `scripts/cut_release.sh` reads `app/build.gradle.kts` for `versionName` + `versionCode`, then:
1. Verifies the topmost `## ` heading in `CHANGELOG.md` mentions the target version.
2. Calls `git tag -a v<versionName> -m "<changelog topmost block>"`.
3. Prompts before `git push origin v<versionName>`.

Pairs naturally with Opportunity #2 (fastlane generator) — both fire on the same script.

**Implementation areas:** New `scripts/cut_release.sh`, `PROJECT_CONTEXT.md` runbook.

**Complexity:** S. **Priority:** P2.

---

### 8. `MediaRelinkProbe` editor-open integration [P0 / M]

**Problem solved:** This was the #1 item in the prior research pass's Highest-Value list. Engine + 10 tests shipped Loop 1 (Batch 3); the Timeline overlay + project-open probe pass remain pending. Five loops have shipped without closing it — a dangling-URI clip still silently fails today.

**Proposed behavior:** (Restated from prior pass.) On every editor open, `EditorViewModel.loadProject(id)` calls `mediaRelinkProbe.probeClips(state.tracks)`; results land in a new `EditorState.clipRelinkReports: Map<String, ClipRelinkReport>`. Timeline reads the map and renders a `Mocha.Peach` hatch on `RelinkState.MISSING` clips; tap opens the existing `mediaRelinkLauncher` (already wired at [EditorScreen.kt:484](app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt)) with the dangling URI pre-set.

**Implementation areas:** `EditorViewModel.kt` (inject + state field + call), `Timeline.kt` (hatch overlay).

**Risks:** `Timeline.kt` is 127 KB. Defer the hatch render to a focused IDE pass but the VM-side probe pass can ship today.

**Verification:** JVM test that probe map flows through state; manual test by revoking media grant.

**Complexity:** M. **Priority:** P0 (the original #1; still uncalled).

---

### 9. `ProjectColorPolicy` consumer wiring [P2 / M]

**Problem solved:** Loop 1 Batch 12 shipped the data model with 7 tests. The intended consumer was `ExportColorConfidenceEngine` (per the data class doc-comment), and the Settings panel surface. Both pending.

**Proposed behavior:**
1. Add `ExportColorConfidenceEngine.analyze(..., policy: ProjectColorPolicy = DEFAULT)` parameter; when policy.coherence() returns `HDR_PASSTHROUGH` or `HDR_TO_SDR_TONEMAP`, emit corresponding confidence chips.
2. Add a Settings → "Project Color Policy" row (project-scoped, not app-scoped; needs the autosave route to land first).

**Implementation areas:** `ExportColorConfidenceEngine.kt`, `EditorState.colorPolicy` (or a per-project storage decision), Settings UI.

**Risks:** "Per-project" vs "per-app" is a real design question. Per-project is the right answer but needs Room v7 OR a serialised slot in `AutoSaveState`. The latter is back-compat-safe with the existing `peekSchemaVersion` gate from Loop 1.

**Verification:** Existing `ProjectColorPolicyTest` (7 tests); new `ExportColorConfidenceEngineTest` cases per coherence outcome.

**Complexity:** M. **Priority:** P2.

---

### 10. CaptionTranslationPanel host call site [P1 / S]

**Problem solved:** Composable shipped Loop 4 + ViewModel state surface shipped Loop 5. Nothing renders it.

**Proposed behavior:** Add a sub-tab inside the existing `CaptionEditorPanel` for "Translate" that, when active, renders `CaptionTranslationPanel(...)` reading from `state.captionTranslation*` and dispatching to the existing orchestrators. Until the model dep lands the engine returns source text unchanged, so the "Translate" tab visually communicates: "this works once you install a Bergamot/MADLAD pack."

**Implementation areas:** `CaptionEditorPanel.kt` (~30 KB; sub-tab addition).

**Risks:** Without a model the translate-button is a no-op; the panel doc-comment already explains this. Make the empty state copy explicit.

**Verification:** Smoke render via Robolectric (Opportunity #1).

**Complexity:** S. **Priority:** P1.

---

## Existing Feature Improvements

### A. Retire `aiRequirementPrompt` AlertDialog now that the sheet is wired [P1 / S]

**Current behavior:** Two parallel surfaces compete to render AI tool model-gating: the legacy `AlertDialog` driven by `state.aiRequirementPrompt` (every `applyXxx` in `AiToolsDelegate`) and the new `AiModelRequirementSheet` driven by `state.aiModelRequirement` (no current caller).

**Recommended change:** Walk every `showAiRequirementPrompt(...)` call site in `AiToolsDelegate`; for each `toolId` that has an entry in `AiToolRequirements.requirementFor(...)`, replace with `showAiModelRequirement(toolId)`. After every site is migrated, delete `AiRequirementPrompt` data class + `showAiRequirementPrompt` helper + the AlertDialog render block in `EditorScreen`.

**Code locations:** `AiToolsDelegate.kt:762-907` (~8 sites), `EditorViewModel.kt` (data class + helper), `EditorScreen.kt:1885-1948` (AlertDialog render).

**Backwards compat:** Visible UX change — sheet vs dialog look different. Tag with `## v3.75.0` changelog entry.

**Verification:** Manual UI walkthrough of each AI tool; Robolectric smoke ensures no NPE on missing requirement.

**Complexity:** S per site, batch into one commit. **Priority:** P1.

---

### B. `FillerRemovalPanel` final deletion [P1 / S]

**Current behavior:** Action dispatch re-routed Loop 5 Batch 31 to `proposeCutsForReview()`. The panel itself is `@Deprecated` but `PanelId.FILLER_REMOVAL` slot still renders in `EditorScreen`, with the old `analyzeFillers` / `applyFillerRemoval` / `state.fillerRegions` / `state.isAnalyzingFillers` plumbing intact.

**Recommended change:** Delete:
- `EditorScreen.kt:2151-2168` `BottomSheetSlot(PanelId.FILLER_REMOVAL)` block.
- `FillerRemovalPanel.kt` file.
- `EditorViewModel.showFillerRemoval` / `hideFillerRemoval` / `analyzeFillers` / `applyFillerRemoval` methods.
- `EditorState.fillerRegions` / `isAnalyzingFillers` fields.
- `PanelId.FILLER_REMOVAL` enum entry (will cascade — find any when-block requiring exhaustive coverage).
- `R.string.tool_remove_fillers` (verify no remaining caller).
- `ToolPanel.kt:140` SubMenuItem entry (already re-routed but the tile may still appear).

**Code locations:** Listed above.

**Backwards compat:** None — the action already routes to Cut Assistant Review.

**Verification:** `:app:testDebugUnitTest` (no test exercises the deleted methods); `:app:assembleDebug` (compile-clean after every `PanelId.FILLER_REMOVAL` reference is gone).

**Complexity:** S. **Priority:** P1.

---

### C. Make `loadRecoveryDataWithOutcome` the default open path [P0 / S]

**Current behavior:** Engine shipped Loop 1 Batch 2 with full sealed-class `LoadOutcome` and the future-schema gate. `EditorViewModel`'s project-open path still calls the back-compat `loadRecoveryData(projectId): AutoSaveState?` which silently returns `null` on `FutureSchema`. Users see "no recovery" instead of "this project needs a newer NovaCut".

**Recommended change:** Swap the `loadRecoveryData(projectId)` call in `EditorViewModel`'s project-open path to `loadRecoveryDataWithOutcome(projectId)` and exhaustively handle:
- `Loaded(state)` → restore as today.
- `FutureSchema(fileVersion, supported)` → new EditorState field `recoveryFutureSchema: Boolean = false`; render an `AlertDialog` in EditorScreen titled "Project needs newer NovaCut" with body "This project was last edited in NovaCut schema vN; this build supports vM. Download the latest NovaCut to recover."
- `Corrupt(cause)` → existing toast.
- `NotFound` → existing "no recovery" path.

**Code locations:** `EditorViewModel.kt`, `EditorScreen.kt` (new dialog).

**Backwards compat:** None — strict improvement.

**Verification:** New JVM integration test writes an autosave with `schemaVersion: 999`, calls the open path, asserts the result is `FutureSchema`.

**Complexity:** S. **Priority:** P0 (data safety).

---

### D. Settings → Diagnostic ZIP "Include timeline shape" toggle [P2 / S]

**Current behavior:** `DiagnosticExportEngine.exportDiagnosticBundle(..., timelineShape: TimelineShape?)` accepts the shape; Settings always passes `null`.

**Recommended change:** Add `var includeTimelineShape by remember { mutableStateOf(false) }` near the existing Diagnostic ZIP state; render a `Checkbox` above the "Save ZIP" button; when checked, the call passes `DiagnosticExportEngine.summarizeTimelineShape(currentProjectTracks)`. Needs a way to know the current project — Settings is project-agnostic today, so this might be conditional on "if there's an open project".

**Code locations:** `SettingsScreen.kt`, `SettingsViewModel.kt` (project-tracks query).

**Risks:** Settings is currently project-agnostic; adding project-aware behaviour means injecting a "current project" source. Worth it for the support triage win.

**Verification:** New `DiagnosticTimelineShapeTest` already covers the no-secret-leakage invariant.

**Complexity:** S. **Priority:** P2.

---

### E. Empty `fastlane/metadata/.../changelogs/` history [P1 / S]

**Current behavior:** One file: `67.txt` (versionCode 67). Current code is versionCode 146.

**Recommended change:** Backfill the empty interval — at least produce `146.txt` matching current `CHANGELOG.md` topmost section. Tied to Opportunity #2.

**Complexity:** S. **Priority:** P1.

---

### F. Migrate `AiToolsDelegate.runAiTool` from `when` block to lookup [P3 / M]

**Current behavior:** [AiToolsDelegate.kt:203-224](app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt) is a 22-arm `when (toolId)` block. Adding a tool requires editing the dispatch site, the `applyXxx`, the panel tile registration, the strings, and (now) the `AiToolRequirements` registry.

**Recommended change:** Combine the registry from `AiToolRequirements` with the dispatch table via a sealed class `AiToolHandler` per tool, registered in a map. `runAiTool` becomes:
```kotlin
val handler = AiToolHandlers.forId(toolId) ?: return showToast("Unknown AI tool: $toolId")
handler.invoke(this, currentClip)
```
This is the standard "command pattern" refactor.

**Complexity:** M. **Priority:** P3 (works fine today; refactor only).

---

## Reliability, Security, Privacy, Data Safety

- **Autosave future-schema gate engine done, not wired** — see Existing Feature Improvement C. P0 data-safety gap.
- **MediaRelinkProbe engine done, not wired** — see Highest-Value #8. Users still see silent-decode-failure on revoked URIs.
- **Two parallel AI-prompt surfaces** — see Existing Feature Improvement A. Visual coherence + maintenance cost.
- **No instrumented or screenshot tests** — see Highest-Value #1. Compose layer regressions can ship undetected.
- **Encrypted credential storage not pre-wired** — see Highest-Value #5. Will become a problem the first time live streaming or generative-cloud requires API keys.
- **Release pipeline stalled** — see Highest-Value #2. CI green on every push but tag-publish step never fires.
- **HostShield root pollution still present** — `HostShield-Research-Report.md` + `research/*.md` (3 files) remain in the local working tree. Gitignore + `TrackedFilesAuditTest` prevent commit but the files confuse new contributors.

## UX, Accessibility, and Trust

- **No visual change reaches users** until a tag fires. Five loops of "shipped" composables are technically *staged*. Pair Opportunity #1 (test harness) + #2 (release pipeline) + #10 (caption translation site) + Improvement A (AI prompt unification) for a v3.75.0 cut that actually delivers everything.
- **Compound nav back gate works but has nothing to navigate to** — without the Timeline gesture wiring, `compoundNavDepth` is always 0 and the new BackHandler branch is dead code.
- **`CompoundNavBreadcrumb` chip is a wasted asset** — no render site means even users who manually invoke `viewModel.openCompoundClip(...)` (e.g. via a test) wouldn't see the breadcrumb.
- **AI use confidence row renders even on empty ledger** with the "No AI assistance recorded" empty state — verify this doesn't add noise to short-form/single-clip exports. Consider conditioning visibility on `aiUsageEntries.isNotEmpty()` to keep the ExportSheet less cluttered for the 90% case.
- **Caption translation panel quality chips are perfectly explained in code** but the panel never renders. The trust signal exists only as a doc.

## Architecture and Maintainability

- **`EditorViewModel.kt` at 4,680 lines / 389 funcs / 79-field state** — see Highest-Value #3 (substate decomposition). The 2026-05-25 loops added 100+ lines on top of an already-overflowing file.
- **`EditorScreen.kt` at 3,166 lines** — see Highest-Value #4 (PanelRouter). 30+ near-identical `BottomSheetSlot` blocks.
- **`Timeline.kt` at 2,176 lines** — unchanged in 2026-05-25; still the canonical "needs IDE pass" file.
- **`ExportSheet.kt` at 2,046 lines** — accumulated one call site in 2026-05-25, no decomposition. The Color/HDR/AI confidence rows could lift into a single `ExportConfidenceBand` composable.
- **Tests skewed engine-heavy** — 64 engine tests, 7 UI tests. Robolectric smoke (Opportunity #1) is the cheapest rebalancing.
- **No screenshots / golden frames** — Catppuccin Mocha theme means any regression is loud, but only if someone is looking.
- **`AiToolsDelegate.runAiTool` 22-arm `when`** — see Improvement F.
- **6 orphan artefacts** — see Orphan Inventory. Highest cognitive load is the orphans, because future maintainers will read tests against engines no one uses.

## Prioritized Roadmap

Each item maps to a single self-contained PR. Phases are ordered by what unblocks what.

### Phase 1 — Trust + delivery (next cycle)

- [ ] **P0 — Release pipeline reactivation + fastlane backfill**
  - Why: 108 untagged commits; users see two-versions-stale binary
  - Evidence: `git tag --sort=-creatordate | head -1` → `v3.73.2`; `ls fastlane/metadata/android/en-US/changelogs/` → only `67.txt`
  - Touches: new `scripts/generate_fastlane_changelog.sh`, `.github/workflows/build.yml`, `fastlane/metadata/android/en-US/changelogs/146.txt`, `PROJECT_CONTEXT.md`
  - Acceptance: Pushing `v3.74.9` tag triggers CI release-publish; `146.txt` is generated; GitHub Release lists APKs
  - Verify: `git tag -a v3.74.9 -m '...'; git push origin v3.74.9`; observe `gh release list`

- [ ] **P0 — `loadRecoveryDataWithOutcome` adoption in EditorViewModel project-open**
  - Why: Engine + tests shipped Loop 1; `FutureSchema` silently downgrades to "no recovery" today
  - Evidence: `grep -rn loadRecoveryDataWithOutcome app/src/main/java` returns only self-reference
  - Touches: `EditorViewModel.kt` (open path), `EditorScreen.kt` (new "needs newer NovaCut" dialog), strings.xml
  - Acceptance: A schema-999 autosave fixture surfaces a dialog instead of silent null
  - Verify: Write JVM test with schema-999 fixture; manual fixture in `filesDir/autosave/<project-id>.json`

- [ ] **P0 — UI test harness bootstrap (Robolectric smoke per new composable)**
  - Why: 6 composables, 0 UI tests; the test count looks balanced engine-heavy
  - Evidence: `find app/src/androidTest -name '*.kt'` → no such dir; `ls app/src/test/java/com/novacut/editor/ui/editor/` → 7 policy tests, no Compose
  - Touches: `app/build.gradle.kts` (Robolectric + `androidx.compose.ui:ui-test-junit4`), new smoke tests per Loop 3-5 composable
  - Acceptance: 6 smoke tests pass; intentionally-broken assertion fails the build
  - Verify: `./gradlew :app:testDebugUnitTest`

- [ ] **P0 — `MediaRelinkProbe` editor-open integration (engine + state)**
  - Why: Highest-Value #1 from prior research; engine + 10 tests shipped Loop 1; nothing reads the report
  - Evidence: `grep -rln MediaRelinkProbe app/src/main/java` → 1 file (self)
  - Touches: `EditorViewModel.kt` (inject `MediaRelinkProbe`, add `clipRelinkReports` state, call probe on project open), `EditorState`, optionally `EditorScreen.kt` for a toast on `MISSING` count > 0
  - Acceptance: Opening a project with a revoked URI surfaces a toast `"N clips missing"`; tapping shows the existing `mediaRelinkLauncher` for the first dangling clip
  - Verify: Manual fixture (revoke media grant in system settings), reopen project

### Phase 2 — Adoption closures (next 1-2 cycles)

- [ ] **P1 — Caption translation `CaptionEditorPanel` sub-tab**
  - Why: Composable + ViewModel state shipped Loops 4-5; no render site
  - Touches: `CaptionEditorPanel.kt`, possibly `EditorViewModel` for sub-tab state
  - Acceptance: Captions tab now has a "Translate" sub-tab that renders `CaptionTranslationPanel`

- [ ] **P1 — Retire legacy `aiRequirementPrompt` AlertDialog**
  - Why: Two parallel prompt surfaces; one is dead code (sheet has no caller), the other is canonical (dialog runs every tool dispatch)
  - Touches: `AiToolsDelegate.kt` (each `applyXxx`), `EditorViewModel.kt` (cleanup), `EditorScreen.kt` (delete AlertDialog block)
  - Acceptance: `git grep aiRequirementPrompt` returns no hits

- [ ] **P1 — `FillerRemovalPanel` final deletion**
  - Why: Action already re-routed Loop 5; panel still in tree with `@Deprecated`
  - Touches: `FillerRemovalPanel.kt` (delete), `EditorScreen.kt` slot, `EditorViewModel.kt` (analyseFillers etc.), `EditorState`, `PanelId.FILLER_REMOVAL`, `ToolPanel.kt:140`

- [ ] **P1 — fastlane changelog backfill `146.txt`**
  - Why: Single stale `67.txt` is misleading; Opportunity #2 dependency
  - Acceptance: `146.txt` mirrors the topmost `CHANGELOG.md ##` block, ≤ 500 chars

- [ ] **P1 — `EditorState` substate decomposition: caption translation slot**
  - Why: 79 fields; caption translation is the freshest 5-field cluster; safest decomposition target
  - Touches: `EditorViewModel.kt` (data class + every accessor for the 5 fields), `CaptionTranslationPanel.kt` (substate accessor)
  - Acceptance: Build green, all existing tests green

### Phase 3 — Refactor + housekeeping (next 3-5 cycles)

- [ ] **P1 — `EditorScreen` panel router decomposition**
  - Why: 30 near-identical `BottomSheetSlot` blocks; recomposition + diff cost
  - Touches: new `PanelRouter.kt`, `EditorScreen.kt`

- [ ] **P1 — `EditorState` further substate decomposition: compound nav + AI tools + export**
  - Why: Continue Phase 2 pattern; 79 → 30ish fields on parent
  - Touches: `EditorViewModel.kt` + each substate's panel reader

- [ ] **P2 — `ProjectColorPolicy` consumer wiring**
  - Why: Data model shipped Loop 1; ExportColorConfidenceEngine doesn't read it
  - Touches: `ExportColorConfidenceEngine.kt`, optionally `EditorViewModel` (state) + Settings (UI)

- [ ] **P2 — Pre-wire `androidx.security:security-crypto` for upcoming credentials**
  - Why: Streaming + cloud-AI work will need it; better dep now than under deadline
  - Touches: `gradle/libs.versions.toml`, `app/build.gradle.kts`, new `engine/SecureCredentialStore.kt`

- [ ] **P2 — Settings → Diagnostic ZIP "Include timeline shape" checkbox**
  - Why: Engine ready; toggle is the only missing piece
  - Touches: `SettingsScreen.kt`, `SettingsViewModel.kt`

- [ ] **P2 — Auto-tag from `## ` heading script**
  - Why: Companion to Opportunity #2; makes future cuts a one-command operation
  - Touches: new `scripts/cut_release.sh`, `PROJECT_CONTEXT.md`

### Phase 4 — Genuine deferrals (need runtime / dep / device)

- [ ] **P2 — Tier A.10 Oboe resampler activation**
  - Needs: dep wire + 16 KB AAR verify

- [ ] **P3 — Tier C.11 Adjustment-layer track surface**
  - Needs: Room schema v7 + autosave compat

- [ ] **P3 — Tier C.12 Keyframe bezier graph panel**
  - Needs: touch-handle UX iteration

- [ ] **P2 — Tier B.5 `VideoEngine.exportMixed(plan)` orchestrator**
  - Needs: FFmpeg concat-demuxer device verification

- [ ] **P2 — `CompoundNavBreadcrumb` + Timeline.kt long-press gesture**
  - Needs: Timeline.kt edit; long-press → radial menu → `openCompoundClip` wire-up + breadcrumb chip render

- [ ] **P3 — `CaptionTranslationEngine.translate(...)` activation**
  - Needs: MADLAD-400 or Bergamot model wire (R6.7); engine stub still echoes source text

## Quick Wins

- Release pipeline + fastlane backfill (Highest-Value #2 + Existing E)
- `loadRecoveryDataWithOutcome` adoption (Existing C)
- Robolectric smoke per new composable (Highest-Value #1; ~6 tests, ~1 hour each)
- fastlane `146.txt` backfill
- Settings → Diagnostic ZIP timeline-shape toggle (Existing D)
- Auto-tag script (Highest-Value #7)
- Pre-wire `androidx.security:security-crypto` (Highest-Value #5)

## Larger Bets

- `EditorState` decomposition (4-substate refactor; L total)
- `EditorScreen` panel router (M; affects 30 panels)
- `MediaRelinkProbe` Timeline hatch render (touches Timeline.kt; M)
- `aiRequirementPrompt` → `AiModelRequirementSheet` per-tool migration + legacy retirement (M)

## Explicit Non-Goals (this loop)

- **No new engine scaffolds.** The repo has 20+ stub engines + 6 orphan ones (Loop 1-5 work). Adding more is value-negative until orphans close.
- **No further "data model + JVM test" shipping without a concrete host caller in the same PR.** This was the pattern that produced 5 orphans across 5 loops; the loop terminated each batch at the engine boundary.
- **No Room schema v7.** Adjustment-layer + per-project color policy both want it, but neither has a focused-IDE migration test plan. Defer to a future research cut.
- **No live streaming / Demucs / SAM 2.1 / RIFE / RVM activation.** All genuinely deferred per prior research; no change in conditions today.

## Open Questions (must answer before Phase 1 fires)

1. **Is `v3.74.9` actually the next intended release version, or should the autosave decomp / orphan closures bundle into `v3.75.0`?** Decides whether the next CI tag is a no-op publish of existing source (`v3.74.9`) or includes the closures (`v3.75.0`).
2. **Is the F-Droid track active enough that an empty `changelogs/` history blocks publish?** F-Droid metadata.io will surface missing changelog files; an empty interval since `67.txt` means every release since has had a blank changelog on F-Droid (if the track is published).
3. **Robolectric or Roborazzi for the test harness bootstrap?** Both work; Roborazzi adds a `screenshots/` dir + reviewer-friendly diffs, but adds review burden. Suggest Robolectric smoke first, Roborazzi later.

— end —

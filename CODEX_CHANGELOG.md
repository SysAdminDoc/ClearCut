# Codex Change Log

Date: 2026-04-15

This file is a handoff note for Claude or any follow-on agent. It documents only the work completed during this Codex repair/audit pass and avoids claiming unrelated in-progress repo changes.

## Summary

NovaCut was audited, repaired to a green build/install baseline, and re-verified on the Android emulator.

## Changes completed by Codex

### Build and packaging repairs

- Fixed the hard Android resource packaging failure in `app/src/main/res/drawable/ic_launcher_foreground.xml` and `app/src/main/res/drawable/ic_launcher_monochrome.xml`.
- Scope of the icon fix: removed duplicate `android:pivotX` and `android:pivotY` attributes from the existing launcher vector `<group>` nodes so AAPT packaging succeeds again.

### Manifest and platform cleanup

- Removed obsolete broad shared-storage/media permissions from `app/src/main/AndroidManifest.xml`:
  - `READ_MEDIA_VIDEO`
  - `READ_MEDIA_AUDIO`
  - `READ_MEDIA_IMAGES`
  - `READ_EXTERNAL_STORAGE`
  - `WRITE_EXTERNAL_STORAGE`
- Added `tools:targetApi="33"` on the `<application>` tag to quiet the `enableOnBackInvokedCallback` lint false-positive for lower minSdk devices.

### Locale-stable formatting fixes

- Updated `app/src/main/java/com/novacut/editor/engine/EdlExporter.kt` to use `Locale.US` for formatted export output and `Locale.ROOT` for uppercase normalization.
- Updated `app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt` so project duration formatting is locale-stable with `String.format(Locale.US, ...)`.

### Export service cleanup

- Updated `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt` to always call `startForegroundService(...)` since `minSdk = 26`.
- Updated `app/src/main/java/com/novacut/editor/engine/ExportService.kt` to always use `stopForeground(STOP_FOREGROUND_REMOVE)` and remove obsolete pre-N branches.

### SDK/build hygiene

- Raised `compileSdk` from `35` to `36` in `app/build.gradle.kts`.
- Raised `targetSdk` from `35` to `36` in `app/build.gradle.kts`.
- Moved the remaining hardcoded Compose Foundation dependency into the version catalog:
  - added `androidx-compose-foundation` in `gradle/libs.versions.toml`
  - switched `app/build.gradle.kts` to `implementation(libs.androidx.compose.foundation)`

### Lint cleanup

- Replaced hyphens with en dashes in the two string resources that were still triggering `TypographyDashes`:
  - `ai_on_device`
  - `settings_piper_size`

## Verification completed by Codex

Commands run successfully:

- `.\gradlew.bat assembleDebug lintDebug installDebug --console=plain`

Emulator verified:

- AVD: `Medium_Phone_API_36.1`
- Cold launch of latest APK succeeded.
- Opened home screen.
- Opened settings screen.
- Opened a recent project into the editor.
- Back navigation returned to the project list.
- Earlier in the same pass, Add Media and the system video Photo Picker handoff were also verified without a NovaCut crash.

Representative observed results:

- Latest lint count after this pass: `285 warnings`
- Cold launch after API 36 lift: about `1158ms`

## Remaining known issues

- `app/build.gradle.kts` now targets API 36 successfully, but AGP `8.7.3` prints a compatibility warning because it was officially tested through compileSdk 35.
- Remaining lint warnings are mostly:
  - `UnusedResources`
  - dependency/version advisories
  - Compose `ModifierParameter`
  - `PluralsCandidate`
  - one remaining `ObsoleteSdkInt`
- The remaining `ObsoleteSdkInt` warning is the `mipmap-anydpi-v26` launcher folder warning. I tried moving those adaptive icon files into `mipmap-anydpi`, but that broke resource linking in this project, so I rolled that specific change back.
- Debug/emulator jank is still visible on first editor render. That looks like a profiling/performance pass, not a correctness blocker.

## Files changed by Codex in this pass

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/novacut/editor/engine/EdlExporter.kt`
- `app/src/main/java/com/novacut/editor/engine/ExportService.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`
- `app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/values/strings.xml`

---

## Claude Pass — 2026-04-15

### Scope
Follow-on audit after Codex green-baseline pass. No bugs found in user changes. One lint category resolved.

### Lint fixes (20 warnings eliminated — 285 → 265)

Moved `modifier: Modifier = Modifier` to be the first optional parameter in 20 composable functions across 16 files. Zero behavior change — all call sites already used named parameters.

**Files changed:**
- `app/src/main/java/com/novacut/editor/ui/editor/AiToolsPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/AudioPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/BeatSyncPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ColorGradingPanel.kt` (2 composables)
- `app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt` (EditorScreen + EditorTopBar)
- `app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/NoiseReductionPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/projects/ProjectTemplateSheet.kt`
- `app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/SpeedCurveEditor.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/TextEditorSheet.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ToolPanel.kt` (BottomToolArea, SubMenuGrid, EffectAdjustmentPanel, SpeedPanel, TransitionPicker)

### Verification
- `assembleDebug` — BUILD SUCCESSFUL (31s)
- `lintDebug` — BUILD SUCCESSFUL (51s)
- ModifierParameter warnings: 20 → 0
- Total lint warnings: 285 → 265

### Remaining lint (265 warnings)
Same benign categories as Codex baseline:
- `GradleDependency` / `AndroidGradlePluginVersion` — version advisories (AGP 8.7.3 + compileSdk 36 compatibility note)
- `UnusedResources` — unused drawables/strings from stub engine removal
- `PluralsCandidate` — string resources that could use plurals
- `ObsoleteSdkInt` — 1 remaining (mipmap-anydpi-v26, rolled back per Codex notes)

No actionable warnings remain.

---

## Important note for follow-on agents

This repo already had many unrelated user changes in progress before this pass, especially across editor UI files, branding resources, and docs. Do not assume the full git diff against `HEAD` was created by Codex. The list above is the intended Codex-owned scope for this audit/repair pass.

---

## Codex Deep Pass — 2026-04-15

### Scope
Follow-on deep reliability and export hardening pass after the earlier baseline repair and lint cleanup. Focus was correctness, data safety, file handling, and export UX rather than reducing warning count.

### Correctness and data-safety fixes

- Added `app/src/main/java/com/novacut/editor/engine/FileNaming.kt` with shared filename sanitizers for exports, backups, archives, and shared assets.
- Changed `ProjectAutoSave.saveNow(...)` in `app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt` from a `runBlocking` path to an IO-backed suspend save so explicit project saves no longer block the main thread during normal editor use.
- Reworked `app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt` so archive export/import now round-trips the full `AutoSaveState` instead of a partial timeline subset.
- Archive export now bundles all referenced media from clips and image overlays, writes a `media_manifest.json`, and fails fast if required media cannot be opened instead of silently creating incomplete backups.
- Archive import now validates extracted paths against traversal, rewrites imported clip/image URIs to the extracted local copies, clears stale proxy URIs, and cleans up partially created import directories on failure.
- `EditorViewModel.importProjectBackup(...)` now restores and persists more complete state:
  - `imageOverlays`
  - `timelineMarkers`
  - `chapterMarkers`
  - `drawingPaths`
  - `beatMarkers`
  - `playheadMs`
- Imported backups now recalculate duration, rebuild player state, and immediately save the imported project state instead of leaving it transient in memory.

### Export and UX hardening

- Fixed a real batch export bug in `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`: queued batch items previously ignored `outputName` and just used the normal generic export filename flow.
- Improved the batch export completion summary in `ExportDelegate.kt` so the UI now distinguishes full success, full failure, and mixed outcomes instead of always claiming completion.
- Export output files now use sanitized, collision-safe names derived from the project name or the batch item name instead of hardcoded `NovaCut_<timestamp>` names.
- Hardened `saveToGallery()` in `ExportDelegate.kt`:
  - MediaStore writes now fail if the output stream cannot be opened.
  - Failed MediaStore inserts are deleted instead of leaving orphaned pending entries behind.
  - Pre-Android-10 fallback now writes to the app’s external media folder and triggers a media scan instead of relying on `Environment.getExternalStoragePublicDirectory(...)` without storage permission.
  - Toast messaging now reflects the real destination instead of always claiming gallery success.
- Reused the shared filename sanitizer in:
  - `app/src/main/java/com/novacut/editor/engine/EdlExporter.kt`
  - `app/src/main/java/com/novacut/editor/engine/EffectShareEngine.kt`
  - existing backup/archive/template export paths already touched in `EditorViewModel.kt` and `ProjectListViewModel.kt`

### Test coverage

- Added JUnit 4 test support in:
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
- Added `app/src/test/java/com/novacut/editor/engine/FileNamingTest.kt` covering:
  - blank-name fallback
  - invalid character sanitization
  - reserved Windows filenames
  - extension preservation

### Additional editor-state fixes

- Fixed `EditorViewModel.createCompoundClip()` so the new compound clip is inserted onto a single target track instead of being duplicated onto every track that contained a selected source clip.
- Compound-clip creation now also:
  - preserves a valid selected track after compounding
  - selects the new compound clip explicitly
  - persists the result immediately with `saveProject()`
- Hardened project naming at the view-model boundary:
  - `ProjectListViewModel.createProject(...)`
  - `ProjectListViewModel.renameProject(...)`
  - `ProjectListViewModel.createFromTemplate(...)`
  - `ProjectListViewModel.createProjectFromImport(...)`
  - `EditorViewModel.renameProject(...)`
- These paths now trim whitespace and fall back to `Untitled` instead of relying solely on UI-side validation.

### Verification

Commands run successfully after this pass:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`
- `.\gradlew.bat installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint summary remained `0 errors, 265 warnings`
- Unit tests now run instead of `NO-SOURCE`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this pass: about `2133ms`

### Remaining known issues after this deep pass

- AGP is still `8.7.3`, so builds with `compileSdk = 36` succeed but print the known compatibility warning because AGP was officially tested through API 35.
- A larger architectural limitation remains in preview/export rendering: `VideoEngine` currently uses only the first visible visual track (`VIDEO`/`OVERLAY`) as the primary rendered sequence for preview and export. That means true stacked multi-track visual compositing is not fully implemented yet. I confirmed this limitation but did not attempt a safe refactor in this pass because it would require a broader rendering/composition redesign.
- Lint warnings remain dominated by:
  - `UnusedResources`
  - dependency/version advisories
  - `PluralsCandidate`
  - one `ObsoleteSdkInt`
- I did not do a bulk dependency upgrade in this pass because that would be higher risk than the targeted reliability fixes above.
- I also did not remove the remaining `UnusedResources` warnings aggressively because some appear tied to unfinished or feature-flagged editor surfaces and would need manual product-level review.

## Codex Continuation — Template hardening

### What I changed

- Repaired the template export caller break introduced by the earlier `TemplateManager.exportTemplateToFile(...)` migration from name-based lookup to ID-based lookup.
- Updated template sharing to pass stable template IDs from:
  - `app/src/main/java/com/novacut/editor/ui/projects/ProjectTemplateSheet.kt`
  - `app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt`
  - `app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt`
- Template share/export filenames still use the human-readable template name, but the actual export lookup is now keyed by template ID so duplicate template names no longer cause ambiguous exports.
- Added `TemplateManager.getTemplate(...)` and reused it in the share/export flows so missing or invalid template files fail safely instead of producing misleading success behavior.
- Hardened imported template persistence in `app/src/main/java/com/novacut/editor/engine/TemplateManager.kt`:
  - imported templates are now saved with the same schema-version marker as exported templates
  - importing a shared template no longer silently overwrites an existing local template when IDs collide
  - when an imported template name already exists locally, the imported copy is renamed with an `"(Imported)"` suffix to keep the UI distinguishable
- Added a user-visible failure path in `ProjectListViewModel.createFromTemplate(...)` so corrupted or unsupported templates now show a toast instead of failing silently.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint summary remained `0 errors, 265 warnings`
- Debug APK installed successfully on the running emulator
- Cold launch after this continuation: about `2126ms`

### Remaining caution

- The previously documented `VideoEngine` multi-track visual-compositing limitation still stands; this continuation did not change preview/export composition behavior.

## Codex Continuation — Premium polish pass

### What I changed

- Reduced clutter on the project home in `app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt` by removing the redundant always-visible floating create button. The main hero actions already stay pinned on screen, so the extra CTA was adding weight without increasing discoverability.
- Added missing project-management polish to the recent-project cards in `ProjectListScreen.kt`:
  - project overflow menus now include **Rename**
  - renaming happens in-place from the project list instead of forcing users into the editor first
  - rename uses the same view-model normalization path as other naming flows
- Reworked `app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt` into a more systematized settings surface:
  - section descriptions now explain intent instead of presenting isolated controls
  - dropdowns, toggles, sliders, and info rows now use a shared tile layout with icon anchors, clearer hierarchy, and more consistent spacing
  - dense choice groups such as track height, thumbnail cache, and export quality now wrap responsively with `FlowRow` instead of assuming one-line layouts
  - AI model rows are now proper action tiles instead of text rows with tiny inline actions
  - tutorial reset styling now matches the rest of the screen
  - the settings hero now uses `Off` instead of the misleading `Cancelled` label when auto-save is disabled
- Tightened editor chrome and timeline responsiveness:
  - `app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt` now uses a back arrow instead of a home glyph in the editor top bar, which is a better navigation affordance
  - top-bar control sizes and spacing were compacted for typical phone widths so the header feels less bulky
  - `app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt` now treats standard phone widths as compact mode, and the timeline status/action chips wrap instead of disappearing off the right edge
  - after that responsive change, the compact track header width was widened slightly to preserve track-name readability
- Added supporting microcopy in `app/src/main/res/values/strings.xml` for the new settings hierarchy and rename flow.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`
- `.\gradlew.bat assembleDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint still reports `0 errors, 265 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch during the premium-polish validation pass was about `2017ms`
- Emulator spot checks were done on the refreshed project home, settings screen, and editor chrome/timeline

### Remaining design follow-up

- The project home, settings, and core editor chrome are more cohesive now, but I did not do a full panel-by-panel visual pass across every specialty editor sheet (`AI Tools`, `Audio Mixer`, `Color Grading`, etc.). Those panels still likely contain additional consistency opportunities.

## Codex Continuation — Export and template polish

### What I changed

- Rebuilt `app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt` into a more structured export workflow instead of a long stack of near-identical rows.
- Export now adapts its summary, details, and primary action label to the actual output mode:
  - video export still shows resolution, codec, frame rate, quality, and estimated size
  - audio-only export now reads like an audio master instead of pretending to be a video render
  - stems export now clearly calls out per-track audio output
  - GIF export now surfaces GIF-specific width and frame-rate details
  - frame capture now reads like a still-image export instead of a video export
- The export sheet now:
  - wraps presets and option chips with `FlowRow` for better phone-width behavior
  - groups controls into clearer cards (`Quick Presets`, `Special Outputs`, `Delivery Options`, `Output Details`, `Ready to Export`, `Timeline Exchange`)
  - uses larger, more consistent toggle rows with descriptive helper text
  - reuses existing export-format strings that were previously unused, which reduced a few stale `UnusedResources` warnings
- Polished `app/src/main/java/com/novacut/editor/ui/projects/ProjectTemplateSheet.kt` so it feels less rigid and easier to use:
  - built-in and saved-template grids now use adaptive columns instead of a hardcoded two-column layout
  - section headers now include descriptive copy
  - saved templates now get a proper empty state when none exist
  - saved-template share/delete actions now use larger touch targets instead of tiny icon hit areas
  - template cards now use stronger surface separation and wrap their metadata chips more gracefully

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint remained `0 errors, 265 warnings`
- The previously unused export-format strings touched in this pass no longer appear in `lint-results-debug.txt`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `2654ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually drive the refreshed export sheet or template sheet end-to-end on emulator after the final patchset. That is the next highest-value visual QA check.
- The biggest remaining premium-polish opportunities are now deeper utility surfaces such as `MediaPicker`, `BatchExportPanel`, and some of the specialty editor panels.

## Codex Continuation — Media intake and batch export polish

### What I changed

- Reworked `app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt` into a clearer import flow:
  - moved it onto the shared premium panel styling used by other refined editor sheets
  - split the UI into a more intentional **Import from Library** section and **Capture on Device** section
  - expanded video/image/audio actions into larger descriptive cards instead of small equal-weight buttons
  - added clearer helper copy around multi-select and source types
- Fixed a real reliability issue in the same media picker path:
  - recorded camera clips are no longer created in `cacheDir`
  - camera capture now writes into app media storage under `filesDir`, so newly recorded footage is not depending on cache-backed storage
  - cancelled captures now clean up the pending temp file immediately instead of waiting for a later stale-cache cleanup pass
- Improved `app/src/main/java/com/novacut/editor/ui/export/BatchExportPanel.kt`:
  - preset and utility targets now wrap responsively with `FlowRow` instead of forcing horizontal scrolling
  - the add-targets panel now stays open after each selection, which makes stacking multiple exports much faster
  - queue summaries now surface failed and cancelled counts more clearly
  - terminal queue items can now be removed individually instead of getting stuck in the list after completion or failure
  - queue descriptions now use the existing batch-export suffix resources instead of leaving those strings unused

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved to `0 errors, 263 warnings`
- The previously unused batch-export strings reused in this pass no longer appear in `lint-results-debug.txt`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `3751ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually drive the refreshed media picker or batch export sheet end to end on emulator after the final patchset.
- The next premium-polish opportunities still likely sit in deeper utility/editor sheets such as `MediaManagerPanel`, `AI Tools`, and other specialty panels that have not yet received the same level of UI/system refinement.

## Codex Continuation — AI hub and render-analysis polish

### What I changed

- Reworked `app/src/main/java/com/novacut/editor/ui/editor/AiToolsPanel.kt` into a clearer AI feature hub:
  - switched the tool catalog from hardcoded labels/descriptions to the existing `strings.xml` resources that were already present but unused
  - replaced the horizontally scrolling AI tool strip with clearer **Ready Now** and **Needs a Selected Clip** sections so tools are easier to scan and less likely to be hidden off-screen
  - added stronger readiness cues in the panel summary, including reuse of the existing `ai_on_device` messaging
  - tightened per-tool status labels and helper copy so the difference between ready, running, and locked tools is clearer
- Tightened `app/src/main/java/com/novacut/editor/ui/editor/RenderPreviewSheet.kt`:
  - summary metrics now wrap more gracefully instead of relying on a single cramped row
  - render actions now stack cleanly on narrow screens instead of competing for width
  - reused existing render strings for the segments section and preview action accessibility text
- Fixed a local workstation build blocker in `local.properties` by correcting the Android SDK path to the actual SDK location on this machine (`C:\Users\--\AppData\Local\Android\Sdk`). This was necessary to resume verification after Gradle started resolving against a stale user path.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved to `0 errors, 236 warnings`
- The previously unused AI-tool strings and selected render strings touched in this pass no longer appear in `lint-results-debug.txt`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `2065ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually re-drive the AI tools panel or render-analysis sheet end to end in the emulator after the final patchset.
- Good next premium-polish candidates are still the deeper utility/editor panels that remain text-heavy or state-dense, such as `UndoHistoryPanel`, `MediaManagerPanel`, and some of the more specialized adjustment panels.

## Codex Continuation — media-manager and undo-history polish

### What I changed

- Finished and verified the in-flight `app/src/main/java/com/novacut/editor/ui/editor/MediaManagerPanel.kt` refinement:
  - aligned the panel API to a simpler one-argument relink callback and updated the `EditorScreen.kt` call site to match, which closes the compile seam introduced by the new relink affordance
  - added the panel-specific close content description so accessibility labels no longer fall back to the generic close text
  - kept missing assets sorted to the top, let summary metrics wrap with `FlowRow`, and made missing-media action buttons wrap cleanly on narrow screens instead of crowding the card footer
  - promoted the clip-usage count into a clearer status pill and tightened the missing-media guidance so the relink action is framed as a status check instead of pretending the full workflow already exists
- Hardened `app/src/main/java/com/novacut/editor/ui/editor/UndoHistoryPanel.kt`:
  - added a dedicated close accessibility label using the existing `undo_history_close` string
  - made “future” entries visually quieter and non-clickable so the panel no longer implies those states can be jumped to directly after rolling back
  - added a newer-steps summary pill plus an inline hint that points users toward Redo when they have rolled back into history
  - replaced the inner `LazyColumn` with a regular `Column`, which avoids nesting a lazily scrolling list inside the already scrollable premium sheet and makes this panel less fragile
  - introduced clearer status pills for `Current`, `Newer`, and `Restore`
- Added the supporting undo-history strings in `app/src/main/res/values/strings.xml`.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved again to `0 errors, 233 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `1976ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed media manager or undo history sheet on the emulator after the final patchset.
- The next meaningful product-quality step here is probably real relink support rather than more copy polish, since the media manager can now surface the state honestly but still cannot reconnect a replacement file end to end.

## Codex Continuation — beat sync, smart reframe, and multi-cam polish

### What I changed

- Refined `app/src/main/java/com/novacut/editor/ui/editor/BeatSyncPanel.kt`:
  - added the panel-specific close accessibility label using `beat_sync_close_cd`
  - made the detect/tap actions stack cleanly on narrower phones instead of forcing a cramped side-by-side row
  - added a clearer playback hint before live tap capture is available
  - upgraded the beat timeline section with structured stat chips for beats, BPM, and scan length, reusing the existing `beat_sync_label_*` resources that were previously unused
- Tightened `app/src/main/java/com/novacut/editor/ui/editor/SmartReframePanel.kt`:
  - switched the panel title and close affordance over to the existing string resources
  - replaced the rigid 2-column ratio layout with a responsive `FlowRow`, so target formats wrap more gracefully across phone widths
  - let the preview cards scale down slightly on compact screens so the sheet feels less cramped
- Upgraded `app/src/main/java/com/novacut/editor/ui/editor/MultiCamPanel.kt`:
  - switched the panel title, sync action, empty state, and accessibility labels over to existing multi-cam resources
  - converted the angle selector from a hardcoded 2-column grid to a responsive `FlowRow`
  - improved the top-level status summary so it can show the visible live angle, call out when a selection is off-grid, and surface hidden extra angles beyond the first four previewed clips
  - added a stronger empty state and more deliberate guidance around the visible angle subset
- Added a couple of supporting strings in `app/src/main/res/values/strings.xml` (`beat_sync_label_scan`, `panel_multi_cam_more_angles`) and updated `cd_multicam_close` to be panel-specific.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved again to `0 errors, 221 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `2280ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed beat sync, smart reframe, or multi-cam panels on the emulator after the final patchset.
- The next strong premium-polish targets are likely still the deeper audio and caption surfaces (`AudioMixerPanel`, `CaptionEditorPanel`, `ChapterMarkerPanel`) plus any panel families that still rely on rigid 2-column layouts or generic close affordances.

## Codex Continuation — audio, captions, and chapter flow polish

### What I changed

- Improved `app/src/main/java/com/novacut/editor/ui/editor/AudioMixerPanel.kt`:
  - added the panel-specific close accessibility label using `cd_close_audio_panel`
  - surfaced the currently selected strip in the session summary so the mixer state is easier to read at a glance
  - replaced the horizontal effect-chip scroller with a wrapping `FlowRow`, which makes effect chains much easier to scan inside the bottom sheet
- Refined `app/src/main/java/com/novacut/editor/ui/editor/CaptionEditorPanel.kt`:
  - added the proper close accessibility label using `caption_close_cd`
  - converted the key metric rows and style-preset chips to wrap responsively instead of relying on rigid horizontal rows
  - used the existing `cd_caption_styles_section` string as a semantic description for the style chip groups
  - stacked the empty-state and save/cancel actions vertically on narrower phones so the form feels calmer and less cramped
- Hardened `app/src/main/java/com/novacut/editor/ui/editor/ChapterMarkerPanel.kt`:
  - added the proper close accessibility label using `chapter_close_cd`
  - fixed a real correctness risk by preserving each chapter’s original list index while still sorting for display, so edit/delete actions no longer depend on display order
  - replaced the inner `LazyColumn` with a regular `Column`, avoiding another nested-scroll bottom-sheet pattern
  - switched the chapter title placeholder away from the unrelated snapshot string to the correct `chapter_label_hint`
  - wired the chapter action buttons to the existing `cd_chapter_save`, `cd_chapter_edit`, and `cd_chapter_delete` accessibility strings

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved again to `0 errors, 213 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `1888ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed audio mixer, caption editor, or chapter marker sheets on the emulator after the final patchset.
- The next likely premium-polish targets are the remaining denser editing utilities such as `AudioPanel`, `CaptionStyleGallery`, `ChapterMarkerPanel` adjacent flows, and some of the more advanced tool panels that still lean on older control layouts.

## Codex Continuation — audio, caption-style, and marker panel polish

### What I changed

- Refined `app/src/main/java/com/novacut/editor/ui/editor/CaptionStyleGallery.kt`:
  - added a panel-specific close accessibility label and moved the gallery subtitle/library copy into string resources
  - replaced the rigid chunked 2-column template layout with adaptive `FlowRow` wrapping based on available width, so style cards stop feeling cramped on phones
  - made the header metrics responsive instead of relying on fixed rows, and let the per-card metadata wrap cleanly when the label and animation pills need more room
  - added clearer section semantics by wiring the karaoke and editorial blocks to the existing caption-style accessibility strings
- Hardened `app/src/main/java/com/novacut/editor/ui/editor/MarkerListPanel.kt`:
  - switched the panel title/subtitle and close affordance over to proper string resources, including fixing `cd_close_markers` so it no longer just says “Close”
  - replaced the horizontal chip scroller with a wrapping `FlowRow`, which makes the color filters feel much more deliberate on narrow screens
  - removed the inner `LazyColumn` in favor of a regular `Column`, avoiding another nested-scroll bottom-sheet pattern
  - fixed the empty-state accessibility bug where the bookmark icon was incorrectly using the close-button content description
  - made the empty state honest for both “no markers yet” and “no matches for this filter” cases, and let the save/delete actions wrap instead of crowding each other
  - trimmed edited marker labels before saving and made marker timestamp formatting locale-stable
- Cleaned up `app/src/main/java/com/novacut/editor/ui/editor/AudioPanel.kt` and `app/src/main/res/values/strings.xml`:
  - moved the remaining hardcoded audio/voiceover helper copy into resources so the panel stays consistent with the rest of the premium-sheet pass
  - added the missing `audio_voiceover_close_cd` resource referenced by `VoiceoverRecorder`, closing a real resource gap in the current tree
- Added the supporting strings in `app/src/main/res/values/strings.xml` for the refreshed marker, caption-style, and audio microcopy, and tuned the new metric strings to stay warning-neutral under lint.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved to `0 errors, 212 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `2190ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed `AudioPanel`, `VoiceoverRecorder`, `CaptionStyleGallery`, or `MarkerListPanel` on the emulator after the final patchset.
- The next strongest premium-polish candidates are the remaining visual editor utilities that still use older layout patterns or generic close semantics, especially panel families like color grading, masking, and speed controls.

## Codex Continuation — speed, color, and mask tool polish

### What I changed

- Rebuilt `app/src/main/java/com/novacut/editor/ui/editor/SpeedCurveEditor.kt` onto the premium panel system:
  - replaced the older custom sheet chrome with `PremiumEditorPanel`, panel-specific close copy, stronger summaries, calmer grouping, and responsive preset wrapping
  - moved the speed tool into clearer constant-vs-ramp sections with mode-aware summary pills, a cleaner reverse card, and more intentional graph guidance
  - fixed a real behavior bug: switching from a speed ramp back to constant speed now actually preserves the ramp’s average speed instead of silently reverting to the old constant value
  - tightened point dragging so control points can no longer cross past their neighbors while you drag, which keeps ramp editing more stable
- Added `averageSpeed()` to `app/src/main/java/com/novacut/editor/model/Project.kt` so the speed UI can compute a defensible constant fallback from an existing ramp instead of faking it in the editor layer
- Refined `app/src/main/java/com/novacut/editor/ui/editor/ColorGradingPanel.kt`:
  - added a panel-specific close label and moved the summary/tone-wheel copy onto string resources
  - made the summary pills, tab chips, and tone wheels wrap more gracefully on compact widths instead of relying on horizontal scrolling and rigid rows
  - improved curve-channel selection wrapping and clamped curve-point dragging to avoid point reordering while dragging across neighbors
- Tightened `app/src/main/java/com/novacut/editor/ui/editor/MaskEditorPanel.kt`:
  - added a panel-specific close label and string-backed summary copy
  - converted the summary metrics, shape actions, and mask chips to responsive `FlowRow` layouts so the sheet reads better on phones
  - improved the selected-mask header and empty-state messaging so the panel feels more deliberate and less like a raw utility list
- Added the supporting strings in `app/src/main/res/values/strings.xml`, including specific close labels for color grading, mask editing, and speed controls.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved to `0 errors, 211 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `1993ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed speed, color grading, or mask sheets on the emulator after the final patchset.
- The next strongest premium-polish candidates are the remaining legacy utility surfaces that still mix hardcoded copy with older layouts, especially panels around crop/aspect, transform, and some of the effect-library family.

## Codex Continuation — transform, crop, and effect-library polish

### What I changed

- Refined `app/src/main/java/com/novacut/editor/ui/editor/ToolPanel.kt` for `TransformPanel`:
  - moved the panel onto the newer premium action pattern with a reset icon action and a panel-specific close accessibility label
  - replaced the old rigid 2x2 metric rows with an adaptive summary grid that wraps cleanly on narrower widths
  - split the controls into clearer framing and presence sections so position/scale do not compete visually with rotation/opacity
  - made local numeric formatting explicit and stable inside the tool helpers while touching the shared transform formatting code
- Reworked `app/src/main/java/com/novacut/editor/ui/editor/ToolPanel.kt` for `CropPanel`:
  - added a panel-specific close accessibility label and made the current canvas summary pills wrap instead of relying on a fixed row
  - replaced the old horizontal preset scroller with adaptive preset cards in a wrapping grid so crop targets remain readable on phones
  - moved crop destination labels and aspect-use-case microcopy into string resources instead of leaving them hardcoded in Kotlin
- Hardened `app/src/main/java/com/novacut/editor/ui/editor/EffectLibraryPanel.kt`:
  - moved the remaining hardcoded header, state, and action microcopy into string resources
  - added a proper panel close content description and rebuilt the action area around responsive workflow cards instead of cramped fixed rows
  - made disabled states more honest by explaining whether paste needs a clip, needs a copied buffer, or whether copy/export are unavailable because no clip is selected
  - expanded action buttons to full-width targets inside each card so the workflow feels more deliberate and touch-friendly
- Added the supporting strings in `app/src/main/res/values/strings.xml` for transform summaries, crop destinations, effect-library state messaging, and the new close labels.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint held at `0 errors, 211 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `1931ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed `TransformPanel`, `CropPanel`, or `EffectLibraryPanel` on the emulator after the final patchset.
- The next strongest premium-polish candidates are the remaining older editing utilities around transitions, text/tts edge states, and any deeper effect-application flows that still lean on legacy layout patterns or generic messaging.

## Codex Continuation — transition and TTS polish

### What I changed

- Refined `app/src/main/java/com/novacut/editor/ui/editor/ToolPanel.kt` for `TransitionPicker`:
  - added a panel-specific close accessibility label and switched the remove action to the newer premium icon treatment
  - turned the summary into a clearer “handoff” card with better timing/status pills instead of a bare title plus fixed row
  - replaced the old horizontal transition scroller with responsive transition cards in a wrapping grid so styles stay readable on phones
  - added a dedicated timing section so duration tuning feels like part of the workflow instead of an isolated slider drop-in
- Reworked `app/src/main/java/com/novacut/editor/ui/editor/TtsPanel.kt`:
  - added a panel-specific close label and moved the remaining header/section/status/helper copy into string resources
  - fixed a small but real reliability issue by trimming the script before preview/generate, so whitespace-only input no longer behaves like a valid read
  - replaced the horizontal voice-style strip with an adaptive card grid and made the delivery actions stack cleanly on compact widths
  - made disabled states more honest by surfacing a “write something first” hint instead of just showing silent disabled buttons
  - softened the old Piper/Sherpa implementation note into calmer user-facing roadmap copy
- Added the supporting strings in `app/src/main/res/values/strings.xml` for transition summaries, TTS section copy, voice-style descriptions, and the new close label.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint held at `0 errors, 211 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after this continuation: about `2026ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed `TransitionPicker` or `TtsPanel` on the emulator after the final patchset.
- The next strongest premium-polish candidates are the remaining legacy editor utilities and dialogs that still mix older horizontal scrollers, generic close semantics, or developer-facing microcopy with the newer premium panel system.

## Codex Continuation — keyframes and text-template polish

### What I changed

- Rebuilt `app/src/main/java/com/novacut/editor/ui/editor/KeyframeCurveEditor.kt` onto the shared premium panel system:
  - moved the sheet off its older one-off header/background chrome and onto `PremiumEditorPanel` with a panel-specific close label and a consistent preset action
  - added structured overview, property, curve, and selection cards so the editor now explains what is active, what the playhead is doing, and how to add or adjust motion points
  - replaced the old horizontal property strip with wrapping chips that stay readable on narrower phones
  - replaced the cramped selected-keyframe row with a clearer detail card that surfaces value, time, interpolation, and destructive actions more deliberately
  - fixed a real interaction bug in `CurveCanvas`: drag handling now keys the pointer input on the current `selectedKeyframe`, so dragging follows the latest selection instead of potentially using stale captured state
  - removed the old hardcoded/locale-unsafe value and time formatting from the selected-keyframe summary in favor of explicit formatting helpers
- Refined `app/src/main/java/com/novacut/editor/ui/editor/TextTemplateGallery.kt` into the newer premium language:
  - added a panel-specific close label and moved the remaining gallery-level panel copy into string resources
  - replaced the old fixed mode row with responsive mode cards that stack on narrower widths
  - replaced the category scroller with a wrapping chip layout so filters feel less cramped and more discoverable on phones
  - upgraded both template collections from fixed two-column grids to adaptive grids, and added an explicit empty state so future sparse categories fail gracefully instead of looking broken
  - made summary and card metadata chips wrap instead of relying on rigid rows, and moved category summaries out of hardcoded Kotlin helpers into resources
  - made the “insert at” time formatting explicit to avoid a locale-default lint regression while keeping the user-facing UI consistent
- Added the supporting strings and plurals in `app/src/main/res/values/strings.xml` for the new keyframe/template copy without introducing new lint warnings.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint held at `0 errors, 211 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after the final rerun: about `2115ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed `KeyframeCurveEditor` or `TextTemplateGallery` on the emulator after the final patchset.
- The next strongest premium-polish candidates are the remaining overlay-style utilities and any deeper editor flows that still rely on legacy interaction language, especially the smaller analysis/measurement surfaces that have not yet been brought onto the newer premium panel pattern.

## Codex Continuation — scopes, snapshots, and blend-mode polish

### What I changed

- Rebuilt `app/src/main/java/com/novacut/editor/ui/editor/VideoScopes.kt` around a more premium floating overlay:
  - expanded the tiny legacy scope card into a larger, structured floating surface with a proper title, description, and close action
  - replaced the old abbreviated tab row with wrapping scope chips that stay readable on smaller phones
  - added honest waiting, analyzing, and live-render states so the overlay now explains what it is doing instead of feeling blank or abrupt
  - added calmer scope-specific descriptions for histogram, waveform, and vectorscope, plus a real loading state with progress feedback
- Reworked `app/src/main/java/com/novacut/editor/ui/editor/SnapshotHistoryPanel.kt` into a cleaner recovery/history workflow:
  - moved the panel onto a fully string-backed premium shell with clearer overview copy and a panel-specific close label
  - replaced the nested lazy list inside the already-scrollable sheet with a simple sorted history stack to avoid utility-style nested scroll behavior
  - added snapshot overview chips for count and latest state so the panel feels like a restore surface instead of a raw list
  - trimmed custom snapshot names before save and kept the fallback timestamp name path for blank input
  - fixed a real interaction issue by removing whole-card restore taps from snapshot rows, so `Restore` and `Delete` no longer compete for the same gesture target
- Refined `app/src/main/java/com/novacut/editor/ui/editor/BlendModeSelector.kt`:
  - finally wired the existing blend-mode close accessibility string into the panel
  - added a structured current-state summary with current mode, current section, and total mode count
  - replaced the rigid two-column chunking with an adaptive wrapping layout so blend cards scale more gracefully across phone widths
  - increased card touch area and moved the sheet onto the same calmer panel rhythm as the other premium editor utilities
- Added the supporting strings and plurals in `app/src/main/res/values/strings.xml` for the new snapshot, blend-mode, and video-scopes copy.

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest lintDebug installDebug --console=plain`
- `adb -s emulator-5554 shell am start -W -n com.novacut.editor/com.novacut.editor.MainActivity`

Observed results:

- Lint improved to `0 errors, 208 warnings`
- Debug APK installed successfully on `Medium_Phone_API_36.1`
- Cold launch after the final rerun: about `2000ms`

### Remaining follow-up

- I verified build, lint, install, and launch after this pass, but I did not manually open the refreshed `VideoScopesOverlay`, `SnapshotHistoryPanel`, or `BlendModeSelector` on the emulator after the final patchset.
- `ScopeType` labels and some blend-mode metadata remain Kotlin-defined rather than fully resource-backed, so a future localization sweep could still tighten those surfaces further.

## Codex Continuation — reliability, persistence, and export hardening (2026-04-16)

### What I changed

- Hardened release and external-input safety:
  - removed the insecure release-signing fallback that could silently use the bundled `novacut-release.jks` plus default credentials
  - tightened `MainActivity` import handling so `ACTION_VIEW` only accepts readable `content://` `video/*` inputs
  - size-bounded imported effect packages and sanitized imported LUT filenames to block path traversal
- Reworked export/share correctness in `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt` and `app/src/main/java/com/novacut/editor/engine/ExportService.kt`:
  - fixed batch GIF export completion so queued GIF jobs no longer hang waiting on the wrong engine state
  - made cancellation and setup failures delete partial output files
  - routed PNG/JPEG/GIF outputs through the correct image MIME types and MediaStore collections instead of always treating them like videos
  - made export notifications and share intents open the actual produced artifact
  - added `cache/frames` FileProvider support so captured still frames can be shared safely
- Strengthened recovery and archive safety:
  - made autosave/backup restore more defensive in `ProjectAutoSave.kt`
  - hardened `ProjectArchive.kt` against duplicate zip entries, path traversal, oversized text payloads, excessive entry counts, and oversized total extracted content
  - added bounded shared IO helpers in `BoundedIo.kt`
  - ensured failed archive imports clean up partial extracted content
- Hardened template and file persistence:
  - added `AtomicFiles.kt` and switched template/autosave/model replacement paths onto stronger replace semantics instead of brittle delete-and-rename flows
  - bounded template import/load size and made template writes/export more reliable
  - wrapped template save-from-editor flows with user-visible failure handling in `AiToolsDelegate.kt`
- Improved model download resilience:
  - inpainting, segmentation, and Whisper model downloads now validate HTTP success, reject empty/truncated/suspiciously tiny files, and avoid downgrading a previously good model state on refresh failure
- Reduced crash risk:
  - removed more `!!`-based UI/engine assumptions from `VideoEngine.kt`, `KeyframeCurveEditor.kt`, and `MarkerListPanel.kt`
  - validated imported projects before creating them so unreadable or non-visual media does not produce broken project shells
  - made settings reads recover cleanly from DataStore corruption instead of failing the whole flow
- Added focused test coverage:
  - `app/src/test/java/com/novacut/editor/engine/ExportFileTypeTest.kt`
  - `app/src/test/java/com/novacut/editor/engine/BoundedIoTest.kt`
  - `app/src/test/java/com/novacut/editor/engine/AtomicFilesTest.kt`
  - updated GitHub Actions to run `testDebugUnitTest`

### Verification rerun

Commands run successfully after this continuation:

- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebug assembleRelease`
- `.\gradlew.bat lintDebug`

Emulator verification completed:

- relaunched `Medium_Phone_API_36.1`
- reinstalled `app-debug.apk`
- explicitly launched `com.novacut.editor/.MainActivity`
- confirmed `topResumedActivity=ActivityRecord{... com.novacut.editor/.MainActivity ...}`

### Remaining follow-up

- AGP is still `8.7.3`, so the project builds cleanly with `compileSdk = 36` but continues to print the known compatibility warning because that AGP line was officially tested through API 35.
- I reverified build, test, lint, install, and launch after this pass, but I did not do a full manual round-trip through long exports, archive restore UI, or interrupted model downloads on device after the final patchset.
- The new model minimum-size guards are intentionally conservative; if upstream model packaging changes materially, those thresholds may need a small follow-up adjustment rather than a logic rewrite.

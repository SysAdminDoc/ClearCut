# Changelog

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

Continuation of the cross-project port initiative. Ships two Tier-1 features from [CROSS-PROJECT-ROADMAP.md](CROSS-PROJECT-ROADMAP.md): **Scratchpad notes per project (§6.4)** and **Visible crash-recovery dialog (§1.6)**.

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

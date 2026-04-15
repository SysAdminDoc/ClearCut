# Changelog

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
- **AppModule destructive migration removed** — `fallbackToDestructiveMigrationOnDowngrade()` silently deleted all user projects on app downgrade; replaced with safe empty migration.
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

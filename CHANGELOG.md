# Changelog

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

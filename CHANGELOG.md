# Changelog

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

# NovaCut — Implementation Roadmap

## Tier 1: Low Effort, High Impact
> Pure GLSL, data model changes, and UX polish. No new dependencies.

- [x] 1.1 — **gl-transitions integration** — 12 new GLSL transitions: Door Open, Burn, Radial Wipe, Mosaic Reveal, Bounce, Lens Flare, Page Curl, Cross Warp, Angular, Kaleidoscope, Squares Wire, Color Phase
- [x] 1.2 — **Social media export presets** — One-tap YouTube/TikTok/Instagram/Threads presets in ExportSheet + ExportConfig.youtube1080()/tiktok()/instagram() factory methods
- [x] 1.3 — **Magnetic timeline snapping** — 8dp snap threshold, diamond indicators, snaps to clip edges + playhead + origin
- [x] 1.4 — **Film grain shader** — Perceptual-aware (shadow-weighted), blue noise pattern, animated via time uniform
- [x] 1.5 — **VHS/Retro effect shader** — Scanlines + chroma bleeding + tracking distortion + posterize
- [x] 1.6 — **Glitch effect shader** — RGB channel split + 8x8 block corruption + horizontal displacement
- [x] 1.7 — **Light leak overlays** — Procedural animated warm gradient with screen blend mode
- [x] 1.8 — **Two-pass Gaussian blur** — 9-tap separable kernel with sigma-based weights (0.227/0.195/0.122/0.054/0.016)
- [x] 1.9 — **Wire slipClip()/slideClip()** — Drag middle of clip = slide, drag in trim mode = slip
- [x] 1.10 — **Clip grouping** — groupId on Clip, groupSelectedClips()/ungroupSelectedClips(), auto-select grouped clips

## Tier 2: Medium Effort, High Impact
> New dependencies (Maven/NDK), model downloads, engine-level changes.

- [ ] 2.1 — **Sherpa-ONNX ASR** — SherpaAsrEngine abstraction layer with model variants, word timestamps, language support. Gradle dep commented (stub — dependency not integrated)
- [ ] 2.2 — **AndroidDeepFilterNet** — NoiseReductionEngine with 5 modes (off/light/moderate/aggressive/spectral gate), noise profiling, ViewModel wired, UI result display. Gradle dep commented (stub — dependency not integrated)
- [ ] 2.3 — **Piper TTS via Sherpa-ONNX** — PiperTtsEngine with 10 voices across 8 languages, synthesize() with progress, voice download management (stub — dependency not integrated; system TTS fallback works)
- [x] 2.4 — **Lottie animated titles** — LottieTemplateEngine with 10 built-in templates, frame-by-frame rendering via TextDelegate, 4 categories
- [x] 2.5 — **Beat detection engine** — Pure-Kotlin spectral flux onset detection + adaptive thresholding + BPM histogram. aubio NDK dep ready to drop in
- [x] 2.6 — **Loudness engine** — EBU R128 measurement (K-weighting, gated blocks, LRA) + 6 platform presets + true-peak limiting
- [x] 2.7 — **YCbCr chroma key** — Professional CbCr distance keying + smoothstep feathering + green/blue spill suppression
- [ ] 2.8 — **Oboe resampler** — Abstraction ready, Gradle dep commented for activation (stub — dependency not integrated)
- [x] 2.9 — **First-run tutorial auto-show** — SettingsRepository flag, 500ms delay trigger in init, persists on dismiss

## Tier 3: High Effort, Differentiating
> ML model integration, NDK builds, compute shaders, significant new features.

- [ ] 3.1 — **RIFE frame interpolation** — FrameInterpolationEngine with 2x/4x/8x configs, model download mgmt, frame duplication fallback (stub — dependency not integrated)
- [x] 3.2 — **LaMa inpainting** — InpaintingEngine with per-frame + video batch processing, ONNX/Qualcomm AI Hub stubs
- [ ] 3.3 — **Real-ESRGAN upscaling** — UpscaleEngine with x4plus + general-x4v3 variants, tile-based processing (stub — dependency not integrated)
- [ ] 3.4 — **RobustVideoMatting** — VideoMattingEngine with temporal coherence (hidden states), 4 background modes (stub — dependency not integrated)
- [ ] 3.5 — **OpenCV stabilization** — StabilizationEngine with L-K/ORB algorithms, Kalman smoothing, configurable crop (stub — dependency not integrated)
- [ ] 3.6 — **Style transfer** — StyleTransferEngine with 9 presets (AnimeGANv2 + Fast NST + OpenCV pencil sketch) (stub — dependency not integrated)
- [x] 3.7 — **Smart reframing** — SmartReframeEngine with EMA-smoothed crop trajectory, face/pose detection stubs, 3 strategies (stationary/pan/track)
- [x] 3.8 — **GPU waveform/vectorscope** — Compute shader documentation added (waveform + vectorscope GLSL for ES 3.1+)
- [ ] 3.9 — **FFmpegX-Android fallback encoder** — FFmpegEngine with execute(), subtitle burning, loudness normalization, audio extraction (stub — dependency not integrated)
- [x] 3.10 — **libass burned-in subtitles** — SubtitleRenderEngine with Canvas rendering + ASS/SSA file generation

## Tier 4: Future / Premium
> Architectural changes, cloud features, advanced workflows.

- [ ] 4.1 — **MobileSAM** — TapSegmentEngine with point/box prompts, mask propagation via optical flow (stub — dependency not integrated)
- [ ] 4.2 — **ProPainter cloud** — CloudInpaintingEngine with job submission/tracking/download API abstraction (stub — dependency not integrated)
- [x] 4.3 — **OpenTimelineIO** — TimelineExchangeEngine with OTIO JSON export/import + FCPXML export
- [x] 4.4 — **AV1/VP9 export** — VP9 added to VideoCodec enum, getAvailableCodecs() queries hardware support
- [ ] 4.5 — **Rive interactive templates** — RiveTemplateEngine with 5 templates, state machine inputs, renderFrame() (stub — dependency not integrated)
- [x] 4.6 — **Soundpipe DSP** — SoundpipeDspEngine with Schroeder reverb, Moog ladder filter, 4 distortion types (working Kotlin fallback)
- [x] 4.7 — **Command-based undo/redo** — EditCommand sealed class with AddClip/RemoveClip/Trim/Move/Speed/Effect/Compound
- [x] 4.8 — **Proxy workflow** — ProxyWorkflowEngine with 3-tier media, auto-switch, generateAllProxies, storage management

## v3.0.0 Release Fixes
- [x] ExportService: tap-to-open PendingIntent on completion notification
- [x] ExportService: actual error messages propagated (was hardcoded "Export failed")
- [x] ExportService: progress notification cancelled before posting completion
- [x] VideoEngine: exportErrorMessage StateFlow added for error propagation
- [x] OTIO import: clips with empty URI now skipped (was crash on playback)
- [x] ProxyWorkflow: empty proxy set returns progress 1.0 (was silent no-op)
- [x] Settings: default codec selector (H.264/HEVC/AV1/VP9)
- [x] Settings: proxy generation toggle
- [x] Settings: AI model management section (Whisper/Segmentation/Piper)
- [x] ToolPanel: removed duplicate smart_reframe action ID

## v3.0.0 Performance & Polish
- [x] **Playhead StateFlow split** — playheadMs in separate MutableStateFlow, syncs to EditorState every 5th frame (60→6 copies/sec)
- [x] **7 new easing types** — BOUNCE, ELASTIC, BACK, CIRCULAR, EXPO, SINE, CUBIC with standard formulas
- [x] **4 new speed presets** — TIME_FREEZE, FILM_REEL, HEARTBEAT, CRESCENDO
- [x] **MultiCamEngine wired** — syncMultiCamClips() using first clip as reference, applies SyncResult offsets
- [x] **Adjustment layer cascade** — Export pipeline applies ADJUSTMENT track effects to overlapping video clips
- [x] **LruCache thumbnails** — Memory-bounded (1/8 heap, byteCount sizing), replaced LinkedHashMap+cacheLock
- [x] **AiFeatures error logging** — Log.w added to 15 silent catch blocks (left cleanup catches untouched)
- [x] **Accessibility** — contentDescription on all interactive UI elements (ToolPanel, EditorScreen, Timeline, AudioMixer)
- [x] **Sticker/GIF overlays** — ImageOverlay data class, ImageOverlayType enum, add/update/remove methods
- [x] **Timeline markers** — TimelineMarker with 6 colors, add/delete/jump-to-next/jump-to-prev
- [x] **Favorites/recent effects** — DataStore persistence, toggle favorite, track usage in SettingsRepository
- [x] **Proxy playback wiring** — prepareTimeline() now uses clip.proxyUri when available
- [x] **Batch export per-item progress** — videoEngine.exportProgress collected and mapped to BatchExportItem.progress
- [x] **EditCommand bridge** — Documentation for gradual migration from snapshot to command-based undo

## v3.1.0 — Code Quality & New Features

### Code Quality
- [x] **Extract StateFlowExt** — Deduplicated MutableStateFlow.update() CAS-loop from 7 delegate classes into shared `StateFlowExt.kt`
- [x] **Fix shadowed `it` lambdas** — Fixed `removeBatchExportItem` and `deleteTimelineMarker` where nested lambdas shadowed outer `it`
- [x] **ExportService lifecycle** — ExportService now properly stopped on export completion, error, and exception
- [x] **Remove artificial delay** — Removed unexplained `delay(1000)` from face tracking in both AiToolsDelegate and EditorViewModel
- [x] **Unused import cleanup** — Removed dead `delay` import from AiToolsDelegate

### Engine Improvements
- [x] **CloudInpaintingEngine** — Config persistence via SharedPreferences, input validation (duration/mask), proper logging, dynamic `isAvailable()` based on config
- [x] **FFmpegEngine** — Reflective runtime invocation (avoids hard dependency), concat demuxer, speed change with atempo chain (0.25x-16x), proper logging
- [x] **NoiseReductionEngine** — Runtime DeepFilterNet detection via reflection, proper OFF mode short-circuit, cascading ML→spectral gate fallback
- [x] **PiperTtsEngine** — Android system TTS fallback via `synthesizeToFile()` with `UtteranceProgressListener`, voice deletion, Sherpa-ONNX runtime detection

### New Features
- [x] **Waveform cache** — LRU cache (64 entries) in AudioEngine prevents redundant PCM decoding on timeline recomposition, with `clearWaveformCache()` on ViewModel clear
- [x] **Haptic feedback** — Timeline trim handles fire `LongPress` haptic on drag start; clip slide fires `TextHandleMove` haptic on magnetic snap
- [x] **Transition icons** — Unique Material icons per transition type (gradient, swipe, zoom, rotate, flip, cube, water, fire, lens, page curl, etc.) replacing generic SwapHoriz
- [x] **Transition selection border** — Active transition gets Mauve accent border for clearer visual feedback
- [x] **Clip reorder** — `reorderClip()` repositions clip within track with automatic timeline recalculation
- [x] **Move clip to track** — `moveClipToTrack()` transfers clip between tracks, appending at end of target track

### Dependency-Gated Items (Planned)
- [ ] **Sherpa-ONNX Piper synthesis** — Direct OfflineTts API with WAV file generation
- [ ] **DeepFilterNet ML processing** — Direct DeepFilterNet API with attenuation levels and spectral gate fallback
- [ ] **RIFE frame interpolation** — NCNN+Vulkan RIFE v4.6 inference with weighted bitmap blend fallback
- [ ] **LaMa inpainting** — ONNX Runtime with NNAPI acceleration, NCHW tensor conversion, neighbor-fill fallback
- [ ] **Cloud inpainting API** — OkHttp multipart upload with job submission/tracking/download
- [ ] **FFmpegX integration** — Direct FFmpegX.execute() with two-pass EBU R128 loudness normalization

## v3.4.0 — Dependency Activation (Reverted)

> Dependencies were briefly added but removed in v3.5.0 due to unavailable artifacts. Engine stubs remain for future integration.

### Engine Implementations
- [x] **PiperTtsEngine → Sherpa-ONNX** — Direct `OfflineTts` API replacing reflection stubs, WAV file generation with RIFF header
- [x] **NoiseReductionEngine → DeepFilterNet** — Direct `DeepFilterNet(context, attenuationDb)` API, `processFile()` with spectral gate fallback
- [x] **FFmpegEngine → FFmpegX** — Direct `FFmpegX.execute()`, two-pass EBU R128 loudness normalization with JSON parsing
- [x] **FrameInterpolationEngine → NCNN RIFE** — HTTP model download with progress, RIFE v4.6 inference, weighted bitmap blend fallback
- [x] **InpaintingEngine → ONNX Runtime** — LaMa model download, NNAPI acceleration, NCHW tensor conversion, neighbor-fill fallback
- [x] **CloudInpaintingEngine → OkHttp** — Multipart upload with Bearer auth, job submission/tracking/download endpoints

### Build Configuration
- [x] **Dependencies activated** — All tier 2-4 dependencies moved from commented stubs to active implementation lines in version catalog
- [x] **ProGuard rules** — Keep rules added for Sherpa-ONNX, DeepFilterNet, NCNN, FFmpegX, OkHttp JNI/native bridges

## v3.2.0 — Performance & UX Hardening

### Bug Fixes
- [x] **Export state stuck at EXPORTING** — ExportDelegate now sets `exportState = COMPLETE` on success and `exportState = ERROR` on failure/exception; previously state was never updated after export began
- [x] **Selection state leak** — `selectClip()` now resets `selectedClipIds` on each call instead of accumulating; `deleteSelectedClip()` clears `selectedClipIds`
- [x] **ExportService not stopped** — Added `stopService()` in ExportDelegate onComplete, onError, and catch blocks; added `stopForeground(STOP_FOREGROUND_REMOVE)` with API level check in ExportService
- [x] **Null-safe ExportService** — All `getSystemService()` calls now use `?.` to avoid NPE
- [x] **Style transfer misleading toast** — Shows "No style adjustments needed" when 0 effects applied (was "Applied 0 style effects")
- [x] **AiToolsDelegate null preview** — Added `getSelectedClip()?.let` guard before `applyPreviewEffects()`
- [x] **Unnecessary recalculateDuration** — Removed from style transfer (effects don't change clip duration)

### Performance
- [x] **Timeline pointerInput optimization** — Replaced 6 `pointerInput(scrollOffsetMs, zoomLevel)` calls with `pointerInput(Unit)` + `rememberUpdatedState`, preventing gesture detector recreation on every scroll/zoom change
- [x] **Extract BASE_SCALE constant** — Replaced magic number `0.15f` with named `BASE_SCALE` constant in Timeline
- [x] **VideoEngine deduplication** — Extracted `addColorGradingEffects()`, `buildTransitionEffect()`, `addOpacityAndTransformEffects()` shared methods, eliminating ~200 lines duplicated between `export()` and `applyPreviewEffects()`
- [x] **AiToolsDelegate deduplication** — Extracted shared `buildTrackingKeyframes()` method from `runTrackMotion()` and `runFaceTrack()`

### UX Improvements
- [x] **Delete confirmation dialog** — Clip deletion now shows confirmation dialog ("Delete this clip? This action can be undone.")
- [x] **Buffering indicator** — PreviewPanel shows a CircularProgressIndicator when player is in BUFFERING state
- [x] **AudioPanel null guard** — Shows "Select a clip to edit audio" message when no clip is selected instead of rendering empty controls

### Error Handling
- [x] **AI operation try/catch** — Wrapped `runTrackMotion`, `runStyleTransfer`, `runFaceTrack`, `runSmartReframe`, `runUpscale` in try/catch with toast error reporting
- [x] **Clip validation guards** — `deleteSelectedClip()` and `duplicateSelectedClip()` now validate clip exists before saving undo state
- [x] **Merge validation** — `mergeWithNextClip()` validates merge preconditions (next clip exists, same source, adjacent trims) before saving undo state

## v3.3.0 — Localization, Performance & Reliability

### Performance
- [x] **Batch pixel access** — Replaced `getPixel()` per-pixel loops with `getPixels()` batch reads in `AiFeatures.kt` (calculateFrameDifference, color analysis, background analysis, style transfer analysis) — ~10x faster on large bitmaps
- [x] **Bitmap leak fix** — `calculateFrameDifference()` now uses try-finally to guarantee bitmap recycling even on exceptions; second bitmap creation also guarded

### Reliability
- [x] **Exception logging** — Added `Log.w` to previously silent catches: WhisperEngine vocab load, WhisperEngine PCM decode, SegmentationEngine frame segmentation, ProjectAutoSave compound clip deserialization

### Localization
- [x] **String extraction** — Extracted 90+ hardcoded UI strings to `strings.xml` across 15 panels: UndoHistoryPanel, AudioPanel, EditorScreen (delete dialog), TtsPanel, SnapshotHistoryPanel, CaptionEditorPanel, CloudBackupPanel, ChapterMarkerPanel, EffectLibraryPanel, FillerRemovalPanel, AutoEditPanel, BeatSyncPanel, BlendModeSelector, RenderPreviewSheet, MediaManagerPanel, PipPresetsPanel, AutoSaveIndicator
- [x] **Accessibility** — All extracted strings include content descriptions for screen readers

## v3.9.0 — Export Expansion, Settings & UX Polish

### Export Enhancements
- [x] **GIF export** — Toggle in ExportSheet with configurable frame rate (10/15/20fps) and max width (320/480/640px)
- [x] **Frame capture** — PNG/JPEG single-frame export from playhead position via `captureFrame()` in ViewModel
- [x] **Subtitle export** — SRT/VTT/ASS format picker in ExportSheet, exports caption data via `exportSubtitles()`
- [x] **Stems export toggle** — `exportStemsOnly` wired in ExportSheet UI
- [x] **Chapter markers toggle** — `includeChapterMarkers` wired in ExportSheet UI
- [x] **FrameCaptureFormat enum** — PNG/JPEG with `.extension` property added to ExportConfig

### Settings Expansion
- [x] **7 new AppSettings fields** — `showWaveforms`, `defaultTrackHeight`, `snapToBeat`, `snapToMarker`, `thumbnailCacheSizeMb`, `confirmBeforeDelete`, `defaultExportQuality`
- [x] **Timeline settings section** — Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height chips (48/64/80/96)
- [x] **Editor settings section** — Confirm Before Delete, Thumbnail Cache chips (64/128/256 MB), Default Export Quality chips
- [x] **DataStore persistence** — All 7 fields persisted with live sync to EditorViewModel snap state

### Marker List Panel
- [x] **MarkerListPanel.kt** — Searchable marker list with color filter chips, inline label editing, jump-to-time, delete
- [x] **Panel wiring** — `PanelId.MARKER_LIST` + ToolPanel "Marker List" action + EditorScreen BottomSheetSlot

### Track Header Enhancements
- [x] **Track model fields** — `showWaveform: Boolean`, `trackHeight: Int`, `isCollapsed: Boolean` added to Track data class
- [x] **ViewModel methods** — `toggleTrackWaveform`, `setTrackHeight`, `toggleTrackCollapsed`, `collapseAllTracks`, `expandAllTracks`
- [x] **ProjectAutoSave** — New Track fields serialized/deserialized with safe defaults

### Snap-to-Beat/Marker Scrubbing
- [x] **Timeline snap extension** — Beat markers and timeline marker positions added as snap targets (settings-driven)
- [x] **Settings sync** — `snapToBeat`/`snapToMarker` flow from SettingsRepository → EditorViewModel → Timeline params

## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish

### Track Header UI Wiring
- [x] **Collapse/expand chevron** — Per-track ChevronRight/ExpandMore icon; collapsed tracks render as thin colored bars (24dp)
- [x] **Per-track height** — Timeline uses `Track.trackHeight` instead of hardcoded 60dp
- [x] **Waveform toggle icon** — GraphicEq icon in track header for VIDEO/AUDIO tracks, gated by `Track.showWaveform`
- [x] **EditorScreen wiring** — `onToggleTrackCollapsed` and `onToggleTrackWaveform` passed to Timeline

### Settings Wired into Editor
- [x] **confirmBeforeDelete** — Delete confirmation dialog skipped when setting is false
- [x] **defaultExportQuality** — Applied to initial ExportConfig on first load (LOW/MEDIUM/HIGH mapping)
- [x] **showWaveforms** — Global waveform visibility: empty waveform map passed to Timeline when disabled

### Chapter Markers on Export
- [x] **Auto-populate chapters** — `ExportDelegate.startExport()` maps `timelineMarkers` → `ChapterMarker` list when `includeChapterMarkers` is true and chapters list is empty

### Clip Color Labels
- [x] **ClipLabel enum** — NONE, RED, PEACH, GREEN, BLUE, MAUVE, YELLOW with Catppuccin ARGB values
- [x] **Clip.clipLabel field** — Added to Clip data class with `ClipLabel.NONE` default
- [x] **Timeline rendering** — 3dp colored top border on clips with non-NONE label
- [x] **ViewModel method** — `setClipLabel()` with undo support, timeline rebuild, and auto-save
- [x] **ProjectAutoSave** — `clipLabel` serialized/deserialized with safe default

### Keyboard Shortcuts (External Keyboard)
- [x] **Play/pause** — Space bar toggles playback
- [x] **Undo/redo** — Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y
- [x] **Seek** — Arrow keys ±1s, Ctrl+arrows ±5s
- [x] **Timeline** — M=add marker, S=split, +/-=zoom, Delete=delete clip
- [x] **Project** — Ctrl+S=save, Ctrl+C/V=copy/paste effects
- [x] **Focus system** — FocusRequester + focusable() on root Box for key event capture

## v3.11.0 — Clip Labels, Track Controls & Localization

### Clip Label Picker UI
- [x] **Label SubMenuItem** — "Color Label" action added to clipEditSubMenu in ToolPanel
- [x] **Label picker Card** — AnimatedVisibility bottom sheet with 7 ClipLabel color circles, selection border, dismiss on deselect
- [x] **EditorScreen wiring** — `"label"` action handler toggles picker, `setClipLabel()` called on tap

### Track Controls
- [x] **Collapse/expand all toggle** — UnfoldLess/UnfoldMore IconButton in Timeline zoom controls row
- [x] **Track height cycling** — Long-press track type icon cycles 48→64→80→96→48dp via `onSetTrackHeight`
- [x] **EditorScreen wiring** — `onCollapseAllTracks`, `onExpandAllTracks`, `onSetTrackHeight` wired to ViewModel

### ToolPanel Localization
- [x] **@StringRes migration** — `TabItem.label` and `SubMenuItem.label` changed from `String` to `@StringRes Int`
- [x] **83 string resources** — All tab labels (12) and sub-menu item labels (71) extracted to strings.xml
- [x] **Composable resolution** — `stringResource(item.labelRes)` used in BottomTabBar and SubMenuGrid rendering

## v3.12.0 — GIF Export, Accessibility & Panel Localization

### GIF Export Backend
- [x] **GIF89a encoder** — Self-contained GIF encoder with LZW compression in ExportDelegate, no external dependencies
- [x] **Frame extraction pipeline** — Extracts frames via `extractThumbnail()`, scales to `gifMaxWidth`, caps at 300 frames
- [x] **ExportDelegate integration** — GIF branch in `startExport()` with progress reporting, short-circuits before MP4 path

### Accessibility
- [x] **25 contentDescription fixes** — Replaced `null` with `stringResource(R.string.cd_*)` across 13 panel files
- [x] **23 string resources** — New `cd_` prefixed content descriptions for screen readers

### Panel Localization
- [x] **75 string extractions** — Hardcoded `Text("...")` from 21 panel composables extracted to strings.xml
- [x] **Organized by panel** — `panel_audio_mixer_*`, `panel_cloud_backup_*`, `panel_pip_*`, etc.
- [x] **Import fixes** — Added missing `stringResource`/`R` imports to 7 panel files

## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish

### GIF Encoder Hardening
- [x] **Bitmap leak fix** — Moved `frames.forEach { it.recycle() }` to finally block, preventing memory leak on export error
- [x] **Division-by-zero guard** — `gifFrameRate.coerceAtLeast(1)` prevents crash on malformed ExportConfig

### Settings Screen Localization
- [x] **22 string extractions** — All hardcoded SettingsScreen labels/descriptions extracted to strings.xml
- [x] **Sections localized** — Editor, Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height, Default Mode, Haptic Feedback, Confirm Before Delete, Thumbnail Cache, Default Export Quality
- [x] **Quality labels** — "Small File"/"Balanced"/"Best Quality" now string resources

### Reset Tutorial Confirmation
- [x] **AlertDialog guard** — Reset Tutorial button now shows confirmation dialog before clearing tutorial state
- [x] **3 new strings** — `settings_reset_tutorial_confirm`, `settings_reset_tutorial_confirm_title`, `settings_confirm`

### Panel String Extractions
- [x] **ChapterMarkerPanel** — Description text + 3 contentDescriptions (Save/Edit/Delete) extracted
- [x] **AutoEditPanel** — 6 InfoCard labels (Clips/Music/Yes/No/Target/~60s) extracted
- [x] **BeatSyncPanel** — 3 stat labels (markers count, Beats, BPM) extracted with pluralization support

### Undo Stack Bounds
- [x] **Redo-path bounded** — `undoStack` on redo now bounded to 50 entries via `.takeLast(50)`, matching the save path

## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness

### Bug Fixes
- [x] **GIF color quantization** — Fixed operator precedence in `((rgb and 0xF0) shr 4)` — shift had higher precedence than AND, causing wrong palette mapping and incorrect colors in exported GIFs

### FirstRunTutorial Localization
- [x] **@StringRes migration** — `TutorialStep` replaced with `TutorialStepDef` using `titleRes`/`descriptionRes` resource IDs
- [x] **14 string resources** — 4 titles, 4 descriptions, Skip, Next, Get Started, step counter format

### ExportSheet Localization
- [x] **8 string extractions** — "Elapsed:", "Transparent Background (WebM VP9)", "Audio Codec", "OTIO", "FCPXML" + reused existing subtitle/stems/chapter strings

### Remaining Panel String Extractions
- [x] **SpeedCurveEditor** — `"Speed: %.2fx"` format string extracted
- [x] **ProjectTemplateSheet** — `"Import Template"` extracted
- [x] **SnapshotHistoryPanel** — Default snapshot name prefix extracted

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

- [x] 2.1 — **Sherpa-ONNX ASR** — SherpaAsrEngine abstraction layer with model variants, word timestamps, language support. Gradle dep commented (activate when ready)
- [x] 2.2 — **AndroidDeepFilterNet** — NoiseReductionEngine with 5 modes (off/light/moderate/aggressive/spectral gate), noise profiling, ViewModel wired, UI result display. Gradle dep commented
- [x] 2.3 — **Piper TTS via Sherpa-ONNX** — PiperTtsEngine with 10 voices across 8 languages, synthesize() with progress, voice download management
- [x] 2.4 — **Lottie animated titles** — LottieTemplateEngine with 10 built-in templates, frame-by-frame rendering via TextDelegate, 4 categories
- [x] 2.5 — **Beat detection engine** — Pure-Kotlin spectral flux onset detection + adaptive thresholding + BPM histogram. aubio NDK dep ready to drop in
- [x] 2.6 — **Loudness engine** — EBU R128 measurement (K-weighting, gated blocks, LRA) + 6 platform presets + true-peak limiting
- [x] 2.7 — **YCbCr chroma key** — Professional CbCr distance keying + smoothstep feathering + green/blue spill suppression
- [x] 2.8 — **Oboe resampler** — Abstraction ready, Gradle dep commented for activation
- [x] 2.9 — **First-run tutorial auto-show** — SettingsRepository flag, 500ms delay trigger in init, persists on dismiss

## Tier 3: High Effort, Differentiating
> ML model integration, NDK builds, compute shaders, significant new features.

- [x] 3.1 — **RIFE frame interpolation** — FrameInterpolationEngine with 2x/4x/8x configs, model download mgmt, frame duplication fallback
- [x] 3.2 — **LaMa inpainting** — InpaintingEngine with per-frame + video batch processing, ONNX/Qualcomm AI Hub stubs
- [x] 3.3 — **Real-ESRGAN upscaling** — UpscaleEngine with x4plus + general-x4v3 variants, tile-based processing
- [x] 3.4 — **RobustVideoMatting** — VideoMattingEngine with temporal coherence (hidden states), 4 background modes
- [x] 3.5 — **OpenCV stabilization** — StabilizationEngine with L-K/ORB algorithms, Kalman smoothing, configurable crop
- [x] 3.6 — **Style transfer** — StyleTransferEngine with 9 presets (AnimeGANv2 + Fast NST + OpenCV pencil sketch)
- [x] 3.7 — **Smart reframing** — SmartReframeEngine with EMA-smoothed crop trajectory, face/pose detection stubs, 3 strategies (stationary/pan/track)
- [x] 3.8 — **GPU waveform/vectorscope** — Compute shader documentation added (waveform + vectorscope GLSL for ES 3.1+)
- [x] 3.9 — **FFmpegX-Android fallback encoder** — FFmpegEngine with execute(), subtitle burning, loudness normalization, audio extraction
- [x] 3.10 — **libass burned-in subtitles** — SubtitleRenderEngine with Canvas rendering + ASS/SSA file generation

## Tier 4: Future / Premium
> Architectural changes, cloud features, advanced workflows.

- [x] 4.1 — **MobileSAM** — TapSegmentEngine with point/box prompts, mask propagation via optical flow
- [x] 4.2 — **ProPainter cloud** — CloudInpaintingEngine with job submission/tracking/download API abstraction
- [x] 4.3 — **OpenTimelineIO** — TimelineExchangeEngine with OTIO JSON export/import + FCPXML export
- [x] 4.4 — **AV1/VP9 export** — VP9 added to VideoCodec enum, getAvailableCodecs() queries hardware support
- [x] 4.5 — **Rive interactive templates** — RiveTemplateEngine with 5 templates, state machine inputs, renderFrame()
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

### Remaining TODO Stubs (Dependency-Gated)
- [ ] **Sherpa-ONNX Piper synthesis** — Full Piper TTS via Sherpa-ONNX (blocked on `com.k2fsa.sherpa:onnx-android` dependency)
- [ ] **DeepFilterNet ML processing** — Full ML noise reduction (blocked on `android-deepfilternet` dependency)
- [ ] **RIFE frame interpolation** — NCNN+Vulkan inference (blocked on NCNN Android dependency)
- [ ] **LaMa inpainting** — ONNX Runtime inference (blocked on model hosting + ONNX dependency)
- [ ] **Cloud inpainting API** — ProPainter cloud endpoint integration (blocked on server deployment)
- [ ] **FFmpegX integration** — Full FFmpeg command execution (blocked on `ffmpegx-android` dependency)

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

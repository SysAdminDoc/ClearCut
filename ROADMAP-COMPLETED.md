# NovaCut — Completed Roadmap Items

# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.1 — **gl-transitions integration** — 12 new GLSL transitions: Door Open, Burn, Radial Wipe, Mosaic Reveal, Bounce, Lens Flare, Page Curl, Cross Warp, Angular, Kaleidoscope, Squares Wire, Color Phase
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.2 — **Social media export presets** — One-tap YouTube/TikTok/Instagram/Threads presets in ExportSheet + ExportConfig.youtube1080()/tiktok()/instagram() factory methods
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.3 — **Magnetic timeline snapping** — 8dp snap threshold, diamond indicators, snaps to clip edges + playhead + origin
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.4 — **Film grain shader** — Perceptual-aware (shadow-weighted), blue noise pattern, animated via time uniform
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.5 — **VHS/Retro effect shader** — Scanlines + chroma bleeding + tracking distortion + posterize
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.6 — **Glitch effect shader** — RGB channel split + 8x8 block corruption + horizontal displacement
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.7 — **Light leak overlays** — Procedural animated warm gradient with screen blend mode
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.8 — **Two-pass Gaussian blur** — 9-tap separable kernel with sigma-based weights (0.227/0.195/0.122/0.054/0.016)
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.9 — **Wire slipClip()/slideClip()** — Drag middle of clip = slide, drag in trim mode = slip
# NovaCut — Implementation Roadmap
## Tier 1: Low Effort, High Impact
- [x] 1.10 — **Clip grouping** — groupId on Clip, groupSelectedClips()/ungroupSelectedClips(), auto-select grouped clips
# NovaCut — Implementation Roadmap
## Tier 2: Medium Effort, High Impact
- [x] 2.4 — **Lottie animated titles** — LottieTemplateEngine with 10 built-in templates, frame-by-frame rendering via TextDelegate, 4 categories
# NovaCut — Implementation Roadmap
## Tier 2: Medium Effort, High Impact
- [x] 2.5 — **Beat detection engine** — Pure-Kotlin spectral flux onset detection + adaptive thresholding + BPM histogram. aubio NDK dep ready to drop in
# NovaCut — Implementation Roadmap
## Tier 2: Medium Effort, High Impact
- [x] 2.6 — **Loudness engine** — EBU R128 measurement (K-weighting, gated blocks, LRA) + 6 platform presets + true-peak limiting
# NovaCut — Implementation Roadmap
## Tier 2: Medium Effort, High Impact
- [x] 2.7 — **YCbCr chroma key** — Professional CbCr distance keying + smoothstep feathering + green/blue spill suppression
# NovaCut — Implementation Roadmap
## Tier 2: Medium Effort, High Impact
- [x] 2.9 — **First-run tutorial auto-show** — SettingsRepository flag, 500ms delay trigger in init, persists on dismiss
# NovaCut — Implementation Roadmap
## Tier 3: High Effort, Differentiating
- [x] 3.2 — **LaMa inpainting** — InpaintingEngine with per-frame + video batch processing, ONNX/Qualcomm AI Hub stubs
# NovaCut — Implementation Roadmap
## Tier 3: High Effort, Differentiating
- [x] 3.7 — **Smart reframing** — SmartReframeEngine with EMA-smoothed crop trajectory, face/pose detection stubs, 3 strategies (stationary/pan/track)
# NovaCut — Implementation Roadmap
## Tier 3: High Effort, Differentiating
- [x] 3.8 — **GPU waveform/vectorscope** — Compute shader documentation added (waveform + vectorscope GLSL for ES 3.1+)
# NovaCut — Implementation Roadmap
## Tier 3: High Effort, Differentiating
- [x] 3.10 — **libass burned-in subtitles** — SubtitleRenderEngine with Canvas rendering + ASS/SSA file generation
# NovaCut — Implementation Roadmap
## Tier 4: Future / Premium
- [x] 4.3 — **OpenTimelineIO** — TimelineExchangeEngine with OTIO JSON export/import + FCPXML export
# NovaCut — Implementation Roadmap
## Tier 4: Future / Premium
- [x] 4.4 — **AV1/VP9 export** — VP9 added to VideoCodec enum, getAvailableCodecs() queries hardware support
# NovaCut — Implementation Roadmap
## Tier 4: Future / Premium
- [x] 4.6 — **Soundpipe DSP** — SoundpipeDspEngine with Schroeder reverb, Moog ladder filter, 4 distortion types (working Kotlin fallback)
# NovaCut — Implementation Roadmap
## Tier 4: Future / Premium
- [x] 4.7 — **Command-based undo/redo** — EditCommand sealed class with AddClip/RemoveClip/Trim/Move/Speed/Effect/Compound
# NovaCut — Implementation Roadmap
## Tier 4: Future / Premium
- [x] 4.8 — **Proxy workflow** — ProxyWorkflowEngine with 3-tier media, auto-switch, generateAllProxies, storage management
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] ExportService: tap-to-open PendingIntent on completion notification
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] ExportService: actual error messages propagated (was hardcoded "Export failed")
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] ExportService: progress notification cancelled before posting completion
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] VideoEngine: exportErrorMessage StateFlow added for error propagation
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] OTIO import: clips with empty URI now skipped (was crash on playback)
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] ProxyWorkflow: empty proxy set returns progress 1.0 (was silent no-op)
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] Settings: default codec selector (H.264/HEVC/AV1/VP9)
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] Settings: proxy generation toggle
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] Settings: AI model management section (Whisper/Segmentation/Piper)
# NovaCut — Implementation Roadmap
## v3.0.0 Release Fixes
- [x] ToolPanel: removed duplicate smart_reframe action ID
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Playhead StateFlow split** — playheadMs in separate MutableStateFlow, syncs to EditorState every 5th frame (60→6 copies/sec)
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **7 new easing types** — BOUNCE, ELASTIC, BACK, CIRCULAR, EXPO, SINE, CUBIC with standard formulas
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **4 new speed presets** — TIME_FREEZE, FILM_REEL, HEARTBEAT, CRESCENDO
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **MultiCamEngine wired** — syncMultiCamClips() using first clip as reference, applies SyncResult offsets
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Adjustment layer cascade** — Export pipeline applies ADJUSTMENT track effects to overlapping video clips
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **LruCache thumbnails** — Memory-bounded (1/8 heap, byteCount sizing), replaced LinkedHashMap+cacheLock
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **AiFeatures error logging** — Log.w added to 15 silent catch blocks (left cleanup catches untouched)
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Accessibility** — contentDescription on all interactive UI elements (ToolPanel, EditorScreen, Timeline, AudioMixer)
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Sticker/GIF overlays** — ImageOverlay data class, ImageOverlayType enum, add/update/remove methods
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Timeline markers** — TimelineMarker with 6 colors, add/delete/jump-to-next/jump-to-prev
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Favorites/recent effects** — DataStore persistence, toggle favorite, track usage in SettingsRepository
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Proxy playback wiring** — prepareTimeline() now uses clip.proxyUri when available
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **Batch export per-item progress** — videoEngine.exportProgress collected and mapped to BatchExportItem.progress
# NovaCut — Implementation Roadmap
## v3.0.0 Performance & Polish
- [x] **EditCommand bridge** — Documentation for gradual migration from snapshot to command-based undo
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Code Quality
- [x] **Extract StateFlowExt** — Deduplicated MutableStateFlow.update() CAS-loop from 7 delegate classes into shared `StateFlowExt.kt`
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Code Quality
- [x] **Fix shadowed `it` lambdas** — Fixed `removeBatchExportItem` and `deleteTimelineMarker` where nested lambdas shadowed outer `it`
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Code Quality
- [x] **ExportService lifecycle** — ExportService now properly stopped on export completion, error, and exception
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Code Quality
- [x] **Remove artificial delay** — Removed unexplained `delay(1000)` from face tracking in both AiToolsDelegate and EditorViewModel
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Code Quality
- [x] **Unused import cleanup** — Removed dead `delay` import from AiToolsDelegate
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Engine Improvements
- [x] **CloudInpaintingEngine** — Config persistence via SharedPreferences, input validation (duration/mask), proper logging, dynamic `isAvailable()` based on config
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Engine Improvements
- [x] **FFmpegEngine** — Reflective runtime invocation (avoids hard dependency), concat demuxer, speed change with atempo chain (0.25x-16x), proper logging
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Engine Improvements
- [x] **NoiseReductionEngine** — Runtime DeepFilterNet detection via reflection, proper OFF mode short-circuit, cascading ML→spectral gate fallback
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### Engine Improvements
- [x] **PiperTtsEngine** — Android system TTS fallback via `synthesizeToFile()` with `UtteranceProgressListener`, voice deletion, Sherpa-ONNX runtime detection
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Waveform cache** — LRU cache (64 entries) in AudioEngine prevents redundant PCM decoding on timeline recomposition, with `clearWaveformCache()` on ViewModel clear
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Haptic feedback** — Timeline trim handles fire `LongPress` haptic on drag start; clip slide fires `TextHandleMove` haptic on magnetic snap
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Transition icons** — Unique Material icons per transition type (gradient, swipe, zoom, rotate, flip, cube, water, fire, lens, page curl, etc.) replacing generic SwapHoriz
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Transition selection border** — Active transition gets Mauve accent border for clearer visual feedback
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Clip reorder** — `reorderClip()` repositions clip within track with automatic timeline recalculation
# NovaCut — Implementation Roadmap
## v3.1.0 — Code Quality & New Features
### New Features
- [x] **Move clip to track** — `moveClipToTrack()` transfers clip between tracks, appending at end of target track
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **PiperTtsEngine → Sherpa-ONNX** — Direct `OfflineTts` API replacing reflection stubs, WAV file generation with RIFF header
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **NoiseReductionEngine → DeepFilterNet** — Direct `DeepFilterNet(context, attenuationDb)` API, `processFile()` with spectral gate fallback
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **FFmpegEngine → FFmpegX** — Direct `FFmpegX.execute()`, two-pass EBU R128 loudness normalization with JSON parsing
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **FrameInterpolationEngine → NCNN RIFE** — HTTP model download with progress, RIFE v4.6 inference, weighted bitmap blend fallback
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **InpaintingEngine → ONNX Runtime** — LaMa model download, NNAPI acceleration, NCHW tensor conversion, neighbor-fill fallback
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Engine Implementations
- [x] **CloudInpaintingEngine → OkHttp** — Multipart upload with Bearer auth, job submission/tracking/download endpoints
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Build Configuration
- [x] **Dependencies activated** — All tier 2-4 dependencies moved from commented stubs to active implementation lines in version catalog
# NovaCut — Implementation Roadmap
## v3.4.0 — Dependency Activation (Reverted)
### Build Configuration
- [x] **ProGuard rules** — Keep rules added for Sherpa-ONNX, DeepFilterNet, NCNN, FFmpegX, OkHttp JNI/native bridges
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **Export state stuck at EXPORTING** — ExportDelegate now sets `exportState = COMPLETE` on success and `exportState = ERROR` on failure/exception; previously state was never updated after export began
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **Selection state leak** — `selectClip()` now resets `selectedClipIds` on each call instead of accumulating; `deleteSelectedClip()` clears `selectedClipIds`
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **ExportService not stopped** — Added `stopService()` in ExportDelegate onComplete, onError, and catch blocks; added `stopForeground(STOP_FOREGROUND_REMOVE)` with API level check in ExportService
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **Null-safe ExportService** — All `getSystemService()` calls now use `?.` to avoid NPE
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **Style transfer misleading toast** — Shows "No style adjustments needed" when 0 effects applied (was "Applied 0 style effects")
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **AiToolsDelegate null preview** — Added `getSelectedClip()?.let` guard before `applyPreviewEffects()`
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Bug Fixes
- [x] **Unnecessary recalculateDuration** — Removed from style transfer (effects don't change clip duration)
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Performance
- [x] **Timeline pointerInput optimization** — Replaced 6 `pointerInput(scrollOffsetMs, zoomLevel)` calls with `pointerInput(Unit)` + `rememberUpdatedState`, preventing gesture detector recreation on every scroll/zoom change
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Performance
- [x] **Extract BASE_SCALE constant** — Replaced magic number `0.15f` with named `BASE_SCALE` constant in Timeline
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Performance
- [x] **VideoEngine deduplication** — Extracted `addColorGradingEffects()`, `buildTransitionEffect()`, `addOpacityAndTransformEffects()` shared methods, eliminating ~200 lines duplicated between `export()` and `applyPreviewEffects()`
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Performance
- [x] **AiToolsDelegate deduplication** — Extracted shared `buildTrackingKeyframes()` method from `runTrackMotion()` and `runFaceTrack()`
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### UX Improvements
- [x] **Delete confirmation dialog** — Clip deletion now shows confirmation dialog ("Delete this clip? This action can be undone.")
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### UX Improvements
- [x] **Buffering indicator** — PreviewPanel shows a CircularProgressIndicator when player is in BUFFERING state
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### UX Improvements
- [x] **AudioPanel null guard** — Shows "Select a clip to edit audio" message when no clip is selected instead of rendering empty controls
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Error Handling
- [x] **AI operation try/catch** — Wrapped `runTrackMotion`, `runStyleTransfer`, `runFaceTrack`, `runSmartReframe`, `runUpscale` in try/catch with toast error reporting
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Error Handling
- [x] **Clip validation guards** — `deleteSelectedClip()` and `duplicateSelectedClip()` now validate clip exists before saving undo state
# NovaCut — Implementation Roadmap
## v3.2.0 — Performance & UX Hardening
### Error Handling
- [x] **Merge validation** — `mergeWithNextClip()` validates merge preconditions (next clip exists, same source, adjacent trims) before saving undo state
# NovaCut — Implementation Roadmap
## v3.3.0 — Localization, Performance & Reliability
### Performance
- [x] **Batch pixel access** — Replaced `getPixel()` per-pixel loops with `getPixels()` batch reads in `AiFeatures.kt` (calculateFrameDifference, color analysis, background analysis, style transfer analysis) — ~10x faster on large bitmaps
# NovaCut — Implementation Roadmap
## v3.3.0 — Localization, Performance & Reliability
### Performance
- [x] **Bitmap leak fix** — `calculateFrameDifference()` now uses try-finally to guarantee bitmap recycling even on exceptions; second bitmap creation also guarded
# NovaCut — Implementation Roadmap
## v3.3.0 — Localization, Performance & Reliability
### Reliability
- [x] **Exception logging** — Added `Log.w` to previously silent catches: WhisperEngine vocab load, WhisperEngine PCM decode, SegmentationEngine frame segmentation, ProjectAutoSave compound clip deserialization
# NovaCut — Implementation Roadmap
## v3.3.0 — Localization, Performance & Reliability
### Localization
- [x] **String extraction** — Extracted 90+ hardcoded UI strings to `strings.xml` across 15 panels: UndoHistoryPanel, AudioPanel, EditorScreen (delete dialog), TtsPanel, SnapshotHistoryPanel, CaptionEditorPanel, CloudBackupPanel, ChapterMarkerPanel, EffectLibraryPanel, FillerRemovalPanel, AutoEditPanel, BeatSyncPanel, BlendModeSelector, RenderPreviewSheet, MediaManagerPanel, PipPresetsPanel, AutoSaveIndicator
# NovaCut — Implementation Roadmap
## v3.3.0 — Localization, Performance & Reliability
### Localization
- [x] **Accessibility** — All extracted strings include content descriptions for screen readers
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **GIF export** — Toggle in ExportSheet with configurable frame rate (10/15/20fps) and max width (320/480/640px)
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **Frame capture** — PNG/JPEG single-frame export from playhead position via `captureFrame()` in ViewModel
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **Subtitle export** — SRT/VTT/ASS format picker in ExportSheet, exports caption data via `exportSubtitles()`
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **Stems export toggle** — `exportStemsOnly` wired in ExportSheet UI
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **Chapter markers toggle** — `includeChapterMarkers` wired in ExportSheet UI
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Export Enhancements
- [x] **FrameCaptureFormat enum** — PNG/JPEG with `.extension` property added to ExportConfig
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Settings Expansion
- [x] **7 new AppSettings fields** — `showWaveforms`, `defaultTrackHeight`, `snapToBeat`, `snapToMarker`, `thumbnailCacheSizeMb`, `confirmBeforeDelete`, `defaultExportQuality`
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Settings Expansion
- [x] **Timeline settings section** — Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height chips (48/64/80/96)
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Settings Expansion
- [x] **Editor settings section** — Confirm Before Delete, Thumbnail Cache chips (64/128/256 MB), Default Export Quality chips
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Settings Expansion
- [x] **DataStore persistence** — All 7 fields persisted with live sync to EditorViewModel snap state
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Marker List Panel
- [x] **MarkerListPanel.kt** — Searchable marker list with color filter chips, inline label editing, jump-to-time, delete
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Marker List Panel
- [x] **Panel wiring** — `PanelId.MARKER_LIST` + ToolPanel "Marker List" action + EditorScreen BottomSheetSlot
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Track Header Enhancements
- [x] **Track model fields** — `showWaveform: Boolean`, `trackHeight: Int`, `isCollapsed: Boolean` added to Track data class
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Track Header Enhancements
- [x] **ViewModel methods** — `toggleTrackWaveform`, `setTrackHeight`, `toggleTrackCollapsed`, `collapseAllTracks`, `expandAllTracks`
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Track Header Enhancements
- [x] **ProjectAutoSave** — New Track fields serialized/deserialized with safe defaults
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Snap-to-Beat/Marker Scrubbing
- [x] **Timeline snap extension** — Beat markers and timeline marker positions added as snap targets (settings-driven)
# NovaCut — Implementation Roadmap
## v3.9.0 — Export Expansion, Settings & UX Polish
### Snap-to-Beat/Marker Scrubbing
- [x] **Settings sync** — `snapToBeat`/`snapToMarker` flow from SettingsRepository → EditorViewModel → Timeline params
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Track Header UI Wiring
- [x] **Collapse/expand chevron** — Per-track ChevronRight/ExpandMore icon; collapsed tracks render as thin colored bars (24dp)
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Track Header UI Wiring
- [x] **Per-track height** — Timeline uses `Track.trackHeight` instead of hardcoded 60dp
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Track Header UI Wiring
- [x] **Waveform toggle icon** — GraphicEq icon in track header for VIDEO/AUDIO tracks, gated by `Track.showWaveform`
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Track Header UI Wiring
- [x] **EditorScreen wiring** — `onToggleTrackCollapsed` and `onToggleTrackWaveform` passed to Timeline
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Settings Wired into Editor
- [x] **confirmBeforeDelete** — Delete confirmation dialog skipped when setting is false
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Settings Wired into Editor
- [x] **defaultExportQuality** — Applied to initial ExportConfig on first load (LOW/MEDIUM/HIGH mapping)
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Settings Wired into Editor
- [x] **showWaveforms** — Global waveform visibility: empty waveform map passed to Timeline when disabled
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Chapter Markers on Export
- [x] **Auto-populate chapters** — `ExportDelegate.startExport()` maps `timelineMarkers` → `ChapterMarker` list when `includeChapterMarkers` is true and chapters list is empty
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Clip Color Labels
- [x] **ClipLabel enum** — NONE, RED, PEACH, GREEN, BLUE, MAUVE, YELLOW with Catppuccin ARGB values
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Clip Color Labels
- [x] **Clip.clipLabel field** — Added to Clip data class with `ClipLabel.NONE` default
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Clip Color Labels
- [x] **Timeline rendering** — 3dp colored top border on clips with non-NONE label
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Clip Color Labels
- [x] **ViewModel method** — `setClipLabel()` with undo support, timeline rebuild, and auto-save
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Clip Color Labels
- [x] **ProjectAutoSave** — `clipLabel` serialized/deserialized with safe default
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Play/pause** — Space bar toggles playback
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Undo/redo** — Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Seek** — Arrow keys ±1s, Ctrl+arrows ±5s
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Timeline** — M=add marker, S=split, +/-=zoom, Delete=delete clip
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Project** — Ctrl+S=save, Ctrl+C/V=copy/paste effects
# NovaCut — Implementation Roadmap
## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
### Keyboard Shortcuts (External Keyboard)
- [x] **Focus system** — FocusRequester + focusable() on root Box for key event capture
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Clip Label Picker UI
- [x] **Label SubMenuItem** — "Color Label" action added to clipEditSubMenu in ToolPanel
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Clip Label Picker UI
- [x] **Label picker Card** — AnimatedVisibility bottom sheet with 7 ClipLabel color circles, selection border, dismiss on deselect
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Clip Label Picker UI
- [x] **EditorScreen wiring** — `"label"` action handler toggles picker, `setClipLabel()` called on tap
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Track Controls
- [x] **Collapse/expand all toggle** — UnfoldLess/UnfoldMore IconButton in Timeline zoom controls row
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Track Controls
- [x] **Track height cycling** — Long-press track type icon cycles 48→64→80→96→48dp via `onSetTrackHeight`
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### Track Controls
- [x] **EditorScreen wiring** — `onCollapseAllTracks`, `onExpandAllTracks`, `onSetTrackHeight` wired to ViewModel
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### ToolPanel Localization
- [x] **@StringRes migration** — `TabItem.label` and `SubMenuItem.label` changed from `String` to `@StringRes Int`
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### ToolPanel Localization
- [x] **83 string resources** — All tab labels (12) and sub-menu item labels (71) extracted to strings.xml
# NovaCut — Implementation Roadmap
## v3.11.0 — Clip Labels, Track Controls & Localization
### ToolPanel Localization
- [x] **Composable resolution** — `stringResource(item.labelRes)` used in BottomTabBar and SubMenuGrid rendering
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### GIF Export Backend
- [x] **GIF89a encoder** — Self-contained GIF encoder with LZW compression in ExportDelegate, no external dependencies
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### GIF Export Backend
- [x] **Frame extraction pipeline** — Extracts frames via `extractThumbnail()`, scales to `gifMaxWidth`, caps at 300 frames
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### GIF Export Backend
- [x] **ExportDelegate integration** — GIF branch in `startExport()` with progress reporting, short-circuits before MP4 path
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### Accessibility
- [x] **25 contentDescription fixes** — Replaced `null` with `stringResource(R.string.cd_*)` across 13 panel files
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### Accessibility
- [x] **23 string resources** — New `cd_` prefixed content descriptions for screen readers
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### Panel Localization
- [x] **75 string extractions** — Hardcoded `Text("...")` from 21 panel composables extracted to strings.xml
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### Panel Localization
- [x] **Organized by panel** — `panel_audio_mixer_*`, `panel_cloud_backup_*`, `panel_pip_*`, etc.
# NovaCut — Implementation Roadmap
## v3.12.0 — GIF Export, Accessibility & Panel Localization
### Panel Localization
- [x] **Import fixes** — Added missing `stringResource`/`R` imports to 7 panel files
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### GIF Encoder Hardening
- [x] **Bitmap leak fix** — Moved `frames.forEach { it.recycle() }` to finally block, preventing memory leak on export error
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### GIF Encoder Hardening
- [x] **Division-by-zero guard** — `gifFrameRate.coerceAtLeast(1)` prevents crash on malformed ExportConfig
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Settings Screen Localization
- [x] **22 string extractions** — All hardcoded SettingsScreen labels/descriptions extracted to strings.xml
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Settings Screen Localization
- [x] **Sections localized** — Editor, Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height, Default Mode, Haptic Feedback, Confirm Before Delete, Thumbnail Cache, Default Export Quality
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Settings Screen Localization
- [x] **Quality labels** — "Small File"/"Balanced"/"Best Quality" now string resources
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Reset Tutorial Confirmation
- [x] **AlertDialog guard** — Reset Tutorial button now shows confirmation dialog before clearing tutorial state
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Reset Tutorial Confirmation
- [x] **3 new strings** — `settings_reset_tutorial_confirm`, `settings_reset_tutorial_confirm_title`, `settings_confirm`
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Panel String Extractions
- [x] **ChapterMarkerPanel** — Description text + 3 contentDescriptions (Save/Edit/Delete) extracted
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Panel String Extractions
- [x] **AutoEditPanel** — 6 InfoCard labels (Clips/Music/Yes/No/Target/~60s) extracted
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Panel String Extractions
- [x] **BeatSyncPanel** — 3 stat labels (markers count, Beats, BPM) extracted with pluralization support
# NovaCut — Implementation Roadmap
## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
### Undo Stack Bounds
- [x] **Redo-path bounded** — `undoStack` on redo now bounded to 50 entries via `.takeLast(50)`, matching the save path
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **Mask.keyframes** — MaskKeyframe list with timeOffsetMs, points, easing now serialized/deserialized
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **TextOverlay.textPath** — TextPath (type, points, progress) serialized/deserialized
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **TextOverlay.templateId** — Nullable string now persisted
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **TextOverlay.keyframes** — Keyframe list reuses existing serialize/deserializeKeyframe methods
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **TimelineMarker.notes** — String field added to marker serialization
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### ProjectAutoSave Serialization (6 missing fields)
- [x] **Clip.proxyUri + motionTrackingData** — Full MotionTrackingData with trackPoints serialized
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Export Fixes
- [x] **GIF speed calculation** — Frame extraction accounts for clip.speed in source timestamp mapping
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Export Fixes
- [x] **MIME type detection** — saveToGallery/getShareIntent detect GIF/WebM extensions for correct MIME
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Export Fixes
- [x] **Batch export config restore** — Original exportConfig saved before loop, restored after
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Export Fixes
- [x] **LZW code size** — Off-by-one fix in GIF encoder (>= instead of >)
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **setTrackBlendMode** — Added rebuildPlayerTimeline() + saveProject() (was missing)
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **pasteClipEffects** — Uses s.copiedEffects (lambda param) instead of stale captured state
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **deleteMultiSelectedClips** — Removes waveform entries for deleted clip IDs
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **SpeedCurve.getSpeedAt** — Returns safe default when clipDurationMs <= 0 (prevents NaN)
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **updateImageOverlay** — Added saveUndoState("Edit sticker") for undo support
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **removeTextOverlay** — Clears editingTextOverlayId when overlay being edited is removed
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **addImageOverlay** — endTimeMs clamped to totalDurationMs
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **moveClipToTrack** — Validates target track exists and type compatibility
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Bug Fixes
- [x] **seekTo** — Position clamped to [0, totalDurationMs]
# NovaCut — Implementation Roadmap
## v3.22.0 — Data Safety, Export Correctness & Bug Fixes
### Error Logging
- [x] **Silent catch blocks** — Log.w added to LutEngine (2), FrameCapture, VideoEngine, MultiCamEngine, MediaPicker (2)
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Trim Handle Fix
- [x] **Always-visible trim handles** — Removed `if (isSelected)` guard, handles render on all clips
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Trim Handle Fix
- [x] **Auto-select on drag** — Trim handle drag start calls `onClipSelected()` so clip is selected immediately
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Trim Handle Fix
- [x] **Visual hierarchy** — Unselected handle alpha 50%, selected full color
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Accessibility (37 strings)
- [x] **27+ null contentDescriptions** — Fixed across 16 UI files
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Exception Logging (16 catches)
- [x] **Silent catch blocks** — Log.w added across AudioEngine, ColorMatchEngine, EffectShareEngine, InpaintingEngine, VoiceoverRecorder, WhisperEngine, ProjectAutoSave
# NovaCut — Implementation Roadmap
## v3.21.0 — Trim Handles, Accessibility & Quality
### Stub Engine UX
- [x] **"Coming soon" toast** — FrameInterpolation, Upscale, AI Background, Style Transfer buttons in AiToolsDelegate
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### Race Condition Fixes
- [x] **AudioMixerDelegate normalizeAudio()** — Re-validates clip inside coroutine after withContext
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### Race Condition Fixes
- [x] **AudioMixerDelegate detectBeats()** — Re-validates clips after async operation
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### Race Condition Fixes
- [x] **AiToolsDelegate saveAsTemplate()** — Captures state inside scope.launch
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### Integer Safety
- [x] **AiFeatures overflow** — 10 getFrameAtTime() calls changed to `* 1000L`
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### Integer Safety
- [x] **AiFeatures divzero** — 4 audio channel count guards added
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### UI State
- [x] **EditorScreen remember keys** — 2 remember blocks keyed on projectName
# NovaCut — Implementation Roadmap
## v3.19.0 — Comprehensive Bug & Quality Audit
### i18n (20+ strings)
- [x] **9 panels** — AiToolsPanel, AudioPanel, CaptionStyleGallery, MarkerListPanel, MaskEditorPanel, MediaPicker, SpeedCurveEditor, SpeedPresets, Timeline
# NovaCut — Implementation Roadmap
## v3.18.0 — Code Quality & Remaining i18n
### Bug Fixes
- [x] **InpaintingEngine bitmap leak** — Scaled input/mask bitmaps recycled in finally block
# NovaCut — Implementation Roadmap
## v3.18.0 — Code Quality & Remaining i18n
### Bug Fixes
- [x] **Stale User-Agent** — SegmentationEngine + WhisperEngine now use NovaCutApp.VERSION dynamically
# NovaCut — Implementation Roadmap
## v3.18.0 — Code Quality & Remaining i18n
### Bug Fixes
- [x] **Deprecated icon warnings** — Proper @Suppress annotations added
# NovaCut — Implementation Roadmap
## v3.18.0 — Code Quality & Remaining i18n
### i18n (4 strings)
- [x] **ColorGradingPanel** — Lift, Gamma, Gain labels extracted
# NovaCut — Implementation Roadmap
## v3.18.0 — Code Quality & Remaining i18n
### i18n (4 strings)
- [x] **StickerPickerPanel** — "Select sticker" accessibility label extracted
# NovaCut — Implementation Roadmap
## v3.17.0 — Security & Resource Leak Audit
### Security
- [x] **Zip Slip path traversal** — ProjectArchive validates canonicalPath stays within targetDir
# NovaCut — Implementation Roadmap
## v3.17.0 — Security & Resource Leak Audit
### Resource Leaks
- [x] **InpaintingEngine ONNX** — Session, tensors, results wrapped in nested finally blocks
# NovaCut — Implementation Roadmap
## v3.17.0 — Security & Resource Leak Audit
### Resource Leaks
- [x] **HttpURLConnection** — disconnect() added to InpaintingEngine, SegmentationEngine, WhisperEngine downloaders
# NovaCut — Implementation Roadmap
## v3.17.0 — Security & Resource Leak Audit
### Resource Leaks
- [x] **ProjectArchive ZipInputStream** — Replaced manual close() with .use {} for exception safety
# NovaCut — Implementation Roadmap
## v3.16.0 — Resource Leak Fix & Remaining i18n
### Bug Fix
- [x] **AiFeatures MediaMetadataRetriever leak** — `generateAutoEdit()` retriever wrapped in try/finally with `release()`, preventing native resource leak during auto-edit clip scoring
# NovaCut — Implementation Roadmap
## v3.16.0 — Resource Leak Fix & Remaining i18n
### Final contentDescription Localization (6 strings)
- [x] **AiSuggestionBanner** — "Dismiss suggestion" contentDescription extracted
# NovaCut — Implementation Roadmap
## v3.16.0 — Resource Leak Fix & Remaining i18n
### Final contentDescription Localization (6 strings)
- [x] **DrawingOverlayPanel** — "Undo", "Clear", "Eraser" contentDescriptions extracted
# NovaCut — Implementation Roadmap
## v3.16.0 — Resource Leak Fix & Remaining i18n
### Final contentDescription Localization (6 strings)
- [x] **MultiCamPanel** — "Close" contentDescription extracted
# NovaCut — Implementation Roadmap
## v3.16.0 — Resource Leak Fix & Remaining i18n
### Final contentDescription Localization (6 strings)
- [x] **Timeline** — "Toggle waveform" contentDescription extracted
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **AudioMixerPanel** — Effects track header, contentDescriptions (pan, mute/unmute, solo/unsolo, audio effects, master, add/remove effect)
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **MediaManagerPanel** — Stat labels (Assets, Size, Missing), "Used Nx", contentDescriptions (Go to, Missing)
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **ExportProgressOverlay** — ETA remaining format, Cancel contentDescription
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **ChromaKeyPanel** — 6 slider labels (Red, Green, Blue, Similarity, Smoothness, Spill Suppress)
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **RenderPreviewSheet** — Duration breakdown labels (Re-encode, Pass-through)
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **ChapterMarkerPanel** — Default chapter name format string
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **KeyframeCurveEditor** — 8 preset labels + Presets/Delete/Close contentDescriptions
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **CaptionEditorPanel** — Auto Caption cd, word count format, Edit cd
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **SmartReframePanel** — Close contentDescription
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **StickerPickerPanel** — Import sticker contentDescriptions
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **CloudBackupPanel** — Close contentDescription
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### Panel Localization (~55 strings)
- [x] **SnapshotHistoryPanel** — Default snapshot prefix now uses stringResource
# NovaCut — Implementation Roadmap
## v3.15.0 — Comprehensive Localization & Notification i18n
### ExportService Notification i18n
- [x] **7 notification strings** — Title, progress text, complete title/text, failed title/default, cancel action
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### Bug Fixes
- [x] **GIF color quantization** — Fixed operator precedence in `((rgb and 0xF0) shr 4)` — shift had higher precedence than AND, causing wrong palette mapping and incorrect colors in exported GIFs
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### FirstRunTutorial Localization
- [x] **@StringRes migration** — `TutorialStep` replaced with `TutorialStepDef` using `titleRes`/`descriptionRes` resource IDs
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### FirstRunTutorial Localization
- [x] **14 string resources** — 4 titles, 4 descriptions, Skip, Next, Get Started, step counter format
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### ExportSheet Localization
- [x] **8 string extractions** — "Elapsed:", "Transparent Background (WebM VP9)", "Audio Codec", "OTIO", "FCPXML" + reused existing subtitle/stems/chapter strings
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### Remaining Panel String Extractions
- [x] **SpeedCurveEditor** — `"Speed: %.2fx"` format string extracted
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### Remaining Panel String Extractions
- [x] **ProjectTemplateSheet** — `"Import Template"` extracted
# NovaCut — Implementation Roadmap
## v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
### Remaining Panel String Extractions
- [x] **SnapshotHistoryPanel** — Default snapshot name prefix extracted

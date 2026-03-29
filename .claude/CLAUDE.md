# NovaCut — Project Instructions & Research

## Architecture
- **Language:** Kotlin, Jetpack Compose UI
- **DI:** Hilt
- **Video engine:** Media3 ExoPlayer (preview) + Transformer (export)
- **Effects pipeline:** OpenGL ES 3.0 fragment shaders via `GlEffect`
- **ML runtime:** ONNX Runtime Android (Whisper, segmentation)
- **Database:** Room (ProjectDatabase, auto-migrations)
- **State:** Single `EditorState` data class in `EditorViewModel`, `StateFlow`-based
- **Package:** `com.novacut.editor`

## Key File Locations
- ViewModel: `app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt` (~3650 lines)
- Editor UI: `app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt` (~1600 lines)
- Data model: `app/src/main/java/com/novacut/editor/model/Project.kt`
- Video engine: `app/src/main/java/com/novacut/editor/engine/VideoEngine.kt`
- AI features: `app/src/main/java/com/novacut/editor/ai/AiFeatures.kt`
- Shader effects: `app/src/main/java/com/novacut/editor/engine/ShaderEffect.kt`
- Auto-save: `app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt`

## Conventions
- Panels toggled via `PanelVisibility` data class + `PanelId` enum in `EditorState`
- All clip mutations go through `_state.update {}` followed by `rebuildTimeline()`
- Undo via `saveUndoState("description")` before mutations
- Color theme: Catppuccin Mocha (`Mocha.*` in `Theme.kt`)

## Shader Pattern
- Constants at bottom of `EffectShaders` in `ShaderEffect.kt`
- Header: `H = "#version 300 es\nprecision mediump float;\n..."`
- Transitions use `uniform float uDurationUs;` + `uTimeUs;`
- VideoEngine maps `TransitionType` → factory in `when` blocks (~line 362 and ~776)

## Export Presets
- `ExportConfig.youtube1080()`, `.tiktok()`, `.instagram()`, `.instagramSquare()`, `.threads()`
- ExportSheet shows preset chips via `PlatformPreset.entries`

## Timeline
- Magnetic snapping: `findSnapTarget()`, 8dp threshold, diamond indicators
- Clip grouping: `Clip.groupId`, `groupSelectedClips()`/`ungroupSelectedClips()`
- Slip/slide: drag body = slide (normal), drag body = slip (trim mode)
- Scrubbing: `onScrubStart`/`onScrubEnd` wired to `beginScrub()`/`endScrub()`

## New Engine Files (Tier 2)
- `engine/whisper/SherpaAsrEngine.kt` — ASR abstraction (Sherpa-ONNX backend, 99 langs)
- `engine/NoiseReductionEngine.kt` — ML noise reduction (DeepFilterNet + spectral gate fallback)
- `engine/PiperTtsEngine.kt` — High-quality TTS (10 voices, 8 langs, VITS architecture)
- `engine/LottieTemplateEngine.kt` — Animated title rendering (10 templates, frame-by-frame export)
- `engine/BeatDetectionEngine.kt` — Spectral flux onset detection + BPM estimation
- `engine/LoudnessEngine.kt` — EBU R128 loudness measurement + 6 platform presets

## Tier 3 Engines
- `engine/FrameInterpolationEngine.kt` — RIFE slow-motion
- `engine/InpaintingEngine.kt` — LaMa object removal
- `engine/UpscaleEngine.kt` — Real-ESRGAN upscaling
- `engine/VideoMattingEngine.kt` — RVM AI green screen
- `engine/StabilizationEngine.kt` — OpenCV stabilization
- `engine/StyleTransferEngine.kt` — 9 AI art filters
- `engine/SmartReframeEngine.kt` — Auto-crop with EMA smoothing
- `engine/FFmpegEngine.kt` — FFmpegX fallback encoder
- `engine/SubtitleRenderEngine.kt` — Canvas + ASS rendering

## Tier 4 Engines
- `engine/TapSegmentEngine.kt` — MobileSAM tap-to-segment
- `engine/TimelineExchangeEngine.kt` — OTIO/FCPXML interchange
- `engine/EditCommand.kt` — Command-pattern undo/redo foundation
- `engine/ProxyWorkflowEngine.kt` — 3-tier proxy media management
- `engine/EffectLibraryPanel.kt` — UI for effect copy/paste/export/import

## ViewModel Engine Injection (29 engines)
videoEngine, projectDao, audioEngine, autoSave, aiFeatures, voiceoverEngine,
templateManager, proxyEngine, settingsRepo, ttsEngine, effectShareEngine,
noiseReductionEngine, beatDetectionEngine, loudnessEngine, frameInterpolationEngine,
inpaintingEngine, upscaleEngine, videoMattingEngine, stabilizationEngine,
styleTransferEngine, smartReframeEngine, ffmpegEngine, subtitleRenderEngine,
piperTtsEngine, lottieTemplateEngine, tapSegmentEngine, timelineExchangeEngine,
proxyWorkflowEngine, sherpaAsrEngine

## Post-Expansion Audit Fixes
- CompoundClips now serialized/deserialized in ProjectAutoSave
- All 9 CaptionStyle fields now serialized (was only 3)
- EffectLibraryPanel created and wired with AnimatedVisibility
- Video scopes overlay uses AnimatedVisibility (was bare `if`)
- 4 additional engines wired into ViewModel (TapSegment, TimelineExchange, ProxyWorkflow, SherpaAsr)
- OTIO + FCPXML export methods added to ViewModel

## UI Reachability Fixes
- VHS_RETRO + LIGHT_LEAK added to EffectType enum + default params + VideoEngine rendering
- OTIO/FCPXML export buttons added to ExportSheet, wired to ViewModel
- Group/Ungroup clip buttons added to ToolPanel sub-menu + EditorScreen dispatch
- Settings: "Reset Tutorial" button added to SettingsScreen
- TtsPanel: System/Piper toggle with Piper voice list (6 voices)
- TextTemplateGallery: Static/Animated tab with 10 Lottie template cards
- AudioNormPanel: Updated presets (added TikTok, Cinema; removed duplicate Streaming)

## Release Prep Fixes
- CAMERA permission added to manifest + runtime request
- Hardcoded keystore password replaced with env vars
- FileProvider paths expanded (voiceovers, tts, noise_reduced, luts, archives, exports)
- VIEW intent handling: incoming video URIs create new project + navigate to editor
- Permission denial: detects permanent denial, directs to Settings
- ProGuard rules verified comprehensive (Hilt, Room, Media3, ONNX, MediaPipe, Coil)

## Build Info
- `versionCode = 75`, `versionName = "3.15.0"`
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- R8 minify + shrink enabled for release
- Signing via `keystore.properties` or env vars (`NOVACUT_KS_PASS`, `NOVACUT_KEY_ALIAS`, `NOVACUT_KEY_PASS`)

## v3.8.0 — Competitor-Inspired Features (14 new)
Research across CapCut, VN, KineMaster, PowerDirector, DaVinci Resolve iPad, and FOSS landscape.

### New UI Components
- `ui/editor/AiSuggestionBanner.kt` — Contextual AI suggestion banner above timeline (auto-detects: no effects → "Auto Color", low audio energy → "Denoise", no transitions → "Add Transitions")
- `ui/editor/DrawingOverlayPanel.kt` — Drawing/annotation overlay with 6 Catppuccin colors, brush size slider, eraser, undo per path (from KineMaster)
- `ui/editor/MultiCamPanel.kt` — 2x2 grid multi-cam angle switching with thumbnail previews, sync button (from PowerDirector)
- `ui/editor/RadialActionMenu.kt` — Long-press radial quick-action menu on preview with spring animation, context-sensitive items (from KineMaster Media Wheel)

### Features Added
- Speed range extended from 16x to 100x everywhere (matching CapCut): ClipEditingDelegate, VideoEngine SpeedProvider, SpeedCurveEditor presets/slider
- Transparent video export via WebM VP9 alpha channel toggle in ExportSheet (from KineMaster)
- Filler word auto-strip from caption text via `cleanCaptionText()` in AiFeatures (from CapCut)
- Manual beat tap mode in BeatSyncPanel — tap button during playback to place markers (from VN)
- Pinch-to-transform gestures on PreviewPanel — pinch=scale, rotate=rotation, drag=position (from CapCut)
- Script-to-video input in AutoEditPanel — text field for smarter clip selection via `parseScriptToSegments()` (from CapCut)
- Auto-generated effect parameter UI — `ParamRange` metadata + `paramRangesForType()` replaced 170-line hand-coded when block in ToolPanel (from Pitivi pattern)
- Community template export/import — `.novacut-template` files via TemplateManager + share intent in ProjectTemplateSheet
- Project backup panel — CloudBackupPanel rewritten from stub to functional export/import archive panel
- F-Droid fastlane metadata structure created

### New EditorState Fields
- `aiSuggestion: AiSuggestion?` — contextual AI suggestion data
- `drawingPaths: List<DrawingPath>`, `isDrawingMode`, `drawingColor`, `drawingStrokeWidth` — drawing overlay state
- `backupEstimatedSize`, `lastBackupTime`, `isExportingBackup` — backup panel state

### New PanelIds
- `DRAWING`, `MULTI_CAM`

## v3.15.0 — Comprehensive Localization & Notification i18n

### Panel Localization (~55 strings)
- AudioMixerPanel: effects track header, 7 semantic contentDescriptions (pan, mute/unmute, solo/unsolo, fx, master, remove)
- MediaManagerPanel: 3 stat labels, usage count format, 2 contentDescriptions
- ExportProgressOverlay: ETA remaining format, cancel contentDescription
- ChromaKeyPanel: 6 slider labels
- RenderPreviewSheet: 2 duration breakdown labels
- ChapterMarkerPanel: default chapter name format
- KeyframeCurveEditor: 8 preset labels + 3 contentDescriptions
- CaptionEditorPanel: 3 remaining strings (auto caption cd, word count, edit cd)
- SmartReframePanel, StickerPickerPanel, CloudBackupPanel, SnapshotHistoryPanel: remaining contentDescriptions

### ExportService Notification i18n
- 7 notification strings extracted: title, progress, complete, failed, cancel action

## v3.14.0 — GIF Quantization Fix & Deep Localization

### GIF Color Quantization Fix
- Operator precedence bug: `rgb and 0xF0 shr 4` → `(rgb and 0xF0) shr 4` (2 occurrences in ExportDelegate)
- Kotlin `shr` binds tighter than `and`, causing incorrect 4-bit color quantization

### Deep Localization (5 screens, ~30 strings)
- FirstRunTutorial: migrated from `TutorialStep(String, String)` to `TutorialStepDef(@StringRes Int, @StringRes Int)` — 10 tutorial strings + Skip/Next/Get Started
- ExportSheet: 8 hardcoded labels extracted (elapsed, transparent bg, audio codec, OTIO, FCPXML)
- SpeedCurveEditor: speed label format string
- ProjectTemplateSheet: "Import Template" label
- SnapshotHistoryPanel: default snapshot name prefix

## v3.13.0 — GIF Hardening, Settings Localization & Editor Polish

### GIF Encoder Hardening
- Bitmap leak fix: `frames.forEach { it.recycle() }` moved to finally block
- Division-by-zero guard: `gifFrameRate.coerceAtLeast(1)` before frame interval calculation

### Settings Screen Localization (22 strings)
- All hardcoded SettingsScreen labels extracted to strings.xml
- Sections: Editor, Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height, Default Mode, Haptic Feedback, Confirm Before Delete, Thumbnail Cache, Default Export Quality

### Reset Tutorial Confirmation
- AlertDialog added before resetting tutorial state

### Panel String Extractions
- ChapterMarkerPanel: description + 3 contentDescriptions (Save/Edit/Delete)
- AutoEditPanel: 6 InfoCard labels
- BeatSyncPanel: 3 stat labels with format string

### Undo Stack Bounds
- Redo-path undoStack bounded to 50 entries via `.takeLast(50)`

## v3.12.0 — GIF Export, Accessibility & Panel Localization

### GIF Export Backend
- Full GIF89a encoder with LZW compression in ExportDelegate (no external libraries)
- Frame extraction pipeline: `extractThumbnail()` per frame, scaling to `gifMaxWidth`
- Supports configurable frame rate and max 300 frames, progress reporting
- `encodeGif()` + `lzwEncode()` private methods with Netscape looping extension

### Accessibility (25 contentDescription fixes)
- Replaced `contentDescription = null` across 13 panel files with `stringResource(R.string.cd_*)`
- 23 new content description strings added to strings.xml
- Covers: AiToolsPanel, BeatSyncPanel, FillerRemovalPanel, FirstRunTutorial, MultiCamPanel, etc.

### Panel Localization (75 string extractions)
- Extracted hardcoded `Text("...")` from 21 panel composable files to strings.xml
- 75 new `panel_*` prefixed string resources organized by panel name
- Panels: AudioMixer, CloudBackup, PipPresets, CaptionEditor, DrawingOverlay, EffectLibrary, etc.
- Added missing `stringResource`/`R` imports to 7 files

## v3.11.0 — Clip Labels, Track Controls & Localization

### Clip Label Picker
- Color label selector UI (7 colors from ClipLabel enum) in BottomToolArea clip edit sub-menu
- AnimatedVisibility Card with color circles, selection borders, dismiss on clip deselect

### Track Controls
- Collapse/expand all tracks toggle (UnfoldLess/UnfoldMore) in Timeline zoom controls row
- Track height cycling via long-press on track type icon (48→64→80→96→48)
- `onCollapseAllTracks`, `onExpandAllTracks`, `onSetTrackHeight` wired through EditorScreen

### ToolPanel Localization
- All 83 hardcoded TabItem/SubMenuItem labels extracted to strings.xml
- `TabItem.label: String` → `TabItem.labelRes: @StringRes Int`
- `SubMenuItem.label: String` → `SubMenuItem.labelRes: @StringRes Int`
- Resolved via `stringResource()` in composable rendering (BottomTabBar, SubMenuGrid)

## v3.9.0 — Export Expansion, Settings & UX Polish

### Export Enhancements
- GIF export mode with configurable frame rate (10/15/20fps) and max width (320/480/640px)
- Frame capture (PNG/JPEG) from current playhead position
- Subtitle export (SRT/VTT/ASS) from caption data
- Audio stems export toggle
- Chapter markers export toggle
- `FrameCaptureFormat` enum added to ExportConfig

### Settings Expansion (7 new AppSettings fields)
- Timeline: Show Waveforms, Snap to Beat, Snap to Markers, Default Track Height (48/64/80/96)
- Editor: Confirm Before Delete, Thumbnail Cache Size (64/128/256 MB), Default Export Quality
- All persisted via DataStore with live sync to EditorViewModel

### Marker List Panel
- `MarkerListPanel.kt` — searchable, filterable marker list with color filter chips
- Inline label editing, jump-to-time on click, delete per marker
- Wired via `PanelId.MARKER_LIST` + ToolPanel "Marker List" action

### Track Header Enhancements
- Track model fields: `showWaveform`, `trackHeight`, `isCollapsed`
- ViewModel methods: `toggleTrackWaveform`, `setTrackHeight`, `toggleTrackCollapsed`, `collapseAllTracks`, `expandAllTracks`
- Serialized/deserialized in ProjectAutoSave

### Snap-to-Beat/Marker Scrubbing
- Timeline snap targets extended with beat markers (when snapToBeat enabled) and timeline markers (when snapToMarker enabled)
- Settings-driven via SettingsRepository → EditorViewModel StateFlow sync

## v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish

### Track Header Enhancements (UI Wiring)
- Collapse/expand chevron per track (ChevronRight/ExpandMore) — collapsed tracks show thin colored bars
- Per-track height from `Track.trackHeight` (replaces hardcoded 60dp)
- Waveform toggle icon (GraphicEq) in track header for VIDEO/AUDIO tracks
- Waveform rendering gated by `track.showWaveform`

### Settings Wired into Editor
- `confirmBeforeDelete` — gates delete confirmation dialog (skip when false)
- `defaultExportQuality` — applied to initial ExportConfig on load (LOW/MEDIUM/HIGH)
- `showWaveforms` — global waveform visibility toggle (empty map when false)

### Chapter Markers on Export
- `ExportDelegate.startExport()` populates `ExportConfig.chapters` from `timelineMarkers` when `includeChapterMarkers` is true

### Clip Color Labels
- `ClipLabel` enum (NONE, RED, PEACH, GREEN, BLUE, MAUVE, YELLOW) with Catppuccin ARGB values
- `Clip.clipLabel` field with colored 3dp top border in Timeline
- `setClipLabel()` ViewModel method with undo support
- Serialized/deserialized in ProjectAutoSave

### Keyboard Shortcuts (External Keyboard)
- Space=play/pause, Delete=delete clip, M=add marker, S=split
- Ctrl+Z=undo, Ctrl+Shift+Z/Ctrl+Y=redo, Ctrl+S=save
- Arrow keys=seek ±1s, Ctrl+arrows=seek ±5s
- +/-=zoom in/out, Ctrl+C/V=copy/paste effects
- FocusRequester + focusable() for key event capture

### Model Changes
- `ExportConfig.transparentBackground: Boolean` — VP9 alpha export toggle
- `Effect.kt` — `ParamRange` data class, `parameterRanges` map, `paramRangesForType()` for auto-generated UI
- `EditorModels.kt` — `DrawingPath` data class, `AiSuggestion` data class, `ScriptSegment` data class

## v3.0.0 Final Engines
- `engine/SoundpipeDspEngine.kt` — Reverb (Schroeder), Moog filter, distortion
- `engine/RiveTemplateEngine.kt` — 5 interactive templates
- `engine/CloudInpaintingEngine.kt` — ProPainter cloud API

## v3.0.0 Performance & Polish
- Playhead in separate StateFlow (60→6 copies/sec during playback)
- 7 new easings (bounce, elastic, back, circular, expo, sine, cubic)
- 4 new speed presets (time freeze, film reel, heartbeat, crescendo)
- MultiCamEngine wired with audio-sync
- Adjustment layers cascade effects in export pipeline
- LruCache thumbnails (memory-bounded, 1/8 heap)
- AiFeatures: Log.w on 15 silent catch blocks
- Accessibility: contentDescription on all interactive UI elements

## v3.0.0 Final Features
- ImageOverlay + ImageOverlayType for sticker/GIF overlays
- TimelineMarker with 6 colors + jump navigation
- Favorites/recent effects in SettingsRepository (DataStore)
- Proxy playback: prepareTimeline uses clip.proxyUri
- Batch export per-item progress tracking
- EditCommand bridge documented for future migration

## Completed Work
All roadmap + performance + accessibility + final features complete.

---

# Feature Breakdown & Open Source Research

## 1. Timeline & NLE Editing

### Current State
- Basic trim, split, speed adjustment
- `slipClip()` and `slideClip()` implemented but not wired to UI gestures
- No magnetic snapping, no clip grouping
- `beginScrub()`/`endScrub()` now wired to ruler drag

### Open Source Improvements

| Project | URL | Technique | Improvement |
|---------|-----|-----------|-------------|
| **Kdenlive** | github.com/KDE/kdenlive | Ripple/roll/slip/slide edits, magnetic snapping (sorted edge list + proximity threshold), clip grouping (group IDs) | Implement magnetic snapping: track all clip edges in a sorted list, snap when drag enters 8dp threshold. Add `ClipGroup` data class for grouped moves |
| **Olive** | github.com/olive-editor/olive | Command pattern for all edits (each op is a serializable `EditCommand`), node-based compositing DAG | Adopt command pattern for undo/redo — each cut/move/trim is a command object. More reliable than current snapshot-based undo |
| **OpenTimelineIO** | github.com/AcademySoftwareFoundation/OpenTimelineIO | Pixar's timeline interchange format. Java bindings available with arm64-v8a JNI. Supports FCPXML, EDL, AAF adapters | Add OTIO export for desktop NLE round-tripping. Users rough-cut on mobile, finish on DaVinci/Premiere |

### New Features to Add
- **Magnetic timeline snapping** — snap clip edges to other edges, playhead, markers
- **Clip grouping** — select multiple clips, group/ungroup, move as unit
- **Ripple delete** — delete clip and close the gap automatically
- **Wire `slipClip()`/`slideClip()`** to horizontal drag gestures on clip thumbnails/bodies

---

## 2. Audio Mixing & Effects

### Current State
- Parametric EQ, compressor (attack/release now fixed), chorus, delay, pitch shift
- Pan control slider (now implemented), VU meters with ballistic smoothing
- Pitch shift uses naive linear interpolation (audible artifacts)

### Open Source Improvements

| Project | URL | Technique | Improvement |
|---------|-----|-----------|-------------|
| **TarsosDSP** | github.com/JorenSix/TarsosDSP | Pure Java DSP: IIR filters, YIN pitch detection, WSOLA time-stretch. Runs directly on Android | Replace naive pitch shift with WSOLA algorithm. Add real-time pitch detection for auto-tune features |
| **Oboe** | github.com/google/oboe | Google's C++ low-latency audio. Includes sinc-based sample rate converter | Use Oboe resampler for mixing 44.1kHz music with 48kHz video audio. Extract standalone resampler from `oboe/src/flowgraph/resampler/` |
| **Soundpipe** | github.com/PaulBatchelor/Soundpipe | 100+ C DSP modules: Moog filter, reverb (Schroeder/zitareverb), compressor, distortion. Compiles on Android NDK | Add reverb effect (currently missing). Link via NDK, use zitareverb module for broadcast-quality reverb |
| **libebur128** | github.com/jiixyj/libebur128 | Pure C EBU R128 loudness measurement. Momentary/short-term/integrated loudness | Replace approximate LUFS normalization with standards-compliant measurement. Add real-time loudness meter overlay |

### New Features to Add
- **Reverb effect** — use Soundpipe's zitareverb via NDK
- **Proper WSOLA pitch shift** — replace current naive implementation via TarsosDSP
- **EBU R128 loudness normalization** with platform presets (YouTube -14, Podcast -16, Broadcast -23 LUFS)
- **Sidechain ducking** — compress music keyed by voice RMS (Ardour-style)

---

## 3. Noise Reduction

### Current State
- Basic spectral analysis, applies DSP filters. No ML-based approach.

### Open Source Improvements

| Project | URL | Technique | Android? | Model Size |
|---------|-----|-----------|----------|------------|
| **AndroidDeepFilterNet** | github.com/KaleyraVideo/AndroidDeepFilterNet | Deep neural net predicting complex spectral filters per frequency bin. PESQ 3.5-4.0+ | **Yes — Maven dependency** | ~8MB (lazy-load) |
| **RNNoise** | github.com/xiph/rnnoise | GRU-based RNN with bark-scale band decomposition. Tiny model, real-time on RPi | **Yes — NDK** | ~85KB |
| **NSNet2** | github.com/microsoft/DNS-Challenge | ONNX model with early-exit for adaptive compute budget | **Yes — ONNX Runtime** | ~5MB |

### Recommendation
- **Primary:** AndroidDeepFilterNet (Maven, one-line integration, best quality)
- **Fallback:** RNNoise for low-end devices (85KB model)
- **Add "Clean Audio" toggle** on audio clips using DeepFilterNet

---

## 4. Beat Detection & Music Analysis

### Current State
- Basic energy-based beat detection. Runs on wrong dispatcher (fixed).

### Open Source Improvements

| Project | URL | Technique | Android? |
|---------|-----|-----------|----------|
| **aubio** | aubio.org / github.com/aubio/aubio | Onset detection (spectral flux, HFC), beat tracking, tempo estimation. C with Android NDK build scripts | **Yes — NDK prebuilt** |
| **TarsosDSP** | github.com/JorenSix/TarsosDSP | BeatRoot algorithm (Simon Dixon), percussion onset detection | **Yes — pure Java** |
| **Essentia** | essentia.upf.edu | `RhythmExtractor2013`, key detection, chord recognition, mood classification | Possible but heavy (~50MB) |

### Recommendation
- **Primary:** aubio via NDK (best accuracy, prebuilt Android module at github.com/adamski/aubio-android)
- **Feature:** "Snap cuts to beats" — auto-place edit points on beat markers

---

## 5. Speech-to-Text / Auto-Captions

### Current State
- Whisper via ONNX Runtime. No KV-cache (O(n^2) per chunk). GPT-2 byte decode incomplete.

### Open Source Improvements

| Project | URL | Speed (Android) | Languages | Model Size |
|---------|-----|-----------------|-----------|------------|
| **Sherpa-ONNX** | github.com/k2-fsa/sherpa-onnx | **27 tok/s** (Whisper Tiny), RTF 0.07 | 99 languages | ~100MB |
| **Moonshine** (via Sherpa) | Same | **42 tok/s**, RTF 0.05 | English only | ~125MB |
| **Vosk** | github.com/alphacep/vosk-api | Streaming, low latency | 20+ languages | 50MB-2GB |
| **whisper.cpp** | github.com/ggml-org/whisper.cpp | 0.55 tok/s (51x slower than Sherpa) | 99 languages | ~75MB |

### Recommendation
- **Replace current Whisper implementation with Sherpa-ONNX** — 51x faster, same model
- Sherpa-ONNX provides Android SDK with Kotlin bindings, word-level timestamps
- Use Moonshine Tiny for English-only (fastest), Whisper Tiny multilingual for international

---

## 6. Color Grading & LUTs

### Current State
- Lift/Gamma/Gain wheels, basic HSL, LUT import (now fully wired with file picker).
- Color wheel indicator dot and blue channel now fixed.

### Open Source Improvements

| Project | URL | Technique | Improvement |
|---------|-----|-----------|-------------|
| **OpenColorIO** | github.com/AcademySoftwareFoundation/OpenColorIO | ACES pipeline, tetrahedral 3D LUT interpolation, GPU shader code generators | Extract GLSL shader code for accurate LUT application. Tetrahedral interpolation is higher quality than hardware trilinear |
| **Filmic tonemapping** | github.com/johnhable/fw-public | S-curve tone mapping (Uncharted 2 filmic curve) | Add as "Film Look" creative grade. ~10 lines GLSL. Also useful for HDR→SDR tone mapping |
| **Waveform/Vectorscope** | N/A (compute shader approach) | Use `GL_SHADER_STORAGE_BUFFER` + compute shaders (ES 3.1) for GPU-accelerated scope rendering | Replace CPU-based scope analysis with GPU compute for real-time scopes during playback |

### New Features to Add
- **ACES color pipeline** — IDT/ODT transforms for accurate color management
- **GPU-accelerated waveform/vectorscope** via compute shaders
- **Filmic tone mapping** as a creative preset
- **HDR grading** support with HLG/PQ output (Android 13+)

---

## 7. Shader Effects & Transitions

### Current State
- Custom GLSL effects for brightness, contrast, saturation, blur, blend modes.
- Blend modes composite against mid-gray (no dual-texture support).
- Gaussian blur is single-pass 3x3 (not true Gaussian).

### Open Source Improvements

| Project | URL | What It Offers |
|---------|-----|----------------|
| **gl-transitions** | github.com/gl-transitions/gl-transitions | **80+ GLSL transition shaders** with standardized interface (`vec4 transition(vec2 uv)`). Drop-in compatible with Media3 GlEffect. Includes: page curl, morph dissolve, pixelation, kaleidoscope, directional wipe, dreamy zoom |
| **GPUImage Android** | github.com/cats-oss/android-gpuimage | 100+ image filter shaders: bilateral filter (skin smoothing), Kuwahara (oil paint), halftone, sketch, toon, vignette, color matrix |
| **Shadertoy ports** | shadertoy.com | Film grain, VHS glitch, lens flare, light leaks, scanlines. Each is 20-50 lines GLSL |

### New Features to Add
- **Drop in gl-transitions** — instant 80+ transition library (lowest effort, highest impact)
- **Film grain** — perceptual-aware noise (more in shadows, less in highlights)
- **VHS/Retro effect** — scanlines + chroma bleeding + tracking distortion
- **Glitch effect** — RGB channel split + block corruption
- **Two-pass separable Gaussian blur** — replace current 3x3 box filter
- **Light leaks** — additive blend of pre-rendered leak textures

---

## 8. Video Stabilization

### Current State
- Basic stabilization analyzing first 30s (now extended to 2min with cancellation).
- Uses frame differencing, not proper optical flow.

### Open Source Improvements

| Project | URL | Technique | Speed (Mobile) |
|---------|-----|-----------|----------------|
| **OpenCV Android SDK** | opencv.org | ORB features + Lucas-Kanade sparse optical flow + RANSAC affine + Kalman smoothing | ~5-10ms/frame (L-K on 200 points) |
| **vid.stab** | github.com/georgmartius/vid.stab | Two-pass: block matching → trajectory smoothing with configurable Gaussian kernel | ~3K lines C, compiles with NDK |

### Recommendation
- **OpenCV L-K + Kalman** — best accuracy-to-performance on mobile
- Process offline during import, store transform data, apply in real-time via GPU affine transform
- Crop 10-15% to hide borders

---

## 9. Object Segmentation & Background Removal

### Current State
- MediaPipe Selfie Segmentation. Full-res GPU readback per frame (performance issue).

### Open Source Improvements

| Project | URL | Quality | Speed (Mobile) | Model Size |
|---------|-----|---------|----------------|------------|
| **MediaPipe Selfie Seg** | developers.google.com/mediapipe | Binary mask, OK edges | ~30fps @ 256x256 | ~1-7MB |
| **RobustVideoMatting** | github.com/PeterL1n/RobustVideoMatting | True alpha matte, hair detail, temporal coherent | ~15-20fps @ 512x288 | ~15MB ONNX |
| **MobileSAM** | github.com/ChaoningZhang/MobileSAM | Tap-to-segment any object | ~200ms/frame | ~10MB |

### Recommendation
- **Keep MediaPipe** for real-time preview (fix readback to downsample first)
- **Add RVM** for "AI Green Screen" export quality (ONNX Runtime)
- **Add MobileSAM** for "tap to select object" — unique differentiator

---

## 10. Chroma Key

### Current State
- Basic chroma key with similarity/smoothness/spill parameters.

### Open Source Improvements

| Technique | Source | Improvement |
|-----------|--------|-------------|
| **YCbCr distance keying** | FFmpeg `vf_chromakey.c`, OBS `color-key-filter.c` | Switch from RGB/HSV to YCbCr — better separation of luminance from chrominance, handles shadows/highlights |
| **Spill suppression** | Keylight patent, OBS implementation | Subtract excess key channel: `pixel.g -= max(0, pixel.g - max(pixel.r, pixel.b) * balance)` |
| **Edge refinement** | Professional keyers | Erode matte 1px → blur edge zone (alpha 0.1-0.9 only) |
| **Clean plate keying** | Nuke IBK | Sample background-only frame, key = `abs(pixel - cleanPlate)`. Handles uneven lighting |

---

## 11. AI Frame Interpolation

### Current State
- Stub ("coming soon" toast).

### Open Source Implementations

| Project | URL | Speed (Android) | Model Size |
|---------|-----|-----------------|------------|
| **RIFE v4.6** | github.com/hzwer/ECCV2022-RIFE | 480p: 43ms, 720p: 100ms, 1080p: 250ms (via NCNN+Vulkan) | ~7-10MB ONNX |
| **IFRNet** | github.com/ltkong218/IFRNet | Comparable to RIFE via NCNN-Vulkan port | ~8MB |
| **FILM** | github.com/google-research/frame-interpolation | 5-10x slower than RIFE | Large |

### Recommendation
- **RIFE v4.6 via NCNN+Vulkan** — proven Android implementation (Jan 2026)
- Use as export-time effect for slow-motion generation (24→60/120fps)
- Zero-copy pipeline using `AHardwareBuffer` for 90%+ GPU utilization

---

## 12. AI Object Removal / Inpainting

### Current State
- Stub ("coming soon" toast).

### Open Source Implementations

| Project | URL | Speed | On-Device? | Use Case |
|---------|-----|-------|------------|----------|
| **LaMa-Dilated** | github.com/advimman/lama | **40ms/frame @ 512x512** (Galaxy S25) | **Yes — Qualcomm AI Hub** | Watermark/logo removal, static object erasing |
| **ProPainter** | github.com/sczhou/ProPainter | Server-speed | No (too heavy) | Temporally coherent video object removal |

### Recommendation
- **LaMa on-device** for per-frame erasing (watermarks, blemishes)
- **ProPainter cloud-side** for full video object removal (future premium feature)

---

## 13. Smart Reframing

### Current State
- Basic aspect ratio change. No subject tracking.

### Open Source Improvements
- **MediaPipe Face Detection** (BlazeFace ~400KB, <1ms) + **BlazePose** (~3-8MB) for subject tracking
- Build: detect faces/poses per frame → compute saliency-weighted crop window → smooth trajectory (EMA) → apply crop
- This replicates YouTube Shorts / Instagram Reels auto-crop

---

## 14. AI Upscaling / Super Resolution

### Current State
- Not implemented.

### Open Source Implementations

| Project | URL | Speed (Android) | Model Size |
|---------|-----|-----------------|------------|
| **Real-ESRGAN x4plus** | github.com/xinntao/Real-ESRGAN | **72ms/frame** (Galaxy S23, Qualcomm AI Hub) | ~17MB |
| **Real-ESRGAN General x4v3** | Same | Faster (lighter variant) | ~12MB |

### Recommendation
- Add "Enhance Video" feature using Real-ESRGAN via TFLite/QNN
- Use lighter variant for preview, full x4plus for export

---

## 15. Style Transfer / AI Filters

### Current State
- Not implemented.

### Open Source Implementations

| Project | URL | Model Size | Real-Time? |
|---------|-----|------------|------------|
| **AnimeGANv2** | github.com/TachibanaYoshino/AnimeGANv2 | **8.6MB** | Yes (ONNX) |
| **Fast Neural Style Transfer** | github.com/yakhyo/fast-neural-style-transfer | **6-7MB/style** | Yes |
| **CartoonGAN** | github.com/FlyingGoblin/CartoonGAN | ~15MB | Near real-time |

### Recommendation
- Bundle **AnimeGANv2** (8.6MB) + 3-4 Fast NST styles (~25MB total)
- Small enough to ship in APK, real-time preview capable

---

## 16. Text-to-Speech / Voiceover

### Current State
- Android system TTS. Race condition fixed. No high-quality voices.

### Open Source Improvements

| Project | URL | Quality | Speed | Model Size |
|---------|-----|---------|-------|------------|
| **Piper TTS via Sherpa-ONNX** | github.com/rhasspy/piper + github.com/k2-fsa/sherpa-onnx | Near-human VITS voices | 20-30ms generation | 15-65MB/voice |
| **eSpeak NG** | github.com/espeak-ng/espeak-ng | Robotic (formant) | Instant | ~2MB |

### Recommendation
- **Piper via Sherpa-ONNX** — 50+ languages, fully offline, production-proven on Android
- Bundle 3-4 voices (~60MB), download more on demand from Hugging Face
- "Text to Voiceover" feature: type text → select voice → generate audio → place on timeline

---

## 17. Motion Graphics & Titles

### Current State
- Basic text overlays with font/size/color. TextTemplateGallery for presets.

### Open Source Improvements

| Project | URL | Technique | Improvement |
|---------|-----|-----------|-------------|
| **Lottie** | github.com/airbnb/lottie-android | After Effects animations as JSON. Render via `LottieDrawable` to `Canvas` → export via Media3 | Animated title templates (typewriter, bounce, slide, kinetic typography) |
| **dotLottie** | dotlottie.io | Compressed Lottie bundles with theming + state machines | Template marketplace — users download `.lottie` packs, customize colors/fonts |
| **Rive** | github.com/rive-app/rive-android | Interactive animations with state machines, 120fps renderer | Next-gen interactive templates with user-adjustable parameters |

### Recommendation
- **Lottie + dotLottie** for animated title templates (lowest effort)
- Render `LottieDrawable` frame-by-frame for export (documented technique)
- Ship 10-15 bundled templates, offer downloadable packs

---

## 18. Export & Encoding

### Current State
- Media3 Transformer export. MP4/H.264. Batch export. EDL/FCPXML export.

### Open Source Improvements

| Project | URL | Improvement |
|---------|-----|-------------|
| **FFmpegX-Android** | github.com/mzgs/FFmpegX-Android | Fallback encoder for complex pipelines. Replaces archived ffmpeg-kit. Supports Android 10-15+, 300+ filters |
| **SVT-AV1** | github.com/AOMediaCodec/SVT-AV1 | AV1 software encoding (4.68x faster than x265). 30-50% bandwidth savings. Use preset 8-10 for mobile |
| **libass** | github.com/libass/libass | Burned-in subtitle rendering with full ASS/SSA styling. RTL, CJK, emoji support via FreeType+FriBidi+HarfBuzz |

### Social Media Export Presets
| Platform | Resolution | Codec | Bitrate | FPS | Aspect |
|----------|------------|-------|---------|-----|--------|
| YouTube | 1920x1080 | H.264 (AAC) | 8 Mbps | 24-60 | 16:9 |
| TikTok | 1080x1920 | H.264 (AAC) | 8-15 Mbps VBR | 30 | 9:16 |
| Instagram Reels | 1080x1920 | H.264 (AAC) | 5-8 Mbps | 30 max | 9:16 |

### New Features to Add
- **One-tap social media export presets** (YouTube, TikTok, Instagram, Threads)
- **AV1 export** option (detect hardware support via `MediaCodecList`)
- **Burned-in subtitle rendering** via libass during export
- **Transmuxing** for trim-only exports (10-100x faster than re-encoding)

---

## 19. Project Management

### Current State
- JSON auto-save (now with format versioning), Room database, project snapshots.
- `fallbackToDestructiveMigration` replaced with downgrade-only.

### Open Source Improvements

| Project | URL | Improvement |
|---------|-----|-------------|
| **Protocol Buffers** | protobuf.dev | Binary project format — 3-10x smaller, 20-100x faster than JSON. Schema evolution via field numbering. Use `protobuf-javalite` for Android |
| **OpenTimelineIO** | github.com/AcademySoftwareFoundation/OpenTimelineIO | Java bindings with arm64-v8a JNI for timeline import/export to FCPXML, EDL, AAF |

### New Features to Add
- **Rotating auto-save backups** (keep 2 most recent: `{id}.json` + `{id}.prev.json`)
- **Command-based undo/redo** with serialized command history
- **Protobuf project format** for fast load/save and crash recovery
- **OTIO export** for desktop NLE round-tripping

---

## 20. Proxy Workflow

### Current State
- ProxyEngine generates proxies. Hash collision now fixed (SHA-256). No progress reporting.

### Improvements (based on DaVinci Resolve / Premiere Pro patterns)
- **3-tier media system:** thumbnail (JPEG strips) → proxy (540p H.264 CRF 28) → original
- **Background generation** via `WorkManager` + `ForegroundService`
- **Auto-switch** between proxy (preview) and original (export)
- **Progress reporting** — poll `Transformer.getProgress()` in coroutine loop

---

## Priority Implementation Roadmap

### Tier 1 — Low Effort, High Impact
1. **gl-transitions** — drop in 80+ transition shaders (pure GLSL, standardized interface)
2. **Social media export presets** — one-tap YouTube/TikTok/Instagram export
3. **Magnetic timeline snapping** — sorted edge list + proximity threshold
4. **Film grain / VHS / Glitch effects** — 20-50 lines GLSL each

### Tier 2 — Medium Effort, High Impact
5. **Sherpa-ONNX ASR** — replace current Whisper with 51x faster implementation
6. **AndroidDeepFilterNet** — Maven dependency for ML noise reduction
7. **Piper TTS** — high-quality offline voiceover generation
8. **Lottie animated titles** — render via LottieDrawable for export
9. **aubio beat detection** — NDK library for snap-to-beat editing

### Tier 3 — High Effort, Differentiating
10. **RIFE frame interpolation** — on-device slow-motion via NCNN+Vulkan
11. **LaMa inpainting** — "Magic Eraser" for video (40ms/frame on flagship)
12. **Real-ESRGAN upscaling** — "Enhance Video" for old/low-res footage
13. **RobustVideoMatting** — AI green screen with true alpha mattes
14. **OpenCV stabilization** — L-K optical flow + Kalman smoothing
15. **AnimeGANv2 + style transfer** — AI art filters (8.6MB model)

### Tier 4 — Future / Premium
16. **MobileSAM** — tap-to-segment any object
17. **ProPainter** — cloud-based video object removal
18. **OpenTimelineIO** — desktop NLE round-tripping
19. **Protobuf project format** — binary serialization for performance
20. **CRDT collaborative editing** — multi-user real-time editing (v2+)

## v3.7.0 Post-Audit Fixes

### Critical Bug Fixes
- **createCompoundClip() crash** — was setting `trimEndMs = compoundDurationMs` without updating `sourceDurationMs`, violating `require(trimEndMs <= sourceDurationMs)`. Now sets `sourceDurationMs = compoundDurationMs`.
- **imageOverlays/timelineMarkers data loss** — neither was serialized in `ProjectAutoSave`. Added full serialize/deserialize for both. Recovery, snapshots, and auto-save state now include them.
- **setTransitionDuration crash on short clips** — `coerceIn(100L, clip.durationMs / 2)` threw `IllegalArgumentException` when clip < 200ms. Now uses `coerceAtLeast(100L)` on the upper bound.

### Persistence Fixes
- **toggleTrackMute/Visibility** — now call `rebuildPlayerTimeline()` + `saveProject()` (mute/visibility changes were lost on restart and not reflected in preview)
- **toggleTrackLock** — now calls `saveProject()`
- **setTrackOpacity** — now calls `saveProject()`
- **setClipOpacity** — now calls `saveProject()`

### Performance Fix
- **autoDuck() boxing** — replaced `waveform.map { }.toShortArray()` (boxes millions of Shorts) with `ShortArray(size) { }` constructor

### Deep Audit Fixes
- **Clip deserialization hardened** — `trimEndMs`/`trimStartMs`/`sourceDurationMs` coerced before Clip construction to satisfy `require(trimEndMs <= sourceDurationMs)`. Prevents silent clip loss from legacy auto-save data.
- **TextOverlay deserialization null-safe** — `deserializeTextOverlay` returns null for empty text instead of crashing on `require(text.isNotEmpty())`.
- **Batch export state reset** — `videoEngine.resetExportState()` called before each batch item to prevent race condition where previous COMPLETE state causes `first { it == EXPORTING }` to never resolve.

### Continued Debugging Fixes
- **TTS clips missing waveform** — Private `addClipToTrack` helper (used by TTS) now extracts waveform after adding clip, matching the delegate's behavior.
- **setClipVolume persistence** — Now calls `saveProject()` (was only saving on auto-save timer)
- **setClipFadeIn/FadeOut persistence** — Both now call `saveProject()`
- **setClipTransform persistence** — Now calls `saveProject()` (transform changes were lost on restart)

### Known Remaining Issues
- Export only uses first visible video track (overlay tracks with clips dropped). Multi-track compositing requires Media3 Compositor API.
- `splitClipAt()` helper ignores speed curves when computing source position
- SmartRenderEngine analysis results not used for actual export

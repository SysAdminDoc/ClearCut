# NovaCut v3.16.0

A professional Android video editor built with Kotlin and Jetpack Compose. Open alternative to CapCut, PowerDirector, and DaVinci Resolve — with on-device AI, GPU-accelerated effects, and desktop NLE interoperability.

## Changelog

### v3.16.0 — Resource Leak Fix & Remaining i18n
- **MediaMetadataRetriever leak fix** — `generateAutoEdit()` in AiFeatures.kt now properly releases retriever in try/finally block after clip quality scoring
- **Final contentDescription localization** — 6 remaining hardcoded strings extracted: AiSuggestionBanner (1), DrawingOverlayPanel (3), MultiCamPanel (1), Timeline (1)

### v3.15.0 — Comprehensive Localization & Notification i18n
- **55+ string extractions** — AudioMixerPanel (10), MediaManagerPanel (6), ExportProgressOverlay (2), ChromaKeyPanel (6), RenderPreviewSheet (2), ChapterMarkerPanel (1), KeyframeCurveEditor (10), ExportService notifications (7), CaptionEditorPanel (3), SmartReframePanel (1), StickerPickerPanel (2), CloudBackupPanel (1), SnapshotHistoryPanel (1)
- **ExportService notification i18n** — All notification title/text/action strings extracted to string resources
- **Accessibility contentDescriptions** — Remaining hardcoded contentDescription strings across 7 panels extracted to string resources
- **Semantic modifier localization** — AudioMixerPanel semantics blocks (pan, mute/unmute, solo/unsolo, fx) now use localized strings

### v3.14.0 — Audit Fixes, Deep Localization & GIF Correctness
- **GIF color quantization fix** — Fixed operator precedence bug in color quantization formula that caused incorrect palette mapping (wrong colors in exported GIFs)
- **FirstRunTutorial localization** — Extracted 4 titles, 4 descriptions, "Skip"/"Next"/"Get Started" to strings.xml with @StringRes data class
- **ExportSheet localization** — Extracted 8 hardcoded strings (Elapsed, Transparent Background, Audio Codec, OTIO, FCPXML, subtitle/stems/chapter labels)
- **SpeedCurveEditor localization** — Speed label format string extracted
- **ProjectTemplateSheet** — "Import Template" extracted to string resource
- **SnapshotHistoryPanel** — Default snapshot name prefix extracted

### v3.13.0 — GIF Hardening, Settings Localization & Editor Polish
- **GIF encoder hardening** — Fixed bitmap memory leak on export error (moved recycle to finally block), added division-by-zero guard for frame rate
- **Settings screen localization** — Extracted 22 hardcoded strings (labels, descriptions, quality labels) to strings.xml
- **Reset tutorial confirmation** — AlertDialog guard before clearing tutorial state, prevents accidental resets
- **Panel string extractions** — ChapterMarkerPanel (4 strings), AutoEditPanel (6 InfoCard labels), BeatSyncPanel (3 stat labels with format strings)
- **Undo stack bounds** — Redo-path undo stack now bounded to 50 entries, matching save path

### v3.12.0 — GIF Export, Accessibility & Panel Localization
- **GIF89a encoder** — Self-contained GIF export with LZW compression in ExportDelegate, no external dependencies. Configurable frame rate (10/15/20fps) and max width (320/480/640px), capped at 300 frames
- **25 contentDescription fixes** — Replaced `null` across 13 panel files with proper `stringResource(R.string.cd_*)` for screen readers
- **75 panel string extractions** — Hardcoded `Text("...")` from 21 panel composables extracted to strings.xml, organized by panel prefix

### v3.11.0 — Clip Labels, Track Controls & Localization
- **Clip label picker** — Color label selector UI (7 Catppuccin colors) via AnimatedVisibility Card in clip edit sub-menu
- **Collapse/expand all tracks** — UnfoldLess/UnfoldMore toggle in Timeline zoom controls row
- **Track height cycling** — Long-press track type icon cycles 48→64→80→96→48dp
- **ToolPanel @StringRes migration** — All 83 TabItem/SubMenuItem labels extracted to strings.xml, resolved via `stringResource()`

### v3.10.0 — Track Headers, Keyboard Shortcuts & Editor Polish
- **Track collapse/expand** — Per-track chevron icon; collapsed tracks render as thin 24dp colored bars
- **Per-track height** — Timeline uses `Track.trackHeight` instead of hardcoded 60dp
- **Waveform toggle** — Per-track GraphicEq icon gated by `Track.showWaveform`
- **Keyboard shortcuts** — Space=play/pause, Ctrl+Z=undo, arrows=seek, M=marker, S=split, Delete=delete, Ctrl+S=save, Ctrl+C/V=copy/paste effects
- **Clip color labels** — ClipLabel enum (7 Catppuccin colors) with 3dp top border on Timeline clips, undo-supported `setClipLabel()`
- **Settings wired** — `confirmBeforeDelete`, `defaultExportQuality`, `showWaveforms` now gate editor behavior
- **Chapter markers on export** — Timeline markers auto-populate export chapter list

### v3.9.0 — Export Expansion, Settings & UX Polish
- **GIF export UI** — Toggle in ExportSheet with frame rate and max width options
- **Frame capture** — PNG/JPEG single-frame export from playhead position
- **Subtitle export** — SRT/VTT/ASS format picker in ExportSheet
- **Stems & chapter markers toggles** — Wired in ExportSheet UI
- **7 new settings** — showWaveforms, snapToBeat, snapToMarker, defaultTrackHeight, confirmBeforeDelete, thumbnailCacheSizeMb, defaultExportQuality (all DataStore-persisted)
- **Marker list panel** — Searchable, filterable marker list with color chips, inline editing, jump-to-time
- **Track header fields** — showWaveform, trackHeight, isCollapsed added to Track model with ViewModel methods
- **Snap-to-beat/marker** — Beat markers and timeline markers added as snap targets (settings-driven)

### v3.8.0 — Competitor-Inspired Features
- **AI suggestion banner** — Contextual suggestions above timeline (auto-detects: no effects, low audio, no transitions)
- **Drawing overlay** — Annotation layer with 6 Catppuccin colors, brush size, eraser, per-path undo (KineMaster-inspired)
- **Multi-cam panel** — 2x2 grid angle switching with thumbnails and sync button (PowerDirector-inspired)
- **Radial action menu** — Long-press quick-action menu on preview with spring animation (KineMaster Media Wheel)
- **Speed range extended** — 16x → 100x (matching CapCut)
- **Transparent video export** — WebM VP9 alpha channel toggle
- **Filler word auto-strip** — `cleanCaptionText()` removes filler words from captions
- **Manual beat tap** — Tap-to-place markers during playback in BeatSyncPanel
- **Pinch-to-transform** — Pinch/rotate/drag gestures on PreviewPanel
- **Script-to-video** — Text input for smarter clip selection in AutoEditPanel
- **Auto-generated effect UI** — `ParamRange` metadata replaces 170-line hand-coded when block
- **Community templates** — `.novacut-template` export/import via share intent
- **Project backup panel** — CloudBackupPanel rewritten to functional archive export/import
- **F-Droid fastlane metadata** — Structure created for F-Droid distribution

### v3.7.0 — Panel System, PanelVisibility & Architecture Refactor
- **PanelVisibility system** — Replaced legacy `show*` booleans with `PanelId` enum + `PanelVisibility` data class in EditorState
- **BottomSheetSlot pattern** — Unified panel rendering via slot-based AnimatedVisibility
- **Delegate pattern consolidation** — ViewModel delegates (ClipEditing, Effects, Export, AudioMixer, ColorGrading, AiTools, Overlay) fully wired

### v3.6.0 — Architecture Hardening & Concurrency Safety

**Critical Fixes**
- Fixed concurrent file write corruption in ProjectAutoSave (added Mutex coordination between `saveNow()` and auto-save timer)
- Fixed Transformer listener callbacks executing on internal thread instead of Main (StateFlow writes now dispatched to Main looper)
- Added export double-start guard preventing overlapping Transformer sessions
- Added Mutex to ProxyWorkflowEngine preventing duplicate concurrent proxy generation
- Removed reference to non-existent `showVoiceoverRecorder` field in EditorState

**Duration & State Fixes**
- `setClipReversed()` now triggers `recalculateDuration()` (timeline total was stale after reversing)
- `setTransition()` and `setTransitionDuration()` now trigger `recalculateDuration()` (transitions affect clip timing)
- Added `@Volatile` to `saveIndicatorJob`, `voiceoverDurationJob`, `toastJob` for cross-thread visibility
- `onCleared()` now cancels all Job fields (`saveIndicatorJob`, `toastJob`, `aiJob`) preventing coroutine leaks

**Model Validation**
- Added `init { require() }` blocks: Transition (durationMs > 0), Track (pan in -1..1), ImageOverlay (startTime < endTime, scale > 0, opacity 0..1), Caption (endTime >= startTime), Mask (feather >= 0, opacity 0..1), Clip (trimEndMs <= sourceDurationMs)

**Dead Code Removal**
- Deleted 5 unused @Singleton engine classes: CloudInpaintingEngine, PiperTtsEngine, RiveTemplateEngine, SoundpipeDspEngine, SubtitleRenderEngine
- Removed unused LutEngine import from ColorGradingDelegate

**VideoEngine Decomposition**
- Decomposed 500-line `export()` method into 4 focused helpers: `buildEditedMediaItem()`, `buildAudioSequences()`, `buildComposition()`, `startTransformerWithPolling()`
- Extracted `LottieOverlaySpec` data class to its own file
- `export()` is now ~55 lines of orchestration logic

**ViewModel Cleanup**
- Added try-catch around `exportProjectArchive` file I/O
- Fixed `autoDuck()` indentation/try-block alignment

### v3.5.0 — Build Cleanup
- Removed 5 unavailable dependencies (sherpa-onnx, deepfilternet, ncnn, ffmpegx, opentimelineio)
- Cleaned ProGuard keep rules for removed dependencies
- Engine stubs retained for future dependency integration

### v3.4.0 — Dependency Activation (Reverted)
- Dependencies briefly added for engine wiring, then reverted in v3.5.0 due to unavailable artifacts
- Engine stubs for PiperTts, NoiseReduction, FFmpeg, FrameInterpolation, Inpainting, CloudInpainting remain for future integration
- ProGuard keep rules retained for future activation

### v3.3.0 — Localization, Performance & Reliability
- 90+ hardcoded UI strings extracted to `strings.xml` across 15+ panels (i18n ready)
- Batch `getPixels()` replacing per-pixel `getPixel()` in AI analysis (~10x faster)
- Bitmap leak fix in `calculateFrameDifference()` via try-finally
- Exception logging in Whisper, Segmentation, ProjectAutoSave silent catches

### v3.2.0 — Performance & UX Hardening
- Timeline `pointerInput(Unit)` + `rememberUpdatedState` pattern (gesture detectors no longer recreated on scroll/zoom)
- Export state fix (COMPLETE/ERROR transitions), selection state leak fix
- Delete confirmation dialog, buffering indicator, AudioPanel null guard
- VideoEngine deduplication (~200 lines removed), AI error handling (try/catch + toast)

### v3.1.0 — Code Quality & Engine Improvements
- StateFlowExt shared CAS-loop, shadowed lambda fixes, ExportService lifecycle
- CloudInpaintingEngine config persistence, FFmpegEngine concat/atempo, NoiseReductionEngine cascading fallback
- PiperTtsEngine system TTS fallback, waveform LRU cache, haptic feedback, transition icons

### v3.0.0 — Full Engine Expansion
- 23 new engines (29 total injectable singletons): frame interpolation, inpainting, upscaling, video matting, stabilization, style transfer, smart reframe, TTS, beat detection, loudness, chroma key, and more
- 12 new GLSL transitions, social media export presets, magnetic snapping, clip grouping, slip/slide editing
- Film grain, VHS/Retro, Glitch, Light leak, Gaussian blur shaders
- Command-based undo/redo foundation, proxy workflow, OTIO timeline exchange

## Features

### Timeline Editing
- Multi-track timeline with video, audio, overlay, text, and adjustment layers
- Trim, split, merge, crop, rotate with visual handles
- **Slip/slide editing** — drag clip body to slide (reposition) or slip (shift source window)
- **Magnetic snapping** — clips snap to edges, playhead, and markers (8dp threshold with diamond indicators)
- **Clip grouping** — select multiple clips, group/ungroup, move as a unit
- Speed control (0.1x-16x) with bezier speed ramping curves and presets
- Keyframe animation for position, scale, rotation, opacity, volume with **12 easing types** (linear, ease in/out, spring, bounce, elastic, back, circular, expo, sine, cubic)
- **14 speed presets** including time freeze, film reel, heartbeat, crescendo
- Undo/redo (50 levels) with full state restoration + command-based undo foundation
- Long-press multi-select for batch operations
- Pinch-to-zoom + zoom in/out/fit buttons
- Timeline scrubbing with frame-accurate seeking
- **Colored timeline markers** — 6 colors (red/orange/yellow/green/blue/purple) with labels, notes, and jump navigation
- **Sticker/GIF/image overlays** — position, scale, rotate, opacity with timeline placement
- **Favorites & recent effects** — mark effects as favorites, track recently used for quick access
- **Multi-cam sync** — audio-based clip synchronization across tracks
- **Clip reorder & move** — reorder clips within a track or move between tracks
- **Haptic feedback** — tactile response on trim handle grab and magnetic snap
- **Waveform caching** — LRU cache avoids redundant audio decoding on timeline recomposition
- **Clip color labels** — 7 Catppuccin colors (red, peach, green, blue, mauve, yellow, none) with colored top border on Timeline
- **Track collapse/expand** — Per-track chevron + collapse/expand all toggle, collapsed tracks show thin 24dp colored bars
- **Track height cycling** — Long-press track type icon to cycle 48→64→80→96dp
- **Keyboard shortcuts** — Space, Ctrl+Z/Y, arrow keys, M, S, +/-, Delete, Ctrl+S, Ctrl+C/V for external keyboard editing
- **Snap-to-beat/marker** — Beat markers and timeline markers as additional snap targets (settings-driven)
- **Marker list panel** — Searchable, filterable marker list with color chips, inline label editing, jump-to-time

### Effects & Transitions
- **37 GPU-accelerated GLSL transitions** with unique Material icons per type — dissolve, wipe, zoom, spin, flip, cube, ripple, pixelate, morph, glitch, swirl, heart, dreamy, plus 12 new: door open, burn, radial wipe, mosaic reveal, bounce, lens flare, page curl, cross warp, angular, kaleidoscope, squares wire, color phase
- **40+ video effects** — brightness, contrast, saturation, hue, sharpen, vignette, mosaic, fisheye, wave, chromatic aberration, radial blur, motion blur, tilt shift
- **Film grain** — perceptual-aware (more in shadows, less in highlights), animated blue noise pattern
- **VHS/Retro** — scanlines, chroma bleeding, tracking distortion, posterized color depth
- **Glitch** — RGB channel splitting, 8x8 block corruption, horizontal line displacement
- **Light leak** — procedural animated warm gradient with screen blend mode
- **9-tap Gaussian blur** — separable kernel with proper sigma-based weights
- 18 blend modes (normal, multiply, screen, overlay, soft light, hard light, difference, exclusion, etc.)
- Freehand/rectangle/ellipse/gradient masks with feather, expansion, and motion tracking
- **Professional chroma key** — YCbCr color space keying with smoothstep feathering and green/blue spill suppression

### Color Grading
- Lift/gamma/gain color wheels with continuous control
- RGB curves and HSL qualifier
- **LUT import** (.cube/.3dl) with file picker and intensity control
- **Color matching** — per-channel gamma correction between reference and target clips
- **Video scopes** — histogram, waveform, vectorscope with animated overlay (GPU compute shader ready for ES 3.1+)

### Audio
- Full audio mixer with per-track volume faders, **pan slider**, mute/solo, **smoothed VU meters** (ballistic attack/decay)
- 15 DSP effects — parametric EQ, compressor (corrected attack/release), limiter, delay, chorus, de-esser, pitch shift, noise gate
- Waveform visualization with fade envelope overlay
- **Beat detection** — spectral flux onset detection with adaptive thresholding and BPM estimation (aubio NDK ready)
- **Auto-duck** — speech-aware volume keyframing (analyzes voice track, creates keyframes on music track)
- **EBU R128 loudness normalization** — K-weighted measurement with 6 platform presets:
  - YouTube/Spotify (-14 LUFS), TikTok (-14 LUFS), Podcast/Apple (-16 LUFS), Broadcast EBU R128 (-23 LUFS), Cinema (-24 LUFS), Loud (-9 LUFS)
- True-peak limiting to prevent clipping
- Voiceover recording with automatic timeline placement
- **Fade overlap protection** — fade in + fade out constrained to clip duration
- **Noise reduction** — Spectral gate heuristic (5 modes: off/light/moderate/aggressive/spectral gate). DeepFilterNet ML path planned

### AI Tools
| Tool | Engine | On-Device? |
|------|--------|------------|
| **Auto Captions** | ONNX Runtime Whisper (multilingual, 99 languages) | Yes |
| **Background Removal** | MediaPipe Selfie Segmentation (~1-7MB, ~30fps) | Yes |
| **AI Green Screen** | Planned -- RobustVideoMatting (requires model integration) | Planned |
| **Object Removal** | LaMa-Dilated inpainting (40ms/frame @ 512x512 on flagship devices) | Yes |
| **Video Upscaling** | Planned -- Real-ESRGAN (requires model integration) | Planned |
| **Frame Interpolation** | Planned -- RIFE v4.6 (requires NCNN dependency) | Planned |
| **Style Transfer** | Planned -- AnimeGANv2 + Fast NST (requires model integration) | Planned |
| **Stabilization** | Planned -- OpenCV (requires dependency) | Planned |
| **Smart Reframe** | EMA-smoothed crop trajectory, 3 strategies (face/pose detection is stub) | Partial |
| **Tap-to-Segment** | Planned -- MobileSAM (requires dependency) | Planned |
| **Scene Detection** | Content-aware frame difference analysis with auto-split | Yes |
| **Auto Color** | Histogram-based brightness/contrast/saturation/temperature | Yes |
| **Motion Tracking** | Template matching with position keyframe generation | Yes |
| **Audio Denoise** | Spectral gate heuristic (DeepFilterNet ML planned) | Yes |

### Text & Titles
- Rich text overlays with 10+ animation styles
- **Static templates** — lower thirds, title cards, end screens, CTAs
- **Animated Lottie templates** — 10 built-in (slide-in lower third, bounce title, typewriter, glitch reveal, neon glow, fade subtitle, circle logo reveal, countdown, subscribe button). Render frame-by-frame for export via LottieDrawable
- Caption editor with start/end time sliders (mutually constrained)
- Caption style gallery with karaoke, word-pop, bounce, typewriter, minimal styles
- **Continuous caption positioning** via BiasAlignment (not 3-zone snap)
- Text on path (straight, curved, circular, wave)
- Shadow, glow, letter spacing, line height controls

### Text-to-Speech
- **System TTS** — Android built-in voices with mutex-protected synthesis
- **Piper TTS** (planned) — near-human quality VITS voices via Sherpa-ONNX (stub, requires dependency integration)
  - 10 voice profiles defined: Amy (US), Ryan (US), Alba (UK), Thorsten (DE), Dave (ES), Siwis (FR), Takumi (JP), Huayan (CN), Sunhi (KR), Faber (BR)
  - Currently falls back to Android System TTS
- System/Piper engine toggle in TTS panel

### Export
- **GIF export** — Self-contained GIF89a encoder with LZW compression, configurable frame rate (10/15/20fps) and max width (320/480/640px)
- **Frame capture** — PNG/JPEG single-frame export from current playhead position
- 480p to 4K Ultra HD
- **4 codecs** — H.264, H.265 (HEVC), AV1, VP9 with hardware capability detection via `MediaCodecList`
- **One-tap platform presets** — YouTube 1080p, YouTube 4K, TikTok, Instagram Reels, Instagram Square, Threads
- Batch export with multiple presets simultaneously
- Background export with progress notification, ETA display, and cancel
- **Timeline interchange** — OTIO (OpenTimelineIO) JSON export/import + FCPXML export for desktop NLE round-tripping (DaVinci Resolve, Premiere Pro, Final Cut Pro)
- EDL export (CMX 3600) with sanitized reel names and proper timecodes
- Chapter markers and subtitle export (SRT, VTT with word-level cues, ASS/SSA with full styling)
- **Burned-in subtitle rendering** — Canvas-based with ASS/SSA file generation for FFmpeg integration
- Audio-only and stems export modes
- Export error cleanup — partial output files deleted on failure/timeout

### Effect Library
- Copy/paste effects between clips
- Export effects to `.ncfx` file for sharing
- Import effects from `.ncfx` with portable LUT references (filename-based, not absolute paths)

### Project Management
- User template system (save/load/delete project templates, preserves non-media track clips)
- Project snapshots with version history and auto-generated default names
- Project archive (ZIP export)
- **Auto-save** with configurable interval, format versioning, rotating backups
  - Full serialization: all clip fields, compound clips, 9 caption style properties, mask bezier handles, clip group IDs
- **Command-based undo/redo** foundation — sealed class with AddClip, RemoveClip, TrimClip, MoveClip, SetClipSpeed, ApplyEffect, CompoundCommand
- **3-tier proxy workflow** — thumbnail (scrubbing) / proxy (540p editing) / original (export) with auto-switch and storage management
- Cloud backup UI (backend pending)
- **First-run tutorial** — auto-shows on first launch, dismissable, resettable from Settings

### Settings
- Default resolution, frame rate, aspect ratio, export codec
- Auto-save toggle + interval (15-300s)
- Proxy resolution selector
- Reset first-run tutorial
- **Show waveforms** — Global waveform visibility toggle
- **Snap to beat / snap to markers** — Timeline snap behavior toggles
- **Default track height** — 48/64/80/96dp chips
- **Confirm before delete** — Gate clip deletion dialog
- **Thumbnail cache size** — 64/128/256 MB
- **Default export quality** — LOW/MEDIUM/HIGH
- All settings persist via DataStore

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (Catppuccin Mocha theme) |
| Video | Media3 1.9.2 (Transformer + ExoPlayer) |
| Effects | OpenGL ES 3.0 (37 GLSL transitions, 40+ effect shaders) |
| Audio DSP | Custom engine (EQ, compressor, chorus, delay, pitch shift) |
| Speech-to-Text | ONNX Runtime 1.17.0 (Whisper) |
| Noise Reduction | Spectral gate fallback (DeepFilterNet planned) |
| Beat Detection | Spectral flux onset detection (aubio NDK ready) |
| Loudness | EBU R128 / ITU-R BS.1770 measurement |
| Segmentation | MediaPipe Tasks Vision 0.10.14 |
| Video Matting | Planned (RobustVideoMatting, ONNX Runtime) |
| Object Removal | LaMa-Dilated (ONNX Runtime, neighbor-fill fallback) |
| Upscaling | Planned (Real-ESRGAN) |
| Frame Interpolation | Planned (NCNN + Vulkan) |
| Style Transfer | Planned (AnimeGANv2 + Fast NST) |
| Stabilization | Planned (OpenCV) |
| TTS | Android System TTS (Piper via Sherpa-ONNX planned) |
| Animated Titles | Lottie (Airbnb) |
| Timeline Exchange | Planned (OpenTimelineIO) |
| DI | Hilt / Dagger |
| Database | Room (v4 with migration chain 1→4) |
| Settings | DataStore Preferences |
| Architecture | MVVM, single-activity Compose navigation, StateFlow |

## Architecture

```
com.novacut.editor/
├── ai/                     # AI features (captions, scene detect, stabilize, auto-edit)
├── engine/                 # Core engines (29 injectable singletons)
│   ├── VideoEngine          # Media3 playback + export
│   ├── AudioEngine          # Waveform extraction + PCM processing
│   ├── AudioEffectsEngine   # DSP chain (EQ, compressor, chorus, etc.)
│   ├── ShaderEffect         # GLSL fragment shader pipeline
│   ├── KeyframeEngine       # Bezier/hold interpolation
│   ├── ProjectAutoSave      # JSON serialization with format versioning
│   ├── ExportService        # Foreground service for background export
│   ├── BeatDetectionEngine  # Spectral flux onset + BPM estimation
│   ├── LoudnessEngine       # EBU R128 measurement + normalization
│   ├── NoiseReductionEngine # Spectral gate (DeepFilterNet stub)
│   ├── FrameInterpolationEngine  # RIFE v4.6 slow-motion (stub)
│   ├── InpaintingEngine     # LaMa object removal (ONNX Runtime + NNAPI)
│   ├── UpscaleEngine        # Real-ESRGAN video upscaling (stub)
│   ├── VideoMattingEngine   # RVM AI green screen (stub)
│   ├── StabilizationEngine  # OpenCV optical flow (stub)
│   ├── StyleTransferEngine  # AnimeGAN + Fast NST (stub)
│   ├── SmartReframeEngine   # Subject-tracking auto-crop
│   ├── TapSegmentEngine     # MobileSAM tap-to-segment (stub)
│   ├── PiperTtsEngine       # Piper VITS TTS (stub, system TTS fallback)
│   ├── LottieTemplateEngine # Animated title rendering
│   ├── FFmpegEngine         # FFmpegX fallback encoder (stub)
│   ├── SubtitleRenderEngine # Canvas + ASS subtitle rendering
│   ├── CloudInpaintingEngine   # ProPainter cloud API (stub)
│   ├── TimelineExchangeEngine  # OTIO/FCPXML interchange
│   ├── ProxyWorkflowEngine  # 3-tier media management
│   ├── EditCommand          # Command-pattern undo/redo
│   ├── db/ProjectDatabase   # Room database with migrations
│   ├── whisper/WhisperEngine     # Built-in Whisper (ONNX)
│   ├── whisper/SherpaAsrEngine   # Sherpa-ONNX ASR (stub)
│   └── segmentation/        # MediaPipe selfie segmentation
├── model/                  # Data classes (Project, Clip, Track, Effect, etc.)
├── ui/
│   ├── editor/             # Main editor (EditorScreen, EditorViewModel, 40+ panels)
│   ├── export/             # ExportSheet, BatchExportPanel
│   ├── mediapicker/        # MediaPickerSheet
│   ├── projects/           # ProjectListScreen, ProjectTemplateSheet
│   ├── settings/           # SettingsScreen, SettingsViewModel
│   └── theme/              # Catppuccin Mocha theme
├── MainActivity.kt         # Single activity, Compose navigation, permission handling
└── NovaCutApp.kt           # Application class, notification channels
```

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties or env vars)
./gradlew assembleRelease
```

### Requirements
- Android Studio Ladybug+ (2024.2+)
- AGP 8.7.3, Gradle 8.9, JDK 17
- Android SDK 35

### Release Signing
Configure via `keystore.properties`:
```properties
storeFile=path/to/your.jks
storePassword=yourpass
keyAlias=youralias
keyPassword=yourpass
```

Or via environment variables: `NOVACUT_KS_PASS`, `NOVACUT_KEY_ALIAS`, `NOVACUT_KEY_PASS`

### Dependencies
Key external dependencies currently in `build.gradle.kts`:

| Dependency | Version | Purpose |
|-----------|---------|---------|
| ONNX Runtime | 1.17.0 | Whisper ASR + LaMa inpainting |
| MediaPipe | 0.10.14 | Selfie segmentation |
| Lottie | 6.6.2 | Animated title templates |
| OkHttp | 4.12.0 | Cloud inpainting API |

## Supported Devices

- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 35 (Android 15)
- **Required:** OpenGL ES 3.0
- **Recommended:** 4GB+ RAM, Snapdragon 7-series or better for AI features
- **AV1 hardware encoding:** Pixel 8+, Snapdragon 8 Gen 3+, Dimensity 9200+

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_MEDIA_VIDEO/AUDIO/IMAGES` | Access media files (API 33+) |
| `READ_EXTERNAL_STORAGE` | Legacy media access (API < 33) |
| `WRITE_EXTERNAL_STORAGE` | Save exports (API < 29) |
| `RECORD_AUDIO` | Voiceover recording |
| `CAMERA` | Video capture from camera |
| `FOREGROUND_SERVICE` | Background export processing |
| `POST_NOTIFICATIONS` | Export progress notifications |
| `INTERNET` | Model downloads (Whisper), cloud inpainting API |
| `VIBRATE` | Haptic feedback |

## Known Limitations
- Blend modes use mid-gray as virtual blend layer (not true dual-texture compositing — requires Media3 Compositor API)
- `clip.isReversed` works in preview but not in export (Media3 Transformer has no reverse playback support)
- Speed curve clips don't correctly affect timeline duration calculation (`Clip.durationMs` uses constant speed only)
- SmartRenderEngine analysis results not used for actual export bypass
- Text overlay strokeWidth not exported (SpannableString has no native stroke support)
- ProjectArchive.importArchive() is export-only (import not fully implemented)
- 11 AI/ML engine stubs awaiting dependency integration (see ROADMAP.md)

## License

MIT

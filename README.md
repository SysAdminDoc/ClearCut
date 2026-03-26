# NovaCut v3.1.0

A professional Android video editor built with Kotlin and Jetpack Compose. Open alternative to CapCut, PowerDirector, and DaVinci Resolve — with on-device AI, GPU-accelerated effects, and desktop NLE interoperability.

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
- **ML noise reduction** — AndroidDeepFilterNet integration (5 modes: off/light/moderate/aggressive/spectral gate) with noise profile analysis

### AI Tools
| Tool | Engine | On-Device? |
|------|--------|------------|
| **Auto Captions** | Sherpa-ONNX (Whisper Tiny multilingual, 99 languages, 27 tok/s — 51x faster than whisper.cpp) | Yes |
| **Background Removal** | MediaPipe Selfie Segmentation (~1-7MB, ~30fps) | Yes |
| **AI Green Screen** | RobustVideoMatting (true alpha mattes with temporal coherence, 4 background modes) | Yes |
| **Object Removal** | LaMa-Dilated inpainting (40ms/frame @ 512x512 on flagship devices) | Yes |
| **Video Upscaling** | Real-ESRGAN x4plus (72ms/frame, tile-based processing) | Yes |
| **Frame Interpolation** | RIFE v4.6 via NCNN+Vulkan (2x/4x/8x slow-motion, 720p @ 100ms/frame) | Yes |
| **Style Transfer** | AnimeGANv2 (8.6MB) + Fast Neural Style Transfer (6-7MB/style) — 9 presets | Yes |
| **Stabilization** | OpenCV L-K optical flow + RANSAC + Kalman smoothing (configurable crop) | Yes |
| **Smart Reframe** | MediaPipe Face/Pose detection + EMA-smoothed crop trajectory (3 strategies) | Yes |
| **Tap-to-Segment** | MobileSAM (~10MB, point/box prompts, optical flow mask propagation) | Yes |
| **Scene Detection** | Content-aware frame difference analysis with auto-split | Yes |
| **Auto Color** | Histogram-based brightness/contrast/saturation/temperature | Yes |
| **Motion Tracking** | Template matching with position keyframe generation | Yes |
| **Audio Denoise** | DeepFilterNet (PESQ 3.5-4.0+) with spectral gate fallback | Yes |

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
- **Piper TTS** (HD) — near-human quality VITS voices via Sherpa-ONNX, 50+ languages, fully offline
  - 10 voice profiles: Amy (US), Ryan (US), Alba (UK), Thorsten (DE), Dave (ES), Siwis (FR), Takumi (JP), Huayan (CN), Sunhi (KR), Faber (BR)
  - Voice models downloaded on demand (~15-65MB each)
- System/Piper engine toggle in TTS panel

### Export
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
- All settings persist via DataStore

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (Catppuccin Mocha theme) |
| Video | Media3 1.9.2 (Transformer + ExoPlayer) |
| Effects | OpenGL ES 3.0 (37 GLSL transitions, 40+ effect shaders) |
| Audio DSP | Custom engine (EQ, compressor, chorus, delay, pitch shift) |
| Speech-to-Text | Sherpa-ONNX / ONNX Runtime 1.17.0 (Whisper, Moonshine) |
| Noise Reduction | AndroidDeepFilterNet / spectral gating fallback |
| Beat Detection | Spectral flux onset detection (aubio NDK ready) |
| Loudness | EBU R128 / ITU-R BS.1770 measurement |
| Segmentation | MediaPipe Tasks Vision 0.10.14 |
| Video Matting | RobustVideoMatting (ONNX Runtime) |
| Object Removal | LaMa-Dilated (ONNX / Qualcomm AI Hub) |
| Upscaling | Real-ESRGAN x4plus (TFLite / QNN) |
| Frame Interpolation | RIFE v4.6 (NCNN + Vulkan) |
| Style Transfer | AnimeGANv2 + Fast NST (ONNX) |
| Stabilization | OpenCV (L-K + Kalman) |
| TTS | Piper (VITS) via Sherpa-ONNX / Android System TTS |
| Animated Titles | Lottie (Airbnb) |
| Timeline Exchange | OpenTimelineIO (OTIO JSON, FCPXML) |
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
│   ├── NoiseReductionEngine # DeepFilterNet + spectral gate
│   ├── FrameInterpolationEngine  # RIFE slow-motion
│   ├── InpaintingEngine     # LaMa object removal
│   ├── UpscaleEngine        # Real-ESRGAN video upscaling
│   ├── VideoMattingEngine   # RVM AI green screen
│   ├── StabilizationEngine  # OpenCV optical flow
│   ├── StyleTransferEngine  # AnimeGAN + Fast NST
│   ├── SmartReframeEngine   # Subject-tracking auto-crop
│   ├── TapSegmentEngine     # MobileSAM tap-to-segment
│   ├── PiperTtsEngine       # Piper VITS text-to-speech
│   ├── LottieTemplateEngine # Animated title rendering
│   ├── FFmpegEngine         # FFmpegX fallback encoder
│   ├── SubtitleRenderEngine # Canvas + ASS subtitle rendering
│   ├── TimelineExchangeEngine  # OTIO/FCPXML interchange
│   ├── ProxyWorkflowEngine  # 3-tier media management
│   ├── EditCommand          # Command-pattern undo/redo
│   ├── db/ProjectDatabase   # Room database with migrations
│   ├── whisper/WhisperEngine     # Built-in Whisper (ONNX)
│   ├── whisper/SherpaAsrEngine   # Sherpa-ONNX ASR (51x faster)
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

### Activating Optional Dependencies
Tier 2-4 dependencies are commented out in `app/build.gradle.kts`. Uncomment and pin versions as needed:

```kotlin
// Speech-to-text (51x faster Whisper)
implementation("com.k2fsa.sherpa:onnx-android:1.10.37")

// ML noise reduction
implementation("io.github.kaleyravideo:android-deepfilternet:0.5.6")

// Animated titles
implementation("com.airbnb.android:lottie-compose:6.6.2")

// Beat detection (NDK)
implementation("com.github.nicholasryan:aubio-android:0.4.9")

// Stabilization
implementation("org.opencv:opencv-android:4.9.0")

// Smart reframing
implementation("com.google.mediapipe:tasks-vision:0.10.14")

// FFmpeg fallback encoder
implementation("io.github.nicholasryan:ffmpegx-android:6.1.2")
```

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
| `INTERNET` | Model downloads (Whisper, Piper voices) |
| `VIBRATE` | Haptic feedback |

## License

MIT

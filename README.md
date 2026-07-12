<p align="center">
  <img src="icon.png" alt="ClearCut" width="128" />
</p>

<h1 align="center">ClearCut</h1>

[![Version](https://img.shields.io/badge/version-3.74.118-89dceb)](https://github.com/SysAdminDoc/ClearCut/releases)
[![License](https://img.shields.io/badge/license-MIT-a6e3a1)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-cba6f7)

### v3.74.118 Timeline edit controls and playback

- The video preview now owns all flexible phone height; the bounded timeline and tool dock stay together at the bottom instead of leaving a large empty timeline panel.
- Cut works directly at the live playhead, selected clips expose an obvious red Delete button, playlist rebuilds start atomically at the active edit point, ripple deletes keep playback attached to surviving content, and Play restarts after reaching the edited timeline end.
- Autosaves restore silently without interrupting editing with a confirmation popup.
- Editing suggestions now offer “Not now” and remain snoozed for 30 minutes instead of reappearing on each clip selection.
- Manual cuts keep the prepared Media3 timeline intact, stored player listeners attach during lazy player creation, and Play re-seeks/restarts correctly after cuts and timeline end.
- Merging adjacent cuts keeps every later clip at its existing timeline position instead of shifting the rest of the track backward into an overlap.
- Playback intent now remains distinct from decoded-frame progress, so buffering after a cut shows Pause and cannot turn a visible Play tap into an accidental cancellation.
- Ended or stalled preview sessions reset their media period and decoder at the active timeline position, with an automatic one-shot recovery when playback does not begin promptly.
- Saved transitions remain active for export, while the live preview uses stable cuts instead of unsafe single-input transition shaders that could leave Media3 on a black frame after rewind.
- Effect safety now includes true alpha opacity, wired chroma spill, tail-aware nonlinear transition timing, corrected gamma/highlight/shadow/posterize math, and guarded GPU edge cases; unsupported generic Speed/Reverse/BG Removal entries are no longer offered as no-op effects.

<p align="center">A professional Android video editor built with Kotlin and Jetpack Compose.<br>Open alternative to CapCut, PowerDirector, and DaVinci Resolve — with AI-assisted tools, GPU-accelerated effects, and desktop NLE interoperability.</p>

















Release history is maintained in git tags and the development checkout's local
`CHANGELOG.md`.

## Premium mobile workspace

The phone-first editing workspace keeps the footage, frame transport, and
multi-track timeline in one continuous AMOLED editing deck. Saved state stays
with the project identity, track controls remain compact, and a six-category
tool dock keeps advanced actions available without turning the canvas into a
dashboard.

<p align="center">
  <img src="artifacts/clearcut-atelier-editor.png" alt="ClearCut AMOLED Android editing workspace" width="360" />
</p>

## Project planning

Planning files are local-only in the development checkout:

- `ROADMAP.md` is the only source of truth for incomplete actionable work.
- `Roadmap_Blocked.md` records blocked or operator-gated work until it can be
  implemented locally.
- `RESEARCH.md` stores consolidated product, platform, and ecosystem research.
- Shipped work lives in git history and the local `CHANGELOG.md`.

## Features

### Languages
- English and Spanish (`es`) are available through Android 13+ per-app language settings.

### Timeline Editing
- Multi-track timeline with video, audio, overlay, text, and adjustment layers
- Trim, split, merge, crop, rotate with visual handles; numeric trim commits as one undoable edit
- **Reliable split ownership** — linked/grouped cuts preserve side-specific grouping, rebase animation/effect/mask/caption timing, renew nested IDs, and retain waveform/tracking context
- **Gap-safe linked ripple delete** — single and multi-delete share one locked-track-aware planner that expands linked/grouped clips without compacting unrelated tracks or intentional gaps
- **Retimed live preview** — constant-speed and ramped clips seek to the correct source frame, keep the playhead aligned, and refresh speed/volume immediately across cuts
- **WYSIWYG overlays and recovery** — titles, stickers, and images stay visible across timeline gaps; decoder failures offer a direct Media Manager recovery path
- **Slip/slide editing** — drag clip body to slide (reposition) or slip (shift source window)
- **Magnetic snapping** — clips snap to edges, playhead, and markers (8dp threshold with diamond indicators)
- **Clip grouping** — select multiple clips, group/ungroup, move as a unit
- Speed control (0.1x-16x) with bezier speed ramping curves and presets
- Keyframe animation for position, scale, rotation, opacity, volume with **12 easing types** (linear, ease in/out, spring, bounce, elastic, back, circular, expo, sine, cubic)
- **14 speed presets** including time freeze, film reel, heartbeat, crescendo
- Undo/redo (50 levels) restores clip selection/playhead context and persists immediately
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
| **Auto Captions** | ONNX Runtime Whisper tiny.en (English; multilingual Sherpa/Whisper path gated) | Yes |
| **Background Removal** | MediaPipe Selfie Segmentation (~1-7MB, ~30fps) | Yes |
| **AI Green Screen** | Planned -- RobustVideoMatting (requires model integration) | Planned |
| **Object Removal** | LaMa-Dilated inpainting; per-frame + full-video pipeline via FFmpeg encode | Yes |
| **Video Upscaling** | Planned -- Real-ESRGAN (requires model integration) | Planned |
| **Frame Interpolation** | Planned -- RIFE v4.6 (requires NCNN dependency) | Planned |
| **Style Transfer** | Planned -- AnimeGANv2 + Fast NST (requires model integration) | Planned |
| **Stabilization** | Planned -- OpenCV (requires dependency) | Planned |
| **Smart Reframe** | MediaPipe BlazeFace detection, EMA-smoothed crop trajectory, 3 strategies (stationary/pan/track) | Yes |
| **Tap-to-Segment** | Planned -- SAM 2.1 Hiera Tiny target with MobileSAM fallback | Planned |
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
- **Platform handoff** — open completed exports in platform apps with suggested post text and manual AI-disclosure reminders
- 480p to 4K Ultra HD
- **4 codecs** — H.264, H.265 (HEVC), AV1, VP9 with hardware capability detection via `MediaCodecList`
- **HDR export confidence** — HEVC, AV1, and VP9 preflight reports HDR10+, Dolby Vision Profile 10, Ultra HDR source gain maps, and device-tier hardware encode support before render
- **One-tap platform presets** — YouTube 1080p, YouTube 4K, TikTok, Instagram Reels, Instagram Square, Threads
- Multi-sequence Media3 Composition export for visible video and overlay tracks, with dedicated audio-track mixdown
- Batch export with multiple presets simultaneously
- Background export with progress notification, ETA display, and cancel
- **Timeline interchange** — OTIO (OpenTimelineIO), FCPXML, and EDL export for desktop NLE handoff; incoming OTIO/FCPXML/EDL files show a guarded import preview until parsers are active
- EDL export (CMX 3600) with sanitized reel names and proper timecodes
- Chapter markers and subtitle export (SRT, VTT with word-level cues, ASS/SSA with full styling)
- **Burned-in subtitle rendering** — Canvas-based with ASS/SSA file generation for FFmpeg integration
- Audio-only and stems export modes
- Export error cleanup — partial output files deleted on failure/timeout

### Effect Library
- Copy/paste effects between clips
- Export effects to `.ncfx` file for sharing
- Import effects from `.ncfx` with portable LUT references (filename-based, not absolute paths)
- Import `.ncstyle` caption/text style packs — validates schema, installs to local registry, merges into style gallery

### Project Management
- User template system (save/load/delete project templates, preserves non-media track clips)
- Project snapshots with version history and auto-generated default names
- Project archive (ZIP export/import through Archive Transfer)
- **Auto-save** with configurable interval, format versioning, rotating backups
  - Full serialization: all clip fields, compound clips, 9 caption style properties, mask bezier handles, clip group IDs
- **Command-based undo/redo** foundation — sealed class with AddClip, RemoveClip, TrimClip, MoveClip, SetClipSpeed, ApplyEffect, CompoundCommand
- **3-tier proxy workflow** — thumbnail (scrubbing) / proxy (540p editing) / original (export) with auto-switch and storage management
- Archive Transfer for local project rollback and device moves; remote sync remains planned behind explicit backend gates
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
| Video | Media3 1.10.1 (Transformer + ExoPlayer) |
| Effects | OpenGL ES 3.0 (37 GLSL transitions, 40+ effect shaders) |
| Audio DSP | Custom engine (EQ, compressor, chorus, delay, pitch shift) |
| Speech-to-Text | ONNX Runtime 1.26.0 (Whisper) |
| Noise Reduction | Spectral gate fallback (DeepFilterNet planned) |
| Beat Detection | Spectral flux onset detection (aubio NDK ready) |
| Loudness | EBU R128 / ITU-R BS.1770 measurement |
| Segmentation | MediaPipe Tasks Vision 0.10.35 |
| Video Matting | Planned (RobustVideoMatting, ONNX Runtime) |
| Object Removal | LaMa-Dilated (ONNX Runtime, neighbor-fill fallback) |
| Upscaling | Planned (Real-ESRGAN) |
| Frame Interpolation | Planned (NCNN + Vulkan) |
| Style Transfer | Planned (AnimeGANv2 + Fast NST) |
| Stabilization | Planned (OpenCV) |
| TTS | Android System TTS (Piper via Sherpa-ONNX planned) |
| ASR acceleration target | Sherpa-ONNX v1.13.2 AAR + Moonshine v2 Tiny EN policy (native backend still gated) |
| Animated Titles | Lottie Compose 6.7.1 and Media3 Lottie overlay support |
| Startup performance | AndroidX Baseline Profile / Macrobenchmark 1.4.1 |
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
│   ├── TapSegmentEngine     # SAM 2.1 / MobileSAM target metadata (stub)
│   ├── PiperTtsEngine       # Piper VITS TTS (stub, system TTS fallback)
│   ├── LottieTemplateEngine # Animated title rendering
│   ├── FFmpegEngine         # FFmpegX fallback encoder (stub)
│   ├── SubtitleRenderEngine # Canvas + ASS subtitle rendering
│   ├── GenerativeVideoPolicy # Cloud-only trust gates for large video generators
│   ├── TimelineExchangeEngine  # OTIO/FCPXML interchange
│   ├── ProxyWorkflowEngine  # 3-tier media management
│   ├── EditCommand          # Command-pattern undo/redo
│   ├── db/ProjectDatabase   # Room database with migrations
│   ├── whisper/WhisperEngine     # Built-in Whisper (ONNX)
│   ├── whisper/SherpaAsrEngine   # Sherpa-ONNX ASR target metadata + fallback
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
└── ClearCutApp.kt           # Application class, notification channels
```

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties or env vars)
./gradlew assembleRelease

# Managed-device startup/editor performance gate
./gradlew :baselineprofile:pixel6Api36BenchmarkReleaseAndroidTest :baselineprofile:collectNonMinifiedReleaseBaselineProfile
```

### Manual QA: audio focus

Before release, verify audio focus on a physical device:

- Start music in another app, open ClearCut, and play timeline preview. The
  external app should pause or duck while ClearCut plays.
- Connect headphones, start preview playback, then unplug them. ClearCut preview
  should pause instead of continuing through the speaker.
- Start timeline preview, then start a voiceover recording. Preview should pause
  before recording starts and focus should release when recording stops.
- Start a TTS preview, then leave the panel or close the editor. Preview speech
  should stop and other audio should be able to resume.

### Manual QA: Android 17 audio hardening

On an Android 17 Beta 3+ device or emulator, enable loud audio-hardening failures before checking editor audio paths:

```bash
adb shell cmd audio set-enable-hardening throw
adb logcat | grep AudioHardening
```

With the editor activity visible, run TTS preview, start and stop a voiceover recording, then start an export. TTS preview and voiceover should continue to require visible editor interaction, and export should stay in the `mediaProcessing` foreground service without starting TextToSpeech, MediaRecorder, audio playback, or audio-focus APIs from `ExportService`.

### Requirements
- Android Studio Ladybug+ (2024.2+)
- AGP 8.7.3, Gradle 8.9, JDK 21
- Android SDK 36

### Release Signing
Configure via `keystore.properties`:
```properties
storeFile=path/to/your.jks
storePassword=yourpass
keyAlias=youralias
keyPassword=yourpass
```

Or via environment variables: `CLEARCUT_STORE_FILE`, `CLEARCUT_STORE_PASSWORD`, `CLEARCUT_KEY_ALIAS`, `CLEARCUT_KEY_PASSWORD`

If release credentials are not configured, `assembleRelease` falls back to debug signing so local verification can still produce a testable release artifact without relying on an embedded keystore.

### Release Verification
Local release builds publish a `.sha256` checksum and `.signing-cert-sha256` certificate-fingerprint sidecar next to every APK. After building `debug`, `release`, and `androidTest` APKs, write or refresh the sidecars, then run the single local release gate:

```powershell
python scripts\write_release_checksums.py --root app\build\outputs\apk
python scripts\write_apk_signing_fingerprints.py --root app\build\outputs\apk
python scripts\verify_release_artifacts.py
```

`verify_release_artifacts.py` checks Gradle/APK version metadata, checksum sidecars, APK signing fingerprints, 16 KB native-library alignment, APK size budget, and Play listing metadata. The fingerprint sidecar contains the APK signing-certificate SHA-256 digest reported by Android build-tools `apksigner`.

### APK Size Budget
Local release verification checks debug, release, and androidTest APK sizes against `scripts/apk_size_baseline.json` with a 2 MB per-output growth allowance. After an intentional dependency or asset-size change, refresh the baseline from a verified build:

```powershell
python scripts\check_apk_size.py --update-baseline
python scripts\check_apk_size.py
```

### Distribution Readiness
GitHub Releases are the direct APK distribution channel for this checkout. Google Play listing metadata, privacy disclosures, Data safety worksheet, and screenshot assets are committed under `fastlane/metadata/android/en-US/` and validated by the local release gate.

F-Droid-compatible Fastlane metadata is present in the same source tree. F-Droid publication still needs a final reproducible-build metadata pass, including `AllowedAPKSigningKeys` after the release signing key policy is fixed.

Android developer verification is not complete. Starting in September 2026, Google requires apps installed on certified Android devices in initial regions to be registered by a verified developer, and package names must be registered with a signed APK. ClearCut can keep shipping direct APKs locally, but broad sideload/F-Droid continuity depends on completing that account/package-name step or documenting a limited-distribution fallback.

### Dependencies
Key external dependencies currently in `build.gradle.kts`:

| Dependency | Version | Purpose |
|-----------|---------|---------|
| ONNX Runtime | 1.26.0 | Whisper ASR + LaMa inpainting |
| Sherpa-ONNX | 1.13.2 target | Future native Moonshine v2 ASR path; official AAR is a GitHub release asset, not a Maven dependency |
| SAM 2.1 ONNX | Targeted | Future tracked-mask path via explicit model download; MobileSAM remains the small-device fallback |
| MediaPipe | 0.10.35 | Selfie segmentation |
| Lottie Compose | 6.7.1 | Animated title templates |
| AndroidX Benchmark/ProfileInstaller | 1.4.1 / 1.4.1 | Baseline Profile generation and release profile install |
| OkHttp | 5.3.2 | Model downloads and future opt-in provider calls |
| FFmpegKit 16 KB | 6.1.1 | FFmpeg-backed fallback export paths not covered by Media3 Transformer |
| Android DeepFilterNet | 0.0.8 | On-device voiceover noise reduction |

### Distribution and Third-party Notices

Open-source notices are available in **Settings > Third-party notices > Open source licenses**. Redistributed builds that include `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` must keep the packaged FFmpegKit / FFmpeg license resources and source-offer text. The fork source is published at https://github.com/moizhassankh/ffmpeg-kit-android-16KB, and the bundled source-offer resource points to FFmpegKit source access at https://github.com/arthenica/ffmpeg-kit/wiki/Source.

## Supported Devices

- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 36 (Android 16)
- **Required:** OpenGL ES 3.0
- **Recommended:** 4GB+ RAM, Snapdragon 7-series or better for AI features
- **AV1 hardware encoding:** Pixel 8+, Snapdragon 8 Gen 3+, Dimensity 9200+

## Permissions

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Voiceover recording |
| `FOREGROUND_SERVICE` | Background export processing |
| `FOREGROUND_SERVICE_MEDIA_PROCESSING` | Android 14+ foreground export classification |
| `POST_NOTIFICATIONS` | Export progress notifications |
| `INTERNET` | Model downloads and future opt-in provider APIs |
| `ACCESS_NETWORK_STATE` | Respect Wi-Fi-only model download settings |
| `VIBRATE` | Haptic feedback |

Media access uses the system Photo Picker (`ActivityResultContracts.PickVisualMedia`) and `ACTION_OPEN_DOCUMENT` exclusively — ClearCut requests **no** broad `READ_MEDIA_VIDEO` / `READ_MEDIA_IMAGES` / `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` permissions, so the per-URI grant model survives background kill without the Android 14 Selected Photos compatibility-mode loss.

## Known Limitations
- Multi-sequence export now honors track opacity through Media3 compositor settings, and all 18 fallback blend modes render distinctly; true source-over-destination blend math still needs a custom programmable compositor because Media3's public settings only expose alpha/transform
- Reversed clip export pre-renders through FFmpeg (clips over 5 minutes export forward; FFmpeg unavailable falls back to forward playback)
- Android Lint runs locally with current Kotlin/AGP detector-crash workarounds; warning debt remains before it should become release-blocking
- 11 AI/ML engine stubs awaiting dependency integration (see ROADMAP.md)

## License

MIT

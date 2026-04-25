# NovaCut — Implementation Roadmap

Forward-looking tracker for planned work. Release history lives in [CHANGELOG.md](CHANGELOG.md).

Current version: **v3.71.0** (versionCode 132).

### v3.69.0 — 15-Feature Wave (shipped)

Twelve new engines + `V369Delegate` + `V369FeaturesPanel` composite hub:
Text-based editing · Auto-chapters · Talking-head framing · Karaoke captions · Stream-copy export detector · Content-ID pre-check · Direct publish · Flash safety (WCAG) · Color-blind preview · AI thumbnail picker · SDH / audio-description · S Pen + MIDI jog/shuttle · HDR10+ metadata flag on `ExportConfig`.

See [CHANGELOG.md](CHANGELOG.md) for the full feature log. The v3.69 wave does not replace anything in Tier A/B/C below — those remain the path for external-dependency work (Sherpa-ONNX, DeepFilterNet, RIFE, Real-ESRGAN, OpenCV stab, etc.).

Legend: `[ ]` not started · `[~]` in progress · `[x]` done (moved to CHANGELOG).

---

## Tier A — Activate Scaffolded Stubs
Engines are already implemented with fallback paths and UI wiring. Each needs only dependency + model asset.

| # | Item | Stub file | Dependency | Model size | Fallback today |
|---|------|-----------|------------|------------|----------------|
| A.1 | **Sherpa-ONNX ASR** — 51× faster than current Whisper path; word-level timestamps; 99 languages | [engine/whisper/SherpaAsrEngine.kt](app/src/main/java/com/novacut/editor/engine/whisper/) | `com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.x` | ~100 MB (Whisper Tiny) / ~125 MB (Moonshine EN) | Built-in Whisper ONNX |
| A.2 | **DeepFilterNet noise reduction** — ML path for aggressive mode, replaces spectral-gate heuristic | [engine/NoiseReductionEngine.kt](app/src/main/java/com/novacut/editor/engine/NoiseReductionEngine.kt) | `com.github.KaleyraVideo:AndroidDeepFilterNet` (jitpack) | ~8 MB | Spectral gate heuristic |
| A.3 | **OpenCV stabilization** — L-K sparse optical flow + Kalman smoothing, configurable crop | [engine/StabilizationEngine.kt](app/src/main/java/com/novacut/editor/engine/StabilizationEngine.kt) | `org.opencv:opencv:4.10.0` (arm64 only, ~40 MB) | Frame-diff only, no motion compensation |
| A.4 | **RIFE v4.6 frame interpolation** — 24→60/120 fps slow-mo, NCNN+Vulkan | [engine/FrameInterpolationEngine.kt](app/src/main/java/com/novacut/editor/engine/FrameInterpolationEngine.kt) | NCNN prebuilt + RIFE v4.6 model | ~7–10 MB | Frame duplication |
| A.5 | **Real-ESRGAN upscaling** — x4plus + general-x4v3 variants, tile-based | [engine/UpscaleEngine.kt](app/src/main/java/com/novacut/editor/engine/UpscaleEngine.kt) | ONNX Runtime (already active) + model download | ~17 MB | Bicubic scale |
| A.6 | **RobustVideoMatting** — true alpha matte, temporal coherence, hair detail | [engine/VideoMattingEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoMattingEngine.kt) | ONNX Runtime (already active) + RVM model | ~15 MB | MediaPipe binary mask |
| A.7 | **MobileSAM tap-to-segment** — point/box prompts, optical-flow mask propagation | [engine/TapSegmentEngine.kt](app/src/main/java/com/novacut/editor/engine/TapSegmentEngine.kt) | ONNX Runtime (already active) + MobileSAM model | ~10 MB | Stub toast |
| A.8 | **Piper TTS via Sherpa-ONNX** — 10 voices / 8 languages, VITS quality | [engine/TtsEngine.kt](app/src/main/java/com/novacut/editor/engine/TtsEngine.kt) | Same dep as A.1 | 15–65 MB / voice | Android system TTS |
| A.9 | **FFmpegX-Android** — fallback encoder; unlocks reverse playback, 300+ filters, subtitle burn-in, EBU R128 two-pass | [engine/FFmpegEngine.kt](app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt) | `com.github.mzgs:FFmpegX-Android` (jitpack) | ~40 MB native | Media3 Transformer only |
| A.10 | **Oboe resampler** — replaces Android `AudioFormat` resample for 44.1↔48 kHz mixing | (new) | `com.google.oboe:oboe:1.9.x` | — | Media3 resample |
| A.11 | **Style transfer (AnimeGANv2 + Fast NST)** — 9 presets, OpenCV pencil-sketch fallback | [engine/StyleTransferEngine.kt](app/src/main/java/com/novacut/editor/engine/StyleTransferEngine.kt) | ONNX Runtime (already active) | 6–9 MB / style | Stub toast |
| A.12 | **ProPainter cloud inpainting** — long-span object removal beyond LaMa's per-frame capability | [engine/InpaintingEngine.kt](app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt) *(cloud path)* | OkHttp (already active), self-host server | — | LaMa per-frame |
| A.13 | **Rive interactive templates** — 5 templates, state machine inputs | [engine/LottieTemplateEngine.kt](app/src/main/java/com/novacut/editor/engine/LottieTemplateEngine.kt) *(parallel engine)* | `app.rive:rive-android:9.x` | — | Lottie only |

### Tier A work pattern
Every stub already wires through ViewModel + UI panel; switching on a dep is ~1 release of work per item (add Maven coord, uncomment init path, ship model download manager, verify graceful fallback when model absent).

---

## Tier B — Fix Known Limitations
Gaps listed in README "Known Limitations" that hurt quality today.

- [ ] **B.1 — Media3 Compositor multi-track export** — Today only the first video track renders to file; PiP, split-screen, podcast layouts silently drop tracks 2+ on export. Required for true multi-track and true dual-texture blend modes. Touch: [engine/VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt), [engine/EffectBuilder.kt](app/src/main/java/com/novacut/editor/engine/EffectBuilder.kt).
- [ ] **B.2 — True dual-texture blend modes** — 18 blend modes currently composite against mid-gray because there is no second texture to blend against. Blocked on B.1 (needs Compositor API).
- [ ] **B.3 — Reverse playback in export** — `clip.isReversed` works in preview only; Media3 Transformer has no reverse playback. Unblocks once A.9 (FFmpegX) lands — filter complex `[0:v]reverse[v]`.
- [ ] **B.4 — Speed-curve-aware `Clip.durationMs`** — Partially addressed in v3.50 (`timelineOffsetToSourceMs` inverse). Still need: forward integration of `speedCurve(t)` into duration so a 2×→0.5× ramp reports the correct timeline length. Touch: [model/Clip.kt](app/src/main/java/com/novacut/editor/model/), [engine/TimelineEditing.kt](app/src/main/java/com/novacut/editor/engine/).
- [ ] **B.5 — `SmartRenderEngine` bypass activation** — Analysis already runs (identifies pass-through-eligible clips) but results are unused. Wire the bypass decision into `VideoEngine.buildEditedMediaItem` to skip transcode for unchanged segments. Touch: [engine/SmartRenderEngine.kt](app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt), [engine/VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt).
- [ ] **B.6 — Text overlay stroke export** — `SpannableString` has no native stroke; preview shows stroke but export does not. Fix: render stroke via Canvas in `ExportTextOverlay` (already composites text to bitmap) rather than delegating to Spannable. Touch: [engine/ExportTextOverlay.kt](app/src/main/java/com/novacut/editor/engine/ExportTextOverlay.kt).
- [x] **B.7 — `ProjectArchive.importArchive()` completion** — Done in v3.70.0. `importArchiveWithReport()` returns schema version, schema-too-new gate, project-ID collision detection (`IdCollisionPolicy.REGENERATE` default), and per-archive media-resolution diagnostics (resolved vs unresolved URIs). `EditorViewModel` surfaces missing-media counts in the import toast.

---

## Tier C — New Features (2026 Competitive Gaps)
Not covered in [RESEARCH.md](RESEARCH.md). Ranked by viral-content / pro-differentiator ROI.

### Audio & speech
- [ ] **C.1 — Audio stem separation** — Demucs v4 or Spleeter via ONNX. Pull vocals/drums/bass/other from any music track. New engine `StemSeparationEngine.kt`, new panel under Audio tools. Model ~80 MB (Demucs htdemucs). High viral ROI (isolate vocals for reaction cuts / acapellas).
- [ ] **C.2 — Silence & filler auto-cut** — Whisper already produces word timestamps; add a pass that proposes cut ranges for silences > configurable threshold and for filler words (um/uh/like/you know). v3.8 `cleanCaptionText()` only scrubs caption display — extend to cut clips. Touch: [ai/AiFeatures.kt](app/src/main/java/com/novacut/editor/ai/AiFeatures.kt), new `AutoCutSilencePanel.kt`.
- [ ] **C.3 — Voice cloning** — Sherpa-ONNX now ships XTTS v2 bindings (6-second enrollment sample, 16 languages). Pairs with A.8. New `VoiceCloneEngine.kt`, voiceover panel gains "Record 6s sample → clone" flow.
- [ ] **C.4 — AI lip-sync (Wav2Lip / SadTalker)** — ONNX models, lip-sync translated/dubbed voiceover to original speaker's face. Useful for C.5 translation workflow. Model ~300 MB (Wav2Lip GAN).
- [ ] **C.5 — Auto-translate captions** — NLLB-200 (200 langs) or MADLAD-400 via ONNX; translate Whisper output. Direct pair with existing caption pipeline. Model ~600 MB distilled / ~150 MB quantised. Touch: [ai/AiFeatures.kt](app/src/main/java/com/novacut/editor/ai/AiFeatures.kt), Caption panel gains language chip.
- [ ] **C.6 — Audio mastering presets** — After A.2 + existing EBU R128: one-tap "Podcast voice / Music master / Dialogue clean / ASMR" chains (EQ + comp + limiter + denoise pre-configured). Touch: [engine/AudioEffectsEngine.kt](app/src/main/java/com/novacut/editor/engine/AudioEffectsEngine.kt).

### Media & assets
- [ ] **C.7 — Stock asset library** — Pexels + Pixabay (video + photo) + Freesound (SFX) + Free Music Archive (music) API tabs in MediaPicker. Each needs license compliance (attribution strings cached per asset). Touch: [ui/mediapicker/MediaPickerSheet.kt](app/src/main/java/com/novacut/editor/ui/mediapicker/).
- [ ] **C.8 — In-app camera with teleprompter** — `CameraX` capture, drop directly into timeline, optional scrolling teleprompter overlay while recording voiceover. Closes "record → edit → export" loop without leaving the app. New `ui/capture/` package.
- [ ] **C.9 — HDR10 / Dolby Vision export** — Android 14+ supports HDR encode via `MediaCodec` profile flags. Walk `EncoderCapabilityProbe` (v3.55) for HDR profile support; surface in ExportSheet when source is HDR. Genuine differentiator — no FOSS mobile NLE ships this.
- [ ] **C.10 — 360 / VR equirectangular editing** — Spherical navigation preview (yaw/pitch/roll gestures), equirectangular-aware crop and transitions. Target Insta360 / GoPro Max footage. New `effect/EquirectangularEffect.kt`, pose metadata passthrough via spatial media (XMP GPano).

### Timeline & composition
- [ ] **C.11 — Adjustment layers** — First-class layer that applies effects to every clip beneath it across its time range. New `AdjustmentLayer` model, EffectBuilder applies via an overlay GL pass. Pro-NLE staple. Touch: [model/Track.kt](app/src/main/java/com/novacut/editor/model/), [engine/EffectBuilder.kt](app/src/main/java/com/novacut/editor/engine/EffectBuilder.kt).
- [ ] **C.12 — Keyframe graph editor** — Bezier curve editor UI for animated values. `KeyframeEngine` already supports 12 easings; missing the visual editor (two-handle bezier per segment, value scrubber, tangent lock). Touch: new `ui/editor/KeyframeGraphPanel.kt`.
- [ ] **C.13 — Nested sequences / compound clip UI** — `createCompoundClip()` exists in the model (v3.7 fix set `sourceDurationMs = compoundDurationMs`). Missing: tap compound to open sub-timeline, edit children, exit back. Touch: [ui/editor/EditorScreen.kt](app/src/main/java/com/novacut/editor/ui/editor/), new `CompoundClipEditor.kt`.

### Interop & distribution
- [ ] **C.14 — NLE round-trip import** — Export to OTIO/FCPXML/EDL exists; build the inverse. Parse FCPXML → map to NovaCut tracks/clips/transitions, best-effort handling of Resolve-specific metadata. Touch: [engine/TimelineExchangeEngine.kt](app/src/main/java/com/novacut/editor/engine/TimelineExchangeEngine.kt).
- [ ] **C.15 — Template marketplace hub** — `.novacut-template` format exists (v3.8 export/import + share intent). Missing: discovery UI, self-hostable registry (e.g. GitHub Releases as backing store), rating/search. Touch: new `ui/templates/MarketplaceScreen.kt`.
- [ ] **C.16 — Cross-device project sync** — Syncthing-style (filesystem only) or Git-style (project history with diffs) for project JSON + media refs. Project archive (ZIP) already exists — extend with rsync-like delta and conflict resolution UI.

---

## Delivery Notes

### Sequencing guidance
1. **Do A before B/C when possible** — many Tier B/C items depend on Tier A engines. B.3 unblocks once A.9 ships; C.3 pairs with A.8; C.5 pairs with existing Whisper; C.6 composes A.2 + EBU R128.
2. **One Tier A + one Tier C per release cycle** keeps scope disciplined — avoid landing multiple new model downloads per release (app-install-size creep).
3. **Gate all model downloads behind explicit user action** — Android Play policies around on-device model auto-download remain touchy. Current pattern (user taps feature → "download 15 MB model?" prompt) is correct; keep it.

### Dependency risk notes
- **A.3 OpenCV** — arm64 only; either ship arm64-only APK split or accept ABI filter. Never ship universal OpenCV (APK bloats past Play Store 150 MB limit).
- **A.9 FFmpegX-Android** — replaces archived ffmpeg-kit. Mehmet Genç (mzgs) maintains; verify active maintenance before pinning.
- **A.6/C.5 large models** — RVM and NLLB both push past typical mobile expectations. Consider quantised variants first (MADLAD-400 3B Q4 is ~1.5 GB; distilled NLLB is ~600 MB; smaller variants trade quality for install size).
- **C.4 Wav2Lip** — some model weights have non-commercial licenses; audit before shipping.

### Architecture touch points common across multiple items
- Model download manager — today each engine open-codes its download. Consolidate to a `ModelDownloadManager` singleton with shared progress UI / retry / integrity check before shipping more on-device ML.
- Stub UX pattern — engines not yet integrated return a "This feature requires a model download" toast. Standardise to a proper dismissible sheet with download-size disclosure and Wi-Fi-only toggle.

---

## Backlog / nice-to-have (unranked)
- Screen recording integration (MediaProjection → timeline)
- Shape layers / SVG import / particle system
- Puppet pin / mesh warp masks (current masks are bezier only)
- Remote PC render trigger over LAN
- Audio: sidechain, multiband comp
- RTL / bidirectional text in overlays
- Accessibility: audio description track
- Brand kit (logo, fonts, color palette, auto-apply to templates)
- YouTube chapters export with thumbnail per chapter
- AI thumbnail generator (pick best frames + composite text)
- 3D LUT capture (generate `.cube` from reference image pair)
- Auto-beat-sync edit (existing beat detection → auto place cuts)
- Settings: Wi-Fi-only model downloads toggle

## Open-Source Research (Round 2)

### Related OSS Projects
- **Open Video Editor (devhyper)** — https://github.com/devhyper/open-video-editor — Kotlin/Compose/Media3, HDR, trim, scale, rotate, grayscale, audio extract, HDR-to-SDR conversion
- **DoubleClips** — https://github.com/DoubleClips/DoubleClips-mobile — multitrack, templates, cross-platform rendering; CapCut-style
- **OpenShot** — https://github.com/OpenShot/openshot-qt — mature NLE; model for effect graph, keyframe curves, export presets
- **Shotcut** — https://github.com/mltframework/shotcut — MLT-framework NLE; video filters/LUTs and proxy workflow
- **Kdenlive** — https://github.com/KDE/kdenlive — MLT again; mature keyframing, motion tracking, subtitle tooling
- **LosslessCut** — https://github.com/mifi/lossless-cut — stream-copy fast trim/split without re-encoding; "quick cut" mode inspiration
- **Media3 Transformer samples** — https://github.com/androidx/media — reference implementations for effect chains, GPU shader effects, HDR
- **Olive Editor** — https://github.com/olive-editor/olive — node-graph NLE, inspiration for advanced graph mode
- **Whisper.cpp** — https://github.com/ggerganov/whisper.cpp — on-device speech recognition for auto-subtitles

### Features to Borrow
- Stream-copy "quick cut" mode for single-track trim without re-encoding — massive speed win (LosslessCut)
- Effect chain as a Media3 Effect graph with GPU shader support (Media3 Transformer samples)
- HDR-to-SDR tone-mapping as a user-facing toggle with BT.2390 + Hable options (Open Video Editor)
- Proxy workflow: auto-generate 480p proxies for 4K/8K clips on import, swap back at export (Shotcut, Kdenlive)
- Keyframe curve editor with ease-in/ease-out presets and Bezier control (Kdenlive, OpenShot)
- Motion tracking (point-track) for text/stickers attached to subjects (Kdenlive)
- Node-graph mode for advanced users that exposes the effect chain visually (Olive)
- "Nudge" frame-accurate playhead with left/right arrows; Shift+arrow = 10-frame (Kdenlive defaults)
- Import/export EDL/XML/FCPXML for round-trip to Resolve/Premiere (Kdenlive, OpenShot)
- Auto-subtitle pass via on-device Whisper.cpp with per-word timestamps
- Template library like CapCut — JSON-serialized recipes, per-clip placeholders (DoubleClips)

### Patterns & Architectures Worth Studying
- MLT-style filter pipeline: every effect is a "producer/filter/consumer" stage, trivially re-orderable (Kdenlive, Shotcut)
- Proxy-clip abstraction: UI holds one reference, renderer transparently picks proxy or original based on preview vs export (Kdenlive)
- EDL as source-of-truth: the timeline serializes to EDL/FCPXML, UI is a view onto that file (Kdenlive)
- Media3 Effect pipeline with OpenGL shaders and GlTextureFrameProcessor (Transformer samples) — matches NovaCut's 45-engine architecture
- Stream-copy container mux with a keyframe-aware cut planner (LosslessCut)

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **androidx/media/libraries/effect/src/main/java/androidx/media3/effect/GlEffect.java** — https://github.com/androidx/media/blob/release/libraries/effect/src/main/java/androidx/media3/effect/GlEffect.java — canonical `GlEffect.toGlShaderProgram(context, useHdr)` contract; `useHdr=true` means BT.2020 linear RGB, else BT.709. Match this in every custom NovaCut effect to avoid HDR gamut mismatch.
- **androidx/media/demos/transformer/src/main/java/androidx/media3/demo/transformer/TransformerActivity.java** — https://github.com/androidx/media/blob/release/demos/transformer/src/main/java/androidx/media3/demo/transformer/TransformerActivity.java — working GlEffect lambda factory (MediaPipe edge detector) passed as Effect to an `EditedMediaItem.Builder`. Shape of the videoEffects chain mirrors NovaCut's.
- **androidx/media/libraries/transformer/src/main/java/androidx/media3/transformer/ExperimentalFrameExtractor.java** — https://github.com/androidx/media/blob/1.6.0/libraries/transformer/src/main/java/androidx/media3/transformer/ExperimentalFrameExtractor.java — non-decoder frame extractor using `PassthroughShaderProgram` + `MatrixTransformation`. Template for a scope/thumbnail path that does not spin up ExoPlayer.
- **k2-fsa/sherpa-onnx/android/SherpaOnnx2Pass** — https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnx2Pass — Kotlin JNI wiring for 2-pass ASR (small streaming + Whisper-tiny). Real swap-in for `SherpaAsrEngine.kt` stub; copy `OnlineRecognizer.kt`/`OfflineRecognizer.kt` + the `jniLibs` ABI-filter block from build.gradle.
- **k2-fsa/sherpa-onnx/c-api-examples/whisper-c-api.c** — https://github.com/k2-fsa/sherpa-onnx/blob/master/c-api-examples/whisper-c-api.c — canonical config: encoder + decoder ONNX + `tokens.txt`. Matches the Whisper release-asset triplet (`sherpa-onnx-whisper-tiny.tar.bz2`).
- **gl-transitions/gl-transitions** — https://github.com/gl-transitions/gl-transitions — 80+ GLSL transition shaders. Spec requires `getFromColor(uv)` / `getToColor(uv)`; wrap in a shim so upstream shaders compile unchanged against NovaCut's 2-texture sampler.
- **transitive-bullshit/ffmpeg-gl-transition/vf_gltransition.c** — https://github.com/transitive-bullshit/ffmpeg-gl-transition/blob/master/vf_gltransition.c — reference port feeding two frames to one GLSL program. Exit path from the mid-gray blend-mode workaround.
- **KaleyraVideo/AndroidDeepFilterNet** — https://github.com/KaleyraVideo/AndroidDeepFilterNet — Maven artifact + JNI wrapper. Minimum wiring: `implementation("com.kaleyra:deepfilternet-android:VERSION")` + `DeepFilterNet.process(pcm48k)`. Sample shows 480-sample frame size at 48kHz.
- **nihui/rife-ncnn-vulkan** — https://github.com/nihui/rife-ncnn-vulkan — Android-ready NCNN+Vulkan build of RIFE v4.6. Ship `librife.so` + model bin/param pair; replace `FrameInterpolationEngine` stub `Log.d` with `System.loadLibrary("rife")` + JNI `interpolate(prev, next, timestep)`.
- **advimman/lama** — https://github.com/advimman/lama — LaMa checkpoints + Qualcomm AI Hub ONNX export (big-lama.onnx). Maps to `InpaintingEngine.kt`; switch to QNN EP when device supports it.

### Known Pitfalls from Similar Projects
- **Media3 shader diverges between GLSurfaceView preview and Transformer export** — androidx/media#1080 — Transformer feeds sRGB-linearized textures; preview path was gamma-encoded. Always sample via `getFromColor` helper or pre-apply gamma in the fragment shader. https://github.com/androidx/media/issues/1080
- **Transformer multi-item transitions unsupported beyond crossfade** — androidx/media#1662 — Composition API supports only crossfade. Wipe/slide require overlapping clips on separate sequences and blending via a custom effect chain. https://github.com/androidx/media/issues/1662
- **gl-transitions Android bundling unresolved** — gl-transitions#129 — no native-library ship; NovaCut will always be string-literal shaders, plan disk/assets storage + SHA validation accordingly. https://github.com/gl-transitions/gl-transitions/issues/129
- **Sherpa-ONNX APK bloat** — each Whisper model per-ABI ~100MB. Use ABI splits + Play Asset Delivery to avoid 200MB base-APK limit. See release naming `sherpa-onnx-1.10.41-arm64-v8a-…apk`. https://github.com/k2-fsa/sherpa-onnx/releases
- **ffmpeg-kit archived in 2025** — upstream stopped publishing. FFmpegEngine stub notes FFmpegX-Android (mzgs) — verify activity before pinning. https://github.com/arthenica/ffmpeg-kit
- **RIFE NCNN VRAM spikes on 1080p mid-range Adreno** — tile-based processing required; use `-t 256` tile size on devices with <6GB RAM or hit `VK_ERROR_OUT_OF_DEVICE_MEMORY`. https://github.com/nihui/rife-ncnn-vulkan/issues
- **LaMa NNAPI delegates silently fall back to CPU on Samsung Exynos** — probe `NnApiDelegate.Options.setAcceleratorName("google-edgetpu")` first, bail to `XnnPackDelegate`. https://github.com/advimman/lama/issues
- **OpenTimelineIO Java bindings arm64-only** — bundling for x86 emulators requires x86_64 JNI which is not shipped. Gate OTIO export on `Build.SUPPORTED_ABIS.contains("arm64-v8a")`. https://github.com/AcademySoftwareFoundation/OpenTimelineIO
- **MediaCodec AV1 encoder availability lies** — `MediaCodecList` reports the encoder but init fails with `MediaCodec.CodecException`. Try-open + close at preset-apply time, not at export start. https://github.com/androidx/media
- **`EditedMediaItemSequence(list)` constructor deprecation continues at 1.9.2** — only the Builder is future-proof. https://github.com/androidx/media

### Library Integration Checklist
- **Sherpa-ONNX (Kotlin ASR)** — `com.k2fsa.sherpa.onnx:sherpa-onnx-android:1.10.41` (pin to model-release tag) — entry: `OfflineRecognizer(config).decode(samples, sampleRate).text`. Gotcha: constructor requires encoder + decoder + tokens as file paths; ship via `assets/` + first-run `copyToFilesDir()` because ONNX Runtime cannot read from Android asset FD.
- **gl-transitions (80+ transitions)** — no package; vendor shaders into `app/src/main/assets/transitions/*.glsl`. Entry: build a shim header defining `getFromColor(uv) = texture(uFrom, uv)` + `getToColor(uv) = texture(uTo, uv)` and prepend to each shader at load time. Gotcha: some transitions declare `uniform float ratio`; default it to the clip aspect or the shader divide-by-zeros.
- **AndroidDeepFilterNet** — `com.kaleyra:deepfilternet-android:<latest>` (verify on Sonatype; artifact has moved) — entry: `DeepFilterNet.init(context)` then `process(FloatArray 480 samples @ 48kHz)`. Gotcha: fixed 48kHz; NovaCut's 16kHz Whisper path must resample to 48k then back — do it once per export, not per frame.
- **RIFE NCNN+Vulkan** — ship `librife.so` + `rife-v4.6/flownet.param` + `flownet.bin` in `jniLibs/arm64-v8a/` and `assets/models/rife/`. Entry: JNI `nativeInterpolate(prev: Bitmap, next: Bitmap, timestep: Float): Bitmap`. Gotcha: Vulkan device init must happen off the main thread; wrap the first call in `withContext(Dispatchers.Default)` or the ANR watchdog fires on cold start.
- **LaMa ONNX** — self-host `big-lama-int8.onnx` (~55MB) on GitHub Release asset; fetch on first use. Entry: `OrtSession(big-lama.onnx).run(mapOf("image" to imageTensor, "mask" to maskTensor))`. Gotcha: model expects 512×512 fixed input — tile-and-blend for higher res; mask channel must be `{0,1}` not `{0,255}`.

## Research Round 4 — Capability Benchmarks to Push NovaCut Beyond Mobile NLEs

Research date: 2026-04-25. Scope: complementary open-source projects, professional editor benchmarks, media-engine standards, and AI/video research that can make NovaCut more capable, stronger, more versatile, and more differentiated. Treat this as product and architecture direction, not a promise to add every dependency directly.

### Executive synthesis
- [ ] **R4.1 — Make timeline interchange a core capability, not an export add-on.** OpenTimelineIO proves that timelines, clips, gaps, transitions, adapters, and media references can become a stable interchange model. NovaCut already has OTIO/FCPXML/EDL export ambitions; the next leap is import, validation, round-trip tests, conflict diagnostics, and a "repair timeline" tool. Touch: `TimelineExchangeEngine.kt`, `TimelineImportEngine.kt`, `ProjectArchive.kt`.
- [ ] **R4.2 — Add a studio-grade color/HDR backbone.** OpenColorIO, ACES, and libplacebo point to a serious color roadmap: named working spaces, display transforms, LUT management, HDR-to-SDR tone mapping, gamut warnings, preview/export parity, and export metadata sanity checks. Touch: `ColorGradingPanel.kt`, `LutEngine.kt`, `HdrCapabilityProbe.kt`, `VideoEngine.kt`, `ExportService.kt`.
- [ ] **R4.3 — Shift from clip editing to object-aware editing.** SAM 2, MediaPipe Tasks, YOLO trackers, AutoFlip, and Depth Anything V2 make the target state clear: users should be able to select a person/object once, then attach captions, stickers, blur, relight, crop, reframe, remove, or background-replace over time. Touch: `TapSegmentEngine.kt`, `SmartReframeEngine.kt`, `VideoMattingEngine.kt`, `InpaintingEngine.kt`.
- [ ] **R4.4 — Add gyro/lens metadata stabilization as a premium differentiator.** Gyroflow shows that stabilization becomes meaningfully better when camera gyro, accelerometer, lens profile, rolling-shutter, and sync data are first-class inputs instead of only frame-difference heuristics. Touch: `StabilizationEngine.kt`, `MediaImportEngine.kt`, new `GyroMetadataParser.kt`.
- [ ] **R4.5 — Build a creator-first auto-edit assistant.** Auto-Editor, Premiere text-based editing, and CapCut auto captions/templates point to one workflow: transcript + silence + filler + beat + face/object salience should produce an editable rough cut, not a one-way render. Touch: `AiFeatures.kt`, `EditorViewModel.kt`, new `AutoCutReviewPanel.kt`.
- [ ] **R4.6 — Introduce a live source / scene graph mode.** OBS's scene/source/filter model is not a traditional NLE feature, but it is powerful for creators. NovaCut can combine CameraX, screen capture, mic sources, layouts, and reusable scenes so recording and editing happen in one product. Touch: `CameraCaptureEngine.kt`, `MediaPickerSheet.kt`, new `LiveStudioPanel.kt`.
- [ ] **R4.7 — Add an advanced compositor graph without making the core editor harder.** Natron, Blender, GStreamer Editing Services, and Olive all argue for a graph/layer model for expert workflows. NovaCut should keep the default editor simple, then expose an optional "Advanced Composite" panel for nodes, adjustment layers, masks, and nested sequences. Touch: `EffectBuilder.kt`, `KeyframeEngine.kt`, `model/Track.kt`.
- [ ] **R4.8 — Treat templates as programmable products.** Remotion, Glaxnimate, Lottie, Rive, and CapCut templates suggest a stronger template format: typed slots, brand tokens, motion presets, caption styles, data-driven variants, preview thumbnails, and compatibility checks before import. Touch: `TemplateManager.kt`, `TemplateMarketplaceEngine.kt`, `LottieTemplateEngine.kt`.
- [ ] **R4.9 — Add professional project confidence tools.** Blender/Pitivi/GES and OTIO point to diagnostics users trust: missing media, broken time ranges, unsupported effects, proxy/original mismatch, color-space mismatch, model missing, export incompatibility, and "what will change on export" reports.
- [ ] **R4.10 — Keep dependency gates strict.** Every research item needs license review, binary-size budget, offline/privacy behavior, device capability probe, fallback path, and deterministic tests before it graduates from roadmap to implementation.

### Project and benchmark findings

| Project / benchmark | What to study | NovaCut opportunity | Priority | Source |
|---|---|---|---|---|
| **OpenTimelineIO** | Stable editorial timeline model, media references, adapters, plugin architecture, FCP XML / AAF / EDL ecosystem | Make OTIO the canonical import/export/validation layer; add "round-trip health" tests and user-facing import diagnostics | P0 | https://github.com/AcademySoftwareFoundation/OpenTimelineIO |
| **GStreamer Editing Services** | Timeline, layers, tracks, clips, effects, project formatters, non-linear editing abstractions | Audit NovaCut's timeline model against proven NLE concepts; add explicit clip/layer validation and project repair messages | P1 | https://gstreamer.freedesktop.org/documentation/gst-editing-services/index.html |
| **Pitivi** | GES-backed editor UX, proxy/background processing patterns, project recovery | Study user-facing project recovery, missing asset handling, and preview/proxy affordances | P2 | https://www.pitivi.org/ |
| **Blender Video Sequencer** | Strips, meta strips, proxies, waveform display, masking, color scopes, compositor bridge | Add compound-strip UX, proxy controls, waveform visibility controls, and better "editor to compositor" handoff | P1 | https://docs.blender.org/manual/en/latest/video_editing/index.html |
| **Natron** | Node-based compositing, rotoscoping, tracking, OpenFX host model | Design optional node graph for power users; map NovaCut effects/masks/keyframes into a readable graph view | P1 | https://natrongithub.github.io/ |
| **OpenFX** | Cross-host image effect plugin API and parameter model | Do not host native OFX on Android yet; borrow its parameter descriptors, preset metadata, keyframe rules, and effect compatibility reporting | P1 | https://openfx.readthedocs.io/en/main/ |
| **OpenColorIO** | Production color management, configs, transforms, display/view separation | Add project color settings, LUT provenance, display transform preview, and warnings when export color metadata disagrees with preview | P0 | https://opencolorio.org/ |
| **ACES** | Professional color pipeline conventions and interchange expectations | Offer an ACES-inspired "Pro color" preset for HDR/log footage; document how NovaCut handles scene/display transforms | P1 | https://acescentral.com/ |
| **libplacebo / mpv renderer** | HDR tone mapping, debanding, scaling, dithering, color management, shader cache | Improve HDR-to-SDR export, high-quality scaling, debanding, and preview/export parity; use as reference even if not directly embedded | P0 | https://github.com/haasn/libplacebo |
| **OBS Studio** | Scenes, sources, filters, audio mixer, recording/streaming workflow | Add "Live Studio" mode: camera/screen/mic sources, reusable layouts, source filters, and direct timeline insertion | P1 | https://obsproject.com/kb/obs-studio-overview |
| **Gyroflow** | Gyro-assisted stabilization, lens profiles, rolling-shutter correction, sync workflow | Add import of camera gyro metadata and lens profiles; expose a stabilization quality mode beyond optical-flow fallback | P0 | https://github.com/gyroflow/gyroflow |
| **Google AutoFlip** | Content-aware reframing for target aspect ratios | Upgrade Smart Reframe from face-only/heuristic framing to salience-aware crop paths with previewable keyframes | P0 | https://opensource.googleblog.com/2020/02/autoflip-open-source-framework-for.html |
| **SAM 2** | Promptable image/video segmentation and video object tracking | Replace single-frame tap segmentation with tracked masks; unlock object removal, sticker attach, background replace, and selective grading | P0 | https://github.com/facebookresearch/sam2 |
| **MediaPipe Tasks** | Android-ready face, hand, pose, object, and segmentation tasks with live/video modes | Use as the pragmatic on-device layer for face/hand/object primitives before heavier models are downloaded | P0 | https://ai.google.dev/edge/mediapipe/solutions/guide |
| **Depth Anything V2** | Monocular depth estimation from normal RGB frames | Add depth blur, foreground/background separation, parallax photos, depth-aware text occlusion, and relighting previews | P1 | https://github.com/DepthAnything/Depth-Anything-V2 |
| **Ultralytics YOLO / trackers** | Detection, segmentation, tracking pipelines | Add object/person track lanes that captions, stickers, blur, and crops can bind to | P1 | https://docs.ultralytics.com/ |
| **Auto-Editor** | Silence/loudness/motion-based automatic cut planning | Add an editable "Cut Assistant" that proposes silence/filler/dead-space edits and keeps every decision reversible | P0 | https://github.com/WyattBlue/auto-editor |
| **Premiere Pro text-based editing** | Source transcription, selecting text to build a rough cut, captions from sequence transcript | Make transcript editing a first-class timeline surface with source transcript vs sequence transcript distinction | P0 | https://helpx.adobe.com/premiere/desktop/edit-projects/edit-video-using-text-based-editing/transcribe-video.html |
| **Final Cut Pro** | Magnetic timeline, roles, multicam, fast organization | Add optional magnetic insert behavior, clip roles, audio-role export, and multicam angle switching | P1 | https://www.apple.com/final-cut-pro/ |
| **DaVinci Resolve** | Dedicated Edit/Fusion/Color/Fairlight/Deliver workspaces | Organize NovaCut's growing tools into clear workspaces or modes: Edit, Audio, Color, AI, Export, with shared status and project diagnostics | P1 | https://www.blackmagicdesign.com/products/davinciresolve |
| **CapCut** | Creator templates, auto captions, text-to-speech, quick social workflows | Improve first-run creator flow: pick format, import clips, generate captions, apply brand/template, export to platform | P0 | https://www.capcut.com/tools/auto-caption-generator |
| **Remotion** | Programmatic video composition, typed props, reusable compositions | Evolve `.novacut-template` into typed recipes with slots, variables, constraints, and generated preview thumbnails | P1 | https://www.remotion.dev/ |
| **Glaxnimate** | Open-source vector animation and motion design focused on Lottie/SVG | Add vector motion-authoring concepts: shape layers, path keyframes, Lottie import validation, and animation previews | P2 | https://glaxnimate.org/ |
| **OpenToonz** | Timeline/xsheet animation, vector drawing, effects, production animation workflow | Borrow exposure-sheet thinking for frame-by-frame overlays, animated stickers, and hand-drawn annotation tracks | P2 | https://opentoonz.github.io/e/ |

### Capability bets to add to the product roadmap

#### 1. Interchange-native pro workflow
- [ ] Add `TimelineImportEngine` support for OTIO/FCPXML/EDL with a visible import report: missing media, unsupported transitions, substituted effects, timecode drift, frame-rate conversions, and color-space assumptions.
- [ ] Add `TimelineExchangeValidator` that runs before export and produces a user-readable compatibility report instead of silently dropping unsupported data.
- [ ] Add golden round-trip fixtures: NovaCut project → OTIO/FCPXML/EDL → NovaCut project should preserve clip order, trim ranges, transitions, markers, text/caption tracks, and media references where possible.
- [ ] Add "archive repair" mode to `ProjectArchive.importArchive()`: remap missing URIs, detect duplicate project IDs, migrate older schema versions, and let the user choose merge vs duplicate.

#### 2. Studio color, scopes, and HDR confidence
- [ ] Add project color settings: source interpretation, working space, display transform, output transform, and LUT stack order.
- [ ] Add a color mismatch warning when preview, source metadata, and export metadata disagree.
- [ ] Add HDR/SDR export preview chips: "HDR preserved", "HDR tone-mapped to SDR", "metadata copied", "metadata rewritten", or "metadata unavailable".
- [ ] Add scopes beyond histogram: waveform, vectorscope, RGB parade, and highlight clipping overlay.
- [ ] Add 3D LUT management: import `.cube`, show source/provenance, preview before/after, and warn when a LUT is applied in the wrong color space.

#### 3. Object-aware editing layer
- [ ] Add a reusable `TrackedObject` model with stable ID, label, confidence, mask path, bounding box path, keyframes, and source engine metadata.
- [ ] Let captions, stickers, blur, mosaic, crop windows, color effects, and audio focus bind to a `TrackedObject`.
- [ ] Add "Select subject once" flow: tap object → refine mask → track through clip → review drift points → apply operation.
- [ ] Add fallback tiers: MediaPipe live task first, then SAM 2 / MobileSAM model download, then manual mask/keyframes if model is unavailable.
- [ ] Add privacy copy for AI tools: on-device by default, cloud only when explicitly selected, with data-retention text in the confirmation sheet.

#### 4. Gyro and lens-aware stabilization
- [ ] Add metadata import for GoPro/Insta360/DJI/Sony gyro streams where available, plus sidecar support for Gyroflow project/protobuf data.
- [ ] Add lens profile selection and auto-detection UI during import.
- [ ] Add stabilization preview metrics: crop required, horizon lock availability, rolling-shutter correction availability, and confidence.
- [ ] Keep optical-flow stabilization as fallback when no gyro metadata exists.

#### 5. Cut Assistant and transcript-first editing
- [ ] Build a non-destructive "Review proposed cuts" panel instead of auto-mutating the timeline.
- [ ] Combine transcript words, silence, filler words, speaker changes, beat detection, motion/salience, and manual protected ranges.
- [ ] Let users accept/reject each proposed cut, ripple the timeline, or create a new rough-cut sequence.
- [ ] Preserve source transcript and sequence transcript separately so users can rough-cut from text and still generate final captions later.

#### 6. Live Studio mode
- [ ] Add a scene/source graph: camera, screen capture, image, video, text, browser/web overlay if supported, mic, and system audio where Android allows it.
- [ ] Add reusable source filters: crop, chroma key, denoise, gain, compressor, LUT, blur, background replace.
- [ ] Add "Record to timeline" so a live scene becomes a clip with editable source metadata and scene layout.
- [ ] Add creator presets: podcast split screen, reaction layout, tutorial camera-over-screen, product demo, livestream intro/outro.

#### 7. Programmable template platform
- [ ] Extend `.novacut-template` with typed slots: media, text, logo, color token, music, caption style, duration, aspect ratio, safe-area rules.
- [ ] Add compatibility checks before import: required engines, model downloads, fonts, aspect ratio support, and license metadata.
- [ ] Add brand kit integration: logo, palette, caption style, type scale, default lower-third, watermark, and platform safe areas.
- [ ] Add template preview rendering with placeholder media and cached thumbnails.
- [ ] Add a self-hostable registry format backed by static JSON/GitHub Releases so the marketplace is not locked to a proprietary server.

### Architecture and implementation guardrails
- **Do not add heavyweight native dependencies blindly.** Several projects are desktop-first or Python/C++ heavy. Borrow architecture and UX patterns first; only embed native code after Android feasibility, ABI size, license, and maintenance checks.
- **Keep model downloads explicit.** SAM 2, Depth Anything, YOLO, ASR, matting, and inpainting models must use a shared `ModelDownloadManager` with size disclosure, checksum verification, retry, Wi-Fi-only setting, and remove-model controls.
- **Keep privacy visible.** AI and cloud paths need a predictable on-device/cloud label, confirmation sheet, and failure fallback. Premium trust depends on users knowing where their media goes.
- **Design every advanced feature with a beginner-safe default.** Advanced graph, color, and interchange tools should not clutter the first-run editor. Use progressive disclosure: simple panel first, expert controls behind "Advanced".
- **Add diagnostics before adding more effects.** Missing media, dropped tracks, unsupported codecs, unsupported effects, model absence, and color/HDR mismatch should be shown before export begins.
- **Use test fixtures for every interchange and repair path.** Roadmap items touching OTIO/FCPXML/EDL/archive import should ship with malformed input, missing media, duplicate ID, old schema, mixed frame-rate, and unsupported-effect fixtures.
- **Prefer reversible operations.** Auto-cutting, object removal, stabilization, reframing, and template application should create editable operations/layers rather than destructive timeline mutations.
- **Budget for device tiers.** Every AI/video feature needs at least three paths: premium acceleration path, mid-device reduced-resolution/tiled path, and no-model/manual fallback path.

### Suggested sequencing
1. **Foundation release:** OTIO/FCPXML/EDL import reports, project archive repair, shared model download manager, and export diagnostics.
2. **Trust release:** color/HDR preview/export parity, scopes, LUT provenance, and metadata warnings.
3. **Creator speed release:** Cut Assistant, transcript-first rough cut, caption style presets, and creator onboarding format picker.
4. **Object-aware release:** tracked object model, MediaPipe task bridge, SAM 2 optional path, tracked stickers/blur/crop.
5. **Motion/template release:** programmable template schema, brand kit, Lottie/Rive validation, vector motion editor concepts.
6. **Studio release:** Live Studio mode, scene/source graph, CameraX/screen capture insertion, OBS-style source filters.
7. **Pro finishing release:** gyro/lens stabilization, advanced compositor graph, OpenFX-like effect descriptors, ACES-inspired color preset.

### Highest-leverage next tickets
- [x] Implement `TimelineExchangeValidator` and run it before every export/import. *(v3.70.0 — wired ahead of `exportToOtio` / `exportToFcpxml`; categorised report drives the result toast.)*
- [x] Complete `ProjectArchive.importArchive()` with media remap, migration, duplicate-ID handling, and recovery copy. *(v3.70.0 — new `importArchiveWithReport()` returns schema/version/media-resolution diagnostics; `IdCollisionPolicy.REGENERATE` default; legacy entry point preserved.)*
- [x] Add shared `ModelDownloadManager` with checksum, retry, Wi-Fi-only, and remove-model controls. *(v3.70.0 — `sha256` per file, `wifiOnly`/`isMeteredNetwork()`, `removeModel`/`removeModels`/`installedBytes`; existing callers source-compatible.)*
- [x] Add Cut Assistant review UI using existing Whisper word timestamps plus silence detection. *(Post-v3.71 pass — added AI Hub / AI toolbar entry plus a review sheet with selected-by-default proposals, accept/reject all, per-proposal toggles, time ranges, reclaimed-duration summary, empty state, and one undoable apply action. Borrowed the comparable Descript / Premiere / CapCut pattern of surfacing detected filler words and pauses for review before bulk deletion.)*
- [~] Add `TrackedObject` model and bind one operation first: tracked blur or tracked sticker. *(v3.71.0 — `TrackedObject` + `TrackedObjectKeyframe` + `TrackedObjectSource` + `TrackedObjectCategory` data classes added; serialised through `AutoSaveState.trackedObjects`; ViewModel `upsertTrackedObject` / `removeTrackedObject` / `setTrackedObjectEnabled` wired with undo. Binding to a real operation (tracked blur shader pulling per-frame keyframes) still TODO — model is persistence-ready and survives autosave + project archive round-trip.)*
- [x] Add color/HDR export confidence chips and mismatch warnings. *(Post-v3.71 continuation — ExportSheet now surfaces a Preserve HDR Metadata toggle, Color / HDR confidence chips, codec/device mismatch warnings, HDR10+ dynamic metadata support status, and advertised HDR encode limit warnings. Pure `ExportColorConfidenceEngine` covers SDR, H.264 HDR mismatch, missing HDR support, HDR10+ support, and limit-overrun cases.)*
- [ ] Add template compatibility metadata and import validation before marketplace work.

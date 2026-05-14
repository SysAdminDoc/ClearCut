# NovaCut — Implementation Roadmap

Forward-looking tracker for planned work. Release history lives in [CHANGELOG.md](CHANGELOG.md).

Current version: **v3.74.9** (versionCode 146).

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
| A.1 | **Sherpa-ONNX ASR** — 51× faster than current Whisper path; word-level timestamps; 99 languages | [engine/whisper/SherpaAsrEngine.kt](app/src/main/java/com/novacut/editor/engine/whisper/) | GitHub Android AAR target `sherpa-onnx-1.13.2.aar` (min Moonshine v2 target `1.12.28+`) | ~33 MB (Moonshine v2 Tiny EN) / ~100 MB (Whisper Tiny multilingual) | Built-in Whisper ONNX |
| A.2 | **DeepFilterNet noise reduction** — ML path for aggressive mode, replaces spectral-gate heuristic | [engine/NoiseReductionEngine.kt](app/src/main/java/com/novacut/editor/engine/NoiseReductionEngine.kt) | `com.github.KaleyraVideo:AndroidDeepFilterNet` (jitpack) | ~8 MB | Spectral gate heuristic |
| A.3 | **OpenCV stabilization** — L-K sparse optical flow + Kalman smoothing, configurable crop | [engine/StabilizationEngine.kt](app/src/main/java/com/novacut/editor/engine/StabilizationEngine.kt) | `org.opencv:opencv:4.10.0` (arm64 only, ~40 MB) | Frame-diff only, no motion compensation |
| A.4 | **RIFE v4.6 frame interpolation** — 24→60/120 fps slow-mo, NCNN+Vulkan | [engine/FrameInterpolationEngine.kt](app/src/main/java/com/novacut/editor/engine/FrameInterpolationEngine.kt) | NCNN prebuilt + RIFE v4.6 model | ~7–10 MB | Frame duplication |
| A.5 | **Real-ESRGAN upscaling** — x4plus + general-x4v3 variants, tile-based | [engine/UpscaleEngine.kt](app/src/main/java/com/novacut/editor/engine/UpscaleEngine.kt) | ONNX Runtime (already active) + model download | ~17 MB | Bicubic scale |
| A.6 | **RobustVideoMatting** — true alpha matte, temporal coherence, hair detail | [engine/VideoMattingEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoMattingEngine.kt) | ONNX Runtime (already active) + RVM model | ~15 MB | MediaPipe binary mask |
| A.7 | **SAM 2.1 / MobileSAM tap-to-segment** — point/box prompts, tracked-mask propagation | [engine/TapSegmentEngine.kt](app/src/main/java/com/novacut/editor/engine/TapSegmentEngine.kt) | ONNX Runtime (already active) + explicit SAM 2.1 or MobileSAM model download | ~10 MB (MobileSAM) / >200 MB working set (SAM 2.1 Hiera Tiny + state cache) | Stub toast |
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

- [x] **B.1 — Media3 Compositor multi-track export** — Done in v3.74.1. All visible `VIDEO` / `OVERLAY` tracks now build independent Media3 1.10 `EditedMediaItemSequence`s and feed one `Composition`; explicit audio tracks remain mixed as separate audio-only sequences.
- [ ] **B.2 — True dual-texture blend modes** — 18 blend modes currently composite against mid-gray because there is no second texture to blend against. Multi-sequence export is now wired; remaining work is replacing the mid-gray shader input with a real dual-input blend path.
- [ ] **B.3 — Reverse playback in export** — `clip.isReversed` works in preview only; Media3 Transformer has no reverse playback. Unblocks once A.9 (FFmpegX) lands — filter complex `[0:v]reverse[v]`.
- [x] **B.4 — Speed-curve-aware `Clip.durationMs`** — Done in this pass. `Clip.durationMs` now integrates `speedCurve(t)` with midpoint sampling, matching the source/timeline inverse mapping so eased ramps report the correct wall-clock length.
- [ ] **B.5 — Segment-level `SmartRenderEngine` bypass activation** — Whole-timeline stream-copy is wired through `ExportDelegate` + `StreamCopyExportEngine` and is now visible in `ExportSheet` as "Fast Trim When Possible". Remaining work: use the analysis ranges to stitch mixed copy/re-encode segments instead of falling back to a full Transformer pass whenever any segment needs processing. Touch: [engine/SmartRenderEngine.kt](app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt), [engine/VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt).
- [x] **B.6 — Text overlay stroke export** — Stroke export is routed through `StrokedTextBitmapOverlay` for stroked text and `ExportTextOverlay` for fill-only text. 2026-04-26 audit confirmed `VideoEngine` selects the bitmap overlay path for positive stroke width.
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
- [x] **C.9 — HDR10 / Dolby Vision export** — Done in v3.74.2 for capable devices. `EncoderCapabilityProbe` now walks advertised HDR profiles for HEVC, AV1, VP9, and AV1-based Dolby Vision Profile 10; `ExportSheet` surfaces HDR10+, Dolby Vision Profile 10, encoder limits, and device-tier hardware encode hints before render.
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
- **Sherpa-ONNX APK bloat** — the official Android AAR is ~54MB before model downloads, and Whisper/Moonshine model bundles can add 30–100MB each. Use ABI splits + Play Asset Delivery before enabling the native backend in base release builds. See release assets such as `sherpa-onnx-1.13.2.aar`. https://github.com/k2-fsa/sherpa-onnx/releases
- **ffmpeg-kit archived in 2025** — upstream stopped publishing. FFmpegEngine stub notes FFmpegX-Android (mzgs) — verify activity before pinning. https://github.com/arthenica/ffmpeg-kit
- **RIFE NCNN VRAM spikes on 1080p mid-range Adreno** — tile-based processing required; use `-t 256` tile size on devices with <6GB RAM or hit `VK_ERROR_OUT_OF_DEVICE_MEMORY`. https://github.com/nihui/rife-ncnn-vulkan/issues
- **LaMa NNAPI delegates silently fall back to CPU on Samsung Exynos** — probe `NnApiDelegate.Options.setAcceleratorName("google-edgetpu")` first, bail to `XnnPackDelegate`. https://github.com/advimman/lama/issues
- **OpenTimelineIO Java bindings arm64-only** — bundling for x86 emulators requires x86_64 JNI which is not shipped. Gate OTIO export on `Build.SUPPORTED_ABIS.contains("arm64-v8a")`. https://github.com/AcademySoftwareFoundation/OpenTimelineIO
- **MediaCodec AV1 encoder availability lies** — `MediaCodecList` reports the encoder but init fails with `MediaCodec.CodecException`. Try-open + close at preset-apply time, not at export start. https://github.com/androidx/media
- **`EditedMediaItemSequence(list)` constructor deprecation continues at 1.9.2** — only the Builder is future-proof. https://github.com/androidx/media

### Library Integration Checklist
- **Sherpa-ONNX (Kotlin ASR)** — target GitHub asset `sherpa-onnx-1.13.2.aar` until an official Maven Central coordinate is published. Entry remains `OfflineRecognizer(config).decode(samples, sampleRate).text`. Gotcha: constructor requires model component file paths; ship via explicit model download + first-run extraction because ONNX Runtime cannot read zipped Android assets directly.
- **gl-transitions (80+ transitions)** — no package; vendor shaders into `app/src/main/assets/transitions/*.glsl`. Entry: build a shim header defining `getFromColor(uv) = texture(uFrom, uv)` + `getToColor(uv) = texture(uTo, uv)` and prepend to each shader at load time. Gotcha: some transitions declare `uniform float ratio`; default it to the clip aspect or the shader divide-by-zeros.
- **AndroidDeepFilterNet** — `com.kaleyra:deepfilternet-android:<latest>` (verify on Sonatype; artifact has moved) — entry: `DeepFilterNet.init(context)` then `process(FloatArray 480 samples @ 48kHz)`. Gotcha: fixed 48kHz; NovaCut's 16kHz Whisper path must resample to 48k then back — do it once per export, not per frame.
- **RIFE NCNN+Vulkan** — ship `librife.so` + `rife-v4.6/flownet.param` + `flownet.bin` in `jniLibs/arm64-v8a/` and `assets/models/rife/`. Entry: JNI `nativeInterpolate(prev: Bitmap, next: Bitmap, timestep: Float): Bitmap`. Gotcha: Vulkan device init must happen off the main thread; wrap the first call in `withContext(Dispatchers.Default)` or the ANR watchdog fires on cold start.
- **LaMa ONNX** — self-host `big-lama-int8.onnx` (~55MB) on GitHub Release asset; fetch on first use. Entry: `OrtSession(big-lama.onnx).run(mapOf("image" to imageTensor, "mask" to maskTensor))`. Gotcha: model expects 512×512 fixed input — tile-and-blend for higher res; mask channel must be `{0,1}` not `{0,255}`.

## Research Round 4 — Capability Benchmarks to Push NovaCut Beyond Mobile NLEs

Research date: 2026-04-25. Scope: complementary open-source projects, professional editor benchmarks, media-engine standards, and AI/video research that can make NovaCut more capable, stronger, more versatile, and more differentiated. Treat this as product and architecture direction, not a promise to add every dependency directly.

### Executive synthesis
- [ ] **R4.1 — Make timeline interchange a core capability, not an export add-on.** OpenTimelineIO proves that timelines, clips, gaps, transitions, adapters, and media references can become a stable interchange model. NovaCut already has OTIO/FCPXML/EDL export ambitions; the next leap is import, validation, round-trip tests, conflict diagnostics, and a "repair timeline" tool. Touch: `TimelineExchangeEngine.kt`, `TimelineImportEngine.kt`, `ProjectArchive.kt`.
- [ ] **R4.2 — Add a studio-grade color/HDR backbone.** OpenColorIO, ACES, and libplacebo point to a serious color roadmap: named working spaces, display transforms, LUT management, HDR-to-SDR tone mapping, gamut warnings, preview/export parity, and export metadata sanity checks. Touch: `ColorGradingPanel.kt`, `LutEngine.kt`, `HdrCapabilityProbe.kt`, `VideoEngine.kt`, `ExportService.kt`.
- [x] **R4.3 — Shift from clip editing to object-aware editing.** First binding pass complete: tracked objects can now drive a clip-level Tracked Mosaic effect in preview/export, the effect persists its target ID, and the Effects panel exposes mosaic actions for tracked masks on the selected clip. Remaining object-aware expansion lives in follow-up items: tracker generation, sticker/text attach, selective grading, crop/reframe, and object removal. Touch: `TrackedObject.kt`, `TrackedObjectEffectBinding.kt`, `EffectBuilder.kt`, `ShaderEffect.kt`, `VideoEngine.kt`.
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
- [x] Add `TrackedObject` model and bind one operation first: tracked blur or tracked sticker. *(Post-v3.71 continuation — Tracked Mosaic now binds `TrackedObject` keyframes to a Media3 shader mask, persists target IDs through autosave/archive, and exposes a selected-clip action in the Effects panel.)*
- [x] Add color/HDR export confidence chips and mismatch warnings. *(Post-v3.71 continuation — ExportSheet now surfaces a Preserve HDR Metadata toggle, Color / HDR confidence chips, codec/device mismatch warnings, HDR10+ dynamic metadata support status, and advertised HDR encode limit warnings. Pure `ExportColorConfidenceEngine` covers SDR, H.264 HDR mismatch, missing HDR support, HDR10+ support, and limit-overrun cases.)*
- [x] Add template compatibility metadata and import validation before marketplace work. *(Post-v3.71 continuation — template exports now include schema/app-version/required-feature metadata plus slot counts; imports infer + merge legacy metadata, reject future schemas/versions/unknown required features before saving, and show clearer failure copy.)*

---

## Research Round 5 — 2026 Refresh

Research date: 2026-05. Scope: refresh Tier A/B unblocks against current upstream releases, identify successor dependencies for archived ones, and surface coverage gaps the previous rounds left thin (accessibility, i18n, observability, distribution, plugin ecosystem, security). Treat as a delta on top of Rounds 2–4 — items already covered are not repeated.

### R5.1 — Media3 1.10 unblocks Tier B.1/B.2/C.9
Media3 1.10 (March 2026) ships the multi-sequence/multi-track Composition API, wider Dolby Vision / HDR handling, and VVC ingest. This is a direct dependency bump that retires the longest-standing limitation in this roadmap.
- [x] **R5.1a — Bump `androidx.media3` from 1.9.2 → 1.10.x** in [app/build.gradle.kts](app/build.gradle.kts). Done in v3.74.0 with Media3 1.10.0 across ExoPlayer / Transformer / Effect / Common / UI / Muxer, then lifted to 1.10.1 in v3.74.2 for the AV1-based Dolby Vision handling fix. Audit confirmed `VideoEngine` and `ProxyEngine` already use `EditedMediaItemSequence.Builder`, so no removed list-constructor migration was needed. Sources: https://github.com/androidx/media/releases · https://developer.android.com/media/media3/transformer/composition
- [x] **R5.1b — Wire multi-sequence Composition into `VideoEngine`** — Done in v3.74.1. `VideoEngine` now exports one sequence per visible visual track, preserves per-track mute/solo/volume semantics for embedded audio, appends dedicated audio-track sequences, and disables embedded-audio transmuxing when multiple visual sequences require compositing. `VideoEngine` and `ProxyEngine` builders now use explicit Media3 track types. Upstream issue #1662 is closed as of 2025-09-09, so NovaCut keeps the existing wipe/slide effect-chain workaround until a dedicated transition migration is implemented.
- [x] **R5.1c — Enable Dolby Vision Profile 10 + HDR10+ export paths** in `EncoderCapabilityProbe` (closes C.9 on capable devices). Done in v3.74.2. The probe now classifies HDR10, HDR10+, and AV1-based Dolby Vision Profile 10 profiles; ExportSheet shows Color / HDR confidence plus a capability-derived Standard / Advanced / Premium device tier. Pixel 10 / Tensor G5-style AV1 + VP9 hardware encode is detected from actual encoders rather than hard-coded model names. Sources: https://developer.android.com/media/media3/transformer/supported-formats · https://developer.android.com/reference/android/media/MediaCodecInfo.CodecProfileLevel · https://www.androidauthority.com/pixel-10-video-recording-av1-vp9-3586429/
- [x] **R5.1d — Android 15/16 Ultra HDR ingest** — Done in v3.74.3. `MediaImportEngine` now records source color metadata for imported clips, classifies video HDR10 / HDR10+ / HLG / Dolby Vision from `MediaFormat`, detects Android Ultra HDR gain-map still images via `Bitmap.hasGainmap()` on Android 14+, persists the metadata in autosave, and feeds source HDR / Ultra HDR chips into ExportSheet confidence. Sources: https://developer.android.com/media/grow/ultra-hdr · https://source.android.com/docs/core/display/hdr

### R5.2 — Dependency successor pivots
- [~] **R5.2a — Pin `salahawad/ffmpeg-kit-community` instead of FFmpegX-Android (A.9).** Blocked in v3.74.4 after re-checking the successor fork: the GitHub project is public and active, but exposes no releases/tags yet, and Maven Central does not currently expose a pinnable `ffmpeg-kit-community` artifact. Do not add an unversioned JitPack dependency for release builds; re-evaluate when the fork publishes a stable tag/AAR coordinate, keeping FFmpegX-Android (mzgs) as the secondary candidate. Sources: https://github.com/arthenica/ffmpeg-kit · https://github.com/salahawad/ffmpeg-kit-community
- [x] **R5.2b — Upgrade Sherpa-ONNX target to v1.12.28+ for Moonshine v2 (A.1).** Done in v3.74.5. `SherpaAsrEngine` now targets Sherpa-ONNX v1.13.2, records the official Android AAR asset URL, and codifies the target model policy: Moonshine v2 Tiny as the default English ASR target and Whisper Tiny multilingual as the non-English fallback. Runtime activation still stays under A.1 because the official project currently ships Android AARs as GitHub release assets rather than a normal Maven Central coordinate, so NovaCut should not silently vendor the native payload into the base app. Source: https://github.com/k2-fsa/sherpa-onnx/releases
- [x] **R5.2c — SAM 2.1 ONNX path now viable for tracked masks (R4.3 follow-up).** Done in v3.74.6. `TapSegmentEngine` now records SAM 2.1 Hiera Tiny ONNX as the default tracked-mask target, preserves MobileSAM as the smaller fallback, and exposes a tested device-gating policy that only recommends SAM 2.1 when premium model downloads are allowed and available RAM meets the >200 MB working-set requirement. Runtime activation remains under A.7 because the model must still be an explicit download. Sources: https://github.com/facebookresearch/sam2 · https://huggingface.co/onnx-community/sam2.1-hiera-tiny-ONNX
- [x] **R5.2d — Generative video stays cloud-optional, not on-device.** Done in v3.74.7. Added `GenerativeVideoPolicy` so Wan 2.2, HunyuanVideo, and VideoCrafter2-class providers are represented only as optional cloud effects, never bundled on-device engines. The policy requires destination, upload-size, retention, and explicit-consent disclosure before a future cloud render can start, with tests preventing accidental on-device bundling. Sources: https://github.com/Wan-Video/Wan2.2 · https://github.com/Tencent-Hunyuan/HunyuanVideo · https://github.com/AILab-CVC/VideoCrafter

### R5.3 — Accessibility coverage gap
- [x] **R5.3a — TalkBack semantics for the timeline custom view.** Done in v3.74.4. Custom-drawn clip nodes now expose richer Compose semantics with clip name/type/track/duration/start-time descriptions, selected and locked-track state, and `customActions` for split, delete, nudge earlier, and nudge later. Accessibility split actions select the clip and move the playhead to a valid split point when needed before reusing the existing split operation. Source: https://developer.android.com/jetpack/compose/accessibility
- [x] **R5.3b — Switch Access + keyboard-only editing flow.** Done in v3.74.8. Visible timeline clips are now focusable nodes with keyboard handling for select, split, delete, and left/right nudge. Focused clips use 100 ms arrow nudges, Shift+arrow uses 1 second nudges, and trim mode maps the same focused arrows to slip edits. The editor shell also supports Shift+arrow selected-clip nudging so hardware keyboards and Switch Access can move a clip without touch.
- [x] **R5.3c — Caption style accessibility presets.** Done in v3.74.9. Caption Style Gallery now has a dedicated accessible preset section with WCAG-AA high-contrast fill/background/stroke templates, a large-text preset above the 24sp 1080p floor, and a reduced-motion preset that maps to static subtitle rendering with no word-by-word or animated caption style. Applying a caption template now carries font, fill, background, highlight, position, outline color/width, shadow, and style-type intent into actual caption data, with autosave preserving the new stroke fields.
- [ ] **R5.3d — Closed audio description track export.** Already in §Backlog. Promote to Round 5 because `SDH / audio-description` text export shipped in v3.69 — the audio track itself is the missing piece. Use TTS engine (A.8) to render the audio-description text into a sidecar or muxed AD track on export.

### R5.4 — Internationalization / localization
- [ ] **R5.4a — Caption translation pipeline (C.5) gains in-editor preview.** Beyond the model dependency, the edit UX needs side-by-side source/target caption rows and a per-caption "regenerate" action. Touch: caption panel.
- [ ] **R5.4b — RTL timeline + overlay bidi text.** Already in §Backlog as "RTL / bidirectional text in overlays". Promote: timeline ruler direction, transition arrows, and the trim-handle hit zones must mirror under RTL locales. Source: https://developer.android.com/training/basics/supporting-devices/languages#BidirectionalText
- [ ] **R5.4c — Strings extraction audit.** Run a one-time pass to make sure no engine-side toasts ship with hardcoded English. Many engine stubs (`UpscaleEngine`, `StyleTransferEngine`, etc.) emit user-facing copy via `Log.d` / `Toast.makeText` with literal strings.
- [ ] **R5.4d — Locale-aware caption font fallback.** Bundle Noto CJK + Noto Arabic + Noto Devanagari subsets and route caption layout through them when the source language ≠ Latin.

### R5.5 — Observability / privacy-preserving telemetry
- [ ] **R5.5a — Opt-in crash reporting via Sentry-Android.** Strict opt-in dialog at first run, settings toggle, redaction of all media URIs and project paths from breadcrumbs. Initialize the SDK only when the user opts in. Source: https://docs.sentry.io/platforms/android/
- [ ] **R5.5b — Aggregate-only usage metrics via Mozilla Glean or a Divvi-Up-style aggregator.** Goal: know which engines actually get used so future stub-activation work targets the most-used features. No raw events, no identifiers. Sources: https://mozilla.github.io/glean/book/language-bindings/android/index.html · https://divviup.org/blog/horizontal-tella/
- [ ] **R5.5c — Privacy dashboard.** A settings screen that lists every category the app collects, lets the user export and delete it, and is the single source of truth for opt-in state. Required for any future Play Store data-safety form changes.
- [ ] **R5.5d — Local-only diagnostic export.** A "Save diagnostic ZIP" action in settings (logcat tail, project header, model registry, Media3 capabilities snapshot) so users can attach to a GitHub issue without us shipping any telemetry pipe.

### R5.6 — Distribution and packaging
- [ ] **R5.6a — Play Asset Delivery for ML model bundles.** Whisper, Moonshine, RVM, RIFE, Real-ESRGAN, MobileSAM, Demucs all together blow past the 200 MB base-AAB. PAD on-demand asset packs, keyed off the existing `ModelDownloadManager`, are the correct vector — keeps F-Droid track buildable while Play install stays small. Source: https://developer.android.com/guide/playcore/asset-delivery
- [ ] **R5.6b — F-Droid track with NonFreeNet anti-feature audit.** Any model fetched from a non-free CDN (Hugging Face is OK; vendor-locked endpoints are not) triggers `NonFreeNet`. Document each model URL + license + checksum in [docs/models.md](docs/models.md) so reproducible-build maintainers can verify. Source: https://f-droid.org/docs/Anti-Features/
- [ ] **R5.6c — Reproducible release builds.** F-Droid inclusion requires byte-identical AAB rebuilds. Pin Gradle, AGP, Kotlin, and JDK in `gradle.properties`; commit the lockfile.
- [ ] **R5.6d — APK split by ABI for OpenCV / NCNN / ONNX.** OpenCV (A.3) is arm64-only at ~40 MB. NCNN (A.4 RIFE) and ONNX Runtime add more. ABI splits + universal-fallback policy on GitHub Releases (arm64 primary, armv7 trimmed, x86_64 emulator-only).

### R5.7 — Plugin ecosystem
- [ ] **R5.7a — Promote `.novacut-template` to first-class plugin format.** Already exists for templates; widen to include LUTs (`.cube`), GLSL transition packs (`.ncfx`), and caption-style packs (`.ncstyle`) under one share intent. Touch: `TemplateManager.kt`, new `PluginRegistry.kt`.
- [ ] **R5.7b — OpenFX-style effect descriptor (read-only).** Stop short of a full OpenFX runtime — instead, define a JSON descriptor that maps NovaCut's effect parameters to OpenFX-named parameters, so future Resolve/Premiere round-trip preserves effect intent. Pairs with C.14 (NLE round-trip import).
- [ ] **R5.7c — Glaxnimate / Rive / Lottie compatibility matrix.** Document which template features survive an export to each format, which require shimming, which are NovaCut-only. Sits in [docs/templates.md](docs/templates.md).

### R5.8 — Testing strategy (when explicitly requested)
*Not auto-executed — listed here so it is on the page when next requested.*
- Roborazzi screenshot tests for every panel (caption, trim, keyframe graph, ExportSheet) — golden images per device tier.
- Espresso flow: import → cut → caption → export. One per major engine activation in Tier A.
- Test fixtures called out in §Architecture guardrails (line 282) — malformed FCPXML, missing media, schema-too-new, mixed frame rate — already in scope; add per-fixture asserts when the request lands.

### R5.9 — Security and supply chain
- [ ] **R5.9a — Track Media3 + ONNX Runtime CVE feeds.** No known unpatched CVEs at research time. Add Dependabot config or Renovate so upstream advisories arrive as PRs. Source: https://nvd.nist.gov/
- [ ] **R5.9b — Model checksum enforcement at runtime.** `ModelDownloadManager` (v3.70.0) records SHA256; make checksum verification non-bypassable on first run, not just at download. Catches partial-download corruption and supply-chain swaps.
- [ ] **R5.9c — Cloud effect call-out sheet.** Any cloud-touching path (C.7 stock, C.4 Wav2Lip cloud variant, R5.2d generative video) shows a one-time "this will leave your device" sheet with the destination, payload size, and an opt-out toggle stored per project.
- [ ] **R5.9d — Sign release artifacts.** Already done for AAB/APK via Play. Add `cosign` signatures to GitHub Release artifacts so users sideloading the APK can verify provenance. Source: https://docs.sigstore.dev/

### R5.10 — Resolved / superseded by upstream
Items in earlier rounds that 2026 upstream releases now resolve or trivialise — reconciled here so they don't get re-researched:
- **A.1 Sherpa-ONNX dep target** — bumped to v1.13.2 for Moonshine v2 (was pinned to 1.10.x).
- **A.9 FFmpegEngine dep target** — switch from FFmpegX-Android to `salahawad/ffmpeg-kit-community` as primary (FFmpegX kept as fallback note).
- **B.1 / B.2 / C.9** — Media3 1.10 dependency bump, multi-sequence export wiring, and HDR/Dolby Vision capability surfacing are complete. Remaining follow-up is the real dual-input blend shader path for B.2.
- **Open Video Editor (devhyper)** — confirmed still the only direct OSS Compose+Media3 competitor at ~650 stars; no new direct competitor surfaced this round.

### Round 5 appendix
- https://github.com/androidx/media/releases — Media3 release notes (1.10 multi-sequence, HDR / Dolby Vision handling, VVC).
- https://github.com/androidx/media/issues/1662 — multi-item transitions beyond crossfade still open.
- https://github.com/arthenica/ffmpeg-kit — archived April 2025.
- https://github.com/salahawad/ffmpeg-kit-community — primary maintained successor fork.
- https://github.com/k2-fsa/sherpa-onnx/releases — Moonshine v2 + Whisper Turbo bindings (v1.12.28+).
- https://github.com/facebookresearch/sam2 — SAM 2.1 ONNX export support.
- https://github.com/Wan-Video/Wan2.2 — generative video, server-side.
- https://github.com/Tencent-Hunyuan/HunyuanVideo — generative video, server-side.
- https://github.com/devhyper/open-video-editor — direct OSS competitor.
- https://github.com/furudo-erika/awesome-capcut-alternatives — awesome-list crosswalk.
- https://github.com/WyattBlue/auto-editor — silence + filler-word algorithm reference for C.2.
- https://developer.android.com/media/grow/ultra-hdr — Ultra HDR gain-map image format and Android API handling.
- https://source.android.com/docs/core/display/hdr — Android HDR metadata / HDR10+ platform keys and playback behavior.
- https://developer.android.com/media/media3/transformer/composition — multi-sequence Composition API.
- https://developer.android.com/media/media3/transformer/supported-formats — Transformer HDR handling and device encode support.
- https://developer.android.com/jetpack/compose/accessibility — Compose semantics + custom actions.
- https://developer.android.com/training/basics/supporting-devices/languages#BidirectionalText — RTL guidance.
- https://developer.android.com/guide/playcore/asset-delivery — Play Asset Delivery for large ML payloads.
- https://f-droid.org/docs/Anti-Features/ — NonFreeNet criteria for ML-model downloads.
- https://docs.sentry.io/platforms/android/ — Sentry-Android opt-in setup.
- https://mozilla.github.io/glean/book/language-bindings/android/index.html — Glean privacy model.
- https://divviup.org/blog/horizontal-tella/ — privacy-preserving aggregate telemetry.
- https://opentelemetry.io/docs/platforms/client-apps/android/ — OpenTelemetry Android SDK.
- https://docs.sigstore.dev/ — cosign signing.
- https://www.androidauthority.com/google-tensor-g5/ — Pixel 10 / Tensor G5 AV1 + VP9 hardware encode.


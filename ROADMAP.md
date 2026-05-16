# NovaCut — Implementation Roadmap

Forward-looking tracker for planned work. Release history lives in [CHANGELOG.md](CHANGELOG.md).

Current version: **v3.74.9** (versionCode 146). Last refresh: **2026-05-16** (Round 6).

Legend: `[ ]` not started · `[~]` in progress · `[x]` done (moved to CHANGELOG).

> **How to read this doc.** Tier A/B/C are the *implementation tables* — every row is a single dependency-bump or limitation fix with a known touch point. Rounds 2–6 are *research deltas* — each captures what changed in the outside world since the prior round and which Tier A/B/C rows it unblocks. The [Forward View](#forward-view--now--next--later--under-consideration--rejected-2026-05) at the end is the synthesis layer: every item from every round classified into Now / Next / Later / Under Consideration / Rejected with one-line justification.

---

## Forward View — Now / Next / Later / Under Consideration / Rejected (2026-05)

This is the **prioritized synthesis** across all rounds. Every line maps back to an item ID elsewhere in this file (Tier A.N, B.N, C.N, R4.N, R5.N, R6.N, or CROSS-PROJECT-ROADMAP §N) so traceability is one search away. New IDs introduced in Round 6 are tagged `R6.*`.

### Now — next 1–2 release cycles
Maximum leverage, builds on shipped foundations, no new model downloads required.

| ID | Item | Why now |
|---|---|---|
| [R6.1](#r61--16-kb-page-size-compliance-play-store-gate) | 16 KB page-size compliance audit | `targetSdk = 36` (Android 16) — Play Store **blocks** non-compliant uploads since 2025-11-01. We bundle ONNX Runtime, MediaPipe; future RIFE/OpenCV/Sherpa-ONNX native deps must be NDK r28+. Compliance is a hard gate. |
| [R6.5](#r65--ffmpeg-kit-16kb-supersedes-r52a-block) | Pin `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` for A.9 | Unblocks B.3 (reverse export), libass subtitle burn-in, two-pass loudnorm, sidechain ducking. Maven Central artifact, 16 KB aligned — supersedes R5.2a "no pinnable artifact" block. |
| [A.2](#tier-a--activate-scaffolded-stubs) | DeepFilterNet 3 activation (model bump) | Already wired with fallback; v3 supersedes v2 with measurably better PESQ on short audio, same ~8 MB footprint, same JNI surface. |
| [A.10](#tier-a--activate-scaffolded-stubs) | Oboe resampler | Pure correctness fix for 44.1↔48 kHz mixing — current Media3 resample drops samples on long mixes. *(In progress 2026-05: [OboeResamplerEngine](app/src/main/java/com/novacut/editor/engine/OboeResamplerEngine.kt) now ships reflection-based `isAvailable()`, `TARGET_OBOE_VERSION` constants, and a pure-math `estimatedOutputFrames(input, fromHz, toHz)` helper audio mix sizing can use today. Runtime `resample()` still returns null until the Maven coord `com.google.oboe:oboe:1.9.0` is wired. 8 new tests.)* |
| [B.2](#tier-b--fix-known-limitations) | True dual-texture programmable blend modes | Single-texture fallback now covers all 18 modes (shipped), but real Hue/Sat/Color/Luminosity math still requires the custom compositor path. Fork or upstream Media3 hook — track androidx/media#1662. |
| [B.5](#tier-b--fix-known-limitations) | Mixed copy/re-encode segment stitching | Whole-timeline stream-copy is wired and surfaced in ExportSheet. Run planner scaffold landed in `SmartRenderEngine.planRuns()` with 8 tests; the composer step (concatenate per-run outputs) is now the only remaining piece and lands once R6.5 (`ffmpeg-kit-16kb`) is wired so the concat demuxer is available. Massive perf win for partial edits. |
| [R6.10](#r610--media3-110-modular-ui-adoption) | Adopt `media3-effect-lottie` module | Removes custom LottieOverlayEffect overlap; one-line dep swap; shipped in Media3 1.10 we already pull. |
| [R5.3d](#r53--accessibility-coverage-gap) | Closed audio description audio track export | SDH text already ships. Pair with system TTS (already wired) for the audio side; muxed AD track via Media3. |
| [R5.4c](#r54--internationalization--localization) | Strings extraction audit | One-time `lint` pass to catch hardcoded `Log.d` / `Toast` literals in engine stubs (`UpscaleEngine`, `StyleTransferEngine`, etc.). Pure mechanical work. |
| [R5.5d](#r55--observability--privacy-preserving-telemetry) | Local-only diagnostic export ZIP | No telemetry pipe to add. Single screen, single button. Strict on-device guarantee. Lowers issue triage cost immediately. |
| [C.6](#audio--speech) | Audio mastering presets (one-tap chains) | Composes A.2 + EBU R128 (both shipped). Pure UI/preset work. High user value, zero new deps. |

### Next — 3–5 release cycles
Dependency activations and engine swaps with concrete upstream targets.

| ID | Item | Cost / gating |
|---|---|---|
| [A.4](#tier-a--activate-scaffolded-stubs) | RIFE v4.6 via NCNN+Vulkan with zero-copy AHardwareBuffer pipeline | ~7–10 MB model. Concrete impl reference: [allenkuo.medium.com](https://allenkuo.medium.com/building-a-high-performance-ai-frame-interpolation-pipeline-on-android-with-vulkan-ncnn-rife-8f279cef51cd). ~10 FPS @ 720p on SD 8 Gen 3. ABI-split required. |
| [A.6](#tier-a--activate-scaffolded-stubs) | RobustVideoMatting activation | ONNX Runtime already in tree; ~15 MB model; Play Asset Delivery (R5.6a) for the bundle. |
| [A.5](#tier-a--activate-scaffolded-stubs) | Real-ESRGAN upscaling activation | Same path as A.6; ~17 MB. Best-paired with R5.6a. |
| [A.7](#tier-a--activate-scaffolded-stubs) | SAM 2.1 Hiera Tiny tap-to-segment activation | Policy + metadata shipped in v3.74.6; remaining work is the model download + inference path. |
| [A.11](#tier-a--activate-scaffolded-stubs) | Style transfer (AnimeGANv2 + Fast NST) activation | 6–9 MB per style; opt-in style packs via PAD. |
| [C.1](#audio--speech) | Demucs htdemucs stem separation | ~80 MB. **Implementation cost > inference cost**: STFT pre/post pipeline is non-trivial — budget engineering for that, not just the ONNX swap. Source: [DEV Community Demucs guide](https://dev.to/stevecase430/spleeter-is-dead-heres-why-everyones-switching-to-demucs-in-2026-j6e). |
| [C.2](#audio--speech) | Silence + filler-word auto-cut | Extends shipped Cut Assistant with word-class filtering. Touch existing `CutAssistantEngine`. |
| [R6.7](#r67--caption-translation-target-pivot-to-madlad-400--bergamot) | Pivot C.5 caption translation target to MADLAD-400 + Mozilla Bergamot | 419 languages, mobile-quantizable. Supersedes NLLB-200 in size/quality. Reference: [Picovoice mobile translation](https://picovoice.ai/blog/open-source-translation/), [RTranslator 3 roadmap](https://nlnet.nl/project/RTranslator/). |
| [R6.8](#r68--whisper-large-v3-turbo-as-multilingual-track-for-a1) | Whisper Large V3 Turbo as the multilingual high-accuracy ASR track | Sits parallel to Moonshine v2 (English-only). 4-decoder-layer ONNX with KleidiAI delivers 2.6× speedup on Arm Android. Pairs cleanly with A.1's existing two-target policy. |
| [R6.2](#r62--litert-migration--nnapi-deprecation) | Remove deprecated NNAPI references from `InpaintingEngine`; document LiteRT CompiledModel path | NNAPI deprecated in Android 15. No code change today (we use ONNX Runtime, not raw NNAPI), but stub docstring lies. Update text + plan future TFLite-backed engines on LiteRT 2.x. |
| [R5.6a](#r56--distribution-and-packaging) | Play Asset Delivery for ML model bundles | Whisper + Moonshine + RVM + RIFE + Real-ESRGAN + SAM + Demucs together blow past the 200 MB base-AAB ceiling. PAD on-demand asset packs keyed off existing `ModelDownloadManager`. F-Droid track still buildable. |
| [R5.5a](#r55--observability--privacy-preserving-telemetry) | Sentry-Android opt-in crash reporting | Strict opt-in, redaction of media URIs, settings toggle. Lowers issue triage cost; no privacy compromise. |
| [R5.5b](#r55--observability--privacy-preserving-telemetry) | Glean aggregate engine-usage metrics | Drives future stub-activation priority. Strictly aggregate, no identifiers. |
| [R4.4](#capability-bets-to-add-to-the-product-roadmap) | Gyro/lens-aware stabilization | **Start with R6.9** (import [Gyroflow project files](https://github.com/gyroflow/gyroflow) as sidecar) before reimplementing gyro math. Falls back to existing optical flow. |
| [C.11](#timeline--composition) | Adjustment layers UX | `AdjustmentLayerEngine` already wired in tree; missing the visual layer model + EffectBuilder bridge. |
| [C.12](#timeline--composition) | Keyframe graph editor (visual bezier UI) | `KeyframeEngine` already supports 12 easings; this is purely UI work in `KeyframeCurveEditor`. |
| [C.13](#timeline--composition) | Compound clip / nested-sequence editor UX | Model exists; missing the "open sub-timeline" gesture and exit flow. |
| [R6.16](#r616--lottie-state-machines--dotlottie-interactive-templates) | Lottie state machines / dotLottie | Closes parity with Rive for A.13 with no new SDK; dotLottie reduces bundle size. |

### Later — beyond 5 cycles or speculative
Larger surface area, premium device tiers, or platform-dependent.

- **[R6.3](#r63--gemini-nano-via-ml-kit-genai-prompt-api)** — Gemini Nano via ML Kit GenAI Prompt API. Auto-summarize project, generate scene descriptions for accessibility, suggest templates, draft caption alternates. Gated on Pixel 10 / 12 GB RAM / NPU; falls back to no-op on other devices.
- **[R6.13](#r613--ai-auto-edit-text-prompt--draft-cut)** — Text-prompt AI Auto-Edit. CapCut Pro 2026 / DaVinci 20 IntelliScript benchmark. Build on Cut Assistant + transcript + beat + face/object track. Reversible operations only.
- **[R6.14](#r614--multicam-smartswitch-via-speaker-detection)** — Multicam SmartSwitch via speaker detection. Binds existing `MultiCamEngine` + Whisper word timestamps + voice-activity detection. DaVinci 20 parity.
- **[R6.15](#r615--ai-animated-subtitles-per-word-emphasis-presets)** — AI Animated Subtitles preset library (per-word emphasis). Extends karaoke captions already shipped in v3.69.
- **[R6.4](#r64--sam-3--sam-31-watch-item-for-tapsegmentengine)** — SAM 3 / SAM 3.1 watch item. 848M-param model, multiplexes 16 objects per forward pass. Not yet mobile-viable; preserve current SAM 2.1 default policy. Refresh when an ONNX-export Hiera-Tiny equivalent ships.
- **[R6.11](#r611--apv-codec-ingest-android-16)** — APV (Advanced Professional Video) codec ingest. Android 16 native; Galaxy S26 Ultra first device. 4:2:2 10-bit, up to 2 Gbps intra-frame.
- **[R6.12](#r612--android-16-ultra-hdr-iso-21496-1-v2)** — Android 16 Ultra HDR ISO 21496-1 v2 (HDR base + SDR gainmap, HEIC encoding). Layer onto shipped v3.74.3 ingest work.
- **[R6.17](#r617--larix-style-live-streaming-output-on-r46)** — Larix-style live streaming output (RTMP / SRT / WebRTC / RIST / NDI). Composes Live Studio mode (R4.6).
- **[R6.18](#r618--musetalk--latentsync-supersede-wav2lip-for-c4)** — MuseTalk / LatentSync supersede Wav2Lip for C.4. **Cloud-only** via shipped `GenerativeVideoPolicy` (R5.2d) — diffusion models are GPU-heavy.
- **[C.4](#audio--speech)** — AI lip-sync. See R6.18 — keep the Wav2Lip stub but pivot to MuseTalk/LatentSync as the actual cloud target.
- **[C.7](#media--assets)** — Stock asset library (Pexels / Pixabay / Freesound / FMA tabs in MediaPicker).
- **[C.8](#media--assets)** — In-app camera with teleprompter. `CameraCaptureEngine` already stubbed.
- **[C.10](#media--assets)** — 360 / VR equirectangular editing. `EquirectangularEngine` already stubbed.
- **[C.14](#interop--distribution)** — NLE round-trip *import* (parse FCPXML/OTIO → NovaCut). Export already ships; inverse is the harder half.
- **[C.15](#interop--distribution)** — Template marketplace. Compatibility metadata shipped post-v3.71; remaining UI + registry.
- **[C.16](#interop--distribution)** — Cross-device project sync. `ProjectSyncEngine` stubbed.
- **[R4.6](#capability-bets-to-add-to-the-product-roadmap)** — Live Studio full scene/source graph.
- **[R4.7](#capability-bets-to-add-to-the-product-roadmap)** — Advanced compositor graph (Natron / Blender VSE inspiration).
- **[R6.19](#r619--libplacebo-as-reference-for-hdr-tone-mapping)** — `libplacebo` as architectural *reference* for HDR tone mapping. Don't embed (Vulkan-only, desktop-first); borrow shader/algo design.
- **CROSS-PROJECT §2.5, §3.1, §3.2, §3.3, §6.5, §6.6/§7.5, §7.4, §8.4, §8.5** — every Cross-Project Tier 2/3 item that isn't a Now/Next clear win. See [CROSS-PROJECT-ROADMAP.md](CROSS-PROJECT-ROADMAP.md).

### Under Consideration — explicit "decide later"
| ID | Item | What blocks the decision |
|---|---|---|
| [A.12](#tier-a--activate-scaffolded-stubs) | ProPainter cloud inpainting | Server cost + ops vs. LaMa per-frame. Decide when usage data justifies the hosting bill. |
| [R5.2d](#r52--dependency-successor-pivots) | Generative video (Wan 2.2 / HunyuanVideo) cloud integration | Policy + tests shipped in v3.74.7. Actual provider integration is speculative until a clear creator workflow demands it. |
| [R6.20](#r620--opencut-arch-cross-pollination-watch-only) | OpenCut architecture cross-pollination (Rust GPU compositor via NDK) | OpenCut is at 50.7k stars but its Android story is thin and the Rust core would be a giant porting effort. Track, don't port. |
| [C.3](#audio--speech) | XTTS v2 voice cloning | License + abuse-risk audit needed; Sherpa-ONNX XTTS bindings exist but cloning consent UX must be designed first. |
| [CROSS §7.7](CROSS-PROJECT-ROADMAP.md) | Geo-tagged clip map | Needs map dep (osmdroid or Mapbox). Worth waiting for §7.4 metadata ingest to ship first. |
| [CROSS §7.8](CROSS-PROJECT-ROADMAP.md) | Preset marketplace scaffold | Depends on §2.1 unified preset library landing first. |
| [CROSS §7.9](CROSS-PROJECT-ROADMAP.md) | AI edit-coach | Heuristic v1 is feasible now; LLM-grade upgrade should pair with R6.3 (Gemini Nano). |
| [R5.7b](#r57--plugin-ecosystem) | OpenFX-style read-only effect descriptor | Useful only if C.14 (NLE round-trip import) lands and round-trip-preserves effect intent. |

### Rejected — explicit "no"
| Item | Why |
|---|---|
| Re-pin `arthenica/ffmpeg-kit` AAR | Archived 2025-04; binaries removed from Maven Central; bundling stale 16 KB-misaligned native lib would fail Play upload. Use R6.5 successor. |
| Always-cloud ASR/TTS that removes the offline fallback | Privacy contract violation; the on-device-by-default stance is explicitly part of the product. |
| Bundling GPL-only native libs without dual license | NovaCut is MIT-licensed; relicensing the binary as GPL is not on the table. Affects: vid.stab (GPL), some Demucs forks. Use Apache/MIT alternatives or shell-out paths. |
| Lip-reading / visual speech recognition | Subsumed by Whisper STT for the 99% case; already evaluated and dropped in CROSS-PROJECT §4. |
| OS-level "Edit in NovaCut" context-menu registry hook | Android `ACTION_EDIT` intent system already covers this; no registry hook needed. CROSS-PROJECT §4. |
| Web-only DOM isolation patterns | N/A on Android native. CROSS-PROJECT §4. |

---



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
- [~] **B.2 — True dual-texture blend modes** — Multi-sequence export now feeds Media3 compositor settings with NovaCut's target output size and per-track opacity, and the single-texture fallback now covers the full 18-mode UI instead of letting Hue / Saturation / Color / Luminosity fall through to Normal. Remaining work is the actual programmable source-over-destination blend path: Media3 1.10 `VideoCompositorSettings` exposes alpha/transform only, so true blend math needs a custom compositor/fork or an upstream Media3 compositor hook beyond the public settings API.
- [ ] **B.3 — Reverse playback in export** — `clip.isReversed` works in preview only; Media3 Transformer has no reverse playback. Unblocks once A.9 (FFmpegX) lands — filter complex `[0:v]reverse[v]`.
- [x] **B.4 — Speed-curve-aware `Clip.durationMs`** — Done in this pass. `Clip.durationMs` now integrates `speedCurve(t)` with midpoint sampling, matching the source/timeline inverse mapping so eased ramps report the correct wall-clock length.
- [~] **B.5 — Segment-level `SmartRenderEngine` bypass activation** — Whole-timeline stream-copy is wired through `ExportDelegate` + `StreamCopyExportEngine` and is now visible in `ExportSheet` as "Fast Trim When Possible". Remaining work: use the analysis ranges to stitch mixed copy/re-encode segments instead of falling back to a full Transformer pass whenever any segment needs processing. *(In progress — the run-planner scaffold landed in `SmartRenderEngine.planRuns(segments)`. It groups consecutive same-flag segments into contiguous `RenderRun`s, breaking on either flag change or timeline gap. 8 new tests in [SmartRenderEngineRunTest](app/src/test/java/com/novacut/editor/engine/SmartRenderEngineRunTest.kt) lock the merge rules. The composer step (export each run with the right engine, concatenate via FFmpeg concat demuxer) waits on R6.5 so the demuxer is available.)* Touch: [engine/SmartRenderEngine.kt](app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt), [engine/VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt).
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
- [x] **C.6 — Audio mastering presets** — After A.2 + existing EBU R128: one-tap "Podcast voice / Music master / Dialogue clean / ASMR" chains (EQ + comp + limiter + denoise pre-configured). *(Done — [AudioMasteringEngine](app/src/main/java/com/novacut/editor/engine/AudioMasteringEngine.kt) already ships the 5 preset recipes (Podcast Voice, Music Master, Dialogue Clean, ASMR, Social Loud). This pass wires them end-to-end: new `buildEffectChain(preset)` converts a `MasteringChain` into the ordered HighPass → ParametricEQ → De-esser → Compressor → Limiter `AudioEffect` list; `AudioMixerDelegate.applyMasteringPreset(trackId, presetId)` replaces the track's audio effect chain in a single saveUndoState/refreshPreview/saveProject pass and is constructor-injected with the engine. 6 new tests in `AudioMasteringEngineTest` cover stage ordering, conditional skips, EQ-slot zero-fill, de-esser threshold scaling, limiter ceiling, and compressor param round-trip.)*

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
- [~] **R5.2a — Pin `salahawad/ffmpeg-kit-community` instead of FFmpegX-Android (A.9).** Blocked in v3.74.4 after re-checking the successor fork: the GitHub project is public and active, but exposes no releases/tags yet, and Maven Central does not currently expose a pinnable `ffmpeg-kit-community` artifact. Do not add an unversioned JitPack dependency for release builds; re-evaluate when the fork publishes a stable tag/AAR coordinate, keeping FFmpegX-Android (mzgs) as the secondary candidate. **Superseded by [R6.5](#r65--ffmpeg-kit-16kb-supersedes-r52a-block)** — `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` is now published on Maven Central, is 16 KB page-size aligned for Android 15+, and is the recommended A.9 pin going forward. Sources: https://github.com/arthenica/ffmpeg-kit · https://github.com/salahawad/ffmpeg-kit-community · https://libraries.io/maven/com.moizhassan.ffmpeg:ffmpeg-kit-16kb
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
- [x] **R5.4c — Strings extraction audit.** *(Done — audit performed 2026-05-16. The Round 5 claim "Many engine stubs emit user-facing copy via `Log.d` / `Toast.makeText`" was incorrect for the current codebase: engines have **zero** `Toast.makeText` or `Snackbar.make` calls. `Log.d` / `Log.w` / `Log.e` output is internal/diagnostic and not subject to localization. The only English-only surfaces in the engine package today are structured diagnostic message fields on result records: `ProjectArchive.errorMessage` (4 strings), `TemplateCompatibility.message` (3), `TimelineExchangeValidator.message` (26). Those carry their own routing story (dialog/report rendering, not toasts) and are tracked as a separate localization workstream rather than via this one-time pass. Added `EngineStringExtractionAuditTest` which fails the build if any engine ever calls `Toast.makeText` or `Snackbar.make` directly — that lane stays closed.)*
- [ ] **R5.4d — Locale-aware caption font fallback.** Bundle Noto CJK + Noto Arabic + Noto Devanagari subsets and route caption layout through them when the source language ≠ Latin.

### R5.5 — Observability / privacy-preserving telemetry
- [ ] **R5.5a — Opt-in crash reporting via Sentry-Android.** Strict opt-in dialog at first run, settings toggle, redaction of all media URIs and project paths from breadcrumbs. Initialize the SDK only when the user opts in. Source: https://docs.sentry.io/platforms/android/
- [ ] **R5.5b — Aggregate-only usage metrics via Mozilla Glean or a Divvi-Up-style aggregator.** Goal: know which engines actually get used so future stub-activation work targets the most-used features. No raw events, no identifiers. Sources: https://mozilla.github.io/glean/book/language-bindings/android/index.html · https://divviup.org/blog/horizontal-tella/
- [ ] **R5.5c — Privacy dashboard.** A settings screen that lists every category the app collects, lets the user export and delete it, and is the single source of truth for opt-in state. Required for any future Play Store data-safety form changes.
- [~] **R5.5d — Local-only diagnostic export.** A "Save diagnostic ZIP" action in settings (logcat tail, project header, model registry, Media3 capabilities snapshot) so users can attach to a GitHub issue without us shipping any telemetry pipe. *(In progress — engine layer shipped via [DiagnosticExportEngine](app/src/main/java/com/novacut/editor/engine/DiagnosticExportEngine.kt). Writes a ZIP under `filesDir/diagnostics/` with 6 entries: app-info, device-info, media-codecs (MediaCodecList.REGULAR_CODECS summary), model-registry (id + install state + size, no file contents), logcat-tail (last 200 lines, redacted), and manifest. Sensitive substrings — content://, file://, /storage/, /data/data/, URLs with query strings, email addresses — are scrubbed before write. Project JSON, media URIs, autosave snapshots, captions/transcripts are **never included** by design. Self-prunes past 3 ZIPs. 8 tests in `DiagnosticExportEngineTest` cover the redaction filter and the bundle structure contract. Remaining work: ~10-line Settings UI wiring to invoke `exportDiagnosticBundle(...)` and route the file through `FileProvider` + `ACTION_SEND` (sketch in the engine docstring).)*

### R5.6 — Distribution and packaging
- [ ] **R5.6a — Play Asset Delivery for ML model bundles.** Whisper, Moonshine, RVM, RIFE, Real-ESRGAN, MobileSAM, Demucs all together blow past the 200 MB base-AAB. PAD on-demand asset packs, keyed off the existing `ModelDownloadManager`, are the correct vector — keeps F-Droid track buildable while Play install stays small. Source: https://developer.android.com/guide/playcore/asset-delivery
- [~] **R5.6b — F-Droid track with NonFreeNet anti-feature audit.** Any model fetched from a non-free CDN (Hugging Face is OK; vendor-locked endpoints are not) triggers `NonFreeNet`. Document each model URL + license + checksum in [docs/models.md](docs/models.md) so reproducible-build maintainers can verify. *(In progress — [docs/models.md](docs/models.md) now records every active model and AAR with source URL, license, and `NonFreeNet` posture for known source domains; SHA-256 columns flagged ⚠ TBD must be filled before each Tier A engine activates per R5.9b.)* Source: https://f-droid.org/docs/Anti-Features/
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
- [x] **R5.9a — Track Media3 + ONNX Runtime CVE feeds.** *(Done — [.github/dependabot.yml](.github/dependabot.yml) watches both Gradle (with grouped PRs for Media3, Compose, AndroidX core, Hilt, ML, Kotlin, Coil) and GitHub Actions. One weekly PR per ecosystem keeps the inbox manageable on a single-maintainer project.)* Source: https://nvd.nist.gov/
- [x] **R5.9b — Model checksum enforcement at runtime.** *(Done — `ModelDownloadManager.isValidModelFile()` gains a `requireChecksum: Boolean = false` parameter; new public `verifyChecksumOrDelete(file, minimumBytes, expectedSha256)` is the first-run verification entry point. When `requireChecksum = true`, a null SHA-256 is a **failure** (not a silent pass-through) — callers loading distribution-critical models pass true so a missing registry entry blocks the load instead of trusting the bytes. Mismatched files are deleted; missing-hash files are kept on disk so a later SHA-256 fill in docs/models.md validates without a re-download. 4 new tests in `ModelDownloadManagerTest`.)*
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

---

## Research Round 6 — 2026-05 Refresh

Research date: 2026-05-16. Scope: changes since the Round 5 cut on 2026-05-14 that materially affect Tier A/B/C sequencing — primarily the 16 KB Play Store gate, the LiteRT migration, the Gemini Nano on-device LLM surface, a concrete ffmpeg-kit successor, SAM 3 / SAM 3.1, and Whisper Turbo. Delta-only: anything already covered in Rounds 2–5 is not repeated. Every item below maps into a tier in the [Forward View](#forward-view--now--next--later--under-consideration--rejected-2026-05) at the top.

### R6.1 — 16 KB page-size compliance (Play Store gate)

Google Play requires 16 KB page-size alignment for all new apps and updates targeting Android 15 (API 35) and higher since 2025-11-01. NovaCut ships with `targetSdk = 36`, so this is a **blocking** constraint, not a future one. The compliance check fires at Play Console upload time, not at runtime. Any bundled native code (ONNX Runtime, MediaPipe Tasks, future NCNN / OpenCV / Sherpa-ONNX / DeepFilterNet / RIFE / Real-ESRGAN AAR payloads) must be NDK r28+ compiled.

- [x] **R6.1a — Audit every bundled `.so` for 16 KB alignment.** *(Done — [scripts/check_16kb_alignment.py](scripts/check_16kb_alignment.py) is a pure-Python ELF parser that walks a directory tree, APK, or AAB and flags any PT_LOAD segment whose alignment is < 0x4000 on arm64-v8a / x86_64 / riscv64. CI workflow [.github/workflows/build.yml](.github/workflows/build.yml) now runs the script over the merged release native libs after `assembleRelease` and fails the build on misalignment.)*
- [x] **R6.1b — Pin NDK r28+ in `gradle.properties`.** *(Done — `gradle.properties` now carries the NDK r28+ pin instructions with the correct AGP block syntax. NovaCut's `:app` currently has no project-side native code, so the pin lives as a comment ready to copy into the `android { }` block when A.4 (NCNN RIFE) or any other project-side native code lands.)*
- [x] **R6.1c — Document the alignment status in [docs/models.md](docs/models.md).** *(Done — initial registry created at [docs/models.md](docs/models.md) with §2 "Native AARs — 16 KB compliance gates" tracking ORT, MediaPipe, ffmpeg-kit-16kb, Sherpa-ONNX, DeepFilterNet, RIFE, OpenCV. R6.1a verification commands documented inline. Every Tier A AAR must record alignment status before it can graduate.)* Sources: https://developer.android.com/guide/practices/page-sizes · https://source.android.com/docs/core/architecture/16kb-page-size/16kb · https://developer.android.com/google/play

### R6.2 — LiteRT migration / NNAPI deprecation

NNAPI is deprecated as of Android 15 (API 35). The replacement is LiteRT (TensorFlow Lite successor) with the CompiledModel API. NovaCut's [`InpaintingEngine.kt`](app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt) still references NNAPI in its docstring as the recommended execution provider — this is now misleading.

- [x] **R6.2a — Strip NNAPI guidance from `InpaintingEngine` docs**; document the ONNX Runtime + XNNPACK/QNN/CoreML EP path as primary, with the LiteRT CompiledModel API as the future TFLite-backed alternative. *(Done — docstring rewritten and the `addNnapi()` call removed from `SessionOptions`. Default CPU EP is used until per-EP capability probing lands. The Tier A InpaintingEngine activation path is unchanged.)*
- [ ] **R6.2b — Audit segmentation / MediaPipe TFLite path** ([`SegmentationEngine.kt`](app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationEngine.kt)) — when MediaPipe Tasks Vision upgrades its internal TFLite to LiteRT, no NovaCut change is needed. Track the upstream version. Sources: https://developer.android.com/ndk/guides/neuralnetworks/migration-guide · https://github.com/google-ai-edge/litert · https://ai.google.dev/edge/litert/overview

### R6.3 — Gemini Nano via ML Kit GenAI Prompt API

Google shipped the ML Kit GenAI Prompt API in alpha 2025-10 and stabilized GenAI APIs (Summarization / Proofreading / Rewriting / Image Description / Speech Recognition) on the Pixel 10 series in 2026. These run on-device via AICore on devices with ≥12 GB RAM and a supported NPU. This is the first time NovaCut can plausibly add a *user-facing* LLM feature without breaking the on-device-by-default privacy stance.

- [ ] **R6.3a — Gated AI Hub card: "Smart Suggestions (Pixel 10 / 12 GB RAM)"** that surfaces device capability and a one-time consent sheet before any Gemini Nano call.
- [ ] **R6.3b — Use cases (in priority order):**
  - Image Description for accessibility — auto-generate spoken audio-description text for a clip range (pair with R5.3d AD track export).
  - Project Summarization — natural-language "what's in this project" for the project list, generated from clip names + caption text.
  - Caption Style Suggestions — rewrite caption text for tone presets (Reels Hook, Tutorial Voice, ASMR Whisper) without leaving the device.
  - Template Pick — given the clip set + transcript, suggest the best `.novacut-template`.
- [ ] **R6.3c — Hard fallback policy:** no GenAI call ever blocks an edit operation. All paths must no-op on non-Pixel-10 / pre-Nano devices.
- [ ] **R6.3d — Never call cloud Gemini from this codepath** — the entire feature is `media3-gen-on-device-only`. Cloud generative work continues to live under `GenerativeVideoPolicy` (R5.2d). Sources: https://developer.android.com/ai/gemini-nano · https://developers.google.com/ml-kit/genai · https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html · https://developer.android.com/google/play/on-device-ai

### R6.4 — SAM 3 / SAM 3.1: watch item for `TapSegmentEngine`

SAM 3 (Nov 2025) introduces text-prompted concept segmentation in addition to point/box prompts. SAM 3.1 (Mar 2026) adds object multiplexing (16 objects per forward pass; doubles video throughput). Combined model is 848M parameters and is currently only feasible on H100-class GPUs.

- **Decision for now:** keep the SAM 2.1 Hiera Tiny default policy shipped in v3.74.6 ([`TapSegmentEngine.kt`](app/src/main/java/com/novacut/editor/engine/TapSegmentEngine.kt)). Do *not* promote SAM 3 / 3.1 to the recommended target until an ONNX-export Tiny variant exists with realistic mobile memory characteristics.
- [x] **R6.4a — Add a `SAM3_HIERA_TINY_ONNX` `ModelVariant` placeholder** with `requiresPremiumTier = true` and `supportsVideoPropagation = true`, gated behind a feature flag that defaults off until upstream ships the export. *(Done — [TapSegmentEngine](app/src/main/java/com/novacut/editor/engine/TapSegmentEngine.kt) now carries `ModelFamily.SAM3` and the `SAM3_HIERA_TINY_ONNX_PLACEHOLDER` enum row with placeholder size estimates (240 MB model, 128 MB state cache, 8 GB RAM floor). `recommendedModelForDevice()` is gated on `SAM3_PLACEHOLDER_ENABLED` (currently false) so the policy default remains SAM 2.1. Tests lock the gate.)*
- [x] **R6.4b — Add a "text prompt → mask" stub method** on `TapSegmentEngine` that delegates to SAM 3 when available; until then, falls back to `MediaPipe.detect()` + bbox → SAM 2.1 mask. Closes the API shape early so the eventual SAM 3 swap is one-line. *(Done — `segmentByTextPrompt(bitmap, textPrompt)` returns null today with an explicit "concept-segmentation unavailable" log. When SAM 3 ships an ONNX export, only the `SAM3` branch needs an implementation; callers don't change.)* Sources: https://ai.meta.com/blog/segment-anything-model-3/ · https://github.com/facebookresearch/sam3 · https://arxiv.org/abs/2511.16719

### R6.5 — `ffmpeg-kit-16kb` supersedes R5.2a block

The R5.2a R&D pass blocked on "no pinnable Maven artifact for ffmpeg-kit successor". As of 2026, `moizhassankh/ffmpeg-kit-android-16KB` publishes to Maven Central as `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`, is 16 KB aligned for Android 15+, and is built with NDK r27d, Full-GPL, and MediaCodec support — the exact wiring [`FFmpegEngine.kt`](app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt) expects.

- [~] **R6.5a — Pin `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`** in [gradle/libs.versions.toml](gradle/libs.versions.toml) and replace the `FFmpegEngine` stub `execute()` body with the `FFmpegKitConfig.executeAsync` bridge. *(In progress — [FFmpegEngine](app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt) now documents the activation path explicitly in its class docstring (catalog entry, build.gradle.kts line, license obligation). `isAvailable()` now does a reflection probe so the rest of the system can branch on FFmpeg presence the moment the dep is added — no recompile needed of consumers. Bridging the stub method bodies to `FFmpegKit.executeAsync` lands when the dep itself is wired.)*
- [ ] **R6.5b — Unblocks downstream:** B.3 reverse playback in export (`filter_complex` `[0:v]reverse[v]`), libass subtitle burn-in (replaces shipped Canvas path on demand), two-pass `loudnorm` filter, sidechain compress ducking, AV1 software-encode fallback on devices without hardware AV1.
- [ ] **R6.5c — GPL note:** `ffmpeg-kit-16kb` is the Full-GPL build. NovaCut is MIT-licensed; bundling a GPL `.so` does not relicense Kotlin source under GPL but does require shipping the LGPL/GPL notice + offer-of-source per FFmpeg's license. Document in [LICENSE](LICENSE) addendum before shipping. (LGPL-only variant exists if we want to dodge the obligation, at the cost of losing libx264/libx265/libfdk — accept H.264/HEVC via MediaCodec only.) Sources: https://github.com/moizhassankh/ffmpeg-kit-android-16KB · https://libraries.io/maven/com.moizhassan.ffmpeg:ffmpeg-kit-16kb · https://www.itpathsolutions.com/ffmpegkit-shutdown-what-to-do-next

### R6.6 — DeepFilterNet 3 model bump for A.2

DeepFilterNet 3 (rolling 2025–2026) raises PESQ to 3.5–4.0+ and STOI past 0.95 on short audio, with the same ~8 MB model footprint and the same JNI surface that A.2 already targets via `KaleyraVideo/AndroidDeepFilterNet`. This is a pure model-bump.

- [x] **R6.6a — When A.2 activates, target DeepFilterNet 3 model weights** (not v2). *(Done — [NoiseReductionEngine](app/src/main/java/com/novacut/editor/engine/NoiseReductionEngine.kt) class docstring now codifies the DeepFilterNet 3 target (PESQ 3.5–4.0+, ~8 MB, same JNI as v2) and the companion object exports `TARGET_MODEL_VERSION = "3"` constants for telemetry and diagnostic surfaces. Activation note documents the model-bytes swap pattern via `ModelDownloadManager` if the AAR still bundles v2. Also collapsed a duplicate `companion object` declaration that shadowed the engine's TAG.)* Sources: https://github.com/Rikorose/DeepFilterNet · https://github.com/KaleyraVideo/AndroidDeepFilterNet · https://noisereducerai.com/deepfilternet-ai-noise-reduction/

### R6.7 — Caption translation target pivot to MADLAD-400 + Bergamot

C.5 (auto-translate captions) was scoped against NLLB-200 in earlier rounds. The 2026 mobile-translation landscape has moved: RTranslator 3 (NGI Mobifree grant, beta Apr–Jun 2026) is replacing NLLB-200 with **MADLAD-400 3B** (419 languages, mobile-quantizable) and **Mozilla Bergamot** (Firefox's offline translation models). Quality benchmarks beat NLLB 54B in their target language pairs.

- [ ] **R6.7a — Re-target C.5 caption translation to MADLAD-400 + Bergamot.** NLLB-200 distilled remains the fallback for languages neither MADLAD nor Bergamot covers well.
- [ ] **R6.7b — In-editor preview UX (already in R5.4a):** side-by-side source/target caption rows, per-caption regenerate action, per-language quality chip. Sources: https://nlnet.nl/project/RTranslator/ · https://github.com/niedev/RTranslator · https://picovoice.ai/blog/open-source-translation/ · https://blog.spikeseed.ai/luxembourgish-translators/

### R6.8 — Whisper Large V3 Turbo as multilingual track for A.1

A.1 already documents the two-target Moonshine v2 (English) + Whisper Tiny multilingual policy. Whisper Large V3 Turbo (4-decoder-layer ONNX) is now the practical multilingual upgrade: ONNX Runtime + Arm KleidiAI delivers ~2.6× faster inference on Android than the Tiny baseline, with substantially better WER. Sherpa-ONNX v1.13.2 already includes the bindings.

- [x] **R6.8a — Document a three-target policy** in `SherpaAsrEngine` model metadata: Moonshine v2 Tiny (English, fastest), Whisper Tiny (multilingual, smallest), Whisper Large V3 Turbo (multilingual, premium accuracy). *(Done — [SherpaAsrEngine](app/src/main/java/com/novacut/editor/engine/whisper/SherpaAsrEngine.kt) ModelVariant now carries `isMultilingual`, `requiresPremiumTier`, and `minimumRamMb` fields. `WHISPER_LARGE_V3_TURBO_MULTILINGUAL` joins the enum as the third target with `sizeMb = 800`, `requiresPremiumTier = true`, `minimumRamMb = 6_144`.)*
- [x] **R6.8b — Gate Turbo on the same premium-tier rule** used for SAM 2.1 (≥6 GB RAM + premium-models-allowed setting). The 4-layer-decoder ONNX is still a heavier download than Tiny. *(Done — `preferredModelFor(language, allowPremiumModels, availableRamMb)` evaluates English-first, then for non-English routes Turbo only when both the user setting and RAM floor are met; otherwise falls back to Whisper Tiny multilingual. Five new tests in `SherpaAsrEngineTest` lock the policy: English ignores premium, multilingual without premium → Tiny, multilingual with premium but low RAM → Tiny, multilingual with premium + ≥6 GB → Turbo, plus metadata assertions on the new enum entry.)* Sources: https://huggingface.co/onnx-community/whisper-large-v3-turbo · https://aihub.qualcomm.com/mobile/models/whisper_large_v3_turbo · https://onnxruntime.ai/blogs · https://github.com/k2-fsa/sherpa-onnx/releases

### R6.9 — Gyroflow project file import for R4.4

R4.4 (gyro/lens-aware stabilization) scopes a from-scratch gyro pipeline. The pragmatic first step is *not* to reimplement: Gyroflow already ships on Google Play, accepts gyro/lens data from GoPro / Sony / DJI / Insta360 / many phones, and writes a `.gyroflow` project file describing the stabilization decisions. NovaCut can accept that file as a sidecar on import, apply the resulting affine + per-frame crop, and skip the whole gyro-math layer entirely.

- [ ] **R6.9a — Add `.gyroflow` sidecar detection** in `MediaImportEngine`. If a sibling file with the same basename exists, parse the JSON (Gyroflow's format is open) and store the resulting per-frame transforms on the imported clip.
- [ ] **R6.9b — Apply Gyroflow transforms during preview/export** via `MatrixTransformation` (already used for clip transforms). Crop ratio surfaced in the stabilization panel.
- [ ] **R6.9c — Full gyro-math reimplementation deferred** to a future round; the sidecar import covers ~80% of the creator value with ~10% of the engineering cost. Sources: https://github.com/gyroflow/gyroflow · https://docs.gyroflow.xyz/app/getting-started/supported-cameras/mobile-phones · https://gyroflow.xyz/

### R6.10 — Media3 1.10 modular UI adoption

Media3 1.10 (we pull 1.10.1) ships several new Compose modules and module splits that NovaCut should opt into rather than maintain custom equivalents.

- [~] **R6.10a — Swap custom `LottieOverlayEffect` for `media3-effect-lottie`** module. *(In progress — [LottieOverlayEffect](app/src/main/java/com/novacut/editor/engine/LottieOverlayEffect.kt) docstring now records the migration plan and the three feature-parity gaps that must be verified before the swap: time-windowed overlay alpha gating via `OverlaySettings`, Lottie `TextDelegate` text substitution for caption templates, and HDR-aware sampling. NovaCut's custom impl carries all three; the official module's 1.10.x surface needs to be audited before the dep flip.)*
- [ ] **R6.10b — Evaluate `media3-ui-compose-material3` Player Composable** to replace bespoke `PreviewPanel` controls. Risk: the new Composable's gesture model differs from our trim-aware preview — likely keep custom for now, but document the parity gap.
- [ ] **R6.10c — Track the `media3-inspector-frame` migration** (the old `FrameExtractor` moved out of `media3-inspector` in 1.10). Audit `extractThumbnail` paths.
- [ ] **R6.10d — `ProgressSlider` Composable** could replace the timeline ruler's progress indicator. Cosmetic, low-priority. Sources: https://developer.android.com/jetpack/androidx/releases/media3 · https://android-developers.googleblog.com/2026/03/media3-110-is-out.html · https://developer.android.com/media/media3/ui/compose

### R6.11 — APV codec ingest (Android 16)

Android 16 ships native APV (Advanced Professional Video) codec support — perceptually lossless intra-frame coding designed for pro post-production. Galaxy S26 Ultra is the first phone to record APV; expect more flagships through 2026. APV 422-10 supports YUV 4:2:2 10-bit at up to 2 Gbps for 2K/4K/8K.

- [x] **R6.11a — Add APV decode probe** to `EncoderCapabilityProbe` / `MediaImportEngine` so APV source files are flagged on import as "pro intra-frame; expect very large files". *(Done — `EncoderCapabilityProbe.probeApvIngest()` returns `ApvSupport(hasDecoder, isHardwareDecoder, decoderNames)`. New `matchingDecoderEntries(mimeTypes)` helper mirrors the existing encoder walker. 5 new tests in `EncoderCapabilityProbeApvTest` cover the MIME constant, the value-object contract, and the JVM-empty fallback.)*
- [ ] **R6.11b — Surface a "Source is APV" chip** in ExportSheet (already chip-driven post-v3.74.3). *(Probe is ready; UI surfacing is the next commit on this item.)*
- [x] **R6.11c — Do NOT encode to APV by default.** *(Codified by omission — the new probe deliberately does not have an `apvEncoder` path. APV is ingest-only.)* Sources: https://source.android.com/docs/whatsnew/android-16-release · https://www.sammobile.com/news/galaxy-s26-ultra-world-first-phone-apv-codec-support/

### R6.12 — Android 16 Ultra HDR ISO 21496-1 v2

v3.74.3 shipped Ultra HDR gain-map ingest using `Bitmap.hasGainmap()` on Android 14+. Android 16 implements additional ISO 21496-1 draft v2 parameters: HDR base + SDR gainmap (inverse of v1), per-colorspace gainmap math, HEIC encoding with gainmap.

- [ ] **R6.12a — Detect HDR-base + SDR-gainmap variants** in `MediaImportEngine`. Today we assume v1 (SDR base + HDR gainmap).
- [ ] **R6.12b — HEIC + gainmap encoding** for still-frame export. Pairs with frame-capture export already shipped.
- [ ] **R6.12c — Update [docs/models.md](docs/models.md)** with the ISO 21496-1 v1 vs v2 distinction. Sources: https://source.android.com/docs/whatsnew/android-16-release · https://developer.android.com/media/grow/ultra-hdr

### R6.13 — AI Auto-Edit (text prompt → draft cut)

The single highest-leverage 2026 competitor feature: CapCut Desktop Pro 2026's AI Auto-Edit and DaVinci Resolve 20's AI IntelliScript both take a text description + raw footage and produce a draft cut (scene recognition, speech transcription, quality scoring, automatic color, audio leveling, transitions). NovaCut has every ingredient — Cut Assistant, Whisper transcripts, beat detection, SmartReframe, color confidence — but no composer that turns a prompt into a draft.

- [ ] **R6.13a — Compose AI Auto-Edit pipeline** as a new `AutoEditComposerEngine` that orchestrates Cut Assistant + transcript + beat + face/object salience.
- [ ] **R6.13b — Input form:** clip set + optional 1-line description + target duration + target platform preset.
- [ ] **R6.13c — Output:** a new `Project` sequence created as a *draft branch*, not a destructive replace. User reviews in a "Proposed Edit" panel (pattern from shipped Cut Assistant Review) and accepts whole-or-piecemeal.
- [ ] **R6.13d — All decisions reversible** — pin to existing command-based undo. Sources: https://flowith.io/blog/capcut-desktop-pro-2026-ai-auto-edit-define-short-form-video-2026/ · https://www.miracamp.com/learn/davinci-resolve/whats-new-all-new-features-explained · https://filmora.wondershare.com/video-editor-review/davinci-resolve-editing-software.html

### R6.14 — Multicam SmartSwitch via speaker detection

DaVinci Resolve 20's Multicam SmartSwitch auto-cuts between camera angles based on which speaker is active. NovaCut has `MultiCamEngine` (audio-based sync) and Whisper word timestamps with speaker labels — missing only the binder.

- [ ] **R6.14a — Add a `SpeakerSwitchPlanner`** that consumes `MultiCamEngine.syncedTracks` + Whisper diarization metadata and emits a cut plan (per-speaker → preferred angle).
- [ ] **R6.14b — Surface in the multicam panel** as "Auto-switch by speaker" toggle, with manual override per speaker → angle assignment. Sources: https://www.miracamp.com/learn/davinci-resolve/whats-new-all-new-features-explained · https://filmora.wondershare.com/video-editor-review/davinci-resolve-editing-software.html

### R6.15 — AI Animated Subtitles (per-word emphasis presets)

DaVinci 20's AI Animated Subtitles animates each word as it's spoken. NovaCut shipped karaoke captions in v3.69 (`KaraokeCaptionEngine`); this is an extension of the same pipeline with motion presets per word.

- [ ] **R6.15a — Extend the caption style gallery** with "Word Pop", "Word Bounce", "Word Glow", "Word Slide-In" presets that operate on word boundaries (already exposed by Whisper word timestamps).
- [ ] **R6.15b — Performance budget:** per-word animation should run on the existing Canvas overlay path; do not add a GPU pass per word. Cap concurrent animating words at 3 to bound the per-frame cost. Sources: https://www.miracamp.com/learn/davinci-resolve/whats-new-all-new-features-explained

### R6.16 — Lottie state machines / dotLottie interactive templates

Lottie shipped state machines in late 2025 (formerly Rive-exclusive). dotLottie is a compressed container (10–15× smaller binaries) with theming + state-machine support. This narrows the A.13 (Rive interactive templates) gap without needing the Rive Android dep.

- [ ] **R6.16a — Bump `lottie-compose` to 7.x** when it ships dotLottie + state-machine APIs. Today we pin 6.6.2.
- [ ] **R6.16b — Add dotLottie import path** to `LottieTemplateEngine` — accept `.lottie` zip in addition to `.json`.
- [ ] **R6.16c — Re-scope A.13 (Rive):** Lottie state machines may obviate Rive for the *interactive template* use case. Keep A.13 in the table but downgrade to "Under Consideration" until a concrete feature requires Rive's specific renderer. Sources: https://lottiefiles.com/blog/lottie-animations/lottiefiles-or-rive · https://unicornicons.com/learn/rive-vs-lottie · https://www.rivemasterclass.com/blog/rive-vs-lottie

### R6.17 — Larix-style live streaming output (on R4.6)

R4.6 (Live Studio mode) scopes scene/source graph composition. The companion output side has a clear reference: Larix Broadcaster on Android does RTMP / RTMPS / SRT / WebRTC / RIST / RTSP / NDI|HX2 with adaptive bitrate, Talkback audio return, and concurrent front+rear camera streaming on Android 11+.

- [ ] **R6.17a — Add an `OutputStreamingEngine` stub** with `RTMP` + `SRT` as the first two protocols (most common for creator workflows). Cloud RTMP target = YouTube / Twitch / Kick endpoints; SRT for low-latency.
- [ ] **R6.17b — Compose against `CameraCaptureEngine`** (already stubbed) once R4.6 lands so a live scene can be sent direct from the scene graph.
- [ ] **R6.17c — Adaptive bitrate** mirrors the Larix pattern: probe network, scale resolution + framerate downward, never block the encoder thread. Sources: https://softvelum.com/larix/ · https://play.google.com/store/apps/details?id=com.wmspanel.larix_broadcaster

### R6.18 — MuseTalk / LatentSync supersede Wav2Lip for C.4

C.4 (AI lip-sync) was scoped against Wav2Lip. The 2026 quality bar has moved: MuseTalk and LatentSync (diffusion-based, latent-space) produce near-photorealistic results where Wav2Lip's GAN artifacts are visible. Wav2Lip retains the cost crown (3-min video → 5–15 min compute on cloud GPU), but quality demand pushes new work toward MuseTalk / LatentSync.

- [ ] **R6.18a — Pivot C.4's target to MuseTalk (primary) / LatentSync (high-quality variant)** and document Wav2Lip as the legacy fallback.
- [ ] **R6.18b — Cloud-only path enforced via shipped `GenerativeVideoPolicy`** (R5.2d). No on-device bundling — all three models are far too heavy.
- [ ] **R6.18c — License audit:** Wav2Lip has non-commercial weights for some checkpoints; MuseTalk is CC-BY-NC; LatentSync uses Stable Diffusion derivatives. Document license per provider in `GenerativeVideoPolicy` provider metadata before any consent sheet ships. Sources: https://www.pixazo.ai/blog/best-open-source-lip-sync-models · https://lipsync.com/blog/open-source-lip-sync · https://sync.so/blog/the-best-free-open-source-lipsync-tools-2/

### R6.19 — `libplacebo` as reference for HDR tone mapping

R4.2 (studio color / HDR backbone) names ACES, OCIO, libplacebo as references. As of 2026, libplacebo is the de-facto HDR tone-mapper for mpv/VLC/FFmpeg with dynamic HDR, Dolby Vision Profile 5 conversion, perceptual gamut stretching, debanding, contrast recovery. Vulkan-first, no Android port — embedding is not practical.

- [ ] **R6.19a — Borrow the algorithm design** (specifically the HDR→SDR display transform with measurement + dynamic exposure) for NovaCut's HDR confidence path. Today we report capability; tomorrow we should report *quality estimate* (banding risk, clipping risk).
- [ ] **R6.19b — Do not embed libplacebo** — Vulkan-only and desktop-first. Borrow patterns into pure GLSL ES 3.0 shaders consistent with the existing 37-transition pipeline. Sources: https://libplacebo.org/options/ · https://github.com/haasn/libplacebo · https://carlosfelic.io/misc/mpv-hdr-guide-2026/

### R6.20 — OpenCut arch cross-pollination (watch-only)

OpenCut (~50.7k stars, v0.3.0 in Apr 2026) is the closest open-source CapCut competitor by attention. Architecture: Next.js web + Rust core (GPU compositor + effects + masks via WASM) + early GPUI desktop. **Android story is thin** — no concrete Android target.

- **Watch, don't port.** The Rust GPU compositor is conceptually exciting (could inspire B.2's programmable dual-texture blend) but porting Rust via NDK to coexist with Media3 is a multi-quarter engineering effort with unclear ROI given NovaCut's Kotlin-first stack.
- [ ] **R6.20a — Track OpenCut's Android proof of concept** if one materializes. No code action yet. Sources: https://github.com/opencut-app/opencut · https://b-lab.team/en/content/c5595409-729f-49d5-9ad7-bd58ae5b8bc9

### R6.21 — Open Video Editor (direct OSS competitor) — community asks

[`devhyper/open-video-editor`](https://github.com/devhyper/open-video-editor) remains the only direct Compose + Media3 + Android-native OSS competitor (~654 stars, latest v1.1.3 in Sep 2024). The open enhancement issues are revealing:

| Their open ask | NovaCut status |
|---|---|
| Timeline (#48) | ✅ Multi-track with thumbs, waveforms, snap, markers, color labels |
| Keyframes for filters (#47) | ✅ KeyframeEngine + 12 easings + bezier editor (graph editor pending — C.12) |
| Image layer support (#24) | ✅ Sticker/GIF/image overlays with position/scale/rotate/opacity |
| Face blurring (#31) | ✅ Tracked Mosaic (post-v3.71) |
| Pitch audio controls (#46) | ✅ Pitch shift in AudioEffectsEngine |
| Audio-video muxing (#37) | ✅ Multi-sequence Media3 Composition (v3.74.1) |
| Rotate quick buttons (#57) | ✅ Crop panel + transform rotation |
| Opus audio support (#35) | ✅ Via Media3 ExoPlayer; MediaPicker launcher now accepts `application/ogg` alongside `audio/*` and the resolver MIME check + extension probe both recognise `.opus` (R6.21 verified). |
| RGB alpha adjustment (#39) | ✅ Color grading + blend modes |

**Takeaway:** NovaCut is *meaningfully ahead* of the only direct OSS Android NLE on every published community ask. The community signal is not "what to add" but "differentiate harder on the polished UX of features you already have." Source: https://github.com/devhyper/open-video-editor/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement

### Round 6 appendix

- https://github.com/androidx/media/releases — Media3 1.10.1 (May 2026), 1.10.0 (Mar 2026); Lottie module split; Dolby Vision Profile 10.
- https://developer.android.com/jetpack/androidx/releases/media3 — Media3 release notes index.
- https://developer.android.com/media/media3/ui/compose — Media3 Compose UI modules (`ui-compose`, `ui-compose-material3`).
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html — Media3 1.10 announcement.
- https://github.com/androidx/media/issues/1662 — Multi-item transitions beyond crossfade (still closed-but-unimplemented as of 2026-05).
- https://developer.android.com/guide/practices/page-sizes — 16 KB page-size compliance.
- https://source.android.com/docs/core/architecture/16kb-page-size/16kb — 16 KB AOSP docs.
- https://developer.android.com/ndk/guides/neuralnetworks/migration-guide — NNAPI migration guide.
- https://github.com/google-ai-edge/litert — LiteRT (TFLite successor).
- https://ai.google.dev/edge/litert/overview — LiteRT overview, CompiledModel API.
- https://developer.android.com/ai/gemini-nano — Gemini Nano + AICore on Android.
- https://developers.google.com/ml-kit/genai — ML Kit GenAI APIs (Summarization, Proofreading, Rewriting, Image Description, Speech Recognition).
- https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html — ML Kit GenAI Prompt API alpha.
- https://developer.android.com/google/play/on-device-ai — Play for On-device AI.
- https://developer.android.com/guide/playcore/asset-delivery — Play Asset Delivery (200 MB base / 4 GB cumulative quotas).
- https://ai.meta.com/blog/segment-anything-model-3/ — SAM 3.1 (Mar 2026 update; multiplexed video tracking).
- https://github.com/facebookresearch/sam3 — SAM 3 model + checkpoints.
- https://arxiv.org/abs/2511.16719 — SAM 3 paper.
- https://github.com/moizhassankh/ffmpeg-kit-android-16KB — ffmpeg-kit successor, 16 KB aligned.
- https://libraries.io/maven/com.moizhassan.ffmpeg:ffmpeg-kit-16kb — Maven Central coordinate.
- https://www.itpathsolutions.com/ffmpegkit-shutdown-what-to-do-next — ffmpeg-kit EOL context.
- https://github.com/Rikorose/DeepFilterNet — DeepFilterNet 3.
- https://github.com/KaleyraVideo/AndroidDeepFilterNet — Android JNI bindings.
- https://noisereducerai.com/deepfilternet-ai-noise-reduction/ — DeepFilterNet 3 quality benchmarks.
- https://nlnet.nl/project/RTranslator/ — RTranslator 3 NGI grant (MADLAD-400 + Bergamot pivot).
- https://github.com/niedev/RTranslator — RTranslator Android source.
- https://picovoice.ai/blog/open-source-translation/ — MADLAD-400 mobile deployability.
- https://huggingface.co/onnx-community/whisper-large-v3-turbo — Whisper Turbo ONNX.
- https://aihub.qualcomm.com/mobile/models/whisper_large_v3_turbo — Qualcomm AI Hub Turbo build.
- https://onnxruntime.ai/blogs — ONNX Runtime + KleidiAI 2.6× Arm Android speedup.
- https://github.com/gyroflow/gyroflow — Gyroflow project + Android app.
- https://docs.gyroflow.xyz/app/getting-started/supported-cameras/mobile-phones — Gyroflow mobile camera support.
- https://allenkuo.medium.com/building-a-high-performance-ai-frame-interpolation-pipeline-on-android-with-vulkan-ncnn-rife-8f279cef51cd — RIFE + NCNN + Vulkan zero-copy AHardwareBuffer pipeline (Snapdragon 8 Gen 3 benchmarks; 720p ~10 FPS, 1080p ~4 FPS).
- https://github.com/nihui/rife-ncnn-vulkan — RIFE NCNN+Vulkan reference impl.
- https://github.com/hzwer/Practical-RIFE — PracticalRIFE lite model variants for mobile.
- https://flowith.io/blog/capcut-desktop-pro-2026-ai-auto-edit-define-short-form-video-2026/ — CapCut Desktop Pro 2026 AI Auto-Edit pipeline detail.
- https://www.miracamp.com/learn/davinci-resolve/whats-new-all-new-features-explained — DaVinci Resolve 20 AI feature list (IntelliScript, SmartSwitch, Animated Subtitles).
- https://filmora.wondershare.com/video-editor-review/davinci-resolve-editing-software.html — DaVinci 20 deep dive.
- https://source.android.com/docs/whatsnew/android-16-release — Android 16 release notes (APV codec, Ultra HDR ISO 21496-1 v2).
- https://www.sammobile.com/news/galaxy-s26-ultra-world-first-phone-apv-codec-support/ — First-device APV ingest.
- https://lottiefiles.com/blog/lottie-animations/lottiefiles-or-rive — Lottie state machines (dotLottie).
- https://unicornicons.com/learn/rive-vs-lottie — Rive vs Lottie 2026.
- https://softvelum.com/larix/ — Larix Broadcaster protocol surface (RTMP/SRT/WebRTC/RIST/NDI).
- https://play.google.com/store/apps/details?id=com.wmspanel.larix_broadcaster — Larix on Google Play.
- https://www.pixazo.ai/blog/best-open-source-lip-sync-models — MuseTalk / LatentSync state of the art.
- https://lipsync.com/blog/open-source-lip-sync — Lip-sync OSS comparison.
- https://libplacebo.org/options/ — libplacebo HDR options.
- https://github.com/haasn/libplacebo — libplacebo source.
- https://carlosfelic.io/misc/mpv-hdr-guide-2026/ — 2026 HDR pipeline reference.
- https://github.com/opencut-app/opencut — OpenCut (50.7k stars, Rust core).
- https://github.com/devhyper/open-video-editor — Open Video Editor (only direct Android Compose+Media3 OSS competitor).
- https://github.com/devhyper/open-video-editor/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement — competitor open enhancement list.
- https://github.com/furudo-erika/awesome-capcut-alternatives — awesome-list crosswalk (still relevant; refreshed Round 6).
- https://www.androidauthority.com/snapdragon-8-elite-gen-5-benchmarks-3600242/ — Snapdragon 8 Elite Gen 5 benchmarks (multicore performance + sustained throttle context for AI inference budgets).
- https://www.androidcentral.com/phones/google-pixel/google-tensor-g5 — Tensor G5 TPU 60% uplift over G4 (Gemini Nano host).
- https://developer.android.com/jetpack/androidx/releases/compose — Compose runtime + Material 3 release notes (drives R6.10 evaluation).
- https://docs.gyroflow.xyz/app/getting-started/basic-usage/stabilization — Gyroflow stabilization parameter surface (drives R6.9 sidecar parser scope).


# ClearCut — Model Registry

Authoritative list of every ML model and native AAR that ClearCut may fetch or bundle. Pairs with the [ModelDownloadManager](../app/src/main/java/com/novacut/editor/engine/ModelDownloadManager.kt) and is the single reference for F-Droid `NonFreeNet` audits, license review, reproducible build verification, and 16 KB page-size compliance tracking.

**Last refresh:** 2026-06-04 · Runtime activation requirements are recorded in the AI tool activation gate matrix below.

---

## 1. Active models (shipped or scaffolded with download path)

| ID | File | Source URL | SHA-256 pinned | Size | License | NDK aligned for 16 KB? | Used by |
|---|---|---|---|---|---|---|---|
| `whisper.tiny.en.onnx` | `encoder_model.onnx`, `decoder_model.onnx`, `vocab.json` | `https://huggingface.co/onnx-community/whisper-tiny.en/resolve/2575352d61be1bf7225cf8f8b268a4678025fc58/onnx/...` + `/vocab.json` | `encoder_model.onnx`: `8c361b9430a5ef6619ee64b7fe06c725df19f36d508cc8b847064b34a888a3fe`; `decoder_model.onnx`: `14f1d425a4821feeba77cf93eeeaf812ca816f2e3fec382b4f0fa93d29de710e`; `vocab.json`: `f6bd25a65e4e63ca31360e9fb11c7e4f9a391a78385d640acd814092dd6eee4f` | 145.2 MiB total (32,904,992 + 118,395,947 + 999,186 bytes) | MIT (model: Apache-2.0 upstream) | n/a (pure ONNX, no native) | [`WhisperEngine.kt`](../app/src/main/java/com/novacut/editor/engine/whisper/WhisperEngine.kt) |
| `selfie_segmenter.tflite` | `selfie_segmenter.tflite` | `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite?generation=1683436453600523` | `191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b` | 249,537 bytes (~244 KiB) | Apache-2.0 (Google MediaPipe) | n/a (TFLite via MediaPipe AAR; AAR alignment tracked below) | [`SegmentationEngine.kt`](../app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationEngine.kt) |
| `lama_dilated.onnx` | `lama_dilated.onnx` | `https://huggingface.co/qualcomm/LaMa-Dilated/resolve/ab898502c9bd764a50eb2719a309694b43eae658/LaMa-Dilated.onnx` (Qualcomm AI Hub export of [advimman/lama](https://github.com/advimman/lama)) | `6f9e1d401eb67a63fb1be6c0cf3283d800bf4c20656028f96b044fedc382d762` | 182,781,794 bytes (~174.3 MiB) | Original implementation Apache-2.0; Qualcomm HF metadata: `other` — review NOTICE before release | n/a (ONNX) | [`InpaintingEngine.kt`](../app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt) |
| `deep_filter_mobile_model` | `res/raw/deep_filter_mobile_model` bundled inside `io.github.kaleyravideo:android-deepfilternet:0.0.8` | `https://repo.maven.apache.org/maven2/io/github/kaleyravideo/android-deepfilternet/0.0.8/android-deepfilternet-0.0.8.aar` (tag `v0.0.8`, commit `42ea9b786babf7d67008a81cf25257b4735e4127`) | model file: `5600b6857117ecc7cf460b8ec4841963bfa6d718921d424d42dea5d3d37a8c32`; AAR: `6566a208fe476a71b20558f92d93a1c0db49fd93b36fcdaea17a10260189d167` | 7,984,565 bytes (~7.6 MiB) model; 26,947,678 bytes AAR | Apache-2.0 for Kaleyra AAR; upstream DeepFilterNet code/weights are MIT or Apache-2.0 at user option | n/a for model bytes; native AAR alignment tracked below | [`NoiseReductionEngine.kt`](../app/src/main/java/com/novacut/editor/engine/NoiseReductionEngine.kt) |

## 2. Native AARs (bundled or planned) — 16 KB compliance gates

Every native AAR shipped with ClearCut must be 16 KB page-size aligned. Google Play **blocks** uploads of non-compliant native libs at `targetSdk = 36` (Android 16) since 2025-11-01. Verify ELF segment alignment with `python scripts/check_16kb_alignment.py app/build/outputs/apk/debug/app-debug.apk` and APK packaging with SDK Build Tools `zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk` before each release. See [ROADMAP.md R6.1](../ROADMAP.md#r61--16-kb-page-size-compliance-play-store-gate).

| AAR | Status | Source | 16 KB aligned? | License | Notes |
|---|---|---|---|---|---|
| `onnxruntime-android:1.26.0` | Bundled today | Microsoft / Maven Central | ✅ Verified 2026-05-17 with `scripts/check_16kb_alignment.py` and `zipalign -P 16` on the debug APK. | MIT | Bumped in R7.3 to move past the 1.17.x pre-Play-gate native package. Re-verify on every ORT bump. |
| `mediapipe-tasks-vision:0.10.35` | Bundled today | Google / Maven Central | ✅ Verified 2026-05-17 with `scripts/check_16kb_alignment.py` and `zipalign -P 16` on the debug APK. | Apache-2.0 | Upgraded from 0.10.14 because the older `libmediapipe_tasks_vision_jni.so` used 4 KB ELF alignment. Current AAR packages `libmediapipe_tasks_jni.so`. |
| `lottie-compose:6.7.1` | Bundled today | Airbnb / Maven Central | n/a (pure Kotlin) | Apache-2.0 | See R6.16 for `lottie-compose:7.x` bump (state-machines + dotLottie). |
| `media3-effect-lottie:1.10.1` | Active Kotlin dep (R7.5 / R6.10a) | `androidx.media3:media3-effect-lottie:1.10.1` from Google Maven (`https://dl.google.com/dl/android/maven2/androidx/media3/media3-effect-lottie/1.10.1/media3-effect-lottie-1.10.1.aar`) | n/a (pure Kotlin; no native libraries) | Apache-2.0 | AAR SHA-256 `83b26f6f25e785b949263fc52cb7c0fb5f0e371445fa1d7b9a0ed0b71c05e69d`; used by `Media3LottieTextureOverlay` for non-HDR finite-window title overlays, with the legacy shader retained for HDR and over-long windows. |
| `sherpa-onnx-1.13.2.aar` | Targeted (A.1) | [GitHub release asset](https://github.com/k2-fsa/sherpa-onnx/releases) | ⚠ Verify per AAR release — Sherpa-ONNX 1.12.28+ targets NDK r27. | Apache-2.0 | Distributed via GitHub release assets, not Maven Central. Must be vendored into `app/libs/` or fetched via PAD. |
| `ffmpeg-kit-16kb:6.1.1` | Active native dep (R6.5a, A.9) | `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` (Maven Central) | ✅ Debug APK verified: 30 required 16 KB-aligned native libs, 0 misaligned | Treat as GPL-3 obligation material: Maven POM declares LGPL-3.0, but the packaged AAR/APK includes GPLv3 `license.txt` and GPLv3 `source.txt` source-offer resources | ClearCut is MIT-licensed; redistributed builds that include the AAR must keep FFmpegKit license/source resources and the [LICENSE](../LICENSE) addendum. |
| `android-deepfilternet:0.0.8` | Active native dep (A.2 / R6.6) | `io.github.kaleyravideo:android-deepfilternet:0.0.8` (Maven Central) | ✅ AAR preflight verified 2026-05-17: arm64-v8a and x86_64 `libdf.so` pass `scripts/check_16kb_alignment.py`; debug APK re-verified after integration with 32 OK, 40 skipped, 0 misaligned. | Apache-2.0 | Bundled-model variant includes `res/raw/deep_filter_mobile_model` (~7.6 MiB), `NativeDeepFilterNet`, 48 kHz mono PCM API, and `libdf.so` for arm64-v8a / armeabi-v7a / x86 / x86_64. |
| `librife.so` + RIFE v4.6 NCNN model | Planned (A.4) | [`nihui/rife-ncnn-vulkan`](https://github.com/nihui/rife-ncnn-vulkan) | ⚠ Self-build with NDK r28+ required | MIT (model: paper authors) | Vulkan-only; arm64-v8a only. ABI split required. |
| OpenCV Android `:opencv:4.10.0+` | Planned (A.3) | opencv.org | ⚠ Verify per release | Apache-2.0 | arm64-only; ~40 MB. Must ABI-split to avoid Play 200 MB base ceiling. |
| `com.google.oboe:oboe:1.9.0` | Planned (A.10) | Maven Central | ⚠ Verify on first integration — arm64 native blob ~700 KB | Apache-2.0 | High-quality sinc resampler for 44.1↔48 kHz mixing. Scaffold + reflection probe + output-frame estimator land 2026-05; runtime path waits for the dep wiring. |

### Still-image HDR metadata tracked outside model downloads

Ultra HDR and ISO 21496-1 gain-map metadata are media-format capabilities, not
ML models, but they affect export confidence and source compatibility. Android
14 introduced `Bitmap.hasGainmap()` for Ultra HDR detection; Android 15 can
encode/decode ISO 21496-1 metadata for JPEG `Bitmap` objects; Android 16 adds
`Gainmap.getGainmapDirection()` so apps can distinguish the legacy SDR-base +
HDR-gainmap direction from the HDR-base + SDR-gainmap direction.

| Variant | Android evidence | ClearCut status |
|---|---|---|
| Ultra HDR v1 / SDR base + HDR gain map | `Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR` means applying the gainmap to an SDR base produces HDR. Older Android 14/15 gainmap images are treated as this direction when the direction API is unavailable. | `MediaImportEngine` records `SourceHdrFormat.ULTRA_HDR_GAIN_MAP`; ExportSheet shows the Ultra HDR source chip through color confidence. |
| ISO 21496-1 v2-style HDR base + SDR gain map | Android 16 `Gainmap.GAINMAP_DIRECTION_HDR_TO_SDR` means the base image is HDR and applying the gainmap produces SDR. | R6.12a records `SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP` during import and persists it through autosave via the existing `hdrFormats` list. |
| HEIC/JPEG still-frame export with gain map | Android's Ultra HDR format guidance says apps that encode/decode JPEG files with gain maps should support both Ultra HDR v1 and ISO 21496-1 metadata and prefer ISO metadata when both are present. | R6.12b remains open. Frame capture currently exports ordinary stills; gain-map HEIC/JPEG writing needs a dedicated encode path and device/API tests. |

## 3. Targeted future models (Round 5 / 6 plans)

These models are *named in the roadmap* but not yet fetched at runtime. They get their own row in §1 when they ship.

| Roadmap ID | Model | Source | Approx. size | Tier policy |
|---|---|---|---|---|
| A.1 / R6.8 | Moonshine v2 Tiny EN (Sherpa-ONNX) | https://github.com/k2-fsa/sherpa-onnx/releases | ~33 MB | Default English ASR; Sherpa-ONNX target. |
| A.1 / R6.8 | Whisper Tiny multilingual (Sherpa-ONNX bundle) | https://github.com/k2-fsa/sherpa-onnx/releases | ~100 MB | Default multilingual fallback. |
| A.1 / R6.8 | Whisper Large V3 Turbo (ONNX) | https://huggingface.co/onnx-community/whisper-large-v3-turbo | ~800 MB (FP16) | Premium-tier multilingual; gated on ≥6 GB RAM + premium-models setting (codified as `SherpaAsrEngine.ModelVariant.WHISPER_LARGE_V3_TURBO_MULTILINGUAL` with `requiresPremiumTier = true`, `minimumRamMb = 6_144`). |
| A.6 | RobustVideoMatting (RVM) | https://github.com/PeterL1n/RobustVideoMatting | ~15 MB | Replaces MediaPipe binary mask for green-screen quality. Upstream code is GPL-3.0, so activation needs an explicit license review and F-Droid posture before any dependency lands. |
| A.5 | Real-ESRGAN x4plus | https://github.com/xinntao/Real-ESRGAN | ~17 MB | Upscaling export pass. |
| A.7 / R6.4 | SAM 2.1 Hiera Tiny ONNX | https://huggingface.co/onnx-community/sam2.1-hiera-tiny-ONNX | ~160 MB model + 96 MB state cache | Premium-tier; ≥6 GB RAM. SAM 3 / SAM 3.1 is a watch item only (R6.4); upstream has no Tiny ONNX export yet. |
| A.7 fallback | MobileSAM ONNX | https://github.com/ChaoningZhang/MobileSAM | ~10 MB model + 24 MB state cache | Small-device fallback. |
| A.8 | Piper TTS voices (via Sherpa-ONNX) | https://github.com/rhasspy/piper | 15–65 MB per voice | Per-voice opt-in download. |
| A.11 | AnimeGANv2 + Fast Neural Style Transfer | https://github.com/TachibanaYoshino/AnimeGANv2 + https://github.com/yakhyo/fast-neural-style-transfer | 6–9 MB per style | Per-style opt-in. |
| A.4 | RIFE v4.6 NCNN model | https://github.com/nihui/rife-ncnn-vulkan | ~7–10 MB | Pairs with `librife.so` (see §2). |
| C.1 | Demucs htdemucs (audio stem separation) | https://github.com/facebookresearch/demucs | ~80 MB | STFT pre/post pipeline is the non-trivial part; see R6.5 note in ROADMAP. |
| C.5 / R6.7 | MADLAD-400 3B (Q4, mobile) | https://huggingface.co/google/madlad400-3b-mt | ~1.5 GB (Q4) | 419 languages; replaces NLLB-200 target. |
| C.5 / R6.7 | Mozilla Bergamot models | https://browser.mt/ | varies (~100 MB per pair) | Per-language-pair download; Firefox offline translation models. |

## 4. Cloud-only providers (consent-gated, never on-device bundled)

Codified by [`GenerativeVideoPolicy.kt`](../app/src/main/java/com/novacut/editor/engine/GenerativeVideoPolicy.kt). Each provider must disclose destination, upload size, retention policy, and collect explicit consent before any cloud call.

| Provider | Use case | Tier | Notes |
|---|---|---|---|
| Wan 2.2 | Text-to-video / image-to-video | Generative (R5.2d) | Server-side only. |
| HunyuanVideo | Text-to-video | Generative (R5.2d) | Server-side only. |
| VideoCrafter2 | Text-to-video | Generative (R5.2d) | Server-side only. |
| MuseTalk / LatentSync | Lip-sync (supersedes Wav2Lip) | Generative (R6.18) | Diffusion-based, GPU-heavy. Licenses CC-BY-NC for MuseTalk. |
| ProPainter | Long-span object removal beyond LaMa | A.12 | Self-hostable; requires server. |

## 5. Anti-Feature posture (F-Droid `NonFreeNet`)

ClearCut's F-Droid build (R5.6b) must declare `NonFreeNet` for any model fetched from a non-free CDN. Today, Hugging Face, GitHub release assets, MediaPipe `storage.googleapis.com`, and Sonatype are all acceptable. Vendor-locked endpoints (Qualcomm AI Hub model assets behind login, Apple model distribution) trigger `NonFreeNet` and must be opt-in or removed from the F-Droid track.

| Source domain | F-Droid status |
|---|---|
| `huggingface.co` | OK (open-license model hosting; verify model license per row) |
| `github.com/.../releases/download/` | OK |
| `storage.googleapis.com/mediapipe-models/` | OK (open redistribution per Google MediaPipe terms) |
| `central.sonatype.com` / Maven Central | OK |
| Qualcomm AI Hub (login-walled) | **NonFreeNet** — gate to opt-in only |

## 6. AI tool activation gate matrix

This matrix is the implementer-facing gate between `AiToolRequirements` and the
model registry above. A tool may show `MODEL_DOWNLOAD_REQUIRED` or `READY` only
when it maps to a §1 active model/dependency row and the runtime/build path has
an explicit checksum behavior. Planned rows in §2/§3 stay
`DEPENDENCY_MISSING` until exact bytes, SHA-256 pins, license posture, delivery
mode, and F-Droid posture are recorded here and in code.

| Tool ID | Registry source | Delivery mode | F-Droid posture | Runtime checksum behavior | Activation status |
|---|---|---|---|---|---|
| `auto_captions` | §1 `whisper.tiny.en.onnx` | Explicit `ModelDownloadManager` download from Hugging Face revision `2575352d61be1bf7225cf8f8b268a4678025fc58` | OK | `checksumRequired = true` for encoder, decoder, and vocab; SHA-256 pins in §1 | `MODEL_DOWNLOAD_REQUIRED` |
| `remove_bg` | §1 `selfie_segmenter.tflite` | Explicit `ModelDownloadManager` download from MediaPipe GCS generation `1683436453600523` | OK | `checksumRequired = true`; SHA-256 pin in §1 | `MODEL_DOWNLOAD_REQUIRED` |
| `object_remove` | §1 `lama_dilated.onnx` | Explicit `ModelDownloadManager` download from the public Qualcomm Hugging Face revision | OK with NOTICE/license review | `checksumRequired = true`; SHA-256 pin in §1 | `MODEL_DOWNLOAD_REQUIRED` |
| `denoise` | §1 `deep_filter_mobile_model` plus §2 `android-deepfilternet:0.0.8` | Bundled Maven AAR dependency | OK | Model-file and AAR SHA-256 pins documented in §1; build/release artifact checks cover packaged bytes | `READY` |
| `ai_background` | §3 RVM planning row | Dependency not bundled | Review required because upstream is GPL-3.0 | Blocked until an exact model export, SHA-256, delivery mode, and redistribution posture are recorded | `DEPENDENCY_MISSING` |
| `ai_stabilize` | §2 OpenCV Android planning row | Dependency not bundled | Review required | Blocked until the selected AAR/source build has SHA-256, 16 KB evidence, and license/NOTICE review | `DEPENDENCY_MISSING` |
| `ai_style_transfer` | §3 AnimeGANv2 / Fast NST planning row | Dependency not bundled | Review required per style | Blocked until each style model has exact source bytes, SHA-256, and redistribution terms | `DEPENDENCY_MISSING` |
| `video_upscale` | §3 Real-ESRGAN planning row | Dependency not bundled | OK once the exact ONNX/NCNN export is pinned | Blocked until the chosen model artifact has a SHA-256 pin and loader wiring | `DEPENDENCY_MISSING` |
| `frame_interp` | §2/§3 RIFE NCNN planning row | Dependency not bundled | OK with native-build review | Blocked until self-built native libs and model files have SHA-256 pins and 16 KB evidence | `DEPENDENCY_MISSING` |
| `tap_segment` | §3 SAM 2.1 / MobileSAM planning rows | Dependency not bundled | OK once exact exports are pinned | Blocked until selected SAM/MobileSAM bytes and state-cache delivery are documented with SHA-256 pins | `DEPENDENCY_MISSING` |
| `stem_separation` | §3 Demucs planning row | Dependency not bundled | OK with model-weight review | Blocked until htdemucs export bytes, SHA-256, and STFT pre/post runtime path are documented | `DEPENDENCY_MISSING` |
| `voice_clone` | §3 Sherpa-ONNX / voice-clone planning row | Dependency not bundled | Review required for voice model terms and consent copy | Blocked until model bytes, SHA-256, consent gate, and runtime enrollment path are documented | `DEPENDENCY_MISSING` |
| `tts_offline` | §3 Piper/Sherpa planning row | Dependency not bundled | Review required per voice because Piper is MIT but eSpeak voice stack has GPL considerations | Blocked until each voice pair has source locator, SHA-256, and license disclosure | `DEPENDENCY_MISSING` |
| `caption_translate` | §3 MADLAD-400 / Bergamot planning rows | Dependency not bundled | OK with license review and per-language download disclosure | Blocked until chosen model/pair bytes, SHA-256, size, and delivery mode are pinned | `DEPENDENCY_MISSING` |
| `generative_video` | §4 cloud-only providers | Cloud only | NonFreeNet / opt-in only | Not applicable to local checksum; provider, destination, upload size, and retention disclosure are required before any call | `CLOUD_OPT_IN` |
| `lip_sync` | §4 cloud-only providers | Cloud only | NonFreeNet / opt-in only | Not applicable to local checksum; provider/license review and explicit upload consent are required before any call | `CLOUD_OPT_IN` |

## 7. Reproducible build pin requirements

For reproducibility (R5.6c), every entry above with a download URL must also record:
- **Source URL** (column 2)
- **SHA-256 of the exact bytes we will load** (column 4)
- **Approximate size** for the user disclosure sheet (column 5)
- **License** for the LICENSE/NOTICE shipping requirement (column 6)

As of the 2026-06-04 v3.74.38 pass, every §1 active model row has an exact source locator and SHA-256 pin that is also wired through `ModelDownloadManager.ModelFile(checksumRequired = true)` or a documented build-artifact checksum path. `app/src/test/java/com/novacut/editor/engine/ModelRegistryDocumentationTest.kt` fails the JVM test suite if §1 reintroduces `TBD` checksum placeholders, malformed SHA-256 values, floating active URLs, or an AI tool gate matrix that omits a registered tool.

Future rows in §3 remain planning targets only. They must move into §1 with a public source locator, exact SHA-256, size disclosure, license posture, PAD/F-Droid decision, and runtime checksum wiring before any corresponding Tier A engine activates.

# NovaCut — Model Registry

Authoritative list of every ML model and native AAR that NovaCut may fetch or bundle. Pairs with the [ModelDownloadManager](../app/src/main/java/com/novacut/editor/engine/ModelDownloadManager.kt) and is the single reference for F-Droid `NonFreeNet` audits, license review, reproducible build verification, and 16 KB page-size compliance tracking.

**Last refresh:** 2026-05-16 · See [ROADMAP.md](../ROADMAP.md) Round 5 §R5.6, §R5.9 and Round 6 §R6.1, §R6.2, §R6.6, §R6.8 for the policy that gates each entry.

---

## 1. Active models (shipped or scaffolded with download path)

| ID | File | Source URL | SHA-256 pinned | Size | License | NDK aligned for 16 KB? | Used by |
|---|---|---|---|---|---|---|---|
| `whisper.tiny.en.onnx` | `model.onnx`, `vocab.json` | `https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/onnx` + `/vocab.json` | ⚠ TBD — record SHA-256 in `ModelDownloadManager.FileSpec` on next bump | ~75 MB | MIT (model: Apache-2.0 by OpenAI) | n/a (pure ONNX, no native) | [`WhisperEngine.kt`](../app/src/main/java/com/novacut/editor/engine/whisper/WhisperEngine.kt) |
| `selfie_segmenter.tflite` | `selfie_segmenter.tflite` | `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite` | `191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b` | ~256 KB | Apache-2.0 (Google MediaPipe) | n/a (TFLite via MediaPipe AAR; AAR alignment tracked below) | [`SegmentationEngine.kt`](../app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationEngine.kt) |
| `lama_dilated.onnx` | `lama_dilated.onnx` | `https://huggingface.co/novacut/lama-dilated-onnx/resolve/main/lama_dilated.onnx` (mirror of [advimman/lama](https://github.com/advimman/lama)) | ⚠ TBD — required before A.12 cloud variant ships | ~174 MB | Apache-2.0 | n/a (ONNX) | [`InpaintingEngine.kt`](../app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt) |

## 2. Native AARs (bundled or planned) — 16 KB compliance gates

Every native AAR shipped with NovaCut must be 16 KB page-size aligned. Google Play **blocks** uploads of non-compliant native libs at `targetSdk = 36` (Android 16) since 2025-11-01. Verify with `python check_elf_alignment.py app/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a/*.so` before each release. See [ROADMAP.md R6.1](../ROADMAP.md#r61--16-kb-page-size-compliance-play-store-gate).

| AAR | Status | Source | 16 KB aligned? | License | Notes |
|---|---|---|---|---|---|
| `onnxruntime-android:1.17.0` | Bundled today | Microsoft / Maven Central | ⚠ Verify on next ORT bump — 1.17.x predates the Play gate. ORT 1.18.0+ ships NDK r27+ builds. | MIT | Track NDK version of release binary; bump to ≥1.18.0 when compatibility is verified. |
| `mediapipe-tasks-vision:0.10.14` | Bundled today | Google / Maven Central | ⚠ Verify — pinned 2024 release; MediaPipe began shipping 16 KB-aligned builds in late 2025. | Apache-2.0 | Vision task bundle includes embedded TFLite runtime. |
| `lottie-compose:6.6.2` | Bundled today | Airbnb / Maven Central | n/a (pure Kotlin) | Apache-2.0 | See R6.16 for `lottie-compose:7.x` bump (state-machines + dotLottie). |
| `media3-effect-lottie:1.10.x` | Planned (R6.10a) | androidx.media3 / Maven Central | n/a (pure Kotlin) | Apache-2.0 | Replaces internal `LottieOverlayEffect`. |
| `sherpa-onnx-1.13.2.aar` | Targeted (A.1) | [GitHub release asset](https://github.com/k2-fsa/sherpa-onnx/releases) | ⚠ Verify per AAR release — Sherpa-ONNX 1.12.28+ targets NDK r27. | Apache-2.0 | Distributed via GitHub release assets, not Maven Central. Must be vendored into `app/libs/` or fetched via PAD. |
| `ffmpeg-kit-16kb:6.1.1` | Pinned target (R6.5a, A.9) | `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` (Maven Central) | ✅ Built with NDK r27d for 16 KB alignment | GPL-3 (Full-GPL build); LGPL-2.1 variant available | NovaCut is MIT-licensed; the FFmpeg license addendum must be shipped with release artifacts. See [LICENSE](../LICENSE). |
| `deepfilternet-android` | Planned (A.2) | `com.kaleyra:deepfilternet-android` (Sonatype) | ⚠ Verify on first integration | LGPL-3.0 | DeepFilterNet 3 model targeted (R6.6a). |
| `librife.so` + RIFE v4.6 NCNN model | Planned (A.4) | [`nihui/rife-ncnn-vulkan`](https://github.com/nihui/rife-ncnn-vulkan) | ⚠ Self-build with NDK r28+ required | MIT (model: paper authors) | Vulkan-only; arm64-v8a only. ABI split required. |
| OpenCV Android `:opencv:4.10.0+` | Planned (A.3) | opencv.org | ⚠ Verify per release | Apache-2.0 | arm64-only; ~40 MB. Must ABI-split to avoid Play 200 MB base ceiling. |

## 3. Targeted future models (Round 5 / 6 plans)

These models are *named in the roadmap* but not yet fetched at runtime. They get their own row in §1 when they ship.

| Roadmap ID | Model | Source | Approx. size | Tier policy |
|---|---|---|---|---|
| A.1 / R6.8 | Moonshine v2 Tiny EN (Sherpa-ONNX) | https://github.com/k2-fsa/sherpa-onnx/releases | ~33 MB | Default English ASR; Sherpa-ONNX target. |
| A.1 / R6.8 | Whisper Tiny multilingual (Sherpa-ONNX bundle) | https://github.com/k2-fsa/sherpa-onnx/releases | ~100 MB | Default multilingual fallback. |
| A.1 / R6.8 | Whisper Large V3 Turbo (ONNX) | https://huggingface.co/onnx-community/whisper-large-v3-turbo | ~800 MB (FP16) | Premium-tier multilingual; gated on ≥6 GB RAM + premium-models setting. |
| A.6 | RobustVideoMatting (RVM) | https://github.com/PeterL1n/RobustVideoMatting | ~15 MB | Replaces MediaPipe binary mask for green-screen quality. |
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

NovaCut's F-Droid build (R5.6b) must declare `NonFreeNet` for any model fetched from a non-free CDN. Today, Hugging Face, GitHub release assets, MediaPipe `storage.googleapis.com`, and Sonatype are all acceptable. Vendor-locked endpoints (Qualcomm AI Hub model assets behind login, Apple model distribution) trigger `NonFreeNet` and must be opt-in or removed from the F-Droid track.

| Source domain | F-Droid status |
|---|---|
| `huggingface.co` | OK (open-license model hosting; verify model license per row) |
| `github.com/.../releases/download/` | OK |
| `storage.googleapis.com/mediapipe-models/` | OK (open redistribution per Google MediaPipe terms) |
| `central.sonatype.com` / Maven Central | OK |
| Qualcomm AI Hub (login-walled) | **NonFreeNet** — gate to opt-in only |

## 6. Reproducible build pin requirements

For reproducibility (R5.6c), every entry above with a download URL must also record:
- **Source URL** (column 2)
- **SHA-256 of the exact bytes we will load** (column 4)
- **Approximate size** for the user disclosure sheet (column 5)
- **License** for the LICENSE/NOTICE shipping requirement (column 6)

Rows currently marked `⚠ TBD` for SHA-256 are blocking-but-not-shipping items: the engine downloads them at runtime today without a checksum pin, which violates R5.9b ("Model checksum enforcement at runtime"). Each row must record the SHA-256 before its corresponding Tier A engine activates.


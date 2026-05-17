# Dataset, Model, and Integration Review - 2026-05-17

## Scope

NovaCut has a meaningful data/model/integration surface: ASR, captions, translation, denoise, matting, segmentation, frame interpolation, upscaling, templates, stock assets, export/interchange, and optional cloud providers. This file focuses on what should be activated, deferred, measured, or governed.

## Current Local Model Governance

Evidence:

- `docs/models.md` is the canonical model registry.
- `ModelDownloadManager` exists in source.
- The roadmap repeatedly ties large models to explicit downloads, Play Asset Delivery, and F-Droid-compatible variants.

Strengths:

- The project already treats models as governed artifacts, not hidden assets.
- Licensing, source URL, delivery channel, and privacy posture are explicit concerns.
- The local-first stance is coherent with the product positioning.

Current gap:

- Some model rows still contain unresolved checksum/source/activation details. No new model activation should proceed until checksums and delivery policy are closed.

## Candidate Model Families

| Area | Candidates | Local State | Recommendation |
|---|---|---|---|
| ASR / transcription | Moonshine, Whisper, Sherpa-ONNX, whisper.cpp references | ASR/caption roadmap and model policy exist | Add WER/speed/device-tier evaluation before expanding model options. |
| Caption translation | MADLAD-400, Bergamot-style mobile translation | Caption translation data model exists | Treat as P1 after model registry closure and phrase-level evaluation fixtures. |
| Noise reduction | DeepFilterNet 3, AndroidDeepFilterNet reference | A.2 activation now pins `io.github.kaleyravideo:android-deepfilternet:0.0.8`; `NoiseReductionEngine` has an Android-only native path and pass-through fallback | Next work is real-device audio QA, PESQ/STOI or proxy fixtures, and improved SNR/profile reporting. |
| Stem separation | Demucs | Roadmap recognizes high complexity | Defer until audio pre/post pipeline and package size budget are clear. |
| Matting | Robust Video Matting | Model roadmap exists | Defer until PAD and evaluation harness. |
| Segmentation | SAM 2.1 Hiera Tiny, MediaPipe Image Segmenter | Tap-to-segment roadmap exists | Keep SAM 2.1 as watch/activation target; use MediaPipe where practical for mobile baseline. |
| Frame interpolation | RIFE / NCNN / Vulkan | Roadmap item exists | Later. Native/Vulkan/device-tier risk is high. |
| Upscaling | Real-ESRGAN | Roadmap item exists | Later. Large model and performance risk. |
| Animated title templates | Media3 `media3-effect-lottie`, Airbnb Lottie | R7.5 / R6.10a now uses the official Media3 Lottie renderer for eligible export overlays | Add committed template fixtures or generated golden-frame tests before deleting the custom HDR/looping fallback shader. |
| Text/prompt assistant | Gemini Nano / ML Kit GenAI Prompt API | Watch item | Keep optional and local-first; no core dependency on device availability. |

## Integration Candidates

| Integration | Value | Risks | Recommendation |
|---|---|---|---|
| Play Asset Delivery | Keeps base app size manageable for large models | Play-only; F-Droid divergence | Necessary before more large model bundles. |
| F-Droid model flavor | Preserves open/offline distribution posture | Requires alternative download/build policy | Keep explicit in `docs/models.md`. |
| Stock asset providers | Expands media picker | Network, provider licensing, privacy | Defer until provider consent and license display are designed. |
| Cloud AI providers | Enables heavy generative video/lip-sync | Media upload privacy, provider cost, account state | Explicit opt-in only; never default. |
| OpenTimelineIO/FCPXML | Pro interoperability | Parser fidelity and project mapping complexity | Good P1/P2 after export pipeline stabilizes. |
| Gyroflow sidecar/project import | Strong stabilization capability without full reimplementation | File format and sensor sync complexity | Prefer import/reference path before custom gyro engine. |
| FFmpeg 16 KB package | Unlocks export features | License, native compliance, fork trust | P0/P1 decision gate. |
| Media3 Compose Material3 player widgets | Could reduce bespoke playback UI | Full `Player` / `ProgressSlider` path is player-timeline based and does not cover editor gap/still/transform/scope states | R6.10b keeps custom `PreviewPanel`; revisit only targeted `ContentFrame`, `PlayerSurface`, or individual button adoption. |
| Media3 inspector-frame | HDR/effect-aware frame extraction, thumbnails, and future still export | Unneeded dependency for current small SDR thumbnail strips; must use new split module/import path | R6.10c keeps current cached thumbnails on `MediaMetadataRetriever`; migrate only HDR/effect/custom-decoder paths. |
| Media3 ProgressSlider | Could reduce bespoke progress UI | Player-position control with internal seek does not match edited-project timeline controls | R6.10d keeps custom timeline ruler and existing external Material3 mini-player slider. |

## Evaluation Harness Opportunities

| Area | Metric/Fixture | Why |
|---|---|---|
| ASR | WER/CER on short clips, noisy clips, multilingual clips | Prevents anecdotal model choice. |
| Caption timing | Word-boundary alignment error, subtitle overlap checks | Protects karaoke/translation/edit UX. |
| Denoise | PESQ/STOI or proxy metrics plus listening fixtures | DeepFilterNet activation needs objective and subjective checks. |
| Segmentation/matting | IoU, boundary F-score, matte temporal stability | Prevents impressive still-frame demos that fail video use. |
| Frame interpolation | Temporal artifact review, dropped-frame count, speed by device tier | RIFE-like features are device-sensitive. |
| Upscaling | PSNR/SSIM where useful plus visual regression frames | Avoids sharpening artifacts and hallucinated detail. |
| Export/render | Golden frame diffs, audio duration drift, container metadata | Needed for FFmpeg, Lottie, blend mode, HDR changes. |
| Lottie overlays | Finite-window title frames, HDR export smoke, over-long title windows | Media3 renderer is active only where parity holds; fallback conditions need visual regression coverage. |
| Model downloads | Checksum failure, interrupted download, resume, offline state | Makes model management trustworthy. |

## Packaging and Distribution Notes

Model activation should follow this sequence:

1. Add exact model source URL, license, size, SHA-256, and version.
2. Decide delivery channel: bundled, Play Asset Delivery, explicit download, F-Droid excluded, or cloud-only.
3. Add checksum validation tests.
4. Add UI state for missing, downloading, failed, verified, and disabled models.
5. Add at least one evaluation fixture before exposing the feature as active.

Avoid:

- Bundling unpinned model binaries.
- Quiet network downloads.
- Cloud fallbacks that upload media without explicit confirmation.
- Treating benchmark results from flagship devices as representative of all supported Android hardware.

## Recommended Next Model/Integration Work

1. Close `docs/models.md` checksum/source/license gaps.
2. Add model registry contract tests.
3. Add model download failure/retry UI states.
4. Choose PAD/F-Droid channel rules for each model family.
5. Add DeepFilterNet real-device evaluation fixtures now that A.2 is wired.
6. Add ASR evaluation fixtures before expanding from existing transcription paths.
7. Add Lottie template/golden-frame fixtures before removing the custom shader fallback.
8. Keep SAM/RIFE/Real-ESRGAN/RVM as later device-tier work.

## Why This File Is Not Thin

NovaCut has significant AI/ML/search/integration relevance. The project already contains model-management architecture, ML dependency choices, caption/transcription features, media import/export integrations, and planned cloud/on-device feature paths. The main need is governance, evaluation, and user-visible trust work before additional model activation.

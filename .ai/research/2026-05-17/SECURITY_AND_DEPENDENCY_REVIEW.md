# Security and Dependency Review - 2026-05-17

## Scope

This review covers dependency freshness, release-readiness risks, native-library platform gates, model/download integrity, privacy/security posture, and hardening ideas. It is not a full vulnerability audit and did not include dynamic penetration testing.

## Dependency Freshness Snapshot

Current versions come from `gradle/libs.versions.toml`. Latest/release metadata was fetched from Maven metadata endpoints on 2026-05-17.

| Area | Current | Metadata Observation | Recommendation |
|---|---:|---|---|
| Android Gradle Plugin | 8.7.3 | Latest metadata points to a 9.3.0 alpha line | Do not blind-bump. Create a toolchain branch when AGP 9 work is intentional. |
| Kotlin | 2.1.0 | Latest metadata points to a 2.4.0 RC line | Do not blind-bump. Keep with KSP/AGP compatibility. |
| KSP | 2.1.0-1.0.29 | Coupled to Kotlin | Update only with Kotlin. |
| Compose BOM | 2024.12.01 | Newer 2026.05.00 metadata available | Candidate for dependency train after Compose compiler/Kotlin compatibility review. |
| Media3 | 1.10.1 | Metadata shows 1.10.1 current; `media3-effect-lottie` metadata latest/release is 1.10.1 on Google Maven | Keep current; R7.5 now wires `media3-effect-lottie` for eligible Lottie overlays while retaining custom fallback for HDR/looping edge cases. |
| Room | 2.6.1 | Newer 2.8.4 metadata available | Candidate for dependency train with migration tests. |
| WorkManager | 2.10.0 | Newer 2.11.2 metadata available | Candidate for dependency train; relevant to model/background jobs. |
| Hilt | 2.53.1 | Newer 2.59.2 metadata available | Candidate for dependency train with KSP/Kotlin review. |
| ONNX Runtime Android | 1.17.0 | Newer 1.26.0 metadata available | High-value but risky; verify native page size, ABI, model output parity, and package size. |
| OkHttp | 4.12.0 | Newer 5.3.2 metadata available | Major-version migration; defer until API and transitive impacts are reviewed. |
| Lottie Compose | 6.6.2 | Newer 6.7.1 metadata available | Low/medium-risk candidate, especially with Media3 Lottie spike. |

## Security and Release Risks

### Native 16 KB page-size compliance

Evidence:

- `targetSdk = 36` in `app/build.gradle.kts`.
- Android official guidance requires native libraries to support 16 KB page sizes for current Android release targets.
- Current and planned native-heavy libraries include ONNX Runtime, MediaPipe Tasks Vision, FFmpeg, possible NCNN/Vulkan/RIFE, Sherpa-ONNX, OpenCV, and Oboe.

Risk:

- A non-compliant native library can block Play upload or fail on devices with 16 KB pages.

Recommendations:

1. Add a repeatable Gradle or PowerShell verification script that inspects packaged `.so` files.
2. Make the check part of release builds and CI artifacts.
3. Record native-library page-size evidence in release notes before publishing.

Continuation update (2026-05-17):

- A.2 DeepFilterNet activation uses `io.github.kaleyravideo:android-deepfilternet:0.0.8` from Maven Central rather than the older roadmap coordinates.
- AAR SHA-256: `6566a208fe476a71b20558f92d93a1c0db49fd93b36fcdaea17a10260189d167`; Maven metadata latest/release is `0.0.8`; GitHub tag `v0.0.8` maps to commit `42ea9b786babf7d67008a81cf25257b4735e4127`.
- The bundled `deep_filter_mobile_model` is 7,984,565 bytes with SHA-256 `5600b6857117ecc7cf460b8ec4841963bfa6d718921d424d42dea5d3d37a8c32`.
- Preflight `scripts/check_16kb_alignment.py` over extracted arm64-v8a and x86_64 `libdf.so` reported OK for both; final debug APK verification after integration reported 32 OK native libs, 40 skipped 32-bit libs, and 0 misaligned libs. Repeat the full APK/AAB check after every native dependency change.

### Media3 Lottie module adoption

Evidence:

- AndroidX Media3 release notes list `androidx.media3:media3-effect-lottie:1.10.1` for applying Lottie effects to video frames.
- Google Maven metadata for `androidx.media3:media3-effect-lottie` reports latest/release `1.10.1`.
- The resolved 1.10.1 AAR is pure Kotlin/Java with no native libraries; AAR SHA-256 is `83b26f6f25e785b949263fc52cb7c0fb5f0e371445fa1d7b9a0ed0b71c05e69d`.
- Source inspection of `LottieOverlay` 1.10.1 shows support for a `LottieProvider`, supplied `LottieDrawable`, `StaticOverlaySettings`, playback speed, and per-call `getOverlaySettings(long)`.

Risk and mitigation:

- Under HDR input, Media3 `OverlayEffect` treats non-text `BitmapOverlay` overlays as Ultra HDR and requires gainmaps. NovaCut therefore keeps the existing custom `LottieOverlayEffect` shader for HDR exports.
- Media3's `LottieOverlay` loops animation progress, while NovaCut's existing renderer holds the final frame for title windows longer than the composition duration. `VideoEngine` keeps the custom shader for over-long windows and uses the official module only where parity holds.
- No native-library alignment risk is introduced by this module.

### Media3 Compose Material3 Player evaluation

Evidence:

- Android Developers documents `media3-ui-compose-material3` as providing a Material3 `Player`, playback buttons, `ProgressSlider`, and position/duration text, with internal state management.
- Android Developers documents lower-level `media3-ui-compose` primitives `ContentFrame` and `PlayerSurface`.
- The `ProgressSlider` API reference says the slider displays player progress and performs the underlying player seek internally before the finish callback.
- Google Maven metadata for `androidx.media3:media3-ui-compose-material3` reports latest/release `1.10.1`; the resolved AAR is 280,813 bytes with SHA-256 `0e0789cef85d948f924c0cec365021a56f6cc63b8c9888cacd05357f83e00112`.
- Local `PreviewPanel` requirements include edited-timeline seeking, gap recovery, still-image fallback, transform gesture callbacks, scopes, and custom chrome.

Risk and mitigation:

- Full `Player` replacement would regress NovaCut's trim-aware preview contract because Media3's Material3 controls are player-timeline widgets.
- `ProgressSlider` should not replace timeline seeking until Media3 exposes externally controlled progress without an internal `Player.seekTo` call.
- No dependency was added in R6.10b. The decision is codified in `PreviewPanelMedia3ComposePolicy` and `docs/preview-media3-compose.md`; revisit targeted `ContentFrame`, `PlayerSurface`, or individual button adoption only behind focused UI tests.

### FFmpeg distribution and license posture

Evidence:

- Roadmap rows A.9/B.3/B.5 depend on FFmpeg-style capabilities.
- The original `ffmpeg-kit` upstream is archived.
- The roadmap currently points toward a 16 KB fork/package.

Risks:

- GPL/LGPL flavor affects Play/F-Droid/default-build posture.
- Native ABI coverage and 16 KB alignment must be verified.
- Fork maintenance and reproducible build evidence matter because FFmpeg is a core export dependency.

Recommendations:

1. Decide exact Maven coordinate and version.
2. Document GPL/LGPL flavor and which app channel uses it.
3. Verify ABI coverage and page-size alignment from the actual AAR.
4. Add export tests before enabling concat demuxer, libass burn-in, reverse export, or loudnorm.

### Model download integrity

Evidence:

- `docs/models.md` contains `SHA TBD` rows.
- `ModelDownloadManager` exists and the product emphasizes explicit model downloads.

Risks:

- Unpinned model binaries are supply-chain risk.
- A model checksum mismatch must fail closed and give the user a recoverable path.
- F-Droid and Play delivery differ; model policy needs channel-specific clarity.

Recommendations:

1. Require SHA-256 for every downloadable model before activation.
2. Add model registry contract tests covering SHA, size, license, source URL, delivery channel, and F-Droid status.
3. Surface checksum/download failure states in UI.
4. Keep cloud/model downloads opt-in and explicit.

### Diagnostic export privacy

Evidence:

- `DiagnosticsExportEngine` exists.
- Roadmap item R5.5d calls for local-only diagnostic export.

Risks:

- Diagnostic bundles can accidentally leak absolute media paths, filenames, account paths, or device identifiers.

Recommendations:

1. Redact media paths by default.
2. Preview bundle contents before share/save where practical.
3. Make local-only behavior explicit in Settings.
4. Add tests for redaction and no raw URI/path leakage.

### Release signing fallback

Evidence:

- `app/build.gradle.kts` contains release signing behavior that can fall back to debug signing when no keystore is configured.

Risk:

- A release artifact could be built with the wrong signing posture if the build environment is not explicit.

Recommendations:

1. Keep debug fallback only for local/development builds.
2. Add a release preflight that fails when a production release is requested without explicit signing env vars.
3. Document signing mode in the release checklist.

### Network and cloud features

Evidence:

- Roadmap includes cloud/provider/model/integration ideas.
- Product posture is privacy-first and local-first.

Risks:

- Network features can erode user trust if they are not explicit and reversible.
- Stock assets, model downloads, cloud AI, sync, and telemetry have different consent and data-handling requirements.

Recommendations:

1. Use explicit per-provider toggles.
2. Keep media upload disabled unless the user chooses a cloud tool and confirms scope.
3. Separate aggregate crash/usage telemetry from model downloads and asset-provider network access.
4. Keep F-Droid-compatible variants free of non-free network requirements by default.

## Hardening Backlog

| Priority | Item | Why |
|---|---|---|
| P0 | Native 16 KB verification script | Release blocker with current target SDK. |
| P0 | Model registry contract tests | Prevents unpinned model supply-chain drift. |
| P0 | Diagnostic ZIP redaction tests | Prevents local path/media metadata leakage. |
| P1 | Release signing preflight | Avoids accidental debug-signed releases. |
| P1 | Dependency train branch with rollback notes | Reduces broad update risk. |
| P1 | Export matrix smoke tests | Codec/container/native-library changes need evidence. |
| P2 | Optional Sentry/Glean privacy policy and toggles | Useful only with strict opt-in and redaction. |

## Review Limitations

- No full dependency vulnerability scanner was run in this pass.
- No emulator/manual UI test was run.
- Maven metadata identifies newer versions but does not replace release-note review.
- FFmpeg fork trust/reproducibility still needs a dedicated implementation spike.

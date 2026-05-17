# Research Log - 2026-05-17

## Objective

Produce an evidence-backed repo understanding and roadmap refresh that can survive future sessions. The run intentionally combined local source reconnaissance, instruction/memory reconciliation, dependency metadata checks, competitor research, and model/integration research.

## Local Reconnaissance

Commands and tools used:

- `rg --files`
- `rg` searches for instruction files, TODO/stub patterns, dependency usage, and high-value engine classes.
- `git status --short --branch`
- `git log -10 --oneline --decorate`
- `git remote -v`
- `git branch -vv`
- `git tag --sort=-creatordate`
- `git ls-files --stage`
- PowerShell `Get-Content`, `Select-String`, `Measure-Object`, and filesystem checks.

Notable local findings:

- `master` is ahead of `origin/master` by 33 commits.
- `v3.74.9` / `versionCode 146` is the live version.
- `AGENTS.md`, `CLAUDE.md`, and `.claude/` are local ignored instruction files.
- `local.properties` had a stale SDK path from another workspace and required a local-only correction.
- The repo has many scaffolded engines with availability gates and tests, making completion/integration work higher leverage than adding new stubs.

## External Research Passes

### Pass 1 - Platform and release gates

Queries:

- Android 16 16 KB page size native libraries.
- Google Play target SDK Android 16 requirements.
- Android NNAPI deprecated LiteRT migration.

Selected sources:

- Android page-size guidance.
- Google Play target SDK requirements.
- LiteRT docs.

Result:

- 16 KB native-library compliance is a release gate because the repo targets SDK 36 and includes native ML/video dependencies.
- NNAPI references in future engine docs should be updated toward LiteRT where TFLite-backed engines are planned.

### Pass 2 - Media3 and dependency release streams

Queries and fetches:

- AndroidX Media3 release notes.
- Media3 Transformer composition.
- Maven metadata for AGP, Kotlin, Media3, Compose BOM, Room, WorkManager, Hilt, ONNX Runtime, OkHttp, Lottie.

Result:

- Media3 1.10.1 is current in the repo.
- Compose BOM, Room, WorkManager, Hilt, ONNX Runtime, OkHttp, and Lottie have newer release trains.
- AGP/Kotlin metadata points at pre-release latest lines; those should be handled on an explicit toolchain branch rather than opportunistically.

### Pass 3 - Commercial editor comparison

Queries:

- CapCut features AI video editor templates mobile desktop.
- DaVinci Resolve 20 AI IntelliScript SmartSwitch.
- PowerDirector Android AI video editor features.
- LumaFusion Android professional video editor features.
- KineMaster mobile editor features templates asset store.
- VN video editor mobile features.
- Adobe Premiere Rush end of life.
- Clipchamp mobile app retirement.

Result:

- Commercial competitors converge around templates, fast social export, AI auto-edit, subtitles, object/person effects, asset stores, multi-platform projects, and recovery/support workflows.
- NovaCut should compete through local-first privacy, transparent model downloads, deterministic exports, and pro workflow depth rather than cloning cloud-first asset marketplaces.

### Pass 4 - Open-source editor comparison

Queries and API checks:

- OpenCut GitHub.
- Android open source video editor Media3 Compose.
- OpenShot, Kdenlive, Shotcut, LosslessCut, OpenTimelineIO, Gyroflow, gl-transitions.

Result:

- OpenCut is a major open-source CapCut-positioned project but is not a direct Android-native Compose app.
- `devhyper/open-video-editor` is the closest direct Android open-source comparator.
- Desktop editors remain the best source for pro NLE concepts: proxy workflows, keyframes, multicam, motion tracking, subtitle export, interchange, and effect graph expectations.
- LosslessCut is the strongest reference for stream-copy and smart-render user expectations.

### Pass 5 - Model, dataset, and integration opportunities

Queries:

- sherpa-onnx Android releases.
- DeepFilterNet Android.
- SAM 2.1 ONNX mobile.
- MADLAD-400 Bergamot mobile translation.
- ONNX Runtime Android latest.
- MediaPipe Android image segmenter.

Result:

- Existing `docs/models.md` has the right model-governance shape but needs checksum closure.
- On-device ASR/noise/masking/translation candidates are credible, but the immediate blocker is verification, packaging, and visible model-management UX.
- Evaluation harnesses should be added before activating more heavyweight model paths.

## Failed or Thin Searches

- `DoubleClips/DoubleClips-mobile` GitHub lookup failed or was not publicly resolvable during this run.
- No stronger direct Android-native open-source editor than `devhyper/open-video-editor` emerged in the targeted pass.
- Some commercial mobile app feature pages are marketing-heavy and do not provide implementation-level detail; they were used only for feature trend and positioning, not architectural claims.
- Clipchamp mobile retirement source discovery was thinner than Premiere Rush. Treat it as a market-signal category unless an official support URL is pinned in a later pass.

## Saturation Test

Research was considered saturated when additional searches produced repeated source classes:

- Official Android/Media3 docs for platform gates.
- Maven metadata for current dependency versions.
- Same commercial clusters: AI auto-edit, templates, captions, cloud/social workflows.
- Same OSS clusters: desktop NLEs, smart rendering, interchange, stabilization, transition registries.
- Same model clusters: ASR, segmentation/matting, denoise, translation, frame interpolation/upscale.

Remaining useful future research would be implementation-specific rather than broad:

- Exact FFmpeg 16 KB Maven coordinate, license flavor, ABI coverage, and reproducible build status.
- Exact ONNX Runtime Android 1.26 migration notes and native page-size status.
- Device-level benchmark data for target ML models on midrange Android hardware.
- Play Asset Delivery and F-Droid split-channel implementation examples.

## Continuation Research Notes - Media3 Lottie R7.5

Targeted follow-up after A.2 queried:

- AndroidX Media3 release notes for `media3-effect-lottie`.
- Android Developers API reference for `androidx.media3.effect.lottie.LottieOverlay`,
  `LottieOverlay.Builder`, and `LottieOverlay.LottieProvider`.
- Google Maven metadata, POM, AAR, and sources for
  `androidx.media3:media3-effect-lottie:1.10.1`.
- Local `LottieOverlayEffect`, `LottieTemplateEngine`, `VideoEngine`, and
  `docs/models.md`.

Result:

- The artifact is published on Google Maven, not Maven Central, with
  latest/release `1.10.1`.
- The AAR is pure Kotlin/Java and carries no native 16 KB alignment risk.
- The official renderer supports supplied `LottieDrawable`, composition provider,
  static settings, and speed, so NovaCut can preserve text substitution and
  full-frame sizing through an adapter.
- The official renderer loops progress and Media3 HDR overlay handling treats
  non-text bitmaps as Ultra HDR/gainmap overlays, so the custom shader remains a
  deliberate fallback for HDR exports and over-long title windows.
  The repo has no committed Lottie template assets, so pixel-golden parity should
  be added when fixtures exist.

## Continuation Research Notes - Media3 Compose Player R6.10b

Targeted follow-up after R6.10a queried:

- Android Developers Media3 Compose UI overview.
- Android Developers Media3 Material3 Compose guide.
- Android Developers `ProgressSlider` API reference.
- Android Developers `androidx.media3.ui.compose` package summary.
- Google Maven metadata and AAR bytes for
  `androidx.media3:media3-ui-compose-material3:1.10.1`.
- Local `PreviewPanel`, `MiniPlayerBar`, and editor call sites.

Result:

- `media3-ui-compose-material3` latest/release is `1.10.1` as of Google Maven
  metadata lastUpdated `20260512123518`; the AAR SHA-256 is
  `0e0789cef85d948f924c0cec365021a56f6cc63b8c9888cacd05357f83e00112`.
- The module is useful for ordinary playback surfaces and controls, but its
  Material3 `Player` / `ProgressSlider` path is player-timeline based.
- `ProgressSlider` performs the underlying player seek internally before its
  finish callback, which conflicts with NovaCut's edited-timeline seek and gap
  recovery path.
- The local preview is not just playback chrome: it owns gap recovery,
  still-image fallback, transform gestures, scopes, and Mocha editor chrome.
- R6.10b is therefore closed as a documented non-adoption for full `Player`
  replacement. Future targeted adoption can revisit lower-level `ContentFrame`,
  `PlayerSurface`, or individual playback buttons with focused preview tests.

## Continuation Research Notes - Media3 Inspector Frame R6.10c

Targeted follow-up after R6.10b queried:

- Android Developers Media3 Inspector docs.
- AndroidX Media3 release notes for the 1.10 module/import split.
- Google Maven metadata, AAR bytes, and sources JAR for
  `androidx.media3:media3-inspector-frame:1.10.1`.
- Local `VideoEngine.extractThumbnail`, `extractThumbnailStrip`,
  `extractFrameToFile`, and callers.
- Local source tree for old `FrameExtractor` and `ExperimentalFrameExtractor`
  imports.

Result:

- `media3-inspector-frame` latest/release is `1.10.1` as of Google Maven
  metadata lastUpdated `20260512123518`; the AAR SHA-256 is
  `ded4a5275a5f977afaa3fb4b1b933667629e2526efbfb94b4bcf2b96fc20e2a0`, and
  the AAR has no native library entries.
- Source inspection confirms the new package path is
  `androidx.media3.inspector.frame.FrameExtractor`, with `setEffects`,
  `setMediaCodecSelector`, `setExtractHdrFrames`, `getFrame`, and
  `getThumbnail`.
- NovaCut has no current Media3 `FrameExtractor` imports and no inspector-frame
  dependency, so there is no broken migration to repair.
- R6.10c closes as a policy/audit item: current small cached SDR thumbnails and
  JPEG freeze frames stay on `MediaMetadataRetriever`; future HDR/effect-aware
  or custom-decoder frame extraction should use the split inspector-frame
  module.

## Continuation Research Notes - Media3 ProgressSlider R6.10d

Targeted follow-up after R6.10c queried:

- Android Developers `ProgressSlider` API reference.
- Android Developers Media3 Material3 Compose guide.
- Local `Timeline`, `TimelineOverviewBar`, `MiniPlayerBar`, and
  `PreviewPanelMedia3ComposePolicy` code.

Result:

- `ProgressSlider` remains a player-position control and performs player seek
  internally before its finish callback.
- NovaCut's timeline ruler is not a cosmetic progress bar; it maps edited
  project time through scroll offset, zoom, markers, snap targets, clip hit
  areas, and scrub lifecycle callbacks.
- `MiniPlayerBar` already uses a standard Material3 `Slider` with external
  `playheadMs / totalDurationMs` state and NovaCut's `onSeek(Long)` callback.
- R6.10d closes as a tested non-adoption. Revisit only if Media3 exposes an
  externally controlled project-timeline progress component.

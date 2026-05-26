# NovaCut — Research and Feature Plan (2026-05-25)

> **Companion to [ROADMAP.md](ROADMAP.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), and [.ai/research/2026-05-17/](.ai/research/2026-05-17/).** This pass is dated 2026-05-25 — 8 days after the existing Round 7/8 consolidation. It does **not** re-derive the Forward View. It records what is now actually shipped vs. still scaffolded after the post-Round-7 commit batch, then surfaces opportunities the existing planning has either not yet captured or has captured as a one-line entry that needs more concrete shape.
>
> Treat this file as: *what would a coding agent need on top of `ROADMAP.md` to safely pick the next 5 items and ship them without redoing the research?*

## Executive Summary

NovaCut at `v3.74.9` (versionCode 146, commit `ba9f38f`) is one of the most architecturally serious mobile NLEs on Android. The repository has ~74,750 lines of Kotlin across 213 main sources and 62 tests; 117 engine files; a custom 73 KB `ShaderEffect.kt` GLSL framework; a 201 KB `EditorViewModel.kt` with extracted delegate classes; a real Media3 1.10.1 multi-sequence export path; on-device Whisper + MediaPipe + LaMa + DeepFilterNet; 37 GPU-accelerated transitions; full color grading with scopes; and a privacy posture (Photo Picker–only media access, no `READ_MEDIA_*` perms, opt-in model downloads, opt-in C2PA disclosure) that is rare on Android.

The **strongest current shape** is "trustworthy local NLE with cinematic-grade export." The roadmap's Round 7/8 closed most of the release-readiness gates (16 KB, edge-to-edge, predictive back, adaptive resizability, model checksums, FFmpeg-16kb, DeepFilterNet, Media3 Lottie, Ultra HDR gainmap direction, thermal headroom, AI ledger). What is *not yet* closed and matters most for the next two cycles is **turning shipped engines into shipped user workflows**: the Tier C UI integrations whose engines have already landed (compound nav, keyframe bezier, adjustment-layer plan, cut-assistant merge, translation editor, Noto fallback, privacy dashboard, plugin registry, OpenFX descriptor), plus a small set of *new* gaps this pass surfaces (project recovery UX, missing-media relink, source-of-truth drift in `.gitignore`, pollution from a former HostShield co-tenant, font/glyph compliance for CJK/RTL, autosave format upgrade strategy, the `versionName` truth set vs. README/CLAUDE drift, and one accessibility regression vector around custom Compose semantics on heavy panels).

**Top 10 opportunities, in priority order:**

1. **Close the Tier C engine→UI gap.** Engines for adjustment layers, compound clip navigation, keyframe bezier graph, cut-assistant multi-word filler, caption translation editor, and Noto fallback all landed in the last 14 days; none has its Compose panel yet. Each is a 1-commit UI pass with high user payoff.
2. **First-class missing-media / relink flow.** `ProjectArchive.importArchiveWithReport()` reports unresolved URIs but the editor itself has no "media missing — relink?" UI when an autosaved project's source URIs revoke or expire (Android's per-URI grant model means this is *expected*, not edge-case).
3. **Repo hygiene fix: `.gitignore` claims `ROADMAP.md`, `CHANGELOG.md`, `CODEX_CHANGELOG.md` are local-only but all three are tracked, and the project root has 4 stray HostShield files (`HostShield-Research-Report.md` plus `research/*.md`) that are not NovaCut concerns.** Mid-trust signal because new contributors arriving via GitHub will see contradictory state. Fix once, lock with a tracked-files audit test.
4. **Crash recovery + first-run UX completion.** Crash-recovery dialog exists in `RecoveryDialogTest.kt`; first-run tutorial exists; tie them together with a "Welcome back, your last edit was recovered" surface that doesn't fire unless autosave actually has data the schema parser can read.
5. **Project-level color management spine.** Round 4 R4.2 (ACES/OCIO/libplacebo) is a "Later" item, but the lower-cost piece — project-wide working space + display transform metadata persisted in `Project` and surfaced as an Export warning when source and display transform disagree — is a 1–2 commit pass that unlocks every Tier B HDR/scope follow-up.
6. **CJK / RTL render verification.** `CaptionFontFallbackPolicy` shipped, but no caption rendering test exercises the Noto bundles end-to-end. RTL bidi text in overlays (R5.4b) is still backlog and *will silently mis-render* with the current `Canvas`-based text overlay path.
7. **Diagnostic ZIP completeness audit.** Settings exposes the bundle; the bundle redacts media URIs, but it does not include the most useful field for support: a sanitized timeline shape (clip count per track, transition count, effect histogram). The user can opt into this without leaking content.
8. **Photo Picker for stickers/overlays.** `MediaPicker` is video-first; importing an image sticker still routes through `ACTION_OPEN_DOCUMENT`. Photo Picker is the more privacy-correct path on API 33+ and is already used for video.
9. **Activate the Cut Assistant review hand-off into Filler Removal panel.** Both exist as separate UI surfaces; the engines feed the same data structure but the two panels do not share state, so users currently re-discover the same proposals twice.
10. **Per-project AI provenance sidecar visibility.** `AiUsageLedger` writes `.ai-use.json` next to exports; nothing in the project surface shows which clips contributed to that ledger before export starts. Pre-export visibility is the actual "trust" UX moment.

---

## Evidence Reviewed

**Files and directories inspected (selection — all under `\\vmware-host\Shared Folders\repos\NovaCut\`):**

- Top-level: [README.md](README.md), [ROADMAP.md](ROADMAP.md) (1141 lines), [CHANGELOG.md](CHANGELOG.md) (1966 lines), [CODEX_CHANGELOG.md](CODEX_CHANGELOG.md) (901 lines), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [CROSS-PROJECT-ROADMAP.md](CROSS-PROJECT-ROADMAP.md), [LICENSE](LICENSE), [HostShield-Research-Report.md](HostShield-Research-Report.md) *(unrelated — flag for cleanup)*.
- Build: [build.gradle.kts](build.gradle.kts), [app/build.gradle.kts](app/build.gradle.kts), [gradle/libs.versions.toml](gradle/libs.versions.toml), [settings.gradle.kts](settings.gradle.kts), [.gitignore](.gitignore).
- Manifest + resources: [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml), [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) (1858 lines, well-extracted), [app/src/main/res/xml/file_paths.xml](app/src/main/res/xml/file_paths.xml), [backup_rules.xml](app/src/main/res/xml/backup_rules.xml), [data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml), [locales_config.xml](app/src/main/res/xml/locales_config.xml).
- App entry: [MainActivity.kt](app/src/main/java/com/novacut/editor/MainActivity.kt), [NovaCutApp.kt](app/src/main/java/com/novacut/editor/NovaCutApp.kt).
- Engines (117 .kt files; full directory listing reviewed): focused reads of `TimelineImportEngine`, `TapSegmentEngine`, `StockAssetEngine`, `CameraCaptureEngine`, `StemSeparationEngine`, `OutputStreamingEngine`, `ProjectSyncEngine`, `OboeResamplerEngine`, `StyleTransferEngine`, `VoiceCloneEngine`, `LipSyncEngine`. Top-by-size: `ProjectAutoSave.kt` (91 KB), `ShaderEffect.kt` (73 KB), `VideoEngine.kt` (61 KB), `AudioEffectsEngine.kt` (32 KB), `EffectBuilder.kt` (28 KB), `TimelineExchangeEngine.kt` (27 KB), `ProjectArchive.kt` (24 KB), `InpaintingEngine.kt` (22 KB), `NoiseReductionEngine.kt` (20 KB), `KeyframeEngine.kt` (19 KB).
- UI (82 .kt files): `EditorViewModel.kt` (201 KB), `EditorScreen.kt` (141 KB), `Timeline.kt` (127 KB), `ExportSheet.kt` (88 KB), `ToolPanel.kt` (79 KB), `SettingsScreen.kt` (54 KB), `ProjectListScreen.kt` (47 KB).
- Tests (62 .kt under `app/src/test`): coverage spans engine helpers (mastering, autosave, cut-assistant, stream-copy, smart-render run-planning, model-download checksum, compatibility, color confidence, font fallback, plugin registry, OpenFX descriptor, privacy dashboard, thermal policy, output streaming scope classifier).
- Schemas: `app/schemas/com.novacut.editor.engine.db.ProjectDatabase/{4,5,6}.json` — Room v6 with migration chain.
- Docs: [docs/models.md](docs/models.md), [docs/templates.md](docs/templates.md), [docs/frame-extraction-media3-inspector.md](docs/frame-extraction-media3-inspector.md), [docs/preview-media3-compose.md](docs/preview-media3-compose.md), [docs/progress-slider-media3-compose.md](docs/progress-slider-media3-compose.md).
- Research: [.ai/research/2026-05-17/STATE_OF_REPO.md](.ai/research/2026-05-17/STATE_OF_REPO.md), [FEATURE_BACKLOG.md](.ai/research/2026-05-17/FEATURE_BACKLOG.md), [PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md), [COMPETITOR_MATRIX.md](.ai/research/2026-05-17/COMPETITOR_MATRIX.md), [DATASET_MODEL_INTEGRATION_REVIEW.md](.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md).
- CI / release: [.github/workflows/build.yml](.github/workflows/build.yml), [.github/dependabot.yml](.github/dependabot.yml), [scripts/check_16kb_alignment.py](scripts/check_16kb_alignment.py), [fastlane/metadata/android/en-US/](fastlane/metadata/android/en-US/).

**Git history range reviewed:** local `master` head `ba9f38f` back to ~`8716b1d` (61 commits since 2026-05-15; 104 commits 2026-04-01 → 2026-05-15; 278 commits total). Local branch is `master`; remote is `github.com/SysAdminDoc/NovaCut`. Last tagged release on local list: `v3.73.2`; live source string is `v3.74.9` — release tags are lagging shipped source. No CI run on `master` HEAD: workflow only fires on `workflow_dispatch` or `tags/v*`, so post-tag work is unverified by CI.

**External sources verified during this pass (cross-checked, not blindly trusted):**

- Android docs already cited in Round 7/8: edge-to-edge, predictive back, Photo Picker, Per-app language preferences, Adaptive resizability, Auto Backup, Stylus handwriting in Compose foundation, Thermal headroom API, Local Network Protection.
- Material 3 Expressive `1.5.0-alpha19` graduation (2026-05-06) — referenced by R8.1.
- Media3 1.10.x release notes covering `media3-effect-lottie`, multi-sequence Composition API, Ultra HDR ingest, AV1/VP9/Dolby Vision Profile 10 capability classification.
- Open-source NLE landscape (OpenCut 50.7k★, devhyper/open-video-editor ~650★, OpenTimelineIO, Auto-Editor, Gyroflow, Olive, Kdenlive, Shotcut, LosslessCut) — used to validate priority of NLE round-trip *import* over more effects.
- 2026 regulatory deltas: EU AI Act Article 50 (2026-08-02), FTC AI policy statement (March 2026), federal "Protecting Consumers from Deceptive AI Act" (April 2026) — already cited as R8.9 driver.
- ARM/Android 16 KB native-library guidance (Play Store enforcement since 2025-11-01).

**Could not verify in this pass:** no emulator/device run — all findings are static. The remaining JVM unit-test failure recorded in `STATE_OF_REPO.md` (`AutoSaveStateTest.deserialize_capsPathologicalRecoveredCollections`) was claimed fixed in PROJECT_CONTEXT but git history shows the fix landed via the same commit batch; current `:app:testDebugUnitTest` status is **unverified by this pass**. No real-device 16 KB check beyond the script run.

---

## Current Product Map

### Core workflows

1. **Open the app → Projects gallery → create / open / duplicate / template / delete project.** Single-activity Compose nav. ([MainActivity.kt](app/src/main/java/com/novacut/editor/MainActivity.kt), [ProjectListScreen.kt](app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt))
2. **Editor: import media → multi-track timeline → cut/trim/split/merge → effects/transitions/keyframes → audio mix → captions/TTS → AI tools → export.** Single editor screen with delegated state surfaces (clip-editing, effects, overlay, export, audio-mixer, color-grading, AI-tools). ([EditorScreen.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt), [EditorViewModel.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt))
3. **Export: single render → batch render → audio-only / stems / GIF / contact sheet / OTIO / FCPXML / EDL / SRT / VTT / ASS / chapter markers.** Foreground service with thermal-aware progress notifications. ([ExportSheet.kt](app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt), [ExportService.kt](app/src/main/java/com/novacut/editor/engine/ExportService.kt), [VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt))
4. **Settings: export defaults → timeline behavior → AI models → diagnostics → about.** Wi-Fi-only model downloads, model size disclosure, removable models, redacted diagnostic ZIP. ([SettingsScreen.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt))
5. **External "Edit in NovaCut" entry:** `content://` video URIs land via `ACTION_VIEW` intent filter and route to Projects gallery. Image and audio entry points are **not** wired — see Opportunity #14.

### Existing high-level feature inventory

Full list lives in [README.md](README.md) and [CHANGELOG.md](CHANGELOG.md); the slice below is the feature inventory the planning agent should treat as the "*currently working*" baseline. Each row is annotated with `[VERIFIED]` if cross-referenced to actual file/test evidence in this pass, `[LIKELY]` if implied by code structure but not statically verified end-to-end, or `[SCAFFOLD]` if the engine exists with stub returns and no real model/dep.

| Surface | Status | Evidence |
|---|---|---|
| Multi-track timeline with snapping, slip/slide, grouping, color labels | `[VERIFIED]` | [Timeline.kt:1-127k](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt), [TimelineEditing.kt](app/src/main/java/com/novacut/editor/ui/editor/TimelineEditing.kt) |
| Media3 Transformer 1.10.1 multi-sequence export | `[VERIFIED]` | [VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt), [TimelineSequencePlannerTest.kt](app/src/test/java/com/novacut/editor/engine/TimelineSequencePlannerTest.kt) |
| GLSL effect / transition pipeline (37 transitions, 40+ effects) | `[VERIFIED]` | [ShaderEffect.kt:73 KB](app/src/main/java/com/novacut/editor/engine/ShaderEffect.kt), [EffectBuilder.kt](app/src/main/java/com/novacut/editor/engine/EffectBuilder.kt) |
| Speed control + bezier ramp + 14 presets | `[VERIFIED]` | [SpeedCurveEditor.kt](app/src/main/java/com/novacut/editor/ui/editor/SpeedCurveEditor.kt), [SpeedPresets.kt](app/src/main/java/com/novacut/editor/ui/editor/SpeedPresets.kt), [ClipTimingTest.kt](app/src/test/java/com/novacut/editor/model/ClipTimingTest.kt) |
| Keyframe engine (12 easings, hold, bezier) | `[VERIFIED]` | [KeyframeEngine.kt](app/src/main/java/com/novacut/editor/engine/KeyframeEngine.kt), [KeyframeBezierGraph.kt](app/src/main/java/com/novacut/editor/engine/KeyframeBezierGraph.kt) — **graph data model done, UI panel pending** |
| Whisper ONNX caption (English tiny, multilingual fallback) | `[VERIFIED]` | [whisper/WhisperEngine.kt](app/src/main/java/com/novacut/editor/engine/whisper/WhisperEngine.kt) |
| MediaPipe selfie segmentation BG removal | `[VERIFIED]` | [segmentation/SegmentationEngine.kt](app/src/main/java/com/novacut/editor/engine/segmentation/SegmentationEngine.kt) |
| LaMa-Dilated object inpainting | `[VERIFIED]` | [InpaintingEngine.kt](app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt) |
| DeepFilterNet noise reduction (active dep) | `[VERIFIED]` | [NoiseReductionEngine.kt](app/src/main/java/com/novacut/editor/engine/NoiseReductionEngine.kt), commit `f4b1a2b` |
| FFmpegKit-16kb (active dep) | `[VERIFIED]` | [FFmpegEngine.kt](app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt), commit `09a711c` |
| EBU R128 loudness + 6 platform presets | `[VERIFIED]` | [LoudnessEngine.kt](app/src/main/java/com/novacut/editor/engine/LoudnessEngine.kt) |
| OTIO/FCPXML/EDL **export** + validation | `[VERIFIED]` | [TimelineExchangeEngine.kt](app/src/main/java/com/novacut/editor/engine/TimelineExchangeEngine.kt), [TimelineExchangeValidator.kt](app/src/main/java/com/novacut/editor/engine/TimelineExchangeValidator.kt) |
| OTIO/FCPXML/EDL **import** | `[SCAFFOLD]` | [TimelineImportEngine.kt:77](app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt) — `Log.d("import: stub")` |
| Stream-copy fast trim (whole-timeline) | `[VERIFIED]` | [StreamCopyExportEngine.kt](app/src/main/java/com/novacut/editor/engine/StreamCopyExportEngine.kt), [SmartRenderEngine.planRuns](app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt) |
| Mixed copy/re-encode segment composer (B.5) | `[SCAFFOLD]` | `SmartRenderEngine` planner only; concat step pending FFmpeg integration follow-up |
| C2PA manifest sidecar on AI-disclosed exports | `[VERIFIED, unsigned]` | [C2paExportEngine.kt](app/src/main/java/com/novacut/editor/engine/C2paExportEngine.kt) — `.c2pa-manifest.json` written; signed MP4 embedding pending |
| AI usage ledger + `.ai-use.json` sidecar + Export sheet disclosure | `[VERIFIED]` | [AiUsageLedger.kt](app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt), commit `f9ebd1a` |
| Thermal-aware export | `[VERIFIED]` | [ThermalHeadroomPolicy.kt](app/src/main/java/com/novacut/editor/engine/ThermalHeadroomPolicy.kt), [ExportService.kt](app/src/main/java/com/novacut/editor/engine/ExportService.kt) |
| Project autosave + recovery dialog | `[VERIFIED]` | [ProjectAutoSave.kt:91 KB](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt), [RecoveryDialogTest.kt](app/src/test/java/com/novacut/editor/ui/RecoveryDialogTest.kt) |
| Adaptive resizability + foldable tabletop posture | `[VERIFIED]` | [AdaptiveEditorLayoutPolicy.kt](app/src/main/java/com/novacut/editor/ui/editor/AdaptiveEditorLayoutPolicy.kt), MainActivity wires `WindowInfoTracker` |
| Adjustment layer engine | `[SCAFFOLD-PLUS]` | `AdjustmentLayerEngine.planForClip` exists; UI track surface pending — C.11 |
| Compound clip navigation stack | `[SCAFFOLD-PLUS]` | `CompoundNavStack` data model + tests; gesture pending — C.13 |
| Cut Assistant review (silence + multi-word filler + merge) | `[SCAFFOLD-PLUS]` | `CutAssistantEngine`, `SilenceDetectionEngine`, [CutAssistantReviewPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CutAssistantReviewPanel.kt) — review panel exists; Filler Removal panel is a separate surface — see Opportunity #10 |
| Caption translation editor (R5.4a) | `[SCAFFOLD-PLUS]` | `CaptionTranslationEngine` data model + tests; Compose row UI pending |
| Noto CJK / RTL caption font fallback | `[SCAFFOLD-PLUS]` | `CaptionFontFallbackPolicy` shipped; bundle install + renderer integration pending |
| Privacy dashboard | `[SCAFFOLD-PLUS]` | `PrivacyDashboard` data model + tests; Compose panel pending |
| Unified plugin registry + OpenFX descriptor + compat matrix | `[VERIFIED]` | [PluginRegistry.kt](app/src/main/java/com/novacut/editor/engine/PluginRegistry.kt), [OpenFxDescriptor.kt](app/src/main/java/com/novacut/editor/engine/OpenFxDescriptor.kt) |
| Stock assets / In-app camera / Live streaming / Sync / Stem separation / Style transfer / Voice clone / Lip-sync / Frame interp / Upscaling / RVM matting / SAM tap-segment / 360 / Oboe resampler | `[SCAFFOLD]` | All return `false` / `null` until dep + model arrive — see Engine Stub Audit below |

### Personas

Inferred from `templates`, `fastlane`/`full_description.txt`, ExportSheet copy, and ToolPanel labels:

1. **Solo creator / short-form** — TikTok / Reels / Shorts; cares about platform-preset export, auto-captions, beat sync, caption styles, smart reframe. Most-used surface is ToolPanel "Effects" + "Text" + "AI" tabs.
2. **YouTuber / long-form** — chapter markers, batch export with platform presets, OTIO/FCPXML hand-off to desktop NLE, audio mastering presets, voiceover, subtitle export.
3. **Mobile pro** — Pixel/Galaxy flagship, HDR10+ / AV1 export, Dolby Vision Profile 10, manual color grading, scopes, mask editor, compound clips.
4. **Privacy-conscious editor / F-Droid user** — explicit model downloads, no telemetry, NonFreeNet awareness, diagnostic ZIP for support.

### Platforms and distribution

- **API target:** minSdk 26 (Android 8.0), targetSdk 36 (Android 16). compileSdk 36.
- **ABIs:** universal APK today (no `splits { abi { ... } }` block); ABI split is `[ ]` in roadmap R5.6d.
- **Channels:** Play Store (primary), F-Droid (planned via R5.6b), direct APK via GitHub Releases on `tags/v*` (CI uploads). Fastlane metadata under `fastlane/metadata/android/en-US/` (title, short_description, full_description, changelogs/67.txt).
- **Signing:** `keystore.properties` + env vars; falls back to debug signing if release credentials are absent. `*.jks` and `keystore.properties` are correctly gitignored and not in the tracked tree (verified via `git ls-files`).

### Permissions surface

| Permission | Use |
|---|---|
| `RECORD_AUDIO` | Voiceover recorder |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROCESSING` | Background export |
| `POST_NOTIFICATIONS` | Export progress |
| `VIBRATE` | Haptic on trim handle / snap |
| `INTERNET` + `ACCESS_NETWORK_STATE` | Model downloads + Wi-Fi-only gating |
| **No** `READ_MEDIA_*` / `WRITE_EXTERNAL_STORAGE` / `CAMERA` | All media is Photo Picker + `ACTION_OPEN_DOCUMENT` + system camera intent |

This is a *strong* privacy posture and worth defending in any future change.

---

## Engine Stub Audit (the gap that matters)

Every "stub" entry below means the engine class is present, compiles, has the right Hilt wiring, runs pure helpers without a dep, but returns `null` / `false` / no-op for the actual work because the native library or model is not yet linked. **None of these is broken — they are intentional scaffolds.** The audit matters because the next planning agent should know which ones are 1-commit activations and which are platform decisions.

| Engine | Lines | Stub reason | Activation cost |
|---|---|---|---|
| [`TimelineImportEngine`](app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt) | 89 | No FCPXML/OTIO/EDL parsers wired | M — pure Kotlin parsers; OTIO JSON is straightforward, FCPXML needs care, EDL is regex-friendly. No new deps. |
| [`TapSegmentEngine`](app/src/main/java/com/novacut/editor/engine/TapSegmentEngine.kt) | 261 | No SAM/MobileSAM model downloaded | L — ORT already in tree; model download + tile/blend infer; UI for box/point prompts; mask propagation. |
| [`StockAssetEngine`](app/src/main/java/com/novacut/editor/engine/StockAssetEngine.kt) | 123 | No provider API wiring | M — 4 REST APIs (Pexels/Pixabay/Freesound/FMA); user-supplied keys; license-string passthrough. |
| [`CameraCaptureEngine`](app/src/main/java/com/novacut/editor/engine/CameraCaptureEngine.kt) | 131 | CameraX not on classpath | L — adds 4 CameraX deps; teleprompter overlay; insert-to-timeline; runtime CAMERA permission; capture-resolution probe. |
| [`StemSeparationEngine`](app/src/main/java/com/novacut/editor/engine/StemSeparationEngine.kt) | 73 | No Demucs ONNX | XL — STFT pre/post + 4-channel ONNX inference + tile-stitch; ~80 MB model. Best deferred. |
| [`OutputStreamingEngine`](app/src/main/java/com/novacut/editor/engine/OutputStreamingEngine.kt) | ~370 | No RTMP/SRT lib | L — Stream-Pack (Apache-2.0) is the cheap path; the LNP scope classifier is already in place. |
| [`ProjectSyncEngine`](app/src/main/java/com/novacut/editor/engine/ProjectSyncEngine.kt) | 113 | No sync backend | XL — needs account / discovery / conflict UX. Defer. |
| [`OboeResamplerEngine`](app/src/main/java/com/novacut/editor/engine/OboeResamplerEngine.kt) | 131 | Oboe AAR not pinned | S — `com.google.oboe:oboe:1.9.0`; bridge `MultiChannelResampler`; 16 KB verify; pure-math `estimatedOutputFrames` already covers buffer sizing. |
| [`StyleTransferEngine`](app/src/main/java/com/novacut/editor/engine/StyleTransferEngine.kt) | ~280 | No per-style ONNX downloaded | M per style; ANIMEGAN tile 256², Fast NST tile 480². ORT already in tree. |
| [`VoiceCloneEngine`](app/src/main/java/com/novacut/editor/engine/VoiceCloneEngine.kt) | ~120 | No Sherpa-ONNX XTTS v2 | L — gated by A.1 dep + consent UX. |
| [`LipSyncEngine`](app/src/main/java/com/novacut/editor/engine/LipSyncEngine.kt) | 80 | No Wav2Lip / MuseTalk model; non-commercial license risk | XL — recommend cloud-only via R5.2d generative-video policy. |
| [`FrameInterpolationEngine`](app/src/main/java/com/novacut/editor/engine/FrameInterpolationEngine.kt) | 188 | No NCNN/Vulkan/RIFE | XL — needs `librife.so` self-build, JNI, ABI split. |
| [`UpscaleEngine`](app/src/main/java/com/novacut/editor/engine/UpscaleEngine.kt) | 198 | No Real-ESRGAN ONNX | L — ORT in tree; PAD packaging needed. |
| [`VideoMattingEngine`](app/src/main/java/com/novacut/editor/engine/VideoMattingEngine.kt) | 199 | No RVM ONNX | M — ORT in tree; replace MediaPipe binary mask. |
| [`StabilizationEngine`](app/src/main/java/com/novacut/editor/engine/StabilizationEngine.kt) | 223 | No OpenCV AAR | L — arm64-only; ABI split mandatory. Pair with Gyroflow sidecar import. |
| [`EquirectangularEngine`](app/src/main/java/com/novacut/editor/engine/EquirectangularEngine.kt) | 161 | No 360 transform path | XL — defer. |
| [`SmartReframeEngine`](app/src/main/java/com/novacut/editor/engine/SmartReframeEngine.kt) | 110 | Face/pose detection not wired | M — MediaPipe Tasks Vision Face Landmarker is already an active dep. |
| [`TtsEngine`](app/src/main/java/com/novacut/editor/engine/TtsEngine.kt) Piper path | ~270 | No Sherpa-ONNX | L — gated by A.1; system TTS works today. |
| [`RiveTemplateEngine`](app/src/main/java/com/novacut/editor/engine/RiveTemplateEngine.kt) | 154 | Rive AAR not pinned | M — `app.rive:rive-android:9.x`; render to canvas; interaction model. |
| [`TemplateMarketplaceEngine`](app/src/main/java/com/novacut/editor/engine/TemplateMarketplaceEngine.kt) | 79 | No registry backend | M — static JSON via GitHub Releases; signature/checksum gate. |

**Read this as a list of "next-N-week" feasibility, not as a backlog.** The Round 7/8 Forward View already ranks these; this audit confirms what is actually in the file system vs. what the planning doc claims.

---

## Competitive and Ecosystem Research (delta on top of existing rounds)

The 2026-05-17 round already enumerated CapCut, DaVinci Resolve, PowerDirector, KineMaster, LumaFusion, VN, Premiere Rush EOL, Clipchamp mobile retirement, OpenCut (50.7k★), devhyper/open-video-editor, OpenShot, Kdenlive, Shotcut, LosslessCut, OpenTimelineIO, Gyroflow, gl-transitions. This pass does not re-derive that — see [.ai/research/2026-05-17/COMPETITOR_MATRIX.md](.ai/research/2026-05-17/COMPETITOR_MATRIX.md). **Two new observations** this pass:

1. **KineMaster's "missing media" UX is the gold standard on mobile** — a clip in the timeline with a revoked URI renders with a hatch overlay + "Tap to relink" instead of disappearing. NovaCut today silently drops or fails-to-decode such clips; the importer surfaces `unresolvedMediaUris` but the editor does not. This is concrete enough to translate into a NovaCut surface (see Highest-Value New Features #2).
2. **CapCut 2026 transparently flips between "stream copy" and "re-encode" labels in their export UI** — NovaCut's ExportSheet shows "Smart render outlook" with the percentage, but only for the **whole-timeline** stream-copy. The mixed-segment composer (B.5) needs the same UI affordance — *per-range* badges. Pair with the run-planner scaffold already in `SmartRenderEngine.planRuns()`.

**Anti-pattern to keep avoiding (already in roadmap rejected list):** CapCut's recent "Upload all your media to our servers for an AI Auto-Edit" default and Premiere Rush's metered-cloud-only roadmap. NovaCut's on-device-default posture is a real differentiator — keep cloud paths opt-in with explicit destination + payload disclosure.

---

## Highest-Value New Features

> *These are items not already in [ROADMAP.md](ROADMAP.md)'s Forward View or that need a more concrete shape than the existing one-line entry.*

### 1. Project recovery + missing-media relink UX [P0 / M]

**Problem solved:** When an autosaved project re-opens with revoked content:// URIs (Android per-URI grant model is *explicitly* the recommended path; this happens any time a user clears the photo picker grant, factory-resets, or the source content provider revokes — *not* an edge case), clips currently fail silently. The user sees no "this clip is missing" affordance, no relink path, no way to remove the dangling clip without going into autosave JSON.

**Evidence:** `ProjectArchive.importArchiveWithReport()` returns `unresolvedMediaUris` and surfaces "missing media count" in toast — but the editor itself has no equivalent on every-day autosave reopen. `ProjectAutoSave.kt` is 91 KB and aggressively-defensive (per CLAUDE.md gotchas), but it does not flag dangling URIs to the editor view. `RecoveryDialogTest.kt` only covers the "yes/no, restore?" branch.

**Proposed behavior:** On every editor open:
1. Walk every visible-track clip's `sourceUri`; check `contentResolver.openAssetFileDescriptor(uri, "r")` non-throw + non-zero-length.
2. Tag dangling clips with `Clip.relinkState = Relink.NEEDED`.
3. Timeline renders dangling clips with a Mocha.Peach hatch + "Missing source — Tap to relink".
4. Tap → MediaPicker filtered to original mime, returns new URI, clip swaps source URI, autosave persists, original `trimStartMs`/`trimEndMs` reapply via duration probe.
5. Bulk relink-all-from-folder gesture in the overflow menu.

**Implementation areas:**
- `model/Clip.kt` — new `relinkState` field (default `NONE`; serialise as opt-in to preserve back-compat).
- `engine/ProjectAutoSave.kt` — URI accessibility probe on load.
- `ui/editor/Timeline.kt` — hatch overlay + tap dispatch.
- `ui/editor/EditorViewModel.kt` — relink action via existing MediaPicker round-trip.
- `ui/editor/ClipEditingDelegate.kt` — `relinkClipSource(clipId: String, newUri: Uri)`.

**Risks / edge cases:** Stable-ID matching on relink (filename heuristic + duration tolerance); content://URI persistence already correctly attempts `takePersistableUriPermission` — keep that. Don't show the hatch on clips that are *intentionally* offline (e.g. compound clips).

**Verification:** New JVM unit test exercising probe + relink + autosave round-trip; manual instrumentation test: import video, clear app's media grant, reopen project, expect hatched clip with tap-to-relink.

**Estimated complexity:** M. **Priority:** P0 (trust/reliability) — landed once, removes a class of "the editor lost my work" support tickets.

---

### 2. Pre-export AI provenance preview [P0 / S]

**Problem solved:** `AiUsageLedger` writes `.ai-use.json` next to exports; ExportSheet shows a Disclose-AI-Use checkbox; but the user has no way to see *which clips* contributed to that ledger before export starts. The "trust moment" is *before* the export button, not in a sidecar file the user has to open after.

**Evidence:** [AiUsageLedger.kt](app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt), commit `f9ebd1a`. ExportSheet copy "Disclose AI use" appears in `strings.xml`. No timeline surface currently renders ledger entries.

**Proposed behavior:** In ExportSheet's existing "Color / HDR confidence" row band, add a third row "AI use" — one chip per (effectKind × disclosure level), tap-to-expand list of (clipId, action, model, range). On the timeline, optional faint AI-action icon at the relevant range.

**Implementation areas:** [ExportSheet.kt](app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt), [AiUsageLedger.kt](app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt) (no engine change), new tiny composable.

**Risks:** Don't double-count overlapping ranges; the `mergeOverlaps()` path already exists in the ledger.

**Verification:** UI manual; existing `AiUsageLedgerTest` already covers the data path.

**Complexity:** S. **Priority:** P0 (regulatory + trust).

---

### 3. Adjustment-layer track surface [P1 / M]

**Problem solved:** Tier C.11 engine landed (`AdjustmentLayerEngine.planForClip` + `AdjustmentLayerSegment` value type); no timeline-track UI yet. This is the next blocking step for pro-NLE adjustment-layer parity.

**Evidence:** ROADMAP Tier C.11 marked *in progress* with engine scaffold done; commit `681b7c4`.

**Proposed behavior:** New track type `ADJUSTMENT`; when added, occupies one row above the topmost video track; user drags onto a time range; while present, all effects added to it apply to every visible video clip beneath that range during export and preview. EffectBuilder feeds each per-clip segment through `planForClip` first.

**Implementation areas:** `model/Track.kt` (new `TrackType.ADJUSTMENT`), `engine/EffectBuilder.kt` (bridge to `planForClip`), `ui/editor/Timeline.kt` (new track type renderer), `ui/editor/EditorViewModel.kt` (new track creation), `editor/AdjustmentLayerEngine.kt` (already exists).

**Risks:** Preview vs. export divergence — must wire through both `VideoEngine.preview` and `VideoEngine.export` paths. Save schema migration needed (Room v7 + autosave compat).

**Verification:** Pure JVM test on `planForClip` exists; add an instrumented export-preview-parity test.

**Complexity:** M. **Priority:** P1.

---

### 4. Keyframe bezier graph panel [P1 / M]

**Problem solved:** Tier C.12 engine landed (`KeyframeBezierGraph` + 12 easing presets + per-segment evaluator). No UI panel yet.

**Evidence:** Commit `2525002`. `KeyframeEngine` already supports 12 easings.

**Proposed behavior:** Open from the Keyframes sub-menu → new panel renders a graph per animatable property (position X/Y, scale X/Y, rotation, opacity, volume) over the clip's timeline. Two-handle bezier per segment, diamond keyframe markers, tangent-lock toggle, preset chip row (linear, ease-in/out, spring, bounce, elastic, back, etc.). Tap-empty-area → add keyframe at that time.

**Implementation areas:** new [KeyframeGraphPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/KeyframeGraphPanel.kt), wire to existing [KeyframeCurveEditor.kt](app/src/main/java/com/novacut/editor/ui/editor/KeyframeCurveEditor.kt) as a mode switch.

**Risks:** Touch hit-areas for bezier handles at small DPIs; the existing `SpeedCurveEditor.kt` is a strong template.

**Verification:** JVM test on `presets` + `rescale` already exists; add screenshot golden for the panel at 360dp / 600dp / 840dp.

**Complexity:** M. **Priority:** P1.

---

### 5. Compound clip open / exit gesture [P1 / S]

**Problem solved:** Tier C.13 engine landed (`CompoundNavStack` with depth cap + cycle detection + breadcrumb + autosave round-trip). No editor gesture yet.

**Evidence:** Commit `68456d7`.

**Proposed behavior:** Long-press a compound clip in the timeline → "Open" action in radial menu → editor swaps active timeline to the children; breadcrumb chip appears above the timeline ("Project > 'Logo Build' (depth 1)"); tap chip or back gesture pops the stack. Predictive back support is already wired at the manifest level; gate `BackHandler` on `CompoundNavStack.depth > 0`.

**Implementation areas:** [EditorViewModel.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt) (new `openCompoundClip(id)`), [EditorScreen.kt](app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt) (breadcrumb composable + predictive-back gate update), [Timeline.kt](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt) (radial menu action), [RadialActionMenu.kt](app/src/main/java/com/novacut/editor/ui/editor/RadialActionMenu.kt) (new entry).

**Risks:** Autosave round-trip — `CompoundNavStack.toSerializedIds()` already exists; the editor must serialise the *stack* not just the active timeline.

**Verification:** Existing `CompoundNavStackTest`; add an instrumented manual flow test.

**Complexity:** S. **Priority:** P1.

---

### 6. Cut Assistant + Filler Removal merge into one review surface [P1 / S]

**Problem solved:** Two engines and two panels (`CutAssistantReviewPanel`, `FillerRemovalPanel`) propose overlapping cuts. The Round 7 `mergeProposals()` helper exists; the panels don't use it.

**Evidence:** `CutAssistantEngine`, `SilenceDetectionEngine.detectMultiWordFillers`, `mergeProposals`. Two separate panel files: [CutAssistantReviewPanel.kt:17 KB](app/src/main/java/com/novacut/editor/ui/editor/CutAssistantReviewPanel.kt), [FillerRemovalPanel.kt:15 KB](app/src/main/java/com/novacut/editor/ui/editor/FillerRemovalPanel.kt).

**Proposed behavior:** Promote `CutAssistantReviewPanel` to the single review surface. Below the existing silence + multi-word filler rows, add filter chips ("Silence", "'Um/Uh/Like'", "'You know / I mean'", "All"). `FillerRemovalPanel` either becomes a chip filter or is removed in favour of the unified surface. Apply once → one undo state, not two.

**Implementation areas:** [CutAssistantReviewPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CutAssistantReviewPanel.kt), [FillerRemovalPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/FillerRemovalPanel.kt), [AiToolsDelegate.kt](app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt), `strings.xml` chip labels.

**Risks:** Mutual-exclusion of panels — the existing dismissedPanelState pattern handles it.

**Verification:** Existing `SilenceDetectionEngineTest` + `CutAssistantEngineTest`. Add a panel-merge UI test.

**Complexity:** S. **Priority:** P1.

---

### 7. Caption translation editor row UI [P1 / S]

**Problem solved:** R5.4a engine + data model shipped (`CaptionTranslationEngine.EditorRowState`, `LanguagePairQuality`, `TranslatedSegment.editorState`); commit `9ea131a`. No editor surface yet.

**Proposed behavior:** Caption panel gains a language chip. Picking a target language renders the existing caption rows two-up: source on top, target below, with state colour (TRANSLATED → green, USER_EDITED → blue, REGENERATE_PENDING → amber). Per-row "Regenerate" action runs the translation re-pass for that row only.

**Implementation areas:** [CaptionEditorPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CaptionEditorPanel.kt), [CaptionTranslationEngine.kt](app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt) (no change), `strings.xml`.

**Risks:** Surfacing model-pair quality (`pairQuality(variant, src, tgt)`) before model download — the API is already pure; show "Experimental" badge on row.

**Verification:** Existing `CaptionTranslationEngineTest` covers data. Add a manual smoke test once a Bergamot or MADLAD model is wired (Tier C.5/R6.7).

**Complexity:** S. **Priority:** P1.

---

### 8. Privacy Dashboard panel (R5.5c) [P1 / S]

**Problem solved:** Data model + 10 invariant tests shipped (`PrivacyDashboard`, commit `cea8de2`); no Compose panel yet.

**Proposed behavior:** Settings → "Privacy" → new screen listing every category (project content, media metadata, ML models, app preferences, template library, diagnostic logs, cloud generative video, opt-in telemetry) with current state, storage location, per-category Export / Delete / Opt-out actions. Cloud + telemetry rows always start *off* (the engine already enforces this).

**Implementation areas:** new [PrivacyDashboardPanel.kt](app/src/main/java/com/novacut/editor/ui/settings/PrivacyDashboardPanel.kt), [SettingsScreen.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt) entry row, [SettingsViewModel.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsViewModel.kt) action wiring.

**Risks:** "Delete project content" must also clear autosave + recovery + thumbnails — call existing `clearRecoveryData()`.

**Complexity:** S. **Priority:** P1.

---

### 9. Photo Picker for sticker / image-overlay imports [P1 / S]

**Problem solved:** [MediaPicker.kt](app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt) uses `PickVisualMedia` for video; the sticker picker still uses `ACTION_OPEN_DOCUMENT` (see [StickerPickerPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/StickerPickerPanel.kt)). Photo Picker is the privacy-correct path and is already in use elsewhere in the app — *plus* it survives background kill better than the OpenDocument route on Selected-Photos restricted devices.

**Implementation areas:** [StickerPickerPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/StickerPickerPanel.kt) (swap action contract), `strings.xml` (existing copy already references `sticker_import_from_gallery`).

**Risks:** Some sticker import flows currently expect `application/octet-stream` for `.ncfx` plugins — keep the OpenDocument path for those, only swap the image case.

**Verification:** Existing `R8.6` Photo Picker policy applies.

**Complexity:** S. **Priority:** P1.

---

### 10. Pre-flight model-availability sheet on every AI tool [P1 / S]

**Problem solved:** AI tool taps that require a model show "Model gated" status chip; the user must hunt for "Review AI models" in Settings. CapCut and KineMaster surface a *single tap* model-download sheet at the tool-tap moment. NovaCut has `ModelDownloadManager` and the disclosure copy in `strings.xml` (`ai_requirement_model_label`, `ai_requirement_review_models`), but the sheet is not consistently wired across the 14 AI tool tiles.

**Proposed behavior:** Tapping any model-gated tool always opens an `AiModelRequirementSheet` showing model name + size + license + Wi-Fi-only setting + Cancel/Download buttons. Once installed, the tool starts directly on next tap.

**Implementation areas:** new [AiModelRequirementSheet.kt](app/src/main/java/com/novacut/editor/ui/editor/AiModelRequirementSheet.kt), [AiToolsPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/AiToolsPanel.kt), [AiToolsDelegate.kt](app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt). `ModelDownloadManager` already exposes everything needed.

**Risks:** Don't show the sheet for tools whose model is already installed.

**Complexity:** S. **Priority:** P1.

---

### 11. App Shortcuts (long-press launcher icon) [P2 / S]

**Problem solved:** No `<meta-data android:name="android.app.shortcuts" ...>` in the manifest. Long-pressing the launcher icon offers only the default "App info" entry. Mobile NLE users expect 1-tap shortcuts: "New project", "Open last project", "Resume recovered draft".

**Proposed behavior:** Add `res/xml/shortcuts.xml` with three dynamic-ish shortcuts; `ProjectListViewModel` exposes "last project" via DataStore. Resume-recovered only appears when autosave has recoverable state.

**Implementation areas:** new `res/xml/shortcuts.xml`, manifest `<meta-data>` row, [ProjectListViewModel.kt](app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt) trigger.

**Complexity:** S. **Priority:** P2 (small but high-affordance polish).

---

### 12. "Send to NovaCut" sharesheet target for images and audio [P2 / S]

**Problem solved:** Manifest's `ACTION_VIEW` intent filter covers `video/*` only. Users sharing an image (sticker / overlay candidate) or audio (music / SFX) from the gallery cannot land on NovaCut.

**Proposed behavior:** Add two more `<intent-filter>` blocks for `image/*` and `audio/*`. Route to ProjectListScreen with a "Pick destination project" sheet; insert into appropriate track on next editor open. Reject `file://` URIs — only `content://`.

**Implementation areas:** [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [MainActivity.handleIncomingIntent](app/src/main/java/com/novacut/editor/MainActivity.kt).

**Risks:** Audio share interferes less with "Edit this video" expectation; keep destination-project prompt clear.

**Complexity:** S. **Priority:** P2.

---

### 13. Project-wide working colour space + display transform [P2 / M]

**Problem solved:** R4.2's full ACES/OCIO/libplacebo backbone is "Later", but the smaller piece — adding `Project.workingColorSpace` and `Project.displayTransform` so the export pipeline can warn when source HDR ≠ working ≠ display — is a 1–2 commit pass that immediately unlocks the existing `ExportColorConfidenceEngine` to emit higher-value warnings.

**Proposed behavior:**
- `Project.workingColorSpace ∈ {SDR_BT709, HDR10_BT2020_PQ, HDR_HLG, ACES_AP1}` (default `SDR_BT709`).
- `Project.displayTransform ∈ {NONE, BT2390_TONEMAP, HABLE_TONEMAP}` (default `NONE`).
- Pure data model; ExportColorConfidenceEngine uses them today; no actual GL change needed yet.

**Implementation areas:** [model/Project.kt](app/src/main/java/com/novacut/editor/model/Project.kt), [ExportColorConfidenceEngine.kt](app/src/main/java/com/novacut/editor/engine/ExportColorConfidenceEngine.kt), settings row in `ProjectTemplateSheet` (per-template default).

**Verification:** New tests on confidence-engine warning emission for each (source × working × display) tuple.

**Complexity:** M. **Priority:** P2 — pure groundwork; unblocks every subsequent HDR feature.

---

### 14. RTL bidi text in overlays [P2 / M]

**Problem solved:** R5.4b is in `Later` with one line. The current overlay text path (`StrokedTextBitmapOverlay`, `ExportTextOverlay`) does not run BiDi reordering before draw. Arabic, Hebrew, Persian text will *silently mis-render* (mirrored shapes, wrong arrow direction for transitions) under RTL locales — already a regression risk in the wild because the manifest declares `android:supportsRtl="true"`.

**Proposed behavior:** Apply `android.text.BidiFormatter` + `TextDirectionHeuristics` to overlay text before Canvas draw; mirror transition arrows and trim-handle hit zones under RTL.

**Implementation areas:** [StrokedTextBitmapOverlay.kt](app/src/main/java/com/novacut/editor/engine/StrokedTextBitmapOverlay.kt), [ExportTextOverlay.kt](app/src/main/java/com/novacut/editor/engine/ExportTextOverlay.kt), [Timeline.kt](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt).

**Verification:** Pair with R5.4d (already in flight — `CaptionFontFallbackPolicy`); add golden-text tests using bundled Noto Arabic.

**Complexity:** M. **Priority:** P2.

---

### 15. Diagnostic ZIP: optional sanitized timeline shape [P2 / S]

**Problem solved:** Diagnostic ZIP (R5.5d) is excellent on privacy — no project content, no media URIs, no autosave. But for support triage, the most useful single field is "timeline shape": clip count per track, transition count, effect histogram, total duration. None of those leak content.

**Proposed behavior:** New optional checkbox in Settings → "Include sanitized timeline shape (no clip names, no media URIs, no captions)". When on, the ZIP includes `timeline-shape.json` with `{ trackCount, totalDurationMs, perTrackClipCount, perEffectTypeCount, perTransitionTypeCount }`.

**Implementation areas:** [DiagnosticExportEngine.kt](app/src/main/java/com/novacut/editor/engine/DiagnosticExportEngine.kt), [SettingsScreen.kt](app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt), `strings.xml`.

**Verification:** Extend [DiagnosticExportEngineTest](app/src/test/java/com/novacut/editor/engine/DiagnosticExportEngineTest.kt) — add a fixture project; assert no string match on any media URI / caption / clip name.

**Complexity:** S. **Priority:** P2.

---

## Existing Feature Improvements

### A. EditorViewModel decomposition is good but Timeline.kt (127 KB) is the next refactor [P2 / M]

**Current behavior:** `EditorViewModel.kt` (201 KB) was already decomposed into 7 delegates (clip-editing, effects, overlay, export, audio-mixer, color-grading, AI-tools). Good. `Timeline.kt` did not get the same treatment; it is now the single largest UI file in the repo and has grown by ~25 KB since R5.

**Recommended change:** Extract Timeline render passes by concern: `TimelineRuler`, `TimelineClipsRow`, `TimelineTrackHeaders`, `TimelineWaveformLayer`, `TimelineSnapIndicators`, `TimelineKeyframeDots`. Existing tests under `TimelineEditingTest.kt` cover input handling — pin them as the contract before refactor.

**Code locations:** [Timeline.kt](app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt), [TimelineEditing.kt](app/src/main/java/com/novacut/editor/ui/editor/TimelineEditing.kt).

**Backwards compat:** Pure UI internal split; no model change.

**Verification:** `:app:testDebugUnitTest` for all existing TimelineEditing tests; manual scrub/zoom/snap test on debug APK.

**Complexity:** M. **Priority:** P2 (pay-as-you-go before adding adjustment-layer track + compound nav UI).

---

### B. Source-of-truth drift in `.gitignore` and stray HostShield artefacts [P1 / S]

**Current behavior:**
- `.gitignore` lists `CLAUDE.md`, `CODEX_CHANGELOG.md`, `.claude/`, `RESEARCH.md`, `ROADMAP.md`, `HostShield-Research-Report.md`, `research/`, `ROADMAP-COMPLETED.md`, `qa/` under "Private docs — keep local only". But `ROADMAP.md`, `CHANGELOG.md`, `CODEX_CHANGELOG.md`, `CROSS-PROJECT-ROADMAP.md`, `PROJECT_CONTEXT.md` are all *tracked* in git (confirmed via `git ls-files`). The `.gitignore` entries were added after the files were already committed; git keeps tracking until explicit `git rm --cached`.
- `HostShield-Research-Report.md` (29 KB) and `research/{dns-blocking-deep-dive,blocklist-management-research,android-firewall-privacy-research}.md` (~74 KB) live at the project root and are about a completely different app (DNS/VPN/hosts ad-blocker). They are correctly gitignored, but their presence on disk is a documentation-pollution risk.

**Problem:** New contributors arriving at the repo see a `.gitignore` that contradicts the tracked state. The HostShield files are real bytes on the local working tree that confuse any "what is this repo about" pass.

**Recommended change:**
1. Decide which of `ROADMAP.md`, `CHANGELOG.md`, `CODEX_CHANGELOG.md`, `CROSS-PROJECT-ROADMAP.md`, `PROJECT_CONTEXT.md` are *meant* to be tracked. Likely answer: keep `ROADMAP.md`, `CHANGELOG.md`, `CROSS-PROJECT-ROADMAP.md`, `PROJECT_CONTEXT.md` tracked, keep `CODEX_CHANGELOG.md` local-only. Remove the corresponding lines from `.gitignore`; `git rm --cached` for the one being privatized.
2. Add a tracked-files guard test (similar to `EngineStringExtractionAuditTest`) that fails the build if a file matches `^HostShield|^research/` in tracked files.
3. Move the local HostShield files into a sibling repo or delete them locally — they are unrelated to NovaCut.

**Code locations:** [.gitignore](.gitignore), new `app/src/test/java/com/novacut/editor/TrackedFilesAuditTest.kt`.

**Verification:** `git ls-files | grep -E '^HostShield|^research/'` returns empty after fix; new audit test passes.

**Complexity:** S. **Priority:** P1 (trust, contributor onboarding).

---

### C. CI pipeline gaps [P1 / S]

**Current behavior:** [.github/workflows/build.yml](.github/workflows/build.yml) only runs on `workflow_dispatch` or `tags/v*`. Every PR / push to `master` skips CI. Latest local tag is `v3.73.2` while source ships at `v3.74.9` — meaning ~146 commits of work between `v3.73.2` and `v3.74.9` were never CI-verified at HEAD. 16 KB alignment, unit tests, and APK build are not gating any commit.

**Recommended change:** Add `push: branches: [ master ]` and `pull_request:` to the workflow trigger. Keep the existing tag-publish behaviour. Consider matrix Java versions (17 only for now; 21 once `gradle.properties` opts in).

**Code locations:** [.github/workflows/build.yml](.github/workflows/build.yml).

**Backwards compat:** None — pure CI plumbing.

**Verification:** Next push to master triggers a green build.

**Complexity:** S. **Priority:** P1.

---

### D. Lagging release tag → release artefact mismatch [P2 / S]

**Current behavior:** `NovaCut-v3.72.0.apk` (117 MB) sits on the working tree; `local.properties` and `keystore.properties` exist for local release builds; source is `v3.74.9`; latest visible tag (from `git tag --sort=-creatordate`) was `v3.73.2`. No `v3.74.x` tag.

**Recommended change:** Two-line release runbook in `PROJECT_CONTEXT.md` Build and Verification section: "Cutting a release: `git tag -a v<X.Y.Z> -m '...'; git push origin v<X.Y.Z>` triggers `.github/workflows/build.yml` which publishes signed APKs to the GitHub Release."

**Complexity:** S. **Priority:** P2.

---

### E. Strings file is well-extracted but lacks per-feature comments at scale [P3 / S]

**Current behavior:** `strings.xml` is 1858 lines, well-organised by panel. No `<!-- ... -->` section anchors in many sections; localizers will lose context.

**Recommended change:** Insert short comment blocks at every panel boundary — already done for some sections (`<!-- ExportSheet -->`, `<!-- AiToolsPanel -->`), missing in several others. Mechanical pass; pairs with R5.4c which is closed.

**Complexity:** S. **Priority:** P3.

---

### F. README vs source-of-truth drift on `versionName` [P3 / S]

**Current behavior:** [README.md](README.md) tech stack section claims "Media3 1.10.1" and matches `gradle/libs.versions.toml`. Good. But CLAUDE.md (local) line 7 says `v3.74.9` while header line 1 still refers to "Full-featured Android video editor". README itself does **not** state the version anywhere — every other claim is fine. PROJECT_CONTEXT.md has the live evidence. `app_version` resource string is correct.

**Recommended change:** Add a one-line "Current version" badge in README header that reads `BuildConfig.VERSION_NAME` from `app/build.gradle.kts` at template-render time, or just inline the literal version once and refresh it on every minor cut.

**Complexity:** S. **Priority:** P3.

---

### G. Tier B.5 mixed copy/re-encode composer is unblocked [P1 / M]

**Current behavior:** Round 7 R6.5 wired FFmpegKit-16kb (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`). The B.5 run-planner scaffold is done (`SmartRenderEngine.planRuns()` with 8 tests). The composer step — concat per-run outputs through FFmpeg concat demuxer — is no longer blocked.

**Recommended change:** Wire the composer: each `RenderRun` exports through the appropriate engine (`StreamCopyExportEngine` for `Copy` runs, Media3 Transformer for `ReEncode` runs); collect outputs; run `ffmpeg -f concat -safe 0 -i list.txt -c copy out.mp4`; expose per-range badges in ExportSheet ("4 ranges fast-trim, 1 needs render").

**Code locations:** [SmartRenderEngine.kt](app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt), [VideoEngine.kt](app/src/main/java/com/novacut/editor/engine/VideoEngine.kt), [StreamCopyExportEngine.kt](app/src/main/java/com/novacut/editor/engine/StreamCopyExportEngine.kt), [FFmpegEngine.kt](app/src/main/java/com/novacut/editor/engine/FFmpegEngine.kt) (concat helper).

**Risks:** Container codec compatibility (concat demuxer requires same codec params); fallback to full Transformer when impossible.

**Verification:** Export matrix smoke test fixture: 60s timeline with 2 untouched clips + 1 trimmed clip + 1 effect clip → expect 3 fast-trim runs + 1 re-encode run + 1 concat output.

**Complexity:** M. **Priority:** P1 (the highest-leverage performance win in the export path).

---

## Reliability, Security, Privacy, Data Safety

### Reliability + data safety

- **Project recovery probe (Highest-Value #1)** — the single biggest reliability win.
- **Autosave format upgrade strategy** — `ProjectAutoSave.kt` is 91 KB; deserialization is defensive (`opt*` everywhere, `safeValueOf`). Good. But there is no explicit *schema version* gate in JSON itself; the file uses Android Room migration for the DB but autosave is JSON. Recommend: add `"schemaVersion": 6` at the top of every autosave JSON; refuse to load a schema *newer* than the running app (show "Project needs newer NovaCut" dialog). Lower complexity, high payoff. Pairs with R5.4d disclosure.
- **`.partial.*` cleanup is correct in `data_extraction_rules.xml`** — verified — excludes partial writes from cloud backup + device transfer.

### Security + privacy

- **No CVEs found in tracked deps for this pass** (Round 5 R5.9a Dependabot is wired). Continue.
- **OkHttp 5.3.2 is on a current train.** ONNX Runtime 1.26.0 / MediaPipe 0.10.35 are current. No action.
- **`largeHeap=true` is intentional** for video editing — keep but note it disables some Android memory protections; the offset is necessary for the workload.
- **`INTERNET` + `ACCESS_NETWORK_STATE`** are the only network permissions; model download is the only outbound traffic; OkHttp is wired but not exercised until cloud features ship. Good.
- **`enableOnBackInvokedCallback="true"`** — confirmed correct; predictive back is on.
- **No `<queries>` block in manifest** — fine for current behaviour (no third-party app discovery); add if/when Camera intent fallback needs to check resolver presence on Android 11+.
- **Diagnostic ZIP redaction tests cover URIs, file://, /storage/, /data/data/, URLs with query strings, email addresses.** Add a property-based test for arbitrary "potentially personal" content if/when Highest-Value #15 lands.

### Missing guardrails

- **Cloud effect call-out sheet (R5.9c) is still `[ ]`.** Whenever a future generative-video provider ships, the one-time "this will leave your device" sheet must land first.
- **Signed C2PA MP4 embedding (R8.2)** — engine has unsigned sidecar today; real C2PA value requires inline embedding. Defer until c2pa-android AAR ships + 16 KB verified.
- **Project ID collision recovery (`ProjectArchive.importArchiveWithReport()`)** — already exists with `REGENERATE` default. Good.

### Recovery and rollback needs

- **Per-clip recoverability** — see Highest-Value #1.
- **Per-render recoverability** — `ExportService` cancels cleanly; failed exports delete partial outputs (verified in CHANGELOG). Good.
- **Per-AI-tool cancellation** — `aiJob` reference + `cancelAiTool()`; `CancellationException` re-thrown after toast (per CLAUDE.md). Good.

### Logging / diagnostics

- `Log.e` on Transformer error and catch block; `Log.w` on autosave consecutive failure ≥3; `Log.w` on unknown action ID. Good.
- `Log.d` from stub engines is intentional and harmless.

---

## UX, Accessibility, and Trust

### Onboarding gaps

- **First-run tutorial exists** ([FirstRunTutorial.kt](app/src/main/java/com/novacut/editor/ui/editor/FirstRunTutorial.kt)) and is resettable from Settings. Good.
- **No "first project" guided flow** — Projects gallery has a "Create First Project" CTA but doesn't *guide* the first 60 seconds. CapCut and KineMaster both do a tutorial overlay on the first cut/split/export. Consider an opt-in 90-second walkthrough on first editor open, similar to `FirstRunTutorial` but for editor-specific gestures. (Already partially there — extend.)
- **Crash recovery dialog** — exists per `RecoveryDialogTest.kt`; ensure it pairs with project recovery probe (Highest-Value #1).

### Empty / loading / error / disabled states

- **Empty timeline:** `editor_empty_title="Build your first cut."` is excellent copy.
- **Preview gap:** `preview_gap_title="No frame at this moment"` + "Jump to Content" — well-designed.
- **No-clip AI tool tap:** "Select a clip to use {toolName}" toast — good but should not double-fire if the user taps multiple disabled tools in one breath; debounce by tool ID.
- **Model-gated AI tool tap:** see Highest-Value #10.

### Destructive / irreversible actions

- Clip delete has "Confirm before delete" setting + 50-level undo — verified.
- Project delete has confirmation + clears autosave + clears recovery — verified.
- Template delete has confirmation — verified.
- **Bulk delete (`bulk_undo_message`) shows undo snackbar** — good.
- **Render output deletion on failure** — verified per CHANGELOG.
- **Missing: an "Are you sure you want to apply Cut Assistant's 47 cuts?" pre-flight summary** on a Cut Assistant apply, especially when reclaimed duration is >30% of timeline.

### Settings clarity

- Settings is well-laid out with section headers and descriptions for every row. Strong.
- "Wi-Fi-only model downloads" toggle is present and respected by `ModelDownloadManager`.
- "Privacy" section is missing as a top-level — see Highest-Value #8.

### Accessibility

- TalkBack semantics for timeline clips with name/type/track/duration/start + custom actions for split/delete/nudge — verified (R5.3a, v3.74.4).
- Caption Style Gallery has WCAG-AA / large-text / reduced-motion presets — verified (R5.3c, v3.74.9).
- Keyboard timeline editing (arrow nudge / shift-arrow / split / delete) — verified (R5.3b, v3.74.8).
- **Custom Compose semantics on heavy panels are a regression risk** — `Timeline.kt`, `KeyframeCurveEditor.kt`, `ColorGradingPanel.kt`, `MaskEditorPanel.kt` are large and easy to break semantics during refactor. Recommend: lock semantics with screenshot golden tests (Roborazzi) on key panels. R5.8 explicitly listed but not requested yet — promote once Highest-Value #4 (Keyframe graph panel) lands.
- **Closed audio-description audio track (R5.3d)** — still `[ ]`. TTS is wired; muxing via Media3 is the missing piece.

### Microcopy and trust signals

- Privacy copy throughout is good ("On-device AI – no internet required", "Imports stay available offline").
- AI disclosure copy needs the pre-export preview (Highest-Value #2).
- HDR confidence copy is excellent (per CHANGELOG R8 entries).
- Diagnostic ZIP "saves app, device, codec, model, and redacted log details. Project files, media, captions, and transcripts are never included." — exemplary.

---

## Architecture and Maintainability

### Module boundaries

- Single-module app (`app/`). Plenty of code; consider splitting `engine/` into a Gradle sub-module once Adjustment Layer + Compound Clip UI lands; lets pure-Kotlin engines drop Android deps and get faster JVM unit-test cycles. **Defer for now** — increases build complexity for unclear payoff at current scale.

### Refactor candidates

1. **`Timeline.kt` decomposition** (Existing Feature Improvement A).
2. **`VideoEngine.kt` (61 KB)** — exhibit B for delegate extraction once Tier B.5 composer lands; export, preview, thumbnail extraction, freeze-frame extraction can split.
3. **`ShaderEffect.kt` (73 KB)** — the GLSL string literals here are massive; consider moving them to `app/src/main/assets/shaders/*.glsl` with `BufferedReader.readText()` at engine init. Pairs with R5.6c reproducible build pin and stable-asset SHA tracking.
4. **`ProjectAutoSave.kt` (91 KB)** — the single largest engine file; the schema migration is in-code rather than data-driven. Once schema version field lands, the migration table can move to its own class.
5. **`AiFeatures.kt` (2683 lines)** — was extracted from EditorViewModel before Round 5; consider splitting by ML domain (captions / vision / audio / motion).

### Test gaps

- Zero Roborazzi / Compose UI tests today (R5.8 is parked).
- Zero instrumented (Espresso) tests today.
- 62 JVM unit tests; coverage skewed toward engines, not UI delegates. AiToolsDelegate.kt (43 KB) and ClipEditingDelegate.kt (35 KB) have no direct tests visible.
- No Roborazzi for the 14+ panel surfaces (Effects, Mask, Color, Keyframe, Speed, Transitions, Audio Mixer, AI Tools, Export, Caption Editor, Sticker Picker, Cut Assistant Review, Tts, Marker List).

### Documentation gaps

- `docs/` is good (`models.md`, `templates.md`, three Media3-decision docs).
- Add: `docs/architecture.md` describing the EditorViewModel delegate split, the engine→panel binding pattern, and the model schema migration story.
- Add: `docs/release.md` for the tag → CI → APK upload flow (currently embedded in `.github/workflows/build.yml`).
- Add: `docs/privacy.md` mirroring the Privacy Dashboard data (Highest-Value #8) as a human-readable reference.

### Release / build / deployment gaps

- **CI not running on push/PR** (Improvement C).
- **ABI splits not configured** (R5.6d).
- **PAD pack scaffolding** (R5.6a) — pending. The single biggest install-size win once any Tier A model activates.
- **F-Droid build configuration** (R5.6b) — pending; needs explicit flavor that excludes ffmpeg-kit-16kb (GPL-3 obligation material) or routes to verified LGPL-only flavor.
- **Reproducible builds** (R5.6c) — pending; lock Gradle / AGP / Kotlin / JDK in `gradle.properties` + commit lockfile.

---

## Prioritized Roadmap

Each item starts P0 / P1 / P2 / P3 (priority) and ends with verification. Items annotated `[ROADMAP→<id>]` cross-reference [ROADMAP.md](ROADMAP.md).

### Phase 1 — Trust, recovery, repo hygiene (next 1–2 cycles)

- [ ] **P0 — Project recovery + missing-media relink UX** *(Highest-Value #1)*
  - Why: silent failure on revoked URIs is the #1 reliability gap. Per-URI grant model means this is *expected*.
  - Evidence: `ProjectArchive.importArchiveWithReport()` surfaces unresolved URIs but editor open does not.
  - Touches: `model/Clip.kt`, `engine/ProjectAutoSave.kt`, `ui/editor/Timeline.kt`, `ui/editor/EditorViewModel.kt`, `ui/editor/ClipEditingDelegate.kt`.
  - Acceptance: a project opened after the user revokes its media's content grant renders dangling clips with a hatch and a Tap-to-relink action; relink swaps source URI without losing trim ranges.
  - Verify: new JVM unit test for probe + relink + autosave round-trip; manual instrumentation (clear media grant → reopen).

- [ ] **P0 — Pre-export AI provenance preview** *(Highest-Value #2)*
  - Why: trust moment is before render, not in a sidecar.
  - Evidence: [AiUsageLedger.kt](app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt), `ExportSheet` already has "Disclose AI use" toggle.
  - Touches: [ExportSheet.kt](app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt).
  - Acceptance: ExportSheet shows a third confidence row "AI use" with one chip per effectKind × disclosure level; tap expands per-clip detail.
  - Verify: manual; reuse existing `AiUsageLedgerTest`.

- [ ] **P1 — Repo hygiene: `.gitignore` truth + HostShield removal** *(Existing Feature Improvement B)*
  - Why: contradictory `.gitignore` claims confuse contributors; HostShield files at root are unrelated.
  - Evidence: `git ls-files` shows `ROADMAP.md` / `CHANGELOG.md` / `CODEX_CHANGELOG.md` are tracked despite being in `.gitignore`. `HostShield-Research-Report.md` + `research/` are local-only but visible.
  - Touches: [.gitignore](.gitignore), new tracked-files audit test.
  - Acceptance: `git ls-files | grep -E '^HostShield|^research/'` is empty; audit test green.
  - Verify: `:app:testDebugUnitTest`.

- [ ] **P1 — CI: run on push/PR, not only tag** *(Existing Feature Improvement C)*
  - Why: 146 commits between `v3.73.2` and `v3.74.9` were CI-unverified.
  - Evidence: [.github/workflows/build.yml](.github/workflows/build.yml) triggers only on `workflow_dispatch` / `tags/v*`.
  - Touches: [.github/workflows/build.yml](.github/workflows/build.yml).
  - Acceptance: next push to `master` triggers tests + 16 KB check + debug build; green badge in README.
  - Verify: GitHub Actions run.

- [ ] **P1 — Autosave schema version field**
  - Why: data-safety; refuse to load a future-schema project rather than crash.
  - Evidence: [ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt) (91 KB, no schemaVersion top-level field).
  - Touches: [ProjectAutoSave.kt](app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt), `Project.kt`.
  - Acceptance: every new autosave includes `"schemaVersion": N`; loader rejects N+1 with a user-facing dialog "This project needs a newer NovaCut".
  - Verify: new `AutoSaveSchemaVersionTest` with future-schema fixture.

### Phase 2 — Tier C engine→UI integrations (cycles 2–4)

- [ ] **P1 — Adjustment-layer track surface** *(Highest-Value #3 / [ROADMAP→C.11])*
  - Why: engine done; UI is the bottleneck.
  - Touches: `model/Track.kt`, `engine/EffectBuilder.kt`, `ui/editor/Timeline.kt`, `EditorViewModel.kt`.
  - Acceptance: adding an `ADJUSTMENT` track and dropping a colour effect on it visibly changes preview+export of clips beneath.
  - Verify: existing `AdjustmentLayerEngineTest`; new preview/export parity test.

- [ ] **P1 — Keyframe bezier graph panel** *(Highest-Value #4 / [ROADMAP→C.12])*
  - Touches: new [KeyframeGraphPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/KeyframeGraphPanel.kt).
  - Acceptance: per-property graph with bezier handles + 12 preset chips + scrubber.
  - Verify: existing `KeyframeBezierGraphTest`; screenshot golden at 360/600/840 dp.

- [ ] **P1 — Compound clip open/exit gesture** *(Highest-Value #5 / [ROADMAP→C.13])*
  - Touches: `EditorViewModel.kt`, `EditorScreen.kt`, `Timeline.kt`, `RadialActionMenu.kt`.
  - Acceptance: long-press compound → opens children timeline with breadcrumb; back gesture pops the stack.
  - Verify: existing `CompoundNavStackTest`; manual instrumentation.

- [ ] **P1 — Cut Assistant + Filler Removal panel merge** *(Highest-Value #6)*
  - Touches: `CutAssistantReviewPanel.kt`, `FillerRemovalPanel.kt`, `AiToolsDelegate.kt`.
  - Acceptance: single review panel with filter chips; one apply → one undo entry.
  - Verify: existing engine tests; UI smoke.

- [ ] **P1 — Caption translation editor row UI** *(Highest-Value #7 / [ROADMAP→R5.4a])*
  - Touches: [CaptionEditorPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/CaptionEditorPanel.kt).
  - Acceptance: per-row source+target rendering with state colour; Regenerate action.

- [ ] **P1 — Privacy Dashboard panel** *(Highest-Value #8 / [ROADMAP→R5.5c])*
  - Touches: new `PrivacyDashboardPanel.kt`, Settings entry row.
  - Acceptance: per-category rows with Export/Delete/Opt-out actions.

- [ ] **P1 — Photo Picker for sticker import** *(Highest-Value #9)*
  - Touches: [StickerPickerPanel.kt](app/src/main/java/com/novacut/editor/ui/editor/StickerPickerPanel.kt).
  - Acceptance: image-sticker import uses `PickVisualMedia`.

- [ ] **P1 — Pre-flight model-availability sheet** *(Highest-Value #10)*
  - Touches: new `AiModelRequirementSheet.kt`, `AiToolsPanel.kt`, `AiToolsDelegate.kt`.

### Phase 3 — Export and platform improvements (cycles 4–6)

- [ ] **P1 — Tier B.5 mixed copy/re-encode composer** *(Existing Feature Improvement G / [ROADMAP→B.5])*
  - Why: FFmpegKit-16kb now active; concat demuxer unblocks segment composition.
  - Touches: `SmartRenderEngine.kt`, `VideoEngine.kt`, `StreamCopyExportEngine.kt`, `FFmpegEngine.kt`, `ExportSheet.kt`.
  - Acceptance: 60s mixed-edit timeline exports with per-range badges in ExportSheet; 3 fast-trim + 1 re-encode + 1 concat output.

- [ ] **P1 — Tier A.10 Oboe resampler activation** *([ROADMAP→A.10])*
  - Touches: `gradle/libs.versions.toml`, `app/build.gradle.kts`, [OboeResamplerEngine.kt](app/src/main/java/com/novacut/editor/engine/OboeResamplerEngine.kt).
  - Acceptance: 44.1↔48 kHz mixing passes through Oboe `MultiChannelResampler`; APK 16 KB verify passes.

- [ ] **P2 — Project-wide working colour space + display transform** *(Highest-Value #13)*
  - Touches: `model/Project.kt`, `ExportColorConfidenceEngine.kt`.

- [ ] **P2 — RTL bidi text in overlays** *(Highest-Value #14 / [ROADMAP→R5.4b])*
  - Touches: `StrokedTextBitmapOverlay.kt`, `ExportTextOverlay.kt`, `Timeline.kt`.

- [ ] **P2 — App Shortcuts** *(Highest-Value #11)*
  - Touches: new `res/xml/shortcuts.xml`, manifest, `ProjectListViewModel.kt`.

- [ ] **P2 — Send-to-NovaCut for image/audio** *(Highest-Value #12)*
  - Touches: manifest, `MainActivity.handleIncomingIntent`.

- [ ] **P2 — Diagnostic ZIP timeline-shape opt-in** *(Highest-Value #15)*
  - Touches: `DiagnosticExportEngine.kt`, `SettingsScreen.kt`.

### Phase 4 — Architecture and release polish (cycles 5–8)

- [ ] **P2 — Timeline.kt decomposition** *(Existing Feature Improvement A)*
- [ ] **P2 — Release runbook in PROJECT_CONTEXT.md** *(Existing Feature Improvement D)*
- [ ] **P2 — ABI splits + PAD scaffolding** *([ROADMAP→R5.6a / R5.6d])*
- [ ] **P3 — `strings.xml` panel comments** *(Existing Feature Improvement E)*
- [ ] **P3 — README version drift fix** *(Existing Feature Improvement F)*

---

## Quick Wins (S complexity, P0 / P1)

- ✅ Pre-export AI provenance preview (1 panel addition)
- ✅ Photo Picker for sticker import (1 contract swap)
- ✅ App Shortcuts (1 xml file + 1 manifest meta-data)
- ✅ Send-to-NovaCut for image/audio (2 manifest filters)
- ✅ Diagnostic ZIP timeline-shape opt-in (1 engine + 1 settings row)
- ✅ Repo `.gitignore` hygiene + audit test
- ✅ CI on push/PR (1 trigger block)
- ✅ Pre-flight model-availability sheet (1 composable)
- ✅ Cut Assistant + Filler Removal panel merge (delete one panel, add chips to the other)
- ✅ Caption translation editor rows (panel addition; engine done)

---

## Larger Bets

- Project recovery + relink UX (M, P0 trust)
- Adjustment-layer track surface (M, P1 — pro-NLE staple)
- Keyframe bezier graph panel (M, P1 — pro-NLE staple)
- Tier B.5 mixed copy/re-encode composer (M, P1 — biggest perf win in export)
- Tier A.10 Oboe resampler activation (S–M, P1)
- Compound clip nav (S, P1 — pro-NLE staple)
- RTL bidi + CJK render verification (M, P2 — i18n trust)
- Privacy Dashboard panel (S, P1)
- Project-wide working colour space (M, P2 — unblocks every future HDR change)
- Eventually: Tier A.6 RVM matting, A.4 RIFE interp, A.5 Real-ESRGAN, A.7 SAM 2.1, A.3 OpenCV stab — but only after PAD packs ship (R5.6a) and per-model checksum + license is closed in `docs/models.md` (R7.2 sets the template; new rows must follow it).

---

## Explicit Non-Goals (for this planning pass)

- **Adding new speculative stub engines.** The repo already has 20+ scaffolded engines; the next-cycle work is to turn shipped engines into shipped UI, not to expand the scaffold count. Round 7/8 said this; this pass confirms.
- **Cross-device cloud project sync** ([ROADMAP→C.16], [ProjectSyncEngine.kt](app/src/main/java/com/novacut/editor/engine/ProjectSyncEngine.kt)) — XL effort, unclear creator demand, dilutes the local-first product identity. Keep deferred.
- **OpenCut Rust GPU compositor port** ([ROADMAP→R6.20]) — track only; the architecture lessons can be borrowed without a port.
- **SAM 3 / SAM 3.1 mobile activation** ([ROADMAP→R6.4]) — no mobile-viable ONNX export yet; placeholder is correct.
- **Always-cloud ASR/TTS that removes the offline fallback** — already a roadmap rejected item; reinforces here.
- **Bundling GPL-only native libs** without a dual-license track — already rejected.
- **OS-level "Edit in NovaCut" context-menu hook** — Android `ACTION_EDIT` and existing `VIEW` filter cover this for video; expand to image/audio (Highest-Value #12) instead.
- **A general LLM in-app for caption rewrite / template selection** ([ROADMAP→R6.3 Gemini Nano] or R8.17 Gemma 3) — premium-device-gated; revisit when MediaPipe LLM Inference benchmarks justify the bundle size on mid-tier devices.
- **Stem separation (Demucs)** ([ROADMAP→C.1]) — STFT pre/post pipeline is large engineering cost relative to current creator demand. Defer.

---

## Open Questions

> *Only questions that block correct prioritization or implementation appear here. Anything answerable by reading the repo or public docs is omitted.*

1. **Is `v3.74.9` actually being shipped to anyone?** Latest local tag is `v3.73.2`. If no, the highest-value work might be cutting `v3.75.0` first (which would pull in all the Round 7/8 polish). The 117 MB local `NovaCut-v3.72.0.apk` blob and `keystore.properties` suggest a manual cut workflow that hasn't fired yet in 2026-05. CI on push (Improvement C) would catch the next regression automatically. **Status:** Likely blocked on user decision; does not block other work.
2. **Should `CODEX_CHANGELOG.md` stay tracked or move to local-only?** Currently both gitignored *and* tracked. If "this is the AI agent's working log" — keep tracked. If "this is local agent state" — `git rm --cached`. **Status:** Surface to user; tag P1.
3. **Is the user planning to release on F-Droid?** R5.6b is "in progress" but the GPL-3 obligation posture from FFmpegKit-16kb means an F-Droid build needs a flavor that excludes that dep. The work is doable but the decision (do we ship two flavors? do we accept GPL-3 obligation across the board?) hasn't been recorded. **Status:** Decision needed before activating F-Droid track.
4. **Is the autosave schema version field landing inside the existing `ProjectAutoSave` JSON or as a sidecar?** The existing autosave never had a `schemaVersion` field; adding one is back-compat-safe (`opt*` parsing accepts missing fields), but the *meaning* of older autosaves becomes "schema 0". Confirm. **Status:** Implementer's call; no user input needed.

---

## Appendix: Notable raw findings

- **Local working tree size:** ~120 MB (mostly the bundled v3.72.0 APK at root).
- **`git log --oneline | wc -l`:** 278 commits total. Commit cadence high — 61 commits in last 10 days.
- **Local `master` was ahead of `origin/master` by 33 commits per STATE_OF_REPO 2026-05-17; assume similar today (untracked AGENTS.md is the only `?? ` in `git status`).**
- **`local.properties` points at `C:\Users\--\AppData\Local\Android\Sdk` per the consolidation note** — correct for this VM.
- **One stale `Untracked file: AGENTS.md`** — intentional per PROJECT_CONTEXT.md.
- **Room schema versions on disk: 4.json, 5.json, 6.json.** Live DB is `v6`.

— end —

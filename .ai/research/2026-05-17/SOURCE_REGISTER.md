# Source Register - 2026-05-17

Every meaningful roadmap claim in this research run should trace to one of these sources. Local sources were read from the working tree; external sources were searched or fetched during the run.

## Local Repository Sources

| ID | Source | Use |
|---|---|---|
| L01 | `git status --short --branch` | Branch and ahead-count state. |
| L02 | `git log -10 --oneline --decorate` | Recent development history. |
| L03 | `git remote -v` | Remote repository identity. |
| L04 | `git branch -vv` | Upstream tracking state. |
| L05 | `git tag --sort=-creatordate` | Recent tag state. |
| L06 | `README.md` | Product summary, current feature surface, tech stack. |
| L07 | `ROADMAP.md` | Existing roadmap and shipped/planned tiers. |
| L08 | `CHANGELOG.md` | Release-history evidence. |
| L09 | `CROSS-PROJECT-ROADMAP.md` | Cross-project backlog source and stale version example. |
| L10 | `AGENTS.md` | Codex instruction bridge. |
| L11 | `CLAUDE.md` | Local Claude working notes and stale-claim inventory. |
| L12 | `.claude/CLAUDE.md` | Nested local Claude notes. |
| L13 | `app/build.gradle.kts` | SDK, version, signing, dependency setup. |
| L14 | `gradle/libs.versions.toml` | Dependency versions. |
| L15 | `app/src/main/res/values/strings.xml` | Visible app version string. |
| L16 | `app/src/main/java/com/novacut/editor/NovaCutApp.kt` | Runtime version string source. |
| L17 | `docs/models.md` | Model registry, licenses, sizes, SHA gates, delivery posture. |
| L18 | `docs/templates.md` | Template/plugin and animation compatibility matrix. |
| L19 | `app/src/main/java/com/novacut/editor/engine/DiagnosticsExportEngine.kt` | Local diagnostic export capability. |
| L20 | `app/src/main/java/com/novacut/editor/viewmodel/SettingsViewModel.kt` | Settings state and missing diagnostic workflow evidence. |
| L21 | `app/src/main/java/com/novacut/editor/screen/SettingsScreen.kt` | Settings UI surface. |
| L22 | `app/src/main/java/com/novacut/editor/engine/ModelDownloadManager.kt` | Model download/validation architecture. |
| L23 | `app/src/main/java/com/novacut/editor/engine/SmartRenderEngine.kt` | Smart render planner state. |
| L24 | `app/src/main/java/com/novacut/editor/engine/OboeResamplerEngine.kt` | Oboe activation scaffold. |
| L25 | `app/src/main/java/com/novacut/editor/engine/CutAssistantEngine.kt` | Auto-cut/filler detection scaffold. |
| L26 | `app/src/main/java/com/novacut/editor/engine/CompoundNavStack.kt` | Nested-sequence navigation scaffold. |
| L27 | `app/src/main/java/com/novacut/editor/engine/KeyframeBezierGraph.kt` | Keyframe graph data model scaffold. |
| L28 | `.github/dependabot.yml` | Dependency update automation presence. |
| L29 | `.github/workflows/build.yml` | CI/build workflow evidence. |
| L30 | `.gitignore` | Ignored local instruction/config files. |
| L31 | `local.properties` | Local-only SDK path issue. Not committed. |
| L32 | `app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt` | Translation model variant compile fix and model roadmap evidence. |
| L33 | `app/src/main/java/com/novacut/editor/model/Project.kt` | Clip model evidence for compound-clip breadcrumb fix. |
| L34 | `app/src/main/java/com/novacut/editor/engine/SpeakerSwitchPlanner.kt` | Multicam SmartSwitch planner verification fix. |
| L35 | `app/src/test/java/com/novacut/editor/engine/AdjustmentLayerEngineTest.kt` | Adjustment-layer test-contract fix. |
| L36 | `app/src/test/java/com/novacut/editor/engine/CompoundNavStackTest.kt` | Compound-nav test fixture fix. |
| L37 | `app/src/main/java/com/novacut/editor/ui/editor/PreviewPanel.kt` | Local preview UI requirements for R6.10b Media3 Compose evaluation. |
| L38 | `app/src/main/java/com/novacut/editor/ui/editor/MiniPlayerBar.kt` | Timeline-relative seek contract evidence for R6.10b. |
| L39 | `app/src/main/java/com/novacut/editor/ui/editor/PreviewPanelMedia3ComposePolicy.kt` | Codified R6.10b adoption policy. |
| L40 | `app/src/test/java/com/novacut/editor/ui/editor/PreviewPanelMedia3ComposePolicyTest.kt` | Regression tests for the R6.10b decision. |
| L41 | `docs/preview-media3-compose.md` | Source-backed R6.10b decision note. |
| L42 | `app/src/main/java/com/novacut/editor/engine/VideoEngine.kt` | Current thumbnail, thumbnail strip, and freeze-frame extraction paths for R6.10c. |
| L43 | `app/src/main/java/com/novacut/editor/engine/FrameExtractionPolicy.kt` | Codified R6.10c migration policy. |
| L44 | `app/src/test/java/com/novacut/editor/engine/FrameExtractionPolicyTest.kt` | Regression tests and source-tree import guard for R6.10c. |
| L45 | `docs/frame-extraction-media3-inspector.md` | Source-backed R6.10c decision note. |
| L46 | `app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt` | Timeline ruler requirements for R6.10d ProgressSlider evaluation. |
| L47 | `app/src/main/java/com/novacut/editor/ui/editor/TimelineProgressSliderPolicy.kt` | Codified R6.10d non-adoption policy. |
| L48 | `app/src/test/java/com/novacut/editor/ui/editor/TimelineProgressSliderPolicyTest.kt` | Regression tests for R6.10d. |
| L49 | `docs/progress-slider-media3-compose.md` | Source-backed R6.10d decision note. |
| L50 | `app/src/main/java/com/novacut/editor/engine/ExportColorConfidenceEngine.kt` | R6.11b APV source summary and ExportSheet chip input. |
| L51 | `app/src/test/java/com/novacut/editor/engine/ExportColorConfidenceEngineTest.kt` | Regression tests for the R6.11b APV chip and distinct-source counting. |
| L52 | `app/src/main/java/com/novacut/editor/engine/MediaImportEngine.kt` | R6.12a Android 16 gainmap direction classification. |
| L53 | `app/src/main/java/com/novacut/editor/model/Project.kt` | R6.12a persisted source HDR format enum. |
| L54 | `app/src/test/java/com/novacut/editor/engine/MediaImportEngineTest.kt` | Regression tests for R6.12a gainmap direction classification. |
| L55 | `docs/models.md` | R6.12c ISO 21496-1 v1/v2 distinction note. |

## Official Platform and Library Sources

| ID | Source | URL | Use |
|---|---|---|---|
| E01 | Android 16 KB page-size guidance | https://developer.android.com/guide/practices/page-sizes | Native library release gate and Android 16 readiness. |
| E02 | Android target SDK requirements | https://developer.android.com/google/play/requirements/target-sdk | Play target SDK context. |
| E03 | AndroidX Media3 releases | https://developer.android.com/jetpack/androidx/releases/media3 | Media3 release and module tracking. |
| E04 | Media3 Transformer composition docs | https://developer.android.com/media/media3/transformer/composition | Composition/export architecture comparison. |
| E05 | Media3 release repository | https://github.com/androidx/media/releases | Release evidence and issue cross-checking. |
| E06 | Android LiteRT docs | https://developer.android.com/ai/edge/litert | NNAPI/LiteRT migration planning. |
| E07 | Android Gradle Plugin Maven metadata | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml | Current/pre-release AGP stream check. |
| E08 | Media3 Transformer Maven metadata | https://dl.google.com/dl/android/maven2/androidx/media3/media3-transformer/maven-metadata.xml | Confirmed current Media3 1.10.1. |
| E09 | Compose BOM Maven metadata | https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml | Compose update opportunity. |
| E10 | Room Maven metadata | https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml | Room update opportunity. |
| E11 | WorkManager Maven metadata | https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml | WorkManager update opportunity. |
| E12 | Kotlin Android Gradle plugin Maven metadata | https://repo1.maven.org/maven2/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml | Kotlin toolchain stream check. |
| E13 | Hilt Maven metadata | https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml | Hilt update opportunity. |
| E14 | ONNX Runtime Android Maven metadata | https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/maven-metadata.xml | ONNX Runtime update opportunity. |
| E15 | OkHttp Maven metadata | https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml | OkHttp update opportunity and major-version caution. |
| E16 | Lottie Compose Maven metadata | https://repo1.maven.org/maven2/com/airbnb/android/lottie-compose/maven-metadata.xml | Lottie update opportunity. |
| E17 | ffmpeg-kit upstream archive | https://github.com/arthenica/ffmpeg-kit | FFmpeg distribution risk. |
| E18 | AndroidDeepFilterNet Maven Central | https://central.sonatype.com/artifact/io.github.kaleyravideo/android-deepfilternet | A.2 runtime coordinate, license, and POM evidence. |
| E19 | AndroidDeepFilterNet Maven metadata | https://repo.maven.apache.org/maven2/io/github/kaleyravideo/android-deepfilternet/maven-metadata.xml | Latest/release version evidence for A.2 activation. |
| E20 | Media3 effect-lottie Maven metadata | https://dl.google.com/dl/android/maven2/androidx/media3/media3-effect-lottie/maven-metadata.xml | R7.5 / R6.10a latest/release evidence for `media3-effect-lottie:1.10.1`. |
| E21 | Media3 `LottieOverlay` API reference | https://developer.android.com/reference/androidx/media3/effect/lottie/LottieOverlay | Official API surface for the adopted Lottie renderer. |
| E22 | Media3 `LottieOverlay.Builder` API reference | https://developer.android.com/reference/androidx/media3/effect/lottie/LottieOverlay.Builder | Builder API evidence for supplied drawable, static settings, and speed. |
| E23 | Media3 release notes | https://developer.android.com/jetpack/androidx/releases/media3 | Release-note evidence that 1.10.x introduced/moved the Lottie module. |
| E24 | Media3 effect-lottie AAR | https://dl.google.com/dl/android/maven2/androidx/media3/media3-effect-lottie/1.10.1/media3-effect-lottie-1.10.1.aar | Resolved AAR bytes; SHA-256 `83b26f6f25e785b949263fc52cb7c0fb5f0e371445fa1d7b9a0ed0b71c05e69d`. |
| E25 | Media3 Compose UI overview | https://developer.android.com/media/media3/ui/compose | R6.10b evidence for `media3-ui-compose` vs `media3-ui-compose-material3`, `ContentFrame`, and `PlayerSurface`. |
| E26 | Media3 Material3 Compose docs | https://developer.android.com/media/media3/ui/compose-material3 | R6.10b evidence for Material3 `Player`, playback controls, progress, and internal state management. |
| E27 | Media3 `ProgressSlider` API reference | https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/material3/indicator/ProgressSlider.composable | R6.10b/R6.10d evidence that `ProgressSlider` is player-position based and performs underlying player seek internally. |
| E28 | Media3 UI Compose package API reference | https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/package-summary | R6.10b evidence for lower-level `ContentFrame` and `PlayerSurface` surfaces. |
| E29 | Media3 UI Compose Material3 Maven metadata | https://dl.google.com/dl/android/maven2/androidx/media3/media3-ui-compose-material3/maven-metadata.xml | Latest/release evidence for `media3-ui-compose-material3:1.10.1`; lastUpdated `20260512123518`. |
| E30 | Media3 UI Compose Material3 AAR | https://dl.google.com/dl/android/maven2/androidx/media3/media3-ui-compose-material3/1.10.1/media3-ui-compose-material3-1.10.1.aar | Resolved AAR bytes; SHA-256 `0e0789cef85d948f924c0cec365021a56f6cc63b8c9888cacd05357f83e00112`. |
| E31 | Media3 Inspector docs | https://developer.android.com/media/media3/inspector | R6.10c evidence for `MetadataRetriever`, `FrameExtractor`, `MediaExtractorCompat`, and platform API replacements. |
| E32 | Media3 inspector-frame Maven metadata | https://dl.google.com/dl/android/maven2/androidx/media3/media3-inspector-frame/maven-metadata.xml | Latest/release evidence for `media3-inspector-frame:1.10.1`; lastUpdated `20260512123518`. |
| E33 | Media3 inspector-frame AAR | https://dl.google.com/dl/android/maven2/androidx/media3/media3-inspector-frame/1.10.1/media3-inspector-frame-1.10.1.aar | Resolved AAR bytes; SHA-256 `ded4a5275a5f977afaa3fb4b1b933667629e2526efbfb94b4bcf2b96fc20e2a0`; no native entries. |
| E34 | Media3 inspector-frame sources JAR | https://dl.google.com/dl/android/maven2/androidx/media3/media3-inspector-frame/1.10.1/media3-inspector-frame-1.10.1-sources.jar | Source inspection for `androidx.media3.inspector.frame.FrameExtractor`, `setEffects`, `setMediaCodecSelector`, `setExtractHdrFrames`, `getFrame`, and `getThumbnail`; SHA-256 `a03b962a242236b87fde0272c5478c2e6b7fc520288d3d58bb0f2e866d827654`. |
| E35 | Android 16 release notes | https://source.android.com/docs/whatsnew/android-16-release | R6.11 APV codec and R6.12 Ultra HDR v2 platform source. |
| E36 | SamMobile Galaxy S26 APV report | https://www.sammobile.com/news/galaxy-s26-ultra-world-first-phone-apv-codec-support/ | Non-official market-signal source for first-device APV ingest claim in the roadmap. |
| E37 | Android `Gainmap` API reference | https://developer.android.com/reference/android/graphics/Gainmap | R6.12a `GAINMAP_DIRECTION_SDR_TO_HDR`, `GAINMAP_DIRECTION_HDR_TO_SDR`, and Android 16 `getGainmapDirection()` source. |
| E38 | Android Ultra HDR image format guidance | https://developer.android.com/media/platform/hdr-image-format | R6.12c ISO 21496-1 compatibility and encode/decode guidance. |

## Open-Source Competitor and Adjacent Sources

| ID | Source | URL | Use |
|---|---|---|---|
| O01 | OpenCut | https://github.com/OpenCut-app/OpenCut | Open-source CapCut-positioned competitor; local-first/editor UX lessons. |
| O02 | devhyper/open-video-editor | https://github.com/devhyper/open-video-editor | Direct Android open-source editor reference. |
| O03 | OpenShot | https://github.com/OpenShot/openshot-qt | Mature desktop NLE comparison. |
| O04 | Kdenlive | https://github.com/KDE/kdenlive | Mature open-source NLE comparison. |
| O05 | Shotcut | https://github.com/mltframework/shotcut | Mature open-source NLE and MLT workflow comparison. |
| O06 | LosslessCut | https://github.com/mifi/lossless-cut | Smart-render / stream-copy workflow reference. |
| O07 | OpenTimelineIO | https://github.com/AcademySoftwareFoundation/OpenTimelineIO | Interchange/import/export reference. |
| O08 | Gyroflow | https://github.com/gyroflow/gyroflow | Gyro/lens stabilization reference. |
| O09 | gl-transitions | https://github.com/gl-transitions/gl-transitions | Transition marketplace/format inspiration. |

GitHub metadata fetched during the run:

| Repo | Stars | Forks | Pushed | License |
|---|---:|---:|---|---|
| `OpenCut-app/OpenCut` | 50,904 | 5,495 | 2026-05-17 | MIT |
| `devhyper/open-video-editor` | 654 | 38 | 2026-05-12 | GPL-3.0 |
| `OpenShot/openshot-qt` | 5,769 | 706 | 2026-05-16 | NOASSERTION |
| `KDE/kdenlive` | 5,055 | 416 | 2026-05-17 | GPL-3.0 |
| `mltframework/shotcut` | 13,963 | 1,363 | 2026-05-17 | GPL-3.0 |
| `mifi/lossless-cut` | 40,487 | 1,957 | 2026-05-10 | GPL-2.0 |
| `AcademySoftwareFoundation/OpenTimelineIO` | 1,864 | 329 | 2026-05-01 | Apache-2.0 |
| `gyroflow/gyroflow` | 8,758 | 422 | 2026-05-16 | GPL-3.0 |
| `gl-transitions/gl-transitions` | 2,085 | 321 | 2026-05-03 | NOASSERTION |

## Commercial Product Sources

| ID | Source | URL | Use |
|---|---|---|---|
| C01 | CapCut | https://www.capcut.com/ | Mobile/desktop editor positioning, templates, AI editing, social workflow benchmark. |
| C02 | DaVinci Resolve | https://www.blackmagicdesign.com/products/davinciresolve | Professional editor benchmark. |
| C03 | DaVinci Resolve What's New | https://www.blackmagicdesign.com/products/davinciresolve/whatsnew | AI/editor feature trend source. |
| C04 | CyberLink PowerDirector | https://www.cyberlink.com/products/powerdirector-video-editing-software/features_en_US.html | Mobile/prosumer AI feature comparison. |
| C05 | LumaFusion Android | https://luma-touch.com/lumafusion-for-android/ | Pro mobile editing workflow benchmark. |
| C06 | KineMaster | https://kinemaster.com/ | Mobile editor template/asset/workflow benchmark. |
| C07 | VN Video Editor | https://www.vlognow.me/ | Mobile creator/editor benchmark. |
| C08 | Adobe Premiere Rush end-of-life | https://helpx.adobe.com/premiere-rush/help/premiere-rush-end-of-life.html | Market signal: mobile editor churn and support risk. |

## Model, Dataset, and Integration Sources

| ID | Source | URL | Use |
|---|---|---|---|
| M01 | sherpa-onnx | https://github.com/k2-fsa/sherpa-onnx | On-device ASR/TTS/speech runtime candidate. |
| M02 | whisper.cpp | https://github.com/ggerganov/whisper.cpp | On-device Whisper implementation/reference. |
| M03 | SAM 2 | https://github.com/facebookresearch/sam2 | Tap-to-segment / object mask model reference. |
| M04 | AndroidDeepFilterNet | https://github.com/KaleyraVideo/AndroidDeepFilterNet | Android DeepFilterNet integration reference. |
| M05 | Picovoice open-source translation overview | https://picovoice.ai/blog/open-source-translation/ | MADLAD/Bergamot-style mobile translation comparison. |
| M06 | RTranslator NLnet page | https://nlnet.nl/project/RTranslator/ | Mobile/offline translation integration signal. |
| M07 | MediaPipe Tasks Vision | https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android | Segmentation integration reference. |
| M08 | AndroidDeepFilterNet v0.0.8 source tag | https://api.github.com/repos/KaleyraVideo/AndroidDeepFilterNet/tags | Tag-to-commit evidence for resolved Maven artifact. |
| M09 | Kaleyra DeepFilterNet mobile optimization notes | https://github.com/KaleyraVideo/DeepFilterNet/blob/main/models/deepfilternet_model_optimization.md | Evidence that the bundled mobile path targets optimized DeepFilterNet3 behavior on Android devices. |

GitHub metadata fetched during the run:

| Repo | Stars | Forks | Pushed | License |
|---|---:|---:|---|---|
| `KaleyraVideo/AndroidDeepFilterNet` | 23 | 5 | 2026-04-29 | Apache-2.0 |
| `k2-fsa/sherpa-onnx` | 12,290 | 1,389 | 2026-05-15 | Apache-2.0 |
| `facebookresearch/sam2` | 19,174 | 2,449 | 2026-04-07 | Apache-2.0 |
| `ggerganov/whisper.cpp` | 49,802 | 5,545 | 2026-05-15 | MIT |

## Query Classes Used

The exact browser/search result ranking is not stable, so the durable record is the query class and selected sources above.

- Android 16 16 KB native library requirement.
- AndroidX Media3 1.10.1 Transformer/Lottie effect releases.
- Media3 `media3-effect-lottie` module metadata and `LottieOverlay` API details.
- Android NNAPI deprecation and LiteRT migration.
- ffmpeg-kit archived / Maven distribution / 16 KB forks.
- CapCut / DaVinci Resolve / PowerDirector / LumaFusion / KineMaster / VN feature and positioning.
- Premiere Rush end-of-life and Clipchamp mobile retirement signals.
- OpenCut, open-video-editor, Kdenlive, Shotcut, OpenShot, LosslessCut, OpenTimelineIO, Gyroflow, gl-transitions.
- sherpa-onnx, DeepFilterNet Android, SAM 2 ONNX/mobile, MADLAD/Bergamot, ONNX Runtime Android.

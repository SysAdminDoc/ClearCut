# Feature Backlog - 2026-05-17

This is the raw harvested backlog before final prioritization. It intentionally includes more ideas than should be implemented immediately.

## Platform, Release, and Build Readiness

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| 16 KB native-library verification script | Android 16/Play requirements; targetSdk 36; native deps in ONNX Runtime/MediaPipe/future FFmpeg | Gradle task, release checklist, CI artifact inspection |
| Dependency train with rollback notes | Maven metadata shows several newer release lines | `gradle/libs.versions.toml`, `build.gradle.kts`, unit tests, CI |
| AGP/Kotlin pre-release policy | Maven metadata latest points to pre-release lines | `gradle/libs.versions.toml`, `README.md`, `PROJECT_CONTEXT.md` |
| Release channel matrix | Play, F-Droid, local APK, model delivery, GPL/LGPL FFmpeg variants | README, release checklist, docs/models.md |
| SDK/local.properties setup guard | Local `local.properties` drift blocked Gradle | README troubleshooting, Gradle preflight task |

## Export, Rendering, and Media Pipeline

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| FFmpeg 16 KB integration decision | Roadmap A.9/B.3/B.5; ffmpeg-kit upstream archive | `gradle/libs.versions.toml`, `SmartRenderEngine`, export pipeline |
| Mixed copy/re-encode composer | `SmartRenderEngine.planRuns()` scaffold exists | Smart render export path, tests |
| Reverse export | Roadmap B.3 and FFmpeg requirement | Export engine and UI affordance |
| libass subtitle burn-in | FFmpeg unlock; caption roadmap | Caption export/burn-in engine |
| Two-pass loudnorm | FFmpeg unlock; audio mastering roadmap | Audio export/mix pipeline |
| `media3-effect-lottie` parity spike | Media3 1.10.1 already present; R6.10a now routes eligible overlays through the official renderer | Lottie overlay/template golden frame tests before deleting the fallback shader |
| `media3-ui-compose-material3` Player evaluation | Media3 1.10.1 offers Material3 player widgets, but R6.10b found full replacement would lose NovaCut preview semantics | Revisit only targeted `ContentFrame`, `PlayerSurface`, or playback-button adoption |
| `media3-inspector-frame` migration gate | Media3 1.10 split frame extraction into a new module/import path; R6.10c found no current broken imports | Use for future HDR/effect-aware thumbnails or custom decoder frame extraction |
| Media3 `ProgressSlider` replacement | R6.10d found player-position/internal-seek behavior conflicts with edited-timeline controls | Revisit only if Media3 exposes externally controlled project-timeline progress |
| Dual-texture programmable blend modes | Current single-texture fallback lacks exact pro blend math | Media3/custom GL compositor path |
| Ultra HDR/gainmap export path | R6.12a source direction detection is complete; R6.12b still needs gain-map still-frame encoding | HEIC/JPEG still export, HDR ingest/export tests |
| APV ingest watch item | Android 16 professional codec trend; R6.11b source chip is complete | Future APV decode/device QA beyond the current `video/apv` import flag |

## Diagnostics, Trust, and Recovery

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| Settings diagnostic ZIP UI | `DiagnosticsExportEngine` exists; R5.5d roadmap | `SettingsScreen`, `SettingsViewModel`, share/save flow |
| Export failure evidence panel | Competitor/pro tooling support bundle pattern | Export sheet, diagnostics engine |
| Privacy dashboard integration | PrivacyDashboard model exists | Settings privacy screen, model/cloud toggles |
| Redacted media-path policy | Local-only diagnostics still need safe sharing | Diagnostics engine, tests |
| Model download failure recovery | Model registry and download manager exist | Model management UI, checksum errors |

## Model, AI, and Evaluation

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| Model SHA-256 closure | `docs/models.md` has `SHA TBD` rows | `docs/models.md`, model metadata tests |
| Model license/PAD/F-Droid matrix tests | Model policy is complex and easy to drift | Unit tests around model registry |
| DeepFilterNet 3 activation | Roadmap A.2; AndroidDeepFilterNet reference | `NoiseReductionEngine`, audio tests |
| Moonshine/Whisper ASR evaluation harness | ASR roadmap and model alternatives | Transcript tests, WER fixtures |
| Caption translation model path | R6.7, MADLAD/Bergamot research | Caption translation editor/data model |
| SAM 2.1 Hiera Tiny activation | Tap-to-segment roadmap; SAM source | `TapSegmentEngine`, model downloads |
| RVM/Real-ESRGAN/RIFE PAD bundles | Large models exceed base app comfort | Play Asset Delivery, model manager |
| Local prompt assistant constraints | Gemini Nano/watch items; privacy posture | Reversible suggestions, local fallback |
| On-device model performance matrix | Device-specific ML variability | Benchmark harness, docs/models.md |

## Timeline, Editing, and UX

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| Diagnostic export Settings flow | High-priority trust workflow | Settings UI |
| Keyframe graph editor UI | `KeyframeBezierGraph` data model exists | Keyframe panel, curve editor |
| Compound clip open/exit UX | `CompoundNavStack` exists | Editor timeline gestures, breadcrumb/exit chip |
| Adjustment layers visual model | Planner helper exists | Timeline track model, EffectBuilder bridge |
| Cut Assistant review surface completion | Multi-word filler and merge helpers exist | Cut Assistant panel |
| Audio mastering presets | Roadmap C.6 and shipped audio analysis | Audio panel and presets |
| Closed audio-description track export | R5.3d; TTS already present | Accessibility export pipeline |
| Strings extraction/i18n audit | R5.4c; hardcoded engine strings likely remain | Android lint, resource extraction |
| No-pill terminology cleanup | Local comments/resource names still use `pill` even when shape is small radius | UI naming/comments; avoid visual regressions |

## Media Management and Asset Workflows

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| Stock asset library | Roadmap C.7; competitor asset/template stores | Media picker tabs, opt-in network policy |
| In-app camera with teleprompter | `CameraCaptureEngine` stub | CameraX path, editor import |
| 360/VR editing | `EquirectangularEngine` stub | Preview/export transforms |
| Project media relink/missing media UX | Pro editor expectation | Project open/import screens |
| Proxy workflow | Desktop NLE benchmark | Media cache, export pipeline |

## Interop, Templates, and Plugins

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| OTIO/FCPXML import | Export exists; import is harder differentiator | Timeline import parser, tests |
| Template marketplace | Metadata exists; commercial template pressure | Template registry/UI |
| dotLottie state machines | `docs/templates.md`, R6.16 | Template runtime |
| Rive activation | Plugin/template roadmap | Rive runtime decision |
| OpenFX descriptor compatibility | Recent commit added registry matrix | Plugin registry UI/docs |
| Gyroflow project import | Gyroflow competitor/source | Stabilization engine, sidecar import |

## Testing and Documentation

| Idea | Evidence | Candidate Touch Points |
|---|---|---|
| Golden-frame render tests | Lottie/blend/HDR changes need visual parity | Test assets, screenshot/golden harness |
| Export matrix smoke tests | Codec/container/device variability | Instrumented tests or local scripts |
| Model registry contract tests | Checksum/license/delivery drift risk | Unit tests |
| Dependency update checklist | Many newer dependency streams | `SECURITY_AND_DEPENDENCY_REVIEW.md`, README |
| Current context file | Stale local instruction files | `PROJECT_CONTEXT.md` |

## Backlog Hygiene Notes

- New speculative model features should wait until model registry, checksum, PAD/F-Droid, and evaluation harness work is finished.
- New cloud integrations should default to opt-in, explicit provider selection, and no media upload without visible consent.
- New UI surfaces should reuse the existing Material 3/tokens approach and avoid broad redesign unless requested.

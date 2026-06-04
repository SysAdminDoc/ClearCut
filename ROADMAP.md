# NovaCut Roadmap

Active roadmap for forward-looking work. Shipped work is summarized in
[COMPLETED.md](COMPLETED.md), research synthesis lives in
[RESEARCH_REPORT.md](RESEARCH_REPORT.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.11** (`versionCode` 148). Last consolidated:
2026-06-04.

## Current State

- Android video editor built with Kotlin, Jetpack Compose, Material 3, Media3
  Transformer/ExoPlayer, Room, DataStore, Hilt, ONNX Runtime, and MediaPipe.
- Current release line already includes Media3 1.10 foundation work, HDR source
  and export confidence surfaces, multi-track export composition, Ultra HDR
  source ingest, accessibility improvements, model-management trust surfaces,
  Cut Assistant scaffolding, tracked-object scaffolding, and Android 16/tablet
  readiness work.
- The active development direction is not another broad feature list. It is
  wiring the shipped scaffolds into reliable editor flows, closing release
  readiness gaps, and keeping AI/model surfaces honest about availability.
- v3.74.11 adds the first repeatable Compose instrumentation smoke harness for
  the project gallery, blank-project editor open, media picker, export sheet,
  Settings, and privacy dashboard surfaces.

## Source Archives

- Previous full roadmap: [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- Cross-project feature-port roadmap: [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- May 25 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- Loop 6 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)

## Active Queue

| Priority | Work | Exit criteria |
|---|---|---|
| P0 | Release pipeline reactivation | Push/PR/tag CI proves unit tests, debug build, release build inputs, 16 KB alignment, and release artifact checks without relying on local-only state. |
| P0 | Recovery open path | `loadRecoveryDataWithOutcome` becomes the default project-open path, with future-schema/corrupt/not-found outcomes surfaced to the user without data loss. |
| P0 | Media relink editor integration | `MediaRelinkProbe` reports missing/unknown media when a project opens and gives users a focused relink path before edit/export. |
| P0 | Compound clip gesture closure | Timeline long-press/radial action opens compound clips through `EditorViewModel.openCompoundClip`, with the existing breadcrumb and predictive-back gate active. |
| P0 | Caption translation panel call site | The Captions sub-tab hosts `CaptionTranslationPanel` and dispatches edits/regeneration through the existing ViewModel surface. |
| P0 | Mixed-render export orchestrator | `VideoEngine.exportMixed(plan)` consumes `MixedRenderComposer` plans and routes stream-copy, Transformer, and FFmpeg concat paths with Android-runtime verification. |
| P1 | FillerRemovalPanel final deletion | Remove the deprecated panel slot, old ViewModel methods, stale state fields, and remaining suppression after the Cut Assistant route is fully adopted. |
| P1 | Per-tool AI requirement adoption | Migrate each AI tool path from the legacy `aiRequirementPrompt` to `AiModelRequirementSheet`, keeping a fallback only for tools without registry entries. |
| P1 | Editor state decomposition | Split `EditorState` into domain sealed substates so caption, compound, export, AI, media, and panel state can evolve independently. |
| P1 | EditorScreen panel router decomposition | Replace the large monolithic panel routing surface with smaller host components that own only their local state and callbacks. |
| P1 | Timeline refactor | Reduce `Timeline.kt` risk by extracting gesture handling, clip layout, overlays, and accessibility actions into focused files with tests where practical. |
| P1 | Model activation gates | For every active AI/model dependency, keep source locator, SHA-256, license posture, delivery mode, F-Droid posture, and runtime checksum behavior current in `docs/models.md`. |
| P2 | Project color policy consumers | Wire `ProjectColorPolicy` into Settings/export confidence once the Room/autosave migration plan is ready. |
| P2 | Diagnostic ZIP timeline-shape toggle | Expose the privacy-preserving timeline-shape summary as an explicit Settings export option. |
| P2 | Dependabot grouping and auto-tag review | Group dependency updates by toolchain risk and evaluate auto-tagging from `CHANGELOG.md` headings. |
| P2 | Fastlane changelog history | Populate `fastlane/metadata/.../changelogs/` from release history or document why the channel is unused. |
| P3 | Caption translation engine activation | Replace source-text echo behavior with a real local model path such as MADLAD-400 or Bergamot only after model gates are complete. |
| P3 | Advanced engine activations | Activate Oboe resampling, adjustment layers, keyframe graph UI, and remaining AI engines only when dependencies, APK size, 16 KB compliance, and device QA are clear. |

## Release Readiness Gates

- `targetSdk = 36` and Android 16 behavior must remain verified.
- Native dependencies must pass 16 KB page-size alignment checks before release.
- Release artifacts must have matching version names, version codes, changelog
  entries, and signed/verified build outputs.
- Any AI model or cloud-capable path must expose local-vs-cloud behavior,
  license posture, opt-in requirements, model size, and removal controls.
- F-Droid/Play channel differences must be explicit when dependencies impose
  GPL, model-download, or cloud-service constraints.

## Deferred Until Runtime Or Dependency Proof Exists

- Full real-time collaboration.
- Cloud backup beyond local project archive/import trust flows.
- Heavy generative-video or cloud inpainting paths without explicit user opt-in.
- New native libraries that have not passed 16 KB, license, and device smoke
  checks.
- Another broad roadmap research pass before the P0/P1 adoption items above are
  closed.

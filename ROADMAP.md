# NovaCut Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Active roadmap for forward-looking work. Shipped work is summarized in
[COMPLETED.md](COMPLETED.md), research synthesis lives in
[RESEARCH_REPORT.md](RESEARCH_REPORT.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.54** (`versionCode` 191). Last consolidated:
2026-06-04.

> Last researched: Cycle 22 - 2026-06-04.

## ▶ Implementer Instructions (for the build machine)

This roadmap is fed continuously by a research machine. On every pass, the
build machine should:

1. `git pull --rebase` to get the latest researched items before starting.
2. Work the open 🤖 items top-down by priority (P0 -> P3). Build them properly:
   multi-file structure, real error handling, no runtime auto-install hacks,
   version strings synced, docs/CHANGELOG updated in the same commit.
3. In addition to building items, run a full UX audit each pass. Walk every
   screen, page, dialog, form, table, and empty/loading/error/disabled state
   across light/dark/high-contrast themes. Check onboarding, navigation clarity,
   spacing/contrast/alignment, clipping/overflow, hierarchy, microcopy,
   destructive-action guards, keyboard and screen-reader accessibility, and
   trust signals. Fix what you find, or file it back as a new 🤖 roadmap item if
   it is larger than a pass.
4. Check off ✅ each item you complete, leave it in place with the checkmark,
   commit per logical change with a "why" message, and push.
5. Never edit this Implementer Instructions block or the 🔬 Researcher Queue
   headings. Never force-push.

Current direction: finish timeline decomposition, keep model-gate honesty
current, close release/platform trust gaps, and make accessibility appearance
variants testable before another broad feature sweep. Use JDK 21 for Gradle
work, keep the release gate concrete, and avoid routine alpha Android Gradle
Plugin bumps. Ownership tags: `🤖` means
implementer-actionable, `🔧` means user/external/manual gated, `🔬` means
researcher-added this cycle, and `✅` means implemented/closed by the build lane.

2026-06-04 research refresh: the v3.74.21 through v3.74.26 editor-state
migrations moved the AI, export, media, compound, caption, and panel storage
slices into `EditorAiState`, `EditorExportDomainState`, `EditorMediaState`,
`EditorCompoundState`, `EditorCaptionState`, and `EditorPanelState`. The next
P1 architecture lane is EditorScreen panel router decomposition; v3.74.27
started it by extracting the primary bottom-sheet cluster into
`EditorPrimaryPanelHost`, v3.74.28 extracted AI tools / Cut Assistant review
into `EditorAiPanelHost`, and v3.74.29 extracted clip-adjustment routes into
`EditorClipAdjustmentPanelHost`. Maven metadata shows
Media3 1.10.1 and WorkManager 2.11.2 current, with Compose BOM 2026.05.01, Room
2.8.4, and Kotlin 2.4.0 available for deliberate review; AGP's newest observed
metadata is 9.3.0-alpha09 and should stay out of routine bumps. v3.74.30
completed the current panel-router lane by extracting utility sheets/dialogs
into `EditorUtilityPanelHost`, always-on overlay routes into
`EditorOverlayHost`, and shared report rows into `EditorFeedbackDialogs`.
Focused Gradle, APK packaging, release metadata, signature, zipalign, and
APK-based 16 KB gates passed locally for this batch. v3.74.31 started the
timeline refactor by extracting interaction/accessibility policy, the
full-project overview scrollbar, shared timeline accent colors, and focused JVM
coverage from `Timeline.kt`. v3.74.32 continued the timeline refactor by moving
timeline chrome composables, compact label formatters, ruler/waveform drawing,
and volume-keyframe filtering into focused files with JVM coverage. v3.74.33
closed the Cycle 2 P0 Android 15 media-processing timeout item by wiring
`ExportService.onTimeout(...)` through a distinct `VideoEngine` error path.
v3.74.34 closed the Cycle 2 Android 13 export notification permission item by
requesting `POST_NOTIFICATIONS` from the editor before first background export
and keeping an in-app progress fallback when notifications stay off.
v3.74.35 closed the managed-media backup policy split by excluding imports from
cloud backup while including them in Android 12+ device transfer.
v3.74.36 closed the P0 fatal-crash capture item by installing a global
uncaught-exception handler that writes bounded, redacted local crash records and
adds them to user-triggered diagnostic ZIP exports.
v3.74.37 closed the active timeline-refactor lane by moving visible clip layout,
clip-content thresholds, trim/slip/slide drag-action policy, and slide snap
target/haptic decisions out of `Timeline.kt` into focused helpers with JVM
coverage.
v3.74.38 closed the model activation gate lane by adding tested delivery,
F-Droid, active-registry, and runtime-checksum metadata to every AI tool
requirement and refreshing `docs/models.md` with a gate matrix that blocks
planned unpinned models from presenting as downloadable.
v3.74.39 closed the instrumentation smoke CI item by adding a dedicated GitHub
Actions emulator job that runs `NovaCutSmokeTest` through
`connectedDebugAndroidTest`.
v3.74.45 closed the Cycle 3 performance gate by adding the `:baselineprofile`
module, generated release Baseline Profile, ProfileInstaller wiring, and
managed-device Macrobenchmark coverage for default/profiled cold startup,
profiled warm startup, and blank-editor timeline scrub frame timing.
v3.74.46 closed the Cycle 4 memory-pressure gate by adding app-level
`onTrimMemory` dispatch, tested trim-level policy, registered thumbnail,
waveform, and proxy scratch cache eviction, and redacted diagnostic
breadcrumbs.
v3.74.47 closed the Cycle 5 distribution-trust gate by adding deterministic
Fastlane Play listing images, SVG sources, alt-text inventory, privacy policy
source/link, Data safety worksheet, and a CI validator for listing assets and
disclosure coverage.
v3.74.48 closed the Cycle 7 appearance gate by adding persisted
System/Dark/High Contrast Dark selection, high-contrast shared chrome tokens,
Compose accessibility smoke hooks, contrast policy tests, and a documented
dark-only System rationale until a light canvas has full visual QA.
v3.74.49 closed the Cycle 8 document-import gate by adding content-only
non-media document intent parsing, bounded JSON/XML/text/ZIP/octet-stream
manifest filters, existing-engine validation for templates, effect packs, LUTs,
OpenFX descriptors, archives, and timeline import status, plus a Projects
preview/report dialog before any mutation.
v3.74.50 closed the Cycle 9 process-death diagnostic gate by recording Android
11+ `ApplicationExitInfo` history on app startup, de-duping bounded exit records,
redacting descriptions/traces, adding `process-exit-history.json` to
user-triggered diagnostic ZIPs, and documenting the local-only privacy surface.
v3.74.51 closed the Cycle 10 settings persistence gate by replacing the
Preferences DataStore delegate with a `PreferenceDataStoreFactory`
`ReplaceFileCorruptionHandler`, adding redacted bounded settings-reset reports,
showing a one-shot Settings notice after recovery, and exporting reset reports
through user-triggered diagnostic ZIPs when present.
v3.74.52 closed the Cycle 11 overlay-fidelity gate by importing shelf/gallery
stickers into app-owned overlay assets before project mutation, rejecting GIFs
with explicit copy until animation is supported, rendering active image
overlays in preview, burning them into Media3 Transformer exports with matching
center-offset geometry, and adding overlay-source relink diagnostics.
v3.74.53 closed the Cycle 12 local-network gate by declaring the Android 16
temporary and Android 17 forward permissions, adding a pure
`LocalNetworkPermissionPolicy` for API/target/scope decisions, exposing
destination-specific gates through `OutputStreamingEngine`, and adding optional
permission-state diagnostic ZIP support.
v3.74.54 started the Cycle 13 C2PA lane by migrating the draft manifest to
current CAWG `cawg.training-mining` labels, adding C2PA builder-shaped manifest
definition JSON, explicitly gating embedded Content Credentials on library,
certificate/key, user PEM, or remote-signer consent availability, and renaming
the local sidecar to `.c2pa-draft-manifest.json` so it cannot be confused with a
signed MP4 manifest store.

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
- v3.74.12 reactivates release-pipeline checks for unit tests, debug/release
  APKs, instrumentation APK packaging, release metadata, signatures, APK zip
  alignment, and 16 KB native-library compliance.
- v3.74.13 makes the schema-aware recovery outcome loader the default editor
  open path and blocks autosave overwrites when recovery data is corrupt or
  written by a newer schema.
- v3.74.14 runs `MediaRelinkProbe` after project open, opens Media Manager
  when missing or unverified sources are found, and feeds probe status into
  focused relink cards.
- v3.74.15 routes timeline long-press and compound-selected radial actions to
  `EditorViewModel.openCompoundClip`, renders the state-backed breadcrumb chip,
  and keeps predictive back active by clearing clip selection on compound entry.
- v3.74.16 hosts `CaptionTranslationPanel` inside the Captions panel, builds
  rows from selected-clip captions on target-language selection, and dispatches
  edits/regeneration through the ViewModel translation surface.
- v3.74.17 adds the mixed-render export orchestrator: eligible mixed plans now
  route pass-through runs through stream-copy, re-encode runs through Media3
  Transformer, and final stitching through FFmpeg concat with release-gated
  fallback to whole-timeline Transformer.
- v3.74.18 deletes the legacy standalone FillerRemovalPanel route after the
  `filler_removal` tool action moved to Cut Assistant Review, removing its
  panel slot, ViewModel state/methods, obsolete AiFeatures analysis path, and
  panel-only strings.
- v3.74.19 migrates strict model/dependency-gated AI tool dispatch to
  `AiModelRequirementSheet`, aligns registry IDs with live editor tool IDs,
  and keeps the legacy requirement prompt only as a fallback for non-registry
  tool IDs.
- v3.74.20 adds the first editor state decomposition layer: a sealed
  `EditorDomainState` projection for panel, caption, compound, export, AI, and
  media slices with JVM tests locking representative field ownership.
- v3.74.21 moves AI-related editor state into `EditorAiState` storage while
  keeping read-only compatibility accessors for UI/delegate reads.
- v3.74.22 moves export-related editor state into `EditorExportDomainState`
  storage while keeping read-only compatibility accessors for export UI and
  delegate reads.
- v3.74.23 moves media/trust-report editor state into `EditorMediaState`
  storage while keeping read-only compatibility accessors for Media Manager,
  backup import, and timeline-exchange reads.
- v3.74.24 moves compound navigation editor state into `EditorCompoundState`
  storage while keeping read-only compatibility accessors for predictive back
  and breadcrumb reads.
- v3.74.25 moves caption translation editor state into `EditorCaptionState`
  storage while keeping read-only compatibility accessors for caption panel
  reads.
- v3.74.26 moves panel visibility, selected-effect, and text-overlay editing
  state into `EditorPanelState` storage while keeping read-only compatibility
  accessors for editor UI and delegate reads.
- v3.74.27 extracts the primary panel router host for media picker, effects,
  speed, transition, text editor, export, audio, and voiceover surfaces.
- v3.74.28 extracts the AI panel router host for AI tools and Cut Assistant
  review surfaces.
- v3.74.29 extracts the clip-adjustment panel router host for transform, crop,
  effect adjustment, color/audio/keyframe/speed/mask/blend/PiP/chroma/caption
  surfaces and their local transform/mask preview overlays.
- v3.74.30 completes the current EditorScreen panel-router decomposition lane by
  extracting utility sheets/dialogs, always-on overlay routes, and shared report
  dialog rows out of the main screen body.
- v3.74.31 starts the Timeline refactor by extracting interaction/accessibility
  policy, the full-project overview scrollbar, and shared track accent colors
  out of the main timeline renderer with focused JVM tests.
- v3.74.32 continues the Timeline refactor by extracting toolbar/chip chrome,
  compact label formatters, ruler/waveform drawing helpers, and volume-keyframe
  filtering out of the main timeline renderer with focused JVM tests.
- v3.74.33 handles Android 15 media-processing foreground-service timeout
  callbacks by failing active exports through a distinct `VideoEngine` timeout
  error, cancelling Transformer work, deleting the active output file, and
  stopping foreground service state.
- v3.74.34 adds a contextual Android 13+ export notification permission path
  before the first background export, remembers handled prompt state, and keeps
  export progress/cancel controls available in-app when notifications are off.
- v3.74.35 splits Android 12+ backup policy so `media/imports` stays out of
  cloud backup quota but is included in device-to-device transfer; partial
  import and generated-media writes remain excluded.
- v3.74.36 installs a global uncaught-exception handler on app startup. Fatal
  exceptions now write a bounded, redacted JSON breadcrumb under
  `filesDir/diagnostics/crashes`, chain to the previous platform handler, and
  appear as `crash-records.json` only when the user exports a diagnostic ZIP.
- v3.74.37 completes the active timeline-refactor lane by extracting visible
  clip bounds, clip badge thresholds, unified trim/slip/slide drag-action
  resolution, slide snap target collection, and snap haptic policy from
  `Timeline.kt` into `TimelineClipLayout` with focused JVM tests.
- v3.74.38 adds explicit model activation gate metadata to `AiToolRequirements`
  and refreshes `docs/models.md` with a tested AI tool gate matrix covering
  source locator, SHA-256 posture, license posture, delivery mode, F-Droid
  posture, and runtime checksum behavior. Planned AnimeGAN/Fast NST,
  Real-ESRGAN, and SAM/MobileSAM tools now stay dependency-missing until exact
  bytes and runtime loaders are pinned.
- v3.74.39 adds a dedicated `instrumentation-smoke` GitHub Actions job that
  boots a hosted Android emulator and runs `NovaCutSmokeTest` through
  `connectedDebugAndroidTest`, covering project gallery, blank editor, media
  picker, export sheet, Settings, and privacy dashboard launch surfaces.
- v3.74.40 adds Sharesheet-compatible incoming video/image/audio routing with
  `ACTION_SEND` / `ACTION_SEND_MULTIPLE` manifest filters, centralized
  `content://` media parsing, ordered multi-share handoff, and parser/manifest
  JVM coverage.
- v3.74.41 wires `ProjectColorPolicy` into Settings and export confidence using
  the conservative default policy, so color-policy coherence warnings are
  visible before the future per-project persistence migration.
- v3.74.42 exposes the privacy-preserving diagnostic timeline-shape summary as
  an explicit Settings opt-in, loading only counts from the latest saved project
  autosave and adding `timeline-shape.json` to user-created diagnostic ZIPs.
- v3.74.43 refreshes the dependency-maintenance surface with Compose BOM
  2026.05.01, risk-lane Dependabot groups, and documented freshness findings
  for Room/Kotlin/KSP; Room 2.8.4 and newer Kotlin/KSP trains stay queued for a
  dedicated toolchain pass because local JVM validation was killed before a
  complete compile result.
- v3.74.44 adds a deterministic Fastlane changelog sync script and populates
  `fastlane/metadata/android/en-US/changelogs/` from explicit `versionCode`
  entries in `CHANGELOG.md`, so Play/F-Droid metadata no longer has a one-file
  changelog history.
- v3.74.45 adds a generated Baseline Profile that ships in release APKs and a
  managed Pixel 6 API 36 Macrobenchmark suite covering default cold startup,
  profile-required cold/warm startup, and blank-editor timeline scrub frames.
- v3.74.46 adds app-level memory-pressure handling for editor caches: active
  media engines register thumbnail/waveform/proxy trim callbacks, visible
  editing keeps proxy scratch on low-memory callbacks, background/critical
  callbacks evict scratch caches, and diagnostic ZIPs can include redacted
  memory-trim breadcrumbs.
- v3.74.47 adds a repeatable Play listing package: committed Fastlane icon,
  feature graphic, phone/tablet screenshots, SVG sources, inventory alt text,
  privacy policy URL, Data safety worksheet, and a CI validator for dimensions,
  screenshot counts, text bounds, and manifest-permission disclosure coverage.
- v3.74.48 adds appearance-mode and contrast regression gates: Settings exposes
  System/Dark/High Contrast Dark, the root theme persists the selected mode,
  shared chrome consumes high-contrast semantic tokens, Compose smoke tests run
  accessibility checks from root, and `NovaCutAppearancePolicyTest` locks text,
  non-text, chip, and low-emphasis-token contrast floors.
- v3.74.49 adds non-media document import routing: supported plugin, LUT,
  archive, and timeline-interchange files flow through a content-only parser,
  bounded manifest filters, existing loader validation, and a Projects
  preview/report dialog before any template import mutation.
- v3.74.50 adds process-death diagnostic history: `ProcessExitRecorder`
  captures Android 11+ `ApplicationExitInfo` records on startup, stores a
  bounded/de-duped local history, redacts process descriptions and trace
  excerpts, and exports `process-exit-history.json` through diagnostic ZIPs.
- v3.74.51 adds Preferences DataStore corruption recovery: unreadable
  `novacut_settings` files are replaced with defaults through
  `ReplaceFileCorruptionHandler`, readable invalid keys fall back per setting,
  Settings shows a dismissible recovery notice, and diagnostic ZIPs can include
  redacted `settings-reset-report.jsonl` records.
- v3.74.52 adds durable image/sticker overlays: bundled sticker shelf URIs and
  gallery images copy to app-private overlay files before state mutation,
  active overlays render in preview and Transformer exports with matching
  timing/geometry, GIF overlays are rejected explicitly until animation is
  supported, and overlay sources participate in relink diagnostics.
- v3.74.53 adds Android local-network permission readiness for future Live
  Studio streaming: public internet destinations skip the gate, LAN/multicast
  destinations map to `NEARBY_WIFI_DEVICES` on Android 16 target SDK 36 and
  `ACCESS_LOCAL_NETWORK` on Android 17 target SDK 37+, permission-denial
  failures surface actionable copy, and diagnostics can include redacted
  permission-state snapshots.
- v3.74.54 adds current CAWG training/data-mining labels and honest C2PA draft
  sidecar semantics: export writes `.c2pa-draft-manifest.json` with unsigned
  status, signer availability explains missing library/certificate/PEM/remote
  consent states, and the privacy dashboard names the local-only draft behavior.

## Source Archives

- Previous full roadmap: [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- Cross-project feature-port roadmap: [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- May 25 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- Loop 6 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)

## Active Queue

| Priority | Work | Exit criteria |
|---|---|---|
| ✅ P1 | Timeline refactor | Implemented in v3.74.37: clip layout, badge visibility, trim/slip/slide drag-action policy, and slide snap target/haptic decisions now live outside `Timeline.kt` with focused JVM tests. |
| ✅ P1 | Model activation gates | Implemented in v3.74.38: every AI tool has tested source/delivery/F-Droid/runtime-checksum posture, and planned unpinned models no longer advertise downloads. |
| ✅ P2 | Project color policy consumers | Implemented in v3.74.41: Settings now shows the current conservative project color policy, and export confidence accepts `ProjectColorPolicy` to surface policy coherence, HDR-pass-through, SDR-tone-map no-op, and HDR-to-SDR tone-map warnings without forcing a Room migration. |
| ✅ P2 | Diagnostic ZIP timeline-shape toggle | Implemented in v3.74.42: Settings has an opt-in "Include timeline shape" switch; diagnostic export includes counts-only `timeline-shape.json` from the latest saved project autosave when available, and still exports without project data when unavailable. |
| ✅ P2 | Dependabot grouping and dependency freshness review | Implemented in v3.74.43: Gradle dependency updates are grouped by toolchain, UI, AndroidX runtime, media, ML/native, and small-library risk lanes; Compose BOM 2026.05.01 is staged; Room/Kotlin/KSP freshness is documented and held for a dedicated toolchain validation pass; changelog-heading auto-tagging is documented as a release-verification path rather than a Dependabot path. |
| P2 | Room/Kotlin toolchain migration pass | Revisit Room 2.8.4 and Kotlin/KSP 2.1.21+ or 2.3+/2.4+ on a clean build graph with enough JVM headroom; exit only after `:app:compileDebugKotlin` and Room/KSP schema generation complete without daemon termination. |
| ✅ P2 | Fastlane changelog history | Implemented in v3.74.44: `scripts/sync_fastlane_changelogs.py` derives 500-character Play changelog files from `CHANGELOG.md` entries with explicit `versionCode` evidence, and `fastlane/metadata/android/en-US/changelogs/` is populated for the recoverable release history. |
| ✅ P2 | Baseline Profile and macrobenchmarks | Implemented in v3.74.45: `:baselineprofile` generates the release Baseline Profile, ProfileInstaller ships it in the APK, and managed Pixel 6 API 36 macrobenchmarks report default/profiled cold startup, profiled warm startup, and blank-editor timeline scrub frame timing. |
| ✅ P2 | Memory trim policy | Implemented in v3.74.46: `NovaCutApp.onTrimMemory` dispatches OS memory-pressure levels through a tested policy, active media engines register cache trim callbacks, proxy trimming skips in-flight renders, and diagnostic ZIPs include bounded redacted memory-trim breadcrumbs when present. |
| ✅ P2 | Play listing release gate | Implemented in v3.74.47: Fastlane metadata now includes deterministic Play icon, feature graphic, phone/tablet screenshots, SVG sources, alt-text inventory, privacy policy URL, Data safety worksheet, and a CI validator for listing/disclosure readiness. |
| ✅ P2 | Appearance and contrast gates | Implemented in v3.74.48: persisted System/Dark/High Contrast Dark selection, high-contrast shared chrome tokens, Compose accessibility smoke checks, `docs/appearance-policy.md`, and JVM contrast guardrails for text, non-text, chips, and low-emphasis token misuse. |
| ✅ P2 | Non-media document import router | Implemented in v3.74.49: content-only plugin/LUT/archive/timeline documents now classify through `IncomingDocumentIntentParser`, manifest filters stay specific without `*/*`, Projects shows a preview/report, and loaders validate templates, effect packs, LUTs, OpenFX descriptors, archives, and timeline-import stub status before mutation. |
| ✅ P2 | Process-death diagnostic history | Implemented in v3.74.50: Android 11+ `ApplicationExitInfo` records are captured through `ProcessExitRecorder`, de-duped by timestamp/reason/PID, redacted/truncated, exposed in Privacy Dashboard copy, and included in diagnostic ZIPs as `process-exit-history.json` with an unsupported marker on older devices. |
| ✅ P1 | Durable image/sticker overlay compositor | Implemented in v3.74.52: `OverlayAssetStore` imports bundled/gallery stickers into app-owned overlay files before project mutation, `PreviewPanel` renders active image overlays, Media3 Transformer exports burn them with matching geometry, GIF overlays reject with explicit copy, and `MediaRelinkProbe` reports missing overlay sources. |
| ✅ P2 | Android local-network permission gate | Implemented in v3.74.53: manifest declares Android 16 `NEARBY_WIFI_DEVICES` and Android 17 `ACCESS_LOCAL_NETWORK`, `LocalNetworkPermissionPolicy` maps API/target/scope decisions, `OutputStreamingEngine` exposes destination gates and permission-aware LAN failure copy, and diagnostics can export redacted permission-state snapshots. |
| ✅ P1 | C2PA draft manifest and signer gate | Implemented in v3.74.54: draft manifests use current CAWG `cawg.training-mining` labels, C2PA builder-shaped manifest JSON, explicit signer/library/consent availability decisions, `.c2pa-draft-manifest.json` naming, and privacy/export copy that does not imply a verifiable embedded MP4 credential. |
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

## Research-Backed Engine Candidates

Specific open-source implementation candidates folded from the local NovaCut OSS
research guide (`RESEARCH.md`, kept local-only and gitignored). Each is gated by
the same dependency, APK-size, 16 KB-alignment, license, and device-QA checks as
the existing "Advanced engine activations" line above. They are enumerated here so
the concrete candidate detail is not stranded in a local-only file.

- [ ] P2 — Timeline magnetic snapping and command-pattern undo
  - Why: `slipClip()`/`slideClip()` exist but are not wired to drag gestures; no
    magnetic snapping or clip grouping on the timeline.
  - Touches: timeline gesture handling, edit-command model, undo/redo.
  - Acceptance: sorted-edge 8dp proximity snap, grouped moves, and slip/slide
    wired to gestures with command-based undo, behind existing accessibility gates.
  - Source: RESEARCH.md (Kdenlive, Olive, OpenTimelineIO).
- [ ] P2 — Captions engine upgrade to Sherpa-ONNX
  - Why: current Whisper-via-ONNX path has no KV cache (O(n^2) per chunk);
    Sherpa-ONNX is ~51x faster on the same model with word-level timestamps.
  - Touches: caption transcription engine, model registry, model gates.
  - Acceptance: Sherpa-ONNX (Whisper Tiny multilingual / Moonshine English) wired
    behind model-requirement sheets with source/checksum/license/size recorded.
  - Source: RESEARCH.md (sherpa-onnx, Moonshine, Vosk).
- [ ] P2 — Video stabilization upgrade
  - Why: current stabilization is basic frame differencing; OpenCV optical flow +
    Kalman or vid.stab trajectory smoothing is substantially more robust.
  - Touches: import-time analysis, GPU affine transform apply path.
  - Acceptance: offline analysis during import, real-time GPU apply, 10-15% crop,
    gated on NDK/dependency and device QA.
  - Source: RESEARCH.md (OpenCV Android, vid.stab).
- [ ] P3 — Segmentation quality and tap-to-select
  - Why: MediaPipe selfie segmentation does a full-res GPU readback (perf issue);
    RVM adds true alpha/hair detail and MobileSAM adds tap-to-segment.
  - Touches: segmentation engine, mask compositing, selection UI.
  - Acceptance: downsampled MediaPipe readback retained for real-time, RVM as an
    AI-quality path, MobileSAM tap-select, all behind model gates.
  - Source: RESEARCH.md (MediaPipe, RobustVideoMatting, MobileSAM).
- [ ] P3 — Frame interpolation and AI upscaling activation
  - Why: frame interpolation and upscaling are stubs/unimplemented; RIFE v4.6
    (NCNN+Vulkan) and Real-ESRGAN x4plus have proven Android paths.
  - Touches: export-time engine activation, model delivery.
  - Acceptance: export-time slow-motion (24->60/120fps) and optional upscale,
    gated on Vulkan availability, model size, and 16 KB compliance.
  - Source: RESEARCH.md (RIFE, IFRNet, Real-ESRGAN).
- [ ] P3 — Neural text-to-speech and style filters
  - Why: TTS uses Android system voices; Piper-via-Sherpa-ONNX is offline and
    near-human; AnimeGANv2/fast-neural-style add creative filters.
  - Touches: TTS engine, AI filter pipeline, model registry.
  - Acceptance: Piper voices and style-transfer filters wired behind opt-in model
    download and license disclosure.
  - Source: RESEARCH.md (Piper, eSpeak NG, AnimeGANv2).
- [ ] P3 — Export codec and subtitle-burn extensions
  - Why: export is Media3 H.264/MP4; AV1 (SVT-AV1) saves 30-50% bandwidth, libass
    enables styled burned-in subtitles, FFmpegX provides a fallback encoder.
  - Touches: export orchestrator, encoder selection, subtitle render.
  - Acceptance: optional AV1 export, libass-styled subtitle burn-in, and an
    FFmpeg fallback path, each gated on device support and APK-size budget.
  - Source: RESEARCH.md (SVT-AV1, libass, FFmpegX-Android).
- [ ] P3 — Proxy media workflow
  - Why: no proxy workflow today; a 3-tier thumbnail/proxy/original pipeline keeps
    large-source editing responsive.
  - Touches: media import, background generation, preview-vs-export source switch.
  - Acceptance: WorkManager+ForegroundService proxy generation (540p H.264) with
    automatic proxy-for-preview / original-for-export switching.
  - Source: RESEARCH.md (DaVinci/Premiere proxy patterns).

---

## Research-Driven Additions

### Researcher Queue (Cycle 1 - 2026-06-04)

- [x] 🔬 `editor-router-dependency-refresh-2026-06-04` - rechecked the active
  EditorScreen panel-router lane, Android 16 / 16 KB release posture, and Maven
  metadata for Media3, WorkManager, Compose BOM, Room, Kotlin, and AGP. Existing
  P1/P2 rows cover the findings; the active working tree already contains the
  next `EditorPrimaryPanelHost` refactor slice, so no duplicate implementation
  row was promoted.

*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

These items came from a source-grounded audit (not a feature-discovery pass). The
remaining high-value gaps are in release verification depth, network/crash
hardening, localization delivery, and doc accuracy — none of which the Active
Queue (scaffold adoption/decomposition), the Research-Backed Engine Candidates
(engine activations), or the Deferred list already cover. Every claim below was
checked against `app/src/main`, `.github/workflows/build.yml`, and the manifest.

### Reliability & Security

- [x] ✅ P0 — Install a global uncaught-exception handler
  - Why: The app ships a diagnostic-ZIP feature but has no crash capture, so a
    fatal exception is lost — nothing to attach to the diagnostic bundle and no
    recovery breadcrumb.
  - Evidence: `grep` for `setDefaultUncaughtExceptionHandler` /
    `UncaughtExceptionHandler` across `app/src/main/java/` returns zero hits;
    `DiagnosticExportEngine.kt` exists and is wired to Settings; `NovaCutApp.kt`
    is the `Application` with an empty-tail `onCreate()` (the natural install
    point).
  - Touches: `NovaCutApp.onCreate`, a crash-record writer feeding the existing
    diagnostic-ZIP path, autosave/recovery handoff.
  - Acceptance: an uncaught exception writes a redacted crash record into the
    diagnostic store, chains to any previous default handler, and the next
    launch surfaces it for export — no PII in the record.
  - Verify: throw in a debug build, confirm the record is written and exportable;
    confirm normal crashes still propagate to the platform after capture.
  - Complexity: M
  - Implemented in v3.74.36: `CrashRecordStore` now installs from
    `NovaCutApp.onCreate()`, records fatal exceptions to a capped local JSON
    store without raw throwable messages, includes the records in
    `DiagnosticExportEngine` as `crash-records.json`, and updates the Privacy
    Dashboard data model to describe local crash breadcrumbs.

- [ ] P2 — Add a `networkSecurityConfig` that blocks cleartext app-wide
  - Why: With the `INTERNET` permission and an OkHttp cloud path, cleartext
    HTTP is permitted by the platform default on API 26-27; an explicit config
    closes the downgrade surface and documents the cloud posture.
  - Evidence: `AndroidManifest.xml` declares `android.permission.INTERNET` with
    no `usesCleartextTraffic` attribute and no `networkSecurityConfig`; OkHttp
    5.3.2 is a declared dependency (`libs.versions.toml`).
  - Touches: `res/xml/network_security_config.xml` (new resource),
    `AndroidManifest.xml` `application` element.
  - Acceptance: `cleartextTrafficPermitted="false"` enforced app-wide (with an
    explicit allowlist only if a cleartext host is genuinely required); cloud
    calls still succeed over TLS.
  - Verify: confirm an `http://` request fails fast in a debug build; confirm
    the existing HTTPS model/cloud paths are unaffected.
  - Complexity: S

- [ ] P2 — Attach checksums and build provenance to release artifacts
  - Why: Tagged-release CI uploads APKs with no integrity proof, so a
    self-host/F-Droid consumer cannot verify a download independently.
  - Evidence: `.github/workflows/build.yml` builds/verifies/zipaligns/16 KB-checks
    APKs and uploads them, but contains no `sha256`/`checksum`/`cosign`/
    `provenance` step; `dependabot.yml` flags cosign as future work.
  - Touches: release job in `build.yml` (emit `*.sha256` next to each APK; optional
    SLSA/cosign attestation), release notes/README verification snippet.
  - Acceptance: every release asset has a published SHA-256 sum; a documented
    one-line command reproduces and matches it.
  - Verify: download a release asset and confirm the published sum matches
    locally computed `sha256sum`.
  - Complexity: S

### Quality & Friction

- [x] ✅ P1 — Execute the instrumentation smoke suite on an emulator in CI
  - Why: The v3.74.11 Compose smoke harness is compiled and packaged but never
    run, so a green pipeline does not prove the app even launches — launch/a11y
    regressions pass CI silently.
  - Evidence: `build.yml` builds and packages the `androidTest` APK
    (`apk/androidTest/debug/*.apk` appears only in alignment/verify loops) but has
    no `connectedAndroidTest` / `connectedCheck` / emulator / Gradle Managed
    Device step.
  - Touches: `.github/workflows/build.yml` (add a Gradle Managed Device or hosted
    emulator job), test-runner config.
  - Acceptance: the gallery/blank-editor/picker/export/Settings/privacy smoke
    tests run on a device image in CI and gate the build.
  - Verify: confirm the new CI job runs the instrumentation tests green and fails
    when a smoke test is deliberately broken.
  - Complexity: M
  - Implemented in v3.74.39: `.github/workflows/build.yml` now has a separate
    `instrumentation-smoke` job using `reactivecircus/android-emulator-runner@v2`
    on API 35 Google APIs x86_64, with animations disabled, running only
    `com.novacut.editor.NovaCutSmokeTest` via `connectedDebugAndroidTest`.

- [ ] P1 — Ship at least one fully translated locale
  - Why: The localization scaffold is complete (~1900 externalized strings, RTL
    `BidiFormatter`, `localeConfig`) but ships zero translated locales — large
    infra investment with no end-user benefit and no real-build exercise of the
    RTL path.
  - Evidence: `res/` contains only `values/` (no `values-*` locale dirs);
    `strings.xml` is ~1937 lines; `BidiTextPolicyTest` and `locales_config.xml`
    exist.
  - Touches: new `res/values-<locale>/strings.xml`, per-app-language QA, README
    locale list.
  - Acceptance: one locale fully translated and selectable via per-app language;
    no missing-string fallbacks in that locale; if RTL is chosen, BidiFormatter
    is exercised on a real layout.
  - Verify: switch app language on a build and confirm full coverage and correct
    bidi rendering.
  - Complexity: M

- [x] ✅ P1 — Wire slip/slide editing to timeline gestures (or scope it down)
  - Why: `slipClip()`/`slideClip()` exist in the engine but have no gesture call
    sites, so the "pro" slip/slide edit is unreachable — a core mobile-NLE
    table-stakes gap versus KineMaster/PowerDirector.
  - Evidence: `grep slipClip|slideClip Timeline.kt` returns zero matches; the
    functions are defined on the edit/engine side; README advertises the feature.
  - Touches: `Timeline.kt` gesture handling, edit-command dispatch, existing
    accessibility-action gates.
  - Acceptance: slip and slide are reachable from timeline drag (and from
    accessibility actions), routed through the edit-command/undo path.
  - Verify: perform a slip and a slide on a built editor and confirm correct
    in/out behavior plus undo.
  - Complexity: M
  - Note: overlaps the "Timeline magnetic snapping and command-pattern undo"
  - Implemented in v3.74.37: `Timeline` resolves trim/slip/slide drag zones,
    dispatches slip/slide body drags, and keeps keyboard/accessibility nudges on
    slip in trim mode and slide otherwise. `EditorScreen` wires those callbacks
    to `EditorViewModel`, whose begin/end edit hooks save undo state, suppress
    per-drag player rebuilds, and persist once the gesture completes.
    Engine Candidate's gesture work; sequence after that lands to avoid churn.

### Documentation & Polish

- [ ] P2 — Correct overstated feature claims in README
  - Why: README lists slip/slide editing as shipped while it is not gesture-wired,
    which misleads users and inflates the "pro" claim.
  - Evidence: README slip/slide claim vs. zero `slipClip`/`slideClip` gesture
    sites in `Timeline.kt` (same evidence as the wiring item above).
  - Touches: `README.md` feature list.
  - Acceptance: README marks slip/slide as planned/in-progress (or the wiring
    item lands first and the claim becomes accurate); no other shipped/unshipped
    mismatches remain in the feature list.
  - Verify: cross-check each README "shipped" claim against a call site or test.
  - Complexity: S

- [ ] P3 — Realign the future-dated consolidation stamps
  - Why: `ROADMAP.md`, `COMPLETED.md`, and `PROJECT_CONTEXT.md` carry a
    future-dated "Last consolidated: 2026-06-04" stamp, which undermines doc
    trust and audit trails.
  - Evidence: all three files contain `Last consolidated: 2026-06-04` (and
    `PROJECT_CONTEXT.md` also `Last implementation update: 2026-06-04`), dates
    ahead of the actual edit history.
  - Touches: the date-stamp lines in those three docs.
  - Acceptance: stamps reflect the actual last-consolidation date and are kept in
    sync on future consolidations.
  - Verify: confirm each stamp matches the commit that last touched it.
  - Complexity: S

### Accessibility (audit)

- [ ] P2 — Run a WCAG 2.2 AA audit of the editor surfaces
  - Why: Edge-to-edge, predictive back, and accessibility actions are shipped,
    but contrast, 48dp touch targets, and content labels across the dense editor
    panels are unverified — and the unrun smoke suite means a11y regressions are
    not caught.
  - Evidence: COMPLETED.md lists a11y actions as shipped; no device/contrast
    audit was possible this pass (no emulator run); the CI smoke suite is built
    but not executed.
  - Touches: editor/timeline/export/Settings composables (labels, target sizes,
    color tokens), TalkBack pass.
  - Acceptance: a documented WCAG 2.2 AA checklist pass for the primary editor
    flows (contrast ratios, 48dp targets, semantic labels, TalkBack traversal).
  - Verify: TalkBack walkthrough plus an automated accessibility scanner on the
    smoke flows, both clean.
  - Complexity: M

### Quick Wins (P2/P3, < 1hr)

- P2 — Add a `networkSecurityConfig` that blocks cleartext app-wide (small XML
  resource + one manifest attribute).
- P2 — Attach SHA-256 sums to release artifacts (one CI step).
- P2 — Correct overstated slip/slide claim in README.
- P3 — Realign the future-dated "Last consolidated: 2026-06-04" stamps.

### Larger Bets (P0/P1, staged)

- P0 — Global uncaught-exception handler feeding the diagnostic-ZIP path (crash
  capture + recovery surfacing).
- P1 — Execute the instrumentation smoke suite on an emulator/Managed Device in
  CI (close the "green-but-unverified" gap).
- P1 — Ship at least one fully translated locale (activate the dormant i18n
  scaffold; exercise the RTL path on a real build).
- P1 — Wire slip/slide to timeline gestures (sequenced after the command-pattern
  undo Engine Candidate).

### Researcher Queue (Cycle 2 - 2026-06-04)

Focus: Android 15/16 platform behavior, notification trust, and backup/restore
data safety. These are net-new against the existing crash/network/CI/i18n/
slip-slide findings above.

#### Platform Reliability

- [x] ✅🔬🤖 P0 — Handle Android 15 `mediaProcessing` foreground-service timeouts
  - Why: NovaCut targets SDK 36 and exports through a
    `mediaProcessing` foreground service. Android allows this service type a
    limited runtime budget and calls `Service.onTimeout(int, int)` when the
    budget is exhausted; the service must stop within seconds or the app can
    ANR/crash during long exports.
  - Evidence: `AndroidManifest.xml` declares
    `foregroundServiceType="mediaProcessing"` for `ExportService`, and
    `ExportService.kt` passes `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` to
    `startForeground()` on Android 15+ but has no `onTimeout` override or timeout
    test path. Android's foreground-service type docs describe the 6-hours-per-
    24-hours budget and the required `stopSelf()` behavior:
    https://developer.android.com/about/versions/15/changes/foreground-service-types
  - Touches: `engine/ExportService.kt`, `engine/VideoEngine.kt` cancellation/error
    reporting, export UI state in `ExportDelegate`, and a focused service/unit or
    instrumentation test.
  - Acceptance: on API 35+, `ExportService.onTimeout(...)` cancels the active
    export, records a user-visible terminal error distinct from manual cancel,
    removes foreground notifications, calls `stopForeground()`/`stopSelf()`, and
    leaves any partial output in the same cleanup state as other export failures.
  - Verify: run an Android 15+ emulator with a shortened timeout such as
    `adb shell device_config put activity_manager media_processing_fgs_timeout_duration 10000`,
    start an export, confirm timeout cleanup and no ANR, then reset the device
    config.
  - Implemented in v3.74.33: `ExportService` overrides both timeout callback
    signatures, fails active media-processing exports through
    `VideoEngine.failExportDueToForegroundServiceTimeout`, removes foreground
    service state immediately, and covers timeout-type classification with JVM
    tests. Emulator shortened-timeout validation remains the device QA follow-up.
  - Complexity: M

- [x] ✅🔬🤖 P1 — Add a contextual Android 13+ notification-permission path for exports
  - Why: Export progress, completion, error, thermal, and cancel affordances rely
    on notifications, but newly installed Android 13+ apps have notifications off
    until the app requests `POST_NOTIFICATIONS`. Today NovaCut declares the
    permission but only requests `RECORD_AUDIO`, so a first export can silently
    lose drawer-visible progress and trust feedback.
  - Evidence: `AndroidManifest.xml` declares `POST_NOTIFICATIONS`; grep shows the
    only `ActivityResultContracts.RequestPermission()` launcher in
    `EditorScreen.kt` requests `Manifest.permission.RECORD_AUDIO`. Android's
    notification permission docs say newly installed Android 13+ apps must request
    the permission before sending notifications, and denied foreground-service
    notifications appear in Task Manager but not the notification drawer:
    https://developer.android.com/develop/ui/compose/notifications/notification-permission
  - Touches: export start flow, Settings notification/status row, string resources,
    and fallback in-app export progress messaging when notifications are denied.
  - Acceptance: before the first background export or thermal warning path on
    API 33+, NovaCut explains why export notifications matter, requests
    `POST_NOTIFICATIONS`, remembers denied/dismissed state, and keeps in-app
    progress/cancel controls complete when notification drawer delivery is blocked.
  - Verify: use the official ADB permission-state commands from the Android docs
    to simulate fresh install, allow, deny, and swipe-away states; confirm export
    notifications and in-app fallbacks behave correctly in each state.
  - Implemented in v3.74.34: `EditorScreen` shows a contextual explanation
    before the first API 33+ export notification request, remembers handled
    prompt state, routes export starts through the Compose permission layer, and
    keeps export running with in-app progress/cancel fallback copy when
    notifications are declined. Settings now shows enabled/off/not-required
    notification delivery state and opens Android notification settings for
    recovery. ADB permission-state validation remains the device QA follow-up.
  - Complexity: M

#### Data Safety

- [x] ✅🔬🤖 P1 — Split backup and device-transfer policy for managed media imports
  - Why: NovaCut copies picked media into `filesDir/media/imports`, but the
    current backup rules use include-whitelists that omit this directory. That
    avoids large cloud backups, but a device-transfer restore can resurrect
    project metadata without the imported media files those projects reference.
    Including all managed videos in cloud backup would also hit Android's 25 MB
    Auto Backup quota quickly, so the policy needs an explicit split instead of
    accidental omission.
  - Evidence: `LocalMediaImport.kt` defines `managedMediaDir(context)` as
    `filesDir/media/imports`; `backup_rules.xml` and `data_extraction_rules.xml`
    include database/autosave/generated media dirs but not `media/imports`.
    Android's Auto Backup docs state that once an `<include>` element is used,
    only included files are backed up, and Android 12+ lets apps separate
    `<cloud-backup>` from `<device-transfer>` so large files can be excluded from
    cloud but transferred device-to-device:
    https://developer.android.com/identity/data/autobackup
  - Touches: `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`,
    managed-media tests, Media Relink/Privacy Dashboard copy, and restore-health
    diagnostics.
  - Acceptance: cloud backup stays below quota and excludes bulky user media
    intentionally; device-transfer either includes `media/imports` or opens a
    first-run restore-health report that explains which projects need relinking;
    `disableIfNoEncryptionCapabilities` is evaluated for cloud backup because
    generated voiceover/TTS/media artifacts can be sensitive.
  - Verify: add tests that assert every durable app-owned media directory is
    classified as cloud-included, cloud-excluded, or D2D-included; run an emulator
    backup/restore or `bmgr` smoke with a project containing an imported local
    clip and confirm the restored project either plays or produces an actionable
    relink report.
  - Implemented in v3.74.35: `data_extraction_rules.xml` now excludes
    `media/imports` from encrypted cloud backup, includes it for Android 12+
    device transfer, and excludes managed-import partials. Legacy
    `backup_rules.xml` documents and preserves cloud-safe exclusion because it
    cannot split transfer modes. `BackupPolicyRulesTest` locks managed imports,
    generated media, and partial-file classification. Emulator `bmgr` or direct
    transfer validation remains the device QA follow-up.
  - Complexity: M

#### Quick Wins (Cycle 2)

- P1 — Add `ExportService.onTimeout(...)` handling and the shortened-timeout adb
  validation recipe before long-export QA.
- P1 — Add notification permission UX before the first Android 13+ background
  export.
- P1 — Add backup-rule classification tests for `media/imports`, generated media,
  diagnostics, models, and temporary/partial files.

#### Appendix — Cycle 2 Sources

- Android foreground-service media-processing timeout:
  https://developer.android.com/about/versions/15/changes/foreground-service-types
- Android notification runtime permission:
  https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android Auto Backup and data-extraction rules:
  https://developer.android.com/identity/data/autobackup

### Researcher Queue (Cycle 3 - 2026-06-04)

Focus: performance verification that complements the existing smoke-test and
release-artifact gates without duplicating the timeline decomposition lane.

#### Performance & Release Quality

- [x] ✅🔬🤖 P2 — Add Baseline Profile and Macrobenchmark coverage for launch and editor entry
  - Why: NovaCut has a Compose smoke harness and release APK verification, but
    no repeatable cold-start or critical-user-journey performance gate. A
    video editor's first-launch project gallery, blank-editor open, and timeline
    scroll paths are exactly the hot code paths that should be ahead-of-time
    optimized for new installs and updates.
  - Evidence: grep for `BaselineProfile`, `macrobenchmark`, `profileinstaller`,
    and `startup` finds no baseline-profile module, generated
    `baseline-prof.txt`, Macrobenchmark dependency, or Gradle task wiring. Android
    official guidance says Baseline Profiles let ART precompile specified startup
    and interaction paths and recommends generating them with the Jetpack
    Macrobenchmark library:
    https://developer.android.com/topic/performance/baselineprofiles/overview and
    https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
  - Touches: new `:benchmark` or `:baselineprofile` module, version catalog
    entries for Benchmark/ProfileInstaller as needed, Gradle Managed Device or
    CI runner wiring, project-gallery/editor/timeline CUJ tests, and release
    artifact verification docs.
  - Acceptance: a generated Baseline Profile ships with the release APK and covers
    project gallery launch, blank-project editor open, export sheet open, and a
    timeline scroll/scrub path; Macrobenchmark reports cold/warm startup and one
    editor interaction metric so regressions are visible before release.
  - Verify: run the profile generation task on a managed device/emulator, confirm
    the release APK contains the generated profile, and compare Macrobenchmark
    startup results with `CompilationMode.None` vs profile/default compilation.
  - Status: implemented in v3.74.45. `:baselineprofile` generated
    `app/src/main/baseline-prof.txt` from the managed Pixel 6 API 36 profile
    journey and the benchmark variant passed default cold startup,
    profile-required cold/warm startup, and blank-editor timeline scrub frame
    metrics. `CompilationMode.None` was attempted but consistently destabilized
    the managed emulator runner after the first successful macrobenchmark, so
    the committed suite uses default installed compilation versus
    profile-required compilation.
  - Complexity: M

#### Appendix — Cycle 3 Sources

- Android Baseline Profiles overview:
  https://developer.android.com/topic/performance/baselineprofiles/overview
- Android Baseline Profile creation with Macrobenchmark:
  https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- Android Macrobenchmark overview:
  https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

### Researcher Queue (Cycle 4 - 2026-06-04)

Focus: memory-pressure behavior for long editing sessions and large imported
projects.

#### Reliability & Performance

- [x] ✅ 🔬🤖 P2 — Add app-level memory trim policy for editor caches
  - Why: NovaCut opts into `android:largeHeap`, keeps a bitmap thumbnail LRU sized
    from `Runtime.maxMemory() / 8`, keeps waveform and generated-media caches, and
    exposes a user-facing thumbnail-cache size setting. Existing OOM cleanup fixes
    individual error paths, but the app does not voluntarily trim caches when the
    OS reports UI-hidden/background/moderate/critical memory pressure.
  - Evidence: `AndroidManifest.xml` sets `android:largeHeap="true"`;
    `VideoEngine.kt` owns `thumbnailCache` and `clearThumbnailCache()`;
    `AudioEngine.kt` owns `waveformCache` and `clearWaveformCache()`; grep for
    `onTrimMemory`, `ComponentCallbacks2`, and `TRIM_MEMORY` returns no app-level
    memory-pressure handler. Android's memory guidance recommends using
    `onTrimMemory()` to reduce memory usage when lifecycle or memory events make
    trimming useful, and the `<application>` docs state most apps should reduce
    memory usage rather than relying on `largeHeap`:
    https://developer.android.com/topic/performance/memory and
    https://developer.android.com/guide/topics/manifest/application-element#largeHeap
  - Touches: `NovaCutApp`, cache-owning engines (`VideoEngine`, `AudioEngine`,
    proxy/model/generated-media caches where applicable), Settings cache-size
    semantics, and focused policy tests.
  - Acceptance: an app-level trim dispatcher maps `TRIM_MEMORY_UI_HIDDEN`,
    `RUNNING_LOW`, `RUNNING_CRITICAL`, `BACKGROUND`, and `COMPLETE` into explicit
    cache actions; visible editing preserves enough preview state for continuity,
    while background/critical states evict thumbnail/waveform/proxy scratch caches
    and log a redacted diagnostic breadcrumb.
  - Verify: unit-test the trim-level policy, call `adb shell am send-trim-memory
    com.novacut.editor MODERATE` / `CRITICAL` on a debug build, confirm caches
    evict without crashing or cancelling active export, and repeat while returning
    from background to a project with thumbnails/waveforms.
  - Status: implemented in v3.74.46. `NovaCutApp.onTrimMemory` now routes trim
    levels through `MemoryTrimDispatcher`; `MemoryTrimPolicy` maps UI-hidden,
    running-low, running-critical, background, moderate, and complete levels to
    explicit thumbnail/waveform/proxy cache actions; active `VideoEngine`,
    `AudioEngine`, and `ProxyEngine` instances register trim callbacks through a
    lightweight registry; proxy trimming skips in-flight renders; and
    diagnostics include bounded redacted `memory-trim.jsonl` breadcrumbs when
    present.
  - Complexity: M

#### Appendix — Cycle 4 Sources

- Android memory management and `onTrimMemory()`:
  https://developer.android.com/topic/performance/memory
- Android `<application>` `largeHeap` guidance:
  https://developer.android.com/guide/topics/manifest/application-element#largeHeap

### Researcher Queue (Cycle 5 - 2026-06-04)

Focus: Play/Fastlane release readiness beyond the already-tracked changelog
history item.

#### Distribution Trust

- [x] ✅ 🔬🤖 P2 — Add Play listing asset and privacy-disclosure release gate
  - Why: NovaCut has enough Fastlane text metadata to describe the editor, but
    the repo does not yet carry the preview assets, screenshot inventory,
    privacy-policy link/text, or data-safety worksheet needed to make Play
    listing readiness repeatable. That leaves release approval dependent on
    manual console state instead of versioned evidence.
  - Evidence: `fastlane/metadata/android/en-US/` currently contains only
    `title.txt`, `short_description.txt`, `full_description.txt`, and
    `changelogs/67.txt`; grep finds no Fastlane `images/` directory,
    `phoneScreenshots/`, `sevenInchScreenshots/`, `tenInchScreenshots/`,
    `featureGraphic`, `icon`, `Fastfile`, or `Appfile`. The manifest declares
    `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`,
    app-owned backup/transfer rules, and content import/export surfaces, so the
    Play Data safety and privacy-policy package needs to distinguish local-only
    editing, optional model/download/network behavior, microphone capture,
    notifications, diagnostics, and user-directed export/share flows. Google
    Play preview-asset guidance requires an app icon, feature graphic, and at
    least two screenshots to publish a store listing, and recommends at least
    four app screenshots at 1080px+ for promotional eligibility:
    https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
  - Touches: Fastlane metadata structure, generated/captured store screenshots
    for phone and large-screen editor flows, feature graphic/icon export,
    privacy-policy source doc or URL wiring, data-safety worksheet, and release
    verification scripts/docs.
  - Acceptance: the repo has a deterministic store-listing package with
    Fastlane-compatible `images/featureGraphic`, `images/icon`, at least four
    phone screenshots, tablet screenshots or an explicit non-tablet-store
    rationale, alt-text/caption inventory for each graphic, a privacy-policy
    source/link that names NovaCut and contact/deletion/retention handling, and
    a data-safety worksheet mapping each declared permission and SDK/user-data
    flow to collected/shared/optional/local-only status.
  - Verify: add a local metadata validator that checks Fastlane image paths,
    dimensions, file types, screenshot counts, changelog coverage, and
    privacy/data-safety worksheet presence; run it in the release gate, then do a
    Play Console or Fastlane supply dry run for metadata/images before promoting
    a release candidate.
  - Status: implemented in v3.74.47. Fastlane now includes
    `images/icon.png`, `images/featureGraphic.png`, four phone screenshots,
    four tablet screenshots, SVG sources, and `asset_inventory.json` alt text.
    `docs/privacy-policy.md`, `docs/play-data-safety.md`, and
    `fastlane/metadata/android/en-US/privacy_policy_url.txt` provide the
    privacy/disclosure package. `scripts/validate_play_listing_assets.py`
    checks dimensions, PNG color type, screenshot counts, inventory coverage,
    text bounds, privacy policy content, and manifest permission disclosure, and
    the GitHub Actions build runs it after release metadata verification.
  - Complexity: M

#### Appendix — Cycle 5 Sources

- Google Play preview asset requirements:
  https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
- Google Play User Data policy, privacy policy, and Data safety consistency:
  https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Google Play Data safety form guidance:
  https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Fastlane supply metadata image and screenshot structure:
  https://docs.fastlane.tools/actions/supply/

### Researcher Queue (Cycle 6 - 2026-06-04)

Focus: incoming media handoff from other apps, without changing the existing
`content://`-only safety posture.

#### Import & Interop

- [x] 🔬🤖 P1 — Add Sharesheet-compatible incoming media routing
  - Why: NovaCut's manifest comments describe video/image/audio handoff from
    other apps, but the exported activity is not registered for Android
    Sharesheet `ACTION_SEND` or `ACTION_SEND_MULTIPLE`. Users sharing a clip,
    image, or audio file from Photos, Files, or another editor can miss NovaCut
    as a target, and image/audio entries that do arrive through `ACTION_VIEW`
    still fall into a video-only project creation path.
  - Evidence: `AndroidManifest.xml` has three media `ACTION_VIEW` filters
    (`video/*`, `image/*`, `audio/*`) and comments naming image/audio share
    targets, but grep finds no manifest receiver for `ACTION_SEND` or
    `ACTION_SEND_MULTIPLE`. `MainActivity.handleIncomingIntent()` handles only
    `Intent.ACTION_VIEW`, reads a single `intent.data` URI, and stores it in the
    historical `pendingVideoUri` state. `ProjectListScreen` forwards that value
    to `ProjectListViewModel.createProjectFromImport(...)`, which calls
    `importUriToManagedMedia(..., "video")` and rejects sources that do not have
    a visual track. Android's current sharing docs say apps receive Sharesheet
    data by adding `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filters and
    reading `EXTRA_STREAM` for single or multiple binary items:
    https://developer.android.com/training/sharing/receive
  - Touches: manifest media send filters, a pure incoming-media parser for
    `ACTION_VIEW`/`ACTION_SEND`/`ACTION_SEND_MULTIPLE`, `MainActivity` pending
    import state, project-gallery and editor routing for video/image/audio,
    copy/validation reuse in `LocalMediaImport`, strings, and focused parser/UI
    tests.
  - Acceptance: NovaCut appears in Android Sharesheet for single video, image,
    and audio shares plus multiple selected media files; video shares can create
    a new project, image/audio shares either attach to the current project or
    open a clear project-selection/add-media flow, multiple shares preserve
    order where the sender supplies it, and every accepted URI remains
    `content://` with read permission handling and MIME/size validation.
  - Verify: add JVM tests for an `IncomingMediaIntentParser` covering
    `ACTION_VIEW`, single `ACTION_SEND`, multi-item `ACTION_SEND_MULTIPLE`,
    malformed MIME, missing grants, and mixed media; add a manifest/intent
    resolution test; then run device or emulator adb shares against `video/*`,
    `image/*`, `audio/*`, and multiple `EXTRA_STREAM` URIs to confirm routing and
    user-facing copy.
  - Completed: v3.74.40 added Sharesheet `ACTION_SEND` and
    `ACTION_SEND_MULTIPLE` filters for video, image, and audio, centralized
    incoming-media intent parsing with `content://` and read-grant enforcement
    for send actions, routed pending imports as ordered media items, and creates
    a new project from shared video/image/audio clips while preserving visual
    and audio ordering on their respective tracks. Focused JVM coverage locks
    parser behavior and manifest send filters; emulator adb share validation is
    still covered by the CI/device follow-up lanes.
  - Complexity: M

#### Appendix — Cycle 6 Sources

- Android receiving simple data from other apps:
  https://developer.android.com/training/sharing/receive
- Android sending simple data and multiple content URIs:
  https://developer.android.com/training/sharing/send

### Researcher Queue (Cycle 7 - 2026-06-04)

Focus: turning the standing light/dark/high-contrast UX audit requirement into
an executable appearance and accessibility regression gate for the Compose app.

#### Accessibility & Visual Trust

- [x] ✅ 🔬🤖 P2 — Add appearance-mode and contrast regression gates
  - Why: NovaCut's process now requires every UX audit to cover light, dark, and
    high-contrast states, but the app has only one hard-coded Catppuccin Mocha
    dark `ColorScheme`. Core text colors pass a spot contrast check, but lower
    emphasis tokens used for overlays, strokes, and secondary UI can fall below
    WCAG/Android non-text guidance when reused as semantic indicators, and there
    is no automated Compose accessibility check to catch regressions.
  - Evidence: `Theme.kt` defines a single `NovaCutColorScheme = darkColorScheme`
    and `NovaCutTheme` always applies it; grep finds no light/high-contrast
    scheme branch or theme setting. `app/build.gradle.kts` includes Compose UI
    test dependencies but not `ui-test-junit4-accessibility`, and
    `NovaCutSmokeTest.kt` does not call `enableAccessibilityChecks()` or
    `tryPerformAccessibilityChecks()`. A local token sanity check measured
    `Overlay0` on `PanelHighest` at 2.92:1 and `CardStrokeStrong` on `Panel` at
    1.81:1, so those colors should not be treated as sufficient text or
    non-text state indicators without a high-contrast policy. Android's Compose
    accessibility testing docs recommend `ui-test-junit4-accessibility` plus
    `enableAccessibilityChecks()` for checks including color contrast, touch
    target size, and traversal order:
    https://developer.android.com/develop/ui/compose/accessibility/testing
  - Touches: `ui/theme/Theme.kt`, appearance settings/DataStore, theme preview
    or screenshot fixtures for gallery/editor/export/Settings, the Compose smoke
    harness, and any components that currently encode state through low-contrast
    strokes or color-only accents.
  - Acceptance: NovaCut exposes a documented appearance policy (System/Dark plus
    either Light or explicit dark-only rationale, and High Contrast); high
    contrast replaces low-emphasis strokes/chips with AA-safe text and 3:1+
    non-text indicators; the smoke flow can run accessibility checks from root
    on gallery, editor empty state, media picker, export sheet, Settings, and
    privacy dashboard; suppressions are narrowly documented with follow-up
    tickets, not broad disables.
  - Verify: run the Compose accessibility smoke on an emulator with normal and
    high-contrast appearance, capture before/after screenshots for the main
    surfaces, manually sample text/icon/stroke contrast against WCAG ratios, and
    perform one TalkBack traversal of the editor timeline and export sheet.
  - Status: implemented in v3.74.48. Settings persists
    `AppearanceMode.SYSTEM`, `DARK`, or `HIGH_CONTRAST_DARK`; `NovaCutTheme`
    resolves System to the documented dark editing canvas until a light palette
    has full screenshot QA; shared chrome consumes high-contrast semantic
    tokens through `LocalNovaCutColors`; `NovaCutSmokeTest` enables Compose
    accessibility checks and calls root checks on the main smoke surfaces; and
    `NovaCutAppearancePolicyTest` locks WCAG AA text contrast, 3:1 non-text
    indicators, selected-chip readability, and low-emphasis token guardrails.
  - Complexity: M

#### Appendix — Cycle 7 Sources

- Android Compose accessibility testing:
  https://developer.android.com/develop/ui/compose/accessibility/testing
- Android Espresso accessibility checking:
  https://developer.android.com/training/testing/espresso/accessibility-checking
- Android color contrast guidance:
  https://support.google.com/accessibility/android/answer/7158390?hl=en
- Jetpack Compose Material 3 theming:
  https://developer.android.com/develop/ui/compose/designsystems/material3
- Android dark theme guidance:
  https://developer.android.com/develop/ui/views/theming/darktheme
- WCAG 2.2 contrast and mobile accessibility criteria:
  https://www.w3.org/TR/WCAG22/

### Researcher Queue (Cycle 8 - 2026-06-04)

Focus: external document handoff for NovaCut-owned plugin, LUT, project archive,
and timeline-interchange files without broadening the media importer into an
unsafe catch-all receiver.

#### Import & Interop

- [x] ✅🔬🤖 P2 — Add non-media document import router for plugins and interchange files
  - Why: NovaCut already documents shareable plugin/LUT files, project archive
    export, and OTIO/FCPXML/EDL interchange, but Android only lists an app for
    implicit opens/shares when the action, category, and data type/scheme match
    its intent filters. Today NovaCut registers only media `ACTION_VIEW` filters
    and `MainActivity` only keeps video/image/audio MIME prefixes, so opening
    `.cube`, `.3dl`, `.ncfx`, `.ncstyle`, `.novacut-template`, `.ncfxd`,
    `.otio`, `.fcpxml`, `.edl`, or a project archive ZIP from Files or another
    app can either omit NovaCut from the resolver or silently ignore the URI.
  - Evidence: `docs/templates.md` lists the plugin family and validation path
    for `.novacut-template`, `.ncfx`, `.ncstyle`, `.cube`, `.3dl`, and
    `.ncfxd`; `README.md` advertises LUT import, `.ncfx` import/export,
    project archive ZIP export, and OTIO/FCPXML/EDL interchange. The manifest
    has `content://` `ACTION_VIEW` filters only for `video/*`, `image/*`, and
    `audio/*`; grep found no document MIME filters for plugin/interchange
    files. `MainActivity.handleIncomingIntent()` handles only `ACTION_VIEW`,
    reads `intent.data`, requires `contentResolver.getType(uri)` to start with
    `video/`, `image/`, or `audio/`, then stores the URI in the historical
    `pendingVideoUri`. `PluginRegistry` already classifies the plugin file
    extensions, `ProjectArchive.importArchiveWithReport(...)` already provides
    structured ZIP import diagnostics, and `TimelineImportEngine` detects
    FCPXML/OTIO/EDL but currently returns an honest "not yet implemented" report.
    Android's receiving guide warns to register specific MIME types, avoid
    broad `*/*` receivers unless the app can handle anything, validate incoming
    MIME/size, and process binary data off the UI thread:
    https://developer.android.com/training/sharing/receive
  - Touches: a pure `IncomingDocumentIntentParser` or document branch beside
    the incoming media parser, manifest filters for specific lower-case
    `content://` document MIME types/routes, `MainActivity` pending import
    state, `PluginRegistry`, `TemplateManager`, `EffectShareEngine`,
    `LutEngine`, `OpenFxDescriptor`, `ProjectArchive.importArchiveWithReport`,
    `TimelineImportEngine`, import-preview/report UI, strings, and focused
    parser/manifest/device tests.
  - Acceptance: NovaCut appears in Android's resolver for the supported plugin,
    LUT, archive, and interchange document types without registering an
    unrestricted `*/*` catch-all; every accepted URI remains `content://`,
    is size/type/extension checked, and opens a preview/report that names the
    file kind, target action, fidelity or unsupported-parser status, and
    warnings before mutating project state. Implemented loaders route to their
    existing engines; pending loaders such as timeline import surface clear
    blocked/stub status instead of claiming success.
  - Verify: add JVM tests for classifier behavior across `ACTION_VIEW`,
    `ACTION_SEND`, malformed type, missing display name, uppercase extension,
    oversized input, and unknown extension; add a manifest/intent-resolution
    test for text/plain, application/json, application/octet-stream, zip, and
    XML-like payloads; then run emulator or device opens from Files/adb for
    `.cube`, `.ncfx`, `.novacut-template`, `.ncfxd`, `.otio`, `.fcpxml`, `.edl`,
    and archive ZIP to confirm resolver visibility, preview copy, and no silent
    project mutation on malformed files.
  - Complexity: M

#### Appendix — Cycle 8 Sources

- Android receiving simple data from other apps:
  https://developer.android.com/training/sharing/receive
- Android `IntentFilter` matching rules:
  https://developer.android.com/reference/android/content/IntentFilter
- OpenTimelineIO architecture and timeline model:
  https://otio-core-documentation.readthedocs.io/en/latest/tutorials/architecture.html
- Apple Final Cut Pro XML transfer guide:
  https://support.apple.com/guide/final-cut-pro/use-xml-to-transfer-projects-verdbd66ae/mac

### Researcher Queue (Cycle 9 - 2026-06-04)

Focus: postmortem diagnostics for process deaths that do not pass through the
app's Java uncaught-exception handler.

#### Reliability & Diagnostics

- [x] ✅🔬🤖 P2 — Add `ApplicationExitInfo` process-death capture to diagnostic ZIP
  - Why: The Cycle 1 crash handler item closes Java uncaught-exception capture,
    but it cannot fully explain ANRs, low-memory kills, native crashes, OS
    signals, freezer kills, initialization failures, or other process deaths
    that happen outside a normal exception chain. Android 11+ exposes recent
    process-death records through `ActivityManager.getHistoricalProcessExitReasons()`;
    NovaCut should fold those local records into the same user-initiated
    diagnostic ZIP so creator support can distinguish "export crashed" from
    "foreground export was low-memory killed" or "main thread ANR".
  - Evidence: grep across `app/src/main` finds no `ApplicationExitInfo`,
    `getHistoricalProcessExitReasons`, `REASON_ANR`, `REASON_LOW_MEMORY`, or
    process-exit recorder. `NovaCutApp.VERSION` already notes crash reports as a
    consumer, but `NovaCutApp.onCreate()` only creates notification channels.
    `DiagnosticExportEngine` writes `app-info.txt`, `device-info.txt`,
    `media-codecs.txt`, `model-registry.txt`, optional `timeline-shape.json`,
    `logcat-tail.txt`, and `manifest.txt`, with no previous-exit history entry.
    `PrivacyDashboard` documents diagnostic logs as user-exported, local-only
    data capped under `filesDir/diagnostics`, so the process-exit record needs
    the same retention and opt-in export boundary. Android's Android 11 feature
    docs say the process-exit API reports whether recent terminations were ANR,
    memory, crash, or other reasons:
    https://developer.android.com/about/versions/11/features#app-process-exit-reasons
  - Touches: a small `ProcessExitRecorder`/store behind an `ActivityManager`
    adapter, `NovaCutApp.onCreate()` startup ingestion on API 30+,
    `DiagnosticExportEngine` entry such as `process-exit-history.json`,
    `PrivacyDashboard` copy, redaction helpers for descriptions/traces, and
    focused JVM/instrumentation tests.
  - Acceptance: on API 30+ NovaCut records the latest bounded set of unique
    exit records once per process start, de-duped by timestamp/reason/pid,
    including reason, status, process name, timestamp, importance, PSS/RSS, and
    a redacted/truncated ANR trace excerpt when `getTraceInputStream()` is
    available. The data remains local, is included only in user-triggered
    diagnostic ZIP exports, omits project names/media URIs/captions, and clearly
    marks unsupported pre-API-30 devices. The existing uncaught-exception handler
    item remains responsible for active Java crash capture; this item covers
    postmortem OS records.
  - Verify: add JVM tests around a fake `ActivityManager` adapter covering
    crash, native crash, ANR trace, low-memory/signal fallback, duplicate
    records, redaction, and ZIP inclusion; add an API 30+ instrumentation smoke
    that launches after `adb shell am crash <package>` or a debug-only ANR test
    hook and confirms the diagnostic ZIP contains the expected exit reason
    without raw user paths.
  - Complexity: M

#### Appendix — Cycle 9 Sources

- Android 11 app process exit reasons:
  https://developer.android.com/about/versions/11/features#app-process-exit-reasons
- Android `ApplicationExitInfo` API:
  https://developer.android.com/reference/android/app/ApplicationExitInfo
- Android `ActivityManager.getHistoricalProcessExitReasons` API:
  https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int)
- Android vitals ANR guidance:
  https://developer.android.com/topic/performance/vitals/anr
- Android vitals low-memory-killer guidance:
  https://developer.android.com/topic/performance/vitals/lmk

### Researcher Queue (Cycle 10 - 2026-06-04)

Focus: settings persistence recovery for Preferences DataStore file-level
corruption without weakening the existing per-key defaulting and validation.

#### Persistence & Diagnostics

- [x] 🔬🤖 P2 — Add Preferences DataStore corruption recovery and settings-reset report
  - Why: NovaCut persists export defaults, autosave interval, proxy mode,
    model-download Wi-Fi policy, one-handed/desktop preferences, and the
    AcoustID key in `novacut_settings`. `SettingsRepository` validates individual
    values after a readable preferences file is emitted, but file-level
    DataStore corruption can still fail before mapping. Android's DataStore docs
    say corrupted data is surfaced as `CorruptionException` from the `data` flow
    unless a `corruptionHandler` replaces it, so NovaCut should recover to known
    defaults and make the reset visible in diagnostics instead of silently
    staying on the `SettingsViewModel` initial `AppSettings()`.
  - Evidence: `SettingsRepository` creates `novacut_settings` with the
    `preferencesDataStore` delegate, catches only `IOException` from
    `context.dataStore.data`, emits `emptyPreferences()` for that IO case, and
    otherwise rethrows. The mapper clamps or defaults unknown enum/out-of-range
    keys and bounds the AcoustID string, which protects readable settings but
    not a corrupted preferences file. Grep found no `CorruptionException`,
    `ReplaceFileCorruptionHandler`, `PreferenceDataStoreFactory`, or
    settings-reset report. `SettingsViewModel.settings` starts with
    `AppSettings()` and `PrivacyDashboard` documents settings/preferences as
    local DataStore data with export/delete controls, but neither exposes a
    "settings were reset" diagnostic state. Android's official DataStore guide
    documents `corruptionHandler` as the supported way to replace corrupted data
    with defaults:
    https://developer.android.com/topic/libraries/architecture/datastore#corruption
  - Touches: a `PreferenceDataStoreFactory` or delegate setup with
    `ReplaceFileCorruptionHandler { emptyPreferences() }`, a small
    settings-reset reporter that does not depend on the corrupted store itself,
    `SettingsRepository`, `SettingsViewModel`, `PrivacyDashboard` copy,
    `DiagnosticExportEngine`, strings, and focused DataStore corruption tests.
  - Acceptance: a corrupted `novacut_settings` file is detected, replaced with
    defaults, and followed by successful reads and writes. The user sees one
    non-blocking Settings notice, diagnostics include a redacted reset
    reason/timestamp, and no AcoustID/API secret or proxy credential is written
    to the diagnostic ZIP. Valid but unknown enum or out-of-range values still
    fall back per key without wiping the whole store.
  - Verify: add a temp-file DataStore test that writes invalid preference bytes,
    asserts defaults are emitted, performs an update after reset, and checks the
    reset report. Add mapper tests proving readable-but-invalid enum/range keys
    still default per key. Add a diagnostic ZIP/privacy-dashboard test that
    includes the reset timestamp/reason while excluding stored secrets, plus an
    emulator smoke restart after corrupting `novacut_settings.preferences_pb`.
  - Complexity: M
  - Status: implemented in v3.74.51. `SettingsRepository` now constructs
    Preferences DataStore with `PreferenceDataStoreFactory` and
    `ReplaceFileCorruptionHandler`, records file-level resets through
    `SettingsResetReportStore`, keeps readable invalid values on per-key
    defaults without full-store wipes, shows a dismissible Settings notice, adds
    a Privacy Dashboard category, and includes `settings-reset-report.jsonl` in
    diagnostic ZIPs when records exist. JVM tests cover invalid preference
    bytes, post-reset writes, mapper defaults, reset-report redaction, and
    Privacy Dashboard disclosure. Device/emulator corruption smoke remains a
    manual QA follow-up because this pass verified the temp-file DataStore path
    and full APK packaging locally.

#### Appendix — Cycle 10 Sources

- Android DataStore file-corruption handling:
  https://developer.android.com/topic/libraries/architecture/datastore#corruption
- Android `ReplaceFileCorruptionHandler` API:
  https://developer.android.com/reference/kotlin/androidx/datastore/core/handlers/ReplaceFileCorruptionHandler

### Researcher Queue (Cycle 11 - 2026-06-04)

Focus: make the advertised sticker/GIF/image overlay lane durable and visible in
preview/export, using app-owned assets instead of fragile raw picker URIs.

#### Creator Workflow & Export Fidelity

- [x] ✅ 🔬🤖 P1 — Add durable image/sticker overlay compositor and asset store
  - Why: NovaCut advertises sticker/GIF/image overlays and exposes a sticker
    picker, custom image picker, autosave persistence, undo snapshots, archive
    packing, and stream-copy disqualification for `imageOverlays`, but the
    current preview/export paths do not appear to composite those overlays.
    `VideoEngine` builds export `OverlayEffect` entries from text overlays,
    Lottie overlays, and the optional brand watermark only; `PreviewPanel`
    renders the current clip/player and chrome, not `imageOverlays`. That means
    a creator can add a sticker-like project object that survives autosave but
    may not show in live preview or the final export.
  - Evidence: README lists "Sticker/GIF/image overlays" as a feature.
    `StickerPickerPanel` creates bundled sticker URIs such as
    `content://com.novacut.editor.stickers/emoji/0`, while the manifest declares
    only AndroidX startup and `${applicationId}.fileprovider` providers, not a
    sticker provider. `EditorScreen` sends gallery stickers through
    `PickVisualMedia(ImageOnly)` directly to `viewModel.addImageOverlay(...)`;
    `OverlayDelegate.addImageOverlay(...)` stores the URI as
    `ImageOverlay.sourceUri`; `ProjectAutoSave` serializes that URI; and
    `ProjectArchive` registers image overlays for media packing. `ExportDelegate`
    disables stream-copy when `state.imageOverlays` is non-empty, but the
    `videoEngine.export(...)` and `exportMixed(...)` calls pass `textOverlays`
    and tracked objects only. Android's Photo Picker docs say default media
    access lasts only until the device restarts or the app stops unless
    persisted, and the SAF docs note even persisted document URIs can break
    when the document is moved or deleted:
    https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access
  - Touches: an `OverlayAssetStore` or generalized `LocalMediaImport` path for
    image/GIF/watermark assets, a bundled-sticker resolver that renders shelf
    glyphs/assets to real app-owned files instead of fake content URIs,
    `OverlayDelegate`/`EditorViewModel` async import states, `PreviewPanel`
    image overlay rendering and transform handles, a Media3
    `BitmapOverlay`/custom `TextureOverlay` export implementation,
    `ProjectAutoSave`, `ProjectArchive`, `MediaRelinkProbe`, privacy/diagnostic
    copy, strings, and focused tests.
  - Acceptance: bundled stickers, custom image stickers, still image overlays,
    and brand watermark assets all resolve to durable app-owned sources or a
    deliberately persisted SAF grant before project state is mutated. Overlays
    are visible at the correct playhead range in preview, transform gestures
    update position/scale/rotation/opacity, and full exports burn them in with
    the same geometry. GIF overlays either animate through a bounded frame or
    Media3 texture path or are clearly marked unsupported before insertion; no
    GIF silently becomes a broken still. Missing/moved external sources produce
    relink cards and diagnostic hints instead of silent no-op exports.
  - Verify: add JVM tests for overlay-asset import, bundled-sticker resolution,
    autosave/archive round-trip, missing-source relink classification, and GIF
    unsupported/animated decisions. Add Compose tests that insert bundled and
    gallery stickers, scrub through active/inactive ranges, and confirm preview
    semantics/controls. Add an instrumentation or golden-pixel export test where
    a high-contrast sticker appears at expected frame coordinates after app
    restart, plus a watermark restart test proving `takePersistableUriPermission`
    or local-copy behavior survives reboot/app-stop conditions.
  - Complexity: L
  - Status: implemented in v3.74.52. `OverlayAssetStore` resolves bundled
    sticker shelf URIs into app-owned PNG files and copies gallery image
    stickers into `filesDir/media/overlays` before `OverlayDelegate` mutates
    project state. GIF imports are rejected with explicit copy until animated
    overlays have a bounded implementation. `PreviewPanel` renders active
    image overlays over clip media, `ExportImageOverlay` burns them into
    Media3 Transformer exports with the same center-offset geometry and active
    time range, and `MediaRelinkProbe` now reports overlay-source accessibility.
    Focused JVM tests cover bundled URI parsing, GIF rejection, export timing
    and geometry helpers, missing overlay-source classification, and privacy
    dashboard disclosure. Autosave/archive schema stayed unchanged because
    overlay URIs were already serialized and packed; this pass makes the saved
    URIs durable before they enter that existing path. Compose and golden-pixel
    instrumentation remain follow-up validation once a device/emulator is
    available.

#### Appendix — Cycle 11 Sources

- Android Photo Picker media-access persistence:
  https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access
- Android Storage Access Framework persisted permission caveats:
  https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions
- AndroidX Media3 `BitmapOverlay` API:
  https://developer.android.com/reference/androidx/media3/effect/BitmapOverlay

### Researcher Queue (Cycle 12 - 2026-06-04)

Focus: prepare NovaCut's future live-streaming/LAN destination path for Android
16 opt-in local-network restrictions and Android 17 enforcement.

#### Platform Permissions & Live Output

- [x] ✅ 🔬🤖 P2 — Add Android local-network permission gate for LAN streaming destinations
  - Why: `OutputStreamingEngine` already classifies public internet, LAN,
    multicast, loopback, IPv6 local, and `.local` destinations and its comments
    say the UI should show a one-time local-network consent sheet before
    connecting. The manifest, strings, and UI do not yet declare or request
    `NEARBY_WIFI_DEVICES` for Android 16 opt-in testing or future
    `ACCESS_LOCAL_NETWORK` for Android 17 target-SDK enforcement. A creator
    streaming to OBS, an RTMP/SRT box, RTSP camera, NDI bridge, or multicast
    target on the same Wi-Fi could therefore see opaque socket timeouts once the
    streaming library is enabled and platform restrictions are active.
  - Evidence: grep finds `OutputStreamingEngine.requiresLocalNetworkPermission`
    and JVM tests for LAN/multicast classification, but no
    `NEARBY_WIFI_DEVICES`, `ACCESS_LOCAL_NETWORK`, `RESTRICT_LOCAL_NETWORK`,
    local-network permission strings, or runtime permission launcher in
    `app/src/main`. Android's local-network permission docs say Android 16
    uses opt-in restrictions for target SDK 36 apps, Android 17 blocks local
    network by default for target SDK 37+ apps, Android 16 testing restores
    access through `NEARBY_WIFI_DEVICES`, and Android 17 uses
    `ACCESS_LOCAL_NETWORK` in the `NEARBY_DEVICES` group:
    https://developer.android.com/privacy-and-security/local-network-permission
  - Touches: `AndroidManifest.xml`, a `LocalNetworkPermissionPolicy` that maps
    SDK/target behavior to `NEARBY_WIFI_DEVICES` vs `ACCESS_LOCAL_NETWORK`,
    future Live Studio / streaming destination UI, `OutputStreamingEngine`,
    user-facing rationale and denial copy, privacy-dashboard permission notes,
    diagnostic export of local-network permission state, and focused policy/UI
    tests.
  - Acceptance: public RTMP/RTMPS/SRT/WebRTC destinations do not request a
    local-network permission; RFC1918, link-local, `.local`, multicast, and
    direct LAN destinations show a scoped rationale before first connection.
    Denial blocks only the LAN destination with actionable copy and does not
    break public internet streaming. Granted/revoked states are rechecked before
    each connection, persisted stream keys remain in encrypted storage, and
    native/socket errors caused by local-network denial are surfaced as a
    permission problem rather than a generic timeout.
  - Verify: keep the existing classifier JVM tests, add policy tests across API
    35/36/37 and target SDK 36/37, add manifest tests for the temporary and
    future permissions, and add UI tests for public vs LAN destinations. On an
    Android 16 device/emulator, enable `RESTRICT_LOCAL_NETWORK`, reboot, confirm
    denied LAN connection fails with the new permission copy, grant Nearby
    devices, and confirm the same connection reaches the native streaming stub;
    repeat with Android 17 preview behavior when the SDK exposes
    `ACCESS_LOCAL_NETWORK`.
  - Complexity: M
  - Status: implemented in v3.74.53. The manifest declares Android 16
    `NEARBY_WIFI_DEVICES` with `neverForLocation` and future Android 17
    `ACCESS_LOCAL_NETWORK`. `LocalNetworkPermissionPolicy` maps runtime SDK,
    target SDK, and destination scope to no gate, Android 16 temporary
    permission, or Android 17 local-network permission; public internet and
    loopback destinations stay ungated. `OutputStreamingEngine` exposes
    destination-specific decisions and permission-aware LAN failure copy, and
    `DiagnosticExportEngine` can include redacted permission-state snapshots.
    JVM tests cover API 35/36/37 policy behavior, granted/denied states,
    public-vs-LAN destination decisions, manifest declarations, diagnostic
    redaction, and existing LAN/multicast classifiers. Verification passed:
    focused JVM coverage, `:app:testDebugUnitTest`, `:app:assembleDebug`,
    `:app:assembleRelease`, `:app:assembleDebugAndroidTest`,
    `scripts/verify_release_artifacts.py`, `scripts/validate_play_listing_assets.py`,
    `scripts/sync_fastlane_changelogs.py --check`, and `git diff --check`.
    Android 16 `RESTRICT_LOCAL_NETWORK` and Android 17 preview device behavior
    remain manual validation because this build environment has no active
    emulator.

#### Appendix — Cycle 12 Sources

- Android local network permission:
  https://developer.android.com/privacy-and-security/local-network-permission
- Android 16 behavior changes:
  https://developer.android.com/about/versions/16/behavior-changes-16

### Researcher Queue (Cycle 13 - 2026-06-04)

Focus: turn NovaCut's AI provenance output from an unsigned detached draft into
a verifiable C2PA Content Credentials export path.

#### Export Provenance & AI Disclosure

- [ ] 🔬🤖 P1 — Wire signed/embedded C2PA export manifests and current CAWG training labels
  - Why: the export sheet tells users AI-assisted edits will be declared in a
    provenance sidecar, and `ExportDelegate` can write a C2PA-shaped JSON draft,
    but the exported MP4 is not signed, does not contain a C2PA manifest store,
    and cannot be verified by a C2PA consumer. This is a trust gap for any
    "Content Credentials" or machine-readable AI disclosure copy, especially
    because the local engine already models Android Keystore, StrongBox, user
    PEM, and web-service signing modes.
  - Evidence: grep finds no `contentauth`, `c2pa-android`, or `simple-c2pa`
    Gradle dependency. `C2paExportEngine.isAvailable()` probes for
    `org.contentauth.c2pa.Builder` / `org.proofmode.simplec2pa.Manifest` and
    the JVM tests assert it is false on the current classpath.
    `signAndEmbed(...)` currently returns `SignResult.Unavailable`, while
    `ExportDelegate.writeC2paManifestSidecar(...)` only writes
    `<export>.c2pa-manifest.json` and never embeds it into the MP4. C2PA 2.4
    says a claim is cryptographically hashed and signed, a standard manifest
    must contain exactly one hard binding to the asset, and BMFF/MP4 assets use
    embedded `uuid` C2PA boxes:
    https://spec.c2pa.org/specifications/specifications/2.4/specs/C2PA_Specification.html
  - Current-source check: official c2pa-android documentation now describes an
    Android AAR with Kotlin APIs for manifest reading, validation, creation, and
    signing, including Android Keystore, StrongBox, direct, callback, and
    web-service signers:
    https://opensource.contentauthenticity.org/docs/c2pa-android/
    The current CAWG Training and Data Mining assertion uses
    `cawg.training-mining` and `cawg.*` entries, while NovaCut emits the older
    `c2pa.training-mining` and
    `c2pa.*` names:
    https://cawg.io/training-and-data-mining/1.1/
  - Touches: Gradle dependency/repository review for a pinned C2PA Android AAR
    or vetted equivalent, `C2paExportEngine.signAndEmbed`, Android
    Keystore/StrongBox key and certificate enrollment policy, optional remote
    signer consent, CAWG training/mining assertion label migration, export-sheet
    availability/copy, `AiUsageLedger` manifest mapping, diagnostic sidecar
    behavior, privacy-dashboard provenance copy, release 16 KB native-library
    gates, and focused JVM/instrumentation tests.
  - Acceptance: when C2PA export is available and enabled, the final MP4
    contains a signed C2PA manifest store with one valid hard binding to the
    output media, current `c2pa.actions` entries derived from the AI ledger,
    current `cawg.training-mining` opt-out entries, NovaCut claim-generator
    metadata, and no leaked clip names or source URIs unless the user opts into
    title disclosure. When the library, signer, certificate enrollment, or
    remote signer is unavailable, the UI says Content Credentials are
    unavailable and may still write a clearly named local draft sidecar, but it
    does not imply the MP4 is verifiable. Any remote signer path requires an
    explicit per-export consent sheet before media or hashes leave the device.
  - Verify: add manifest-construction tests for C2PA 2.4 claim-generator fields,
    CAWG training/mining labels, redacted titles, and AI ledger action mapping.
    Add signed-export tests that run a C2PA reader against the generated MP4,
    assert a valid claim signature and BMFF hard binding, and prove byte
    tampering fails validation. Add StrongBox/TEE fallback tests, remote-signer
    denial tests, export-sheet availability tests, and release APK checks
    confirming the C2PA native libraries still pass zipalign and 16 KB page-size
    gates.
  - Complexity: L
  - Status: partial prerequisite shipped in v3.74.54. `C2paExportEngine` now
    emits current CAWG `cawg.training-mining` entries, exposes
    C2PA-builder-shaped manifest definition JSON with `claim_generator` and
    `claim_generator_info`, keeps redacted titles omitted from that definition,
    and maps AI ledger entries into current `c2pa.actions`. It also exposes
    explicit availability states for missing libraries, missing Android
    Keystore/StrongBox certificate enrollment, missing user PEM material, and
    remote-signer consent. `ExportDelegate` writes
    `.c2pa-draft-manifest.json` with unsigned/not-verifiable status instead of
    the ambiguous `.c2pa-manifest.json`, and export/privacy copy now reflects
    that local drafts are not Content Credentials. Remaining work: pin and
    verify an actual C2PA Android AAR or equivalent, add certificate enrollment
    and signer credential storage, implement embedded MP4 signing, validate the
    BMFF hard binding with a reader, prove tamper failure, and rerun the 16 KB
    native-library release gates. Verification passed: focused JVM
    `C2paExportEngineTest` and `PrivacyDashboardTest`, `:app:testDebugUnitTest`,
    `:app:assembleDebug`, `:app:assembleRelease`,
    `:app:assembleDebugAndroidTest`, `scripts/verify_release_artifacts.py`,
    `scripts/validate_play_listing_assets.py`,
    `scripts/sync_fastlane_changelogs.py --check`, and `git diff --check`.
  - Blocker: live dependency review on 2026-06-04 found the documented
    unauthenticated JitPack coordinates for `com.github.contentauth:c2pa-android`
    returning HTTP 500 for both `1.0.0` and `v1.0.0`, while the documented
    GitHub Packages coordinate requires credentials and returned HTTP 401 in
    this environment. The latest public GitHub release is `0.0.9` with a
    37,276,693 byte `c2pa-release.aar` asset, but NovaCut should not silently
    vendor that native AAR into the base app without a product/release decision
    on binary size, license notice packaging, authenticated package access vs
    vendoring, and 16 KB native-library verification. Continue this item after
    choosing a dependency distribution path.

#### Appendix — Cycle 13 Sources

- Official c2pa-android library:
  https://opensource.contentauthenticity.org/docs/c2pa-android/
- C2PA Technical Specification 2.4:
  https://spec.c2pa.org/specifications/specifications/2.4/specs/C2PA_Specification.html
- CAWG Training and Data Mining Assertion 1.1:
  https://cawg.io/training-and-data-mining/1.1/

### Researcher Queue (Cycle 14 - 2026-06-04)

Focus: make NovaCut's platform-publish surface distinguish an Android share
handoff from real API-backed direct publishing.

#### Platform Publishing & Share Handoff

- [ ] 🔬🤖 P2 — Split Direct Publish into honest share handoff and gated API upload
  - Why: the current panel is titled "Direct Publish" and offers YouTube,
    TikTok, Instagram, Threads, X, and LinkedIn chips, but the implementation
    only opens a platform-targeted `ACTION_SEND` intent. That can hand the MP4
    and suggested text to another app, but it cannot guarantee a post was
    created, set visibility, preserve metadata, apply platform AI-disclosure
    toggles, schedule, resume upload, or report processing failures. Users need
    a clear "Open in platform" handoff today and an explicitly gated API upload
    path only when the platform prerequisites are real.
  - Evidence: `DirectPublishEngine.publish(...)` validates a local file, obtains
    a FileProvider URI, builds an `Intent.ACTION_SEND` with title/text/stream,
    optionally pins the target package if installed, and always returns
    `Result(..., Method.SHARE_INTENT, "Opening ...")`. There is no OAuth token
    storage, upload session, API client, SDK dependency, progress/cancel state,
    status polling, or `Method.API_UPLOAD` code path. `V369Delegate` immediately
    calls `startActivity(intent)` and only catches activity-launch failure. The
    tests cover share-body normalization and target AI-disclosure flags, not
    actual platform publishing.
  - Current-source check: Android's sharing docs say `ACTION_SEND` sends data to
    another app and recommend the Android Sharesheet for consistent sharing:
    https://developer.android.com/training/sharing/send
    YouTube `videos.insert` is an authorized media-upload API, requires an
    upload scope, and notes that
    uploads from unverified API projects are restricted to private visibility:
    https://developers.google.com/youtube/v3/docs/videos/insert
    YouTube's resumable-upload guide recommends resumable uploads for large
    files, unstable networks, and mobile-originated uploads:
    https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol
    TikTok Direct Post requires creator-info query, post initialization,
    exporting the video to TikTok servers, explicit consent, and audit before
    unaudited clients can post beyond private mode:
    https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
  - Touches: `V369FeaturesPanel` copy/title/chips, `DirectPublishEngine.Result`
    semantics, share-intent chooser/targeting policy, `V369Delegate` toast and
    status handling, platform-specific capability model, YouTube/TikTok upload
    adapters if enabled, encrypted OAuth/token storage, privacy-dashboard
    disclosure of platform uploads, AI-disclosure handoff copy, progress/cancel
    UI, diagnostic export of publish attempts without secrets, and JVM/UI tests.
  - Acceptance: when only the share fallback is available, the UI says "Open in
    YouTube/TikTok/..." or "Share export", uses the Android Sharesheet or a
    clearly targeted resolver, and reports only that the target app was opened.
    It never claims NovaCut published the video, set visibility, scheduled it,
    or toggled platform AI-disclosure controls; instead it includes a concise
    reminder in the handoff text when the AI ledger has disclosure-bearing
    entries. When API upload is enabled for a platform, it requires explicit
    OAuth/user consent, stores refresh credentials encrypted, supports
    resumable upload with progress/cancel/retry, honors private/audit
    restrictions, maps metadata and disclosure controls only where the platform
    API exposes them, and records a redacted status history.
  - Verify: add JVM tests for share-vs-upload capability labels, no-credential
    fallback, AI-disclosure handoff text, title/body bounds, and no-secret
    diagnostics. Add fake YouTube/TikTok uploader tests for OAuth missing,
    unaudited/private-only, resumable interruption/resume, cancellation, and
    upload-status polling. Add Compose tests that prove the share-only panel no
    longer reads as a successful publish flow and API-upload controls appear
    only when the capability model says they are available.
  - Complexity: L

#### Appendix — Cycle 14 Sources

- Android Sharesheet / sending simple data:
  https://developer.android.com/training/sharing/send
- YouTube Data API `videos.insert`:
  https://developers.google.com/youtube/v3/docs/videos/insert
- YouTube resumable upload protocol:
  https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol
- TikTok Content Posting API — Direct Post:
  https://developers.tiktok.com/doc/content-posting-api-reference-direct-post

### Researcher Queue (Cycle 15 - 2026-06-04)

Focus: make device capture honest about the current external camera-app handoff
and prepare a separate, permission-correct CameraX recorder lane.

#### Media Capture & Camera Permissions

- [ ] 🔬🤖 P2 — Split external camera capture from future in-app CameraX recording
  - Why: Add Media currently presents "Capture on Device" as "Record a clip
    without leaving NovaCut" and says camera permission is requested when
    recording starts. In reality the app uses `ActivityResultContracts.CaptureVideo`
    to launch a system camera activity with a FileProvider destination and does
    not declare or request NovaCut's own `CAMERA` permission. That is a valid
    privacy-preserving fallback, but it should be labeled as an external camera
    handoff. The planned in-app CameraX/teleprompter recorder needs a separate
    capability gate, permission path, and device/no-camera fallback.
  - Evidence: `MediaPickerSheet` stores `cameraVideoFile` under
    `pendingCameraCaptureDir(context)`, launches `CaptureVideo()`, then imports
    the result through `finalizePendingCameraCapture(...)`. `AndroidManifest.xml`
    declares only `uses-feature android.hardware.camera required=false`; grep
    finds no `android.permission.CAMERA`, `Manifest.permission.CAMERA`, CameraX
    dependency, or camera-permission launcher. `CameraCaptureEngine` still says
    it is a CameraX/teleprompter stub, probes for `androidx.camera.video.VideoCapture`,
    and returns `false` from `startRecording(...)`.
  - Current-source check: Android's camera docs describe invoking an existing
    camera app by intent as the quick way to take pictures or videos without
    direct camera-object work:
    https://developer.android.com/media/camera/camera-deprecated/camera-api
    Android 11 behavior changes say only pre-installed system camera apps can
    respond to `android.media.action.VIDEO_CAPTURE` unless the app targets a
    specific package/component:
    https://developer.android.com/about/versions/11/behavior-changes-11
    Manifest `uses-feature` docs recommend `android:required="false"` when a
    camera feature is optional so Google Play does not filter out devices:
    https://developer.android.com/guide/topics/manifest/uses-feature-element
    CameraX VideoCapture is the separate in-app capture architecture:
    https://developer.android.com/media/camera/camerax/video-capture
  - Touches: Add Media capture strings, `MediaPickerSheet` capture action/error
    handling, camera-intent availability checks, stale capture cleanup,
    `CameraCaptureEngine` capability model, optional CameraX dependency plan,
    manifest permission/feature policy, runtime `CAMERA` and audio permission
    flows for true in-app recording, teleprompter UI copy, diagnostics for
    failed capture handoff without source paths, and focused JVM/Compose tests.
  - Acceptance: today's fallback is labeled as "Open camera app" or equivalent,
    explains that NovaCut receives only the returned clip, does not claim an
    in-app recorder or camera permission prompt, and shows an actionable message
    when no system camera handler is available or capture returns an empty file.
    The `uses-feature required=false` declaration remains so editor-only users
    are not filtered from devices without a camera. A future in-app recorder
    appears only when CameraX is on the classpath and a camera is available,
    requests `CAMERA` only at record time, coordinates microphone permission
    separately for audio/voiceover, supports cancel/cleanup, and falls back to
    the external camera handoff when any prerequisite is missing.
  - Verify: add JVM tests for pending capture path validation, empty-file
    cleanup, no-source-path diagnostics, and capability mapping between
    external intent vs CameraX. Add Compose tests for copy/state differences
    across external-only, no-camera-handler, denied CameraX permission, and
    CameraX-available states. Add an emulator/device smoke test for Android 11+
    camera-intent behavior and a manifest test that `CAMERA` is absent until the
    in-app CameraX recorder is actually enabled.
  - Complexity: M

#### Appendix — Cycle 15 Sources

- Android camera intent / camera app capture docs:
  https://developer.android.com/media/camera/camera-deprecated/camera-api
- Android 11 camera intent behavior changes:
  https://developer.android.com/about/versions/11/behavior-changes-11
- Android manifest `uses-feature`:
  https://developer.android.com/guide/topics/manifest/uses-feature-element
- CameraX VideoCapture:
  https://developer.android.com/media/camera/camerax/video-capture

### Researcher Queue (Cycle 16 - 2026-06-04)

Focus: make every FileProvider share and capture producer covered by a narrow
XML grant path.

#### File Sharing & URI Grants

- [ ] 🔬🤖 P1 — Add FileProvider grant-path contract for camera capture and shared artifacts
  - Why: Record Video currently creates its destination under
    `cacheDir/camera-captures` and asks FileProvider for a content URI before
    launching the camera intent. `file_paths.xml` does not expose that cache
    directory, so URI generation can throw before the external camera app opens.
    Because diagnostics, exports, archives, direct publish, editor media, and
    generated audio also rely on FileProvider, NovaCut needs a repeatable
    contract that every producer has a deliberately narrow grant path and no
    private root is accidentally exposed.
  - Evidence: `MediaPickerSheet.startCameraCapture()` creates
    `File(pendingCameraCaptureDir(context), "novacut_<timestamp>.mp4")`, where
    `pendingCameraCaptureDir(context)` is `File(context.cacheDir,
    "camera-captures")`, then immediately calls
    `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider",
    videoFile)`. `res/xml/file_paths.xml` contains
    `<cache-path name="frame_capture" path="frames/" />` but no cache path for
    `camera-captures/`. Grep finds additional `getUriForFile(...)` producers in
    `SettingsScreen`, `ProjectListViewModel`, `ExportService`,
    `DirectPublishEngine`, `MediaPicker`, `EditorViewModel`, and
    `ExportDelegate`; current focused tests do not enumerate each producer's
    expected XML path.
  - Current-source check: AndroidX FileProvider docs say a provider can generate
    content URIs only for files declared in the app's XML paths, and
    `getUriForFile(...)` throws when the file is outside those configured roots:
    https://developer.android.com/reference/androidx/core/content/FileProvider
    Android's secure file-sharing setup documents declaring allowed roots in
    `res/xml/file_paths.xml` and granting other apps temporary read access to
    content URIs instead of exposing raw file paths:
    https://developer.android.com/training/secure-file-sharing/setup-sharing
  - Touches: `res/xml/file_paths.xml`, `MediaPickerSheet.startCameraCapture()`
    error handling, pending camera capture cleanup, FileProvider producer
    inventory, provider path contract tests, diagnostic/export/archive/direct
    publish/share flows, and no-root-exposure regression tests.
  - Acceptance: Record Video can create a content URI for
    `cacheDir/camera-captures` without broadening access beyond that directory.
    Every FileProvider producer maps to a named, narrow XML root; unrelated
    internal files remain rejected; URI-generation failures surface actionable
    UI copy instead of crashing; share intents grant only scoped read access; and
    diagnostics redact local source paths.
  - Verify: add a JVM/XML contract test that enumerates expected FileProvider
    path names and paths, plus a source-table or focused test that every
    `getUriForFile(...)` producer is represented. Add a Robolectric or
    instrumentation test for the camera capture URI, representative tests for
    diagnostics/export/archive/direct publish files, and a negative test proving
    an unrelated private file cannot be shared.
  - Complexity: M

#### Appendix — Cycle 16 Sources

- AndroidX FileProvider:
  https://developer.android.com/reference/androidx/core/content/FileProvider
- Android secure file sharing setup:
  https://developer.android.com/training/secure-file-sharing/setup-sharing

### Researcher Queue (Cycle 17 - 2026-06-04)

Focus: make the stock-asset library a provider-aware, license-safe catalog
instead of a generic search/download stub.

#### Stock Assets, Attribution & Provider Terms

- [ ] 🔬🤖 P2 — Gate stock-asset providers on terms, attribution, and rate-limit contracts
  - Why: `StockAssetEngine` already names Pexels, Pixabay, Freesound, and Free
    Music Archive and exposes one generic `StockAsset` shape, but those
    providers have different API-key, branding, attribution, cache, rate-limit,
    hotlinking, commercial-use, and Creative Commons requirements. A working
    stock-asset panel should never imply that all results are equally reusable
    in exported videos, platform uploads, or commercial work. It needs a
    provider capability model before any real HTTP/download path ships.
  - Evidence: `StockAssetEngine.isProviderConfigured(...)` always returns
    false, `search(...)` returns `SearchResult(emptyList(), 0, ...)`, and
    `download(...)` returns false. The engine comment says API keys are
    user-supplied via Settings, but `SettingsRepository` currently stores only
    the optional `acoustIdApiKey`, and grep finds no stock-provider keys, HTTP
    client, cache policy, provider UI, or `StockAssetEngine` tests. The single
    `attributionLine(...)` helper formats title/author/provider/license but
    does not persist source URL, license URL, provider terms, commercial-use
    status, derivative/no-derivatives constraints, or export-time credit
    placement.
  - Current-source check: Pexels API docs require an authorization key, visible
    Pexels links, photographer credit when possible, rate-limit tracking, and
    no rate-limit abuse:
    https://www.pexels.com/api/documentation/
    Pixabay API docs ask API users to show where images/videos come from,
    rate-limit by API key, cache requests for 24 hours, forbid permanent image
    hotlinking, and return 429 on limit errors:
    https://pixabay.com/api/docs/
    Freesound API terms say free API use is non-commercial, require crediting
    Freesound and users according to sound licenses, and forbid bandwidth abuse
    or multiple keys to avoid limits:
    https://freesound.org/docs/api/terms_of_use.html
    Free Music Archive's FAQ and license guide emphasize per-track Creative
    Commons terms, including attribution, NonCommercial, NoDerivatives, and
    ShareAlike constraints:
    https://freemusicarchive.org/faq/
    https://freemusicarchive.org/License_Guide
  - Touches: stock-provider settings and encrypted/key-handling policy,
    provider capability model, provider-specific HTTP clients, retry and 429
    handling, 24-hour Pixabay cache, Pexels/Pixabay result branding, Freesound
    commercial-use gate, FMA/manual-source or documented-provider replacement,
    asset metadata persistence in project state, export credits/caption
    insertion, privacy-dashboard disclosure of external provider lookups,
    diagnostic redaction of API keys and source URLs, and JVM/Compose tests.
  - Acceptance: stock search remains disabled until the selected provider has
    the required key or documented no-key source. Search results display the
    provider/source branding required by that provider, downloads avoid
    disallowed hotlinking, rate-limit and cache state are handled without
    hammering APIs, and every imported asset persists source page, author,
    author URL, license name, license URL, provider, fetched timestamp, and
    reuse constraints. Export/direct-publish flows include or prompt for
    required attribution, block commercial-intent exports for NC assets unless
    the user confirms a noncommercial workflow, block ND assets when NovaCut has
    materially remixed them, and show clear status when a provider is unavailable
    or terms are unknown.
  - Verify: add pure JVM tests for query validation, provider configuration,
    attribution formatting, per-provider capability flags, rate-limit/cache
    decisions, and license gates for BY/NC/ND/SA/CC0. Add fake-client tests for
    Pexels/Pixabay/Freesound response parsing, 429/error handling, and no-secret
    diagnostics. Add Compose tests that unavailable providers show setup copy,
    search results show required source branding, and export/share paths include
    or block attribution according to persisted asset metadata.
  - Complexity: L

#### Appendix — Cycle 17 Sources

- Pexels API documentation:
  https://www.pexels.com/api/documentation/
- Pixabay API documentation:
  https://pixabay.com/api/docs/
- Freesound API terms:
  https://freesound.org/docs/api/terms_of_use.html
- Free Music Archive FAQ:
  https://freemusicarchive.org/faq/
- Free Music Archive License Guide:
  https://freemusicarchive.org/License_Guide

### Researcher Queue (Cycle 18 - 2026-06-04)

Focus: make original-media metadata retention and archive sharing explicit,
minimal, and testable.

#### Media Privacy & Project Archives

- [ ] 🔬🤖 P2 — Add original-media metadata disclosure and scrubbed archive/share paths
  - Why: NovaCut's local-first model is good for privacy, but "local" is not the
    same as "metadata-free." Imported photos/videos and external camera captures
    can carry provider-supplied metadata such as camera details, timestamps,
    location tags, filenames, and content-provider URI details. NovaCut keeps
    source bytes for editing and project restore, then packages those bytes into
    `.novacut` archives. Users need clear archive/share copy, redacted archive
    manifests, and a best-effort scrubbed path when they are preparing media for
    another person or platform.
  - Evidence: `importUriToManagedMedia(...)` copies the selected URI's input
    stream into `filesDir/media/imports` via a `.partial` file and rename.
    `finalizePendingCameraCapture(...)` moves or copies the camera-capture MP4
    into the same managed-media directory. `ProjectArchive.exportArchive(...)`
    calls `collectArchivedMedia(state)`, writes each source stream into the ZIP
    under `media/<index>_<sanitized-name>`, and writes `media_manifest.json`
    entries with `originalUri` plus `entryName`. `PrivacyDashboard.Category.MEDIA_METADATA`
    currently says only "durations, codecs, dimensions," and the codebase has no
    `ExifInterface` import, no `ACCESS_MEDIA_LOCATION` permission, no media
    location strings, and no tests that prove archive manifests avoid original
    URI leaks or that scrubbed media can be produced.
  - Current-source check: Android Photo Picker docs describe the picker as a
    privacy-preserving way for users to grant an app access only to selected
    images/videos, not the whole media library:
    https://developer.android.com/training/data-storage/shared/photo-picker
    Android media-storage docs say photo location information is hidden by
    default under scoped storage, and unredacted EXIF location requires
    `ACCESS_MEDIA_LOCATION` plus explicit user consent:
    https://developer.android.com/training/data-storage/shared/media#media-location-permission
    `MediaStore.setRequireOriginal(...)` is the exact-byte path and may require
    `ACCESS_MEDIA_LOCATION` to avoid sensitive metadata redaction:
    https://developer.android.com/reference/android/provider/MediaStore#setRequireOriginal(android.net.Uri)
    AndroidX ExifInterface exposes GPS latitude/longitude tags for supported
    image formats:
    https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface
  - Touches: `PrivacyDashboard` media row copy, project archive/export copy,
    `media_manifest.json` schema, archive import compatibility, managed-media
    metadata inspection helper, optional AndroidX ExifInterface dependency,
    image scrubber for supported still formats, video/camera metadata warning
    policy, direct-publish/share/export handoff copy, diagnostics redaction, and
    JVM/fixture tests.
  - Acceptance: NovaCut does not request `ACCESS_MEDIA_LOCATION` by default and
    never calls `setRequireOriginal(...)` unless a future user-visible feature
    explicitly needs unredacted location metadata. Project archives no longer
    expose raw `file://`, `content://`, or provider-specific source URIs in a
    shareable manifest; import still rewrites archived media through stable
    opaque IDs. The privacy dashboard and archive sheet state that archives
    include original media bytes and may retain source metadata. A user can
    choose a scrubbed archive/share/export path for supported still images that
    removes GPS and high-risk EXIF fields while preserving render correctness;
    unsupported video/container metadata is disclosed with a warning or routed
    through an explicit re-encode-only scrub path.
  - Verify: add fixture-based JVM tests with a JPEG containing GPS EXIF to prove
    the scrubbed path removes location tags and the normal managed-media path
    preserves edit bytes when scrub is off. Add archive tests proving manifests
    use opaque IDs rather than original URIs, old archives still import, and
    no diagnostic ZIP leaks source URI strings. Add privacy-dashboard tests for
    the expanded media-metadata disclosure and UI tests for archive/export copy
    across default, scrubbed, and unsupported-video states.
  - Complexity: L

#### Appendix — Cycle 18 Sources

- Android Photo Picker:
  https://developer.android.com/training/data-storage/shared/photo-picker
- Android media location permission:
  https://developer.android.com/training/data-storage/shared/media#media-location-permission
- Android `MediaStore.setRequireOriginal(...)`:
  https://developer.android.com/reference/android/provider/MediaStore#setRequireOriginal(android.net.Uri)
- AndroidX ExifInterface:
  https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface

### Researcher Queue (Cycle 19 - 2026-06-04)

Focus: separate SDH caption support from true audio-description delivery and
make both exportable before the UI presents them as accessibility outputs.

#### Media Accessibility Outputs

- [ ] 🔬🤖 P2 — Split SDH captions from verified audio-description tracks
  - Why: NovaCut surfaces "SDH + Audio Description" as a feature card, but the
    current code only has a heuristic SDH/event-tag helper and an audio-line
    collision validator. SDH captions and audio description solve different
    accessibility needs and have different export requirements. Users should
    not see or deliver a video as "audio described" unless NovaCut actually
    creates a synchronized narration track, mixes it safely with the program
    audio, and verifies it is included in the selected output.
  - Evidence: `V369FeaturesPanel` shows a "SDH + Audio Description" card with
    subtitle "Bracketed non-speech tags + AD track stub (YAMNet planned)" and
    no action button. `AudioDescriptionEngine.mergeSdh(...)` appends bracketed
    captions for supplied events only when no speech caption spans the event,
    `classify(...)` infers generic `music` events from long transcript gaps,
    and `validate(...)` returns manually-authored AD lines that do not collide
    with word timestamps. Grep finds no `AudioDescriptionEngine` tests, no
    YAMNet dependency/model gate, no visual-scene description generator, no TTS
    render path for AD lines, no second audio-track insertion, no sidechain
    ducking against narration, and no export/caption flag that marks outputs as
    SDH or audio described.
  - Current-source check: WCAG 2.2 time-based media criteria distinguish
    prerecorded captions from prerecorded audio description:
    https://www.w3.org/TR/WCAG22/#time-based-media
    W3C's captions guidance says captions are provided for prerecorded audio
    content in synchronized media:
    https://www.w3.org/WAI/WCAG22/Understanding/captions-prerecorded.html
    W3C's audio-description guidance says AA conformance requires synchronized
    spoken descriptions of important visual information in prerecorded video:
    https://www.w3.org/WAI/WCAG22/Understanding/audio-description-prerecorded.html
    W3C's visual-description guidance explains that description covers actions,
    characters, scene changes, and on-screen text that matter to understanding:
    https://www.w3.org/WAI/media/av/description/
    TensorFlow's YAMNet docs describe sound-event classification across 521
    AudioSet classes, which can assist SDH event tagging but does not describe
    visual content:
    https://www.tensorflow.org/hub/tutorials/yamnet?hl=en
  - Touches: `V369FeaturesPanel` copy/actions, `AudioDescriptionEngine`
    capability model, transcript/non-speech-event review UI, optional YAMNet
    model requirement gate, caption export config for SDH labels, AD script
    editor, TTS rendering through `TtsEngine`, audio mixer sidechain ducking,
    multi-track export packaging, direct-publish/share copy, AI usage ledger if
    generated labels/narration are AI-assisted, and JVM/Compose/export tests.
  - Acceptance: the UI labels the current state as "SDH event tags" or
    "accessibility track draft" until exportable outputs exist. SDH generation
    requires a transcript, exposes inferred non-speech tags for user review,
    writes those tags into selected caption outputs, and distinguishes reviewed
    SDH captions from unreviewed guesses. Audio description requires a script or
    reviewed generated visual descriptions, validates that narration fits
    dialogue gaps or offers extended-audio/reflow options, renders narration via
    a selected voice, ducks program audio where appropriate, includes the AD
    track in exports that support it, and refuses to claim AD availability when
    any prerequisite is missing.
  - Verify: add JVM tests for SDH event merging, transcript-gap classification,
    reviewed/unreviewed event state, AD collision validation, narration-fit
    policy, and export capability labels. Add fake TTS/audio-mixer tests for
    AD-line rendering, ducking ranges, and missing voice/model failure states.
    Add Compose tests that the feature card no longer implies completed AD, SDH
    review state is visible, and export UI marks SDH/AD only when the output
    contract is satisfied.
  - Complexity: L

#### Appendix — Cycle 19 Sources

- WCAG 2.2 time-based media:
  https://www.w3.org/TR/WCAG22/#time-based-media
- W3C captions guidance:
  https://www.w3.org/WAI/WCAG22/Understanding/captions-prerecorded.html
- W3C audio-description guidance:
  https://www.w3.org/WAI/WCAG22/Understanding/audio-description-prerecorded.html
- W3C description of visual information:
  https://www.w3.org/WAI/media/av/description/
- TensorFlow YAMNet:
  https://www.tensorflow.org/hub/tutorials/yamnet?hl=en

### Researcher Queue (Cycle 20 - 2026-06-04)

Focus: make product-health observability useful without weakening NovaCut's
local-first privacy posture or duplicating the completed Baseline Profile gate.

#### Privacy-Preserving Product Health

- [ ] P2 - 🔬🤖 Add a local-first product-health ledger before telemetry SDKs
  - Why: NovaCut already advertises an opt-in telemetry category and names
    future Sentry/Glean collectors, but the app currently has only local crash
    records and user-triggered diagnostic ZIPs. A local product-health ledger
    gives maintainers aggregate evidence about fragile editor paths while
    keeping media projects, filenames, captions, transcripts, and edit details
    off the network until a user deliberately opts into a documented upload
    path. This promotes the archived R5.5b aggregate-usage-metrics idea without
    adding another generic analytics item.
  - Evidence: `PrivacyDashboard.kt:33` defines `OPT_IN_TELEMETRY("Opt-in usage
    telemetry (Sentry / Glean)")`; `PrivacyDashboard.kt:159`-`PrivacyDashboard.kt:164`
    marks that row as cloud/on-demand, deletable, opt-out-capable, collected by
    future SentryAndroid/Mozilla Glean, and disabled by default.
    `DiagnosticExportEngine.kt:74`-`DiagnosticExportEngine.kt:88` already
    documents a counts-only, user-triggered diagnostic payload that excludes
    clip names, source URIs, captions, transcripts, and file paths.
    `NovaCutApp.kt:32`-`NovaCutApp.kt:35` installs only the local
    `CrashRecordStore` handler and notification channels. Grep across
    `app/src/main`, Gradle scripts, and `gradle/libs.versions.toml` finds no
    Sentry, Glean, Firebase Analytics, Crashlytics, OpenTelemetry, JankStats,
    `PerformanceMetricsState`, or telemetry SDK dependency today. The archived
    roadmap's R5.5b proposed aggregate-only usage metrics via Mozilla Glean or
    Divvi Up, while R5.5d completed local diagnostic ZIP export without a
    telemetry pipe.
  - Current-source check: Android vitals gives Play developers opt-in,
    system-collected quality signals for crashes, ANRs, rendering, battery,
    and permission issues, but only for Play installs and users who allow data
    sharing. JankStats and `PerformanceMetricsState` are the AndroidX path for
    tagging real UI jank with Compose state. Google Play's Data safety and User
    Data policies require accurate disclosure, SDK review, privacy policy
    coverage, prominent in-app disclosure/affirmative consent where needed, and
    retention/deletion mechanisms. F-Droid treats tracking or reporting
    activity as an anti-feature when it is on by default or lacks informed
    consent, and proprietary tracking SDKs can affect inclusion. Mozilla Glean
    sends built-in pings only when collection is enabled and includes a
    deletion-request ping when telemetry is disabled. OpenTelemetry Android and
    Sentry can collect rich lifecycle, ANR, crash, frame, session, device, and
    connectivity context, so NovaCut needs an explicit redaction/schema layer
    before considering either. DAP/Divvi Up-style aggregation is promising for
    anonymous aggregate counters, but it adds infrastructure and privacy-set
    requirements that are premature before local schema review.
  - Touches: `PrivacyDashboard` telemetry copy and controls, Settings privacy
    toggle copy, `SettingsRepository` consent state, a new local product-health
    ledger under app-private storage, optional `DiagnosticExportEngine` summary
    inclusion, redaction helpers, event-schema docs, SDK facade stubs only after
    consent is proven, Play Data safety/F-Droid metadata docs, and focused JVM
    plus instrumentation tests.
  - Acceptance: telemetry remains off on fresh install, after upgrade, and
    after settings restore. The first build implements only a local, reviewable
    ledger with coarse counters such as export attempts by codec family,
    cancelled exports, anonymized failure class, cold-start bucket, slow-frame
    bucket, model-download failure class, and diagnostic-ZIP creation count.
    The schema forbids project names, media URIs, file paths, account IDs,
    persistent identifiers, captions, transcripts, prompts, thumbnails,
    location, device serials, and raw stack traces. Users can view, export,
    delete, and disable the ledger. Any future network upload is separately
    gated by affirmative consent, lists provider, endpoint, retention, deletion
    behavior, Data safety impact, and F-Droid anti-feature impact, and does not
    initialize a telemetry SDK until consent is true. Sentry crash reporting,
    Glean aggregate metrics, Firebase Analytics, OpenTelemetry, and DAP/Divvi
    Up are evaluated as mutually exclusive adapters rather than silently bundled
    together.
  - Verify: add tests proving fresh installs do not initialize telemetry SDKs
    or open network exporters, cloud/telemetry dashboard rows are off by
    default, the local ledger redacts prohibited fields, export/delete/disable
    controls work, diagnostic ZIP inclusion is opt-in and counts-only, consent
    changes persist across process restart, and future adapter code refuses to
    upload when consent is false. Add an instrumentation or fake-network guard
    that exercises app startup and Settings without telemetry traffic.
  - Complexity: M
  - Status: implemented in v3.74.50. Added `ProcessExitRecorder`, API 30+
    `ActivityManager.getHistoricalProcessExitReasons(...)` ingestion at
    startup, local bounded/de-duped history, redacted/truncated description and
    trace handling, `process-exit-history.json` diagnostic ZIP inclusion, and
    privacy-dashboard copy. Focused JVM tests cover fake adapter records,
    unsupported devices, crash/native/ANR/low-memory/signal mapping, duplicate
    records, redaction, bounded trace excerpts, and diagnostic bundle entry
    shape.
  - Status: implemented in v3.74.49. Added `IncomingDocumentIntentParser`,
    `IncomingDocumentImportRouter`, bounded document manifest filters, Projects
    preview/report UI, parser/manifest JVM tests, and docs in
    `docs/incoming-document-imports.md`. The first pass validates templates,
    effect packs, LUTs, OpenFX descriptors, archives, and timeline import status
    before mutation; only template save is enabled directly from the preview.

#### Appendix - Cycle 20 Sources

- Android vitals overview:
  https://developer.android.com/topic/performance/vitals
- Android vitals crashes:
  https://developer.android.com/topic/performance/vitals/crash
- Android vitals ANRs:
  https://developer.android.com/topic/performance/vitals/anr
- Android vitals slow/frozen frames:
  https://developer.android.com/topic/performance/vitals/render
- Android JankStats:
  https://developer.android.com/topic/performance/jankstats
- AndroidX `PerformanceMetricsState`:
  https://developer.android.com/reference/androidx/metrics/performance/PerformanceMetricsState
- Android system tracing:
  https://developer.android.com/topic/performance/tracing
- Android app startup:
  https://developer.android.com/topic/performance/appstartup
- Android `StrictMode`:
  https://developer.android.com/reference/android/os/StrictMode
- Android `ApplicationExitInfo`:
  https://developer.android.com/reference/android/app/ApplicationExitInfo
- Google Play Data safety form:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play SDK Index:
  https://play.google.com/sdks
- Mozilla Glean overview:
  https://mozilla.github.io/glean/book/index.html
- Mozilla Glean Android binding:
  https://mozilla.github.io/glean/book/language-bindings/android/index.html
- Mozilla Glean metrics:
  https://mozilla.github.io/glean/book/user/collected-metrics/metrics.html
- Mozilla Glean pings:
  https://mozilla.github.io/glean/book/user/pings/index.html
- Mozilla Glean built-in pings:
  https://mozilla.github.io/glean/book/user/pings/sent-by-glean.html
- Mozilla Glean source:
  https://github.com/mozilla/glean
- OpenTelemetry Android:
  https://opentelemetry.io/docs/platforms/client-apps/android/
- OpenTelemetry Android source:
  https://github.com/open-telemetry/opentelemetry-android
- Sentry Android:
  https://docs.sentry.io/platforms/android/
- Sentry Android options:
  https://docs.sentry.io/platforms/android/configuration/options/
- Firebase Analytics Android setup:
  https://firebase.google.com/docs/analytics/get-started?platform=android
- Firebase Analytics events:
  https://firebase.google.com/docs/analytics/events?platform=android
- F-Droid anti-features:
  https://f-droid.org/en/docs/Anti-Features/
- F-Droid inclusion policy:
  https://f-droid.org/en/docs/Inclusion_Policy/
- IETF PPM working group:
  https://datatracker.ietf.org/group/ppm/
- IETF DAP draft:
  https://datatracker.ietf.org/doc/draft-ietf-ppm-dap/
- Divvi Up Tella Android case study:
  https://divviup.org/blog/horizontal-tella/
- W3C Privacy Principles:
  https://www.w3.org/TR/privacy-principles/

### Researcher Queue (Cycle 21 - 2026-06-04)

Focus: turn the caption-translation UI from source-text echo into a real,
reviewable offline translation path with model, license, and timing gates.

#### Caption Translation Activation

- [ ] P3 - 🔬🤖 Activate caption translation with offline model gates and reviewable output
  - Why: NovaCut already has the caption-translation panel, target-language
    selection, quality chip, per-row edit/regenerate state, and ViewModel
    orchestration. The engine still reports no model, refuses downloads, and
    returns the source caption text as the translated text. A dedicated
    activation contract prevents the UI from implying completed translation and
    gives the implementer a concrete choice between ML Kit, Bergamot/Mozilla
    Firefox Translation models, and MADLAD-style open weights.
  - Evidence: `CaptionTranslationEngine.kt:15` calls the engine a stub for
    on-device caption translation; `CaptionTranslationEngine.kt:48`-`CaptionTranslationEngine.kt:52`
    lists NLLB, MADLAD-400, and Bergamot variants; `CaptionTranslationEngine.kt:128`
    always returns false for readiness; `CaptionTranslationEngine.kt:137`-`CaptionTranslationEngine.kt:143`
    logs `downloadModel` as a stub and returns false; `CaptionTranslationEngine.kt:149`-`CaptionTranslationEngine.kt:165`
    maps every target segment back to `seg.text`. `EditorViewModel.kt:4474`-`EditorViewModel.kt:4528`
    already launches `captionTranslationEngine.translate(...)` when a target
    language is picked, and `CaptionEditorPanel.kt:219`-`CaptionEditorPanel.kt:229`
    renders `CaptionTranslationPanel`. `docs/models.md:67`-`docs/models.md:68`
    names MADLAD-400 and Bergamot planning rows, while `docs/models.md:118`
    keeps `caption_translate` at `DEPENDENCY_MISSING` until exact bytes,
    SHA-256, size, delivery mode, and F-Droid posture are pinned. The live
    roadmap summary row already lists "Caption translation engine activation"
    as P3; this queue item expands that row rather than adding a parallel lane.
  - Current-source check: ML Kit translation can translate between more than
    50 languages, uses on-demand dynamic model downloads, recommends Wi-Fi
    downloads and explicit model deletion, and now documents Data safety
    collection such as device/app information, performance metrics, event
    types, error codes, configured source/destination languages, Firebase
    Remote Config, and Firebase installations for Translate. That makes ML Kit
    useful for the Play build but not a drop-in F-Droid/local-first answer.
    MADLAD-400 3B is Apache-2.0 and lists 419 languages on its Hugging Face
    card, with broader MADLAD research covering 419-language audited data and
    translation models trained across more than 450 languages; however, the
    mobile Q4 target is still very large and needs runtime proof. NLLB distilled
    remains a broad fallback but the 600M model card is CC-BY-NC and describes
    a research-oriented single-sentence model, so it should not become the
    commercial/default implementation. Bergamot/Firefox Translation models run
    locally and privately with CPU-optimized per-pair models, but language
    coverage is narrower and the Android path needs a C++/Marian integration
    decision. WebVTT, TTML, W3C caption guidance, BCP-47/CLDR locale handling,
    and bidi controls make translated-caption output a timing, wrapping,
    speaker-label, and script-direction problem, not just a string replacement.
  - Touches: `CaptionTranslationEngine`, `ModelDownloadManager`,
    `AiToolRequirements` `caption_translate`, `docs/models.md`, Settings/model
    download copy, `CaptionTranslationPanel`, `EditorViewModel`
    `runCaptionTranslation(...)`, subtitle exporters, `BidiTextPolicy`,
    `AiUsageLedger`, privacy dashboard/model disclosure copy, F-Droid metadata,
    and JVM/Compose/export tests.
  - Acceptance: the UI clearly labels translation as unavailable until a
    supported model or language-pair pack is installed. Model selection records
    exact source URL, license, size, SHA-256, delivery mode, F-Droid posture,
    and fallback language-pair matrix before the tool can report anything above
    `DEPENDENCY_MISSING`. Play builds may offer an ML Kit adapter only if the
    Data safety and SDK-disclosure copy names the collected language/model
    diagnostics; F-Droid builds must either use open local models or hide the
    adapter. The first local adapter should support a small reviewed language
    set, preserve caption cue start/end times, re-interpolate word timing only
    when karaoke export needs it, keep speaker labels and SDH tags separate
    from translated dialogue, validate BCP-47 target codes, handle RTL/bidi
    text without corrupting WebVTT/TTML output, route user edits into the saved
    caption state, and record assistive translation use in the project ledger.
  - Verify: add engine tests proving no source-text echo is presented as a
    completed translation, installed and missing models produce distinct
    states, source/target BCP-47 tags validate, model SHA/license metadata is
    required before readiness, translation failures preserve the original
    captions, and user-edited target rows are not overwritten by regeneration.
    Add Compose tests for unavailable/download/translated/edited/pending states.
    Add exporter tests for WebVTT/TTML/SRT round-trips with long translated
    lines, speaker labels, SDH tags, RTL text, and karaoke word timings.
  - Complexity: L

#### Appendix - Cycle 21 Sources

- ML Kit Translation for Android:
  https://developers.google.com/ml-kit/language/translation/android
- ML Kit translation supported languages:
  https://developers.google.com/ml-kit/language/translation/translation-language-support
- ML Kit language identification for Android:
  https://developers.google.com/ml-kit/language/identification/android
- ML Kit Android data disclosure:
  https://developers.google.com/ml-kit/android-data-disclosure
- ML Kit model installation paths:
  https://developers.google.com/ml-kit/tips/model-installation-paths/android
- Google MADLAD-400 3B model card:
  https://huggingface.co/google/madlad400-3b-mt
- Hugging Face MADLAD-400 docs:
  https://huggingface.co/docs/transformers/model_doc/madlad-400
- MADLAD-400 paper:
  https://arxiv.org/abs/2309.04662
- Google Research MADLAD-400 code:
  https://github.com/google-research/google-research/tree/master/madlad_400
- Meta NLLB-200 announcement:
  https://ai.meta.com/blog/nllb-200-high-quality-machine-translation/en-gb/
- Meta NLLB-200 distilled 600M model card:
  https://huggingface.co/facebook/nllb-200-distilled-600M
- Meta NLLB fairseq branch:
  https://github.com/facebookresearch/fairseq/tree/nllb
- NLLB Nature paper:
  https://www.nature.com/articles/s41586-024-07335-x
- Bergamot project:
  https://browser.mt/
- Mozilla Translations:
  https://github.com/mozilla/translations
- Bergamot translator library:
  https://github.com/browsermt/bergamot-translator
- Firefox Translation models:
  https://github.com/mozilla/firefox-translations-models
- Marian NMT:
  https://marian-nmt.github.io/
- RTranslator 3 grant:
  https://nlnet.nl/project/RTranslator/
- RTranslator source:
  https://github.com/niedev/RTranslator
- ONNX Runtime Android mobile deployment:
  https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html
- ONNX Runtime quantization:
  https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html
- Android Play Asset Delivery:
  https://developer.android.com/guide/playcore/asset-delivery
- Android App Bundles:
  https://developer.android.com/guide/app-bundle
- Android startup analysis:
  https://developer.android.com/topic/performance/appstartup/analysis-optimization
- W3C WebVTT:
  https://www.w3.org/TR/webvtt1/
- W3C TTML2:
  https://www.w3.org/TR/ttml2/
- W3C captions guidance:
  https://www.w3.org/WAI/media/av/captions/
- W3C bidi controls:
  https://www.w3.org/International/questions/qa-bidi-unicode-controls
- Unicode CLDR LDML:
  https://unicode-org.github.io/cldr/ldml/tr35.html

### Researcher Queue (Cycle 22 - 2026-06-04)

Focus: revive the archived cross-device project-sync idea as a conservative,
conflict-safe handoff that does not silently overwrite editor work or duplicate
the already-shipped Android backup rules.

#### Project Portability And Sync

- [ ] P3 - 🔬🤖 Add conflict-safe project sync planning for folders and WebDAV
  - Why: NovaCut already has Android backup coverage for app-private metadata
    and project archive import/export, but those are not the same as explicit
    user-controlled sync between devices. The live `ProjectSyncEngine` names
    local-folder, self-hosted, and LAN-peer backends, yet every real sync method
    is still a stub. The first implementable slice should sync project archives
    or content-addressed archive parts to a user-selected folder or WebDAV
    endpoint, surface conflicts as reviewable copies, and defer direct LAN peer
    sync to the existing local-network permission/security lane.
  - Evidence: `ProjectSyncEngine.kt:15` calls the engine a cross-device sync
    stub; `ProjectSyncEngine.kt:17`-`ProjectSyncEngine.kt:29` says it should
    extend `ProjectArchive`, support local folder / self-hosted / LAN peer
    targets, and forbid last-writer-wins; `ProjectSyncEngine.kt:36` lists the
    three backend types; `ProjectSyncEngine.kt:88`-`ProjectSyncEngine.kt:91`
    returns null from `plan(...)`; `ProjectSyncEngine.kt:93`-`ProjectSyncEngine.kt:100`
    returns "Sync backend not implemented" from `sync(...)`. The archived
    roadmap listed C.16 cross-device project sync and suggested Syncthing-style
    filesystem sync or Git-style history with project JSON/media refs, but the
    current live roadmap has no detailed queue item for it. `ProjectArchive.kt`
    already exports `.novacut` ZIPs, writes `media_manifest.json`, rejects
    path traversal, and gates newer schemas; `ProjectAutoSave.kt` already has
    schema-version inspection and future-schema load outcomes that a sync plan
    can reuse.
  - Current-source check: Android's Storage Access Framework lets a user grant
    access to a selected directory tree, which fits a local-folder/Syncthing
    target without broad storage permissions. Syncthing's docs describe block
    hashing, temporary files, conflict copies, and per-folder versioning, which
    supports a "never overwrite silently" design. WebDAV RFC 4918 supplies
    ETag, locking, and conflict status semantics; Nextcloud documents WebDAV
    endpoints and chunked upload v2 with destination headers, 5 MB-5 GB chunk
    limits, and 24-hour upload-directory expiry. Git LFS's Basic Transfer API
    separates object upload/download from verification by object ID and size,
    which is a useful content-addressing reference even if NovaCut does not
    embed Git. JSON Patch / Merge Patch are useful for metadata deltas, but a
    media-heavy project should start with whole-archive or content-addressed
    media parts because timeline-level merge semantics are not yet defined.
  - Touches: `ProjectSyncEngine`, `ProjectArchive`, `ProjectAutoSave`,
    sync-target settings UI, SAF folder grants, WebDAV client facade,
    manifest/hash helper, conflict-review dialog, backup/privacy dashboard
    copy, local-network-gated LAN-peer placeholder, and JVM tests with fake
    targets.
  - Acceptance: the first release supports only local folder and WebDAV targets.
    Users choose the folder or endpoint explicitly, credentials stay in the
    platform credential store or encrypted app storage, and sync is opt-in per
    project. Each pushed unit includes a manifest with project ID, schema
    version, app version, local revision hash, parent revision hash, archive or
    part hashes, media entry hashes, size, and created time. `plan(...)` reports
    `LOCAL_AHEAD`, `REMOTE_AHEAD`, `DIVERGED`, or `NONE` without writing. A
    diverged state refuses automatic overwrite and offers keep local, keep
    remote, or save both as copies. Large archives use chunked/resumable upload
    where the backend supports it. Partial uploads, temp files, credentials,
    diagnostics, and model caches are never treated as project content.
  - Verify: add fake local-folder and fake WebDAV tests for target add/remove
    races, manifest hashing, unchanged/no-op plans, local-ahead plans,
    remote-ahead plans, diverged plans, chunk retry behavior, interruption
    cleanup, future-schema refusal, conflict-copy creation, and no mutation
    during `plan(...)`. Add archive round-trip tests proving synced archives
    still reject path traversal, preserve media references through the manifest,
    and can be opened as duplicate copies when conflict resolution requests it.
  - Complexity: L

#### Appendix - Cycle 22 Sources

- Android Storage Access Framework:
  https://developer.android.com/guide/topics/providers/document-provider
- Android shared documents and tree access:
  https://developer.android.com/training/data-storage/shared/documents-files
- Android data backup overview:
  https://developer.android.com/identity/data/backup
- Android Auto Backup:
  https://developer.android.com/identity/data/autobackup
- Android network security:
  https://developer.android.com/privacy-and-security/security-ssl
- Android network operations:
  https://developer.android.com/develop/connectivity/network-ops/connecting
- Syncthing synchronization model:
  https://docs.syncthing.net/users/syncing.html
- Syncthing file versioning:
  https://docs.syncthing.net/users/versioning.html
- Syncthing folder types:
  https://docs.syncthing.net/users/foldertypes.html
- Nextcloud WebDAV API:
  https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/index.html
- Nextcloud WebDAV chunked upload:
  https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/chunking.html
- WebDAV RFC 4918:
  https://datatracker.ietf.org/doc/html/rfc4918
- Git LFS Basic Transfer API:
  https://github.com/git-lfs/git-lfs/blob/main/docs/api/basic-transfers.md
- JSON Patch RFC 6902:
  https://datatracker.ietf.org/doc/html/rfc6902
- JSON Merge Patch RFC 7396:
  https://datatracker.ietf.org/doc/html/rfc7396
- rsync algorithm:
  https://rsync.samba.org/tech_report/
- Automerge:
  https://automerge.org/
- Yjs:
  https://yjs.dev/

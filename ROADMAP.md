# NovaCut Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03 onward.

Active roadmap for forward-looking work. Shipped work is in
[CHANGELOG.md](CHANGELOG.md), research synthesis lives in
[RESEARCH.md](RESEARCH.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.73** (`versionCode` 210). Last consolidated:
2026-06-11.

> Last researched: Cycle 26 - 2026-06-06.

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
v3.74.56 closed the Cycle 28 audio-focus lane by routing preview playback
through Media3 audio attributes with built-in focus/noisy-device handling,
adding transient focus gates for TTS preview and voiceover recording, pausing
preview before speech/recording capture, and documenting the physical-device QA
checklist.
v3.74.73 started the OSS-backed media durability lane by writing a managed-media
asset sidecar for each new app-owned import/camera capture, including stable
asset id, original/managed URI, type, MIME, size, duration/dimensions when
available, import status, and a cheap content fingerprint for later background
hash/dedupe work.

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
- v3.74.56 adds platform-correct audio focus behavior for live preview,
  TTS preview, and voiceover recording, plus a README physical-device QA
  checklist for external music duck/pause, headphone unplug, recording focus,
  and editor close cleanup.

## Source Archives

- Previous full roadmap: [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- Cross-project feature-port roadmap: [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- May 25 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- Loop 6 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)

## Active Queue

| Priority | Work | Exit criteria |
|---|---|---|
| P2 | Room/Kotlin toolchain migration pass | Revisit Room 2.8.4 and Kotlin/KSP 2.1.21+ or 2.3+/2.4+ on a clean build graph with enough JVM headroom; exit only after `:app:compileDebugKotlin` and Room/KSP schema generation complete without daemon termination. |
| P2 | Project rights and disclosure ledger | Add a project-persisted ledger that joins imported media provenance, stock/provider asset licenses, AI usage, metadata handling, C2PA/IPTC disclosure fields, and share/publish handoff requirements; exit only after export, archive, C2PA draft/signing, stock assets, and direct-publish/share surfaces consume the same source of truth. |
| P2 | Export/share/publish capability matrix | Split share-only handoffs from API-backed uploads with a tested platform capability model covering Android Sharesheet, YouTube `status.containsSyntheticMedia`, TikTok `is_aigc` and commercial toggles, Instagram/Threads manual disclosure reminders, X/LinkedIn upload prerequisites, and user-facing readiness copy. |
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


*Research conducted 2026-06-03. Items below are new — not duplicates of Existing Planned Work.*

These items came from a source-grounded audit (not a feature-discovery pass). The
remaining high-value gaps are in release verification depth, network/crash
hardening, localization delivery, and doc accuracy — none of which the Active
Queue (scaffold adoption/decomposition), the Research-Backed Engine Candidates
(engine activations), or the Deferred list already cover. Every claim below was
checked against `app/src/main`, `.github/workflows/build.yml`, and the manifest.

### Reliability & Security


### Quality & Friction


### Documentation & Polish

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



#### Data Safety


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


#### Appendix — Cycle 4 Sources

- Android memory management and `onTrimMemory()`:
  https://developer.android.com/topic/performance/memory
- Android `<application>` `largeHeap` guidance:
  https://developer.android.com/guide/topics/manifest/application-element#largeHeap

### Researcher Queue (Cycle 5 - 2026-06-04)

Focus: Play/Fastlane release readiness beyond the already-tracked changelog
history item.

#### Distribution Trust


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


#### Appendix — Cycle 6 Sources

- Android receiving simple data from other apps:
  https://developer.android.com/training/sharing/receive
- Android sending simple data and multiple content URIs:
  https://developer.android.com/training/sharing/send

### Researcher Queue (Cycle 7 - 2026-06-04)

Focus: turning the standing light/dark/high-contrast UX audit requirement into
an executable appearance and accessibility regression gate for the Compose app.

#### Accessibility & Visual Trust


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


#### Appendix — Cycle 10 Sources

- Android DataStore file-corruption handling:
  https://developer.android.com/topic/libraries/architecture/datastore#corruption
- Android `ReplaceFileCorruptionHandler` API:
  https://developer.android.com/reference/kotlin/androidx/datastore/core/handlers/ReplaceFileCorruptionHandler

### Researcher Queue (Cycle 11 - 2026-06-04)

Focus: make the advertised sticker/GIF/image overlay lane durable and visible in
preview/export, using app-owned assets instead of fragile raw picker URIs.

#### Creator Workflow & Export Fidelity


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

### Researcher Queue (Cycle 23 - 2026-06-05)

Focus: reconcile the newly tracked `RESEARCH_FEATURE_PLAN.md` companion plan
with the active roadmap without duplicating existing implementation lanes or
touching the in-progress FileProvider source/test worktree edits.

#### Companion Plan Reconciliation


### Researcher Queue (Cycle 24 - 2026-06-06)

Focus: define a single project-level rights and disclosure ledger that connects
AI usage, imported media, stock assets, archive manifests, C2PA/IPTC fields,
metadata handling, and platform share/publish requirements.

#### Rights Ledger And Platform Disclosure

- [ ] 🔬🤖 P2 - Add a project rights and disclosure ledger for export, archive,
  share, and publish paths
  - Why: NovaCut now has several honest but separate trust surfaces. The AI
    usage ledger can summarize AI edits, C2PA export can build draft assertions,
    stock assets have an attribution helper, archive export records media entry
    names, and the roadmap already tracks metadata-scrubbed share/archive paths.
    Those pieces do not share one persisted project schema, so export, archive,
    direct-publish/share handoff, and future platform API uploads cannot answer
    the same user question: "What rights, provenance, metadata, and disclosure
    obligations travel with this finished video?" A unified ledger prevents
    each downstream surface from inventing its own partial truth.
  - Evidence: `AiUsageLedger.kt:40` owns only AI edit records, with default
    severity, merged ranges, JSON persistence, chip summaries, and export
    declaration helpers. `ProjectAutoSave.kt:358`, `ProjectAutoSave.kt:437`,
    and `ProjectAutoSave.kt:683` persist that AI ledger, but there is no
    equivalent persisted ledger for stock assets, imported-media rights,
    metadata-scrub decisions, or platform disclosure status.
    `C2paExportEngine.kt:118`-`C2paExportEngine.kt:177` maps AI entries into
    `c2pa.actions` and digital-source types, while
    `C2paExportEngine.kt:434`-`C2paExportEngine.kt:448` still returns
    unavailable until an embedded signer bridge exists. `StockAssetEngine.kt`
    lines 21-41 define provider-specific stock assets with
    author and license fields, but `StockAssetEngine.kt:54` leaves provider
    configuration false and `StockAssetEngine.kt:82`-`StockAssetEngine.kt:91`
    only formats one attribution line. `ProjectArchive.kt:429`-`ProjectArchive.kt:446`
    writes `media_manifest.json` with only `originalUri` and `entryName`, which
    preserves relink context but not source license, provenance class, creator
    attribution, metadata mode, or AI disclosure requirements.
    `ExportConfig.kt:36`-`ExportConfig.kt:38` has export toggles for AI
    disclosure and sidecars, and `AiUseConfidenceRow.kt` renders AI confidence
    chips, but export settings do not yet consume a broader rights/readiness
    source of truth.
  - Current-source check: YouTube requires creators to disclose realistic
    altered or synthetic content and documents a "How this content was made"
    section that can include creator disclosure and C2PA 2.1+ signals. TikTok's
    help center describes an AI-generated content label for content completely
    generated or significantly edited by AI, and its commercial-content docs
    separately require brand/product disclosure. Meta says Facebook, Instagram,
    and Threads use C2PA/IPTC indicators for AI-generated image labels and
    require people to disclose realistic AI-generated or altered video/audio
    while detection matures. IPTC Photo Metadata 2025.1 adds AI Prompt
    Information, AI Prompt Writer Name, AI System Used, and AI System Version
    Used, which makes "AI disclosure" a structured metadata concern rather than
    only a UI toggle. Adobe Content Credentials positions creator attribution,
    capture/edit/generative history, and inspection as a creator trust surface.
    Pexels and Pixabay both allow broad creative use but restrict standalone
    redistribution and service replication patterns; Adobe Stock license terms
    separate licensed assets, standard/enhanced uses, and indemnified assets.
    The practical implication for NovaCut is that source media, stock media,
    AI-generated material, and platform upload targets need a normalized ledger
    with provider-specific obligations and export-time explanations.
  - Proposed solution: introduce a pure Kotlin `ProjectRightsLedger` (or
    equivalent name) with bounded, versioned entries linked to clip IDs,
    overlay IDs, caption/audio assets, generated AI ranges, and archive media
    entries. Each entry should record source class (`USER_IMPORTED`,
    `CAMERA_HANDOFF`, `STOCK_PROVIDER`, `AI_GENERATED`, `AI_EDITED`,
    `TEMPLATE_ASSET`, `UNKNOWN`), source URI hash or redacted locator,
    provider, provider asset ID, source URL, author, author URL, license name,
    license URL, allowed use flags, attribution requirement, no-standalone
    warning, commercial-use caveat, AI disclosure severity, metadata handling
    mode, C2PA/IPTC field candidates, and last-reviewed timestamp. Keep it
    autosave-persisted, cap array sizes like `AiUsageLedger`, and make import
    tolerant of unknown future fields.
  - UX requirements: add one export/readiness surface that summarizes
    disclosure and rights status before users share, archive, or publish. It
    should show concise rows such as "AI disclosure required", "Stock
    attribution needed", "Metadata will be scrubbed", "Archive contains source
    media", "Platform label must be set manually", and "Content Credentials
    draft only". Rows should drill into per-asset detail without exposing raw
    local file paths. Share/publish handoff copy should include required AI or
    stock attribution reminders when the target platform cannot receive those
    flags through an API. Archive export should carry ledger data but redact or
    hash local-only paths.
  - Technical requirements: extend `AutoSaveState` with a bounded ledger field;
    add pure serialization/deserialization tests; update `ProjectArchive` to
    include rights entries in or alongside `media_manifest.json`; map
    `StockAssetEngine.StockAsset` into ledger entries before import; bridge
    `AiUsageLedger` entries into broader ledger summaries without duplicating
    AI data; feed C2PA/IPTC candidate fields from the ledger; expose a reducer
    that computes per-export `DisclosureReadiness`; route `DirectPublishEngine`
    and share-intent copy through that reducer; and update Privacy Dashboard
    copy to explain that the ledger is local project metadata, not telemetry.
  - Acceptance:
    - [ ] Imported user media, camera-handoff clips, stock assets, AI-generated
      clips/ranges, image overlays, and template assets can each produce a
      ledger entry with stable IDs and redacted source details.
    - [ ] Autosave round-trips the ledger, caps runaway entries, skips corrupt
      rows without dropping the rest of the project, and ignores unknown future
      fields.
    - [ ] Export readiness computes AI disclosure, stock attribution,
      metadata-scrub/preserve warnings, C2PA draft/signed status, and
      platform-handoff reminders from the same ledger.
    - [ ] Archive export includes enough ledger detail to restore attribution
      and disclosure obligations on another device without leaking local
      filesystem paths.
    - [ ] Share/direct-publish handoff copy never claims a platform label,
      API upload, license clearance, or Content Credential exists unless the
      capability model and ledger prove it.
    - [ ] Privacy Dashboard and Data safety docs explain where the ledger is
      stored, when it is exported, and how users can inspect or clear
      project-scoped records.
  - Verify: add JVM tests for ledger serialization, corrupt-row tolerance,
    source-class mapping, stock-license flags, no-standalone warnings, AI
    severity aggregation, metadata-mode summaries, C2PA/IPTC field projection,
    archive manifest redaction, share-copy reminders, and no-secret/no-path
    diagnostics. Add Compose tests for empty, warning, blocked, and ready
    export states. Add regression fixtures proving older autosaves with only
    `aiUsageLedger` still load and newer ledger fields do not break current
    project recovery.
  - Dependencies: Cycle 13 signed C2PA work can consume this ledger but does
    not need to finish first. Cycle 17 stock-provider contracts should feed
    license fields into this ledger. Cycle 18 metadata-scrub paths should use
    the ledger's metadata mode instead of a separate export-only toggle.
    Cycle 14 direct-publish/share copy should use the same readiness reducer.
  - Risks: provider terms can change, platform disclosure controls can move,
    and over-recording source details can create a privacy problem. Keep terms
    source URLs and review timestamps explicit, avoid raw path persistence, and
    bias toward "needs review" when license/disclosure status is unknown.
  - Score: User Value 4 + Product/Trust Value 5 + Strategic Differentiation 4 +
    Confidence 4 - Effort 4 = 13.
  - Complexity: L

#### Appendix - Cycle 24 Sources

- YouTube altered or synthetic content disclosure:
  https://support.google.com/youtube/answer/14328491
- YouTube "How this content was made" and C2PA disclosure notes:
  https://support.google.com/youtube/answer/15447836
- TikTok AI-generated content help:
  https://support.tiktok.com/en/using-tiktok/creating-videos/ai-generated-content
- TikTok commercial content disclosure:
  https://ads.tiktok.com/help/article/about-the-content-disclosure-setting-for-creators
- Meta labeling AI-generated images on Facebook, Instagram, and Threads:
  https://about.fb.com/news/2024/02/labeling-ai-generated-images-on-facebook-instagram-and-threads/
- Meta approach to AI-generated content and manipulated media:
  https://about.fb.com/news/2024/04/metas-approach-to-labeling-ai-generated-content-and-manipulated-media/
- IPTC Photo Metadata Standard:
  https://iptc.org/standards/photo-metadata/iptc-standard/
- IPTC Photo Metadata User Guide:
  https://www.iptc.org/std/photometadata/documentation/userguide/
- Adobe Content Credentials overview:
  https://helpx.adobe.com/creative-cloud/apps/adobe-content-authenticity/content-credentials/overview.html
- C2PA specifications:
  https://spec.c2pa.org/specifications/
- Pexels terms guide:
  https://help.pexels.com/hc/en-us/articles/900005880463-What-are-the-Terms-and-Conditions
- Pexels API documentation:
  https://www.pexels.com/api/documentation/
- Pixabay Terms of Service:
  https://pixabay.com/service/terms/
- Adobe Stock license terms:
  https://stock.adobe.com/license-terms

### Researcher Queue (Cycle 25 - 2026-06-06)

- **Focus:** export, Android share handoff, and "direct publish" readiness copy
  across platform-specific disclosure and upload requirements.
- **Local files inspected:**
  - `app/src/main/java/com/novacut/editor/engine/DirectPublishEngine.kt`
  - `app/src/main/java/com/novacut/editor/ui/editor/V369FeaturesPanel.kt`
  - `app/src/main/java/com/novacut/editor/ui/editor/V369Delegate.kt`
  - `app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt`
  - `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`
  - `app/src/main/java/com/novacut/editor/engine/PrivacyDashboard.kt`
  - `app/src/test/java/com/novacut/editor/engine/DirectPublishEngineTest.kt`
  - `app/src/test/java/com/novacut/editor/engine/PrivacyDashboardTest.kt`
  - `README.md`
  - `fastlane/metadata/android/en-US/full_description.txt`
- **External sources checked:** Android sharing guidance, YouTube Data API
  video resource, TikTok Content Posting API Direct Post, TikTok Content
  Sharing Guidelines, Meta AI-labeling notes for Facebook/Instagram/Threads,
  X chunked media upload docs, and LinkedIn Videos API docs.

#### P2 - Replace direct-publish claims with a tested platform capability matrix

- **Problem:** NovaCut currently exposes a "Direct Publish" surface, but the
  implementation is an Android share-intent handoff rather than a platform API
  uploader. That mismatch is especially risky now that platform APIs and policy
  surfaces have distinct synthetic-media, AI-generated, commercial-disclosure,
  consent, visibility, upload, and status-tracking requirements.
- **Why this matters:** creators need to know whether NovaCut actually set a
  platform label, merely opened another app, or only copied disclosure text into
  a share payload. Trust features such as the AI usage ledger, C2PA draft
  sidecars, stock attribution, and future publish adapters should converge on
  one readiness model so NovaCut does not overclaim compliance.
- **Evidence:** `DirectPublishEngine.kt:16` describes a direct-publish facade
  for YouTube, TikTok, Instagram, and Threads, but `DirectPublishEngine.kt:84`
  to `DirectPublishEngine.kt:90` build an `Intent.ACTION_SEND` share intent.
  `DirectPublishEngine.kt:38` to `DirectPublishEngine.kt:42` marks only
  YouTube and TikTok as having AI disclosure controls, while
  `DirectPublishEngine.kt:122` to `DirectPublishEngine.kt:134` turns that into
  share text reading `AI disclosure selected` rather than proof that a platform
  API field was set. `V369FeaturesPanel.kt:213` to
  `V369FeaturesPanel.kt:214` label the UI "Direct Publish" and "Send the last
  export to YouTube / TikTok / IG / Threads / X / LinkedIn"; the chip action at
  `V369FeaturesPanel.kt:234` calls `V369Delegate.publishLastExport(...)`.
  `V369Delegate.kt:403` to `V369Delegate.kt:424` summarizes the AI ledger and
  launches the returned intent. `ExportDelegate.kt:733` to
  `ExportDelegate.kt:755` creates the generic share intent and only appends
  `AI disclosure: ...` text when the disclosure setting is enabled.
  `ExportSheet.kt:628` to `ExportSheet.kt:652` distinguishes AI disclosure and
  AI-use/C2PA sidecars, but the completed export action at
  `ExportSheet.kt:387` is only `onShare`. `PrivacyDashboard.kt:169` to
  `PrivacyDashboard.kt:175` correctly states the AI usage ledger is local and
  may be exported into sidecars, but there is not yet a capability model that
  explains when a platform label was actually transmitted.
- **Current-source check:** Android's documentation shows share-only flows
  should use `Intent.createChooser(...)` for the Android Sharesheet and cautions
  that adding custom targets reduces system suggestions. YouTube's video
  resource exposes `status.containsSyntheticMedia` for `videos.insert` and
  `videos.update`, and unverified API projects uploading videos are restricted
  to private viewing until audit. TikTok Direct Post requires creator-info
  query, post initialization, export/upload, explicit consent, and includes
  `is_aigc`, `brand_content_toggle`, and `brand_organic_toggle` request fields;
  TikTok's guidelines require commercial-disclosure UI, preview/editable text,
  express consent before upload, processing-status polling or webhooks, and
  confidential client-secret handling. Meta says Facebook, Instagram, and
  Threads use C2PA/IPTC indicators for AI-generated image labeling and require
  user disclosure for realistic AI-generated or altered video/audio, but the
  inspected NovaCut handoff cannot prove those app-side labels were set. X and
  LinkedIn both document API upload pipelines with chunking, multipart upload,
  permissions, processing status, and token/secrets requirements that do not
  exist in the current share-intent facade.
- **Proposed solution:** add a pure Kotlin `PlatformPublishCapability` or
  `PublishReadinessMatrix` owned by the export/share layer. Each target should
  declare supported methods (`ANDROID_SHARE_SHEET`, `TARGETED_APP_HANDOFF`,
  `API_UPLOAD_AVAILABLE`, `API_UPLOAD_BLOCKED`), current availability, required
  account/auth scopes, upload strategy, max file limits, visibility/audit
  constraints, processing-status behavior, secrets policy, editable metadata
  requirements, and disclosure mappings. The UI should render the matrix before
  opening a target so users see "Share handoff", "Manual label needed",
  "API upload unavailable", "Private until platform audit", "No status
  tracking", or "Ready for API upload" instead of a single direct-publish claim.
- **Platform mappings to model:**
  - YouTube: when a real API uploader exists, map disclosure-bearing AI ledger
    or future rights-ledger summaries into `status.containsSyntheticMedia` and
    preserve paid-product-placement as a separate field. Until then, share
    handoff copy must say the user still needs to set the YouTube altered or
    synthetic content disclosure manually.
  - TikTok: when Direct Post exists, map AI-generated entries to `is_aigc`,
    project/commercial ledger rows to `brand_content_toggle` and
    `brand_organic_toggle`, and block publish if the commercial disclosure
    toggle is on with no selected option. Require preview, editable text,
    explicit consent, client-secret isolation, transfer-mode selection, and
    status polling/webhook handling. Until then, treat TikTok as manual app
    handoff.
  - Instagram, Threads, X, and LinkedIn: mark as manual handoff or blocked API
    upload until adapter-specific authorization, media-upload, disclosure, and
    status requirements are implemented and tested. Do not claim platform AI,
    commercial, or content-credential labels were set through share intent.
  - Generic Android: use the Android Sharesheet by default for share-only export
    and keep target chips as explicit app handoff shortcuts with honest copy.
- **UX requirements:** rename the current `Direct Publish` panel to
  `Open in app` or `Share handoff` until API uploads exist. Show a compact
  readiness list beside each target: method, disclosure state, sidecar state,
  platform label state, account/API state, and status-tracking state. Keep the
  completed export primary action as a generic share action, but route the
  platform shortcut flow through the capability matrix. If the rights ledger
  from Cycle 24 exists, include stock attribution and metadata-scrub reminders
  in the same readiness copy.
- **Technical requirements:** add a matrix reducer that consumes `ExportConfig`,
  `AiUsageLedger`, future `ProjectRightsLedger`, target enum, API credential
  availability, and app install/package detection. Keep it pure and JVM-tested.
  Add explicit data classes for disclosure fields rather than deriving copy
  directly from `Target.hasAiDisclosureControl`. Split result methods so tests
  can distinguish `SHARE_INTENT`, `ANDROID_SHARE_SHEET`, `TARGETED_APP_HANDOFF`,
  `API_UPLOAD`, and `BLOCKED`. Keep secrets out of diagnostics and open-source
  code. Update `DirectPublishEngineTest` expectations so "selected" is only
  used when an actual API payload field is set.
- **Acceptance:**
  - [ ] The V369 panel no longer uses "Direct Publish" wording when only share
        intents are available.
  - [ ] Generic export sharing uses the Android Sharesheet by default, with any
        platform-specific chips clearly labeled as app handoff shortcuts.
  - [ ] YouTube readiness distinguishes manual share handoff from API upload and
        only marks `status.containsSyntheticMedia` set when the API upload path
        has populated that field.
  - [ ] TikTok readiness models `is_aigc`, `brand_content_toggle`,
        `brand_organic_toggle`, user preview, editable text, express consent,
        client-secret isolation, transfer mode, and status polling/webhook
        requirements.
  - [ ] Instagram, Threads, X, and LinkedIn are not described as receiving AI,
        commercial, or C2PA labels unless an adapter explicitly supports and
        verifies those fields.
  - [ ] Share text includes manual disclosure and stock-attribution reminders
        whenever the target cannot receive structured platform fields.
  - [ ] Tests cover capability computation, copy generation, unavailable API
        states, no-secret diagnostics, and older share-intent behavior.
- **Verify:** add JVM tests for every target/method state, AI-ledger summaries,
  manual disclosure reminders, TikTok commercial-disclosure validation,
  YouTube synthetic-media mapping, Android Sharesheet intent generation, and
  the difference between opening an app and uploading through an API. Add
  Compose tests for the target-readiness rows and disabled/blocking states.
- **Dependencies:** Cycle 24 `ProjectRightsLedger` would provide stock,
  metadata, and rights obligations. Cycle 13 signed C2PA work can feed the
  content-credential state. Cycle 14 direct-publish planning should be revised
  to consume this matrix before any OAuth/upload adapter work.
- **Risks:** platform APIs and disclosure policies change, platform partner
  approvals may be required, unverified upload projects may force private
  visibility, and secret/token handling cannot be embedded in a local-only
  Android client without a carefully designed backend or partner configuration.
- **Score:** User Value 4 + Product/Trust Value 5 + Strategic Differentiation 4
  + Confidence 4 - Effort 3 = 15.
- **Complexity:** M.

#### Appendix - Cycle 25 Sources

- Android sharing and Sharesheet guidance:
  https://developer.android.com/training/sharing/send
- YouTube Data API videos resource:
  https://developers.google.com/youtube/v3/docs/videos
- YouTube Data API videos.insert:
  https://developers.google.com/youtube/v3/docs/videos/insert
- TikTok Content Posting API Direct Post:
  https://developers.tiktok.com/doc/content-posting-api-reference-direct-post?enter_method=left_navigation
- TikTok Content Sharing Guidelines:
  https://developers.tiktok.com/doc/content-sharing-guidelines/
- Meta labeling AI-generated images on Facebook, Instagram, and Threads:
  https://about.fb.com/news/2024/02/labeling-ai-generated-images-on-facebook-instagram-and-threads/
- Instagram Graph API media endpoint:
  https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media
- X chunked media upload:
  https://docs.x.com/x-api/media/quickstart/media-upload-chunked
- LinkedIn Videos API:
  https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/videos-api?view=li-lms-2026-04

### Researcher Queue (Cycle 26 - 2026-06-06)

- **Focus:** README, fastlane Play metadata, screenshot source copy, privacy
  policy, model registry, and release-validator claims against implementation
  gates.
- **Local files inspected:**
  - `README.md`
  - `fastlane/metadata/android/en-US/full_description.txt`
  - `fastlane/metadata/android/en-US/short_description.txt`
  - `fastlane/metadata/android/en-US/title.txt`
  - `fastlane/metadata/android/en-US/images/asset_inventory.json`
  - `fastlane/metadata/android/en-US/images/_source/*.svg`
  - `docs/privacy-policy.md`
  - `docs/models.md`
  - `docs/play-data-safety.md`
  - `docs/play-listing-assets.md`
  - `CHANGELOG.md`
  - `scripts/validate_play_listing_assets.py`
  - `scripts/generate_play_listing_assets.py`
  - `app/src/main/java/com/novacut/editor/engine/AiToolRequirements.kt`
  - `app/src/main/java/com/novacut/editor/engine/C2paExportEngine.kt`
  - `app/src/main/java/com/novacut/editor/engine/DirectPublishEngine.kt`
  - `app/src/main/java/com/novacut/editor/engine/StockAssetEngine.kt`
  - `app/src/main/java/com/novacut/editor/engine/whisper/WhisperEngine.kt`
  - `app/build.gradle.kts`
- **External sources checked:** Google Play Deceptive Behavior policy, Google
  Play Metadata policy, Google Play preview asset guidance, Google Play
  AI-generated content policy explainer, and current Google Play/feature-page
  wording for CapCut, KineMaster, and PowerDirector.
- **Verification run:** `python scripts\validate_play_listing_assets.py --quiet`
  passed, which confirms the current validator covers packaging/length/privacy
  checks but not semantic claim accuracy.


#### Appendix - Cycle 26 Sources

- Google Play Deceptive Behavior policy:
  https://support.google.com/googleplay/android-developer/answer/16680223?hl=en
- Google Play Metadata policy:
  https://support.google.com/googleplay/android-developer/answer/9898842?hl=en
- Google Play preview asset guidance:
  https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
- Google Play AI-generated content policy explainer:
  https://support.google.com/googleplay/android-developer/answer/14094294?hl=en
- CapCut Google Play listing:
  https://play.google.com/store/apps/details?gl=US&id=com.lemon.lvoverseas
- KineMaster Google Play listing:
  https://play.google.com/store/apps/details?gl=US&hl=en-GB&id=com.nexstreaming.app.kinemasterfree
- KineMaster features page:
  https://www.kinemaster.com/features
- PowerDirector Google Play listing:
  https://play.google.com/store/apps/details?gl=US&hl=en-US&id=com.cyberlink.powerdirector.DRA140225_01

## Continuation State

### Last Completed Cycle

Cycle 26 - README and Play listing claim audit against implementation gates.

### Current Focus

Continue with Cycle 27: inspect project archive/import, media manifest, media
relink, metadata-scrub/preserve decisions, AI-use/C2PA sidecar placement, and
rights/disclosure reporting. Add an implementation-ready archive/import rights
report spec that explains what obligations travel with a project archive, what
local paths must be redacted, and what users see when imported media or rights
entries cannot be restored.

### Important Findings So Far

- Direct repo write access succeeded for `ROADMAP.md` on 2026-06-06.
- `rtk git log -10` could not run because `rtk` is not on PATH in this Codex
  Windows environment; regular `git -C ... log -10 --oneline` was used instead.
- Recent commits show FileProvider path hardening and C2PA dependency blocker
  documentation after the previous Cycle 23 reconciliation.
- The worktree already had an untracked `AGENTS.md` before this roadmap edit;
  it was left untouched.
- No Gradle verification was run for Cycle 26 because this pass changed only
  `ROADMAP.md`.
- `python scripts\validate_play_listing_assets.py --quiet` passed, proving the
  current release validator checks listing asset packaging and privacy/Data
  Safety coverage but not semantic claim drift.
- README currently says Auto Captions use multilingual/99-language Whisper,
  while the active model registry and runtime point to `whisper.tiny.en.onnx`;
  multilingual Sherpa/Whisper models are future targets.
- Fastlane full description lists stabilization and style transfer as
  on-device AI tools, while `AiToolRequirements` marks those paths
  `DEPENDENCY_MISSING`.
- Privacy policy wording is stronger than the fastlane offline paragraph because
  it names optional model downloads, cloud-capable tools, app-directed
  share/export paths, LAN streaming, and future remote C2PA signing.
- Screenshot source text says disclosure metadata is ready for share targets;
  that should be gated by Cycle 25's platform capability matrix and the current
  draft-only C2PA availability state.
- Google Play policy sources confirm metadata, screenshots, and promotional
  assets need to precisely reflect app functionality.

### Next Best Actions

1. Inspect `ProjectArchive`, archive import report types, media manifests,
   backup/import UI surfacing, `MediaRelinkProbe`, metadata scrub/preserve
   engines, AI-use sidecar placement, and direct share/export handoff paths.
2. Research archive/backup metadata disclosure patterns for creative apps,
   C2PA/IPTC sidecars, and Android privacy guidance around redacting local
   paths in exported diagnostic or archive artifacts.
3. Add Cycle 27 findings to `ROADMAP.md` with a rights/disclosure report spec
   for archive export/import, acceptance criteria, risks, and test fixtures.

### Unprocessed Leads

- Whether archive export currently includes `.ai-use.json` and
  `.c2pa-draft-manifest.json` sidecars or only final video export does.
- Whether `media_manifest.json` includes enough redacted provenance for imported
  media and overlay assets without leaking local paths.
- Whether archive import reports rights/disclosure warnings alongside missing
  media, schema mismatch, regenerated IDs, and lossy timeline-exchange issues.
- Whether metadata-scrub decisions are project state, export-only state, or
  currently not represented in archive manifests.
- Whether a future `ProjectRightsLedger` should live in archive root, media
  manifest, autosave JSON, or all three with versioned reconciliation rules.

### Files Still To Inspect

- `app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt`
- `app/src/main/java/com/novacut/editor/engine/MediaRelinkProbe.kt`
- `app/src/main/java/com/novacut/editor/engine/MediaImportEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/MetadataScrubEngine.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ProjectBackupPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`
- `app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt`
- `app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt`
- `app/src/main/java/com/novacut/editor/engine/C2paExportEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/StockAssetEngine.kt`

### Searches Still To Run

- `Android exported archive redact local file paths privacy guidance`
- `C2PA sidecar archive manifest best practices`
- `IPTC metadata exported archive creator attribution AI disclosure`
- `video editor project archive missing media relink report UX`
- `creative app backup restore missing assets report`
- `stock media attribution project archive manifest`

### Next Research Cycles

1. Cycle 27 - Archive/import rights report and metadata-scrub UX.
2. Cycle 28 - Stock-provider provider-contract implementation spec.
3. Cycle 29 - C2PA dependency distribution decision and binary-size gate.
4. Cycle 30 - Instrumented accessibility audit for export and disclosure sheets.
5. Cycle 31 - Localization slice for trust/disclosure strings.
6. Cycle 32 - Product-health ledger relationship to local project ledger.
7. Cycle 33 - WebDAV/local-folder sync manifest compatibility with rights data.
8. Cycle 34 - External community pain points around mobile editor export,
    watermark, attribution, and platform disclosure workflows.
9. Cycle 35 - Platform publish adapter architecture and backend/secret options.
10. Cycle 36 - Public-claim validator implementation breakdown and fixtures.

### Researcher Queue (Cycle 27 - 2026-06-09)

Focus: competitive gap closure, toolchain freshness, platform compliance,
performance, and monetization readiness grounded in ecosystem research across
30+ sources.

**Local files inspected:** full repository tree (255 Kotlin source files),
`build.gradle.kts`, `gradle/libs.versions.toml`, `CLAUDE.md` (813 lines),
`README.md`, `ROADMAP.md`, `RESEARCH.md`, `CHANGELOG.md`, `.github/workflows/`,
`scripts/`, `docs/`, `fastlane/`, last 200 git commits.

**External sources checked:** Media3 1.10 release blog, Compose BOM 2026.05
release notes, Room 2.8.4 release notes, Hilt/Dagger 2.58 changelog,
Sherpa-ONNX benchmarks (51x speedup), Moonshine ASR benchmarks (5-15x faster
than Whisper), VN Video Editor feature set, Meta Edits feature set, CapCut
pricing/ban status, KineMaster/PowerDirector features, Open Video Editor
(F-Droid), Real-ESRGAN-ncnn-vulkan Android port, C2PA Android library docs,
DeepFilterNet Android library, FFmpegKit 16KB alignment, MediaPipe GPU delegate
issues, Android 16/17 behavior changes, Google 16KB deadline, Google developer
verification mandate, F-Droid distribution threat, WCAG 2.2 mobile guidance,
Compose recomposition optimization guides, app monetization strategy surveys.

#### Toolchain & Build

- [ ] 🔬🤖 P2 — Upgrade Kotlin to 2.2+ for Strong Skipping and Compose performance
  - Why: NovaCut's large EditorState with 6 domain slices benefits directly from
    Strong Skipping Mode improvements in Kotlin 2.2+ Compose compiler, which
    automatically skips composables whose parameters are reference-equal even if
    technically unstable. This covers ~30% of recomposition issues for free and
    is especially valuable for the dense editor panel router hosts.
  - Evidence: `libs.versions.toml:2` declares `kotlin = "2.1.0"`. The current
    Compose BOM is 2026.05.01. Hilt 2.58 already supports Kotlin 2.2 via
    unshaded Kotlinx Metadata. Room 2.7.2 targets Kotlin 2.0+. Kotlin 2.4.0 is
    available per roadmap notes, but 2.2 is the minimum for Strong Skipping
    improvements.
  - Touches: `libs.versions.toml` (kotlin, ksp versions), `build.gradle.kts`,
    Compose compiler config, Hilt/KSP compatibility verification.
  - Acceptance: `./gradlew :app:compileDebugKotlin --rerun-tasks` passes with
    Kotlin 2.2+ and matching KSP. Compose metrics show improved skippability
    for EditorScreen and panel hosts. No daemon OOM during build.
  - Complexity: M
  - Risk: KSP version must match Kotlin exactly; Room/Hilt compatibility needs
    verification on clean build graph with sufficient JVM heap.

#### Competitive Feature Gaps

- [ ] 🔬🤖 P2 — Add AI editing suggestions (smart cut/trim recommendations)
  - Why: Meta Edits differentiates with "automated editing suggestions that
    analyze uploaded footage to recommend intelligent cuts, effects, and
    enhancements." CapCut has similar smart-cut features. NovaCut already has
    scene detection, beat sync, and auto-edit engines, but they require manual
    activation. A proactive suggestion system would match the competitive bar.
  - Evidence: NovaCut has `AiFeatures.generateAutoEdit()` for highlight reels
    and `generateBeatSyncEdits()` for beat-synced cuts. These are already wired
    but require user initiation from dedicated panels. No proactive suggestion
    UI exists.
  - Touches: New `EditingSuggestionEngine` analyzing imported clips on add,
    non-intrusive suggestion chips in timeline or tool area, existing AI
    features for analysis backend.
  - Acceptance: when a user adds media, a background analysis generates optional
    suggestions (trim points, scene boundaries, beat markers) that appear as
    dismissible chips. No suggestion blocks the editing flow.
  - Complexity: M

#### Platform Compliance & Distribution

- [ ] 🔬🤖 P1 — Prepare for Google developer-verification mandate (September 2026)
  - Why: From September 2026, developers distributing apps outside the Play
    Store must register with Google or have their apps blocked on certified
    Android devices. This directly threatens NovaCut's F-Droid and sideload
    distribution channels. The 7-month enrollment window is already active.
  - Evidence: Google's registration requires identity verification, government
    ID, agreement to Google's TOS, and a $25 fee. F-Droid's catalog model
    conflicts with this since F-Droid distributes apps from developers who may
    be anonymous by design. The EFF and F-Droid have formally objected.
  - Touches: Developer account registration, F-Droid metadata (`fdroid.yml`),
    alternative distribution documentation, README distribution section.
  - Acceptance: NovaCut's developer account is registered with Google before
    September 2026. F-Droid metadata includes a contingency note. README
    documents available distribution channels and their status.
  - Status: local distribution-readiness subset shipped in v3.74.63. CI now
    validates GitHub Releases, Google Play metadata, F-Droid metadata, checksum
    publication, and Android developer-verification wording. README and
    Fastlane copy document that verified-developer package-name registration is
    not complete. Remaining work is external/operator-gated: complete the
    account identity flow, register `com.novacut.editor` with a signed APK, and
    finalize the F-Droid reproducible-build signing-key policy tracked by the
    AllowedAPKSigningKeys item below.
  - Blocker: requires Google account identity verification, any applicable
    developer fee, signing-key/account ownership decisions, and final
    F-Droid/reproducible-build distribution policy.
  - Complexity: S

- [ ] 🔬🤖 P2 — Audit Android 17 foreground-service audio API restrictions
  - Why: Android 17 blocks foreground services started from the background from
    accessing audio APIs that typically require a visible UI context. NovaCut's
    `ExportService` uses `MEDIA_PROCESSING` type and delegates to Media3
    Transformer, but voiceover preview via `TtsEngine` and `VoiceoverRecorder`
    may be affected.
  - Evidence: Android 17 docs say "beginning in Android 17, foreground services
    started from the background are blocked from audio APIs." NovaCut's export
    pipeline uses Transformer (not direct audio APIs), but `TtsEngine.kt`
    synthesizes via Android TTS and `VoiceoverRecorder.kt` uses MediaRecorder.
  - Touches: `TtsEngine.kt`, `VoiceoverRecorder.kt`, `ExportService.kt`,
    manifest foreground-service type declarations.
  - Acceptance: TTS preview and voiceover recording work on Android 17 without
    audio API blocks. Export pipeline confirmed unaffected. Document any
    required UI-context constraints.
  - Status: local static audit shipped in v3.74.64. CI now validates that
    `ExportService` stays free of TextToSpeech, MediaRecorder, playback, and
    audio-focus calls; the manifest keeps the service non-exported and
    media-processing-only; TTS preview and voiceover recording request focus
    from visible editor paths; and README documents the Android 17
    `adb shell cmd audio set-enable-hardening throw` smoke path. Remaining
    verification requires an Android 17 Beta 3+ device/emulator with audio
    hardening enabled and logcat checked for `AudioHardening`.
  - Blocker: physical/emulator Android 17 Beta 3+ runtime validation is not
    available in this local pass.
  - Complexity: S

#### Performance & Architecture

- [ ] 🔬🤖 P2 — Audit Compose recomposition scope with Compose metrics and Layout Inspector
  - Why: NovaCut's EditorState was decomposed into 6 domain slices (v3.74.20-
    v3.74.26) but no tooling audit has confirmed that panel hosts actually skip
    recomposition when unrelated slices change. With the dense editor UI, an
    invisible recomposition storm could drain battery and cause micro-stutter on
    mid-range devices.
  - Evidence: CLAUDE.md documents the domain-slice decomposition but no Compose
    compiler metrics or Layout Inspector recomposition counts are referenced
    anywhere in the codebase. The "Invisible Performance Killer" Compose
    stability analysis pattern applies directly to NovaCut's architecture.
  - Touches: Compose compiler metrics output (add `-PcomposeMetrics` to CI),
    `@Stable`/`@Immutable` annotations on domain state classes, Layout Inspector
    verification.
  - Acceptance: Compose compiler reports show all domain state slices as stable.
    Panel hosts skip recomposition when only unrelated slices change, verified
    via recomposition counts in Layout Inspector. No recomposition count > 2 on
    any panel host when editing in a different panel.
  - Complexity: M

- [ ] 🔬🤖 P3 — Evaluate multi-module Gradle structure for build performance
  - Why: 255 Kotlin files in a single `:app` module means every change triggers
    a full module recompilation. As the codebase grows, incremental build times
    will degrade. Splitting engines and UI into separate Gradle modules enables
    parallel compilation and better incremental builds.
  - Evidence: All source lives under `app/src/main/java/com/novacut/editor/`.
    There is one `:baselineprofile` module but no feature/library modules. The
    engine layer (29 singletons) has no Compose dependency and could be a pure
    Kotlin/Android library module.
  - Touches: New `:engine` and `:model` Gradle modules, Hilt module boundary
    configuration, build.gradle.kts dependency graph.
  - Acceptance: engine and model code compiles independently. Incremental build
    after a UI-only change does not recompile engine code. Hilt injection still
    works across module boundaries.
  - Complexity: L

#### Monetization & Distribution Readiness

- [ ] 🔬🤖 P2 — Define monetization strategy and implement freemium gate infrastructure
  - Why: NovaCut is MIT-licensed and free, but has no monetization path. The
    mobile video editor market shows freemium with one-time IAP converts 4-9%
    for productivity apps. CapCut's Pro price doubling to $19.99/mo drove
    creator exodus, creating a window for a no-watermark, privacy-first
    alternative with optional premium features.
  - Evidence: No billing dependency, Play Billing Library, or IAP code exists
    in the codebase. The README says MIT license. Competitors: CapCut Pro
    $19.99/mo, KineMaster watermark on free, PowerDirector freemium.
  - Touches: Product decision (which features are free vs premium), Google Play
    Billing Library integration, feature-flag infrastructure, Settings premium
    section, Play Store listing price/IAP metadata.
  - Acceptance: a clear product decision documented in ROADMAP. If freemium:
    billing infrastructure wired, at least one premium feature gated (e.g., AI
    model packs, 4K export, batch export), free tier retains no-watermark
    promise. If pure FOSS: document sustainability plan (donations, sponsors).
  - Complexity: L

#### Appendix -- Cycle 27 Sources

- Media3 1.10 release blog: https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- Sherpa-ONNX (51x faster Whisper): https://github.com/k2-fsa/sherpa-onnx
- Moonshine ASR (5-15x faster): https://github.com/moonshine-ai/moonshine
- Offline speech benchmark: https://voiceping.net/en/blog/research-offline-speech-transcription-benchmark/
- VN Video Editor multi-track: https://vlognow.me/blog/features/multi-track-timeline/
- Meta Edits 2026 features: https://www.inro.social/blog/edits-new-meta-app
- CapCut ban status: https://capcutpro-apks.com/capcut-ban-status/
- CapCut alternatives (eesel): https://www.eesel.ai/blog/capcut-alternatives
- C2PA Android library: https://opensource.contentauthenticity.org/docs/c2pa-android/
- FFmpegKit 16KB: https://proandroiddev.com/ffmpeg-kit-16-kb-page-size-in-android-d522adc5efa2
- DeepFilterNet Android: https://github.com/KaleyraVideo/AndroidDeepFilterNet
- MediaPipe GPU issue: https://github.com/google-ai-edge/mediapipe/issues/5954
- Android 17 FGS changes: https://developer.android.com/about/versions/17/behavior-changes-17
- Google developer verification: https://www.androidpolice.com/f-droid-google-dev-registration-decree/
- Google 16KB deadline: https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html
- WCAG 2.2 mobile guide: https://corpowid.ai/blog/mobile-application-accessibility-practical-humancentered-guide-android-ios
- Compose performance: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose stability deep dive: https://ahmednmahran.medium.com/the-invisible-performance-killer-a-deep-dive-into-jetpack-compose-stability-236a810c16fb
- App monetization 2026: https://adapty.io/blog/mobile-app-monetization-strategies/
- Room 2.8.4 releases: https://developer.android.com/jetpack/androidx/releases/room
- Hilt/Dagger 2.58: https://github.com/google/dagger/releases
- Real-ESRGAN Android: https://github.com/tumuyan/RealSR-NCNN-Android
- Compose BOM 2026.05: https://developer.android.com/jetpack/androidx/releases/compose-material3

### Researcher Queue (Cycle 28 - 2026-06-09)

Focus: code-verified behavioral gaps and table-stakes feature absences that
Cycles 1-27 did not cover. Every item below was checked against `app/src/main`
source, `gradle/libs.versions.toml`, `.gitignore`/`git ls-files`, and external
competitor/platform sources. No overlap with existing queue items was found
(audio focus is distinct from the Cycle 27 Android 17 FGS-audio audit;
reproducible builds is distinct from the Cycle 1 checksum item and the Cycle 27
developer-verification item; the loaded-timeline benchmark extends, not
repeats, the shipped blank-editor Macrobenchmark).

#### P2 — trust, reliability, data safety

- [ ] 🔬🤖 P2 — F-Droid reproducible-build readiness with AllowedAPKSigningKeys
  Why: reproducible builds let F-Droid distribute NovaCut's own signed APK
  (no F-Droid key divergence), which is the strongest mitigation available for
  the September 2026 developer-verification distribution risk already tracked
  in Cycle 27 — and it hardens the supply chain beyond the Cycle 1 checksum
  item.
  Evidence: release builds fall back to debug signing in CI
  (`app/build.gradle.kts` signing fallback; README Release Signing section),
  meaning CI artifacts are not byte-comparable to developer-signed releases.
  F-Droid verifies by copying the v2/v3 signature onto its own build, so APKs
  must be bit-identical apart from the signature
  (https://f-droid.org/docs/Reproducible_Builds/,
  https://f-droid.org/en/2023/09/03/reproducible-builds-signing-keys-and-binary-repos.html).
  Touches: deterministic build inputs (pin JDK/AGP, strip timestamps, disable
  vcsInfo, stable resource ordering), an apksigner-only signing flow
  documented, a CI job that builds twice and diffs the unsigned APKs,
  fastlane/F-Droid metadata with `AllowedAPKSigningKeys` (signer cert
  SHA-256), docs section on verification.
  Acceptance: two clean-room release builds of the same tag produce
  byte-identical unsigned APKs; the signer cert digest is published; an
  F-Droid-style verification (copy signature, apksigner verify) passes; CI
  enforces the double-build diff on tagged releases.
  Complexity: M

#### P2 — competitive feature gaps

#### P2 — verification depth

- [ ] 🔬🤖 P2 — Roborazzi screenshot-regression suite for primary editor surfaces
  Why: the standing per-pass UX audit and the v3.74.48 contrast-token gates
  cannot catch rendered regressions (clipping, overflow, spacing, broken
  badges) — a JVM screenshot lane makes them diffable in CI without an
  emulator.
  Evidence: `gradle/libs.versions.toml` has no robolectric/roborazzi/paparazzi
  entries; appearance tests assert token math only. Roborazzi runs
  Robolectric-based screenshot tests in the JVM lane and can auto-generate
  tests from Compose Previews via ComposablePreviewScanner
  (https://github.com/takahirom/roborazzi,
  https://github.com/sergio-sastre/ComposablePreviewScanner).
  Touches: Robolectric plus Roborazzi Gradle setup (record/verify tasks), seed
  previews/tests for ProjectListScreen, EditorScreen chrome, ExportSheet,
  Settings, and AiModelRequirementSheet across Dark and High Contrast Dark,
  committed golden images under `app/src/test/snapshots/`, CI verify step with
  diff artifacts, docs on the re-record workflow.
  Acceptance: Roborazzi record/verify Gradle tasks run locally and in CI; a
  deliberate 2dp padding change on a covered surface fails verification with a
  visual diff artifact; both appearance modes covered for every golden.
  Complexity: M

- [ ] 🔬🤖 P2 — Loaded-timeline scrub Macrobenchmark (many clips + overlays)
  Why: VN's most-cited 2026 user complaint is lag and sync drift with many
  overlays — exactly the workload NovaCut's shipped Macrobenchmark never
  exercises (it scrubs a blank editor), so a real-world regression would ship
  unnoticed.
  Evidence: v3.74.45 suite covers cold/warm startup and blank-editor timeline
  scrub frame timing (ROADMAP Current State); VN complaint signal:
  https://codecarbon.com/top-5-free-capcut-alternatives-for-mobile-creators-reddit-picks/
  Touches: a deterministic seeded-project fixture (for example 30 clips across
  4 tracks, 10 text overlays, 5 image overlays, keyframes plus effects)
  buildable without picker interaction (debug-only intent extra or test
  autosave JSON), a new Macrobenchmark scenario in `baselineprofile/`
  measuring scrub/zoom frame timing on the seeded project, frame-timing
  thresholds recorded as the regression baseline.
  Acceptance: the benchmark runs on the managed Pixel 6 API 36 device against
  the seeded project; P50/P90 frame times are recorded and a documented
  threshold turns regressions into gate failures; the fixture is reusable by
  future perf work.
  Complexity: M

#### P3

- [ ] 🔬🤖 P3 — Passive update check for sideload/GitHub-release installs
  Why: GitHub-release users (the only current distribution channel) have no
  way to learn about fixes; a passive, opt-in check closes the loop without
  the F-Droid anti-feature risk of a self-updater.
  Evidence: distribution is GitHub releases (repo `artifacts/`, release CI);
  no update-check code exists (no releases-API client in `app/src/main`).
  F-Droid flags self-updating/tracking as anti-features — keep it opt-in and
  metadata-only (https://f-droid.org/en/docs/Anti-Features/).
  Touches: Settings opt-in toggle (default off), OkHttp call to the GitHub
  releases API comparing `versionName` (TLS-only, no identifiers beyond the
  HTTP request itself), a dismissible update-available row linking to the
  release page (no APK download/install in-app), privacy-dashboard row for
  the network touch, build-flavor guard so F-Droid builds can compile it out.
  Acceptance: with the toggle off (default) no network call ever fires; with
  it on, a newer tag shows a non-blocking notice linking to the releases page;
  the privacy dashboard discloses the endpoint; JVM tests cover version
  comparison and opt-out behavior.
  Complexity: S

#### Appendix — Cycle 28 Sources

- Media3 ExoPlayer audio focus: https://developer.android.com/media/media3/exoplayer/audio-focus
- Easy audio focus with ExoPlayer: https://medium.com/google-exoplayer/easy-audio-focus-with-exoplayer-a2dcbbe4640e
- CapCut Trash/recovery behavior: https://www.swellai.com/blog/how-to-find-deleted-capcut-videos
- CapCut custom font import: https://capprocutapk.com/how-to-add-fonts-to-capcut/
- KineMaster features incl. font import: https://www.capcut.com/resource/kinemaster-video-editor
- VN overlay-lag community signal: https://codecarbon.com/top-5-free-capcut-alternatives-for-mobile-creators-reddit-picks/
- Media3 BitmapOverlay: https://developer.android.com/reference/androidx/media3/effect/BitmapOverlay
- Roborazzi: https://github.com/takahirom/roborazzi
- ComposablePreviewScanner: https://github.com/sergio-sastre/ComposablePreviewScanner
- APK size diff in CI: https://github.com/microsoft/android-app-size-diff
- Measuring app size in CI: https://medium.com/microsoft-mobile-engineering/measuring-android-app-size-in-ci-c6f886b88a3
- F-Droid reproducible builds: https://f-droid.org/docs/Reproducible_Builds/
- F-Droid signing-key/repro post: https://f-droid.org/en/2023/09/03/reproducible-builds-signing-keys-and-binary-repos.html
- F-Droid anti-features: https://f-droid.org/en/docs/Anti-Features/

### Researcher Queue (Cycle 29 - 2026-06-10)

Focus: legal-compliance UX, dependency supply chain, preview/export pipeline
unification, and CI static analysis — seams Cycles 1-28 did not cover. Every
item code-verified against `app/src/main`, `gradle/`, `.github/workflows/`,
`LICENSE`, and `docs/models.md`; no overlap with existing queue items (the
in-app license surface is distinct from the Cycle 1 release-checksum item and
the Cycle 24 rights ledger, which cover artifacts and user-content rights, not
bundled-dependency attribution; dependency verification is distinct from both
checksum items because it pins build-time Maven artifacts, not release outputs).

#### P1

#### P2 — trust, reliability, supply chain

- [ ] P2 — Enable Gradle dependency verification and mirror the retired-fork native AARs
  Why: the build pulls two personal-fork native AARs straight from Maven
  Central with no checksum pinning — `com.moizhassan.ffmpeg:ffmpeg-kit-16kb`
  (fork of ffmpeg-kit, whose upstream was retired Jan 2025, had binaries
  deleted from Maven Central Apr 2025, and was archived Jun 2025) and
  `io.github.kaleyravideo:android-deepfilternet`. A compromised or vanished
  fork release means arbitrary native code in every build or a broken build —
  Open Video Editor (the closest FOSS comparable) was broken exactly this way
  when ffmpeg-kit binaries vanished. `docs/models.md` pins model bytes but
  Maven artifacts resolve unpinned.
  Evidence: no `gradle/verification-metadata.xml` (verified);
  `app/build.gradle.kts:196`; https://tanersener.medium.com/saying-goodbye-to-ffmpegkit-33ae939767e1;
  https://github.com/devhyper/open-video-editor/issues/119;
  https://docs.gradle.org/current/userguide/dependency_verification.html
  Touches: run `./gradlew --write-verification-metadata sha256 help` and
  commit `gradle/verification-metadata.xml`; CI step asserting verification
  is active (a tampered-checksum negative test in docs); mirror copies of the
  exact ffmpeg-kit-16kb 6.1.1 and android-deepfilternet 0.0.8 AARs (vendored
  under `app/libs/` behind a repository-content filter, or attached to a
  GitHub release documented in `docs/dependency-maintenance.md`) with their
  SHA-256s recorded in `docs/models.md`; documented metadata-refresh
  procedure for Dependabot bumps.
  Acceptance: a build with any modified dependency artifact fails checksum
  verification; CI proves verification is enforced; both fork AARs have an
  offline mirror with recorded hashes so a Maven takedown cannot break
  release builds; Dependabot PRs include the regenerated metadata step in
  their checklist.
  Complexity: S

#### P3 — verification depth and architecture

- [ ] P3 — Add a full Android Lint CI lane with a committed baseline
  Why: CI runs unit tests, assembles, and verifies signatures/alignment but
  never runs `lint`; only the implicit fatal-only `lintVitalRelease` inside
  `assembleRelease` gates releases. A full lint pass with a committed
  baseline catches API-level misuse, missing translations, and accessibility
  regressions cheaply, and feeds the existing WCAG and localization items.
  Evidence: `.github/workflows/build.yml` (no lint step);
  `app/build.gradle.kts:103-108` (lint block only disables the crashing
  `NullSafeMutableLiveData` detector).
  Touches: `app/build.gradle.kts` lint block (`baseline =
  file("lint-baseline.xml")`, `warningsAsErrors` for selected categories,
  `abortOnError = true`), committed `app/lint-baseline.xml`, a `./gradlew
  :app:lintDebug` step in `build.yml`, HTML/SARIF report upload as CI
  artifact, baseline-refresh procedure note in `docs/dependency-maintenance.md`.
  Acceptance: CI fails on any new lint error not in the baseline; the lint
  report is downloadable per run; the baseline shrinks (never grows) without
  an explicit reviewed commit.
  Complexity: S

- [ ] P3 — CompositionPlayer evaluation spike for preview/export parity
  Why: preview (ExoPlayer `setMediaItems` + ClippingConfiguration + a
  hand-maintained preview-effect subset) and export (Transformer
  `Composition` via `EffectBuilder`) are parallel pipelines kept in sync by
  convention — the README blend-mode/opacity caveats and the
  reversed-clip preview/export mismatch are symptoms of the class of bug
  this creates. Media3 `CompositionPlayer` (experimental since 1.9, improved
  in 1.10.1 — the version already shipped) previews the same `Composition`
  object Transformer exports, which would root-cause preview/export drift
  instead of patching instances of it.
  Evidence: zero `CompositionPlayer` mentions repo-wide (verified);
  `engine/VideoEngine.kt:1536` documents the preview-effect subset exclusions;
  https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html;
  https://github.com/androidx/media/blob/release/libraries/transformer/src/main/java/androidx/media3/transformer/CompositionPlayer.java
  Touches: a time-boxed spike branch building NovaCut's existing
  `TimelineSequencePlanner` composition for CompositionPlayer playback;
  evaluate seek latency, gap/still-image handling, custom GL shader effect
  support, scopes overlay compatibility, and memory on a min-spec device;
  record the keep/adopt/partial decision with revisit criteria in a
  `docs/preview-composition-player.md` policy doc mirroring
  `docs/preview-media3-compose.md`, plus a testable policy class.
  Acceptance: a written decision doc with measured findings (seek latency,
  effect parity matrix vs `EffectBuilder`, unsupported-feature list) and
  explicit revisit criteria tied to Media3 versions; no production switch in
  this item; follow-up adoption items filed only if the spike passes.
  Complexity: M

#### Appendix — Cycle 29 Sources

- FFmpeg legal checklist: https://www.ffmpeg.org/legal.html
- ffmpeg-kit licenses wiki: https://github.com/arthenica/ffmpeg-kit/wiki/Licenses
- ffmpeg-kit retirement: https://tanersener.medium.com/saying-goodbye-to-ffmpegkit-33ae939767e1
- ffmpeg-kit-16kb fork: https://github.com/moizhassankh/ffmpeg-kit-android-16KB
- ffmpeg-kit-16kb on Maven Central: https://central.sonatype.com/artifact/com.moizhassan.ffmpeg/ffmpeg-kit-16kb
- Open Video Editor breakage precedent: https://github.com/devhyper/open-video-editor/issues/119
- AboutLibraries: https://github.com/mikepenz/AboutLibraries
- Google OSS notices guidance: https://developers.google.com/android/guides/opensource
- Gradle dependency verification: https://docs.gradle.org/current/userguide/dependency_verification.html
- Dependency verification guide: https://britter.dev/blog/2025/02/10/gradle-dependency-verification/
- Media3 1.9 (CompositionPlayer debut): https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html
- Media3 1.10 blog: https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- CompositionPlayer source: https://github.com/androidx/media/blob/release/libraries/transformer/src/main/java/androidx/media3/transformer/CompositionPlayer.java
- LumaFusion for Android: https://luma-touch.com/lumafusion-for-android/
- LumaFusion 2.3 HDR: https://digitalproduction.com/2025/07/01/lumafusion-2-3-for-android-adds-hdr-support-at-last/
- ML Kit GenAI (rejected idea): https://developers.google.com/ml-kit/genai
- Coil 3 upgrade (folded into toolchain pass): https://coil-kt.github.io/coil/upgrading_to_coil3/

## 🤖 Deep audit 2026-06-11 — deferred items

- [ ] P1 — Defer playhead reads to break 30fps whole-screen recomposition
  Why: EditorScreen collects playheadMs at top level and threads it as a plain parameter, so the entire EditorScreen + 2400-line Timeline lambda re-execute ~30x/s during playback. Fix = pass `() -> Long` providers (remembered lambdas over the State), read only inside Canvas draw scopes / small leaf composables (header time text, playhead line, ruler indicator, PreviewPanel time display). Touches ~20 read sites incl. gesture handlers and the auto-scroll loop — needs on-device smoothness verification, which is why it was deferred from the audit pass.
  Where: ui/editor/EditorScreen.kt, ui/editor/Timeline.kt (lines ~323/1713/1732, TimelineOverviewBar), ui/editor/PreviewPanel.kt

- [ ] P2 — Animated GIF/WebP overlay frames follow wall clock during export
  Why: ExportAnimatedImageOverlay starts an AnimatedImageDrawable and draws whatever frame the realtime animation is on; export is not realtime, so exported GIF timing speeds up/slows down with encode speed and is non-deterministic. Needs presentation-time-driven frame extraction (ImageDecoder frame-by-frame or Movie API) instead of a wall-clock animation.
  Where: engine/ExportAnimatedImageOverlay.kt (getBitmap)

- [ ] P3 — Engine/model enum display names are hardcoded English
  Why: KaraokeCaptionEngine.KaraokeStyle.displayName, DirectPublishEngine.Target.displayName, ColorBlindPreviewEngine.Mode.displayName, AspectRatio.label, SortMode/FilterMode.label render directly in UI but live in engine/model classes, so they bypass string resources (and the ProjectListScreen header sentences interpolating them stay half-localized). Map enum -> @StringRes at the UI boundary.
  Where: engine/KaraokeCaptionEngine.kt, engine/DirectPublishEngine.kt, engine/ColorBlindPreviewEngine.kt, model/Project.kt (AspectRatio), ui/projects/ProjectListViewModel.kt (SortMode/FilterMode), ui/projects/ProjectListScreen.kt:206-214

- [ ] P3 — ViewModel toasts are hardcoded English strings
  Why: ProjectListViewModel/EditorViewModel showToast("Restored ...", "Emptied trash ...", etc.) bypass resources; systemic sweep needed (inject @ApplicationContext getString or surface string resource IDs through the toast channel).
  Where: ui/projects/ProjectListViewModel.kt, ui/editor/EditorViewModel.kt + delegates

- [ ] P3 — contentDescription resources reused as visible button text
  Why: The 2026-06-11 string externalization reused cd_-prefixed resources (cd_check_mark, cd_auto_edit_generate) for visible text where the strings matched; translators may need different text for spoken vs visible contexts. Duplicate into purpose-named resources.
  Where: ui/editor/V369FeaturesPanel.kt, ui/editor/AutoEditPanel.kt, res/values/strings.xml

## 🤖 Improvement ideas 2026-06-11 — product, UX & depth enhancements

Net-new opportunities after de-duplicating against all 29 research cycles and
the deferred-audit list above. Each was verified absent in the current code
(the four stub engines, sidechain ducking, cloud sync, OTIO export, effect
favorites, HDR/HSL grading, live scopes, and word-level captions are already
tracked or built and are intentionally excluded here).

### Discoverability & workflow

- [ ] P1 — Unified command palette / feature search
  Why: The editor exposes 40+ effects, 25 transitions, ~14 audio effects, color tools, and a dozen AI tools across a bottom tab bar, overflow menu, and nested sub-menus. A new user cannot find "Smart Reframe" or "Denoise" without hunting. There is no global search (verified: no SearchBar/CommandPalette in ui/editor). Add a single search field (tap the title bar) that fuzzy-matches tool/effect/preset/AI-action names and dispatches the existing onAction IDs. Pairs well with progressive coachmarks for advanced tools beyond the existing 4-step FirstRunTutorial.
  Where: ui/editor/EditorScreen.kt (onAction dispatch), ui/editor/ToolPanel.kt (action-id registry), ui/editor/FirstRunTutorial.kt
  Effort: M

- [ ] P1 — Batch operations on multi-selected clips
  Why: Multi-select already exists (long-press toggles selectedClipIds: Set<String>) but the only batch action wired is delete. There is no "apply effect/grade/speed/transform to all selected" (verified: no applyEffectToMultiple* anywhere; no "Apply to all" in EffectAdjustmentPanel). Editors routinely want to slow all B-roll or push saturation across every clip; today that is copy-paste per clip. Add applyEffectToSelectedClips / applySpeedToSelected / applyTransformToSelected in ClipEditingDelegate and an "Apply to all selected" affordance in the adjustment panels.
  Where: ui/editor/ClipEditingDelegate.kt, ui/editor/EffectAdjustmentPanel.kt, ui/editor/EditorViewModel.kt
  Effort: S-M

- [ ] P2 — Live effect/preset preview thumbnails in pickers
  Why: When choosing among 40 effects the grid shows only an icon + label (verified: no per-effect preview rendering). Users apply blind, then undo. Render the selected clip's current frame through each effect (downscaled, cached) so the picker shows real before/after tiles, the way the transition and caption galleries hint at their results.
  Where: ui/editor/ToolPanel.kt (effects grid), engine/VideoEngine.kt (applyPreviewEffects already builds effect chains)
  Effort: M

### Editing depth

- [ ] P2 — Multi-track / adjustment-layer transitions
  Why: All 25 transitions are clip-to-clip on a single track (verified: AdjustmentLayer has planForClip but no transition wiring). There is no "fade to black" that crosses all layers and no title-overlay fade-in from a solid. Wire transition segments through the adjustment-layer path so a dissolve/fade can span the whole composition, not just two adjacent clips.
  Where: engine/EffectBuilder.kt (transition injection), engine/AdjustmentLayerEngine.kt, engine/VideoEngine.kt
  Effort: S-M

- [ ] P2 — Rich text styling for text overlays (parity with captions)
  Why: Captions already support word-level/karaoke styling, but the title/text-overlay system (TextEditorSheet) tops out at 10 fixed animations and flat fills — no gradient or outline fills, no per-character/word stagger (verified: stagger lives only in Caption.kt/CaptionStyleGallery, not the overlay model). Bring the caption engine's richness to text overlays: gradient/outline fills, per-character entrance stagger, and auto-fit sizing when the project aspect changes.
  Where: ui/editor/TextEditorSheet.kt, model/Project.kt (TextOverlay), engine/ExportTextOverlay.kt
  Effort: M-L

- [ ] P2 — Composition guides & platform safe-zones in the preview
  Why: No rule-of-thirds grid, title/action-safe guides, or platform UI keep-out overlays exist (verified: no safe-zone/grid code). NovaCut's audience is social-first, where TikTok/Reels caption bars and button rails cover the lower third and right edge — creators routinely place text that gets hidden behind platform UI. Add toggleable overlays in PreviewPanel: thirds grid, action/title-safe rectangles, and per-platform keep-out masks (TikTok, Reels, Shorts) driven by the existing aspect-ratio model.
  Where: ui/editor/PreviewPanel.kt, model/Project.kt (AspectRatio)
  Effort: S-M

### Platform & input

- [ ] P3 — Stylus pressure/tilt support
  Why: All gesture handlers treat a stylus as a finger (verified: no PointerType.Stylus / pressure checks in ui/). On S Pen / Pencil tablets — a premium editing surface — mask drawing, the drawing overlay, and bezier curve handles would benefit from pressure-driven feather/brush size and tilt. Read PointerType.Stylus + pressure in the relevant pointerInput blocks and map them to the active tool's primary parameter.
  Where: ui/editor/MaskEditorPanel.kt, ui/editor/DrawingOverlayPanel.kt, ui/editor/KeyframeCurveEditor.kt
  Effort: M

### Export & assets

- [ ] P1 — Export confidence: encoder detection, live ETA, stall detection
  Why: Export shows a percentage but never says which encoder will run (hardware vs software fallback), gives no real ETA, and only fails after a flat 10-minute poll timeout (verified in VideoEngine startTransformerWithPolling). On a long 4K render with no feedback the app feels hung and users force-kill it. Add: query MediaCodecList up front and tell the user "hardware H.264" vs "software (slower)"; compute a running bitrate to show a dynamic ETA; warn early if no frame has been produced in ~30s instead of waiting for the full timeout.
  Where: engine/VideoEngine.kt (startTransformerWithPolling, encoder factory), ui/export/ExportSheet.kt
  Effort: M

- [ ] P2 — Project media/asset pool: dedup, disk usage, bulk relink, reclaim
  Why: The existing "remove unused" only clears empty tracks (MediaManager) — there is no project-wide media pool. Re-importing the same source twice duplicates it in exports, missing sources must be relinked one at a time, and there is no way to see which clips eat the most disk. Add an AssetLibrary that tracks source URIs + reference counts and a panel for: duplicate-source detection, per-asset size, bulk relink of moved sources, and reclaim of orphaned managed media (voiceovers/freeze-frames/proxies).
  Where: model/Project.kt, engine/ (new AssetLibrary), ui/editor/MediaManagerPanel.kt
  Effort: M

### Media durability lane (OSS-backed, 2026-06-11)

Research basis: Kdenlive/MLT and Shotcut keep explicit project media bins with
stable asset identity, proxy/cache separation, and missing-file repair instead
of treating clip source paths as the only source of truth. Open Video Editor's
public saved-project playback failures are a concrete warning for NovaCut: a
mobile editor must persist app-owned media, verify it before playback, and give
users a project-level repair surface when storage permissions or external files
move.

- [x] P1 — Start managed-media asset sidecars for every new app-owned import
  and camera capture.
  Shipped: v3.74.73 writes `.asset.json` siblings with asset id,
  original/managed URI, media type, MIME, size, duration/dimensions when
  probeable, import/verify timestamps, and a cheap fingerprint. Cleanup deletes
  sidecars with their media and ignores sidecars during orphan sweeps.
  Next: make existing project opens backfill sidecars lazily.

- [ ] P1 — Promote `MediaAsset` into the project model and persistence layer.
  Add an asset table/manifest keyed by stable asset id. Clips should migrate to
  `assetId` references while keeping `sourceUri` as a compatibility fallback for
  older autosaves/archives. Acceptance: saved projects can reopen without
  needing the original picker grant, and project JSON contains enough metadata
  to diagnose missing assets without touching timeline clips.

- [ ] P1 — Add a project-level repair/relink flow before preview/export.
  Verify every referenced asset when opening a project and before playback or
  export. If media is missing, show a Media Manager repair state with grouped
  missing assets, original names, durations/sizes, and one bulk relink action.
  Acceptance: broken assets do not fail silently or produce black previews.

- [ ] P1 — Move imports to a first-class background ingest pipeline.
  Use WorkManager/foreground-service policy for large imports with progress,
  cancel, free-space preflight, partial cleanup, and atomic project mutation
  only after the managed asset is verified. Acceptance: force-stopping during a
  large import leaves no playable timeline clip pointing at a partial file.

- [ ] P2 — Separate originals, project assets, proxies, previews, and exports.
  Keep app-owned originals under managed media, persistent project assets under
  a project-scoped directory, proxies/previews under reclaimable cache, and
  exports in MediaStore/SAF destinations. Acceptance: storage UI can explain
  what is safe to reclaim without deleting source media.

- [ ] P2 — Add lazy full-content hashing and dedupe.
  Current sidecars carry a cheap first/last-window fingerprint to avoid blocking
  import. Add a background full SHA-256 job with size+hash dedupe, refcounts,
  and collision-safe verification before replacing duplicates.

- [ ] P2 — Harden export/share storage semantics.
  Continue using MediaStore pending rows for public exports and SAF
  `ACTION_CREATE_DOCUMENT` for user-chosen files. Reserve FileProvider URIs for
  temporary share-only handoff, never as durable saved-project references.

- [ ] P2 — Add saved-project media lifecycle tests.
  Cover import -> edit -> autosave -> process death -> reopen -> preview,
  archive export/import URI rewrite, missing asset repair, project delete
  cleanup, and proxy-cache eviction without losing originals.

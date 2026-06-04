# NovaCut Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Active roadmap for forward-looking work. Shipped work is summarized in
[COMPLETED.md](COMPLETED.md), research synthesis lives in
[RESEARCH_REPORT.md](RESEARCH_REPORT.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.44** (`versionCode` 181). Last consolidated:
2026-06-04.

> Last researched: Cycle 17 - 2026-06-04.

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

- [ ] 🔬🤖 P2 — Add Baseline Profile and Macrobenchmark coverage for launch and editor entry
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

- [ ] 🔬🤖 P2 — Add app-level memory trim policy for editor caches
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

- [ ] 🔬🤖 P2 — Add Play listing asset and privacy-disclosure release gate
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

- [ ] 🔬🤖 P2 — Add appearance-mode and contrast regression gates
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

- [ ] 🔬🤖 P2 — Add non-media document import router for plugins and interchange files
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

- [ ] 🔬🤖 P2 — Add `ApplicationExitInfo` process-death capture to diagnostic ZIP
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

- [ ] 🔬🤖 P2 — Add Preferences DataStore corruption recovery and settings-reset report
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

#### Appendix — Cycle 10 Sources

- Android DataStore file-corruption handling:
  https://developer.android.com/topic/libraries/architecture/datastore#corruption
- Android `ReplaceFileCorruptionHandler` API:
  https://developer.android.com/reference/kotlin/androidx/datastore/core/handlers/ReplaceFileCorruptionHandler

### Researcher Queue (Cycle 11 - 2026-06-04)

Focus: make the advertised sticker/GIF/image overlay lane durable and visible in
preview/export, using app-owned assets instead of fragile raw picker URIs.

#### Creator Workflow & Export Fidelity

- [ ] 🔬🤖 P1 — Add durable image/sticker overlay compositor and asset store
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

- [ ] 🔬🤖 P2 — Add Android local-network permission gate for LAN streaming destinations
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

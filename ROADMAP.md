# NovaCut Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Active roadmap for forward-looking work. Shipped work is summarized in
[COMPLETED.md](COMPLETED.md), research synthesis lives in
[RESEARCH_REPORT.md](RESEARCH_REPORT.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.35** (`versionCode` 172). Last consolidated:
2026-06-04.

> Last researched: Cycle 9 - 2026-06-04.

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

## Source Archives

- Previous full roadmap: [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- Cross-project feature-port roadmap: [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- May 25 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- Loop 6 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)

## Active Queue

| Priority | Work | Exit criteria |
|---|---|---|
| P1 | Timeline refactor | Continue after v3.74.32 by extracting clip layout and remaining gesture bodies from `Timeline.kt` into focused files with tests where practical. |
| P1 | Model activation gates | For every active AI/model dependency, keep source locator, SHA-256, license posture, delivery mode, F-Droid posture, and runtime checksum behavior current in `docs/models.md`. |
| P2 | Project color policy consumers | Wire `ProjectColorPolicy` into Settings/export confidence once the Room/autosave migration plan is ready. |
| P2 | Diagnostic ZIP timeline-shape toggle | Expose the privacy-preserving timeline-shape summary as an explicit Settings export option. |
| P2 | Dependabot grouping and dependency freshness review | Group dependency updates by toolchain risk, evaluate auto-tagging from `CHANGELOG.md` headings, and stage the current Compose BOM / Room / Kotlin freshness batch only after the editor-state migration has a clean compile baseline. |
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

- [ ] P0 — Install a global uncaught-exception handler
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

- [ ] P1 — Execute the instrumentation smoke suite on an emulator in CI
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

- [ ] P1 — Wire slip/slide editing to timeline gestures (or scope it down)
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

- [ ] 🔬🤖 P1 — Add Sharesheet-compatible incoming media routing
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

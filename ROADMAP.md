# NovaCut Roadmap

> Single source of truth for all planned work. Items above the `---` are
> existing plans; items below are research conducted 2026-06-03.

Active roadmap for forward-looking work. Shipped work is summarized in
[COMPLETED.md](COMPLETED.md), research synthesis lives in
[RESEARCH_REPORT.md](RESEARCH_REPORT.md), and detailed historical plans are
archived under [docs/archive](docs/archive/).

Current version: **v3.74.24** (`versionCode` 161). Last consolidated:
2026-06-04.

2026-06-04 research refresh: the v3.74.21 through v3.74.24 editor-state
migrations moved the AI, export, media, and compound storage slices into
`EditorAiState`, `EditorExportDomainState`, `EditorMediaState`, and
`EditorCompoundState`, leaving panel and caption storage as the remaining P1
migration lane. Maven metadata shows
Media3 1.10.1 and WorkManager 2.11.2 current, with Compose BOM 2026.05.01, Room
2.8.4, and Kotlin 2.4.0 available for deliberate review; AGP's newest observed
metadata is 9.3.0-alpha09 and should stay out of routine bumps. Focused Gradle,
full APK, release metadata, signature, zipalign, and 16 KB gates passed locally.

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

## Source Archives

- Previous full roadmap: [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- Cross-project feature-port roadmap: [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- May 25 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- Loop 6 research plan: [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)

## Active Queue

| Priority | Work | Exit criteria |
|---|---|---|
| P1 | Editor state storage migration | Move remaining `EditorState` constructor storage and `copy(...)` call sites onto the panel and caption domain slices without regressing existing editor surfaces. |
| P1 | EditorScreen panel router decomposition | Replace the large monolithic panel routing surface with smaller host components that own only their local state and callbacks. |
| P1 | Timeline refactor | Reduce `Timeline.kt` risk by extracting gesture handling, clip layout, overlays, and accessibility actions into focused files with tests where practical. |
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

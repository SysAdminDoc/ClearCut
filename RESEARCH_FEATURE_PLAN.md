# Project Research and Feature Plan

## Executive Summary

NovaCut is already a broad offline-first Android video editor with real project
persistence, timeline state, export plumbing, model-gated AI tools, release
checks, privacy surfaces, and a large regression suite. The highest-value next
work is not another broad feature list; it is closing trust gaps where shipped
UI or documentation is ahead of implementation, hardening Android platform
contracts, and turning mature scaffolds into verifiable user flows. Priority
order: fix the FileProvider camera/share grant contract, make C2PA either
signed and embedded or visibly draft-only, split direct publish from social
share handoff, ship one fully translated locale, add release provenance and
checksums, correct FCPXML/text-overlay fidelity, validate transform gestures on
device, create metadata-scrubbed archive/share paths, plan Play On-device AI
pack delivery after the required toolchain migration, and make sync a
conflict-safe local/WebDAV feature rather than a stub.

## Evidence Reviewed

- Repository state: `master` was clean and aligned with `origin/master` before
  this document was added.
- Recent shipped work reviewed with `rtk git log -10`: local network permission
  gate, durable overlay compositor, settings corruption recovery, process-exit
  diagnostics, document import router, appearance contrast gates, Play listing
  gate, memory trim policy, and C2PA draft manifest gate.
- Live project docs reviewed: `README.md`, `ROADMAP.md`,
  `PROJECT_CONTEXT.md`, `RESEARCH_REPORT.md`, `COMPLETED.md`,
  `CHANGELOG.md`, `docs/models.md`, and `docs/dependency-maintenance.md`.
- Build files reviewed: `settings.gradle.kts`, root `build.gradle.kts`,
  `app/build.gradle.kts`, and `gradle/libs.versions.toml`.
- Android metadata reviewed: `app/src/main/AndroidManifest.xml`,
  `app/src/main/res/xml/file_paths.xml`, `backup_rules.xml`,
  `data_extraction_rules.xml`, and `locales_config.xml`.
- Source reviewed with targeted reads and repository-wide search across
  `app/src/main`, `app/src/test`, and `app/src/androidTest`, including editor,
  export, AI, capture, sharing, sync, privacy, and timeline interchange paths.
- Validation run:
  - `JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'; .\gradlew.bat :app:compileDebugKotlin --offline --rerun-tasks`
  - Result: build successful.
  - `JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'; .\gradlew.bat :app:testDebugUnitTest --offline`
  - Result: build successful, 97 XML result files, 700 tests, 0 failures, 0
    errors, 0 skipped.
- Not run in this pass: emulator instrumentation, real camera capture, real
  share-sheet grants, C2PA dependency integration, store submission, or live
  platform API uploads.
- External sources reviewed:
  - Android network security configuration:
    https://developer.android.com/privacy-and-security/security-config
  - Android 16 local network behavior changes:
    https://developer.android.com/about/versions/16/behavior-changes-16#access-local-network
  - Android Photo Picker:
    https://developer.android.com/training/data-storage/shared/photopicker
  - Android selected photos access:
    https://developer.android.com/about/versions/14/changes/partial-photo-video-access
  - Android CameraX video capture:
    https://developer.android.com/media/camera/camerax/video-capture
  - Android Media3 Transformer:
    https://developer.android.com/media/media3/transformer
  - Android Auto Backup:
    https://developer.android.com/identity/data/autobackup
  - Play On-device AI:
    https://developer.android.com/google/play/on-device-ai
  - C2PA 2.4 specification:
    https://spec.c2pa.org/specifications/specifications/2.4/specs/C2PA_Specification.html
  - Content Authenticity Initiative c2pa-android:
    https://opensource.contentauthenticity.org/docs/c2pa-android/
  - C2PA/CAWG project home:
    https://cawg.io/
  - F-Droid Anti-Features:
    https://f-droid.org/docs/Anti-Features/
  - YouTube Data API `videos.insert`:
    https://developers.google.com/youtube/v3/docs/videos/insert
  - TikTok Content Posting API:
    https://developers.tiktok.com/doc/content-posting-api-get-started/
  - Instagram content publishing:
    https://developers.facebook.com/docs/instagram-api/guides/content-publishing/
  - CapCut:
    https://www.capcut.com/
  - CyberLink PowerDirector mobile:
    https://www.cyberlink.com/products/powerdirector-video-editing-app/features_en_US.html
  - KineMaster:
    https://kinemaster.com/
  - DaVinci Resolve:
    https://www.blackmagicdesign.com/products/davinciresolve
  - LumaFusion:
    https://luma-touch.com/lumafusion/
  - OpenTimelineIO:
    https://opentimelineio.readthedocs.io/

## Current Product Map

- Platform: Android app with Kotlin, Jetpack Compose, Hilt, Room, WorkManager,
  Media3, CameraX dependencies, ONNX Runtime, MediaPipe Tasks Vision, FFmpeg Kit
  16 KB fork, and local model assets.
- Current app identity: `versionName` is `3.74.55` and `versionCode` is `192`
  in `app/build.gradle.kts`.
- Android targets: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- Product posture: local-first editor with explicit model availability gates
  and privacy dashboard surfaces.
- Mature areas: project persistence, editor state, many Compose panels,
  offline export/test scaffolds, release metadata checks, model manifest checks,
  and broad unit-test coverage.
- Weak areas: several branded or roadmap features are still handoffs, stubs, or
  draft-only outputs; some README claims need call-site proof; device-only flows
  need emulator and real-device validation.
- Current roadmap direction: close platform trust gaps, wire existing
  scaffolds, and keep future AI/social/sync claims honest until dependencies
  and credentials exist.

## Feature Inventory

### Project Gallery, Templates, And Settings

- Name: Local project gallery, templates, settings, and app preferences.
- User value: gives users a durable starting point, project discovery, and
  recoverable configuration without an account.
- Entry point: main app launch, settings navigation, template surfaces.
- Main code locations: `app/src/main/java/com/novacut/ui`, `EditorViewModel.kt`,
  `SettingsStore`, Room entities/DAOs, `PrivacyDashboard.kt`, and settings UI
  panels.
- Current maturity: high for local storage and UI state; still needs broader
  locale and backup-policy proof.
- Tests/docs coverage: strong unit coverage for engines and settings recovery;
  instrumentation smoke suite exists in `NovaCutSmokeTest.kt`.
- Improvement opportunities: finish one fully translated locale, document backup
  inclusion/exclusion decisions per data class, and keep settings copy aligned
  with actual model/network behavior.

### Media Import, Picker, Camera Handoff, And Document Routing

- Name: media import, Photo Picker integration, external camera capture handoff,
  and document import routing.
- User value: lets users bring in clips, stills, and files without granting broad
  library access.
- Entry point: media picker/editor import actions.
- Main code locations: `MediaPicker.kt`, `CameraCaptureEngine.kt`,
  `DocumentImportRouter`, `AndroidManifest.xml`, `file_paths.xml`, and editor
  event handlers.
- Current maturity: mixed. The picker/document-router direction is sound, but
  external capture writes to `cacheDir/camera-captures` while `file_paths.xml`
  does not expose that path.
- Tests/docs coverage: unit tests cover routing and related validation, but the
  camera grant path requires instrumentation or real-device proof.
- Improvement opportunities: add the missing FileProvider path contract, test
  every shared artifact URI, and rename UI copy so external camera handoff is
  not mistaken for in-app CameraX recording.

### Editor Timeline And Core Editing

- Name: timeline editing with tracks, clips, cuts, trims, overlays, transitions,
  and editor state.
- User value: provides the primary video creation workflow.
- Entry point: `EditorScreen`, timeline panels, tool drawers, and edit actions.
- Main code locations: `EditorViewModel.kt`, timeline model classes, editor UI
  panels, timeline engines, undo/redo state, and transform overlay components.
- Current maturity: medium to high for stateful editing; several advanced
  editing promises are still partially wired or need gesture-level validation.
- Tests/docs coverage: broad unit coverage for timeline engines; UI smoke suite
  exists but was not run in this pass.
- Improvement opportunities: ship command-pattern undo for high-risk edits,
  implement magnetic snapping as an actual editing affordance, and validate
  transform gesture end-state persistence on device.

### Preview, Transform, Crop, And Overlay Placement

- Name: preview transform controls, crop/positioning, and overlay interaction.
- User value: lets users visually position clips, stickers, images, text, and
  generated layers.
- Entry point: preview canvas, transform overlay, clip adjustment panels.
- Main code locations: `TransformOverlay.kt`, editor preview panels, overlay
  compositor code, and related view-model state reducers.
- Current maturity: medium. Durable image overlay compositor work is present,
  but the transform overlay has separate transform and drag gesture handlers and
  lacks an obvious transform-end callback for one path.
- Tests/docs coverage: code-level tests cover compositor behavior; real pointer
  behavior needs UI/device validation.
- Improvement opportunities: centralize transform gesture state, call a single
  end-state persistence path, and add Compose UI tests for drag, pinch, rotate,
  and resize handles.

### Text, Captions, Translation, SDH, And Accessibility Text

- Name: text overlays, captions, subtitle export/burn, SDH captions, and planned
  caption translation.
- User value: improves discoverability, accessibility, and social reach.
- Entry point: caption/text panels and export settings.
- Main code locations: caption engines, `AiToolRequirements.kt`, editor text
  overlay state, subtitle export paths, `TimelineExchangeEngine.kt`, and
  `TimelineExchangeValidator.kt`.
- Current maturity: high for some local text/caption state, medium for export
  fidelity, low for translation activation.
- Tests/docs coverage: model gating is documented in `docs/models.md`; tests
  exist for many engines, but FCPXML overlay fidelity is inconsistent.
- Improvement opportunities: fix FCPXML text-overlay export claims, split SDH
  captions from audio description tracks, and activate translation only after
  offline model packaging, license, and review UI are complete.

### Effects, Color, Transitions, Masks, And AI-Assisted Visual Tools

- Name: visual effects, color/effect packs, masks, background removal, object
  erase, AI upscaling, segmentation, and frame interpolation plans.
- User value: gives creators high-leverage edits that are difficult to do by
  hand on mobile.
- Entry point: effects drawer, AI tools drawer, model manager, and export
  preview.
- Main code locations: `engine` package, AI tool engines, model manifest
  helpers, `docs/models.md`, effect pack code, and render/export paths.
- Current maturity: varied. Several tools have real offline model gates, while
  larger neural features remain planned or dependency-gated.
- Tests/docs coverage: strong model manifest documentation; selected engines
  have tests. Real quality/performance testing remains limited.
- Improvement opportunities: prioritize segmentation quality and tap-to-select
  over adding more model families, then use Play On-device AI as the future
  delivery path once the AGP and Play Core requirements are met.

### Audio, Voiceover, Beat Tools, And Mix Controls

- Name: voiceover, audio mixer, beat sync, ducking, cleanup, and audio export.
- User value: enables complete social/video production without leaving the app.
- Entry point: audio panels, recording flows, mixer controls, and export.
- Main code locations: audio engines, editor state, model manifest, FFmpeg and
  Media3 export paths.
- Current maturity: medium. There is meaningful scaffolding and some shipped
  behavior, but pro-level mix verification and accessibility audio-description
  separation need work.
- Tests/docs coverage: engine tests exist; real device microphone, latency, and
  export sync validation need stronger coverage.
- Improvement opportunities: add latency test fixtures, separate SDH from audio
  description, and keep audio cleanup model gates explicit.

### Export, Render, GIF, Frame Capture, And Release Outputs

- Name: local render/export, subtitle burn, GIF/frame capture, export sheets,
  release artifact checks, and Media3/FFmpeg integrations.
- User value: produces final files and shareable outputs.
- Entry point: export sheet and share/export actions.
- Main code locations: export engines, Media3 Transformer paths, FFmpeg paths,
  `C2paExportEngine.kt`, `DirectPublishEngine.kt`, release metadata scripts,
  and `.github/workflows/build.yml`.
- Current maturity: medium to high for local export scaffolding; lower for
  signed provenance and release artifact provenance.
- Tests/docs coverage: release metadata tests and many export tests exist; full
  signed APK artifact provenance and real C2PA embedding are not yet present.
- Improvement opportunities: add checksums/provenance to release artifacts,
  wire C2PA signing with dependency-backed tests, and test every URI shared from
  exported outputs.

### Timeline Interchange, Archives, And Project Portability

- Name: OTIO, FCPXML, EDL, project archive/share, relink, and portability tools.
- User value: lets users back up work and move projects into other editors.
- Entry point: export/interchange/archive actions.
- Main code locations: `TimelineExchangeEngine.kt`,
  `TimelineExchangeValidator.kt`, project archive engines, relink engines, and
  editor export state.
- Current maturity: medium. OTIO direction is credible, EDL lossy behavior is
  expected, and FCPXML currently appears to drop text overlays while validation
  treats FCPXML as preserving them.
- Tests/docs coverage: interchange tests exist but need coverage for overlays
  and round-trip warnings.
- Improvement opportunities: align validators with actual exporters, add
  fixtures for text/transition/effect preservation, and create scrubbed archive
  variants for sharing.

### Project Persistence, Recovery, Relink, Backup, And Diagnostics

- Name: Room-backed project persistence, autosave/recovery, relink, backup
  rules, settings recovery, process-exit diagnostics, and privacy dashboard.
- User value: protects work and gives understandable diagnostics when something
  fails.
- Entry point: app launch, project open, settings, privacy dashboard, and
  recovery prompts.
- Main code locations: Room database, project repository, recovery engines,
  backup XML, diagnostics engines, `PrivacyDashboard.kt`, and settings stores.
- Current maturity: high for local diagnostic posture; backup/disclosure
  details need continued proof.
- Tests/docs coverage: strong unit coverage for many recovery and diagnostic
  paths; backup policy needs current Android behavior validation.
- Improvement opportunities: map every stored data type to backup/data-safety
  handling, add local-first product-health ledger, and expose scrubbed export
  paths for shared archives.

### Privacy, Data Safety, Network Controls, And Telemetry

- Name: privacy dashboard, network permission gates, diagnostics, data safety,
  and planned telemetry choices.
- User value: makes offline-first trust claims verifiable.
- Entry point: privacy dashboard, settings, network-gated features, release
  docs.
- Main code locations: `PrivacyDashboard.kt`, `PrivacyDashboardPanel.kt`,
  `AndroidManifest.xml`, network engines, diagnostics, and docs.
- Current maturity: good local-first foundation. Network cleartext config is not
  currently declared, and future telemetry rows are intentionally disabled.
- Tests/docs coverage: privacy panels and network gates have tests/docs; no
  live store declaration validation was run in this pass.
- Improvement opportunities: add `networkSecurityConfig`, local network
  permission probes for Android 16 behavior, data-safety claim tests, and
  local-only product-health ledger before any external telemetry SDK.

### Direct Publish And Social Sharing

- Name: platform share handoff and future authenticated platform upload.
- User value: reduces friction after export.
- Entry point: export/share actions and direct publish UI.
- Main code locations: `DirectPublishEngine.kt`, FileProvider configuration,
  export UI, and platform package detection.
- Current maturity: low to medium. The current engine is a validated
  `ACTION_SEND` handoff with targeted package selection, not API-backed upload.
- Tests/docs coverage: local validation can be tested; authenticated YouTube,
  TikTok, and Instagram publishing is not implemented.
- Improvement opportunities: rename the current flow as share handoff, create
  separate OAuth/API upload modules behind explicit credential gates, and add
  compliance notes for each platform.

### Stock Assets, Templates, Plugins, And Marketplace-Like Packs

- Name: stock assets, effect packs, templates, plugins, and external providers.
- User value: speeds up editing with reusable creative assets.
- Entry point: template/asset/effect panels.
- Main code locations: asset engines, template repositories, effect pack code,
  plugin or provider contracts, and UI panels.
- Current maturity: medium for local pack concepts; low for external provider
  ingestion.
- Tests/docs coverage: partial tests likely exist for local packs; no verified
  terms/attribution/rate-limit provider contract was found.
- Improvement opportunities: require license, attribution, cache, and rate-limit
  metadata before any provider ships.

### Live Streaming And Local Network Features

- Name: live/stream-related features and local network capability gates.
- User value: enables local device workflows and possible live creator use
  cases.
- Entry point: network-gated feature surfaces.
- Main code locations: manifest permissions, local network gate code, privacy
  dashboard, and network engines.
- Current maturity: early. Android 16 behavior changes make this area sensitive
  because target 36 apps need `NEARBY_WIFI_DEVICES` for local network sockets,
  while target 37+ moves to `ACCESS_LOCAL_NETWORK`.
- Tests/docs coverage: recent local network gate work exists; live device
  validation remains needed.
- Improvement opportunities: add a version-aware network permission matrix,
  explicit denial UX, and instrumentation around LAN discovery failure modes.

### Cross-Device Sync And External Folder/WebDAV Planning

- Name: planned project sync, folder sync, and WebDAV sync.
- User value: helps users move projects across devices without a central cloud
  account.
- Entry point: sync settings or project actions once implemented.
- Main code locations: `ProjectSyncEngine.kt`, project repository, media archive
  paths, storage access framework integration, and conflict model.
- Current maturity: stub. `plan(...)` returns null and `sync(...)` reports that
  the backend is not implemented.
- Tests/docs coverage: minimal for real sync behavior.
- Improvement opportunities: start with dry-run planning, conflict records,
  deterministic file manifests, and local folder sync before WebDAV.

### Release Pipeline, Store Readiness, And Dependency Maintenance

- Name: Gradle build, offline dependency verification, Play listing checks,
  release metadata checks, signatures, zipalign, APK artifact upload, and
  dependency monitoring.
- User value: gives users safer releases and maintainers predictable delivery.
- Entry point: CI and release scripts.
- Main code locations: `.github/workflows/build.yml`,
  `.github/dependabot.yml`, Gradle files, `docs/dependency-maintenance.md`,
  release verification scripts, and metadata files.
- Current maturity: strong for APK build/test/signature metadata; missing
  checksums, artifact provenance, and signed supply-chain attestations.
- Tests/docs coverage: CI has build/test/lint/instrumentation/release jobs;
  this pass only ran local compile and unit tests.
- Improvement opportunities: add SHA-256 checksums, SLSA or GitHub artifact
  attestations where feasible, and cosign/minisign documentation for releases.

### Accessibility, Appearance, Large Screens, And Device QA

- Name: accessibility checks, contrast gates, dynamic type, large-screen editor
  layout, smoke tests, and baseline profile.
- User value: makes the editor usable across devices and user needs.
- Entry point: all major UI surfaces.
- Main code locations: `NovaCutSmokeTest.kt`, Compose UI code, appearance
  settings, accessibility helpers, baseline profile module, and screenshots.
- Current maturity: good automated foundation. The emulator/device evidence
  still needs regular execution, especially after UI changes.
- Tests/docs coverage: instrumentation smoke tests enable accessibility checks;
  not run in this pass.
- Improvement opportunities: run a WCAG 2.2 AA audit of editor surfaces,
  capture screenshots at phone/tablet/font-scale variants, and keep large-screen
  layout issues in the roadmap until verified.

## Competitive and Ecosystem Research

### CapCut

- Product/source: https://www.capcut.com/
- Notable capabilities: consumer-first mobile/video editing, auto captions,
  background removal, templates, social-ready formats, AI-assisted tooling, and
  fast sharing workflows.
- What learn: guided creator workflows matter more than raw feature count;
  high-value AI features should surface as simple editor actions with preview
  and rollback.
- What avoid: cloud-first ambiguity, broad AI claims without local model
  disclosure, and template flows that obscure media provenance or licensing.

### CyberLink PowerDirector Mobile

- Product/source:
  https://www.cyberlink.com/products/powerdirector-video-editing-app/features_en_US.html
- Notable capabilities: mobile NLE tools, effects, stock content, stabilization,
  templates, titles, transitions, and direct social outputs.
- What learn: professional-feeling mobile editors invest in asset packs,
  stabilization, export presets, and clear upgrade paths.
- What avoid: external stock integrations without a complete terms,
  attribution, and cache policy.

### KineMaster

- Product/source: https://kinemaster.com/
- Notable capabilities: layered editing, chroma key, asset store, templates,
  speed controls, and mobile-first creator workflows.
- What learn: layer manipulation and reusable assets should be easy to reach
  from the editor, not buried in settings.
- What avoid: marketplace-like claims before provider rules, user licenses, and
  offline availability are proven.

### DaVinci Resolve

- Product/source: https://www.blackmagicdesign.com/products/davinciresolve
- Notable capabilities: professional edit, cut, color, Fusion, Fairlight,
  delivery, media management, proxy workflows, and timeline interchange.
- What learn: interop, media management, proxy workflows, color consistency, and
  audio tooling are trust-building professional features.
- What avoid: copying desktop complexity onto mobile without constrained
  workflows and strong defaults.

### LumaFusion

- Product/source: https://luma-touch.com/lumafusion/
- Notable capabilities: mobile-first multitrack editing, trim tools, external
  media workflows, effects, titles, audio mixing, and professional export.
- What learn: phone/tablet NLEs win by making multitrack editing fast,
  predictable, and touch-native.
- What avoid: relying on undocumented gesture behavior; every touch editing
  gesture needs device proof.

### OpenTimelineIO

- Product/source: https://opentimelineio.readthedocs.io/
- Notable capabilities: open timeline interchange with adapters and schema
  versioning.
- What learn: OTIO can be the honest interchange core, with FCPXML/EDL presented
  as adapters with explicit loss reports.
- What avoid: validators that promise preservation for a format when the
  exporter does not actually serialize the data.

### Android Platform And Play Delivery

- Product/source: Android developer documentation for network security,
  Photo Picker, selected-photo access, Media3 Transformer, CameraX, Auto Backup,
  Android 16 local network behavior, and Play On-device AI.
- Notable capabilities: platform permission hardening, media processing rules,
  user-selected media grants, local network permission changes, and AI pack
  delivery through Play for AGP 8.8+ projects.
- What learn: platform compliance work should be treated as product work because
  it directly affects trust, launch success, and store eligibility.
- What avoid: targeting current SDKs while leaving cleartext, URI grants, local
  network, backup, and model delivery contracts implicit.

### YouTube, TikTok, And Instagram Publishing APIs

- Product/source:
  https://developers.google.com/youtube/v3/docs/videos/insert,
  https://developers.tiktok.com/doc/content-posting-api-get-started/,
  https://developers.facebook.com/docs/instagram-api/guides/content-publishing/
- Notable capabilities: authenticated uploads, quota/rate limits, platform
  review, OAuth, metadata requirements, and account/business constraints.
- What learn: true direct publishing is an integration program, not an intent
  wrapper.
- What avoid: labeling Android share intents as direct upload or implying
  account-backed posting before credentials, API review, quota handling, and
  user consent are in place.

### C2PA And CAWG

- Product/source: C2PA specification, c2pa-android documentation, and CAWG
  guidance.
- Notable capabilities: content provenance manifests, assertions, signing,
  embedding, and training/data-mining assertions.
- What learn: draft sidecars are useful for development, but user-facing trust
  requires signed embedded manifests and clear status labels.
- What avoid: presenting unsigned JSON sidecars as content credentials.

### F-Droid And Open Distribution Signals

- Product/source: https://f-droid.org/docs/Anti-Features/
- Notable capabilities: anti-feature labels for tracking, non-free dependencies,
  non-free network services, ads, and source availability.
- What learn: NovaCut's local-first posture can become a distribution advantage
  if every SDK, model, provider, and network dependency is disclosed.
- What avoid: introducing telemetry, stock providers, or proprietary model
  delivery without a user-visible anti-feature/data-safety review.

## Highest-Value New Features

### FileProvider Grant Contract Verifier

- Title: FileProvider grant contract verifier for capture, exports, archives,
  and share handoffs.
- User problem solved: prevents camera/share/export flows from failing at launch
  because an internally generated file path cannot be granted through
  FileProvider.
- Evidence: `MediaPicker.kt` writes pending video captures under
  `cacheDir/camera-captures`, but `file_paths.xml` exposes `frames/` and
  `files/media/` paths rather than `camera-captures/`.
- Proposed behavior: a single contract enumerates every internally generated
  share/capture/archive path, asserts it is covered by FileProvider XML, and
  runs an instrumentation probe that creates and grants each URI.
- Implementation areas: `file_paths.xml`, `MediaPicker.kt`, export/share
  engines, archive engines, instrumentation tests, and release gates.
- Data model/API/UI implications: no data-model change. UI should show a clear
  capture failure message if a device camera rejects the grant.
- Risks/edge cases: OEM camera apps may require persisted grants or reject
  video capture destinations; API behavior can vary across Android releases.
- Verification plan: unit test the path matrix, instrumentation-test
  `FileProvider.getUriForFile` for each path, launch an external capture intent
  on emulator or device, and verify returned media cleanup.
- Estimated complexity: M.
- Priority: P0.

### Signed Embedded Content Credentials

- Title: signed embedded C2PA export manifests with draft-only fallback.
- User problem solved: gives users trustworthy provenance without overclaiming
  unsigned sidecars.
- Evidence: `C2paExportEngine.kt` builds C2PA-shaped JSON and checks for
  c2pa/proofmode classes, but no Gradle dependency is present and signing is not
  implemented.
- Proposed behavior: add a real C2PA Android integration path, configure local
  test credentials, embed manifests into supported exports, and label every
  unsupported output as "draft sidecar" or "unavailable".
- Implementation areas: `C2paExportEngine.kt`, Gradle dependencies,
  `docs/models.md` or provenance docs, export UI, release checks, fixtures, and
  tests.
- Data model/API/UI implications: add provenance status fields to export result
  objects; UI needs separate labels for signed, draft sidecar, unavailable, and
  failed.
- Risks/edge cases: dependency availability, signing-key custody, media format
  support, Android binary size, and CAWG/C2PA schema changes.
- Verification plan: fixture export embeds a signed manifest, C2PA reader
  validates it, unsigned fallback test remains clear, and release docs list
  credentials used only for testing.
- Estimated complexity: L.
- Priority: P1.

### Honest Platform Publishing

- Title: split share handoff from authenticated API publishing.
- User problem solved: users know whether NovaCut is opening the platform share
  sheet or uploading through an account-backed API.
- Evidence: `DirectPublishEngine.kt` returns `Method.SHARE_INTENT` and has no
  OAuth/API upload implementation; platform API docs show real uploads require
  credentials, quota handling, and platform-specific review.
- Proposed behavior: rename existing flow to "Share to platform", keep it as a
  local intent handoff, and create separate disabled API upload modules for
  YouTube, TikTok, and Instagram until credentials and review status exist.
- Implementation areas: `DirectPublishEngine.kt`, export UI, strings,
  platform-specific modules, privacy dashboard, account settings, and tests.
- Data model/API/UI implications: add publishing capability states: unavailable,
  share handoff, authenticated upload configured, upload queued, upload failed.
- Risks/edge cases: OAuth token storage, platform policy changes, upload quotas,
  privacy disclosures, metadata defaults, and user expectations around failed
  uploads.
- Verification plan: unit-test share handoff intents, mock API clients for
  future upload modules, document credential gates, and run a live sandbox upload
  only after platform approval.
- Estimated complexity: L for real API publishing, S for the honest split.
- Priority: P1.

### First Fully Translated Locale

- Title: ship one complete translated locale with locale and layout tests.
- User problem solved: validates that NovaCut can serve non-English users and
  catches string/layout assumptions early.
- Evidence: `locales_config.xml` lists only `en`, and no `values-*` localized
  resource directories were found.
- Proposed behavior: choose one priority locale, translate all user-facing
  strings, add `locale_config`, run pseudo-localization/long-string checks, and
  verify editor, export, settings, and privacy flows.
- Implementation areas: `strings.xml`, new `res/values-<locale>/strings.xml`,
  `locales_config.xml`, Compose UI screenshot tests, and docs.
- Data model/API/UI implications: no data-model change. UI must avoid fixed
  widths that break translated text.
- Risks/edge cases: machine translation quality, legal/privacy copy accuracy,
  long strings in compact panels, and model/tool names that should not be
  translated.
- Verification plan: string completeness check, pseudo-locale run, instrumentation
  smoke test with selected locale, and manual screenshot review at large font.
- Estimated complexity: M.
- Priority: P1.

### Release Provenance And Checksums

- Title: release artifact checksums and provenance.
- User problem solved: users and maintainers can verify downloaded APKs and
  trace artifacts back to CI inputs.
- Evidence: `.github/workflows/build.yml` builds, verifies, signs, zipaligns,
  and uploads artifacts, but no checksum or artifact-attestation step was found.
- Proposed behavior: generate SHA-256 checksums for release APKs, attach them to
  GitHub releases, add provenance/attestation where available, and document
  verification steps.
- Implementation areas: CI release job, release scripts, `README.md`,
  `CHANGELOG.md`, and release checklist docs.
- Data model/API/UI implications: no app data change.
- Risks/edge cases: tag-only release behavior, secret availability, reproducible
  build limitations, and signing-key policy.
- Verification plan: tag dry-run or workflow dispatch in a test branch, validate
  checksums match uploaded artifacts, and test documented verification command.
- Estimated complexity: M.
- Priority: P2.

### Metadata-Scrubbed Share And Archive Paths

- Title: explicit original-media metadata disclosure and scrubbed share/archive
  variants.
- User problem solved: users can decide whether to preserve or strip sensitive
  metadata before sharing archives or exported media.
- Evidence: the roadmap tracks metadata disclosure and scrubbed archive/share
  paths; external distribution and Play data-safety rules make this a trust
  feature.
- Proposed behavior: add export/archive options for preserve metadata,
  scrub metadata, and disclose original metadata, with defaults biased toward
  safe sharing.
- Implementation areas: archive engine, export engine, media metadata readers,
  privacy dashboard, export UI, and tests.
- Data model/API/UI implications: export settings gain metadata mode; archive
  manifest records metadata handling.
- Risks/edge cases: removing metadata can alter workflows that rely on camera
  timestamps or rotation tags; some containers may retain side channels.
- Verification plan: fixture media with EXIF/location/device tags, export both
  modes, assert scrubbed output has expected fields removed and preserved output
  is labeled.
- Estimated complexity: M.
- Priority: P2.

### Model Pack Delivery Plan

- Title: Play On-device AI model pack delivery after toolchain migration.
- User problem solved: large optional AI models can be delivered without bloating
  every install and without hiding model provenance.
- Evidence: current `docs/models.md` already separates active and planned
  models with hashes; Play On-device AI requires AGP 8.8+, Play Core 2.1.0+,
  R8 8.10.21+, and supports AI packs in beta.
- Proposed behavior: keep current local asset gates, add a migration plan for
  AGP/R8/Play Core prerequisites, then package selected future models as AI
  packs with license, hash, and fallback status.
- Implementation areas: Gradle version catalog, model manifest, Play delivery
  config, model manager, `docs/models.md`, and tests.
- Data model/API/UI implications: model state must distinguish bundled,
  downloaded AI pack, missing, corrupt, and unsupported device.
- Risks/edge cases: beta platform behavior, Play-only distribution tradeoffs,
  F-Droid anti-feature impact, model license constraints, and offline install
  expectations.
- Verification plan: dependency migration build, install-time/fast-follow pack
  tests, missing-pack fallback tests, and docs for non-Play builds.
- Estimated complexity: XL.
- Priority: P2.

### Conflict-Safe Local And WebDAV Sync

- Title: deterministic project sync planner for folder and WebDAV targets.
- User problem solved: users can move projects across devices without losing
  edits or relying on a proprietary cloud.
- Evidence: `ProjectSyncEngine.kt` is currently a stub that returns no plan and
  reports the backend is not implemented.
- Proposed behavior: build dry-run sync planning first, then local-folder sync,
  then WebDAV. Every operation should produce a manifest, conflict list, and
  recoverable journal before writes.
- Implementation areas: `ProjectSyncEngine.kt`, project archive manifests, media
  relink engine, storage access framework integration, WebDAV client, settings,
  and tests.
- Data model/API/UI implications: add sync target records, file manifests,
  conflict records, sync journal, and last-success metadata.
- Risks/edge cases: partial writes, duplicate media, renamed projects, clock
  skew, large files, deleted originals, auth failures, and WebDAV server
  differences.
- Verification plan: deterministic fixture directories, dry-run snapshots,
  conflict simulations, interrupted sync recovery, and WebDAV mock-server tests.
- Estimated complexity: XL.
- Priority: P3.

## Existing Feature Improvements

### Camera Capture Path Coverage

- Current behavior: camera handoff can create pending captures in
  `cacheDir/camera-captures`.
- Problem: `file_paths.xml` does not expose that cache path, so
  `FileProvider.getUriForFile` can throw before the camera app launches.
- Recommended change: add a named cache path for camera captures and a contract
  test covering every `FileProvider` URI-producing feature.
- Code locations likely affected: `file_paths.xml`, `MediaPicker.kt`,
  `NovaCutSmokeTest.kt`, and any share/export URI helpers.
- Backward compatibility concerns: preserve existing path names for old shared
  artifacts.
- Verification plan: unit matrix plus instrumentation launch test.
- Estimated complexity: S.
- Priority: P0.

### FCPXML Text Overlay Fidelity

- Current behavior: `EditorViewModel.exportToFcpxml()` validates text overlays
  as if FCPXML can preserve them, but `TimelineExchangeEngine.exportToFcpxml`
  takes tracks, project name, and frame rate only.
- Problem: FCPXML exports can silently drop text overlays while the validator
  does not warn.
- Recommended change: either serialize text overlays into FCPXML or update the
  validator/UI to report text overlay loss for FCPXML.
- Code locations likely affected: `TimelineExchangeEngine.kt`,
  `TimelineExchangeValidator.kt`, `EditorViewModel.kt`, and interchange tests.
- Backward compatibility concerns: adding serialized text overlays changes
  exported FCPXML structure; warning-only behavior changes user expectations.
- Verification plan: fixture timeline with text overlay, export FCPXML, assert
  overlay exists or warning is shown.
- Estimated complexity: M.
- Priority: P1.

### Transform Overlay Gesture Persistence

- Current behavior: `TransformOverlay.kt` has separate transform and drag
  pointer handlers; drag calls end-state handling, while transform gestures do
  not obviously call `onTransformEnded`.
- Problem: pinch/rotate/zoom edits may preview correctly but fail to persist or
  merge into undo history consistently.
- Recommended change: centralize gesture state and guarantee end-state callback
  for every transform path.
- Code locations likely affected: `TransformOverlay.kt`, editor view-model
  transform reducers, undo history, and Compose UI tests.
- Backward compatibility concerns: existing saved projects should not change;
  only interaction semantics should improve.
- Verification plan: Compose UI tests for drag, pinch, rotate, and combined
  gestures plus manual device verification.
- Estimated complexity: M.
- Priority: P1.

### C2PA Draft Labeling

- Current behavior: draft C2PA-shaped sidecars exist, while signed embedded
  manifests are unavailable without a dependency and signer bridge.
- Problem: provenance UI/docs can become misleading if draft outputs are not
  clearly separated from signed credentials.
- Recommended change: enforce status labels in export results and docs:
  signed embedded, draft sidecar, unavailable, or failed.
- Code locations likely affected: `C2paExportEngine.kt`, export UI, strings,
  docs, and tests.
- Backward compatibility concerns: existing sidecar filenames can remain, but
  labels should change.
- Verification plan: tests for every status and fixture docs showing exact copy.
- Estimated complexity: S for labeling, L for real signing.
- Priority: P1.

### Direct Publish Naming

- Current behavior: direct publish is an `ACTION_SEND` share-intent handoff.
- Problem: users may expect authenticated platform upload.
- Recommended change: rename current flow to share handoff and keep future API
  uploads separate behind explicit credential gates.
- Code locations likely affected: `DirectPublishEngine.kt`, export UI, strings,
  README feature table, and privacy dashboard.
- Backward compatibility concerns: no file or database migration required.
- Verification plan: string tests, intent tests, and docs scan for overclaims.
- Estimated complexity: S.
- Priority: P1.

### External Camera Handoff Naming

- Current behavior: app strings and docs can imply in-app camera recording,
  while the active flow uses an external `CaptureVideo` activity result and
  `CameraCaptureEngine.kt` remains CameraX scaffolding.
- Problem: expectation mismatch and higher bug risk around camera permissions,
  teleprompter, and capture lifecycle.
- Recommended change: label the current feature as external camera handoff and
  create a separate gated CameraX recorder milestone.
- Code locations likely affected: `MediaPicker.kt`, `CameraCaptureEngine.kt`,
  strings, README, roadmap, and tests.
- Backward compatibility concerns: none for project files.
- Verification plan: UI copy scan and emulator/device capture proof.
- Estimated complexity: S for naming, L for CameraX recorder.
- Priority: P2.

### Network Security Configuration

- Current behavior: the manifest requests `INTERNET` but no explicit
  `networkSecurityConfig` or `usesCleartextTraffic` declaration was found.
- Problem: cleartext behavior is implicit and trust-sensitive.
- Recommended change: add a network security config that blocks cleartext by
  default, then explicitly allow only development endpoints if needed in debug
  variants.
- Code locations likely affected: `AndroidManifest.xml`,
  `res/xml/network_security_config.xml`, debug manifest overlays, and tests.
- Backward compatibility concerns: any existing HTTP-only integration must be
  migrated or debug-gated.
- Verification plan: manifest merge test, cleartext negative test, and debug
  exception test if required.
- Estimated complexity: S.
- Priority: P2.

### Android Local Network Matrix

- Current behavior: recent local network permission gate work exists.
- Problem: Android 16 behavior differs by target SDK: target 36 uses
  `NEARBY_WIFI_DEVICES` for local network protections, while target 37+ uses
  `ACCESS_LOCAL_NETWORK`.
- Recommended change: maintain a version-aware permission matrix, denial UX, and
  test fixtures for local discovery/connect failures.
- Code locations likely affected: manifest, network gate code, privacy
  dashboard, strings, and tests.
- Backward compatibility concerns: avoid prompting for permissions on devices or
  targets that do not require them.
- Verification plan: API-level matrix tests and at least one Android 16 device
  or emulator pass when available.
- Estimated complexity: M.
- Priority: P2.

### README Claim Audit

- Current behavior: README is large and contains some feature descriptions that
  appear ahead of implementation details tracked in ROADMAP.
- Problem: feature overclaims weaken trust and create false release readiness.
- Recommended change: create a claim-to-callsite audit for README feature
  bullets and downgrade any stub/handoff/planned item.
- Code locations likely affected: `README.md`, `ROADMAP.md`,
  `PROJECT_CONTEXT.md`, and docs tests.
- Backward compatibility concerns: no runtime impact.
- Verification plan: docs grep for known sensitive phrases and link each claim
  to source/tests.
- Estimated complexity: M.
- Priority: P2.

### Stock Provider Contract

- Current behavior: stock assets/providers are roadmap concepts with no complete
  provider terms/attribution/rate-limit contract found in this pass.
- Problem: external assets can introduce license, attribution, privacy, and
  network-policy risk.
- Recommended change: require provider metadata before enabling any provider:
  license, attribution, cache TTL, rate limits, offline behavior, and data
  disclosed.
- Code locations likely affected: provider interfaces, asset cache, privacy
  dashboard, strings, and docs.
- Backward compatibility concerns: local-only asset packs should keep working.
- Verification plan: provider contract tests and a blocked-provider fixture.
- Estimated complexity: M.
- Priority: P2.

### SDH And Audio Description Separation

- Current behavior: SDH captions and audio-description concepts are both present
  in docs/roadmap language.
- Problem: SDH captions are text tracks for deaf/hard-of-hearing users, while
  audio description is a separate audio track; combining claims can mislead
  users.
- Recommended change: split feature names, storage, export settings, and tests
  for SDH captions versus verified audio-description tracks.
- Code locations likely affected: caption/audio engines, export settings,
  strings, README, and tests.
- Backward compatibility concerns: exported project metadata should keep older
  caption data readable.
- Verification plan: fixture exports with captions only, audio-description only,
  and both.
- Estimated complexity: M.
- Priority: P2.

### Product-Health Ledger Before Telemetry

- Current behavior: privacy dashboard labels future telemetry providers as
  disabled.
- Problem: external telemetry would change data-safety posture; local
  diagnostics can provide value without network disclosure.
- Recommended change: add a local-first product-health ledger for crashes,
  export failures, model failures, and performance warnings before any SDK.
- Code locations likely affected: diagnostics engines, privacy dashboard,
  settings, local storage, export/model error paths, and tests.
- Backward compatibility concerns: ledger retention and deletion must follow
  privacy settings.
- Verification plan: local event fixture, retention test, delete-all-data test,
  and no-network test.
- Estimated complexity: M.
- Priority: P2.

### Caption Translation Activation

- Current behavior: caption translation is planned and dependency-gated.
- Problem: UI or docs can imply translation exists before model delivery,
  review UI, and quality controls are ready.
- Recommended change: keep translation disabled until offline model packaging,
  language-pair metadata, review/edit UI, and export labeling are in place.
- Code locations likely affected: `AiToolRequirements.kt`, caption translation
  engine, model manifest, UI strings, export metadata, and tests.
- Backward compatibility concerns: existing captions remain source-language
  captions.
- Verification plan: missing-model gate test, supported-language fixture,
  editable translation review test, and export round-trip.
- Estimated complexity: L.
- Priority: P3.

### Proxy Workflow

- Current behavior: proxy workflow is a roadmap item.
- Problem: high-resolution editing on mobile will remain brittle without proxy
  generation, relink, and export-original safeguards.
- Recommended change: add proxy generation and use proxies in preview while
  preserving original media for final export.
- Code locations likely affected: media repository, export engine, preview
  engine, cache management, project schema, and UI.
- Backward compatibility concerns: project files need to tolerate missing or
  stale proxy files.
- Verification plan: fixture with 4K source, proxy generation, missing-proxy
  regeneration, and final export from original.
- Estimated complexity: XL.
- Priority: P3.

## Reliability, Security, Privacy, And Data Safety

- Add a `FileProvider` path matrix and instrumentation grant test before
  shipping more capture/share/export surfaces.
- Add explicit `networkSecurityConfig` and default cleartext blocking.
- Keep Android 16 local network behavior documented in code and tests, including
  target 36 and target 37+ differences.
- Map each stored data class to backup behavior, data-safety category, retention
  behavior, and delete/export user controls.
- Keep future telemetry SDKs disabled until a local product-health ledger proves
  what data is useful and what can remain on device.
- Require release checksums and provenance for APK artifacts.
- Require signed embedded C2PA proof before user-facing "content credentials"
  copy; draft sidecars should remain clearly labeled.
- Require provider contracts for stock assets and templates before enabling any
  network-backed provider.
- Keep F-Droid anti-feature implications visible for telemetry, non-free
  network services, proprietary SDKs, stock providers, and Play-only AI packs.

## UX, Accessibility, And Trust

- Run the existing Compose accessibility smoke suite on emulator before
  claiming readiness for major UI surfaces.
- Add a WCAG 2.2 AA editor audit focused on timeline controls, preview handles,
  export sheets, settings, privacy dashboard, and model-gated tools.
- Test phone, foldable/tablet, landscape, large font, display size, dark mode,
  and high-contrast variants.
- Keep UI copy precise for handoff versus upload, draft sidecar versus signed
  provenance, external camera versus in-app recording, SDH captions versus audio
  description, and planned AI tools versus available models.
- Add screenshot evidence for core flows after large UI changes.
- Avoid adding more editor panels until the existing panel density is verified
  under large font and narrow width.

## Architecture And Maintainability

- Keep `docs/models.md` as the authoritative model-gate source and require hash,
  license, delivery path, availability state, and fallback behavior for every
  model.
- Treat OTIO as the interchange source of truth where possible, with FCPXML and
  EDL adapters reporting exact losses.
- Build new sync work around deterministic manifests and journals before any
  remote protocol.
- Keep platform-specific publishing clients isolated behind interfaces so share
  handoff, OAuth, upload, quota, retry, and metadata behavior are testable.
- Move URI grant generation into a shared helper so capture, export, frame
  capture, archive, C2PA sidecar, and share handoff paths use one contract.
- Add more golden fixtures for project archives, timeline interchange, model
  gates, metadata scrubbing, and export warnings.
- Prefer small, proof-backed user flows over broad scaffolds with optimistic UI
  labels.

## Prioritized Roadmap

- [ ] P0 - Fix FileProvider grant coverage for camera captures and shared outputs
  - Why: capture/share flows can fail before the user reaches the camera or
    share sheet.
  - Evidence: `MediaPicker.kt` uses `cacheDir/camera-captures`; `file_paths.xml`
    does not expose that path.
  - Touches: `file_paths.xml`, `MediaPicker.kt`, URI grant helper, and
    instrumentation tests.
  - Acceptance: every internally generated share/capture URI is covered by a
    named FileProvider path and has a passing grant test.
  - Verify: run unit URI matrix, emulator FileProvider test, and at least one
    external camera capture smoke test.

- [ ] P1 - Align FCPXML validation with actual overlay export behavior
  - Why: users should not lose text overlays without warning.
  - Evidence: FCPXML validator treats overlays as preserved, but the exporter
    signature does not accept overlay data.
  - Touches: `TimelineExchangeEngine.kt`, `TimelineExchangeValidator.kt`,
    `EditorViewModel.kt`, and interchange fixtures.
  - Acceptance: FCPXML either serializes text overlays or emits a visible loss
    warning.
  - Verify: export fixture timeline with text overlay and assert exported data
    or warning.

- [ ] P1 - Make C2PA status honest and wire signed embedded manifests
  - Why: provenance claims require signed embedded manifests, not unsigned draft
    JSON.
  - Evidence: current C2PA engine is draft/dependency-gated.
  - Touches: `C2paExportEngine.kt`, Gradle dependencies, export UI, docs, and
    tests.
  - Acceptance: export result clearly reports signed embedded, draft sidecar,
    unavailable, or failed.
  - Verify: C2PA reader fixture validates signed output; fallback tests validate
    draft labels.

- [ ] P1 - Rename direct publish to share handoff and gate real API upload
  - Why: platform API uploads require OAuth, quotas, review, and upload clients.
  - Evidence: current engine returns `ACTION_SEND` share intents only.
  - Touches: `DirectPublishEngine.kt`, export UI, strings, README, privacy
    dashboard, and future API modules.
  - Acceptance: current flow is labeled share handoff; API upload is disabled
    until credentials and platform approval exist.
  - Verify: intent tests and docs scan for direct-upload overclaims.

- [ ] P1 - Ship one fully translated locale
  - Why: localization proof catches layout and copy issues while the UI is still
    changing.
  - Evidence: `locales_config.xml` currently lists only `en`.
  - Touches: strings, `locales_config.xml`, UI layouts, screenshot tests, and
    docs.
  - Acceptance: one non-English locale is complete and selectable with no
    missing translated strings.
  - Verify: locale completeness script, pseudo-locale run, and emulator smoke
    screenshots.

- [ ] P2 - Add release artifact checksums and provenance
  - Why: release consumers need a way to verify APK artifacts.
  - Evidence: CI signs and uploads artifacts but no checksum/provenance step was
    found.
  - Touches: `.github/workflows/build.yml`, release scripts, README, and
    release checklist docs.
  - Acceptance: tag releases include SHA-256 checksums and provenance or
    attestation where supported.
  - Verify: release dry-run validates checksums against uploaded artifacts.

- [ ] P2 - Add explicit network security config
  - Why: cleartext behavior should be deliberate and testable.
  - Evidence: manifest has `INTERNET` with no network security config found.
  - Touches: manifest, `res/xml/network_security_config.xml`, debug overlays,
    tests, and docs.
  - Acceptance: release builds block cleartext by default; any debug exceptions
    are explicit.
  - Verify: manifest merge and cleartext negative tests.

- [ ] P2 - Validate transform gestures and persist every gesture end state
  - Why: touch transforms must commit reliably to state and undo history.
  - Evidence: transform and drag handlers in `TransformOverlay.kt` have separate
    callback behavior.
  - Touches: `TransformOverlay.kt`, view-model reducers, undo history, and
    Compose UI tests.
  - Acceptance: drag, pinch, rotate, and combined gestures all persist and
    create expected undo entries.
  - Verify: Compose UI gesture tests plus device smoke test.

- [ ] P2 - Add scrubbed metadata share/archive option
  - Why: local-first trust requires user control over original media metadata.
  - Evidence: roadmap tracks metadata disclosure and scrubbed archive/share
    paths; privacy surfaces already exist.
  - Touches: export/archive engines, metadata readers, privacy dashboard,
    strings, and tests.
  - Acceptance: export/archive offers preserve and scrub modes with clear
    labels.
  - Verify: fixture media with location/device tags proves scrubbed output
    removes selected fields.

- [ ] P2 - Complete README claim-to-callsite audit
  - Why: docs should distinguish shipped, gated, stub, handoff, and planned
    behavior.
  - Evidence: README contains broad feature claims while ROADMAP tracks several
    still-open corrections.
  - Touches: README, ROADMAP, PROJECT_CONTEXT, and docs checks.
  - Acceptance: sensitive claims link to source/tests or are downgraded.
  - Verify: docs grep plus manual review of feature inventory.

- [ ] P2 - Add Android local network permission matrix tests
  - Why: Android 16 behavior and future target SDK changes affect LAN features.
  - Evidence: Android docs distinguish target 36 and target 37+ requirements.
  - Touches: manifest, network gate code, privacy dashboard, strings, and tests.
  - Acceptance: local network features report correct unavailable/permission
    states by API and target behavior.
  - Verify: API matrix tests and emulator/device smoke when supported.

- [ ] P2 - Prepare Play On-device AI pack migration plan
  - Why: large optional models need a delivery mechanism that preserves install
    size and model transparency.
  - Evidence: Play On-device AI requires AGP 8.8+, Play Core 2.1.0+, and R8
    8.10.21+; current project uses AGP 8.7.3.
  - Touches: version catalog, model manifest, Play delivery config, model
    manager, docs, and tests.
  - Acceptance: migration plan lists required toolchain changes and first model
    pack candidate.
  - Verify: dependency migration branch builds and missing-pack fallbacks pass.

- [ ] P3 - Activate caption translation only after offline model gates are real
  - Why: translation must be reviewable and license-safe.
  - Evidence: caption translation remains dependency-gated.
  - Touches: `AiToolRequirements.kt`, translation engine, model manifest, UI,
    export metadata, and tests.
  - Acceptance: supported language pairs are model-backed, editable, and
    labeled in export metadata.
  - Verify: missing-model, supported-model, review-edit, and export tests.

- [ ] P3 - Build deterministic local folder sync before WebDAV
  - Why: sync without conflict planning risks data loss.
  - Evidence: `ProjectSyncEngine.kt` is a stub.
  - Touches: sync engine, archive manifests, relink engine, storage access
    framework, WebDAV client, settings, and tests.
  - Acceptance: dry-run sync produces deterministic operations and conflict
    records before writes.
  - Verify: fixture directories, interrupted sync recovery, and mock WebDAV
    tests.

- [ ] P3 - Add proxy media workflow
  - Why: mobile editing of high-resolution media needs responsive preview and
    original-quality final export.
  - Evidence: proxy workflow is on the roadmap and aligns with professional NLE
    competitors.
  - Touches: media repository, preview engine, export engine, cache management,
    project schema, and UI.
  - Acceptance: projects can generate, regenerate, and discard proxies while
    final export uses original media.
  - Verify: 4K fixture preview/export test and missing-proxy recovery test.

## Quick Wins

- Add `camera-captures/` to FileProvider XML and test it.
- Add a docs/string cleanup that renames share-intent publishing to share
  handoff.
- Add C2PA status labels for draft sidecar versus signed embedded output.
- Update FCPXML validator warnings if text overlay serialization is not shipped
  immediately.
- Add release SHA-256 checksum generation to CI.
- Add `network_security_config.xml` with release cleartext blocked by default.
- Add a string-resource completeness check for the first translated locale.
- Add a README claim-audit table for sensitive claims: direct publish, camera
  recording, content credentials, translation, sync, stock providers, and audio
  description.

## Larger Bets

- Real C2PA signed embedded manifests across supported export formats.
- Authenticated YouTube, TikTok, and Instagram upload modules with OAuth,
  quotas, platform review, and privacy disclosure.
- Play On-device AI model packs for large optional tools after AGP/R8/Play Core
  migration.
- Conflict-safe local folder and WebDAV project sync.
- Proxy editing and original-media final export.
- Higher-quality segmentation, tap-to-select masking, and neural upscaling with
  measurable device performance budgets.
- CameraX in-app recorder with teleprompter, audio monitoring, pause/resume, and
  lifecycle-safe capture.

## Explicit Non-Goals

- Do not present share intents as direct platform upload.
- Do not present unsigned C2PA-shaped JSON sidecars as signed content
  credentials.
- Do not enable telemetry SDKs before local diagnostics, retention, delete, and
  data-safety behavior are complete.
- Do not add stock providers without license, attribution, cache, rate-limit,
  and privacy contracts.
- Do not ship WebDAV sync before deterministic local-folder sync planning and
  conflict recovery.
- Do not add large AI models to the base APK without a documented delivery,
  license, hash, and fallback strategy.
- Do not claim FCPXML preserves data that the exporter does not serialize.
- Do not label external camera handoff as in-app CameraX recording.

## Open Questions

- Which locale should be the first full translation, and who validates final
  legal/privacy copy?
- Which C2PA Android dependency path is acceptable for release size, license,
  signing-key custody, and offline verification?
- Should NovaCut prioritize OTIO-first interchange and warn on FCPXML/EDL losses,
  or invest in deeper FCPXML serialization first?
- Which social platform, if any, should be the first authenticated upload target
  after share handoff naming is corrected?
- Will Play-only AI pack delivery be acceptable for the main distribution
  channel, or must every AI feature also support a non-Play offline model path?
- What is the retention policy for a local product-health ledger, and should it
  be exportable for bug reports?
- Which media metadata fields should be preserved by default for project
  recovery versus scrubbed by default for shared archives?
- What device matrix is required for CameraX recorder work: API level, vendor,
  camera orientation, microphone routing, and storage pressure?
- Should sync support only user-selected folders first, or should WebDAV planning
  start in parallel behind dry-run-only UI?
- Which README claims are release-critical enough to block the next version if
  no source/test proof exists?

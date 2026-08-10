# Changelog

## v3.78.1

- Added a secret-free GitHub Actions PR/master gate covering JVM tests, lint, QA/debug packaging, package/release identity, dependency/native SBOM checks, 16 KB alignment, deterministic artifact hashes, and build provenance evidence.
- Upgraded the pinned Media3 runtime to 1.11.0 after the QA compatibility probe, full release-gate matrix, generated dependency evidence, and APK alignment checks passed.
- Media Manager now provides local name/note/tag search, missing/relink/used/unused triage filters, deterministic sorting, and persisted per-asset notes/tags across autosave, Room migration 9→10, archives, and managed-media sidecars.
- Batch export queues now persist priority order and planned outputs, support reorder controls plus active pause/cancel, retain eligible partial video paths for resume after interruption, and detect already-complete outputs to avoid duplicate work.
- Export completion now verifies requested versus actual video/audio codecs, container, dimensions, frame rate, duration, and MP4 layout; Media3 fallback callbacks and rejected stream-copy artifacts remain in export diagnostics/history, while unavailable encoder probes fail closed.
- Export audio-codec choices now expose only the device-verified AAC path; legacy Opus/FLAC config values remain readable but fail before encoding instead of producing AAC output under a false codec label.
- Added a CFR export toggle that normalizes VFR visual clips through the bundled FFmpeg cadence pass before Media3 rendering, persists in batch plans, discloses the rendered-only path in the export UI, and verifies constant sample intervals on an Android 16 device.
- Batch export can now queue each existing source clip's trim range as an independent output with its own persisted source URI/range, while uniform project exports remain available.
- Added a timeline range-selection tool that mutes audio non-destructively with volume keyframes across preview/export, preserving clip boundaries and autosave/undo behavior.
- Nested editor back navigation now uses Android's predictive-back progress with edge-aware preview motion, cancellation-safe state handling, and the existing immersive/panel/tool/selection/compound precedence.
- Android 15+ now uses Media3's platform loudness-controller path for preview/export audio decoding, applies a guarded HDR headroom policy to HDR editor windows and preview surfaces, and restores prior display settings on exit; older Android versions remain no-op fallbacks.
- Added an immersive fullscreen preview toggle that hides editor chrome and system bars, preserves the active Media3 surface/playback, and exits cleanly through the toggle or Back.
- Clip transform controls now support undoable horizontal and vertical flips that persist through autosave and edit-decision JSON while sharing the preview/export Media3 matrix path.
- Added portable `.clearcut-edl.json` edit-decision export/import with documented schema v1, preview-first routing, future-schema rejection, media-relink diagnostics, and canonical mapping for clips, markers, captions, and overlays.
- Media Manager diagnostics now detect embedded subtitle and GPS-like tracks; text subtitles can be converted locally to VTT/SRT, NMEA or container locations to GPX/CSV, and unsupported telemetry is explained without network access. Sidecars are bounded, stored under the existing secure diagnostic-share root, and share through a MIME-specific FileProvider URI.
- Android 10+ gallery saves now have an on-device MediaStore contract proving real MP4 exports appear in the Video collection with video classification, duration, and time metadata.
- `.ncfx` effect packs now embed validated `.cube`/`.3dl` LUT bytes within an 8 MB bounded payload and install them under content-hash-derived local names on import, so shared color grades no longer lose their LUT.
- Declarative pack hashes now canonicalize numbers using JSON's serialized representation, keeping float-bearing exports valid after a save-and-reload round trip.
- Metadata scrubbing now re-encodes WebP images without source metadata and routes TIFF images through the bundled FFmpeg TIFF codec with bounded input/timeout guards; failed conversions leave no claimed scrubbed output.
- Diagnostic ZIPs now live under a dedicated `diagnostic-shares/` FileProvider root instead of the private `diagnostics/` store; shared metadata redacts Windows/app-private paths, model URL query data, and stable build fingerprints while raw encoder errors remain explicit-consent only.
- `ACTION_VIEW` imports now accept secondary clip-data URIs only when the intent carries a read grant; the primary data URI remains supported without one.
- GIF and contact-sheet exports now replace the `{sizeMB}` filename token after atomic output completes, matching video and stream-copy exports.
- Freeze-frame insertion now honors the selected PNG/JPEG capture format for both bitmap encoding and output file names.
- Video frame-rate detection now prefers camera capture metadata and falls back to the actual video track's MediaFormat frame rate, normalizing fractional rates such as 23.976/29.97/59.94 instead of silently assuming 30 fps.
- Reversed clips now mirror timeline/source mapping in frame lookup, source-to-timeline conversion, and split boundaries, including the binary inverse used by speed curves.
- Incoming media and document intents now resolve MIME types, provider descriptors, display names, and sizes on Dispatchers.IO with cancellable latest-intent work, keeping slow cloud-backed providers off the main thread.
- Batch media imports now release only the persistable URI grants they acquired across review, cancellation, and storage-abort paths; dropped-file storage failures report insufficient space instead of a generic copy error.
- Exported project shortcuts now verify their project ID against Room off the main thread before opening the editor; stale or external IDs stay on Projects and cannot seed ghost rows.
- Batch export status pills and secondary project/settings feedback now use bilingual resources and plurals; localized editor-mode labels retain their canonical selection state.
- Export sheet playback now releases its player and codec lease with the composition, elapsed/ETA remain live during encoder stalls, and gallery saves move file checks off the main thread with double-tap protection.
- Accepted reverse-export fallbacks now surface a warning through the ViewModel toast and export sheet, including the completed export state; preflight and runtime copy share one deterministic message formatter.
- Editor canvases now resolve structural grids, rulers, scope surfaces, and template gradients through semantic theme roles; duplicated Catppuccin accents use palette tokens while signal/chroma colors remain explicit.
- Mixer pan and audio DSP now render through the same Media3 PCM chain in preview and export; constant-power mono/stereo pan and cross-buffer filter/effect state are covered by JVM and on-device audio-golden tests.
- Exported MP4s now preserve source creation time and rotation through Media3's muxer; GPS and namespaced stream tags are opt-in, while metadata scrubbing filters forwarded source metadata and disables stream-copy shortcuts.
- Removed the unused `BitrateMode`/CQ model and batch-plan field because Media3 1.10.1 rejects CQ at the encoder boundary; exports retain the supported bitrate strategy instead of exposing a nonfunctional control.
- Added bounded per-track A/V sync offsets with frame nudges and millisecond entry; offsets persist through autosave, participate in preview/Transformer timing, remain undoable, and force modified timelines away from stream-copy shortcuts.
- Settings now offers Replay Editor Walkthrough, which opens the editor tutorial immediately for the latest project without auto-opening it on ordinary editor launches or persisting an obsolete reset flag; the English and Spanish copy and navigation/UI coverage are aligned.
- Release R8 rules now retain only exact `Class.forName` probes and Room's generated database constructor while delegating Hilt, WorkManager, Media3, Compose, Coil, DataStore, OkHttp, ONNX Runtime, MediaPipe, and Lottie to their consumer rules; the minified release produced retraceable mapping/usage reports, passed native alignment checks, and reduced each APK split by 4.88 MB against the recorded baseline.
- Startup now removes only stale, app-owned pending MediaStore rows from ClearCut public output directories, recovering invisible artifacts left by a killed export or archive save.
- Media3 export timeout cleanup now routes Transformer cancellation through an explicit termination fence before deleting output, preventing a late muxer write from recreating a partial artifact.
- Speed-curve splits now carry their rounded boundary correction into subsequent clips, preserving the next clip and any intentional following gaps while retaining exact left/right abutment.
- Timeline clip thumbnails now use keyed Compose lazy strips with a bounded cache window, and off-screen thumbnail/waveform surfaces pause through visibility callbacks; deterministic policy checks and the existing frame-timing scrub benchmark guard the scroll path.
- ONNX Runtime sessions now attempt the bundled XNNPACK provider with bounded internal threading and retry on a fresh CPU session when the provider or model path cannot use it; Whisper and LaMa share the ownership-safe factory, and an instrumentation probe records native capability.
- Pure single-asset MP4 trims now opt Media3 into GOP trim optimization and MP4 edit-list trimming when no timeline, audio, visual, overlay, or speed edits are present; ineligible edits retain the full Transformer path, and the existing output verification remains mandatory.
- Media3 exports now enable CodecDB-Lite encoder tuning, round computed dimensions to encoder-safe multiples, apply Media3 unset-side rounding to proxies, and cap speed-processed clips at 60 fps; JVM and device coverage guard odd geometry, frame-rate ballooning, and valid output artifacts.
- Replaced hard-coded dependency freshness claims with a committed source-backed snapshot and offline provenance gate; refresh reads authoritative Maven metadata, AGP/Lifecycle/Room release facts are recorded explicitly, and catalog changes require a passing local QA compatibility probe.
- Added conservative Media3 export resume support for single, gap-free A/V MP4 timelines: eligible cancellations retain a partial, Resume writes to a distinct destination and verifies completion before cleanup, while changed or failed resumes clean artifacts and fall back to Restart.
- Added frame-quantized export ranges with Set Start/Set End/Clear controls; exports rebase media, effects, captions, overlays, transitions, chapters, audio gaps, and tracked state without mutating the project, and history records the resolved bounds.
- Added bounded SRT/WebVTT caption import with encoding, language-confidence, overlap, invalid-cue, and size diagnostics; accepted previews apply offset-aware captions to the selected clip as one undoable edit.
- Batch export plans now persist atomically with bounded, versioned JSON; interrupted work restores as explicit `INTERRUPTED` items, changed project/config fingerprints require review, and completed jobs remain in export history.
- GIF export now streams sampled frames into its atomic output, recycles each frame immediately, derives the logical canvas from source geometry, and uses a bounded primitive LZW table with streaming sub-blocks.
- Added an isolated `qa` Android build with a separate application ID, bundled timeline fixture, deterministic Compose tags, and on-device coverage for import, trim, split, delete, undo/redo, relaunch persistence, and scoped cleanup.
- Multi-selected local media now opens a capture-time/name/manual starter-sequence review with drag reordering, then appends the accepted sequence as one undoable timeline mutation.
- Waveform and proxy jobs now bind results to clip timeline identities, discard stale completions after edits, and requeue current waveform work when the timeline changes during extraction.
- Media health now reports container, codec, track-language, HDR/color, timestamp, and sync-frame diagnostics, with previous/next sync-frame navigation and export preflight risk disclosure.
- GIF export now requests aspect-preserving frames at the configured maximum width, prefers base VIDEO tracks over overlays, and maps palette overflow to the nearest available color.
- Cross-app media-picker drops now accept MIME metadata during drag-start, request temporary URI access before ingesting content URIs, and release the grant on success, failure, or early exit.
- Projects, templates, privacy, licenses, and settings now resolve structural colors through the semantic theme roles while preserving stable media accents; the source-policy test audits both feature trees for raw palette and hex bypasses.
- Editor orchestration now delegates document/recovery, archive transfer, WorkManager jobs, and preview playback sessions to fake-tested Hilt coordinators while preserving the ViewModel's state projection and cancellation behavior.
- Separated Android backup scopes: cloud backup remains bounded to project metadata, while device transfer carries validated app-owned fonts/LUTs and excludes partial copies; archive disclosures call out external references that may need relinking.
- Settings open-source notices now generate from the resolved release runtime graph and native lock: 240 runtime and 15 vendored native components are version-current, unmapped licenses or stale curated entries fail verification, and the reviewed FFmpeg source offer remains intact.
- Public release copy and capability metadata now share executable claim gates, including registry evidence, dependency-missing qualifiers, optional-network disclosures, and release self-tests.
- Reachable editor UI copy now uses parity-checked English/Spanish resources with a hard-coded-literal ratchet and debug pseudo-locale coverage for expansion and RTL checks.
- Raised compact editor actions to 48dp semantic hit targets while keeping their visual geometry, with localized labels and radio-button semantics for text and glow swatches.
- Track-level blend modes now share an explicit capability policy: unsupported edits are rejected before mutation, and imported non-NORMAL track state is disclosed as a normal-alpha export fallback requiring consent.
- Native SBOM verification now hashes the checked-in security patch canonically across Windows line endings while retaining byte-exact artifact and POM checksums; expiry, advisory coverage, and deterministic self-tests remain enforced.
- Smart-render export now consumes the tested segment planner: eligible timelines mix stream-copy runs with boundary re-encodes, verify each artifact, preserve authored gaps by falling back safely, and concat only after the run outputs pass validation.
- Rebuilt the vendored FFmpegKitNext AAR under an LGPL-3.0-or-later profile, removed x264, disabled every currently reachable FFmpeg advisory surface with named runtime refusals, and upgraded the native lock/SBOM gate to require the full CVE inventory and reproducible security patch.
- Added a shared safe declarative-pack contract with schema migration outcomes, SHA-256 payload verification, executable-field rejection, provenance reporting, conflict previews, atomic installs, and one-step rollback across style/effect/LUT/font asset flows.
- Added a shared, device-ceiling-aware lease queue for decoder, retriever, preview-player, and proxy-pipeline work, with cancellation-safe contention tests and live counts in diagnostic bundles.
- Added conformant OTIO timing/metadata export, FCPXML and CMX 3600 import parsers, official-adapter validation tooling, and a fidelity/media-relink preview that commits accepted timelines through the canonical project document boundary.
- Moved timeline interchange export validation, serialization, naming, and atomic file writes behind a typed coordinator; the editor facade now owns only snapshot capture and localized feedback.
- Routed manual and periodic project writes through one persistence coordinator so Room metadata/assets and the canonical autosave document share a tested write boundary.
- Extracted pure composition track planning and shared Media3 composition assembly from `VideoEngine`, keeping preview and export on the same tested selection/build contracts.
- Moved mutable editor-state construction behind a tested `EditorStateStore`; the existing ViewModel/delegate APIs remain behavior-compatible while screens observe a read-only flow.
- Conformed the unsigned C2PA draft to 2.4 generator-info and actions-v2 metadata, removed the retired training-mining assertion, and made the no-signing/no-embed status invariant explicit in sidecars and UI copy.

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# Roadmap — ClearCut

## Research-Driven Additions

### P2 — Later

- [ ] P2 — Implement offline, hardware-aware stabilization
  Why: Stabilization is table-stakes in LumaFusion, PowerDirector, InShot, and DaVinci, while `StabilizationEngine.analyzeMotion()` remains a stub.
  Evidence: `engine/StabilizationEngine.kt`, competitor feature/release pages, existing Media3/FFmpeg/capability infrastructure.
  Touches: stabilization engine/model, preview/export composition, progress/cancel, thermal/storage preflight, UI, golden clips.
  Acceptance: A local implementation produces deterministic motion/crop data, previews the crop before mutation, cancels cleanly, preserves originals, matches export, and gates unsupported/thermal-constrained devices with an explanation.
  Complexity: XL
  Update (2026-08-02): adopt Gyroflow's data model rather than inventing one — stabilization is stored as motion data plus a lens profile and a sync offset, applied at render, never baked into pixels. That satisfies "preserves originals" structurally instead of by discipline, and makes the crop preview a projection of stored data rather than a separate code path. Independently: `ui/editor/AiToolsDelegate.kt:1212,1222` passes `onProgress = { }` to the stabilization call, so whatever backend lands will drive a progress indicator that cannot move — fix that with the relabelling work, not with this XL item.
  Update (2026-07-29, second pass): the stub is not the only problem — `ui/editor/AiToolsDelegate.kt:953-983` currently applies a static `scaleX`/`scaleY` zoom with no counter-motion and toasts "Basic stabilization applied (N% shake)". Do not wait for this XL item to fix that: the P1 "Stop reporting AI analysis failures as positive findings" item relabels or removes the zoom-only path now. `engine/StabilizationEngine.kt:157 isOpenCvAvailable()` returns a hard `false`, making the entire OpenCV branch at `AiToolsDelegate.kt:985-1032` unreachable — decide whether that branch is the intended implementation or dead weight before starting.

### P3 — Under Consideration

- [ ] P3 — Add local frame interpolation only behind measured capability gates
  Why: Smooth slow motion is common in VN, PowerDirector, and InShot, but the current RIFE engine is a stub and mobile thermal/storage cost is high.
  Evidence: `engine/FrameInterpolationEngine.kt`, VN/PowerDirector/InShot release pages, on-device mobile video research.
  Touches: model packaging/download, inference pipeline, frame/timebase integration, thermal/storage estimate, progress/cancel, export tests.
  Acceptance: A benchmarked on-device backend preserves source frames/timebase, predicts cost before download/run, supports cancel/resume cleanup, degrades explicitly, and passes temporal/artifact goldens on the supported device matrix.
  Complexity: XL

## Research-Driven Additions — 2026-07-29 (second pass)

Sourced from `RESEARCH.md` (2026-07-29 second pass). Theme: the previous drain hardened *failure* paths; these items address *success* paths that report work which did not happen, plus code that exists but cannot be reached.

### P0 — Now

### P1 — Next

### P2 — Later

- [ ] P2 — Delete the dead engine layer
  Why: Roughly 110 KB of engine source has zero references anywhere including tests, which inflates APK size, review surface, and the singleton graph, and makes the honest-stub inventory harder to read.
  Evidence: zero-reference classes under `engine/`: `HdrCapabilityProbe.kt`, `ProjectSyncEngine.kt`, `RiveTemplateEngine.kt`, `AdjustmentLayerEngine.kt`, `AssetLibrary.kt`, `MetadataScrubEngine.kt`, `EquirectangularEngine.kt`, `OboeResamplerEngine.kt`, `StockAssetEngine.kt`, `TemplateMarketplaceEngine.kt`, `StemSeparationEngine.kt`, `LipSyncEngine.kt`, `VoiceCloneEngine.kt`, `CameraCaptureEngine.kt`, `TapSegmentEngine.kt`, plus `WordEmphasisAnimator.kt:27`, `KeyframeBezierGraph.kt:29`; injected-but-unused params at `ui/editor/AiToolsDelegate.kt:34,36` and `ui/editor/V369Delegate.kt:57,59`; unreachable public API at `ai/AiFeatures.kt:1388,1545,1722,2047`; `engine/C2paExportEngine.kt:433 signAndEmbed` (rejected in research — delete, do not complete).
  Touches: `engine/`, `ai/AiFeatures.kt`, `ui/editor/AiToolsDelegate.kt`, `ui/editor/V369Delegate.kt`, Hilt modules, `README.md` architecture tree, `EngineStringExtractionAuditTest`.
  Acceptance: Every listed symbol is removed or given a reachable caller; the README architecture tree matches the surviving files; the APK size baseline is re-measured and reduced.
  Complexity: M

- [ ] P2 — Replace source-text ratchet tests with tests that execute the app
  Why: 28 test files (~71 tests) assert on source text read off disk rather than behaviour, which inflates apparent coverage, breaks on renames and reformatting, and in one case actively forbids a migration the codebase's own policy recommends.
  Evidence: `FrameExtractionPolicyTest.kt:87-88` asserts `media3-inspector-frame` is absent from the version catalog while `engine/FrameExtractionPolicy.kt` states HDR/effect frames should migrate to it; `EngineStringExtractionAuditTest.kt:59-77` asserts the engine directory file count is in 95..180; `EditorPlaybackContractTest.kt` asserts a literal source substring; `ExportStoragePolicyTest.kt` asserts statement ordering by character offset; `EditorPreviewLayoutContractTest.kt` asserts `.weight(1f)` appears between two comments; zero Compose UI unit tests exist and `unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts:139`).
  Touches: `app/src/test/`, `engine/FrameExtractionPolicy.kt`, `ui/editor/PreviewPanelMedia3ComposePolicy.kt`, `ui/editor/TimelineProgressSliderPolicy.kt`, `gradle/libs.versions.toml`.
  Acceptance: Contract tests that can only be satisfied by source text are replaced by Robolectric or Compose tests asserting the behaviour they stand in for, or are re-labelled as lint rules rather than tests; the frame-extraction ratchet is inverted so adopting `media3-inspector-frame` is permitted; the three unwired policy objects are either consumed at runtime or converted to documentation without a test budget.
  Complexity: M
  Update (2026-08-02, audit pass): the population grew by three in v3.78.0 — `DestructiveActionRecoverabilityTest`, `SettingsConsumerRatchetTest`, and the source-text half of `AnalysisOutcomeHonestyTest` and `PublicFeatureClaimsTest`. Two of them are already demonstrably weak: `DestructiveActionRecoverabilityTest.theUndoSnapshotActuallyCarriesTheCheckpointList` asserts the exact wiring that the P0 snapshot-loss finding shows is broken, and passes; `SettingsConsumerRatchetTest` passes on a setting whose value every consumer discards. Treat those two as the worked examples when choosing what a behavioural replacement must actually prove — a text match on the fix is not evidence the fix works.

- [ ] P2 — Generate and ship a real baseline profile
  Why: The shipped profile is a 13-line hand-written wildcard guess with no measured startup or frame data behind it, and the macrobenchmark that would produce one has never been executed.
  Evidence: `app/src/main/baseline-prof.txt` (13 lines of wildcard `HSPL` rules, 13 of 6,265 merged lines); `app/build.gradle.kts:471-473` sets `automaticGenerationDuringBuild = false`; `app/src/release/generated/baselineProfiles/` is empty; `baselineprofile/src/main/java/.../StartupAndEditorMacrobenchmark.kt` has never run; no runner exists in `scripts/`.
  Touches: `baselineprofile/`, `app/build.gradle.kts`, `scripts/`, `app/src/main/baseline-prof.txt`.
  Acceptance: The generator runs against a real or managed device and its output is committed; a script in `scripts/` regenerates it; startup and editor-scroll metrics are recorded as a baseline so regressions are detectable.
  Complexity: M

- [ ] P2 — Introduce a logging seam, StrictMode, and a live product-health ledger
  Why: 464 raw `android.util.Log` sites across 96 files have no abstraction, level control or release gating; the redaction ratchet polices their *content* but nothing polices their existence, and the health counters that would explain field behaviour are dead.
  Evidence: 464 `android.util.Log` call sites across 96 files; no `StrictMode` anywhere; `engine/ProductHealthLedger.kt` defines 13 counters but the only `record()` call in `app/src/main` is `ClearCutApp.kt:58` (`COLD_START`), and the ledger is never added to the diagnostics ZIP; `engine/CrashRecordStore.kt:138-139` stores `messagePresent` and `messageSha256` but never the exception message; `permissionSnapshots` is a supported bundle section the Settings caller never fills.
  Touches: `engine/` logging seam, `ClearCutApp.kt`, `engine/ProductHealthLedger.kt`, `engine/DiagnosticExportEngine.kt`, `engine/CrashRecordStore.kt`, `ui/settings/SettingsScreen.kt`.
  Acceptance: All logging routes through one seam with level control and release gating, enforced by the existing ratchet; `StrictMode` runs in debug with a documented violation policy; the health ledger records its 13 counters and ships in the diagnostics ZIP alongside a filled `permissionSnapshots`; crash records retain a redacted message rather than only a digest.
  Complexity: M
  Update (2026-07-29, third pass): WorkManager 2.12 adds a `work-analytics` artifact (`WorkMetricsQuery`, `workerDurationMillis`, `stopReasonCounts`, `runAttemptCount`, and a Flow of finished `WorkMetricsInfo`). If export or model-download work runs through WorkManager, this answers "why was my export killed" with a real stop reason (thermal, quota, constraint loss) instead of a silent retry — feed it into the same diagnostics bundle rather than building a parallel counter.

- [ ] P2 — Ship a light theme or remove the option that pretends to be one
  Why: Only dark colour schemes exist, yet the Appearance dropdown offers "System" as a distinct choice that resolves to Dark in both branches, so the control is a labelled no-op and the app ignores the platform preference it reads.
  Evidence: `ui/theme/Theme.kt:82` (`ClearCutDarkColorScheme`) and `:119` (`ClearCutHighContrastColorScheme`, also a `darkColorScheme`); no `lightColorScheme` import exists; `:193-201` maps `AppearanceMode.SYSTEM` to `DARK` whether `systemDark` is true or false while `isSystemInDarkTheme()` is read at `:400` and discarded; `res/values/themes.xml` is dark-only with no `values-night/`; 29 raw colour literals sit outside the token layer (`ui/editor/Timeline.kt` 7, `VideoScopes.kt` 7, `PipPresetsPanel.kt` 6).
  Touches: `ui/theme/Theme.kt`, `ui/theme/Tokens.kt`, `res/values/themes.xml`, panels holding raw colour literals, `ui/settings/SettingsScreen.kt`.
  Acceptance: Either a light scheme exists and "System" selects it, or the option is removed and the dropdown offers only what the app implements; instrument colours (scopes, curves, waveforms) are the only permitted raw literals and are documented as such.
  Complexity: M
  Update (2026-08-02, audit pass): the first half of the acceptance is met. v3.78.0 removed `AppearanceMode.SYSTEM` and its string, so the dropdown now offers only `Dark` and `High Contrast Dark`, both of which exist; `ui/theme/Theme.kt:197` reduces `resolveMode` to identity and the discarded `isSystemInDarkTheme()` read is gone. What remains is the choice this item still poses — ship an actual light scheme, or accept dark-only permanently — plus the raw-literal cleanup, which is unchanged at 28 literals outside `ui/theme/` (`Timeline.kt` 7, `VideoScopes.kt` 7, `PipPresetsPanel.kt` 6, `KeyframeCurveEditor.kt` 3, `TextTemplateGallery.kt` 2, `SpeedCurveEditor.kt` 2, `TimelineDrawing.kt` 1). Note that removing `SYSTEM` also means the app no longer reads the platform preference at all, so adding a light scheme later is a fresh decision rather than a re-wiring.

- [ ] P2 — Add offline awareness
  Why: Nothing in the app observes connectivity, so model downloads, caption translation and update checks present as available and fail at the point of use.
  Evidence: no `ConnectivityManager` observer anywhere in `app/src/main`; the only network inspection is the metered/Wi-Fi gate at `engine/ModelDownloadManager.kt:165-172`; the screen/state audit found offline handling missing on every screen.
  Touches: connectivity observer, `ui/settings/SettingsScreen.kt`, `engine/ModelDownloadManager.kt`, `engine/UpdateChecker.kt`, caption translation UI.
  Acceptance: Network-dependent controls disable with an explanatory state when offline and recover automatically; no network-dependent action fails with a generic error that a pre-check could have prevented.
  Complexity: S

- [ ] P2 — Migrate to Room 3.0
  Why: Room 2.x entered maintenance mode in March 2026 with no further feature work, and ClearCut's migration cost is unusually low because the DAO layer already satisfies Room 3.0's hardest requirement.
  Evidence: Room 3.0 announcement (2026-03) — `androidx.room3` package, KSP-only, no Java codegen, `SQLiteDriver` replaces SupportSQLite, blocking DAO functions disallowed; `gradle/libs.versions.toml` pins room 2.8.4; `engine/db/ProjectDatabase.kt` is the only `@Dao` file, with 15 suspend functions and 2 Flow returns and zero blocking DAO functions; the only SupportSQLite usage is the 8 `Migration.migrate` bodies.
  Touches: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `engine/db/ProjectDatabase.kt`, migrations, `app/schemas/`, `DependencyFreshnessTest`.
  Acceptance: Room 3.0 is adopted with migrations rewritten against the driver API, the schema chain still validates, and the migration test passes on a real device. Sequence after the toolchain lane, since Room 3.0 raises the Kotlin/KSP floor.
  Complexity: M
  Update (2026-08-02): the sequencing precondition is met — the toolchain lane landed (AGP 9.1.1, Gradle 9.3.1, Kotlin 2.4.10, KSP 2.3.10, compileSdk/targetSdk 37 in `gradle/libs.versions.toml`), so nothing blocks this on the floor any more. The device half of the acceptance is also cheaper than recorded: see the P0 AVD item below.

- [ ] P2 — Complete distribution metadata and fix the two broken release scripts
  Why: The app ships a fully translated Spanish locale with no Spanish store listing, its listing screenshots are rendered mockups rather than device captures, and two release scripts cannot run as documented.
  Evidence: `fastlane/metadata/android/` contains only `en-US` while `res/values-es/` is 100% key-complete (2323 strings plus 32 plurals, zero missing); listing images derive from `_source/*.svg`; `scripts/sync_fastlane_changelogs.py` reads `CHANGELOG.md`, which `.gitignore` excludes, so it cannot run from a clean clone; `scripts/write_release_checksums.py --root <relative>` crashes in `relative_to` and the README documents that exact invocation.
  Touches: `fastlane/metadata/android/es-ES/`, `scripts/sync_fastlane_changelogs.py`, `scripts/write_release_checksums.py`, `README.md`.
  Acceptance: A Spanish listing exists with translated description and changelog; listing screenshots are real captures from a device or emulator; both scripts run successfully from a clean clone with the invocations the README documents.
  Complexity: S

### P3 — Under Consideration

- [ ] P3 — Support APV (Advanced Professional Video) as an import format
  Why: Samsung's APV is supported in Android 16 and on Snapdragon 8 Elite Gen 5 / Exynos 2600 devices, and DaVinci Resolve and LumaFusion already ingest it — Galaxy flagship owners shooting APV currently have no FOSS Android editor that can open their footage.
  Evidence: Samsung APV developer documentation; Android 16 codec support; Galaxy S26 Ultra as the first shipping device; `engine/VideoEngine.kt` codec probing already exists.
  Touches: codec probe, import validation, proxy generation, preflight disclosure, `README.md` format table.
  Acceptance: APV sources are detected and either edited via a generated proxy or refused with an explicit device-capability explanation before the user commits to an edit; no APV path silently degrades quality without disclosure. Gate on evidence that target users have APV footage — this is speculative reach, not a known request.
  Complexity: L

## Research-Driven Additions — 2026-07-29 (third pass)

Sourced from `RESEARCH.md` (2026-07-29). This pass covered the source classes the second pass under-reached: competitor issue trackers, dependency release notes, and community forums. Items are new; where research only sharpened an existing item, an inline `Update`/`Correction` note was added to that item instead.

### P0 — Now

### P1 — Next

### P2 — Later

- [ ] P2 — Implement smart cut so trims do not force a full re-encode
  Why: Frame-accurate cutting without re-encoding the whole timeline is the single most-demanded feature found across every editor surveyed, and it directly serves ClearCut's existing stream-copy export path.
  Evidence: LosslessCut #126 has 157 reactions, the highest of any issue in this survey; ClearCut already ships a stream-copy export route and `engine/VideoEngine.kt` codec probing; Media3 1.11's `Mp4Muxer` improvements and `setAttemptStreamableOutputEnabled` reduce the muxing cost of the re-encoded segments.
  Touches: export path selection, GOP/keyframe analysis, `engine/VideoEngine.kt`, `engine/FFmpegEngine.kt`, export preflight disclosure, golden fixtures.
  Acceptance: A trim whose boundaries fall inside a GOP re-encodes only the affected GOPs and stream-copies the remainder, producing frame-accurate output; the export sheet discloses which strategy was chosen and why; A/B fixtures prove the smart-cut output is frame-identical to a full re-encode at the cut boundaries and byte-identical in the copied regions.
  Complexity: L
  Correction (2026-08-02): do not build the GOP analysis. Media3 already ships this as `Transformer.Builder.experimentalSetTrimOptimizationEnabled(boolean)`, which re-encodes only the GOP between the trim start and the next keyframe and stream-copies the remainder, reporting the outcome in `ExportResult.optimizationResult` across seven constants — notably `OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED` (effects were requested) versus `OPTIMIZATION_FAILED_FORMAT_MISMATCH` (the encoder cannot match the source). There are zero references to it in this repo. Constraints: single-asset MP4 input, level/profile-compatible encoder output, no-op video effects, rotations divisible by 90°; anything else silently falls back to a full transcode, which is the part this project must surface rather than swallow. That collapses this item from L to S/M: enable the flag on the eligible path, map the seven result codes to user-facing copy, and keep the A/B fixture acceptance as written. Explicitly do **not** adopt the sibling `experimentalSetMp4EditListTrimEnabled` — it writes an edit list with a pre-roll, so the trimmed frames physically remain in the delivered file.

- [ ] P2 — Take the cheap dependency wins that do not require the toolchain lane
  Why: Several pinned libraries are behind on releases that deliver measurable wins independent of the AGP/Kotlin migration, so they can land now rather than waiting on the atomic upgrade.
  Evidence: Coil 3.3.0 → 3.5.0 prefers embedded video thumbnails in `coil-video` and coalesces concurrent duplicate requests, and `app/build.gradle.kts:431` already depends on `coil-video`; Robolectric 4.14.1 → 4.16.1; lifecycle 2.10.0 → 2.11.0 adds `rememberViewModelStoreOwner` for auto-clearing per-clip ViewModels; coreKtx 1.16.0 → 1.19.0; Compose BOM 2026.06.00 → 2026.06.01; MediaPipe 0.10.35 → 1.0.0 adds GPU post-processing for segmentation, removing a CPU readback per frame.
  Touches: `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `DependencyFreshnessTest`, media picker and timeline thumbnail paths, segmentation engine.
  Acceptance: Each bump lands independently with the suite green and verification metadata refreshed; timeline and media-picker thumbnail latency is measured before and after the Coil bump; Robolectric's JDK requirement is confirmed against the build JDK before bumping. Explicitly excluded here: AGP, Kotlin, KSP, Hilt, Room, and benchmark, which belong to the toolchain lane.
  Complexity: S

- [ ] P2 — Add export presets for common delivery targets
  Why: Users repeatedly ask editors for one-tap platform presets rather than assembling resolution, aspect ratio, and bitrate by hand, and ClearCut already models every underlying parameter.
  Evidence: OpenCut #737 requests YouTube/TikTok/Instagram export presets with automatic aspect ratio; OpenCut #858 requests batch export of selected clips to separate files, which ClearCut's batch export path partially covers; `ui/export/ExportSheet.kt` and `engine/ExportConfig` already expose resolution, frame rate, codec, and quality.
  Touches: `ui/export/ExportSheet.kt`, export config model, presets registry, `strings.xml`.
  Acceptance: Selecting a delivery preset sets resolution, aspect ratio, frame rate, and codec together and discloses any re-framing it implies before export; presets are data, not hard-coded branches, so adding one requires no UI change.
  Complexity: S
  Update (2026-08-02): mostly shipped — `model/ExportConfig.kt:16` carries `platformPreset`, six presets exist (`youtube1080`, `youtube4k`, `tiktok`, `instagram`, `instagramSquare`, `threads`), and `ui/export/ExportSheet.kt:600-613` renders them as selectable chips from `PlatformPreset.entries`, so the "presets are data" half is met. What is not met is the aspect-ratio half: the chip's `onClick` at `:606-609` copies `resolution`, `frameRate`, `codec` and `platformPreset` but **not** `aspectRatio`, even though every `ExportConfig` factory function sets it. Combined with the P2 finding below on the filename template, a preset selection can therefore produce a file whose name advertises a ratio the file does not have. Remaining work is the missing `aspectRatio` copy plus the re-framing disclosure.

- [ ] P2 — Add silence-aware jump-cut removal
  Why: Automatic removal of silent regions is an established, well-liked workflow in adjacent tooling that no Android editor offers, and ClearCut already computes the audio analysis it requires.
  Evidence: `WyattBlue/auto-editor` (4,632 stars, actively maintained as of 2026-07-29) is built entirely around this workflow; OpenCut #791 requests a silence remover; ClearCut already has energy-envelope analysis in `ai/AiFeatures.kt` and auto-duck speech detection at `EditorViewModel.autoDuck()`.
  Touches: audio analysis, timeline edit operations, preview of proposed cuts, undo, `ui/editor/AudioPanel.kt`.
  Acceptance: Detected silent regions are previewed as a proposed cut list the user can adjust before a single undoable commit; thresholds and minimum-gap are user-tunable; the operation never mutates the timeline without an explicit confirmation step.
  Complexity: M

## Audit Findings — 2026-08-02

Read-only audit pass over the v3.78.0 tree (commit `8ca774c`). Baseline recorded before
auditing: `:app:testDebugUnitTest --rerun-tasks` = **1348 tests, 0 failures, 0 skipped**;
`:app:lintDebug` = **0 errors, 437 warnings**. No source file was modified by this pass.

Several findings are regressions introduced by the v3.78.0 drain itself. Where a finding
sharpens an item that already exists above, an inline `Update (2026-08-02)` was added to
that item instead of a new entry here.

### P0 — Now

### P1 — Next

### P2 — Later

- [ ] P2 — Deleted templates accumulate in a trash nothing ever empties
  Category: reliability
  Where: `engine/TemplateManager.kt:137-163`.
  Problem: v3.78.0 changed template deletion from an unlink into a move to `templates/trash/`, which is the right call for recoverability, but nothing ever removes the moved file. Every template a user has ever deleted stays in app-private storage forever, invisible in the UI and not covered by any retention policy — up to `maxTemplateBytes` (10 MB) each. The projects list has an "empty trash" flow; templates have no equivalent.
  Evidence: `deleteTemplate` at `:145` renames into `trashFileForId(id)`. The only other readers are `restoreTemplate` (`:149`) and `isTemplateRestorable` (`:158`). No code anywhere deletes from that directory: `grep -rn "trash" app/src/main/java --include=*.kt` returns no pruning site in `TemplateManager`, and `listTemplates` (`:68-74`) filters on `extension == "json"` so the subdirectory never appears in the UI. The class already demonstrates the pattern to copy — `ExportIncidentStore.pruneOldIncidents` (`engine/ExportIncidentBundle.kt:156-161`) keeps the newest `MAX_INCIDENT_FILES` and deletes the rest.
  Fix: Prune on write, exactly as `ExportIncidentStore` does: on each `deleteTemplate`, drop trashed files beyond a small retention count and beyond an age (the restore offer is only ever presented for the most recent deletion, so retaining more than a handful serves nothing). Optionally surface trashed templates in the existing storage accounting so the space is attributable.
  Acceptance: Deleting N templates leaves at most the retained count in `templates/trash/`; a test asserts the directory does not grow without bound.
  Confidence: Verified
  Effort: S

- [ ] P2 — The Settings default aspect ratio reaches only the output filename, not the output
  Category: correctness
  Where: `ui/editor/EditorViewModel.kt:1131`, `:1404`; `ui/editor/ExportDelegate.kt:707`, `:338-353`, `:1881`; `ui/editor/EditorPrimaryPanelHost.kt:201`.
  Problem: v3.78.0 seeded `exportConfig.aspectRatio` from `settings.defaultAspectRatio` as part of making dead Settings do something, but every consumer that matters overrides it with the project's own aspect ratio — correctly, since the project's format is what should govern. One consumer does not: the filename template. With the "Name + Preset" filename option selected and a Settings default that differs from the project's ratio, ClearCut writes a file whose name advertises an aspect ratio the file does not have.
  Evidence: `EditorViewModel.kt:1131` sets `aspectRatio = settings.defaultAspectRatio` on the export config. The actual export overrides it at `ExportDelegate.kt:707` (`.copy(aspectRatio = currentState.project.aspectRatio)`), preview overrides it at `EditorViewModel.kt:1404`, and the export sheet is passed `state.project.aspectRatio` directly at `EditorPrimaryPanelHost.kt:201`. But `applyFilenameTemplate` is called at `ExportDelegate.kt:1881` with `stateFlow.value.exportConfig` — the un-overridden value — and derives the preset token at `:353` from `config.platformPreset?.displayName ?: config.aspectRatio.label`. New projects already pick up the setting independently via `ProjectListViewModel.createProject`, so the seeding at `:1131` adds nothing correct.
  Fix: Remove the `aspectRatio` seed at `EditorViewModel.kt:1131` — the project's ratio is the only meaningful source — and pass the resolved config (the one carrying `project.aspectRatio`) into `applyFilenameTemplate` at `:1881`. Keep the `codec` seed, which has no such override.
  Acceptance: With a Settings default of 16:9 and a 9:16 project, the exported filename's preset token reads 9:16. Removing the seed leaves `SettingsConsumerRatchetTest` green because `createProject` still reads the setting.
  Confidence: Verified
  Effort: S

- [ ] P2 — The export error card can offer a report describing a different failure
  Category: correctness
  Where: `ui/editor/ExportDelegate.kt:278`, `:231-244`, and the four `recordExportIncident` call sites (`:1309`, `:1389`, `:1501`, `:1858`); `ui/editor/EditorDomainState.kt:93`; `ui/export/ExportSheet.kt:456-484`.
  Problem: `lastIncidentReport` is written once, on incident capture, and never cleared — not when an export starts, not when one succeeds, not when the sheet closes. Only four export failure paths record an incident, while `VideoEngine.ExportFailureCause` enumerates twelve terminal causes. A failure that takes an unrecorded path renders the error card with the previous failure's report still attached, so "Copy report" hands the user a report about a different export. A triager receiving it is worse off than with no report at all.
  Evidence: `grep -rn "lastIncidentReport" app/src/main/java` returns exactly three hits: the field (`EditorDomainState.kt:93`), the single write (`ExportDelegate.kt:278`), and the read (`EditorPrimaryPanelHost.kt:213`). There is no reset. `grep -n "recordExportIncident(" ExportDelegate.kt` shows four call sites against twelve `ExportFailureCause` values.
  Fix: Clear `lastIncidentReport` when an export begins, so the card can only ever show a report for the run that just failed. Then either record an incident on every terminal failure path — the natural seam is the `failExport(cause, message)` helper the same release added to `VideoEngine` — or have the card omit the "Copy report" action when no report exists for the current run.
  Acceptance: Starting a new export clears any prior report; a failure with no recorded incident shows the error card without a "Copy report" action rather than with a stale one.
  Confidence: Verified
  Effort: S

- [ ] P2 — Spanish copy carries orthography errors and incomplete plural forms
  Category: ux
  Where: `app/src/main/res/values-es/strings.xml:576`, `:1589`, `:1763`, `:1838`, plus 32 `<plurals>` blocks flagged by lint.
  Problem: The only translated locale is 100% key-complete but has spelling defects in visible strings, including two missing accents that change the word. This is the locale a Spanish store listing would ship against.
  Evidence: `:app:lintDebug` flags each with `[Typos]`. `:576` `settings_model_error` is `necesita atencion` (should be `atención`; the English source is "Needs attention", so the sentence case is also lost). `:1838` `auto_edit_info_music` is `musica` (should be `música`). `:1589` `panel_auto_edit_script_label` and `:1763` `panel_tts_script_title` use `Guión`, which the RAE reclassified as monosyllabic — current orthography is `Guion`. Lint additionally reports 32 `[MissingQuantity]` warnings that Spanish plurals omit the `many` form; impact is low because Android falls back to `other`, but it leaves the locale formally incomplete. The `AV1` -> `AV!` suggestions in the same lint category are false positives and should be ignored.
  Fix: Correct the four strings. Add the `many` quantity to the flagged Spanish plurals, or suppress `MissingQuantity` in `lint.xml` with a comment recording that `other` is the intended fallback for `es` — but pick one rather than leaving 32 standing warnings.
  Acceptance: `:app:lintDebug` reports no `[Typos]` in `values-es` other than the `AV1` false positives, and no `[MissingQuantity]` warnings remain unaddressed. Cross-reference: this is copy quality in the existing locale, distinct from the existing P1 "Close reachable localization and accessibility gaps" item, which covers hard-coded English in `ui/`.
  Confidence: Verified
  Effort: S

- [ ] P2 — Batch media import misreports a full disk, cannot be cancelled, and spins indeterminately
  Category: ux
  Where: `ui/mediapicker/MediaPicker.kt:128-178`, `:599-640` (`MediaImportStatusCard`); `engine/MediaIngestWorker.kt:48-58` (the richer result type that already exists).
  Problem: Three gaps in the same flow, all visible on a long import. Running out of space is reported as a generic copy failure, even though the check knows the exact shortfall. The progress card counts files but drives an indeterminate spinner, so it still cannot distinguish slow from stuck at the level that matters. And a forty-file import has no cancel.
  Evidence: `:141-143` computes `hasSpace` from `checkFreeSpace(context, totalSize)`; when false, `:160-164` releases the URI permissions and falls through with `mediaItems` empty, so `:168-170` sets `permissionMessage = R.string.media_picker_local_copy_failed` — the same message as a decode failure, with no mention of storage. The machinery for a better message exists elsewhere: `MediaIngestWorker` models `IngestResult.InsufficientSpace(requiredBytes, availableBytes)` (`:48-58`). `MediaImportStatusCard` at `:610` uses `CircularProgressIndicator` with no `progress` argument even though `operation.completed` and `operation.total` are populated at `:145-148`, and the card takes no cancel callback. The v3.78.0 change to import one file at a time on the caller makes cancellation straightforward, since the loop is now interruptible.
  Fix: Return a typed outcome from the space check and show a message naming the shortfall, matching the `IngestResult.InsufficientSpace` shape. Pass `completed / total` to the determinate `CircularProgressIndicator(progress = ...)` overload. Add a cancel action to the card that cancels the enclosing coroutine and releases the remaining persisted URI permissions on the way out.
  Acceptance: A batch import that exceeds free space reports how much is needed; the card shows determinate progress for a multi-file import; cancelling mid-import stops further work and leaks no persisted URI grants.
  Confidence: Verified
  Effort: S

- [ ] P2 — Two AI tools show another tool's description
  Category: ux
  Where: `ui/editor/AiToolsPanel.kt:179-188` (`frame_interp`), `:134-140` (`face_track`); `app/src/main/res/values/strings.xml:462`, `:452`.
  Problem: The AI tools grid describes Frame Interpolation as the upscaler. A user reading the card is told the tool does something it does not do.
  Evidence: `AiToolsPanel.kt:182` passes `R.string.ai_tool_ai_upscale_desc` as the description for the `frame_interp` entry; the same resource is used correctly by the `video_upscale` entry at `:172`. `strings.xml:462` defines it as "Upscale video with Real-ESRGAN". Separately, `:137` gives the `face_track` entry `R.string.ai_tool_track_motion_desc` ("Track objects across frames", `strings.xml:452`), which is generic rather than wrong; there is no face-specific description string.
  Fix: Add `ai_tool_frame_interp_desc` and `ai_tool_face_track_desc` to `values/strings.xml` and `values-es/strings.xml` (parity is enforced), and reference them from the two entries. A short assertion that no two `AiToolConfig` entries share a description resource would prevent the copy-paste from recurring.
  Acceptance: Each AI tool card describes its own tool; both new keys exist in both locales.
  Confidence: Verified
  Effort: S

- [ ] P2 — No line-ending normalization, so releases produce thousand-line phantom diffs
  Category: maintainability
  Where: `.gitattributes` (single line, `*.aar binary`); 9 tracked CRLF source files and 2 mixed.
  Problem: Without an `eol` rule, files carry whichever line ending the last tool used, and a routine edit can rewrite an entire file. v3.78.0 did exactly that: `ProjectListScreen.kt` shows 3501 changed lines in the release diff, where the substantive change was on the order of thirty. Review and `git blame` are both defeated for those files, and the next such flip is unpredictable.
  Evidence: `git show e027878:app/src/main/java/com/novacut/editor/ui/projects/ProjectListScreen.kt` is 1740 CRLF / 1740 LF (fully CRLF); at `8ca774c` it is 0 CRLF / 1761 LF. `Theme.kt` went from 130 CRLF to 0. `git diff --stat e027878..8ca774c` reports `ProjectListScreen.kt | 3501 +++---` accordingly. Surveying the tree today: 692 LF files, 9 fully-CRLF files (`engine/PrivacyDashboard.kt`, `engine/SubtitleExporter.kt`, `ui/editor/ToolPanel.kt`, `ui/theme/Tokens.kt`, `app/src/test/.../ClipTimingTest.kt`, three Fastlane changelogs, `gradle/verification-metadata.xml`) and 2 mixed (`ui/theme/AppChrome.kt`, `app/src/test/.../SubtitleExporterTest.kt`).
  Fix: Add `* text=auto eol=lf` to `.gitattributes`, keeping the existing `*.aar binary` line and adding binary rules for other committed binaries (`*.png`, `*.jks`, `*.onnx`, `*.ttf`). Then renormalize once in a single commit that touches nothing else (`git add --renormalize .`), so the churn is isolated and reviewers can skip it.
  Acceptance: `git ls-files --eol` reports `lf` for every text file; a subsequent single-line source edit produces a single-line diff.
  Confidence: Verified
  Effort: S

- [ ] P2 — The Settings consumer ratchet has no negative fixture and accepts a read-and-discard
  Category: testing
  Where: `app/src/test/java/com/novacut/editor/engine/SettingsConsumerRatchetTest.kt:30-60`.
  Problem: The ratchet exists to catch settings that persist and change nothing, but it only checks that the token `.propertyName` appears somewhere outside two excluded files. A property that is read into an expression whose result is then overridden or ignored counts as consumed, which is the same defect wearing a reference. Unlike the sibling ratchet in the same package, it has no test proving it can fail, so a future refactor that breaks the scan would read as a pass.
  Evidence: `:50-53` builds `orphans` from `Regex("\\.$property\\b")` matched against file text. The P2 finding "The Settings default aspect ratio reaches only the output filename" above is a live instance: `.defaultAspectRatio` appears at `EditorViewModel.kt:1131`, so the ratchet passes, yet every consumer overrides the value and the setting changes nothing about the export. Compare `RedactedLoggingRatchetTest.theRatchetActuallyDetectsAViolation` (`engine/RedactedLoggingRatchetTest.kt:59-66`), which asserts the scan flags a known-bad line and clears a known-good one; `SettingsConsumerRatchetTest` has no equivalent.
  Fix: Add a negative fixture in the style of the redaction ratchet — run the orphan scan over a synthetic source set containing one consumed and one unconsumed property and assert it reports exactly the unconsumed one. Extract the scan into a testable helper to make that possible. The read-and-discard hole cannot be closed by text matching at all; note that limitation in the KDoc so the next reader does not over-trust it, and treat behavioural coverage as the real fix under the existing P2 "Replace source-text ratchet tests" item.
  Acceptance: The ratchet fails on a fixture whose property has no consumer, and its KDoc states what it cannot detect.
  Confidence: Verified
  Effort: S

### P3 — Under Consideration

- [ ] P3 — `isTemplateRestorable` has no callers
  Category: maintainability
  Where: `engine/TemplateManager.kt:157-158`.
  Problem: Added in v3.78.0 alongside the template trash and never wired to anything, so the restore affordance decides what to offer from `_restorableTemplate` state instead. Small, but it is new dead public API in a codebase that already carries a P2 item for deleting ~110 KB of unreachable engine code.
  Evidence: `grep -rn "isTemplateRestorable" app/src` returns only the declaration — no call site in `app/src/main`, `app/src/test`, or `app/src/androidTest`.
  Fix: Delete it, or use it in `ProjectListViewModel.restoreDeletedTemplate` (`ui/projects/ProjectListViewModel.kt:436-451`) to check the file is still there before offering the restore, which would also let the offer expire correctly if the trash is pruned by the retention item above.
  Acceptance: The function is either removed or has a caller.
  Confidence: Verified
  Effort: S

- [ ] P3 — Unaudited surfaces needing their own pass
  Category: docs
  Where: n/a — scope note for the next audit.
  Problem: This pass did not cover the following, and no conclusion should be drawn about them from its findings.
  Evidence: Nothing was run on a physical device (`adb devices` is empty on this machine), so no visual, gesture, TalkBack, rotation, multi-window, font-scale or RTL behaviour was observed — every UI finding here was derived from source and from the lint report. Not read in depth: `engine/FFmpegEngine.kt`, `engine/VideoEngine.kt` composition assembly beyond the thumbnail cache and failure-cause plumbing, the colour-grading and audio-effect engines, `ui/editor/Timeline.kt` gesture handling, `baselineprofile/`, `third_party/`, the androidTest source set, and `gradle/verification-metadata.xml`. Theme coverage was limited to confirming that only dark schemes exist (`ui/theme/Theme.kt` has no `lightColorScheme`), so no second theme could be compared; `HIGH_CONTRAST_DARK` was not exercised. No contrast ratios were measured.
  Fix: Run the next pass on a device, driving the editor, export, settings, media picker and project dashboard in both `DARK` and `HIGH_CONTRAST_DARK`, with TalkBack and 200% font scale.
  Acceptance: The surfaces listed above have been observed running, and any findings are logged here.
  Confidence: Verified
  Effort: M

## Research-Driven Additions — 2026-08-02 (fourth pass)

Sourced from `RESEARCH.md` (2026-08-02), run against the v3.78.0 tree at commit `1993ff2`. This pass
covered what the previous three did not reach: the GPU effect layer, the accessibility of drag-only
editing surfaces, the actual contents of the Media3 overlay shader, the current CVE state of the
vendored native artifact, and the developer-verification calendar. Where research only sharpened an
existing item, an inline `Update`/`Correction` note was added to that item above instead of a new
entry here.

### P0 — Now

- [ ] P0 — Create an AVD; the "no emulator" blocker behind nine parked items is false
  Why: Nine of the twelve P1 items in `Roadmap_Blocked.md` are parked on the claim that no emulator exists on this workstation. The emulator and an API 37 system image are installed; only an AVD definition is missing, so one command converts a whole blocked class into runnable work.
  Evidence: `Roadmap_Blocked.md` states "`emulator` is not installed on 2026-08-02" at lines 17, 138, 142, 146, 150, 155 and 160, and reports `adb devices -l` empty elsewhere. Actual state on 2026-08-02: `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` reports version 36.6.11.0; `cmdline-tools/latest/bin/avdmanager.bat` is present; system images exist for android-26, android-35, android-36, android-36.1 and **android-37.0** (`google_apis_ps16k/x86_64`); `~/.android/avd` is empty. Unblocks: the Room 1-to-9 migration matrix, API 37 device acceptance, the accessibility/interaction matrix, rendered localization (en-XA/ar-XB, TalkBack at 200% font scale), preview/export parity, the four failure-class fixtures, the `bmgr` backup matrix, the AGP 9 benchmark lane and baseline-profile collection. Genuine exceptions that still need hardware: HDR encode and real per-device codec ceilings — `getMaxSupportedInstances()` on an emulator is not evidence about a phone. Leave those two parked and say so.
  Touches: `Roadmap_Blocked.md` (correct the premise on every affected row), `scripts/` (an AVD provisioning and launch script so this is reproducible), `CLAUDE.md`.
  Acceptance: A named API 37 AVD exists and boots; `adb devices` lists it; `:app:connectedDebugAndroidTest` runs the four existing androidTest classes to completion; each affected row in `Roadmap_Blocked.md` is either unparked or re-blocked with a reason that is true.
  Complexity: S

### P1 — Next

- [ ] P1 — Stop the GPU effect layer from degrading silently while the export reports success
  Why: A fragment shader that fails to compile is replaced with a pass-through and a segmentation failure is replaced with a neutral mask, both logged only, both inside the export composition — so the user applies an effect, the export reaches COMPLETE, and the effect is simply absent from the file. This is the exact class of dishonesty the last 118 fix-commits were spent eliminating everywhere else.
  Evidence: `engine/ShaderEffect.kt:124-129` catches a `RuntimeException` from the fragment-shader compile, logs it, and compiles `FRAG_PASSTHROUGH` instead, with no flag, counter or caller-visible signal; `glProgram` is non-zero afterwards so nothing downstream can tell. `engine/segmentation/SegmentationGlEffect.kt:90-103` catches a `segment()` failure, logs at warn, and runs `uploadFallbackMask()` for that frame; the same happens at `:105` whenever the segmenter is absent. `engine/VideoEngine.kt` reaches `ExportState.COMPLETE` at `:788`, `:1139` and `:2180` without consulting either. `ShaderEffect.kt` is 1592 LOC with zero tests. Contrast with the existing honesty machinery — `AnalysisOutcomeHonestyTest` and the v3.78.0 export failure-cause work police exactly this distinction on other paths.
  Touches: `engine/ShaderEffect.kt`, `engine/segmentation/SegmentationGlEffect.kt`, `engine/VideoEngine.kt` (export result assembly), `engine/ProductHealthLedger.kt`, `ui/export/ExportSheet.kt`, a new `app/src/test/.../ShaderEffectTest.kt`.
  Acceptance: A shader compile failure and a segmentation failure each surface a typed outcome the export result carries; an export in which any effect degraded reports which effect and on how many frames rather than reporting plain success; a unit test proves the degraded outcome is produced and propagated, injecting the compile step so no GL context is needed. No effect failure may reach `COMPLETE` unannounced.
  Complexity: M

- [ ] P1 — Keep HDR for text and gain-mapped bitmap overlays instead of forcing every overlay to SDR
  Why: The blanket SDR fallback is stricter than Media3 requires. Text overlays work on HDR content today with no extra work at all, and image and watermark overlays work when the bitmap carries a gain map — so users lose HDR on every overlay for a constraint that applies to only some of them.
  Evidence: `OverlayShaderProgram.findHdrTypes()` read at tag 1.10.1 — `TextOverlay` maps to `HDR_TYPE_TEXT` (one sampler), `BitmapOverlay` maps to `HDR_TYPE_ULTRA_HDR` (two samplers, `checkState(SDK_INT >= 34)`, gain map required), and anything else throws "is not supported on HDR content"; SDR is capped at 15 overlays, HDR by sampler budget. Media3 1.4.0 release notes add Ultra HDR bitmap overlays, HDR support for `TextOverlay`, and `OverlaySettings.Builder.setHdrLuminanceMultiplier()`, and removed `OverlaySettings.useHdr`. ClearCut has zero references to `Gainmap` or `hasGainmap` anywhere in `app/src/main`, and `engine/Media3LottieTextureOverlay.kt:31-36` documents the older belief. Cross-reference: `Roadmap_Blocked.md` "Verify HDR overlay fallback and HDR encode on an Android device" verifies the *current* fallback; this item narrows what that fallback has to cover, so sequence this first.
  Touches: `engine/VideoEngine.kt:1524-1525` (overlay assembly), `engine/Media3LottieTextureOverlay.kt`, the overlay-to-`BitmapOverlay` conversion for text, image and watermark, the HDR preflight disclosure copy, `engine/EncoderCapabilityProbe.kt`.
  Acceptance: An HDR export carrying only text overlays preserves HDR with no disclosure of an SDR fallback, because none occurs; image and watermark overlays preserve HDR on API 34+ when rendered with a gain map, and fall back with a disclosure only below 34 or when a gain map cannot be produced; Lottie keeps its existing route; the sampler budget is enforced with a named error rather than an `IllegalArgumentException` reaching the user.
  Complexity: M

- [ ] P1 — Give the four drag-only editing surfaces an accessibility path
  Why: Crop/transform, preview pan-zoom, the colour curve and the keyframe bezier are operable only by dragging, which makes them unusable with TalkBack or Switch Access and puts them in direct violation of WCAG 2.2 SC 2.5.7; the fix pattern already exists in this repo.
  Evidence: zero occurrences of `semantics` in `ui/editor/TransformOverlay.kt` (gesture at `:64`, `detectTransformGesturesWithLifecycle`), `ui/editor/PreviewPanel.kt` (`:174`), `ui/editor/ColorGradingPanel.kt` (`:310`) and `ui/editor/KeyframeCurveEditor.kt` (`:613`) — the `contentDescription`s present in those files belong to neighbouring icon buttons, not to the gesture surface. `ui/editor/Timeline.kt:42-50,139,175` is the in-repo template: `CustomAccessibilityAction`, `customActions`, `stateDescription`, `selected`, `role`. WCAG 2.2 SC 2.5.7 (AA) requires a single-pointer non-drag alternative; SC 2.5.8 sets a target-size floor, against which two sub-48dp targets remain — `ui/editor/EditorScreen.kt:1904-1906` (36dp `IconButton`) and `ui/editor/RadialActionMenu.kt:126` (40dp clickable). `minimumInteractiveComponentSize()` appears zero times in `ui/`.
  Touches: the four files above, `ui/theme/Tokens.kt` (`TouchTarget.minimum`), `res/values/strings.xml` and `res/values-es/strings.xml` (action labels, parity enforced), `ui/editor/EditorScreen.kt`, `ui/editor/RadialActionMenu.kt`.
  Acceptance: Each of the four surfaces exposes a semantics node with a `stateDescription` naming its current value and custom actions covering every operation reachable by drag; the two sub-48dp targets meet the token minimum; the assertions run as Robolectric/Compose tests rather than waiting on a device.
  Complexity: M

- [ ] P1 — Turn on the JVM verification capacity that is already paid for and never runs
  Why: Roborazzi is declared and unused, and the only accessibility assertions in the repo sit in an androidTest class no device has executed — yet both Roborazzi and Compose accessibility checks run on the JVM under Robolectric. This is the cheapest available answer to "zero Compose UI unit tests" and it needs no hardware.
  Evidence: `roborazzi = "1.40.0"` in `gradle/libs.versions.toml` and the plugin and dependency at `app/build.gradle.kts:457-459`, with zero source references and no committed goldens; `app/src/androidTest/.../ClearCutSmokeTest.kt:28,34,44` calls `enableAccessibilityChecks()`, `tryPerformAccessibilityChecks()` and `assertAccessibilityChecksPass()`, but the androidTest set is four classes and has never been run here. `app/build.gradle.kts:139` sets `unitTests.isReturnDefaultValues = true`, which a Robolectric lane replaces. Upstream Roborazzi is 1.70.0; `androidx.compose.ui:ui-test-junit4-accessibility` is BOM-managed and already on the classpath; `AccessibilityValidator().setThrowExceptionFor(WARNING)` tightens the checks.
  Touches: `app/build.gradle.kts` (Robolectric/Roborazzi test lane, `testOptions`), `gradle/libs.versions.toml`, `app/src/test/` (new screenshot and accessibility tests), a goldens directory, `.gitattributes` (binary rule for goldens).
  Acceptance: A JVM task records and compares Roborazzi goldens for the project dashboard, editor, export sheet and settings in both `DARK` and `HIGH_CONTRAST_DARK`; accessibility checks run in the same lane and fail on a missing label, an undersized target or a contrast violation; a deliberately broken layout fails the lane, proving it can. Surfaces that genuinely need a device stay out of scope and are named as such.
  Complexity: M

### P2 — Later

- [ ] P2 — Delete `getVideoFrameRate`, or make it work and use it
  Why: It returns 30 for effectively every input, its comment describes a fallback that is not in the body, and nothing calls it — so it is dead code that reads as a working frame-rate probe to the next person who needs one.
  Evidence: `engine/VideoEngine.kt:460-471` reads `METADATA_KEY_CAPTURE_FRAMERATE`, which is null for any recording that is not slow-motion; the comment says to fall back to parsing bitrate and no such branch exists; searching `app/src` for `getVideoFrameRate` returns only the declaration. The project migrated to rational frame rates in Room schema 9, so a real probe would have somewhere correct to land.
  Touches: `engine/VideoEngine.kt`, any new caller, `app/src/test/`.
  Acceptance: The function is removed, or it derives the frame rate from the track format (`MediaFormat.KEY_FRAME_RATE` via `MediaExtractor`, with `METADATA_KEY_CAPTURE_FRAMERATE` used only for the slow-motion case), has a caller, and has a test with a non-slow-motion fixture.
  Complexity: S

- [ ] P2 — Make the "New Project" launcher shortcut do what it says, and fix shortcuts in the streaming variant
  Why: The shortcut is labelled "Start a fresh edit" and opens the gallery instead, and both static shortcuts are inert in the `streaming` build type because the manifest hardcodes the release package name.
  Evidence: `MainActivity.kt:189-195` — the `ACTION_NEW_PROJECT` and `ACTION_OPEN_RECENT` branches contain comments and no code. `res/values/strings.xml:1800-1801` carries the shortcut label. `res/xml/shortcuts.xml:22,35` sets `android:targetPackage="com.novacut.editor"` while `app/build.gradle.kts:89` gives the `streaming` build type `applicationIdSuffix = ".streaming"`, and the activity declares `android:intentMatchingFlags="enforceIntentFilter"`, so the intent cannot match in that variant.
  Touches: `MainActivity.kt`, `res/xml/shortcuts.xml`, `app/build.gradle.kts` (manifest placeholder for the application id), `engine/ProjectShortcutPlanner.kt`.
  Acceptance: Tapping New Project opens the new-project flow and Open Recent opens the most recent project; `targetPackage` resolves from a manifest placeholder so both shortcuts work in every build type; a test asserts the two intent actions are handled rather than falling through.
  Complexity: S

- [ ] P2 — Restore meaning to the dependency pins
  Why: Two pins sit in the fixture with no recorded reason, two more are held by an AGP ceiling that no longer exists, and one is a pre-release inside the gate that produces release evidence — so the freshness test currently proves only that the catalog has not changed, not that any pin is still justified.
  Evidence: `app/src/test/java/com/novacut/editor/DependencyFreshnessTest.kt:27-46` lists `media3 = 1.10.1` and `lifecycle = 2.10.0` in `expectedVersions` while `HOLDS` (`:56-84`) covers only `coil`, `lottieCompose`, `onnxruntime` and `protobufJavalite`; the KDoc at `:53` says dependencies absent from that map are assumed to be at latest verified, which `coreKtx = 1.16.0` and `activity = 1.10.1` are not — they were capped by AGP 8.7.3, and the catalog now reads `agp = "9.1.1"`. `androidxBenchmark = "1.5.0-beta01"` while 1.4.1 is the stable line. Upstream as of 2026-08-02: AGP 9.3.1 (requires Gradle 9.5.0 or newer), Gradle 9.6.1, Compose BOM 2026.06.01, ONNX Runtime 1.28.0, androidx.sqlite 2.7.0, Roborazzi 1.70.0. Media3 1.11.0 is still rc01 — keep waiting, and record *that* as a hold rather than as a bare fixture value.
  Touches: `gradle/libs.versions.toml`, `app/src/test/java/com/novacut/editor/DependencyFreshnessTest.kt`, `gradle/verification-metadata.xml`, `baselineprofile/`.
  Acceptance: Every pin below its upstream latest has a `HOLDS` entry naming the reason and the condition for lifting it; `coreKtx` and `activity` are either raised or given a hold that is true today; `androidxBenchmark` moves to the stable line or records why a beta is required; the AGP and Gradle bump is evaluated as its own change with the suite and verification metadata green.
  Complexity: S

- [ ] P2 — Probe encoder HDR capability instead of inferring it
  Why: `MediaCodecInfo.CodecCapabilities` declares exactly whether an encoder can do HDR editing, and ClearCut asks it nothing — so an HDR export the device cannot perform is discovered at encode time rather than at preflight.
  Evidence: `engine/EncoderCapabilityProbe.kt` contains no `FEATURE_` or `isFeatureSupported` reference; it probes codec existence and profiles only. AOSP `MediaCodecInfo` exposes `FEATURE_HdrEditing` (RGBA_1010102 and P010 input, generates HDR metadata), `FEATURE_HlgEditing` (10-bit HLG surface, no metadata), `FEATURE_DetachedSurface` (decoder configurable without a Surface, making preview-to-export hand-off cheaper) and `FEATURE_Roi` (per-region QP offsets). Related: `Build.VERSION.MEDIA_PERFORMANCE_CLASS` gives a tested vocabulary for default export resolution and preview track count in place of a device blocklist.
  Touches: `engine/EncoderCapabilityProbe.kt`, `engine/CapabilityRegistryGenerated.kt` and `scripts/generate_capability_registry.py`, the export preflight disclosure, `engine/DiagnosticExportEngine.kt`.
  Acceptance: HDR export is offered only where `FEATURE_HdrEditing` or `FEATURE_HlgEditing` is reported for the chosen encoder, and refused at preflight with a device-capability explanation otherwise; the probed features appear in the diagnostics bundle.
  Complexity: S

- [ ] P2 — Escape the remaining filtergraph metacharacters
  Why: `escapeFilterPath` handles three of the characters FFmpeg's filtergraph parser treats specially and misses four, so a path containing them would split the chain or be read as a link label.
  Evidence: `engine/FFmpegEngine.kt:685-690` replaces backslash, colon and apostrophe only; open bracket, close bracket, comma and semicolon pass through. It is used at `:693` by `subtitleFilter`, reached from `burnSubtitles` (`:321`, called at `ui/editor/ExportDelegate.kt:1279`). Reachability is currently low because the subtitle file is app-generated in `filesDir`, which is why this is P2 rather than higher — but `app/src/test/.../FFmpegEngineTest.kt` already tests this function, so each fixture is one line.
  Touches: `engine/FFmpegEngine.kt`, `app/src/test/java/com/novacut/editor/engine/FFmpegEngineTest.kt`.
  Acceptance: A path containing brackets, a comma and a semicolon round-trips through `subtitleFilter` without altering the filter graph, proven by test.
  Complexity: S

- [ ] P2 — Add a per-clip audio sync offset
  Why: It is the standard remedy for the drift class this project already knows about, it is the highest-signal request in the surveyed trackers that ClearCut does not already implement, and every other piece of the timing model is in place.
  Evidence: searching `app/src/main/java/com/novacut/editor/model/` for `offsetMs`, `audioOffset` and `syncOffset` returns nothing — the only `delayMs` is an echo-effect parameter (`Effect.kt:230`). lossless-cut #216, "delay/advance individual tracks by plus or minus seconds", carries 16 reactions; androidx/media #921 documents the progressive desync produced by a 3000 ms video track against a 3030 ms audio track, reported against all devices, and `Roadmap_Blocked.md` already plans a fixture for exactly that mismatch. Dual-system sound — separately recorded audio — has no remedy in the app today.
  Touches: `model/Timeline.kt` and `model/Project.kt` (clip field), `engine/ProjectAutoSave.kt` (envelope and schema version), `ui/editor/AudioMixerPanel.kt` or the clip inspector, `engine/VideoEngine.kt` composition assembly, `engine/AudioEngine.kt` mixdown, undo.
  Acceptance: A clip carries a signed audio offset in milliseconds that survives save and load, is frame-quantized consistently with the rest of the timeline, and produces identical alignment in preview, video export, audio mixdown and stems; the control is undoable and reports the offset in a readable unit.
  Complexity: M

- [ ] P2 — Open a translation contribution path
  Why: The only open issue on the repository asks for translations, and the two conditions that previously justified deferring — key parity and localizability — are met for the locale that exists.
  Evidence: issue #52, "Translations wanted: Spanish is currently the only one", is the sole open issue. `res/values/strings.xml` and `res/values-es/strings.xml` both carry 2639 `<string>` entries with `LocaleResourceCoverageTest` enforcing parity, and `res/xml/locales_config.xml` declares `en` and `es`. The blocker is narrower than recorded: `UiHardcodedLiteralRatchetTest.kt:18-26` polices eight named files and budgets 13 literals across three of them, so "the reachable UI ratchet is at zero" is true only inside that allowlist — every other file under `ui/` is unbounded by it. The prior rejection of more production locales before localizability closure was correct at the time; this item supersedes it.
  Touches: `app/src/test/java/com/novacut/editor/UiHardcodedLiteralRatchetTest.kt` (widen to the whole `ui/` tree with a measured starting budget), a translator guide in `docs/` naming the parity test as the gate, `fastlane/metadata/android/`, `res/xml/locales_config.xml`.
  Acceptance: The literal ratchet covers every file under `ui/` at a measured, non-growing budget; a documented path exists for a contributor to add a locale and know whether it passes before opening a pull request; issue #52 has an answer that is a process rather than a promise.
  Complexity: S

### P3 — Under Consideration

- [ ] P3 — Make the archive size estimate count what it could not read
  Why: An unreadable dependency contributes zero to the pre-archive size estimate, so a storage preflight can pass on a number that is too low and the write then fails for space.
  Evidence: `engine/ProjectArchive.kt:867` has an empty `catch (_: Exception) { }` commented "Skip unreadable files", inside the accumulation loop of `estimateArchiveSize` (`:849`); the result is consumed at `ui/editor/EditorViewModel.kt:2660`. Note for the next auditor: this reads at a glance like an archive being *written* incomplete and reported complete — it is not, the write path is elsewhere, and the 2026-08-02 pass corrected itself on exactly this point. The neighbouring `engine/ExportOutputVerifier.kt:185-191` conflation of an absent `MediaFormat` key with a zero value is in the same category — real, but the verifier reports genuine read failures with a reason at `:178` and takes the maximum `KEY_DURATION` across tracks at `:83,:88`, so one missing key is absorbed. Neither belongs above P3.
  Touches: `engine/ProjectArchive.kt`, `ui/editor/EditorViewModel.kt`, `engine/ExportOutputVerifier.kt`.
  Acceptance: An estimate that could not read one or more dependencies reports that it is a lower bound and names the count, so the preflight can refuse rather than under-promise; the verifier distinguishes an absent duration key from a zero duration.
  Complexity: S

- [ ] P3 — Import embedded CEA-608/708 captions
  Why: Recorded broadcast and screen-capture footage carries captions inside the video track that ClearCut cannot see, and ExoPlayer already surfaces them as text tracks — so the decode work is done and only the wiring is missing.
  Evidence: no reference to CEA-608, CEA-708 or text-track selection anywhere in `app/src/main`; the only `selectTrack` calls (`engine/AudioEngine.kt:148,324`, `engine/MultiCamEngine.kt:188`, `engine/StreamCopyMuxer.kt:123`) are audio and stream-copy paths. `engine/SubtitleExporter.kt` and the caption model already handle everything downstream of extraction. Gate on evidence that target users have such footage — this is reach, not a known request.
  Touches: `engine/MediaImportEngine.kt`, the caption import path, `engine/SubtitleExporter.kt`, import disclosure copy.
  Acceptance: A source carrying embedded CEA-608/708 captions offers to import them as an editable caption track, and a source without them says so rather than silently offering nothing.
  Complexity: M
```

</details>

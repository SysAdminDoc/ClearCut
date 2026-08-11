# Changelog

## v3.78.1

- Added offline hardware-aware stabilization: bounded platform motion analysis with low-RAM and thermal gates, live progress and cancellation, source-preserving review/apply UI, persistent motion/lens/sync/crop data, shared preview/export transforms, and API 37 device acceptance.
- Completed the AGP 9/API 37 acceptance lane: the managed Pixel 6 benchmark/profile tasks, detector ratchet, lint, advisory-floor, debug, and baseline-profile package gates are green; the connected QA baseline remains 17 tests with seven known emulator codec, accessibility-tree, preview, and migration-fixture failures.
- Completed LaMa object-removal acceptance on the API 37 AVD with the pinned 174 MiB model: still inference and an audio-bearing video both produced usable outputs, and FFmpeg now rejects zero-sample hardware artifacts before retrying with its MPEG-4 software floor.
- Completed the Room v1–v10 migration matrix on the API 37 AVD; each hop now preserves the project row, frame-rate values, media-asset table/indexes, and v10 manifest columns, including a corrected v9 fixture for the non-null frame-rate pair.
- Completed Android 17/API 37 acceptance: the corrected `cmd audio set-hardening throw` policy smoke, media-processing foreground-service start, audio export, 2400×1080 resizability launch, debug/streaming packages, Room matrix, managed Pixel 6 benchmark, and baseline-profile collection all passed; emulator-suppressed profile cases remain explicitly skipped.
- Added API 37 device acceptance for dense-cut thumbnail memory, a software 1080p-to-720p render, two trims from one source window, 3000ms/3030ms A/V alignment, and rejection of short or empty outputs before `COMPLETE`.
- Completed the API 37 headless Android system-backup matrix: a large project restored its autosave/database scope while generated media stayed excluded from the cloud scope, oversized autosave data hit the transport quota, and disabling backup blocked new backup requests and reinstall restore.
- Added a headless JVM visual verification lane for the dashboard, editor, export sheet, and settings in dark and high-contrast dark modes, with committed Roborazzi goldens and strict accessibility checks.
- Added a secret-free GitHub Actions PR/master gate covering JVM tests, lint, QA/debug packaging, package/release identity, dependency/native SBOM checks, 16 KB alignment, deterministic artifact hashes, and build provenance evidence.
- Upgraded the pinned Media3 runtime to 1.11.0 after the QA compatibility probe, full release-gate matrix, generated dependency evidence, and APK alignment checks passed.
- HDR export overlays now keep native text and API 34+ gain-mapped stills in the HDR compositor; unsupported bitmap paths disclose their SDR fallback, preserve gain maps through scaling, and fail early with a named sampler-budget error.
- Transform, preview pan/zoom, colour curves, and keyframe curves now expose localized state descriptions and non-drag accessibility actions for TalkBack and Switch Access; editor action targets use the shared 48dp token.
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
# ClearCut Roadmap

Current version: **v3.78.1** (`versionCode` 296). Last deep audit:
2026-07-17.

`ROADMAP.md` contains only work that can be implemented in the local build
environment. Completed work belongs in git history and `CHANGELOG.md`; research
context belongs in `RESEARCH.md`; blocked or operator-gated work belongs in
`Roadmap_Blocked.md`.

## Active Queue

Deferred findings from the 2026-07-17 deep audit (v3.74.157). Verified real,
not fixed this pass — mostly larger mechanical work or UI polish.

## Blocked Queue

Blocked items were moved to `Roadmap_Blocked.md`. Move an item back into this
file only after the blocker clears and the next implementation step can be
verified locally.

## Research-Driven Additions

## Research-Driven Additions

## Audit Backlog (2026-07-12)

Deferred findings from the deep engineering/QA audit. Verified but not fixed in
that pass — either ambiguous product behavior, a larger refactor, or negligible
practical impact.

## Research-Driven Additions

## Research-Driven Additions (2026-07-14)

## Deep Audit Backlog (2026-07-14)

Verified findings from the deep engineering audit that were not fixed in that
pass (larger refactors, ambiguous product behavior, or device-gated verification).

## Research-Driven Additions

### P1 — Next

### P2 — Later


## Research-Driven Additions (2026-07-22)

Reinforces existing items: this pass re-confirms the open P1 "Make public feature claims an executable capability contract" (extend it to cover the blend-mode and bitrate-mode gaps below), and the P2 Media3 export items (edit-list trim fast-path, CodecDB-Lite/rounding/fps) and Compose-1.9 scroll-perf item — all still valid against the 2026 Media3 1.8–1.10 / Compose 1.10 release notes. `SmartRenderEngine`, `StabilizationEngine`, chroma key, motion tracking, `autoDuck`, loudness normalization, and on-device `WhisperEngine` captions already exist — do NOT re-add them as new features. ONNX Runtime is already 1.26.0 (no CVE bump needed). No numeric ID scheme in this file; items stay unnumbered.

### P1 — Now

### P2 — Next

### P3 — Later

## Research-Driven Additions

- [ ] P2 — Give media scanning an explicit failure and retry state
  Why: The outer media scan exposes only an analyzing boolean and per-URI resolver exceptions are absorbed, so a provider-wide failure can leave users without a clear retry path or an explanation of partial results.
  Evidence: `app/src/main/java/com/novacut/editor/ui/media/MediaManagerPanel.kt:95-108,1073-1165`; the current scan catches resolver failures per URI but has no typed terminal error state or retry action.
  Touches: `MediaManagerPanel.kt`, media scan state/diagnostics, strings, cancellation handling, and unit/Compose tests.
  Acceptance: The UI distinguishes idle, scanning, ready-with-partial-results, failed, and cancelled states; failed providers and skipped assets are counted with actionable detail; retry is explicit and idempotent; cancellation never leaves a permanent spinner; tests cover resolver failure, empty results, cancellation, and retry.
  Complexity: S

- [ ] P2 — Establish a Compose accessibility and font-scale matrix
  Why: Existing smoke tests cover pseudo-locale and RTL behavior, but there is no broad font-scale or large-screen matrix for dense export, batch, and media-manager surfaces. Compose semantics and state descriptions should be verified as part of the product’s accessibility contract.
  Evidence: `app/src/androidTest/java/com/novacut/editor/ClearCutSmokeTest.kt`; existing locale/resource and semantic-theme tests; official guidance at https://developer.android.com/develop/ui/compose/accessibility, https://developer.android.com/develop/ui/compose/accessibility/semantics, and https://developer.android.com/develop/ui/compose/testing/semantics.
  Touches: smoke/instrumentation tests, `ExportSheet.kt`, `BatchExportPanel.kt`, `MediaManagerPanel.kt`, semantics, and strings.
  Acceptance: Instrumentation covers wide layouts, 200% and 300% font scale, RTL, and pseudo-locales; no primary action, status, progress, or error is clipped or hidden; controls expose stable labels, roles, values, and state descriptions; the matrix runs in the invisible device test lane.
  Complexity: M

- [ ] P2 — Add a container and fast-start compatibility gate
  Why: The exporter accounts for MP4 `moov` size but does not assert atom order or clearly distinguish a stream-safe output contract from a merely playable file. Android’s format guidance makes codec/container combinations and streamed MP4 ordering explicit.
  Evidence: `app/src/main/java/com/novacut/editor/model/ExportConfig.kt:81`; `app/src/main/java/com/novacut/editor/engine/ExportOutputVerifier.kt`; https://developer.android.com/media/platform/supported-formats; Media3’s current muxer notes at https://developer.android.com/blog/posts/media3-whats-new?hl=en.
  Touches: `ExportOutputVerifier.kt`, container parser/policy, export diagnostics/share metadata, fixtures, and instrumentation tests.
  Acceptance: The output gate checks MP4 atom order and declared codec/container compatibility, reports when an output is playable but not stream-safe, and does not make an unverified live-streaming claim; fixtures cover valid and invalid `moov` placement and supported/unsupported audio combinations.
  Complexity: M

- [ ] P3 — Decompose the largest editor coordinators around stable seams
  Why: `EditorViewModel.kt`, `VideoEngine.kt`, `ExportDelegate.kt`, `Timeline.kt`, `ExportSheet.kt`, and `ProjectAutoSave.kt` remain high-churn, multi-thousand-line coordination points. Smaller pure state transitions and explicit interfaces would reduce regression risk while preserving the current architecture.
  Evidence: Repository line-count and recent-churn audit on 2026-08-08; existing seams in `ExportDelegate.kt`, `ProjectAutoSave.kt`, and `EditorViewModel.kt` provide bounded extraction points.
  Touches: `EditorViewModel.kt`, `VideoEngine.kt`, `ExportDelegate.kt`, `ProjectAutoSave.kt`, editor state/coordinator interfaces, and regression tests.
  Acceptance: Extract one bounded concern at a time behind narrow interfaces, preserve behavior and dependency direction, add focused state/contract tests before moving code, and demonstrate reduced coordinator responsibility without a broad rewrite or new architectural dependency.
  Complexity: XL
```

</details>

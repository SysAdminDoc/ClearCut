# Changelog

## Unreleased

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

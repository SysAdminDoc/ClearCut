# Changelog

## Unreleased

- Raised compact editor actions to 48dp semantic hit targets while keeping their visual geometry, with localized labels and radio-button semantics for text and glow swatches.
- Rebuilt the vendored FFmpegKitNext AAR under an LGPL-3.0-or-later profile, removed x264, disabled every currently reachable FFmpeg advisory surface with named runtime refusals, and upgraded the native lock/SBOM gate to require the full CVE inventory and reproducible security patch.
- Added a shared safe declarative-pack contract with schema migration outcomes, SHA-256 payload verification, executable-field rejection, provenance reporting, conflict previews, atomic installs, and one-step rollback across style/effect/LUT/font asset flows.
- Added a shared, device-ceiling-aware lease queue for decoder, retriever, preview-player, and proxy-pipeline work, with cancellation-safe contention tests and live counts in diagnostic bundles.
- Added conformant OTIO timing/metadata export, FCPXML and CMX 3600 import parsers, official-adapter validation tooling, and a fidelity/media-relink preview that commits accepted timelines through the canonical project document boundary.
- Moved timeline interchange export validation, serialization, naming, and atomic file writes behind a typed coordinator; the editor facade now owns only snapshot capture and localized feedback.
- Routed manual and periodic project writes through one persistence coordinator so Room metadata/assets and the canonical autosave document share a tested write boundary.
- Extracted pure composition track planning and shared Media3 composition assembly from `VideoEngine`, keeping preview and export on the same tested selection/build contracts.
- Moved mutable editor-state construction behind a tested `EditorStateStore`; the existing ViewModel/delegate APIs remain behavior-compatible while screens observe a read-only flow.
- Conformed the unsigned C2PA draft to 2.4 generator-info and actions-v2 metadata, removed the retired training-mining assertion, and made the no-signing/no-embed status invariant explicit in sidecars and UI copy.

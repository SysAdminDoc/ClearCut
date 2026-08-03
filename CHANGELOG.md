# Changelog

## Unreleased

- Added a shared, device-ceiling-aware lease queue for decoder, retriever, preview-player, and proxy-pipeline work, with cancellation-safe contention tests and live counts in diagnostic bundles.
- Added conformant OTIO timing/metadata export, FCPXML and CMX 3600 import parsers, official-adapter validation tooling, and a fidelity/media-relink preview that commits accepted timelines through the canonical project document boundary.
- Moved timeline interchange export validation, serialization, naming, and atomic file writes behind a typed coordinator; the editor facade now owns only snapshot capture and localized feedback.
- Routed manual and periodic project writes through one persistence coordinator so Room metadata/assets and the canonical autosave document share a tested write boundary.

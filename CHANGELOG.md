# Changelog

## Unreleased

- Added a shared, device-ceiling-aware lease queue for decoder, retriever, preview-player, and proxy-pipeline work, with cancellation-safe contention tests and live counts in diagnostic bundles.
- Added conformant OTIO timing/metadata export, FCPXML and CMX 3600 import parsers, official-adapter validation tooling, and a fidelity/media-relink preview that commits accepted timelines through the canonical project document boundary.
- Moved timeline interchange export validation, serialization, naming, and atomic file writes behind a typed coordinator; the editor facade now owns only snapshot capture and localized feedback.
- Routed manual and periodic project writes through one persistence coordinator so Room metadata/assets and the canonical autosave document share a tested write boundary.
- Extracted pure composition track planning and shared Media3 composition assembly from `VideoEngine`, keeping preview and export on the same tested selection/build contracts.
- Moved mutable editor-state construction behind a tested `EditorStateStore`; the existing ViewModel/delegate APIs remain behavior-compatible while screens observe a read-only flow.
- Conformed the unsigned C2PA draft to 2.4 generator-info and actions-v2 metadata, removed the retired training-mining assertion, and made the no-signing/no-embed status invariant explicit in sidecars and UI copy.

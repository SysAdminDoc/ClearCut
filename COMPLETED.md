# NovaCut Completed Work

Consolidated completion log for shipped roadmap work. The full chronological
history remains in [CHANGELOG.md](CHANGELOG.md).

Last consolidated: 2026-06-04.

## Current Delivered Baseline

- Current version: v3.74.14 (`versionCode` 151).
- Multi-track Android NLE with project gallery, editor, timeline, preview,
  effects, transitions, text, captions, audio, export, settings, templates,
  project archive/import, and diagnostic bundle surfaces.
- Privacy-first AI posture: on-device where practical, explicit download and
  model-state surfaces, AI-use ledger, local diagnostics, and opt-in cloud
  boundaries.

## Shipped Release Cohorts

| Area | Completed work |
|---|---|
| Core editing foundation | Multi-track timeline, trim/split/merge, speed/ramp controls, keyframes, overlays, stickers, markers, grouping, undo/redo, project search/sort/duplicate/delete, autosave, and recovery surfaces. |
| Effects and export | GPU shaders, 37 transitions, LUT/color tools, blend modes, masks, GIF/frame capture, platform presets, batch export, foreground export service, share/save-to-gallery, OTIO/FCPXML/EDL planning, subtitles, and export cleanup. |
| Audio | Mixer, fades, waveform cache, voiceover, beat detection, auto-duck, loudness normalization, true-peak limiting, spectral-gate denoise fallback, and DeepFilterNet wiring. |
| AI and model scaffolds | Whisper captions, MediaPipe segmentation, LaMa inpainting, AI usage ledger, model download management, requirement sheets, source/checksum/license metadata, and explicit planned-engine fallback behavior. |
| Reliability audits | Multiple audit phases covering persistence parity, resource leaks, defensive deserialization, export races, GL/shader guards, DSP math, gesture robustness, OOM cleanup, and storage safety. |
| Cross-project features | Target-size export, filename templates, social text templates, scratchpad sidecars, visible recovery dialogs, preset grouping, contact-sheet export, watermark burn-in, project filters, export/import polish, and device encoder probes. |
| Architecture cleanup | Model-file split, Compose stability annotations, `EffectBuilder`, `VolumeAudioProcessor`, `ExportTextOverlay`, panel visibility abstraction, and scaffold cleanup for unavailable dependencies. |
| Android 16 readiness | Edge-to-edge routing, predictive back evidence, adaptive resizability/tablet policy, drag/drop media import, APV ingest signals, Ultra HDR gainmap distinction, thermal export monitoring, and 16 KB native alignment checks. |

## Recent Completed Batches

- Privacy dashboard panel adopted in Settings.
- AI model requirement sheet wired as a parallel surface.
- Filler-removal action re-routed to Cut Assistant Review.
- Caption translation ViewModel surface shipped.
- Compound navigation orchestrator and predictive-back gate shipped.
- Cut Assistant filter chips and AI-use export confidence row adopted.
- Dynamic launcher shortcut planner and static app shortcuts shipped.
- Media relink probe, diagnostic timeline-shape summary, and autosave schema
  future-gate shipped at the engine layer.
- UI test harness bootstrap shipped in v3.74.11: shared Compose test tags,
  androidTest dependencies, and a smoke test covering project list,
  blank-project editor open, media picker, export sheet, Settings, and privacy
  dashboard surfaces.
- Release pipeline reactivation shipped in v3.74.12: CI now packages debug,
  release, and instrumentation APKs, verifies repository/build metadata,
  checks APK signatures and ZIP alignment, and runs the APK-based 16 KB
  native-library gate without local-only signing state.
- Recovery open path shipped in v3.74.13: editor opens now use
  `loadRecoveryDataWithOutcome`, resume shortcuts can flag expected recovery
  opens, corrupt/future-schema autosaves surface warnings, and autosave writes
  stay blocked until recovery data is safe to overwrite.
- Media relink editor integration shipped in v3.74.14: project-open scans run
  `MediaRelinkProbe`, missing/unverified source problems auto-open Media
  Manager, and Media Manager cards consume the probe report before users edit
  or export.

## Preserved Historical Detail

Detailed batch notes and research-to-implementation mappings were moved out of
the root roadmap and remain available in:

- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- [CHANGELOG.md](CHANGELOG.md)

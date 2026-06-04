# NovaCut Completed Work

Consolidated completion log for shipped roadmap work. The full chronological
history remains in [CHANGELOG.md](CHANGELOG.md).

Last consolidated: 2026-06-04.

## Current Delivered Baseline

- Current version: v3.74.20 (`versionCode` 157).
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
- Compound clip gesture closure shipped in v3.74.15: timeline long-press opens
  compound clips through `EditorViewModel.openCompoundClip`, the selected
  compound radial menu includes an Open action, the breadcrumb chip is rendered
  from `EditorState`, and compound entry clears selection so predictive back
  exits the nested level first.
- Caption translation panel call site shipped in v3.74.16: the Captions panel
  now hosts `CaptionTranslationPanel`, selected-clip captions are converted into
  translation rows when a target language is selected, and edit/regenerate
  actions dispatch through the ViewModel translation surface.
- Mixed-render export orchestrator shipped in v3.74.17: `VideoEngine.exportMixed`
  consumes `MixedRenderComposer` plans, stream-copy preflights pass-through
  runs, renders modified runs through Transformer, and stitches successful run
  outputs with FFmpeg concat before falling back to whole-timeline Transformer
  when the safe mixed-render envelope is not met.
- FillerRemovalPanel final deletion shipped in v3.74.18: the deleted standalone
  panel route no longer carries a `PanelId`, bottom-sheet slot, ViewModel
  analyze/apply methods, filler-region state, obsolete `AiFeatures`
  detection path, or panel-only strings; the `filler_removal` tool action stays
  on Cut Assistant Review.
- Per-tool AI requirement adoption shipped in v3.74.19: strict
  model/dependency-gated AI tool launches now raise `AiModelRequirementSheet`
  before processing starts, accepted ready sheets dispatch through a bypass
  run path, and the legacy `aiRequirementPrompt` is reserved for tool IDs
  without `AiToolRequirements` registry entries.
- Editor domain-state projection shipped in v3.74.20: `EditorState` now has a
  sealed `EditorDomainState` projection that groups panel, caption, compound,
  export, AI, and media state into tested slices before storage/copy call sites
  migrate onto those domains.

## Preserved Historical Detail

Detailed batch notes and research-to-implementation mappings were moved out of
the root roadmap and remain available in:

- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- [CHANGELOG.md](CHANGELOG.md)

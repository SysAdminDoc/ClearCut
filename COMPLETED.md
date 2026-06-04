# NovaCut Completed Work

Consolidated completion log for shipped roadmap work. The full chronological
history remains in [CHANGELOG.md](CHANGELOG.md).

Last consolidated: 2026-06-04.

## Current Delivered Baseline

- Current version: v3.74.36 (`versionCode` 173).
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
- AI editor-state storage migration shipped in v3.74.21: AI requirement
  prompts, processing state, suggestions, usage ledger, Cut Assistant review,
  smart-reframe/auto-edit/TTS/noise flags, and noise analysis now live in
  `EditorAiState`, with compatibility accessors preserving existing reads.
- Export editor-state storage migration shipped in v3.74.22: export config,
  progress/state, last output path, error text, start time, smart-render
  preview data, batch export queue, and saved preview config now live in
  `EditorExportDomainState`, with compatibility accessors preserving existing
  reads.
- Media editor-state storage migration shipped in v3.74.23: backup-import
  feedback, timeline-exchange feedback, and media relink reports now live in
  `EditorMediaState`, with compatibility accessors preserving existing reads.
- Compound editor-state storage migration shipped in v3.74.24: compound
  navigation depth and breadcrumb text now live in `EditorCompoundState`, with
  compatibility accessors preserving existing reads.
- Caption editor-state storage migration shipped in v3.74.25: caption
  translation rows, source/target language, pair quality, and model variant now
  live in `EditorCaptionState`, with compatibility accessors preserving
  existing reads.
- Panel editor-state storage migration shipped in v3.74.26: panel visibility,
  selected effect, and text-overlay editing target now live in
  `EditorPanelState`, with compatibility accessors preserving existing reads.
- Primary panel router host extraction shipped in v3.74.27: media picker,
  effects, speed, transition, text editor, export, audio, and voiceover recorder
  routes now live in `EditorPrimaryPanelHost`.
- AI panel router host extraction shipped in v3.74.28: AI tools and Cut
  Assistant review routes now live in `EditorAiPanelHost`.
- Clip adjustment panel router host extraction shipped in v3.74.29: transform,
  crop, effect adjustment, color grading, audio mixer, keyframe, speed curve,
  mask, blend mode, PiP, chroma key, caption editor, and local transform/mask
  overlay routes now live in `EditorClipAdjustmentPanelHost`.
- Utility panel and overlay router host extraction shipped in v3.74.30:
  utility sheets/dialogs now live in `EditorUtilityPanelHost`, always-on
  preview/status overlays now live in `EditorOverlayHost`, and shared report row
  rendering now lives in `EditorFeedbackDialogs`.
- Timeline interaction and overview extraction shipped in v3.74.31: compound
  long-press dispatch, snap targeting, clip hit testing, accessible split
  points, keyboard nudge step sizing, and the full-project overview scrollbar
  now live outside `Timeline.kt` with focused JVM coverage.
- Timeline chrome and drawing helper extraction shipped in v3.74.32: toolbar
  chips/buttons, compact label formatters, ruler drawing, waveform drawing, and
  volume-keyframe filtering now live outside the main timeline renderer with
  focused JVM coverage for the pure helpers.
- Android 15 media-processing timeout handling shipped in v3.74.33:
  `ExportService` now handles both foreground-service timeout callbacks,
  fails active exports through `VideoEngine` with a distinct timeout error,
  removes foreground service state, and has focused JVM policy coverage.
- Android 13 export notification permission path shipped in v3.74.34: first
  background export now shows a contextual `POST_NOTIFICATIONS` prompt on API
  33+, remembers handled prompt state, and keeps in-app progress/cancel controls
  as the fallback when notifications stay off. Settings also shows current
  notification delivery status and links to Android notification settings.
- Backup/device-transfer policy split shipped in v3.74.35: Android 12+
  data-extraction rules exclude `media/imports` from cloud backup, include it
  for device-to-device transfer, keep partial writes excluded, and require cloud
  backup encryption capability.
- Fatal-crash capture shipped in v3.74.36: app startup now installs a global
  uncaught-exception handler, writes bounded redacted crash records under
  `filesDir/diagnostics/crashes`, chains to the previous platform handler, and
  includes `crash-records.json` only in user-triggered diagnostic ZIP exports.

## Preserved Historical Detail

Detailed batch notes and research-to-implementation mappings were moved out of
the root roadmap and remain available in:

- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)
- [CHANGELOG.md](CHANGELOG.md)

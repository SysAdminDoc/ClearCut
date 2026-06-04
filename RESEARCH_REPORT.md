# NovaCut Research Report

Research synthesis for planning. The detailed source plans are archived under
[docs/archive/research](docs/archive/research/), and the previous expanded
roadmaps are archived under [docs/archive/roadmap](docs/archive/roadmap/).

Last consolidated: 2026-06-03.

## Executive Summary

NovaCut has moved beyond basic feature discovery. The research backlog now
points to three practical themes:

- Close adoption gaps in already-shipped scaffolds.
- Keep Android 16, native-library, release-channel, and model-delivery gates
  verifiable.
- Treat AI and cloud-capable features as trust surfaces with explicit consent,
  local-first defaults, and documented dependency/license posture.

## Evidence Base

The archived research and prior roadmap covered:

- Android 16 behavior, 16 KB native library page-size requirements, predictive
  back, adaptive resizability, selected-photos compliance, and edge-to-edge UI.
- Media3 1.10 Transformer, Composition, Lottie effects, inspector-frame APIs,
  and Compose playback controls.
- Commercial and open-source video editors including CapCut, DaVinci Resolve,
  PowerDirector, KineMaster, LumaFusion, VN, OpenCut, OpenShot, Kdenlive,
  Shotcut, LosslessCut, OpenTimelineIO, Gyroflow, and gl-transitions.
- Model and dependency candidates including Whisper, sherpa-onnx, DeepFilterNet,
  SAM/MobileSAM, MediaPipe, MADLAD/Bergamot-style translation, ONNX Runtime,
  FFmpeg 16 KB forks, and Lottie/Rive/template systems.
- Cross-project feature ideas from sibling editing/media utilities, especially
  target-size export, filename templates, contact sheets, recovery UI, preset
  grouping, and diagnostics.

## Planning Implications

| Theme | Implication |
|---|---|
| Release trust | CI, signing/release artifacts, version consistency, 16 KB alignment, and changelog discipline are product features for this app. |
| Scaffold adoption | Many engines and panels already exist. The highest leverage work is wiring them into the user journey, not adding new placeholders. |
| AI model honesty | Every model-backed feature needs source, checksum, size, license, local/cloud runtime, removal, and fallback behavior. |
| Editor maintainability | `EditorState`, `EditorScreen`, and `Timeline.kt` remain high-risk surfaces because they own too many independent domains. |
| Privacy | Diagnostic ZIPs, AI ledgers, cloud opt-ins, and model downloads must stay local-first and reviewable. |
| Android platform churn | Android 15/16 behavior changes, Media3 API movement, and native dependency packaging should be refreshed before each release train. |

## Highest-Value Research-Derived Work

- UI test harness bootstrap.
- Release pipeline reactivation.
- Recovery and relink UX closure.
- Caption translation panel adoption.
- Mixed-render export orchestration.
- Final removal of legacy filler-removal UI.
- Domain decomposition for editor state and panel routing.
- Timeline refactor with accessibility/gesture preservation.
- Per-tool AI requirement sheet migration.
- Dependency grouping and model-registry enforcement.

## Archived Research Inputs

- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)
- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)

Older root `RESEARCH.md`, `HostShield-Research-Report.md`, and `research/`
paths are intentionally ignored local artifacts and were not folded into the
tracked archive in this pass.

# Research - NovaCut

## Executive Summary
NovaCut is a local-first Android video editor built with Kotlin, Jetpack Compose, Media3 Transformer, Room, FFmpegKit, ONNX Runtime, MediaPipe, DeepFilterNet, Lottie, and a large privacy/reliability layer around import, project recovery, diagnostics, export, template formats, and model gates. Verified from `README.md`, `app/build.gradle.kts`, `.github/workflows/build.yml`, `docs/`, and `app/src/main`: the project is already more mature than most mobile OSS editors in trust surfaces, release checks, media health, and local diagnostics. The highest-value new direction is narrow: completed exports should be structurally verified before the UI/history call them successful, and the already-tested `SpeakerSwitchPlanner` should become a reviewable multicam workflow instead of remaining stranded engine code. Priority opportunities: 1) post-export playable-output verification gate; 2) speaker-aware multicam auto-switch review flow; 3) continue the existing export incident bundle work; 4) continue the existing audio conformance/resampling work; 5) continue the existing bounded native media/model policy; 6) continue the existing style-pack/storyboard/dotLottie items without duplicating them.

## Product Map
- Core workflows: import via Photo Picker, SAF, share intents, document intents, and camera capture; manage media through health/relink surfaces; edit multi-track video/audio/text/overlay timelines; run local AI-assisted tools; export/share/save to MediaStore or user-chosen destinations.
- User personas: mobile creators who want CapCut/VN speed without watermark or cloud lock-in; privacy-conscious FOSS users; prosumers who need local projects, archive/relink behavior, diagnostics, and desktop-NLE interchange.
- Platforms and distribution: Android minSdk 26, target/compile SDK 36 in Gradle; GitHub release artifacts today; Play/F-Droid readiness tracked through CI, release metadata, signing/alignment/page-size checks, listing assets, and privacy/data-safety docs.
- Key integrations and data flows: Room/project/autosave stores, app-owned managed media, SAF/MediaStore/FileProvider, Media3 Transformer/ExoPlayer, FFmpegKit fork, ONNX Runtime, MediaPipe, DeepFilterNet, local model downloads with checksums, Lottie/dotLottie-adjacent templates, C2PA draft manifests, OTIO/FCPXML/EDL/SRT interchange, `.novacut-template`, `.ncfx`, `.ncstyle`, and `.nclut` document routing.

## Competitive Landscape
- Open Video Editor: Android-native OSS reference using Media3/Compose. Learn from its focused scope, F-Droid/Play release notes, precise frame seeking, and export scan behavior. Avoid its smaller repair surface; NovaCut should keep managed media and diagnostic depth as differentiators.
- LosslessCut: strong fast-cut/export trust model with explicit segment metadata and user-visible export decisions. Learn from clear output validation and low-surprise destructive operations. Avoid narrowing NovaCut into only lossless cutting.
- Kdenlive, Shotcut, OpenShot, Olive, and Flowblade: desktop OSS NLE references for media bins, project repair, proxy/cache separation, interchange, and export logs. Learn asset identity and recovery reporting. Avoid desktop-density controls on phone-first workflows.
- CapCut, VN, InShot, KineMaster, and PowerDirector: commercial/mobile references for templates, captions, style packs, auto-edit, stock/effects, keyframes, and creator-speed flows. Learn that templates/style ecosystems and export confidence are table stakes. Avoid opaque failure states, forced accounts, cloud lock-in, and unbounded asset marketplaces.
- Meta Edits: mobile creator reference for storyboards, project planning, text/caption style libraries, and no-watermark social export. Existing roadmap already captured storyboard/style opportunities; do not duplicate them.
- Blackmagic Camera and LumaFusion: adjacent mobile/pro references for multicam capture/control, cloud/project handoff, and FCPXML workflows. Learn reviewable multicam handoff and explicit interchange limitations. Avoid making NovaCut a live-switching/broadcast app unless scoped separately.
- Gyroflow, OpenTimelineIO, OpenFX, C2PA, and dotLottie: adjacent standards/projects for transparent processing reports, timeline interchange, plugin/effect naming, provenance, and template compatibility. Learn versioned compatibility checks and round-trip reports. Avoid promising full conformance without fixture-based validation.

## Security, Privacy, and Reliability
- Verified: `VideoEngine.kt` prevents 0-byte Transformer completions and has cancellation/timeout handling, but successful exports are not reopened and structurally probed for expected duration, tracks, dimensions, rotation, audio presence, and readable frames/samples before `COMPLETE`/history success.
- Verified: `ExportDelegate.kt` scans saved files for gallery visibility and records history fields, but gallery scanning is not output validation. A non-empty but truncated or malformed export can still be treated as user-shareable if the encoder reports completion.
- Verified: `DiagnosticExportEngine.kt` has a strong local-only redaction posture; the existing export incident bundle roadmap item should reuse that boundary. The new verification gate should feed that same incident path when a "completed" file fails structural validation.
- Verified: `SpeakerSwitchPlanner.kt` is pure, documented, and covered by JVM tests, but its own comment says binding to live Whisper/MultiCam output is future work. `MultiCamPanel.kt` only exposes manual angle selection and sync, with no speaker map, proposed cut review, or one-shot apply flow.
- Verified: `ExportMediaPreflight.kt` still focuses on media-health/relink blockers; audio conformance and Oboe resampling are already in the roadmap and should remain priority, not be re-added.
- Verified: `FFmpegEngine.kt`, `ModelDownloadManager.kt`, and ONNX consumers still justify native/runtime bounds, but that is already in the roadmap; avoid adding another generic native-security item.
- Likely: Android developer verification, F-Droid/reproducible-build posture, local-network permission, accessibility audit, i18n hardcoded-string cleanup, telemetry consent, and dependency verification are already covered by existing roadmap entries.
- Needs live validation: device UI contrast/TalkBack, loaded-timeline performance, export behavior on vendor encoders, and actual corrupt-output fixtures require emulator/device runs; this research pass did not execute UI QA.

## Architecture Assessment
- Add an `ExportOutputVerifier`-style boundary near `VideoEngine.kt` that uses platform probes (`MediaMetadataRetriever`, `MediaExtractor`, and/or Media3 inspection where appropriate) after Transformer/FFmpeg completion and before terminal success. It should return a bounded report consumed by export history, export sheet messaging, diagnostics, and save/share actions.
- Make export state distinguish "encoding complete" from "verified complete" so the UI cannot present share/save/gallery actions before the output probe passes. The current `ensureNonEmptyExportOutput()` helper is a useful first check but not sufficient.
- Route failed output verification into the existing planned export incident bundle rather than creating a second support package. This preserves `DiagnosticExportEngine.kt` redaction and keeps privacy guarantees stable.
- Add a multicam binding layer, likely a `MultiCamDelegate` or focused `EditorViewModel` slice, that converts synced tracks plus speaker turns into `SpeakerSwitchPlanner.CutPlan`, stores a temporary review plan, and applies accepted cuts as one undoable timeline mutation.
- Extend `MultiCamPanel.kt` to show speaker-angle assignments, proposed cut counts, dwell policy, preview/reject/apply controls, and clear unavailable states when there are fewer than two angles or no speaker turns.
- Test gaps for the new work: corrupt/truncated output probes, missing-audio expectations, duration drift thresholds, verifier redaction, export-state transitions, speaker-angle mapping, no-diarization fallback, undo boundary for applied multicam cuts, and panel state formatting.
- Documentation gaps for the new work: no new markdown is needed until implementation; update existing diagnostics/export/template docs only when code changes public behavior.

## Rejected Ideas
- Add a default cloud collaboration/account system: rejected because the project is explicitly local-first and existing privacy docs/roadmap require opt-in network behavior.
- Build a stock-asset marketplace next: rejected because provider terms, royalties, attribution, and trust are already a separate risk lane; local validated packs are safer.
- Turn NovaCut into a Blackmagic-style live switcher/broadcast controller: rejected because multicam editing is in scope, but live capture/control would add network, latency, device support, and permission risks not needed for the current editor.
- Re-add FCPXML/OTIO import/export as a new roadmap item: rejected because timeline interchange is already implemented/queued in `TimelineExchangeEngine.kt`, `TimelineImportEngine.kt`, and existing roadmap content.
- Replace custom preview/timeline controls wholesale with Media3 Compose widgets: rejected because existing docs and roadmap already scope Media3 Compose/CompositionPlayer as evaluation gates, not a full UI rewrite.
- Activate Rive before dotLottie evaluation: rejected because Lottie/dotLottie already fit the current template path with lower dependency risk, and dotLottie evaluation is already queued.
- Add another generic accessibility/i18n/telemetry/dependency-verification item: rejected because those categories are already covered in `ROADMAP.md`; duplicates would make continuation harder.

## Sources

Project and platform:
- https://github.com/SysAdminDoc/NovaCut
- https://developer.android.com/media/media3/transformer
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/privacy-and-security/developer-verification

OSS and adjacent projects:
- https://github.com/devhyper/open-video-editor
- https://f-droid.org/packages/io.github.devhyper.openvideoeditor/
- https://github.com/mifi/lossless-cut
- https://github.com/KDE/kdenlive
- https://github.com/mltframework/shotcut
- https://github.com/OpenShot/openshot-qt
- https://github.com/Gyroflow/Gyroflow
- https://github.com/AcademySoftwareFoundation/OpenTimelineIO
- https://openeffects.org/

Commercial and pro references:
- https://www.capcut.com/tools
- https://www.capcut.com/help/template-export-gets-stuck
- https://www.vlognow.me/
- https://vlognow.me/blog/features/keyframe-control/
- https://www.kinemaster.com/
- https://www.cyberlink.com/products/powerdirector-video-editing-software/overview_en_US.html
- https://about.fb.com/news/2025/04/introducing-edits-streamlined-video-creation-app/
- https://www.blackmagicdesign.com/products/blackmagiccamera
- https://luma-touch.com/fcpxml-export/
- https://luma-touch.com/multicam-for-lumafusion/

Standards, dependencies, security, community:
- https://c2pa.org/specifications/specifications/2.2/index.html
- https://dotlottie.io/spec/2.0/
- https://tanersener.medium.com/saying-goodbye-to-ffmpegkit-33ae939767e1
- https://ffmpeg.org/security.html
- https://nvd.nist.gov/vuln/detail/CVE-2026-40962
- https://www.reddit.com/r/CapCut/comments/11dzxhn/capcut_audio_lag_after_export_solution/

## Open Questions
- What tolerance should define a verified export: exact expected duration, fixed millisecond drift by frame rate, or a codec/profile-specific tolerance table?
- Should speaker-aware multicam initially require manual speaker labels, or can it ship with a voice-activity fallback before real diarization metadata exists?

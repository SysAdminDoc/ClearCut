# Research - ClearCut

## Executive Summary
ClearCut is a local-first Android video editor built with Kotlin, Jetpack Compose, Media3 Transformer/ExoPlayer, Room, DataStore, ONNX Runtime, MediaPipe, and a 16 KB-page FFmpegKit fork. Its strongest current shape is trust-centered mobile editing: Photo Picker/SAF import, local model gates, export preflight/history, archive recovery, privacy-first permissions, and a broad creator toolset that already exceeds most Android OSS peers. The highest-value direction is to keep reliability and explainability ahead of new engine activation: 1. gate FFmpeg decoder risk, 2. restore local release verification after workflow removal, 3. replace stale CI/Dependabot maintenance docs with local gates, 4. ratchet disabled lint detectors, 5. finish semantic color migration for high-contrast coverage, 6. repair import-package documentation truth, 7. add cut-list/marker paste import, 8. harden CJK/RTL caption rendering, 9. verify gallery-visible export metadata, and 10. protect timeline edits while waveform/proxy jobs run.

## Product Map
- Core workflows: import device media/documents/packages; edit multi-track timelines with clips, effects, captions, audio, keyframes, masks, markers, transcripts, and AI assists; preflight media/export health; export/share/save locally.
- User personas: privacy/FOSS Android users, short-form creators replacing CapCut/KineMaster, prosumers needing desktop NLE handoff, caption/audio editors, and direct-APK users who need verifiable local builds.
- Platforms and distribution: Android minSdk 26, target/compileSdk 36, direct APK/GitHub Releases today; Play, F-Droid, developer verification, store metadata, and signing-policy decisions remain tracked in `Roadmap_Blocked.md`.
- Key integrations and data flows: Room project DB, JSON auto-save/archive recovery, DataStore settings, Media3 preview/export, FFmpegKit fallback processing, ONNX/MediaPipe local inference, OkHttp for opt-in update/model/provider flows, Fastlane metadata for store-facing assets.

## Competitive Landscape
- CapCut: strong template discovery, auto captions, AI-assisted edits, and platform handoff. Learn progressive creator workflows and export-first presets. Avoid account/cloud dependence as an editing requirement.
- KineMaster: mature mobile template/asset workflow, auto captions, and simple feature discovery. Learn from its mobile-first package affordances. Avoid watermark/subscription gating as a core interaction.
- LumaFusion for Android: credible pro mobile editor with one-time purchase positioning and external/pro workflow clarity. Learn explicit workflow boundaries. Avoid forcing desktop NLE density into every phone surface.
- PowerDirector Mobile: useful benchmark for beginner-friendly guided tools, AI marketing, and effects breadth. Learn assistive recovery copy. Avoid feature sprawl that weakens local trust.
- Open Video Editor: closest Android OSS peer using modern Android media APIs, but issues show FFmpeg dependency breakage, confusing controls, export metadata/file-extension problems, crashy filename settings, and stale maintenance concerns. Learn that fresh-checkout builds and gallery-visible exports are table stakes.
- OpenCut: web/desktop OSS competitor with demand for auto import, portable project files, CJK/Arabic captions, range selection, keyframe easing, and offline operation. Learn from community demand; avoid porting browser/server assumptions into Android.
- LosslessCut: precision editor with repeated requests around custom cut-list import, marker handling, timestamp/frame naming, keyframe/codec diagnostics, GPS/subtitle sidecars, and FFmpeg CVE response. Learn transparent media diagnostics and low-friction edit-decision input.
- Shotcut: mature OSS NLE with recent work around HDR/export clarity, safe plugin behavior, motion-tracker persistence, proxy/edit races, crash recovery, and modified-state correctness. Learn explicit capability warnings and recovery discipline before expanding plugin-style surfaces.

## Security, Privacy, and Reliability
- Verified: `gradle/libs.versions.toml` pins `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`; `FFmpegEngine.kt` still routes reverse, audio extraction, concat, speed, subtitle burn-in, loudness, and stream-copy trim through bundled FFmpeg; `NativeProcessingPolicy.kt` screens extension/MIME but cannot yet prove internal codec safety before FFmpeg execution. NVD and FFmpeg security pages identify CVE-2026-8461 as a MagicYUV decoder issue fixed in FFmpeg 8.1.2, and LosslessCut issue #2943 shows adjacent editor users are already reacting to the risk.
- Verified: `.github/workflows` was removed, but `README.md`, `scripts/verify_release_artifacts.py`, `scripts/validate_distribution_readiness.py`, and `scripts/validate_android_audio_api_policy.py` still describe or require workflow artifacts. That makes local release gates both stale and misleading.
- Verified: `docs/dependency-maintenance.md` still describes Dependabot grouping even though `TrackedFilesAuditTest.kt` forbids Dependabot, Renovate, and GitHub Actions files. The repo needs one local dependency truth source with hold reasons.
- Verified: `docs/incoming-document-imports.md` still says `.ncstyle` installation is blocked even though style-pack import is implemented in source and documented in the changelog.
- Verified: Open Video Editor issues #96, #98, and #95 show gallery/file-manager trust failures from missing time metadata, missing file extensions, and bad custom output names. ClearCut already has `ExportOutputVerifier.kt`, `FileNaming.kt`, and MediaStore save paths; these should become contract tests for gallery-visible outputs.
- Missing guardrails: no local release command fully replaces the removed workflow path; native-processing denials are not yet a structured export incident class; background waveform/proxy generation needs explicit tests that editing during generation cannot corrupt timeline state.
- Recovery and rollback needs: release checks must run from a clean local checkout; FFmpeg risk mitigation should fail closed for known-dangerous combinations and explain the safe fallback; auto-save/undo-clean state should be tested before larger edit-history changes.

## Architecture Assessment
- Module/boundary improvements: `EditorViewModel.kt`, `ExportSheet.kt`, `Timeline.kt`, `ProjectAutoSave.kt`, and `AiFeatures.kt` remain large workflow surfaces. New work should land as focused engines, policy objects, or delegates with JVM tests instead of adding more branch-heavy UI logic.
- Refactor candidates: `scripts/verify_release_artifacts.py` should become the local release-trust orchestrator; `NativeProcessingPolicy.kt` needs codec/container-aware checks before `FFmpegEngine.kt` dispatch; `IncomingDocumentImportRouter.kt` and docs need one source of truth for package support; caption preview/export should share bidi/font fallback policy instead of diverging.
- Test gaps: five lint detectors remain disabled in `app/build.gradle.kts`; background waveform/proxy jobs lack race-focused tests against live timeline edits; export output verification should assert extension, MIME, duration/time metadata, and MediaStore visibility.
- Documentation gaps: README release verification still references workflow attestations; dependency maintenance still references Dependabot; import docs understate `.ncstyle` support; `Roadmap_Blocked.md` Room 3 wording should continue to follow `docs/room-3-migration-plan.md`.
- Accessibility and i18n: `docs/appearance-policy.md` correctly defers light mode, but raw `Mocha` imports in editor/export/mediapicker surfaces limit high-contrast coverage. OpenCut CJK/Arabic caption requests reinforce treating caption font fallback and bidi rendering as correctness work, not locale expansion.
- Observability/offline/migration: export incidents and privacy dashboard foundations exist, but native-processing denials, gallery-output verification failures, and background-job races should be recorded in user-safe diagnostics. Offline/local-first remains the product differentiator; sync, store distribution, and external model hosting should stay blocked until operator/product gates clear.

## Rejected Ideas
- Immediate Room 3 migration: `docs/room-3-migration-plan.md` and AndroidX release pages show Room 2.8.4 as the current stable line; keep Room 3 in the blocked queue until a real pre-release exists.
- Immediate Coil 3.5 or Hilt 2.59 upgrades: `Roadmap_Blocked.md` already tracks Kotlin/AGP prerequisites; do not duplicate them in the active roadmap.
- Immediate C2PA, Sherpa, RVM, RIFE, Real-ESRGAN, style-transfer, voice-clone, lip-sync, or stem-separation activation: these require model hosting, binary-size review, 16 KB validation, device QA, signing policy, or product decisions already tracked as blocked.
- Cloud collaboration or multi-user sync: competitor demand exists, but conflict-safe sync is already blocked and broad cloud collaboration weakens ClearCut's local-first privacy position.
- Headless/API render mode now: OpenCut demand is credible for web and automation workflows, but Android release trust, export correctness, and mobile editing reliability are higher-value first.
- New human-language locale packs: community demand exists, but production translations need human review; keep locale expansion blocked until reviewed strings are available.
- Full plugin ecosystem now: Shotcut and native library incidents show plugin/package systems need crash isolation, signatures, and support policy. First correct local package docs and registry trust.

## Sources
OSS competitors and community signal:
- https://github.com/devhyper/open-video-editor
- https://github.com/devhyper/open-video-editor/issues/119
- https://github.com/devhyper/open-video-editor/issues/117
- https://github.com/devhyper/open-video-editor/issues/96
- https://github.com/devhyper/open-video-editor/issues/98
- https://github.com/OpenCut-app/OpenCut
- https://github.com/OpenCut-app/OpenCut/issues/828
- https://github.com/OpenCut-app/OpenCut/issues/817
- https://github.com/OpenCut-app/OpenCut/issues/744
- https://github.com/OpenCut-app/OpenCut/issues/719
- https://github.com/mifi/lossless-cut/issues/2947
- https://github.com/mifi/lossless-cut/issues/2945
- https://github.com/mifi/lossless-cut/issues/2943
- https://github.com/mifi/lossless-cut/issues/2807
- https://github.com/mifi/lossless-cut/issues/2805
- https://github.com/mltframework/shotcut/releases/tag/v26.6.25
- https://github.com/mltframework/shotcut/issues/1820
- https://github.com/mltframework/shotcut/issues/1145
- https://github.com/mltframework/shotcut/issues/1096

Commercial competitors:
- https://www.capcut.com/tools/desktop-video-editor
- https://www.kinemaster.com/features
- https://luma-touch.com/lumafusion-for-android/
- https://www.cyberlink.com/products/powerdirector-video-editing-app/features_en_US.html
- https://www.adobe.com/products/premiere/app.html

Platform, dependencies, and advisories:
- https://nvd.nist.gov/vuln/detail/CVE-2026-8461
- https://ffmpeg.org/security.html
- https://github.com/moizhassankh/ffmpeg-kit-android-16KB
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/jetpack/androidx/releases/room

## Open Questions
- None blocking the active roadmap additions. Store distribution, signing, device-only QA, model hosting, external-drive design, sync architecture, and human-reviewed translations remain in `Roadmap_Blocked.md`.

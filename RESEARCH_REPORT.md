# NovaCut Research Report

Research synthesis for planning. The detailed source plans are archived under
[docs/archive/research](docs/archive/research/), and the previous expanded
roadmaps are archived under [docs/archive/roadmap](docs/archive/roadmap/).

Last refreshed: 2026-06-04.

## 2026-06-04 Freshness Refresh

- [Verified] The Active Queue's editor state storage migration lane completed
  through v3.74.26 / versionCode 163. The v3.74.21 through v3.74.26 passes moved
  AI, export, media, compound, caption, and panel storage into
  `EditorAiState`, `EditorExportDomainState`, `EditorMediaState`,
  `EditorCompoundState`, `EditorCaptionState`, and `EditorPanelState`, while
  preserving read-only compatibility accessors for existing UI and delegate
  reads. The next architecture lane is EditorScreen panel router decomposition;
  v3.74.27 started it by extracting the primary bottom-sheet cluster into
  `EditorPrimaryPanelHost`, v3.74.28 extracted AI tools / Cut Assistant review
  into `EditorAiPanelHost`, and v3.74.29 extracted clip-adjustment routes into
  `EditorClipAdjustmentPanelHost`.
- [Verified] Current Maven metadata check: Media3 `1.10.1` and WorkManager
  `2.11.2` match latest release metadata; Compose BOM is `2026.05.00` vs
  `2026.05.01`; Room is `2.7.2` vs `2.8.4`; Kotlin is `2.1.0` vs `2.4.0`; AGP
  `8.7.3` has newer `9.3.0-alpha09` metadata, so that should remain a deliberate
  toolchain lane rather than an automatic bump.
- [Verified] The existing release-hardening findings still hold in the current
  tree: no `networkSecurityConfig` / `usesCleartextTraffic`, no
  `connectedAndroidTest` or Gradle Managed Device CI execution, no SHA-256/cosign
  release step, and no `slipClip` / `slideClip` gesture call site in
  `Timeline.kt` or README grep output.
- [Verified] Focused and full local gates passed with JDK 21: `git diff
  --check`, release artifact metadata checks, APK-based 16 KB checks,
  `apksigner verify`, `zipalign -c -P 16 -v 4`, the focused
  `EditorDomainStateTest`, and the debug-unit/debug-APK/release-APK/androidTest
  Gradle matrix. The known lint/R8 warnings remain non-fatal.
- [Verified] Cycle 1 handoff check: Maven metadata still reports Media3 1.10.1,
  WorkManager 2.11.2, Compose BOM 2026.05.01, Room 2.8.4, Kotlin 2.4.0, and AGP
  9.3.0-alpha09. The active dirty tree already contains the next
  `EditorPrimaryPanelHost` / `EditorScreen` panel-router refactor slice, so this
  pass recorded it as the current implementation lane instead of adding a
  duplicate roadmap item.

## Executive Summary

NovaCut is a mature, single-maintainer open-source Android NLE (Kotlin / Jetpack
Compose / Material 3 / Media3 Transformer + ExoPlayer / Room / DataStore / Hilt /
ONNX Runtime / MediaPipe). The engine, persistence, model-trust, and release-CI
layers are already hardened: Room ships five sequential migrations, the model
download manager enforces SHA-256 (with a required-checksum gate), the release
workflow builds debug/release/instrumentation APKs and runs signature, ZIP, and
16 KB-alignment verification, and core dependency versions are mostly current
(Media3 1.10.1 and WorkManager 2.11.2 match current metadata; Compose BOM, Room,
and Kotlin have newer stable metadata to review deliberately). The prior
consolidation already folded legacy roadmaps and research plans into the
canonical trio plus `docs/archive/`.

This pass therefore did not re-survey feature ideas. Instead it audited the repo
against actual source and found the remaining high-value gaps are in **release
verification depth, network/crash hardening, localization delivery, and doc
accuracy** — none of which the Active Queue (scaffold adoption/decomposition) or the
Research-Backed Engine Candidates (engine activations) already cover.

Top opportunities (one line each):

1. **[P0]** No global uncaught-exception handler despite a shipped diagnostic-ZIP
   feature — crashes are lost; close the crash-recovery loop locally.
2. **[P1]** CI builds the instrumentation smoke APK but never runs it on an emulator
   — the v3.74.11 smoke harness is compiled, never asserted.
3. **[P1]** Localization scaffold is complete (~1900 externalized strings, RTL
   BidiFormatter, localeConfig) but ships zero translated locales — only `values/`.
4. **[P2]** No `networkSecurityConfig` despite INTERNET + an OkHttp cloud path —
   cleartext is permitted by platform default on API 26-27.
5. **[P2]** README lists slip/slide editing as shipped, but `slipClip`/`slideClip`
   have zero gesture call sites in `Timeline.kt`.
6. **[P2]** Tagged-release CI uploads APKs with no SHA-256 sums or provenance
   (dependabot.yml flags R5.9d cosign as future work).
7. **[P3]** `ROADMAP.md`/`COMPLETED.md`/`PROJECT_CONTEXT.md` carry a future-dated
   "Last consolidated: 2026-06-04" stamp.

## Evidence Reviewed

- **Git range:** `git -C NovaCut log -30 --oneline`, HEAD `3e7490d`
  (2026-06-03), 543 commits total. Recent line: compound-clip gesture open, media
  relink-on-open, recovery outcome opens, CI reactivation, UI smoke harness.
- **Build / deps:** `app/build.gradle.kts` (compileSdk/targetSdk 36, minSdk 26,
  versionCode 152/`v3.74.15` at read time; a concurrent agent bumped to
  `v3.74.16`/153 mid-pass), `gradle/libs.versions.toml` (Media3 1.10.1, AGP 8.7.3,
  Kotlin 2.1.0, Compose BOM 2026.05.00, Hilt 2.58, Room 2.7.2, ONNX 1.26.0,
  MediaPipe 0.10.35, OkHttp 5.3.2, Lottie 6.7.1, FFmpegKit-16kb 6.1.1,
  DeepFilterNet 0.0.8).
- **Manifest:** `app/src/main/AndroidManifest.xml` (permissions, share-target
  intent filters, foreground media-processing service, FileProvider).
- **CI:** `.github/workflows/build.yml`, `.github/dependabot.yml`,
  `scripts/check_16kb_alignment.py`, `scripts/verify_release_artifacts.py`.
- **Code surfaces:** `engine/ModelDownloadManager.kt` (checksum gate),
  `engine/db/ProjectDatabase.kt` (Room v6, MIGRATION_1_2..5_6),
  `ui/editor/Timeline.kt` (snapping wired; slip/slide not),
  `res/xml/locales_config.xml`, `res/values/strings.xml` (1937 lines),
  largest files by size (see Architecture).
- **External sources:** Media3 release notes (latest stable 1.10.0/.1) —
  https://developer.android.com/jetpack/androidx/releases/media3 and
  https://android-developers.googleblog.com/2026/03/media3-110-is-out.html .
- **Unverifiable in this pass:** runtime UI behavior (no emulator/device run),
  actual APK size and 16 KB compliance on a built artifact, and whether the OkHttp
  cloud inpainting path is reachable without explicit consent at runtime (consent
  policy files exist; not exercised).

## Current Product Map

- **Personas:** mobile creators producing short-form/social video on-device;
  privacy-conscious users who want local-first AI; OSS users wanting a CapCut/
  PowerDirector alternative without cloud lock-in.
- **Primary workflows:** project gallery -> editor (multi-track timeline, preview,
  effects/transitions/text/captions/audio) -> export (platform presets, batch,
  foreground service) -> share/save. Secondary: project archive/import, media
  relink, diagnostics export, per-tool AI model download + requirement sheets.
- **Modules:** single `:app` module, packages `ai`, `engine` (+ `engine/db`,
  `engine/segmentation`, `engine/whisper`), `model`, `ui` (`editor`, `export`,
  `mediapicker`, `projects`, `settings`, `theme`). 227 Kotlin source files,
  78 JVM unit-test files, 1 instrumentation test.
- **Release:** GitHub Actions on push/PR/tag; tagged builds cut a GitHub Release
  with APKs. Fastlane metadata dir present.

## Feature Inventory (audited)

| Feature | Access | Code | Maturity | Test/Doc |
|---|---|---|---|---|
| Multi-track timeline, trim/split/merge, keyframes, markers, grouping | Editor | `Timeline.kt`, `EditorViewModel.kt` | Shipped | Heavy engine tests; 1 smoke (unrun in CI) |
| Magnetic snapping (8dp) | Timeline drag | `Timeline.kt` (31 refs) | Shipped | Indirect |
| Slip/slide editing | (claimed) timeline drag | `slipClip`/`slideClip` defined, **0 gesture call sites** | **Not wired** | README overstates |
| Effects/transitions/LUT/masks/GIF/frame capture | Tool panels | `ShaderEffect.kt`, `EffectBuilder.kt` | Shipped | Engine tests |
| Audio mixer, ducking, loudness, DeepFilterNet denoise | Audio panel | `AudioEffectsEngine.kt`, `AudioMasteringEngine.kt` | Shipped | Engine tests |
| Captions (Whisper/ONNX) + translation panel | Captions sub-tab | `engine/whisper`, `CaptionTranslationEngine.kt` | Translation echoes source until model gate | Unit tests; concurrent agent actively editing |
| AI engines (inpainting, matting, upscale, interpolation, TTS, etc.) | AI tools | many `engine/*Engine.kt` scaffolds | Scaffolded/gated | Policy + requirement tests |
| Model download + checksum gate | Model sheets | `ModelDownloadManager.kt` | Shipped (SHA-256 enforced) | `ModelDownloadManagerTest` |
| Export (Media3 H.264/MP4, presets, batch, foreground svc, OTIO/FCPXML/EDL plan) | Export sheet | `VideoEngine.kt`, `ExportSheet.kt`, `ExportService` | Shipped | Engine tests |
| Project archive/import, recovery, media relink | Gallery/editor open | `ProjectAutoSave.kt`, `MediaRelinkProbe` | Shipped | Tests |
| Diagnostics ZIP + timeline-shape summary | Settings | `DiagnosticExportEngine.kt` | Shipped | `DiagnosticExportEngineTest` |
| Privacy dashboard, AI-use ledger, cloud opt-in policy | Settings | `PrivacyDashboard.kt`, `AiUsageLedger.kt`, `GenerativeVideoPolicy.kt` | Shipped | Tests |
| Localization (externalized strings, RTL, localeConfig) | System app-language | `strings.xml`, BidiFormatter | **Infra only — 0 translated locales** | `BidiTextPolicyTest` |

## Competitive Landscape

- **CapCut (mobile):** best-in-class auto-captions, templates, and one-tap effects;
  lesson — caption accuracy + speed is the headline feature; avoid — aggressive
  cloud upload of user media and opaque data use. NovaCut's local-first caption +
  model-trust posture is the differentiator; the unrun smoke suite and
  echo-until-gated translation are the weak points to close.
- **DaVinci Resolve / LumaFusion:** desktop/iPad NLE depth (proxy media, color,
  multicam); lesson — proxy workflow keeps large-source editing responsive (already
  an Engine Candidate); avoid — desktop-class complexity on a phone screen.
- **KineMaster / PowerDirector:** mature mobile timelines with slip/slide and
  asset stores; lesson — slip/slide is table-stakes for a "pro" claim, so README
  should not advertise it before it is gesture-wired.
- **OpenShot / Kdenlive / Shotcut (OSS desktop):** command-pattern undo, magnetic
  timeline, OTIO interop; lesson — command-based edit model (already a candidate)
  is the right foundation; NovaCut already interops via OTIO/FCPXML/EDL planning.
- **LosslessCut:** stream-copy/lossless cuts; lesson — the mixed-render export
  orchestrator (Active Queue P0) should keep a true stream-copy fast path.
- **Standards to track:** Android 15/16 16 KB page-size upload gate (enforced in
  CI), per-app language (Android 13+ `localeConfig`, already declared),
  WCAG 2.2 AA for the editor UI (contrast/touch-target/labels — partial), and
  network security config / cleartext-block as an Android hardening baseline.

## Quality & Friction Findings

- **[Major]** CI compiles but never executes the instrumentation smoke suite — a
  green pipeline does not prove the app launches. -> roadmap "Execute the
  instrumentation smoke suite on an emulator in CI" (P1). [Verified]
- **[Major]** No translated locales despite a complete i18n scaffold — large infra
  investment with zero user benefit. -> "Ship at least one fully translated locale"
  (P1). [Verified]
- **[Major]** No global uncaught-exception handler despite a diagnostic-ZIP
  feature — crashes are unrecoverable and unattachable. -> "Install a global
  uncaught-exception handler" (P0). [Verified]
- **[Minor]** No `networkSecurityConfig` with an INTERNET permission + OkHttp cloud
  path — cleartext permitted by default on API 26-27. -> "Add a
  `networkSecurityConfig` that blocks cleartext app-wide" (P2). [Verified]
- **[Minor]** README advertises slip/slide as shipped; it is not gesture-wired. ->
  "Correct README feature claims" (P2). [Verified]
- **[Minor]** Release artifacts ship without SHA-256 sums or provenance. -> "Attach
  checksums and build provenance to release artifacts" (P2). [Verified]
- **[Cosmetic]** Future-dated "Last consolidated: 2026-06-04" stamp. -> "Realign
  the date stamps" (P3). [Verified]

## Architecture & Technical Findings

- **Overgrown files (decomposition already in Active Queue, not re-added here):**
  `EditorViewModel.kt` ~214 KB, `EditorScreen.kt` ~141 KB, `Timeline.kt` ~126 KB,
  `AiFeatures.kt` ~110 KB, `ProjectAutoSave.kt` ~95 KB. These remain the highest
  maintainability risk; the existing "Editor state decomposition",
  "EditorScreen panel router decomposition", and "Timeline refactor" items cover
  them. [Verified]
- **Persistence:** Room v6 with five sequential migrations and `exportSchema = true`
  — healthy; no `fallbackToDestructiveMigration` found. [Verified]
- **Model trust:** `ModelDownloadManager` enforces SHA-256 with a required-checksum
  gate and deletes corrupt files before retry — R5.9b is implemented, not pending.
  [Verified]
- **Dependency health:** Media3 1.10.1 and WorkManager 2.11.2 match current
  release metadata; Compose BOM 2026.05.00, Room 2.7.2, and Kotlin 2.1.0 have
  newer stable metadata; AGP's newest observed metadata is 9.3.0-alpha09 and
  should not be pulled into the release line casually. Dependabot groups by
  ecosystem weekly. No obviously vulnerable/unused declared deps spotted.
  [Likely]
- **Native/16 KB:** no first-party native code yet; native libs arrive via AAR
  (ONNX, MediaPipe, FFmpegKit-16kb fork); CI runs `check_16kb_alignment.py` over
  built APKs. NDK pin documented for when first-party native lands. [Verified]
- **Release automation:** signature verify + zipalign + 16 KB check + metadata
  verify on every build; tag triggers a GitHub Release. Missing: artifact checksums
  / provenance (see findings). [Verified]

## Security / Privacy / Data Safety

- Cloud paths are consent-gated (`GenerativeVideoPolicy`, `PrivacyDashboard`,
  `AiUsageLedger`); no hardcoded `http://` URLs in `src/main`. [Verified]
- Share-target intent filters are `content://`-only by design (manifest comments),
  blocking `file://` handoff from other apps. [Verified]
- Gaps: no cleartext-blocking network config; no crash-record capture; release
  artifacts lack independent integrity proof. All three are addressed by the
  roadmap items above. [Verified]

## UX & Accessibility

- Edge-to-edge, predictive back, adaptive resizability, and accessibility actions
  are shipped per COMPLETED.md; the smoke harness uses test tags. The unrun
  instrumentation suite means a11y/launch regressions are not caught in CI.
  A WCAG 2.2 AA contrast/touch-target/label audit of the editor remains
  unverified (no device run this pass). [Needs validation]

## Explicit Non-Goals (rejected this pass)

- **Re-adding engine activations** (RIFE/Real-ESRGAN/Sherpa-ONNX/AV1/proxy, etc.):
  already enumerated as Research-Backed Engine Candidates; duplicating them would
  violate the no-duplication rule.
- **Decomposition items** (EditorViewModel/EditorScreen/Timeline): already in the
  Active Queue — not re-added.
- **Another broad feature-discovery research pass:** the existing roadmap explicitly
  defers this until the P0/P1 adoption items close; respected.
- **Cloud backup / real-time collaboration:** in the existing "Deferred" list;
  out of this pass's hardening focus.

## Open Questions (genuine blockers only)

- Should the first shipped locale be RTL (to exercise BidiFormatter on a real build)
  or a high-coverage LTR locale (`es`/`pt-BR`) first? Affects translation sourcing.
- Is a hosted CI emulator runner acceptable for runtime/cost, or should the smoke
  suite move to Gradle Managed Devices? Affects the CI item's implementation.
- For provenance: SHA-256 sums only (cheap, immediate) vs. full SLSA/cosign
  attestation (heavier) — which trust level is wanted for a single-maintainer repo?

---

### Note on this refresh

The research-driven items summarized above have now been transcribed into
`ROADMAP.md`'s `## Research-Driven Additions` section as full entries
(Why/Evidence/Touches/Acceptance/Verify/Complexity), grouped by category with
Quick Wins and Larger Bets sub-groupings. The previous concurrent `ROADMAP.md`
edits (the `v3.74.x` version bumps and caption-translation work) have since
landed, so the additions were appended cleanly under the trailing
`## Research-Driven Additions` header without entangling unrelated working-tree
changes. The ten roadmap items expand the seven headline findings here with two
gesture/accessibility items and an explicit README-claim correction, all tied to
the same verified evidence. Each finding's claim was re-checked against
`app/src/main`, `.github/workflows/build.yml`, and `AndroidManifest.xml` on
2026-06-03: zero slip/slide gesture sites in `Timeline.kt`, no
`UncaughtExceptionHandler`, no `networkSecurityConfig`, `values/`-only resources,
and no checksum/emulator step in CI.

## Archived Research Inputs

- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)
- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)

Older root `RESEARCH.md`, `HostShield-Research-Report.md`, and `research/`
paths are intentionally ignored local artifacts and were not folded into the
tracked archive.

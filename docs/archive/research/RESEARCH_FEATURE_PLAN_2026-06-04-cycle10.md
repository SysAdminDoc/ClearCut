# Project Research and Feature Plan

## Executive Summary

NovaCut is a local-first Android video editor built with Kotlin, Jetpack Compose, Media3, Room, DataStore, Hilt, ONNX Runtime, and MediaPipe. Its strongest current shape is a creator workstation with deep timeline editing, AI-assisted editing, captions, chapter markers, export sidecars, privacy disclosures, and platform share intents. The highest-value next direction is not another broad editing engine: it is a distribution-ready publish package that assembles the rendered MP4, metadata, captions, chapters, thumbnails, AI-use declaration, C2PA manifest, file checksums, and rights attribution into one repeatable handoff before deeper platform API upload work.

Top opportunities in priority order:

- P1 - Publish package manifest and ZIP/folder exporter.
- P1 - Publish readiness checklist in the Direct Publish surface.
- P1 - Rights and attribution ledger for stock, template, LUT, plugin, and music assets.
- P2 - Thumbnail, cover, and contact-sheet pack tied to platform presets.
- P2 - Platform API adapter contracts with dry-run validators before OAuth uploads.
- P2 - C2PA sign-and-embed activation after the sidecar flow is packaged.
- P2 - Content-ID and rights preflight surfaced as advisory status.
- P3 - Stock asset provider activation gated by attribution and API-key policy.
- P3 - Post-export analytics checklist/import hooks, not automatic tracking.

## Evidence Reviewed

Local files and directories inspected:

- `README.md`
- `ROADMAP.md`
- `RESEARCH_REPORT.md`
- `COMPLETED.md` references through roadmap context
- `.github/workflows/build.yml`
- `app/build.gradle.kts`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml` through prior roadmap evidence and code search
- `app/src/main/java/com/novacut/editor/MainActivity.kt`
- `app/src/main/java/com/novacut/editor/engine/DirectPublishEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/StockAssetEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/C2paExportEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/AiUsageLedger.kt`
- `app/src/main/java/com/novacut/editor/engine/ContactSheetExporter.kt`
- `app/src/main/java/com/novacut/editor/engine/SubtitleExporter.kt`
- `app/src/main/java/com/novacut/editor/engine/ContentIdEngine.kt`
- `app/src/main/java/com/novacut/editor/engine/ProjectAutoSave.kt`
- `app/src/main/java/com/novacut/editor/engine/ProjectArchive.kt`
- `app/src/main/java/com/novacut/editor/engine/PrivacyDashboard.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/V369Delegate.kt`
- `app/src/main/java/com/novacut/editor/ui/editor/V369FeaturesPanel.kt`
- `app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt`
- `app/src/test/java/com/novacut/editor/engine/DirectPublishEngineTest.kt`
- `app/src/test/java/com/novacut/editor/engine/C2paExportEngineTest.kt`
- `app/src/test/java/com/novacut/editor/engine/AiUsageLedgerTest.kt`
- `app/src/test/java/com/novacut/editor/engine/SubtitleExporterTest.kt`
- `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`
- `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md`
- `docs/models.md`
- `docs/templates.md`

Git history range reviewed:

- `ad492f1 docs: add NovaCut process exit diagnostics research`
- `d87e975 docs: add NovaCut document import research`
- `c3629cd docs: add NovaCut appearance accessibility research`
- `24cb121 fix: audit pass 3 - undo integrity, timeline safety, UI correctness`
- `3783fef fix: audit pass 2 - undo flooding, export guard, drag performance`
- `d806504 fix: deep audit - 11 engine/UI/security fixes`
- `bd6f465 docs: add NovaCut incoming media research`
- `1e174e4 docs: add NovaCut Play listing research`
- `643b44d fix: split backup transfer policy`
- `edb6a56 docs: add NovaCut memory pressure research`

Build, test, docs, and release artifacts inspected:

- Gradle Android app module and version catalog show Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2026.05.00, Media3 1.10.1, WorkManager 2.11.2, minSdk 26, compileSdk/targetSdk 36.
- The current local worktree already has uncommitted baseline-profile build edits (`versionName` 3.74.36 / `versionCode` 173, `baselineprofile/`, `app/src/main/baseline-prof.txt`). This report does not modify or depend on those edits.
- GitHub Actions build workflow runs unit tests, instrumentation APK packaging, debug/release APK builds, release metadata verification, signature verification, zipalign, 16 KB page-size checks, and artifact upload.
- Existing roadmap already covers crash capture, process-exit diagnostics, Play listing/privacy gates, incoming sharesheet routing, document import routing, appearance/accessibility gates, memory pressure, notification permission, backup policy, and baseline-profile work. This plan avoids duplicating those items.

External sources reviewed:

- Android receiving shared data: <https://developer.android.com/training/sharing/receive>
- YouTube Data API `videos.insert`: <https://developers.google.com/youtube/v3/docs/videos/insert>
- YouTube Data API `captions.insert`: <https://developers.google.com/youtube/v3/docs/captions/insert>
- YouTube GenAI disclosure help: <https://support.google.com/youtube/answer/14328491?hl=en>
- TikTok Content Posting API overview: <https://developers.tiktok.com/products/content-posting-api/>
- TikTok Content Posting API Direct Post reference: <https://developers.tiktok.com/doc/content-posting-api-reference-direct-post>
- C2PA Android SDK API docs: <https://contentauth.github.io/c2pa-android/>
- Pexels API docs: <https://www.pexels.com/api/documentation/>
- Pixabay API docs: <https://pixabay.com/api/docs/>
- Freesound API docs: <https://freesound.org/docs/api/>
- CapCut official feature table: <https://ads.us.tiktok.com/help/article/about-capcut?lang=en>
- YouTube Create help: <https://support.google.com/youtube/answer/13521789?hl=en>
- LinkedIn Videos API docs: <https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/videos-api>
- Google Play Data safety guidance: <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>
- Google Play User Data policy guidance: <https://support.google.com/googleplay/android-developer/answer/10144311?hl=en>

Areas that could not be verified:

- Needs live validation: actual device behavior for share targets, Android resolver visibility, platform app install states, and platform-specific intent handling.
- Needs live validation: current YouTube, TikTok, Instagram, Threads, X, and LinkedIn account-level API approvals, quota availability, and audit status.
- Needs live validation: Play Console listing state, privacy-policy URL, and Data safety answers, because console state is not in the repo.
- Needs live validation: Meta Instagram and Threads publishing documentation pages were not reliably retrievable in this environment. Treat API-upload support for Meta platforms as gated until the implementer refreshes the official docs and approval requirements.

## Current Product Map

Core workflows:

- Create or open a project from the project gallery.
- Import media into managed local storage.
- Edit on a multi-track timeline with clips, overlays, captions, audio, effects, markers, and keyframes.
- Generate or edit AI-assisted captions, auto chapters, thumbnails, transcript-driven tools, background removal, inpainting, auto edit, TTS, and other gated AI features.
- Export video, GIF, audio, stems, frames, subtitles, chapters, contact sheets, sidecars, project archives, and diagnostic bundles.
- Share or publish the last export through Android intents.
- Manage settings, privacy dashboard, model gates, backups, templates, and recovery.

Existing features:

- Timeline editing: multi-track clips, trim/split/merge/crop/rotate, slip/slide foundations, snapping, grouping, markers, keyboard shortcuts, waveform caching, track collapse, track height, undo/redo, and proxy workflow.
- Effects and transitions: 37 GPU transitions, 40+ GLSL effects, blend modes, masks, chroma key, Lottie templates, stickers, color grading, LUT import, scopes, audio DSP, loudness, beat detection, ducking, voiceover, and noise reduction.
- AI tools: auto captions, scene detection, auto color, motion tracking, background removal, object removal, smart reframe, TTS, style/upscale/frame-interpolation stubs, and model requirement gates.
- Export: Media3 composition export, stream-copy/mixed-render planning, platform presets, HDR confidence, background export, batch export, GIF, frames, contact sheets, subtitles, chapters, AI-use sidecars, C2PA sidecar manifests, and share intents.
- Interop: project archive ZIP, OTIO/FCPXML/EDL scaffolds, effect sharing, template docs, LUT import, incoming media/document roadmap items.
- Trust: Privacy Dashboard, diagnostic ZIP, local-first AI posture, explicit model gates, release checks, backup/transfer policy.

User personas:

- Mobile creators who need short-form exports for TikTok, Reels, Shorts, Threads, X, and LinkedIn.
- Long-form YouTube creators who need chapters, captions, thumbnails, AI disclosure, and predictable metadata.
- Privacy-sensitive creators who want local editing and controlled disclosure before sharing.
- Power users who want desktop-NLE-style timelines, interchange formats, export diagnostics, and archiveable deliverables.
- Open-source/release maintainers who need reproducible build, release, and policy evidence.

Platforms and distribution channels:

- Android app, single-activity Compose UI.
- GitHub release artifacts through CI.
- Play/F-Droid readiness is planned and gated by roadmap items, model licensing, native-library alignment, privacy docs, and metadata evidence.
- User-directed Android share intents are implemented for exported media.
- OAuth/platform API uploads are intentionally not implemented yet in `DirectPublishEngine`.

Important integrations, permissions, storage, and data flows:

- Media3 Transformer/ExoPlayer for preview/export.
- Room for project database.
- DataStore for settings.
- WorkManager for background work.
- Hilt for dependency injection.
- FileProvider for sharing exported files.
- `filesDir` and app-managed media/project directories for local data.
- Optional network-capable providers and model downloads are gated by settings/model policy.
- Diagnostic and AI-use data remain local until the user exports or shares them.

## Feature Inventory

| Feature | User value | Entry point | Main code locations | Maturity | Coverage | Improvement opportunities |
|---|---|---|---|---|---|---|
| Project gallery and project lifecycle | Start, rename, duplicate, delete, recover projects | App home / project list | `ProjectListScreen.kt`, `ProjectListViewModel.kt`, `ProjectAutoSave.kt`, `ProjectArchive.kt` | Complete with active roadmap hardening | JVM and Compose smoke coverage | Publish package should be project-linked and archiveable without mutating project state unexpectedly |
| Timeline editing | Main editing workflow | Editor timeline | `Timeline.kt`, `TimelineChrome.kt`, `TimelineDrawing.kt`, `TimelineEditing.kt`, `TimelineInteractionPolicy.kt`, `EditorViewModel.kt` | Complete but undergoing decomposition | Focused JVM tests and roadmap refactor lane | Publish checklist can read markers, captions, aspect ratio, duration, and selected platform preset from timeline/export state |
| Export sheet and background export | Produce final media | Export panel/sheet | `ExportSheet.kt`, `ExportDelegate.kt`, `ExportService.kt`, `VideoEngine.kt`, `ExportConfig.kt` | Complete, with sidecar extras | Unit tests around export policies; CI builds APKs | Add `PublishPackageEngine` after render completion so sidecars and metadata are assembled deterministically |
| Subtitle export | Upload-ready captions | Export settings and editor caption export | `SubtitleExporter.kt`, `ExportDelegate.kt`, `EditorViewModel.kt` | Complete | `SubtitleExporterTest.kt` | Include SRT/VTT/ASS in publish package, choose default format per platform, expose missing-caption status |
| Auto chapters | YouTube description block | V3.69 feature panel | `AutoChapterEngine.kt`, `V369Delegate.kt`, `V369FeaturesPanel.kt` | Partial-to-complete | Engine tests likely present through search | Direct publish currently does not pass chapters into `PublishMeta`; package should include `chapters.txt` and description merge |
| AI-use ledger | Disclosure and provenance | AI tools and export sheet | `AiUsageLedger.kt`, `AiUsageRecordFactory.kt`, `ExportDelegate.kt`, `PrivacyDashboard.kt` | Complete for local declaration | `AiUsageLedgerTest.kt`, chip tests | Convert declarations into publish checklist rows and package files; keep platform disclosure wording aligned with YouTube/TikTok requirements |
| C2PA manifest sidecar | Content provenance | Export completion | `C2paExportEngine.kt`, `ExportDelegate.kt` | Partial: manifest builder exists, signing unavailable | `C2paExportEngineTest.kt` | Publish package should include sidecar now and later replace/add signed embedded MP4 when SDK is wired |
| Direct Publish | Send last export to social apps | V3.69 feature panel | `DirectPublishEngine.kt`, `V369Delegate.kt`, `V369FeaturesPanel.kt`, `DirectPublishEngineTest.kt` | Partial: share-intent fallback only | JVM tests for validation and text normalization | Add readiness checklist, metadata editor, tags/chapters/caption/thumb assets, package export, and API adapter contracts |
| Contact sheet export | Review sheet / thumbnails | Export path | `ContactSheetExporter.kt`, `ExportDelegate.kt` | Complete | Export delegate path, likely focused tests | Include contact sheet as optional package artifact and use frame selection for platform cover candidates |
| Thumbnail generation | Click-worthy preview/cover | AI thumbnail and frame capture surfaces | `AiThumbnailEngine.kt`, `FrameCapture.kt`, `StillImageOutputFiles.kt` | Partial/complete depending path | Engine tests around frame files | Add platform cover pack with aspect/dimension validation |
| Platform presets | Match target format | Export sheet | `ExportConfig.kt`, `ExportSheet.kt`, README feature list | Complete | Export config tests | Extend preset metadata to readiness rules: aspect, duration, captions, thumbnail, title length, disclosure |
| Stock asset engine | Search/import external assets | Stub/no active UI verified | `StockAssetEngine.kt` | Hidden/stub | Query validation pure code but no provider tests found | Do not activate search until rights ledger exists; provider attribution must be captured at import time |
| Content-ID engine | Rights risk hint | V3.69 content ID surface | `ContentIdEngine.kt`, `V369Delegate.kt` | Partial: local hash only | Not fully inspected | Treat as advisory readiness item; do not claim real match until AcoustID/Chromaprint path is implemented |
| Privacy dashboard | Trust and data flow clarity | Settings | `PrivacyDashboard.kt`, `PrivacyDashboardPanel.kt`, `SettingsScreen.kt` | Complete and active | `PrivacyDashboardTest.kt`, display tests | Add publish-package data inventory and platform handoff explanations |
| Diagnostic ZIP | Support and troubleshooting | Settings | `DiagnosticExportEngine.kt`, `SettingsScreen.kt` | Complete with active roadmap additions | Diagnostic tests | Include publish package manifest validation errors in diagnostics when user opts in |
| Release pipeline | Reproducible APK checks | GitHub Actions | `.github/workflows/build.yml`, `scripts/verify_release_artifacts.py`, `scripts/check_16kb_alignment.py` | Complete for APK release gates | CI workflow | Add docs-only validation for publish package schema and later release-check generated examples |
| Templates/plugins/LUTs/interchange | Reuse and NLE handoff | Editor panels, docs, import/export | `TemplateManager.kt`, `PluginRegistry.kt`, `EffectShareEngine.kt`, `LutEngine.kt`, `TimelineImportEngine.kt`, `ProjectArchive.kt` | Mixed: some complete, some scaffolded | Focused engine tests | Rights ledger should record reusable asset provenance where user imports stock/template/plugin assets |

## Competitive and Ecosystem Research

| Product/source | Notable capabilities | What NovaCut should learn | What NovaCut should avoid |
|---|---|---|---|
| CapCut official feature table | Cross-platform editor, auto aspect ratio, auto captions, TTS, background removal, templates, teamspace, TikTok Ads creative toolkit | Social distribution workflows win when aspect, captions, templates, and platform handoff are integrated in one flow | Avoid making a cloud/teamspace clone before local export trust is complete |
| YouTube Create help | Android/iOS creator app, imports media from gallery/other apps, manages projects, targets Shorts and long-form creation | First-party creator tools keep publish context close to editing context; NovaCut can compete by packaging captions, chapters, cover, and disclosure more transparently | Avoid assuming YouTube-only integration is enough for creators working across multiple platforms |
| YouTube Data API and disclosure docs | Upload API supports video metadata, captions API uploads caption tracks, AI disclosure setting exists in Studio, C2PA metadata can affect labels | YouTube readiness requires metadata, captions, AI disclosure, and provenance awareness; package files should map cleanly to API fields | Avoid direct YouTube public upload promises from unverified API projects; uploads can be restricted or audited |
| TikTok Content Posting API | Direct Post requires creator-info query, post initialization, export to TikTok servers, privacy options, audit/approval constraints | Build validators and UI from creator-info/platform capabilities rather than hardcoded assumptions | Avoid making direct upload the default while partner/audit status is unknown |
| LinkedIn Videos API | Video API supports initializing uploads with captions and thumbnails; media is later referenced in posts | A platform package should carry caption and thumbnail assets as first-class files, not only text in a share intent | Avoid one generic social caption that ignores professional/post context |
| C2PA Android SDK | Kotlin wrapper around C2PA C API for provenance functionality | NovaCut's pure manifest builder is well positioned; publish package can use sidecars now and signed embedding later | Avoid remote signing as a default; keep local Keystore/StrongBox posture |
| Pexels API | API key required, rate limits, attribution/backlink expected for higher limits and acceptable API use | Stock import must persist source URLs and attribution before provider activation | Avoid search-only UI that loses attribution after import |
| Pixabay API | Search/retrieve royalty-free images/videos; asks apps to show origin when search results display | The import flow should preserve origin and license evidence even when attribution is not mandatory | Avoid mixing license terms from different providers into one vague "free stock" label |
| Freesound API | Search and retrieve sound metadata with license-bearing audio assets | Audio stock requires license handling and credits, not only media copy | Avoid allowing SFX/music imports into exports without a visible credits/preflight path |
| Google Play Data safety/User Data guidance | Developers must complete Data safety, privacy policy must disclose collection/use/sharing, and declarations must match behavior | Publish package and direct upload flows should document user-directed sharing vs NovaCut-side collection clearly | Avoid adding network upload providers without updating privacy docs and data-safety worksheet |

## Highest-Value New Features

### Publish Package Manifest and Export

- Priority: P1
- Estimated complexity: M
- User problem solved: Creators finish an export but must manually collect subtitles, chapters, disclosure text, thumbnails, stock credits, and platform metadata. Missing one artifact can create upload friction or compliance mistakes.
- Evidence: `ExportDelegate` already writes notes, subtitles, `.ai-use.json`, and `.c2pa-manifest.json` sidecars; `DirectPublishEngine.PublishMeta` already has fields for title, description, tags, chapters, AI disclosure, and visibility; `V369Delegate.publishLastExport()` currently passes `tags = emptyList()` and does not pass chapters; official YouTube/LinkedIn APIs treat captions/thumbnails/metadata as upload assets.
- Proposed behavior: After a successful export, NovaCut can generate a deterministic folder or ZIP next to the rendered video:
  - `video.mp4`
  - `publish-manifest.json`
  - `description.txt`
  - `chapters.txt`
  - `captions.srt` and/or `captions.vtt`
  - `thumbnail.jpg` or `cover.png`
  - `contact-sheet.png`
  - `ai-use.json`
  - `c2pa-manifest.json`
  - `credits.txt`
  - `checksums.sha256`
- Implementation areas: new `PublishPackageEngine`, `ExportDelegate`, `DirectPublishEngine`, `ExportConfig`, `AiUsageLedger`, `C2paExportEngine`, `SubtitleExporter`, `ContactSheetExporter`, `FrameCapture`, tests under `app/src/test/java/com/novacut/editor/engine`.
- Data model/API/UI implications: introduce a versioned JSON schema such as `com.novacut.publish-package.v1`; no project autosave migration is required for a package generated from current state, but package preferences may later live in `ExportConfig`.
- Risks and edge cases: stale sidecars after repeated exports, file collisions, non-ASCII filenames, huge contact sheets, missing captions, user-disabled AI disclosure, private project names in manifests, package creation failure after successful video render.
- Verification plan: JVM tests for manifest schema, file naming, collision handling, checksum generation, redaction policy, missing optional assets, and sidecar inclusion; manual export flow validating package contents.

### Publish Readiness Checklist

- Priority: P1
- Estimated complexity: M
- User problem solved: Direct Publish currently opens target apps with a basic title and project notes. Creators do not know whether title, description, tags, chapters, captions, thumbnail, AI disclosure, rights credits, aspect ratio, and duration are complete before leaving NovaCut.
- Evidence: `V369FeaturesPanel` Direct Publish card only exposes a title text field and target chips; `DirectPublishEngine` already normalizes metadata but the UI does not collect tags/chapters; YouTube's AI-use setting and TikTok creator-info publishing UI both require platform-specific choices.
- Proposed behavior: Before share or package export, show a compact checklist by target:
  - Video file exists and is readable.
  - Export preset matches target aspect/duration.
  - Title and description are within target limits.
  - Tags/hashtags are sanitized.
  - Captions exist or are intentionally skipped.
  - Chapters exist for long-form targets.
  - Cover/thumbnail is selected.
  - AI disclosure is required, recommended, not required, or manually reviewed.
  - C2PA sidecar/signature status is available.
  - Credits/rights ledger is complete.
  - Content-ID check is complete or unavailable.
- Implementation areas: `V369FeaturesPanel`, `V369Delegate`, `DirectPublishEngine`, new `PublishReadinessPolicy`, `ExportConfig`, `AiUsageLedger`, `ContentIdEngine`.
- Data model/API/UI implications: readiness should be a pure value model that can be rendered in Compose and tested without Android UI.
- Risks and edge cases: platform docs change, duration/aspect constraints differ by account type, share intents may ignore extras, over-blocking user-directed sharing.
- Verification plan: JVM tests for each target policy; Compose screenshot/accessibility checks after existing Cycle 7 gate lands; manual share to installed platform apps.

### Rights and Attribution Ledger

- Priority: P1
- Estimated complexity: L
- User problem solved: Stock, template, LUT, plugin, SFX, and music assets can enter a project, but there is no verified project-level source/rights ledger to generate credits or warn before publishing.
- Evidence: `StockAssetEngine.StockAsset` already carries `author`, `authorUrl`, `licenseName`, and `attribution`; the engine comments say exporters must honor attribution, but there is no project-level ledger in `ProjectAutoSave` or package output; Pexels, Pixabay, and Freesound APIs all expose source/metadata flows that require at least provider origin clarity.
- Proposed behavior: Add `ProjectAttributionLedger` records for imported non-original assets:
  - asset ID and local media ID
  - provider/source type
  - title
  - author and URL
  - license name and URL
  - attribution text
  - import timestamp
  - usage range or clip IDs
  - commercial-use/ad-safety notes where known
- Implementation areas: `StockAssetEngine`, `ProjectAutoSave`, `ProjectArchive`, `MediaImportEngine`, `TemplateManager`, `EffectShareEngine`, `LutEngine`, `PrivacyDashboard`, `ExportDelegate`, publish package engine.
- Data model/API/UI implications: autosave schema migration, archive import/export compatibility, credits editor UI, privacy dashboard data-flow update.
- Risks and edge cases: user-provided assets with unknown source, edited/derived stock assets, conflicting provider requirements, legacy projects without ledger, imported archives from older versions.
- Verification plan: schema tests, archive round-trip tests, credits generation tests, legacy autosave migration test, UI checklist test with missing/unknown rights.

### Thumbnail, Cover, and Contact-Sheet Pack

- Priority: P2
- Estimated complexity: M
- User problem solved: Creators often need a thumbnail/cover separate from the MP4. NovaCut can capture frames and create contact sheets, but those are not first-class publish assets.
- Evidence: README advertises frame capture and contact sheets; `ContactSheetExporter` is called from `ExportDelegate`; YouTube and LinkedIn upload APIs support or rely on thumbnail/cover flows.
- Proposed behavior: Add a cover selector to choose current frame, generated thumbnail, or uploaded image; validate dimensions/aspect by target; add selected cover and optional contact sheet to publish package.
- Implementation areas: `FrameCapture`, `AiThumbnailEngine`, `ContactSheetExporter`, `ExportSheet`, `V369FeaturesPanel`, new cover policy tests.
- Data model/API/UI implications: store selected cover candidate in export/publish state; package engine copies normalized image.
- Risks and edge cases: HDR frame capture tone mapping, portrait/landscape mismatch, unavailable thumbnail extraction for remote/content URIs.
- Verification plan: tests for target dimension/aspect policy, manual exports for 16:9, 9:16, and 1:1 projects.

### Platform API Adapter Contracts and Dry-Run Validators

- Priority: P2
- Estimated complexity: L
- User problem solved: Direct uploads require OAuth, API quota, app audit, privacy updates, account-specific constraints, and platform UI fields. Implementing one-off uploads directly in `DirectPublishEngine` would create brittle code.
- Evidence: `DirectPublishEngine` comments already state API upload is future work requiring approval/configuration; YouTube uploads can be constrained by project verification; TikTok Direct Post requires creator-info query and publish initialization; LinkedIn requires upload initialization/finalization and versioned headers.
- Proposed behavior: Add sealed adapter interfaces without real credentials:
  - `PublishTargetCapabilitiesProvider`
  - `PublishDryRunValidator`
  - `PublishUploadAdapter`
  - target-specific DTOs for YouTube, TikTok, Meta, LinkedIn
  - fake/in-memory adapters for tests
- Implementation areas: `DirectPublishEngine`, new `publish` engine package, settings for provider capability state, privacy dashboard.
- Data model/API/UI implications: credentials remain absent until a future opt-in flow; package export remains default.
- Risks and edge cases: platforms changing required fields, unpublished API docs, per-account constraints, privacy/data-safety impact.
- Verification plan: unit tests with fake capability responses; no network in default tests; manual platform sandbox tests only after credentials and policy docs are ready.

### C2PA Sign-and-Embed Activation

- Priority: P2
- Estimated complexity: L
- User problem solved: Sidecar manifests are helpful but less durable than embedded Content Credentials in the MP4.
- Evidence: `C2paExportEngine` builds a structured manifest and reflection-probes for C2PA libraries; `signAndEmbed()` currently returns unavailable; C2PA Android SDK exposes Kotlin/C API bindings.
- Proposed behavior: After package engine lands, wire `c2pa-android` behind a build/license/native-alignment gate; sign with Android Keystore by default; include signed output status in readiness/package manifests.
- Implementation areas: `C2paExportEngine`, Gradle dependencies, native alignment scripts, `ExportDelegate`, `PrivacyDashboard`, tests.
- Data model/API/UI implications: add per-export Content Credentials status and error copy; do not require signing for ordinary export.
- Risks and edge cases: native library size/16 KB alignment, minSdk/API constraints, unsupported formats, key generation errors, StrongBox availability.
- Verification plan: JVM tests remain pure; device test signs a sample MP4; CI confirms APK alignment and license/model docs.

### Rights and Content-ID Preflight

- Priority: P2
- Estimated complexity: M
- User problem solved: Creators need a clear rights status before posting, but NovaCut only has local hashes and no external match service.
- Evidence: `ContentIdEngine` provides local fingerprint/hash behavior; `V369Delegate` can run content ID; current code does not prove real platform rights clearance.
- Proposed behavior: Surface a non-blocking readiness row: "Local fingerprint recorded", "External match unavailable", "Credits complete", or "Needs review". Later add Chromaprint/AcoustID for audio if license/network posture is accepted.
- Implementation areas: `ContentIdEngine`, publish readiness policy, `PrivacyDashboard`, `ProjectAttributionLedger`.
- Data model/API/UI implications: advisory status only; no upload blocking unless user opts into strict mode.
- Risks and edge cases: false confidence, network/privacy concerns, unsupported audio extraction, external API terms.
- Verification plan: tests for advisory wording and no false "cleared" state.

### Stock Asset Provider Activation

- Priority: P3
- Estimated complexity: L
- User problem solved: Creators want quick B-roll, photos, SFX, and music without leaving the editor.
- Evidence: `StockAssetEngine` already defines Pexels/Pixabay/Freesound/FMA providers but returns false/empty; provider docs require API keys, rate limits, license/source display, or attribution handling.
- Proposed behavior: Activate one provider at a time only after attribution ledger and privacy copy exist. Start with Pexels or Pixabay photo/video search because the data model already matches those providers. Freesound should wait until audio license rendering is solid.
- Implementation areas: `StockAssetEngine`, settings API-key storage, search UI, import pipeline, attribution ledger, privacy dashboard, network security config.
- Data model/API/UI implications: encrypted or carefully scoped key storage; provider-specific terms in settings; no bundled API key.
- Risks and edge cases: rate limits, key leakage, license changes, inappropriate search results, offline behavior.
- Verification plan: fake provider tests, no-network error tests, attribution round-trip tests, manual provider sandbox.

## Existing Feature Improvements

### Direct Publish Metadata Completeness

- Current behavior: `V369FeaturesPanel` only captures a title; `V369Delegate.publishLastExport()` uses project notes as description and sends `tags = emptyList()` without chapters.
- Problem or missed opportunity: `DirectPublishEngine.PublishMeta` already supports tags and chapters, but the user cannot provide them from the visible Direct Publish UI.
- Recommended change: Add description, tag, chapter inclusion, visibility, disclosure, and package buttons; pass `V369Delegate.youtubeChapterClipboard()` or formatted markers into `PublishMeta.chapters`.
- Code locations likely affected: `V369FeaturesPanel.kt`, `V369Delegate.kt`, `DirectPublishEngine.kt`, `DirectPublishEngineTest.kt`.
- Backward compatibility concerns: Existing share-intent behavior should remain available; default metadata should match current title/project-notes behavior.
- Verification plan: JVM tests for meta construction; Compose UI test for fields; manual share-intent inspection.
- Estimated complexity: S
- Priority: P1

### AI Disclosure Copy Alignment

- Current behavior: AI disclosure summary is generated from `AiUsageLedger.summaryLine()` and added to share text when non-empty.
- Problem or missed opportunity: YouTube distinguishes content that requires disclosure from minor assistance; TikTok and other platforms have their own disclosure UI. A generic summary is not enough to guide user choice.
- Recommended change: Add platform-specific disclosure guidance derived from ledger severity, with "required", "recommended", "not required", and "manual review" states.
- Code locations likely affected: `AiUsageLedger.kt`, `ExportSheet.kt`, `DirectPublishEngine.kt`, publish readiness policy, `PrivacyDashboard.kt`.
- Backward compatibility concerns: Do not rewrite existing `.ai-use.json` schema without versioning.
- Verification plan: ledger severity tests for YouTube/TikTok copy; snapshot tests for generated manifest.
- Estimated complexity: M
- Priority: P1

### C2PA Sidecar Status

- Current behavior: C2PA manifest sidecar is written when AI disclosure sidecar writes; signing is unavailable if the SDK is absent.
- Problem or missed opportunity: Users may not understand sidecar vs embedded Content Credentials.
- Recommended change: Publish package manifest should include `c2pa.status = sidecar_only | embedded_signed | unavailable | failed` and a user-facing status row.
- Code locations likely affected: `C2paExportEngine.kt`, `ExportDelegate.kt`, `PrivacyDashboard.kt`, publish package engine.
- Backward compatibility concerns: Keep existing `.c2pa-manifest.json` filename and schema stable.
- Verification plan: tests for status mapping and unavailable case.
- Estimated complexity: S
- Priority: P2

### Credits Generation

- Current behavior: `StockAssetEngine.attributionLine()` can format one asset, but no exporter consumes project-wide credits.
- Problem or missed opportunity: Credits and platform descriptions can omit required attribution or source context.
- Recommended change: Generate `credits.txt` and optional description footer from `ProjectAttributionLedger`.
- Code locations likely affected: new ledger, `StockAssetEngine.kt`, `ExportDelegate.kt`, publish package engine.
- Backward compatibility concerns: Legacy projects should show "No tracked external assets" rather than warning by default.
- Verification plan: import/ledger tests and package output tests.
- Estimated complexity: M
- Priority: P1

### Share Intent Multi-File Handoff

- Current behavior: `DirectPublishEngine` and `ExportDelegate.getShareIntent()` send one video stream and text extras.
- Problem or missed opportunity: Captions, thumbnails, and sidecars are not handed off with the video.
- Recommended change: Keep one-video share as default, but add package export/share for files that support `ACTION_SEND_MULTIPLE`; keep target-specific support as "best effort" because many social apps ignore extra streams.
- Code locations likely affected: `DirectPublishEngine.kt`, `ExportDelegate.kt`, FileProvider paths, manifest file provider config.
- Backward compatibility concerns: Some targets may reject multi-stream shares; default should remain single video.
- Verification plan: intent-construction tests; manual installed-app checks.
- Estimated complexity: M
- Priority: P2

### Publish Diagnostics

- Current behavior: Diagnostic ZIP focuses on app/device/media/model/log/timeline evidence.
- Problem or missed opportunity: Failed package generation or platform share errors are not captured as structured diagnostics.
- Recommended change: Add redacted `publish-readiness.json` and last package error only when user exports diagnostics.
- Code locations likely affected: `DiagnosticExportEngine.kt`, publish readiness policy.
- Backward compatibility concerns: Do not include project names, captions, media URIs, or account tokens.
- Verification plan: diagnostic ZIP tests with redaction assertions.
- Estimated complexity: S
- Priority: P3

## Reliability, Security, Privacy, and Data Safety

Bugs or risks found:

- Verified: Direct Publish claims YouTube/TikTok/Instagram/Threads/X/LinkedIn targets but only share-intent fallback is wired. This is honest in code comments, but the UI wording should not imply API upload.
- Verified: Direct Publish metadata is under-collected; tags and chapters exist in the engine model but are not passed from UI/delegate.
- Verified: C2PA signing is not available; only a sidecar can be produced today.
- Verified: Stock provider search is stubbed and should remain hidden until API-key, attribution, and privacy behavior are implemented.
- Likely: Multiple exported sidecars can drift from the final publish action because there is no single package manifest tying them to the MP4 checksum.

Missing guardrails:

- Publish package schema validation.
- File checksum generation for MP4 and included sidecars.
- Per-target readiness policy tests.
- Attribution completeness status for external assets.
- Redaction policy for project names and notes in publish manifests.
- Platform API approval/configuration state before any direct upload button.

Permission, network, and file-system concerns:

- Direct uploads would introduce user-data transmission from the device and must update privacy policy, Data safety worksheet, settings copy, and opt-in flows.
- Stock providers require user-supplied API keys or a documented service posture; do not ship hardcoded keys.
- FileProvider share grants should remain read-only and short-lived.
- Publish package output should stay in app-controlled/export-selected locations and avoid leaking absolute file paths.
- Package ZIP generation must bound file sizes and avoid blocking the UI thread.

Recovery and rollback needs:

- If package generation fails after a successful video export, keep the video export marked successful and show package failure separately.
- Package generation should be retryable from the last export.
- If C2PA signing fails, retain unsigned video and sidecar status instead of failing the entire export.
- If a package manifest version changes, older packages should remain readable by validation tools.

Logging and diagnostics needs:

- Log package-generation failures with redacted file basenames, not full media paths.
- Include a bounded, user-triggered publish readiness diagnostic entry.
- Record target, method (`share_intent`, `package`, `api_dry_run`, `api_upload`), and local validation status without account identifiers.

## UX, Accessibility, and Trust

Onboarding gaps:

- Users can see Direct Publish without knowing that only share-intent fallback is available. Add copy such as "Share via installed app" and reserve "Direct upload" for configured API adapters.
- Package export should appear as a reliable alternative when target apps ignore metadata extras.

Empty/loading/error/disabled states:

- No exported file: show package and publish actions disabled with "Export first".
- No captions: show "Captions not generated" with a one-tap route to captions.
- No chapters: show "No chapters" for long-form targets, not for short-form presets.
- No cover: show "Use current frame" or "Generate thumbnail" action.
- C2PA unavailable: show "Sidecar only" rather than an error.
- Unknown rights: show "Review external assets" with the ledger screen.

Destructive or irreversible actions:

- Publish API upload, once implemented, must require final confirmation and show target account, visibility, caption, and whether AI disclosure is selected.
- Package overwrite should use versioned names or explicit replacement confirmation.

Settings clarity:

- Add provider settings for API keys only when provider activation begins.
- Add a "Publish package defaults" settings group only after the feature ships.
- Privacy Dashboard should distinguish local package generation from remote direct upload.

Accessibility issues:

- The checklist must use text and icons, not color alone.
- Each row should have a content description that states status and required action.
- Disabled publish actions should explain why through accessible text.
- Existing Cycle 7 appearance/accessibility gate should cover the new checklist surface.

Microcopy and trust-signal improvements:

- "Share via app" for intent fallback.
- "Export publish package" for folder/ZIP handoff.
- "Direct upload not configured" until OAuth/API approval exists.
- "C2PA sidecar included" vs "Content Credentials embedded".
- "Credits complete" vs "Unknown external asset source".

## Architecture and Maintainability

Module or boundary improvements:

- Add a focused `publish` engine boundary instead of growing `DirectPublishEngine`.
- Keep readiness computation as pure Kotlin policy classes for fast JVM tests.
- Keep package writing separate from platform adapters.
- Use versioned JSON schemas for manifests and package validation.
- Keep provider-specific API clients behind interfaces with fake implementations for tests.

Refactor candidates:

- `DirectPublishEngine` should become orchestration around methods: share intent, package export, and future API upload.
- `V369FeaturesPanel` should delegate publish readiness rendering to a small Compose component once policy values exist.
- `ExportDelegate` should call one package engine after sidecars rather than manually coordinating every future publish artifact.

Test gaps:

- No publish package manifest tests because the feature does not exist.
- No target readiness policy tests.
- No Direct Publish UI tests for tags/chapters/captions/AI disclosure.
- No attribution ledger migration/archive tests.
- No multi-stream share-intent construction tests.

Documentation gaps:

- README advertises direct publishing surfaces but does not clearly distinguish share-intent fallback from platform API upload.
- Privacy docs should explain package generation and future direct uploads.
- `docs/models.md` should stay focused on model gates; publish package docs should live separately or in `ROADMAP.md` when promoted.

Release/build/deployment gaps:

- Existing release gate is strong for APK artifacts.
- Add a lightweight schema validator for sample publish packages after implementation.
- Any C2PA SDK/native dependency must pass 16 KB alignment and license checks before release.

## Prioritized Roadmap

- [ ] P1 - Add publish package engine and manifest schema
  - Why: Exported video, subtitles, AI-use, C2PA, chapters, thumbnails, and credits need one deterministic handoff.
  - Evidence: `ExportDelegate` writes several sidecars independently; official YouTube/LinkedIn upload flows treat captions/thumbnails/metadata as separate assets.
  - Touches: `app/src/main/java/com/novacut/editor/engine/PublishPackageEngine.kt`, `ExportDelegate.kt`, `AiUsageLedger.kt`, `C2paExportEngine.kt`, `SubtitleExporter.kt`, `ContactSheetExporter.kt`, `FrameCapture.kt`.
  - Acceptance: A successful export can produce a folder/ZIP with MP4, manifest JSON, text metadata, optional captions, optional thumbnail/contact sheet, AI-use JSON, C2PA sidecar, credits, and SHA-256 checksums.
  - Verify: `./gradlew :app:testDebugUnitTest --tests "*PublishPackage*"` plus manual export and file inspection.

- [ ] P1 - Add Direct Publish readiness policy
  - Why: Users need to know what is missing before leaving NovaCut for a target platform.
  - Evidence: `V369FeaturesPanel` has only a title input and target chips; `DirectPublishEngine.PublishMeta` already supports more metadata than the UI provides.
  - Touches: `DirectPublishEngine.kt`, new `PublishReadinessPolicy.kt`, `V369Delegate.kt`, `V369FeaturesPanel.kt`.
  - Acceptance: Each target reports structured rows for file, aspect/duration, title, description, tags, captions, chapters, cover, AI disclosure, C2PA, credits, and rights preflight.
  - Verify: `./gradlew :app:testDebugUnitTest --tests "*PublishReadiness*"` and manual UI pass.

- [ ] P1 - Wire chapters, tags, captions, and disclosure into publish metadata
  - Why: The engine accepts chapters and tags, but delegate/UI currently drops them.
  - Evidence: `V369Delegate.publishLastExport()` passes `tags = emptyList()` and omits `chapters`; `V369Delegate.youtubeChapterClipboard()` already formats chapter data.
  - Touches: `V369Delegate.kt`, `V369FeaturesPanel.kt`, `DirectPublishEngine.kt`, strings.
  - Acceptance: Share text and package metadata can include sanitized tags, chapters, AI disclosure summary, title, and description.
  - Verify: `./gradlew :app:testDebugUnitTest --tests "com.novacut.editor.engine.DirectPublishEngineTest"`.

- [ ] P1 - Add project attribution ledger
  - Why: Provider/source/license details must survive import and export for safe publishing.
  - Evidence: `StockAssetEngine.StockAsset` has attribution fields but no project-level ledger or exporter consumption.
  - Touches: `ProjectAutoSave.kt`, `ProjectArchive.kt`, `StockAssetEngine.kt`, `MediaImportEngine.kt`, `TemplateManager.kt`, `EffectShareEngine.kt`, `PrivacyDashboard.kt`.
  - Acceptance: Imported external assets can persist source, license, author, URL, attribution, and usage references; legacy projects load with an empty ledger.
  - Verify: `./gradlew :app:testDebugUnitTest --tests "*AttributionLedger*"`.

- [ ] P1 - Generate credits package output and checklist status
  - Why: Credits should be visible before publish and included with the package.
  - Evidence: Pexels/Pixabay/Freesound docs all require or strongly request source/license clarity in app flows.
  - Touches: publish package engine, attribution ledger, `PrivacyDashboard.kt`, Direct Publish UI.
  - Acceptance: `credits.txt` is generated when ledger entries exist; checklist flags unknown/uncleared assets without blocking original-user media.
  - Verify: unit tests for credits text and manifest inclusion.

- [ ] P2 - Add cover/thumbnail pack
  - Why: YouTube/LinkedIn and social workflows rely on covers/thumbnails, but NovaCut does not attach them to publishing.
  - Evidence: `FrameCapture`, `AiThumbnailEngine`, and `ContactSheetExporter` exist; LinkedIn Videos API supports thumbnail uploads.
  - Touches: `FrameCapture.kt`, `AiThumbnailEngine.kt`, `ContactSheetExporter.kt`, export/publish UI.
  - Acceptance: User can select current frame/generated thumbnail/contact sheet; package includes normalized cover and target status.
  - Verify: unit tests for policy plus manual 16:9/9:16/1:1 exports.

- [ ] P2 - Add platform API adapter contracts without credentials
  - Why: Direct upload requires platform-specific approval, quota, OAuth, and privacy work; contracts let NovaCut validate shape before network implementation.
  - Evidence: `DirectPublishEngine` comments already identify API upload as future and approval-gated; TikTok and YouTube official docs confirm multi-step/approval constraints.
  - Touches: `DirectPublishEngine.kt`, new target adapter interfaces, settings capability model, tests.
  - Acceptance: Fake adapters can dry-run YouTube/TikTok/LinkedIn/Meta capability responses; no real network credentials or uploads are shipped.
  - Verify: unit tests with fake adapters.

- [ ] P2 - Promote C2PA sidecar status and plan SDK activation
  - Why: Users need accurate provenance status now and embedded signing later.
  - Evidence: `C2paExportEngine.signAndEmbed()` returns unavailable; `C2paExportEngine.isAvailable()` reflection-probes for SDK classes.
  - Touches: `C2paExportEngine.kt`, publish package engine, export UI, privacy dashboard.
  - Acceptance: Package manifest records `sidecar_only` vs `embedded_signed` vs `unavailable`; UI wording is accurate.
  - Verify: `./gradlew :app:testDebugUnitTest --tests "com.novacut.editor.engine.C2paExportEngineTest"`.

- [ ] P2 - Add rights and Content-ID advisory preflight
  - Why: Rights state should be visible but not falsely claim clearance.
  - Evidence: `ContentIdEngine` is local-only today; no external match service is verified.
  - Touches: `ContentIdEngine.kt`, publish readiness policy, attribution ledger UI.
  - Acceptance: Checklist shows advisory states and never says "cleared" without a real provider.
  - Verify: unit tests for copy/status mapping.

- [ ] P2 - Add publish package validator script
  - Why: Release and support workflows need a deterministic way to validate package structure.
  - Evidence: CI already runs artifact validators; publish package schema should get the same local discipline once implemented.
  - Touches: `scripts/verify_publish_package.py`, sample package fixtures, docs.
  - Acceptance: Validator checks schema version, required files, optional asset references, checksums, and no absolute local paths.
  - Verify: `python scripts/verify_publish_package.py <sample-package-dir>`.

- [ ] P3 - Activate one stock provider behind the attribution ledger
  - Why: Stock search is valuable but unsafe before attribution and provider terms are persisted.
  - Evidence: `StockAssetEngine` is stubbed; Pexels/Pixabay APIs require keys/source display/rate-limit behavior.
  - Touches: `StockAssetEngine.kt`, settings, network client, attribution ledger, import UI.
  - Acceptance: One provider can search with a user key, import one asset, persist attribution, and appear in package credits.
  - Verify: fake provider unit tests and manual sandbox test.

- [ ] P3 - Add publish diagnostics export entry
  - Why: Support needs to debug package/readiness failures without receiving user media.
  - Evidence: `DiagnosticExportEngine` exists and roadmap adds more diagnostics; publish package errors would otherwise be transient.
  - Touches: `DiagnosticExportEngine.kt`, publish readiness policy, privacy dashboard.
  - Acceptance: Diagnostic ZIP can include redacted readiness/error JSON only after user-triggered export.
  - Verify: diagnostic ZIP unit tests with redaction checks.

## Quick Wins

- Pass chapters into `DirectPublishEngine.PublishMeta` from `V369Delegate.youtubeChapterClipboard()`.
- Add tag entry and sanitization preview to Direct Publish UI.
- Rename or clarify Direct Publish UI copy to "Share via installed app" until API uploads are configured.
- Add C2PA status copy: "Manifest sidecar included; embedded signing unavailable in this build."
- Add package manifest schema draft and tests before wiring UI.
- Generate `credits.txt` from an empty ledger with explicit "No tracked external third-party assets" wording.
- Add `DirectPublishEngineTest` coverage for chapter inclusion and target-specific body text.

## Larger Bets

- Project attribution ledger with autosave/archive migration and UI.
- Full publish package folder/ZIP with schema validator and package browsing.
- Platform API upload adapters with OAuth, quotas, approval states, and privacy-policy/Data-safety updates.
- C2PA Android SDK integration with local Keystore signing and native release gates.
- Stock provider activation with safe search, API key settings, import provenance, and license handling.
- A multi-platform campaign planner is intentionally later; first ship a trustworthy package for one export.

## Explicit Non-Goals

- Do not implement real platform API upload until credentials, approval requirements, privacy docs, and Data safety impact are handled.
- Do not add hardcoded stock-provider API keys.
- Do not claim real Content-ID clearance from local hashes.
- Do not replace existing share intents; keep them as the lowest-friction fallback.
- Do not make C2PA signing mandatory for ordinary exports.
- Do not broaden media/document import handling here; Cycle 6 and Cycle 8 already cover those lanes.
- Do not duplicate active roadmap items for crash handling, process-exit diagnostics, Play listing assets, accessibility gates, memory trim, notification permission, backup policy, or baseline profiles.

## Open Questions

- Which platform should be the first real API-upload target after package/readiness work: YouTube, TikTok, LinkedIn, or Meta?
- Should publish packages default to a folder for inspectability, a ZIP for sharing, or both?
- What privacy wording should be used when a package includes project title, description, captions, credits, and AI-use declarations?
- Which stock provider, if any, is acceptable for first activation under NovaCut's privacy and open-source distribution posture?
- Should package generation be automatic after every export or an explicit user action from the export-complete/share surface?

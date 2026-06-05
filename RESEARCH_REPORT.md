# NovaCut Research Report

Research synthesis for planning. The detailed source plans are archived under
[docs/archive/research](docs/archive/research/), and the previous expanded
roadmaps are archived under [docs/archive/roadmap](docs/archive/roadmap/).

Last refreshed: 2026-06-05.

## 2026-06-05 Cycle 16 Implementation Closure

- [Shipped] v3.74.55 closes the FileProvider grant-path contract promoted in
  Cycle 16. Camera captures now use a dedicated `cache-path` for
  `camera-captures/`, managed imported media is no longer exposed through a
  broad `filesDir/media` root, template exports have explicit external and
  internal roots, and export share/open URI failures fall back to user-visible
  copy or the app shell instead of crashing.
- [Verified] `FileProviderPathsTest` now enumerates the exact XML roots,
  rejects broad/private media paths, and scans source for all
  `getUriForFile(...)` producer files so future producers must update the
  contract.

## 2026-06-05 Cycle 23 Research Plan Reconciliation

- [Verified] `RESEARCH_FEATURE_PLAN.md` was added in `79019e0 docs: add
  research feature plan` as a root companion planning document after a broad
  pass over source, docs, build files, Android metadata, and external sources.
- [Verified] The companion plan's top recommendations do not require fresh
  duplicate build-lane rows today. FileProvider/camera coverage is already
  active in Cycles 15 and 16 and has in-progress source/test work in the
  worktree; signed C2PA is Cycle 13; direct publish naming is Cycle 14; the
  translated-locale, network-security, release-provenance, README-claim, and
  transform-gesture rows already exist in Cycle 1; local-network matrix work is
  Cycle 12; metadata-scrubbed archives are Cycle 18; SDH/audio-description is
  Cycle 19; product-health ledger is Cycle 20; caption translation activation is
  Cycle 21; conflict-safe sync is Cycle 22; and proxy workflow remains in the
  older research-backed engine candidates.
- [Promoted] Added a completed Cycle 23 reconciliation row to `ROADMAP.md` so
  implementers know the root companion plan is tracked, but the roadmap remains
  the implementation single source of truth.
- [Rejected] Do not duplicate the current FileProvider row while
  `MediaPicker.kt`, `strings.xml`, `file_paths.xml`, and
  `FileProviderPathsTest.kt` are already dirty with implementation-lane work.

## 2026-06-04 Cycle 22 Project Sync Refresh

- [Verified] `ProjectSyncEngine` is a live stub: it names local-folder,
  self-hosted, and LAN-peer targets, forbids last-writer-wins in its class
  contract, but `plan(...)` returns null and `sync(...)` returns "Sync backend
  not implemented." The detailed cross-device sync idea exists only in archived
  C.16 roadmap notes, not in the current active Researcher Queue.
- [Verified] NovaCut already has adjacent building blocks: Android backup rules
  for app-private metadata, `ProjectArchive` export/import with media manifest
  and schema checks, and `ProjectAutoSave` schema-version gating. Those pieces
  make a conservative archive/manifest sync path more realistic than timeline
  patch merging as a first implementation.
- [Verified] Current primary sources favor local-folder and WebDAV first.
  Android SAF supports user-selected directory-tree access. Syncthing documents
  block hashing, temporary files, conflict copies, and versioning. WebDAV and
  Nextcloud supply ETag/conflict/chunked-upload semantics. Git LFS provides an
  object-ID/size verification reference. JSON Patch/Merge Patch and CRDT tools
  are useful references but should wait until NovaCut defines timeline-level
  merge semantics.
- [Promoted] Added a P3 roadmap item for conflict-safe project sync planning:
  local folder and WebDAV only for the first release, opt-in per project,
  manifest hashes, no writes during planning, explicit conflict copies, chunked
  upload where supported, and LAN peer sync held behind the existing local
  network permission/security lane.
- [Rejected] Do not ship last-writer-wins sync, hidden cloud backup-as-sync,
  automatic LAN discovery, or timeline JSON patch merging as the first slice.
  Those paths can overwrite work, surprise users, or create merge semantics the
  current archive/autosave model does not yet define.

## 2026-06-04 Cycle 21 Caption Translation Activation Refresh

- [Verified] `CaptionTranslationEngine` is still a translation stub: readiness
  is always false, `downloadModel(...)` logs and returns false, and
  `translate(...)` maps source caption text back into target caption text.
  The UI and ViewModel path are farther along: the caption panel renders
  `CaptionTranslationPanel`, target-language selection calls
  `runCaptionTranslation(...)`, and row edit/regenerate state is already tested.
- [Verified] `docs/models.md` keeps `caption_translate` at
  `DEPENDENCY_MISSING` until exact model bytes, SHA-256, license posture,
  delivery mode, and F-Droid posture are recorded. The live roadmap summary
  already has a P3 caption-translation activation row, so the new queue entry
  expands that existing row rather than opening a duplicate lane.
- [Verified] Current sources split the implementation choices clearly. ML Kit
  Translation is mature and has explicit model-management APIs, but its Android
  data-disclosure page lists diagnostic/analytics collection and Translate
  source/destination language collection, so it needs Play-only disclosure and
  F-Droid handling. MADLAD-400 is Apache-2.0 and broad but large. NLLB distilled
  is broad but CC-BY-NC and research-oriented. Bergamot/Firefox Translation
  models keep text local and private but require narrower per-pair model and
  C++/Marian integration decisions.
- [Promoted] Added a P3 roadmap item for offline, reviewable caption translation
  activation with model/license/checksum gates, ML Kit as an optional disclosed
  adapter, open local models for F-Droid, no source-text echo presented as
  success, and exporter coverage for WebVTT/TTML/SRT, RTL/bidi text, SDH tags,
  speaker labels, and karaoke word timings.
- [Rejected] Do not route captions to cloud translation by default, do not ship
  NLLB as a production default under its noncommercial license, and do not mark
  source-text echo output as translated. Each of those would create user-trust,
  licensing, or accessibility-delivery risk.

## 2026-06-04 Cycle 20 Opt-In Product Health Refresh

- [Verified] `PrivacyDashboard` already exposes an `OPT_IN_TELEMETRY` category
  labeled "Opt-in usage telemetry (Sentry / Glean)" and marks it cloud/on-demand,
  deletable, opt-out-capable, and disabled by default. The only app startup
  observability currently installed is the local `CrashRecordStore` global
  handler, and the diagnostic ZIP path is user-triggered, local-first, redacted,
  and counts-only for its optional timeline-shape payload.
- [Verified] Grep across `app/src/main`, Gradle scripts, and
  `gradle/libs.versions.toml` finds no Sentry, Mozilla Glean, Firebase
  Analytics, Crashlytics, OpenTelemetry, JankStats, `PerformanceMetricsState`,
  or telemetry SDK dependency. This means the dashboard row is a future-facing
  contract, not a shipped network path.
- [Verified] Current Android vitals, JankStats, Play Data safety, User Data,
  F-Droid anti-feature, Mozilla Glean, OpenTelemetry Android, Sentry Android,
  Firebase Analytics, IETF DAP, Divvi Up, and W3C Privacy Principles sources all
  point toward the same planning constraint: collect the minimum useful product
  health signal, disclose SDK/provider behavior precisely, gate network upload
  behind affirmative consent, and preserve deletion/export controls.
- [Promoted] Merged the archived R5.5b aggregate-only usage-metrics idea into a
  new P2 roadmap item for a local-first product-health ledger. The first
  implementation should be a reviewable on-device counter ledger and diagnostic
  summary, with Sentry/Glean/Firebase/OpenTelemetry/DAP treated as later
  mutually exclusive adapter choices rather than hidden startup dependencies.
- [Rejected] Do not add default-on analytics, automatic crash upload, per-event
  timeline traces, raw stack-trace upload, screen-name analytics, persistent
  identifiers, or media/project metadata sampling. Those would conflict with
  NovaCut's local-first trust posture and create Play/F-Droid disclosure work
  before the product has a proved user benefit.

## 2026-06-04 Cycle 19 SDH and Audio Description Export Refresh

- [Verified] `V369FeaturesPanel` exposes a card titled "SDH + Audio
  Description" with subtitle "Bracketed non-speech tags + AD track stub
  (YAMNet planned)" and body copy that says it requires a transcript plus an
  export pass. There is no button or state path from that card into the
  delegate today, and grep finds no tests for `AudioDescriptionEngine`.
- [Verified] `AudioDescriptionEngine` can merge supplied non-speech events into
  captions as bracketed labels and can infer a generic `[music]` event from
  long transcript gaps. It validates manually-authored audio-description lines
  by dropping lines that collide with spoken word timestamps, but it does not
  generate visual-scene descriptions, synthesize narration audio, add a second
  audio track, duck program audio, export caption tracks, or mark any output as
  SDH/AD-ready.
- [Verified] Current W3C time-based media guidance distinguishes captions for
  prerecorded audio from audio description for prerecorded video. Captions
  cover audio information, including non-speech audio needed to understand the
  media, while WCAG 2.2 AA audio description requires synchronized spoken
  descriptions of important visual information in video. TensorFlow's current
  YAMNet docs describe a sound-classification model that predicts 521 audio
  event classes, which can support SDH event tagging but cannot supply visual
  descriptions.
- [Promoted] Added a P2 roadmap item to split SDH and audio description into
  honest, exportable contracts: SDH can start with reviewed non-speech event
  tags and caption export, while true AD requires user-authored or reviewed
  visual narration, TTS/audio-track rendering, mix/ducking, and explicit export
  validation before the UI claims the video has audio description.

## 2026-06-04 Cycle 18 Original-Media Metadata Privacy Refresh

- [Verified] NovaCut's local import path copies provider-supplied media bytes
  into `filesDir/media/imports` with an atomic `.partial` rename, and
  camera-capture finalization moves or copies the captured MP4 into the same
  managed-media directory. `ProjectArchive.exportArchive(...)` then copies each
  clip/image-overlay source stream into the `.novacut` ZIP and writes
  `media_manifest.json` entries containing the original URI string. There is no
  EXIF/GPS scrub, source-URI redaction, or user-facing "archive includes
  original media metadata" disclosure in these paths.
- [Verified] The privacy dashboard row for media metadata currently describes
  "durations, codecs, dimensions" collected by `MediaImportEngine` and
  `MediaPickerSheet`, but the retained bytes can also include provider-supplied
  image/video metadata such as camera, timestamp, location, source filename, or
  content-provider URI details depending on the source. Grep finds no
  `ExifInterface`, no `ACCESS_MEDIA_LOCATION`, no media-location copy, and no
  tests that assert archive manifests avoid leaking original URIs or that
  export/share/archive flows can produce sanitized media.
- [Verified] Android's current media docs say Photo Picker is a privacy-safe
  way to grant access only to selected images/videos, while MediaStore hides
  photo location information by default under scoped storage. Accessing
  unredacted EXIF location requires explicit `ACCESS_MEDIA_LOCATION` consent and
  `MediaStore.setRequireOriginal(...)`; AndroidX ExifInterface exposes GPS
  latitude/longitude tags that can be inspected or rewritten for supported image
  formats.
- [Promoted] Added a P2 roadmap item for an original-media metadata privacy
  contract: disclose that project archives include source media bytes, avoid
  requesting location metadata by default, redact archive source identifiers,
  and add an opt-in scrubbed archive/share/export path with tests for EXIF/GPS
  and manifest leaks.

## 2026-06-04 Cycle 17 Stock Asset Provider Terms Refresh

- [Verified] `StockAssetEngine` is still a stub: `isProviderConfigured(...)`
  always returns false, `search(...)` returns an empty result, and
  `download(...)` returns false. It names Pexels, Pixabay, Freesound, and Free
  Music Archive providers and carries `author`, `authorUrl`, `licenseName`, and
  `attribution` fields, but grep finds no UI wiring, provider API keys in
  `SettingsRepository`, HTTP clients, cache policy, download persistence, or
  unit tests for provider configuration and attribution.
- [Verified] The provider terms are materially different. Pexels requires API
  authorization, visible Pexels links, photographer credit when possible, and
  rate-limit tracking. Pixabay asks API users to show where results come from,
  rate-limits by API key, requires 24-hour caching, and forbids permanent
  image hotlinking. Freesound's API terms say free API use is non-commercial,
  require credit to Freesound and users according to sound licenses, and forbid
  bandwidth abuse or multiple keys to avoid limits. Free Music Archive content
  is per-track licensed, mostly Creative Commons, where BY/NC/ND/SA terms can
  decide whether export, remix, commercial posting, or no-attribution use is
  allowed.
- [Verified] The current single `attributionLine(...)` helper is too thin for a
  real stock-asset feature: it does not persist the source page, provider terms
  snapshot, license URL, commercial-use eligibility, derivative/no-derivatives
  constraint, search-result provider branding, rate-limit/cache metadata, or
  export-time credits. Treating every provider as the same "download and credit"
  flow would create both user-trust and licensing risk.
- [Promoted] Added a P2 roadmap item to implement stock assets as a
  provider-gated catalog with explicit API-key setup, per-provider
  rate-limit/cache handling, persisted source/license/attribution metadata, and
  export/share checks that block or warn when a selected asset's license does
  not allow the intended use.

## 2026-06-04 Cycle 16 FileProvider Grant-Path Refresh

- [Verified] The active Add Media camera action creates a pending MP4 in
  `cacheDir/camera-captures` via `pendingCameraCaptureDir(context)` and calls
  `FileProvider.getUriForFile(...)` before launching
  `ActivityResultContracts.CaptureVideo()`. `res/xml/file_paths.xml` exposes a
  cache path only for `frames/`, not `camera-captures/`, and the URI creation is
  not wrapped in a user-facing error path. Record Video can therefore fail
  before the external camera handoff ever opens.
- [Verified] The manifest provider itself is scoped correctly with
  `android:exported="false"` and `android:grantUriPermissions="true"`, but the
  repository has several FileProvider producers for diagnostics, archives,
  exports, direct publish, editor media, and TTS outputs. Existing tests cover
  selected directories such as TTS output, not a complete contract that every
  `getUriForFile(...)` producer maps to a narrow XML grant path. The fix should
  add only the missing explicit roots, not a broad root or catch-all files path.
- [Verified] AndroidX FileProvider documentation says available files must be
  declared in a `res/xml` paths file and `getUriForFile(...)` throws
  `IllegalArgumentException` when a requested file is outside the configured
  roots. Android's secure file-sharing setup also frames content-URI sharing as
  a temporary read grant to a recipient app rather than exposing file paths.
- [Promoted] Added a P1 roadmap item for a FileProvider grant-path contract:
  add a narrow `camera-captures/` cache root, cover all share/capture producers
  with path tests, keep unrelated private files rejected, and show actionable
  copy if URI generation fails.

## 2026-06-04 Cycle 15 Camera Capture Handoff Refresh

- [Verified] The active Add Media panel uses
  `ActivityResultContracts.CaptureVideo()` with a FileProvider URI under
  `cacheDir/camera-captures`, then moves successful captures into managed media
  through `finalizePendingCameraCapture(...)`. NovaCut declares
  `uses-feature android.hardware.camera required=false`, but grep finds no
  `android.permission.CAMERA`, `Manifest.permission.CAMERA`, CameraX dependency,
  or runtime camera permission launcher in `app/src/main`.
- [Verified] User-facing copy overstates the implemented path:
  `media_picker_capture_description` says "Record a clip without leaving
  NovaCut" and "Camera permission is only requested when you start recording",
  while the actual flow opens another camera activity and does not request
  NovaCut's own camera permission. `CameraCaptureEngine` still documents the
  intended in-app CameraX/teleprompter recorder but is a reflection-gated stub
  whose `startRecording(...)` returns false.
- [Verified] Android's current camera docs describe using an intent as the quick
  way to let an existing camera app capture photos or videos without direct
  camera-object work. Android 11 behavior changes restrict
  `ACTION_VIDEO_CAPTURE` responders to pre-installed system camera apps unless a
  specific third-party package/component is targeted. The manifest
  `uses-feature` docs say `required=false` prevents unnecessary Play filtering
  when camera hardware is optional, and CameraX VideoCapture docs cover the
  separate path for an in-app recorder that binds camera use cases.
- [Promoted] Added a P2 roadmap item to split today's external camera handoff
  from the future in-app CameraX recorder: correct the copy and permission
  expectations, add no-camera/no-handler error states, keep captured temp-file
  cleanup, and gate any true in-app recording path on CameraX dependencies,
  runtime camera permission, preview/audio policy, and UI tests.

## 2026-06-04 Cycle 14 Direct Publish Handoff Refresh

- [Verified] `DirectPublishEngine` is honest in code comments that "today only
  the share-intent fallback is wired", and the implementation always builds an
  `ACTION_SEND` intent with `FLAG_GRANT_READ_URI_PERMISSION`. It validates the
  exported file, normalizes title/description/chapters/tags/AI disclosure text,
  optionally targets an installed package, and returns `Method.SHARE_INTENT`;
  there is no OAuth token path, resumable upload client, platform SDK, publish
  status polling, or API-upload result path in `app/src/main`.
- [Verified] The visible surface still reads as "Direct Publish" and offers
  platform chips for YouTube, TikTok, Instagram, Threads, X, and LinkedIn.
  `V369Delegate.publishLastExport(...)` immediately starts the returned intent
  and shows no distinction between "opened the target app" and "posted
  successfully". The JVM tests cover file validation, share-body bounding, and
  which targets claim AI disclosure controls, not platform upload behavior.
- [Verified] Android's current sharing docs frame `ACTION_SEND` as sending data
  to another app and recommend the Android Sharesheet for sharing outside the
  app. Actual platform publishing has stricter flows: YouTube `videos.insert`
  requires authorized media upload and unverified API projects are restricted to
  private uploads; YouTube's resumable-upload guide recommends resumable upload
  for large files and mobile/unstable networks. TikTok Direct Post requires
  creator-info query, post initialization, exporting the video to TikTok
  servers, explicit user consent, and an audit to lift private-only restrictions
  for unaudited clients.
- [Promoted] Added a P2 roadmap item to split "share handoff" from real direct
  API publishing: rename/gate the current intent path, stop implying AI
  disclosure controls were set by NovaCut, add completion/status honesty, and
  keep YouTube/TikTok API upload adapters behind explicit OAuth, audit,
  encrypted-token, progress, cancellation, and consent requirements.

## 2026-06-04 Cycle 13 C2PA Export Provenance Refresh

- [Verified] NovaCut already tells users the export sheet will declare AI use in
  a "provenance sidecar" and `ExportDelegate.writeAiDisclosureSidecars(...)`
  writes both `.ai-use.json` and `.c2pa-manifest.json` drafts when the
  `AiUsageLedger` has disclosure entries. That path never calls
  `C2paExportEngine.signAndEmbed(...)`, so the exported MP4 itself remains
  unchanged.
- [Verified] `C2paExportEngine` is explicitly a manifest-construction stub:
  grep finds no `contentauth`, `c2pa-android`, or `simple-c2pa` dependency in
  Gradle, `isAvailable_returnsFalseWhenNoC2paLibraryOnClasspath` locks the
  absent-library state, and `signAndEmbed_returnsUnavailableWhenLibraryAbsent`
  asserts that signing returns `UNAVAILABLE`. The current JSON sidecar is
  therefore unsigned, detached, and not cryptographically bound to the media.
- [Verified] Current C2PA 2.4 says a claim gathers asset assertions, is hashed
  and signed, and a standard manifest contains exactly one hard binding to the
  asset. For BMFF/MP4 assets, C2PA embeds provenance through a `uuid` box in the
  media file. The official c2pa-android library now documents an AAR with Kotlin
  APIs for manifest creation/validation and Android Keystore, StrongBox, direct,
  callback, and web-service signing modes. CAWG Training and Data Mining 1.1
  separately defines the current training/mining assertion label as
  `cawg.training-mining` with `cawg.*` entries, while NovaCut still emits the
  older `c2pa.training-mining` / `c2pa.*` names.
- [Promoted] Added a P1 roadmap item to wire a real signed-and-embedded C2PA
  export path: add the Android C2PA dependency or a proven equivalent, sign and
  embed manifests into MP4 output, migrate the training/mining assertion labels
  to current CAWG names, gate user copy on actual availability, keep sidecars as
  diagnostic drafts only, and verify output with a C2PA reader plus tamper tests.

## 2026-06-04 Cycle 12 Local-Network Permission Refresh

- [Verified] `OutputStreamingEngine` is still a live-streaming stub, but it
  already exposes protocol metadata, destination validation, LAN/multicast
  classification, and `requiresLocalNetworkPermission(...)` tests for RTMP,
  SRT, RIST, RTSP, WebRTC, IPv4 private ranges, IPv6 local ranges, multicast,
  `.local`, and loopback cases.
- [Verified] The app has no local-network permission implementation yet. Grep
  found no `NEARBY_WIFI_DEVICES`, `ACCESS_LOCAL_NETWORK`,
  `RESTRICT_LOCAL_NETWORK`, local-network permission strings, or runtime
  permission launcher. The manifest still declares only media/export/audio,
  notification, haptics, internet, and network-state permissions.
- [Verified] Android's current local-network permission docs say Android 16
  restrictions are opt-in for target SDK 36 apps, Android 17 enforces local
  network blocking by default for target SDK 37+ apps, Android 16 testing uses
  the `RESTRICT_LOCAL_NETWORK` compat flag and `NEARBY_WIFI_DEVICES`, and the
  future enforced permission is `ACCESS_LOCAL_NETWORK` in the `NEARBY_DEVICES`
  group. Local TCP usually fails as timeout, while UDP/general denials usually
  return `EPERM`.
- [Promoted] Added a P2 roadmap item for an Android local-network permission
  gate around future LAN streaming destinations: public ingest URLs skip the
  prompt, LAN/mDNS/multicast destinations show scoped rationale and request the
  right platform permission, denied/revoked states produce actionable copy, and
  Android 16 compat plus Android 17 preview tests prove behavior before the
  streaming library lands.

## 2026-06-04 Cycle 11 Image Overlay Export Refresh

- [Verified] README advertises "Sticker/GIF/image overlays", and the app does
  persist `ImageOverlay` objects through autosave, undo snapshots, and archive
  media collection. The sticker picker also exposes bundled sticker categories
  plus a gallery-import route, so this is a visible creator workflow rather than
  a hidden model field.
- [Verified] The current overlay asset model is fragile. Bundled stickers are
  represented as `content://com.novacut.editor.stickers/...` URIs, but the
  manifest declares only AndroidX startup and FileProvider providers. Gallery
  stickers use `PickVisualMedia(ImageOnly)` and are passed straight into
  `viewModel.addImageOverlay(...)`; `OverlayDelegate` stores that source URI
  directly, unlike the main media picker path that copies clips into
  `filesDir/media/imports`.
- [Verified] Export and preview evidence points to a rendering gap:
  `ExportDelegate` disables stream-copy when `state.imageOverlays` exists, but
  the Transformer calls pass only `textOverlays` and tracked objects into
  `VideoEngine`; `VideoEngine` builds Media3 `OverlayEffect` entries from text
  overlays, Lottie overlays, and `config.watermark`; and `PreviewPanel` renders
  the current clip/player without consuming `imageOverlays`.
- [Verified] Android's current Photo Picker docs say default selected-media
  access lasts until the device restarts or the app stops unless the app takes a
  persistable grant, and SAF docs note even persisted document URI access is
  lost if the underlying document is moved or deleted. Media3's `BitmapOverlay`
  API supports bitmap/URI overlays through `TextureOverlay`, so the path forward
  is an app-owned overlay asset store plus preview/export compositing.
- [Promoted] Added a P1 roadmap item for durable image/sticker overlay
  compositing: import or generate durable overlay assets, replace fake bundled
  sticker URIs with real resolvable sources, render active overlays in preview,
  burn them into full exports, handle GIFs explicitly, and add restart/export
  tests that prove overlay assets remain available.

## 2026-06-04 Cycle 10 DataStore Settings Recovery Refresh

- [Verified] `SettingsRepository` creates the `novacut_settings` Preferences
  DataStore with the `preferencesDataStore` delegate and catches only
  `IOException` from `context.dataStore.data`, emitting `emptyPreferences()` for
  that case and rethrowing other failures. Its mapper already clamps or
  defaults readable-but-invalid settings, including enum values, ranges, and the
  bounded AcoustID string, but that protection runs only after DataStore can
  read the preferences file.
- [Verified] Grep found no `CorruptionException`, `ReplaceFileCorruptionHandler`,
  `PreferenceDataStoreFactory`, or settings-reset report. `SettingsViewModel`
  exposes `AppSettings()` as the initial state, so a non-IO DataStore failure
  could leave the UI on defaults without explaining whether persisted settings
  were reset, ignored, or unavailable.
- [Verified] `PrivacyDashboard` documents settings/preferences as local
  DataStore data with export/delete controls, while `DiagnosticExportEngine`
  has no settings-reset artifact. Android's official DataStore guide says
  unreadable corrupted data is surfaced as `CorruptionException` from the data
  flow unless a `corruptionHandler` replaces it, and the
  `ReplaceFileCorruptionHandler` API is the documented replacement hook.
- [Promoted] Added a P2 roadmap item for Preferences DataStore corruption
  recovery: install a handler that replaces a corrupted store with defaults,
  record a bounded reset reason/timestamp outside the corrupted store, show a
  one-time Settings notice, include a redacted diagnostic ZIP entry, and verify
  corrupted-file recovery without leaking AcoustID/API or proxy secrets.

## 2026-06-04 Cycle 9 Process-Exit Diagnostics Refresh

- [Verified] Grep found no `ApplicationExitInfo`,
  `getHistoricalProcessExitReasons`, `REASON_ANR`, `REASON_LOW_MEMORY`, or
  process-exit recorder in `app/src/main`. That means the existing diagnostics
  plan can capture active Java crashes only after the Cycle 1 uncaught-exception
  item lands, but it still lacks postmortem OS records for ANR, LMK, native
  crash, signal, freezer, initialization-failure, or resource-kill cases.
- [Verified] `NovaCutApp.VERSION` already calls out crash reports as a consumer
  of the build version, but `NovaCutApp.onCreate()` currently only creates the
  export notification channel. `DiagnosticExportEngine` writes app/device/codecs,
  model registry, optional timeline shape, logcat tail, and manifest entries,
  but no `process-exit-history` artifact.
- [Verified] Android 11+ exposes recent process-death records through
  `ActivityManager.getHistoricalProcessExitReasons()` returning
  `ApplicationExitInfo`; the API includes reason, status, process name,
  timestamp, memory sizes, and an ANR trace stream when available. Android
  vitals treats user-perceived ANRs and LMKs as release-health signals, with
  LMKs looking like crashes to users and bypassing normal lifecycle saving.
- [Promoted] Added a P2 roadmap item for `ApplicationExitInfo` capture in the
  local diagnostic ZIP: API 30+ startup ingestion, de-duplication, redacted ANR
  trace excerpts, pre-API-30 unsupported marking, privacy-dashboard retention
  copy, and JVM/instrumentation verification. This complements, rather than
  replaces, the existing uncaught-exception handler item.

## 2026-06-04 Cycle 8 Plugin/Interchange Import Refresh

- [Verified] NovaCut's docs and README expose several non-media file formats:
  the plugin family (`.novacut-template`, `.ncfx`, `.ncstyle`, `.cube`, `.3dl`,
  `.ncfxd`), LUT import, project archive ZIP export, and OTIO/FCPXML/EDL
  interchange. Android's current intent guidance says an app is listed for
  incoming content through matching manifest filters, and its `IntentFilter`
  API requires action, category, and data type/scheme to match.
- [Verified] The manifest currently registers only `content://` media
  `ACTION_VIEW` filters for `video/*`, `image/*`, and `audio/*`. The app's
  incoming path handles only `ACTION_VIEW`, reads one `intent.data` URI, rejects
  MIME types that do not start with `video/`, `image/`, or `audio/`, and stores
  the result in historical `pendingVideoUri` state. That leaves text/plain,
  application/json, application/octet-stream, XML-like interchange files, and
  archive ZIPs without a dedicated open/import route.
- [Verified] The engines are uneven but useful enough for a router:
  `PluginRegistry` already classifies NovaCut plugin extensions,
  `ProjectArchive.importArchiveWithReport(...)` already returns structured ZIP
  import diagnostics, while `TimelineImportEngine` currently detects
  FCPXML/OTIO/EDL and returns an explicit "not yet implemented" result. The
  next step should therefore be an import-preview/report router, not a claim
  that every parser is complete.
- [Promoted] Added a P2 roadmap item for a non-media document import router:
  specific document intent filters instead of broad `*/*`, `content://`
  validation, type/extension/size checks, routing to existing plugin/LUT/archive
  engines, honest blocked status for pending timeline parsers, and
  JVM/manifest/device verification across plugin, LUT, archive, OTIO, FCPXML,
  and EDL inputs.

## 2026-06-04 Cycle 7 Appearance & Accessibility Refresh

- [Verified] `Theme.kt` defines one hard-coded Catppuccin Mocha
  `darkColorScheme`, and `NovaCutTheme` always applies it. Grep found no light
  scheme, high-contrast scheme, system-theme branch, or Settings appearance
  preference, even though the standing build-machine handoff now asks each UX
  audit to walk light, dark, and high-contrast states.
- [Verified] The app has a Compose smoke test and Compose UI test dependency,
  but no `ui-test-junit4-accessibility` dependency and no
  `enableAccessibilityChecks()` / `tryPerformAccessibilityChecks()` call in
  `NovaCutSmokeTest.kt`. Android's current Compose accessibility testing docs
  recommend enabling those checks because they can catch contrast, small touch
  target, and traversal-order problems during UI test actions.
- [Verified] A local contrast sanity check showed primary text tokens are strong
  (`Text` on `Midnight` 13.59:1; `Subtext0` on `PanelHighest` 6.41:1), but
  lower-emphasis tokens are unsafe if used as semantic text/non-text indicators
  (`Overlay0` on `PanelHighest` 2.92:1; `CardStrokeStrong` on `Panel` 1.81:1).
  Android color-contrast guidance points to 4.5:1 for small text, 3:1 for large
  text, and recommends high-contrast themes or user color choice for primary
  content.
- [Promoted] Added a P2 roadmap item for an appearance-mode and contrast
  regression gate: document System/Dark/Light-or-dark-only/High-Contrast policy,
  add a high-contrast color path for editor surfaces, wire Compose accessibility
  checks into the smoke flow, and require screenshots plus TalkBack/contrast
  verification before closing the broad accessibility audit lane.

## 2026-06-04 Cycle 6 Incoming Media Refresh

- [Verified] The manifest exposes video/image/audio `ACTION_VIEW` filters and
  comments label image/audio entries as share targets, but grep found no
  `ACTION_SEND` or `ACTION_SEND_MULTIPLE` receiver. Android's current receiving
  guide says Sharesheet delivery uses those send actions and `EXTRA_STREAM`
  payloads for binary content.
- [Verified] `MainActivity` handles only a single `ACTION_VIEW` `intent.data`
  URI and stores it in historical `pendingVideoUri` state. The gallery then
  calls `ProjectListViewModel.createProjectFromImport(...)`, which imports the
  URI as `"video"` and rejects sources without a visual track, so image/audio
  handoff is not yet routed to overlay/audio import despite the manifest
  comments.
- [Promoted] Added a P1 roadmap item for Sharesheet-compatible incoming media:
  manifest send filters, a pure intent parser, media-type-aware routing for
  video/image/audio and multi-item shares, `content://` grant validation, and
  parser plus device/emulator verification.

## 2026-06-04 Cycle 5 Distribution Metadata Refresh

- [Verified] The Fastlane metadata tree contains only the English title,
  short description, full description, and one legacy changelog file. Grep found
  no Fastlane `images/` directory, screenshot folders, `featureGraphic`, store
  icon, `Fastfile`, or `Appfile`, while the active roadmap already tracks
  changelog history separately.
- [Verified] NovaCut's manifest and shipped surfaces include user-data-relevant
  paths (`INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`,
  `POST_NOTIFICATIONS`, app-owned media import/export, backup/transfer policy,
  optional model downloads, diagnostics, and user-directed sharing). Google Play
  requires privacy-policy and Data safety declarations to remain complete,
  accurate, and consistent with in-app behavior, while preview-asset guidance
  requires store listing assets such as app icon, feature graphic, and
  screenshots.
- [Promoted] Added a P2 roadmap item for a deterministic Play/Fastlane listing
  package: Fastlane-compatible graphics and screenshots, privacy-policy
  source/link, data-safety worksheet, and a release validator for asset
  dimensions/counts plus disclosure artifact presence.

## 2026-06-04 Cycle 4 Memory Pressure Refresh

- [Verified] NovaCut declares `android:largeHeap="true"` and owns multiple
  memory-sensitive caches (`VideoEngine.thumbnailCache`,
  `AudioEngine.waveformCache`, generated media/proxy caches, and the Settings
  thumbnail-cache preference), but grep found no `onTrimMemory`,
  `ComponentCallbacks2`, or `TRIM_MEMORY` handler. Android memory guidance
  recommends using `onTrimMemory()` to voluntarily reduce memory use, and the
  `<application>` docs warn that most apps should reduce memory use rather than
  depending on `largeHeap`.
- [Promoted] Added a P2 roadmap item for an app-level memory trim dispatcher that
  maps UI-hidden/background/moderate/critical trim levels to cache eviction and
  diagnostic breadcrumbs without disrupting active exports or visible editing.

## 2026-06-04 Cycle 3 Performance Verification Refresh

- [Verified] NovaCut has project-gallery/editor smoke coverage and release APK
  verification, but grep found no Baseline Profile module, generated
  `baseline-prof.txt`, `profileinstaller`, `benchmark-macro`, or macrobenchmark
  task wiring. Android's Baseline Profile guidance says shipping profiles lets
  ART ahead-of-time compile startup and common interaction paths for first launch
  and app updates; the creation guide recommends Jetpack Macrobenchmark and
  `BaselineProfileRule` for repeatable generation.
- [Promoted] Added a P2 roadmap item for Baseline Profile and Macrobenchmark
  coverage of launch, blank-editor open, export-sheet open, and timeline
  scroll/scrub critical-user journeys. This complements, rather than duplicates,
  the existing instrumentation-smoke and timeline-refactor lanes.

## 2026-06-04 Cycle 2 Platform Hardening Refresh

- [Verified] NovaCut targets SDK 36 and declares `ExportService` as
  `mediaProcessing`; `ExportService.kt` starts foreground work with
  `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` on Android 15+ but has no
  `Service.onTimeout(int, int)` override. Android's foreground-service type docs
  make timeout cleanup a release risk for long exports because the service must
  call `stopSelf()` within seconds after the timeout callback.
- [Verified] `POST_NOTIFICATIONS` is declared, but the only runtime permission
  launcher found in the editor requests `RECORD_AUDIO`. Android 13+ newly
  installed apps keep notifications off until requested, and denied foreground
  service notifications are absent from the notification drawer, so export
  progress needs a contextual permission/fallback path.
- [Verified] `LocalMediaImport.kt` stores app-owned imported media under
  `filesDir/media/imports`, while `backup_rules.xml` and
  `data_extraction_rules.xml` whitelist database/autosave/generated-media dirs
  but omit managed imports. Android Auto Backup docs make this important in both
  directions: include-whitelists exclude all unspecified files, cloud backup has
  a 25 MB quota, and Android 12+ permits different `<cloud-backup>` and
  `<device-transfer>` rules.
- [Implemented v3.74.35] Android 12+ data extraction rules now keep
  `media/imports` out of cloud backup, include it for device-to-device transfer,
  exclude partial import copies in both modes, and require cloud backup
  encryption capability. Legacy full-backup rules remain cloud-safe because they
  cannot split transfer modes.
- [Promoted] These findings were added to `ROADMAP.md` under
  `Researcher Queue (Cycle 2 - 2026-06-04)` as P0/P1 implementation-ready items
  with Android official-doc links, local file evidence, acceptance criteria, and
  verification recipes.

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

## Tracked Research Inputs

- [RESEARCH_FEATURE_PLAN.md](RESEARCH_FEATURE_PLAN.md)
- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md)
- [docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md](docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25-loop6.md)
- [docs/archive/roadmap/ROADMAP-2026-05-25.md](docs/archive/roadmap/ROADMAP-2026-05-25.md)
- [docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md](docs/archive/roadmap/CROSS-PROJECT-ROADMAP.md)

Older root `RESEARCH.md`, `HostShield-Research-Report.md`, and `research/`
paths are intentionally ignored local artifacts and were not folded into the
tracked archive.

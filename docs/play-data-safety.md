# NovaCut Play Data Safety Worksheet

Last updated: 2026-06-04

This worksheet is the versioned source for the Google Play Data safety form.
The in-app source of truth is `PrivacyDashboard.kt`; this document maps those
categories plus manifest permissions to Play disclosure answers. It is not a
legal filing by itself, but the release validator keeps it present and aligned
with the manifest permission list.

## Manifest Permission Map

| Permission | Purpose | Collected/shared posture |
|---|---|---|
| `android.permission.RECORD_AUDIO` | Voiceover recording when the user starts recording. | Optional user content, stored locally in the project/export path, not shared by NovaCut unless the user exports or shares the result. |
| `android.permission.FOREGROUND_SERVICE` | Keeps long-running export work alive. | No user data collected by the permission itself. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING` | Android media-processing foreground-service type for exports. | No user data collected by the permission itself. |
| `android.permission.POST_NOTIFICATIONS` | Shows export progress/cancel controls when the user allows notifications. | Optional notification display only; no NovaCut-side sharing. |
| `android.permission.VIBRATE` | Timeline and editing haptic feedback. | No user data collected by the permission itself. |
| `android.permission.INTERNET` | Optional model downloads and future consent-gated cloud tools. | Network paths are optional or feature-gated; editing works offline. |
| `android.permission.ACCESS_NETWORK_STATE` | Checks network availability before optional downloads/cloud-capable actions. | Device network state is used locally for gating and error copy; no NovaCut telemetry upload. |

## Privacy Dashboard Category Map

| Dashboard category | Play data type | Collected by default | Shared by NovaCut | Required or optional | Purpose and retention |
|---|---|---:|---:|---|---|
| Project content | User content: photos/videos/audio/sticker images, app activity within projects | Yes | No, except user-directed export/share | Required for editing | Stored locally until project/media-copy deletion or app storage clear; sticker/image overlays selected from the picker are copied to app-private overlay storage before project mutation. |
| Media metadata | App activity and file metadata such as codec, duration, dimensions | Yes | No | Required for editing/import | Stored locally with the project and removed when clips/projects are removed. |
| Downloaded ML models | App info/performance metadata about model install state | No | No | Optional | Kept until the user removes the model from Settings. Model files are not diagnostic payloads. |
| App preferences | App activity/preferences | Yes | No | Required for app settings | Kept until app data is cleared or the app is uninstalled. |
| Template library | User content/templates and app activity | Yes when templates are saved | No | Optional | Kept until template deletion. |
| Settings reset reports | Diagnostics/preferences recovery | Yes after settings-file corruption recovery | No automatic sharing | Required for local recovery diagnostics | Bounded local reset reason/timestamp records, redacted and included only in user-triggered diagnostic ZIPs. |
| Diagnostic logs | Diagnostics | No | No automatic sharing | Optional | Created only when the user exports a diagnostic ZIP; redacted and locally bounded. |
| Crash records | Diagnostics/crash breadcrumbs | Yes after fatal crashes | No automatic sharing | Required for local crash recovery diagnostics | Bounded local fatal-crash breadcrumbs, included only in user-triggered diagnostic ZIPs. |
| Process-death summaries | Diagnostics/process-death history | Yes on Android 11+ after process restarts | No automatic sharing | Required for local ANR/low-memory/native-crash diagnostics | Bounded local `ApplicationExitInfo` summaries, redacted and included only in user-triggered diagnostic ZIPs. |
| Cloud generative video calls | User content sent to a provider only after consent | No | Yes, with the selected provider only after explicit invocation | Optional | Provider retention is disclosed in the consent sheet before each call. |
| AI usage ledger | App activity and AI disclosure history | No until an AI tool is used | No | Optional | Stored in the project for export/disclosure review and removable from the project controls. |
| Opt-in usage telemetry | Diagnostics/app activity | No | No in the current release line | Optional and disabled | Future SDK adapters must remain off unless consent is true and a separate disclosure is shipped. |

## Current Declaration Summary

- NovaCut does not require an account for editing.
- NovaCut does not sell user data.
- NovaCut does not upload analytics or crash telemetry by default.
- User content can leave the device only through user-directed export/share,
  optional model/cloud actions, Android backup/device-transfer behavior, or
  another app selected by the user.
- The privacy policy source is `docs/privacy-policy.md`; the public URL used
  for Play listing metadata is stored in
  `fastlane/metadata/android/en-US/privacy_policy_url.txt`.

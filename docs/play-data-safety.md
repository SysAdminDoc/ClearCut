# Google Play Data safety worksheet — ClearCut

This is the source of truth for ClearCut's Play Console **Data safety** form. It
is derived from the in-app privacy dashboard (`PrivacyDashboard.kt`) and the
shipped manifests, and the release gate fails if a declared permission or a
dashboard category is missing from this file.

See [privacy-policy.md](privacy-policy.md) for the user-facing policy.

## Form answers

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — no user data is collected |
| Do you provide a way for users to request that their data is deleted? | **Yes** — all data is on-device and removable from the app or from Android's app storage settings |
| Does your app contain ads? | **No** |
| Data types collected | **None** |
| Data types shared | **None** |

ClearCut is local-first: projects, media, and derived data stay in app-private
storage. There is no account, no server, no advertising SDK, and no analytics
SDK. The optional network paths below are user-initiated and carry no personal
data, so they do not constitute collection under Play's definition — they are
documented here anyway.

## Optional, user-initiated network activity

| Path | Default | What is sent | Consent |
| --- | --- | --- | --- |
| Cloud generative video calls | Off | The media you submit to that tool | A consent sheet naming the provider, shown before each call |
| MediaPipe on-device task metrics (Google) | Off | Anonymous performance metrics: app id/version, task and mode, invocation and drop counts, latency, initialization errors. No frames or pixels | Versioned consent in Settings → Privacy; revoking closes running tasks |
| App update check | Off | An HTTPS GET to the public GitHub releases API. Nothing about the user or their projects | Settings → Updates toggle |
| ML model download | User-initiated | An HTTPS GET for the model file recorded in `models.md` | The user starts the download |

No telemetry provider (Sentry, Glean, or otherwise) is integrated. The
"Opt-in usage telemetry" dashboard row is a placeholder so that adding one is a
visible change rather than a silent one.

## On-device data categories

These mirror the in-app privacy dashboard one-for-one. None are collected in the
Play Data safety sense — they never leave the device except in a diagnostic ZIP
or project archive the user exports and shares themselves.

- **Project content** (clips, overlays, timelines, captions) — kept until the
  project is deleted or app storage is cleared. Cloud backup carries the project
  documents only; generated media is device-transfer or archive-export only.
- **Media metadata** (durations, codecs, dimensions) — discarded when the source
  clip leaves every project.
- **Downloaded ML models** — kept until removed in Settings → AI Models.
- **App preferences** — kept until app storage is cleared.
- **Saved templates / effect packs** — kept until the template is deleted.
- **Settings reset reports** — capped at the 16 most recent resets.
- **Diagnostic logs** and export-incident summaries — redacted, capped at the 10
  most recent incidents, included only in user-triggered diagnostic ZIPs.
- **Crash records** — capped at the 8 most recent records.
- **Process-death history** — capped at the 16 most recent unique records.
- **Cloud generative video calls** — consent-gated; see the table above.
- **MediaPipe on-device task metrics** — consent-gated; see the table above.
- **AI usage ledger** — stored inside the project autosave; clearable from the
  export disclosure review.
- **Opt-in usage telemetry** — not integrated; off.
- **App update check** — off by default; stores nothing.

## Declared permissions

Normal (published) builds — `app/src/main/AndroidManifest.xml`:

| Permission | Purpose | Data safety impact |
| --- | --- | --- |
| `android.permission.RECORD_AUDIO` | Voiceover recording, only while recording | Audio is written to app-private storage; never uploaded |
| `android.permission.FOREGROUND_SERVICE` | Runs the export service | None |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING` | Export service type on API 35+ | None |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Export service type fallback on API 34 (`maxSdkVersion="34"`) | None |
| `android.permission.POST_NOTIFICATIONS` | Export progress and completion notifications | None |
| `android.permission.VIBRATE` | Timeline haptics | None |
| `android.permission.INTERNET` | Model downloads, optional update check, consent-gated cloud tools | See the optional-network table |
| `android.permission.ACCESS_NETWORK_STATE` | Wi-Fi-only model download preference | None |

ClearCut requests no broad storage permission. Media is read through the Android
photo picker and Storage Access Framework, so the app sees only files the user
picks.

### Streaming flavor only

The `streaming` build flavor (`app/src/streaming/AndroidManifest.xml`) is **not**
what is published to Google Play or GitHub Releases. It declares:

- `android.permission.NEARBY_WIFI_DEVICES` (`neverForLocation`) — discovering
  local-network media sources
- `android.permission.ACCESS_LOCAL_NETWORK` — connecting to those sources

The release gate rejects a normal build that declares either permission, so they
cannot reach a published artifact without this worksheet changing first.

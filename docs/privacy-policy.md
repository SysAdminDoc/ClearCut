# ClearCut Privacy Policy

**Last updated: 2026-07-29**

ClearCut is a local-first Android video editor. Your projects, media, and edits
stay on your device. ClearCut has no account system, no advertising, and no
analytics SDK. Nothing in this policy is aspirational: every statement below
describes what the shipped app does.

## What ClearCut stores, and where

Everything in this section is stored in ClearCut's private app storage on your
device. None of it is transmitted anywhere unless a section below says
otherwise.

| Data | Why it exists | Retention |
| --- | --- | --- |
| Project content (clips, overlays, timelines, captions) | The projects you create | Kept until you delete the project or clear app storage |
| Media metadata (durations, codecs, dimensions) | Timeline layout and export planning | Discarded when the source clip leaves every project |
| Downloaded ML models (Whisper, segmentation) | On-device speech-to-text and background removal | Kept until you remove the model in Settings → AI Models |
| App preferences | Theme, export defaults, editor layout | Kept until you clear app storage |
| Saved templates and effect packs | Reusable looks you created or imported | Kept until you delete the template |
| Settings reset reports | Recovering from corrupted preferences | Capped at the 16 most recent resets |
| Diagnostic logs and export-incident summaries | Diagnosing a failed export | Capped at the 10 most recent incidents |
| Crash records | Fatal-exception breadcrumbs | Capped at the 8 most recent records |
| Process-death history | Android 11+ ANR / low-memory / native-crash summaries | Capped at the 16 most recent unique records |
| AI usage ledger | Per-project record of which AI tools touched a project | Stored inside the project autosave; clearable from the export disclosure review |

Diagnostic, crash, and process-death records are never sent anywhere. They are
included only in a diagnostic ZIP that **you** create and share, and paths and
URIs in them are redacted.

## What leaves your device

Only these, and each one requires you to act first:

- **Cloud generative video calls**: off unless you invoke a cloud generative
  tool, and a consent sheet discloses the provider before each call.
- **MediaPipe on-device task metrics**: off by default. If you grant consent in
  Settings → Privacy, Google's MediaPipe Tasks SDK uploads anonymous performance
  metrics (app id/version, task and mode, invocation and drop counts, latency,
  initialization errors). Your frames and pixels never leave the device.
  Revoking consent closes any running task and blocks it from starting again.
- **App update check**: off by default. When enabled in Settings → Updates,
  ClearCut makes a single TLS request to the public GitHub releases API to
  compare the latest tag with your installed version. No data is stored and no
  APK is downloaded or installed.
- **Model downloads**: fetching a Whisper or segmentation model contacts the
  model host recorded in `docs/models.md`. You start the download; nothing about
  your projects is sent with it.

ClearCut integrates no advertising SDK, no attribution SDK, and no usage
analytics. The "opt-in usage telemetry" row in the in-app privacy dashboard is a
placeholder for a future provider: no telemetry provider is integrated today,
and the row exists so it cannot be added silently.

## Backup and device transfer

ClearCut participates in Android's built-in backup. The **cloud** backup scope is
deliberately limited to your project documents (the project database and autosave
files). Android Auto Backup is capped at 25 MB per app and fails the whole backup
when that is exceeded, so generated timeline media: freeze frames, voiceovers,
text-to-speech audio, noise-reduced audio, stabilized clips: is excluded. A
cloud restore therefore returns your projects with that generated media missing.

**Device-to-device transfer** has no such quota and carries everything, including
your imported media copies. You can also export a project archive yourself at any
time, which contains the complete project.

Cloud backup is disabled entirely if your device lacks backup encryption
capabilities. You can turn Android backup off for ClearCut in your device's
system settings.

## Permissions

| Permission | What it is used for |
| --- | --- |
| `RECORD_AUDIO` | Recording a voiceover, only while you are recording one |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC` | Keeping an export running when you leave the app |
| `POST_NOTIFICATIONS` | Export progress and completion notifications |
| `VIBRATE` | Haptic feedback on timeline edits |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Model downloads, the optional update check, and consent-gated cloud tools |

Media is read through the Android photo picker and Storage Access Framework, so
ClearCut requests no broad storage permission and can only see files you pick.

The `NEARBY_WIFI_DEVICES` and `ACCESS_LOCAL_NETWORK` permissions appear **only**
in the separate `streaming` build flavor, which is not what is published on Play
or GitHub Releases. Normal builds declare neither, and the release gate fails if
they appear.

## Deletion

- **A project**: delete it from the Projects screen; it moves to trash, and
  emptying the trash removes the project, its autosave, and its managed media
  copies.
- **AI models**: Settings → AI Models → Remove model.
- **Diagnostic, crash, and process-death records**: clearing app storage removes
  them; they are also capped and rotate automatically.
- **Everything**: Android Settings → Apps → ClearCut → Storage → Clear storage
  removes all ClearCut data from the device. Uninstalling does the same.

Because ClearCut has no account and stores nothing about you on a server, there
is no server-side copy to request deletion of.

## Children

ClearCut is not directed at children and collects no personal information from
anyone, including children.

## Changes to this policy

Changes are published in this file in the ClearCut repository, and its history is
public. The "Last updated" date above changes whenever the substance changes.

## Contact

Questions about this policy, or about anything ClearCut does with your data:

- Open an issue at <https://github.com/SysAdminDoc/ClearCut/issues>
- Email: matt_parker@outlook.com

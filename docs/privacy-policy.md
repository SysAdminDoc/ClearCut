# NovaCut Privacy Policy

Last updated: 2026-06-04

NovaCut is a local-first Android video editor. Editing, project storage,
timeline autosave, export, privacy dashboard review, and diagnostic ZIP creation
run on the user's device. NovaCut does not require an account for editing and
does not operate a NovaCut telemetry backend in the current release line.

## Data NovaCut Stores

NovaCut stores project content, clip metadata, app preferences, templates, model
registry state, AI usage ledger entries, fatal-crash breadcrumbs, Android 11+
process-death summaries, settings-reset reports, and optional diagnostic ZIPs in app-private storage
unless the user explicitly exports or shares a file.

Project archives and exported videos are created only when the user chooses an
export/share action. Android backup and device-transfer rules keep managed media
imports out of cloud backup while allowing app-private project metadata and
settings to move through supported device-transfer flows.

## Network Use

Editing works offline. Network access is used only for user-initiated or
feature-gated paths such as optional model downloads, optional cloud-capable
generative tools when a consent sheet is accepted, or user-directed Android
share/export flows handled by other apps. NovaCut does not initialize analytics
or crash-reporting SDK uploads by default.

## Microphone, Notifications, And Files

NovaCut may request microphone access for voiceover recording. Notification
permission is used for foreground export progress on supported Android versions.
Content shared into NovaCut from other apps is copied or referenced only after
the user chooses NovaCut as the target.

## Retention

Project data remains until the project is deleted. App preferences remain until
the app is uninstalled or storage is cleared. Downloaded models remain until the
user removes them from Settings. Diagnostic ZIPs are capped to the most recent
local bundles, crash/process-death breadcrumbs are bounded, and memory-trim
breadcrumbs and settings-reset reports are bounded under the diagnostics
directory.

## Deletion

Users can delete projects from the project list, remove downloaded models in
Settings, clear templates, export/delete diagnostic bundles, and clear all local
NovaCut data through Android system app storage controls. Because NovaCut does
not require accounts for editing, there is no NovaCut account-deletion flow.

## Contact

For privacy questions, open a GitHub issue at:

https://github.com/SysAdminDoc/NovaCut/issues

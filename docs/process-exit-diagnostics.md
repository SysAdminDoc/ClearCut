# Process-Exit Diagnostics

NovaCut records Android process-death summaries locally so user-triggered
diagnostic ZIPs can distinguish Java crashes from ANRs, native crashes,
low-memory kills, signals, freezer kills, and other OS-level terminations.

## Source

`ProcessExitRecorder` reads `ActivityManager.getHistoricalProcessExitReasons()`
on Android 11 / API 30 and newer. Older devices write an explicit unsupported
payload so support can tell "not available on this Android version" from "no
recent exits".

The recorder stores a bounded JSON history at:

```text
filesDir/diagnostics/process-exit-history.json
```

The diagnostic ZIP includes the same content as:

```text
process-exit-history.json
```

## Stored Fields

Each unique record is de-duped by timestamp, reason, and PID, then capped to the
latest 16 entries. Records include:

- process name
- PID
- timestamp
- reason code and reason label
- exit status
- process importance code and label
- PSS/RSS memory samples in KB
- redacted description
- redacted and truncated trace excerpt when Android exposes one

## Privacy Boundary

- No process-exit data is uploaded by NovaCut.
- Diagnostic ZIPs are created only after the user taps Export diagnostic ZIP.
- Descriptions and traces run through the same URI/path/email redaction as
  logcat, plus key-value redaction for caption, transcript, project name, media
  URI, and source URI fields.
- The existing `CrashRecordStore` remains responsible for active Java
  uncaught-exception breadcrumbs. `ProcessExitRecorder` covers postmortem OS
  process records that may not pass through Java exception handling.

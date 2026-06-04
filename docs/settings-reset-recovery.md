# Settings Reset Recovery

Last updated: 2026-06-04

NovaCut stores app preferences in the `novacut_settings` Preferences DataStore.
Readable but invalid values are handled per key by `SettingsRepository`: unknown
enums and out-of-range numeric values fall back to defaults while valid values
in the same file stay intact.

If the DataStore file itself is unreadable, NovaCut uses
`ReplaceFileCorruptionHandler` through `PreferenceDataStoreFactory` to replace
the file with empty preferences. That emits the normal `AppSettings` defaults
and allows later writes to succeed.

Each file-level recovery also writes a bounded local report:

```text
filesDir/diagnostics/settings-reset-report.jsonl
```

The report includes the reset timestamp, reason, error type, and a sanitized
message. It is independent of the corrupted settings file and redacts content
URIs, file paths, URLs with query strings, AcoustID/API keys, tokens, passwords,
and proxy credentials.

Settings shows a dismissible notice when a reset report is found after loading
preferences. Diagnostic ZIP exports include `settings-reset-report.jsonl` only
when reset reports exist.

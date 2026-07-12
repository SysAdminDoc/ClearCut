package com.novacut.editor.engine

/**
 * R5.5c — Privacy dashboard data model.
 *
 * Single source of truth for every category of data ClearCut collects, where
 * the data lives, what user controls exist, and how the user can export or
 * delete it. The Settings → Privacy dashboard Composable consumes this
 * model directly so the displayed surface is automatically in sync with
 * what the engines actually do.
 *
 * The dashboard is **also** the source for any future Play Store data-safety
 * form changes — if a category is not represented here, it must not be
 * collected. New categories are added by appending a [Category] entry plus
 * a [DashboardEntry] with the engine that owns the data.
 *
 * Pure Kotlin. Tests verify the invariants the dashboard's UX depends on
 * (no on-by-default cloud collection, every entry has a delete action,
 * every entry references its retention policy).
 */
object PrivacyDashboard {

    enum class Category(val displayName: String) {
        PROJECT_CONTENT("Project content (clips, overlays, timelines, captions)"),
        MEDIA_METADATA("Media metadata (durations, codecs, dimensions)"),
        ML_MODELS("Downloaded ML models (Whisper, MediaPipe)"),
        APP_PREFERENCES("App preferences (theme, export defaults)"),
        TEMPLATE_LIBRARY("Saved templates / effect packs"),
        SETTINGS_RESET_REPORTS("Settings reset reports (preferences recovery)"),
        DIAGNOSTIC_LOGS("Diagnostic logs and export-incident summaries (redacted)"),
        CRASH_RECORDS("Crash records (fatal exception breadcrumbs)"),
        PROCESS_EXIT_HISTORY("Process-death history (ANR, low-memory, native crash)"),
        CLOUD_GENERATIVE("Cloud generative video calls (consent-gated)"),
        AI_USAGE_LEDGER("AI usage ledger (per-project disclosure history)"),
        OPT_IN_TELEMETRY("Opt-in usage telemetry (Sentry / Glean)"),
        UPDATE_CHECK("App update check (sideload / GitHub-release version lookup)"),
    }

    /**
     * Where the data physically lives.
     */
    enum class StorageLocation(val displayName: String) {
        DEVICE_INTERNAL("On this device, app private"),
        DEVICE_SHARED("On this device, shared with other apps"),
        CLOUD_ON_DEMAND("Cloud service (only when explicitly invoked)"),
    }

    /**
     * Controls the user has over a category.
     *
     * @param canExport user can export a copy via the diagnostic ZIP /
     *   project archive / explicit per-category export.
     * @param canDelete user can wipe the category from on-device storage.
     * @param hasOptOut user can disable collection entirely from Settings.
     */
    data class Controls(
        val canExport: Boolean,
        val canDelete: Boolean,
        val hasOptOut: Boolean,
    )

    data class DashboardEntry(
        val category: Category,
        val location: StorageLocation,
        val controls: Controls,
        /**
         * Where the data is collected from / written by. Used so the UX can
         * say "Stored by VideoEngine, Project autosave" rather than a vague
         * "Stored locally".
         */
        val collectedBy: List<String>,
        /**
         * How long the data persists by default. Human-readable copy
         * intended for the dashboard row's secondary line.
         */
        val retentionPolicy: String,
        /**
         * Whether this category is collected by default. Cloud + telemetry
         * paths must be `false` — they require explicit consent.
         */
        val collectedByDefault: Boolean,
    )

    /**
     * Canonical dashboard rows. Keep ordered by Category enum declaration so
     * the displayed list is deterministic.
     */
    val entries: List<DashboardEntry> = listOf(
        DashboardEntry(
            category = Category.PROJECT_CONTENT,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("ProjectAutoSave", "ProjectDatabase", "ProjectArchive", "OverlayAssetStore"),
            retentionPolicy = "Kept until the project/media copy is deleted or app storage is cleared.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.MEDIA_METADATA,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("MediaImportEngine", "MediaPickerSheet"),
            retentionPolicy = "Discarded when the source clip is removed from any project.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.ML_MODELS,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = false, canDelete = true, hasOptOut = true),
            collectedBy = listOf("ModelDownloadManager"),
            retentionPolicy = "Kept until the user removes the model from Settings → AI Models.",
            collectedByDefault = false,
        ),
        DashboardEntry(
            category = Category.APP_PREFERENCES,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("SettingsRepository", "DataStore"),
            retentionPolicy = "Kept until the app is uninstalled or storage is cleared.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.TEMPLATE_LIBRARY,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("TemplateManager"),
            retentionPolicy = "Kept until the template is removed from the Templates panel.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.SETTINGS_RESET_REPORTS,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("SettingsRepository", "SettingsResetReportStore", "DiagnosticExportEngine"),
            retentionPolicy = "Preferences corruption-recovery reports are stored locally under filesDir/diagnostics/settings-reset-report.jsonl, capped to the 16 most recent resets, and included only in user-triggered diagnostic ZIP exports.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.DIAGNOSTIC_LOGS,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("DiagnosticExportEngine", "ExportIncidentStore"),
            retentionPolicy = "Private export incidents are capped to 10 records under filesDir/diagnostics/export-incidents. User-triggered diagnostic ZIPs include only bundle-pseudonymized structured summaries and are capped to the 3 most recent ZIPs.",
            collectedByDefault = false,
        ),
        DashboardEntry(
            category = Category.CRASH_RECORDS,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("CrashRecordStore", "DiagnosticExportEngine"),
            retentionPolicy = "Fatal-crash breadcrumbs are stored locally under filesDir/diagnostics/crashes, capped to the 8 most recent records, and included only in user-triggered diagnostic ZIP exports.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.PROCESS_EXIT_HISTORY,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("ProcessExitRecorder", "DiagnosticExportEngine"),
            retentionPolicy = "Android 11+ process-death summaries are stored locally under filesDir/diagnostics/process-exit-history.json, capped to the 16 most recent unique records, and included only in user-triggered diagnostic ZIP exports.",
            collectedByDefault = true,
        ),
        DashboardEntry(
            category = Category.CLOUD_GENERATIVE,
            location = StorageLocation.CLOUD_ON_DEMAND,
            controls = Controls(canExport = false, canDelete = true, hasOptOut = true),
            collectedBy = listOf("GenerativeVideoPolicy"),
            retentionPolicy = "Per the provider's policy; disclosed in the consent sheet before each call.",
            collectedByDefault = false,
        ),
        DashboardEntry(
            category = Category.AI_USAGE_LEDGER,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("AiUsageLedger", "ProjectAutoSave", "ExportDelegate", "DirectPublishEngine", "C2paExportEngine"),
            retentionPolicy = "Stored only inside the project autosave; users can clear it from the export " +
                "disclosure review. Export can write local .ai-use.json and unsigned " +
                ".c2pa-draft-manifest.json sidecars; remote C2PA signing requires explicit per-export " +
                "consent before any media or hashes leave the device.",
            collectedByDefault = false,
        ),
        DashboardEntry(
            category = Category.OPT_IN_TELEMETRY,
            location = StorageLocation.CLOUD_ON_DEMAND,
            controls = Controls(canExport = false, canDelete = true, hasOptOut = true),
            collectedBy = listOf("(future) SentryAndroid", "(future) Mozilla Glean"),
            retentionPolicy = "Provider retention; disabled by default; toggle in Settings → Privacy.",
            collectedByDefault = false,
        ),
        DashboardEntry(
            category = Category.UPDATE_CHECK,
            location = StorageLocation.CLOUD_ON_DEMAND,
            controls = Controls(canExport = false, canDelete = true, hasOptOut = true),
            collectedBy = listOf("UpdateChecker"),
            retentionPolicy = "No data is stored. When enabled, ClearCut makes a single TLS request to the " +
                "public GitHub releases API to compare the latest tag with the installed version; it never " +
                "downloads or installs an APK. Off by default; turn it off in Settings → Updates to stop all checks.",
            collectedByDefault = false,
        ),
    )

    /**
     * Return the entry for a category, or null if the category isn't tracked.
     */
    fun entryFor(category: Category): DashboardEntry? =
        entries.firstOrNull { it.category == category }

    /**
     * Categories that involve the network or external services. The dashboard
     * groups these into a "Cloud & Telemetry" section with extra prominence
     * for the opt-out toggles.
     */
    fun cloudOrTelemetryCategories(): List<DashboardEntry> =
        entries.filter { it.location == StorageLocation.CLOUD_ON_DEMAND }

    // --- Display-layer helpers (Batch 15) ---
    //
    // The Compose panel orders rows by risk so the user reads the most
    // disclosure-bearing entries first (cloud paths > opt-in collection >
    // device-only enabled-by-default > device-only opt-in). These helpers
    // give the panel a stable contract without rewriting the entries list.

    /**
     * Coarse-grained section the dashboard renders. Cloud/telemetry sit at
     * the top with prominent opt-out chips; on-device collected-by-default
     * comes next; on-device opt-in (the user already turned this on at some
     * point) renders last.
     */
    enum class Section(val displayName: String) {
        CLOUD_AND_TELEMETRY("Cloud & telemetry"),
        ON_DEVICE_COLLECTED("Stored on this device"),
        ON_DEVICE_OPT_IN("Opt-in features (off by default)"),
    }

    /**
     * Classify an entry into its display section.
     */
    fun sectionFor(entry: DashboardEntry): Section = when {
        entry.location == StorageLocation.CLOUD_ON_DEMAND -> Section.CLOUD_AND_TELEMETRY
        entry.collectedByDefault -> Section.ON_DEVICE_COLLECTED
        else -> Section.ON_DEVICE_OPT_IN
    }

    /**
     * Return the dashboard entries already sorted for display: cloud first,
     * then collected-by-default, then opt-in. Within a section the original
     * [Category]-enum order is preserved so the panel rendering is stable.
     */
    fun sortForDisplay(): List<DashboardEntry> {
        val rank = mapOf(
            Section.CLOUD_AND_TELEMETRY to 0,
            Section.ON_DEVICE_COLLECTED to 1,
            Section.ON_DEVICE_OPT_IN to 2,
        )
        return entries.withIndex()
            .sortedWith(
                compareBy<IndexedValue<DashboardEntry>> { rank[sectionFor(it.value)] }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    /**
     * Group the dashboard entries by [Section] for direct rendering as
     * Compose sections. The returned map's iteration order matches
     * [sortForDisplay] — cloud first, then collected-by-default, then
     * opt-in. Empty sections are omitted entirely so the panel doesn't
     * render an empty header.
     */
    fun groupForDisplay(): Map<Section, List<DashboardEntry>> {
        val out = linkedMapOf<Section, MutableList<DashboardEntry>>()
        for (entry in sortForDisplay()) {
            out.getOrPut(sectionFor(entry)) { mutableListOf() }.add(entry)
        }
        return out
    }

    /**
     * Short summary of what the user can do with an entry, suitable for a
     * single-line caption. The wording is intentionally action-oriented —
     * users want to know "what can I do here" not "what is this category".
     */
    fun controlSummary(entry: DashboardEntry): String {
        val parts = mutableListOf<String>()
        if (entry.controls.canExport) parts += "Export"
        if (entry.controls.canDelete) parts += "Delete"
        if (entry.controls.hasOptOut) parts += "Opt out"
        return if (parts.isEmpty()) "Read-only" else parts.joinToString(" · ")
    }
}

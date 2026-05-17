package com.novacut.editor.engine

/**
 * R5.5c — Privacy dashboard data model.
 *
 * Single source of truth for every category of data NovaCut collects, where
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
        PROJECT_CONTENT("Project content (clips, timelines, captions)"),
        MEDIA_METADATA("Media metadata (durations, codecs, dimensions)"),
        ML_MODELS("Downloaded ML models (Whisper, MediaPipe)"),
        APP_PREFERENCES("App preferences (theme, export defaults)"),
        TEMPLATE_LIBRARY("Saved templates / effect packs"),
        DIAGNOSTIC_LOGS("Diagnostic logs (logcat tail, redacted)"),
        CLOUD_GENERATIVE("Cloud generative video calls (consent-gated)"),
        AI_USAGE_LEDGER("AI usage ledger (per-project disclosure history)"),
        OPT_IN_TELEMETRY("Opt-in usage telemetry (Sentry / Glean)"),
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
            collectedBy = listOf("ProjectAutoSave", "ProjectDatabase", "ProjectArchive"),
            retentionPolicy = "Kept until the project is deleted from the project list.",
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
            category = Category.DIAGNOSTIC_LOGS,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = Controls(canExport = true, canDelete = true, hasOptOut = false),
            collectedBy = listOf("DiagnosticExportEngine"),
            retentionPolicy = "Generated only when the user taps Export diagnostic ZIP; capped to the 3 most recent ZIPs in filesDir/diagnostics.",
            collectedByDefault = false,
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
            collectedBy = listOf("AiUsageLedger", "ProjectAutoSave", "ExportDelegate", "DirectPublishEngine"),
            retentionPolicy = "Stored only inside the project autosave; users can clear it from the export disclosure review.",
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
}

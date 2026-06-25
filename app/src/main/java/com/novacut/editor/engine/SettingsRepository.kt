package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_DATASTORE_NAME = "clearcut_settings"

data class AppSettings(
    val defaultResolution: Resolution = Resolution.FHD_1080P,
    val defaultFrameRate: Int = 30,
    val defaultAspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val defaultCodec: String = "H264",
    val proxyEnabled: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val autoSaveIntervalSec: Int = 60,
    val proxyResolution: ProxyResolution = ProxyResolution.QUARTER,
    val editorMode: String = "Pro",
    val hapticEnabled: Boolean = true,
    val showWaveforms: Boolean = true,
    val defaultTrackHeight: Int = 64,
    val snapToBeat: Boolean = false,
    val snapToMarker: Boolean = true,
    val thumbnailCacheSizeMb: Int = 128,
    val confirmBeforeDelete: Boolean = true,
    val defaultExportQuality: String = "HIGH",
    val aiModelWifiOnly: Boolean = true,
    // v3.69: UI-mode flags. `desktopMode` is auto-detected from the device
    // config (Samsung DeX, Chromebook, or generic large-screen + mouse). It
    // can still be user-overridden via [updateDesktopModeOverride]. `oneHandedMode`
    // is strictly user-opt-in and intended for phone-width sessions.
    val oneHandedMode: Boolean = false,
    val desktopModeOverride: DesktopOverride = DesktopOverride.AUTO,
    // v3.69: optional AcoustID API key for content-ID lookup. Empty = use the
    // local hash-only path (see ContentIdEngine).
    val acoustIdApiKey: String = "",
    val includeDiagnosticTimelineShape: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    // Opt-in passive update check for sideload / GitHub-release installs. Off by
    // default so no network request is ever made without explicit consent.
    val updateCheckEnabled: Boolean = false,
)

enum class DesktopOverride { AUTO, FORCE_ON, FORCE_OFF }

enum class AppearanceMode {
    SYSTEM,
    DARK,
    HIGH_CONTRAST_DARK,
}

internal object SettingsPreferenceKeys {
    val RESOLUTION = stringPreferencesKey("default_resolution")
    val FRAME_RATE = intPreferencesKey("default_frame_rate")
    val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
    val AUTO_SAVE = booleanPreferencesKey("auto_save_enabled")
    val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval_sec")
    val PROXY_RES = stringPreferencesKey("proxy_resolution")
    val DEFAULT_CODEC = stringPreferencesKey("default_codec")
    val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
    val TUTORIAL_SHOWN = booleanPreferencesKey("tutorial_shown")
    val FAVORITE_EFFECTS = stringSetPreferencesKey("favorite_effects")
    val RECENT_EFFECTS = stringPreferencesKey("recent_effects")
    val EDITOR_MODE = stringPreferencesKey("editor_mode")
    val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    val SHOW_WAVEFORMS = booleanPreferencesKey("show_waveforms")
    val DEFAULT_TRACK_HEIGHT = intPreferencesKey("default_track_height")
    val SNAP_TO_BEAT = booleanPreferencesKey("snap_to_beat")
    val SNAP_TO_MARKER = booleanPreferencesKey("snap_to_marker")
    val THUMBNAIL_CACHE_SIZE_MB = intPreferencesKey("thumbnail_cache_size_mb")
    val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
    val DEFAULT_EXPORT_QUALITY = stringPreferencesKey("default_export_quality")
    val AI_MODEL_WIFI_ONLY = booleanPreferencesKey("ai_model_wifi_only")
    val ONE_HANDED_MODE = booleanPreferencesKey("one_handed_mode")
    val DESKTOP_OVERRIDE = stringPreferencesKey("desktop_override")
    val ACOUSTID_KEY = stringPreferencesKey("acoustid_api_key")
    val INCLUDE_DIAGNOSTIC_TIMELINE_SHAPE = booleanPreferencesKey("include_diagnostic_timeline_shape")
    val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
    val UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
}

internal fun mapPreferencesToAppSettings(prefs: Preferences): AppSettings = AppSettings(
    defaultResolution = prefs[SettingsPreferenceKeys.RESOLUTION]?.enumOrNull<Resolution>() ?: Resolution.FHD_1080P,
    defaultFrameRate = prefs[SettingsPreferenceKeys.FRAME_RATE]?.takeIf { it in 1..120 } ?: 30,
    defaultAspectRatio = prefs[SettingsPreferenceKeys.ASPECT_RATIO]?.enumOrNull<AspectRatio>()
        ?: AspectRatio.RATIO_16_9,
    defaultCodec = prefs[SettingsPreferenceKeys.DEFAULT_CODEC]
        ?.takeIf { codec -> runCatching { VideoCodec.valueOf(codec) }.isSuccess }
        ?: VideoCodec.H264.name,
    proxyEnabled = prefs[SettingsPreferenceKeys.PROXY_ENABLED] ?: true,
    autoSaveEnabled = prefs[SettingsPreferenceKeys.AUTO_SAVE] ?: true,
    autoSaveIntervalSec = prefs[SettingsPreferenceKeys.AUTO_SAVE_INTERVAL]?.takeIf { it in 15..300 } ?: 60,
    proxyResolution = prefs[SettingsPreferenceKeys.PROXY_RES]?.enumOrNull<ProxyResolution>()
        ?: ProxyResolution.QUARTER,
    editorMode = prefs[SettingsPreferenceKeys.EDITOR_MODE]
        ?.takeIf { it == "Easy" || it == "Pro" }
        ?: "Pro",
    hapticEnabled = prefs[SettingsPreferenceKeys.HAPTIC_ENABLED] ?: true,
    showWaveforms = prefs[SettingsPreferenceKeys.SHOW_WAVEFORMS] ?: true,
    defaultTrackHeight = prefs[SettingsPreferenceKeys.DEFAULT_TRACK_HEIGHT]?.takeIf { it in 48..120 } ?: 64,
    snapToBeat = prefs[SettingsPreferenceKeys.SNAP_TO_BEAT] ?: false,
    snapToMarker = prefs[SettingsPreferenceKeys.SNAP_TO_MARKER] ?: true,
    thumbnailCacheSizeMb = prefs[SettingsPreferenceKeys.THUMBNAIL_CACHE_SIZE_MB]?.takeIf { it in 32..512 } ?: 128,
    confirmBeforeDelete = prefs[SettingsPreferenceKeys.CONFIRM_BEFORE_DELETE] ?: true,
    defaultExportQuality = prefs[SettingsPreferenceKeys.DEFAULT_EXPORT_QUALITY]
        ?.takeIf { quality -> runCatching { ExportQuality.valueOf(quality) }.isSuccess }
        ?: ExportQuality.HIGH.name,
    aiModelWifiOnly = prefs[SettingsPreferenceKeys.AI_MODEL_WIFI_ONLY] ?: true,
    oneHandedMode = prefs[SettingsPreferenceKeys.ONE_HANDED_MODE] ?: false,
    desktopModeOverride = prefs[SettingsPreferenceKeys.DESKTOP_OVERRIDE]?.enumOrNull<DesktopOverride>()
        ?: DesktopOverride.AUTO,
    acoustIdApiKey = prefs[SettingsPreferenceKeys.ACOUSTID_KEY] ?: "",
    includeDiagnosticTimelineShape = prefs[SettingsPreferenceKeys.INCLUDE_DIAGNOSTIC_TIMELINE_SHAPE] ?: false,
    appearanceMode = prefs[SettingsPreferenceKeys.APPEARANCE_MODE]?.enumOrNull<AppearanceMode>()
        ?: AppearanceMode.SYSTEM,
    updateCheckEnabled = prefs[SettingsPreferenceKeys.UPDATE_CHECK_ENABLED] ?: false,
)

internal fun createClearCutSettingsDataStore(
    context: Context,
    resetReportStore: SettingsResetReportStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): DataStore<Preferences> = createClearCutSettingsDataStore(
    produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) },
    resetReportStore = resetReportStore,
    scope = scope,
)

internal fun createClearCutSettingsDataStore(
    produceFile: () -> java.io.File,
    resetReportStore: SettingsResetReportStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            resetReportStore.recordCorruptionReset(it)
            runCatching { produceFile().delete() }.onFailure { deleteError ->
                Log.w("SettingsRepository", "Could not remove corrupt settings file before reset", deleteError)
            }
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )

private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

@Singleton
class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val resetReportStore: SettingsResetReportStore,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        resetReportStore: SettingsResetReportStore,
    ) : this(
        dataStore = createClearCutSettingsDataStore(context, resetReportStore),
        resetReportStore = resetReportStore,
    )

    private val data: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    val settings: Flow<AppSettings> = data
        .map(::mapPreferencesToAppSettings)

    suspend fun updateResolution(value: Resolution) {
        dataStore.edit { it[SettingsPreferenceKeys.RESOLUTION] = value.name }
    }

    suspend fun updateFrameRate(value: Int) {
        val validated = value.coerceIn(1, 120)
        dataStore.edit { it[SettingsPreferenceKeys.FRAME_RATE] = validated }
    }

    suspend fun updateAspectRatio(value: AspectRatio) {
        dataStore.edit { it[SettingsPreferenceKeys.ASPECT_RATIO] = value.name }
    }

    suspend fun updateAutoSave(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.AUTO_SAVE] = enabled }
    }

    suspend fun updateAutoSaveInterval(sec: Int) {
        dataStore.edit { it[SettingsPreferenceKeys.AUTO_SAVE_INTERVAL] = sec.coerceIn(15, 300) }
    }

    suspend fun updateProxyResolution(value: ProxyResolution) {
        dataStore.edit { it[SettingsPreferenceKeys.PROXY_RES] = value.name }
    }

    suspend fun updateDefaultCodec(value: String) {
        // Validate against known enum values to prevent storing garbage from corrupt settings
        val validated = try {
            VideoCodec.valueOf(value).name
        } catch (_: IllegalArgumentException) {
            Log.w("SettingsRepository", "Ignoring unknown codec value: $value")
            return
        }
        dataStore.edit { it[SettingsPreferenceKeys.DEFAULT_CODEC] = validated }
    }

    suspend fun updateProxyEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.PROXY_ENABLED] = enabled }
    }

    suspend fun isTutorialShown(): Boolean {
        return data.map { it[SettingsPreferenceKeys.TUTORIAL_SHOWN] ?: false }.first()
    }

    suspend fun setTutorialShown(shown: Boolean = true) {
        dataStore.edit { it[SettingsPreferenceKeys.TUTORIAL_SHOWN] = shown }
    }

    suspend fun getFavoriteEffects(): Set<String> {
        return data.map { it[SettingsPreferenceKeys.FAVORITE_EFFECTS] ?: emptySet() }.first()
    }

    suspend fun toggleFavoriteEffect(effectType: String) {
        dataStore.edit { prefs ->
            val current = prefs[SettingsPreferenceKeys.FAVORITE_EFFECTS] ?: emptySet()
            prefs[SettingsPreferenceKeys.FAVORITE_EFFECTS] = if (effectType in current) {
                current - effectType
            } else {
                current + effectType
            }
        }
    }

    suspend fun addRecentEffect(effectType: String) {
        dataStore.edit { prefs ->
            val current = (prefs[SettingsPreferenceKeys.RECENT_EFFECTS] ?: "")
                .split(",")
                .filter { it.isNotBlank() && it != effectType }
            val updated = (listOf(effectType) + current).take(20)
            prefs[SettingsPreferenceKeys.RECENT_EFFECTS] = updated.joinToString(",")
        }
    }

    suspend fun updateEditorMode(mode: String) {
        val normalized = when (mode) {
            "Easy", "Pro" -> mode
            else -> return
        }
        dataStore.edit { it[SettingsPreferenceKeys.EDITOR_MODE] = normalized }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun getRecentEffects(): List<String> {
        return data.map { prefs ->
            (prefs[SettingsPreferenceKeys.RECENT_EFFECTS] ?: "")
                .split(",")
                .filter { it.isNotBlank() }
        }.first()
    }

    suspend fun updateShowWaveforms(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.SHOW_WAVEFORMS] = value }
    }

    suspend fun updateDefaultTrackHeight(value: Int) {
        dataStore.edit { it[SettingsPreferenceKeys.DEFAULT_TRACK_HEIGHT] = value.coerceIn(48, 120) }
    }

    suspend fun updateSnapToBeat(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.SNAP_TO_BEAT] = value }
    }

    suspend fun updateSnapToMarker(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.SNAP_TO_MARKER] = value }
    }

    suspend fun updateThumbnailCacheSize(value: Int) {
        dataStore.edit { it[SettingsPreferenceKeys.THUMBNAIL_CACHE_SIZE_MB] = value.coerceIn(32, 512) }
    }

    suspend fun updateConfirmBeforeDelete(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.CONFIRM_BEFORE_DELETE] = value }
    }

    suspend fun updateDefaultExportQuality(value: String) {
        val validated = try { ExportQuality.valueOf(value).name } catch (_: IllegalArgumentException) { return }
        dataStore.edit { it[SettingsPreferenceKeys.DEFAULT_EXPORT_QUALITY] = validated }
    }

    suspend fun updateAiModelWifiOnly(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.AI_MODEL_WIFI_ONLY] = value }
    }

    suspend fun updateIncludeDiagnosticTimelineShape(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.INCLUDE_DIAGNOSTIC_TIMELINE_SHAPE] = value }
    }

    suspend fun updateAppearanceMode(value: AppearanceMode) {
        dataStore.edit { it[SettingsPreferenceKeys.APPEARANCE_MODE] = value.name }
    }

    suspend fun updateUpdateCheckEnabled(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.UPDATE_CHECK_ENABLED] = value }
    }

    suspend fun updateOneHandedMode(value: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.ONE_HANDED_MODE] = value }
    }

    suspend fun updateDesktopOverride(value: DesktopOverride) {
        dataStore.edit { it[SettingsPreferenceKeys.DESKTOP_OVERRIDE] = value.name }
    }

    suspend fun updateAcoustIdKey(value: String) {
        // Trim + cap to a sane length so corrupt pastes can't blow up the
        // DataStore file.
        val sanitised = value.trim().take(64)
        dataStore.edit { it[SettingsPreferenceKeys.ACOUSTID_KEY] = sanitised }
    }

    fun latestSettingsResetReport(): SettingsResetReport? =
        resetReportStore.latestReport()
}

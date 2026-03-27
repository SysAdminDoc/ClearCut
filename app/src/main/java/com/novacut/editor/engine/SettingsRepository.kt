package com.novacut.editor.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novacut_settings")

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
    val hapticEnabled: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
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
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultResolution = prefs[Keys.RESOLUTION]?.let {
                try { Resolution.valueOf(it) } catch (_: Exception) { null }
            } ?: Resolution.FHD_1080P,
            defaultFrameRate = prefs[Keys.FRAME_RATE] ?: 30,
            defaultAspectRatio = prefs[Keys.ASPECT_RATIO]?.let {
                try { AspectRatio.valueOf(it) } catch (_: Exception) { null }
            } ?: AspectRatio.RATIO_16_9,
            defaultCodec = prefs[Keys.DEFAULT_CODEC] ?: "H264",
            proxyEnabled = prefs[Keys.PROXY_ENABLED] ?: true,
            autoSaveEnabled = prefs[Keys.AUTO_SAVE] ?: true,
            autoSaveIntervalSec = prefs[Keys.AUTO_SAVE_INTERVAL] ?: 60,
            proxyResolution = prefs[Keys.PROXY_RES]?.let {
                try { ProxyResolution.valueOf(it) } catch (_: Exception) { null }
            } ?: ProxyResolution.QUARTER,
            editorMode = prefs[Keys.EDITOR_MODE] ?: "Pro",
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true
        )
    }

    suspend fun updateResolution(value: Resolution) {
        context.dataStore.edit { it[Keys.RESOLUTION] = value.name }
    }

    suspend fun updateFrameRate(value: Int) {
        val validated = value.coerceIn(1, 120)
        context.dataStore.edit { it[Keys.FRAME_RATE] = validated }
    }

    suspend fun updateAspectRatio(value: AspectRatio) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = value.name }
    }

    suspend fun updateAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }

    suspend fun updateAutoSaveInterval(sec: Int) {
        context.dataStore.edit { it[Keys.AUTO_SAVE_INTERVAL] = sec }
    }

    suspend fun updateProxyResolution(value: ProxyResolution) {
        context.dataStore.edit { it[Keys.PROXY_RES] = value.name }
    }

    suspend fun updateDefaultCodec(value: String) {
        context.dataStore.edit { it[Keys.DEFAULT_CODEC] = value }
    }

    suspend fun updateProxyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROXY_ENABLED] = enabled }
    }

    suspend fun isTutorialShown(): Boolean {
        return context.dataStore.data.map { it[Keys.TUTORIAL_SHOWN] ?: false }.first()
    }

    suspend fun setTutorialShown(shown: Boolean = true) {
        context.dataStore.edit { it[Keys.TUTORIAL_SHOWN] = shown }
    }

    suspend fun getFavoriteEffects(): Set<String> {
        return context.dataStore.data.map { it[Keys.FAVORITE_EFFECTS] ?: emptySet() }.first()
    }

    suspend fun toggleFavoriteEffect(effectType: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_EFFECTS] ?: emptySet()
            prefs[Keys.FAVORITE_EFFECTS] = if (effectType in current) {
                current - effectType
            } else {
                current + effectType
            }
        }
    }

    suspend fun addRecentEffect(effectType: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.RECENT_EFFECTS] ?: "")
                .split(",")
                .filter { it.isNotBlank() && it != effectType }
            val updated = (listOf(effectType) + current).take(20)
            prefs[Keys.RECENT_EFFECTS] = updated.joinToString(",")
        }
    }

    suspend fun updateEditorMode(mode: String) {
        context.dataStore.edit { it[Keys.EDITOR_MODE] = mode }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun getRecentEffects(): List<String> {
        return context.dataStore.data.map { prefs ->
            (prefs[Keys.RECENT_EFFECTS] ?: "")
                .split(",")
                .filter { it.isNotBlank() }
        }.first()
    }
}

package com.novacut.editor.engine

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.ProxyResolution
import com.novacut.editor.model.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun corruptPreferencesFile_replacedWithDefaultsAndAllowsWrites() = runBlocking {
        val settingsFile = temp.newFile("clearcut_settings.preferences_pb")
        settingsFile.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x00))
        val reportStore = SettingsResetReportStore.forFile(temp.newFile("settings-reset-report.jsonl"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = createClearCutSettingsDataStore(
                produceFile = { settingsFile },
                resetReportStore = reportStore,
                scope = scope,
            )
            val repo = SettingsRepository(dataStore, reportStore)

            assertEquals(AppSettings(), repo.settings.first())
            repo.updateFrameRate(60)
            assertEquals(60, repo.settings.first().defaultFrameRate)

            val report = reportStore.latestReport()
            assertNotNull(report)
            assertEquals(SettingsResetReportStore.DEFAULT_REASON, report!!.reason)
            assertNotNull(reportStore.buildDiagnosticText())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun mapPreferencesToAppSettings_defaultsInvalidKeysWithoutFullWipe() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.RESOLUTION to "NOT_A_RESOLUTION",
            SettingsPreferenceKeys.FRAME_RATE to 999,
            SettingsPreferenceKeys.ASPECT_RATIO to "BROKEN_RATIO",
            SettingsPreferenceKeys.PROXY_RES to "MISSING_PROXY_SIZE",
            SettingsPreferenceKeys.DEFAULT_TRACK_HEIGHT to 2,
            SettingsPreferenceKeys.THUMBNAIL_CACHE_SIZE_MB to 9999,
            SettingsPreferenceKeys.DEFAULT_CODEC to "FAKE_CODEC",
            SettingsPreferenceKeys.DEFAULT_EXPORT_QUALITY to "FAKE_QUALITY",
            SettingsPreferenceKeys.DESKTOP_OVERRIDE to "MAYBE",
            SettingsPreferenceKeys.APPEARANCE_MODE to "LIGHTISH",
            SettingsPreferenceKeys.PROXY_ENABLED to false,
            SettingsPreferenceKeys.AUTO_SAVE to false,
            SettingsPreferenceKeys.ACOUSTID_KEY to "kept-local-key",
        )

        val settings = mapPreferencesToAppSettings(prefs)

        assertEquals(Resolution.FHD_1080P, settings.defaultResolution)
        assertEquals(30, settings.defaultFrameRate)
        assertEquals(AspectRatio.RATIO_16_9, settings.defaultAspectRatio)
        assertEquals(ProxyResolution.QUARTER, settings.proxyResolution)
        assertEquals(64, settings.defaultTrackHeight)
        assertEquals(128, settings.thumbnailCacheSizeMb)
        assertEquals("H264", settings.defaultCodec)
        assertEquals("HIGH", settings.defaultExportQuality)
        assertEquals(DesktopOverride.AUTO, settings.desktopModeOverride)
        assertEquals(AppearanceMode.SYSTEM, settings.appearanceMode)
        assertEquals(false, settings.proxyEnabled)
        assertEquals(false, settings.autoSaveEnabled)
        assertEquals("kept-local-key", settings.acoustIdApiKey)
    }

    @Test
    fun validUpdatesSurviveRecoveredStore() = runBlocking {
        val settingsFile = temp.root.resolve("clearcut_settings_valid.preferences_pb")
        val reportStore = SettingsResetReportStore.forFile(temp.newFile("settings-reset-valid.jsonl"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = createClearCutSettingsDataStore(
                produceFile = { settingsFile },
                resetReportStore = reportStore,
                scope = scope,
            )
            dataStore.edit {
                it[SettingsPreferenceKeys.FRAME_RATE] = 24
                it[SettingsPreferenceKeys.PROXY_ENABLED] = false
            }
            val repo = SettingsRepository(dataStore, reportStore)

            val settings = repo.settings.first()

            assertEquals(24, settings.defaultFrameRate)
            assertEquals(false, settings.proxyEnabled)
            assertEquals(null, reportStore.latestReport())
        } finally {
            scope.cancel()
        }
    }
}

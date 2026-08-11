package com.novacut.editor.engine

import com.novacut.editor.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class AppLogTest {

    @After
    fun restoreDefaultMinimumLevel() {
        AppLog.setMinimumLevel(if (BuildConfig.DEBUG) AppLog.Level.VERBOSE else AppLog.Level.INFO)
    }

    @Test
    fun minimumLevelControlsWhatCanBeEmitted() {
        AppLog.setMinimumLevel(AppLog.Level.WARN)

        assertFalse(AppLog.isLoggable(AppLog.Level.INFO))
        assertTrue(AppLog.isLoggable(AppLog.Level.WARN))
        assertTrue(AppLog.isLoggable(AppLog.Level.ERROR))
    }

    @Test
    fun releaseFloorCannotBeLowered() {
        AppLog.setMinimumLevel(AppLog.Level.VERBOSE)

        assertEquals(
            if (BuildConfig.DEBUG) AppLog.Level.VERBOSE else AppLog.Level.INFO,
            AppLog.minimumLevel,
        )
    }
}

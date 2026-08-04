package com.novacut.editor.engine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStorePendingRowSweeperTest {

    @Test
    fun policyDeletesOnlyOldRowsInClearCutOutputDirectories() {
        val nowSeconds = 2_000_000L
        val staleSeconds = nowSeconds - (MediaStorePendingRowPolicy.ORPHAN_AGE_MS / 1_000L)

        assertTrue(MediaStorePendingRowPolicy.shouldDelete("Movies/ClearCut/", staleSeconds, nowSeconds))
        assertTrue(MediaStorePendingRowPolicy.shouldDelete("Download/ClearCut", staleSeconds - 1L, nowSeconds))
        assertFalse(MediaStorePendingRowPolicy.shouldDelete("Movies/ClearCut/", staleSeconds + 1L, nowSeconds))
        assertFalse(MediaStorePendingRowPolicy.shouldDelete("Movies/OtherApp/", staleSeconds, nowSeconds))
        assertFalse(MediaStorePendingRowPolicy.shouldDelete(null, staleSeconds, nowSeconds))
        assertFalse(MediaStorePendingRowPolicy.shouldDelete("Movies/ClearCut/", 0L, nowSeconds))
    }

    @Test
    fun startupWiresTheBoundedSweepThroughTheApplicationScope() {
        val source = locate("app/src/main/java/com/novacut/editor/ClearCutApp.kt").readText()
        val sweeper = locate("app/src/main/java/com/novacut/editor/engine/MediaStorePendingRowSweeper.kt").readText()

        assertTrue(source.contains("mediaStorePendingRowSweeper.sweep()"))
        assertTrue(source.contains("applicationScope.launch"))
        assertTrue(sweeper.contains("MediaStore.MediaColumns.OWNER_PACKAGE_NAME"))
        assertTrue(sweeper.contains("MediaStore.MATCH_ONLY"))
        assertTrue(sweeper.contains("MediaStore.setIncludePending"))
    }

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }
}

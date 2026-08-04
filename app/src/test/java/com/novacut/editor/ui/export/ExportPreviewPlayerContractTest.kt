package com.novacut.editor.ui.export

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPreviewPlayerContractTest {

    @Test
    fun previewPlayerOwnsCodecLeaseAndPlayerThroughDisposableEffect() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt").readText()

        assertTrue(source.contains("DisposableEffect(filePath, context)"))
        assertTrue(source.contains("createdPlayer.release()"))
        assertTrue(source.contains("lease.close()"))
        assertTrue(source.contains("if (!file.isFile || file.length() <= 0L)"))
        assertFalse(source.contains("val playerSession = remember(filePath)"))
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

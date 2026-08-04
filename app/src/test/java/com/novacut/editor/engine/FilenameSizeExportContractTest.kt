package com.novacut.editor.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilenameSizeExportContractTest {

    @Test
    fun specialExportPaths_finalizeSizeAwareNames() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt"
        ).readText()

        assertTrue(delegate.contains("val finalizedSheetFile = finalizeFilenameSize(targetSheetFile)"))
        assertTrue(delegate.contains("val finalizedGifFile = finalizeFilenameSize(targetGifFile)"))
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

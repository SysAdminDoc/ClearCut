package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IncomingIntentIoBoundaryTest {

    @Test
    fun incomingMediaAndDocumentHandlersScheduleProviderWorkOffMainThread() {
        val source = locate("app/src/main/java/com/novacut/editor/MainActivity.kt").readText()
        val mediaHandler = source.between(
            "    private fun handleIncomingMediaIntent",
            "    private fun handleIncomingDocumentIntent"
        )
        val documentHandler = source.between(
            "    private fun handleIncomingDocumentIntent",
            "    private fun launchIncomingIntentWork"
        )

        assertTrue(source.contains("private var incomingIntentJob: Job? = null"))
        assertTrue(source.contains("incomingIntentJob?.cancel()"))
        assertTrue(mediaHandler.contains("withContext(Dispatchers.IO)"))
        assertTrue(mediaHandler.contains("readableIncomingMediaItems(intent)"))
        assertTrue(mediaHandler.contains("readableIncomingDocumentItems(intent)"))
        assertTrue(documentHandler.contains("withContext(Dispatchers.IO)"))
        assertTrue(documentHandler.contains("readableIncomingDocumentItems(intent)"))
        assertFalse(mediaHandler.contains("contentResolver."))
        assertFalse(documentHandler.contains("contentResolver."))
    }

    @Test
    fun providerResolutionRemainsInsideExplicitIoHelpers() {
        val source = locate("app/src/main/java/com/novacut/editor/MainActivity.kt").readText()
        val mediaHelper = source.between(
            "    private fun readableIncomingMediaItems",
            "    private fun readableIncomingDocumentItems"
        )
        val documentHelper = source.between(
            "    private fun readableIncomingDocumentItems",
            "    private fun incomingDocumentMetadata"
        )

        assertTrue(mediaHelper.contains("IncomingMediaIntentParser.parse"))
        assertTrue(mediaHelper.contains("contentResolver.getType(uri)"))
        assertTrue(mediaHelper.contains("openAssetFileDescriptor"))
        assertTrue(documentHelper.contains("IncomingDocumentIntentParser.parse"))
        assertTrue(documentHelper.contains("incomingDocumentMetadata(uri, intent.type)"))
        assertTrue(documentHelper.contains("openAssetFileDescriptor"))
        assertTrue(source.contains("displayName = resolveMediaDisplayName(this, uri)"))
        assertTrue(source.contains("arrayOf(OpenableColumns.SIZE)"))
    }

    private fun String.between(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        val contentStart = startIndex + start.length
        val endIndex = indexOf(end, contentStart)
        require(endIndex >= 0) { "Missing source marker: $end" }
        return substring(contentStart, endIndex)
    }

    private fun locate(path: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("Could not read user.dir"))
            .absoluteFile
        repeat(8) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Could not locate $path")
        }
        error("Could not locate $path")
    }
}

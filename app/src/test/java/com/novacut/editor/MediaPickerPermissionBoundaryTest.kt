package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaPickerPermissionBoundaryTest {

    @Test
    fun batchPickerTracksAndReleasesOnlyAcquiredPersistedPermissions() {
        val source = locate(
            "app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt"
        ).readText()

        assertTrue(source.contains("var sequencePersistedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }"))
        assertTrue(source.contains("val persistedUris = mutableListOf<Uri>()"))
        assertTrue(source.contains("if (takePersistableReadPermission(context, uri))"))
        assertTrue(source.contains("stageSequenceReview(uris, persistedUris = persistedUris)"))
        assertTrue(source.contains("releasePersistedReadPermissions(context, persistedUris)"))
        assertTrue(source.contains("uris.distinct().forEach { uri ->"))
        assertTrue(source.contains("activeOperationJob?.cancel()"))
        assertTrue(source.contains("isCancelled = { !operationContext.isActive }"))
        assertTrue(source.contains("completed = index + 1"))
        assertTrue(source.contains("progress = { batchProgress }"))
        assertFalse(
            "Batch cleanup must not release every URI merely because it was selected",
            source.contains("releasePersistedReadPermission(context, selection.uri)")
        )
    }

    @Test
    fun droppedBatchInsufficientSpaceUsesTheStorageMessage() {
        val source = locate(
            "app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt"
        ).readText()
        val strings = locate("app/src/main/res/values/strings.xml").readText()

        assertTrue(source.contains("insufficientSpaceFor(context, totalSize)"))
        assertTrue(source.contains("permissionMessage = insufficientSpaceMessage(ingestResult)"))
        assertTrue(strings.contains("name=\"media_picker_insufficient_space_format\""))
        assertTrue(strings.contains("name=\"media_picker_cancel_import\""))
    }

    @Test
    fun rejectedAudioPickReleasesItsPersistedPermission() {
        val source = locate(
            "app/src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt"
        ).readText()

        assertTrue(source.contains("if (persisted)"))
        assertTrue(source.contains("releasePersistedReadPermissions(context, listOf(uri))"))
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

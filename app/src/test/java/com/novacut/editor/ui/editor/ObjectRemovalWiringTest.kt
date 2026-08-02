package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ObjectRemovalWiringTest {

    @Test
    fun selectedMaskReachesStillAndVideoEngines() {
        val delegate = locate(
            "app/src/main/java/com/novacut/editor/ui/editor/AiToolsDelegate.kt"
        ).readText()
        val engine = locate(
            "app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt"
        ).readText()

        assertTrue(delegate.contains("getSelectedMask"))
        assertTrue(delegate.contains("inpaintingEngine.inpaintFrame"))
        assertTrue(delegate.contains("inpaintingEngine.inpaintVideo"))
        assertTrue(delegate.contains("masks = current.masks.filterNot"))
        assertFalse(delegate.contains("ai_object_removal_unavailable_toast"))
        assertTrue(engine.contains("encodeImageSequenceWithAudio"))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
}

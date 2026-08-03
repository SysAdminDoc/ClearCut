package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorBoundaryOwnershipTest {

    @Test
    fun editorFacadeDoesNotOwnTimelineOrProjectStoreWrites() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()

        assertFalse(source.contains("timelineExchangeEngine.exportTo"))
        assertFalse(source.contains("projectDao.saveProjectWithMediaAssets"))
        assertTrue(source.contains("timelineExportCoordinator.export"))
        assertTrue(source.contains("documentCoordinator.save("))
        assertFalse(source.contains("MutableStateFlow(EditorState())"))
        assertTrue(source.contains("private val stateStore = EditorStateStore()"))
    }

    @Test
    fun videoFacadeDelegatesCompositionAssemblyToTheDedicatedOwner() {
        val source = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()

        assertFalse(source.contains("private fun buildComposition("))
        assertTrue(source.contains("CompositionBuilder.build("))
        assertTrue(source.contains("CompositionPlanBuilder.build("))
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

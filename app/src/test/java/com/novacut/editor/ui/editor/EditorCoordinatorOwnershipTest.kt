package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorCoordinatorOwnershipTest {

    @Test
    fun largeCoordinatorFilesStayWithinTheirReviewedOwnershipBudgets() {
        val budgets = mapOf(
            "app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt" to 315_000L,
            "app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt" to 172_000L,
            "app/src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt" to 151_000L,
            "app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt" to 130_000L,
            "app/src/main/java/com/novacut/editor/ui/editor/TimelineToolbar.kt" to 8_000L,
        )

        budgets.forEach { (relativePath, budget) ->
            val file = locate(relativePath)
            assertTrue(
                "$relativePath grew beyond its reviewed ownership budget: " +
                    "${file.length()} > $budget bytes",
                file.length() <= budget,
            )
        }
    }

    @Test
    fun timelineToolbarOwnsItsActionRenderingBoundary() {
        val timeline = locate("app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt").readText()
        val toolbar = locate("app/src/main/java/com/novacut/editor/ui/editor/TimelineToolbar.kt").readText()

        assertTrue("Timeline must call the extracted toolbar boundary.", "TimelineToolbarControls(" in timeline)
        assertTrue("Toolbar rendering must live in its own owner.", "internal fun TimelineToolbarControls(" in toolbar)
        assertTrue("Timeline must not regain a private toolbar owner.", "private fun TimelineToolbarControls(" !in timeline)
        assertTrue("Timeline must not render a second overflow button.", "TimelineToolbarButton(" !in timeline)
        assertTrue("Timeline must not retain the old toolbar menu state.", "timelineOptionsExpanded" !in timeline)
        assertTrue("Toolbar owner must render the overflow menu.", "moreMenuExpanded" in toolbar)
        assertTrue("Timeline must use the shared zoom policy boundary.", "TimelineToolbarPolicy.clampZoom" in timeline)
    }

    private fun locate(relativePath: String): File = listOf(
        File(relativePath),
        File("../$relativePath"),
    ).firstOrNull(File::exists) ?: error("$relativePath not found")
}

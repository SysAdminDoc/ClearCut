package com.novacut.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PublicFeatureClaimsTest {
    @Test
    fun readmeSlipSlideClaimIsBackedByEditorWiring() {
        val readme = locate("README.md").readText()
        if (!readme.contains("slip/slide editing", ignoreCase = true)) {
            return
        }

        val editorScreen = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()
        val timeline = locate("app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt").readText()
        val editorViewModel = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()

        assertTrue(
            "README slip/slide claim requires EditorScreen to wire slide gestures",
            editorScreen.contains("onSlideClip = viewModel::slideClip")
        )
        assertTrue(
            "README slip/slide claim requires EditorScreen to wire slip gestures",
            editorScreen.contains("onSlipClip = viewModel::slipClip")
        )
        assertTrue("Timeline must expose a slide callback", timeline.contains("onSlideClip: (clipId: String, deltaMs: Long) -> Unit"))
        assertTrue("Timeline must expose a slip callback", timeline.contains("onSlipClip: (clipId: String, deltaMs: Long) -> Unit"))
        assertTrue("EditorViewModel must implement slide edits", editorViewModel.contains("fun slideClip(clipId: String, slideAmountMs: Long)"))
        assertTrue("EditorViewModel must implement slip edits", editorViewModel.contains("fun slipClip(clipId: String, slipAmountMs: Long)"))
    }

    private fun locate(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found")
    }
}

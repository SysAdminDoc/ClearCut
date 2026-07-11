package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiSuggestionSnoozeTest {

    @Test
    fun dismissedSuggestion_staysHiddenUntilItsCooldownExpires() {
        val snoozed = mapOf("add_transitions" to 31_000L)

        assertFalse(shouldShowEditingSuggestion("add_transitions", 30_000L, snoozed))
        assertTrue(shouldShowEditingSuggestion("add_transitions", 31_000L, snoozed))
        assertTrue(shouldShowEditingSuggestion("auto_color", 30_000L, snoozed))
    }

    @Test
    fun suggestionBanner_exposesAVisibleNotNowAction() {
        val banner = locate("app/src/main/java/com/novacut/editor/ui/editor/AiSuggestionBanner.kt").readText()

        assertTrue(banner.contains("ai_suggestion_not_now"))
        assertTrue(banner.contains("onClick = onDismiss"))
    }

    private fun locate(relativePath: String): File {
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
    }
}

package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PartialRestoreCopyTest {

    @Test
    fun editorUsesLocalizedQuantityCopyInsteadOfRawRestoreLabels() {
        val editor = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()

        assertTrue(editor.contains("partialRestoreBulletList("))
        assertFalse(editor.contains("report.countsByKind().joinToString"))
    }

    @Test
    fun clipAndChapterMarkerQuantitiesAreTranslatedInBothLocales() {
        val base = locate("app/src/main/res/values/strings.xml").readText()
        val spanish = locate("app/src/main/res/values-es/strings.xml").readText()

        assertTrue(pluralItem(base, "partial_restore_clips", "one").contains("%1\$d clip"))
        assertTrue(pluralItem(base, "partial_restore_clips", "other").contains("%1\$d clips"))
        assertTrue(pluralItem(base, "partial_restore_chapter_markers", "one").contains("%1\$d chapter marker"))
        assertTrue(pluralItem(base, "partial_restore_chapter_markers", "other").contains("%1\$d chapter markers"))
        assertTrue(pluralItem(spanish, "partial_restore_clips", "one").contains("%1\$d clip"))
        assertTrue(pluralItem(spanish, "partial_restore_clips", "other").contains("%1\$d clips"))
        assertTrue(pluralItem(spanish, "partial_restore_chapter_markers", "one").contains("%1\$d marcador de capítulo"))
        assertTrue(pluralItem(spanish, "partial_restore_chapter_markers", "other").contains("%1\$d marcadores de capítulo"))
    }

    private fun pluralItem(xml: String, name: String, quantity: String): String {
        val block = Regex("<plurals name=\\\"$name\\\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("Missing plural $name")
        return Regex("<item quantity=\\\"$quantity\\\">(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
            .find(block)
            ?.groupValues
            ?.get(1)
            ?: error("Missing $quantity quantity for $name")
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath")).first { it.exists() }
}

package com.novacut.editor.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SemanticThemeSourcePolicyTest {

    @Test
    fun featureSurfacesDoNotBypassSemanticOrApprovedAccentTokens() {
        val root = locateRepoRoot()
        val sourceRoots = listOf("editor", "export", "mediapicker").map { area ->
            File(root, "app/src/main/java/com/novacut/editor/ui/$area")
        }
        val files = sourceRoots.flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

        assertTrue("Expected feature source files to audit.", files.isNotEmpty())
        files.forEach { file ->
            val source = file.readText()
            assertFalse("${file.relativeTo(root)} imports the raw Mocha palette.", RAW_MOCHA_IMPORT in source)
            assertFalse("${file.relativeTo(root)} reads a raw Mocha token.", "Mocha." in source)
            APPROVED_ACCENT.findAll(source).forEach { match ->
                assertTrue(
                    "${file.relativeTo(root)} uses an unapproved accent role: ${match.groupValues[1]}",
                    match.groupValues[1] in APPROVED_ACCENT_NAMES,
                )
            }
        }
    }

    private fun locateRepoRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }
        var directory: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = directory ?: error("Could not locate repository root")
            if (File(current, ".git").isDirectory) return current
            directory = current.parentFile
        }
        error("Could not locate repository root from $userDir")
    }

    private companion object {
        const val RAW_MOCHA_IMPORT = "import com.novacut.editor.ui.theme.Mocha"
        val APPROVED_ACCENT = Regex("ClearCutAccents\\.([A-Za-z0-9_]+)")
        val APPROVED_ACCENT_NAMES = setOf(
            "Lavender", "Blue", "Sapphire", "Sky", "Teal", "Green", "Yellow",
            "Peach", "Maroon", "Red", "Mauve", "Pink", "Flamingo", "Rosewater",
        )
    }
}

package com.novacut.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the reachable editor-copy debt from growing while the remaining
 * panels are moved to resources. This deliberately measures only UI-shaped
 * literal call sites; technical constants and diagnostics are not user copy.
 */
class UiHardcodedLiteralRatchetTest {

    @Test
    fun reachableUiLiteralCountsDoNotGrow() {
        val root = locateRepoRoot()
        val budgets = mapOf(
            "CaptionEditorPanel.kt" to 15,
            "AudioMixerPanel.kt" to 2,
            "MultiCamPanel.kt" to 7,
            "PipPresetsPanel.kt" to 21,
            "SpeedPresets.kt" to 14,
            "StickerPickerPanel.kt" to 7,
            "ToolPanel.kt" to 10,
            "EditorViewModel.kt" to 7,
        )

        budgets.forEach { (fileName, budget) ->
            val file = File(root, "app/src/main/java/com/novacut/editor/ui/editor/$fileName")
            val count = UI_LITERAL_PATTERNS.sumOf { pattern -> pattern.findAll(file.readText()).count() }
            assertTrue(
                "$fileName added reachable hard-coded UI copy: $count > $budget",
                count <= budget,
            )
        }
    }

    private fun locateRepoRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: error("Could not read user.dir")).absoluteFile
        repeat(8) {
            if (File(directory, ".git").isDirectory) return directory
            directory = directory.parentFile ?: error("Could not locate the repository root")
        }
        error("Could not locate the repository root")
    }

    private companion object {
        val UI_LITERAL_PATTERNS = listOf(
            Regex("(?m)^\\s*(?:text|title|subtitle|label|description|valueLabel)\\s*=\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
            Regex("(?m)\\bText\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
            Regex("(?m)\\bshowToast\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
        )
    }
}

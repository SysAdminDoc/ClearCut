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
            "CaptionEditorPanel.kt" to 0,
            "AudioMixerPanel.kt" to 2,
            "MultiCamPanel.kt" to 0,
            "PipPresetsPanel.kt" to 0,
            "SpeedPresets.kt" to 0,
            "StickerPickerPanel.kt" to 0,
            "ToolPanel.kt" to 4,
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

    @Test
    fun everyUiSourceFileStaysWithinTheMeasuredLiteralBudget() {
        val root = locateRepoRoot()
        val uiRoot = File(root, "app/src/main/java/com/novacut/editor/ui")
        val files = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val total = files.sumOf { file ->
            UI_LITERAL_PATTERNS.sumOf { pattern -> pattern.findAll(file.readText()).count() }
        }

        assertTrue("UI literal scan must cover source files", files.isNotEmpty())
        assertTrue(
            "UI source literal count grew: $total > $UI_LITERAL_TOTAL_BUDGET",
            total <= UI_LITERAL_TOTAL_BUDGET,
        )
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
        // Baseline measured across every Kotlin source file under ui/ on 2026-08-22.
        // Raise this only when a localized resource migration or a reviewed UI
        // addition changes the measured baseline.
        const val UI_LITERAL_TOTAL_BUDGET = 97

        val UI_LITERAL_PATTERNS = listOf(
            Regex("(?m)^\\s*(?:text|title|subtitle|label|description|valueLabel)\\s*=\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
            Regex("(?m)\\bText\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
            Regex("(?m)\\bshowToast\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\""),
        )
    }
}

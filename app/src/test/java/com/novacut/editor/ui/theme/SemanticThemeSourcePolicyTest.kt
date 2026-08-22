package com.novacut.editor.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SemanticThemeSourcePolicyTest {

    @Test
    fun featureSurfacesDoNotBypassSemanticOrApprovedAccentTokens() {
        val root = locateRepoRoot()
        val sourceRoots = listOf("editor", "export", "mediapicker", "projects", "settings").map { area ->
            File(root, "app/src/main/java/com/novacut/editor/ui/$area")
        }
        val files = sourceRoots.flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        val rawColorFiles = listOf("editor", "projects", "settings").flatMap { area ->
            File(root, "app/src/main/java/com/novacut/editor/ui/$area")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
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
        rawColorFiles.forEach { file ->
            val allowedRawColors = INSTRUMENT_COLOR_ALLOWLIST[file.name].orEmpty()
            val violations = RAW_COLOR_LITERAL.findAll(file.readText())
                .map { it.value }
                .filterNot { it in allowedRawColors }
                .toList()
            assertTrue(
                "${file.relativeTo(root)} contains non-content-driven raw color literals: $violations",
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun roundedGeometryStaysWithinTheTwelveDpScale() {
        val root = locateRepoRoot()
        val uiRoot = File(root, "app/src/main/java/com/novacut/editor/ui")
        val violations = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                    .replace(BLOCK_COMMENT, "")
                    .replace(LINE_COMMENT, "")
                ROUNDED_CORNER_CALL.findAll(source).flatMap { call ->
                    DP_LITERAL.findAll(call.value)
                        .filter { literal -> literal.groupValues[1].toFloat() > MAX_RADIUS_DP }
                        .map { literal ->
                            "${file.relativeTo(root)} uses ${literal.value} in ${call.value.trim()}"
                        }
                }
            }
            .toList()

        assertTrue(
            "Rounded UI geometry must stay within the 0/4/6/8/10/12dp scale:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
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
        val RAW_COLOR_LITERAL = Regex("Color\\(0x[0-9A-Fa-f]+\\)")
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\\r\\n]*")
        val ROUNDED_CORNER_CALL = Regex(
            "RoundedCornerShape\\(([^)]*)\\)",
            RegexOption.DOT_MATCHES_ALL,
        )
        val DP_LITERAL = Regex("([0-9]+(?:\\.[0-9]+)?)\\.dp")
        const val MAX_RADIUS_DP = 12f
        /**
         * Scope traces are instrument colours with fixed signal meaning, so they are
         * intentionally independent of the UI theme. Every structural editor colour
         * and content-control colour must resolve through semantic, accent, or content
         * tokens instead.
         */
        val INSTRUMENT_COLOR_ALLOWLIST = mapOf(
            "VideoScopes.kt" to setOf(
                "Color(0xFFFF4444)",
                "Color(0xFF44FF44)",
                "Color(0xFF4488FF)",
            ),
        )
        val APPROVED_ACCENT_NAMES = setOf(
            "Neutral", "Lavender", "Blue", "Sapphire", "Sky", "Teal", "Green", "Yellow",
            "Peach", "Maroon", "Red", "Mauve", "Pink", "Flamingo", "Rosewater",
        )
    }
}

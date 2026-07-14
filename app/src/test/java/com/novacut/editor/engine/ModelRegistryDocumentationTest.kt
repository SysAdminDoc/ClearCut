package com.novacut.editor.engine

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * R7.2 model registry closure guard.
 *
 * Active rows in docs/models.md describe model bytes ClearCut may load at
 * runtime. A planning row can be incomplete, but an active row cannot carry a
 * floating URL or a placeholder checksum.
 */
class ModelRegistryDocumentationTest {

    @Test
    fun activeModelRowsHavePinnedSha256Values() {
        val activeRows = activeModelRows()
        assertTrue("Expected at least one active model row in docs/models.md", activeRows.isNotEmpty())

        val placeholderRows = activeRows.filter { it.contains("TBD", ignoreCase = true) }
        assertTrue(
            "Active model rows must not use checksum placeholders: ${placeholderRows.joinToString()}",
            placeholderRows.isEmpty()
        )

        val rowsWithoutSha = activeRows.mapNotNull { row ->
            val columns = row.columns()
            val id = columns.getOrNull(0).orEmpty()
            val shaColumn = columns.getOrNull(3).orEmpty()
            if (SHA_256_REGEX.containsMatchIn(shaColumn)) null else id
        }
        assertTrue(
            "Every active model row needs at least one 64-character SHA-256: $rowsWithoutSha",
            rowsWithoutSha.isEmpty()
        )
    }

    @Test
    fun activeModelRowsDoNotUseFloatingSourceLocators() {
        val floatingRows = activeModelRows().mapNotNull { row ->
            val columns = row.columns()
            val id = columns.getOrNull(0).orEmpty()
            val source = columns.getOrNull(2).orEmpty()
            val usesFloatingHuggingFace = "huggingface.co" in source &&
                "/resolve/main/" in source
            val usesFloatingGcsLatest = "storage.googleapis.com" in source &&
                "/latest/" in source &&
                "generation=" !in source
            if (usesFloatingHuggingFace || usesFloatingGcsLatest) id else null
        }

        assertTrue(
            "Active model URLs must be revision/generation-pinned: $floatingRows",
            floatingRows.isEmpty()
        )
    }

    @Test
    fun activeModelEnginesRequireChecksumVerification() {
        val sourceDir = locateSourceDir()
            ?: error("Could not locate app/src/main/java from the test working directory")

        // Discover every runtime ModelFile download spec by scanning source
        // rather than hard-coding a fixed engine list, so a new engine that
        // constructs a ModelFile without checksum verification fails this guard.
        // ModelDownloadManager.kt defines the ModelFile type itself and is not
        // a spec site.
        val specFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != MODEL_FILE_DEFINITION }
            .filter { MODEL_FILE_CONSTRUCTOR in it.readText() }
            .toList()

        assertTrue("Expected to discover at least one runtime ModelFile spec", specFiles.isNotEmpty())

        // Sanity: the known download engines must be among the discovered specs.
        val discoveredNames = specFiles.map { it.name }.toSet()
        assertTrue(
            "Known model engines must be discovered as ModelFile specs: $discoveredNames",
            discoveredNames.containsAll(
                setOf(
                    "WhisperEngine.kt",
                    "SegmentationEngine.kt",
                    "InpaintingEngine.kt",
                    "SmartReframeEngine.kt",
                )
            )
        )

        val missingGuards = specFiles.mapNotNull { file ->
            val text = file.readText()
            if ("checksumRequired = true" in text && "sha256 =" in text) null else file.name
        }

        assertTrue(
            "Every runtime ModelFile spec must require checksum verification: $missingGuards",
            missingGuards.isEmpty()
        )
    }

    @Test
    fun aiToolActivationGateMatrixCoversEveryRequirementTool() {
        val documentedToolIds = aiToolGateRows().map { row ->
            row.columns().first().trim('`')
        }.toSet()
        val registryToolIds = AiToolRequirements.Tool.entries.map { it.toolId }.toSet()

        assertEquals(
            "docs/models.md AI tool gate matrix must cover every requirement tool",
            registryToolIds,
            documentedToolIds
        )
    }

    @Test
    fun runnableRequirementModelsAreDocumentedAsActiveRows() {
        val activeModelIds = activeModelRows().map { row ->
            row.columns().first().trim('`')
        }.toSet()
        val runnableModelIds = AiToolRequirements.Tool.entries.mapNotNull { tool ->
            val requirement = AiToolRequirements.requirementFor(tool.toolId)
            requirement?.modelRegistryId?.takeIf {
                requirement.availability != AiToolRequirements.Availability.DEPENDENCY_MISSING
            }
        }.toSet()

        assertTrue(
            "Runnable/downloadable AI tools must point to active docs/models.md rows: $runnableModelIds",
            activeModelIds.containsAll(runnableModelIds)
        )
    }

    private fun activeModelRows(): List<String> {
        val docs = locateModelsDoc()
            ?: error("Could not locate docs/models.md from the test working directory")
        val text = docs.readText()
        val start = text.indexOf("## 1. Active models")
        val end = text.indexOf("## 2. Native AARs")
        require(start >= 0 && end > start) { "docs/models.md active model section not found" }
        return text.substring(start, end)
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("| `") }
            .toList()
    }

    private fun aiToolGateRows(): List<String> {
        val docs = locateModelsDoc()
            ?: error("Could not locate docs/models.md from the test working directory")
        val text = docs.readText()
        val start = text.indexOf("## 6. AI tool activation gate matrix")
        val end = text.indexOf("## 7. Reproducible build pin requirements")
        require(start >= 0 && end > start) { "docs/models.md AI tool activation gate matrix not found" }
        return text.substring(start, end)
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("| `") }
            .toList()
    }

    private fun String.columns(): List<String> =
        trim().trim('|').split('|').map { it.trim() }

    private fun locateModelsDoc(): File? {
        val candidates = listOf(
            File("docs/models.md"),
            File("../docs/models.md"),
            File("../../docs/models.md"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun locateSourceDir(): File? {
        val candidates = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    private companion object {
        val SHA_256_REGEX = Regex("[0-9a-fA-F]{64}")
        const val MODEL_FILE_CONSTRUCTOR = "ModelFile("
        const val MODEL_FILE_DEFINITION = "ModelDownloadManager.kt"
    }
}

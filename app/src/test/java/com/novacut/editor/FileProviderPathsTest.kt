package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FileProviderPathsTest {

    @Test
    fun fileProviderRoots_coverKnownShareAndCaptureProducers() {
        val roots = fileProviderRoots()
        val expected = setOf(
            ProviderRoot("cache-path", "frame_capture", "frames/"),
            ProviderRoot("cache-path", "camera_captures", "camera-captures/"),
            ProviderRoot("files-path", "frame_captures", "frame_captures/"),
            ProviderRoot("files-path", "freeze_frames", "freeze_frames/"),
            ProviderRoot("files-path", "internal_exports", "ClearCut/"),
            ProviderRoot("files-path", "internal_archives", "archives/"),
            ProviderRoot("files-path", "diagnostics", "diagnostics/"),
            ProviderRoot("files-path", "internal_templates", "templates/"),
            ProviderRoot("external-files-path", "exports", "Movies/ClearCut/"),
            ProviderRoot("external-files-path", "external_archives", "archives/"),
            ProviderRoot("external-files-path", "timeline_exports", "exports/"),
            ProviderRoot("external-files-path", "templates", "templates/"),
            ProviderRoot("files-path", "voiceovers", "voiceovers/"),
            ProviderRoot("files-path", "tts_output", "tts_output/"),
            ProviderRoot("files-path", "legacy_tts_output", "tts/"),
            ProviderRoot("files-path", "noise_reduced", "noise_reduced/"),
            ProviderRoot("files-path", "stabilized", "stabilized/"),
            ProviderRoot("files-path", "luts", "luts/")
        )

        assertEquals(expected, roots)
    }

    @Test
    fun fileProviderRoots_stayNarrowAndDoNotExposeManagedMediaImports() {
        val roots = fileProviderRoots()
        val unsafePaths = setOf("", ".", "./", "/", "media/", "media/imports/")

        roots.forEach { root ->
            assertFalse(
                "FileProvider root ${root.name} must not expose broad path ${root.path}",
                root.path in unsafePaths
            )
        }
        assertTrue(
            "Camera capture handoff must use the actual cacheDir/camera-captures producer",
            roots.contains(ProviderRoot("cache-path", "camera_captures", "camera-captures/"))
        )
        assertFalse(
            "Managed imported media should not be exposed through a FileProvider root",
            roots.any { it.tag == "files-path" && it.path.startsWith("media/") }
        )
    }

    @Test
    fun fileProviderProducers_areRegisteredInTheSourceContract() {
        val roots = fileProviderRoots()
        val contracts = listOf(
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/ui/mediapicker/MediaPicker.kt",
                marker = "pendingCameraCaptureDir(context)",
                requiredRoots = setOf(ProviderRoot("cache-path", "camera_captures", "camera-captures/"))
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt",
                marker = "shareDiagnosticBundle",
                requiredRoots = setOf(ProviderRoot("files-path", "diagnostics", "diagnostics/"))
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt",
                marker = "\"archives/templates\"",
                requiredRoots = setOf(
                    ProviderRoot("external-files-path", "external_archives", "archives/"),
                    ProviderRoot("files-path", "internal_archives", "archives/")
                )
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt",
                marker = "\"templates\"",
                requiredRoots = setOf(
                    ProviderRoot("external-files-path", "templates", "templates/"),
                    ProviderRoot("files-path", "internal_templates", "templates/")
                )
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/engine/DirectPublishEngine.kt",
                marker = "validatePublishableFile(file)",
                requiredRoots = shareableExportRoots()
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/engine/ExportService.kt",
                marker = "latestOutputPath",
                requiredRoots = shareableExportRoots()
            ),
            ProducerContract(
                sourcePath = "src/main/java/com/novacut/editor/ui/editor/ExportDelegate.kt",
                marker = "lastExportedFilePath",
                requiredRoots = shareableExportRoots()
            )
        )

        assertEquals(
            "Every FileProvider URI producer must be listed in this contract.",
            contracts.map { it.sourcePath }.sorted(),
            fileProviderCallSiteFiles().sorted()
        )

        contracts.forEach { contract ->
            val source = File(contract.sourcePath).readText()
            assertTrue(
                "${contract.sourcePath} marker ${contract.marker} is missing",
                source.contains(contract.marker)
            )
            contract.requiredRoots.forEach { root ->
                assertTrue(
                    "${contract.sourcePath} requires FileProvider root $root",
                    roots.contains(root)
                )
            }
        }
    }

    private fun fileProviderRoots(): Set<ProviderRoot> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/file_paths.xml"))
        val paths = document.documentElement
        val roots = mutableSetOf<ProviderRoot>()
        for (index in 0 until paths.childNodes.length) {
            val element = paths.childNodes.item(index) as? Element ?: continue
            roots += ProviderRoot(
                tag = element.tagName,
                name = element.getAttribute("name"),
                path = element.getAttribute("path")
            )
        }
        return roots
    }

    private fun fileProviderCallSiteFiles(): List<String> {
        return File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    val trimmed = line.trimStart()
                    !trimmed.startsWith("*") &&
                        !trimmed.startsWith("//") &&
                        (
                            line.contains("FileProvider.getUriForFile") ||
                                line.contains("androidx.core.content.FileProvider.getUriForFile")
                            )
                }
            }
            .map { it.invariantSeparatorsPath }
            .toList()
    }

    private fun shareableExportRoots(): Set<ProviderRoot> {
        return setOf(
            ProviderRoot("files-path", "internal_exports", "ClearCut/"),
            ProviderRoot("external-files-path", "exports", "Movies/ClearCut/"),
            ProviderRoot("external-files-path", "timeline_exports", "exports/")
        )
    }

    private data class ProducerContract(
        val sourcePath: String,
        val marker: String,
        val requiredRoots: Set<ProviderRoot>
    )

    private data class ProviderRoot(
        val tag: String,
        val name: String,
        val path: String
    )
}

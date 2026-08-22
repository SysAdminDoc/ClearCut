package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocaleResourceCoverageTest {

    @Test
    fun spanishLocaleMirrorsBaseStringResources() {
        val repoRoot = locateRepoRoot()
        val base = readResourceContracts(File(repoRoot, "app/src/main/res/values/strings.xml"))
        val spanish = readResourceContracts(File(repoRoot, "app/src/main/res/values-es/strings.xml"))
        val intentionalSpanishFallbacks = emptySet<String>()

        assertEquals(
            "Spanish strings must mirror the base resource keys so the locale " +
                "does not silently fall back to English for shipped UI copy.",
            base.keys - intentionalSpanishFallbacks,
            spanish.keys
        )
        assertEquals("Spanish fallback allowlist must remain explicit and minimal.", emptySet<String>(), intentionalSpanishFallbacks)
        assertEquals("Base resource keys must be unique.", base.keys.size, base.keyOrder.size)
        assertEquals("Spanish resource keys must be unique.", spanish.keys.size, spanish.keyOrder.size)
        base.forEach { (key, contract) ->
            assertEquals("Spanish placeholder contract differs for $key.", contract, spanish[key])
        }
    }

    @Test
    fun spanishLocaleIsRegisteredForPerAppLanguagePicker() {
        val repoRoot = locateRepoRoot()
        val localesConfig = readLocaleConfigTags(File(repoRoot, "app/src/main/res/xml/locales_config.xml"))

        assertTrue(
            "locales_config.xml must list es once values-es ships, otherwise " +
                "Android 13+ per-app language settings cannot select it.",
            "es" in localesConfig
        )
    }

    @Test
    fun everyTranslatedResourceDirectoryIsRegisteredInLocaleConfig() {
        val repoRoot = locateRepoRoot()
        val resourceRoot = File(repoRoot, "app/src/main/res")
        val localeTags = readLocaleConfigTags(File(resourceRoot, "xml/locales_config.xml"))
        val translatedLocaleTags = resourceRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .map { androidLocaleTag(it.name) }
            .filter { it.isNotBlank() }
            .toSet()

        translatedLocaleTags.forEach { localeTag ->
            assertTrue(
                "values-$localeTag ships strings but locales_config.xml does not list $localeTag.",
                localeTag in localeTags,
            )
        }
    }

    @Test
    fun everyTranslatedLocaleMirrorsResourcesAndPlayMetadata() {
        val repoRoot = locateRepoRoot()
        val resourceRoot = File(repoRoot, "app/src/main/res")
        val base = readResourceContracts(File(resourceRoot, "values"))
        val localeTags = readLocaleConfigTags(File(resourceRoot, "xml/locales_config.xml"))
        val translatedDirectories = resourceRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }

        translatedDirectories.forEach { directory ->
            val androidTag = androidLocaleTag(directory.name)
            val translated = readResourceContracts(directory)
            assertEquals("$directory must mirror English resource keys.", base.keys, translated.keys)
            base.forEach { (key, contract) ->
                assertEquals("$directory placeholder contract differs for $key.", contract, translated[key])
            }
            assertTrue("$directory must be listed in locales_config.xml.", androidTag in localeTags)

            val playDirectory = File(repoRoot, "fastlane/metadata/android")
                .listFiles()
                .orEmpty()
                .firstOrNull { candidate ->
                    candidate.isDirectory && candidate.name.substringBefore('-')
                        .equals(androidTag.substringBefore('-'), ignoreCase = true)
            }
            assertTrue("Missing Play metadata directory for $androidTag.", playDirectory?.isDirectory == true)
            if (playDirectory == null) return@forEach
            listOf("title.txt", "short_description.txt", "full_description.txt", "privacy_policy_url.txt")
                .forEach { fileName ->
                    assertTrue(
                        "Missing Play metadata file $fileName for $androidTag.",
                        File(playDirectory, fileName).isFile,
                    )
                }
        }
    }

    @Test
    fun debugBuildGeneratesXaAndXbPseudoLocales() {
        val repoRoot = locateRepoRoot()
        val buildFile = File(repoRoot, "app/build.gradle.kts").readText()
        val releaseConfig = File(repoRoot, "app/src/main/res/xml/locales_config.xml").readText()
        val debugConfig = File(repoRoot, "app/src/debug/res/xml/locales_config.xml").readText()

        assertTrue(
            "Debug builds must generate en-XA and ar-XB pseudo-locales for expansion/RTL QA.",
            "isPseudoLocalesEnabled = true" in buildFile,
        )
        assertTrue("en-XA" !in releaseConfig && "ar-XB" !in releaseConfig)
        assertTrue("en-XA" in debugConfig && "ar-XB" in debugConfig)
    }

    @Test
    fun criticalExportTimelineSpeedAndMaskCopyUsesResources() {
        val repoRoot = locateRepoRoot()
        val files = listOf(
            "app/src/main/java/com/novacut/editor/ui/export/ExportSheet.kt",
            "app/src/main/java/com/novacut/editor/ui/editor/SpeedCurveEditor.kt",
            "app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt",
            "app/src/main/java/com/novacut/editor/ui/editor/MaskEditorPanel.kt",
        )
        val forbidden = listOf(
            "Export exceeds source resolution",
            "Target bitrate:",
            "Speed point position percent",
            "Delete speed point",
            "Trim start time in seconds",
            "Trim end time in seconds",
            "Control Points",
            "X coordinate percent",
            "Y coordinate percent",
            "MaskType.displayName",
            "captureFormat.displayName",
            "quality.label",
            "tier.displayName",
            "pos.displayName",
        )

        files.forEach { relative ->
            val source = File(repoRoot, relative).readText()
            forbidden.forEach { literal ->
                assertTrue("$relative contains reachable hard-coded copy: $literal", literal !in source)
            }
        }
    }

    private data class ResourceContracts(
        val keyOrder: List<String>,
        val contracts: Map<String, Set<String>>,
    ) : Map<String, Set<String>> by contracts

    private fun readResourceContracts(file: File): ResourceContracts {
        val keys = mutableListOf<String>()
        val contracts = linkedMapOf<String, Set<String>>()

        val xmlFiles = if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        } else {
            listOf(file)
        }
        xmlFiles.forEach { xmlFile ->
            val resources = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xmlFile)
                .documentElement
            for (index in 0 until resources.childNodes.length) {
                val node = resources.childNodes.item(index)
                if (node is Element) {
                    when (node.tagName) {
                    "string" -> {
                        val key = "string:${node.getAttribute("name")}"
                        keys += key
                        contracts[key] = placeholders(node.textContent)
                    }
                    "plurals" -> {
                        val pluralName = node.getAttribute("name")
                        for (itemIndex in 0 until node.childNodes.length) {
                            val item = node.childNodes.item(itemIndex)
                            if (item is Element && item.tagName == "item") {
                                val key = "plurals:$pluralName:${item.getAttribute("quantity")}"
                                keys += key
                                contracts[key] = placeholders(item.textContent)
                            }
                        }
                    }
                    "string-array" -> {
                        val arrayName = node.getAttribute("name")
                        var itemIndex = 0
                        for (itemIndexInNode in 0 until node.childNodes.length) {
                            val item = node.childNodes.item(itemIndexInNode)
                            if (item is Element && item.tagName == "item") {
                                val key = "string-array:$arrayName:$itemIndex"
                                keys += key
                                contracts[key] = placeholders(item.textContent)
                                itemIndex++
                            }
                        }
                    }
                    }
                }
            }
        }

        return ResourceContracts(keys, contracts)
    }

    private fun placeholders(value: String): Set<String> =
        FORMAT_PLACEHOLDER.findAll(value)
            .map { it.value }
            .toSet()

    private fun readLocaleConfigTags(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        return (0 until document.documentElement.childNodes.length)
            .map { document.documentElement.childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "locale" }
            .map { it.getAttribute("android:name") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun androidLocaleTag(directoryName: String): String {
        val raw = directoryName.removePrefix("values-")
        return if (raw.startsWith("b+")) {
            raw.removePrefix("b+").replace('+', '-')
        } else {
            raw.replace(Regex("-r(?=[A-Z])"), "-")
        }
    }

    private fun locateRepoRoot(): File {
        val userDir = System.getProperty("user.dir")
            ?: error("Could not read user.dir while locating repo root")
        var dir: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = dir ?: error("Could not locate repo root from ${System.getProperty("user.dir")}")
            if (File(current, ".git").exists()) return current
            dir = current.parentFile
        }
        error("Could not locate repo root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val FORMAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[a-zA-Z]")
    }
}

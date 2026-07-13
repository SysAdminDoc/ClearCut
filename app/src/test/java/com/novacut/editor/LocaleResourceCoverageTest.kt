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
        val localesConfig = File(repoRoot, "app/src/main/res/xml/locales_config.xml").readText()

        assertTrue(
            "locales_config.xml must list es once values-es ships, otherwise " +
                "Android 13+ per-app language settings cannot select it.",
            """android:name="es"""" in localesConfig
        )
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
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val resources = document.documentElement
        val keys = mutableListOf<String>()
        val contracts = linkedMapOf<String, Set<String>>()

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
                }
            }
        }

        return ResourceContracts(keys, contracts)
    }

    private fun placeholders(value: String): Set<String> =
        FORMAT_PLACEHOLDER.findAll(value)
            .map { it.value }
            .toSet()

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

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
        val base = readResourceKeys(File(repoRoot, "app/src/main/res/values/strings.xml"))
        val spanish = readResourceKeys(File(repoRoot, "app/src/main/res/values-es/strings.xml"))

        assertEquals(
            "Spanish strings must mirror the base resource keys so the locale " +
                "does not silently fall back to English for shipped UI copy.",
            base,
            spanish
        )
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

    private fun readResourceKeys(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val resources = document.documentElement
        val keys = mutableListOf<String>()

        for (index in 0 until resources.childNodes.length) {
            val node = resources.childNodes.item(index)
            if (node is Element) {
                when (node.tagName) {
                    "string" -> keys += "string:${node.getAttribute("name")}"
                    "plurals" -> {
                        val pluralName = node.getAttribute("name")
                        for (itemIndex in 0 until node.childNodes.length) {
                            val item = node.childNodes.item(itemIndex)
                            if (item is Element && item.tagName == "item") {
                                keys += "plurals:$pluralName:${item.getAttribute("quantity")}"
                            }
                        }
                    }
                }
            }
        }

        return keys
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
}

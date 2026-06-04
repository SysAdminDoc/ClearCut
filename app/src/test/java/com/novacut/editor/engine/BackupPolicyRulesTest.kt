package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupPolicyRulesTest {

    @Test
    fun legacyFullBackupKeepsManagedImportsOutOfCloudQuota() {
        val rules = readRules("backup_rules.xml")

        assertFalse(rules.includes("file", "media/imports/local_clip.mp4"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4.partial"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = true)
    }

    @Test
    fun cloudBackupExcludesManagedImportsAndRequiresEncryptionCapability() {
        val document = readDocument("data_extraction_rules.xml")
        val cloud = document.documentElement.firstChildElement("cloud-backup")

        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))

        val rules = cloud.readRules()
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4.partial"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = true)
    }

    @Test
    fun deviceTransferIncludesManagedImportsButNotPartialCopies() {
        val rules = readRules("data_extraction_rules.xml", section = "device-transfer")

        assertTrue(rules.includes("file", "media/imports/local_clip.mp4"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4.partial"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = true)
    }

    private fun assertGeneratedMediaPolicy(
        rules: List<BackupRule>,
        expectedIncluded: Boolean
    ) {
        GENERATED_MEDIA_SAMPLES.forEach { sample ->
            val message = "$sample should be ${if (expectedIncluded) "included" else "excluded"}"
            if (expectedIncluded) {
                assertTrue(message, rules.includes("file", sample))
            } else {
                assertFalse(message, rules.includes("file", sample))
            }
        }
        PARTIAL_MEDIA_SAMPLES.forEach { partial ->
            assertFalse("$partial should never be backed up or transferred", rules.includes("file", partial))
        }
    }

    private fun readRules(
        fileName: String,
        section: String? = null
    ): List<BackupRule> {
        val document = readDocument(fileName)
        val element = if (section == null) {
            document.documentElement
        } else {
            document.documentElement.firstChildElement(section)
        }
        return element.readRules()
    }

    private fun readDocument(fileName: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(locateResXml(fileName))

    private fun Element.readRules(): List<BackupRule> {
        val rules = mutableListOf<BackupRule>()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName == "include" || child.tagName == "exclude") {
                rules += BackupRule(
                    kind = child.tagName,
                    domain = child.getAttribute("domain"),
                    path = child.getAttribute("path")
                )
            }
        }
        return rules
    }

    private fun Element.firstChildElement(tagName: String): Element {
        val matches = getElementsByTagName(tagName)
        return matches.item(0) as? Element
            ?: error("Missing <$tagName> in ${this.tagName}")
    }

    private fun List<BackupRule>.includes(domain: String, path: String): Boolean {
        if (any { it.kind == "exclude" && it.domain == domain && it.matches(path) }) {
            return false
        }
        val includes = filter { it.kind == "include" && it.domain == domain }
        return includes.isEmpty() || includes.any { it.matches(path) }
    }

    private fun BackupRule.matches(candidate: String): Boolean {
        val normalizedPath = path.trim('/')
        val normalizedCandidate = candidate.trim('/')
        if ('*' in normalizedPath) {
            return wildcardRegex(normalizedPath).matches(normalizedCandidate)
        }
        return normalizedCandidate == normalizedPath || normalizedCandidate.startsWith("$normalizedPath/")
    }

    private fun wildcardRegex(pattern: String): Regex {
        val regex = buildString {
            append("^")
            pattern.forEach { char ->
                when (char) {
                    '*' -> append("[^/]*")
                    '.', '/', '_', '-' -> append(Regex.escape(char.toString()))
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append("$")
        }
        return Regex(regex)
    }

    private fun locateResXml(fileName: String): File {
        val candidates = listOf(
            File("app/src/main/res/xml/$fileName"),
            File("src/main/res/xml/$fileName"),
            File("../app/src/main/res/xml/$fileName")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate $fileName from ${File(".").absoluteFile}")
    }

    private data class BackupRule(
        val kind: String,
        val domain: String,
        val path: String
    )

    companion object {
        private val GENERATED_MEDIA_SAMPLES = listOf(
            "freeze_frames/frame.jpg",
            "voiceovers/take.m4a",
            "tts_output/narration.wav",
            "tts/dialog.wav",
            "noise_reduced/clean.m4a",
            "stabilized/shot.mp4"
        )

        private val PARTIAL_MEDIA_SAMPLES = listOf(
            "freeze_frames/frame.partial.jpg",
            "voiceovers/take.partial.m4a",
            "tts_output/narration.partial.wav",
            "tts/dialog.partial.wav",
            "noise_reduced/clean.partial.m4a",
            "stabilized/shot.partial.mp4"
        )
    }
}

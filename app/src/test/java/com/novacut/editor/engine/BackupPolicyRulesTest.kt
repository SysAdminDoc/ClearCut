package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupPolicyRulesTest {

    /**
     * Android Auto Backup allows 25 MB per app and fails the whole backup when
     * that is exceeded. Generated media is unbounded, so it must stay out of
     * both cloud scopes — otherwise a user with a few renders silently gets no
     * backup at all instead of a small working one.
     */
    @Test
    fun legacyFullBackupIsBoundedToProjectDocuments() {
        val rules = readRules("backup_rules.xml")

        assertTrue(
            "the Room database must stay in the cloud scope",
            rules.any { it.kind == "include" && it.domain == "database" && it.path == "." }
        )
        assertTrue(rules.includes("file", "autosave/project-1.json"))
        assertFalse(rules.includes("file", "autosave/project-1.json.tmp"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = false)
    }

    @Test
    fun cloudBackupIsBoundedToProjectDocumentsAndRequiresEncryptionCapability() {
        val document = readDocument("data_extraction_rules.xml")
        val cloud = document.documentElement.firstChildElement("cloud-backup")

        assertEquals("true", cloud.getAttribute("disableIfNoEncryptionCapabilities"))

        val rules = cloud.readRules()
        assertTrue(
            "the Room database must stay in the cloud scope",
            rules.any { it.kind == "include" && it.domain == "database" && it.path == "." }
        )
        assertTrue(rules.includes("file", "autosave/project-1.json"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = false)
    }

    /** The two cloud scopes must not drift apart across the Android 12 boundary. */
    @Test
    fun legacyAndModernCloudScopesAgree() {
        val legacy = readRules("backup_rules.xml").map { it.kind to (it.domain to it.path) }.toSet()
        val modern = readDocument("data_extraction_rules.xml")
            .documentElement.firstChildElement("cloud-backup")
            .readRules().map { it.kind to (it.domain to it.path) }.toSet()

        assertEquals(legacy, modern)
    }

    @Test
    fun deviceTransferIncludesManagedImportsButNotPartialCopies() {
        val rules = readRules("data_extraction_rules.xml", section = "device-transfer")

        assertTrue(rules.includes("file", "media/imports/local_clip.mp4"))
        assertFalse(rules.includes("file", "media/imports/local_clip.mp4.partial"))
        assertGeneratedMediaPolicy(rules, expectedIncluded = true)
    }

    @Test
    fun deviceTransferCarriesAppOwnedFontsAndLutsButNotPartialCopies() {
        val deviceRules = readRules("data_extraction_rules.xml", section = "device-transfer")
        val cloudRules = readDocument("data_extraction_rules.xml")
            .documentElement.firstChildElement("cloud-backup")
            .readRules()

        assertTrue(deviceRules.includes("file", "fonts/Inter.ttf"))
        assertTrue(deviceRules.includes("file", "luts/cinema.cube"))
        assertFalse(deviceRules.includes("file", "fonts/.Inter.ttf.partial"))
        assertFalse(deviceRules.includes("file", "luts/.cinema.cube.partial"))
        assertFalse(cloudRules.includes("file", "fonts/Inter.ttf"))
        assertFalse(cloudRules.includes("file", "luts/cinema.cube"))
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

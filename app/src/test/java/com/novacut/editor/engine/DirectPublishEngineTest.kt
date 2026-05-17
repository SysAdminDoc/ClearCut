package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DirectPublishEngineTest {

    @Test
    fun validatePublishableFile_rejectsMissingFile() {
        val file = File("missing-export-${System.nanoTime()}.mp4")

        assertEquals("Export file not found", validatePublishableFile(file))
    }

    @Test
    fun validatePublishableFile_rejectsDirectories() {
        val dir = Files.createTempDirectory("publish-dir-").toFile()
        try {
            assertEquals("Export path is not a video file", validatePublishableFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validatePublishableFile_rejectsEmptyFiles() {
        val dir = Files.createTempDirectory("publish-empty-").toFile()
        try {
            val file = File(dir, "export.mp4").apply { writeBytes(ByteArray(0)) }

            assertEquals("Export file is empty", validatePublishableFile(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validatePublishableFile_acceptsReadableNonEmptyFiles() {
        val dir = Files.createTempDirectory("publish-valid-").toFile()
        try {
            val file = File(dir, "export.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            assertNull(validatePublishableFile(file))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun normalizePublishMeta_boundsIntentExtras() {
        val normalized = normalizePublishMeta(
            DirectPublishEngine.PublishMeta(
                title = "\n\t",
                description = "d".repeat(10_000),
                chapters = "c".repeat(10_000),
                aiDisclosureSummary = "a".repeat(2_000),
                tags = listOf("launch!", "launch!", "tag-with-dashes", "x".repeat(100))
            )
        )

        assertEquals("NovaCut export", normalized.title)
        assertTrue(normalized.description.length <= 4_000)
        assertTrue(normalized.chapters.length <= 4_000)
        assertTrue(normalized.aiDisclosureSummary.length <= 1_000)
        assertEquals(listOf("launch", "tagwithdashes", "x".repeat(48)), normalized.tags)
    }

    @Test
    fun buildPublishShareText_capsBodyAndRemovesUnsafeTags() {
        val body = buildPublishShareText(
            DirectPublishEngine.PublishMeta(
                title = "My Export",
                description = "d".repeat(7_700),
                chapters = "00:00 Intro\n00:10 Main",
                aiDisclosureSummary = "AI assistance recorded: 1 × auto edit local (NovaCut Auto Edit).",
                tags = listOf("good_tag", "bad tag!", "---")
            ),
            target = DirectPublishEngine.Target.YOUTUBE
        )

        assertTrue(body.length <= 8_000)
        assertTrue(body.startsWith("My Export"))
        assertTrue(body.contains("AI disclosure selected: AI assistance recorded"))
        assertTrue(body.contains("00:00 Intro\n00:10 Main"))
        assertTrue(body.contains("#good_tag"))
        assertTrue(body.contains("#badtag"))
        assertFalse(body.contains("#---"))
    }

    @Test
    fun onlyYoutubeAndTiktokExposeAiDisclosureControls() {
        assertTrue(DirectPublishEngine.Target.YOUTUBE.hasAiDisclosureControl)
        assertTrue(DirectPublishEngine.Target.TIKTOK.hasAiDisclosureControl)
        assertFalse(DirectPublishEngine.Target.INSTAGRAM.hasAiDisclosureControl)
    }
}

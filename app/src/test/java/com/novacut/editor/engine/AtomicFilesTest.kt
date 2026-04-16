package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicFilesTest {

    @Test
    fun moveFileReplacing_overwritesTargetAndDeletesSource() {
        val dir = Files.createTempDirectory("atomic-files-").toFile()
        try {
            val source = File(dir, "source.txt").apply { writeText("new", Charsets.UTF_8) }
            val target = File(dir, "target.txt").apply { writeText("old", Charsets.UTF_8) }

            moveFileReplacing(source, target)

            assertFalse(source.exists())
            assertTrue(target.exists())
            assertEquals("new", target.readText(Charsets.UTF_8))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeUtf8TextAtomically_replacesContentUsingUtf8() {
        val dir = Files.createTempDirectory("atomic-write-").toFile()
        try {
            val target = File(dir, "template.json").apply { writeText("before", Charsets.UTF_8) }

            writeUtf8TextAtomically(target, "caf\u00e9")

            assertEquals("caf\u00e9", target.readText(Charsets.UTF_8))
        } finally {
            dir.deleteRecursively()
        }
    }
}

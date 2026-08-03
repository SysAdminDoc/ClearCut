package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LutRegistryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val registry = LutRegistry(context)

    @Test
    fun validStoredLutReportsHashAndSupportsRollbackAfterRemoval() {
        val fileName = "registry-${System.nanoTime()}.cube"
        val file = File(context.filesDir, "luts/$fileName").apply {
            parentFile?.mkdirs()
            writeText(validCube())
        }
        try {
            val listed = registry.listImportedLuts().single { it.fileName == fileName }
            assertEquals(2, listed.size)
            assertTrue(listed.contentHash.matches(Regex("[0-9a-f]{64}")))
            assertFalse(listed.canRollback)

            assertTrue(registry.deleteLut(fileName))
            assertFalse(file.exists())
            assertTrue(registry.canRollback(fileName))
            assertTrue(registry.rollbackLut(fileName))
            assertTrue(file.exists())
            assertFalse(registry.canRollback(fileName))
        } finally {
            file.delete()
            File(context.filesDir, "luts/rollback/$fileName").delete()
        }
    }

    @Test
    fun unsafeNamesCannotDeleteOutsideTheLutDirectory() {
        assertFalse(registry.deleteLut("../databases/room.db"))
        assertFalse(registry.rollbackLut("../../private/font.cube"))
    }

    private fun validCube(): String = buildString {
        appendLine("LUT_3D_SIZE 2")
        repeat(8) { appendLine("0 0 0") }
    }
}

package com.novacut.editor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Path-traversal guard for untrusted `.stylepack` ids. A hostile id must never
 * resolve to a file outside `filesDir/style_packs/`.
 */
@RunWith(RobolectricTestRunner::class)
class StylePackManagerSecurityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = StylePackManager(context)

    @Test
    fun removePackRejectsTraversalIdAndLeavesExternalFileIntact() {
        // A file the traversal would target if the id were used verbatim.
        val victim = File(context.filesDir, "databases-room-projects.marker").apply {
            parentFile?.mkdirs()
            writeText("important")
        }
        try {
            assertFalse(manager.removePack("../databases-room-projects.marker"))
            assertFalse(manager.removePack("../../databases/room-projects"))
            assertTrue("traversal must not delete files outside style_packs/", victim.exists())
        } finally {
            victim.delete()
        }
    }

    @Test
    fun isInstalledRejectsUnsafeIds() {
        assertFalse(manager.isInstalled("../secret"))
        assertFalse(manager.isInstalled("a/b"))
        assertFalse(manager.isInstalled(""))
    }
}

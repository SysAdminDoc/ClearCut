package com.novacut.editor.engine

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Managed-media GC keep-set coverage. Two data-loss/leak regressions:
 * 1. A crash between the two renames in writeAutoSaveFileLocked leaves a
 *    project's only recovery artifact as `.bak` — the reference scan must
 *    still see its media or the sweeper deletes a recoverable project's files.
 * 2. `imported_<ts>` archive-extraction dirs were never garbage-collected;
 *    the new sweeper must remove unreferenced old ones and keep live ones.
 */
@RunWith(RobolectricTestRunner::class)
class ManagedMediaGcTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun autoSaveJsonReferencing(uri: String): String {
        return JSONObject().apply {
            put("tracks", JSONArray().put(JSONObject().apply {
                put("clips", JSONArray().put(JSONObject().apply {
                    put("sourceUri", uri)
                }))
            }))
        }.toString()
    }

    @Test
    fun collectReferencedSourceUris_includesBakOnlyAutosaves() = runBlocking {
        val autoSaveDir = File(context.filesDir, "autosave").apply { mkdirs() }
        File(autoSaveDir, "proj-live.json")
            .writeText(autoSaveJsonReferencing("file:///media/live.mp4"))
        // Crash-window artifact: the .json was renamed to .bak but the new
        // .json never landed. The media it references must stay protected.
        File(autoSaveDir, "proj-crashed.bak")
            .writeText(autoSaveJsonReferencing("file:///media/crashed.mp4"))

        val referenced = ProjectAutoSave(context).collectReferencedSourceUris()

        assertTrue("got: $referenced", "file:///media/live.mp4" in referenced)
        assertTrue("got: $referenced", "file:///media/crashed.mp4" in referenced)
    }

    @Test
    fun collectMediaReferenceUris_includesStoryboardMediaUri() {
        val json = JSONObject().apply {
            put("storyboardCards", JSONArray().put(JSONObject().apply {
                put("id", "card-1")
                put("mediaUri", "file:///media/card.mp4")
            }))
        }.toString()

        val uris = collectMediaReferenceUrisFromAutoSaveJson(json)
        assertTrue("json: $json got: $uris", "file:///media/card.mp4" in uris)
    }

    @Test
    fun sweepUnreferencedArchiveImports_deletesOnlyOldUnreferencedDirs() {
        val oldMs = System.currentTimeMillis() - 48L * 60L * 60L * 1000L

        val orphanDir = File(context.filesDir, "imported_100").apply { mkdirs() }
        val orphanFile = File(orphanDir, "orphan.mp4").apply { writeText("x".repeat(100)) }
        assertTrue(orphanFile.setLastModified(oldMs))

        val liveDir = File(context.filesDir, "imported_200").apply { mkdirs() }
        val liveFile = File(liveDir, "live.mp4").apply { writeText("y") }
        assertTrue(liveFile.setLastModified(oldMs))

        val freshDir = File(context.filesDir, "imported_300").apply { mkdirs() }
        File(freshDir, "fresh.mp4").writeText("z")

        val unrelated = File(context.filesDir, "autosave").apply { mkdirs() }

        val result = sweepUnreferencedArchiveImports(
            context,
            referencedUris = setOf(Uri.fromFile(liveFile))
        )

        assertFalse("orphan dir should be swept", orphanDir.exists())
        assertTrue("referenced dir must survive", liveFile.isFile)
        assertTrue("fresh dir must survive the min-age buffer", freshDir.isDirectory)
        assertTrue("non-import dirs must never be touched", unrelated.isDirectory)
        assertEquals(1, result.filesDeleted)
        assertEquals(100L, result.bytesFreed)
    }
}

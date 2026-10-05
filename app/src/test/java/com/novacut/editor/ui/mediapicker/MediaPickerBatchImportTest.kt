package com.novacut.editor.ui.mediapicker

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.novacut.editor.engine.managedMediaDir
import com.novacut.editor.engine.writeManagedMediaAssetSidecar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowStatFs
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaPickerBatchImportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun giveTheAppRoomToCopy() {
        ShadowStatFs.reset()
        ShadowStatFs.registerStats(context.filesDir, 1_000_000, 1_000_000, 1_000_000)
        managedMediaDir(context).deleteRecursively()
    }

    @After
    fun cleanUp() {
        managedMediaDir(context).deleteRecursively()
        ShadowStatFs.reset()
    }

    @Test
    fun aFinishedBatchKeepsEveryCopy() = runBlocking {
        val result = importMediaPickerBatch(context, freshSources(3))

        assertEquals(3, result.imported.size)
        assertTrue(result.imported.all { File(checkNotNull(it.uri.path)).isFile })
        assertEquals(3, managedMediaFiles().size)
    }

    @Test
    fun cancellingAtAnyItemBoundaryLeavesOnlyWhatWasThereBefore() {
        val alreadyManaged = existingManagedFile()
        val before = managedTree()
        // Three items report progress six times: before and after each one.
        for (boundary in 1..6) {
            val selections = listOf(
                freshSource("a$boundary"),
                selection(Uri.fromFile(alreadyManaged), "kept$boundary"),
                freshSource("b$boundary"),
            )

            val failure = runAbortingAt(selections, boundary) { job -> job.cancel() }

            assertTrue("boundary $boundary: $failure", failure is CancellationException)
            assertEquals("boundary $boundary", before, managedTree())
            assertTrue(alreadyManaged.isFile)
        }
    }

    @Test
    fun aBatchThatFailsPartWayDeletesTheCopiesItMade() {
        val before = managedTree()

        val failure = runAbortingAt(freshSources(3), boundary = 4) { error("disk went away") }

        assertTrue("$failure", failure is IllegalStateException)
        assertEquals(before, managedTree())
    }

    private fun runAbortingAt(
        selections: List<MediaPickerSelection>,
        boundary: Int,
        abort: (Job) -> Unit,
    ): Throwable? = runBlocking {
        var calls = 0
        var failure: Throwable? = null
        lateinit var job: Job
        job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                importMediaPickerBatch(context, selections) { if (++calls == boundary) abort(job) }
            } catch (thrown: Throwable) {
                failure = thrown
            }
        }
        job.start()
        job.join()
        failure
    }

    private fun managedMediaFiles(): List<File> =
        managedMediaDir(context).listFiles()?.filter { !it.name.endsWith(".asset.json") }.orEmpty()

    private fun managedTree(): Set<String> =
        managedMediaDir(context).walkTopDown().filter { it.isFile }.map { it.name }.toSet()

    /** A file an earlier import already copied in, sidecar and all. */
    private fun existingManagedFile(): File {
        val file = File(managedMediaDir(context).apply { mkdirs() }, "already-here.mp4")
        file.writeBytes(ByteArray(2_048) { 7 })
        writeManagedMediaAssetSidecar(context, Uri.fromFile(file), Uri.parse("content://picker/already-here"), "video")
        return file
    }

    private fun freshSources(count: Int): List<MediaPickerSelection> = (1..count).map { freshSource("clip$it") }

    private fun freshSource(name: String): MediaPickerSelection {
        val file = File(context.cacheDir, "$name-${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(4_096) { 1 }) }
        return selection(Uri.fromFile(file), name)
    }

    private fun selection(uri: Uri, name: String) = MediaPickerSelection(
        id = name,
        uri = uri,
        mediaType = "video",
        displayName = name,
        captureTimeMs = null,
    )
}

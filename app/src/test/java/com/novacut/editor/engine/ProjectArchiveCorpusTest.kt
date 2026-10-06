package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowStatFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-built archives that a sender could craft to get past the archive importer and its
 * preview. Every refusal must leave no destination, no extraction stage and no staged
 * copy behind, and nothing in this corpus may come out as an app or code file.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectArchiveCorpusTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context get() = RuntimeEnvironment.getApplication().applicationContext
    private val stagingDir: File get() = File(context.cacheDir, "project-archive-staging")
    private val validProject: ByteArray get() = AutoSaveState(projectId = "corpus").serialize().toByteArray()

    @Before
    fun registerStorage() {
        registerFreeBlocks(1_800_000)
    }

    @After
    fun resetStorage() {
        ShadowStatFs.reset()
    }

    @Test
    fun entryNamesThatLeaveTheProjectAreRefusedByImportAndPreview() = runBlocking {
        val names = listOf(
            "../escape.mp4",
            "media/../../escape.mp4",
            "/absolute.mp4",
            "media\\windows.mp4",
            "C:/drive.mp4",
            "media/./dot.mp4",
        )
        names.forEachIndexed { index, name ->
            val archive = writeArchive(
                "traversal-$index",
                Entry("project.json", validProject),
                Entry(name, "payload".toByteArray()),
            )
            assertImportRefused(archive, "traversal-$index", "unsafe entry path|invalid entry name")
            assertPreviewRefused(Uri.fromFile(archive), "unsafe entry path|invalid entry name")
        }
        assertFalse(File(temp.root, "escape.mp4").exists())
        assertFalse(File(temp.root.parentFile, "escape.mp4").exists())
    }

    @Test
    fun aDuplicatedEntryIsRefusedByImportAndPreview() = runBlocking {
        val duplicateProject = patchName(
            zipBytes(Entry("project.json", validProject), Entry("project.jsoX", validProject)),
            from = "project.jsoX",
            to = "project.json",
        )
        val duplicateMedia = patchName(
            zipBytes(
                Entry("project.json", validProject),
                Entry("media/clip.mp4", "first".toByteArray()),
                Entry("media/clip.mpX", "second".toByteArray()),
            ),
            from = "media/clip.mpX",
            to = "media/clip.mp4",
        )

        listOf("duplicate-project" to duplicateProject, "duplicate-media" to duplicateMedia).forEach { (label, bytes) ->
            val archive = File(temp.root, "$label.clearcut").apply { writeBytes(bytes) }
            assertImportRefused(archive, label, "duplicate entry")
        }
        assertPreviewRefused(
            Uri.fromFile(File(temp.root, "duplicate-project.clearcut")),
            "duplicate entry",
        )
    }

    @Test
    fun aPayloadThatInflatesPastItsDeclaredSizeIsRefused() = runBlocking {
        val archive = archiveWithDeclaredMediaSize("declared-small") { actual -> actual - 100L }

        assertImportRefused(archive, "declared-small", "exceeded declared size")
    }

    @Test
    fun aPayloadThatInflatesShortOfItsDeclaredSizeIsRefused() = runBlocking {
        val archive = archiveWithDeclaredMediaSize("declared-large") { actual -> actual + 100L }

        assertImportRefused(archive, "declared-large", "size changed")
    }

    @Test
    fun malformedProjectJsonIsRefused() = runBlocking {
        val archive = writeArchive("malformed", Entry("project.json", "{\"state\": {\"tracks\": [".toByteArray()))

        assertImportRefused(archive, "malformed", null)
        assertFalse(ProjectArchive.previewArchive(context, Uri.fromFile(archive)).valid)
    }

    @Test
    fun aFutureSchemaIsReportedAndNothingIsInstalled() = runBlocking {
        val future = """{"schemaVersion": ${AutoSaveState.FORMAT_VERSION + 50}, "projectId": "from-the-future"}"""
        val archive = writeArchive(
            "future",
            Entry("project.json", future.toByteArray()),
            Entry("media/clip.mp4", "media".toByteArray()),
        )
        val target = File(temp.root, "future-import")

        val result = ProjectArchive.importArchiveWithReport(context, Uri.fromFile(archive), target)
        val preview = ProjectArchive.previewArchive(context, Uri.fromFile(archive))

        assertNull(result.state)
        assertTrue(result.report.schemaTooNew)
        assertEquals("from-the-future", result.report.originalProjectId)
        assertNothingLeftBehind(target)
        assertTrue(preview.report.schemaTooNew)
        assertFalse(preview.valid)
    }

    @Test
    fun appAndCodeFilesByNameAreSkippedWithAWarning() = runBlocking {
        val archive = writeArchive(
            "named-code",
            Entry("project.json", validProject),
            Entry("media/payload.apk", "not really an apk".toByteArray()),
            Entry("luts/helper.so", "not really a library".toByteArray()),
            Entry("fonts/Tool.JAR", "not really a jar".toByteArray()),
            Entry("media/clip.mp4", "media".toByteArray()),
        )
        val target = File(temp.root, "named-code-import")

        val result = ProjectArchive.importArchiveWithReport(context, Uri.fromFile(archive), target)

        assertNotNull(result.errorMessage, result.state)
        assertEquals(
            listOf(
                "Skipped an app or code file: media/payload.apk",
                "Skipped an app or code file: luts/helper.so",
                "Skipped an app or code file: fonts/Tool.JAR",
            ),
            result.report.warnings.filter { it.startsWith("Skipped an app or code file") },
        )
        val installed = target.walkTopDown().filter { it.isFile }.map { it.name }.toList()
        assertTrue(installed.toString(), "clip.mp4" in installed)
        assertTrue(installed.toString(), installed.none { it.endsWith(".apk") || it.endsWith(".so") || it.endsWith(".JAR") })
    }

    @Test
    fun codeDisguisedAsMediaRefusesTheWholeArchive() = runBlocking {
        val disguises = listOf(
            Triple("media/clip.mp4", byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00), "Dalvik bytecode"),
            Triple("fonts/Brand.ttf", byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00), "native library"),
            Triple("watermarks/logo.png", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x08, 0x00), "ZIP package"),
        )
        disguises.forEachIndexed { index, (name, head, kind) ->
            val archive = writeArchive(
                "disguised-$index",
                Entry("project.json", validProject),
                Entry("media/first.mp4", "fine".toByteArray()),
                Entry(name, head + ByteArray(256) { it.toByte() }),
            )
            assertImportRefused(archive, "disguised-$index", kind)
        }
    }

    @Test
    fun aDotLottieZipContainerStillImports() = runBlocking {
        val lottie = zipBytes(Entry("manifest.json", "{}".toByteArray()))
        val archive = writeArchive(
            "lottie",
            Entry("project.json", validProject),
            Entry("media/sticker.lottie", lottie),
        )
        val target = File(temp.root, "lottie-import")

        val result = ProjectArchive.importArchiveWithReport(context, Uri.fromFile(archive), target)

        assertNotNull(result.errorMessage, result.state)
        assertTrue(File(target, "media/sticker.lottie").readBytes().contentEquals(lottie))
    }

    @Test
    fun aProviderStreamThatFailsMidCopyLeavesNoStagedArchive() = runBlocking {
        val bytes = zipBytes(
            Entry("project.json", validProject),
            Entry("media/clip.mp4", randomBytes(256 * 1024), stored = true),
        )
        val uri = Uri.parse("content://corpus.provider/interrupted.clearcut")
        shadowOf(context.contentResolver).registerInputStream(uri, FailingAfter(bytes, failAt = 64 * 1024))
        val target = File(temp.root, "interrupted-import")

        val result = ProjectArchive.importArchiveWithReport(context, uri, target)

        assertNull(result.state)
        assertTrue(result.errorMessage, result.errorMessage.orEmpty().contains("provider went away"))
        assertNothingLeftBehind(target)
    }

    @Test
    fun stagingStopsWhenStorageRunsOut() = runBlocking {
        registerFreeBlocks(1_000)
        val archive = writeArchive("no-room", Entry("project.json", validProject))

        assertImportRefused(archive, "no-room", "Insufficient storage for archive staging")
    }

    @Test
    fun previewStopsReadingAnEndlessDeflateStream() = runBlocking {
        val stream = EndlessEmptyDeflate("project.json")
        val uri = Uri.parse("content://corpus.provider/endless.clearcut")
        shadowOf(context.contentResolver).registerInputStream(uri, stream)

        val preview = ProjectArchive.previewArchive(context, uri)

        assertFalse(preview.valid)
        assertTrue(preview.errorMessage, preview.errorMessage.orEmpty().contains("runs past"))
        assertTrue(
            "read ${stream.served} bytes",
            stream.served <= ProjectArchive.MAX_ARCHIVE_PREVIEW_INPUT_BYTES + 64 * 1024,
        )
    }

    @Test
    fun previewRefusesADirectoryEntryThatCarriesData() = runBlocking {
        val archive = writeArchive(
            "directory-data",
            Entry("assets/", randomBytes(4_096)),
            Entry("project.json", validProject),
        )

        assertPreviewRefused(Uri.fromFile(archive), "directory entry carries data")
    }

    private data class Entry(val name: String, val bytes: ByteArray, val stored: Boolean = false)

    private fun writeArchive(label: String, vararg entries: Entry): File =
        File(temp.root, "$label.clearcut").apply { writeBytes(zipBytes(*entries)) }

    private fun zipBytes(vararg entries: Entry): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { entry ->
                val zipEntry = ZipEntry(entry.name)
                if (entry.stored) {
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = entry.bytes.size.toLong()
                    zipEntry.compressedSize = entry.bytes.size.toLong()
                    zipEntry.crc = CRC32().apply { update(entry.bytes) }.value
                }
                zip.putNextEntry(zipEntry)
                zip.write(entry.bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Renames every copy of an entry name in place; ZipOutputStream won't write a duplicate itself. */
    private fun patchName(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val needle = from.toByteArray()
        var patched = 0
        var index = 0
        while (index <= bytes.size - needle.size) {
            if (needle.indices.all { bytes[index + it] == needle[it] }) {
                to.toByteArray().copyInto(bytes, index)
                patched++
                index += needle.size
            } else {
                index++
            }
        }
        check(patched == 2) { "expected the local and central names, patched $patched" }
        return bytes
    }

    /** Builds an archive whose central directory lies about media/clip.mp4's inflated size. */
    private fun archiveWithDeclaredMediaSize(label: String, declare: (Long) -> Long): File {
        val media = randomBytes(16 * 1024)
        val bytes = zipBytes(Entry("project.json", validProject), Entry("media/clip.mp4", media))
        val name = "media/clip.mp4".toByteArray()
        var header = -1
        for (offset in 0..bytes.size - 46) {
            if (readInt(bytes, offset) != CENTRAL_HEADER) continue
            val nameLength = readShort(bytes, offset + 28)
            if (nameLength == name.size && name.indices.all { bytes[offset + 46 + it] == name[it] }) {
                header = offset
                break
            }
        }
        check(header >= 0) { "central header for media/clip.mp4 not found" }
        val declared = declare(media.size.toLong())
        for (shift in 0 until 4) bytes[header + 24 + shift] = (declared shr (8 * shift)).toByte()
        return File(temp.root, "$label.clearcut").apply { writeBytes(bytes) }
    }

    /** [messagePart] may list alternatives separated by '|'. */
    private suspend fun assertImportRefused(archive: File, label: String, messagePart: String?) {
        val target = File(temp.root, "$label-import")
        val result = ProjectArchive.importArchiveWithReport(context, Uri.fromFile(archive), target)

        assertNull("$label imported", result.state)
        assertNotNull("$label has no error", result.errorMessage)
        if (messagePart != null) {
            val message = result.errorMessage.orEmpty()
            assertTrue("$label: $message", messagePart.split('|').any(message::contains))
        }
        assertNothingLeftBehind(target)
    }

    private suspend fun assertPreviewRefused(uri: Uri, messagePart: String) {
        val preview = ProjectArchive.previewArchive(context, uri)
        assertFalse("preview of $uri passed", preview.valid)
        val message = preview.errorMessage.orEmpty()
        assertTrue("$uri: $message", messagePart.split('|').any(message::contains))
    }

    private fun assertNothingLeftBehind(target: File) {
        assertFalse("${target.name} was created", target.exists())
        val stages = temp.root.listFiles().orEmpty().filter { it.name.startsWith(".${target.name}.import-") }
        assertTrue("extraction stage left: $stages", stages.isEmpty())
        val staged = stagingDir.listFiles().orEmpty().filter { it.name.startsWith("project-import-") }
        assertTrue("staged archive left: $staged", staged.isEmpty())
    }

    private fun registerFreeBlocks(freeBlocks: Int) {
        ShadowStatFs.registerStats(temp.root, 2_000_000, freeBlocks, freeBlocks)
        ShadowStatFs.registerStats(stagingDir, 2_000_000, freeBlocks, freeBlocks)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (0 until 4).fold(0) { value, shift -> value or ((bytes[offset + shift].toInt() and 0xFF) shl (8 * shift)) }

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /** Serves the archive, then fails the way a provider does when its app is killed mid-transfer. */
    private class FailingAfter(private val bytes: ByteArray, private val failAt: Int) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position >= failAt) throw IOException("provider went away")
            return if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= failAt) throw IOException("provider went away")
            if (position >= bytes.size) return -1
            val count = minOf(length, failAt - position, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    /**
     * A local header for a deflated entry followed by empty stored deflate blocks forever:
     * the inflater keeps asking for input and never produces a byte.
     */
    private class EndlessEmptyDeflate(name: String) : InputStream() {
        private val header: ByteArray = ByteArrayOutputStream().apply {
            fun le(value: Int, size: Int) = repeat(size) { write((value shr (8 * it)) and 0xFF) }
            le(LOCAL_HEADER, 4)
            le(20, 2)
            le(0x08, 2)
            le(ZipEntry.DEFLATED, 2)
            le(0, 4)
            le(0, 4)
            le(0, 4)
            le(0, 4)
            le(name.length, 2)
            le(0, 2)
            write(name.toByteArray())
        }.toByteArray()
        private val block = byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())
        var served = 0L
            private set

        override fun read(): Int {
            val value = byteAt(served)
            served++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            for (index in 0 until length) buffer[offset + index] = byteAt(served + index).toByte()
            served += length
            return length
        }

        private fun byteAt(position: Long): Int =
            if (position < header.size) header[position.toInt()].toInt() and 0xFF
            else block[((position - header.size) % block.size).toInt()].toInt() and 0xFF
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
    }
}

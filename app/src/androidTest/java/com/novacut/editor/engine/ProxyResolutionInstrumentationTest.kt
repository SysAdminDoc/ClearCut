package com.novacut.editor.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.ProxyResolutionPolicy.Plan
import com.novacut.editor.model.ProxyResolution
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders real proxies. trim-boundary.mp4 is 320x240; the portrait copy stores the same
 * frames with a 90 degree rotation, so it is shown 240 wide and 320 tall.
 */
@RunWith(AndroidJUnit4::class)
class ProxyResolutionInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = ProxyEngine(context, MemoryTrimRegistry())
    private val created = mutableListOf<File>()

    @After
    fun cleanUp() {
        created.forEach { it.delete() }
        engine.clearProxies()
    }

    @Test
    fun aRotatedPortraitSourceGetsAPortraitProxyAtItsShownHeight() = runBlocking {
        val source = Uri.fromFile(copyAsset("trim-boundary-portrait.mp4"))

        val plan = engine.planProxy(source, ProxyResolution.EIGHTH)
        // 1/8 of the 320 shown rows is 40, lifted to the 120-row floor.
        assertEquals(Plan.Generate(120), plan)

        val proxy = checkNotNull(engine.generateProxy(source, ProxyResolution.EIGHTH, plan))
        val (width, height) = shownSize(proxy)
        assertEquals(120, height)
        assertEquals(90, width)
    }

    @Test
    fun aLandscapeSourceScalesFromItsOwnHeightNotAFixed1080() = runBlocking {
        val source = Uri.fromFile(copyAsset("trim-boundary.mp4"))

        val proxy = checkNotNull(engine.generateProxy(source, ProxyResolution.EIGHTH))

        assertEquals(160 to 120, shownSize(proxy))
    }

    @Test
    fun aCachedProxyCanBeAskedForAgainAndAgain() = runBlocking {
        val source = Uri.fromFile(copyAsset("trim-boundary.mp4"))
        val rendered = checkNotNull(engine.generateProxy(source, ProxyResolution.EIGHTH))

        // Returning the cached file used to keep the per-source lock, so the request after
        // it never came back.
        repeat(2) {
            assertEquals(rendered, withTimeout(10_000) { engine.generateProxy(source, ProxyResolution.EIGHTH) })
        }
    }

    @Test
    fun aSourceAlreadyAtOrBelowTheTierIsEditedDirectly() = runBlocking {
        val source = Uri.fromFile(copyAsset("trim-boundary.mp4"))

        val plan = engine.planProxy(source, ProxyResolution.HALF)

        assertTrue("$plan", plan is Plan.UseSource)
        assertNull(engine.generateProxy(source, ProxyResolution.HALF, plan))
        assertNull(engine.getProxyUri(source))
    }

    private fun copyAsset(name: String): File {
        val file = File(context.cacheDir, "proxy-source-${System.nanoTime()}-$name")
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file.also(created::add)
    }

    private fun shownSize(uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun read(key: Int) = checkNotNull(retriever.extractMetadata(key)).toInt()
            val width = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return if (rotation % 180 == 90) height to width else width to height
        } finally {
            retriever.release()
        }
    }
}

package com.novacut.editor.engine

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.model.Clip
import com.novacut.editor.model.TimelineTimebase
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * FCPXML is parsed with the platform's own XML stack, which on a phone is not the JDK
 * parser the JVM suite runs on. These run the same documents through the device's
 * parser: a plain round trip (which failed on every phone while ClearCut set parser
 * features Android rejects), an external entity aimed at an app-private file, and a
 * nested entity bomb. Both hostile documents are refused for their internal DTD.
 */
@RunWith(AndroidJUnit4::class)
class FcpxmlDeviceParserInstrumentationTest {
    private val engine = TimelineExchangeEngine(null)

    @Test
    fun anExportedFcpxmlImportsBackThroughTheDeviceParser() {
        val source = Clip(
            id = "device-clip",
            sourceUri = Uri.parse("file:///sdcard/Movies/device-clip.mp4"),
            sourceDurationMs = 2_000L,
            timelineStartMs = 0L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            name = "Device clip",
        )
        val xml = engine.exportToFcpxml(
            tracks = listOf(Track(type = TrackType.VIDEO, index = 0, clips = listOf(source))),
            projectName = "Device parser",
            timebase = TimelineTimebase(30),
        )

        val imported = engine.importFromFcpxml(xml, Uri::parse)

        assertEquals(emptyList<String>(), imported.warnings)
        assertEquals("Device clip", imported.tracks.single().clips.single().name)
    }

    @Test
    fun anExternalEntityNeverReadsALocalFile() {
        val secret = "fcpxml-secret-${UUID.randomUUID()}"
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fcpxml-secret.txt")
        target.writeText(secret)
        try {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE fcpxml [<!ENTITY leak SYSTEM "file://${target.absolutePath}">]>
                <fcpxml version="1.10">
                  <resources>
                    <format id="r1" frameDuration="1/30s"/>
                    <asset id="r2" name="&leak;" src="file:///sdcard/Movies/a.mp4" duration="2s" hasVideo="1"/>
                  </resources>
                  <library><event><project name="&leak;"><sequence format="r1"><spine>
                    <asset-clip ref="r2" name="&leak;" offset="0s" start="0s" duration="2s"/>
                  </spine></sequence></project></event></library>
                </fcpxml>
            """.trimIndent()

            val imported = runCatching { engine.importFromFcpxml(xml, Uri::parse) }

            val seen = imported.getOrNull()?.let { result ->
                result.tracks.flatMap { it.clips }.mapNotNull { it.name } + result.warnings
            }.orEmpty() + listOfNotNull(imported.exceptionOrNull()?.message)
            assertFalse("the entity's file reached the import: $seen", seen.any { it.contains(secret) })
            assertEquals(listOf(INTERNAL_DTD_REFUSAL), imported.getOrNull()?.warnings)
        } finally {
            target.delete()
        }
    }

    @Test
    fun aNestedEntityBombIsRefusedWithoutExpanding() {
        val levels = (1..9).joinToString("\n") { level ->
            val previous = "&e${level - 1};"
            "<!ENTITY e$level \"${previous.repeat(10)}\">"
        }
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE fcpxml [
            <!ENTITY e0 "lol">
            $levels
            ]>
            <fcpxml version="1.10">
              <resources><format id="r1" frameDuration="1/30s"/>
                <asset id="r2" src="file:///sdcard/Movies/a.mp4" duration="2s" hasVideo="1"/></resources>
              <library><event><project name="bomb"><sequence format="r1"><spine>
                <asset-clip ref="r2" name="&e9;" offset="0s" start="0s" duration="2s"/>
              </spine></sequence></project></event></library>
            </fcpxml>
        """.trimIndent()

        val started = System.nanoTime()
        val imported = runCatching { engine.importFromFcpxml(xml, Uri::parse) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertTrue("parsing took ${elapsedMs}ms", elapsedMs < 5_000L)
        val longestName = imported.getOrNull()?.tracks.orEmpty()
            .flatMap { it.clips }.maxOfOrNull { it.name?.length ?: 0 } ?: 0
        assertTrue("an entity expanded into a ${longestName}-character name", longestName < 10_000)
        assertEquals(listOf(INTERNAL_DTD_REFUSAL), imported.getOrNull()?.warnings)
    }

    private companion object {
        const val INTERNAL_DTD_REFUSAL =
            "Failed to parse FCPXML: the file declares an internal DTD, which ClearCut doesn't load"
    }
}

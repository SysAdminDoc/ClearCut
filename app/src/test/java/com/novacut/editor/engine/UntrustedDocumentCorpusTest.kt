package com.novacut.editor.engine

import android.net.TestUri
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Hostile and broken interchange documents and LUTs. Each one is refused or warned about,
 * nothing reads outside the document, and no reader holds more than its cap in memory.
 * The archive half of the corpus lives in ProjectArchiveCorpusTest.
 */
class UntrustedDocumentCorpusTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val engine = TimelineExchangeEngine(null)

    @Test
    fun theDoctypeCheckReadsOnlyWhatTheDeclarationAsksFor() {
        assertNull(engine.fcpxmlDoctypeIssue(fcpxml()))
        assertNull(engine.fcpxmlDoctypeIssue(fcpxml(prolog = "<!-- exported -->\n<!DOCTYPE fcpxml>")))
        assertNull(engine.fcpxmlDoctypeIssue("\uFEFF" + fcpxml(prolog = "<!DOCTYPE fcpxml>")))
        assertEquals("an internal DTD", engine.fcpxmlDoctypeIssue(fcpxml(prolog = "<!DOCTYPE fcpxml [<!ENTITY a \"b\">]>")))
        assertEquals("an internal DTD", engine.fcpxmlDoctypeIssue(fcpxml(prolog = "<!DOCTYPE fcpxml\n\t[\n<!ENTITY a \"b\">\n]>")))
        assertEquals("an external DTD", engine.fcpxmlDoctypeIssue(fcpxml(prolog = "<!DOCTYPE fcpxml SYSTEM \"file:///etc/hosts\">")))
        assertEquals(
            "a bracket inside a quoted public id is not a subset",
            "an external DTD",
            engine.fcpxmlDoctypeIssue(fcpxml(prolog = "<!DOCTYPE fcpxml PUBLIC \"-//Odd[Name//EN\" 'fcpxml.dtd'>")),
        )
    }

    @Test
    fun anInternalSubsetIsRefusedBeforeTheParserSeesIt() {
        val secret = temp.newFile("secret.txt").apply { writeText("corpus-secret") }
        val documents = listOf(
            fcpxml(
                prolog = "<!DOCTYPE fcpxml [<!ENTITY leak SYSTEM \"${secret.toURI()}\">]>",
                clipName = "&leak;",
            ),
            fcpxml(
                prolog = "<!DOCTYPE fcpxml [\n<!ENTITY e0 \"lol\">\n" +
                    (1..9).joinToString("\n") { "<!ENTITY e$it \"${"&e${it - 1};".repeat(10)}\">" } + "\n]>",
                clipName = "&e9;",
            ),
        )

        documents.forEach { xml ->
            val started = System.nanoTime()
            val imported = engine.importFromFcpxml(xml, ::uri)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L

            assertTrue("took ${elapsedMs}ms", elapsedMs < 2_000L)
            assertEquals(emptyList<Any>(), imported.tracks)
            assertEquals(
                listOf("Failed to parse FCPXML: the file declares an internal DTD, which ClearCut doesn't load"),
                imported.warnings,
            )
        }
    }

    @Test
    fun anExternalDtdIsRefusedWithoutFetchingIt() {
        val dtd = File(temp.root, "never-read.dtd")
        val imported = engine.importFromFcpxml(
            fcpxml(prolog = "<!DOCTYPE fcpxml SYSTEM \"${dtd.toURI()}\">"),
            ::uri,
        )

        assertEquals(emptyList<Any>(), imported.tracks)
        assertEquals(
            listOf("Failed to parse FCPXML: the file declares an external DTD, which ClearCut doesn't load"),
            imported.warnings,
        )
    }

    @Test
    fun aBareDoctypeWithAByteOrderMarkStillImports() {
        val imported = engine.importFromFcpxml(
            "\uFEFF" + fcpxml(prolog = "<!-- Final Cut Pro -->\n<!DOCTYPE fcpxml>"),
            ::uri,
        )

        assertEquals(emptyList<String>(), imported.warnings)
        assertEquals("Corpus clip", imported.tracks.single().clips.single().name)
    }

    @Test
    fun malformedOtioAndEditDecisionJsonAreWarnedNotThrown() {
        listOf("", "{", "[]", "{\"OTIO_SCHEMA\": \"Timeline.1\", \"tracks\": 7}", "\u0000\u0001binary").forEach { raw ->
            val otio = engine.importFromOtio(raw, ::uri)
            assertEquals("OTIO <$raw>", emptyList<Any>(), otio.tracks)
            assertTrue("OTIO <$raw> gave no warning", otio.warnings.isNotEmpty())

            val decisions = engine.importFromEditDecisionJson(raw, ::uri)
            assertEquals("edit decisions <$raw>", emptyList<Any>(), decisions.tracks)
            assertTrue("edit decisions <$raw> gave no warning", decisions.warnings.isNotEmpty())
        }
    }

    @Test
    fun aValidLutPaddedPastTheFileCapIsRefused() {
        val cubeText = "LUT_3D_SIZE 2\n" + "0 0.5 1\n".repeat(8)
        val dlText = "0 512 1023 1023\n" + "512 512 512\n".repeat(64)
        val padding = "#" + "x".repeat(LutEngine.MAX_LUT_FILE_BYTES.toInt()) + "\n"
        val cube = File(temp.root, "padded.cube").apply { writeText(cubeText + padding) }
        val dl = File(temp.root, "padded.3dl").apply { writeText(dlText + padding) }
        assertTrue(cube.length() > LutEngine.MAX_LUT_FILE_BYTES)

        assertNull(LutEngine.parseCube(cube))
        assertNull(LutEngine.parse3dl(dl))
        // The same LUTs without the padding are fine.
        cube.writeText(cubeText)
        dl.writeText(dlText)
        assertEquals(2, LutEngine.parseCube(cube)?.size)
        assertEquals(4, LutEngine.parse3dl(dl)?.size)
    }

    @Test
    fun aLutWithMoreRowsThanItsGridIsRefused() {
        val cube = File(temp.root, "extra.cube").apply {
            writeText("LUT_3D_SIZE 2\n" + "0.5 0.5 0.5\n".repeat(9))
        }
        val dl = File(temp.root, "extra.3dl").apply {
            writeText("0 512 1023 2047\n" + "512 512 512\n".repeat(65))
        }

        assertNull(LutEngine.parseCube(cube))
        assertNull(LutEngine.parse3dl(dl))
    }

    @Test
    fun aLutOfJunkRowsIsRefusedAndAValidOneStillParses() {
        val junk = File(temp.root, "junk.cube").apply {
            writeText("LUT_3D_SIZE 2\n" + "red green blue\n".repeat(50_000))
        }
        val valid = File(temp.root, "valid.cube").apply {
            writeText("TITLE \"ok\"\nLUT_3D_SIZE 2\n" + "0 0.5 1\n".repeat(8))
        }

        assertNull(LutEngine.parseCube(junk))
        val lut = requireNotNull(LutEngine.parseCube(valid))
        assertEquals(2, lut.size)
        assertEquals(24, lut.data.size)
        assertEquals(0.5f, lut.data[1])
    }

    private fun fcpxml(prolog: String = "", clipName: String = "Corpus clip"): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        PROLOG
        <fcpxml version="1.11">
          <resources>
            <format id="r0" frameDuration="1/30s" width="1920" height="1080"/>
            <asset id="r1" name="Corpus" src="file:///media/corpus.mp4" duration="2s"/>
          </resources>
          <library><event name="Corpus"><project name="Corpus">
            <sequence format="r0" duration="2s"><spine>
              <asset-clip ref="r1" name="CLIP_NAME" offset="0s" start="0s" duration="2s"/>
            </spine></sequence>
          </project></event></library>
        </fcpxml>
    """.trimIndent().replace("PROLOG", prolog).replace("CLIP_NAME", clipName)

    private fun uri(raw: String): Uri {
        val scheme = raw.substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        return TestUri(raw = raw, schemeValue = scheme, segment = raw.substringAfterLast('/'))
    }
}

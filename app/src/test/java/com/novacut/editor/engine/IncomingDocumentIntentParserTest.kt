package com.novacut.editor.engine

import android.content.Intent
import android.net.TestUri
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingDocumentIntentParserTest {

    @Test
    fun actionViewAcceptsSupportedPluginDocument() {
        val lut = contentUri("looks/TEAL_ORANGE.CUBE")

        val parsed = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_VIEW,
            dataUri = lut,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = null,
            hasReadGrant = false,
            resolveMetadata = {
                IncomingDocumentMetadata(
                    displayName = "TEAL_ORANGE.CUBE",
                    mimeType = "text/plain",
                    sizeBytes = 1024L
                )
            }
        )

        assertParsed(parsed, lut.toString() to IncomingDocumentKind.LUT_CUBE)
        assertEquals("TEAL_ORANGE.CUBE", parsed.single().displayName)
    }

    @Test
    fun actionSendRequiresReadGrantAndRoutesEffectPack() {
        val effectPack = contentUri("grade.ncfx")

        val withoutGrant = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(effectPack),
            clipDataUris = emptyList(),
            intentMimeType = "application/octet-stream",
            hasReadGrant = false,
            resolveMetadata = { metadata("grade.ncfx", "application/octet-stream", 512L) }
        )
        val withGrant = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(effectPack),
            clipDataUris = emptyList(),
            intentMimeType = "application/octet-stream",
            hasReadGrant = true,
            resolveMetadata = { metadata("grade.ncfx", "application/octet-stream", 512L) }
        )

        assertTrue(withoutGrant.isEmpty())
        assertParsed(withGrant, effectPack.toString() to IncomingDocumentKind.EFFECT_PACK)
    }

    @Test
    fun actionSendMultiplePreservesOrderAndDropsDuplicateUris() {
        val template = contentUri("intro.clearcut-template")
        val archive = contentUri("project.clearcut")
        val otio = contentUri("cut.otio")

        val parsed = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_SEND_MULTIPLE,
            dataUri = null,
            streamUris = listOf(template, archive),
            clipDataUris = listOf(archive, otio),
            intentMimeType = "application/octet-stream",
            hasReadGrant = true,
            resolveMetadata = { uri ->
                when (uri.toString()) {
                    template.toString() -> metadata("intro.clearcut-template", "application/octet-stream", 400L)
                    archive.toString() -> metadata("project.clearcut", "application/octet-stream", 2_000L)
                    else -> metadata("cut.otio", "application/json", 2_000L)
                }
            }
        )

        assertParsed(
            parsed,
            template.toString() to IncomingDocumentKind.TEMPLATE,
            archive.toString() to IncomingDocumentKind.PROJECT_ARCHIVE,
            otio.toString() to IncomingDocumentKind.TIMELINE_OTIO
        )
    }

    @Test
    fun missingDisplayNameFallsBackToUriPathAndUppercaseExtension() {
        val descriptor = contentUri("Blur.NCFXD")

        val parsed = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_VIEW,
            dataUri = descriptor,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = "application/json",
            hasReadGrant = false,
            resolveMetadata = { metadata(null, "application/json", 300L) }
        )

        assertParsed(parsed, descriptor.toString() to IncomingDocumentKind.OPENFX_DESCRIPTOR)
        assertEquals("Blur.NCFXD", parsed.single().displayName)
    }

    @Test
    fun rejectsFileUrisMalformedMimeOversizedInputAndUnknownExtension() {
        val validContent = contentUri("timeline.edl")
        val oversized = contentUri("huge.cube")
        val unknown = contentUri("notes.txt")
        val file = testUri(raw = "file:///tmp/effect.ncfx", scheme = "file", lastPathSegment = "effect.ncfx")

        val malformedMime = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(validContent),
            clipDataUris = emptyList(),
            intentMimeType = "*/*",
            hasReadGrant = true,
            resolveMetadata = { metadata("timeline.edl", "*/*", 20L) }
        )
        val fileUri = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(file),
            clipDataUris = emptyList(),
            intentMimeType = "application/octet-stream",
            hasReadGrant = true,
            resolveMetadata = { metadata("effect.ncfx", "application/octet-stream", 20L) }
        )
        val tooLarge = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_VIEW,
            dataUri = oversized,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = "text/plain",
            hasReadGrant = false,
            resolveMetadata = { metadata("huge.cube", "text/plain", IncomingDocumentKind.LUT_CUBE.maxBytes + 1L) }
        )
        val unknownExtension = IncomingDocumentIntentParser.parse(
            action = Intent.ACTION_VIEW,
            dataUri = unknown,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = "text/plain",
            hasReadGrant = false,
            resolveMetadata = { metadata("notes.txt", "text/plain", 20L) }
        )

        assertTrue(malformedMime.isEmpty())
        assertTrue(fileUri.isEmpty())
        assertTrue(tooLarge.isEmpty())
        assertTrue(unknownExtension.isEmpty())
    }

    @Test
    fun classifiesXmlJsonTextZipAndOctetInterchangePayloads() {
        assertEquals(
            IncomingDocumentKind.TIMELINE_FCPXML,
            IncomingDocumentIntentParser.classify("handoff.fcpxml", "application/xml")
        )
        assertEquals(
            IncomingDocumentKind.TIMELINE_OTIO,
            IncomingDocumentIntentParser.classify("handoff.otio", "application/json")
        )
        assertEquals(
            IncomingDocumentKind.TIMELINE_EDL,
            IncomingDocumentIntentParser.classify("handoff.edl", "text/plain")
        )
        assertEquals(
            IncomingDocumentKind.PROJECT_ARCHIVE,
            IncomingDocumentIntentParser.classify("project.zip", "application/zip")
        )
        assertEquals(
            IncomingDocumentKind.STYLE_PACK,
            IncomingDocumentIntentParser.classify("captions.ncstyle", "application/octet-stream")
        )
    }

    private fun metadata(displayName: String?, mimeType: String?, sizeBytes: Long?): IncomingDocumentMetadata {
        return IncomingDocumentMetadata(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }

    private fun contentUri(lastPathSegment: String): Uri {
        return testUri(
            raw = "content://sender/$lastPathSegment",
            scheme = "content",
            lastPathSegment = lastPathSegment
        )
    }

    private fun testUri(raw: String, scheme: String, lastPathSegment: String): Uri {
        return TestUri(raw = raw, schemeValue = scheme, segment = lastPathSegment)
    }

    private fun assertParsed(
        parsed: List<IncomingDocumentItem>,
        vararg expected: Pair<String, IncomingDocumentKind>
    ) {
        assertEquals(expected.toList(), parsed.map { it.uri.toString() to it.kind })
    }
}

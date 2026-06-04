package com.novacut.editor.engine

import android.content.Intent
import android.net.TestUri
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMediaIntentParserTest {

    @Test
    fun actionViewAcceptsSingleContentDataUri() {
        val video = contentUri("clip.mp4")

        val parsed = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_VIEW,
            dataUri = video,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = null,
            hasReadGrant = false,
            resolveMimeType = { "video/mp4" }
        )

        assertParsed(parsed, video.toString() to IncomingMediaKind.VIDEO)
    }

    @Test
    fun actionSendRequiresReadGrantAndRoutesSingleStream() {
        val image = contentUri("still.jpg")

        val withoutGrant = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(image),
            clipDataUris = emptyList(),
            intentMimeType = "image/jpeg",
            hasReadGrant = false,
            resolveMimeType = { "image/jpeg" }
        )
        val withGrant = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(image),
            clipDataUris = emptyList(),
            intentMimeType = "image/jpeg",
            hasReadGrant = true,
            resolveMimeType = { "image/jpeg" }
        )

        assertTrue(withoutGrant.isEmpty())
        assertParsed(withGrant, image.toString() to IncomingMediaKind.IMAGE)
    }

    @Test
    fun actionSendMultiplePreservesOrderAndDropsDuplicateClipDataUris() {
        val video = contentUri("a.mp4")
        val audio = contentUri("b.m4a")
        val image = contentUri("c.png")
        val mimeTypes = mapOf(
            video.toString() to "video/mp4",
            audio.toString() to "audio/mp4",
            image.toString() to "image/png"
        )

        val parsed = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND_MULTIPLE,
            dataUri = null,
            streamUris = listOf(video, audio),
            clipDataUris = listOf(audio, image),
            intentMimeType = "*/*",
            hasReadGrant = true,
            resolveMimeType = { mimeTypes[it.toString()] }
        )

        assertParsed(
            parsed,
            video.toString() to IncomingMediaKind.VIDEO,
            audio.toString() to IncomingMediaKind.AUDIO,
            image.toString() to IncomingMediaKind.IMAGE
        )
    }

    @Test
    fun rejectsFileUrisMalformedMimeAndUnsupportedActions() {
        val content = contentUri("doc.pdf")
        val file = testUri(raw = "file:///tmp/clip.mp4", scheme = "file", lastPathSegment = "clip.mp4")

        val malformedMime = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(content),
            clipDataUris = emptyList(),
            intentMimeType = "application/pdf",
            hasReadGrant = true,
            resolveMimeType = { null }
        )
        val fileUri = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(file),
            clipDataUris = emptyList(),
            intentMimeType = "video/mp4",
            hasReadGrant = true,
            resolveMimeType = { "video/mp4" }
        )
        val unsupportedAction = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_EDIT,
            dataUri = content,
            streamUris = emptyList(),
            clipDataUris = emptyList(),
            intentMimeType = "video/mp4",
            hasReadGrant = true,
            resolveMimeType = { "video/mp4" }
        )

        assertTrue(malformedMime.isEmpty())
        assertTrue(fileUri.isEmpty())
        assertTrue(unsupportedAction.isEmpty())
    }

    @Test
    fun fallsBackToIntentMimeTypeWhenResolverTypeIsMissing() {
        val ogg = contentUri("voice.ogg")

        val parsed = IncomingMediaIntentParser.parse(
            action = Intent.ACTION_SEND,
            dataUri = null,
            streamUris = listOf(ogg),
            clipDataUris = emptyList(),
            intentMimeType = "application/ogg",
            hasReadGrant = true,
            resolveMimeType = { null }
        )

        assertParsed(parsed, ogg.toString() to IncomingMediaKind.AUDIO)
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
        parsed: List<IncomingMediaItem>,
        vararg expected: Pair<String, IncomingMediaKind>
    ) {
        assertEquals(expected.toList(), parsed.map { it.uri.toString() to it.kind })
    }
}

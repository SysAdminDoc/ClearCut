package com.novacut.editor.engine

import com.novacut.editor.R
import com.novacut.editor.ui.editor.V369Delegate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The content-ID pre-check never contacts AcoustID. A `matchedTitle == null`
 * result therefore means "nobody asked", not "your audio is clear" — and the UI
 * used to render it as the latter. These tests keep the two apart.
 */
class ContentIdLookupOutcomeTest {

    private val engine = ContentIdEngine()

    @Test
    fun noApiKeyReportsThatNothingWasLookedUp() {
        val match = runBlocking { engine.analyze(pcm(), apiKey = null) }

        assertEquals(ContentIdEngine.LookupOutcome.NOT_CHECKED_NO_API_KEY, match.lookup)
        assertFalse(match.lookup.wasLookedUp)
        assertNull(match.matchedTitle)
        assertTrue("the local fingerprint is still real work", match.hash.isNotEmpty())
    }

    @Test
    fun anApiKeyAloneDoesNotMakeItACompletedLookup() {
        val match = runBlocking { engine.analyze(pcm(), apiKey = "a-key") }

        assertFalse(
            "AcoustID needs a Chromaprint fingerprint that this build cannot produce",
            engine.isChromaprintAvailable()
        )
        assertEquals(ContentIdEngine.LookupOutcome.NOT_CHECKED_NO_FINGERPRINT_BACKEND, match.lookup)
        assertFalse(match.lookup.wasLookedUp)
    }

    @Test
    fun onlyACompletedLookupMayRenderAsNoMatch() {
        assertEquals(
            R.string.v369_content_id_no_match,
            V369Delegate.contentIdOutcomeMessageRes(ContentIdEngine.LookupOutcome.NO_MATCH)
        )

        val notChecked = listOf(
            ContentIdEngine.LookupOutcome.NOT_CHECKED_NO_API_KEY,
            ContentIdEngine.LookupOutcome.NOT_CHECKED_NO_FINGERPRINT_BACKEND,
        )
        notChecked.forEach { outcome ->
            val res = V369Delegate.contentIdOutcomeMessageRes(outcome)
            assertTrue(
                "$outcome must not reuse the clear-result copy",
                res != R.string.v369_content_id_no_match && res != R.string.v369_content_id_match
            )
        }
        assertEquals(
            "each not-checked reason gets its own explanation",
            notChecked.size,
            notChecked.map { V369Delegate.contentIdOutcomeMessageRes(it) }.toSet().size
        )
    }

    @Test
    fun everyOutcomeHasCopy() {
        ContentIdEngine.LookupOutcome.entries.forEach { outcome ->
            assertTrue(
                "$outcome has no user-visible message",
                V369Delegate.contentIdOutcomeMessageRes(outcome) != 0
            )
        }
    }

    private fun pcm(): ShortArray = ShortArray(8192) { (it % 512).toShort() }
}

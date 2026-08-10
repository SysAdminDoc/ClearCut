package com.novacut.editor.ui.editor

import com.novacut.editor.R
import com.novacut.editor.engine.VideoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seven distinct terminal export failures used to collapse into one generic
 * sentence, in a project whose central claim is that exports do not lie. Each
 * cause must reach the user as its own message with its own remediation.
 */
class ExportFailureCopyTest {

    @Test
    fun everyTerminalCauseHasItsOwnMessage() {
        val specific = VideoEngine.ExportFailureCause.entries
            .filter { it != VideoEngine.ExportFailureCause.UNKNOWN }

        val messages = specific.map { exportFailureCopyFor(it).messageRes }
        assertEquals(
            "each cause needs distinct copy: ${specific.zip(messages)}",
            specific.size,
            messages.toSet().size
        )
        assertTrue(
            "no specific cause may reuse the generic export-failed string",
            messages.none { it == R.string.export_video_failed_message }
        )
    }

    @Test
    fun everyCauseCarriesARemediationLine() {
        VideoEngine.ExportFailureCause.entries.forEach { cause ->
            val copy = exportFailureCopyFor(cause)
            assertTrue("$cause has no message", copy.messageRes != 0)
            assertTrue("$cause has no remediation", copy.remediationRes != 0)
            assertTrue("$cause reuses its message as remediation", copy.messageRes != copy.remediationRes)
        }
    }

    @Test
    fun onlyTheUnknownCauseFallsBackToGenericCopy() {
        assertEquals(
            R.string.export_video_failed_message,
            exportFailureCopyFor(VideoEngine.ExportFailureCause.UNKNOWN).messageRes
        )
        assertEquals(
            "a missing cause is treated as unknown, not as a specific failure",
            exportFailureCopyFor(VideoEngine.ExportFailureCause.UNKNOWN),
            exportFailureCopyFor(null)
        )
    }

    @Test
    fun theCausesTheEngineComputesAreAllRepresented() {
        // The failures named in the audit: transformer error, zero-byte output,
        // failed verification, stall, unsupported standalone audio codec, subtitle
        // burn-in failure, and storage refusal.
        val required = listOf(
            VideoEngine.ExportFailureCause.ENCODER_FAILED,
            VideoEngine.ExportFailureCause.EMPTY_OUTPUT,
            VideoEngine.ExportFailureCause.VERIFICATION_FAILED,
            VideoEngine.ExportFailureCause.STALLED,
            VideoEngine.ExportFailureCause.AUDIO_ENCODE_FAILED,
            VideoEngine.ExportFailureCause.SUBTITLE_BURN_IN_FAILED,
            VideoEngine.ExportFailureCause.STORAGE,
            VideoEngine.ExportFailureCause.GPU_EFFECT_DEGRADED,
        )

        assertTrue(VideoEngine.ExportFailureCause.entries.containsAll(required))
        assertEquals(required.size, required.map { exportFailureCopyFor(it).messageRes }.toSet().size)
    }
}

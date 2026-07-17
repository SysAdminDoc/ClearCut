package com.novacut.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportConfigTargetSizeTest {

    private fun estimatedBytes(config: ExportConfig, durationMs: Long): Long {
        val video = (config.bitrateOverride ?: config.videoBitrate).toLong() * durationMs / 8000L
        val audio = config.audioBitrate.toLong() * durationMs / 8000L
        return video + audio
    }

    @Test
    fun `short timeline resolves bitrate that honors the target`() {
        val durationMs = 30_000L
        val target = 8L * 1024L * 1024L
        val resolved = ExportConfig(targetSizeBytes = target).resolveTargetSize(durationMs)

        assertTrue(estimatedBytes(resolved, durationMs) <= target)
    }

    @Test
    fun `long timeline steps audio down instead of blowing past the target`() {
        // 3 minutes against Discord's 8 MB cap: the old 500 kbps video floor
        // plus untouched 256 kbps audio produced a ~17 MB file — more than
        // double the promise.
        val durationMs = 3L * 60L * 1000L
        val target = 8L * 1024L * 1024L
        val resolved = ExportConfig(
            targetSizeBytes = target,
            audioBitrate = 256_000
        ).resolveTargetSize(durationMs)

        assertTrue(
            "resolved ${estimatedBytes(resolved, durationMs)} bytes must fit $target",
            estimatedBytes(resolved, durationMs) <= target
        )
        assertTrue(resolved.audioBitrate >= ExportConfig.MIN_TARGET_AUDIO_BITRATE)
        assertTrue((resolved.bitrateOverride ?: 0) >= ExportConfig.MIN_TARGET_VIDEO_BITRATE)
    }

    @Test
    fun `no target size leaves the config untouched`() {
        val config = ExportConfig()
        assertEquals(config, config.resolveTargetSize(60_000L))
    }
}

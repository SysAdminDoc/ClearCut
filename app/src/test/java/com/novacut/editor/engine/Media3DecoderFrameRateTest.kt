package com.novacut.editor.engine

import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Samsung A-series, OPPO, Realme, Xiaomi and vivo decoders report `KEY_FRAME_RATE = 0`.
 * Media3 1.11.0 passed that straight to `Format.Builder.setFrameRate`, which rejects it,
 * so every export on those phones died at the first decoded frame (androidx/media#3399).
 * 1.11.1 normalizes it. This pins the dependency above the broken release: going back
 * to 1.11.0 throws here instead of on users' phones.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class Media3DecoderFrameRateTest {

    @Test
    fun aDecoderReportingZeroFramesPerSecondStillYieldsAFormat() {
        val floatZero = decoderOutput().apply { setFloat(MediaFormat.KEY_FRAME_RATE, 0f) }
        val intZero = decoderOutput().apply { setInteger(MediaFormat.KEY_FRAME_RATE, 0) }

        assertEquals(Format.NO_VALUE.toFloat(), MediaFormatUtil.createFormatFromMediaFormat(floatZero).frameRate)
        assertEquals(Format.NO_VALUE.toFloat(), MediaFormatUtil.createFormatFromMediaFormat(intZero).frameRate)
    }

    @Test
    fun aRealDecoderFrameRateIsKept() {
        val ntsc = decoderOutput().apply { setFloat(MediaFormat.KEY_FRAME_RATE, 29.97f) }

        assertEquals(29.97f, MediaFormatUtil.createFormatFromMediaFormat(ntsc).frameRate, 0.0001f)
    }

    private fun decoderOutput(): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
}

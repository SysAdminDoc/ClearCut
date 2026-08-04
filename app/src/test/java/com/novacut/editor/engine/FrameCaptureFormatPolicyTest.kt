package com.novacut.editor.engine

import android.graphics.Bitmap
import com.novacut.editor.model.FrameCaptureFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCaptureFormatPolicyTest {

    @Test
    fun bitmapEncoding_matchesRequestedCaptureFormat() {
        assertEquals(Bitmap.CompressFormat.PNG, frameCaptureBitmapFormat(FrameCaptureFormat.PNG))
        assertEquals(Bitmap.CompressFormat.JPEG, frameCaptureBitmapFormat(FrameCaptureFormat.JPEG))
    }

    @Test
    fun pngUsesLosslessQualityAndJpegUsesBoundedQuality() {
        assertEquals(100, frameCaptureQuality(FrameCaptureFormat.PNG))
        assertEquals(95, frameCaptureQuality(FrameCaptureFormat.JPEG))
    }
}

package com.novacut.editor.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.TextOverlay
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionRendererRobolectricTest {

    @Test
    fun installedFallbacksExposeGlyphsForCommittedFixtures() {
        mapOf(
            "\u4F60\u597D\u4E16\u754C" to "Noto Sans CJK SC",
            "\u65E5\u672C\u8A9E\u30C6\u30B9\u30C8" to "Noto Sans CJK JP",
            "\uC548\uB155\uD558\uC138\uC694" to "Noto Sans CJK KR",
            "\u0645\u0631\u062D\u0628\u0627" to "Noto Sans Arabic",
        ).forEach { (text, family) ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(family, Typeface.NORMAL)
                textSize = 48f
            }
            text.codePoints().forEach { codePoint ->
                assertTrue(
                    "$family is missing U+${codePoint.toString(16).uppercase()}",
                    paint.hasGlyph(String(Character.toChars(codePoint))),
                )
            }
        }
    }

    @Test
    fun strokedBitmapPathProducesInkForCjkAndRtlMixedText() {
        listOf(
            "\u4F60\u597D\u4E16\u754C",
            "\u0645\u0631\u062D\u0628\u0627 ClearCut 2026",
        ).forEach { fixture ->
            val renderer = StrokedTextBitmapOverlay(
                overlay = TextOverlay(
                    text = fixture,
                    fontFamily = "LatinOnlyDisplay",
                    fontSize = 64f,
                    strokeWidth = 3f,
                ),
                relStartMs = 0L,
                relEndMs = 1_000L,
                canvasDim = 640,
            )
            val bitmap = renderer.getBitmap(500_000L)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(bitmap.width > 2 && bitmap.height > 2)
            assertTrue("No rendered ink for $fixture", pixels.count { Color.alpha(it) > 0 } > 20)
            renderer.release()
        }
    }
}

package com.novacut.editor.engine

import android.util.Log
import com.novacut.editor.model.Caption
import com.novacut.editor.model.SubtitleFormat
import java.io.File

/**
 * Exports captions to subtitle files in SRT, VTT, or ASS format.
 */
object SubtitleExporter {

    fun export(captions: List<Caption>, format: SubtitleFormat, outputFile: File): Boolean {
        if (captions.isEmpty()) return false

        // Filter out invalid captions (negative times, zero/negative duration, blank text)
        val sorted = captions
            .filter { it.startTimeMs >= 0 && it.endTimeMs > it.startTimeMs && it.text.isNotBlank() }
            .sortedBy { it.startTimeMs }
        if (sorted.isEmpty()) return false
        val content = when (format) {
            SubtitleFormat.SRT -> generateSrt(sorted)
            SubtitleFormat.VTT -> generateVtt(sorted)
            SubtitleFormat.ASS -> generateAss(sorted)
        }

        return try {
            writeUtf8TextAtomically(outputFile, content)
            true
        } catch (e: Exception) {
            Log.e("SubtitleExporter", "Export failed", e)
            false
        }
    }

    private fun generateSrt(captions: List<Caption>): String {
        return buildString {
            captions.forEachIndexed { index, caption ->
                appendLine("${index + 1}")
                appendLine("${formatSrtTime(caption.startTimeMs)} --> ${formatSrtTime(caption.endTimeMs)}")
                appendLine(sanitizeSrtText(caption.text))
                appendLine()
            }
        }
    }

    private fun generateVtt(captions: List<Caption>): String {
        return buildString {
            appendLine("WEBVTT")
            appendLine()
            captions.forEachIndexed { index, caption ->
                appendLine("${index + 1}")
                appendLine("${formatVttTime(caption.startTimeMs)} --> ${formatVttTime(caption.endTimeMs)}")

                // Word-level cues if available.
                // Filter to words whose startTimeMs falls within the caption's own range —
                // out-of-range word timestamps are rejected by VTT parsers and cause the
                // entire cue to be silently dropped.
                if (caption.words.isNotEmpty()) {
                    val validWords = caption.words.filter {
                        it.startTimeMs in caption.startTimeMs..caption.endTimeMs
                    }
                    val wordText = if (validWords.isNotEmpty()) {
                        validWords.joinToString(" ") { word ->
                            "<${formatVttTime(word.startTimeMs)}><c>${escapeVttText(word.text)}</c>"
                        }
                    } else {
                        escapeVttText(caption.text)
                    }
                    appendLine(wordText)
                } else {
                    appendLine(escapeVttText(caption.text))
                }
                appendLine()
            }
        }
    }

    private fun generateAss(captions: List<Caption>): String {
        val styles = linkedMapOf<AssStyle, String>()
        captions.forEach { caption ->
            val style = AssStyle.from(caption)
            styles.getOrPut(style) { "Caption${styles.size + 1}" }
        }
        return buildString {
            appendLine("[Script Info]")
            appendLine("Title: ClearCut Export")
            appendLine("ScriptType: v4.00+")
            appendLine("WrapStyle: 0")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine("PlayResX: 1920")
            appendLine("PlayResY: 1080")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            styles.forEach { (style, name) ->
                appendLine(style.toAssLine(name))
            }
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            captions.forEach { caption ->
                val start = formatAssTime(caption.startTimeMs)
                val end = formatAssTime(caption.endTimeMs)
                val text = escapeAssText(caption.text)
                val styleName = styles.getValue(AssStyle.from(caption))
                appendLine("Dialogue: 0,$start,$end,$styleName,,0,0,0,,$text")
            }
        }
    }

    private data class AssStyle(
        val fontName: String,
        val fontSize: Int,
        val primaryColor: String,
        val secondaryColor: String,
        val outlineColor: String,
        val backgroundColor: String,
        val outlineWidth: Float,
        val shadowDepth: Int,
        val marginV: Int,
    ) {
        fun toAssLine(name: String): String =
            "Style: $name,$fontName,$fontSize,$primaryColor,$secondaryColor,$outlineColor," +
                "$backgroundColor,-1,0,0,0,100,100,0,0,1,$outlineWidth,$shadowDepth,2,10,10,$marginV,1"

        companion object {
            fun from(caption: Caption): AssStyle {
                val style = caption.style
                val requested = style.fontFamily
                    .removePrefix(FontRegistry.CUSTOM_PREFIX)
                    .substringBeforeLast('.')
                    .replace(Regex("[,_\\r\\r\n]+"), " ")
                    .trim()
                    .ifBlank { "sans-serif" }
                return AssStyle(
                    fontName = CaptionFontFallbackPolicy.familyNameForText(requested, caption.text),
                    fontSize = style.fontSize.toInt().coerceIn(1, 512),
                    primaryColor = assColor(style.color),
                    secondaryColor = assColor(style.highlightColor),
                    outlineColor = assColor(style.outlineColor),
                    backgroundColor = assColor(style.backgroundColor),
                    outlineWidth = if (style.outline) style.outlineWidth.coerceIn(0f, 20f) else 0f,
                    shadowDepth = if (style.shadow) 2 else 0,
                    marginV = ((1f - style.positionY.coerceIn(0f, 1f)) * 1080f).toInt().coerceAtLeast(10),
                )
            }
        }
    }

    internal fun assColor(argb: Long): String {
        val value = argb.toInt()
        val alpha = 0xFF - ((value ushr 24) and 0xFF)
        val red = (value ushr 16) and 0xFF
        val green = (value ushr 8) and 0xFF
        val blue = value and 0xFF
        return "&H%02X%02X%02X%02X".format(alpha, blue, green, red)
    }

    private fun sanitizeSrtText(raw: String): String {
        return raw
            .replace("\r\n", "\r\n")
            .replace('\r', '\r\n')
            .lines()
            .joinToString("\r\n") { line -> line.replace("-->", "->") }
    }

    private fun escapeVttText(raw: String): String {
        return raw
            .replace("\r\n", "\r\n")
            .replace('\r', '\r\n')
            .replace("-->", "->")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeAssText(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("\r\n", "\r\n")
            .replace('\r', '\r\n')
            .replace("\r\n", "\\N")
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    private fun formatAssTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val centis = (ms % 1000) / 10
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
    }
}

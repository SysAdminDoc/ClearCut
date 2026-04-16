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

        // Filter out invalid captions (negative times, zero/negative duration)
        val sorted = captions
            .filter { it.startTimeMs >= 0 && it.endTimeMs > it.startTimeMs }
            .sortedBy { it.startTimeMs }
        if (sorted.isEmpty()) return false
        val content = when (format) {
            SubtitleFormat.SRT -> generateSrt(sorted)
            SubtitleFormat.VTT -> generateVtt(sorted)
            SubtitleFormat.ASS -> generateAss(sorted)
        }

        return try {
            outputFile.writeText(content, Charsets.UTF_8)
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
                appendLine(caption.text)
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

                // Word-level cues if available
                if (caption.words.isNotEmpty()) {
                    val wordText = caption.words.joinToString(" ") { word ->
                        "<${formatVttTime(word.startTimeMs)}><c>${word.text}</c>"
                    }
                    appendLine(wordText)
                } else {
                    appendLine(caption.text)
                }
                appendLine()
            }
        }
    }

    private fun generateAss(captions: List<Caption>): String {
        return buildString {
            appendLine("[Script Info]")
            appendLine("Title: NovaCut Export")
            appendLine("ScriptType: v4.00+")
            appendLine("WrapStyle: 0")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine("PlayResX: 1920")
            appendLine("PlayResY: 1080")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,1,2,10,10,30,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            captions.forEach { caption ->
                val start = formatAssTime(caption.startTimeMs)
                val end = formatAssTime(caption.endTimeMs)
                val text = caption.text.replace("\n", "\\N")
                appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,$text")
            }
        }
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

package com.novacut.editor.engine

import com.novacut.editor.model.Caption
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.PriorityQueue
import java.util.regex.Pattern

/**
 * Bounded, preview-first parser for the two caption formats ClearCut exports.
 *
 * The parser deliberately does not touch project state. Callers can show the
 * returned analysis, then use [mapToClip] only after the user accepts it.
 */
object CaptionImportEngine {
    const val MAX_BYTES = 10_000_000L
    const val MAX_CUES = 10_000

    enum class Format(val displayName: String) {
        SRT("SRT"),
        WEBVTT("WebVTT"),
    }

    enum class Encoding(val displayName: String) {
        UTF_8("UTF-8"),
        UTF_16_LE("UTF-16LE"),
        UTF_16_BE("UTF-16BE"),
    }

    enum class Failure(val displayName: String) {
        OVERSIZED("file is too large"),
        EMPTY("file is empty"),
        BINARY("binary content is not a caption file"),
        UNSUPPORTED_ENCODING("encoding is unsupported or malformed"),
        INVALID_HEADER("caption header is invalid"),
        INVALID_CUES("one or more cues are invalid"),
        EXCESSIVE_CUES("file contains too many cues"),
        NO_CUES("file contains no cues"),
    }

    data class Cue(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,
    )

    data class Preview(
        val format: Format,
        val sourceBytes: Long,
        val encoding: Encoding? = null,
        val cues: List<Cue> = emptyList(),
        val invalidCueCount: Int = 0,
        val overlapCount: Int = 0,
        val durationMs: Long = 0L,
        val language: String = "und",
        val languageConfidence: Float = 0f,
        val warnings: List<String> = emptyList(),
        val failure: Failure? = null,
    ) {
        val isValid: Boolean get() = failure == null && cues.isNotEmpty()
    }

    data class ClipMapping(
        val captions: List<Caption>,
        val clippedCueCount: Int,
        val skippedCueCount: Int,
    )

    fun formatFor(kind: IncomingDocumentKind): Format? = when (kind) {
        IncomingDocumentKind.CAPTION_SRT -> Format.SRT
        IncomingDocumentKind.CAPTION_WEBVTT -> Format.WEBVTT
        else -> null
    }

    fun analyze(bytes: ByteArray, format: Format): Preview {
        val sourceBytes = bytes.size.toLong()
        if (sourceBytes > MAX_BYTES) {
            return Preview(format = format, sourceBytes = sourceBytes, failure = Failure.OVERSIZED)
        }
        if (bytes.isEmpty()) {
            return Preview(format = format, sourceBytes = 0L, failure = Failure.EMPTY)
        }

        val decoded = decode(bytes)
            ?: return Preview(
                format = format,
                sourceBytes = sourceBytes,
                failure = Failure.UNSUPPORTED_ENCODING,
            )
        if (looksBinary(decoded.text)) {
            return Preview(
                format = format,
                sourceBytes = sourceBytes,
                encoding = decoded.encoding,
                failure = Failure.BINARY,
            )
        }

        val normalized = decoded.text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val language = detectLanguage(normalized)
        if (normalized.isBlank()) {
            return Preview(
                format = format,
                sourceBytes = sourceBytes,
                encoding = decoded.encoding,
                language = language.code,
                languageConfidence = language.confidence,
                failure = Failure.EMPTY,
            )
        }

        val parsed = when (format) {
            Format.SRT -> parseSrt(normalized)
            Format.WEBVTT -> parseWebVtt(normalized)
        }
        if (parsed.headerInvalid) {
            return Preview(
                format = format,
                sourceBytes = sourceBytes,
                encoding = decoded.encoding,
                language = language.code,
                languageConfidence = language.confidence,
                failure = Failure.INVALID_HEADER,
            )
        }
        val cues = parsed.cues.sortedWith(compareBy<Cue> { it.startTimeMs }.thenBy { it.endTimeMs })
        val overlapCount = countOverlaps(cues)
        val failure = when {
            parsed.observedCueCount > MAX_CUES || cues.size > MAX_CUES -> Failure.EXCESSIVE_CUES
            parsed.invalidCueCount > 0 -> Failure.INVALID_CUES
            cues.isEmpty() -> Failure.NO_CUES
            else -> null
        }
        val warnings = buildList {
            if (parsed.invalidCueCount > 0) {
                add("${parsed.invalidCueCount} invalid cue(s) were found; nothing will be imported.")
            }
            if (overlapCount > 0) {
                add("$overlapCount cue overlap(s) detected; overlaps will be preserved.")
            }
            if (language.confidence < 0.5f) {
                add("Language is only a low-confidence local guess; no network detection is used.")
            }
        }
        return Preview(
            format = format,
            sourceBytes = sourceBytes,
            encoding = decoded.encoding,
            cues = cues,
            invalidCueCount = parsed.invalidCueCount,
            overlapCount = overlapCount,
            durationMs = cues.maxOfOrNull { it.endTimeMs } ?: 0L,
            language = language.code,
            languageConfidence = language.confidence,
            warnings = warnings,
            failure = failure,
        )
    }

    /**
     * Convert file/timeline timestamps into the selected clip's local clock.
     * The source formats are treated as project-time media: [targetOffsetMs]
     * is subtracted, then cues are clipped to the target clip. Cues wholly
     * outside the clip are skipped and reported to the caller.
     */
    fun mapToClip(
        preview: Preview,
        clipDurationMs: Long,
        targetOffsetMs: Long,
    ): ClipMapping {
        if (!preview.isValid || clipDurationMs <= 0L) {
            return ClipMapping(emptyList(), clippedCueCount = 0, skippedCueCount = preview.cues.size)
        }
        var clipped = 0
        var skipped = 0
        val captions = buildList {
            preview.cues.forEach { cue ->
                val localStart = cue.startTimeMs - targetOffsetMs
                val localEnd = cue.endTimeMs - targetOffsetMs
                if (localEnd <= 0L || localStart >= clipDurationMs) {
                    skipped++
                    return@forEach
                }
                val start = localStart.coerceIn(0L, clipDurationMs)
                val end = localEnd.coerceIn(0L, clipDurationMs)
                if (start != localStart || end != localEnd) clipped++
                if (end <= start || cue.text.isBlank()) {
                    skipped++
                    return@forEach
                }
                add(Caption(text = cue.text, startTimeMs = start, endTimeMs = end))
            }
        }.sortedWith(compareBy<Caption> { it.startTimeMs }.thenBy { it.endTimeMs })
        return ClipMapping(captions, clippedCueCount = clipped, skippedCueCount = skipped)
    }

    private data class DecodedText(val text: String, val encoding: Encoding)

    private data class ParsedCues(
        val cues: List<Cue>,
        val invalidCueCount: Int,
        val observedCueCount: Int,
        val headerInvalid: Boolean = false,
    )

    private data class LanguageGuess(val code: String, val confidence: Float)

    private fun decode(bytes: ByteArray): DecodedText? {
        val (encoding, body) = when {
            bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) ->
                Encoding.UTF_8 to bytes.copyOfRange(3, bytes.size)
            bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ->
                Encoding.UTF_16_LE to bytes.copyOfRange(2, bytes.size)
            bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ->
                Encoding.UTF_16_BE to bytes.copyOfRange(2, bytes.size)
            else -> Encoding.UTF_8 to bytes
        }
        return try {
            val charset = when (encoding) {
                Encoding.UTF_8 -> Charsets.UTF_8
                Encoding.UTF_16_LE -> Charsets.UTF_16LE
                Encoding.UTF_16_BE -> Charsets.UTF_16BE
            }
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            DecodedText(decoder.decode(ByteBuffer.wrap(body)).toString(), encoding)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun looksBinary(text: String): Boolean {
        return text.any { char ->
            char == '\u0000' || (char.code < 0x09) || (char.code in 0x0E..0x1F)
        }
    }

    private fun parseSrt(text: String): ParsedCues {
        val cues = mutableListOf<Cue>()
        var invalid = 0
        var observed = 0
        splitBlocks(text).forEach { block ->
            val lines = block.lines().map(String::trimEnd).filter { it.isNotBlank() }
            if (lines.isEmpty()) return@forEach
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex !in 0..1) {
                invalid++
                observed++
                return@forEach
            }
            observed++
            val timing = parseTiming(lines[timingIndex], Format.SRT)
            val captionText = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (timing == null || captionText.isBlank()) {
                invalid++
                return@forEach
            }
            cues += Cue(timing.first, timing.second, cleanCaptionText(captionText))
        }
        return ParsedCues(cues, invalid, observed)
    }

    private fun parseWebVtt(text: String): ParsedCues {
        val blocks = splitBlocks(text)
        val first = blocks.firstOrNull()?.lineSequence()?.firstOrNull()?.trim()
        if (first == null || !first.startsWith("WEBVTT", ignoreCase = true)) {
            return ParsedCues(emptyList(), invalidCueCount = 0, observedCueCount = 0, headerInvalid = true)
        }
        val cues = mutableListOf<Cue>()
        var invalid = 0
        var observed = 0
        blocks.drop(1).forEach { block ->
            val lines = block.lines().map(String::trimEnd).filter { it.isNotBlank() }
            if (lines.isEmpty()) return@forEach
            val firstToken = lines.first().trim().uppercase()
            if (firstToken == "NOTE" || firstToken.startsWith("NOTE ") ||
                firstToken == "STYLE" || firstToken == "REGION"
            ) {
                return@forEach
            }
            val timingIndex = when {
                lines.first().contains("-->") -> 0
                lines.size > 1 && lines[1].contains("-->") -> 1
                else -> -1
            }
            if (timingIndex < 0) {
                // Header metadata belongs in the first WEBVTT block. A later
                // unrecognised block is malformed rather than silently ignored.
                invalid++
                observed++
                return@forEach
            }
            observed++
            val timing = parseTiming(lines[timingIndex], Format.WEBVTT)
            val captionText = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (timing == null || captionText.isBlank()) {
                invalid++
                return@forEach
            }
            cues += Cue(timing.first, timing.second, cleanCaptionText(captionText))
        }
        return ParsedCues(cues, invalid, observed)
    }

    private fun parseTiming(line: String, format: Format): Pair<Long, Long>? {
        val sides = line.split("-->", limit = 2)
        if (sides.size != 2) return null
        val start = parseTimestamp(sides[0].trim(), format) ?: return null
        val endToken = sides[1].trim().split(Regex("\\s+"), limit = 2).first()
        val end = parseTimestamp(endToken, format) ?: return null
        if (start < 0L || end <= start) return null
        return start to end
    }

    private fun parseTimestamp(raw: String, format: Format): Long? {
        val pattern = when (format) {
            Format.SRT -> SRT_TIMESTAMP
            Format.WEBVTT -> WEBVTT_TIMESTAMP
        }
        val match = pattern.matcher(raw)
        if (!match.matches()) return null
        val (hours, minutes, seconds, millis) = when (format) {
            Format.SRT -> {
                val groups = (1..4).map { match.group(it)?.toLongOrNull() ?: return null }
                groups
            }
            Format.WEBVTT -> listOf(
                match.group(1)?.toLongOrNull() ?: 0L,
                match.group(2)?.toLongOrNull() ?: return null,
                match.group(3)?.toLongOrNull() ?: return null,
                match.group(4)?.toLongOrNull() ?: return null,
            )
        }
        if (minutes !in 0L..59L || seconds !in 0L..59L || millis !in 0L..999L) return null
        return (((hours * 60L + minutes) * 60L + seconds) * 1000L + millis)
    }

    private fun cleanCaptionText(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "")
            .replace("\\N", "\n")
            .trim()
    }

    private fun splitBlocks(text: String): List<String> {
        return text.split(Regex("\\n[ \\t]*\\n+"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun countOverlaps(cues: List<Cue>): Int {
        if (cues.size < 2) return 0
        val activeEnds = PriorityQueue<Long>()
        var overlaps = 0
        cues.forEach { cue ->
            while (activeEnds.isNotEmpty() && (activeEnds.peek()?.let { it <= cue.startTimeMs } == true)) {
                activeEnds.poll()
            }
            overlaps += activeEnds.size
            activeEnds += cue.endTimeMs
        }
        return overlaps
    }

    private fun detectLanguage(text: String): LanguageGuess {
        val explicitMatcher = LANGUAGE_PATTERN.matcher(text)
        if (explicitMatcher.find()) {
            return LanguageGuess(explicitMatcher.group(1)?.lowercase() ?: "und", 0.95f)
        }

        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return LanguageGuess("und", 0f)
        if (letters.any { it in '\u0600'..'\u06FF' }) return LanguageGuess("ar", 0.8f)
        if (letters.any { it in '\u0400'..'\u04FF' }) return LanguageGuess("und-Cyrl", 0.65f)
        if (letters.any { it in '\u3040'..'\u30FF' }) return LanguageGuess("ja", 0.8f)
        if (letters.any { it in '\u4E00'..'\u9FFF' }) return LanguageGuess("zh", 0.65f)
        if (letters.any { it in '\u0900'..'\u097F' }) return LanguageGuess("hi", 0.8f)

        val words = text.lowercase()
            .split(Regex("[^a-z]+"))
            .filter(String::isNotBlank)
        val commonEnglish = words.count { it in ENGLISH_MARKERS }
        return if (commonEnglish > 0) {
            LanguageGuess("en", (0.35f + commonEnglish / words.size.coerceAtLeast(1).toFloat()).coerceAtMost(0.8f))
        } else {
            LanguageGuess("und", 0.2f)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        return size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }

    private val SRT_TIMESTAMP = Pattern.compile("^(\\d{1,5}):(\\d{2}):(\\d{2}),(\\d{3})$")
    private val WEBVTT_TIMESTAMP = Pattern.compile("^(?:(\\d{1,5}):)?(\\d{2}):(\\d{2})\\.(\\d{3})$")
    private val LANGUAGE_PATTERN = Pattern.compile(
        "(?im)^\\s*(?:language|lang)\\s*[:=]\\s*([A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?)\\s*$"
    )
    private val ENGLISH_MARKERS = setOf(
        "a", "an", "and", "are", "but", "for", "have", "i", "in", "is", "it",
        "of", "on", "that", "the", "this", "to", "we", "with", "you"
    )
}

package com.novacut.editor.engine

import kotlin.math.roundToLong

/**
 * Pure parser for pasted / imported cut lists and marker lists.
 *
 * Each non-blank, non-comment line describes either a single timecode (a
 * marker) or a start/end range (a cut), optionally followed by a label:
 *
 * ```
 * 0:10 - 0:15  Intro trim        # colon minute:second range with label
 * 00:01:23.500 Chapter 2         # hour:minute:second.frac marker
 * 12.5 to 18   B-roll            # bare seconds (dot fractional) range
 * 90, 95, Outro                  # comma-separated range + label
 * # this whole line is a comment and is ignored
 * ```
 *
 * Supported timecode forms:
 * - `H:MM:SS(.mmm)` and `M:SS(.mmm)` — colon separated, dot fractional seconds.
 * - bare seconds `SS(.mmm)` (e.g. `12`, `12.5`).
 *
 * Range separators: `-`, en/em dash, `..`, `to`, or `,`. The remainder of the
 * line after the timecode(s) is the (optional) label. Blank lines and lines
 * beginning with `#` are skipped without error. Every other malformed line is
 * reported as a [ParseError] carrying the 1-based line number so the UI can
 * point the user at the exact row to fix — nothing is applied for an error row.
 *
 * The parser is intentionally free of Android dependencies so it is covered by
 * fast JVM unit tests.
 */
object CutListParser {

    /**
     * One parsed row. [endMs] is null for a point marker and non-null for a cut
     * range (guaranteed `endMs > startMs`).
     */
    data class Entry(
        val startMs: Long,
        val endMs: Long?,
        val label: String,
        val lineNumber: Int
    ) {
        val isRange: Boolean get() = endMs != null
    }

    data class ParseError(
        val lineNumber: Int,
        val text: String,
        val message: String
    )

    data class Result(
        val entries: List<Entry>,
        val errors: List<ParseError>
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
        val isEmpty: Boolean get() = entries.isEmpty() && errors.isEmpty()
    }

    fun parse(input: String): Result {
        val entries = mutableListOf<Entry>()
        val errors = mutableListOf<ParseError>()
        input.split('\n').forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            when (val parsed = parseLine(line, lineNumber)) {
                is Parsed.Ok -> entries += parsed.entry
                is Parsed.Err -> errors += ParseError(lineNumber, line, parsed.message)
            }
        }
        return Result(entries, errors)
    }

    private sealed interface Parsed {
        data class Ok(val entry: Entry) : Parsed
        data class Err(val message: String) : Parsed
    }

    private fun parseLine(line: String, lineNumber: Int): Parsed {
        val startMatch = LEADING_TIME.find(line)
            ?: return Parsed.Err("No timecode found — expected something like 0:10 or 1:23.5")
        val startMs = parseTimecode(startMatch.value)
            ?: return Parsed.Err("Invalid start timecode '${startMatch.value}'")

        var rest = line.substring(startMatch.range.last + 1)

        val sepMatch = SEPARATOR.find(rest)
        if (sepMatch != null) {
            val afterSep = rest.substring(sepMatch.range.last + 1)
            val endMatch = LEADING_TIME.find(afterSep)
                ?: return Parsed.Err("Range separator without an end timecode")
            val endMs = parseTimecode(endMatch.value)
                ?: return Parsed.Err("Invalid end timecode '${endMatch.value}'")
            if (endMs <= startMs) {
                return Parsed.Err("End timecode must be after the start")
            }
            val label = cleanLabel(afterSep.substring(endMatch.range.last + 1))
            return Parsed.Ok(Entry(startMs, endMs, label, lineNumber))
        }

        val label = cleanLabel(rest)
        return Parsed.Ok(Entry(startMs, null, label, lineNumber))
    }

    private fun cleanLabel(raw: String): String =
        raw.trim().trim { it == ',' || it == '-' || it == ':' }.trim()

    /**
     * Parse a single timecode token to milliseconds, or null if malformed.
     * Public so the marker/cut apply path and tests can reuse it.
     */
    fun parseTimecode(token: String): Long? {
        val t = token.trim()
        if (t.isEmpty()) return null
        if (':' in t) {
            val parts = t.split(':')
            if (parts.size < 2 || parts.size > 3) return null
            val seconds = parts.last().toDoubleOrNull() ?: return null
            if (seconds < 0.0 || seconds >= 60.0) return null
            val leading = parts.dropLast(1).map { it.toIntOrNull() ?: return null }
            if (leading.any { it < 0 }) return null
            var totalMs = (seconds * 1000.0).roundToLong()
            when (leading.size) {
                1 -> totalMs += leading[0] * 60_000L
                2 -> {
                    val hours = leading[0]
                    val minutes = leading[1]
                    if (minutes >= 60) return null
                    totalMs += minutes * 60_000L + hours * 3_600_000L
                }
            }
            return totalMs
        }
        val secs = t.toDoubleOrNull() ?: return null
        if (secs < 0.0 || !secs.isFinite()) return null
        return (secs * 1000.0).roundToLong()
    }

    // A timecode: digits, optional :parts, optional .fraction. Bounded so it
    // doesn't greedily swallow a trailing label word.
    private val LEADING_TIME = Regex("""^[0-9]+(?::[0-9]+)*(?:\.[0-9]+)?""")

    // Anchored at the start of the post-timecode remainder. `to` needs a word
    // boundary so a label like "total" isn't mistaken for a separator.
    private val SEPARATOR = Regex("""^\s*(?:-|–|—|\.\.|to\b|,)\s*""", RegexOption.IGNORE_CASE)
}

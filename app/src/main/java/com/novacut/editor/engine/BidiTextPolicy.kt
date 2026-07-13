package com.novacut.editor.engine

/**
 * R5.4b — Bidirectional text policy for overlay rendering.
 *
 * ClearCut's text-overlay path (`StrokedTextBitmapOverlay`, `ExportTextOverlay`)
 * draws Canvas text directly, which gives correct glyph shaping for Arabic
 * / Hebrew / Persian / Urdu / Yiddish at the typeface level but does **not**
 * apply Unicode Bidi reordering before draw. That means a mixed-direction
 * caption like `"Hello مرحبا"` can render with the Arabic run misordered,
 * and pure-RTL overlays anchor their alignment from the wrong side of the
 * frame.
 *
 * This policy is the pure-Kotlin half of the fix:
 *  - [classify] inspects a string and returns the dominant text direction
 *    (`LTR`, `RTL`, or `MIXED`). The classifier uses the same Unicode
 *    bidi-class buckets `android.text.BidiFormatter` uses internally, but
 *    without an Android runtime so the engine layer can use it.
 *  - [recommendAlignment] maps a direction onto a default
 *    [PreferredAlignment]. The overlay renderer applies this when the
 *    user hasn't pinned a specific alignment.
 *
 * The actual `BidiFormatter.unicodeWrap(...)` call still has to happen on
 * the Android side (it injects U+200E / U+200F marks). This file lets the
 * engine layer decide *whether* to wrap; the Android side does the wrap.
 *
 * Reference: https://developer.android.com/training/basics/supporting-devices/languages#BidirectionalText
 */
object BidiTextPolicy {

    enum class Direction {
        /** All strong characters are left-to-right (or string has no strong characters). */
        LTR,

        /** All strong characters are right-to-left. */
        RTL,

        /** Strong characters of both directions appear in the string. */
        MIXED,
    }

    /**
     * Suggested horizontal alignment for the overlay frame when the user
     * hasn't explicitly pinned one. RTL strings anchor from the right
     * instead of the left so the cursor caret + visual flow match the
     * user's reading direction.
     */
    enum class PreferredAlignment { START, END }

    /**
     * Classify the bidi direction of [text].
     *
     * The decision uses the first-strong heuristic the Unicode bidi
     * algorithm uses for paragraph direction (the "P2" rule from UAX #9):
     * the direction of the first strong character defines the paragraph
     * direction. We also scan for any opposing strong character so we can
     * distinguish [Direction.MIXED] from a pure single-direction string.
     *
     * Empty / whitespace-only / no-strong-characters strings classify as
     * [Direction.LTR] — that's the safe default for label text like
     * timestamps or numbers.
     */
    fun classify(text: String): Direction {
        if (text.isEmpty()) return Direction.LTR
        var sawLtr = false
        var sawRtl = false
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            when (strongDirection(codePoint)) {
                Direction.LTR -> sawLtr = true
                Direction.RTL -> sawRtl = true
                Direction.MIXED -> Unit
            }
            // Short-circuit as soon as we have both — no need to scan a long caption.
            if (sawLtr && sawRtl) return Direction.MIXED
            index += Character.charCount(codePoint)
        }
        return when {
            sawRtl && !sawLtr -> Direction.RTL
            sawLtr && !sawRtl -> Direction.LTR
            sawLtr && sawRtl -> Direction.MIXED
            else -> Direction.LTR
        }
    }

    /**
     * The recommended alignment for a paragraph in the given [direction].
     * RTL maps to END; LTR + MIXED map to START (mixed uses the
     * first-strong direction, which the overlay's existing left-anchor
     * already handles correctly).
     */
    fun recommendAlignment(direction: Direction): PreferredAlignment = when (direction) {
        Direction.RTL -> PreferredAlignment.END
        Direction.LTR, Direction.MIXED -> PreferredAlignment.START
    }

    /** First-strong paragraph direction, retained even for mixed text. */
    fun baseDirection(text: String): Direction {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            when (val direction = strongDirection(codePoint)) {
                Direction.LTR, Direction.RTL -> return direction
                Direction.MIXED -> Unit
            }
            index += Character.charCount(codePoint)
        }
        return Direction.LTR
    }

    fun recommendAlignment(text: String): PreferredAlignment =
        if (baseDirection(text) == Direction.RTL) PreferredAlignment.END else PreferredAlignment.START

    /**
     * True when [text] contains any strong RTL character. Cheap predicate
     * the Compose layer can use to decide whether to call
     * `BidiFormatter.unicodeWrap` at all — wrapping an ASCII timestamp is
     * wasted allocation.
     */
    fun needsBidiWrap(text: String): Boolean {
        if (text.isEmpty()) return false
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (strongDirection(codePoint) == Direction.RTL) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    /**
     * Return the strong direction class of [c], or MIXED for any character
     * that is neutral / weak. Coverage of every RTL Unicode block that
     * Android renders today:
     *  - Hebrew (U+0590..U+05FF)
     *  - Arabic (U+0600..U+06FF)
     *  - Arabic Supplement (U+0750..U+077F)
     *  - NKo (U+07C0..U+07FF)
     *  - Syriac (U+0700..U+074F)
     *  - Thaana (U+0780..U+07BF)
     *  - Hebrew Presentation Forms (U+FB1D..U+FB4F)
     *  - Arabic Presentation Forms-A (U+FB50..U+FDFF)
     *  - Arabic Presentation Forms-B (U+FE70..U+FEFF)
     *  - SMP Arabic blocks (U+10800..U+10FFF — covered via codepoint scan
     *    where Java surrogates require pair handling; not exhaustive but
     *    sufficient for caption text).
     */
    private fun strongDirection(codePoint: Int): Direction {
        return when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> Direction.LTR
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> Direction.RTL
            else -> Direction.MIXED
        }
    }
}

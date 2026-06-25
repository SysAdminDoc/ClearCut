package com.novacut.editor.engine

import com.novacut.editor.engine.BidiTextPolicy.Direction
import com.novacut.editor.engine.BidiTextPolicy.PreferredAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the bidi-text policy that the overlay renderer will consult before
 * applying `BidiFormatter.unicodeWrap(...)` on the Android side
 * (RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #14 / R5.4b).
 *
 * The classifier is pure Kotlin so it can ship into the engine layer and
 * be unit-tested without an Android runtime. The Compose / Canvas wrap
 * step is an Android side-effect performed by `BidiFormatter`, which is
 * driven by the boolean predicate [BidiTextPolicy.needsBidiWrap] —
 * pinning that predicate keeps wasted allocation off the common ASCII
 * caption path.
 */
class BidiTextPolicyTest {

    @Test
    fun classify_emptyStringIsLtr() {
        assertEquals(Direction.LTR, BidiTextPolicy.classify(""))
    }

    @Test
    fun classify_neutralOnlyStringIsLtr() {
        // Pure punctuation / digits has no strong direction; LTR is the
        // safe default for the timestamp-label use case.
        assertEquals(Direction.LTR, BidiTextPolicy.classify("   "))
        assertEquals(Direction.LTR, BidiTextPolicy.classify("12:34"))
        assertEquals(Direction.LTR, BidiTextPolicy.classify("..."))
    }

    @Test
    fun classify_englishIsLtr() {
        assertEquals(Direction.LTR, BidiTextPolicy.classify("Hello, world."))
        assertEquals(Direction.LTR, BidiTextPolicy.classify("ClearCut"))
    }

    @Test
    fun classify_arabicIsRtl() {
        assertEquals(Direction.RTL, BidiTextPolicy.classify("مرحبا"))
        assertEquals(Direction.RTL, BidiTextPolicy.classify("السلام عليكم"))
    }

    @Test
    fun classify_hebrewIsRtl() {
        assertEquals(Direction.RTL, BidiTextPolicy.classify("שלום"))
    }

    @Test
    fun classify_mixedEnglishAndArabicIsMixed() {
        assertEquals(Direction.MIXED, BidiTextPolicy.classify("Hello مرحبا"))
        assertEquals(Direction.MIXED, BidiTextPolicy.classify("مرحبا World"))
    }

    @Test
    fun classify_arabicSupplementBlockIsRtl() {
        // Sample from U+0750..U+077F Arabic Supplement.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("ݐݑݒ"))
    }

    @Test
    fun classify_hebrewPresentationFormsAreRtl() {
        // Hebrew Presentation Forms U+FB1D..U+FB4F.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("יִﬠאּ"))
    }

    @Test
    fun classify_arabicPresentationFormsAreRtl() {
        // Arabic Presentation Forms-A U+FB50..U+FDFF.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("ﭐﰀﴰ"))
        // Arabic Presentation Forms-B U+FE70..U+FEFF.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("ﹰﺀﻰ"))
    }

    @Test
    fun classify_thaanaAndSyriacAreRtl() {
        // Thaana U+0780..U+07BF.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("ހސޠ"))
        // Syriac U+0700..U+074F.
        assertEquals(Direction.RTL, BidiTextPolicy.classify("ܐܠ"))
    }

    @Test
    fun recommendAlignment_rtlMapsToEnd() {
        assertEquals(PreferredAlignment.END, BidiTextPolicy.recommendAlignment(Direction.RTL))
    }

    @Test
    fun recommendAlignment_ltrAndMixedMapToStart() {
        assertEquals(PreferredAlignment.START, BidiTextPolicy.recommendAlignment(Direction.LTR))
        // Mixed defaults to the first-strong direction, which the existing
        // left-anchor handling already follows — START is the safe choice.
        assertEquals(PreferredAlignment.START, BidiTextPolicy.recommendAlignment(Direction.MIXED))
    }

    @Test
    fun needsBidiWrap_isFalseForPureAscii() {
        assertFalse(BidiTextPolicy.needsBidiWrap("Hello"))
        assertFalse(BidiTextPolicy.needsBidiWrap(""))
        assertFalse(BidiTextPolicy.needsBidiWrap("01:23"))
    }

    @Test
    fun needsBidiWrap_isFalseForLatin1WithoutRtl() {
        assertFalse(BidiTextPolicy.needsBidiWrap("Café résumé naïve"))
    }

    @Test
    fun needsBidiWrap_isTrueForAnyRtlCharacter() {
        assertTrue(BidiTextPolicy.needsBidiWrap("مرحبا"))
        assertTrue(BidiTextPolicy.needsBidiWrap("Hello مرحبا"))
        assertTrue(BidiTextPolicy.needsBidiWrap("שלום"))
    }
}

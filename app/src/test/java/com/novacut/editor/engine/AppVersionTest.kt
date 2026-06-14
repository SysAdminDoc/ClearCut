package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the version-comparison contract behind the passive update check:
 * malformed input never reports an update, and only a strictly higher
 * major/minor/patch counts as newer.
 */
class AppVersionTest {

    @Test
    fun parse_stripsLeadingVAndReadsComponents() {
        val parsed = AppVersion.parse("v3.74.92")
        assertEquals(AppVersion.Parsed(3, 74, 92), parsed)
        assertEquals(AppVersion.Parsed(3, 74, 92), AppVersion.parse("3.74.92"))
        assertEquals(AppVersion.Parsed(3, 74, 92), AppVersion.parse("V3.74.92"))
    }

    @Test
    fun parse_defaultsMissingComponentsToZero() {
        assertEquals(AppVersion.Parsed(4, 0, 0), AppVersion.parse("v4"))
        assertEquals(AppVersion.Parsed(4, 2, 0), AppVersion.parse("4.2"))
    }

    @Test
    fun parse_ignoresPreReleaseAndBuildSuffix() {
        assertEquals(AppVersion.Parsed(3, 74, 93), AppVersion.parse("v3.74.93-rc1"))
        assertEquals(AppVersion.Parsed(3, 74, 93), AppVersion.parse("3.74.93+build7"))
    }

    @Test
    fun parse_returnsNullForGarbage() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun isNewer_detectsHigherReleaseOnEveryComponent() {
        assertTrue(AppVersion.isNewer("v3.74.93", "v3.74.92"))
        assertTrue(AppVersion.isNewer("v3.75.0", "v3.74.92"))
        assertTrue(AppVersion.isNewer("v4.0.0", "v3.74.92"))
    }

    @Test
    fun isNewer_falseWhenSameOrOlder() {
        assertFalse(AppVersion.isNewer("v3.74.92", "v3.74.92"))
        assertFalse(AppVersion.isNewer("v3.74.91", "v3.74.92"))
        assertFalse(AppVersion.isNewer("v3.0.0", "v3.74.92"))
    }

    @Test
    fun isNewer_falseWhenEitherSideUnparseable() {
        assertFalse(AppVersion.isNewer("nightly", "v3.74.92"))
        assertFalse(AppVersion.isNewer("v3.74.93", "garbage"))
        assertFalse(AppVersion.isNewer(null, "v3.74.92"))
    }
}

package com.novacut.editor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard advertises Export / Delete / Opt out on every row. A row the
 * dashboard cannot act on must therefore name where its control actually is;
 * otherwise the advertised control is a claim with nothing behind it.
 */
class PrivacyControlLocationTest {

    @Test
    fun everyEntryNamesWhereItsControlsLive() {
        for (entry in PrivacyDashboard.entries) {
            assertTrue(
                "${entry.category} must name a reachable control location",
                entry.controlLocation.isNotBlank()
            )
        }
    }

    @Test
    fun controlLocationsPointAtARealSurface() {
        val surfaces = listOf("Settings", "Projects", "Editor", "Android app-info")
        for (entry in PrivacyDashboard.entries) {
            assertTrue(
                "${entry.category} control location '${entry.controlLocation}' " +
                    "does not name a surface the user can reach",
                surfaces.any { entry.controlLocation.startsWith(it) }
            )
        }
    }

    @Test
    fun controlLocationsAreNotVagueRestatementsOfTheCategory() {
        for (entry in PrivacyDashboard.entries) {
            assertFalse(
                "${entry.category} control location must be an instruction, not a restatement",
                entry.controlLocation.equals(entry.category.displayName, ignoreCase = true)
            )
            assertTrue(
                "${entry.category} control location should name a destination or an action",
                entry.controlLocation.contains("→") || entry.controlLocation.contains("—")
            )
        }
    }

    @Test
    fun optOutEntriesNameTheToggleThatTurnsThemOff() {
        val optOutEntries = PrivacyDashboard.entries.filter { it.controls.hasOptOut }
        assertTrue("expected at least one opt-out category", optOutEntries.isNotEmpty())
        for (entry in optOutEntries) {
            assertTrue(
                "${entry.category} advertises an opt-out; its location must say where the toggle is",
                entry.controlLocation.contains("Settings") || entry.controlLocation.contains("consent")
            )
        }
    }
}

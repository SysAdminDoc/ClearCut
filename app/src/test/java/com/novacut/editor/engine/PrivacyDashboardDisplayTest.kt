package com.novacut.editor.engine

import com.novacut.editor.engine.PrivacyDashboard.Section
import com.novacut.editor.engine.PrivacyDashboard.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the display-layer helpers `sectionFor` / `sortForDisplay` /
 * `groupForDisplay` / `controlSummary` that the future Privacy Dashboard
 * panel will consume (RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #8).
 *
 * The dashboard's risk-ordered presentation is a privacy UX promise:
 * cloud and telemetry rows must read first so users are never surprised by
 * what data leaves the device, even if ClearCut is mostly on-device today.
 */
class PrivacyDashboardDisplayTest {

    @Test
    fun sectionFor_cloudEntriesAreCloudSection() {
        val cloud = PrivacyDashboard.entries
            .first { it.location == StorageLocation.CLOUD_ON_DEMAND }
        assertEquals(Section.CLOUD_AND_TELEMETRY, PrivacyDashboard.sectionFor(cloud))
    }

    @Test
    fun sectionFor_onDeviceCollectedByDefaultIsOnDevice() {
        val collected = PrivacyDashboard.entries.first {
            it.location != StorageLocation.CLOUD_ON_DEMAND && it.collectedByDefault
        }
        assertEquals(Section.ON_DEVICE_COLLECTED, PrivacyDashboard.sectionFor(collected))
    }

    @Test
    fun sectionFor_onDeviceOptInIsOptInSection() {
        val optIn = PrivacyDashboard.entries.first {
            it.location != StorageLocation.CLOUD_ON_DEMAND && !it.collectedByDefault
        }
        assertEquals(Section.ON_DEVICE_OPT_IN, PrivacyDashboard.sectionFor(optIn))
    }

    @Test
    fun sortForDisplay_cloudRowsAppearFirst() {
        val sorted = PrivacyDashboard.sortForDisplay()
        assertEquals(PrivacyDashboard.entries.size, sorted.size)
        // Walk the list and check section ranks are monotone non-decreasing.
        val ranks = sorted.map {
            when (PrivacyDashboard.sectionFor(it)) {
                Section.CLOUD_AND_TELEMETRY -> 0
                Section.ON_DEVICE_COLLECTED -> 1
                Section.ON_DEVICE_OPT_IN -> 2
            }
        }
        for (i in 1 until ranks.size) {
            assertTrue(
                "Sort order is not monotone: ${ranks[i - 1]} > ${ranks[i]} at index $i",
                ranks[i - 1] <= ranks[i]
            )
        }
        // First entry must be cloud.
        assertEquals(Section.CLOUD_AND_TELEMETRY, PrivacyDashboard.sectionFor(sorted.first()))
    }

    @Test
    fun groupForDisplay_iterationOrderMirrorsSort() {
        val grouped = PrivacyDashboard.groupForDisplay()
        // LinkedHashMap iteration order must match the section ranking.
        val iteratedSections = grouped.keys.toList()
        val expectedOrder = listOf(
            Section.CLOUD_AND_TELEMETRY,
            Section.ON_DEVICE_COLLECTED,
            Section.ON_DEVICE_OPT_IN,
        ).filter { it in iteratedSections }
        assertEquals(expectedOrder, iteratedSections)
    }

    @Test
    fun groupForDisplay_unionEqualsEntriesSet() {
        val grouped = PrivacyDashboard.groupForDisplay()
        val flattened = grouped.values.flatten().toSet()
        assertEquals(PrivacyDashboard.entries.toSet(), flattened)
    }

    @Test
    fun groupForDisplay_doesNotEmitEmptySections() {
        for (entryList in PrivacyDashboard.groupForDisplay().values) {
            assertFalse(entryList.isEmpty())
        }
    }

    @Test
    fun controlSummary_combinesAvailableActions() {
        val full = PrivacyDashboard.DashboardEntry(
            category = PrivacyDashboard.Category.APP_PREFERENCES,
            location = StorageLocation.DEVICE_INTERNAL,
            controls = PrivacyDashboard.Controls(canExport = true, canDelete = true, hasOptOut = true),
            collectedBy = listOf("test"),
            retentionPolicy = "test",
            collectedByDefault = false,
        )
        assertEquals("Export · Delete · Opt out", PrivacyDashboard.controlSummary(full))

        val noControls = full.copy(
            controls = PrivacyDashboard.Controls(canExport = false, canDelete = false, hasOptOut = false),
        )
        assertEquals("Read-only", PrivacyDashboard.controlSummary(noControls))
    }

    @Test
    fun cloudCategoriesAlwaysHaveOptOut() {
        for (cloud in PrivacyDashboard.cloudOrTelemetryCategories()) {
            assertTrue(
                "Cloud category ${cloud.category} must offer an opt-out toggle",
                cloud.controls.hasOptOut
            )
        }
    }
}

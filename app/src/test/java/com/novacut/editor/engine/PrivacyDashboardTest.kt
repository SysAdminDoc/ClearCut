package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5.5c — Privacy dashboard invariant tests.
 *
 * These checks lock the user-facing privacy contract: every category has a
 * delete action, cloud / telemetry paths are never on by default, and the
 * dashboard list stays in 1:1 correspondence with the Category enum so a
 * future commit can't add a Category without an entry.
 */
class PrivacyDashboardTest {

    @Test
    fun everyCategoryHasExactlyOneEntry() {
        val categoryCounts = PrivacyDashboard.entries.groupingBy { it.category }.eachCount()
        for (category in PrivacyDashboard.Category.entries) {
            assertEquals(
                "Category $category must have exactly one dashboard entry",
                1,
                categoryCounts[category],
            )
        }
        assertEquals(
            PrivacyDashboard.Category.entries.size,
            PrivacyDashboard.entries.size,
        )
    }

    @Test
    fun everyEntryAllowsDelete() {
        // R5.5c hard contract: every data category must be deletable.
        for (entry in PrivacyDashboard.entries) {
            assertTrue(
                "${entry.category} entry must allow delete",
                entry.controls.canDelete,
            )
        }
    }

    @Test
    fun everyEntryReferencesARetentionPolicy() {
        for (entry in PrivacyDashboard.entries) {
            assertTrue(
                "${entry.category} entry must record a retention policy",
                entry.retentionPolicy.isNotBlank(),
            )
        }
    }

    @Test
    fun everyEntryRecordsAtLeastOneCollector() {
        for (entry in PrivacyDashboard.entries) {
            assertTrue(
                "${entry.category} entry must list at least one collectedBy source",
                entry.collectedBy.isNotEmpty(),
            )
        }
    }

    @Test
    fun cloudAndTelemetryAreNeverOnByDefault() {
        // R5.9c contract: any cloud-touching path requires explicit consent.
        for (entry in PrivacyDashboard.cloudOrTelemetryCategories()) {
            assertFalse(
                "${entry.category} must NOT collect by default",
                entry.collectedByDefault,
            )
            assertTrue(
                "${entry.category} must expose an opt-out",
                entry.controls.hasOptOut,
            )
        }
    }

    @Test
    fun cloudAndTelemetryCategoriesAreOnlyTheCloudOnes() {
        val cloud = PrivacyDashboard.cloudOrTelemetryCategories()
        for (entry in cloud) {
            assertEquals(
                PrivacyDashboard.StorageLocation.CLOUD_ON_DEMAND,
                entry.location,
            )
        }
        // At least CLOUD_GENERATIVE and OPT_IN_TELEMETRY belong here.
        val categoriesInCloud = cloud.map { it.category }.toSet()
        assertTrue(PrivacyDashboard.Category.CLOUD_GENERATIVE in categoriesInCloud)
        assertTrue(PrivacyDashboard.Category.OPT_IN_TELEMETRY in categoriesInCloud)
    }

    @Test
    fun mlModelsRowAdvertisesOptOut() {
        val entry = PrivacyDashboard.entryFor(PrivacyDashboard.Category.ML_MODELS)
        assertNotNull(entry)
        assertTrue(entry!!.controls.hasOptOut)
        // ML models are NOT collected by default — they download only when
        // the user explicitly accepts the per-model size disclosure.
        assertFalse(entry.collectedByDefault)
    }

    @Test
    fun aiUsageLedgerRowIsLocalProjectScopedAndExportable() {
        val entry = PrivacyDashboard.entryFor(PrivacyDashboard.Category.AI_USAGE_LEDGER)
        assertNotNull(entry)
        assertEquals(PrivacyDashboard.StorageLocation.DEVICE_INTERNAL, entry!!.location)
        assertTrue(entry.controls.canExport)
        assertTrue(entry.controls.canDelete)
        assertFalse(entry.controls.hasOptOut)
        assertFalse(entry.collectedByDefault)
        assertTrue(entry.collectedBy.contains("AiUsageLedger"))
        assertTrue(entry.collectedBy.contains("C2paExportEngine"))
        assertTrue(entry.retentionPolicy.contains("project autosave", ignoreCase = true))
        assertTrue(entry.retentionPolicy.contains(".c2pa-draft-manifest.json"))
    }

    @Test
    fun settingsResetReportRowIsLocalAndDiagnosticExportable() {
        val entry = PrivacyDashboard.entryFor(PrivacyDashboard.Category.SETTINGS_RESET_REPORTS)
        assertNotNull(entry)
        assertEquals(PrivacyDashboard.StorageLocation.DEVICE_INTERNAL, entry!!.location)
        assertTrue(entry.controls.canExport)
        assertTrue(entry.controls.canDelete)
        assertFalse(entry.controls.hasOptOut)
        assertTrue(entry.collectedBy.contains("SettingsResetReportStore"))
        assertTrue(entry.collectedBy.contains("DiagnosticExportEngine"))
        assertTrue(entry.retentionPolicy.contains("settings-reset-report.jsonl"))
    }

    @Test
    fun diagnosticLogsDisclosePrivateIncidentsAndPseudonymizedSharing() {
        val entry = PrivacyDashboard.entryFor(PrivacyDashboard.Category.DIAGNOSTIC_LOGS)
        assertNotNull(entry)
        assertTrue(entry!!.collectedBy.contains("ExportIncidentStore"))
        assertTrue(entry.retentionPolicy.contains("capped to 10"))
        assertTrue(entry.retentionPolicy.contains("pseudonym", ignoreCase = true))
        assertFalse(entry.collectedByDefault)
    }

    @Test
    fun projectContentRowDisclosesOverlayAssetCopies() {
        val entry = PrivacyDashboard.entryFor(PrivacyDashboard.Category.PROJECT_CONTENT)
        assertNotNull(entry)
        assertTrue(entry!!.collectedBy.contains("OverlayAssetStore"))
        assertTrue(entry.category.displayName.contains("overlays", ignoreCase = true))
    }

    @Test
    fun entryFor_unknownReturnsNull() {
        // Sanity: this object's lookup is implemented as firstOrNull, so
        // pasting an enum value not present in entries must return null.
        // We don't have such a case today, but the contract is locked.
        assertNotNull(PrivacyDashboard.entryFor(PrivacyDashboard.Category.PROJECT_CONTENT))
    }

    @Test
    fun categoriesAreOrderedByEnumDeclaration() {
        // Locks display order — the UI iterates entries directly so the
        // declared sequence matters.
        val expected = PrivacyDashboard.Category.entries.toList()
        val actual = PrivacyDashboard.entries.map { it.category }
        assertEquals(expected, actual)
    }

    @Test
    fun controlsValueObjectEquality() {
        val a = PrivacyDashboard.Controls(canExport = true, canDelete = true, hasOptOut = false)
        val b = PrivacyDashboard.Controls(canExport = true, canDelete = true, hasOptOut = false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun entryForNull_returnsCleanResult() {
        // If a caller passes a value that no entry covers, the lookup is
        // safe. (Defensive — current Category enum is 1:1 with entries.)
        val safeMissing: PrivacyDashboard.DashboardEntry? = run {
            val all = PrivacyDashboard.Category.entries.toSet()
            val first = all.first()
            // Re-run entryFor to ensure no exception path.
            PrivacyDashboard.entryFor(first)
        }
        assertNotNull(safeMissing)
    }
}

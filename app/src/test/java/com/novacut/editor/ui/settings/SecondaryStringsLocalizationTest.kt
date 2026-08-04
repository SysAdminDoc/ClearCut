package com.novacut.editor.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecondaryStringsLocalizationTest {

    @Test
    fun editorModeSelectionUsesCanonicalValuesWithLocalizedLabels() {
        val options = listOf(
            SettingsEditorModeOption("Easy", "Fácil"),
            SettingsEditorModeOption("Pro", "Profesional")
        )

        assertEquals(options[0], selectedEditorModeOption("Easy", options))
        assertEquals(options[1], selectedEditorModeOption("Pro", options))
        assertEquals("Profesional", selectedEditorModeOption("Pro", options).label)
        assertEquals(
            "Unknown values should fall back to the canonical default mode",
            options[1],
            selectedEditorModeOption("unsupported", options)
        )
    }

    @Test
    fun targetedUiSurfacesRouteSecondaryCopyThroughResources() {
        val batchPanel = source("app/src/main/java/com/novacut/editor/ui/export/BatchExportPanel.kt")
        listOf(
            "R.string.batch_export_subtitle",
            "R.plurals.batch_export_total",
            "R.plurals.batch_export_active",
            "R.plurals.batch_export_needs_review",
            "R.plurals.batch_export_needs_attention",
            "R.plurals.batch_export_done",
            "R.plurals.batch_export_queued",
            "R.plurals.batch_export_exporting",
            "R.plurals.batch_export_failed",
            "R.plurals.batch_export_cancelled",
            "R.plurals.batch_export_interrupted",
            "R.plurals.batch_export_review"
        ).forEach { resourceReference ->
            assertTrue("Batch export copy is missing $resourceReference", batchPanel.contains(resourceReference))
        }
        assertFalse(batchPanel.contains("subtitle = \"Queue multiple delivery variants"))
        assertFalse(batchPanel.contains("\"\$queuedCount queued\""))
        assertFalse(batchPanel.contains("\"\$inProgressCount exporting\""))
        assertFalse(batchPanel.contains("\"\$failedCount failed\""))

        val projectList = source("app/src/main/java/com/novacut/editor/ui/projects/ProjectListViewModel.kt")
        listOf(
            "R.string.project_template_import_partial_warning",
            "R.string.project_document_preview_multiple_warning",
            "R.string.project_template_share_chooser",
            "R.string.project_template_restore_partial_warning",
            "R.string.project_untitled"
        ).forEach { resourceReference ->
            assertTrue("Project-list copy is missing $resourceReference", projectList.contains(resourceReference))
        }
        assertFalse(projectList.contains("Only the first recognized document was previewed"))
        assertFalse(projectList.contains("Intent.createChooser(shareIntent, \"Share Template\")"))
        assertFalse(projectList.contains("Warning: \${loaded.restoreReport.summary()}"))
        assertFalse(projectList.contains("name: String = \"Untitled\""))

        val settingsViewModel = source("app/src/main/java/com/novacut/editor/ui/settings/SettingsViewModel.kt")
        assertTrue(settingsViewModel.contains("R.string.settings_diagnostic_export_create_failed"))
        assertTrue(settingsViewModel.contains("R.string.settings_diagnostic_export_share_failed"))
        assertFalse(settingsViewModel.contains("\"Diagnostic ZIP could not be created. Try again.\""))
        assertFalse(settingsViewModel.contains("\"Diagnostic ZIP could not be shared from this device.\""))

        val settingsScreen = source("app/src/main/java/com/novacut/editor/ui/settings/SettingsScreen.kt")
        assertTrue(settingsScreen.contains("value = selectedEditorMode.label"))
        assertTrue(settingsScreen.contains("setEditorMode(editorModeOptions[it].value)"))
        assertFalse(settingsScreen.contains("value = settings.editorMode"))
        assertFalse(settingsScreen.contains("listOf(\"Easy\", \"Pro\")[it]"))
    }

    @Test
    fun batchCountCopyIsPluralizedInBothShippedLocales() {
        val base = resource("app/src/main/res/values/strings.xml")
        val spanish = resource("app/src/main/res/values-es/strings.xml")
        val pluralNames = listOf(
            "batch_export_total",
            "batch_export_active",
            "batch_export_needs_review",
            "batch_export_needs_attention",
            "batch_export_done",
            "batch_export_queued",
            "batch_export_exporting",
            "batch_export_failed",
            "batch_export_cancelled",
            "batch_export_interrupted",
            "batch_export_review"
        )

        pluralNames.forEach { name ->
            val openingTag = "<plurals name=\"$name\""
            assertTrue("Base locale must pluralize $name", base.contains(openingTag))
            assertTrue("Spanish locale must pluralize $name", spanish.contains(openingTag))
            assertTrue("Base locale is missing one quantity for $name", pluralBlock(base, name).contains("quantity=\"one\""))
            assertTrue("Base locale is missing other quantity for $name", pluralBlock(base, name).contains("quantity=\"other\""))
            assertTrue("Spanish locale is missing one quantity for $name", pluralBlock(spanish, name).contains("quantity=\"one\""))
            assertTrue("Spanish locale is missing other quantity for $name", pluralBlock(spanish, name).contains("quantity=\"other\""))
        }
    }

    private fun source(path: String): String = locateRepoRoot().resolve(path).readText()

    private fun resource(path: String): String = source(path)

    private fun pluralBlock(xml: String, name: String): String {
        val start = xml.indexOf("<plurals name=\"$name\"")
        require(start >= 0) { "Plural resource $name not found" }
        val end = xml.indexOf("</plurals>", start)
        require(end > start) { "Plural resource $name is not closed" }
        return xml.substring(start, end)
    }

    private fun locateRepoRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: error("Could not read user.dir")).absoluteFile
        repeat(8) {
            if (File(directory, ".git").isDirectory) return directory
            directory = directory.parentFile ?: error("Could not locate the repository root")
        }
        error("Could not locate the repository root")
    }
}

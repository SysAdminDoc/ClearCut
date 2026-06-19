package com.novacut.editor

import com.novacut.editor.engine.TimelineExchangeEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PublicFeatureClaimsTest {
    @Test
    fun readmeSlipSlideClaimIsBackedByEditorWiring() {
        val readme = locate("README.md").readText()
        if (!readme.contains("slip/slide editing", ignoreCase = true)) {
            return
        }

        val editorScreen = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorScreen.kt").readText()
        val timeline = locate("app/src/main/java/com/novacut/editor/ui/editor/Timeline.kt").readText()
        val editorViewModel = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()

        assertTrue(
            "README slip/slide claim requires EditorScreen to wire slide gestures",
            editorScreen.contains("onSlideClip = viewModel::slideClip")
        )
        assertTrue(
            "README slip/slide claim requires EditorScreen to wire slip gestures",
            editorScreen.contains("onSlipClip = viewModel::slipClip")
        )
        assertTrue("Timeline must expose a slide callback", timeline.contains("onSlideClip: (clipId: String, deltaMs: Long) -> Unit"))
        assertTrue("Timeline must expose a slip callback", timeline.contains("onSlipClip: (clipId: String, deltaMs: Long) -> Unit"))
        assertTrue("EditorViewModel must implement slide edits", editorViewModel.contains("fun slideClip(clipId: String, slideAmountMs: Long)"))
        assertTrue("EditorViewModel must implement slip edits", editorViewModel.contains("fun slipClip(clipId: String, slipAmountMs: Long)"))
    }

    @Test
    fun archiveTransferCopyDoesNotPromiseCloudSync() {
        val readme = locate("README.md").readText()
        val strings = stringResources(locate("app/src/main/res/values/strings.xml"))
        val archiveTransferKeys = listOf(
            "tool_cloud_backup",
            "cloud_backup_title",
            "cloud_backup_restore",
            "cloud_backup_no_backups",
            "panel_cloud_backup_title",
            "panel_cloud_backup_description",
            "panel_cloud_backup_export",
            "panel_cloud_backup_import",
            "panel_cloud_backup_status_title",
            "panel_cloud_backup_import_confirm_body",
            "vm_backup_saved_toast",
            "vm_importing_backup_toast",
        )

        archiveTransferKeys.forEach { key ->
            val value = strings.getValue(key)
            assertFalse("$key must not promise cloud sync", value.contains("cloud", ignoreCase = true))
            assertFalse("$key should use archive language instead of backup branding", value.contains("backup", ignoreCase = true))
        }
        assertTrue(strings.getValue("tool_cloud_backup").contains("Archive"))
        assertTrue(strings.getValue("panel_cloud_backup_description").contains("Downloads/NovaCut"))

        val importRouter = locate("app/src/main/java/com/novacut/editor/engine/IncomingDocumentImportRouter.kt").readText()
        val legacyFeatureName = "Cloud " + "Backup"
        assertFalse(importRouter.contains(legacyFeatureName, ignoreCase = true))
        assertTrue(importRouter.contains("Archive Transfer import"))

        val editorViewModel = locate("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt").readText()
        assertFalse(editorViewModel.contains("\"Archive saved:"))
        assertFalse(editorViewModel.contains("\"Archive export failed\""))
        assertTrue(editorViewModel.contains("R.string.vm_backup_saved_toast"))

        assertFalse(readme.contains("Cloud backup", ignoreCase = true))
        assertFalse(readme.contains("backend pending", ignoreCase = true))
        assertTrue(readme.contains("Archive Transfer"))
    }

    @Test
    fun readmeTimelineImportCopyMatchesRuntimeGate() {
        val readme = locate("README.md").readText()
        val timelineInterchangeLine = readme
            .lineSequence()
            .first { it.contains("Timeline interchange") }
        val timelineImportEngine =
            locate("app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt").readText()

        if (timelineImportEngine.contains("not yet implemented", ignoreCase = true)) {
            assertFalse(
                "README must not advertise active timeline import while TimelineImportEngine is gated",
                timelineInterchangeLine.contains("export/import", ignoreCase = true)
            )
            assertFalse(
                "README must not promise NLE round-tripping while import is gated",
                timelineInterchangeLine.contains("round-tripping", ignoreCase = true)
            )
            assertTrue(
                "README should explain that incoming timeline files are preview-gated",
                timelineInterchangeLine.contains("guarded import preview", ignoreCase = true)
            )
        }
    }

    @Test
    fun timelineExchangeCapabilityDoesNotAdvertiseGatedImport() {
        val timelineImportEngine =
            locate("app/src/main/java/com/novacut/editor/engine/TimelineImportEngine.kt").readText()

        if (timelineImportEngine.contains("not yet implemented", ignoreCase = true)) {
            assertFalse(TimelineExchangeEngine.TimelineExchangeFormat.OTIO.canImport)
        }
    }

    private fun locate(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found")
    }

    private fun stringResources(file: File): Map<String, String> {
        val resources = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
        val strings = mutableMapOf<String, String>()
        for (index in 0 until resources.childNodes.length) {
            val element = resources.childNodes.item(index) as? Element ?: continue
            if (element.tagName == "string") {
                strings[element.getAttribute("name")] = element.textContent
            }
        }
        return strings
    }
}

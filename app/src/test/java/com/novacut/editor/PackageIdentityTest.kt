package com.novacut.editor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PackageIdentityTest {

    @Test
    fun registryMatchesGradleAndPublicMigrationPolicy() {
        val registry = JSONObject(locate("scripts/package_identity.json").readText())
        val applicationId = registry.getString("applicationId")
        val namespace = registry.getString("namespace")
        val build = locate("app/build.gradle.kts").readText()
        val readme = locate("README.md").readText()

        assertEquals(1, registry.getInt("schemaVersion"))
        assertEquals(applicationId, namespace)
        assertTrue(build.contains("namespace = \"$namespace\""))
        assertTrue(build.contains("applicationId = \"$applicationId\""))
        assertTrue(build.contains("applicationIdSuffix = \".streaming\""))
        assertTrue(readme.contains("### Package identity and upgrade policy"))
        assertTrue(readme.contains("`$applicationId`"))
        assertTrue(readme.contains("new install with an explicit export/import path"))
        assertFalse("Retired product branding must not return to public README copy", readme.contains("NovaCut"))
    }

    @Test
    fun manifestAndArchiveSurfacesStayOnTheFrozenLineage() {
        val registry = JSONObject(locate("scripts/package_identity.json").readText())
        val applicationId = registry.getString("applicationId")
        val manifest = locate("app/src/main/AndroidManifest.xml").readText()
        val shortcuts = locate("app/src/main/res/xml/shortcuts.xml").readText()
        val parser = locate("app/src/main/java/com/novacut/editor/engine/IncomingDocumentIntentParser.kt").readText()
        val plugins = locate("app/src/main/java/com/novacut/editor/engine/PluginRegistry.kt").readText()

        assertTrue(manifest.contains("\${applicationId}.androidx-startup"))
        assertTrue(manifest.contains("\${applicationId}.fileprovider"))
        val shortcutActions = registry.getJSONObject("shortcutTarget").getJSONArray("actions")
        (0 until shortcutActions.length()).forEach { index ->
            val action = shortcutActions.getString(index)
            assertTrue("Manifest must keep shortcut action $action", manifest.contains(action))
        }
        assertTrue(shortcuts.contains("android:targetPackage=\"\${applicationId}\""))
        assertTrue(shortcuts.contains("android:targetClass=\"\${applicationId}.MainActivity\""))

        val associations = registry.getJSONObject("archiveAssociations")
        val extensions = associations.getJSONArray("extensions")
        (0 until extensions.length()).forEach { index ->
            val extension = extensions.getString(index)
            val source = if (extension == ".clearcut-template") plugins else parser
            assertTrue("Archive extension $extension must remain recognized", source.contains(extension))
        }
        val mimeTypes = associations.getJSONArray("mimeTypes")
        (0 until mimeTypes.length()).forEach { index ->
            val mime = mimeTypes.getString(index)
            assertTrue("Archive MIME $mime must remain registered", manifest.contains(mime))
        }

        assertTrue(locate("app/src/main/res/values/strings.xml").readText().contains(
            "<string name=\"app_name\">ClearCut</string>"
        ))
        assertTrue(locate("app/src/main/res/values-es/strings.xml").readText().contains(
            "<string name=\"app_name\">ClearCut</string>"
        ))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
}

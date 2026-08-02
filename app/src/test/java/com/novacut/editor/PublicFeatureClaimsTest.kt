package com.novacut.editor

import com.novacut.editor.engine.TimelineExchangeEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PublicFeatureClaimsTest {
    private companion object {
        /**
         * Comment markers that excuse an architecture-tree entry from the reachability check.
         * An entry without one of these is read as a shipped, user-reachable capability.
         */
        val UNREACHABLE_MARKERS = listOf("stub", "not wired", "not reachable", "planned", "target metadata")
    }

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
        assertTrue(strings.getValue("panel_cloud_backup_description").contains("Downloads/ClearCut"))

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
    fun readmeDoesNotPromiseDebugSigningFallback() {
        val readme = locate("README.md").readText()
        val buildScript = locate("app/build.gradle.kts").readText()
        val releaseBlock = buildScript
            .substringAfter("        release {", "")
            .substringBefore("        create(\"streaming\")", "")
        val releaseUsesDebugSigning = Regex("signingConfig\\s*=.*debug", RegexOption.IGNORE_CASE)
            .containsMatchIn(releaseBlock)

        if (!releaseUsesDebugSigning) {
            assertFalse(
                "README must not promise a debug-signing fallback when release signing is required",
                Regex("(?:assembleRelease`?|release builds?)\\s+falls back to (?:debug signing|the debug key)", RegexOption.IGNORE_CASE)
                    .containsMatchIn(readme)
            )
        }
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

    @Test
    fun stubbedEnginesAreNotAdvertisedAsAvailable() {
        val readme = locate("README.md").readText()
        val fastlane = locate("fastlane/metadata/android/en-US/full_description.txt").readText()

        val stubbedEngines = mapOf(
            "TemplateMarketplaceEngine" to listOf("template marketplace", "community marketplace", "browse templates online"),
            "StockAssetEngine" to listOf("stock library", "stock footage", "Pexels integration", "Pixabay integration"),
            "CameraCaptureEngine" to listOf("in-app camera", "CameraX recorder", "built-in recorder"),
            "CaptionTranslationEngine" to listOf("caption translation ready", "translate captions automatically", "real-time translation"),
            "StemSeparationEngine" to listOf("stem separation ready", "isolate vocals", "Demucs integration"),
            "VoiceCloneEngine" to listOf("voice cloning ready", "clone your voice", "XTTS integration"),
            "LipSyncEngine" to listOf("lip sync ready", "automatic lip sync", "Wav2Lip integration"),
            "EquirectangularEngine" to listOf("360 video editing", "VR editing ready", "equirectangular projection"),
            "ContentIdEngine" to listOf("content ID ready", "AcoustID fingerprint", "music identification"),
        )

        for ((engineName, forbiddenClaims) in stubbedEngines) {
            val engineFile = locate("app/src/main/java/com/novacut/editor/engine/$engineName.kt")
            val engineSource = engineFile.readText()
            val isStub = engineSource.contains("stub", ignoreCase = true)
                    || engineSource.contains("not yet implemented", ignoreCase = true)
                    || engineSource.contains("not wired", ignoreCase = true)

            if (isStub) {
                for (claim in forbiddenClaims) {
                    assertFalse(
                        "README must not claim '$claim' while $engineName is a stub",
                        readme.contains(claim, ignoreCase = true)
                    )
                    assertFalse(
                        "Fastlane must not claim '$claim' while $engineName is a stub",
                        fastlane.contains(claim, ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun captionTranslationEngineReportsNotReady() {
        val engineSource = locate(
            "app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt"
        ).readText()

        if (engineSource.contains("stub", ignoreCase = true)) {
            assertTrue(
                "CaptionTranslationEngine.isModelReady() must return false while stubbed",
                engineSource.contains("fun isModelReady(): Boolean = false")
            )
        }
    }

    @Test
    fun stockAssetEngineReportsNotConfigured() {
        val engineSource = locate(
            "app/src/main/java/com/novacut/editor/engine/StockAssetEngine.kt"
        ).readText()

        if (engineSource.contains("stub", ignoreCase = true)
            || engineSource.contains("not configured", ignoreCase = true)
        ) {
            assertTrue(
                "StockAssetEngine.isProviderConfigured() must return false while stubbed",
                engineSource.contains("fun isProviderConfigured(provider: Provider): Boolean = false")
            )
        }
    }

    @Test
    fun architectureTreeNamesOnlyPathsThatExist() {
        val entries = architectureTree(locate("README.md").readText())
        assertTrue("README architecture tree parsed no entries", entries.size > 20)

        val sourceRoot = locate("app/src/main/java/com/novacut/editor")
        entries.forEach { entry ->
            val target = if (entry.path.endsWith("/")) {
                File(sourceRoot, entry.path.trimEnd('/'))
            } else if (entry.path.endsWith(".kt")) {
                File(sourceRoot, entry.path)
            } else {
                File(sourceRoot, "${entry.path}.kt")
            }
            assertTrue(
                "README architecture tree names ${entry.path}, which does not exist at ${target.path}",
                target.exists()
            )
        }
    }

    @Test
    fun architectureTreeCountsMatchTheSourceTree() {
        val readme = locate("README.md").readText()
        val engineDir = locate("app/src/main/java/com/novacut/editor/engine")
        val engineSources = engineDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val singletonFiles = engineSources.count { it.readText().contains("@Singleton") }

        val claim = readme.lineSequence().first { it.contains("injectable singletons across") }
        val numbers = Regex("\\d+").findAll(claim).map { it.value.toInt() }.toList()
        assertTrue("engine/ claim line must carry two counts: $claim", numbers.size >= 2)
        assertTrue(
            "README claims ${numbers[0]} injectable singletons; source has $singletonFiles",
            numbers[0] == singletonFiles
        )
        assertTrue(
            "README claims ${numbers[1]} engine files; source has ${engineSources.size}",
            numbers[1] == engineSources.size
        )
    }

    @Test
    fun architectureTreeEnginesAreReachableUnlessLabelledOtherwise() {
        val readme = locate("README.md").readText()
        val sourceRoot = locate("app/src/main/java/com/novacut/editor")
        val allSources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        architectureTree(readme)
            .filter { !it.path.endsWith("/") && !it.path.endsWith(".kt") }
            .filter { entry -> UNREACHABLE_MARKERS.none { entry.comment.contains(it, ignoreCase = true) } }
            .forEach { entry ->
                val simpleName = entry.path.substringAfterLast('/')
                val declaration = File(sourceRoot, "${entry.path}.kt")
                val referencing = allSources.count { source ->
                    source != declaration && Regex("\\b$simpleName\\b").containsMatchIn(source.readText())
                }
                assertTrue(
                    "README presents $simpleName as shipped, but it has no production call site. " +
                        "Either wire it up or label the tree entry (stub / not wired / not reachable / planned).",
                    referencing > 0
                )
            }
    }

    @Test
    fun techStackVersionsMatchTheVersionCatalog() {
        val readme = locate("README.md").readText()
        val catalog = versionCatalog(locate("gradle/libs.versions.toml"))
        val rowToCatalogKey = mapOf(
            "Language" to "kotlin",
            "Video" to "media3",
            "Speech-to-Text" to "onnxruntime",
            "Noise Reduction" to "deepfilternet",
            "Segmentation" to "mediapipe",
            "Animated Titles" to "lottieCompose",
            "Startup performance" to "androidxBenchmark",
            "Database" to "room",
        )

        rowToCatalogKey.forEach { (label, key) ->
            val row = readme.lineSequence().firstOrNull { it.startsWith("| $label ") || it.startsWith("| $label|") }
                ?: error("Tech Stack table has no '$label' row")
            val expected = catalog[key] ?: error("version catalog has no '$key'")
            assertTrue(
                "Tech Stack row '$label' must state catalog version $expected: $row",
                row.contains(expected)
            )
        }
    }

    private data class TreeEntry(val path: String, val comment: String)

    private fun architectureTree(readme: String): List<TreeEntry> {
        val heading = readme.indexOf("## Architecture")
        require(heading >= 0) { "README has no Architecture section" }
        val fenceStart = readme.indexOf("```", heading) + 3
        val fenceEnd = readme.indexOf("```", fenceStart)
        require(fenceEnd > fenceStart) { "README Architecture section has no code fence" }

        val entries = mutableListOf<TreeEntry>()
        val parents = mutableListOf<String>()
        readme.substring(fenceStart, fenceEnd).lineSequence().forEach { raw ->
            val marker = raw.indexOfFirst { it == '\u251c' || it == '\u2514' }
            if (marker < 0) return@forEach
            val body = raw.substring(marker + 3)
            val name = body.substringBefore('#').trim()
            if (name.isEmpty()) return@forEach
            val depth = marker / 4
            while (parents.size > depth) parents.removeAt(parents.size - 1)
            entries.add(TreeEntry(parents.joinToString("") + name, body.substringAfter('#', "").trim()))
            if (name.endsWith("/")) parents.add(name)
        }
        return entries
    }

    private fun versionCatalog(file: File): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        var inVersions = false
        file.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inVersions = trimmed == "[versions]"
                return@forEach
            }
            if (!inVersions) return@forEach
            val match = Regex("^([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]+)\"").find(trimmed) ?: return@forEach
            versions[match.groupValues[1]] = match.groupValues[2]
        }
        return versions
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

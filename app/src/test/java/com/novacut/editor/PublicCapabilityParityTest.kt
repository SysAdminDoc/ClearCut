package com.novacut.editor

import com.novacut.editor.engine.AiToolRequirements
import com.novacut.editor.engine.CapabilityRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicCapabilityParityTest {

    @Test
    fun generatedCapabilitiesAndDependenciesMatchTheJsonRegistry() {
        val registry = JSONObject(locate("scripts/capability_registry.json").readText())
        val capabilityJson = registry.getJSONArray("capabilities").objectsById()
        val dependencyJson = registry.getJSONArray("dependencies").objectsById()
        val generatedCapabilities = CapabilityRegistry.capabilities.associateBy { it.id }
        val generatedDependencies = CapabilityRegistry.dependencies.associateBy { it.id }

        assertEquals(capabilityJson.keys, generatedCapabilities.keys)
        capabilityJson.forEach { (id, entry) ->
            val generated = generatedCapabilities.getValue(id)
            assertEquals(entry.getString("name"), generated.name)
            assertEquals(entry.getString("engine"), generated.engine)
            assertEquals(entry.getString("onDevice"), generated.onDevice)
            assertEquals(entry.getString("status"), generated.status)
            assertEquals(entry.getString("reachability"), generated.reachability)
        }

        assertEquals(dependencyJson.keys, generatedDependencies.keys)
        dependencyJson.forEach { (id, entry) ->
            val generated = generatedDependencies.getValue(id)
            assertEquals(entry.getString("label"), generated.label)
            assertEquals(entry.getString("version"), generated.version)
            assertEquals(entry.getString("coordinate"), generated.coordinate)
            assertEquals(entry.getString("purpose"), generated.purpose)
            assertEquals(entry.getString("publicStatus"), generated.publicStatus)
            val keys = entry.getJSONArray("catalogKeys")
            assertEquals((0 until keys.length()).map(keys::getString), generated.catalogKeys)
        }
    }

    @Test
    fun generatedReadmeTablesMatchTheRuntimeRegistry() {
        val readme = locate("README.md").readText()
        val capabilityRows = readme.registryRows("ai-tools")
        val dependencyRows = readme.registryRows("dependencies")

        val expectedCapabilityRows = CapabilityRegistry.capabilities.map { capability ->
            "| **${capability.name}** | ${capability.engine} | ${capability.onDevice} |"
        }.toSet()
        val expectedDependencyRows = CapabilityRegistry.dependencies.map { dependency ->
            val purpose = if (dependency.publicStatus == "bundled") {
                dependency.purpose
            } else {
                "${dependency.purpose} (${dependency.publicStatus})"
            }
            "| ${dependency.label} | ${dependency.version} | $purpose |"
        }.toSet()

        assertEquals(expectedCapabilityRows, capabilityRows)
        assertEquals(expectedDependencyRows, dependencyRows)
    }

    @Test
    fun dependencyRegistryVersionsMatchTheVersionCatalog() {
        val versions = versionCatalog(locate("gradle/libs.versions.toml"))

        CapabilityRegistry.dependencies
            .filter { it.catalogKeys.isNotEmpty() }
            .forEach { dependency ->
                val expected = dependency.catalogKeys.joinToString(" / ") { key ->
                    versions[key] ?: error("Version catalog has no $key")
                }
                assertEquals("${dependency.id} version drift", expected, dependency.version)
            }
    }

    @Test
    fun aiCapabilityStatusMatchesTheRuntimeRequirement() {
        val capabilityToTool = mapOf(
            "auto_captions" to AiToolRequirements.Tool.AUTO_CAPTIONS,
            "background_removal" to AiToolRequirements.Tool.REMOVE_BACKGROUND,
            "ai_green_screen" to AiToolRequirements.Tool.AI_BACKGROUND,
            "object_removal" to AiToolRequirements.Tool.OBJECT_REMOVE,
            "video_upscaling" to AiToolRequirements.Tool.AI_UPSCALE,
            "frame_interpolation" to AiToolRequirements.Tool.FRAME_INTERP,
            "style_transfer" to AiToolRequirements.Tool.AI_STYLE,
            "stabilization" to AiToolRequirements.Tool.AI_STABILIZE,
            "tap_to_segment" to AiToolRequirements.Tool.TAP_SEGMENT,
            "audio_denoise" to AiToolRequirements.Tool.REDUCE_NOISE,
        )

        capabilityToTool.forEach { (capabilityId, tool) ->
            val capability = requireNotNull(CapabilityRegistry.capabilityFor(capabilityId))
            val requirement = requireNotNull(AiToolRequirements.requirementFor(tool.toolId))
            val expected = when (requirement.availability) {
                AiToolRequirements.Availability.READY -> "available" to "reachable"
                AiToolRequirements.Availability.MODEL_DOWNLOAD_REQUIRED -> "available" to "model_gated"
                AiToolRequirements.Availability.DEPENDENCY_MISSING -> "planned" to "dependency_missing"
                AiToolRequirements.Availability.CLOUD_OPT_IN -> "planned" to "not_reachable"
            }
            assertEquals("$capabilityId public status drift", expected.first, capability.status)
            assertEquals("$capabilityId reachability drift", expected.second, capability.reachability)
            assertTrue(
                "$capabilityId must name on-device availability",
                capability.onDevice == "Yes" || capability.onDevice.startsWith("Yes (") ||
                    requirement.availability == AiToolRequirements.Availability.DEPENDENCY_MISSING,
            )
        }
    }

    private fun org.json.JSONArray.objectsById(): Map<String, JSONObject> =
        (0 until length()).associate { index ->
            val entry = getJSONObject(index)
            entry.getString("id") to entry
        }

    private fun String.registryRows(key: String): Set<String> {
        val begin = "<!-- capability-registry:$key:begin -->"
        val end = "<!-- capability-registry:$key:end -->"
        return substringAfter(begin)
            .substringBefore(end)
            .lineSequence()
            .map(String::trim)
            .filter { line -> line.startsWith("|") && !line.startsWith("|-") }
            .drop(1)
            .toSet()
    }

    private fun versionCatalog(file: File): Map<String, String> {
        val versions = mutableMapOf<String, String>()
        var inVersions = false
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inVersions = trimmed == "[versions]"
            } else if (inVersions) {
                Regex("^([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]+)\"")
                    .find(trimmed)
                    ?.let { match -> versions[match.groupValues[1]] = match.groupValues[2] }
            }
        }
        return versions
    }

    private fun locate(relativePath: String): File = listOf(
        File(relativePath),
        File("../$relativePath"),
    ).firstOrNull(File::exists) ?: error("$relativePath not found")
}

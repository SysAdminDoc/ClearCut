package com.novacut.editor

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LintDetectorRatchetTest {

    @Test
    fun sourceDetectorsAreNotDisabledByTheToolchain() {
        val build = projectFile("app/build.gradle.kts").readText()
        assertFalse("Source lint detectors must run on the migrated toolchain.", "sourceDetectorCrashWorkarounds" in build)
        assertFalse("The old per-detector probe escape hatch must be removed.", "cleancutLintProbe" in build)
    }

    @Test
    fun dependencyUpgradeForcesWorkaroundReview() {
        val versions = projectFile("gradle/libs.versions.toml").readText()
        val snapshot = JSONObject(projectFile("scripts/dependency_freshness_snapshot.json").readText())
        val dependencies = snapshot.getJSONObject("dependencies")
        listOf("agp", "kotlin", "ksp", "composeBom", "lifecycle").forEach { name ->
            val entry = dependencies.getJSONObject(name)
            val version = entry.getString("pinnedVersion")
            assertTrue(
                "$name changed; independently re-audit the source lint detectors before updating this ratchet.",
                Regex("(?m)^$name\\s*=\\s*\"${Regex.escape(version)}\"$").containsMatchIn(versions),
            )
            assertTrue(
                "$name must keep the source-detector review marker in the freshness snapshot.",
                entry.getBoolean("lintReviewRequired"),
            )
            assertTrue(
                "$name must be upgraded through the executable compatibility probe.",
                entry.getJSONObject("compatibilityProbe")
                    .getString("command")
                    .contains("scripts/probe_dependency_upgrade.py"),
            )
        }
        assertTrue(
            "The freshness policy must require a compatibility probe before catalog changes.",
            snapshot.getJSONObject("policy").getBoolean("catalogChangesRequireProbe"),
        )
    }

    private fun projectFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }
        var directory: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = directory ?: error("Could not locate repository root")
            val candidate = File(current, relativePath)
            if (candidate.isFile) return candidate
            directory = current.parentFile
        }
        error("Could not locate $relativePath")
    }

}

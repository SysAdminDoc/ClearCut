package com.novacut.editor

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
        mapOf(
            "agp" to "9.1.1",
            "kotlin" to "2.4.10",
            "ksp" to "2.3.10",
            "composeBom" to "2026.06.00",
            "lifecycle" to "2.10.0",
        ).forEach { (name, version) ->
            assertTrue(
                "$name changed; independently re-audit the source lint detectors before updating this ratchet.",
                Regex("(?m)^$name\\s*=\\s*\"${Regex.escape(version)}\"$").containsMatchIn(versions),
            )
        }
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

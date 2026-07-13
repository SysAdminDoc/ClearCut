package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LintDetectorRatchetTest {

    @Test
    fun onlyCurrentlyCrashingSourceDetectorsRemainDisabled() {
        val build = projectFile("app/build.gradle.kts").readText()
        val workaroundBlock = build.substringAfter("val sourceDetectorCrashWorkarounds = listOf(")
            .substringBefore("val probeDetector")

        assertEquals(
            setOf(
                "NullSafeMutableLiveData",
                "FrequentlyChangingValue",
                "FlowOperatorInvokedInComposition",
            ),
            QUOTED_VALUE.findAll(workaroundBlock).map { it.groupValues[1] }.toSet(),
        )
        listOf(
            "RememberInComposition",
            "AutoboxingStateCreation",
            "UnrememberedMutableState",
        ).forEach { detector ->
            assertFalse("Passing detector was globally disabled again: $detector", detector in workaroundBlock)
        }
        assertTrue("Per-detector re-probe escape hatch was removed.", "filterNot { it == probeDetector }" in build)
    }

    @Test
    fun dependencyUpgradeForcesWorkaroundReview() {
        val versions = projectFile("gradle/libs.versions.toml").readText()
        mapOf(
            "agp" to "8.7.3",
            "kotlin" to "2.1.21",
            "composeBom" to "2026.06.00",
            "lifecycle" to "2.10.0",
        ).forEach { (name, version) ->
            assertTrue(
                "$name changed; independently re-run each cleancutLintProbe before updating this ratchet.",
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

    private companion object {
        val QUOTED_VALUE = Regex("\"([^\"]+)\"")
    }
}

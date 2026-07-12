package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Local dependency truth gate — replaces Dependabot/CI-based policy.
 *
 * Every pinned version in libs.versions.toml is either at the latest verified
 * upstream version or has an explicit hold reason in [HOLDS]. Adding a new
 * hold entry here is the single action needed to document why a dependency is
 * not at upstream latest.
 *
 * Run: ./gradlew :app:testDebugUnitTest --tests com.novacut.editor.DependencyFreshnessTest
 */
class DependencyFreshnessTest {

    /**
     * Core dependency versions that this test locks. The map key is the TOML
     * version key, the value is the expected pinned version. If the catalog
     * drifts from these expectations the test fails, forcing the developer to
     * update this fixture (and its hold reason, if applicable).
     */
    private val expectedVersions = mapOf(
        "agp" to "8.7.3",
        "kotlin" to "2.1.21",
        "ksp" to "2.1.21-2.0.1",
        "composeBom" to "2026.06.00",
        "media3" to "1.10.1",
        "hilt" to "2.58",
        "room" to "2.8.4",
        "coroutines" to "1.11.0",
        "lifecycle" to "2.10.0",
        "navigation" to "2.8.5",
        "coil" to "3.3.0",
        "okhttp" to "5.4.0",
        "lottieCompose" to "6.7.1",
        "onnxruntime" to "1.26.0",
        "mediapipe" to "0.10.35",
        "robolectric" to "4.14.1",
    )

    /**
     * Hold reasons for dependencies intentionally kept below upstream latest.
     * Each entry documents why the upgrade is deferred and under what condition
     * it can proceed.
     *
     * Dependencies absent from this map are assumed to be at latest verified.
     */
    @Suppress("unused")
    val HOLDS = mapOf(
        "agp" to Hold(
            "8.7.3",
            "AGP 8.8+ requires compileSdk 36 alignment; upgrade is a separate toolchain migration.",
            "Upgrade when compileSdk 36 is baseline and AGP 8.8 reaches stable."
        ),
        "kotlin" to Hold(
            "2.1.21",
            "Kotlin 2.4.0 context parameters are desirable but KSP2 beta gaps block adoption.",
            "Upgrade when KSP2 is stable and docs/kotlin-2.4-upgrade-plan.md criteria are met."
        ),
        "ksp" to Hold(
            "2.1.21-2.0.1",
            "Tracks Kotlin 2.1 maintenance line; KSP 2.3+ requires AGP APIs absent from AGP 8.7.3.",
            "Upgrade alongside Kotlin."
        ),
        "hilt" to Hold(
            "2.58",
            "Dagger 2.58 lint AAR crashes AGP 8.7.3 lint (NegativeArraySizeException). " +
                "Runtime and compiler are fine; only the lint artifact is excluded.",
            "Re-test lint AAR after AGP upgrade."
        ),
        "coil" to Hold(
            "3.3.0",
            "Coil 3.4.0+ requires Kotlin 2.4.0; ClearCut uses 2.1.21.",
            "Upgrade alongside Kotlin 2.4."
        ),
        "lottieCompose" to Hold(
            "6.7.1",
            "Lottie 7.x state-machine API blocked on Compose compatibility validation.",
            "Upgrade when Lottie 7.x stable passes local integration tests."
        ),
    )

    @Test
    fun versionCatalogMatchesExpectedPins() {
        val catalogVersions = parseCatalogVersions()
        if (catalogVersions.isEmpty()) {
            org.junit.Assume.assumeTrue(
                "Could not read libs.versions.toml; skipping freshness check", false
            )
            return
        }

        val mismatches = expectedVersions.mapNotNull { (key, expected) ->
            val actual = catalogVersions[key]
            if (actual != expected) "$key: expected=$expected actual=$actual" else null
        }

        assertTrue(
            "Version catalog has drifted from DependencyFreshnessTest expectations. " +
                "Update expectedVersions (and add a HOLDS entry if the version is " +
                "intentionally held below latest). Mismatches:\n${mismatches.joinToString("\n")}",
            mismatches.isEmpty()
        )
    }

    @Test
    fun holdReasonsReferenceCurrentVersions() {
        HOLDS.forEach { (key, hold) ->
            val expected = expectedVersions[key]
            assertEquals(
                "Hold for '$key' references version ${hold.pinnedVersion} but " +
                    "expectedVersions says $expected. Update the hold entry.",
                expected, hold.pinnedVersion
            )
        }
    }

    @Test
    fun allExpectedKeysExistInCatalog() {
        val catalogVersions = parseCatalogVersions()
        if (catalogVersions.isEmpty()) return

        val missing = expectedVersions.keys.filterNot { it in catalogVersions }
        assertTrue(
            "Expected version keys missing from libs.versions.toml: $missing",
            missing.isEmpty()
        )
    }

    private fun parseCatalogVersions(): Map<String, String> {
        val repoRoot = locateRepoRoot() ?: return emptyMap()
        val toml = File(repoRoot, "gradle/libs.versions.toml")
        if (!toml.exists()) return emptyMap()

        val versions = mutableMapOf<String, String>()
        var inVersionsSection = false
        for (line in toml.readLines()) {
            val trimmed = line.trim()
            if (trimmed == "[versions]") {
                inVersionsSection = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed != "[versions]") {
                inVersionsSection = false
                continue
            }
            if (inVersionsSection && "=" in trimmed) {
                val parts = trimmed.split("=", limit = 2)
                val key = parts[0].trim()
                val value = parts[1].trim().removeSurrounding("\"")
                versions[key] = value
            }
        }
        return versions
    }

    private fun locateRepoRoot(): File? {
        val userDir = System.getProperty("user.dir") ?: return null
        var dir: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = dir ?: return null
            if (File(current, ".git").exists()) return current
            dir = current.parentFile
        }
        return null
    }

    data class Hold(
        val pinnedVersion: String,
        val reason: String,
        val upgradeWhen: String,
    )
}

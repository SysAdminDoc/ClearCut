package com.novacut.editor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Offline dependency truth gate.
 *
 * The version catalog is intentionally pinned and the committed snapshot is
 * refreshed separately from authoritative Maven/release metadata. This test
 * never needs network access: it checks that every tracked pin has provenance,
 * a review date, and an executable compatibility path before a catalog change
 * can be treated as current.
 *
 * Refresh: `python scripts/refresh_dependency_freshness.py`
 * Probe: `python scripts/probe_dependency_upgrade.py --dependency <key> --version <candidate>`
 */
class DependencyFreshnessTest {

    private val trackedVersionKeys = setOf(
        "agp",
        "kotlin",
        "ksp",
        "composeBom",
        "media3",
        "hilt",
        "room",
        "coroutines",
        "coreKtx",
        "activity",
        "lifecycle",
        "navigation",
        "coil",
        "okhttp",
        "sqlite",
        "lottieCompose",
        "onnxruntime",
        "mediapipe",
        "protobufJavalite",
        "robolectric",
        "androidxBenchmark",
    )

    @Test
    fun sourceBackedSnapshotMatchesThePinnedCatalog() {
        val root = locateRepoRoot() ?: return
        val snapshotFile = File(root, "scripts/dependency_freshness_snapshot.json")
        assertTrue("Committed dependency freshness snapshot is missing.", snapshotFile.isFile)
        val snapshot = JSONObject(snapshotFile.readText())
        assertEquals(1, snapshot.getInt("schemaVersion"))
        assertTrue(
            "Refresh command must remain executable from the repository root.",
            snapshot.getString("refreshCommand").contains("scripts/refresh_dependency_freshness.py"),
        )
        assertTrue(
            "Catalog changes must be gated by the local compatibility probe.",
            snapshot.getJSONObject("policy").getBoolean("catalogChangesRequireProbe"),
        )
        assertEquals(
            "strict",
            snapshot.getJSONObject("policy").getString("dependencyVerification"),
        )
        val policy = snapshot.getJSONObject("policy")
        val reviewHorizonDays = policy.getInt("reviewHorizonDays")
        assertTrue("Review horizon must be a positive bounded window.", reviewHorizonDays in 1..90)
        assertTrue(
            "Offline freshness report command must remain executable.",
            policy.getString("offlineReportCommand").contains("scripts/check_dependency_freshness.py"),
        )
        val asOf = LocalDate.now(ZoneOffset.UTC)
        val snapshotReviewedOn = LocalDate.parse(snapshot.getString("reviewedOn"))
        val snapshotAgeDays = ChronoUnit.DAYS.between(snapshotReviewedOn, asOf)
        assertTrue("Snapshot review date cannot be in the future.", snapshotAgeDays >= 0)
        assertTrue(
            "Dependency freshness evidence is stale: reviewed $snapshotReviewedOn, as of $asOf, " +
                "horizon ${reviewHorizonDays}d. Run the offline report and refresh command.",
            snapshotAgeDays <= reviewHorizonDays,
        )

        val catalog = parseCatalogVersions(root)
        val dependencies = snapshot.getJSONObject("dependencies")
        trackedVersionKeys.forEach { key ->
            assertTrue("Snapshot is missing tracked dependency '$key'.", dependencies.has(key))
            val entry = dependencies.getJSONObject(key)
            val pinned = entry.getString("pinnedVersion")
            assertEquals("Snapshot pin for '$key' drifted from libs.versions.toml.", pinned, catalog[key])
            assertHttpUrl(entry.getString("source"), "$key release source")
            assertHttpUrl(entry.getString("metadataSource"), "$key metadata source")
            assertIsoDate(entry.getString("reviewedOn"), "$key review date")
            val entryReviewedOn = LocalDate.parse(entry.getString("reviewedOn"))
            val entryAgeDays = ChronoUnit.DAYS.between(entryReviewedOn, asOf)
            assertTrue(
                "$key evidence is stale: reviewed $entryReviewedOn, as of $asOf, " +
                    "horizon ${reviewHorizonDays}d.",
                entryAgeDays in 0..reviewHorizonDays,
            )

            val probe = entry.getJSONObject("compatibilityProbe")
            assertTrue(
                "$key must name the executable compatibility probe.",
                probe.getString("command").contains("scripts/probe_dependency_upgrade.py"),
            )
            val decision = entry.getJSONObject("candidateDecision")
            assertNonBlank(decision.getString("action"), "$key candidate decision")
            assertNonBlank(decision.getString("candidateVersion"), "$key candidate version")
            assertTrue(
                "$key candidate decision must retain an executable probe.",
                decision.getString("probe").contains("scripts/probe_dependency_upgrade.py"),
            )
            if (key == "androidxBenchmark") {
                assertEquals("pre-release", entry.getString("state"))
                assertEquals("retain-beta", decision.getString("action"))
                assertEquals("beta", decision.getString("releaseChannel"))
                assertTrue(decision.has("stableAlternative"))
            }
            if (probe.getString("status") == "passed") {
                assertEquals(
                    "A passing probe cannot describe a different catalog pin.",
                    pinned,
                    probe.getString("version"),
                )
            }
            if (entry.getString("state") != "current") {
                assertNonBlank(entry.getString("reason"), "$key hold reason")
                assertNonBlank(entry.getString("unblockCondition"), "$key unblock condition")
                assertTrue(
                    "$key unblock condition must be executable, not a vague reminder.",
                    entry.getString("unblockCondition").contains("scripts/probe_dependency_upgrade.py"),
                )
            }
            if (entry.getString("state") == "held") {
                assertEquals("Held candidates must declare a hold decision.", "hold", decision.getString("action"))
                assertTrue(
                    "Held candidate must differ from the pinned version until its probe passes.",
                    decision.getString("candidateVersion") != pinned,
                )
            }
        }
    }

    @Test
    fun knownUpstreamReleaseFactsStaySourceBacked() {
        val root = locateRepoRoot() ?: return
        val facts = JSONObject(
            File(root, "scripts/dependency_freshness_snapshot.json").readText()
        ).getJSONObject("facts")

        val agp = facts.getJSONObject("agp_9_2_0")
        assertEquals("9.2.0", agp.getString("version"))
        assertEquals("stable", agp.getString("status"))
        assertEquals("9.4.1", agp.getString("requiresGradle"))
        assertHttpUrl(agp.getString("source"), "AGP 9.2 source")

        val lifecycle = facts.getJSONObject("lifecycle_2_11_0")
        assertEquals("2.11.0", lifecycle.getString("version"))
        assertEquals("stable", lifecycle.getString("status"))
        assertEquals("9.2.0", lifecycle.getString("requiresAgp"))
        assertHttpUrl(lifecycle.getString("source"), "Lifecycle 2.11 source")

        val room = facts.getJSONObject("room3_0_0")
        assertEquals("3.0.0", room.getString("version"))
        assertEquals("stable", room.getString("status"))
        assertEquals("androidx.room3:room3-runtime", room.getString("coordinate"))
        assertHttpUrl(room.getString("source"), "Room 3 source")
    }

    @Test
    fun everyTrackedKeyExistsInTheVersionCatalog() {
        val root = locateRepoRoot() ?: return
        val catalog = parseCatalogVersions(root)
        val missing = trackedVersionKeys.filterNot { it in catalog }
        assertTrue(
            "Tracked dependency keys missing from libs.versions.toml: $missing",
            missing.isEmpty(),
        )
    }

    private fun parseCatalogVersions(root: File): Map<String, String> {
        val toml = File(root, "gradle/libs.versions.toml")
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

    private fun assertHttpUrl(value: String, label: String) {
        assertTrue("$label must be an HTTPS URL.", value.startsWith("https://"))
    }

    private fun assertIsoDate(value: String, label: String) {
        assertTrue("$label must use YYYY-MM-DD.", Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))
    }

    private fun assertNonBlank(value: String, label: String) {
        assertTrue("$label must not be blank.", value.trim().isNotEmpty())
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
}

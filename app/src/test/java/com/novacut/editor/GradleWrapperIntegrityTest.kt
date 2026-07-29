package com.novacut.editor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Gradle wrapper download is the first supply-chain step of any build, and
 * it runs before every other gate in this repository. Without a pinned
 * distribution checksum the wrapper accepts whatever the download produced.
 */
class GradleWrapperIntegrityTest {

    @Test
    fun theWrapperPinsItsDistributionChecksum() {
        val properties = locateWrapperProperties() ?: run {
            assumeTrue("gradle-wrapper.properties not reachable; skipping", false)
            return
        }
        val text = properties.readText()

        val checksum = Regex("""distributionSha256Sum=([0-9a-fA-F]{64})""").find(text)
        assertNotNull(
            "gradle/wrapper/gradle-wrapper.properties must pin distributionSha256Sum " +
                "(64 hex characters) so the wrapper cannot silently accept a substituted " +
                "Gradle distribution.",
            checksum
        )
    }

    @Test
    fun theWrapperValidatesItsDistributionUrl() {
        val properties = locateWrapperProperties() ?: return
        val text = properties.readText()

        assertTrue(
            "validateDistributionUrl must stay enabled",
            "validateDistributionUrl=true" in text
        )
        assertTrue(
            "the distribution must come from services.gradle.org over https",
            """distributionUrl=https\://services.gradle.org/""" in text
        )
    }

    private fun locateWrapperProperties(): File? {
        val userDir = System.getProperty("user.dir") ?: return null
        var dir: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = dir ?: return null
            val candidate = File(current, "gradle/wrapper/gradle-wrapper.properties")
            if (candidate.isFile) return candidate
            dir = current.parentFile
        }
        return null
    }
}

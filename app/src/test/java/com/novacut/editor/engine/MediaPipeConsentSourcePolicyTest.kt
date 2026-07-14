package com.novacut.editor.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P0 invariant: every MediaPipe Tasks constructor call site must be gated by
 * [MediaPipeUsageGate]. This scans source rather than trusting a hand-kept list,
 * so a newly-added `ImageSegmenter`/`FaceDetector` construction that forgets the
 * consent check fails the build.
 */
class MediaPipeConsentSourcePolicyTest {

    @Test
    fun everyMediaPipeTasksConstructorIsConsentGated() {
        val root = locateRepoRoot()
        val packageDir = File(root, "app/src/main/java/com/novacut/editor")
        val ktFiles = packageDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("Expected Kotlin sources to scan.", ktFiles.isNotEmpty())

        val constructorSites = ktFiles.filter { file ->
            val src = file.readText()
            TASKS_CONSTRUCTORS.any { it in src }
        }

        // We know the two engines that legitimately build Tasks objects. If this
        // set changes, the test forces a review of the new site.
        val siteNames = constructorSites.map { it.name }.toSet()
        assertTrue(
            "Expected SegmentationEngine + SmartReframeEngine to construct Tasks objects, found $siteNames",
            siteNames.containsAll(setOf("SegmentationEngine.kt", "SmartReframeEngine.kt")),
        )

        constructorSites.forEach { file ->
            val src = file.readText()
            assertTrue(
                "${file.relativeTo(root)} constructs a MediaPipe Tasks object but does not consult " +
                    "MediaPipeUsageGate.isConsented() before doing so.",
                CONSENT_CHECK in src,
            )
        }
    }

    private fun locateRepoRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }
        var directory: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = directory ?: error("Could not locate repository root")
            if (File(current, ".git").isDirectory) return current
            directory = current.parentFile
        }
        error("Could not locate repository root from $userDir")
    }

    private companion object {
        val TASKS_CONSTRUCTORS = listOf(
            "ImageSegmenter.createFromOptions",
            "FaceDetector.createFromOptions",
        )
        const val CONSENT_CHECK = "mediaPipeGate.isConsented()"
    }
}

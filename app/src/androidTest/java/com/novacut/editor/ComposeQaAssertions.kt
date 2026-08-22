package com.novacut.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import java.io.File

internal fun AndroidComposeTestRule<*, *>.waitUntilAtLeastOneExists(
    tag: String,
    timeoutMillis: Long = 20_000L,
) {
    try {
        waitUntil(timeoutMillis) {
            runCatching { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    } catch (error: Throwable) {
        throw failureWithQaDiagnostics("wait-$tag", error)
    }
}

internal fun AndroidComposeTestRule<*, *>.waitUntilNoNodesExist(
    tag: String,
    timeoutMillis: Long = 20_000L,
) {
    waitUntil(timeoutMillis) {
        runCatching {
            val hasRoot = onAllNodes(isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
            hasRoot && onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }.getOrDefault(false)
    }
}

internal fun AndroidComposeTestRule<*, *>.waitForComposeHierarchy(
    timeoutMillis: Long = 20_000L,
) {
    waitUntil(timeoutMillis) {
        runCatching {
            onAllNodes(isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }.getOrDefault(false)
    }
}

internal fun AndroidComposeTestRule<*, *>.assertAccessibilityChecksPass(
    artifactLabel: String = "accessibility",
) {
    waitForComposeHierarchy()
    val roots = onAllNodes(isRoot(), useUnmergedTree = true)
    val rootCount = roots.fetchSemanticsNodes(atLeastOneRootRequired = true).size
    try {
        repeat(rootCount) { index -> roots[index].tryPerformAccessibilityChecks() }
    } catch (error: Throwable) {
        throw failureWithQaDiagnostics(artifactLabel, error)
    }
}

internal fun AndroidComposeTestRule<*, *>.failureWithQaDiagnostics(
    artifactLabel: String,
    error: Throwable,
): AssertionError {
    val screenshot = runCatching { writeQaFailureScreenshot(artifactLabel) }
        .fold(
            onSuccess = File::getAbsolutePath,
            onFailure = { failure -> "unavailable (${failure.message})" },
        )
    val semanticPaths = runCatching {
        onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .joinToString(separator = "\n") { node -> node.accessibilityPath() }
    }.getOrElse { failure -> "unavailable (${failure.message})" }
    return AssertionError(
        "QA failure screenshot: $screenshot\nClickable semantic paths:\n$semanticPaths",
        error,
    )
}

private fun AndroidComposeTestRule<*, *>.writeQaFailureScreenshot(artifactLabel: String): File {
    val mediaRoot = activity.externalMediaDirs.firstOrNull() ?: activity.cacheDir
    val outputDirectory = File(mediaRoot, "additional_test_output").apply { mkdirs() }
    val safeLabel = artifactLabel.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val output = File(outputDirectory, "$safeLabel-${System.nanoTime()}.png")
    output.outputStream().use { stream ->
        val root = onAllNodes(isRoot(), useUnmergedTree = true)[0]
        check(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
    }
    return output
}

private fun SemanticsNode.accessibilityPath(): String {
    val ancestry = generateSequence(this) { node -> node.parent }
        .map { node -> "#${node.id}" }
        .toList()
        .asReversed()
        .joinToString(" > ")
    return "$ancestry bounds=$boundsInWindow semantics=$config"
}

package com.novacut.editor.engine

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Production logs must not carry raw user paths or URIs.
 *
 * A media path is frequently the file name of something personal, and `logcat`
 * is readable by paired debug tooling and by anything the user pastes into a bug
 * report. Every log site that needs to name an asset uses [RedactedLog], which
 * emits a stable digest instead.
 *
 * This scans the Kotlin sources rather than runtime output because the offending
 * lines only fire on failure paths that are hard to provoke.
 */
class RedactedLoggingRatchetTest {

    /** Identifiers whose interpolation into a log line would print a real path. */
    private val sensitiveNames = listOf(
        "uri", "sourceUri", "inputUri", "outputUri", "managedUri", "originalUri",
        "path", "filePath", "absolutePath", "file", "outputFile", "inputFile",
        "sourceFile", "targetFile", "displayName",
    )

    private val logCall = Regex("""AppLog\.[dviwe]\s*\(""")
    private val rawLogCall = Regex(
        """android\.util\.Log|\bLog\.(?:v|d|i|w|e|wtf|println|isLoggable)\s*\("""
    )

    @Test
    fun productionLoggingUsesOnlyTheAppLogSeam() {
        val sourceRoot = locateSourceRoot() ?: run {
            assumeTrue("Kotlin sources not reachable; skipping logging seam ratchet", false)
            return
        }

        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "AppLog.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (rawLogCall.containsMatchIn(line)) {
                        val relative = file.path.replace('\\', '/').substringAfter("java/")
                        offenders += "$relative:${index + 1} uses android.util.Log directly"
                    }
                }
            }

        assertTrue(
            "Production logging must route through AppLog. Offenders:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun noLogSiteInterpolatesARawPathOrUri() {
        val sourceRoot = locateSourceRoot() ?: run {
            assumeTrue("Kotlin sources not reachable; skipping redaction ratchet", false)
            return
        }

        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (!logCall.containsMatchIn(line)) return@forEachIndexed
                    val violation = sensitiveNames.firstOrNull { name -> line.leaksIdentifier(name) }
                    if (violation != null) {
                        val relative = file.path.replace('\\', '/').substringAfter("java/")
                        offenders += "$relative:${index + 1} interpolates raw '$violation'"
                    }
                }
            }

        assertTrue(
            "AppLog sites must redact user paths and URIs via RedactedLog " +
                "(uri.redacted(), file.redacted(), RedactedLog.path(...)). Offenders:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun theRatchetActuallyDetectsAViolation() {
        // Guards against the scan silently matching nothing after a refactor.
        assertTrue("""AppLog.w(TAG, "failed for ${'$'}uri")""".leaksIdentifier("uri"))
        assertTrue("""AppLog.e(TAG, "bad ${'$'}{file.absolutePath}")""".leaksIdentifier("absolutePath"))
        assertTrue(!"""AppLog.w(TAG, "failed for ${'$'}{uri.redacted()}")""".leaksIdentifier("uri"))
        assertTrue(!"""AppLog.w(TAG, "failed for ${'$'}{RedactedLog.path(uri)}")""".leaksIdentifier("uri"))
    }

    /**
     * True when [name] is interpolated into the line without going through
     * [RedactedLog]. `$uri` and `${uri.something}` leak; `${uri.redacted()}`,
     * `${RedactedLog.uri(uri)}`, and `${uri.length}`-style scalar reads do not.
     */
    private fun String.leaksIdentifier(name: String): Boolean {
        val bare = Regex("""\$$name\b""")
        val braced = Regex("""\$\{[^}]*\b$name\b[^}]*}""")
        if (bare.containsMatchIn(this)) return true
        return braced.findAll(this).any { match ->
            val expression = match.value
            when {
                "redacted()" in expression -> false
                "RedactedLog" in expression -> false
                // Non-identifying scalar reads off the same object are fine.
                Regex("""\b$name\.(length|size|exists\(\)|isFile|length\(\))""").containsMatchIn(expression) -> false
                else -> true
            }
        }
    }

    private fun locateSourceRoot(): File? {
        val userDir = System.getProperty("user.dir") ?: return null
        var dir: File? = File(userDir).absoluteFile
        repeat(6) {
            val current = dir ?: return null
            val candidate = File(current, "app/src/main/java/com/novacut/editor")
            if (candidate.isDirectory) return candidate
            dir = current.parentFile
        }
        return null
    }
}

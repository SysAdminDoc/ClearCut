package com.novacut.editor

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locks the tracked-file set so cross-project artefacts can't sneak into git.
 *
 * Context: the working tree on contributor machines may contain leftover docs
 * from sibling projects (most notably HostShield's DNS/blocklist research). They
 * are correctly gitignored, but a contributor running `git add -A` without
 * looking could still slip them in. This test runs `git ls-files` and fails if
 * any tracked path matches the forbidden patterns.
 *
 * The test is a no-op when `git` is not on PATH (e.g. unusual CI environments)
 * so it never blocks builds for the wrong reason.
 */
class TrackedFilesAuditTest {

    @Test
    fun trackedFilesDoNotIncludeCrossProjectPollution() {
        val repoRoot = locateRepoRoot()
            ?: error(
                "Could not locate the repository root. The audit walks up from the " +
                    "test working directory looking for the .git folder; adjust this " +
                    "resolver if the repo layout moves."
            )
        val tracked = gitLsFiles(repoRoot) ?: run {
            assumeTrue("git command unavailable; skipping tracked-files audit", false)
            return
        }

        val offenders = tracked.filter { path ->
            FORBIDDEN_PREFIXES.any { path.startsWith(it) }
        }

        assertTrue(
            "Tracked files include cross-project pollution. The following paths " +
                "must not be committed to NovaCut — they belong to a sibling project " +
                "(HostShield) and are blocked by .gitignore for that reason. Either " +
                "delete the file from the working tree or run `git rm --cached <path>`. " +
                "Offenders: $offenders",
            offenders.isEmpty()
        )
    }

    /**
     * Sanity check: the public build/release contract files must remain tracked.
     * Planning and research markdown are intentionally local-only; the README is
     * the only tracked markdown file in this repo.
     */
    @Test
    fun requiredPublicFilesRemainTracked() {
        val repoRoot = locateRepoRoot() ?: return
        val tracked = gitLsFiles(repoRoot)?.toSet() ?: run {
            assumeTrue("git command unavailable; skipping tracked-docs audit", false)
            return
        }

        val missing = REQUIRED_TRACKED.filterNot { it in tracked }
        assertTrue(
            "Required public files must stay tracked. Missing: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun markdownDocsStayLocalExceptReadme() {
        val repoRoot = locateRepoRoot() ?: return
        val tracked = gitLsFiles(repoRoot) ?: run {
            assumeTrue("git command unavailable; skipping tracked-markdown audit", false)
            return
        }

        val trackedPrivateMarkdown = tracked.filter { path ->
            path.endsWith(".md") && path != "README.md"
        }

        assertTrue(
            "README.md is the only tracked markdown file; private planning docs " +
                "must stay local-only. Tracked private markdown: $trackedPrivateMarkdown",
            trackedPrivateMarkdown.isEmpty()
        )
    }

    private fun gitLsFiles(repoRoot: File): List<String>? {
        return try {
            val process = ProcessBuilder("git", "ls-files")
                .directory(repoRoot)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readLines() }
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                output.filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
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

    companion object {
        private val FORBIDDEN_PREFIXES = listOf(
            "HostShield",
            "research/",
        )

        private val REQUIRED_TRACKED = listOf(
            "README.md",
            "LICENSE",
            ".github/workflows/build.yml",
            ".github/dependabot.yml",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "gradle/libs.versions.toml",
        )
    }
}

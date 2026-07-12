package com.novacut.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseSigningConfigTest {

    @Test
    fun signingPathsResolveFromTheRepositoryRoot() {
        val buildFile = locate("app/build.gradle.kts").readText()

        assertTrue(buildFile.contains("storeFile = rootProject.file(storePath)"))
        assertFalse(buildFile.contains("storeFile = file(storePath)"))
    }

    private fun locate(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::exists)
            ?: error("$relativePath not found")
}

package com.novacut.editor.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every control in Settings must move something.
 *
 * Eight of them used to persist a value to DataStore, render it back in the UI, and
 * be read by no consumer at all -- the user changed the setting and nothing happened.
 * This ratchet fails when an [AppSettings] property has no reader outside its own
 * definition and the settings screen that draws it, which is exactly the shape that
 * defect took.
 */
class SettingsConsumerRatchetTest {

    private companion object {
        /**
         * Not consumers: the data class itself, and the screen that renders each saved
         * value back to the user. SettingsViewModel *is* a consumer -- it runs the
         * diagnostics export and the update check on the user's behalf.
         */
        val NOT_A_CONSUMER = listOf(
            "engine/SettingsRepository.kt",
            "ui/settings/SettingsScreen.kt",
        )
    }

    @Test
    fun everySettingHasAConsumerBeyondTheSettingsScreen() {
        val sourceRoot = listOf(
            File("app/src/main/java/com/novacut/editor"),
            File("../app/src/main/java/com/novacut/editor"),
        ).first { it.isDirectory }

        val repository = File(sourceRoot, "engine/SettingsRepository.kt").readText()
        val properties = appSettingsProperties(repository)
        assertTrue("failed to parse AppSettings properties", properties.size > 15)

        val consumers = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file ->
                val path = file.path.replace('\\', '/')
                NOT_A_CONSUMER.any { path.endsWith(it) }
            }
            .map { it to it.readText() }
            .toList()

        val orphans = properties.filter { property ->
            val reference = Regex("\\.$property\\b")
            consumers.none { (_, text) -> reference.containsMatchIn(text) }
        }

        assertTrue(
            "These settings persist and render but nothing reads them, so changing them " +
                "does nothing. Wire each to a consumer or delete the key: $orphans",
            orphans.isEmpty()
        )
    }

    private fun appSettingsProperties(source: String): List<String> {
        val start = source.indexOf("data class AppSettings(")
        require(start >= 0) { "AppSettings not found" }
        val end = source.indexOf("\n)", start)
        return Regex("^\\s*val (\\w+):", RegexOption.MULTILINE)
            .findAll(source.substring(start, end))
            .map { it.groupValues[1] }
            .toList()
    }
}

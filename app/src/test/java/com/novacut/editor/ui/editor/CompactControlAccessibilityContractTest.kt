package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompactControlAccessibilityContractTest {

    @Test
    fun compact_effect_remove_action_keeps_a_48dp_button_surface() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/AudioMixerPanel.kt").readText()

        assertTrue(source.contains(".size(48.dp)"))
        assertTrue(source.contains("clickable(role = Role.Button, onClick = onRemove)"))
        assertTrue(source.contains("cd_mixer_remove_effect"))
        assertTrue(source.contains(".size(16.dp)"))
    }

    @Test
    fun radial_actions_keep_their_40dp_visual_inside_a_48dp_target() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/RadialActionMenu.kt").readText()

        assertTrue(source.contains(".size(48.dp)"))
        assertTrue(source.contains(".size(40.dp)"))
        assertTrue(source.contains("clickable(role = Role.Button)"))
        assertTrue(source.contains(".semantics { contentDescription = actionLabel }"))
    }

    @Test
    fun text_and_glow_swatches_are_localized_radio_choices() {
        val source = locate("app/src/main/java/com/novacut/editor/ui/editor/TextEditorSheet.kt").readText()

        assertTrue(source.contains("private val textColorOptions"))
        assertTrue(source.contains("private val glowColorOptions"))
        assertTrue(source.contains(".size(48.dp)"))
        assertTrue(source.contains("role = Role.RadioButton"))
        assertTrue(source.contains("contentDescription = optionLabel"))
        assertTrue(source.contains(".size(34.dp)"))
        assertTrue(source.contains(".size(26.dp)"))
    }

    private fun locate(relativePath: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relativePath)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

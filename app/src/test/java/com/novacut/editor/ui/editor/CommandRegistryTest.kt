package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRegistryTest {

    @Test
    fun fuzzyMatchFindsExactSubstring() {
        assertTrue(CommandRegistry.fuzzyMatch("split", "Split Clip"))
    }

    @Test
    fun fuzzyMatchIsCaseInsensitive() {
        assertTrue(CommandRegistry.fuzzyMatch("SPLIT", "Split Clip"))
        assertTrue(CommandRegistry.fuzzyMatch("split", "SPLIT CLIP"))
    }

    @Test
    fun fuzzyMatchFindsScatteredCharacters() {
        assertTrue(CommandRegistry.fuzzyMatch("smt", "Smart Crop"))
        assertTrue(CommandRegistry.fuzzyMatch("bg", "Background Removal"))
    }

    @Test
    fun fuzzyMatchRejectsNonMatchingQuery() {
        assertFalse(CommandRegistry.fuzzyMatch("xyz", "Split Clip"))
        assertFalse(CommandRegistry.fuzzyMatch("zz", "Trim"))
    }

    @Test
    fun fuzzyMatchEmptyQueryMatchesEverything() {
        assertTrue(CommandRegistry.fuzzyMatch("", "anything"))
        assertTrue(CommandRegistry.fuzzyMatch("  ", "anything"))
    }

    @Test
    fun allCommandsReturnsNonEmptyList() {
        val commands = CommandRegistry.allCommands()
        assertTrue("Expected at least 40 commands", commands.size >= 40)
    }

    @Test
    fun allCommandsHaveUniqueIds() {
        val commands = CommandRegistry.allCommands()
        val ids = commands.map { it.actionId }
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Duplicate action IDs: $dupes", dupes.isEmpty())
    }

    @Test
    fun clipRequiringCommandsAreTagged() {
        val commands = CommandRegistry.allCommands()
        val split = commands.find { it.actionId == "split" }
        assertTrue("split should require a clip", split?.requiresClip == true)
        val addMedia = commands.find { it.actionId == "add_media" }
        assertFalse("add_media should not require a clip", addMedia?.requiresClip == true)
    }

    @Test
    fun commandAvailabilityMatchesActionPreconditions() {
        val commands = CommandRegistry.allCommands().associateBy { it.actionId }

        assertTrue(commands.getValue("captions").requiresClip)
        assertFalse(commands.getValue("draw").requiresClip)
        assertFalse(commands.getValue("ai_hub").requiresClip)
        assertFalse(commands.getValue("cut_assistant").requiresClip)
        assertTrue(commands.getValue("bg_replace").requiresClip)
        assertTrue(commands.getValue("face_track").requiresClip)
        assertTrue(commands.getValue("frame_interp").requiresClip)
    }

    @Test
    fun easyModeUsesCurrentTabIdsAndKeepsMoreWorkbenchReachable() {
        assertEquals(
            setOf("edit", "audio", "text", "effects", "more"),
            visibleClipTabs(EditorMode.EASY).mapTo(mutableSetOf()) { it.id }
        )
    }

    @Test
    fun secondaryMotionToolsAreReachableAndProjectCaptionsAreNotOffered() {
        assertTrue(
            clipMoreActionIds().containsAll(
                setOf("transform", "keyframes", "pip", "chroma_key")
            )
        )
        assertFalse("incomplete mask editor must not be offered", "masks" in clipMoreActionIds())
        assertFalse("single-input blend filters must not be offered as compositing", "blend_mode" in clipMoreActionIds())
        assertFalse(CommandRegistry.allCommands().any { it.actionId in setOf("masks", "blend_mode") })
        assertFalse("captions require a clip", "captions" in projectTextActionIds())
    }
}

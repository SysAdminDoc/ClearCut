package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every path that restores an [AutoSaveState] into editor state must carry the
 * full set of persisted edit domains. v3.74.156 shipped with restoreSnapshot
 * silently dropping globalTransitions and importProjectBackup dropping both
 * globalTransitions and the AI usage ledger — user-authored edits lost on the
 * very flows meant to protect them. This scan keeps the restore blocks honest.
 */
class StateRestorationCoverageTest {

    private val source: String by lazy {
        val candidates = listOf(
            File("src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"),
            File("app/src/main/java/com/novacut/editor/ui/editor/EditorViewModel.kt"),
        )
        candidates.first { it.isFile }.readText()
    }

    private fun functionBody(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("EditorViewModel.$name not found", start >= 0)
        // Bounded excerpt: restore blocks live within the function's first
        // ~4000 chars; enough to cover the _state.update copy block.
        return source.substring(start, minOf(source.length, start + 4000))
    }

    @Test
    fun restoreSnapshot_restoresAllPersistedEditDomains() {
        val body = functionBody("restoreSnapshot")
        for (field in listOf(
            "tracks =", "textOverlays =", "imageOverlays =", "timelineMarkers =",
            "globalTransitions =", "chapterMarkers =", "drawingPaths =",
            "beatMarkers =", "usageLedger =", "storyboardCards =", "trackedObjects ="
        )) {
            assertTrue("restoreSnapshot must restore `$field`", field in body)
        }
    }

    @Test
    fun importProjectBackup_restoresAllPersistedEditDomains() {
        val body = functionBody("importProjectBackup")
        for (field in listOf(
            "textOverlays =", "imageOverlays =", "timelineMarkers =",
            "globalTransitions =", "chapterMarkers =", "drawingPaths =",
            "beatMarkers =", "usageLedger =", "storyboardCards =", "trackedObjects ="
        )) {
            assertTrue("importProjectBackup must restore `$field`", field in body)
        }
        assertTrue(
            "importProjectBackup must re-apply the TEXT-track invariant",
            "ensureEditorTracks(" in body
        )
    }
}

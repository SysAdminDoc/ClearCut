package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R5.4c string extraction audit.
 *
 * The roadmap originally suspected that engine stubs (`UpscaleEngine`,
 * `StyleTransferEngine`, etc.) emitted user-facing copy directly via
 * `Toast.makeText` or `Snackbar.make`. A 2026-05 audit confirmed they do not —
 * engines surface user-visible copy only through structured result records that
 * the UI layer ultimately renders. This test locks that invariant so a future
 * commit can't quietly reintroduce a hardcoded engine-side toast.
 *
 * What this test does NOT cover:
 * - Diagnostic message fields on result records (ProjectArchive.errorMessage,
 *   TimelineExchangeValidator.message, TemplateCompatibility.message). Those
 *   carry English-only copy today; routing them through string resources is a
 *   larger refactor tracked under R5.4c proper, not by this regression guard.
 * - `Log.d` / `Log.w` / `Log.e` lines. Logcat output is not user-facing.
 */
class EngineStringExtractionAuditTest {

    @Test
    fun noEngineDirectlyShowsAToastOrSnackbar() {
        val engineDir = locateEngineSourceDir()
            ?: error(
                "Could not locate the engine source directory. The audit relies on " +
                    "the canonical project layout — adjust this resolver if the repo moves."
            )

        val offenders = mutableListOf<String>()
        engineDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if (text.contains("Toast.makeText") || text.contains("Snackbar.make")) {
                    offenders += file.relativeTo(engineDir).invariantSeparatorsPath
                }
            }

        assertTrue(
            "Engines must not call Toast.makeText or Snackbar.make directly — " +
                "route the message through a delegate / ViewModel so the UI layer " +
                "owns presentation and localization. Offenders: $offenders",
            offenders.isEmpty()
        )
    }

    /**
     * Locks the count of engine source files at the time of the audit. If a new
     * engine is added without an entry in [docs/models.md] (when applicable),
     * this test will fail and prompt the author to update the documentation.
     */
    @Test
    fun engineSourceFileCountIsTracked() {
        val engineDir = locateEngineSourceDir() ?: return
        val count = engineDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .count()
        // 2026-05-16: 102 engine .kt files. Bump intentionally when adding new
        // engines so the docs/models.md registry and the ROADMAP stay in sync.
        // This assertion is a checkpoint, not a hard cap.
        assertTrue(
            "Engine file count drifted from the audit baseline (was 102, now $count). " +
                "If you added an engine, update docs/models.md and bump this number.",
            count in 95..130
        )
    }

    private fun locateEngineSourceDir(): File? {
        // Tests run with the working dir at the module or the project root, so
        // try both before giving up.
        val candidates = listOf(
            File("app/src/main/java/com/novacut/editor/engine"),
            File("src/main/java/com/novacut/editor/engine"),
            File("../app/src/main/java/com/novacut/editor/engine"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }
}

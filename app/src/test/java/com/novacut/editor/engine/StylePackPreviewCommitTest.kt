package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Previewing a `.ncstyle` document must not install it. Validation and
 * installation are separate operations; only the confirmed commit writes.
 */
@RunWith(RobolectricTestRunner::class)
class StylePackPreviewCommitTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = StylePackManager(context)
    private val packsDir get() = File(context.filesDir, "style_packs")

    @Test
    fun validationDoesNotInstall() {
        val result = manager.validateFromJson(validPackJson())

        assertNotNull("Valid pack should parse", result.pack)
        assertEquals(StylePackFailure.NONE, result.failure)
        assertFalse("Preview must not install", manager.isInstalled(PACK_ID))
        assertTrue(manager.listInstalledPacks().isEmpty())
    }

    @Test
    fun validationIsIdempotent() {
        val first = manager.validateFromJson(validPackJson())
        val second = manager.validateFromJson(validPackJson())

        assertEquals(first.pack?.id, second.pack?.id)
        assertEquals(first.warnings, second.warnings)
        assertFalse(manager.isInstalled(PACK_ID))
    }

    @Test
    fun commitInstallsWhatValidationApproved() {
        val validated = manager.validateFromJson(validPackJson())
        val installed = manager.importFromJson(validPackJson())

        assertEquals(validated.pack?.id, installed.pack?.id)
        assertTrue(manager.isInstalled(PACK_ID))
        assertEquals(listOf(PACK_ID), manager.listInstalledPacks().map { it.id })
    }

    @Test
    fun invalidPackWritesNothing() {
        assertEquals(StylePackFailure.INVALID_JSON, manager.validateFromJson("{not json").failure)
        assertEquals(StylePackFailure.EMPTY_STYLES, manager.validateFromJson(emptyStylesJson()).failure)
        assertEquals(StylePackFailure.INVALID_JSON, manager.importFromJson("{not json").failure)
        assertEquals(StylePackFailure.EMPTY_STYLES, manager.importFromJson(emptyStylesJson()).failure)

        assertFalse(packsDir.isDirectory && packsDir.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun previewWarnsBeforeReplacingAnInstalledPack() {
        manager.importFromJson(validPackJson())

        val replacementPreview = manager.validateFromJson(validPackJson(name = "Renamed"))

        assertTrue(
            "Preview should disclose the pending replacement",
            replacementPreview.warnings.any { it.contains("replace", ignoreCase = true) }
        )
        // …and the currently installed pack is untouched until commit.
        assertEquals("Sample Pack", manager.listInstalledPacks().single().name)
    }

    @Test
    fun traversalIdIsRejectedByValidationBeforeAnyWrite() {
        val result = manager.validateFromJson(validPackJson(id = "../../databases/room-projects"))

        assertEquals(StylePackFailure.MISSING_REQUIRED_FIELDS, result.failure)
        assertNull(result.pack)
    }

    private fun validPackJson(id: String = PACK_ID, name: String = "Sample Pack"): String = """
        {
          "schemaVersion": 1,
          "id": "$id",
          "name": "$name",
          "version": 1,
          "author": "ClearCut",
          "license": "MIT",
          "styles": [
            { "id": "style-a", "name": "Style A" },
            { "id": "style-b", "name": "Style B" }
          ]
        }
    """.trimIndent()

    private fun emptyStylesJson(): String = """
        {
          "schemaVersion": 1,
          "id": "$PACK_ID",
          "name": "Empty",
          "version": 1,
          "styles": []
        }
    """.trimIndent()

    private companion object {
        const val PACK_ID = "sample-pack"
    }
}

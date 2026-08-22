package com.novacut.editor.engine

import com.novacut.editor.model.ColorGrade
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EffectShareEnginePackTest {

    private val engine = EffectShareEngine(RuntimeEnvironment.getApplication())

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun legacyEffectPackRemainsReadableWithExplicitMigrationWarning() {
        val result = engine.validateEffectsJson(legacyRoot().toString())

        assertEquals(EffectShareEngine.EffectPackFailure.NONE, result.failure)
        assertNotNull(result.imported)
        assertEquals(DeclarativePackContract.LEGACY_SCHEMA_VERSION, result.schemaVersion)
        assertTrue(result.warnings.any { it.contains("migrated", ignoreCase = true) })
    }

    @Test
    fun currentEffectPackHashAndProvenanceAreVerified() {
        val root = currentRoot()
        val accepted = engine.validateEffectsJson(root.toString())

        assertEquals(EffectShareEngine.EffectPackFailure.NONE, accepted.failure)
        assertEquals("test export", accepted.provenanceSource)
        assertEquals("PACK_OK", accepted.reasonCode)
        assertEquals(setOf(DeclarativePackContract.EFFECT_PACK_CAPABILITY), accepted.requiredCapabilities)
        assertEquals(DeclarativePackContract.contentHash(root), accepted.contentHash)

        root.getJSONArray("effects").getJSONObject(0).put("enabled", false)
        val tampered = engine.validateEffectsJson(root.toString())
        assertEquals(EffectShareEngine.EffectPackFailure.HASH_MISMATCH, tampered.failure)
    }

    @Test
    fun currentEffectPackRejectsExecutableAndUnknownEntries() {
        val executable = currentRoot().put("metadata", JSONObject().put("script", "run"))
        assertEquals(
            EffectShareEngine.EffectPackFailure.UNSAFE_CONTENT,
            engine.validateEffectsJson(executable.toString()).failure,
        )

        val unknown = currentRoot()
            .put("effects", JSONArray().put(JSONObject().put("type", "UNKNOWN_EFFECT")))
        unknown.put("contentHash", DeclarativePackContract.contentHash(unknown))
        assertEquals(
            EffectShareEngine.EffectPackFailure.INVALID_ENTRY,
            engine.validateEffectsJson(unknown.toString()).failure,
        )
    }

    @Test
    fun futureEffectPackHasAnExplicitOutcome() {
        val future = currentRoot().put("schemaVersion", 99)
        assertEquals(
            EffectShareEngine.EffectPackFailure.INCOMPATIBLE_VERSION,
            engine.validateEffectsJson(future.toString()).failure,
        )
    }

    @Test
    fun currentEffectPackRejectsUnknownCapabilityAndNewerApp() {
        val unknown = currentRoot().put(
            "requiredCapabilities",
            JSONArray().put("future-feature-v9"),
        )
        unknown.put("contentHash", DeclarativePackContract.contentHash(unknown))
        val unknownResult = engine.validateEffectsJson(unknown.toString())
        assertEquals(EffectShareEngine.EffectPackFailure.UNKNOWN_REQUIRED_CAPABILITY, unknownResult.failure)
        assertEquals("PACK_UNKNOWN_REQUIRED_CAPABILITY", unknownResult.reasonCode)

        val newer = currentRoot().put("minAppVersion", "999.0.0")
        newer.put("contentHash", DeclarativePackContract.contentHash(newer))
        val newerResult = engine.validateEffectsJson(newer.toString())
        assertEquals(EffectShareEngine.EffectPackFailure.INCOMPATIBLE_APP_VERSION, newerResult.failure)
        assertEquals("PACK_INCOMPATIBLE_APP_VERSION", newerResult.reasonCode)
    }

    @Test
    fun exportAndImport_embeddedLutInstallsHashNamedCopy() = runBlocking {
        val source = temp.newFile("cinematic.cube")
        source.writeText(MINIMAL_CUBE, Charsets.UTF_8)
        var exported: File? = null
        var installed: File? = null
        try {
            val exportedFile = engine.exportEffects(
                name = "Portable grade",
                effects = emptyList(),
                colorGrade = ColorGrade(lutPath = source.absolutePath),
            ) ?: error("Expected effect export")
            exported = exportedFile
            val root = JSONObject(exportedFile.readText(Charsets.UTF_8))
            val colorGrade = root.getJSONObject("colorGrade")
            assertEquals(source.name, colorGrade.getString("lutFileName"))
            assertTrue(colorGrade.getString("lutBase64").isNotBlank())
            assertEquals(
                "hash=${root.getString("contentHash")} actual=${DeclarativePackContract.contentHash(root)}",
                root.getString("contentHash"),
                DeclarativePackContract.contentHash(root),
            )
            val validation = engine.validateEffectsJson(root.toString())
            assertEquals(EffectShareEngine.EffectPackFailure.NONE, validation.failure)

            val imported = engine.importEffects(exportedFile) ?: error("Expected effect import")
            val installedPath = imported.colorGrade?.lutPath ?: error("Expected imported LUT path")
            val installedFile = File(installedPath)
            installed = installedFile
            assertTrue(installedFile.name.startsWith("ncfx_"))
            assertArrayEquals(source.readBytes(), installedFile.readBytes())
            assertTrue(imported.embeddedLut == null)
        } finally {
            exported?.delete()
            installed?.delete()
        }
    }

    @Test
    fun currentEffectPackRejectsMalformedEmbeddedLut() {
        val root = currentRoot().put(
            "colorGrade",
            JSONObject()
                .put("lutFileName", "broken.cube")
                .put("lutBase64", "not-base64"),
        )
        root.put("contentHash", DeclarativePackContract.contentHash(root))

        assertEquals(
            EffectShareEngine.EffectPackFailure.INVALID_LUT,
            engine.validateEffectsJson(root.toString()).failure,
        )
    }

    private fun legacyRoot(): JSONObject = JSONObject().apply {
        put("name", "Legacy")
        put("version", 1)
        put("type", "clearcut_effects")
        put("effects", JSONArray().put(JSONObject().put("type", "BRIGHTNESS")))
    }

    private fun currentRoot(): JSONObject = JSONObject().apply {
        put("name", "Current")
        put("version", 1)
        put("type", "clearcut_effects")
        put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
        put("packType", DeclarativePackKind.EFFECT.wireName)
        put("provenance", JSONObject().put("source", "test export"))
        put("minAppVersion", "3.80.0")
        put("requiredCapabilities", JSONArray().put(DeclarativePackContract.EFFECT_PACK_CAPABILITY))
        put("effects", JSONArray().put(JSONObject().put("type", "BRIGHTNESS")))
        put("contentHash", DeclarativePackContract.contentHash(this))
    }

    private companion object {
        val MINIMAL_CUBE = """
            TITLE "Minimal"
            LUT_3D_SIZE 2
            0 0 0
            0 0 1
            0 1 0
            0 1 1
            1 0 0
            1 0 1
            1 1 0
            1 1 1
        """.trimIndent()
    }
}

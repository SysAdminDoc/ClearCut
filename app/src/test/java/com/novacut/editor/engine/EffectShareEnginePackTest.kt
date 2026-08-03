package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EffectShareEnginePackTest {

    private val engine = EffectShareEngine(RuntimeEnvironment.getApplication())

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
        put("effects", JSONArray().put(JSONObject().put("type", "BRIGHTNESS")))
        put("contentHash", DeclarativePackContract.contentHash(this))
    }
}

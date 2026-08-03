package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarativePackContractTest {

    @Test
    fun legacySchemaIsAcceptedWithMigrationWarning() {
        val envelope = DeclarativePackContract.inspect(
            JSONObject("""{"id":"pack","styles":[]}"""),
            DeclarativePackKind.STYLE,
        )

        assertEquals(DeclarativePackContract.LEGACY_SCHEMA_VERSION, envelope.schemaVersion)
        assertEquals(DeclarativePackIssue.NONE, envelope.issue)
        assertTrue(envelope.warnings.single().contains("migrated"))
    }

    @Test
    fun currentSchemaRequiresAndVerifiesContentHash() {
        val root = currentRoot()

        val accepted = DeclarativePackContract.inspect(root, DeclarativePackKind.STYLE)
        assertEquals(DeclarativePackIssue.NONE, accepted.issue)
        assertEquals(DeclarativePackContract.contentHash(root), accepted.contentHash)

        root.getJSONArray("styles").getJSONObject(0).put("name", "changed")
        val rejected = DeclarativePackContract.inspect(root, DeclarativePackKind.STYLE)
        assertEquals(DeclarativePackIssue.HASH_MISMATCH, rejected.issue)
    }

    @Test
    fun canonicalHashIgnoresObjectKeyOrderButNotPayload() {
        val first = currentRoot()
        val second = JSONObject().apply {
            put("provenance", JSONObject().put("source", "test"))
            put("styles", org.json.JSONArray(first.getJSONArray("styles").toString()))
            put("packType", "style")
            put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
            put("id", "pack")
        }

        assertEquals(DeclarativePackContract.contentHash(first), DeclarativePackContract.contentHash(second))
        second.getJSONArray("styles").getJSONObject(0).put("id", "other")
        assertNotEquals(DeclarativePackContract.contentHash(first), DeclarativePackContract.contentHash(second))
    }

    @Test
    fun futureAndWrongKindSchemasAreBlocked() {
        val invalid = currentRoot().put("schemaVersion", "two")
        assertEquals(
            DeclarativePackIssue.INVALID_SCHEMA,
            DeclarativePackContract.inspect(invalid, DeclarativePackKind.STYLE).issue,
        )

        val future = currentRoot().put("schemaVersion", 99)
        assertEquals(
            DeclarativePackIssue.FUTURE_SCHEMA,
            DeclarativePackContract.inspect(future, DeclarativePackKind.STYLE).issue,
        )

        val wrongKind = currentRoot().put("packType", "effect")
        assertEquals(
            DeclarativePackIssue.WRONG_KIND,
            DeclarativePackContract.inspect(wrongKind, DeclarativePackKind.STYLE).issue,
        )
    }

    @Test
    fun executableFieldsAreRejectedAtAnyDepth() {
        val root = currentRoot().put("metadata", JSONObject().put("script", "run-this"))
        assertEquals(
            DeclarativePackIssue.EXECUTABLE_CONTENT,
            DeclarativePackContract.inspect(root, DeclarativePackKind.STYLE).issue,
        )
    }

    private fun currentRoot(): JSONObject = JSONObject().apply {
        put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
        put("packType", "style")
        put("provenance", JSONObject().put("source", "test"))
        put("id", "pack")
        put("styles", org.json.JSONArray().put(JSONObject().put("id", "style-a")))
        put("contentHash", DeclarativePackContract.contentHash(this))
    }
}

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
    fun currentSchemaRequiresCompatibilityManifest() {
        val root = currentRoot()
        root.remove("minAppVersion")

        val rejected = DeclarativePackContract.inspect(root, DeclarativePackKind.STYLE)

        assertEquals(DeclarativePackIssue.MISSING_MANIFEST_FIELDS, rejected.issue)
        assertEquals("PACK_MISSING_MANIFEST_FIELDS", rejected.reasonCode)
    }

    @Test
    fun unknownCapabilitiesAndNewerAppVersionsAreRejected() {
        val unknown = currentRoot().put(
            "requiredCapabilities",
            org.json.JSONArray().put("future-feature-v9"),
        )
        unknown.put("contentHash", DeclarativePackContract.contentHash(unknown))
        assertEquals(
            DeclarativePackIssue.UNKNOWN_REQUIRED_CAPABILITY,
            DeclarativePackContract.inspect(unknown, DeclarativePackKind.STYLE).issue,
        )

        val newer = currentRoot().put("minAppVersion", "999.0.0")
        newer.put("contentHash", DeclarativePackContract.contentHash(newer))
        val incompatible = DeclarativePackContract.inspect(
            newer,
            DeclarativePackKind.STYLE,
            supportedAppVersion = "3.80.0",
        )
        assertEquals(DeclarativePackIssue.INCOMPATIBLE_APP_VERSION, incompatible.issue)
        assertEquals("PACK_INCOMPATIBLE_APP_VERSION", incompatible.reasonCode)
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
            put("minAppVersion", "3.80.0")
            put("requiredCapabilities", org.json.JSONArray().put(DeclarativePackContract.STYLE_PACK_CAPABILITY))
        }

        assertEquals(DeclarativePackContract.contentHash(first), DeclarativePackContract.contentHash(second))
        second.getJSONArray("styles").getJSONObject(0).put("id", "other")
        assertNotEquals(DeclarativePackContract.contentHash(first), DeclarativePackContract.contentHash(second))
    }

    @Test
    fun canonicalHashSurvivesJsonRoundTripForFloatingPointPayloads() {
        val root = JSONObject().apply {
            put("schemaVersion", DeclarativePackContract.CURRENT_SCHEMA_VERSION)
            put("packType", "effect")
            put("provenance", JSONObject().put("source", "test"))
            put("value", 0.0)
        }
        val serialized = root.toString()

        assertEquals(
            DeclarativePackContract.contentHash(root),
            DeclarativePackContract.contentHash(JSONObject(serialized)),
        )
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
        put("minAppVersion", "3.80.0")
        put("requiredCapabilities", org.json.JSONArray().put(DeclarativePackContract.STYLE_PACK_CAPABILITY))
        put("id", "pack")
        put("styles", org.json.JSONArray().put(JSONObject().put("id", "style-a")))
        put("contentHash", DeclarativePackContract.contentHash(this))
    }
}

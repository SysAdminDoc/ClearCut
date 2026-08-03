package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Shared envelope rules for local-first packs. A pack is data, never a plugin:
 * the importer accepts only JSON values interpreted by an existing ClearCut
 * engine and rejects fields that could be used to smuggle executable payloads.
 */
enum class DeclarativePackKind(val wireName: String) {
    STYLE("style"),
    EFFECT("effect"),
    LUT("lut"),
    FONT("font"),
}

enum class DeclarativePackIssue {
    NONE,
    INVALID_SCHEMA,
    FUTURE_SCHEMA,
    WRONG_KIND,
    MISSING_CONTENT_HASH,
    HASH_MISMATCH,
    EXECUTABLE_CONTENT,
}

data class DeclarativePackEnvelope(
    val schemaVersion: Int,
    val kind: DeclarativePackKind,
    val issue: DeclarativePackIssue = DeclarativePackIssue.NONE,
    val warnings: List<String> = emptyList(),
    val contentHash: String? = null,
    val source: String? = null,
)

object DeclarativePackContract {
    const val CURRENT_SCHEMA_VERSION = 2
    const val LEGACY_SCHEMA_VERSION = 1

    private val executableKeyMarkers = setOf(
        "code",
        "command",
        "dex",
        "entrypoint",
        "executable",
        "jar",
        "native",
        "plugin",
        "script",
        "wasm",
    )

    /** Inspect the shared envelope before a type-specific parser runs. */
    fun inspect(root: JSONObject, expectedKind: DeclarativePackKind): DeclarativePackEnvelope {
        val rawSchema = root.opt("schemaVersion")
        val schemaVersion = when {
            rawSchema == null || rawSchema == JSONObject.NULL -> LEGACY_SCHEMA_VERSION
            rawSchema is Number && rawSchema.toDouble().isFinite() && rawSchema.toDouble() % 1.0 == 0.0 ->
                rawSchema.toInt()
            else -> 0
        }
        val rawKind = root.optString("packType", "").trim()
        val kind = rawKind.ifEmpty { expectedKind.wireName }
        val parsedKind = DeclarativePackKind.entries.firstOrNull { it.wireName == kind }
            ?: expectedKind
        val source = root.optJSONObject("provenance")
            ?.optString("source", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val unsafeKey = findExecutableKey(root)
        if (unsafeKey != null) {
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = parsedKind,
                issue = DeclarativePackIssue.EXECUTABLE_CONTENT,
                warnings = listOf("Executable pack field rejected: $unsafeKey"),
                contentHash = root.optString("contentHash", "").takeIf { it.isNotBlank() },
                source = source,
            )
        }
        if (schemaVersion <= 0) {
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = parsedKind,
                issue = DeclarativePackIssue.INVALID_SCHEMA,
                warnings = listOf("Pack schemaVersion must be a positive integer."),
                contentHash = root.optString("contentHash", "").takeIf { it.isNotBlank() },
                source = source,
            )
        }
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = parsedKind,
                issue = DeclarativePackIssue.FUTURE_SCHEMA,
                warnings = listOf(
                    "Pack uses schema v$schemaVersion; this app supports up to v$CURRENT_SCHEMA_VERSION."
                ),
                contentHash = root.optString("contentHash", "").takeIf { it.isNotBlank() },
                source = source,
            )
        }
        if (schemaVersion >= CURRENT_SCHEMA_VERSION && rawKind != expectedKind.wireName) {
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = parsedKind,
                issue = DeclarativePackIssue.WRONG_KIND,
                warnings = listOf("Expected a ${expectedKind.wireName} pack, received $kind."),
                contentHash = root.optString("contentHash", "").takeIf { it.isNotBlank() },
                source = source,
            )
        }
        if (schemaVersion >= CURRENT_SCHEMA_VERSION) {
            val expectedHash = root.optString("contentHash", "").trim().lowercase()
            if (!isSha256(expectedHash)) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.MISSING_CONTENT_HASH,
                    warnings = listOf("Current-schema packs must carry a SHA-256 contentHash."),
                    source = source,
                )
            }
            val actualHash = contentHash(root)
            if (actualHash != expectedHash) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.HASH_MISMATCH,
                    warnings = listOf("Pack contentHash does not match its declarative payload."),
                    contentHash = expectedHash,
                    source = source,
                )
            }
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = expectedKind,
                contentHash = actualHash,
                source = source,
            )
        }
        return DeclarativePackEnvelope(
            schemaVersion = schemaVersion,
            kind = expectedKind,
            warnings = listOf(
                "Legacy schema v$schemaVersion will be migrated to v$CURRENT_SCHEMA_VERSION on install."
            ),
            contentHash = contentHash(root),
            source = source,
        )
    }

    /** Hash the canonical JSON payload, excluding mutable envelope metadata. */
    fun contentHash(root: JSONObject): String {
        val canonical = canonicalJson(root, excludedKeys = setOf("contentHash", "installedAtEpochMs"))
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun isSha256(value: String): Boolean = value.length == 64 && value.all { it in "0123456789abcdef" }

    private fun findExecutableKey(value: Any?): String? {
        return when (value) {
            is JSONObject -> value.keys().asSequence().sorted().firstNotNullOfOrNull { key ->
                val normalized = key.filter(Char::isLetterOrDigit).lowercase()
                if (normalized in executableKeyMarkers || executableKeyMarkers.any(normalized::contains)) {
                    key
                } else {
                    findExecutableKey(value.opt(key))
                }
            }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index ->
                findExecutableKey(value.opt(index))
            }
            else -> null
        }
    }

    private fun canonicalJson(value: Any?, excludedKeys: Set<String>): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys()
                .asSequence()
                .filterNot { it in excludedKeys }
                .sorted()
                .joinToString(prefix = "{", postfix = "}") { key ->
                    "${JSONObject.quote(key)}:${canonicalJson(value.opt(key), excludedKeys)}"
                }
            is JSONArray -> (0 until value.length())
                .joinToString(prefix = "[", postfix = "]") { index ->
                    canonicalJson(value.opt(index), excludedKeys)
                }
            is String -> JSONObject.quote(value)
            is Boolean, is Number -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }
}

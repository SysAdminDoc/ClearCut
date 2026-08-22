package com.novacut.editor.engine

import com.novacut.editor.BuildConfig
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
    STABILIZATION_PROFILE("stabilization-profile"),
}

enum class DeclarativePackIssue(val reasonCode: String) {
    NONE("PACK_OK"),
    INVALID_SCHEMA("PACK_INVALID_SCHEMA"),
    FUTURE_SCHEMA("PACK_FUTURE_SCHEMA"),
    WRONG_KIND("PACK_WRONG_KIND"),
    MISSING_MANIFEST_FIELDS("PACK_MISSING_MANIFEST_FIELDS"),
    UNKNOWN_REQUIRED_CAPABILITY("PACK_UNKNOWN_REQUIRED_CAPABILITY"),
    INCOMPATIBLE_APP_VERSION("PACK_INCOMPATIBLE_APP_VERSION"),
    MISSING_CONTENT_HASH("PACK_MISSING_CONTENT_HASH"),
    HASH_MISMATCH("PACK_HASH_MISMATCH"),
    EXECUTABLE_CONTENT("PACK_EXECUTABLE_CONTENT");

}

data class DeclarativePackEnvelope(
    val schemaVersion: Int,
    val kind: DeclarativePackKind,
    val issue: DeclarativePackIssue = DeclarativePackIssue.NONE,
    val warnings: List<String> = emptyList(),
    val contentHash: String? = null,
    val source: String? = null,
    val minAppVersion: String? = null,
    val requiredCapabilities: Set<String> = emptySet(),
) {
    val reasonCode: String
        get() = issue.reasonCode
}

object DeclarativePackContract {
    const val CURRENT_SCHEMA_VERSION = 3
    const val LEGACY_SCHEMA_VERSION = 1
    const val EFFECT_PACK_CAPABILITY = "effect-pack-v1"
    const val STYLE_PACK_CAPABILITY = "style-pack-v1"
    const val LUT_PACK_CAPABILITY = "lut-pack-v1"
    const val FONT_PACK_CAPABILITY = "font-pack-v1"
    const val EMBEDDED_LUT_CAPABILITY = "embedded-lut-v1"
    const val AUDIO_EFFECTS_CAPABILITY = "audio-effects-v1"
    const val STABILIZATION_PROFILE_CAPABILITY = "stabilization-profile-v1"

    val supportedCapabilities: Set<String> = setOf(
        EFFECT_PACK_CAPABILITY,
        STYLE_PACK_CAPABILITY,
        LUT_PACK_CAPABILITY,
        FONT_PACK_CAPABILITY,
        EMBEDDED_LUT_CAPABILITY,
        AUDIO_EFFECTS_CAPABILITY,
        STABILIZATION_PROFILE_CAPABILITY,
    )

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
    fun inspect(
        root: JSONObject,
        expectedKind: DeclarativePackKind,
        supportedAppVersion: String = BuildConfig.VERSION_NAME,
        capabilities: Set<String> = DeclarativePackContract.supportedCapabilities,
    ): DeclarativePackEnvelope {
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
        val minAppVersion = root.optString("minAppVersion", "").trim().takeIf { it.isNotEmpty() }
        val requiredCapabilities = root.optJSONArray("requiredCapabilities")
            ?.let { array ->
                buildSet {
                    for (index in 0 until array.length()) {
                        array.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
            }
            .orEmpty()
        val unsafeKey = findExecutableKey(root)
        if (unsafeKey != null) {
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = parsedKind,
                issue = DeclarativePackIssue.EXECUTABLE_CONTENT,
                warnings = listOf("Executable pack field rejected: $unsafeKey"),
                contentHash = root.optString("contentHash", "").takeIf { it.isNotBlank() },
                source = source,
                minAppVersion = minAppVersion,
                requiredCapabilities = requiredCapabilities,
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
                minAppVersion = minAppVersion,
                requiredCapabilities = requiredCapabilities,
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
                minAppVersion = minAppVersion,
                requiredCapabilities = requiredCapabilities,
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
                minAppVersion = minAppVersion,
                requiredCapabilities = requiredCapabilities,
            )
        }
        if (schemaVersion >= CURRENT_SCHEMA_VERSION) {
            val manifestMissing = minAppVersion == null ||
                root.optJSONArray("requiredCapabilities") == null ||
                requiredCapabilities.isEmpty() ||
                source == null
            if (manifestMissing) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.MISSING_MANIFEST_FIELDS,
                    warnings = listOf(
                        "Current-schema packs must declare minAppVersion, requiredCapabilities, and provenance.source."
                    ),
                    source = source,
                    minAppVersion = minAppVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            }
            val unknownCapability = requiredCapabilities.firstOrNull { it !in capabilities }
            if (unknownCapability != null) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.UNKNOWN_REQUIRED_CAPABILITY,
                    warnings = listOf("Pack requires unsupported capability '$unknownCapability'."),
                    source = source,
                    minAppVersion = minAppVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            }
            if (!isVersionAtLeast(supportedAppVersion, minAppVersion)) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.INCOMPATIBLE_APP_VERSION,
                    warnings = listOf(
                        "Pack requires ClearCut $minAppVersion or newer; this build is $supportedAppVersion."
                    ),
                    source = source,
                    minAppVersion = minAppVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            }
            val expectedHash = root.optString("contentHash", "").trim().lowercase()
            if (!isSha256(expectedHash)) {
                return DeclarativePackEnvelope(
                    schemaVersion = schemaVersion,
                    kind = expectedKind,
                    issue = DeclarativePackIssue.MISSING_CONTENT_HASH,
                    warnings = listOf("Current-schema packs must carry a SHA-256 contentHash."),
                    source = source,
                    minAppVersion = minAppVersion,
                    requiredCapabilities = requiredCapabilities,
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
                    minAppVersion = minAppVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            }
            return DeclarativePackEnvelope(
                schemaVersion = schemaVersion,
                kind = expectedKind,
                contentHash = actualHash,
                source = source,
                minAppVersion = minAppVersion,
                requiredCapabilities = requiredCapabilities,
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
            minAppVersion = minAppVersion,
            requiredCapabilities = requiredCapabilities,
        )
    }

    private fun isVersionAtLeast(current: String, minimum: String?): Boolean {
        if (minimum.isNullOrBlank()) return false
        val currentParts = parseVersion(current) ?: return false
        val minimumParts = parseVersion(minimum) ?: return false
        val size = maxOf(currentParts.size, minimumParts.size)
        for (index in 0 until size) {
            val actual = currentParts.getOrNull(index) ?: 0
            val required = minimumParts.getOrNull(index) ?: 0
            if (actual != required) return actual > required
        }
        return true
    }

    private fun parseVersion(value: String): List<Int>? =
        Regex("^\\d+(?:\\.\\d+){0,3}(?:[-+][0-9A-Za-z.-]+)?$")
            .takeIf { it.matches(value.trim()) }
            ?.let {
                value.trim()
                    .substringBefore('-')
                    .substringBefore('+')
                    .split('.')
                    .map(String::toInt)
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
            is Boolean -> value.toString()
            is Number -> JSONObject.numberToString(value)
            else -> JSONObject.quote(value.toString())
        }
    }
}

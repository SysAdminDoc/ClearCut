package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal const val MAX_MEDIA_ASSET_NOTES_CHARS = 2_000
internal const val MAX_MEDIA_ASSET_TAGS = 16
internal const val MAX_MEDIA_ASSET_TAG_CHARS = 32

internal fun normalizeMediaAssetNotes(value: String): String {
    return value.replace('\u0000', ' ').trim().take(MAX_MEDIA_ASSET_NOTES_CHARS)
}

internal fun normalizeMediaAssetTags(values: Iterable<String>): List<String> {
    return values
        .flatMap { it.split(',') }
        .asSequence()
        .map { tag ->
            tag.replace('\u0000', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
                .removePrefix("#")
                .trim()
                .take(MAX_MEDIA_ASSET_TAG_CHARS)
        }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedBy { it.lowercase(Locale.ROOT) }
        .take(MAX_MEDIA_ASSET_TAGS)
        .toList()
}

internal fun mediaAssetTagsToJson(tags: Iterable<String>): JSONArray {
    return JSONArray().apply {
        normalizeMediaAssetTags(tags).forEach(::put)
    }
}

internal fun mediaAssetTagsFromJson(json: JSONObject, key: String = "tags"): List<String> {
    val values = json.optJSONArray(key) ?: return emptyList()
    return normalizeMediaAssetTags(
        (0 until values.length()).mapNotNull { index ->
            values.optString(index).takeIf { it.isNotBlank() }
        }
    )
}

internal fun encodeMediaAssetTags(tags: Iterable<String>): String =
    mediaAssetTagsToJson(tags).toString()

internal fun decodeMediaAssetTags(raw: String): List<String> {
    return runCatching {
        val values = JSONArray(raw)
        normalizeMediaAssetTags(
            (0 until values.length()).mapNotNull { index ->
                values.optString(index).takeIf { it.isNotBlank() }
            }
        )
    }.getOrDefault(emptyList())
}

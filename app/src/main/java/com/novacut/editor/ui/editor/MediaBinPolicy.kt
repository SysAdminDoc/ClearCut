package com.novacut.editor.ui.editor

import com.novacut.editor.engine.MediaRelinkProbe
import java.util.Locale

enum class MediaBinFilter {
    ALL,
    MISSING,
    RELINK_NEEDED,
    USED,
    UNUSED,
    TAGGED,
}

enum class MediaBinSort {
    STATUS,
    NAME,
    SIZE,
    DURATION,
    USAGE,
}

data class MediaBinQuery(
    val search: String = "",
    val filter: MediaBinFilter = MediaBinFilter.ALL,
    val sort: MediaBinSort = MediaBinSort.STATUS,
)

internal fun filterAndSortMediaAssets(
    assets: List<MediaAsset>,
    query: MediaBinQuery,
): List<MediaAsset> {
    val search = query.search.trim().lowercase(Locale.ROOT)
    val filtered = assets.asSequence()
        .filter { asset ->
            search.isBlank() || listOf(
                asset.fileName,
                asset.uri.toString(),
                asset.notes,
                asset.tags.joinToString(" "),
            ).any { value -> value.lowercase(Locale.ROOT).contains(search) }
        }
        .filter { asset ->
            when (query.filter) {
                MediaBinFilter.ALL -> true
                MediaBinFilter.MISSING -> !asset.isAccessible
                MediaBinFilter.RELINK_NEEDED ->
                    asset.relinkState != MediaRelinkProbe.RelinkState.OK
                MediaBinFilter.USED -> asset.usedInClipIds.isNotEmpty()
                MediaBinFilter.UNUSED -> asset.usedInClipIds.isEmpty()
                MediaBinFilter.TAGGED -> asset.tags.isNotEmpty()
            }
        }
        .toList()

    val statusRank = { asset: MediaAsset ->
        when (asset.relinkState) {
            MediaRelinkProbe.RelinkState.MISSING -> 0
            MediaRelinkProbe.RelinkState.UNKNOWN -> 1
            MediaRelinkProbe.RelinkState.OK -> 2
        }
    }
    val comparator = when (query.sort) {
        MediaBinSort.STATUS -> compareBy<MediaAsset> { statusRank(it) }
            .thenByDescending { it.usedInClipIds.size }
        MediaBinSort.NAME -> compareBy<MediaAsset> { it.fileName.lowercase(Locale.ROOT) }
        MediaBinSort.SIZE -> compareByDescending<MediaAsset> { it.fileSize }
        MediaBinSort.DURATION -> compareByDescending<MediaAsset> { it.durationMs }
        MediaBinSort.USAGE -> compareByDescending<MediaAsset> { it.usedInClipIds.size }
    }
    return filtered.sortedWith(comparator.thenBy { it.assetId })
}

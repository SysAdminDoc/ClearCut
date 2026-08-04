package com.novacut.editor.engine

/** Ordering choices exposed by the multi-asset starter-sequence review. */
enum class MediaSequenceOrder {
    CAPTURE_TIME,
    NAME,
    MANUAL,
}

/** Metadata needed to order a picked asset without opening or decoding it. */
data class MediaSequenceCandidate(
    val key: String,
    val displayName: String,
    val captureTimeMs: Long?,
)

/**
 * Return a new list in the requested order. Unknown capture times sort after
 * known times and use the name as a deterministic tie-breaker.
 */
fun orderMediaSequence(
    candidates: List<MediaSequenceCandidate>,
    order: MediaSequenceOrder,
): List<MediaSequenceCandidate> = when (order) {
    MediaSequenceOrder.CAPTURE_TIME -> candidates.sortedWith(
        compareBy<MediaSequenceCandidate> { it.captureTimeMs ?: Long.MAX_VALUE }
            .thenBy { naturalMediaNameKey(it.displayName) }
            .thenBy { it.key }
    )
    MediaSequenceOrder.NAME -> candidates.sortedWith(
        compareBy<MediaSequenceCandidate> { naturalMediaNameKey(it.displayName) }
            .thenBy { it.captureTimeMs ?: Long.MAX_VALUE }
            .thenBy { it.key }
    )
    MediaSequenceOrder.MANUAL -> candidates.toList()
}

/** Move one candidate while retaining every item exactly once. */
fun moveMediaSequenceItem(
    candidates: List<MediaSequenceCandidate>,
    fromIndex: Int,
    toIndex: Int,
): List<MediaSequenceCandidate> {
    if (fromIndex !in candidates.indices || toIndex !in candidates.indices || fromIndex == toIndex) {
        return candidates.toList()
    }
    return candidates.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private val MEDIA_NAME_DIGITS = Regex("\\d+")

private fun naturalMediaNameKey(name: String): String =
    MEDIA_NAME_DIGITS.replace(name) { it.value.padStart(10, '0') }.lowercase()

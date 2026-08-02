package com.novacut.editor.engine

/**
 * Budget for the bitmap lists held directly by the Compose timeline.
 *
 * VideoEngine's LRU cannot reclaim a bitmap while the timeline still owns it
 * in its state map. Keep that second strong-reference set below half of the
 * automatic thumbnail budget so the cache and the visible strips share a
 * bounded envelope instead of multiplying the heap/8 limit.
 */
internal object ThumbnailStripPolicy {
    private const val STRIP_BUDGET_DIVISOR = 2L

    fun budgetBytes(maxMemoryBytes: Long): Long =
        (ThumbnailCachePolicy.automaticBytes(maxMemoryBytes).toLong() /
            STRIP_BUDGET_DIVISOR).coerceAtLeast(1L)

    /**
     * Return the newest entries that fit the budget. Entries are expected in
     * insertion order; evicting from the front keeps the most recently loaded
     * strips visible when a dense cut exceeds the cap.
     */
    fun retainedKeys(
        entriesInInsertionOrder: List<StripEntry>,
        budgetBytes: Long,
    ): Set<String> {
        if (entriesInInsertionOrder.isEmpty() || budgetBytes <= 0L) return emptySet()
        var usedBytes = 0L
        val retained = ArrayDeque<StripEntry>()
        entriesInInsertionOrder.asReversed().forEach { entry ->
            val entryBytes = entry.bytes.coerceAtLeast(0L)
            if (entryBytes > budgetBytes) return@forEach
            if (usedBytes + entryBytes <= budgetBytes || retained.isEmpty()) {
                retained.addFirst(entry)
                usedBytes += entryBytes
            }
        }
        return retained.mapTo(linkedSetOf()) { it.key }
    }

    data class StripEntry(val key: String, val bytes: Long)
}

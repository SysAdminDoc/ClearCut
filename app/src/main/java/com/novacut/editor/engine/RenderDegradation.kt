package com.novacut.editor.engine

/** A render fallback that changes the pixels the user asked to export. */
enum class RenderDegradationType {
    SHADER_COMPILE,
    SEGMENTATION_FRAME,
}

data class RenderDegradationEntry(
    val type: RenderDegradationType,
    val effectName: String,
    val count: Int,
)

/**
 * Thread-safe, per-export collection of effect fallbacks.
 *
 * Transformer invokes GL effects on its render thread while its listener runs
 * on the main thread, so this deliberately has no coroutine or Android
 * dependencies.
 */
class RenderDegradationLedger {
    private data class Key(val type: RenderDegradationType, val effectName: String)

    private val counts = linkedMapOf<Key, Int>()

    @Synchronized
    fun record(type: RenderDegradationType, effectName: String) {
        val safeName = effectName.trim().ifEmpty { "unnamed effect" }.take(80)
        val key = Key(type, safeName)
        counts[key] = (counts[key] ?: 0) + 1
    }

    @Synchronized
    fun outcome(): RenderDegradationOutcome? {
        if (counts.isEmpty()) return null
        return RenderDegradationOutcome(
            entries = counts.map { (key, count) ->
                RenderDegradationEntry(key.type, key.effectName, count)
            }
        )
    }
}

data class RenderDegradationOutcome(
    val entries: List<RenderDegradationEntry>,
) {
    val summary: String
        get() = entries.joinToString(separator = "; ") { entry ->
            when (entry.type) {
                RenderDegradationType.SHADER_COMPILE ->
                    "${entry.effectName}: shader compile fallback (${entry.count} occurrence(s))"
                RenderDegradationType.SEGMENTATION_FRAME ->
                    "${entry.effectName}: neutral mask fallback (${entry.count} frame(s))"
            }
        }
}

class RenderDegradationException(
    val outcome: RenderDegradationOutcome,
) : IllegalStateException("Export could not honor GPU effects: ${outcome.summary}")

/**
 * Convert the per-export ledger snapshot into the typed callback failure used
 * by every Transformer export path. Keeping this decision pure makes it
 * possible to test propagation without creating a GLES context.
 */
internal fun renderDegradationExceptionOrNull(
    outcome: RenderDegradationOutcome?,
): RenderDegradationException? = outcome?.let(::RenderDegradationException)

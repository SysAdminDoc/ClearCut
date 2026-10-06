package com.novacut.editor.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Shared ceiling for in-memory PCM accumulation during audio decode.
 *
 * `AudioEngine.decodeToPCM`, `MultiCamEngine.extractMonoPcm`, and the Whisper
 * decode path buffer the whole decoded track before use. Without a bound, a
 * long or crafted file accumulates hundreds of MB and OOMs the app. This caps
 * accumulation and lets callers fail closed (stop decoding) instead of crashing.
 *
 * Pure Kotlin so the overflow-safe budget check is unit-testable on the JVM.
 */
object AudioDecodeBudget {

    /**
     * Maximum decoded 16-bit samples kept in memory (~192 MB of `ShortArray`,
     * or ~384 MB as boxed floats). Covers roughly 16 minutes of 48 kHz stereo;
     * longer inputs stop decoding rather than exhaust the heap.
     */
    const val MAX_PCM_SAMPLES: Int = 96_000_000

    /**
     * True when appending [incoming] samples to [current] would exceed [cap]
     * (or overflow `Int`). Overflow-safe: never evaluates `current + incoming`.
     */
    fun exceedsBudget(current: Int, incoming: Int, cap: Int = MAX_PCM_SAMPLES): Boolean {
        if (incoming <= 0) return false
        val exceeds = current >= cap || current > cap - incoming
        MemoryBudgetLedger.notePcm(if (exceeds) cap.toLong() else current.toLong() + incoming, cap, exceeds)
        return exceeds
    }
}

/**
 * The last known state of ClearCut's biggest memory budgets, kept on disk while the
 * app runs so the next launch can say what it was holding when Android killed it
 * for memory: how much decoded audio was buffered and which models were loaded.
 *
 * Nothing is written until [install] names a file, so unit tests and other
 * processes that never install it pay nothing.
 */
object MemoryBudgetLedger {
    /** Decoded audio is noted each time it crosses another twelfth of its cap. */
    private const val PCM_STEPS = 12

    private val lock = Any()
    private var file: File? = null
    private var pcmSamples = 0L
    private var pcmCap = 0L
    private var pcmCapReached = false
    private var pcmNotedAtEpochMs = 0L
    private val models = linkedMapOf<String, LoadedModel>()

    private class LoadedModel(val bytes: Long, var count: Int)

    /** Starts a fresh ledger for this process, replacing what the last one left. */
    fun install(target: File) {
        synchronized(lock) {
            file = target
            pcmSamples = 0L
            pcmCap = 0L
            pcmCapReached = false
            pcmNotedAtEpochMs = 0L
            models.clear()
            target.delete()
        }
    }

    /** Stops writing. For tests. */
    internal fun uninstall() {
        synchronized(lock) { file = null }
    }

    internal fun notePcm(samplesHeld: Long, cap: Int, capReached: Boolean, nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (file == null || cap <= 0) return
            val step = cap.toLong() / PCM_STEPS
            val crossed = step <= 0 || samplesHeld / step != pcmSamples / step
            if (!crossed && capReached == pcmCapReached) return
            pcmSamples = samplesHeld
            pcmCap = cap.toLong()
            pcmCapReached = capReached
            pcmNotedAtEpochMs = nowEpochMs
            persist(nowEpochMs)
        }
    }

    fun modelLoaded(name: String, bytes: Long, nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (file == null) return
            models.getOrPut(name) { LoadedModel(bytes.coerceAtLeast(0L), 0) }.count += 1
            persist(nowEpochMs)
        }
    }

    fun modelReleased(name: String, nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (file == null) return
            val model = models[name] ?: return
            model.count -= 1
            if (model.count <= 0) models.remove(name)
            persist(nowEpochMs)
        }
    }

    private fun persist(nowEpochMs: Long) {
        val target = file ?: return
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("updatedAtEpochMs", nowEpochMs)
            .put("pcmSamplesHeld", pcmSamples)
            .put("pcmCapSamples", pcmCap)
            .put("pcmCapReached", pcmCapReached)
            .put("pcmNotedAtEpochMs", pcmNotedAtEpochMs)
            .put("modelsLoaded", JSONArray().apply {
                models.forEach { (name, model) ->
                    put(JSONObject().put("name", name).put("bytes", model.bytes).put("sessions", model.count))
                }
            })
        runCatching { writeUtf8TextAtomically(target, json.toString()) }
            .onFailure { AppLog.w("MemoryBudgetLedger", "Couldn't save the memory budget state", it) }
    }

    /** What the previous process left in [source], or null when it left nothing readable. */
    fun read(source: File): JSONObject? =
        runCatching { JSONObject(source.readText(Charsets.UTF_8)) }.getOrNull()
            ?.takeIf { it.optString("schema") == SCHEMA }

    const val SCHEMA = "com.clearcut.memory-budget.v1"
}

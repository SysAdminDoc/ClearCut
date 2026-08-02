package com.novacut.editor.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A cancellable, FIFO-ish lease queue for platform codec-backed work.
 *
 * Android exposes a per-codec concurrent-instance ceiling, but the framework
 * does not queue callers for us. Keeping the semaphore and the diagnostics
 * counters in one place means waveform analysis, AI audio analysis, and
 * thumbnail/retriever work all observe the same budget instead of racing to
 * create decoder instances on Dispatchers.IO.
 */
internal class CodecPermitRegistry(
    private val ceilingForKey: (String) -> CodecCeiling,
) {
    private data class State(
        val ceiling: CodecCeiling,
        val semaphore: Semaphore,
        val activeTotal: AtomicInteger = AtomicInteger(0),
        val queuedTotal: AtomicInteger = AtomicInteger(0),
        val activeByKind: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
        val queuedByKind: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
    )

    private val states = ConcurrentHashMap<String, State>()

    suspend fun acquire(kind: String, key: String): CodecPermitLease = withContext(Dispatchers.IO) {
        acquireCancellable(kind, normalize(key))
    }

    fun acquireBlocking(kind: String, key: String): CodecPermitLease =
        acquireUninterruptibly(kind, normalize(key))

    fun snapshots(kind: String): List<CodecLeaseSnapshot> = states.mapNotNull { (key, state) ->
        val active = state.activeByKind[kind]?.get() ?: 0
        val queued = state.queuedByKind[kind]?.get() ?: 0
        if (active == 0 && queued == 0 &&
            !state.activeByKind.containsKey(kind) && !state.queuedByKind.containsKey(kind)
        ) {
            null
        } else {
            CodecLeaseSnapshot(
                kind = kind,
                mimeType = key,
                declaredCeiling = state.ceiling.declared,
                effectiveCeiling = state.ceiling.effective,
                active = active,
                queued = queued,
                totalActive = state.activeTotal.get(),
                totalQueued = state.queuedTotal.get(),
            )
        }
    }.sortedWith(compareBy({ it.kind }, { it.mimeType }))

    /** Test-only reset for isolated stress fixtures. */
    internal fun clearForTests() = states.clear()

    private suspend fun acquireCancellable(kind: String, key: String): CodecPermitLease {
        val state = stateFor(key)
        state.queuedTotal.incrementAndGet()
        val queuedForKind = state.queuedByKind.computeIfAbsent(kind) { AtomicInteger(0) }
        queuedForKind.incrementAndGet()
        try {
            while (true) {
                try {
                    if (state.semaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) break
                } catch (_: InterruptedException) {
                    currentCoroutineContext().ensureActive()
                }
                currentCoroutineContext().ensureActive()
            }
        } finally {
            queuedForKind.decrementAndGet()
            state.queuedTotal.decrementAndGet()
        }
        return createPermit(kind, state)
    }

    private fun acquireUninterruptibly(kind: String, key: String): CodecPermitLease {
        val state = stateFor(key)
        state.queuedTotal.incrementAndGet()
        val queuedForKind = state.queuedByKind.computeIfAbsent(kind) { AtomicInteger(0) }
        queuedForKind.incrementAndGet()
        try {
            state.semaphore.acquire()
        } finally {
            queuedForKind.decrementAndGet()
            state.queuedTotal.decrementAndGet()
        }
        return createPermit(kind, state)
    }

    private fun createPermit(kind: String, state: State): CodecPermitLease {
        state.activeTotal.incrementAndGet()
        val activeForKind = state.activeByKind.computeIfAbsent(kind) { AtomicInteger(0) }
        activeForKind.incrementAndGet()
        return CodecPermitLease {
            activeForKind.decrementAndGet()
            state.activeTotal.decrementAndGet()
            state.semaphore.release()
        }
    }

    private fun stateFor(key: String): State = states.computeIfAbsent(key) {
        val ceiling = ceilingForKey(key)
        State(ceiling, Semaphore(ceiling.effective, true))
    }

    private fun normalize(key: String): String = key.trim().lowercase(Locale.US)
}

internal class CodecLeasePool<R>(
    private val kind: String,
    private val ceilingForKey: (String) -> CodecCeiling,
    private val createResource: (String) -> R,
    private val closeResource: (R) -> Unit,
    private val permits: CodecPermitRegistry = CodecPermitRegistry(ceilingForKey),
) {
    suspend fun acquire(key: String): CodecLease<R> =
        createLease(permits.acquire(kind, normalize(key)), normalize(key))

    fun acquireBlocking(key: String): CodecLease<R> =
        createLease(permits.acquireBlocking(kind, normalize(key)), normalize(key))

    fun snapshots(): List<CodecLeaseSnapshot> = permits.snapshots(kind)

    /** Test-only reset for isolated stress fixtures. */
    internal fun clearForTests() = permits.clearForTests()

    private fun createLease(permit: CodecPermitLease, key: String): CodecLease<R> {
        return try {
            val resource = createResource(key)
            CodecLease(resource) {
                try {
                    closeResource(resource)
                } finally {
                    permit.close()
                }
            }
        } catch (t: Throwable) {
            permit.close()
            throw t
        }
    }

    private fun normalize(key: String): String = key.trim().lowercase(Locale.US)
}

internal class CodecPermitLease internal constructor(
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

internal class CodecLease<R> internal constructor(
    val resource: R,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

internal data class CodecCeiling(
    val declared: Int?,
    val effective: Int,
)

internal data class CodecLeaseSnapshot(
    val kind: String,
    val mimeType: String,
    val declaredCeiling: Int?,
    val effectiveCeiling: Int,
    val active: Int,
    val queued: Int,
    val totalActive: Int,
    val totalQueued: Int,
)

/** Shared production lease pools for explicit decoders and frame retrievers. */
object CodecInstanceBudget {
    private const val UNKNOWN_CEILING = 4
    private const val VIDEO_FAMILY = "video/*"
    private val permits = CodecPermitRegistry(::declaredCeiling)

    private val decoderPool = CodecLeasePool(
        kind = "decoder",
        ceilingForKey = ::declaredCeiling,
        createResource = { mime -> MediaCodec.createDecoderByType(mime) },
        closeResource = { codec ->
            try {
                codec.stop()
            } catch (_: Exception) {
                // A configure/start failure can leave the codec non-started.
            }
            try {
                codec.release()
            } catch (_: Exception) {
                // Release is best-effort during cancellation and decoder errors.
            }
        },
        permits = permits,
    )

    private val retrieverPool = CodecLeasePool(
        kind = "retriever",
        ceilingForKey = ::declaredCeiling,
        createResource = { MediaMetadataRetriever() },
        closeResource = { retriever ->
            try {
                retriever.release()
            } catch (_: Exception) {
                // Release is best-effort during cancellation and decoder errors.
            }
        },
        permits = permits,
    )

    private val playerPool = CodecLeasePool(
        kind = "player",
        ceilingForKey = ::declaredCeiling,
        createResource = { Unit },
        closeResource = {},
        permits = permits,
    )

    private val pipelinePool = CodecLeasePool(
        kind = "pipeline",
        ceilingForKey = ::declaredCeiling,
        createResource = { Unit },
        closeResource = {},
        permits = permits,
    )

    internal suspend fun acquireDecoder(mimeType: String): CodecLease<MediaCodec> =
        decoderPool.acquire(mimeType)

    internal suspend fun acquireRetriever(mimeType: String? = null): CodecLease<MediaMetadataRetriever> =
        retrieverPool.acquire(mimeType ?: VIDEO_FAMILY)

    internal fun acquireRetrieverBlocking(mimeType: String? = null): CodecLease<MediaMetadataRetriever> =
        retrieverPool.acquireBlocking(mimeType ?: VIDEO_FAMILY)

    internal fun acquirePlayerBlocking(mimeType: String? = null): CodecLease<Unit> =
        playerPool.acquireBlocking(mimeType ?: VIDEO_FAMILY)

    internal fun acquirePipelineBlocking(mimeType: String? = null): CodecLease<Unit> =
        pipelinePool.acquireBlocking(mimeType ?: VIDEO_FAMILY)

    internal fun snapshots(): List<CodecLeaseSnapshot> =
        (decoderPool.snapshots() + retrieverPool.snapshots() +
            playerPool.snapshots() + pipelinePool.snapshots())
            .sortedWith(compareBy({ it.kind }, { it.mimeType }))

    fun diagnosticSummary(): String = buildString {
        appendLine("# ClearCut codec instance lease summary")
        appendLine("# kind\tmime\tdeclared_ceiling\teffective_ceiling\tactive\tqueued\ttotal_active\ttotal_queued")
        val entries = snapshots()
        if (entries.isEmpty()) {
            appendLine("# no codec-backed work has been leased in this process")
        } else {
            entries.forEach { entry ->
                appendLine(
                    "${entry.kind}\t${entry.mimeType}\t" +
                        "${entry.declaredCeiling ?: "unknown"}\t" +
                        "${entry.effectiveCeiling}\t${entry.active}\t${entry.queued}\t" +
                        "${entry.totalActive}\t${entry.totalQueued}"
                )
            }
        }
    }

    /** Test-only reset for the stress fixture. */
    internal fun clearForTests() {
        permits.clearForTests()
    }

    private fun declaredCeiling(mimeType: String): CodecCeiling {
        val declared = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filterNot(MediaCodecInfo::isEncoder)
                .flatMap { info ->
                    info.supportedTypes.asSequence()
                        .filter { supported -> matchesMime(mimeType, supported) }
                        .mapNotNull { supported ->
                            runCatching {
                                info.getCapabilitiesForType(supported)
                                    .maxSupportedInstances
                            }.getOrNull()?.takeIf { it > 0 }
                        }
                }
                .minOrNull()
        }.getOrNull()
        return CodecCeiling(declared = declared, effective = declared ?: UNKNOWN_CEILING)
    }

    private fun matchesMime(requested: String, supported: String): Boolean =
        if (requested == VIDEO_FAMILY) {
            supported.startsWith("video/", ignoreCase = true)
        } else {
            requested.equals(supported, ignoreCase = true)
        }
}

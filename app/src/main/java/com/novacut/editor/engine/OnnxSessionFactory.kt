package com.novacut.editor.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.novacut.editor.engine.AppLog

/**
 * Centralizes ONNX Runtime execution-provider selection for on-device models.
 *
 * XNNPACK is attempted first because it ships in the Android Runtime AAR and
 * accelerates the floating-point operators used by Whisper and LaMa. The
 * default CPU provider remains the correctness fallback: a missing native
 * provider, an unsupported ABI, or a model/provider construction failure must
 * never make an otherwise runnable model unavailable.
 */
object OnnxSessionFactory {

    enum class ExecutionProvider {
        XNNPACK,
        CPU,
    }

    data class ProviderSelection(
        val provider: ExecutionProvider,
        val xnnpackAttempted: Boolean,
        val fellBackToCpu: Boolean,
    )

    /** Keeps SessionOptions alive for exactly as long as its session needs it. */
    class SessionHandle internal constructor(
        val session: OrtSession,
        val provider: ExecutionProvider,
        private val options: OrtSession.SessionOptions,
    ) : AutoCloseable {
        override fun close() {
            try {
                session.close()
            } finally {
                options.close()
            }
        }
    }

    data class XnnpackProbeResult(
        val available: Boolean,
        val failureClass: String? = null,
    )

    private const val TAG = "OnnxSessionFactory"
    private const val XNNPACK_THREADS_OPTION = "intra_op_num_threads"
    private const val ALLOW_INTRA_OP_SPINNING = "session.intra_op.allow_spinning"
    private const val DEFAULT_CPU_THREADS = 4
    private const val MAX_XNNPACK_THREADS = 4

    /**
     * Pure provider decision seam used by JVM tests and by the native probe.
     * Only recoverable provider/linkage failures become CPU fallback; process
     * and VM failures still propagate.
     */
    internal fun selectProvider(probeXnnpack: () -> Unit): ProviderSelection {
        return try {
            probeXnnpack()
            ProviderSelection(
                provider = ExecutionProvider.XNNPACK,
                xnnpackAttempted = true,
                fellBackToCpu = false,
            )
        } catch (failure: Throwable) {
            if (!isRecoverableProviderFailure(failure)) throw failure
            ProviderSelection(
                provider = ExecutionProvider.CPU,
                xnnpackAttempted = true,
                fellBackToCpu = true,
            )
        }
    }

    /**
     * Registers XNNPACK without loading a model. This is suitable for an
     * instrumentation capability probe and exercises the same native entry
     * point used by [createSession].
     */
    fun probeXnnpack(): XnnpackProbeResult {
        var options: OrtSession.SessionOptions? = null
        return try {
            options = buildXnnpackOptions()
            XnnpackProbeResult(available = true)
        } catch (failure: Throwable) {
            if (!isRecoverableProviderFailure(failure)) throw failure
            XnnpackProbeResult(available = false, failureClass = failure::class.java.name)
        } finally {
            try {
                options?.close()
            } catch (_: Exception) {
                // A failed capability probe is already a CPU fallback signal.
            }
        }
    }

    /**
     * Creates a model session with XNNPACK first and CPU fallback second.
     * Each provider attempt owns its own options object so a failed XNNPACK
     * attempt cannot leak a native handle or contaminate the CPU retry.
     */
    fun createSession(
        environment: OrtEnvironment,
        modelPath: String,
        cpuThreads: Int = DEFAULT_CPU_THREADS,
    ): SessionHandle {
        var xnnpackOptions: OrtSession.SessionOptions? = null
        val selection = selectProvider {
            xnnpackOptions = buildXnnpackOptions()
        }
        if (selection.provider == ExecutionProvider.XNNPACK) {
            val options = requireNotNull(xnnpackOptions)
            try {
                val session = environment.createSession(modelPath, options)
                AppLog.d(TAG, "Using XNNPACK for ${modelPath.substringAfterLast('/')}")
                return SessionHandle(session, ExecutionProvider.XNNPACK, options)
            } catch (failure: Throwable) {
                if (!isRecoverableProviderFailure(failure)) throw failure
                closeQuietly(options)
                AppLog.w(TAG, "XNNPACK session creation failed; retrying on CPU", failure)
            }
        } else {
            closeQuietly(xnnpackOptions)
            AppLog.w(TAG, "XNNPACK unavailable; using CPU for ${modelPath.substringAfterLast('/')}")
        }

        val cpuOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(cpuThreads.coerceAtLeast(1))
        }
        return try {
            val session = environment.createSession(modelPath, cpuOptions)
            AppLog.d(TAG, "Using CPU for ${modelPath.substringAfterLast('/')}")
            SessionHandle(session, ExecutionProvider.CPU, cpuOptions)
        } catch (failure: Throwable) {
            closeQuietly(cpuOptions)
            throw failure
        }
    }

    internal fun xnnpackThreadCount(availableProcessors: Int): Int =
        availableProcessors.coerceIn(1, MAX_XNNPACK_THREADS)

    private fun buildXnnpackOptions(): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        return try {
            // XNNPACK owns the compute-heavy operator pool; keep ORT's pool at
            // one thread and disable its spinning to avoid thread contention.
            options.setIntraOpNumThreads(1)
            options.addConfigEntry(ALLOW_INTRA_OP_SPINNING, "0")
            options.addXnnpack(
                mapOf(
                    XNNPACK_THREADS_OPTION to
                        xnnpackThreadCount(Runtime.getRuntime().availableProcessors()).toString(),
                ),
            )
            options
        } catch (failure: Throwable) {
            closeQuietly(options)
            throw failure
        }
    }

    private fun closeQuietly(options: OrtSession.SessionOptions?) {
        try {
            options?.close()
        } catch (_: Exception) {
            // Preserve the provider/session failure that caused cleanup.
        }
    }

    private fun isRecoverableProviderFailure(failure: Throwable): Boolean =
        failure is Exception || failure is LinkageError
}

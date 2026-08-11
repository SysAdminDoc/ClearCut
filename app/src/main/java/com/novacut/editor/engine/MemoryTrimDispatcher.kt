package com.novacut.editor.engine

import com.novacut.editor.engine.AppLog
import javax.inject.Inject
import javax.inject.Singleton

private const val MEMORY_TRIM_DISPATCHER_TAG = "MemoryTrimDispatcher"

data class MemoryTrimDispatch(
    val decision: MemoryTrimDecision,
    val results: List<MemoryTrimRegistry.DispatchResult>,
)

@Singleton
class MemoryTrimDispatcher @Inject constructor(
    private val policy: MemoryTrimPolicy,
    private val registry: MemoryTrimRegistry,
    private val breadcrumbStore: MemoryTrimBreadcrumbStore,
) {

    fun onTrimMemory(level: Int): MemoryTrimDispatch {
        val decision = policy.decisionFor(level)
        val results = registry.dispatch(decision.actions)
        if (decision.logBreadcrumb) {
            runCatching {
                breadcrumbStore.record(decision, results)
            }.onFailure { t ->
                AppLog.w(MEMORY_TRIM_DISPATCHER_TAG, "Unable to write memory trim breadcrumb", t)
            }
        }
        return MemoryTrimDispatch(decision, results)
    }
}

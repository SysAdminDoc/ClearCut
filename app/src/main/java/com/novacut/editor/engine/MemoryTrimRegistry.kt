package com.novacut.editor.engine

import com.novacut.editor.engine.AppLog
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

private const val MEMORY_TRIM_REGISTRY_TAG = "MemoryTrimRegistry"

@Singleton
class MemoryTrimRegistry @Inject constructor() {

    data class DispatchResult(
        val action: MemoryTrimAction,
        val targetName: String,
        val succeeded: Boolean,
        val errorType: String? = null,
    )

    private data class Target(
        val action: MemoryTrimAction,
        val name: String,
        val onTrim: () -> Unit,
    )

    private val targets: MutableMap<MemoryTrimAction, MutableList<Target>> =
        EnumMap(MemoryTrimAction::class.java)

    @Synchronized
    fun register(
        action: MemoryTrimAction,
        targetName: String,
        onTrim: () -> Unit,
    ) {
        require(targetName.isNotBlank()) { "Memory trim target names must be non-blank." }
        targets.getOrPut(action) { mutableListOf() } += Target(action, targetName, onTrim)
    }

    fun dispatch(actions: List<MemoryTrimAction>): List<DispatchResult> {
        val snapshot = synchronized(this) {
            actions.flatMap { action ->
                targets[action]?.toList().orEmpty()
            }
        }

        return snapshot.map { target ->
            try {
                target.onTrim()
                DispatchResult(
                    action = target.action,
                    targetName = target.name,
                    succeeded = true,
                )
            } catch (t: Throwable) {
                AppLog.w(MEMORY_TRIM_REGISTRY_TAG, "Memory trim target failed: ${target.name}", t)
                DispatchResult(
                    action = target.action,
                    targetName = target.name,
                    succeeded = false,
                    errorType = t.javaClass.simpleName,
                )
            }
        }
    }
}

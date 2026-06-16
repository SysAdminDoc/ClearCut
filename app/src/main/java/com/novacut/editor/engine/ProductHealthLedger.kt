package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductHealthLedger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ledgerFile = File(context.filesDir, "diagnostics/product-health-ledger.json")
    private val mutex = Mutex()

    @Volatile private var cached: LedgerData? = null

    data class LedgerData(
        val exportAttempts: Int = 0,
        val exportCompleted: Int = 0,
        val exportCancelled: Int = 0,
        val exportFailed: Int = 0,
        val exportFailureClasses: Map<String, Int> = emptyMap(),
        val coldStartCount: Int = 0,
        val warmStartCount: Int = 0,
        val modelDownloadAttempts: Int = 0,
        val modelDownloadFailed: Int = 0,
        val diagnosticZipCreated: Int = 0,
        val projectsCreated: Int = 0,
        val projectsDeleted: Int = 0,
        val aiToolInvocations: Int = 0,
        val lastUpdatedEpochMs: Long = 0L
    )

    suspend fun record(event: HealthEvent) = mutex.withLock {
        val current = loadOrDefault()
        val updated = when (event) {
            HealthEvent.EXPORT_ATTEMPT -> current.copy(exportAttempts = current.exportAttempts + 1)
            HealthEvent.EXPORT_COMPLETE -> current.copy(exportCompleted = current.exportCompleted + 1)
            HealthEvent.EXPORT_CANCELLED -> current.copy(exportCancelled = current.exportCancelled + 1)
            is HealthEvent.ExportFailed -> current.copy(
                exportFailed = current.exportFailed + 1,
                exportFailureClasses = current.exportFailureClasses.toMutableMap().apply {
                    val key = event.failureClass.take(64)
                    put(key, (get(key) ?: 0) + 1)
                }
            )
            HealthEvent.COLD_START -> current.copy(coldStartCount = current.coldStartCount + 1)
            HealthEvent.WARM_START -> current.copy(warmStartCount = current.warmStartCount + 1)
            HealthEvent.MODEL_DOWNLOAD_ATTEMPT -> current.copy(modelDownloadAttempts = current.modelDownloadAttempts + 1)
            HealthEvent.MODEL_DOWNLOAD_FAILED -> current.copy(modelDownloadFailed = current.modelDownloadFailed + 1)
            HealthEvent.DIAGNOSTIC_ZIP_CREATED -> current.copy(diagnosticZipCreated = current.diagnosticZipCreated + 1)
            HealthEvent.PROJECT_CREATED -> current.copy(projectsCreated = current.projectsCreated + 1)
            HealthEvent.PROJECT_DELETED -> current.copy(projectsDeleted = current.projectsDeleted + 1)
            HealthEvent.AI_TOOL_INVOKED -> current.copy(aiToolInvocations = current.aiToolInvocations + 1)
        }.copy(lastUpdatedEpochMs = System.currentTimeMillis())

        cached = updated
        persist(updated)
    }

    suspend fun read(): LedgerData = mutex.withLock { loadOrDefault() }

    suspend fun clear() = mutex.withLock {
        cached = LedgerData()
        withContext(Dispatchers.IO) {
            ledgerFile.delete()
        }
    }

    suspend fun toJson(): JSONObject {
        val data = read()
        return JSONObject().apply {
            put("exportAttempts", data.exportAttempts)
            put("exportCompleted", data.exportCompleted)
            put("exportCancelled", data.exportCancelled)
            put("exportFailed", data.exportFailed)
            if (data.exportFailureClasses.isNotEmpty()) {
                put("exportFailureClasses", JSONObject().apply {
                    data.exportFailureClasses.entries
                        .sortedByDescending { it.value }
                        .take(MAX_FAILURE_CLASSES)
                        .forEach { (k, v) -> put(k, v) }
                })
            }
            put("coldStartCount", data.coldStartCount)
            put("warmStartCount", data.warmStartCount)
            put("modelDownloadAttempts", data.modelDownloadAttempts)
            put("modelDownloadFailed", data.modelDownloadFailed)
            put("diagnosticZipCreated", data.diagnosticZipCreated)
            put("projectsCreated", data.projectsCreated)
            put("projectsDeleted", data.projectsDeleted)
            put("aiToolInvocations", data.aiToolInvocations)
            put("lastUpdatedEpochMs", data.lastUpdatedEpochMs)
        }
    }

    private fun loadOrDefault(): LedgerData {
        cached?.let { return it }
        if (!ledgerFile.isFile) return LedgerData()
        return try {
            val json = JSONObject(ledgerFile.readText())
            val failureClasses = json.optJSONObject("exportFailureClasses")?.let { obj ->
                val map = mutableMapOf<String, Int>()
                for (key in obj.keys()) {
                    map[key.take(64)] = obj.optInt(key, 0)
                }
                map.toMap()
            } ?: emptyMap()
            LedgerData(
                exportAttempts = json.optInt("exportAttempts", 0),
                exportCompleted = json.optInt("exportCompleted", 0),
                exportCancelled = json.optInt("exportCancelled", 0),
                exportFailed = json.optInt("exportFailed", 0),
                exportFailureClasses = failureClasses,
                coldStartCount = json.optInt("coldStartCount", 0),
                warmStartCount = json.optInt("warmStartCount", 0),
                modelDownloadAttempts = json.optInt("modelDownloadAttempts", 0),
                modelDownloadFailed = json.optInt("modelDownloadFailed", 0),
                diagnosticZipCreated = json.optInt("diagnosticZipCreated", 0),
                projectsCreated = json.optInt("projectsCreated", 0),
                projectsDeleted = json.optInt("projectsDeleted", 0),
                aiToolInvocations = json.optInt("aiToolInvocations", 0),
                lastUpdatedEpochMs = json.optLong("lastUpdatedEpochMs", 0L)
            ).also { cached = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load product health ledger", e)
            LedgerData()
        }
    }

    private suspend fun persist(data: LedgerData) = withContext(Dispatchers.IO) {
        try {
            ledgerFile.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("exportAttempts", data.exportAttempts)
                put("exportCompleted", data.exportCompleted)
                put("exportCancelled", data.exportCancelled)
                put("exportFailed", data.exportFailed)
                if (data.exportFailureClasses.isNotEmpty()) {
                    put("exportFailureClasses", JSONObject().apply {
                        data.exportFailureClasses.entries
                            .sortedByDescending { it.value }
                            .take(MAX_FAILURE_CLASSES)
                            .forEach { (k, v) -> put(k, v) }
                    })
                }
                put("coldStartCount", data.coldStartCount)
                put("warmStartCount", data.warmStartCount)
                put("modelDownloadAttempts", data.modelDownloadAttempts)
                put("modelDownloadFailed", data.modelDownloadFailed)
                put("diagnosticZipCreated", data.diagnosticZipCreated)
                put("projectsCreated", data.projectsCreated)
                put("projectsDeleted", data.projectsDeleted)
                put("aiToolInvocations", data.aiToolInvocations)
                put("lastUpdatedEpochMs", data.lastUpdatedEpochMs)
            }
            writeUtf8TextAtomically(ledgerFile, json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist product health ledger", e)
        }
    }

    companion object {
        private const val TAG = "ProductHealth"
        private const val MAX_FAILURE_CLASSES = 20
    }
}

sealed class HealthEvent {
    data object EXPORT_ATTEMPT : HealthEvent()
    data object EXPORT_COMPLETE : HealthEvent()
    data object EXPORT_CANCELLED : HealthEvent()
    data class ExportFailed(val failureClass: String) : HealthEvent()
    data object COLD_START : HealthEvent()
    data object WARM_START : HealthEvent()
    data object MODEL_DOWNLOAD_ATTEMPT : HealthEvent()
    data object MODEL_DOWNLOAD_FAILED : HealthEvent()
    data object DIAGNOSTIC_ZIP_CREATED : HealthEvent()
    data object PROJECT_CREATED : HealthEvent()
    data object PROJECT_DELETED : HealthEvent()
    data object AI_TOOL_INVOKED : HealthEvent()
}

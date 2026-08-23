package com.novacut.editor.engine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportServiceThermalContractTest {

    @Test
    fun api35ReadsDeviceThresholdsAndApi36ListensForHeadroomChanges() {
        val source = serviceSource()

        assertTrue(source.contains("Build.VERSION_CODES.VANILLA_ICE_CREAM"))
        assertTrue(source.contains("powerManager.thermalHeadroomThresholds"))
        assertTrue(source.contains("Build.VERSION_CODES.BAKLAVA"))
        assertTrue(source.contains("powerManager.addThermalHeadroomListener"))
        assertTrue(source.contains("powerManager.removeThermalHeadroomListener"))
    }

    @Test
    fun forecastPollingUsesTheTenSecondPolicyFloor() {
        val source = serviceSource()

        assertTrue(
            source.contains(
                "THERMAL_POLL_INTERVAL_MS = ThermalHeadroomPolicy.MIN_FORECAST_POLL_INTERVAL_MS",
            ),
        )
        assertTrue(source.contains("delay(THERMAL_POLL_INTERVAL_MS)"))
        assertFalse(source.contains("delay(1_000L)"))
    }

    @Test
    fun shutdownMessageIsArmedOnlyAfterEngineConfirmsCancellation() {
        val source = serviceSource()
        val cancellationBranch = source
            .substringAfter("if (decision.action == ThermalHeadroomPolicy.ExportAction.CANCEL) {")
            .substringBefore("            return")

        val cancelIndex = cancellationBranch.indexOf("videoEngine.cancelExport()")
        val stateIndex = cancellationBranch.indexOf("videoEngine.exportState.value == ExportState.CANCELLED")
        val messageIndex = cancellationBranch.indexOf("userMessageAfterTransition")
        val armIndex = cancellationBranch.indexOf("thermalCancellationPending = true")
        assertTrue(cancelIndex >= 0)
        assertTrue(cancelIndex < stateIndex)
        assertTrue(stateIndex < messageIndex)
        assertTrue(messageIndex < armIndex)
    }

    @Test
    fun thermalCopyDescribesAdvisoryContinuationInsteadOfFakeThrottling() {
        val source = serviceSource()
        val strings = locate("app/src/main/res/values/strings.xml").readText()

        assertTrue(source.contains("CONTINUE_WITH_COOLING_ADVISORY"))
        assertFalse(source.contains("ExportAction.PAUSE"))
        assertFalse(source.contains("ExportAction.THROTTLE"))
        assertTrue(strings.contains("This encoder cannot pause safely"))
        assertTrue(strings.contains("export is continuing unchanged"))
    }

    private fun serviceSource(): String = locate(
        "app/src/main/java/com/novacut/editor/engine/ExportService.kt",
    ).readText()

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }
}

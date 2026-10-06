package com.novacut.editor.ui.export

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.engine.ExportContractDisposition
import com.novacut.editor.engine.ExportContractField
import com.novacut.editor.engine.ExportContractReport
import com.novacut.editor.engine.ExportHistoryEntry
import com.novacut.editor.engine.ExportHistoryStatus
import com.novacut.editor.engine.ExportObservation
import com.novacut.editor.model.AudioCodec
import com.novacut.editor.model.VideoCodec
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import java.util.Locale

/**
 * How a finished export's file compares with what was asked for: one line for the
 * verdict, one for what the file holds, and one per property that came out different.
 */
@Composable
internal fun ExportContractLines(entry: ExportHistoryEntry) {
    if (entry.status != ExportHistoryStatus.COMPLETE) return
    val semanticColors = LocalClearCutColors.current
    val contract = entry.contract
    val disposition = contract?.disposition ?: ExportContractDisposition.UNVERIFIED
    val observed = contract?.observed
    val verdict = when (disposition) {
        ExportContractDisposition.EXACT -> stringResource(R.string.export_contract_exact)
        ExportContractDisposition.ACCEPTED_FALLBACK -> stringResource(R.string.export_contract_fallback)
        ExportContractDisposition.DEGRADED -> if (contract?.degradationSummary != null) {
            stringResource(R.string.export_contract_degraded_accepted)
        } else {
            stringResource(R.string.export_contract_degraded)
        }
        ExportContractDisposition.REJECTED -> stringResource(
            R.string.export_contract_rejected,
            observed?.failure ?: stringResource(R.string.export_contract_unknown),
        )
        ExportContractDisposition.UNVERIFIED -> stringResource(R.string.export_contract_unverified)
    }
    Text(
        text = verdict,
        color = when (disposition) {
            ExportContractDisposition.EXACT -> ClearCutAccents.Green
            ExportContractDisposition.ACCEPTED_FALLBACK -> ClearCutAccents.Teal
            ExportContractDisposition.DEGRADED -> ClearCutAccents.Yellow
            ExportContractDisposition.REJECTED -> ClearCutAccents.Red
            ExportContractDisposition.UNVERIFIED -> semanticColors.subtext
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(ClearCutTestTags.EXPORT_HISTORY_CONTRACT),
    )
    if (contract == null || observed == null || !observed.valid) return
    val held = observedSummary(observed)
    if (held.isNotEmpty()) {
        Text(text = held, color = semanticColors.subtext, style = MaterialTheme.typography.bodySmall)
    }
    contract.mismatches.forEach { field ->
        Text(
            text = stringResource(
                R.string.export_contract_mismatch,
                fieldLabel(field),
                requestedValue(contract, field),
                observedValue(observed, field),
            ),
            color = ClearCutAccents.Yellow,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (disposition == ExportContractDisposition.DEGRADED && contract.fallbackSummary != null) {
        Text(
            text = stringResource(R.string.export_contract_fallback_also),
            color = ClearCutAccents.Teal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun observedSummary(observed: ExportObservation): String = listOfNotNull(
    observed.videoMimeType?.let(::codecLabel),
    observed.audioMimeType?.let(::codecLabel),
    sizeText(observed.width, observed.height),
    observed.frameRate?.let { stringResource(R.string.export_contract_fps, frameRateText(it)) },
    observed.container,
).joinToString(" · ")

@Composable
private fun fieldLabel(field: ExportContractField): String = stringResource(
    when (field) {
        ExportContractField.CONTAINER -> R.string.export_contract_field_container
        ExportContractField.VIDEO_CODEC -> R.string.export_contract_field_video_codec
        ExportContractField.AUDIO_CODEC -> R.string.export_contract_field_audio_codec
        ExportContractField.SIZE -> R.string.export_contract_field_size
        ExportContractField.FRAME_RATE -> R.string.export_contract_field_frame_rate
        ExportContractField.FAST_START -> R.string.export_contract_field_fast_start
    }
)

@Composable
private fun requestedValue(contract: ExportContractReport, field: ExportContractField): String = when (field) {
    ExportContractField.CONTAINER -> contract.requestedContainer
    ExportContractField.VIDEO_CODEC -> contract.requestedVideoMimeType?.let(::codecLabel)
    ExportContractField.AUDIO_CODEC -> contract.requestedAudioMimeType?.let(::codecLabel)
    ExportContractField.SIZE -> sizeText(contract.requestedWidth, contract.requestedHeight)
        ?: contract.requestedMaxWidth?.let { stringResource(R.string.export_contract_max_width, it) }
    ExportContractField.FRAME_RATE -> contract.requestedFrameRate?.let {
        stringResource(R.string.export_contract_fps, it.toString())
    }
    ExportContractField.FAST_START -> stringResource(
        if (contract.requestedFastStart) R.string.export_contract_on else R.string.export_contract_off
    )
} ?: stringResource(R.string.export_contract_unknown)

@Composable
private fun observedValue(observed: ExportObservation, field: ExportContractField): String = when (field) {
    ExportContractField.CONTAINER -> observed.container
    ExportContractField.VIDEO_CODEC -> observed.videoMimeType?.let(::codecLabel)
    // The only way a missing audio track lands here is a mix that had sound.
    ExportContractField.AUDIO_CODEC -> observed.audioMimeType?.let(::codecLabel)
        ?: stringResource(R.string.export_contract_no_audio)
    ExportContractField.SIZE -> sizeText(observed.width, observed.height)
    ExportContractField.FRAME_RATE -> observed.frameRate?.let {
        stringResource(R.string.export_contract_fps, frameRateText(it))
    }
    ExportContractField.FAST_START -> observed.fastStart?.let {
        stringResource(if (it) R.string.export_contract_on else R.string.export_contract_off)
    }
} ?: stringResource(R.string.export_contract_unknown)

private fun codecLabel(mimeType: String): String =
    VideoCodec.entries.firstOrNull { it.mimeType.equals(mimeType, ignoreCase = true) }?.label
        ?: AudioCodec.entries.firstOrNull { it.mimeType.equals(mimeType, ignoreCase = true) }?.label
        ?: mimeType

private fun sizeText(width: Int?, height: Int?): String? =
    if (width != null && height != null) "$width × $height" else null

private fun frameRateText(frameRate: Float): String =
    if (frameRate % 1f == 0f) frameRate.toInt().toString() else String.format(Locale.US, "%.2f", frameRate)

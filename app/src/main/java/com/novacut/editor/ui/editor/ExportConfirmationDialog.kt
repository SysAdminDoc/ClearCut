package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

/**
 * Shown before any export work starts when preflight found warnings. Render
 * intents the pipeline cannot honour are listed first and separately, because
 * accepting them produces a file that differs from what the timeline shows.
 */
@Composable
internal fun ExportConfirmationDialog(
    request: ExportConfirmationRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val accent = if (request.intentFallbacks.isNotEmpty()) ClearCutAccents.Red else ClearCutAccents.Yellow
    val intentMessages = request.intentFallbacks.map { it.message }.toSet()
    val otherWarnings = request.warnings.filterNot { it in intentMessages }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ClearCutDialogIcon(icon = Icons.Default.ReportProblem, accent = accent) },
        title = {
            Text(
                text = stringResource(R.string.export_confirm_title),
                color = semanticColors.text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = request.summary,
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (request.intentFallbacks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.export_confirm_intent_heading),
                        color = ClearCutAccents.Red,
                        style = MaterialTheme.typography.labelLarge
                    )
                    request.intentFallbacks.forEach { fallback ->
                        ExportConfirmationRow(text = fallback.message, accent = ClearCutAccents.Red)
                    }
                }
                if (otherWarnings.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.export_confirm_warnings_heading),
                        color = ClearCutAccents.Yellow,
                        style = MaterialTheme.typography.labelLarge
                    )
                    otherWarnings.forEach { warning ->
                        ExportConfirmationRow(text = warning, accent = ClearCutAccents.Yellow)
                    }
                }
            }
        },
        confirmButton = {
            ClearCutPrimaryButton(
                text = stringResource(R.string.export_confirm_accept),
                onClick = onConfirm,
                icon = Icons.Default.ReportProblem
            )
        },
        dismissButton = {
            ClearCutSecondaryButton(
                text = stringResource(R.string.export_confirm_cancel),
                onClick = onDismiss,
                icon = Icons.Default.Close
            )
        },
        containerColor = semanticColors.panelHighest,
        titleContentColor = semanticColors.text,
        textContentColor = semanticColors.subtext,
        shape = RoundedCornerShape(Radius.xxl)
    )
}

@Composable
private fun ExportConfirmationRow(text: String, accent: androidx.compose.ui.graphics.Color) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.09f),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.md),
            color = semanticColors.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

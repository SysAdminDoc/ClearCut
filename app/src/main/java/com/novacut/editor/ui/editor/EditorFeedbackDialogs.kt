@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.TimelineExchangeValidator
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

@Composable
internal fun AiRequirementInfoChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Mocha.Subtext0
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun BackupImportReportDialog(
    feedback: BackupImportFeedback,
    onDismiss: () -> Unit
) {
    val accent = if (feedback.succeeded) Mocha.Green else Mocha.Red
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ClearCutDialogIcon(
                icon = if (feedback.succeeded) Icons.Default.TaskAlt else Icons.Default.Error,
                accent = accent
            )
        },
        title = {
            Text(
                text = feedback.title,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            BackupImportReportBody(feedback = feedback, accent = accent)
        },
        confirmButton = {
            ClearCutPrimaryButton(
                text = stringResource(R.string.done),
                onClick = onDismiss,
                icon = Icons.Default.Check
            )
        },
        containerColor = Mocha.PanelHighest,
        titleContentColor = Mocha.Text,
        textContentColor = Mocha.Subtext0,
        shape = RoundedCornerShape(Radius.xxl)
    )
}

@Composable
private fun BackupImportReportBody(
    feedback: BackupImportFeedback,
    accent: Color
) {
    val report = feedback.report
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = feedback.body,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium
        )
        feedback.errorMessage?.let {
            ReportCallout(
                title = "Reason",
                body = it,
                accent = Mocha.Red
            )
        }
        FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
        ) {
            ReportMetric("Schema", "v${report.schemaVersion}", accent)
            ReportMetric("Media", "${report.mediaResolved}/${report.mediaTotal}", if (report.mediaMissing > 0) Mocha.Peach else Mocha.Green)
            ReportMetric("Warnings", report.warnings.size.toString(), if (report.warnings.isEmpty()) Mocha.Green else Mocha.Yellow)
            ReportMetric("Project ID", if (report.projectIdCollided) "Regenerated" else "Clean", if (report.projectIdCollided) Mocha.Sapphire else Mocha.Green)
        }
        if (report.mediaMissing > 0) {
            ReportCallout(
                title = "Missing media",
                body = "${report.mediaMissing} linked file(s) were not bundled or could not be restored. Relink them before export.",
                accent = Mocha.Peach
            )
            report.unresolvedMediaUris.take(4).forEach { uri ->
                ReportIssueRow(
                    severity = "Media",
                    path = uri,
                    message = "Still points to the original location.",
                    suggestedFix = "Open Media Manager and relink this asset.",
                    accent = Mocha.Peach
                )
            }
            if (report.unresolvedMediaUris.size > 4) {
                Text(
                    text = "+${report.unresolvedMediaUris.size - 4} more missing media reference(s)",
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        report.warnings.forEach { warning ->
            ReportIssueRow(
                severity = "Warning",
                path = "Archive",
                message = warning,
                suggestedFix = null,
                accent = Mocha.Yellow
            )
        }
    }
}

@Composable
internal fun TimelineExchangeReportDialog(
    feedback: TimelineExchangeFeedback,
    onDismiss: () -> Unit
) {
    val accent = when {
        !feedback.succeeded -> Mocha.Red
        feedback.report.warnings.isNotEmpty() -> Mocha.Yellow
        else -> Mocha.Green
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ClearCutDialogIcon(
                icon = if (feedback.succeeded) Icons.Default.IosShare else Icons.Default.ReportProblem,
                accent = accent
            )
        },
        title = {
            Text(
                text = feedback.title,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            TimelineExchangeReportBody(feedback = feedback, accent = accent)
        },
        confirmButton = {
            ClearCutPrimaryButton(
                text = stringResource(R.string.done),
                onClick = onDismiss,
                icon = Icons.Default.Check
            )
        },
        containerColor = Mocha.PanelHighest,
        titleContentColor = Mocha.Text,
        textContentColor = Mocha.Subtext0,
        shape = RoundedCornerShape(Radius.xxl)
    )
}

@Composable
private fun TimelineExchangeReportBody(
    feedback: TimelineExchangeFeedback,
    accent: Color
) {
    val report = feedback.report
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = feedback.body,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium
        )
        feedback.outputFileName?.let {
            ReportCallout(
                title = "Saved file",
                body = it,
                accent = Mocha.Green
            )
        }
        FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
        ) {
            ReportMetric("Format", report.format.displayName, accent)
            ReportMetric("Blocking", report.errors.size.toString(), if (report.errors.isEmpty()) Mocha.Green else Mocha.Red)
            ReportMetric("Lossy", report.warnings.size.toString(), if (report.warnings.isEmpty()) Mocha.Green else Mocha.Yellow)
            ReportMetric("Notes", report.infos.size.toString(), Mocha.Sapphire)
        }
        report.issues.take(8).forEach { issue ->
            val issueAccent = when (issue.severity) {
                TimelineExchangeValidator.Severity.ERROR -> Mocha.Red
                TimelineExchangeValidator.Severity.WARNING -> Mocha.Yellow
                TimelineExchangeValidator.Severity.INFO -> Mocha.Sapphire
            }
            ReportIssueRow(
                severity = issue.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                path = issue.path,
                message = issue.message,
                suggestedFix = issue.suggestedFix,
                accent = issueAccent
            )
        }
        if (report.issues.size > 8) {
            Text(
                text = "+${report.issues.size - 8} more issue(s)",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReportMetric(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReportCallout(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.09f),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                color = accent,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = body,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReportIssueRow(
    severity: String,
    path: String,
    message: String,
    suggestedFix: String?,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Mocha.Panel.copy(alpha = 0.74f),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "$severity · $path",
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = message,
                color = Mocha.Text,
                style = MaterialTheme.typography.bodySmall
            )
            if (!suggestedFix.isNullOrBlank()) {
                Text(
                    text = suggestedFix,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

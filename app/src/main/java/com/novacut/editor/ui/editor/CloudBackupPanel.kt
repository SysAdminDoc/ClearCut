package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudBackupPanel(
    lastBackupTime: Long?,
    estimatedSizeBytes: Long,
    isExporting: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBackup = lastBackupTime != null
    val lastBackupLabel = lastBackupTime?.let(::formatBackupTime) ?: stringResource(R.string.panel_cloud_backup_never)
    val status = when {
        isExporting -> BackupStatus(
            title = stringResource(R.string.panel_cloud_backup_status_exporting_title),
            body = stringResource(R.string.panel_cloud_backup_status_exporting_body),
            accent = Mocha.Mauve,
            icon = Icons.Default.Upload,
            label = stringResource(R.string.panel_cloud_backup_exporting)
        )
        hasBackup -> BackupStatus(
            title = stringResource(R.string.panel_cloud_backup_status_ready_title),
            body = stringResource(R.string.panel_cloud_backup_status_ready_body),
            accent = Mocha.Green,
            icon = Icons.Default.Backup,
            label = stringResource(R.string.panel_cloud_backup_ready)
        )
        else -> BackupStatus(
            title = stringResource(R.string.panel_cloud_backup_status_empty_title),
            body = stringResource(R.string.panel_cloud_backup_status_empty_body),
            accent = Mocha.Blue,
            icon = Icons.Default.Backup,
            label = stringResource(R.string.panel_cloud_backup_never)
        )
    }

    PremiumEditorPanel(
        title = stringResource(R.string.panel_cloud_backup_title),
        subtitle = stringResource(R.string.panel_cloud_backup_subtitle),
        icon = Icons.Default.Backup,
        accent = Mocha.Blue,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = status.accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.panel_cloud_backup_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.panel_cloud_backup_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PremiumPanelPill(
                        text = formatBackupFileSize(estimatedSizeBytes),
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = status.label,
                        accent = status.accent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                BackupMetric(
                    title = stringResource(R.string.panel_cloud_backup_estimated_size),
                    value = formatBackupFileSize(estimatedSizeBytes),
                    accent = Mocha.Peach,
                    modifier = Modifier.weight(1f)
                )
                BackupMetric(
                    title = stringResource(R.string.panel_cloud_backup_last_backup),
                    value = lastBackupLabel,
                    accent = if (hasBackup) Mocha.Green else Mocha.Overlay0,
                    modifier = Modifier.weight(1f)
                )
            }

            BackupMessageCard(
                title = status.title,
                body = status.body,
                accent = status.accent,
                icon = status.icon
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = stringResource(R.string.panel_cloud_backup_archive_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.panel_cloud_backup_archive_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            BackupMessageCard(
                title = stringResource(R.string.panel_cloud_backup_archive_include_title),
                body = stringResource(R.string.panel_cloud_backup_archive_include_body),
                accent = Mocha.Mauve,
                icon = Icons.Default.Backup
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.panel_cloud_backup_include_timeline),
                    accent = Mocha.Blue
                )
                PremiumPanelPill(
                    text = stringResource(R.string.panel_cloud_backup_include_links),
                    accent = Mocha.Peach
                )
                PremiumPanelPill(
                    text = stringResource(R.string.panel_cloud_backup_include_state),
                    accent = Mocha.Green
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        PremiumPanelCard(accent = Mocha.Green) {
            Text(
                text = stringResource(R.string.panel_cloud_backup_actions_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.panel_cloud_backup_actions_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                BackupActionRow(
                    isCompact = maxWidth < 430.dp,
                    isExporting = isExporting,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            }

            if (isExporting) {
                Text(
                    text = stringResource(R.string.panel_cloud_backup_actions_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun BackupActionRow(
    isCompact: Boolean,
    isExporting: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    if (isCompact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            BackupExportButton(
                isExporting = isExporting,
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth()
            )
            NovaCutSecondaryButton(
                text = stringResource(R.string.panel_cloud_backup_import),
                onClick = onImportBackup,
                icon = Icons.Default.Download,
                contentColor = Mocha.Blue,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            BackupExportButton(
                isExporting = isExporting,
                onClick = onExportBackup,
                modifier = Modifier.weight(1f)
            )
            NovaCutSecondaryButton(
                text = stringResource(R.string.panel_cloud_backup_import),
                onClick = onImportBackup,
                icon = Icons.Default.Download,
                contentColor = Mocha.Blue,
                enabled = !isExporting,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BackupExportButton(
    isExporting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isExporting,
        modifier = modifier.defaultMinSize(minHeight = TouchTarget.minimum),
        colors = ButtonDefaults.buttonColors(
            containerColor = Mocha.Rosewater,
            contentColor = Mocha.Midnight,
            disabledContainerColor = Mocha.Surface1.copy(alpha = 0.5f),
            disabledContentColor = Mocha.Subtext0
        ),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Mocha.Subtext0,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = if (isExporting) {
                stringResource(R.string.panel_cloud_backup_exporting)
            } else {
                stringResource(R.string.panel_cloud_backup_export)
            },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class BackupStatus(
    val title: String,
    val body: String,
    val accent: Color,
    val icon: ImageVector,
    val label: String
)

@Composable
private fun BackupMessageCard(
    title: String,
    body: String,
    accent: Color,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(Radius.xl),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.lg),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(Spacing.sm)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun BackupMetric(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Mocha.Subtext0,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = if (accent == Mocha.Subtext0) Mocha.Text else accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
            )
        }
    }
}

private fun formatBackupTime(lastBackupTime: Long?): String {
    if (lastBackupTime == null) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastBackupTime))
}

private fun formatBackupFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.ProjectSnapshot
import com.novacut.editor.ui.theme.Mocha
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnapshotHistoryPanel(
    snapshots: List<ProjectSnapshot>,
    onCreateSnapshot: (String) -> Unit,
    onRestoreSnapshot: (String) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var snapshotName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val snapshotNameFormat = remember { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()) }
    val snapshotPrefix = stringResource(R.string.snapshot_default_prefix)
    val sortedSnapshots = remember(snapshots) { snapshots.sortedByDescending { it.timestamp } }
    val latestSnapshot = sortedSnapshots.firstOrNull()

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = Mocha.Base,
            title = {
                Text(
                    text = stringResource(R.string.panel_snapshot_save_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.panel_snapshot_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                    OutlinedTextField(
                        value = snapshotName,
                        onValueChange = { snapshotName = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.snapshot_name_hint),
                                color = Mocha.Subtext0
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Mocha.Mauve,
                            unfocusedBorderColor = Mocha.CardStroke,
                            focusedTextColor = Mocha.Text,
                            unfocusedTextColor = Mocha.Text,
                            cursorColor = Mocha.Mauve
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fallbackName = "$snapshotPrefix ${snapshotNameFormat.format(Date())}"
                        onCreateSnapshot(snapshotName.trim().ifEmpty { fallbackName })
                        snapshotName = ""
                        showNameDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.panel_snapshot_save_button),
                        color = Mocha.Mauve
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(
                        text = stringResource(R.string.panel_snapshot_cancel),
                        color = Mocha.Subtext0
                    )
                }
            }
        )
    }

    PremiumEditorPanel(
        title = stringResource(R.string.snapshot_title),
        subtitle = stringResource(R.string.panel_snapshot_subtitle),
        icon = Icons.Default.History,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
        closeContentDescription = stringResource(R.string.snapshot_close_cd),
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.snapshot_take_cd),
                onClick = { showNameDialog = true },
                tint = Mocha.Green
            )
        }
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = stringResource(R.string.panel_snapshot_overview_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (latestSnapshot != null) {
                    stringResource(
                        R.string.panel_snapshot_overview_ready,
                        dateFormat.format(Date(latestSnapshot.timestamp))
                    )
                } else {
                    stringResource(R.string.panel_snapshot_overview_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.panel_snapshot_saved_count,
                        sortedSnapshots.size,
                        sortedSnapshots.size
                    ),
                    accent = Mocha.Mauve
                )
                latestSnapshot?.let {
                    PremiumPanelPill(
                        text = stringResource(R.string.panel_snapshot_latest_badge),
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = dateFormat.format(Date(it.timestamp)),
                        accent = Mocha.Sky
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            if (sortedSnapshots.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.cd_history),
                        tint = Mocha.Overlay1,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = stringResource(R.string.snapshot_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = Mocha.Text
                    )
                    Text(
                        text = stringResource(R.string.panel_snapshot_save_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.panel_snapshot_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.panel_snapshot_history_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
                sortedSnapshots.forEachIndexed { index, snapshot ->
                    SnapshotRow(
                        snapshot = snapshot,
                        dateFormat = dateFormat,
                        isLatest = snapshot.id == latestSnapshot?.id,
                        onRestore = { onRestoreSnapshot(snapshot.id) },
                        onDelete = { onDeleteSnapshot(snapshot.id) }
                    )
                    if (index < sortedSnapshots.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnapshotRow(
    snapshot: ProjectSnapshot,
    dateFormat: SimpleDateFormat,
    isLatest: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Mocha.PanelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Mocha.Mauve.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = stringResource(R.string.cd_save_snapshot),
                            tint = Mocha.Mauve,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snapshot.label.ifEmpty { stringResource(R.string.panel_snapshot_untitled) },
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dateFormat.format(Date(snapshot.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Mocha.Subtext0
                        )
                    }
                }

                if (isLatest) {
                    PremiumPanelPill(
                        text = stringResource(R.string.panel_snapshot_latest_badge),
                        accent = Mocha.Blue
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SnapshotAction(
                    icon = Icons.Default.Restore,
                    label = stringResource(R.string.snapshot_restore),
                    accent = Mocha.Green,
                    onClick = onRestore
                )
                SnapshotAction(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.snapshot_delete),
                    accent = Mocha.Red,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun SnapshotAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}

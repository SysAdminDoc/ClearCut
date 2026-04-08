package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Delete
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
    val snapshotPrefix = stringResource(R.string.snapshot_default_prefix)
    val latestSnapshot = snapshots.maxByOrNull { it.timestamp }

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
                        text = "Name this restore point so you can jump back before a risky edit or export pass.",
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
                        val defaultName = "$snapshotPrefix ${SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date())}"
                        onCreateSnapshot(snapshotName.ifBlank { defaultName })
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
        subtitle = "Create restore points before experimenting, then roll the timeline back without losing your place.",
        icon = Icons.Default.History,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restore points",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (latestSnapshot != null) {
                            "The latest snapshot was saved ${dateFormat.format(Date(latestSnapshot.timestamp))}. Keep a clean trail before major timing or grading changes."
                        } else {
                            "Snapshots are lightweight checkpoints for your edit state before you commit to bigger decisions."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "${snapshots.size} saved",
                        accent = Mocha.Mauve
                    )
                    latestSnapshot?.let {
                        PremiumPanelPill(
                            text = dateFormat.format(Date(it.timestamp)),
                            accent = Mocha.Blue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            if (snapshots.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.cd_history),
                        tint = Mocha.Overlay1,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                    text = "Snapshot history",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(snapshots.sortedByDescending { it.timestamp }, key = { it.id }) { snapshot ->
                        SnapshotRow(
                            snapshot = snapshot,
                            dateFormat = dateFormat,
                            onRestore = { onRestoreSnapshot(snapshot.id) },
                            onDelete = { onDeleteSnapshot(snapshot.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: ProjectSnapshot,
    dateFormat: SimpleDateFormat,
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
                .clickable(onClick = onRestore)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                PremiumPanelPill(
                    text = "Restore",
                    accent = Mocha.Green
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SnapshotAction(
                    icon = Icons.Default.Restore,
                    label = "Restore",
                    accent = Mocha.Green,
                    onClick = onRestore
                )
                Spacer(modifier = Modifier.width(8.dp))
                SnapshotAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
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

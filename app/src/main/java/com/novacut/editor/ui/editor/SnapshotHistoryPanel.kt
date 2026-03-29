package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.model.ProjectSnapshot
import com.novacut.editor.ui.theme.Mocha
import java.text.SimpleDateFormat
import java.util.*

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

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.panel_snapshot_save_title), color = Mocha.Text) },
            text = {
                OutlinedTextField(
                    value = snapshotName,
                    onValueChange = { snapshotName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.snapshot_name_hint), color = Mocha.Subtext0.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.Subtext0.copy(alpha = 0.3f),
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val prefix = stringResource(R.string.snapshot_default_prefix)
                    val defaultName = "$prefix ${java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                    onCreateSnapshot(snapshotName.ifBlank { defaultName })
                    snapshotName = ""
                    showNameDialog = false
                }) {
                    Text(stringResource(R.string.panel_snapshot_save_button), color = Mocha.Mauve)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.panel_snapshot_cancel), color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Base
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.snapshot_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showNameDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.snapshot_take_cd), tint = Mocha.Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.snapshot_close_cd), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (snapshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = Mocha.Subtext0.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.snapshot_empty), color = Mocha.Subtext0, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.panel_snapshot_save_hint), color = Mocha.Subtext0.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(snapshots.sortedByDescending { it.timestamp }, key = { it.id }) { snapshot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Mocha.Surface0)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SaveAlt, null, tint = Mocha.Yellow, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    snapshot.label.ifEmpty { stringResource(R.string.panel_snapshot_untitled) },
                                    color = Mocha.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    dateFormat.format(Date(snapshot.timestamp)),
                                    color = Mocha.Subtext0,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Restore
                            IconButton(
                                onClick = { onRestoreSnapshot(snapshot.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Restore, stringResource(R.string.snapshot_restore_cd), tint = Mocha.Green, modifier = Modifier.size(16.dp))
                            }
                            // Delete
                            IconButton(
                                onClick = { onDeleteSnapshot(snapshot.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, stringResource(R.string.snapshot_delete_cd), tint = Mocha.Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

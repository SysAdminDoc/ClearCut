package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Backup, stringResource(R.string.cd_backup), tint = Mocha.Blue, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.panel_cloud_backup_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.panel_cloud_backup_description),
            color = Mocha.Subtext0,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.panel_cloud_backup_estimated_size), color = Mocha.Subtext0, fontSize = 11.sp)
                Text(
                    formatFileSize(estimatedSizeBytes),
                    color = Mocha.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.panel_cloud_backup_last_backup), color = Mocha.Subtext0, fontSize = 11.sp)
                Text(
                    if (lastBackupTime != null) {
                        java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(lastBackupTime))
                    } else stringResource(R.string.panel_cloud_backup_never),
                    color = if (lastBackupTime != null) Mocha.Green else Mocha.Subtext0,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExportBackup,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Blue),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Mocha.Base, strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_cloud_backup_exporting), fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Upload, stringResource(R.string.cd_upload), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_cloud_backup_export), fontSize = 12.sp)
                }
            }
            OutlinedButton(
                onClick = onImportBackup,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, stringResource(R.string.cd_download), tint = Mocha.Blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_cloud_backup_import), color = Mocha.Blue, fontSize = 12.sp)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

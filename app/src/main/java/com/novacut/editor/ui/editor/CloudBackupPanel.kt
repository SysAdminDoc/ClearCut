package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
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
    isSignedIn: Boolean,
    lastBackupTime: Long?,
    backupProgress: Float?,
    onSignIn: () -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    onAutoBackupToggled: (Boolean) -> Unit,
    autoBackupEnabled: Boolean,
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
                Icon(Icons.Default.Cloud, null, tint = Mocha.Blue, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.cloud_backup_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isSignedIn) {
            // Sign in prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mocha.Surface0)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, tint = Mocha.Subtext0.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.cloud_backup_sign_in), color = Mocha.Subtext0, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Blue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cloud_backup_sign_in_google))
                    }
                }
            }
        } else {
            // Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Mocha.Green, modifier = Modifier.size(14.dp))
                        Text("Connected", color = Mocha.Green, fontSize = 12.sp)
                    }
                    if (lastBackupTime != null) {
                        Text(
                            "Last backup: ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastBackupTime))}",
                            color = Mocha.Subtext0,
                            fontSize = 10.sp
                        )
                    }
                }
                if (backupProgress != null) {
                    CircularProgressIndicator(
                        progress = { backupProgress },
                        modifier = Modifier.size(24.dp),
                        color = Mocha.Blue,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Auto-backup toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mocha.Surface0)
                    .clickable { onAutoBackupToggled(!autoBackupEnabled) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.cloud_backup_auto), color = Mocha.Text, fontSize = 13.sp)
                    Text(stringResource(R.string.cloud_backup_auto_desc), color = Mocha.Subtext0, fontSize = 10.sp)
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Mocha.Blue)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackupNow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Mocha.Blue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cloud_backup_now), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, null, tint = Mocha.Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cloud_backup_restore), color = Mocha.Blue, fontSize = 12.sp)
                }
            }
        }
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun AutoEditPanel(
    clipCount: Int,
    hasAudio: Boolean,
    isProcessing: Boolean,
    onGenerate: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scriptText by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.auto_edit_title), color = Mocha.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.auto_edit_close_cd), tint = Mocha.Subtext0)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.auto_edit_description),
            color = Mocha.Subtext0,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Info cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoCard(stringResource(R.string.auto_edit_info_clips), "$clipCount", Icons.Default.Videocam, Mocha.Blue, Modifier.weight(1f))
            InfoCard(stringResource(R.string.auto_edit_info_music), if (hasAudio) stringResource(R.string.auto_edit_info_yes) else stringResource(R.string.auto_edit_info_no), Icons.Default.MusicNote, if (hasAudio) Mocha.Green else Mocha.Surface1, Modifier.weight(1f))
            InfoCard(stringResource(R.string.auto_edit_info_target), stringResource(R.string.auto_edit_info_target_value), Icons.Default.Timer, Mocha.Peach, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = scriptText,
            onValueChange = { scriptText = it },
            label = { Text(stringResource(R.string.panel_auto_edit_script_label), color = Mocha.Subtext0, fontSize = 12.sp) },
            placeholder = { Text(stringResource(R.string.panel_auto_edit_script_placeholder), color = Mocha.Surface2, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                cursorColor = Mocha.Mauve,
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.Surface1,
                focusedLabelColor = Mocha.Mauve,
                unfocusedLabelColor = Mocha.Subtext0
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onGenerate(scriptText.takeIf { it.isNotBlank() }) },
            enabled = clipCount > 0 && !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve, contentColor = Mocha.Base)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Mocha.Base, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.panel_auto_edit_generating))
            } else {
                Icon(Icons.Default.AutoFixHigh, contentDescription = stringResource(R.string.cd_auto_edit_generate), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auto_edit_start))
            }
        }

        if (!hasAudio) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.panel_auto_edit_add_music_hint),
                color = Mocha.Subtext0,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Mocha.Surface0, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = Mocha.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Mocha.Subtext0, fontSize = 10.sp)
    }
}

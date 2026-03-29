package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.novacut.editor.engine.TtsEngine
import com.novacut.editor.ui.theme.Mocha

@Composable
fun TtsPanel(
    isAvailable: Boolean,
    isSynthesizing: Boolean,
    onSynthesize: (String, TtsEngine.VoiceStyle) -> Unit,
    onPreview: (String, TtsEngine.VoiceStyle) -> Unit,
    onStopPreview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(TtsEngine.VoiceStyle.NARRATOR) }
    // Piper HD voices will be available after Sherpa-ONNX integration

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tts_title), color = Mocha.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = { onStopPreview(); onClose() }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tts_close_cd), tint = Mocha.Subtext0)
            }
        }

        if (!isAvailable) {
            Text(
                stringResource(R.string.panel_tts_not_available),
                color = Mocha.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.tts_enter_text), color = Mocha.Overlay0) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 120.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.Surface1,
                cursorColor = Mocha.Mauve,
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text
            ),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.panel_tts_piper_hint),
            color = Mocha.Subtext0,
            fontSize = 10.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Voice style selector
        Text(stringResource(R.string.tts_voice), color = Mocha.Subtext0, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TtsEngine.VoiceStyle.entries.toList()) { style ->
                val isSelected = style == selectedStyle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Mocha.Mauve else Mocha.Surface0)
                        .clickable { selectedStyle = style }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            style.displayName,
                            color = if (isSelected) Mocha.Base else Mocha.Text,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            "%.1fx".format(style.rate),
                            color = if (isSelected) Mocha.Mantle else Mocha.Subtext0,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Preview button
            OutlinedButton(
                onClick = { if (text.isNotBlank()) onPreview(text, selectedStyle) },
                enabled = text.isNotBlank() && !isSynthesizing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Mauve),
                border = BorderStroke(1.dp, if (text.isNotBlank()) Mocha.Mauve else Mocha.Surface1)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_tts_preview), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.panel_tts_preview), fontSize = 13.sp)
            }

            // Generate button
            Button(
                onClick = { if (text.isNotBlank()) onSynthesize(text, selectedStyle) },
                enabled = text.isNotBlank() && !isSynthesizing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve, contentColor = Mocha.Base)
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Mocha.Base,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tts_generating), fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = stringResource(R.string.tts_generate_icon_cd), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tts_generate), fontSize = 13.sp)
                }
            }
        }
    }
}

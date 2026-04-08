package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    PremiumEditorPanel(
        title = stringResource(R.string.auto_edit_title),
        subtitle = "Turn rough footage into a first-cut highlight reel with a concise creative brief and an AI-assisted timing pass.",
        icon = Icons.Default.AutoFixHigh,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = "Source overview",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.auto_edit_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_clips),
                    value = clipCount.toString(),
                    icon = Icons.Default.Videocam,
                    accent = Mocha.Blue,
                    modifier = Modifier.weight(1f)
                )
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_music),
                    value = if (hasAudio) stringResource(R.string.auto_edit_info_yes) else stringResource(R.string.auto_edit_info_no),
                    icon = Icons.Default.MusicNote,
                    accent = if (hasAudio) Mocha.Green else Mocha.Overlay1,
                    modifier = Modifier.weight(1f)
                )
                AutoEditInfoCard(
                    label = stringResource(R.string.auto_edit_info_target),
                    value = stringResource(R.string.auto_edit_info_target_value),
                    icon = Icons.Default.Timer,
                    accent = Mocha.Peach,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = "Edit brief",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Guide the first cut with a short prompt like \"fast travel recap with strongest reactions first\" or leave it blank for a neutral highlight reel.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            OutlinedTextField(
                value = scriptText,
                onValueChange = { scriptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = {
                    Text(
                        text = stringResource(R.string.panel_auto_edit_script_label),
                        color = Mocha.Subtext0
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.panel_auto_edit_script_placeholder),
                        color = Mocha.Overlay1
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mocha.Mauve,
                    unfocusedBorderColor = Mocha.CardStroke,
                    focusedLabelColor = Mocha.Mauve,
                    unfocusedLabelColor = Mocha.Subtext0,
                    focusedTextColor = Mocha.Text,
                    unfocusedTextColor = Mocha.Text,
                    cursorColor = Mocha.Mauve
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (hasAudio) Mocha.Green else Mocha.Peach) {
            Text(
                text = "Generate highlight reel",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = if (hasAudio) {
                    "NovaCut can pace the reel against your current audio bed while it scores the strongest moments."
                } else {
                    "You can still generate a first cut now, but adding music or guide audio usually improves pacing."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = "$clipCount clips",
                    accent = Mocha.Blue
                )
                PremiumPanelPill(
                    text = if (hasAudio) "Audio ready" else "No soundtrack",
                    accent = if (hasAudio) Mocha.Green else Mocha.Peach
                )
            }

            Button(
                onClick = { onGenerate(scriptText.takeIf { it.isNotBlank() }) },
                enabled = clipCount > 0 && !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Base,
                    disabledContainerColor = Mocha.Surface1,
                    disabledContentColor = Mocha.Subtext0
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Mocha.Base,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.panel_auto_edit_generating))
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(R.string.cd_auto_edit_generate)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.auto_edit_start))
                }
            }

            if (!hasAudio) {
                Text(
                    text = stringResource(R.string.panel_auto_edit_add_music_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

@Composable
private fun AutoEditInfoCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    PremiumPanelCard(accent = accent, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Mocha.Subtext0
            )
        }
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun EffectLibraryPanel(
    hasClipSelected: Boolean,
    hasCopiedEffects: Boolean,
    onExportEffects: () -> Unit,
    onImportEffects: () -> Unit,
    onCopyEffects: () -> Unit,
    onPasteEffects: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.effect_library_title),
        subtitle = "Save, swap, and reuse effect chains so your look stays consistent across clips and projects.",
        icon = Icons.Default.ContentCopy,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier.heightIn(max = 480.dp),
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = if (hasClipSelected) "Clip ready" else "No clip selected",
                    accent = if (hasClipSelected) Mocha.Green else Mocha.Red
                )
                PremiumPanelPill(
                    text = if (hasCopiedEffects) "Paste buffer ready" else "Paste buffer empty",
                    accent = if (hasCopiedEffects) Mocha.Sapphire else Mocha.Subtext0
                )
            }

            Text(
                text = "Chain workflow",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.panel_effect_library_description),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EffectLibraryActionCard(
                title = stringResource(R.string.panel_effect_library_copy),
                subtitle = "Capture the current clip’s effect stack as a reusable chain.",
                icon = Icons.Default.ContentCopy,
                accent = Mocha.Mauve,
                enabled = hasClipSelected,
                buttonLabel = stringResource(R.string.panel_effect_library_copy),
                buttonStyle = ActionButtonStyle.Outlined,
                onClick = onCopyEffects,
                modifier = Modifier.weight(1f)
            )
            EffectLibraryActionCard(
                title = stringResource(R.string.panel_effect_library_paste),
                subtitle = "Apply the buffered chain to the selected clip in one move.",
                icon = Icons.Default.ContentPaste,
                accent = Mocha.Green,
                enabled = hasClipSelected && hasCopiedEffects,
                buttonLabel = stringResource(R.string.panel_effect_library_paste),
                buttonStyle = ActionButtonStyle.Outlined,
                onClick = onPasteEffects,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EffectLibraryActionCard(
                title = stringResource(R.string.panel_effect_library_export),
                subtitle = "Package the selected clip’s look into an `.ncfx` preset you can share.",
                icon = Icons.Default.Upload,
                accent = Mocha.Peach,
                enabled = hasClipSelected,
                buttonLabel = stringResource(R.string.panel_effect_library_export),
                buttonStyle = ActionButtonStyle.Filled,
                onClick = onExportEffects,
                modifier = Modifier.weight(1f)
            )
            EffectLibraryActionCard(
                title = stringResource(R.string.panel_effect_library_import),
                subtitle = "Bring in a saved preset chain and keep the grade or treatment consistent.",
                icon = Icons.Default.Download,
                accent = Mocha.Blue,
                enabled = true,
                buttonLabel = stringResource(R.string.panel_effect_library_import),
                buttonStyle = ActionButtonStyle.Filled,
                onClick = onImportEffects,
                modifier = Modifier.weight(1f)
            )
        }

        if (!hasClipSelected) {
            Spacer(modifier = Modifier.height(12.dp))
            PremiumPanelCard(accent = Mocha.Red) {
                Text(
                    text = "Select a clip first",
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.panel_effect_library_select_clip_hint),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private enum class ActionButtonStyle {
    Filled,
    Outlined
}

@Composable
private fun EffectLibraryActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    buttonLabel: String,
    buttonStyle: ActionButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) accent.copy(alpha = 0.24f) else Mocha.CardStroke
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = accent.copy(alpha = if (enabled) 0.16f else 0.08f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    accent.copy(alpha = if (enabled) 0.24f else 0.14f)
                )
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) accent else Mocha.Subtext0,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    color = if (enabled) Mocha.Text else Mocha.Subtext0,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (buttonStyle) {
                ActionButtonStyle.Filled -> {
                    Button(
                        onClick = onClick,
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Mocha.Base,
                            disabledContainerColor = Mocha.Surface0,
                            disabledContentColor = Mocha.Overlay0
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = buttonLabel,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(buttonLabel)
                    }
                }

                ActionButtonStyle.Outlined -> {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = enabled,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accent,
                            disabledContentColor = Mocha.Overlay0
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (enabled) accent.copy(alpha = 0.28f) else Mocha.CardStroke
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = buttonLabel,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(buttonLabel)
                    }
                }
            }
        }
    }
}

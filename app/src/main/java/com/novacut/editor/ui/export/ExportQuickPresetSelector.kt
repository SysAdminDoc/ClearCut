package com.novacut.editor.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.PlatformPreset
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.TouchTarget

private val featuredPresets = listOf(
    PlatformPreset.YOUTUBE_1080,
    PlatformPreset.TIKTOK,
)

private val overflowPresets = PlatformPreset.entries.filterNot(featuredPresets::contains)

@Composable
internal fun ExportQuickPresetSelector(
    config: ExportConfig,
    sourceAspectRatio: AspectRatio,
    outputAspectRatio: AspectRatio,
    onConfigChanged: (ExportConfig) -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val overflowSelection = config.platformPreset?.takeIf(overflowPresets::contains)
    var showOverflowPresets by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        featuredPresets.forEach { preset ->
            FilterChip(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TouchTarget.minimum),
                onClick = { onConfigChanged(config.withPlatformPreset(preset)) },
                label = {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                selected = config.platformPreset == preset,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = semanticColors.panelRaised,
                    labelColor = semanticColors.subtext,
                    selectedContainerColor = ClearCutAccents.Green.copy(alpha = 0.16f),
                    selectedLabelColor = ClearCutAccents.Green,
                ),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            FilterChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.minimum),
                onClick = { showOverflowPresets = true },
                label = {
                    Text(
                        text = overflowSelection?.displayName
                            ?: stringResource(R.string.export_more_presets),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                },
                selected = overflowSelection != null,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = semanticColors.panelRaised,
                    labelColor = semanticColors.subtext,
                    iconColor = semanticColors.subtext,
                    selectedContainerColor = ClearCutAccents.Green.copy(alpha = 0.16f),
                    selectedLabelColor = ClearCutAccents.Green,
                    selectedTrailingIconColor = ClearCutAccents.Green,
                ),
            )
            DropdownMenu(
                expanded = showOverflowPresets,
                onDismissRequest = { showOverflowPresets = false },
                containerColor = semanticColors.panelHighest,
                shape = RoundedCornerShape(Radius.md),
            ) {
                overflowPresets.forEach { preset ->
                    val isSelected = config.platformPreset == preset
                    DropdownMenuItem(
                        text = { Text(preset.displayName) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ClearCutAccents.Green,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onConfigChanged(config.withPlatformPreset(preset))
                            showOverflowPresets = false
                        },
                    )
                }
            }
        }
    }

    if (config.platformPreset != null && outputAspectRatio != sourceAspectRatio) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ClearCutAccents.Yellow.copy(alpha = 0.12f),
            shape = RoundedCornerShape(Radius.md),
            border = BorderStroke(1.dp, ClearCutAccents.Yellow.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ClearCutAccents.Yellow,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        R.string.export_preset_reframe_disclosure,
                        sourceAspectRatio.label,
                        outputAspectRatio.label,
                    ),
                    color = semanticColors.text,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

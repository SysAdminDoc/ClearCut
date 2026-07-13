package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiCamPanel(
    tracks: List<Track>,
    selectedClipId: String?,
    onAngleSelected: (String) -> Unit,
    onSyncClips: () -> Unit,
    onClose: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val isCompactGrid = LocalConfiguration.current.screenWidthDp < 430
    val allVideoClips = tracks
        .filter { it.type == TrackType.VIDEO }
        .flatMap { it.clips }
        .filterNot { clip -> isStillImagePath(clip.sourceUri.lastPathSegment) }
    val videoClips = allVideoClips
        .take(4)
    val hiddenAngleCount = (allVideoClips.size - videoClips.size).coerceAtLeast(0)
    val activeAngleLabel = videoClips.indexOfFirst { it.id == selectedClipId }
        .takeIf { it >= 0 }
        ?.let(::formatCameraLabel)

    PremiumEditorPanel(
        title = stringResource(R.string.panel_multi_cam_title),
        subtitle = "Sync angles, compare coverage, and switch the active shot without leaving the edit context.",
        icon = Icons.Default.Videocam,
        accent = ClearCutAccents.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_multicam_close),
        scrollable = true
    ) {
        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Angle overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (videoClips.isEmpty()) {
                            "Add at least two motion clips to start a multi-cam review pass."
                        } else {
                            "Choose an angle to make it active, then sync clips if the cameras need alignment. Still photos stay hidden here so the angle grid remains camera-focused."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "${videoClips.size} angles",
                        accent = ClearCutAccents.Blue
                    )
                    PremiumPanelPill(
                        text = when {
                            activeAngleLabel != null -> "$activeAngleLabel live"
                            selectedClipId != null -> "Selection off-grid"
                            else -> "No angle selected"
                        },
                        accent = if (activeAngleLabel != null) ClearCutAccents.Green else semanticColors.overlayStrong
                    )
                    if (hiddenAngleCount > 0) {
                        PremiumPanelPill(
                            text = stringResource(R.string.panel_multi_cam_more_angles, hiddenAngleCount),
                            accent = ClearCutAccents.Mauve
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Text(
                text = "Sync cameras",
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = "Run a sync pass before switching angles if the camera starts or audio reference drifted across tracks.",
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            Button(
                onClick = onSyncClips,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClearCutAccents.Mauve,
                    contentColor = semanticColors.surfaceBase
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = stringResource(R.string.cd_multicam_sync)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.panel_multi_cam_sync))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (videoClips.isEmpty()) semanticColors.overlayStrong else ClearCutAccents.Green) {
            Text(
                text = "Available angles",
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )

            if (videoClips.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = semanticColors.panelRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke)
                ) {
                    Text(
                        text = stringResource(R.string.panel_multi_cam_no_clips),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semanticColors.subtext,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Text(
                    text = "The first four motion clips appear here as switchable camera angles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    videoClips.forEachIndexed { index, clip ->
                        MultiCamAngleCard(
                            label = formatCameraLabel(index),
                            fileName = clip.sourceUri.lastPathSegment?.substringAfterLast('/') ?: "Clip",
                            isActive = clip.id == selectedClipId,
                            onClick = { onAngleSelected(clip.id) },
                            modifier = Modifier.widthIn(min = if (isCompactGrid) 136.dp else 156.dp, max = 220.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isStillImagePath(pathSegment: String?): Boolean {
    val extension = pathSegment
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?: return false
    return extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
}

@Composable
private fun MultiCamAngleCard(
    label: String,
    fileName: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    val accent = if (isActive) ClearCutAccents.Mauve else ClearCutAccents.Blue

    Surface(
        modifier = modifier,
        color = if (isActive) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) accent.copy(alpha = 0.28f) else semanticColors.cardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        color = if (isActive) accent.copy(alpha = 0.2f) else semanticColors.surfaceBase,
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = stringResource(R.string.cd_multicam_angle),
                    tint = if (isActive) accent else semanticColors.overlayStrong,
                    modifier = Modifier.size(28.dp)
                )

                if (isActive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = ClearCutAccents.Mauve,
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_multicam_selected),
                                tint = semanticColors.onAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.text
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = semanticColors.subtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PremiumPanelPill(
                text = if (isActive) "Active angle" else "Tap to switch",
                accent = accent
            )
        }
    }
}

private fun formatCameraLabel(index: Int): String = "Cam ${'A' + index}"

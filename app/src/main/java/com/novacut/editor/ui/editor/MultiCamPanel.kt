package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.theme.Mocha

@Composable
fun MultiCamPanel(
    tracks: List<Track>,
    selectedClipId: String?,
    onAngleSelected: (String) -> Unit,
    onSyncClips: () -> Unit,
    onClose: () -> Unit
) {
    val videoClips = tracks
        .filter { it.type == TrackType.VIDEO }
        .flatMap { it.clips }
        .filterNot { clip -> isStillImagePath(clip.sourceUri.lastPathSegment) }
        .take(4)

    PremiumEditorPanel(
        title = "Multi-Cam",
        subtitle = "Sync angles, compare coverage, and switch the active shot without leaving the edit context.",
        icon = Icons.Default.Videocam,
        accent = Mocha.Blue,
        onClose = onClose,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Blue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Angle overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (videoClips.isEmpty()) {
                            "Add at least two motion clips to start a multi-cam review pass."
                        } else {
                            "Choose an angle to make it active, then sync clips if the cameras need alignment. Still photos stay hidden here so the angle grid remains camera-focused."
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
                        text = "${videoClips.size} angles",
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = if (selectedClipId != null) "Angle live" else "No angle selected",
                        accent = if (selectedClipId != null) Mocha.Green else Mocha.Overlay1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = "Sync cameras",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "Run a sync pass before switching angles if the camera starts or audio reference drifted across tracks.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Button(
                onClick = onSyncClips,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Base
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync cameras"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Sync angles")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (videoClips.isEmpty()) Mocha.Overlay1 else Mocha.Green) {
            Text(
                text = "Available angles",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )

            if (videoClips.isEmpty()) {
                Text(
                    text = "No motion clips available for multi-cam yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    videoClips.chunked(2).forEachIndexed { rowIndex, rowClips ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowClips.forEachIndexed { index, clip ->
                                val cameraIndex = rowIndex * 2 + index
                                MultiCamAngleCard(
                                    label = "Cam ${'A' + cameraIndex}",
                                    fileName = clip.sourceUri.lastPathSegment?.substringAfterLast('/') ?: "Clip",
                                    isActive = clip.id == selectedClipId,
                                    onClick = { onAngleSelected(clip.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowClips.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
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
    val accent = if (isActive) Mocha.Mauve else Mocha.Blue

    Surface(
        modifier = modifier,
        color = if (isActive) accent.copy(alpha = 0.12f) else Mocha.PanelRaised,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) accent.copy(alpha = 0.28f) else Mocha.CardStroke
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
                        color = if (isActive) accent.copy(alpha = 0.2f) else Mocha.Base,
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = label,
                    tint = if (isActive) accent else Mocha.Overlay1,
                    modifier = Modifier.size(28.dp)
                )

                if (isActive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = Mocha.Mauve,
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected angle",
                                tint = Mocha.Crust,
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
                    color = Mocha.Text
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0,
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

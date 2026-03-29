package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
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
        .take(4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.panel_multi_cam_title), color = Mocha.Text, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onSyncClips,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Mauve),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Mocha.Mauve)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.cd_multicam_sync), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.panel_multi_cam_sync), fontSize = 12.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Mocha.Subtext0, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (videoClips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.panel_multi_cam_no_clips), color = Mocha.Overlay0, fontSize = 13.sp)
            }
        } else {
            val rows = videoClips.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowClips ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowClips.forEach { clip ->
                            val isActive = clip.id == selectedClipId
                            val borderColor = if (isActive) Mocha.Mauve else Mocha.Surface0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                    .background(Mocha.Surface0)
                                    .clickable { onAngleSelected(clip.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = stringResource(R.string.cd_multicam_angle),
                                        tint = if (isActive) Mocha.Mauve else Mocha.Overlay0,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val fileName = clip.sourceUri.lastPathSegment?.substringAfterLast('/') ?: "Clip"
                                    Text(
                                        fileName,
                                        color = if (isActive) Mocha.Text else Mocha.Subtext0,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .background(Mocha.Mauve, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.cd_multicam_selected),
                                            tint = Mocha.Crust,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
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

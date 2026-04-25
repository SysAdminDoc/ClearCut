package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.R
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

/**
 * Desktop-class left sidebar. Rendered beside the editor column when
 * `LocalLayoutMode == DESKTOP` (Samsung DeX, Chromebook, or large-screen with
 * mouse). Keeps the phone layout untouched by being completely absent on
 * `PHONE` / `ONE_HANDED`.
 *
 * Today the sidebar surfaces:
 *   * Project meta (name, duration, resolution)
 *   * Quick actions (Add media, Export, Toggle timeline, v3.69 hub)
 *   * A compact media-library strip — clips already in the project, grouped
 *     by track type, so creators can re-drag an existing asset without
 *     re-opening the Photo Picker.
 *
 * The sidebar is a pure consumer of `EditorViewModel`; it never mutates state
 * directly, so it can be swapped for a richer `MediaBinScreen` later without
 * disturbing the rest of the editor.
 */
@Composable
fun DesktopSidebar(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(Mocha.Mantle)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ProjectHeaderBlock(
            name = state.project.name,
            resolution = state.project.resolution.label,
            fps = state.project.frameRate,
            totalDurationMs = state.totalDurationMs
        )
        QuickActionsBlock(viewModel = viewModel)
        MediaLibraryBlock(viewModel = viewModel, state = state)
    }
}

@Composable
private fun ProjectHeaderBlock(
    name: String,
    resolution: String,
    fps: Int,
    totalDurationMs: Long
) {
    Column {
        Text(
            text = stringResource(R.string.desktop_sidebar_project),
            color = Mocha.Overlay1,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = name,
            color = Mocha.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$resolution · ${fps}fps · ${formatDuration(totalDurationMs)}",
            color = Mocha.Subtext0,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun QuickActionsBlock(viewModel: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.desktop_sidebar_quick),
            color = Mocha.Overlay1,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        SidebarAction(icon = Icons.Default.Add, label = stringResource(R.string.editor_add_media), tint = Mocha.Blue) {
            viewModel.showMediaPicker()
        }
        SidebarAction(icon = Icons.Default.Videocam, label = stringResource(R.string.desktop_sidebar_record), tint = Mocha.Green) {
            viewModel.showMediaPicker()
        }
        SidebarAction(icon = Icons.Default.FileDownload, label = stringResource(R.string.editor_export), tint = Mocha.Rosewater) {
            viewModel.showExportSheet()
        }
        SidebarAction(icon = Icons.Default.AutoAwesome, label = stringResource(R.string.v369_features_label), tint = Mocha.Mauve) {
            viewModel.showV369Features()
        }
    }
}

@Composable
private fun SidebarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(tint.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, color = Mocha.Text, fontSize = 13.sp)
    }
}

@Composable
private fun ColumnScope.MediaLibraryBlock(
    viewModel: EditorViewModel,
    state: EditorState
) {
    val entries = remember(state.tracks) {
        state.tracks
            .flatMap { track -> track.clips.map { it to track.type } }
            .distinctBy { (clip, _) -> clip.sourceUri.toString() }
    }
    Column(modifier = Modifier.weight(1f, fill = true)) {
        Text(
            text = stringResource(R.string.desktop_sidebar_media_count, entries.size),
            color = Mocha.Overlay1,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.sm))
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.desktop_sidebar_media_empty),
                color = Mocha.Subtext0,
                fontSize = 11.sp
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(
                items = entries,
                key = { (clip, _) -> clip.sourceUri.toString() }
            ) { (clip, trackType) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Mocha.PanelRaised)
                        .clickable(role = Role.Button) { viewModel.selectClip(clip.id) }
                        .padding(horizontal = Spacing.sm, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (trackType) {
                            TrackType.VIDEO -> Icons.Default.Movie
                            TrackType.AUDIO -> Icons.Default.MusicNote
                            TrackType.OVERLAY -> Icons.Default.Layers
                            TrackType.TEXT -> Icons.Default.TextFields
                            TrackType.ADJUSTMENT -> Icons.Default.Tune
                        },
                        contentDescription = null,
                        tint = Mocha.Subtext1,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = clip.sourceUri.lastPathSegment?.substringAfterLast('/') ?: "clip",
                        color = Mocha.Text,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    val h = m / 60
    val mm = m % 60
    return if (h > 0) "%d:%02d:%02d".format(h, mm, r) else "%d:%02d".format(m, r)
}

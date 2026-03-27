package com.novacut.editor.ui.projects

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

data class ProjectTemplateUI(
    val id: String,
    val nameResId: Int,
    val descriptionResId: Int,
    val category: TemplateCategory,
    val icon: ImageVector,
    val accentColor: Color,
    val aspectRatio: AspectRatio,
    val tracks: List<TrackType>,
    val suggestedDurationResId: Int
)

val projectTemplates = listOf(
    ProjectTemplateUI(
        id = "blank", nameResId = R.string.template_blank_name, descriptionResId = R.string.template_blank_desc,
        category = TemplateCategory.BLANK, icon = Icons.Default.Add, accentColor = Mocha.Subtext0,
        aspectRatio = AspectRatio.RATIO_16_9, tracks = listOf(TrackType.VIDEO, TrackType.AUDIO),
        suggestedDurationResId = R.string.template_blank_duration
    ),
    ProjectTemplateUI(
        id = "vlog", nameResId = R.string.template_vlog_name, descriptionResId = R.string.template_vlog_desc,
        category = TemplateCategory.VLOG, icon = Icons.Default.Videocam, accentColor = Mocha.Mauve,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_vlog_duration
    ),
    ProjectTemplateUI(
        id = "tutorial", nameResId = R.string.template_tutorial_name, descriptionResId = R.string.template_tutorial_desc,
        category = TemplateCategory.TUTORIAL, icon = Icons.Default.School, accentColor = Mocha.Blue,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_tutorial_duration
    ),
    ProjectTemplateUI(
        id = "short_tiktok", nameResId = R.string.template_short_tiktok_name, descriptionResId = R.string.template_short_tiktok_desc,
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.PhoneAndroid, accentColor = Mocha.Red,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_short_tiktok_duration
    ),
    ProjectTemplateUI(
        id = "short_reel", nameResId = R.string.template_reel_name, descriptionResId = R.string.template_reel_desc,
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.CameraRoll, accentColor = Mocha.Peach,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_reel_duration
    ),
    ProjectTemplateUI(
        id = "cinematic", nameResId = R.string.template_cinematic_name, descriptionResId = R.string.template_cinematic_desc,
        category = TemplateCategory.CINEMATIC, icon = Icons.Default.Movie, accentColor = Mocha.Yellow,
        aspectRatio = AspectRatio.RATIO_21_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_cinematic_duration
    ),
    ProjectTemplateUI(
        id = "slideshow", nameResId = R.string.template_slideshow_name, descriptionResId = R.string.template_slideshow_desc,
        category = TemplateCategory.SLIDESHOW, icon = Icons.Default.PhotoLibrary, accentColor = Mocha.Green,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_slideshow_duration
    ),
    ProjectTemplateUI(
        id = "promo", nameResId = R.string.template_promo_name, descriptionResId = R.string.template_promo_desc,
        category = TemplateCategory.PROMO, icon = Icons.Default.Campaign, accentColor = Mocha.Teal,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_promo_duration
    ),
    ProjectTemplateUI(
        id = "square_social", nameResId = R.string.template_square_name, descriptionResId = R.string.template_square_desc,
        category = TemplateCategory.PROMO, icon = Icons.Default.CropSquare, accentColor = Mocha.Blue,
        aspectRatio = AspectRatio.RATIO_1_1,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_square_duration
    )
)

@Composable
fun ProjectTemplateSheet(
    onTemplateSelected: (ProjectTemplateUI) -> Unit,
    onDismiss: () -> Unit,
    onUserTemplateSelected: (UserTemplate) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onShareTemplate: (String) -> Unit = {},
    onImportTemplate: () -> Unit = {},
    userTemplates: List<UserTemplate> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.template_new_project), color = Mocha.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.close), tint = Mocha.Subtext0)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.template_choose), color = Mocha.Subtext0, fontSize = 13.sp)

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onImportTemplate,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Mocha.Blue.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null, tint = Mocha.Blue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import Template", color = Mocha.Blue, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (userTemplates.isEmpty()) 400.dp else 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(projectTemplates, key = { it.id }) { template ->
                ProjectTemplateCard(
                    template = template,
                    onClick = { onTemplateSelected(template) }
                )
            }
        }

        // User templates section
        if (userTemplates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.template_my_templates), color = Mocha.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userTemplates, key = { it.id }) { ut ->
                    UserTemplateCard(
                        template = ut,
                        onClick = { onUserTemplateSelected(ut) },
                        onDelete = { onDeleteUserTemplate(ut.id) },
                        onShare = { onShareTemplate(ut.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserTemplateCard(
    template: UserTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mocha.Surface0)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Mocha.Mauve.copy(alpha = 0.15f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bookmark,
                template.name,
                tint = Mocha.Mauve,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    template.name,
                    color = Mocha.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Share,
                    "Share",
                    tint = Mocha.Blue.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onShare)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.template_delete),
                    tint = Mocha.Subtext0.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onDelete)
                )
            }
            Text(
                if (template.textOverlayCount > 0) stringResource(R.string.template_tracks_texts_format, template.trackTypes.size, template.textOverlayCount)
                else stringResource(R.string.template_tracks_format, template.trackTypes.size),
                color = Mocha.Subtext0,
                fontSize = 9.sp,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    template.aspectRatio.label,
                    color = Mocha.Mauve,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(Mocha.Mauve.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ProjectTemplateCard(
    template: ProjectTemplateUI,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Mocha.Surface0)
            .clickable(onClick = onClick)
    ) {
        // Header with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(template.accentColor.copy(alpha = 0.2f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                template.icon,
                stringResource(template.nameResId),
                tint = template.accentColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(stringResource(template.nameResId), color = Mocha.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(template.descriptionResId), color = Mocha.Subtext0, fontSize = 10.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    template.aspectRatio.label,
                    color = template.accentColor,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(template.accentColor.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Text(
                    stringResource(template.suggestedDurationResId),
                    color = Mocha.Subtext0,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(Mocha.Surface0, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

package com.novacut.editor.ui.projects

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.model.*
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutMetricPill
import com.novacut.editor.ui.theme.ClearCutSectionHeader
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing

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
        category = TemplateCategory.BLANK, icon = Icons.Default.Add, accentColor = ClearCutAccents.Neutral,
        aspectRatio = AspectRatio.RATIO_16_9, tracks = listOf(TrackType.VIDEO, TrackType.AUDIO),
        suggestedDurationResId = R.string.template_blank_duration
    ),
    ProjectTemplateUI(
        id = "vlog", nameResId = R.string.template_vlog_name, descriptionResId = R.string.template_vlog_desc,
        category = TemplateCategory.VLOG, icon = Icons.Default.Videocam, accentColor = ClearCutAccents.Mauve,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_vlog_duration
    ),
    ProjectTemplateUI(
        id = "tutorial", nameResId = R.string.template_tutorial_name, descriptionResId = R.string.template_tutorial_desc,
        category = TemplateCategory.TUTORIAL, icon = Icons.Default.School, accentColor = ClearCutAccents.Blue,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_tutorial_duration
    ),
    ProjectTemplateUI(
        id = "short_tiktok", nameResId = R.string.template_short_tiktok_name, descriptionResId = R.string.template_short_tiktok_desc,
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.PhoneAndroid, accentColor = ClearCutAccents.Red,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_short_tiktok_duration
    ),
    ProjectTemplateUI(
        id = "short_reel", nameResId = R.string.template_reel_name, descriptionResId = R.string.template_reel_desc,
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.CameraRoll, accentColor = ClearCutAccents.Peach,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_reel_duration
    ),
    ProjectTemplateUI(
        id = "cinematic", nameResId = R.string.template_cinematic_name, descriptionResId = R.string.template_cinematic_desc,
        category = TemplateCategory.CINEMATIC, icon = Icons.Default.Movie, accentColor = ClearCutAccents.Yellow,
        aspectRatio = AspectRatio.RATIO_21_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_cinematic_duration
    ),
    ProjectTemplateUI(
        id = "slideshow", nameResId = R.string.template_slideshow_name, descriptionResId = R.string.template_slideshow_desc,
        category = TemplateCategory.SLIDESHOW, icon = Icons.Default.PhotoLibrary, accentColor = ClearCutAccents.Green,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_slideshow_duration
    ),
    ProjectTemplateUI(
        id = "promo", nameResId = R.string.template_promo_name, descriptionResId = R.string.template_promo_desc,
        category = TemplateCategory.PROMO, icon = Icons.Default.Campaign, accentColor = ClearCutAccents.Teal,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_promo_duration
    ),
    ProjectTemplateUI(
        id = "square_social", nameResId = R.string.template_square_name, descriptionResId = R.string.template_square_desc,
        category = TemplateCategory.PROMO, icon = Icons.Default.CropSquare, accentColor = ClearCutAccents.Blue,
        aspectRatio = AspectRatio.RATIO_1_1,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDurationResId = R.string.template_square_duration
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectTemplateSheet(
    onTemplateSelected: (ProjectTemplateUI, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUserTemplateSelected: (UserTemplate) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onShareTemplate: (String) -> Unit = {},
    onImportTemplate: () -> Unit = {},
    userTemplates: List<UserTemplate> = emptyList(),
) {
    var pendingDeleteTemplate by remember { mutableStateOf<UserTemplate?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .testTag(ClearCutTestTags.TEMPLATE_SHEET)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .background(LocalClearCutColors.current.panel, RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl))
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(LocalClearCutColors.current.surfaceHigh.copy(alpha = 0.55f))
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.template_new_project),
                    color = LocalClearCutColors.current.text,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.template_headline),
                    color = ClearCutAccents.Rosewater,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ClearCutChromeIconButton(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                onClick = onDismiss
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.template_subtitle),
            color = LocalClearCutColors.current.subtext,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClearCutMetricPill(
                text = stringResource(R.string.projects_templates_count, projectTemplates.size),
                accent = ClearCutAccents.Mauve,
                icon = Icons.Default.DashboardCustomize
            )
            if (userTemplates.isNotEmpty()) {
                ClearCutMetricPill(
                    text = stringResource(R.string.template_saved_count, userTemplates.size),
                    accent = ClearCutAccents.Sapphire,
                    icon = Icons.Default.BookmarkAdded
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            onClick = onImportTemplate,
            modifier = Modifier.fillMaxWidth(),
            color = LocalClearCutColors.current.panelHighest,
            shape = RoundedCornerShape(Radius.xl),
            border = androidx.compose.foundation.BorderStroke(1.dp, LocalClearCutColors.current.cardStrokeStrong)
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 76.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(ClearCutAccents.Sapphire.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = null,
                        tint = ClearCutAccents.Sapphire,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.template_import),
                        color = LocalClearCutColors.current.text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.template_import_description),
                        color = LocalClearCutColors.current.subtext,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TemplateSectionHeader(
            title = stringResource(R.string.template_built_in_section),
            description = stringResource(R.string.template_built_in_description)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier
                .testTag(ClearCutTestTags.TEMPLATE_GRID)
                .fillMaxWidth()
                .heightIn(max = if (userTemplates.isEmpty()) 460.dp else 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(projectTemplates, key = { it.id }) { template ->
                ProjectTemplateCard(
                    template = template,
                    onClick = { templateName -> onTemplateSelected(template, templateName) }
                )
            }
        }

        // User templates section
        if (userTemplates.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            TemplateSectionHeader(
                title = stringResource(R.string.template_my_templates),
                description = stringResource(R.string.template_my_templates_description)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(userTemplates, key = { it.id }) { ut ->
                    UserTemplateCard(
                        template = ut,
                        onClick = { onUserTemplateSelected(ut) },
                        onDelete = { pendingDeleteTemplate = ut },
                        onShare = { onShareTemplate(ut.id) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
            TemplateSectionHeader(
                title = stringResource(R.string.template_my_templates),
                description = stringResource(R.string.template_my_templates_description)
            )
            EmptyTemplateStateCard()
        }
    }

    pendingDeleteTemplate?.let { template ->
        DeleteUserTemplateDialog(
            templateName = template.name,
            onDismissRequest = { pendingDeleteTemplate = null },
            onConfirm = {
                pendingDeleteTemplate = null
                onDeleteUserTemplate(template.id)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserTemplateCard(
    template: UserTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {}
) {
    val templateDescription = stringResource(
        R.string.template_user_card_cd,
        template.name,
        template.aspectRatio.label
    )

    Column(
        modifier = Modifier
            .height(176.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(LocalClearCutColors.current.panelHighest)
            .border(1.dp, LocalClearCutColors.current.cardStrokeStrong, RoundedCornerShape(Radius.xl))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = templateDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(ClearCutAccents.Mauve.copy(alpha = 0.24f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bookmark,
                null,
                tint = ClearCutAccents.Mauve,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    template.name,
                    color = LocalClearCutColors.current.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TemplateActionButton(
                        icon = Icons.Default.Share,
                        contentDescription = stringResource(R.string.template_share_cd_format, template.name),
                        tint = ClearCutAccents.Blue,
                        onClick = onShare
                    )
                    TemplateActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.template_delete_cd_format, template.name),
                        tint = ClearCutAccents.Red,
                        onClick = onDelete
                    )
                }
            }
            Text(
                if (template.textOverlayCount > 0) stringResource(R.string.template_tracks_texts_format, template.trackTypes.size, template.textOverlayCount)
                else stringResource(R.string.template_tracks_format, template.trackTypes.size),
                color = LocalClearCutColors.current.subtext,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TemplateBadge(text = template.aspectRatio.label, accent = ClearCutAccents.Mauve)
                if (template.compatibility.slotCount > 0) {
                    TemplateBadge(
                        text = stringResource(R.string.template_slots_format, template.compatibility.slotCount),
                        accent = ClearCutAccents.Sapphire
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectTemplateCard(
    template: ProjectTemplateUI,
    onClick: (String) -> Unit
) {
    val templateName = stringResource(template.nameResId)
    val category = formatCategory(template.category)
    val templateDescription = stringResource(
        R.string.template_builtin_card_cd,
        templateName,
        category,
        template.aspectRatio.label
    )

    Column(
        modifier = Modifier
            .height(184.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(LocalClearCutColors.current.panelHighest)
            .border(1.dp, LocalClearCutColors.current.cardStrokeStrong, RoundedCornerShape(Radius.xl))
            .then(
                if (template.id == "blank") Modifier.testTag(ClearCutTestTags.TEMPLATE_BLANK) else Modifier
            )
            .clickable(role = Role.Button, onClick = { onClick(templateName) })
            .semantics { contentDescription = templateDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(template.accentColor.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.TopStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(LocalClearCutColors.current.canvas.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        template.icon,
                        null,
                        tint = template.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    template.aspectRatio.label,
                    color = template.accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(LocalClearCutColors.current.canvas.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    templateName,
                    color = LocalClearCutColors.current.text,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(template.descriptionResId),
                    color = LocalClearCutColors.current.subtext,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TemplateBadge(text = category, accent = template.accentColor)
                TemplateBadge(text = stringResource(template.suggestedDurationResId), accent = LocalClearCutColors.current.subtext)
                TemplateBadge(text = stringResource(R.string.template_tracks_format, template.tracks.size), accent = LocalClearCutColors.current.subtext)
            }
        }
    }
}

@Composable
private fun TemplateBadge(
    text: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(Radius.sm),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TemplateSectionHeader(
    title: String,
    description: String
) {
    ClearCutSectionHeader(
        title = title,
        description = description
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EmptyTemplateStateCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LocalClearCutColors.current.panelHighest,
        shape = RoundedCornerShape(Radius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalClearCutColors.current.cardStrokeStrong)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.template_saved_empty_title),
                color = LocalClearCutColors.current.text,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.template_saved_empty_body),
                color = LocalClearCutColors.current.subtext,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DeleteUserTemplateDialog(
    templateName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            ClearCutDialogIcon(
                icon = Icons.Default.Delete,
                accent = ClearCutAccents.Red
            )
        },
        title = {
            Text(
                text = stringResource(R.string.template_delete_confirm_title),
                color = LocalClearCutColors.current.text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.template_delete_confirm_body, templateName),
                color = LocalClearCutColors.current.subtext,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            ClearCutSecondaryButton(
                text = stringResource(R.string.template_delete_confirm_action),
                onClick = onConfirm,
                icon = Icons.Default.Delete,
                contentColor = ClearCutAccents.Red
            )
        },
        dismissButton = {
            ClearCutSecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest
            )
        },
        containerColor = LocalClearCutColors.current.panelHighest,
        titleContentColor = LocalClearCutColors.current.text,
        textContentColor = LocalClearCutColors.current.subtext,
        shape = RoundedCornerShape(Radius.xxl)
    )
}

@Composable
private fun TemplateActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    ClearCutChromeIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = tint,
        containerColor = tint.copy(alpha = 0.12f),
        borderColor = tint.copy(alpha = 0.18f),
        shape = RoundedCornerShape(Radius.md)
    )
}

private fun formatCategory(category: TemplateCategory): String {
    return category.name
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

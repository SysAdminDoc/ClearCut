package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.engine.CaptionImportEngine
import com.novacut.editor.engine.CaptionTranslationEngine.EditorRow
import com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import com.novacut.editor.model.CaptionStyleType
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionEditorPanel(
    captions: List<Caption>,
    playheadMs: Long,
    clipDurationMs: Long,
    onAddCaption: (Caption) -> Unit,
    onUpdateCaption: (Caption) -> Unit,
    onDeleteCaption: (String) -> Unit,
    onGenerateAutoCaption: () -> Unit,
    onImportCaptions: () -> Unit = {},
    captionImportPreview: CaptionImportEngine.Preview? = null,
    onApplyCaptionImport: () -> Unit = {},
    onDismissCaptionImport: () -> Unit = {},
    translationRows: List<EditorRow> = emptyList(),
    translationSourceLang: String = "en",
    translationTargetLang: String? = null,
    translationQuality: LanguagePairQuality? = null,
    translationTargets: List<String> = emptyList(),
    translationUnavailable: Boolean = false,
    translationOffline: Boolean = false,
    onTranslationTargetSelected: (String) -> Unit = {},
    onTranslationUserEdit: (rowIndex: Int, newTargetText: String) -> Unit = { _, _ -> },
    onTranslationRegenerate: (rowIndex: Int) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    var editingCaption by remember { mutableStateOf<Caption?>(null) }
    var selectedStyleType by remember { mutableStateOf(CaptionStyleType.SUBTITLE_BAR) }
    // This panel survives clip switches (it lives in a keyless BottomSheetSlot), so the
    // edit target can outlive its clip. Re-resolve it against the current caption list
    // each time that list changes — keeps the form bound to the live object and clears
    // it when the caption no longer exists (e.g. after switching clips).
    LaunchedEffect(captions) {
        editingCaption = editingCaption?.let { e -> captions.find { it.id == e.id } }
    }
    val activeCaptionCount = captions.count { playheadMs in it.startTimeMs..it.endTimeMs }
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 430
    val captionStylesSectionDescription = stringResource(R.string.cd_caption_styles_section)
    val newCaptionText = stringResource(R.string.caption_new_default)

    fun createCaption() {
        val newCaption = Caption(
            text = newCaptionText,
            startTimeMs = playheadMs,
            endTimeMs = (playheadMs + 2_000L).coerceAtMost(clipDurationMs),
            style = CaptionStyle(type = selectedStyleType)
        )
        onAddCaption(newCaption)
        editingCaption = newCaption
    }

    PremiumEditorPanel(
        title = stringResource(R.string.caption_title),
        subtitle = stringResource(R.string.caption_panel_subtitle),
        icon = Icons.Default.ClosedCaption,
        accent = ClearCutAccents.Yellow,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.caption_close_cd),
        modifier = modifier,
        scrollable = true,
        headerActions = {
            PremiumPanelIconButton(
                icon = Icons.Default.FileOpen,
                contentDescription = stringResource(R.string.caption_import_cd),
                onClick = onImportCaptions,
                tint = ClearCutAccents.Blue
            )
            PremiumPanelIconButton(
                icon = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.cd_caption_auto),
                onClick = onGenerateAutoCaption,
                tint = ClearCutAccents.Yellow
            )
            PremiumPanelIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.caption_add_cd),
                onClick = ::createCaption,
                tint = ClearCutAccents.Green
            )
        }
    ) {
        PremiumPanelCard(accent = if (activeCaptionCount > 0) ClearCutAccents.Green else ClearCutAccents.Yellow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.caption_system_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = semanticColors.text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.caption_system_description),
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
                        text = pluralStringResource(
                            R.plurals.caption_total_count,
                            captions.size,
                            captions.size,
                        ),
                        accent = ClearCutAccents.Blue
                    )
                    PremiumPanelPill(
                        text = if (activeCaptionCount > 0) {
                            stringResource(R.string.caption_active_count, activeCaptionCount)
                        } else {
                            stringResource(R.string.caption_ready)
                        },
                        accent = if (activeCaptionCount > 0) ClearCutAccents.Green else ClearCutAccents.Yellow
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionMetric(
                    title = stringResource(R.string.caption_playhead),
                    value = formatSeconds(playheadMs),
                    accent = ClearCutAccents.Peach,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
                CaptionMetric(
                    title = stringResource(R.string.caption_default_style),
                    value = stringResource(selectedStyleType.displayNameRes()),
                    accent = ClearCutAccents.Mauve,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Mauve) {
            Text(
                text = stringResource(R.string.caption_default_style_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = stringResource(R.string.caption_default_style_description),
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            FlowRow(
                modifier = Modifier.semantics {
                    contentDescription = captionStylesSectionDescription
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionStyleType.entries.forEach { styleType ->
                    FilterChip(
                        selected = styleType == selectedStyleType,
                        onClick = { selectedStyleType = styleType },
                        label = {
                            Text(
                                text = stringResource(styleType.displayNameRes()),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClearCutAccents.Mauve.copy(alpha = 0.18f),
                            selectedLabelColor = ClearCutAccents.Mauve,
                            containerColor = semanticColors.panelRaised,
                            labelColor = semanticColors.subtext
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Green) {
            CaptionTranslationPanel(
                rows = translationRows,
                sourceLang = translationSourceLang,
                targetLang = translationTargetLang,
                currentQuality = translationQuality,
                availableTargets = translationTargets,
                onTargetSelected = onTranslationTargetSelected,
                onUserEdit = onTranslationUserEdit,
                onRegenerate = onTranslationRegenerate,
                unavailable = translationUnavailable,
                offline = translationOffline,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = ClearCutAccents.Blue) {
            Text(
                text = stringResource(R.string.caption_list_title),
                style = MaterialTheme.typography.titleMedium,
                color = semanticColors.text
            )
            Text(
                text = if (captions.isEmpty()) {
                    stringResource(R.string.caption_list_empty_description)
                } else {
                    stringResource(R.string.caption_list_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = semanticColors.subtext
            )

            if (captions.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = semanticColors.panelRaised,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.caption_no_captions),
                            style = MaterialTheme.typography.titleSmall,
                            color = semanticColors.text
                        )
                        Text(
                            text = stringResource(R.string.caption_empty_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.subtext
                        )
                        if (isCompactLayout) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onGenerateAutoCaption,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, ClearCutAccents.Yellow.copy(alpha = 0.35f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Yellow)
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.cd_auto_awesome)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.panel_caption_auto_generate))
                                }

                                Button(
                                    onClick = ::createCaption,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve)
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.caption_add_cd)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.caption_add))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onGenerateAutoCaption,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, ClearCutAccents.Yellow.copy(alpha = 0.35f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClearCutAccents.Yellow)
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.cd_auto_awesome)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.panel_caption_auto_generate))
                                }

                                Button(
                                    onClick = ::createCaption,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve)
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.caption_add_cd)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.caption_add))
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    captions.sortedBy { it.startTimeMs }.forEach { caption ->
                        CaptionListCard(
                            caption = caption,
                            playheadMs = playheadMs,
                            isEditing = editingCaption?.id == caption.id,
                            onEdit = { editingCaption = caption },
                            onDelete = { onDeleteCaption(caption.id) }
                        )
                    }
                }
            }
        }

        editingCaption?.let { caption ->
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = ClearCutAccents.Yellow) {
                Text(
                    text = stringResource(R.string.caption_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = semanticColors.text
                )
                Text(
                    text = stringResource(R.string.caption_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semanticColors.subtext
                )

                CaptionEditForm(
                    caption = caption,
                    clipDurationMs = clipDurationMs,
                    onUpdate = { updated ->
                        onUpdateCaption(updated)
                        editingCaption = updated
                    },
                    onDone = { editingCaption = null }
                )
            }
        }
    }

    captionImportPreview?.let { preview ->
        CaptionImportPreviewDialog(
            preview = preview,
            onApply = onApplyCaptionImport,
            onDismiss = onDismissCaptionImport,
        )
    }
}

@Composable
private fun CaptionImportPreviewDialog(
    preview: CaptionImportEngine.Preview,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val confidence = (preview.languageConfidence * 100f).roundToInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.caption_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (preview.failure == null) {
                        stringResource(R.string.caption_import_description)
                    } else {
                        stringResource(R.string.caption_import_invalid)
                    },
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.caption_import_format, preview.format.displayName))
                Text(stringResource(R.string.caption_import_encoding, preview.encoding?.displayName ?: "unknown"))
                Text(stringResource(R.string.caption_import_cues, preview.cues.size))
                Text(stringResource(R.string.caption_import_duration, formatCaptionImportDuration(preview.durationMs)))
                Text(stringResource(R.string.caption_import_language, preview.language, confidence))
                Text(stringResource(R.string.caption_import_overlaps, preview.overlapCount))
                Text(stringResource(R.string.caption_import_invalid_cues, preview.invalidCueCount))
                Text(
                    text = stringResource(R.string.caption_import_mapping),
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.bodySmall,
                )
                preview.failure?.let { failure ->
                    Text(
                        text = failure.displayName,
                        color = ClearCutAccents.Red,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                preview.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        color = ClearCutAccents.Yellow,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (preview.isValid) {
                TextButton(onClick = onApply) {
                    Text(stringResource(R.string.caption_import_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = semanticColors.panelHighest,
        titleContentColor = semanticColors.text,
        textContentColor = semanticColors.subtext,
    )
}

private fun formatCaptionImportDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun CaptionMetric(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalClearCutColors.current
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CaptionListCard(
    caption: Caption,
    playheadMs: Long,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val isActive = playheadMs in caption.startTimeMs..caption.endTimeMs
    val accent = when {
        isEditing -> ClearCutAccents.Mauve
        isActive -> ClearCutAccents.Green
        else -> ClearCutAccents.Blue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        color = if (isEditing) accent.copy(alpha = 0.12f) else semanticColors.panelRaised,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (isEditing || isActive) accent.copy(alpha = 0.2f) else semanticColors.cardStroke
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = semanticColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.caption_time_range_format,
                            formatSeconds(caption.startTimeMs),
                            formatSeconds(caption.endTimeMs),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.subtext
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PremiumPanelPill(
                    text = when {
                        isEditing -> stringResource(R.string.caption_status_editing)
                        isActive -> stringResource(R.string.caption_status_live)
                        else -> stringResource(caption.style.type.displayNameRes())
                    },
                    accent = accent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (caption.words.isNotEmpty()) {
                        stringResource(R.string.caption_word_count, caption.words.size)
                    } else {
                        stringResource(R.string.caption_manual)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (caption.words.isNotEmpty()) ClearCutAccents.Peach else semanticColors.subtext
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumPanelIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_caption_edit),
                        onClick = onEdit,
                        tint = ClearCutAccents.Blue
                    )
                    PremiumPanelIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.caption_delete_cd),
                        onClick = onDelete,
                        tint = ClearCutAccents.Red
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionEditForm(
    caption: Caption,
    clipDurationMs: Long,
    onUpdate: (Caption) -> Unit,
    onDone: () -> Unit
) {
    val semanticColors = LocalClearCutColors.current
    val isCompactLayout = LocalConfiguration.current.screenWidthDp < 430
    val captionStylesSectionDescription = stringResource(R.string.cd_caption_styles_section)
    var text by remember(caption.id) { mutableStateOf(caption.text) }
    var startTime by remember(caption.id) { mutableFloatStateOf(caption.startTimeMs / 1000f) }
    var endTime by remember(caption.id) { mutableFloatStateOf(caption.endTimeMs / 1000f) }
    var fontSize by remember(caption.id) { mutableFloatStateOf(caption.style.fontSize) }
    var positionY by remember(caption.id) { mutableFloatStateOf(caption.style.positionY) }
    var styleType by remember(caption.id) { mutableStateOf(caption.style.type) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.caption_text_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ClearCutAccents.Mauve,
                unfocusedBorderColor = semanticColors.cardStroke,
                focusedTextColor = semanticColors.text,
                unfocusedTextColor = semanticColors.text,
                cursorColor = ClearCutAccents.Mauve,
                focusedLabelColor = ClearCutAccents.Mauve,
                unfocusedLabelColor = semanticColors.subtext
            ),
            maxLines = 3,
            textStyle = TextStyle(fontSize = 15.sp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptionMetric(
                title = stringResource(R.string.caption_start_metric),
                value = formatSeconds((startTime * 1000f).toLong()),
                accent = ClearCutAccents.Blue,
                modifier = Modifier.widthIn(min = 132.dp)
            )
            CaptionMetric(
                title = stringResource(R.string.caption_end_metric),
                value = formatSeconds((endTime * 1000f).toLong()),
                accent = ClearCutAccents.Green,
                modifier = Modifier.widthIn(min = 132.dp)
            )
        }

        CaptionSlider(
            label = stringResource(R.string.caption_start_time),
            value = startTime,
            valueRange = 0f..(clipDurationMs / 1000f),
            accent = ClearCutAccents.Blue,
            onValueChange = { startTime = it.coerceAtMost(endTime) }
        )
        CaptionSlider(
            label = stringResource(R.string.caption_end_time),
            value = endTime,
            valueRange = 0f..(clipDurationMs / 1000f),
            accent = ClearCutAccents.Green,
            onValueChange = { endTime = it.coerceAtLeast(startTime) }
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.panel_caption_style_label),
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
            )
            FlowRow(
                modifier = Modifier.semantics {
                    contentDescription = captionStylesSectionDescription
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionStyleType.entries.forEach { type ->
                    FilterChip(
                        selected = type == styleType,
                        onClick = { styleType = type },
                        label = { Text(stringResource(type.displayNameRes())) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClearCutAccents.Mauve.copy(alpha = 0.18f),
                            selectedLabelColor = ClearCutAccents.Mauve,
                            containerColor = semanticColors.panelRaised,
                            labelColor = semanticColors.subtext
                        )
                    )
                }
            }
        }

        CaptionSlider(
            label = stringResource(R.string.caption_font_size),
            value = fontSize,
            valueRange = 16f..72f,
            accent = ClearCutAccents.Mauve,
            onValueChange = { fontSize = it },
            valueFormatter = { "${it.toInt()} pt" }
        )
        CaptionSlider(
            label = stringResource(R.string.panel_caption_position_y),
            value = positionY,
            valueRange = 0.1f..0.95f,
            accent = ClearCutAccents.Yellow,
            onValueChange = { positionY = it },
            valueFormatter = { "%.0f%%".format(it * 100f) }
        )

        if (isCompactLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = semanticColors.subtext)
                ) {
                    Text(text = stringResource(R.string.done))
                }

                Button(
                    onClick = {
                        onUpdate(
                            caption.copy(
                                text = text.trim(),
                                startTimeMs = (startTime * 1000f).toLong(),
                                endTimeMs = (endTime * 1000f).toLong(),
                                style = caption.style.copy(
                                    type = styleType,
                                    fontSize = fontSize,
                                    positionY = positionY
                                )
                            )
                        )
                        onDone()
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = stringResource(R.string.panel_caption_save))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, semanticColors.cardStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = semanticColors.subtext)
                ) {
                    Text(text = stringResource(R.string.done))
                }

                Button(
                    onClick = {
                        onUpdate(
                            caption.copy(
                                text = text.trim(),
                                startTimeMs = (startTime * 1000f).toLong(),
                                endTimeMs = (endTime * 1000f).toLong(),
                                style = caption.style.copy(
                                    type = styleType,
                                    fontSize = fontSize,
                                    positionY = positionY
                                )
                            )
                        )
                        onDone()
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ClearCutAccents.Mauve),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = stringResource(R.string.panel_caption_save))
                }
            }
        }
    }
}

@Composable
private fun CaptionSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: androidx.compose.ui.graphics.Color,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.1fs".format(it) }
) {
    val semanticColors = LocalClearCutColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = semanticColors.subtext
            )
            PremiumPanelPill(text = valueFormatter(value), accent = accent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = semanticColors.surface
            )
        )
    }
}

private fun formatSeconds(ms: Long): String {
    val totalSeconds = (ms / 1000f).coerceAtLeast(0f)
    return if (totalSeconds >= 60f) {
        val minutes = (totalSeconds / 60f).toInt()
        val seconds = totalSeconds % 60f
        String.format(Locale.getDefault(), "%d:%04.1f", minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%.1fs", totalSeconds)
    }
}

private fun CaptionStyleType.displayNameRes(): Int = when (this) {
    CaptionStyleType.SUBTITLE_BAR -> R.string.caption_style_type_subtitle_bar
    CaptionStyleType.WORD_BY_WORD -> R.string.caption_style_type_word_by_word
    CaptionStyleType.KARAOKE -> R.string.caption_style_type_karaoke
    CaptionStyleType.BOUNCE -> R.string.caption_style_type_bounce
    CaptionStyleType.TYPEWRITER -> R.string.caption_style_type_typewriter
    CaptionStyleType.MINIMAL -> R.string.caption_style_type_minimal
}

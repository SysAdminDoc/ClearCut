package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.CaptionTranslationEngine.EditorRow
import com.novacut.editor.engine.CaptionTranslationEngine.EditorRowState
import com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality
import com.novacut.editor.ui.theme.Radius

/**
 * Caption translation panel (R5.4a UI / RESEARCH_FEATURE_PLAN_2026-05-25
 * Highest-Value #7).
 *
 * Renders the side-by-side source/target caption rows for the
 * `CaptionTranslationEngine`. The engine's editor-row state machine
 * (`buildEditorRows`, `applyUserEdit`, `markRegeneratePending`,
 * `completeRegenerate`) owns every mutation; this composable surfaces the
 * data and forwards user actions back to the host via callbacks.
 *
 * The panel does NOT call the engine's `translate()` itself — that's the
 * host's responsibility because translation may take several seconds and
 * must run on a background scope. The host:
 *   1. Calls `engine.translate(segments, sourceLang, targetLang)`.
 *   2. Wraps the result in `engine.buildEditorRows(...)` for [rows].
 *   3. Wires [onUserEdit] / [onRegenerate] to mutate that same list via
 *      `engine.applyUserEdit(...)` / `engine.markRegeneratePending(...)`.
 *
 * Empty [rows] renders the "Pick a target language to begin" empty state
 * so the panel can be opened before any translation has run.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionTranslationPanel(
    rows: List<EditorRow>,
    sourceLang: String,
    targetLang: String?,
    currentQuality: LanguagePairQuality?,
    availableTargets: List<String>,
    onTargetSelected: (String) -> Unit,
    onUserEdit: (rowIndex: Int, newTargetText: String) -> Unit,
    onRegenerate: (rowIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        TargetPicker(
            sourceLang = sourceLang,
            targetLang = targetLang,
            availableTargets = availableTargets,
            currentQuality = currentQuality,
            onTargetSelected = onTargetSelected,
        )
        if (unavailable) {
            UnavailableState()
        } else if (rows.isEmpty() || targetLang.isNullOrBlank()) {
            EmptyState()
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rows.forEach { row ->
                    TranslationRow(
                        row = row,
                        onUserEdit = { newText -> onUserEdit(row.index, newText) },
                        onRegenerate = { onRegenerate(row.index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    val semanticColors = LocalClearCutColors.current
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.caption_translation_title),
            color = semanticColors.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.caption_translation_subtitle),
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetPicker(
    sourceLang: String,
    targetLang: String?,
    availableTargets: List<String>,
    currentQuality: LanguagePairQuality?,
    onTargetSelected: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (target in availableTargets.distinct().sorted()) {
                if (target == sourceLang) continue
                LanguageChip(
                    code = target,
                    selected = target == targetLang,
                    onClick = { onTargetSelected(target) },
                )
            }
        }
        if (!targetLang.isNullOrBlank() && currentQuality != null) {
            Spacer(modifier = Modifier.height(8.dp))
            QualityChip(currentQuality)
        }
    }
}

@Composable
private fun LanguageChip(code: String, selected: Boolean, onClick: () -> Unit) {
    val semanticColors = LocalClearCutColors.current
    val container = if (selected) ClearCutAccents.Mauve.copy(alpha = 0.18f) else semanticColors.panelHighest.copy(alpha = 0.5f)
    val border = if (selected) ClearCutAccents.Mauve.copy(alpha = 0.62f) else semanticColors.cardStrokeStrong.copy(alpha = 0.5f)
    val textColor = if (selected) semanticColors.text else semanticColors.subtext
    Text(
        text = code.uppercase(),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.lg))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(Radius.lg))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun QualityChip(quality: LanguagePairQuality) {
    val color = qualityColor(quality)
    Text(
        text = stringResource(R.string.caption_translation_quality_chip_format, quality.displayName),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun qualityColor(quality: LanguagePairQuality): Color {
    val semanticColors = LocalClearCutColors.current
    return when (quality) {
        LanguagePairQuality.EXCELLENT -> ClearCutAccents.Green
        LanguagePairQuality.GOOD -> ClearCutAccents.Sky
        LanguagePairQuality.FAIR -> ClearCutAccents.Yellow
        LanguagePairQuality.EXPERIMENTAL -> ClearCutAccents.Peach
        LanguagePairQuality.UNKNOWN -> semanticColors.subtext
    }
}

@Composable
private fun EmptyState() {
    val semanticColors = LocalClearCutColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(semanticColors.panelHighest.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.caption_translation_no_target),
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun UnavailableState() {
    val semanticColors = LocalClearCutColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(semanticColors.panelHighest.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.caption_translation_unavailable),
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TranslationRow(
    row: EditorRow,
    onUserEdit: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val seg = row.segment
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .border(BorderStroke(1.dp, semanticColors.cardStrokeStrong.copy(alpha = 0.4f)), RoundedCornerShape(Radius.lg))
            .background(semanticColors.panelHighest.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Source row (read-only).
        Text(
            text = seg.sourceText,
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
        // Target row (editable). Status chip on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditableTargetField(
                text = seg.targetText,
                pending = row.isPendingRegenerate,
                onChange = onUserEdit,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            RowStatusChip(seg.editorState)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RegenerateButton(enabled = !row.isPendingRegenerate, onClick = onRegenerate)
        }
    }
}

@Composable
private fun EditableTargetField(
    text: String,
    pending: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = LocalClearCutColors.current
    BasicTextField(
        value = text,
        onValueChange = onChange,
        enabled = !pending,
        textStyle = TextStyle(
            color = if (pending) semanticColors.subtext else semanticColors.text,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
        ),
        cursorBrush = SolidColor(ClearCutAccents.Mauve),
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(semanticColors.backgroundMid.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun RowStatusChip(state: EditorRowState) {
    val (label, color) = when (state) {
        EditorRowState.TRANSLATED -> stringResource(R.string.caption_translation_status_translated) to ClearCutAccents.Green
        EditorRowState.USER_EDITED -> stringResource(R.string.caption_translation_status_edited) to ClearCutAccents.Sky
        EditorRowState.REGENERATE_PENDING -> stringResource(R.string.caption_translation_status_pending) to ClearCutAccents.Yellow
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun RegenerateButton(enabled: Boolean, onClick: () -> Unit) {
    val semanticColors = LocalClearCutColors.current
    val accent = if (enabled) ClearCutAccents.Mauve else semanticColors.subtext.copy(alpha = 0.4f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled, onClick = onClick)
            // Guarantee a 48dp minimum touch target for the icon+label control.
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = stringResource(R.string.caption_translation_regenerate),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

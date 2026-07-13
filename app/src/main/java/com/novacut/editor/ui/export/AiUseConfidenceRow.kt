package com.novacut.editor.ui.export

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.AiUsageLedger
import com.novacut.editor.engine.AiUsageLedger.Chip
import com.novacut.editor.engine.AiUsageLedger.Severity
import com.novacut.editor.ui.theme.Radius

/**
 * Pre-export AI provenance preview (RESEARCH_FEATURE_PLAN_2026-05-25
 * Highest-Value #2).
 *
 * Renders a single ExportSheet confidence row driven by
 * [AiUsageLedger.summarizeForChips]. The row sits alongside the existing
 * Color / HDR confidence row in `ExportSheet.kt` — wiring it in is one
 * Composable call:
 *
 *     AiUseConfidenceRow(chips = AiUsageLedger.summarizeForChips(state.aiUsageLedger))
 *
 * Each chip exposes the bucket's count + total range + distinct models
 * via [Chip.describe] so the row stays self-contained — no string
 * resources for per-chip body text.
 *
 * Tap-to-expand surfaces the per-clip detail through [onChipClick]; the
 * Composable defaults to a no-op so the wiring commit can adopt the row
 * before the detail panel ships.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiUseConfidenceRow(
    chips: List<Chip>,
    modifier: Modifier = Modifier,
    onChipClick: (Chip) -> Unit = {},
) {
    val semanticColors = LocalClearCutColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.export_ai_use_title),
            color = semanticColors.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.export_ai_use_description),
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (chips.isEmpty()) {
            EmptyAiUseRow()
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (chip in chips) {
                    AiUseChip(chip = chip, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

@Composable
private fun EmptyAiUseRow() {
    val semanticColors = LocalClearCutColors.current
    Text(
        text = stringResource(R.string.export_ai_use_empty),
        color = semanticColors.subtextStrong,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(semanticColors.panelHighest.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun AiUseChip(chip: Chip, onClick: () -> Unit) {
    val semanticColors = LocalClearCutColors.current
    val accent = severityColor(chip.severity)
    val severityLabel = severityLabel(chip.severity)
    val container = accent.copy(alpha = 0.16f)
    val border = accent.copy(alpha = 0.55f)
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.lg))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(Radius.lg))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = severityLabel,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "  ${chip.effectKindLabel}  ·  ${chip.describe()}",
            color = semanticColors.text,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun severityLabel(s: Severity): String = stringResource(
    when (s) {
        Severity.DISCLOSURE_REQUIRED -> R.string.export_ai_use_severity_required
        Severity.DISCLOSURE_RECOMMENDED -> R.string.export_ai_use_severity_recommended
        Severity.INTERNAL_ONLY -> R.string.export_ai_use_severity_internal
    }
)

private fun severityColor(s: Severity): Color = when (s) {
    // Severity rank: REQUIRED is the strongest disclosure ask. Use Peach so
    // it reads as "attention" without escalating to error-red, which would
    // wrongly imply the export will fail.
    Severity.DISCLOSURE_REQUIRED -> ClearCutAccents.Peach
    // Recommended is informational; Sky reads as a friendly heads-up.
    Severity.DISCLOSURE_RECOMMENDED -> ClearCutAccents.Sky
    // Internal-only is the lowest signal; muted Green matches "everything
    // here ran locally and needs no disclosure."
    Severity.INTERNAL_ONLY -> ClearCutAccents.Green
}

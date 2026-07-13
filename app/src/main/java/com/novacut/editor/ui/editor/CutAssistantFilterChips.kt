package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.CutAssistantEngine
import com.novacut.editor.engine.SilenceDetectionEngine.CutProposal
import com.novacut.editor.engine.SilenceDetectionEngine.ProposalCategory
import com.novacut.editor.ui.theme.Radius

/**
 * Classify a Cut Assistant [CutAssistantEngine.ReviewProposal] into a filter
 * bucket. Mirrors [SilenceDetectionEngine.categorize] (which operates on the
 * lower-level per-clip `CutProposal`) but works on the timeline-level review
 * proposals the panel actually renders — both carry `reason` + `matchedText`,
 * which is all the classification needs. Single source of truth for the chip
 * row and the panel's visible-proposal filter.
 */
internal fun reviewProposalCategory(
    proposal: CutAssistantEngine.ReviewProposal
): ProposalCategory = when (proposal.reason) {
    CutProposal.Reason.SILENCE -> ProposalCategory.SILENCE
    CutProposal.Reason.FILLER_WORD -> {
        val matched = proposal.matchedText?.trim().orEmpty()
        when {
            matched.isEmpty() -> ProposalCategory.OTHER
            matched.contains(' ') -> ProposalCategory.MULTI_WORD_FILLER
            else -> ProposalCategory.SINGLE_WORD_FILLER
        }
    }
}

/**
 * Filter-chip row for the unified Cut Assistant Review panel
 * (RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #6).
 *
 * Consumes `SilenceDetectionEngine.ProposalCategory` directly. Each chip
 * shows the bucket's label + count; tapping toggles its membership in the
 * `enabled` set. The "All" chip is a convenience toggle that flips between
 * every category enabled and every category disabled.
 *
 * The actual filter application is driven by
 * `SilenceDetectionEngine.filterByCategory(proposals, enabledSet)` — this
 * Composable owns the chip selection state only; the proposal list is
 * filtered by the caller (panel) using the engine helper.
 *
 * The panel owns the [enabled] state — typical wiring:
 *
 *     var enabled by remember { mutableStateOf(ProposalCategory.entries.toSet()) }
 *     CutAssistantFilterChips(
 *         proposals = proposals,
 *         enabled = enabled,
 *         onToggle = { cat ->
 *             enabled = if (cat in enabled) enabled - cat else enabled + cat
 *         },
 *         onToggleAll = {
 *             enabled = if (enabled.size == ProposalCategory.entries.size) emptySet()
 *                       else ProposalCategory.entries.toSet()
 *         },
 *     )
 *     val visible = SilenceDetectionEngine().filterByCategory(proposals, enabled)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CutAssistantFilterChips(
    proposals: List<CutAssistantEngine.ReviewProposal>,
    enabled: Set<ProposalCategory>,
    onToggle: (ProposalCategory) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Per-category count drives the chip label; precomputed once per recomposition.
    val countsByCategory = remember(proposals) {
        proposals.groupingBy { reviewProposalCategory(it) }.eachCount()
    }
    val total = proposals.size
    val allOn = enabled.size == ProposalCategory.entries.size

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            label = stringResource(R.string.cut_filter_count_format, stringResource(R.string.cut_filter_all), total),
            selected = allOn,
            onClick = onToggleAll,
        )
        for (category in ProposalCategory.entries) {
            val count = countsByCategory[category] ?: 0
            if (count == 0) continue
            FilterChip(
                label = stringResource(
                    R.string.cut_filter_count_format,
                    labelFor(category),
                    count
                ),
                selected = category in enabled,
                onClick = { onToggle(category) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semanticColors = LocalClearCutColors.current
    val container = if (selected) ClearCutAccents.Mauve.copy(alpha = 0.18f) else semanticColors.panelHighest.copy(alpha = 0.5f)
    val border = if (selected) ClearCutAccents.Mauve.copy(alpha = 0.62f) else semanticColors.cardStrokeStrong.copy(alpha = 0.5f)
    val textColor = if (selected) semanticColors.text else semanticColors.subtext
    Text(
        text = label,
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
private fun labelFor(category: ProposalCategory): String = stringResource(
    when (category) {
        ProposalCategory.SILENCE -> R.string.cut_filter_silence
        ProposalCategory.SINGLE_WORD_FILLER -> R.string.cut_filter_single_filler
        ProposalCategory.MULTI_WORD_FILLER -> R.string.cut_filter_multi_filler
        ProposalCategory.OTHER -> R.string.cut_filter_other
    }
)

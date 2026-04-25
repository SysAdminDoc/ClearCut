package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.CutAssistantEngine
import com.novacut.editor.engine.SilenceDetectionEngine.CutProposal
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CutAssistantReviewPanel(
    review: CutAssistantEngine.ReviewSet,
    tracks: List<Track>,
    onToggleProposal: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipLookup = remember(tracks) {
        tracks.flatMap { it.clips }.associateBy { it.id }
    }
    val acceptedCount = review.acceptedProposals.size
    val proposalCount = review.proposals.size
    val silenceCount = review.proposals.count { it.reason == CutProposal.Reason.SILENCE }
    val fillerCount = review.proposals.count { it.reason == CutProposal.Reason.FILLER_WORD }
    val reclaimLabel = formatCutAssistantDuration(review.totalReclaimMs)

    PremiumEditorPanel(
        title = stringResource(R.string.cut_assistant_title),
        subtitle = stringResource(R.string.cut_assistant_subtitle),
        icon = Icons.Default.ContentCut,
        accent = Mocha.Peach,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cut_assistant_close_cd),
        modifier = modifier,
        scrollable = false
    ) {
        PremiumPanelCard(accent = Mocha.Peach) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cut_assistant_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.cut_assistant_overview_body),
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
                        text = pluralStringResource(
                            R.plurals.cut_assistant_selected_count,
                            acceptedCount,
                            acceptedCount
                        ),
                        accent = if (acceptedCount > 0) Mocha.Green else Mocha.Overlay1
                    )
                    PremiumPanelPill(
                        text = stringResource(R.string.cut_assistant_reclaim_pill, reclaimLabel),
                        accent = Mocha.Peach
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = pluralStringResource(
                        R.plurals.cut_assistant_candidate_count,
                        proposalCount,
                        proposalCount
                    ),
                    accent = Mocha.Blue
                )
                PremiumPanelPill(
                    text = stringResource(R.string.cut_assistant_silence_count, silenceCount),
                    accent = Mocha.Mauve
                )
                PremiumPanelPill(
                    text = stringResource(R.string.cut_assistant_filler_count, fillerCount),
                    accent = Mocha.Teal
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (review.proposals.isEmpty()) {
            CutAssistantEmptyState(onClose = onClose)
        } else {
            PremiumPanelCard(accent = Mocha.Blue) {
                Text(
                    text = stringResource(R.string.cut_assistant_review_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.cut_assistant_review_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = review.proposals,
                        key = { it.id }
                    ) { proposal ->
                        CutProposalReviewCard(
                            proposal = proposal,
                            clip = clipLookup[proposal.clipId],
                            isAccepted = proposal.id in review.accepted,
                            onToggle = { onToggleProposal(proposal.id) }
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NovaCutSecondaryButton(
                        text = stringResource(R.string.cut_assistant_reject_all),
                        onClick = onRejectAll,
                        icon = Icons.Default.Close,
                        modifier = Modifier.widthIn(min = 128.dp)
                    )
                    NovaCutSecondaryButton(
                        text = stringResource(R.string.cut_assistant_accept_all),
                        onClick = onAcceptAll,
                        icon = Icons.Default.Check,
                        modifier = Modifier.widthIn(min = 128.dp)
                    )
                    NovaCutPrimaryButton(
                        text = stringResource(R.string.cut_assistant_apply_selected, acceptedCount),
                        onClick = onApply,
                        icon = Icons.Default.ContentCut,
                        enabled = acceptedCount > 0,
                        modifier = Modifier.widthIn(min = 164.dp)
                    )
                }

                if (acceptedCount == 0) {
                    Text(
                        text = stringResource(R.string.cut_assistant_none_selected_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mocha.Subtext0
                    )
                }
            }
        }
    }
}

@Composable
private fun CutAssistantEmptyState(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumPanelCard(accent = Mocha.Green, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = Mocha.Green.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Mocha.Green.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Mocha.Green,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.cut_assistant_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Mocha.Text
                )
                Text(
                    text = stringResource(R.string.cut_assistant_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
        NovaCutSecondaryButton(
            text = stringResource(R.string.done),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CutProposalReviewCard(
    proposal: CutAssistantEngine.ReviewProposal,
    clip: Clip?,
    isAccepted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (proposal.reason) {
        CutProposal.Reason.SILENCE -> Mocha.Mauve
        CutProposal.Reason.FILLER_WORD -> Mocha.Teal
    }
    val reasonLabel = when (proposal.reason) {
        CutProposal.Reason.SILENCE -> stringResource(R.string.cut_assistant_reason_silence)
        CutProposal.Reason.FILLER_WORD -> stringResource(R.string.cut_assistant_reason_filler)
    }
    val fallbackDetail = when (proposal.reason) {
        CutProposal.Reason.SILENCE -> stringResource(R.string.cut_assistant_detail_silence)
        CutProposal.Reason.FILLER_WORD -> stringResource(R.string.cut_assistant_detail_filler)
    }
    val detail = proposal.matchedText
        ?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.cut_assistant_matched_text, it) }
        ?: fallbackDetail
    val clipName = clip?.displayName() ?: stringResource(R.string.cut_assistant_unknown_clip)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle),
        color = if (isAccepted) Mocha.PanelHighest else Mocha.PanelRaised.copy(alpha = 0.78f),
        shape = RoundedCornerShape(Radius.xl),
        border = BorderStroke(
            1.dp,
            if (isAccepted) accent.copy(alpha = 0.42f) else Mocha.CardStroke
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = if (isAccepted) 0.12f else 0.05f),
                        Mocha.PanelHighest.copy(alpha = 0.98f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAccepted,
                    onCheckedChange = { onToggle() }
                )
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = if (proposal.reason == CutProposal.Reason.SILENCE) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.GraphicEq
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = reasonLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(
                                R.string.cut_assistant_duration_saved,
                                formatCutAssistantDuration(proposal.durationMs)
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumPanelPill(
                            text = stringResource(R.string.cut_assistant_clip_pill, clipName),
                            accent = Mocha.Blue
                        )
                        PremiumPanelPill(
                            text = stringResource(
                                R.string.cut_assistant_time_range,
                                formatCutAssistantTimestamp(proposal.timelineStartMs),
                                formatCutAssistantTimestamp(proposal.timelineEndMs)
                            ),
                            accent = accent
                        )
                    }
                }
            }
        }
    }
}

private fun Clip.displayName(): String {
    return sourceUri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: id.take(8)
}

private fun formatCutAssistantTimestamp(ms: Long): String {
    val clampedMs = ms.coerceAtLeast(0L)
    val totalSeconds = clampedMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (clampedMs % 1000L) / 100L
    return if (minutes > 0L) {
        String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
    } else {
        String.format(Locale.US, "0:%02d.%d", seconds, tenths)
    }
}

private fun formatCutAssistantDuration(ms: Long): String {
    val clampedMs = ms.coerceAtLeast(0L)
    return if (clampedMs < 60_000L) {
        String.format(Locale.US, "%.1fs", clampedMs / 1000f)
    } else {
        val totalSeconds = clampedMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        String.format(Locale.US, "%dm %02ds", minutes, seconds)
    }
}

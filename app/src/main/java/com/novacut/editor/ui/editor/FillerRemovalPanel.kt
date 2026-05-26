package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

/**
 * Legacy filler-removal panel. **Pending deletion.**
 *
 * Highest-Value #6 / 2026-05-25 Loop 3 promoted `CutAssistantReviewPanel`
 * to the single review surface; its chip-row filter (Silence /
 * Single-word filler / Multi-word filler) supersedes the standalone
 * Analyze + Apply flow this panel exposes. The "filler_removal" tool
 * action in EditorScreen now opens Cut Assistant Review instead.
 *
 * Kept in tree until the corresponding `PanelId.FILLER_REMOVAL`
 * BottomSheetSlot is removed; that follow-up also drops
 * `viewModel.showFillerRemoval` / `hideFillerRemoval` /
 * `analyzeFillers` / `applyFillerRemoval` / `state.fillerRegions` /
 * `state.isAnalyzingFillers` from `EditorViewModel`.
 */
@Deprecated(
    "Use CutAssistantReviewPanel — see RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #6.",
    ReplaceWith("CutAssistantReviewPanel(/* review = … */)"),
    DeprecationLevel.WARNING,
)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FillerRemovalPanel(
    regionCount: Int,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val regionsReadyLabel = if (regionCount > 0) {
        pluralStringResource(
            R.plurals.filler_removal_ready_count,
            regionCount,
            regionCount
        )
    } else {
        stringResource(R.string.filler_removal_status_waiting)
    }
    val statusLabel = when {
        isAnalyzing -> stringResource(R.string.filler_removal_status_analyzing)
        regionCount > 0 -> stringResource(R.string.filler_removal_status_ready)
        else -> stringResource(R.string.filler_removal_status_waiting)
    }
    val messageState = when {
        isAnalyzing -> FillerRemovalMessageState(
            title = stringResource(R.string.filler_removal_analyzing_title),
            body = stringResource(R.string.filler_removal_analyzing_body),
            accent = Mocha.Peach,
            icon = Icons.Default.Search
        )
        regionCount > 0 -> FillerRemovalMessageState(
            title = stringResource(R.string.filler_removal_apply_title),
            body = stringResource(R.string.filler_removal_apply_body),
            accent = Mocha.Green,
            icon = Icons.Default.ContentCut
        )
        else -> FillerRemovalMessageState(
            title = stringResource(R.string.filler_removal_waiting_title),
            body = stringResource(R.string.filler_removal_waiting_body),
            accent = Mocha.Blue,
            icon = Icons.Default.Check
        )
    }

    PremiumEditorPanel(
        title = stringResource(R.string.filler_removal_title),
        subtitle = stringResource(R.string.filler_removal_subtitle),
        icon = Icons.Default.ContentCut,
        accent = Mocha.Blue,
        onClose = onClose,
        modifier = modifier,
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
                        text = stringResource(R.string.filler_removal_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.filler_removal_description),
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
                        text = regionsReadyLabel,
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = statusLabel,
                        accent = when {
                            isAnalyzing -> Mocha.Peach
                            regionCount > 0 -> Mocha.Green
                            else -> Mocha.Overlay0
                        }
                    )
                }
            }

            FillerRemovalMessageCard(
                title = messageState.title,
                body = messageState.body,
                accent = messageState.accent,
                icon = messageState.icon
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Peach) {
            Text(
                text = stringResource(R.string.filler_removal_scope_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.filler_removal_scope_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            FillerRemovalMessageCard(
                title = stringResource(R.string.filler_removal_detection_title),
                body = stringResource(R.string.filler_removal_detection_body),
                accent = Mocha.Peach,
                icon = Icons.Default.Search
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.filler_removal_filler_words),
                    accent = Mocha.Blue
                )
                PremiumPanelPill(
                    text = stringResource(R.string.filler_removal_silences),
                    accent = Mocha.Mauve
                )
                PremiumPanelPill(
                    text = stringResource(R.string.filler_removal_detection_safe),
                    accent = Mocha.Green
                )
            }

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Blue,
                    contentColor = Mocha.Base,
                    disabledContainerColor = Mocha.Blue.copy(alpha = 0.45f),
                    disabledContentColor = Mocha.Base.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Mocha.Base,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.filler_removal_analyzing))
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_filler_analyze)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.filler_removal_analyze_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (regionCount > 0) Mocha.Green else Mocha.Overlay1) {
            if (regionCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.panel_filler_removal_found, regionCount),
                                style = MaterialTheme.typography.titleMedium,
                                color = Mocha.Text
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.panel_filler_removal_found_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mocha.Subtext0
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        PremiumPanelPill(
                            text = stringResource(R.string.filler_removal_ready_badge),
                            accent = Mocha.Green
                        )
                    }

                    Button(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mocha.Mauve,
                            contentColor = Mocha.Base
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = stringResource(R.string.cd_filler_remove_all)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.panel_filler_removal_remove_all, regionCount))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FillerRemovalMessageCard(
                        title = stringResource(R.string.filler_removal_empty_title),
                        body = stringResource(R.string.filler_removal_empty_body),
                        accent = Mocha.Overlay1,
                        icon = Icons.Default.Check
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 430.dp
                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onAnalyze,
                                    enabled = !isAnalyzing,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(text = stringResource(R.string.filler_removal_empty_cta))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onAnalyze,
                                    enabled = !isAnalyzing,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(text = stringResource(R.string.filler_removal_empty_cta))
                                }
                                Text(
                                    text = stringResource(R.string.filler_removal_disabled_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Mocha.Subtext0,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FillerRemovalMessageState(
    val title: String,
    val body: String,
    val accent: Color,
    val icon: ImageVector
)

@Composable
private fun FillerRemovalMessageCard(
    title: String,
    body: String,
    accent: Color,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
        }
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun FillerRemovalPanel(
    regionCount: Int,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.filler_removal_title),
        subtitle = "Spot filler words and dead air, then clean the cut in one pass before you polish pacing by hand.",
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
                        text = "Cleanup pass",
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
                        text = if (regionCount > 0) "$regionCount cuts ready" else "Awaiting scan",
                        accent = Mocha.Blue
                    )
                    PremiumPanelPill(
                        text = if (isAnalyzing) "Analyzing audio" else "Speech cleanup",
                        accent = if (isAnalyzing) Mocha.Peach else Mocha.Green
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Peach) {
            Text(
                text = "Analyze this clip",
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = "NovaCut looks for filler speech and silent gaps so you can remove the soft spots before the final trim pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

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
                        text = "Ready to cut",
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
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.cd_filler_regions_found),
                            tint = Mocha.Overlay1
                        )
                        Text(
                            text = "No cleanup regions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Mocha.Text
                        )
                    }
                    Text(
                        text = "Run analysis first. If the clip is already tight, NovaCut will leave the pacing untouched.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                    OutlinedButton(
                        onClick = onAnalyze,
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(text = "Run scan")
                    }
                }
            }
        }
    }
}

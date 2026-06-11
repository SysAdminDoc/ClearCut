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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun NoiseReductionPanel(
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier,
    analysisResult: String? = null,
    onAnalyze: () -> Unit,
    onClose: () -> Unit
) {
    PremiumEditorPanel(
        title = stringResource(R.string.noise_reduction_title),
        subtitle = stringResource(R.string.noise_reduction_subtitle),
        icon = Icons.Default.GraphicEq,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.noise_reduction_profile_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.noise_reduction_description),
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
                        text = if (analysisResult != null) stringResource(R.string.noise_reduction_profile_ready) else stringResource(R.string.noise_reduction_awaiting_scan),
                        accent = Mocha.Mauve
                    )
                    PremiumPanelPill(
                        text = if (isAnalyzing) stringResource(R.string.noise_reduction_cleaning_now) else stringResource(R.string.noise_reduction_ai_assist),
                        accent = if (isAnalyzing) Mocha.Peach else Mocha.Blue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Blue) {
            Text(
                text = stringResource(R.string.noise_reduction_analyze_title),
                style = MaterialTheme.typography.titleMedium,
                color = Mocha.Text
            )
            Text(
                text = stringResource(R.string.noise_reduction_analyze_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(text = stringResource(R.string.noise_reduction_type_hiss), accent = Mocha.Blue)
                PremiumPanelPill(text = stringResource(R.string.noise_reduction_type_hum), accent = Mocha.Mauve)
                PremiumPanelPill(text = stringResource(R.string.noise_reduction_type_broadband), accent = Mocha.Peach)
            }

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Base,
                    disabledContainerColor = Mocha.Mauve.copy(alpha = 0.45f),
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
                    Text(text = stringResource(R.string.noise_reduction_analyzing))
                } else {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.cd_noise_analyze)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.noise_reduction_analyze_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = if (analysisResult != null) Mocha.Green else Mocha.Overlay1) {
            if (analysisResult != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.done),
                        tint = Mocha.Green
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.noise_reduction_cleanup_applied),
                            style = MaterialTheme.typography.titleMedium,
                            color = Mocha.Text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analysisResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Subtext0
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.noise_reduction_no_result_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Text(
                        text = stringResource(R.string.noise_reduction_no_result_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }
            }
        }
    }
}

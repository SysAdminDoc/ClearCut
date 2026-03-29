package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.ui.theme.Mocha

@Composable
fun RenderPreviewSheet(
    segments: List<SmartRenderEngine.RenderSegment>,
    summary: SmartRenderEngine.SmartRenderSummary,
    onRenderPreview: () -> Unit,
    onRenderFull: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.render_preview_title), color = Mocha.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.render_preview_close_cd), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Smart render summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryChip(
                label = stringResource(R.string.panel_render_pass_through),
                value = "${summary.passThroughSegments}",
                color = Mocha.Green
            )
            SummaryChip(
                label = stringResource(R.string.panel_render_re_encode),
                value = "${summary.reEncodeSegments}",
                color = if (summary.reEncodeSegments > 0) Mocha.Yellow else Mocha.Green
            )
            SummaryChip(
                label = stringResource(R.string.panel_render_speedup),
                value = if (summary.estimatedSpeedup < 100f) "%.1fx".format(summary.estimatedSpeedup) else "Max",
                color = Mocha.Mauve
            )
        }

        Spacer(Modifier.height(4.dp))

        // Duration breakdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.render_re_encode_duration, formatMs(summary.reEncodeDurationMs)),
                color = Mocha.Yellow,
                fontSize = 10.sp
            )
            Text(
                stringResource(R.string.render_pass_through_duration, formatMs(summary.passThroughDurationMs)),
                color = Mocha.Green,
                fontSize = 10.sp
            )
        }

        // Progress bar showing re-encode vs pass-through ratio
        if (summary.totalDurationMs > 0) {
            Spacer(Modifier.height(4.dp))
            val reEncodeRatio = summary.reEncodeDurationMs.toFloat() / summary.totalDurationMs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Mocha.Green.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(reEncodeRatio)
                        .background(Mocha.Yellow.copy(alpha = 0.6f))
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Segment list
        Text(stringResource(R.string.panel_render_segments), color = Mocha.Subtext0, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(segments) { segment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Mocha.Surface0)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (segment.needsReEncode) Mocha.Yellow else Mocha.Green,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            "${formatMs(segment.startMs)} - ${formatMs(segment.endMs)}",
                            color = Mocha.Subtext0,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        segment.reason,
                        color = if (segment.needsReEncode) Mocha.Yellow else Mocha.Green,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick preview (low-res)
            OutlinedButton(
                onClick = onRenderPreview,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Mocha.Peach.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Preview, null, tint = Mocha.Peach, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_render_preview), color = Mocha.Peach, fontSize = 12.sp)
            }

            // Full quality export
            Button(
                onClick = onRenderFull,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_render_export), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Mocha.Subtext0, fontSize = 9.sp)
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return if (m > 0) "%d:%02d".format(m, s % 60) else "${s}s"
}

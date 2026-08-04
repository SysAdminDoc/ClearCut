package com.novacut.editor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.PrivacyDashboard
import com.novacut.editor.engine.PrivacyDashboard.DashboardEntry
import com.novacut.editor.engine.PrivacyDashboard.Section
import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Radius

/**
 * Settings → Privacy panel (R5.5c UI / RESEARCH_FEATURE_PLAN_2026-05-25
 * Highest-Value #8).
 *
 * Consumes `PrivacyDashboard.groupForDisplay()` so the displayed list
 * automatically tracks engine reality — the panel never hand-codes which
 * categories show. Risk-ordered: cloud + telemetry first, then on-device
 * collected by default, then on-device opt-in.
 *
 * A row advertises Export / Delete / Opt out, so it must not present an
 * affordance that does nothing. [actionsFor] lets the host supply the actions
 * it can genuinely perform from here; any row the host cannot act on renders
 * non-interactive and states where its control actually lives
 * ([DashboardEntry.controlLocation]) instead.
 */
@Composable
fun PrivacyDashboardPanel(
    modifier: Modifier = Modifier,
    actionsFor: (DashboardEntry) -> List<PrivacyDashboardAction> = { emptyList() },
) {
    val grouped = PrivacyDashboard.groupForDisplay()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header()
        for ((section, entries) in grouped) {
            SectionHeader(section)
            for (entry in entries) {
                EntryCard(entry = entry, actions = actionsFor(entry))
            }
        }
    }
}

/** An action the dashboard can actually run for a row. */
data class PrivacyDashboardAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun Header() {
    Column {
        Text(
            text = stringResource(R.string.privacy_dashboard_title),
            color = LocalClearCutColors.current.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.privacy_dashboard_subtitle),
            color = LocalClearCutColors.current.subtext,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionHeader(section: Section) {
    val (icon, accent, labelRes) = when (section) {
        Section.CLOUD_AND_TELEMETRY -> Triple(
            Icons.Default.Cloud,
            ClearCutAccents.Peach,
            R.string.privacy_dashboard_section_cloud,
        )
        Section.ON_DEVICE_COLLECTED -> Triple(
            Icons.Default.Computer,
            ClearCutAccents.Sky,
            R.string.privacy_dashboard_section_on_device,
        )
        Section.ON_DEVICE_OPT_IN -> Triple(
            Icons.Default.LockOpen,
            ClearCutAccents.Green,
            R.string.privacy_dashboard_section_opt_in,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(20.dp)
                .padding(end = 6.dp),
        )
        Text(
            text = stringResource(labelRes),
            color = LocalClearCutColors.current.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EntryCard(entry: DashboardEntry, actions: List<PrivacyDashboardAction>) {
    val sectionAccent = sectionAccent(PrivacyDashboard.sectionFor(entry))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(BorderStroke(1.dp, LocalClearCutColors.current.cardStrokeStrong.copy(alpha = 0.55f)), RoundedCornerShape(Radius.lg))
            .background(LocalClearCutColors.current.panelHighest.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = entry.category.displayName,
            color = LocalClearCutColors.current.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        MetaLine(
            label = stringResource(R.string.privacy_dashboard_storage_label),
            value = entry.location.displayName,
            accent = sectionAccent,
        )
        MetaLine(
            label = stringResource(R.string.privacy_dashboard_retention_label),
            value = entry.retentionPolicy,
        )
        MetaLine(
            label = stringResource(R.string.privacy_dashboard_collected_by_label),
            value = entry.collectedBy.joinToString(", "),
        )
        MetaLine(
            label = stringResource(R.string.privacy_dashboard_controls_label),
            value = PrivacyDashboard.controlSummary(entry),
            accent = ClearCutAccents.Mauve,
        )
        if (actions.isEmpty()) {
            // No affordance is rendered here, so say where the control is.
            MetaLine(
                label = stringResource(R.string.privacy_dashboard_managed_in_label),
                value = entry.controlLocation,
                accent = ClearCutAccents.Peach,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { action ->
                    Text(
                        text = action.label,
                        color = ClearCutAccents.Sky,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.md))
                            .clickable(onClick = action.onClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String, accent: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label · ",
            color = if (accent == Color.Unspecified) LocalClearCutColors.current.subtextStrong else accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = LocalClearCutColors.current.subtext,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun sectionAccent(section: Section): Color = when (section) {
    Section.CLOUD_AND_TELEMETRY -> ClearCutAccents.Peach
    Section.ON_DEVICE_COLLECTED -> ClearCutAccents.Sky
    Section.ON_DEVICE_OPT_IN -> ClearCutAccents.Green
}

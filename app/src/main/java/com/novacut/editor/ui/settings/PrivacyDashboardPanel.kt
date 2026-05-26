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
import com.novacut.editor.ui.theme.Mocha
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
 * Tap-to-act callbacks let the host wire in the actions the underlying
 * controls allow (`canExport` / `canDelete` / `hasOptOut`). The composable
 * itself is read-only; the host owns the wiring to the existing
 * `ModelDownloadManager` / `ProjectAutoSave.clearRecoveryData` / etc.
 */
@Composable
fun PrivacyDashboardPanel(
    modifier: Modifier = Modifier,
    onEntryClicked: (DashboardEntry) -> Unit = {},
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
                EntryCard(entry = entry, onClick = { onEntryClicked(entry) })
            }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            text = stringResource(R.string.privacy_dashboard_title),
            color = Mocha.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.privacy_dashboard_subtitle),
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionHeader(section: Section) {
    val (icon, accent, labelRes) = when (section) {
        Section.CLOUD_AND_TELEMETRY -> Triple(
            Icons.Default.Cloud,
            Mocha.Peach,
            R.string.privacy_dashboard_section_cloud,
        )
        Section.ON_DEVICE_COLLECTED -> Triple(
            Icons.Default.Computer,
            Mocha.Sky,
            R.string.privacy_dashboard_section_on_device,
        )
        Section.ON_DEVICE_OPT_IN -> Triple(
            Icons.Default.LockOpen,
            Mocha.Green,
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
            color = Mocha.Text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EntryCard(entry: DashboardEntry, onClick: () -> Unit) {
    val sectionAccent = sectionAccent(PrivacyDashboard.sectionFor(entry))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.55f)), RoundedCornerShape(Radius.lg))
            .background(Mocha.PanelHighest.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = entry.category.displayName,
            color = Mocha.Text,
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
            accent = Mocha.Mauve,
        )
    }
}

@Composable
private fun MetaLine(label: String, value: String, accent: Color = Mocha.Subtext1) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label · ",
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun sectionAccent(section: Section): Color = when (section) {
    Section.CLOUD_AND_TELEMETRY -> Mocha.Peach
    Section.ON_DEVICE_COLLECTED -> Mocha.Sky
    Section.ON_DEVICE_OPT_IN -> Mocha.Green
}

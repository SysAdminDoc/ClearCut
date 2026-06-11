package com.novacut.editor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.OpenSourceLicenseNotice
import com.novacut.editor.engine.OpenSourceLicenses
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Radius

@Composable
fun OpenSourceLicensesPanel(
    modifier: Modifier = Modifier,
    notices: List<OpenSourceLicenseNotice> = OpenSourceLicenses.noticesForDisplay(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(count = notices.size)
        notices.forEach { notice ->
            NoticeCard(notice = notice)
        }
    }
}

@Composable
private fun Header(count: Int) {
    Column {
        Text(
            text = stringResource(R.string.opensource_licenses_title),
            color = Mocha.Text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.opensource_licenses_subtitle, count),
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NoticeCard(notice: OpenSourceLicenseNotice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.55f)), RoundedCornerShape(Radius.lg))
            .background(Mocha.PanelHighest.copy(alpha = 0.55f), RoundedCornerShape(Radius.lg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "${notice.name} ${notice.version}",
            color = Mocha.Text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        MetaLine(
            icon = Icons.Default.Code,
            label = stringResource(R.string.opensource_licenses_artifact_label),
            value = notice.artifact,
            accent = Mocha.Sapphire,
        )
        MetaLine(
            icon = Icons.Default.Description,
            label = stringResource(R.string.opensource_licenses_license_label),
            value = notice.licenseName,
            accent = Mocha.Mauve,
        )
        Text(
            text = notice.licenseText,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodySmall,
        )
        notice.complianceNote?.let {
            MetaBlock(
                label = stringResource(R.string.opensource_licenses_notice_label),
                value = it,
                accent = Mocha.Peach,
            )
        }
        notice.sourceOfferText?.let {
            MetaBlock(
                label = stringResource(R.string.opensource_licenses_source_offer_label),
                value = it,
                accent = Mocha.Green,
            )
        }
        MetaLine(
            icon = Icons.Default.Link,
            label = stringResource(R.string.opensource_licenses_project_label),
            value = notice.projectUrl,
            accent = Mocha.Teal,
        )
        MetaLine(
            icon = Icons.Default.Description,
            label = stringResource(R.string.opensource_licenses_full_text_label),
            value = notice.licenseUrl,
            accent = Mocha.Sky,
        )
    }
}

@Composable
private fun MetaLine(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
        )
        Column {
            Text(
                text = label,
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
}

@Composable
private fun MetaBlock(
    label: String,
    value: String,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

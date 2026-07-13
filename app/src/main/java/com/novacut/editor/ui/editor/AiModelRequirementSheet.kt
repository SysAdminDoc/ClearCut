package com.novacut.editor.ui.editor

import com.novacut.editor.ui.theme.ClearCutAccents
import com.novacut.editor.ui.theme.LocalClearCutColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.AiToolRequirements
import com.novacut.editor.engine.AiToolRequirements.Availability
import com.novacut.editor.engine.AiToolRequirements.Runtime
import com.novacut.editor.engine.AiToolRequirements.ToolRequirement
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius

/**
 * Pre-flight sheet shown before an AI tool runs (Highest-Value #10).
 *
 * Consumes [AiToolRequirements.ToolRequirement] directly so the data
 * contract stays frozen and tested under [com.novacut.editor.engine.AiToolRequirementsTest].
 *
 * Three primary CTAs depending on [ToolRequirement.availability]:
 *  - READY → "Use now" (no download, no cloud)
 *  - MODEL_DOWNLOAD_REQUIRED → "Download model"
 *  - CLOUD_OPT_IN → "Send and run" (gated on the consent checkbox)
 *  - DEPENDENCY_MISSING → dismiss-only, "Review AI models" secondary
 *
 * `requiresOptInConsent` forces a checkbox that gates the primary CTA;
 * even on-device voice cloning trips this because it enrolls a recording
 * of a real person's voice.
 */
@Composable
fun AiModelRequirementSheet(
    requirement: ToolRequirement,
    onDismiss: () -> Unit,
    onDownload: (ToolRequirement) -> Unit,
    onRun: (ToolRequirement) -> Unit,
    onReviewModels: () -> Unit = {},
) {
    val semanticColors = LocalClearCutColors.current
    val title = when (requirement.availability) {
        Availability.READY -> stringResource(R.string.ai_requirement_title_ready)
        Availability.MODEL_DOWNLOAD_REQUIRED -> stringResource(R.string.ai_requirement_title_download)
        Availability.DEPENDENCY_MISSING -> stringResource(R.string.ai_requirement_title_dependency)
        Availability.CLOUD_OPT_IN -> stringResource(R.string.ai_requirement_title_cloud)
    }
    val (icon, accent) = when (requirement.availability) {
        Availability.READY -> Icons.Default.Verified to ClearCutAccents.Green
        Availability.MODEL_DOWNLOAD_REQUIRED -> Icons.Default.Download to ClearCutAccents.Sky
        Availability.DEPENDENCY_MISSING -> Icons.Default.CloudOff to ClearCutAccents.Peach
        Availability.CLOUD_OPT_IN -> Icons.Default.Cloud to ClearCutAccents.Yellow
    }

    var consentChecked by remember(requirement) { mutableStateOf(false) }
    val needsConsent = requirement.requiresOptInConsent
    val primaryEnabled = !needsConsent || consentChecked

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ClearCutDialogIcon(icon = icon, accent = accent) },
        title = {
            Text(
                text = title,
                color = semanticColors.text,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = body(requirement),
                    color = semanticColors.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                )
                MetaRow(
                    icon = Icons.Default.Verified,
                    label = stringResource(R.string.ai_requirement_license_format, requirement.license),
                )
                if (requirement.estimatedBytes > 0L) {
                    MetaRow(
                        icon = Icons.Default.Download,
                        label = stringResource(R.string.ai_requirement_size_format, requirement.sizeMb),
                    )
                }
                MetaRow(
                    icon = if (requirement.runtimeLocation == Runtime.ON_DEVICE)
                        Icons.Default.Verified
                    else
                        Icons.Default.Cloud,
                    label = stringResource(
                        if (requirement.runtimeLocation == Runtime.ON_DEVICE)
                            R.string.ai_requirement_runtime_on_device
                        else
                            R.string.ai_requirement_runtime_cloud
                    ),
                )
                if (needsConsent) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = ClearCutAccents.Mauve,
                                uncheckedColor = semanticColors.subtext,
                                checkmarkColor = semanticColors.text,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.ai_requirement_consent_label),
                            color = semanticColors.subtextStrong,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (requirement.availability == Availability.DEPENDENCY_MISSING) {
                ClearCutSecondaryButton(
                    text = stringResource(R.string.ai_requirement_review_models),
                    onClick = onReviewModels,
                )
            } else {
                val (label, action) = when (requirement.availability) {
                    Availability.READY ->
                        stringResource(R.string.ai_requirement_use_action) to { onRun(requirement) }
                    Availability.MODEL_DOWNLOAD_REQUIRED ->
                        stringResource(R.string.ai_requirement_download_action) to { onDownload(requirement) }
                    Availability.CLOUD_OPT_IN ->
                        stringResource(R.string.ai_requirement_cloud_action) to { onRun(requirement) }
                    Availability.DEPENDENCY_MISSING -> "" to {} // handled above
                }
                ClearCutPrimaryButton(
                    text = label,
                    onClick = action,
                    enabled = primaryEnabled,
                )
            }
        },
        dismissButton = {
            ClearCutSecondaryButton(
                text = stringResource(R.string.ai_requirement_not_now),
                onClick = onDismiss,
            )
        },
        containerColor = semanticColors.panelHighest,
        titleContentColor = semanticColors.text,
        textContentColor = semanticColors.subtext,
        shape = RoundedCornerShape(Radius.xxl),
    )
}

@Composable
private fun MetaRow(icon: ImageVector, label: String) {
    val semanticColors = LocalClearCutColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = semanticColors.subtext,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = label,
            color = semanticColors.subtext,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun body(req: ToolRequirement): String = when (req.availability) {
    Availability.READY -> stringResource(R.string.ai_requirement_body_ready)
    Availability.MODEL_DOWNLOAD_REQUIRED -> stringResource(
        R.string.ai_requirement_body_download_format,
        req.modelDisplayName,
        req.sizeMb,
    )
    Availability.DEPENDENCY_MISSING -> stringResource(
        R.string.ai_requirement_body_dependency,
        req.modelDisplayName,
    )
    Availability.CLOUD_OPT_IN -> stringResource(
        R.string.ai_requirement_body_cloud_format,
        req.modelDisplayName,
    )
}

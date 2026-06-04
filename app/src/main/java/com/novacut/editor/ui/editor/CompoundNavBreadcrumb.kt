package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.NovaCutTestTags
import com.novacut.editor.ui.theme.Mocha

/**
 * Breadcrumb chip rendered above the timeline whenever the editor is
 * inside a compound clip's sub-timeline (Tier C.13 UI / Highest-Value #5).
 *
 * Hidden when [breadcrumbText] is blank — the timeline reads as the project
 * itself in that case and no chip is required. The ViewModel owns breadcrumb
 * formatting so the UI can consume the same immutable state that drives the
 * predictive-back gate.
 *
 * Tap-anywhere-to-exit semantics: tapping the chip pops one level via
 * [onExit]. The leading back arrow doubles as the affordance hint; the
 * Predictive-back integration lives at the EditorScreen level — gate the
 * existing `BackHandler` on `compoundNavDepth > 0` so the system back gesture
 * also pops one level. This composable owns the visual affordance only.
 */
@Composable
fun CompoundNavBreadcrumb(
    breadcrumbText: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = breadcrumbText.takeIf { it.isNotBlank() } ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(NovaCutTestTags.EDITOR_COMPOUND_BREADCRUMB)
            .clip(RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.55f)), RoundedCornerShape(20.dp))
            .background(Mocha.Mauve.copy(alpha = 0.14f))
            .clickable(onClick = onExit)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.compound_breadcrumb_exit_cd),
            tint = Mocha.Mauve,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            color = Mocha.Text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

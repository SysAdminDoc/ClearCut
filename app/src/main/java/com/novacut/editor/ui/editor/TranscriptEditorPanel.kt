package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.Transcript
import com.novacut.editor.ui.theme.Mocha

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptEditorPanel(
    transcript: Transcript?,
    selectedWordIndices: Set<Int>,
    clipId: String?,
    onWordTapped: (Int) -> Unit,
    onWordSeek: (Long) -> Unit,
    onSelectFillers: () -> Unit,
    onClearSelection: () -> Unit,
    onApplyDeletions: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.v369_text_edit_title),
        subtitle = stringResource(R.string.v369_text_edit_subtitle),
        icon = Icons.AutoMirrored.Filled.Subject,
        accent = Mocha.Blue,
        onClose = onClose,
        closeContentDescription = stringResource(R.string.cd_close_speed_curve),
        modifier = modifier,
        scrollable = false
    ) {
        if (transcript == null || transcript.words.isEmpty()) {
            PremiumPanelCard(accent = Mocha.Sapphire) {
                Text(
                    text = stringResource(R.string.v369_text_edit_need_transcript),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0
                )
            }
            return@PremiumEditorPanel
        }

        PremiumPanelCard(accent = Mocha.Blue) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumPanelPill(
                    text = "${transcript.words.size} words",
                    accent = Mocha.Blue
                )
                if (selectedWordIndices.isNotEmpty()) {
                    PremiumPanelPill(
                        text = "${selectedWordIndices.size} selected",
                        accent = Mocha.Peach
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onSelectFillers,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Mocha.Mauve.copy(alpha = 0.15f),
                        contentColor = Mocha.Mauve
                    )
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.v369_select_fillers))
                }
                if (selectedWordIndices.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onClearSelection,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Mocha.Surface1,
                            contentColor = Mocha.Subtext0
                        )
                    ) {
                        Text(stringResource(R.string.transcript_clear))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PremiumPanelCard(accent = if (selectedWordIndices.isNotEmpty()) Mocha.Peach else Mocha.Blue) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                transcript.words.forEachIndexed { index, word ->
                    val isSelected = index in selectedWordIndices
                    TranscriptWord(
                        text = word.text,
                        isSelected = isSelected,
                        onTap = { onWordTapped(index) },
                        onLongPress = { onWordSeek(word.startMs) }
                    )
                }
            }
        }

        if (selectedWordIndices.isNotEmpty() && clipId != null) {
            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onApplyDeletions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Mocha.Red.copy(alpha = 0.15f),
                    contentColor = Mocha.Red
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.transcript_remove_selected, selectedWordIndices.size))
            }
        }
    }
}

@Composable
private fun TranscriptWord(
    text: String,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        isSelected -> Mocha.Red.copy(alpha = 0.25f)
        else -> Mocha.Surface0
    }
    val textColor = when {
        isSelected -> Mocha.Red
        else -> Mocha.Text
    }

    Text(
        text = text,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onTap)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .semantics {
                role = Role.Checkbox
                selected = isSelected
                contentDescription = if (isSelected) "$text (selected for removal)" else text
            }
    )
}

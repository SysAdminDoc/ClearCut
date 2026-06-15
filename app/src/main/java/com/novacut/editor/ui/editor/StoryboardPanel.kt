package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.model.StoryboardCard
import com.novacut.editor.model.StoryboardCardStatus
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.TouchTarget

@Composable
fun StoryboardPanel(
    cards: List<StoryboardCard>,
    onAddCard: (String) -> Unit,
    onUpdateCard: (String, String?, StoryboardCardStatus?, Long?) -> Unit,
    onRemoveCard: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newShotText by remember { mutableStateOf("") }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var editText by remember(editingCardId) { mutableStateOf("") }

    PremiumEditorPanel(
        title = stringResource(R.string.storyboard_title),
        subtitle = stringResource(R.string.storyboard_subtitle),
        icon = Icons.AutoMirrored.Filled.ViewList,
        accent = Mocha.Teal,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Teal) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newShotText,
                    onValueChange = { newShotText = it },
                    placeholder = { Text(stringResource(R.string.storyboard_new_shot_hint), color = Mocha.Overlay0) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mocha.Teal,
                        unfocusedBorderColor = Mocha.Surface1,
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = newShotText.trim()
                        if (text.isNotEmpty()) {
                            onAddCard(text)
                            newShotText = ""
                        }
                    },
                    enabled = newShotText.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.storyboard_add_shot_cd),
                        tint = if (newShotText.isNotBlank()) Mocha.Teal else Mocha.Overlay0
                    )
                }
            }
        }

        if (cards.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
            ) {
                cards.sortedBy { it.ordinal }.forEach { card ->
                    val isEditing = editingCardId == card.id
                    PremiumPanelCard(accent = statusColor(card.status)) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Mocha.Teal,
                                    unfocusedBorderColor = Mocha.Surface1,
                                    focusedTextColor = Mocha.Text,
                                    unfocusedTextColor = Mocha.Text
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Row {
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        if (editText.isNotBlank()) {
                                            onUpdateCard(card.id, editText.trim(), null, null)
                                        }
                                        editingCardId = null
                                    },
                                    label = { Text(stringResource(R.string.storyboard_save)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Mocha.Teal
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = false,
                                    onClick = { editingCardId = null },
                                    label = { Text(stringResource(R.string.cancel)) }
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        card.shotText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Mocha.Text,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StoryboardCardStatus.entries.forEach { status ->
                                            val selected = card.status == status
                                            Text(
                                                status.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) statusColor(status) else Mocha.Overlay0,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (selected) statusColor(status).copy(alpha = 0.15f)
                                                        else Mocha.Surface0
                                                    )
                                                    .clickable { onUpdateCard(card.id, null, status, null) }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingCardId = card.id
                                        editText = card.shotText
                                    },
                                    modifier = Modifier.size(TouchTarget.minimum)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.storyboard_edit_shot_cd),
                                        tint = Mocha.Subtext0,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveCard(card.id) },
                                    modifier = Modifier.size(TouchTarget.minimum)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.storyboard_delete_shot_cd),
                                        tint = Mocha.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cards.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${cards.size} shot${if (cards.size != 1) "s" else ""} • ${cards.sumOf { it.targetDurationMs / 1000 }}s target",
                style = MaterialTheme.typography.labelSmall,
                color = Mocha.Subtext0
            )
        }
    }
}

private fun statusColor(status: StoryboardCardStatus) = when (status) {
    StoryboardCardStatus.PLANNED -> Mocha.Yellow
    StoryboardCardStatus.FILMED -> Mocha.Blue
    StoryboardCardStatus.EDITED -> Mocha.Green
}

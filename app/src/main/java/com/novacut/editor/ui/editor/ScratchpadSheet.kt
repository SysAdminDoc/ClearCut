package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Scratchpad for per-project free-form notes. Content is auto-saved ~750ms after the
 * last keystroke to avoid hammering Room on every character. Intentionally minimal —
 * no markdown, no voice input, no timestamp anchors (those can be layered on later).
 */
@Composable
fun ScratchpadSheet(
    initialNotes: String,
    projectName: String,
    onNotesChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Key the remember to the project id via initialNotes so opening a different
    // project reloads its notes instead of sticking to the previous project's state.
    var text by remember(initialNotes) { mutableStateOf(initialNotes) }
    val committed = remember(initialNotes) { mutableStateOf(initialNotes) }

    // Debounced persist: flush after 750ms of quiet typing.
    LaunchedEffect(text) {
        if (text == committed.value) return@LaunchedEffect
        delay(750)
        if (text != committed.value) {
            committed.value = text
            onNotesChanged(text)
        }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.scratchpad_title),
        subtitle = projectName.ifBlank { stringResource(R.string.scratchpad_subtitle_default) },
        icon = Icons.AutoMirrored.Filled.Notes,
        accent = Mocha.Yellow,
        onClose = onClose,
        modifier = modifier,
        closeContentDescription = stringResource(R.string.scratchpad_close_content_description)
    ) {
        val isSaved = text == committed.value
        PremiumPanelCard(accent = Mocha.Yellow) {
            Text(
                text = stringResource(R.string.scratchpad_hint),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )

            ScratchpadStatusPill(
                text = if (isSaved) {
                    stringResource(R.string.scratchpad_saved, text.length)
                } else {
                    stringResource(R.string.scratchpad_saving)
                },
                saved = isSaved
            )
        }

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.scratchpad_placeholder),
                    color = Mocha.Subtext0.copy(alpha = 0.7f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                focusedBorderColor = Mocha.Yellow.copy(alpha = 0.88f),
                unfocusedBorderColor = Mocha.CardStroke,
                focusedContainerColor = Mocha.PanelHighest,
                unfocusedContainerColor = Mocha.PanelRaised,
                cursorColor = Mocha.Yellow,
                focusedPlaceholderColor = Mocha.Subtext0,
                unfocusedPlaceholderColor = Mocha.Overlay1
            ),
            shape = RoundedCornerShape(Radius.xl),
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ScratchpadStatusPill(
    text: String,
    saved: Boolean
) {
    val accent = if (saved) Mocha.Green else Mocha.Sapphire
    Surface(
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(Radius.pill),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.1f), Mocha.PanelHighest.copy(alpha = 0.0f))
                    )
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = if (saved) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.height(16.dp)
            )
            Text(
                text = text,
                color = accent,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

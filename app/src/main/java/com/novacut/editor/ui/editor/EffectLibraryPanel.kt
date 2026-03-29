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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun EffectLibraryPanel(
    hasClipSelected: Boolean,
    hasCopiedEffects: Boolean,
    onExportEffects: () -> Unit,
    onImportEffects: () -> Unit,
    onCopyEffects: () -> Unit,
    onPasteEffects: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.effect_library_title), color = Mocha.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.effect_library_close_cd), tint = Mocha.Subtext0)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.panel_effect_library_description),
            color = Mocha.Subtext0,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Copy effects from selected clip
            OutlinedButton(
                onClick = onCopyEffects,
                enabled = hasClipSelected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Mauve),
                border = BorderStroke(1.dp, Mocha.Mauve.copy(alpha = if (hasClipSelected) 0.5f else 0.2f))
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_effect_library_copy), fontSize = 12.sp)
            }

            // Paste effects to selected clip
            OutlinedButton(
                onClick = onPasteEffects,
                enabled = hasClipSelected && hasCopiedEffects,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Green),
                border = BorderStroke(1.dp, Mocha.Green.copy(alpha = if (hasCopiedEffects) 0.5f else 0.2f))
            ) {
                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_effect_library_paste), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Export effects to .ncfx file
            Button(
                onClick = onExportEffects,
                enabled = hasClipSelected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Base
                )
            ) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_effect_library_export), fontSize = 12.sp)
            }

            // Import effects from .ncfx file
            Button(
                onClick = onImportEffects,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Blue,
                    contentColor = Mocha.Base
                )
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.panel_effect_library_import), fontSize = 12.sp)
            }
        }

        if (!hasClipSelected) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.panel_effect_library_select_clip_hint),
                color = Mocha.Subtext0.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

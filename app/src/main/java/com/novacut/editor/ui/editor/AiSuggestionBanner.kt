package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

@Composable
fun AiSuggestionBanner(
    suggestion: AiSuggestion?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = suggestion != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        suggestion?.let { s ->
            Surface(
                color = Mocha.Panel,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Mocha.Mauve.copy(alpha = 0.12f),
                                    Mocha.PanelHighest.copy(alpha = 0.8f),
                                    Mocha.Panel
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Surface(
                        color = Mocha.Mauve.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_ai_suggestion),
                            tint = Mocha.Rosewater,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = s.message,
                        color = Mocha.Subtext1,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = { onApply(s.actionId) },
                        color = Mocha.Rosewater,
                        contentColor = Mocha.Midnight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_apply),
                            color = Mocha.Midnight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_suggestion),
                        tint = Mocha.Subtext0,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDismiss() }
                    )
                }
            }
        }
    }
}

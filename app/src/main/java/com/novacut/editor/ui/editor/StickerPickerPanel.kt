package com.novacut.editor.ui.editor

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

private enum class StickerCategory(val label: String) {
    EMOJI("Emoji"),
    SHAPES("Shapes"),
    ARROWS("Arrows"),
    SOCIAL("Social"),
    CUSTOM("Custom")
}

private data class StickerItem(
    val id: String,
    val display: String,
    val category: StickerCategory,
    val contentUri: Uri
)

private val bundledStickers: List<StickerItem> = buildList {
    val emoji = listOf(
        "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D",
        "\uD83E\uDD29", "\uD83D\uDE0E", "\uD83E\uDD14",
        "\uD83D\uDE31", "\uD83D\uDE4C", "\uD83D\uDD25",
        "\u2B50", "\uD83C\uDF89", "\u2764\uFE0F"
    )
    emoji.forEachIndexed { index, glyph ->
        add(
            StickerItem(
                id = "emoji_$index",
                display = glyph,
                category = StickerCategory.EMOJI,
                contentUri = Uri.parse("content://com.novacut.editor.stickers/emoji/$index")
            )
        )
    }

    val shapes = listOf(
        "\u25CF", "\u25A0", "\u25B2",
        "\u25C6", "\u2B1B", "\u2B1C",
        "\u25CB", "\u25A1", "\u25BD",
        "\u2B55", "\u26AB", "\u26AA"
    )
    shapes.forEachIndexed { index, glyph ->
        add(
            StickerItem(
                id = "shape_$index",
                display = glyph,
                category = StickerCategory.SHAPES,
                contentUri = Uri.parse("content://com.novacut.editor.stickers/shapes/$index")
            )
        )
    }

    val arrows = listOf(
        "\u2B06\uFE0F", "\u2B07\uFE0F", "\u27A1\uFE0F",
        "\u2B05\uFE0F", "\u2197\uFE0F", "\u2198\uFE0F",
        "\u2196\uFE0F", "\u2199\uFE0F", "\u21BB",
        "\u21BA", "\u27B0", "\u27BF"
    )
    arrows.forEachIndexed { index, glyph ->
        add(
            StickerItem(
                id = "arrow_$index",
                display = glyph,
                category = StickerCategory.ARROWS,
                contentUri = Uri.parse("content://com.novacut.editor.stickers/arrows/$index")
            )
        )
    }

    val social = listOf(
        "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDCAC",
        "\uD83D\uDCF7", "\uD83C\uDFA5", "\uD83C\uDFB5",
        "\uD83D\uDCE2", "\uD83D\uDD14", "\uD83D\uDC40",
        "\u2705", "\u274C", "\uD83D\uDCCC"
    )
    social.forEachIndexed { index, glyph ->
        add(
            StickerItem(
                id = "social_$index",
                display = glyph,
                category = StickerCategory.SOCIAL,
                contentUri = Uri.parse("content://com.novacut.editor.stickers/social/$index")
            )
        )
    }
}

@Composable
fun StickerPickerPanel(
    onStickerSelected: (Uri) -> Unit,
    onImportFromGallery: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(StickerCategory.EMOJI) }
    val accent = stickerAccent(selectedCategory)
    val stickers = remember(selectedCategory) {
        bundledStickers.filter { it.category == selectedCategory }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.sticker_title),
        subtitle = "Drop in reactions, arrows, social callouts, or branded art without interrupting the cut.",
        icon = Icons.Default.EmojiEmotions,
        accent = accent,
        onClose = onClose,
        modifier = modifier.heightIn(max = 470.dp)
    ) {
        PremiumPanelCard(accent = accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = if (selectedCategory == StickerCategory.CUSTOM) {
                        "Custom import"
                    } else {
                        "${stickers.size} ready"
                    },
                    accent = accent
                )
                PremiumPanelPill(
                    text = selectedCategory.label,
                    accent = Mocha.Sapphire
                )
            }

            Text(
                text = "Sticker shelf",
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Keep overlays fast and expressive with curated bundles for humor, emphasis, direction, and social prompts.",
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StickerCategory.entries.forEach { category ->
                    StickerCategoryChip(
                        category = category,
                        selected = category == selectedCategory,
                        accent = stickerAccent(category),
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedCategory == StickerCategory.CUSTOM) {
            PremiumPanelCard(accent = accent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Mocha.PanelRaised.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = accent.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.cd_sticker_import),
                                tint = accent,
                                modifier = Modifier.padding(18.dp)
                            )
                        }

                        Text(
                            text = "Bring in your own art",
                            color = Mocha.Text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.sticker_add_own_images),
                            color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = onImportFromGallery,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Mocha.Base
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = stringResource(R.string.cd_sticker_import_gallery),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sticker_import_from_gallery))
                        }
                    }
                }
            }
        } else {
            PremiumPanelCard(accent = accent) {
                Text(
                    text = "Bundled collection",
                    color = Mocha.Rosewater,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Tap any sticker to place it immediately as an image overlay on the timeline.",
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 78.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    StickerTile(
                        sticker = sticker,
                        accent = accent,
                        onClick = { onStickerSelected(sticker.contentUri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerCategoryChip(
    category: StickerCategory,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = 0.14f) else Mocha.PanelHighest,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.28f) else Mocha.CardStroke
        )
    ) {
        Text(
            text = category.label,
            color = if (selected) accent else Mocha.Subtext0,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun StickerTile(
    sticker: StickerItem,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val selectLabel = stringResource(R.string.cd_select_sticker, sticker.display)

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClickLabel = selectLabel, onClick = onClick),
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Mocha.CardStrokeStrong.copy(alpha = 0.9f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = accent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = sticker.display,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tap to place",
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun stickerAccent(category: StickerCategory): androidx.compose.ui.graphics.Color = when (category) {
    StickerCategory.EMOJI -> Mocha.Yellow
    StickerCategory.SHAPES -> Mocha.Sapphire
    StickerCategory.ARROWS -> Mocha.Green
    StickerCategory.SOCIAL -> Mocha.Pink
    StickerCategory.CUSTOM -> Mocha.Peach
}

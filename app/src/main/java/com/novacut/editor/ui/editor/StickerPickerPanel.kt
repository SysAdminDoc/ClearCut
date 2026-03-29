package com.novacut.editor.ui.editor

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha

// --- Sticker category model ---

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

// Bundled sticker data using placeholder Unicode characters.
// Each sticker gets a deterministic content URI so the caller can pass it to addImageOverlay().
private val bundledStickers: List<StickerItem> = buildList {
    // Emoji
    val emoji = listOf(
        "\uD83D\uDE00" /* 😀 */, "\uD83D\uDE02" /* 😂 */, "\uD83D\uDE0D" /* 😍 */,
        "\uD83E\uDD29" /* 🤩 */, "\uD83D\uDE0E" /* 😎 */, "\uD83E\uDD14" /* 🤔 */,
        "\uD83D\uDE31" /* 😱 */, "\uD83D\uDE4C" /* 🙌 */, "\uD83D\uDD25" /* 🔥 */,
        "\u2B50"       /* ⭐ */, "\uD83C\uDF89" /* 🎉 */, "\u2764\uFE0F" /* ❤️ */
    )
    emoji.forEachIndexed { i, ch ->
        add(StickerItem("emoji_$i", ch, StickerCategory.EMOJI, Uri.parse("content://com.novacut.editor.stickers/emoji/$i")))
    }

    // Shapes
    val shapes = listOf(
        "\u25CF" /* ● */, "\u25A0" /* ■ */, "\u25B2" /* ▲ */,
        "\u25C6" /* ◆ */, "\u2B1B" /* ⬛ */, "\u2B1C" /* ⬜ */,
        "\u25CB" /* ○ */, "\u25A1" /* □ */, "\u25BD" /* ▽ */,
        "\u2B55" /* ⭕ */, "\u26AB" /* ⚫ */, "\u26AA" /* ⚪ */
    )
    shapes.forEachIndexed { i, ch ->
        add(StickerItem("shape_$i", ch, StickerCategory.SHAPES, Uri.parse("content://com.novacut.editor.stickers/shapes/$i")))
    }

    // Arrows
    val arrows = listOf(
        "\u2B06\uFE0F" /* ⬆️ */, "\u2B07\uFE0F" /* ⬇️ */, "\u27A1\uFE0F" /* ➡️ */,
        "\u2B05\uFE0F" /* ⬅️ */, "\u2197\uFE0F" /* ↗️ */, "\u2198\uFE0F" /* ↘️ */,
        "\u2196\uFE0F" /* ↖️ */, "\u2199\uFE0F" /* ↙️ */, "\u21BB"       /* ↻ */,
        "\u21BA"       /* ↺ */, "\u27B0"       /* ➰ */, "\u27BF"       /* ➿ */
    )
    arrows.forEachIndexed { i, ch ->
        add(StickerItem("arrow_$i", ch, StickerCategory.ARROWS, Uri.parse("content://com.novacut.editor.stickers/arrows/$i")))
    }

    // Social
    val social = listOf(
        "\uD83D\uDC4D" /* 👍 */, "\uD83D\uDC4E" /* 👎 */, "\uD83D\uDCAC" /* 💬 */,
        "\uD83D\uDCF7" /* 📷 */, "\uD83C\uDFA5" /* 🎥 */, "\uD83C\uDFB5" /* 🎵 */,
        "\uD83D\uDCE2" /* 📢 */, "\uD83D\uDD14" /* 🔔 */, "\uD83D\uDC40" /* 👀 */,
        "\u2705"       /* ✅ */, "\u274C"       /* ❌ */, "\uD83D\uDCCC" /* 📌 */
    )
    social.forEachIndexed { i, ch ->
        add(StickerItem("social_$i", ch, StickerCategory.SOCIAL, Uri.parse("content://com.novacut.editor.stickers/social/$i")))
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Mocha.Base)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mocha.Surface0)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sticker_title),
                color = Mocha.Text,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.sticker_close_picker),
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = StickerCategory.entries.indexOf(selectedCategory),
            containerColor = Mocha.Surface0,
            contentColor = Mocha.Text,
            edgePadding = 8.dp,
            divider = {}
        ) {
            StickerCategory.entries.forEach { category ->
                val isSelected = category == selectedCategory
                val tabColor by animateColorAsState(
                    targetValue = if (isSelected) Mocha.Blue else Mocha.Subtext0,
                    label = "tabColor"
                )
                Tab(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    text = {
                        Text(
                            text = category.label,
                            color = tabColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        // Content area
        if (selectedCategory == StickerCategory.CUSTOM) {
            // Custom category: just the import button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.cd_sticker_import),
                        tint = Mocha.Blue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onImportFromGallery,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mocha.Blue,
                            contentColor = Mocha.Base
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = stringResource(R.string.cd_sticker_import_gallery),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sticker_import_from_gallery), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.sticker_add_own_images),
                        color = Mocha.Subtext0,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            // Bundled stickers grid
            val stickers = bundledStickers.filter { it.category == selectedCategory }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable(
                                onClickLabel = "Select sticker ${sticker.display}"
                            ) {
                                onStickerSelected(sticker.contentUri)
                            },
                        colors = CardDefaults.cardColors(containerColor = Mocha.Surface0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sticker.display,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

// --- Tab & sub-menu data ---

data class TabItem(val id: String, val icon: ImageVector, @StringRes val labelRes: Int)
data class SubMenuItem(val id: String, val icon: ImageVector, @StringRes val labelRes: Int)

// Project mode tabs (no clip selected)
val projectTabs = listOf(
    TabItem("edit", Icons.Default.Edit, R.string.tool_tab_edit),
    TabItem("audio", Icons.Default.MusicNote, R.string.tool_tab_audio),
    TabItem("text", Icons.Default.Title, R.string.tool_tab_text),
    TabItem("effects", Icons.Default.AutoFixHigh, R.string.tool_tab_effects),
    TabItem("aspect", Icons.Default.AspectRatio, R.string.tool_tab_aspect),
    TabItem("project_tools", Icons.Default.Build, R.string.tool_tab_tools)
)

// Clip mode tabs (clip selected)
val clipTabs = listOf(
    TabItem("back", Icons.AutoMirrored.Filled.ArrowBack, 0),
    TabItem("edit", Icons.Default.Edit, R.string.tool_tab_edit),
    TabItem("audio", Icons.Default.MusicNote, R.string.tool_tab_audio),
    TabItem("text", Icons.Default.Title, R.string.tool_tab_text),
    TabItem("speed", Icons.Default.Speed, R.string.tool_tab_speed),
    TabItem("transform", Icons.Default.Transform, R.string.tool_tab_motion),
    TabItem("effects", Icons.Default.AutoFixHigh, R.string.tool_tab_fx),
    TabItem("transition", Icons.Default.SwapHoriz, R.string.tool_tab_trans),
    TabItem("color", Icons.Default.Palette, R.string.tool_tab_color),
    TabItem("ai", Icons.Default.AutoAwesome, R.string.tool_tab_ai)
)

// Project mode — Text tab sub-menu
private val textSubMenu = listOf(
    SubMenuItem("add_text", Icons.Default.Title, R.string.tool_add_text),
    SubMenuItem("text_templates", Icons.Default.Dashboard, R.string.tool_text_templates),
    SubMenuItem("captions", Icons.Default.ClosedCaption, R.string.tool_captions),
    SubMenuItem("caption_styles", Icons.Default.Subtitles, R.string.tool_caption_styles),
    SubMenuItem("stickers", Icons.Default.EmojiEmotions, R.string.tool_stickers),
    SubMenuItem("tts", Icons.Default.RecordVoiceOver, R.string.tool_text_to_speech)
)

// Clip mode — Edit tab sub-menu
private val clipEditSubMenu = listOf(
    SubMenuItem("split", Icons.AutoMirrored.Filled.CallSplit, R.string.tool_split),
    SubMenuItem("trim", Icons.Default.ContentCut, R.string.tool_trim),
    SubMenuItem("merge", Icons.Default.Compress, R.string.tool_merge_next),
    SubMenuItem("duplicate", Icons.Default.ContentCopy, R.string.tool_duplicate),
    SubMenuItem("freeze", Icons.Default.AcUnit, R.string.tool_freeze_frame),
    SubMenuItem("copy_fx", Icons.Default.FileCopy, R.string.tool_copy_effects),
    SubMenuItem("paste_fx", Icons.Default.ContentPaste, R.string.tool_paste_effects),
    SubMenuItem("effect_library", Icons.Default.CollectionsBookmark, R.string.effect_library_title),
    SubMenuItem("unlink_av", Icons.Default.LinkOff, R.string.tool_unlink_av),
    SubMenuItem("compound", Icons.Default.ViewModule, R.string.tool_compound_clip),
    SubMenuItem("speed_presets", Icons.Default.Speed, R.string.tool_speed_presets),
    SubMenuItem("group", Icons.Default.GroupWork, R.string.tool_group),
    SubMenuItem("ungroup", Icons.Default.Workspaces, R.string.tool_ungroup),
    SubMenuItem("draw", Icons.Default.Draw, R.string.tool_draw),
    @Suppress("DEPRECATION")
    SubMenuItem("label", Icons.Default.Label, R.string.tool_color_label)
)

// Clip mode — Motion tab sub-menu (replaces simple Transform panel)
private val clipMotionSubMenu = listOf(
    SubMenuItem("transform", Icons.Default.Transform, R.string.tool_submenu_transform),
    SubMenuItem("keyframes", Icons.Default.Timeline, R.string.tool_keyframes),
    SubMenuItem("masks", Icons.Default.Layers, R.string.tool_masks),
    SubMenuItem("blend_mode", Icons.Default.BlurOn, R.string.tool_blend_mode),
    SubMenuItem("pip", Icons.Default.PictureInPicture, R.string.tool_pip),
    SubMenuItem("chroma_key", Icons.Default.Deblur, R.string.tool_chroma_key)
)

// Clip mode — Color tab sub-menu
private val clipColorSubMenu = listOf(
    SubMenuItem("color_grade", Icons.Default.Palette, R.string.tool_color_grade),
    SubMenuItem("effects", Icons.Default.AutoFixHigh, R.string.tool_submenu_effects),
    SubMenuItem("audio_norm", Icons.AutoMirrored.Filled.VolumeUp, R.string.tool_normalize_audio)
)

// Clip mode — AI Magic tab sub-menu (expanded)
private val clipAiSubMenu = listOf(
    SubMenuItem("ai_hub", Icons.Default.AutoAwesome, R.string.tool_ai_hub),
    SubMenuItem("scene_detect", Icons.Default.ContentCut, R.string.tool_scene_detect),
    SubMenuItem("remove_bg", Icons.Default.Wallpaper, R.string.tool_remove_bg),
    SubMenuItem("bg_replace", Icons.Default.PhotoFilter, R.string.tool_replace_bg),
    SubMenuItem("track_motion", Icons.Default.GpsFixed, R.string.tool_track_motion),
    SubMenuItem("face_track", Icons.Default.Face, R.string.tool_face_track),
    SubMenuItem("smart_crop", Icons.Default.Crop, R.string.tool_smart_crop),
    SubMenuItem("smart_reframe", Icons.Default.CropRotate, R.string.tool_smart_reframe),
    SubMenuItem("stabilize", Icons.Default.Straighten, R.string.tool_stabilize),
    SubMenuItem("denoise", Icons.AutoMirrored.Filled.VolumeOff, R.string.tool_denoise),
    SubMenuItem("auto_captions", Icons.Default.ClosedCaption, R.string.tool_auto_captions),
    SubMenuItem("auto_color", Icons.Default.Palette, R.string.tool_auto_color),
    SubMenuItem("style_transfer", Icons.Default.Style, R.string.tool_style_transfer),
    SubMenuItem("object_remove", Icons.Default.HideImage, R.string.tool_object_remove),
    SubMenuItem("upscale", Icons.Default.ZoomIn, R.string.tool_upscale_4k),
    SubMenuItem("frame_interp", Icons.Default.SlowMotionVideo, R.string.tool_frame_interp),
    SubMenuItem("video_upscale", Icons.Default.ZoomIn, R.string.tool_ai_upscale),
    SubMenuItem("ai_background", Icons.Default.PhotoFilter, R.string.tool_ai_background),
    SubMenuItem("ai_stabilize", Icons.Default.Straighten, R.string.tool_ai_stabilize),
    SubMenuItem("ai_style_transfer", Icons.Default.Style, R.string.tool_ai_style),
    SubMenuItem("filler_removal", Icons.Default.ContentCut, R.string.tool_remove_fillers),
    SubMenuItem("noise_reduction", Icons.Default.GraphicEq, R.string.tool_reduce_noise)
)

// Project mode — Tools tab sub-menu
private val projectToolsSubMenu = listOf(
    SubMenuItem("audio_mixer", Icons.Default.Equalizer, R.string.tool_audio_mixer),
    SubMenuItem("beat_detect", Icons.Default.GraphicEq, R.string.tool_beat_detect),
    SubMenuItem("auto_duck", Icons.Default.RecordVoiceOver, R.string.tool_auto_duck),
    SubMenuItem("adjustment_layer", Icons.Default.Tune, R.string.tool_adj_layer),
    SubMenuItem("scopes", Icons.Default.Insights, R.string.tool_video_scopes),
    SubMenuItem("chapters", Icons.Default.Bookmarks, R.string.tool_chapters),
    SubMenuItem("snapshot", Icons.Default.Save, R.string.tool_snapshot),
    SubMenuItem("history", Icons.Default.History, R.string.tool_version_history),
    SubMenuItem("export_srt", Icons.Default.Subtitles, R.string.tool_export_srt),
    SubMenuItem("media_manager", Icons.Default.FolderOpen, R.string.tool_media_manager),
    SubMenuItem("render_preview", Icons.Default.Preview, R.string.tool_render_analysis),
    SubMenuItem("cloud_backup", Icons.Default.Cloud, R.string.tool_cloud_backup),
    SubMenuItem("archive", Icons.Default.Archive, R.string.tool_project_archive),
    SubMenuItem("batch_export", Icons.Default.DynamicFeed, R.string.tool_batch_export),
    SubMenuItem("proxy_toggle", Icons.Default.Speed, R.string.tool_proxy_edit),
    SubMenuItem("beat_sync", Icons.Default.MusicNote, R.string.tool_beat_sync),
    SubMenuItem("auto_edit", Icons.Default.AutoFixHigh, R.string.tool_auto_edit),
    SubMenuItem("multi_cam", Icons.Default.Videocam, R.string.tool_multi_cam),
    SubMenuItem("marker_list", Icons.Default.BookmarkBorder, R.string.tool_marker_list)
)

// --- Bottom tool area (tab bar + contextual sub-menu grids) ---

@Composable
fun BottomToolArea(
    selectedClipId: String?,
    hasCopiedEffects: Boolean,
    modifier: Modifier = Modifier,
    textOverlays: List<TextOverlay> = emptyList(),
    onAction: (String) -> Unit,
    onEditTextOverlay: (String) -> Unit = {},
    onDeleteTextOverlay: (String) -> Unit = {},
    editorMode: EditorMode = EditorMode.PRO
) {
    val isClipMode = selectedClipId != null

    val visibleProjectTabs = if (editorMode == EditorMode.EASY) {
        projectTabs.filter { it.id in setOf("edit", "audio", "text", "effects") }
    } else projectTabs

    val visibleClipTabs = if (editorMode == EditorMode.EASY) {
        clipTabs.filter { it.id in setOf("back", "edit", "speed", "effects", "transition") }
    } else clipTabs

    val tabs = if (isClipMode) visibleClipTabs else visibleProjectTabs
    var activeTabId by remember { mutableStateOf<String?>(null) }

    // Reset active tab when switching between project/clip mode
    LaunchedEffect(isClipMode) {
        activeTabId = null
    }

    // Resolve sub-menu for the currently active tab
    val subMenuItems: List<SubMenuItem>? = when {
        !isClipMode && activeTabId == "text" -> textSubMenu
        !isClipMode && activeTabId == "project_tools" -> projectToolsSubMenu
        isClipMode && activeTabId == "edit" -> clipEditSubMenu
        isClipMode && activeTabId == "text" -> textSubMenu
        isClipMode && activeTabId == "transform" -> clipMotionSubMenu
        isClipMode && activeTabId == "color" -> clipColorSubMenu
        isClipMode && activeTabId == "ai" -> clipAiSubMenu
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Sub-menu grid (slides up above tab bar)
        AnimatedVisibility(
            visible = subMenuItems != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            subMenuItems?.let { items ->
                Column {
                    SubMenuGrid(
                        items = items,
                        onItemSelected = { itemId ->
                            onAction(itemId)
                            activeTabId = null
                        },
                        disabledIds = buildSet {
                            if (!hasCopiedEffects) add("paste_fx")
                            if (!isClipMode) {
                                add("color_grade"); add("keyframes"); add("masks"); add("blend_mode")
                            }
                        }
                    )
                    // Text overlay list when text tab active
                    if (!isClipMode && activeTabId == "text" && textOverlays.isNotEmpty()) {
                        TextOverlayList(
                            overlays = textOverlays,
                            onEdit = onEditTextOverlay,
                            onDelete = onDeleteTextOverlay
                        )
                    }
                }
            }
        }

        // Tab bar
        BottomTabBar(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabTapped = { tabId ->
                when (tabId) {
                    "back" -> {
                        activeTabId = null
                        onAction("back")
                    }
                    "edit" -> {
                        if (isClipMode) {
                            activeTabId = if (activeTabId == "edit") null else "edit"
                        } else {
                            activeTabId = null
                            onAction("edit")
                        }
                    }
                    "audio" -> {
                        activeTabId = null
                        onAction(if (isClipMode) "audio_tool" else "audio_add")
                    }
                    "text" -> {
                        activeTabId = if (activeTabId == "text") null else "text"
                    }
                    "speed" -> {
                        activeTabId = null
                        onAction("speed")
                    }
                    "transform" -> {
                        // Clip mode: show Motion sub-menu (Transform, Keyframes, Masks, Blend, PiP, Chroma)
                        if (isClipMode) {
                            activeTabId = if (activeTabId == "transform") null else "transform"
                        } else {
                            activeTabId = null
                            onAction("transform")
                        }
                    }
                    "effects" -> {
                        activeTabId = null
                        onAction(if (isClipMode) "effects" else "effects_disabled")
                    }
                    "transition" -> {
                        activeTabId = null
                        onAction("transition")
                    }
                    "color" -> {
                        // Clip mode: show Color sub-menu (Color Grade, Effects, Normalize Audio)
                        activeTabId = if (activeTabId == "color") null else "color"
                    }
                    "ai" -> {
                        activeTabId = if (activeTabId == "ai") null else "ai"
                    }
                    "aspect" -> {
                        activeTabId = null
                        onAction("aspect")
                    }
                    "project_tools" -> {
                        // Project mode: show Tools sub-menu (Audio Mixer, Beat Detect, etc.)
                        activeTabId = if (activeTabId == "project_tools") null else "project_tools"
                    }
                }
            }
        )
    }
}

@Composable
private fun BottomTabBar(
    tabs: List<TabItem>,
    activeTabId: String?,
    onTabTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Mocha.Panel,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val fitAllTabs = tabs.size <= 6
            val compactItem = maxWidth < 390.dp

            if (fitAllTabs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        BottomTabBarItem(
                            tab = tab,
                            isActive = activeTabId == tab.id,
                            compact = compactItem,
                            onClick = { onTabTapped(tab.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val tabWidth = if (compactItem) 60.dp else 68.dp
                val canScrollBackward by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                    }
                }
                val canScrollForward by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleItem < layoutInfo.totalItemsCount - 1
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            BottomTabBarItem(
                                tab = tab,
                                isActive = activeTabId == tab.id,
                                compact = compactItem,
                                onClick = { onTabTapped(tab.id) },
                                modifier = Modifier.width(tabWidth)
                            )
                        }
                    }

                    if (canScrollBackward) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(18.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Mocha.Panel, Mocha.Panel.copy(alpha = 0f))
                                    )
                                )
                        )
                    }

                    if (canScrollForward) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(18.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Mocha.Panel.copy(alpha = 0f), Mocha.Panel)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabBarItem(
    tab: TabItem,
    isActive: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBack = tab.id == "back"
    val tabLabel = if (tab.labelRes != 0) stringResource(tab.labelRes) else ""

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (isActive && !isBack) Mocha.Mauve.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 34.dp)
                .clip(CircleShape)
                .background(
                    if (isActive && !isBack) Mocha.Mauve.copy(alpha = 0.14f)
                    else Mocha.PanelHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tab.icon,
                contentDescription = tabLabel.ifEmpty { tab.id },
                tint = if (isActive && !isBack) Mocha.Rosewater else Mocha.Subtext0,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }

        if (tabLabel.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tabLabel,
                fontSize = if (compact) 9.sp else 10.sp,
                color = if (isActive) Mocha.Rosewater else Mocha.Subtext0,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = if (compact) 11.sp else 12.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SubMenuGrid(
    items: List<SubMenuItem>,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabledIds: Set<String> = emptySet()
) {
    val itemsPerRow = 5
    val rows = items.chunked(itemsPerRow)

    Surface(
        color = Mocha.Panel,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Mocha.Surface2.copy(alpha = 0.8f))
            )
            Spacer(modifier = Modifier.height(14.dp))
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowItems.forEach { item ->
                        val isDisabled = item.id in disabledIds
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(if (!isDisabled) Modifier.clickable { onItemSelected(item.id) } else Modifier)
                                .background(Mocha.PanelHighest.copy(alpha = 0.86f))
                                .padding(horizontal = 6.dp, vertical = 10.dp)
                                .width(60.dp)
                                .then(if (isDisabled) Modifier.alpha(0.35f) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val itemLabel = stringResource(item.labelRes)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Mocha.Mauve.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = itemLabel,
                                    tint = Mocha.Text,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = itemLabel,
                                fontSize = 10.sp,
                                color = Mocha.Subtext0,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 12.sp
                            )
                        }
                    }
                    // Fill empty slots so items don't stretch
                    repeat(itemsPerRow - rowItems.size) {
                        Spacer(modifier = Modifier.width(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EffectsPanel(
    selectedClip: Clip?,
    onAddEffect: (EffectType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EffectCategory.COLOR) }
    val accent = effectAccent(selectedCategory)
    val effects = remember(selectedCategory) { EffectType.entries.filter { it.category == selectedCategory } }

    PremiumEditorPanel(
        title = stringResource(R.string.tool_effects),
        subtitle = stringResource(R.string.panel_effects_subtitle),
        icon = Icons.Default.AutoFixHigh,
        accent = accent,
        onClose = onClose,
        modifier = modifier.heightIn(max = 360.dp)
    ) {
        PremiumPanelCard(accent = accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PremiumPanelPill(
                    text = stringResource(R.string.panel_effects_available_count, effects.size),
                    accent = Mocha.Sapphire
                )
                if (selectedClip != null) {
                    PremiumPanelPill(
                        text = stringResource(R.string.panel_effects_applied_count, selectedClip.effects.size),
                        accent = accent
                    )
                }
            }

            Text(
                text = stringResource(R.string.panel_effects_categories),
                color = Mocha.Rosewater,
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EffectCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    val categoryAccent = effectAccent(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.Panel,
                            labelColor = Mocha.Subtext0,
                            selectedContainerColor = categoryAccent.copy(alpha = 0.18f),
                            selectedLabelColor = categoryAccent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(effects) { effectType ->
                val isApplied = selectedClip?.effects?.any { it.type == effectType } == true
                Surface(
                    modifier = Modifier.width(112.dp),
                    onClick = { onAddEffect(effectType) },
                    color = Mocha.PanelHighest,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isApplied) accent.copy(alpha = 0.34f) else Mocha.CardStroke
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                if (isApplied) accent.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isApplied) accent.copy(alpha = 0.18f)
                                    else Mocha.Panel
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = effectIcon(selectedCategory),
                                contentDescription = effectType.displayName,
                                tint = if (isApplied) accent else Mocha.Subtext0,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = effectType.displayName,
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = selectedCategory.displayName,
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (isApplied) {
                            PremiumPanelPill(
                                text = stringResource(R.string.tool_applied),
                                accent = Mocha.Green
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EffectAdjustmentPanel(
    effect: Effect,
    onUpdateParams: (Map<String, Float>) -> Unit,
    modifier: Modifier = Modifier,
    onEffectDragStarted: () -> Unit = {},
    onToggleEnabled: () -> Unit = {},
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    val accent = effectAccent(effect.type.category)

    PremiumEditorPanel(
        title = effect.type.displayName,
        subtitle = stringResource(R.string.panel_effect_adjust_subtitle),
        icon = effectIcon(effect.type.category),
        accent = accent,
        onClose = onClose,
        modifier = modifier,
        headerActions = {
            PremiumPanelIconButton(
                icon = if (effect.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (effect.enabled) stringResource(R.string.tool_disable) else stringResource(R.string.tool_enable),
                tint = if (effect.enabled) Mocha.Green else Mocha.Subtext0,
                onClick = onToggleEnabled
            )
            PremiumPanelIconButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.tool_remove),
                tint = Mocha.Red,
                onClick = onRemove
            )
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumPanelPill(
                text = if (effect.enabled) {
                    stringResource(R.string.panel_effect_status_enabled)
                } else {
                    stringResource(R.string.panel_effect_status_disabled)
                },
                accent = if (effect.enabled) Mocha.Green else Mocha.Subtext0
            )
            PremiumPanelPill(
                text = effect.type.category.displayName,
                accent = accent
            )
        }

        PremiumPanelCard(accent = accent) {
            val ranges = EffectType.paramRangesForType(effect.type)
            val defaults = EffectType.defaultParams(effect.type)
            val ds = onEffectDragStarted
            for ((key, range) in ranges) {
                val currentValue = effect.params[key] ?: defaults[key] ?: 0f
                EffectSlider(range.label, currentValue, range.min, range.max, ds) {
                    onUpdateParams(effect.params + (key to it))
                }
            }
        }
    }
}

@Composable
fun EffectSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    onValueChange: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    Surface(
        color = Mocha.PanelHighest.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    color = Mocha.Subtext1,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = formatEffectValue(value, min, max),
                    color = Mocha.Rosewater,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = value,
                onValueChange = {
                    if (!isDragging) {
                        isDragging = true
                        onDragStarted()
                    }
                    onValueChange(it)
                },
                onValueChangeFinished = { isDragging = false; onDragEnded() },
                valueRange = min..max,
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Rosewater,
                    activeTrackColor = Mocha.Mauve,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }
    }
}

@Composable
fun SpeedPanel(
    currentSpeed: Float,
    isReversed: Boolean,
    modifier: Modifier = Modifier,
    onSpeedDragStarted: () -> Unit = {},
    onSpeedDragEnded: () -> Unit = {},
    onSpeedChanged: (Float) -> Unit,
    onReversedChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val presetSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f)

    PremiumEditorPanel(
        title = stringResource(R.string.tool_speed),
        subtitle = stringResource(R.string.panel_speed_subtitle),
        icon = Icons.Default.Speed,
        accent = Mocha.Peach,
        onClose = onClose,
        modifier = modifier
    ) {
        PremiumPanelCard(accent = Mocha.Peach) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = "${formatEffectValue(currentSpeed, 0.1f, 100f)}x",
                    accent = Mocha.Rosewater
                )
                PremiumPanelPill(
                    text = if (isReversed) {
                        stringResource(R.string.panel_speed_reverse_on)
                    } else {
                        stringResource(R.string.panel_speed_reverse_off)
                    },
                    accent = if (isReversed) Mocha.Red else Mocha.Subtext0
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetSpeeds.forEach { speed ->
                    val isActive = kotlin.math.abs(currentSpeed - speed) < 0.01f
                    FilterChip(
                        onClick = {
                            onSpeedDragStarted()
                            onSpeedChanged(speed)
                        },
                        label = {
                            Text(
                                text = "${formatEffectValue(speed, 0.1f, 100f)}x",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        selected = isActive,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Mocha.Panel,
                            labelColor = Mocha.Text,
                            selectedContainerColor = Mocha.Peach.copy(alpha = 0.2f),
                            selectedLabelColor = Mocha.Peach
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            EffectSlider(
                label = stringResource(R.string.tool_custom_speed),
                value = currentSpeed,
                min = 0.1f,
                max = 100f,
                onDragStarted = onSpeedDragStarted,
                onDragEnded = onSpeedDragEnded,
                onValueChange = onSpeedChanged
            )

            Surface(
                color = Mocha.Panel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Mocha.CardStroke)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tool_reverse),
                            color = Mocha.Text,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isReversed) {
                                stringResource(R.string.panel_speed_reverse_hint_on)
                            } else {
                                stringResource(R.string.panel_speed_reverse_hint_off)
                            },
                            color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isReversed,
                        onCheckedChange = onReversedChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Mocha.Rosewater,
                            checkedTrackColor = Mocha.Mauve.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TransformPanel(
    clip: Clip,
    onTransformDragStarted: () -> Unit,
    onTransformChanged: (positionX: Float?, positionY: Float?, scaleX: Float?, scaleY: Float?, rotation: Float?) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.tool_transform),
        subtitle = stringResource(R.string.panel_transform_subtitle),
        icon = Icons.Default.Transform,
        accent = Mocha.Sapphire,
        onClose = onClose,
        modifier = modifier,
        headerActions = {
            TextButton(onClick = onReset) {
                Text(
                    text = stringResource(R.string.tool_reset),
                    color = Mocha.Peach,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    ) {
        PremiumPanelCard(accent = Mocha.Sapphire) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransformMetricCard(
                    label = stringResource(R.string.text_editor_position),
                    value = "${formatSigned(clip.positionX)} / ${formatSigned(clip.positionY)}",
                    accent = Mocha.Sapphire,
                    modifier = Modifier.weight(1f)
                )
                TransformMetricCard(
                    label = stringResource(R.string.panel_transform_scale),
                    value = "${formatEffectValue(clip.scaleX, 0.1f, 5f)}x / ${formatEffectValue(clip.scaleY, 0.1f, 5f)}x",
                    accent = Mocha.Peach,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransformMetricCard(
                    label = stringResource(R.string.tool_rotation),
                    value = "${formatSigned(clip.rotation)}deg",
                    accent = Mocha.Mauve,
                    modifier = Modifier.weight(1f)
                )
                TransformMetricCard(
                    label = stringResource(R.string.tool_opacity),
                    value = "${(clip.opacity * 100).toInt()}%",
                    accent = Mocha.Green,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PremiumPanelCard(accent = Mocha.Mauve) {
            EffectSlider(stringResource(R.string.tool_position_x), clip.positionX, -1f, 1f, onTransformDragStarted) {
                onTransformChanged(it, null, null, null, null)
            }
            EffectSlider(stringResource(R.string.tool_position_y), clip.positionY, -1f, 1f, onTransformDragStarted) {
                onTransformChanged(null, it, null, null, null)
            }
            EffectSlider(stringResource(R.string.tool_scale_x), clip.scaleX, 0.1f, 5f, onTransformDragStarted) {
                onTransformChanged(null, null, it, null, null)
            }
            EffectSlider(stringResource(R.string.tool_scale_y), clip.scaleY, 0.1f, 5f, onTransformDragStarted) {
                onTransformChanged(null, null, null, it, null)
            }
            EffectSlider(stringResource(R.string.tool_rotation), clip.rotation, -360f, 360f, onTransformDragStarted) {
                onTransformChanged(null, null, null, null, it)
            }
            EffectSlider(stringResource(R.string.tool_opacity), clip.opacity, 0f, 1f, onTransformDragStarted) {
                onOpacityChanged(it)
            }
        }
    }
}

private data class CropPreset(
    val ratio: AspectRatio,
    val platform: String
)

private val cropPresets = listOf(
    CropPreset(AspectRatio.RATIO_16_9, "YouTube / TV"),
    CropPreset(AspectRatio.RATIO_9_16, "TikTok / Reels"),
    CropPreset(AspectRatio.RATIO_1_1, "Instagram Square"),
    CropPreset(AspectRatio.RATIO_4_5, "Instagram Portrait"),
    CropPreset(AspectRatio.RATIO_4_3, "Classic"),
    CropPreset(AspectRatio.RATIO_3_4, "Portrait Classic"),
    CropPreset(AspectRatio.RATIO_21_9, "Cinematic")
)

@Composable
fun CropPanel(
    onCropSelected: (AspectRatio) -> Unit,
    currentAspect: AspectRatio,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumEditorPanel(
        title = stringResource(R.string.tool_crop_aspect_ratio),
        subtitle = stringResource(R.string.panel_crop_subtitle),
        icon = Icons.Default.Crop,
        accent = Mocha.Sapphire,
        onClose = onClose,
        modifier = modifier
    ) {
        PremiumPanelCard(accent = Mocha.Sapphire) {
            Text(
                text = currentAspect.label,
                color = Mocha.Text,
                style = MaterialTheme.typography.headlineMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = aspectUseCase(currentAspect),
                    accent = Mocha.Sapphire
                )
                PremiumPanelPill(
                    text = stringResource(R.string.panel_crop_live_canvas),
                    accent = Mocha.Rosewater
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(cropPresets) { preset ->
                val isActive = currentAspect == preset.ratio
                Surface(
                    modifier = Modifier.width(112.dp),
                    onClick = { onCropSelected(preset.ratio) },
                    color = Mocha.PanelHighest,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isActive) Mocha.Sapphire.copy(alpha = 0.32f) else Mocha.CardStroke
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                if (isActive) Mocha.Sapphire.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val previewW: Float
                        val previewH: Float
                        val maxDim = 40f
                        if (preset.ratio.toFloat() >= 1f) {
                            previewW = maxDim
                            previewH = maxDim / preset.ratio.toFloat()
                        } else {
                            previewH = maxDim
                            previewW = maxDim * preset.ratio.toFloat()
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Mocha.Panel),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = previewW.dp, height = previewH.dp)
                                    .border(
                                        width = 2.dp,
                                        color = if (isActive) Mocha.Sapphire else Mocha.Subtext0,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = preset.ratio.label,
                                color = if (isActive) Mocha.Sapphire else Mocha.Text,
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.platform,
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransitionPicker(
    onTransitionSelected: (TransitionType) -> Unit,
    onRemoveTransition: () -> Unit,
    onDurationChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onDurationDragStarted: () -> Unit = {},
    onClose: () -> Unit,
    currentTransition: Transition?
) {
    PremiumEditorPanel(
        title = stringResource(R.string.tool_transitions),
        subtitle = stringResource(R.string.panel_transition_subtitle),
        icon = Icons.Default.SwapHoriz,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        headerActions = {
            if (currentTransition != null) {
                TextButton(onClick = onRemoveTransition) {
                    Text(
                        text = stringResource(R.string.tool_remove),
                        color = Mocha.Red,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Text(
                text = if (currentTransition != null) {
                    currentTransition.type.displayName
                } else {
                    stringResource(R.string.panel_transition_none_selected)
                },
                color = Mocha.Text,
                style = MaterialTheme.typography.headlineMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumPanelPill(
                    text = if (currentTransition != null) {
                        stringResource(R.string.panel_transition_active)
                    } else {
                        stringResource(R.string.panel_transition_pick_one)
                    },
                    accent = Mocha.Rosewater
                )
                if (currentTransition != null) {
                    PremiumPanelPill(
                        text = "${currentTransition.durationMs}ms",
                        accent = Mocha.Peach
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(TransitionType.entries.toList()) { type ->
                val isActive = currentTransition?.type == type
                val icon = transitionIcon(type)
                Surface(
                    modifier = Modifier.width(108.dp),
                    onClick = { onTransitionSelected(type) },
                    color = Mocha.PanelHighest,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isActive) Mocha.Mauve.copy(alpha = 0.32f) else Mocha.CardStroke
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                if (isActive) Mocha.Mauve.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) Mocha.Mauve.copy(alpha = 0.16f) else Mocha.Panel),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = type.displayName,
                                tint = if (isActive) Mocha.Mauve else Mocha.Subtext0,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = type.displayName,
                            color = if (isActive) Mocha.Mauve else Mocha.Text,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        if (currentTransition != null) {
            Spacer(modifier = Modifier.height(12.dp))
            PremiumPanelCard(accent = Mocha.Peach) {
                EffectSlider(
                    label = stringResource(R.string.tool_duration),
                    value = currentTransition.durationMs.toFloat(),
                    min = 100f,
                    max = 2000f,
                    onDragStarted = onDurationDragStarted,
                    onValueChange = { onDurationChanged(it.toLong()) }
                )
            }
        }
    }
}

@Composable
private fun TransformMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Mocha.Panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Mocha.CardStroke)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

private fun effectAccent(category: EffectCategory): Color = when (category) {
    EffectCategory.COLOR -> Mocha.Sapphire
    EffectCategory.FILTER -> Mocha.Mauve
    EffectCategory.BLUR -> Mocha.Sky
    EffectCategory.DISTORTION -> Mocha.Peach
    EffectCategory.KEYING -> Mocha.Green
    EffectCategory.SPEED -> Mocha.Yellow
}

private fun effectIcon(category: EffectCategory): ImageVector = when (category) {
    EffectCategory.COLOR -> Icons.Default.Palette
    EffectCategory.FILTER -> Icons.Default.FilterVintage
    EffectCategory.BLUR -> Icons.Default.BlurOn
    EffectCategory.DISTORTION -> Icons.Default.Waves
    EffectCategory.KEYING -> Icons.Default.Wallpaper
    EffectCategory.SPEED -> Icons.Default.Speed
}

private fun transitionIcon(type: TransitionType): ImageVector = when (type) {
    TransitionType.DISSOLVE -> Icons.Default.Gradient
    TransitionType.WIPE_LEFT, TransitionType.WIPE_RIGHT,
    TransitionType.WIPE_UP, TransitionType.WIPE_DOWN -> Icons.Default.SwipeLeft
    TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT -> Icons.Default.ZoomIn
    TransitionType.SPIN -> Icons.AutoMirrored.Filled.RotateRight
    TransitionType.FLIP -> Icons.Default.Flip
    TransitionType.CUBE -> Icons.Default.ViewInAr
    TransitionType.RIPPLE -> Icons.Default.Water
    TransitionType.PIXELATE -> Icons.Default.GridOn
    TransitionType.MORPH -> Icons.Default.Transform
    TransitionType.GLITCH -> Icons.Default.BrokenImage
    TransitionType.SWIRL -> Icons.Default.Cyclone
    TransitionType.HEART -> Icons.Default.Favorite
    TransitionType.DREAMY -> Icons.Default.AutoAwesome
    TransitionType.BURN -> Icons.Default.LocalFireDepartment
    TransitionType.LENS_FLARE -> Icons.Default.LensBlur
    TransitionType.PAGE_CURL -> Icons.Default.AutoStories
    TransitionType.KALEIDOSCOPE -> Icons.Default.FilterVintage
    else -> Icons.Default.SwapHoriz
}

private fun aspectUseCase(ratio: AspectRatio): String = when (ratio) {
    AspectRatio.RATIO_9_16 -> "Short form ready"
    AspectRatio.RATIO_1_1 -> "Square social"
    AspectRatio.RATIO_4_5 -> "Feed optimized"
    AspectRatio.RATIO_21_9 -> "Cinematic widescreen"
    else -> "Landscape master"
}

private fun formatEffectValue(value: Float, min: Float, max: Float): String {
    val span = max - min
    return when {
        span <= 2f -> "%.2f".format(value)
        span <= 20f -> "%.1f".format(value)
        else -> value.toInt().toString()
    }
}

private fun formatSigned(value: Float): String {
    val formatted = if (kotlin.math.abs(value) < 10f) {
        "%.2f".format(value)
    } else {
        "%.1f".format(value)
    }
    return if (value > 0f) "+$formatted" else formatted
}

@Composable
private fun TextOverlayList(
    overlays: List<TextOverlay>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mocha.Mantle)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(stringResource(R.string.tool_text_overlays), color = Mocha.Subtext1, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            overlays.forEach { overlay ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Mocha.Surface0)
                        .clickable { onEdit(overlay.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Title,
                        contentDescription = stringResource(R.string.tool_text_overlay_cd),
                        tint = Color(overlay.color),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            overlay.text,
                            color = Mocha.Text,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val startSec = overlay.startTimeMs / 1000f
                        val endSec = overlay.endTimeMs / 1000f
                        Text(
                            "%.1fs — %.1fs".format(startSec, endSec),
                            color = Mocha.Subtext0,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = { onEdit(overlay.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, stringResource(R.string.tool_edit), tint = Mocha.Mauve, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = { onDelete(overlay.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.tool_delete), tint = Mocha.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

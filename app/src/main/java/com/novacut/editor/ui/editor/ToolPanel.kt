package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

// --- Tab & sub-menu data ---

data class TabItem(val id: String, val icon: ImageVector, val label: String)
data class SubMenuItem(val id: String, val icon: ImageVector, val label: String)

// Project mode tabs (no clip selected)
val projectTabs = listOf(
    TabItem("edit", Icons.Default.Edit, "Edit"),
    TabItem("audio", Icons.Default.MusicNote, "Audio"),
    TabItem("text", Icons.Default.Title, "Text"),
    TabItem("effects", Icons.Default.AutoFixHigh, "Effects"),
    TabItem("aspect", Icons.Default.AspectRatio, "Aspect"),
    TabItem("project_tools", Icons.Default.Build, "Tools")
)

// Clip mode tabs (clip selected)
val clipTabs = listOf(
    TabItem("back", Icons.AutoMirrored.Filled.ArrowBack, ""),
    TabItem("edit", Icons.Default.Edit, "Edit"),
    TabItem("audio", Icons.Default.MusicNote, "Audio"),
    TabItem("speed", Icons.Default.Speed, "Speed"),
    TabItem("transform", Icons.Default.Transform, "Motion"),
    TabItem("effects", Icons.Default.AutoFixHigh, "FX"),
    TabItem("transition", Icons.Default.SwapHoriz, "Trans"),
    TabItem("color", Icons.Default.Palette, "Color"),
    TabItem("ai", Icons.Default.AutoAwesome, "AI")
)

// Project mode — Text tab sub-menu
private val textSubMenu = listOf(
    SubMenuItem("add_text", Icons.Default.Title, "Add Text"),
    SubMenuItem("text_templates", Icons.Default.Dashboard, "Templates"),
    SubMenuItem("captions", Icons.Default.ClosedCaption, "Captions"),
    SubMenuItem("caption_styles", Icons.Default.Subtitles, "Caption\nStyles"),
    SubMenuItem("stickers", Icons.Default.EmojiEmotions, "Stickers"),
    SubMenuItem("tts", Icons.Default.RecordVoiceOver, "Text to Speech")
)

// Clip mode — Edit tab sub-menu
private val clipEditSubMenu = listOf(
    SubMenuItem("split", Icons.AutoMirrored.Filled.CallSplit, "Split"),
    SubMenuItem("trim", Icons.Default.ContentCut, "Trim"),
    SubMenuItem("merge", Icons.Default.Compress, "Merge\nNext"),
    SubMenuItem("duplicate", Icons.Default.ContentCopy, "Duplicate"),
    SubMenuItem("freeze", Icons.Default.AcUnit, "Freeze\nFrame"),
    SubMenuItem("copy_fx", Icons.Default.FileCopy, "Copy\nEffects"),
    SubMenuItem("paste_fx", Icons.Default.ContentPaste, "Paste\nEffects"),
    SubMenuItem("unlink_av", Icons.Default.LinkOff, "Unlink\nA/V"),
    SubMenuItem("compound", Icons.Default.ViewModule, "Compound\nClip"),
    SubMenuItem("speed_presets", Icons.Default.Speed, "Speed\nPresets"),
    SubMenuItem("group", Icons.Default.GroupWork, "Group"),
    SubMenuItem("ungroup", Icons.Default.Workspaces, "Ungroup")
)

// Clip mode — Motion tab sub-menu (replaces simple Transform panel)
private val clipMotionSubMenu = listOf(
    SubMenuItem("transform", Icons.Default.Transform, "Transform"),
    SubMenuItem("keyframes", Icons.Default.Timeline, "Keyframes"),
    SubMenuItem("masks", Icons.Default.Layers, "Masks"),
    SubMenuItem("blend_mode", Icons.Default.BlurOn, "Blend\nMode"),
    SubMenuItem("pip", Icons.Default.PictureInPicture, "PiP"),
    SubMenuItem("chroma_key", Icons.Default.Deblur, "Chroma\nKey")
)

// Clip mode — Color tab sub-menu
private val clipColorSubMenu = listOf(
    SubMenuItem("color_grade", Icons.Default.Palette, "Color\nGrade"),
    SubMenuItem("effects", Icons.Default.AutoFixHigh, "Effects"),
    SubMenuItem("audio_norm", Icons.AutoMirrored.Filled.VolumeUp, "Normalize\nAudio")
)

// Clip mode — AI Magic tab sub-menu (expanded)
private val clipAiSubMenu = listOf(
    SubMenuItem("scene_detect", Icons.Default.ContentCut, "Scene\nDetect"),
    SubMenuItem("remove_bg", Icons.Default.Wallpaper, "Remove\nBG"),
    SubMenuItem("bg_replace", Icons.Default.PhotoFilter, "Replace\nBG"),
    SubMenuItem("track_motion", Icons.Default.GpsFixed, "Track\nMotion"),
    SubMenuItem("face_track", Icons.Default.Face, "Face\nTrack"),
    SubMenuItem("smart_crop", Icons.Default.Crop, "Smart\nCrop"),
    SubMenuItem("smart_reframe", Icons.Default.CropRotate, "Smart\nReframe"),
    SubMenuItem("stabilize", Icons.Default.Straighten, "Stabilize"),
    SubMenuItem("denoise", Icons.AutoMirrored.Filled.VolumeOff, "Denoise"),
    SubMenuItem("auto_captions", Icons.Default.ClosedCaption, "Auto\nCaptions"),
    SubMenuItem("auto_color", Icons.Default.Palette, "Auto\nColor"),
    SubMenuItem("style_transfer", Icons.Default.Style, "Style\nTransfer"),
    SubMenuItem("object_remove", Icons.Default.HideImage, "Object\nRemove"),
    SubMenuItem("upscale", Icons.Default.ZoomIn, "Upscale\n4K"),
    SubMenuItem("frame_interp", Icons.Default.SlowMotionVideo, "Frame\nInterp"),
    SubMenuItem("video_upscale", Icons.Default.ZoomIn, "AI\nUpscale"),
    SubMenuItem("ai_background", Icons.Default.PhotoFilter, "AI\nBackground"),
    SubMenuItem("ai_stabilize", Icons.Default.Straighten, "AI\nStabilize"),
    SubMenuItem("ai_style_transfer", Icons.Default.Style, "AI\nStyle"),
    SubMenuItem("filler_removal", Icons.Default.ContentCut, "Remove\nFillers"),
    SubMenuItem("noise_reduction", Icons.Default.GraphicEq, "Reduce\nNoise")
)

// Project mode — Tools tab sub-menu
private val projectToolsSubMenu = listOf(
    SubMenuItem("audio_mixer", Icons.Default.Equalizer, "Audio\nMixer"),
    SubMenuItem("beat_detect", Icons.Default.GraphicEq, "Beat\nDetect"),
    SubMenuItem("auto_duck", Icons.Default.RecordVoiceOver, "Auto\nDuck"),
    SubMenuItem("adjustment_layer", Icons.Default.Tune, "Adj\nLayer"),
    SubMenuItem("scopes", Icons.Default.Insights, "Video\nScopes"),
    SubMenuItem("chapters", Icons.Default.Bookmarks, "Chapters"),
    SubMenuItem("snapshot", Icons.Default.Save, "Snapshot"),
    SubMenuItem("history", Icons.Default.History, "Version\nHistory"),
    SubMenuItem("export_srt", Icons.Default.Subtitles, "Export\nSRT"),
    SubMenuItem("media_manager", Icons.Default.FolderOpen, "Media\nManager"),
    SubMenuItem("render_preview", Icons.Default.Preview, "Render\nAnalysis"),
    SubMenuItem("cloud_backup", Icons.Default.Cloud, "Cloud\nBackup"),
    SubMenuItem("archive", Icons.Default.Archive, "Project\nArchive"),
    SubMenuItem("batch_export", Icons.Default.DynamicFeed, "Batch\nExport"),
    SubMenuItem("proxy_toggle", Icons.Default.Speed, "Proxy\nEdit"),
    SubMenuItem("beat_sync", Icons.Default.MusicNote, "Beat\nSync"),
    SubMenuItem("auto_edit", Icons.Default.AutoFixHigh, "Auto\nEdit")
)

// --- Bottom tool area (tab bar + contextual sub-menu grids) ---

@Composable
fun BottomToolArea(
    selectedClipId: String?,
    hasCopiedEffects: Boolean,
    textOverlays: List<TextOverlay> = emptyList(),
    onAction: (String) -> Unit,
    onEditTextOverlay: (String) -> Unit = {},
    onDeleteTextOverlay: (String) -> Unit = {},
    editorMode: EditorMode = EditorMode.PRO,
    modifier: Modifier = Modifier
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
                        if (!isClipMode) {
                            activeTabId = if (activeTabId == "text") null else "text"
                        }
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
        color = Mocha.Crust,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(tabs, key = { it.id }) { tab ->
                val isActive = activeTabId == tab.id
                val isBack = tab.id == "back"

                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabTapped(tab.id) }
                        .background(
                            if (isActive && !isBack) Mocha.Mauve.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label.ifEmpty { tab.id },
                        tint = if (isActive && !isBack) Mocha.Mauve else Mocha.Subtext0,
                        modifier = Modifier.size(24.dp)
                    )
                    if (tab.label.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            color = if (isActive) Mocha.Mauve else Mocha.Subtext0,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 12.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubMenuGrid(
    items: List<SubMenuItem>,
    onItemSelected: (String) -> Unit,
    disabledIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val itemsPerRow = 5
    val rows = items.chunked(itemsPerRow)

    Surface(
        color = Mocha.Mantle,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowItems.forEach { item ->
                        val isDisabled = item.id in disabledIds
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (!isDisabled) Modifier.clickable { onItemSelected(item.id) } else Modifier)
                                .padding(8.dp)
                                .width(56.dp)
                                .then(if (isDisabled) Modifier.alpha(0.35f) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = Mocha.Text,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.label,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_effects), color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = EffectCategory.entries.indexOf(selectedCategory),
            containerColor = Color.Transparent,
            contentColor = Mocha.Mauve,
            edgePadding = 0.dp,
            divider = {}
        ) {
            EffectCategory.entries.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = {
                        Text(
                            category.displayName,
                            fontSize = 12.sp,
                            color = if (selectedCategory == category) Mocha.Mauve else Mocha.Subtext0
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Effects grid
        val effects = EffectType.entries.filter { it.category == selectedCategory }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(effects) { effectType ->
                val isApplied = selectedClip?.effects?.any { it.type == effectType } == true

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddEffect(effectType) }
                        .background(
                            if (isApplied) Mocha.Mauve.copy(alpha = 0.2f)
                            else Mocha.Surface0
                        )
                        .padding(12.dp)
                        .width(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (selectedCategory) {
                            EffectCategory.COLOR -> Icons.Default.Palette
                            EffectCategory.FILTER -> Icons.Default.FilterVintage
                            EffectCategory.BLUR -> Icons.Default.BlurOn
                            EffectCategory.DISTORTION -> Icons.Default.Waves
                            EffectCategory.KEYING -> Icons.Default.Wallpaper
                            EffectCategory.SPEED -> Icons.Default.Speed
                        },
                        contentDescription = effectType.displayName,
                        tint = if (isApplied) Mocha.Mauve else Mocha.Subtext0,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        effectType.displayName,
                        fontSize = 10.sp,
                        color = if (isApplied) Mocha.Mauve else Mocha.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isApplied) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.tool_applied),
                            tint = Mocha.Green,
                            modifier = Modifier.size(14.dp)
                        )
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
    onEffectDragStarted: () -> Unit = {},
    onToggleEnabled: () -> Unit = {},
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                effect.type.displayName,
                color = if (effect.enabled) Mocha.Text else Mocha.Subtext0,
                fontSize = 16.sp
            )
            Row {
                IconButton(onClick = onToggleEnabled, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (effect.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (effect.enabled) stringResource(R.string.tool_disable) else stringResource(R.string.tool_enable),
                        tint = if (effect.enabled) Mocha.Green else Mocha.Surface2,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.tool_remove), tint = Mocha.Red, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Parameter sliders based on effect type
        val defaults = EffectType.defaultParams(effect.type)
        fun param(key: String) = effect.params[key] ?: defaults[key] ?: 0f

        val ds = onEffectDragStarted
        when (effect.type) {
            EffectType.BRIGHTNESS -> {
                EffectSlider("Brightness", param("value"), -1f, 1f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.CONTRAST -> {
                EffectSlider("Contrast", param("value"), 0f, 3f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.SATURATION -> {
                EffectSlider("Saturation", param("value"), 0f, 3f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.TEMPERATURE -> {
                EffectSlider("Temperature", param("value"), -5f, 5f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.EXPOSURE -> {
                EffectSlider("Exposure", param("value"), -2f, 2f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.VIGNETTE -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
                EffectSlider("Radius", param("radius"), 0.1f, 1f, ds) {
                    onUpdateParams(mapOf("radius" to it))
                }
            }
            EffectType.GAUSSIAN_BLUR -> {
                EffectSlider("Radius", param("radius"), 0f, 25f, ds) {
                    onUpdateParams(mapOf("radius" to it))
                }
            }
            EffectType.CHROMA_KEY -> {
                EffectSlider("Similarity", param("similarity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("similarity" to it))
                }
                EffectSlider("Smoothness", param("smoothness"), 0f, 0.5f, ds) {
                    onUpdateParams(mapOf("smoothness" to it))
                }
                EffectSlider("Spill", param("spill"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("spill" to it))
                }
            }
            EffectType.BG_REMOVAL -> {
                EffectSlider("Threshold", param("threshold"), 0.1f, 0.9f, ds) {
                    onUpdateParams(mapOf("threshold" to it))
                }
            }
            EffectType.FILM_GRAIN -> {
                EffectSlider("Intensity", param("intensity"), 0f, 0.5f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.SHARPEN -> {
                EffectSlider("Strength", param("strength"), 0f, 2f, ds) {
                    onUpdateParams(mapOf("strength" to it))
                }
            }
            EffectType.GLITCH -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.PIXELATE -> {
                EffectSlider("Size", param("size"), 2f, 50f, ds) {
                    onUpdateParams(mapOf("size" to it))
                }
            }
            EffectType.CHROMATIC_ABERRATION -> {
                EffectSlider("Intensity", param("intensity"), 0f, 2f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.CYBERPUNK, EffectType.NOIR, EffectType.VINTAGE, EffectType.COOL_TONE, EffectType.WARM_TONE -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.TILT_SHIFT -> {
                EffectSlider("Blur", param("blur"), 0f, 0.05f, ds) {
                    onUpdateParams(mapOf("blur" to it))
                }
                EffectSlider("Focus Y", param("focusY"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("focusY" to it))
                }
                EffectSlider("Width", param("width"), 0.01f, 0.5f, ds) {
                    onUpdateParams(mapOf("width" to it))
                }
            }
            EffectType.TINT -> {
                EffectSlider("Tint", param("value"), -1f, 1f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.GAMMA -> {
                EffectSlider("Gamma", param("value"), 0.2f, 3f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.HIGHLIGHTS -> {
                EffectSlider("Highlights", param("value"), -1f, 1f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.SHADOWS -> {
                EffectSlider("Shadows", param("value"), -1f, 1f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.VIBRANCE -> {
                EffectSlider("Vibrance", param("value"), -1f, 1f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.MOSAIC -> {
                EffectSlider("Size", param("size"), 2f, 50f, ds) {
                    onUpdateParams(mapOf("size" to it))
                }
            }
            EffectType.RADIAL_BLUR -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.MOTION_BLUR -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.FISHEYE -> {
                EffectSlider("Intensity", param("intensity"), 0f, 1f, ds) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.WAVE -> {
                EffectSlider("Amplitude", param("amplitude"), 0f, 0.1f, ds) {
                    onUpdateParams(mapOf("amplitude" to it))
                }
                EffectSlider("Frequency", param("frequency"), 1f, 30f, ds) {
                    onUpdateParams(mapOf("frequency" to it))
                }
            }
            EffectType.POSTERIZE -> {
                EffectSlider("Levels", param("levels"), 2f, 16f, ds) {
                    onUpdateParams(mapOf("levels" to it))
                }
            }
            EffectType.SPEED -> {
                EffectSlider("Speed", param("value"), 0.1f, 16f, ds) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            else -> {
                // Effects without adjustable parameters (GRAYSCALE, SEPIA, INVERT, MIRROR, REVERSE)
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
    onValueChange: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Mocha.Subtext1, fontSize = 12.sp)
            Text("%.2f".format(value), color = Mocha.Subtext0, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = {
                if (!isDragging) {
                    isDragging = true
                    onDragStarted()
                }
                onValueChange(it)
            },
            onValueChangeFinished = { isDragging = false },
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Mocha.Mauve,
                activeTrackColor = Mocha.Mauve,
                inactiveTrackColor = Mocha.Surface1
            )
        )
    }
}

@Composable
fun SpeedPanel(
    currentSpeed: Float,
    isReversed: Boolean,
    onSpeedDragStarted: () -> Unit = {},
    onSpeedChanged: (Float) -> Unit,
    onReversedChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presetSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_speed), color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Speed presets
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presetSpeeds) { speed ->
                val isActive = kotlin.math.abs(currentSpeed - speed) < 0.01f
                FilterChip(
                    onClick = { onSpeedDragStarted(); onSpeedChanged(speed) },
                    label = { Text("${speed}x", fontSize = 12.sp) },
                    selected = isActive,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        labelColor = Mocha.Text,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom speed slider with drag start for undo debounce
        EffectSlider(stringResource(R.string.tool_custom_speed), currentSpeed, 0.1f, 16f, onSpeedDragStarted) { onSpeedChanged(it) }

        // Reverse toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_reverse), color = Mocha.Text, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isReversed,
                onCheckedChange = onReversedChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Mocha.Mauve,
                    checkedTrackColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_transform), color = Mocha.Text, fontSize = 16.sp)
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.tool_reset), color = Mocha.Peach, fontSize = 12.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_crop_aspect_ratio), color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cropPresets) { preset ->
                val isActive = currentAspect == preset.ratio
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCropSelected(preset.ratio) }
                        .background(
                            if (isActive) Mocha.Mauve.copy(alpha = 0.2f) else Mocha.Surface0
                        )
                        .padding(12.dp)
                        .width(80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Aspect ratio preview box
                    val previewW: Float
                    val previewH: Float
                    val maxDim = 32f
                    if (preset.ratio.toFloat() >= 1f) {
                        previewW = maxDim
                        previewH = maxDim / preset.ratio.toFloat()
                    } else {
                        previewH = maxDim
                        previewW = maxDim * preset.ratio.toFloat()
                    }
                    Box(
                        modifier = Modifier
                            .size(width = previewW.dp, height = previewH.dp)
                            .border(
                                width = 2.dp,
                                color = if (isActive) Mocha.Mauve else Mocha.Subtext0,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        preset.ratio.label,
                        fontSize = 12.sp,
                        color = if (isActive) Mocha.Mauve else Mocha.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        preset.platform,
                        fontSize = 9.sp,
                        color = if (isActive) Mocha.Mauve.copy(alpha = 0.7f) else Mocha.Subtext0,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    onDurationDragStarted: () -> Unit = {},
    onClose: () -> Unit,
    currentTransition: Transition?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.tool_transitions), color = Mocha.Text, fontSize = 16.sp)
            Row {
                if (currentTransition != null) {
                    TextButton(onClick = onRemoveTransition) {
                        Text(stringResource(R.string.tool_remove), color = Mocha.Red, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.tool_close), tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TransitionType.entries.toList()) { type ->
                val isActive = currentTransition?.type == type
                val icon = when (type) {
                    TransitionType.DISSOLVE -> Icons.Default.Gradient
                    TransitionType.WIPE_LEFT, TransitionType.WIPE_RIGHT,
                    TransitionType.WIPE_UP, TransitionType.WIPE_DOWN -> Icons.Default.SwipeLeft
                    TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT -> Icons.Default.ZoomIn
                    TransitionType.SPIN -> Icons.Default.RotateRight
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
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTransitionSelected(type) }
                        .background(
                            if (isActive) Mocha.Mauve.copy(alpha = 0.2f) else Mocha.Surface0
                        )
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) Mocha.Mauve else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .width(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = type.displayName,
                        tint = if (isActive) Mocha.Mauve else Mocha.Subtext0,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        type.displayName,
                        fontSize = 10.sp,
                        color = if (isActive) Mocha.Mauve else Mocha.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        // Duration control (visible when a transition is applied)
        if (currentTransition != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.tool_duration), color = Mocha.Subtext1, fontSize = 12.sp)
                Text("${currentTransition.durationMs}ms", color = Mocha.Subtext0, fontSize = 12.sp)
            }
            var isDragging by remember { mutableStateOf(false) }
            Slider(
                value = currentTransition.durationMs.toFloat(),
                onValueChange = {
                    if (!isDragging) {
                        isDragging = true
                        onDurationDragStarted()
                    }
                    onDurationChanged(it.toLong())
                },
                onValueChangeFinished = { isDragging = false },
                valueRange = 100f..2000f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Mauve,
                    activeTrackColor = Mocha.Mauve,
                    inactiveTrackColor = Mocha.Surface1
                )
            )
        }
    }
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

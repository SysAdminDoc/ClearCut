package com.novacut.editor.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.novacut.editor.R

data class CommandEntry(
    val actionId: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val category: String,
    val requiresClip: Boolean = false
)

object CommandRegistry {

    fun allCommands(): List<CommandEntry> = buildList {
        addTextCommands()
        addEditCommands()
        addMotionCommands()
        addColorCommands()
        addAiCommands()
        addToolCommands()
        addProjectCommands()
    }

    private fun MutableList<CommandEntry>.addTextCommands() {
        val cat = "Text"
        add(CommandEntry("add_text", Icons.Default.Title, R.string.tool_add_text, cat))
        add(CommandEntry("text_templates", Icons.Default.Dashboard, R.string.tool_text_templates, cat))
        add(CommandEntry("captions", Icons.Default.ClosedCaption, R.string.tool_captions, cat))
        add(CommandEntry("caption_styles", Icons.Default.Subtitles, R.string.tool_caption_styles, cat))
        add(CommandEntry("stickers", Icons.Default.EmojiEmotions, R.string.tool_stickers, cat))
        add(CommandEntry("tts", Icons.Default.RecordVoiceOver, R.string.tool_text_to_speech, cat))
    }

    private fun MutableList<CommandEntry>.addEditCommands() {
        val cat = "Edit"
        add(CommandEntry("split", Icons.AutoMirrored.Filled.CallSplit, R.string.tool_split, cat, requiresClip = true))
        add(CommandEntry("trim", Icons.Default.ContentCut, R.string.tool_trim, cat, requiresClip = true))
        add(CommandEntry("merge", Icons.Default.Compress, R.string.tool_merge_next, cat, requiresClip = true))
        add(CommandEntry("duplicate", Icons.Default.ContentCopy, R.string.tool_duplicate, cat, requiresClip = true))
        add(CommandEntry("freeze", Icons.Default.AcUnit, R.string.tool_freeze_frame, cat, requiresClip = true))
        add(CommandEntry("copy_fx", Icons.Default.FileCopy, R.string.tool_copy_effects, cat, requiresClip = true))
        add(CommandEntry("paste_fx", Icons.Default.ContentPaste, R.string.tool_paste_effects, cat, requiresClip = true))
        add(CommandEntry("effect_library", Icons.Default.CollectionsBookmark, R.string.effect_library_title, cat))
        add(CommandEntry("speed_presets", Icons.Default.Speed, R.string.tool_speed_presets, cat, requiresClip = true))
        add(CommandEntry("group", Icons.Default.GroupWork, R.string.tool_group, cat, requiresClip = true))
        add(CommandEntry("ungroup", Icons.Default.Workspaces, R.string.tool_ungroup, cat, requiresClip = true))
        add(CommandEntry("draw", Icons.Default.Draw, R.string.tool_draw, cat, requiresClip = true))
    }

    private fun MutableList<CommandEntry>.addMotionCommands() {
        val cat = "Motion"
        add(CommandEntry("transform", Icons.Default.Transform, R.string.tool_submenu_transform, cat, requiresClip = true))
        add(CommandEntry("keyframes", Icons.Default.Timeline, R.string.tool_keyframes, cat, requiresClip = true))
        add(CommandEntry("masks", Icons.Default.Layers, R.string.tool_masks, cat, requiresClip = true))
        add(CommandEntry("blend_mode", Icons.Default.BlurOn, R.string.tool_blend_mode, cat, requiresClip = true))
        add(CommandEntry("pip", Icons.Default.PictureInPicture, R.string.tool_pip, cat, requiresClip = true))
        add(CommandEntry("chroma_key", Icons.Default.Deblur, R.string.tool_chroma_key, cat, requiresClip = true))
    }

    private fun MutableList<CommandEntry>.addColorCommands() {
        val cat = "Color"
        add(CommandEntry("color_grade", Icons.Default.Palette, R.string.tool_color_grade, cat, requiresClip = true))
        add(CommandEntry("audio_norm", Icons.AutoMirrored.Filled.VolumeUp, R.string.tool_normalize_audio, cat, requiresClip = true))
    }

    private fun MutableList<CommandEntry>.addAiCommands() {
        val cat = "AI"
        add(CommandEntry("ai_hub", Icons.Default.AutoAwesome, R.string.tool_ai_hub, cat, requiresClip = true))
        add(CommandEntry("cut_assistant", Icons.Default.ContentCut, R.string.tool_cut_assistant, cat, requiresClip = true))
        add(CommandEntry("scene_detect", Icons.Default.ContentCut, R.string.tool_scene_detect, cat, requiresClip = true))
        add(CommandEntry("remove_bg", Icons.Default.Wallpaper, R.string.tool_remove_bg, cat, requiresClip = true))
        add(CommandEntry("smart_crop", Icons.Default.Crop, R.string.tool_smart_crop, cat, requiresClip = true))
        add(CommandEntry("smart_reframe", Icons.Default.CropRotate, R.string.tool_smart_reframe, cat, requiresClip = true))
        add(CommandEntry("stabilize", Icons.Default.Straighten, R.string.tool_stabilize, cat, requiresClip = true))
        add(CommandEntry("denoise", Icons.AutoMirrored.Filled.VolumeOff, R.string.tool_denoise, cat, requiresClip = true))
        add(CommandEntry("auto_captions", Icons.Default.ClosedCaption, R.string.tool_auto_captions, cat, requiresClip = true))
        add(CommandEntry("auto_color", Icons.Default.Palette, R.string.tool_auto_color, cat, requiresClip = true))
        add(CommandEntry("style_transfer", Icons.Default.Style, R.string.tool_style_transfer, cat, requiresClip = true))
        add(CommandEntry("object_remove", Icons.Default.HideImage, R.string.tool_object_remove, cat, requiresClip = true))
        add(CommandEntry("upscale", Icons.Default.ZoomIn, R.string.tool_upscale_4k, cat, requiresClip = true))
        add(CommandEntry("filler_removal", Icons.Default.ContentCut, R.string.tool_remove_fillers, cat, requiresClip = true))
        add(CommandEntry("noise_reduction", Icons.Default.GraphicEq, R.string.tool_reduce_noise, cat, requiresClip = true))
        add(CommandEntry("auto_edit", Icons.Default.AutoFixHigh, R.string.tool_auto_edit, cat))
        add(CommandEntry("beat_sync", Icons.Default.MusicNote, R.string.tool_beat_sync, cat))
    }

    private fun MutableList<CommandEntry>.addToolCommands() {
        val cat = "Tools"
        add(CommandEntry("audio_mixer", Icons.Default.Equalizer, R.string.tool_audio_mixer, cat))
        add(CommandEntry("beat_detect", Icons.Default.GraphicEq, R.string.tool_beat_detect, cat))
        add(CommandEntry("auto_duck", Icons.Default.RecordVoiceOver, R.string.tool_auto_duck, cat))
        add(CommandEntry("scopes", Icons.Default.Insights, R.string.tool_video_scopes, cat))
        add(CommandEntry("chapters", Icons.Default.Bookmarks, R.string.tool_chapters, cat))
        add(CommandEntry("media_manager", Icons.Default.FolderOpen, R.string.tool_media_manager, cat))
        add(CommandEntry("batch_export", Icons.Default.DynamicFeed, R.string.tool_batch_export, cat))
        add(CommandEntry("archive", Icons.Default.Archive, R.string.tool_project_archive, cat))
        add(CommandEntry("multi_cam", Icons.Default.Videocam, R.string.tool_multi_cam, cat))
        add(CommandEntry("marker_list", Icons.Default.BookmarkBorder, R.string.tool_marker_list, cat))
    }

    private fun MutableList<CommandEntry>.addProjectCommands() {
        val cat = "Project"
        add(CommandEntry("add_media", Icons.Default.Add, R.string.tool_add_media, cat))
        add(CommandEntry("export", Icons.Default.Upload, R.string.tool_export, cat))
        add(CommandEntry("undo", Icons.AutoMirrored.Filled.Undo, R.string.tool_undo, cat))
        add(CommandEntry("redo", Icons.AutoMirrored.Filled.Redo, R.string.tool_redo, cat))
        add(CommandEntry("multi_delete", Icons.Default.Delete, R.string.editor_delete, "Batch", requiresClip = true))
        add(CommandEntry("multi_paste_fx", Icons.Default.ContentPaste, R.string.editor_paste_fx, "Batch", requiresClip = true))
    }

    fun fuzzyMatch(query: String, label: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        val l = label.lowercase()
        if (l.contains(q)) return true
        var qi = 0
        for (c in l) {
            if (qi < q.length && c == q[qi]) qi++
        }
        return qi >= q.length
    }
}

@Composable
fun CommandPaletteSheet(
    hasSelectedClip: Boolean,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCommands = remember { CommandRegistry.allCommands() }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filtered = remember(query, hasSelectedClip) {
        val resolved = allCommands.map { cmd ->
            cmd to context.getString(cmd.labelRes)
        }
        if (query.isBlank()) {
            resolved
        } else {
            resolved.filter { (cmd, label) ->
                CommandRegistry.fuzzyMatch(query, label) ||
                    CommandRegistry.fuzzyMatch(query, cmd.category) ||
                    CommandRegistry.fuzzyMatch(query, cmd.actionId)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.command_palette_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(top = 4.dp)
            ) {
                items(filtered, key = { it.first.actionId }) { (cmd, label) ->
                    val enabled = !cmd.requiresClip || hasSelectedClip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                onAction(cmd.actionId)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = cmd.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                            Text(
                                text = cmd.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        if (cmd.requiresClip && !hasSelectedClip) {
                            Text(
                                text = "Select clip",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

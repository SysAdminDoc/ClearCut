package com.novacut.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.novacut.editor.R
import com.novacut.editor.engine.ColorBlindPreviewEngine
import com.novacut.editor.engine.DirectPublishEngine
import com.novacut.editor.engine.KaraokeCaptionEngine
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.ClearCutFilterChip
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget

/**
 * Creator workflow hub: a single scrollable sheet that groups the advanced
 * creator, safety, publishing, and accessibility tools. Keeps the editor compact
 * instead of scattering 10 new PanelIds across the bottom tab bar. Each
 * card dispatches directly into V369Delegate via the ViewModel.
 *
 * UX invariants:
 *   * Only the card header is clickable-to-expand; child controls (buttons,
 *     chips, text fields) never double-trigger the toggle.
 *   * Every chip row is horizontally scrollable so narrow phones don't
 *     truncate options.
 *   * Features whose backing pipeline needs prerequisites explain the next
 *     step instead of presenting a dead control.
 */
@Composable
fun V369FeaturesPanel(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val v = state.v369
    val hasClip = state.selectedClipId != null

    PremiumEditorPanel(
        title = stringResource(R.string.v369_features_label),
        subtitle = stringResource(R.string.v369_subtitle),
        icon = Icons.Default.AutoAwesome,
        accent = Mocha.Mauve,
        onClose = onDismiss,
        scrollable = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CreatorReadinessStrip(
                hasClip = hasClip,
                hasTranscript = v.transcript != null,
                hasExport = state.lastExportedFilePath != null
            )

        // 1. Text-based editing
        FeatureCard(
            title = stringResource(R.string.v369_text_edit_title),
            subtitle = stringResource(R.string.v369_text_edit_subtitle),
            accent = Mocha.Blue,
            icon = Icons.AutoMirrored.Filled.Subject
        ) {
            val transcript = v.transcript
            Text(
                if (transcript == null) stringResource(R.string.v369_text_edit_need_transcript)
                else stringResource(R.string.v369_text_edit_words_status, transcript.words.size, v.selectedWordIndices.size),
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyButton(stringResource(R.string.v369_select_fillers), enabled = transcript != null) {
                    viewModel.v369Delegate.selectFillerWords()
                }
                TinyButton(stringResource(R.string.v369_apply_cuts), enabled = hasClip && v.selectedWordIndices.isNotEmpty()) {
                    state.selectedClipId?.let { viewModel.v369Delegate.applyDeletions(it) }
                }
            }
        }

        // 2. Auto-chapter
        FeatureCard(
            title = stringResource(R.string.v369_chapters_title),
            subtitle = stringResource(R.string.v369_chapters_subtitle),
            accent = Mocha.Green,
            icon = Icons.AutoMirrored.Filled.ViewList
        ) {
            Text(
                pluralStringResource(R.plurals.v369_chapter_candidates_ready, v.chapterCandidates.size, v.chapterCandidates.size),
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyButton(stringResource(R.string.v369_detect), enabled = v.transcript != null && !v.isGeneratingChapters) {
                    viewModel.v369Delegate.generateChapters(v.transcript?.words ?: emptyList())
                }
                TinyButton(stringResource(R.string.ai_apply), enabled = v.chapterCandidates.isNotEmpty()) {
                    viewModel.v369Delegate.applyChaptersToProject()
                }
            }
        }

        // 3. Talking-head framing
        FeatureCard(
            title = stringResource(R.string.v369_talking_head_title),
            subtitle = stringResource(R.string.v369_talking_head_subtitle),
            accent = Mocha.Sapphire,
            icon = Icons.Default.Face
        ) {
            if (v.isTrackingFaces) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Sapphire)
            TinyButton(stringResource(R.string.v369_track_selected_clip), enabled = hasClip && !v.isTrackingFaces) {
                state.selectedClipId?.let { viewModel.v369Delegate.trackTalkingHead(it) }
            }
        }

        // 4. Karaoke captions
        FeatureCard(
            title = stringResource(R.string.v369_karaoke_title),
            subtitle = stringResource(R.string.v369_karaoke_subtitle),
            accent = Mocha.Yellow,
            icon = Icons.Default.Mic
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (style in KaraokeCaptionEngine.KaraokeStyle.entries) {
                    ClearCutFilterChip(
                        selected = v.karaokeStyle == style,
                        onClick = { viewModel.v369Delegate.setKaraokeStyle(style) },
                        text = karaokeStyleLabel(style),
                        accent = Mocha.Yellow
                    )
                }
            }
            TinyButton(stringResource(R.string.v369_generate_captions), enabled = v.transcript != null) {
                viewModel.v369Delegate.generateKaraokeCaptions()
            }
        }

        // 5. Stream-copy export
        FeatureCard(
            title = stringResource(R.string.v369_stream_copy_title),
            subtitle = stringResource(R.string.v369_stream_copy_subtitle),
            accent = Mocha.Teal,
            icon = Icons.Default.Speed
        ) {
            val elig = v.streamCopyEligibility
            val label = when {
                elig == null -> stringResource(R.string.v369_stream_copy_unchecked)
                elig.eligible -> stringResource(R.string.v369_stream_copy_eligible)
                else -> stringResource(R.string.v369_stream_copy_reencode, elig.reason)
            }
            val color = when {
                elig == null -> Mocha.Subtext1
                elig.eligible -> Mocha.Green
                else -> Mocha.Peach
            }
            Text(label, color = color, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.v369_stream_copy_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
            TinyButton(stringResource(R.string.v369_check_eligibility)) {
                viewModel.v369Delegate.checkStreamCopyEligibility()
            }
        }

        // 6. Content-ID / AcoustID
        FeatureCard(
            title = stringResource(R.string.v369_content_id_title),
            subtitle = stringResource(R.string.v369_content_id_subtitle),
            accent = Mocha.Red,
            icon = Icons.Default.Copyright
        ) {
            val result = v.contentIdResult
            if (result != null) {
                val matchedTitle = result.matchedTitle
                val txt = if (matchedTitle != null) stringResource(R.string.v369_content_id_match, matchedTitle)
                else stringResource(R.string.v369_content_id_hash, result.hash.take(16))
                Text(txt, color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.v369_content_id_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
            TinyButton(stringResource(R.string.v369_check_last_export), enabled = state.lastExportedFilePath != null) {
                viewModel.v369Delegate.runContentIdOnLastExport()
            }
        }

        // 7. Platform share handoff
        FeatureCard(
            title = stringResource(R.string.v369_direct_publish_title),
            subtitle = stringResource(R.string.v369_direct_publish_subtitle),
            accent = Mocha.Pink,
            icon = Icons.Default.Share
        ) {
            var title by rememberSaveable(state.project.id) { mutableStateOf(state.project.name) }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(stringResource(R.string.v369_publish_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Mocha.PanelHighest,
                    unfocusedContainerColor = Mocha.PanelHighest.copy(alpha = 0.72f),
                    focusedBorderColor = Mocha.Pink.copy(alpha = 0.64f),
                    unfocusedBorderColor = Mocha.CardStroke,
                    cursorColor = Mocha.Pink,
                    focusedTextColor = Mocha.Text,
                    unfocusedTextColor = Mocha.Text,
                    focusedLabelColor = Mocha.Pink,
                    unfocusedLabelColor = Mocha.Subtext0
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.v369_direct_publish_handoff_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
            val hasExport = state.lastExportedFilePath != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (target in DirectPublishEngine.Target.entries) {
                    AssistChip(
                        onClick = {
                            viewModel.v369Delegate.publishLastExport(target, title, state.project.notes)
                        },
                        label = {
                            Text(
                                stringResource(R.string.v369_open_target_format, publishTargetLabel(target)),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        enabled = hasExport
                    )
                }
            }
        }

        // 8. Flash safety
        FeatureCard(
            title = stringResource(R.string.v369_flash_safety_title),
            subtitle = stringResource(R.string.v369_flash_safety_subtitle),
            accent = Mocha.Peach,
            icon = Icons.Default.FlashOn
        ) {
            if (v.isAnalyzingFlashes) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Peach)
            if (v.flashWarnings.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.v369_flash_risky_segments, v.flashWarnings.size, v.flashWarnings.size),
                    color = Mocha.Peach,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TinyButton(stringResource(R.string.v369_scan_selected_clip), enabled = hasClip && !v.isAnalyzingFlashes) {
                state.selectedClipId?.let { viewModel.v369Delegate.analyzeFlashSafety(it) }
            }
        }

        // 9. Color-blind preview
        FeatureCard(
            title = stringResource(R.string.v369_color_blind_title),
            subtitle = stringResource(R.string.v369_color_blind_subtitle),
            accent = Mocha.Mauve,
            icon = Icons.Default.Palette
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (mode in ColorBlindPreviewEngine.Mode.entries) {
                    ClearCutFilterChip(
                        selected = v.colorBlindMode == mode,
                        onClick = { viewModel.v369Delegate.setColorBlindMode(mode) },
                        text = colorBlindModeLabel(mode),
                        accent = Mocha.Mauve
                    )
                }
            }
            Text(
                stringResource(R.string.v369_color_blind_hint),
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // 10. AI thumbnail picker
        FeatureCard(
            title = stringResource(R.string.v369_thumbnail_title),
            subtitle = stringResource(R.string.v369_thumbnail_subtitle),
            accent = Mocha.Sky,
            icon = Icons.Default.Image
        ) {
            if (v.isScoringThumbnails) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Sky)
            if (v.thumbnailCandidates.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    v.thumbnailCandidates.forEach { cand ->
                        val bmp = cand.bitmap
                        if (bmp != null && !bmp.isRecycled) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.v369_thumbnail_candidate_cd, cand.timeMs),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Mocha.Sky.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                )
                                Text(
                                    stringResource(R.string.v369_thumbnail_score_format, cand.score),
                                    color = Mocha.Subtext0,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            TinyButton(stringResource(R.string.v369_score_clip_frames), enabled = hasClip && !v.isScoringThumbnails) {
                state.selectedClipId?.let { viewModel.v369Delegate.scoreThumbnails(it) }
            }
        }

        // 11. HDR preservation (HDR10 / HDR10+ / HLG passthrough on HEVC/AV1/VP9)
        FeatureCard(
            title = stringResource(R.string.v369_hdr_title),
            subtitle = stringResource(R.string.v369_hdr_subtitle),
            accent = Mocha.Rosewater,
            icon = Icons.Default.Hd
        ) {
            val hdr = state.exportConfig.hdr10PlusMetadata
            val codecCanCarryHdr = state.exportConfig.codec !=
                com.novacut.editor.model.VideoCodec.H264
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = hdr && codecCanCarryHdr,
                    enabled = codecCanCarryHdr,
                    onCheckedChange = { on ->
                        viewModel.updateExportConfig(state.exportConfig.copy(hdr10PlusMetadata = on))
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !codecCanCarryHdr -> stringResource(R.string.v369_hdr_switch_codec)
                        hdr -> stringResource(R.string.v369_hdr_enabled)
                        else -> stringResource(R.string.v369_hdr_off)
                    },
                    color = Mocha.Subtext1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 12. SDH / Audio description
        FeatureCard(
            title = stringResource(R.string.v369_sdh_title),
            subtitle = stringResource(R.string.v369_sdh_subtitle),
            accent = Mocha.Maroon,
            icon = Icons.Default.Hearing
        ) {
            Text(
                stringResource(R.string.v369_sdh_hint),
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 13. DeX / desktop-mode
        FeatureCard(
            title = stringResource(R.string.v369_dex_title),
            subtitle = stringResource(R.string.v369_dex_subtitle),
            accent = Mocha.Lavender,
            icon = Icons.Default.DesktopWindows
        ) {
            val override by viewModel.desktopOverride.collectAsStateWithLifecycle()
            val active = LocalLayoutMode.current == LayoutMode.DESKTOP
            Text(
                if (active) stringResource(R.string.v369_dex_active) else stringResource(R.string.v369_dex_phone),
                color = if (active) Mocha.Green else Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (opt in com.novacut.editor.engine.DesktopOverride.entries) {
                    ClearCutFilterChip(
                        selected = override == opt,
                        onClick = { viewModel.setDesktopOverride(opt) },
                        text = when (opt) {
                            com.novacut.editor.engine.DesktopOverride.AUTO -> stringResource(R.string.v369_dex_auto)
                            com.novacut.editor.engine.DesktopOverride.FORCE_ON -> stringResource(R.string.v369_dex_desktop)
                            com.novacut.editor.engine.DesktopOverride.FORCE_OFF -> stringResource(R.string.v369_dex_phone_chip)
                        },
                        accent = Mocha.Lavender
                    )
                }
            }
        }

        // 14. One-handed mode
        FeatureCard(
            title = stringResource(R.string.v369_one_handed_title),
            subtitle = stringResource(R.string.v369_one_handed_subtitle),
            accent = Mocha.Flamingo,
            icon = Icons.Default.TouchApp
        ) {
            val one by viewModel.oneHandedMode.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = one, onCheckedChange = { viewModel.setOneHandedMode(it) })
                Spacer(Modifier.width(8.dp))
                Text(
                if (one) stringResource(R.string.v369_one_handed_active) else stringResource(R.string.state_off),
                    color = Mocha.Subtext1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 15. S Pen + BT MIDI jog/shuttle
        FeatureCard(
            title = stringResource(R.string.v369_midi_title),
            subtitle = stringResource(R.string.v369_midi_subtitle),
            accent = Mocha.Flamingo,
            icon = Icons.Default.Tune
        ) {
            val midiScanningToast = stringResource(R.string.v369_midi_scanning)
            val midiNotFoundToast = stringResource(R.string.v369_midi_not_found)
            TinyButton(stringResource(R.string.v369_midi_connect)) {
                val ok = viewModel.v369Delegate.stylusMidi.connectFirstAvailable()
                viewModel.showToast(if (ok) midiScanningToast else midiNotFoundToast)
            }
        }

        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreatorReadinessStrip(
    hasClip: Boolean,
    hasTranscript: Boolean,
    hasExport: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        ReadinessCard(
            label = stringResource(R.string.v369_readiness_clip),
            value = if (hasClip) stringResource(R.string.v369_readiness_clip_selected) else stringResource(R.string.v369_readiness_choose_one),
            accent = if (hasClip) Mocha.Green else Mocha.Peach,
            icon = Icons.Default.Movie
        )
        ReadinessCard(
            label = stringResource(R.string.v369_readiness_transcript),
            value = if (hasTranscript) stringResource(R.string.ai_tool_status_ready) else stringResource(R.string.v369_readiness_run_captions),
            accent = if (hasTranscript) Mocha.Green else Mocha.Sapphire,
            icon = Icons.Default.ClosedCaption
        )
        ReadinessCard(
            label = stringResource(R.string.export_title),
            value = if (hasExport) stringResource(R.string.v369_readiness_available) else stringResource(R.string.v369_readiness_render_first),
            accent = if (hasExport) Mocha.Green else Mocha.Mauve,
            icon = Icons.Default.FileUpload
        )
    }
}

@Composable
private fun ReadinessCard(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector
) {
    Surface(
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    text = label,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = value,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    body: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Mocha.PanelRaised,
        shape = RoundedCornerShape(Radius.xl),
        border = BorderStroke(1.dp, accent.copy(alpha = if (expanded) 0.52f else 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header-only clickable so body controls never double-trigger the toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TouchTarget.minimum)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(Radius.lg),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .padding(Spacing.sm)
                            .size(18.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        subtitle,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.v369_collapse_format, title) else stringResource(R.string.v369_expand_format, title),
                    tint = Mocha.Subtext0,
                    modifier = Modifier.size(22.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { body() }
            }
        }
    }
}

@Composable
private fun TinyButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (enabled) Mocha.Mauve.copy(alpha = 0.10f) else Color.Transparent,
            contentColor = Mocha.Mauve,
            disabledContentColor = Mocha.Overlay0
        ),
        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun karaokeStyleLabel(style: KaraokeCaptionEngine.KaraokeStyle): String = when (style) {
    KaraokeCaptionEngine.KaraokeStyle.MRBEAST -> stringResource(R.string.v369_karaoke_mrbeast)
    KaraokeCaptionEngine.KaraokeStyle.SUBWAY -> stringResource(R.string.v369_karaoke_subway)
    KaraokeCaptionEngine.KaraokeStyle.HORMOZI -> stringResource(R.string.v369_karaoke_hormozi)
    KaraokeCaptionEngine.KaraokeStyle.TIKTOK_WHITE -> stringResource(R.string.v369_karaoke_tiktok_white)
    KaraokeCaptionEngine.KaraokeStyle.POP_SCALE -> stringResource(R.string.v369_karaoke_pop_scale)
    KaraokeCaptionEngine.KaraokeStyle.TYPEWRITER -> stringResource(R.string.v369_karaoke_typewriter)
    KaraokeCaptionEngine.KaraokeStyle.NEON -> stringResource(R.string.v369_karaoke_neon)
    KaraokeCaptionEngine.KaraokeStyle.MINIMAL -> stringResource(R.string.v369_karaoke_minimal)
}

@Composable
private fun publishTargetLabel(target: DirectPublishEngine.Target): String = when (target) {
    DirectPublishEngine.Target.YOUTUBE -> stringResource(R.string.v369_target_youtube)
    DirectPublishEngine.Target.TIKTOK -> stringResource(R.string.v369_target_tiktok)
    DirectPublishEngine.Target.INSTAGRAM -> stringResource(R.string.v369_target_instagram)
    DirectPublishEngine.Target.THREADS -> stringResource(R.string.v369_target_threads)
    DirectPublishEngine.Target.TWITTER_X -> stringResource(R.string.v369_target_x)
    DirectPublishEngine.Target.LINKEDIN -> stringResource(R.string.v369_target_linkedin)
}

@Composable
private fun colorBlindModeLabel(mode: ColorBlindPreviewEngine.Mode): String = when (mode) {
    ColorBlindPreviewEngine.Mode.OFF -> stringResource(R.string.v369_cb_off)
    ColorBlindPreviewEngine.Mode.DEUTERANOPIA -> stringResource(R.string.v369_cb_deuteranopia)
    ColorBlindPreviewEngine.Mode.PROTANOPIA -> stringResource(R.string.v369_cb_protanopia)
    ColorBlindPreviewEngine.Mode.TRITANOPIA -> stringResource(R.string.v369_cb_tritanopia)
    ColorBlindPreviewEngine.Mode.ACHROMATOPSIA -> stringResource(R.string.v369_cb_achromatopsia)
}

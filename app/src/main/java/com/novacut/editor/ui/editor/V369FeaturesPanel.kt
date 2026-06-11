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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.novacut.editor.engine.ColorBlindPreviewEngine
import com.novacut.editor.engine.DirectPublishEngine
import com.novacut.editor.engine.KaraokeCaptionEngine
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutFilterChip
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
        title = "Creator Intelligence",
        subtitle = "Speech edits, safety checks, publishing prep, and adaptive controls.",
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
            title = "Text-Based Editing",
            subtitle = "Delete transcript words and ripple-cut the matching timeline ranges.",
            accent = Mocha.Blue,
            icon = Icons.AutoMirrored.Filled.Subject
        ) {
            val transcript = v.transcript
            Text(
                if (transcript == null) "Run Auto Captions first to enable text-based edits."
                else "${transcript.words.size} words available; ${v.selectedWordIndices.size} selected.",
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyButton("Select fillers", enabled = transcript != null) {
                    viewModel.v369Delegate.selectFillerWords()
                }
                TinyButton("Apply cuts", enabled = hasClip && v.selectedWordIndices.isNotEmpty()) {
                    state.selectedClipId?.let { viewModel.v369Delegate.applyDeletions(it) }
                }
            }
        }

        // 2. Auto-chapter
        FeatureCard(
            title = "Auto-Chapters",
            subtitle = "Detect natural sections and prepare a platform-ready chapter list.",
            accent = Mocha.Green,
            icon = Icons.AutoMirrored.Filled.ViewList
        ) {
            Text(
                "${v.chapterCandidates.size} candidate chapter${if (v.chapterCandidates.size == 1) "" else "s"} ready.",
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyButton("Detect", enabled = v.transcript != null && !v.isGeneratingChapters) {
                    viewModel.v369Delegate.generateChapters(v.transcript?.words ?: emptyList())
                }
                TinyButton("Apply", enabled = v.chapterCandidates.isNotEmpty()) {
                    viewModel.v369Delegate.applyChaptersToProject()
                }
            }
        }

        // 3. Talking-head framing
        FeatureCard(
            title = "Talking-Head Framing",
            subtitle = "Create smooth face-aware position keyframes for talking-head edits.",
            accent = Mocha.Sapphire,
            icon = Icons.Default.Face
        ) {
            if (v.isTrackingFaces) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Sapphire)
            TinyButton("Track selected clip", enabled = hasClip && !v.isTrackingFaces) {
                state.selectedClipId?.let { viewModel.v369Delegate.trackTalkingHead(it) }
            }
        }

        // 4. Karaoke captions
        FeatureCard(
            title = "Karaoke Captions",
            subtitle = "Generate word-timed captions with concise social-video styles.",
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
                    NovaCutFilterChip(
                        selected = v.karaokeStyle == style,
                        onClick = { viewModel.v369Delegate.setKaraokeStyle(style) },
                        text = style.displayName,
                        accent = Mocha.Yellow
                    )
                }
            }
            TinyButton("Generate captions", enabled = v.transcript != null) {
                viewModel.v369Delegate.generateKaraokeCaptions()
            }
        }

        // 5. Stream-copy export
        FeatureCard(
            title = "Stream-Copy Export",
            subtitle = "Confirm when a trim can export losslessly without re-encoding.",
            accent = Mocha.Teal,
            icon = Icons.Default.Speed
        ) {
            val elig = v.streamCopyEligibility
            val label = when {
                elig == null -> "Eligibility has not been checked yet."
                elig.eligible -> "Eligible for fast lossless export."
                else -> "Re-encode required: ${elig.reason}"
            }
            val color = when {
                elig == null -> Mocha.Subtext1
                elig.eligible -> Mocha.Green
                else -> Mocha.Peach
            }
            Text(label, color = color, style = MaterialTheme.typography.bodySmall)
            Text(
                "Untouched single-source trims can use the fast path; complex timelines export through the normal render pipeline.",
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
            TinyButton("Check eligibility") {
                viewModel.v369Delegate.checkStreamCopyEligibility()
            }
        }

        // 6. Content-ID / AcoustID
        FeatureCard(
            title = "Content-ID Pre-check",
            subtitle = "Fingerprint the last export before upload for copyright-risk review.",
            accent = Mocha.Red,
            icon = Icons.Default.Copyright
        ) {
            val result = v.contentIdResult
            if (result != null) {
                val txt = if (result.matchedTitle != null) "Match: ${result.matchedTitle}"
                else "Local hash: ${result.hash.take(16)}..."
                Text(txt, color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Local hashing works now. Online lookup can be enabled when the fingerprint service is configured.",
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
            TinyButton("Check last export", enabled = state.lastExportedFilePath != null) {
                viewModel.v369Delegate.runContentIdOnLastExport()
            }
        }

        // 7. Direct publish
        FeatureCard(
            title = "Direct Publish",
            subtitle = "Prepare the last export for platform handoff with a clean title.",
            accent = Mocha.Pink,
            icon = Icons.Default.Share
        ) {
            var title by rememberSaveable(state.project.id) { mutableStateOf(state.project.name) }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") },
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
                        label = { Text(target.displayName, style = MaterialTheme.typography.labelMedium) },
                        enabled = hasExport
                    )
                }
            }
        }

        // 8. Flash safety
        FeatureCard(
            title = "Flash Safety (WCAG)",
            subtitle = "Scan high-contrast flash patterns before publishing.",
            accent = Mocha.Peach,
            icon = Icons.Default.FlashOn
        ) {
            if (v.isAnalyzingFlashes) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Peach)
            if (v.flashWarnings.isNotEmpty()) {
                Text(
                    "${v.flashWarnings.size} risky segment${if (v.flashWarnings.size == 1) "" else "s"}",
                    color = Mocha.Peach,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TinyButton("Scan selected clip", enabled = hasClip && !v.isAnalyzingFlashes) {
                state.selectedClipId?.let { viewModel.v369Delegate.analyzeFlashSafety(it) }
            }
        }

        // 9. Color-blind preview
        FeatureCard(
            title = "Color-Blind Preview",
            subtitle = "Preview accessibility simulations without altering the export.",
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
                    NovaCutFilterChip(
                        selected = v.colorBlindMode == mode,
                        onClick = { viewModel.v369Delegate.setColorBlindMode(mode) },
                        text = mode.displayName,
                        accent = Mocha.Mauve
                    )
                }
            }
            Text(
                "Preview mode is non-destructive and stays separate from final render settings.",
                color = Mocha.Overlay1,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // 10. AI thumbnail picker
        FeatureCard(
            title = "AI Thumbnail Picker",
            subtitle = "Score candidate frames for sharpness, faces, and composition.",
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
                                    contentDescription = "Candidate at ${cand.timeMs}ms",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Mocha.Sky.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                )
                                Text(
                                    "%.2f".format(cand.score),
                                    color = Mocha.Subtext0,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            TinyButton("Score clip frames", enabled = hasClip && !v.isScoringThumbnails) {
                state.selectedClipId?.let { viewModel.v369Delegate.scoreThumbnails(it) }
            }
        }

        // 11. HDR preservation (HDR10 / HDR10+ / HLG passthrough on HEVC/AV1/VP9)
        FeatureCard(
            title = "Preserve HDR on Export",
            subtitle = "Keep HDR metadata when the selected codec can carry it.",
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
                        !codecCanCarryHdr -> "Switch to HEVC/AV1/VP9 in Export to enable"
                        hdr -> "Enabled; export keeps HDR metadata."
                        else -> "Off; export tone-maps to SDR."
                    },
                    color = Mocha.Subtext1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 12. SDH / Audio description
        FeatureCard(
            title = "SDH + Audio Description",
            subtitle = "Prepare captions and description intent for accessible exports.",
            accent = Mocha.Maroon,
            icon = Icons.Default.Hearing
        ) {
            Text(
                "Use a transcript first; export-time audio-description generation is handled separately.",
                color = Mocha.Subtext1,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 13. DeX / desktop-mode
        FeatureCard(
            title = "DeX / Desktop Layout",
            subtitle = "Adapt the editor for DeX, Chromebook, large screens, and pointer workflows.",
            accent = Mocha.Lavender,
            icon = Icons.Default.DesktopWindows
        ) {
            val override by viewModel.desktopOverride.collectAsStateWithLifecycle()
            val active = LocalLayoutMode.current == LayoutMode.DESKTOP
            Text(
                if (active) "Desktop layout active" else "Phone layout",
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
                    NovaCutFilterChip(
                        selected = override == opt,
                        onClick = { viewModel.setDesktopOverride(opt) },
                        text = when (opt) {
                            com.novacut.editor.engine.DesktopOverride.AUTO -> "Auto"
                            com.novacut.editor.engine.DesktopOverride.FORCE_ON -> "Desktop"
                            com.novacut.editor.engine.DesktopOverride.FORCE_OFF -> "Phone"
                        },
                        accent = Mocha.Lavender
                    )
                }
            }
        }

        // 14. One-handed mode
        FeatureCard(
            title = "One-Handed Mode",
            subtitle = "Compact key controls into a thumb-friendly phone layout.",
            accent = Mocha.Flamingo,
            icon = Icons.Default.TouchApp
        ) {
            val one by viewModel.oneHandedMode.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = one, onCheckedChange = { viewModel.setOneHandedMode(it) })
                Spacer(Modifier.width(8.dp))
                Text(
                if (one) "Active; compact controls" else "Off",
                    color = Mocha.Subtext1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 15. S Pen + BT MIDI jog/shuttle
        FeatureCard(
            title = "S Pen + MIDI Jog/Shuttle",
            subtitle = "Connect precision input for keyframes, transport, and shuttle review.",
            accent = Mocha.Flamingo,
            icon = Icons.Default.Tune
        ) {
            TinyButton("Connect first MIDI device") {
                val ok = viewModel.v369Delegate.stylusMidi.connectFirstAvailable()
                viewModel.showToast(if (ok) "Scanning for MIDI controller" else "No MIDI device found")
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
            label = "Clip",
            value = if (hasClip) "Selected" else "Choose one",
            accent = if (hasClip) Mocha.Green else Mocha.Peach,
            icon = Icons.Default.Movie
        )
        ReadinessCard(
            label = "Transcript",
            value = if (hasTranscript) "Ready" else "Run captions",
            accent = if (hasTranscript) Mocha.Green else Mocha.Sapphire,
            icon = Icons.Default.ClosedCaption
        )
        ReadinessCard(
            label = "Export",
            value = if (hasExport) "Available" else "Render first",
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
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
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

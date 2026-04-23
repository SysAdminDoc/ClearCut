package com.novacut.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.novacut.editor.engine.ColorBlindPreviewEngine
import com.novacut.editor.engine.DirectPublishEngine
import com.novacut.editor.engine.KaraokeCaptionEngine
import com.novacut.editor.ui.theme.Mocha

/**
 * v3.69 feature hub — a single scrollable sheet that groups all 15 new
 * features as collapsible cards. Keeps the tool-panel surface compact
 * instead of scattering 10 new PanelIds across the bottom tab bar. Each
 * card dispatches directly into V369Delegate via the ViewModel.
 *
 * UX invariants:
 *   * Only the card HEADER is clickable-to-expand — child controls (buttons,
 *     chips, text fields) never double-trigger the toggle.
 *   * Every chip row is horizontally scrollable so narrow phones don't
 *     truncate options.
 *   * Features whose backing pipeline is not yet wired surface a dimmed
 *     "pipeline pending" note instead of a dead toggle that pretends to work.
 */
@Composable
fun V369FeaturesPanel(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val v = state.v369
    val hasClip = state.selectedClipId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mocha.Panel)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = Mocha.Mauve, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("v3.69 Features", color = Mocha.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0)
            }
        }
        Text(
            "Borrows from CapCut Script Editor, Descript, Submagic, LosslessCut, DaVinci match, Harding flash safety.",
            color = Mocha.Subtext0, fontSize = 12.sp
        )

        // 1. Text-based editing
        FeatureCard(
            title = "Text-Based Editing",
            subtitle = "Delete words → delete clip ranges (needs transcript from AI → Auto Captions)",
            accent = Mocha.Blue,
            icon = Icons.AutoMirrored.Filled.Subject
        ) {
            val transcript = v.transcript
            Text(
                if (transcript == null) "Run Auto Captions first to populate a transcript."
                else "${transcript.words.size} words, ${v.selectedWordIndices.size} selected",
                color = Mocha.Subtext1, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyButton("Strip fillers", enabled = transcript != null) {
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
            subtitle = "TextTiling segmentation + YouTube description block",
            accent = Mocha.Green,
            icon = Icons.AutoMirrored.Filled.ViewList
        ) {
            Text("${v.chapterCandidates.size} candidate chapters", color = Mocha.Subtext1, fontSize = 12.sp)
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
            subtitle = "Face tracking + one-euro smoothing → position keyframes",
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
            subtitle = "Word-pop animated captions (MrBeast / Subway / Hormozi / …)",
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
                    FilterChip(
                        selected = v.karaokeStyle == style,
                        onClick = { viewModel.v369Delegate.setKaraokeStyle(style) },
                        label = { Text(style.displayName, fontSize = 10.sp) }
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
            subtitle = "LosslessCut-style fast trim when eligible (50× faster)",
            accent = Mocha.Teal,
            icon = Icons.Default.Speed
        ) {
            val elig = v.streamCopyEligibility
            val label = when {
                elig == null -> "Not checked yet"
                elig.eligible -> "ELIGIBLE — tap Export"
                else -> "Re-encode needed: ${elig.reason}"
            }
            val color = when {
                elig == null -> Mocha.Subtext1
                elig.eligible -> Mocha.Green
                else -> Mocha.Peach
            }
            Text(label, color = color, fontSize = 12.sp)
            Text(
                "Stream-copy mux lights up with the FFmpegX dependency (Tier A.9).",
                color = Mocha.Overlay1, fontSize = 10.sp
            )
            TinyButton("Check eligibility") {
                viewModel.v369Delegate.checkStreamCopyEligibility()
            }
        }

        // 6. Content-ID / AcoustID
        FeatureCard(
            title = "Content-ID Pre-check",
            subtitle = "Copyright fingerprint before upload (energy-envelope hash)",
            accent = Mocha.Red,
            icon = Icons.Default.Copyright
        ) {
            val result = v.contentIdResult
            if (result != null) {
                val txt = if (result.matchedTitle != null) "Match: ${result.matchedTitle}"
                else "Hash: ${result.hash.take(16)}…"
                Text(txt, color = Mocha.Subtext1, fontSize = 12.sp)
            }
            Text(
                "AcoustID lookup requires Chromaprint NDK (pending) — today the hash is computed locally.",
                color = Mocha.Overlay1, fontSize = 10.sp
            )
            TinyButton("Fingerprint last export", enabled = state.lastExportedFilePath != null) {
                viewModel.v369Delegate.runContentIdOnLastExport()
            }
        }

        // 7. Direct publish
        FeatureCard(
            title = "Direct Publish",
            subtitle = "Send the last export to YouTube / TikTok / IG / Threads / X / LinkedIn",
            accent = Mocha.Pink,
            icon = Icons.Default.Share
        ) {
            var title by rememberSaveable(state.project.id) { mutableStateOf(state.project.name) }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
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
                        label = { Text(target.displayName, fontSize = 10.sp) },
                        enabled = hasExport
                    )
                }
            }
        }

        // 8. Flash safety
        FeatureCard(
            title = "Flash Safety (WCAG)",
            subtitle = "Detect strobe segments that could trigger seizures",
            accent = Mocha.Peach,
            icon = Icons.Default.FlashOn
        ) {
            if (v.isAnalyzingFlashes) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Peach)
            if (v.flashWarnings.isNotEmpty()) {
                Text(
                    "${v.flashWarnings.size} risky segment${if (v.flashWarnings.size == 1) "" else "s"}",
                    color = Mocha.Peach, fontSize = 12.sp
                )
            }
            TinyButton("Scan selected clip", enabled = hasClip && !v.isAnalyzingFlashes) {
                state.selectedClipId?.let { viewModel.v369Delegate.analyzeFlashSafety(it) }
            }
        }

        // 9. Color-blind preview
        FeatureCard(
            title = "Color-Blind Preview",
            subtitle = "Brettel/Viénot simulation — preview only, never exported",
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
                    FilterChip(
                        selected = v.colorBlindMode == mode,
                        onClick = { viewModel.v369Delegate.setColorBlindMode(mode) },
                        label = { Text(mode.displayName, fontSize = 10.sp) }
                    )
                }
            }
            Text(
                "Mode is recorded; the GL preview pass is injected in v3.70.",
                color = Mocha.Overlay1, fontSize = 10.sp
            )
        }

        // 10. AI thumbnail picker
        FeatureCard(
            title = "AI Thumbnail Picker",
            subtitle = "Score frames by sharpness / faces / rule-of-thirds",
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
                                    color = Mocha.Subtext0, fontSize = 10.sp
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
            subtitle = "Keep HDR metadata through the encoder (HEVC/AV1/VP9 only; H.264 is SDR)",
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
                        hdr -> "Enabled — HDR_MODE_KEEP_HDR"
                        else -> "Off — tone-map to SDR"
                    },
                    color = Mocha.Subtext1, fontSize = 12.sp
                )
            }
        }

        // 12. SDH / Audio description
        FeatureCard(
            title = "SDH + Audio Description",
            subtitle = "Bracketed non-speech tags + AD track stub (YAMNet planned)",
            accent = Mocha.Maroon,
            icon = Icons.Default.Hearing
        ) {
            Text("Requires transcript + enabled on export pass.", color = Mocha.Subtext1, fontSize = 12.sp)
        }

        // 13. DeX / desktop-mode
        FeatureCard(
            title = "DeX / Desktop Layout",
            subtitle = "Auto-detects Samsung DeX, Chromebook, or large-screen + mouse",
            accent = Mocha.Lavender,
            icon = Icons.Default.DesktopWindows
        ) {
            val override by viewModel.desktopOverride.collectAsStateWithLifecycle()
            val active = LocalLayoutMode.current == LayoutMode.DESKTOP
            Text(
                if (active) "Desktop layout active" else "Phone layout",
                color = if (active) Mocha.Green else Mocha.Subtext1, fontSize = 12.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (opt in com.novacut.editor.engine.DesktopOverride.entries) {
                    FilterChip(
                        selected = override == opt,
                        onClick = { viewModel.setDesktopOverride(opt) },
                        label = {
                            Text(
                                when (opt) {
                                    com.novacut.editor.engine.DesktopOverride.AUTO -> "Auto"
                                    com.novacut.editor.engine.DesktopOverride.FORCE_ON -> "Always desktop"
                                    com.novacut.editor.engine.DesktopOverride.FORCE_OFF -> "Always phone"
                                },
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }
        }

        // 14. One-handed mode
        FeatureCard(
            title = "One-Handed Mode",
            subtitle = "Thumb-zone compact toolbar on phones <600dp",
            accent = Mocha.Flamingo,
            icon = Icons.Default.TouchApp
        ) {
            val one by viewModel.oneHandedMode.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = one, onCheckedChange = { viewModel.setOneHandedMode(it) })
                Spacer(Modifier.width(8.dp))
                Text(
                    if (one) "Active — compact controls" else "Off",
                    color = Mocha.Subtext1, fontSize = 12.sp
                )
            }
        }

        // 15. S Pen + BT MIDI jog/shuttle
        FeatureCard(
            title = "S Pen + MIDI Jog/Shuttle",
            subtitle = "Stylus pressure for keyframe curves; BT MIDI CC maps to transport",
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

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    body: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Mocha.PanelRaised)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header-only clickable so body controls never double-trigger the toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Mocha.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Mocha.Subtext0, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = Mocha.Subtext0
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
    }
}

@Composable
private fun TinyButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = Mocha.Mauve,
            disabledContentColor = Mocha.Overlay0
        )
    ) { Text(label, fontSize = 12.sp) }
}

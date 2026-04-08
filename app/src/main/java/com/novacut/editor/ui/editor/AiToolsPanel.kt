package com.novacut.editor.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.engine.segmentation.SegmentationModelState
import com.novacut.editor.engine.whisper.WhisperModelState
import com.novacut.editor.ui.theme.Mocha

data class AiToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val requiresClip: Boolean = true
)

val aiTools = listOf(
    AiToolConfig(
        "auto_captions",
        "Auto Captions",
        "Generate subtitles from speech",
        Icons.Default.ClosedCaption,
        Mocha.Blue
    ),
    AiToolConfig(
        "remove_bg",
        "Remove BG",
        "Remove video background",
        Icons.Default.Wallpaper,
        Mocha.Green
    ),
    AiToolConfig(
        "scene_detect",
        "Scene Detect",
        "Auto-detect scene changes",
        Icons.Default.ContentCut,
        Mocha.Peach
    ),
    AiToolConfig(
        "track_motion",
        "Track Motion",
        "Track objects across frames",
        Icons.Default.GpsFixed,
        Mocha.Mauve
    ),
    AiToolConfig(
        "smart_crop",
        "Smart Crop",
        "AI-powered framing",
        Icons.Default.Crop,
        Mocha.Teal
    ),
    AiToolConfig(
        "auto_color",
        "Auto Color",
        "AI color correction",
        Icons.Default.Palette,
        Mocha.Yellow
    ),
    AiToolConfig(
        "stabilize",
        "Stabilize",
        "Reduce camera shake",
        Icons.Default.Straighten,
        Mocha.Sapphire
    ),
    AiToolConfig(
        "denoise",
        "Denoise Audio",
        "Remove background noise",
        Icons.AutoMirrored.Filled.VolumeOff,
        Mocha.Flamingo
    ),
    AiToolConfig(
        "video_upscale",
        "AI Upscale",
        "Upscale video with Real-ESRGAN",
        Icons.Default.ZoomIn,
        Mocha.Rosewater
    ),
    AiToolConfig(
        "ai_background",
        "AI Background",
        "AI green screen with RVM matting",
        Icons.Default.PhotoFilter,
        Mocha.Lavender
    ),
    AiToolConfig(
        "ai_stabilize",
        "AI Stabilize",
        "OpenCV optical flow stabilization",
        Icons.Default.Straighten,
        Mocha.Sky
    ),
    AiToolConfig(
        "ai_style_transfer",
        "Style Transfer",
        "AnimeGAN / Neural Style Transfer",
        Icons.Default.Style,
        Mocha.Maroon
    )
)

@Composable
fun AiToolsPanel(
    hasSelectedClip: Boolean,
    onToolSelected: (String) -> Unit,
    onDisabledToolTapped: (String) -> Unit = {},
    onCancelProcessing: () -> Unit = {},
    onClose: () -> Unit,
    processingTool: String? = null,
    whisperModelState: WhisperModelState = WhisperModelState.NOT_DOWNLOADED,
    whisperDownloadProgress: Float = 0f,
    onDownloadWhisper: () -> Unit = {},
    onDeleteWhisper: () -> Unit = {},
    segmentationModelState: SegmentationModelState = SegmentationModelState.NOT_DOWNLOADED,
    segmentationDownloadProgress: Float = 0f,
    onDownloadSegmentation: () -> Unit = {},
    onDeleteSegmentation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val readyTools = aiTools.count { !it.requiresClip || hasSelectedClip }

    PremiumEditorPanel(
        title = stringResource(R.string.ai_tools_title),
        subtitle = "Stage on-device assists, model downloads, and clip-aware magic.",
        icon = Icons.Default.AutoAwesome,
        accent = Mocha.Mauve,
        onClose = onClose,
        modifier = modifier,
        scrollable = true
    ) {
        PremiumPanelCard(accent = Mocha.Mauve) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Creative stack",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasSelectedClip) {
                            "Your selected clip is ready for captioning, cleanup, reframing, and enhancement."
                        } else {
                            "Most tools unlock once a clip is selected, so the panel explains what is ready now and what needs media."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumPanelPill(
                        text = "$readyTools tools ready",
                        accent = Mocha.Mauve
                    )
                    PremiumPanelPill(
                        text = if (hasSelectedClip) "Clip selected" else "Awaiting clip",
                        accent = if (hasSelectedClip) Mocha.Green else Mocha.Peach
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ModelStatusCard(
            accent = modelAccent(whisperModelState),
            icon = Icons.Default.RecordVoiceOver,
            title = when (whisperModelState) {
                WhisperModelState.READY -> stringResource(R.string.ai_whisper_ready)
                WhisperModelState.DOWNLOADING -> stringResource(R.string.ai_downloading_model)
                WhisperModelState.ERROR -> stringResource(R.string.ai_download_failed)
                else -> stringResource(R.string.ai_whisper_size)
            },
            description = when (whisperModelState) {
                WhisperModelState.READY -> stringResource(R.string.ai_whisper_active)
                WhisperModelState.NOT_DOWNLOADED -> stringResource(R.string.ai_whisper_description)
                WhisperModelState.ERROR -> "Retry to restore captioning, voice analysis, and speech-driven tools."
                WhisperModelState.DOWNLOADING -> "Downloading the speech-to-text model for on-device transcription."
            },
            progress = if (whisperModelState == WhisperModelState.DOWNLOADING) whisperDownloadProgress else null,
            primaryActionLabel = when (whisperModelState) {
                WhisperModelState.NOT_DOWNLOADED, WhisperModelState.ERROR -> stringResource(R.string.get)
                else -> null
            },
            onPrimaryAction = when (whisperModelState) {
                WhisperModelState.NOT_DOWNLOADED, WhisperModelState.ERROR -> onDownloadWhisper
                else -> null
            },
            secondaryActionLabel = if (whisperModelState == WhisperModelState.READY) stringResource(R.string.remove) else null,
            onSecondaryAction = if (whisperModelState == WhisperModelState.READY) onDeleteWhisper else null
        )

        Spacer(modifier = Modifier.height(12.dp))

        ModelStatusCard(
            accent = segmentationAccent(segmentationModelState),
            icon = Icons.Default.PersonOff,
            title = when (segmentationModelState) {
                SegmentationModelState.READY -> stringResource(R.string.ai_segmentation_ready)
                SegmentationModelState.DOWNLOADING -> stringResource(R.string.ai_downloading_model)
                SegmentationModelState.ERROR -> stringResource(R.string.ai_download_failed)
                else -> stringResource(R.string.ai_segmentation_size)
            },
            description = when (segmentationModelState) {
                SegmentationModelState.READY -> "Background-aware tools are armed for matte extraction and smart composites."
                SegmentationModelState.NOT_DOWNLOADED -> stringResource(R.string.ai_segmentation_description)
                SegmentationModelState.ERROR -> "Retry to restore background removal and AI compositing tools."
                SegmentationModelState.DOWNLOADING -> "Downloading the segmentation model for on-device background isolation."
            },
            progress = if (segmentationModelState == SegmentationModelState.DOWNLOADING) segmentationDownloadProgress else null,
            primaryActionLabel = when (segmentationModelState) {
                SegmentationModelState.NOT_DOWNLOADED, SegmentationModelState.ERROR -> stringResource(R.string.get)
                else -> null
            },
            onPrimaryAction = when (segmentationModelState) {
                SegmentationModelState.NOT_DOWNLOADED, SegmentationModelState.ERROR -> onDownloadSegmentation
                else -> null
            },
            secondaryActionLabel = if (segmentationModelState == SegmentationModelState.READY) stringResource(R.string.remove) else null,
            onSecondaryAction = if (segmentationModelState == SegmentationModelState.READY) onDeleteSegmentation else null
        )

        if (processingTool != null) {
            Spacer(modifier = Modifier.height(12.dp))

            PremiumPanelCard(accent = Mocha.Mauve) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Mocha.Mauve,
                        strokeWidth = 2.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Processing now",
                            style = MaterialTheme.typography.titleSmall,
                            color = Mocha.Text
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.ai_processing_format,
                                aiTools.find { it.id == processingTool }?.name ?: processingTool
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mocha.Subtext0
                        )
                    }
                    TextButton(onClick = onCancelProcessing) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Mocha.Red
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Creative assists",
            style = MaterialTheme.typography.titleMedium,
            color = Mocha.Text
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Each tool calls out whether it is ready to run now or waiting on a selected clip.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mocha.Subtext0
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(aiTools) { tool ->
                val isEnabled = !tool.requiresClip || hasSelectedClip
                val isProcessing = processingTool == tool.id

                AiToolCard(
                    tool = tool,
                    isEnabled = isEnabled,
                    isProcessing = isProcessing,
                    onClick = {
                        when {
                            isProcessing -> Unit
                            !isEnabled -> onDisabledToolTapped(tool.name)
                            else -> onToolSelected(tool.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    accent: Color,
    icon: ImageVector,
    title: String,
    description: String,
    progress: Float?,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?
) {
    PremiumPanelCard(accent = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Mocha.Text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0
                    )
                }
            }

            if (progress == null) {
                val actionLabel = primaryActionLabel ?: secondaryActionLabel
                val action = onPrimaryAction ?: onSecondaryAction
                if (actionLabel != null && action != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = action,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.18f),
                            contentColor = accent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = accent,
                trackColor = Mocha.Surface1
            )
        }

        if (progress == null && primaryActionLabel != null && secondaryActionLabel != null && onPrimaryAction != null && onSecondaryAction != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.copy(alpha = 0.18f),
                        contentColor = accent
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(primaryActionLabel)
                }

                TextButton(onClick = onSecondaryAction) {
                    Text(
                        text = secondaryActionLabel,
                        color = Mocha.Subtext0
                    )
                }
            }
        }
    }
}

@Composable
private fun AiToolCard(
    tool: AiToolConfig,
    isEnabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(168.dp)
            .height(170.dp)
            .clickable(onClick = onClick),
        color = if (isEnabled) Mocha.PanelHighest else Mocha.PanelRaised.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            when {
                isProcessing -> tool.color.copy(alpha = 0.55f)
                isEnabled -> Mocha.CardStrokeStrong
                else -> Mocha.CardStroke
            }
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        tool.color.copy(alpha = if (isEnabled) 0.16f else 0.08f),
                        Mocha.PanelHighest,
                        Mocha.PanelRaised
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = tool.color.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, tool.color.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = tool.color,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.name,
                                    tint = if (isEnabled) tool.color else Mocha.Surface2,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    PremiumPanelPill(
                        text = when {
                            isProcessing -> "Running"
                            isEnabled -> "Ready"
                            else -> "Clip required"
                        },
                        accent = when {
                            isProcessing -> tool.color
                            isEnabled -> Mocha.Green
                            else -> Mocha.Peach
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isEnabled) Mocha.Text else Mocha.Subtext0
                    )
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) Mocha.Subtext0 else Mocha.Overlay1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = if (isEnabled) "Tap to stage or run this assist." else "Select a clip to unlock this workflow.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) tool.color else Mocha.Overlay1,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

private fun modelAccent(state: WhisperModelState): Color = when (state) {
    WhisperModelState.READY -> Mocha.Blue
    WhisperModelState.DOWNLOADING -> Mocha.Yellow
    WhisperModelState.ERROR -> Mocha.Red
    WhisperModelState.NOT_DOWNLOADED -> Mocha.Surface2
}

private fun segmentationAccent(state: SegmentationModelState): Color = when (state) {
    SegmentationModelState.READY -> Mocha.Green
    SegmentationModelState.DOWNLOADING -> Mocha.Yellow
    SegmentationModelState.ERROR -> Mocha.Red
    SegmentationModelState.NOT_DOWNLOADED -> Mocha.Surface2
}

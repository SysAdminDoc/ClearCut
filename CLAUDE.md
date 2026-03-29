# NovaCut - Android Video Editor

## Overview
Full-featured Android video editor built as a PowerDirector alternative. Kotlin + Jetpack Compose + Media3 Transformer.

## Version
v3.15.0

## Tech Stack
- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose (Material 3, Catppuccin Mocha theme)
- **Video Engine**: Media3 Transformer 1.9.2 + ExoPlayer
- **Effects**: OpenGL ES 3.0 GLSL shaders (via Media3 RgbMatrix + custom GlEffect)
- **Audio**: MediaCodec PCM decode, waveform extraction, mixing engine
- **DI**: Hilt/Dagger
- **DB**: Room (project persistence)
- **Navigation**: Compose Navigation (projects list -> editor)
- **Architecture**: MVVM + single-activity Compose navigation

## Build
- Android Studio Ladybug+ / AGP 8.7.3 / Gradle 8.9
- Min SDK 26, Target SDK 35, Compile SDK 35
- `JAVA_HOME='C:\Program Files\Android\Android Studio\jbr' ./gradlew assembleDebug`
- Needs `local.properties` with `sdk.dir` pointing to Android SDK

## Key Files
- `app/src/main/java/com/novacut/editor/`
  - `MainActivity.kt` - Single activity with NavHost (projects -> editor/{projectId})
  - `ui/projects/ProjectListScreen.kt` - Project gallery with search, sort, create/delete/duplicate/open
  - `ui/projects/ProjectListViewModel.kt` - Project list state management (search, sort, duplicate)
  - `ui/editor/EditorScreen.kt` - Main editor composable (EditorTopBar + preview + timeline + BottomToolArea) with onAction dispatch
  - `ui/editor/EditorViewModel.kt` - Editor state management (tracks, clips, effects, undo/redo, voiceover, loop). Delegates heavy logic to:
    - `ui/editor/StateFlowExt.kt` - Shared MutableStateFlow.update() CAS-loop extension (used by all delegates)
    - `ui/editor/ClipEditingDelegate.kt` - Clip add/delete/duplicate/merge/split/trim/speed/reverse/reorder/move-to-track
    - `ui/editor/EffectsDelegate.kt` - Effect add/update/toggle/remove/copy/paste, transitions
    - `ui/editor/OverlayDelegate.kt` - Text overlays, image/sticker overlays, timeline markers
    - `ui/editor/ExportDelegate.kt` - Export, batch export, render preview, share, save-to-gallery
    - `ui/editor/AudioMixerDelegate.kt` - Audio mixer, track volume/pan/solo, audio effects, beat detection, normalization
    - `ui/editor/ColorGradingDelegate.kt` - Color grading, LUT import, clip color grade
    - `ui/editor/AiToolsDelegate.kt` - AI tool dispatch, model downloads, ML engine wrappers
  - `ui/editor/Timeline.kt` - Custom multi-track timeline with thumbnail strips + waveforms + trim handles + keyframe dots + effect badges + trim mode indicator
  - `ui/editor/PreviewPanel.kt` - ExoPlayer-based video preview with playback controls + loop toggle
  - `ui/editor/ToolPanel.kt` - PowerDirector-style BottomToolArea (two-mode tab bar + sub-menu grids) + effects/speed/transform/crop/transition panels
  - `ui/editor/TextEditorSheet.kt` - Text overlay editor with font selector, animations
  - `ui/editor/AudioPanel.kt` - Audio controls, waveform visualization with fade envelope overlay, voiceover recorder
  - `ui/editor/AiToolsPanel.kt` - AI tools UI (captions, bg removal, scene detect, smart crop, etc.)
  - `ui/editor/StickerPickerPanel.kt` - Sticker/emoji picker with 5 category tabs + custom import
  - `ui/mediapicker/MediaPicker.kt` - Media picker with Photo Picker (API 33+) + OpenDocument fallback + camera capture
  - `res/xml/file_paths.xml` - FileProvider paths config for camera capture + export sharing
  - `ui/export/ExportSheet.kt` - Export settings (resolution, codec, quality, progress, error with retry, share, save to gallery)
  - `engine/VideoEngine.kt` - Media3 Transformer export + ExoPlayer + thumbnail extraction (~670 lines)
  - `engine/EffectBuilder.kt` - Pure effect mapping: NovaCut model → Media3 Effect (buildVideoEffect, color grading, transitions, transforms)
  - `engine/VolumeAudioProcessor.kt` - Audio processor for volume/fade/keyframe envelope during export
  - `engine/ExportTextOverlay.kt` - Text overlay rendering with animation state machine for export
  - `engine/AudioEngine.kt` - Audio waveform extraction + PCM mixing
  - `engine/KeyframeEngine.kt` - Keyframe interpolation with 5 easing curves
  - `engine/ExportService.kt` - Foreground service for export notifications (MEDIA_PROCESSING type)
  - `engine/VoiceoverRecorder.kt` - MediaRecorder wrapper for voiceover recording
  - `engine/ProjectAutoSave.kt` - Periodic auto-save with full JSON serialization/deserialization
  - `engine/AppModule.kt` - Hilt DI module (Room DB + DAO)
  - `engine/db/ProjectDatabase.kt` - Room database (v3) + ProjectDao + converters
  - `engine/ShaderEffect.kt` - Custom GLSL shader framework (ShaderEffect/ShaderProgram/EffectShaders) for 14 visual effects + 25 transition shaders + color grading + blend modes + masks via BaseGlShaderProgram
  - `engine/AudioEffectsEngine.kt` - DSP audio effects (EQ, compressor, limiter, reverb, delay, chorus, flanger, de-esser, noise gate, pitch shift, normalizer, filters) + beat detection + VU metering
  - `engine/LutEngine.kt` - 3D LUT parser (.cube/.3dl) + GPU LUT application via 3D texture
  - `engine/ProxyEngine.kt` - Low-res proxy generation for smooth editing
  - `engine/whisper/WhisperEngine.kt` - Whisper tiny.en ONNX speech-to-text (model download, PCM decode, inference, token decode)
  - `engine/whisper/WhisperMel.kt` - 80-channel log-mel spectrogram (FFT, mel filterbank, Whisper preprocessing)
  - `engine/segmentation/SegmentationEngine.kt` - MediaPipe selfie segmenter (model download, per-frame segmentation)
  - `engine/segmentation/SegmentationGlEffect.kt` - Custom GlEffect for export pipeline (GL readback + segmentation + mask shader)
  - `engine/TemplateManager.kt` - User template save/load/delete (JSON files in filesDir/templates/)
  - `ui/projects/ProjectTemplateSheet.kt` - Template picker (preset + user templates grid)
  - `ui/editor/ColorGradingPanel.kt` - Lift/gamma/gain color wheels, RGB curves editor, HSL qualifier, LUT import
  - `ui/editor/AudioMixerPanel.kt` - Per-track faders, pan, mute/solo, VU meters, audio effect chain
  - `ui/editor/KeyframeCurveEditor.kt` - Bezier keyframe curve editor with property toggles, diamond handles, presets
  - `ui/editor/SpeedCurveEditor.kt` - Speed ramping with bezier curve editor + presets (ramp up/down, pulse)
  - `ui/editor/MaskEditorPanel.kt` - Freehand/rect/ellipse/gradient masks with feather, invert, motion tracking + preview overlay
  - `ui/editor/BlendModeSelector.kt` - 18 blend modes (multiply, screen, overlay, etc.)
  - `ui/export/BatchExportPanel.kt` - Platform presets (YouTube/TikTok/Instagram/etc), batch queue, audio-only/stems export
  - `ai/AiFeatures.kt` - AI features (auto captions, bg removal, scene detect, motion track, auto color, stabilize, denoise, style transfer, face track, upscale, frame interp, object remove, bg replace, smart reframe)
  - `model/Project.kt` - All data models (Project, Track, Clip, Effect, Transition, Keyframe, ColorGrade, Mask, SpeedCurve, AudioEffect, Caption, BlendMode, BatchExport, ProjectSnapshot, etc.)

## Architecture Decisions
- **ViewModel delegate pattern** — EditorViewModel (~3300 lines, down from 4170) decomposed into 7 plain-class delegates (ClipEditing, Effects, Overlay, Export, AudioMixer, ColorGrading, AiTools). Each receives `MutableStateFlow<EditorState>` + lambda refs to ViewModel internals (saveUndoState, showToast, etc.). Not Hilt ViewModels — just extracted behavior with shared state. ViewModel methods thin-delegate to these classes.
- **Immutable collections** in all models (List/Map, not MutableList/MutableMap) for safe undo/redo copy-on-write
- **Transformer.start() on Main thread** - Media3 Transformer requires a Looper, export runs withContext(Dispatchers.Main)
- **Multi-clip playback** via ExoPlayer setMediaItems() with ClippingConfiguration per clip
- **Player.Listener** syncs play/pause/end state; periodic coroutine syncs playhead at ~30fps. Tracked via `setPlayerListener()`/`removePlayerListener()` for proper lifecycle cleanup.
- **Auto-save** uses org.json serialization of full Track/Clip/Effect/Keyframe/TextOverlay model tree with safe deserialization (optString/optLong/safeValueOf — never crashes on missing or unknown values)
- **SavedStateHandle** for projectId in EditorViewModel, loaded from NavHost route arg
- **Panel mutual exclusion** — atomic `dismissedPanelState()` + show in single `_state.update` to prevent intermediate states. Voiceover panel included in dismissal.
- **Trim debounce** — `beginTrim()` saves undo once on drag start; `trimClip()` updates live without undo spam
- **Ripple delete** — clip deletion shifts subsequent clips back to close timeline gaps
- **Media type routing** — MediaPickerSheet passes media type string; audio routed to AUDIO track
- **rebuildPlayerTimeline()** — called after every clip mutation (add, delete, split, merge, trim, speed, reverse, undo, redo) to keep ExoPlayer in sync with visual timeline
- **VideoEngine is @Singleton** — ViewModel calls `removePlayerListener()` + `resetExportState()` in onCleared(), never `release()`. The engine outlives any individual ViewModel.
- **Thumbnail cache** — thread-safe LinkedHashMap with `cacheLock` synchronization and `removeEldestEntry` auto-eviction at 200 entries. `accessOrder=false` prevents ConcurrentModificationException.
- **AudioEngine PCM decode** — ShortArray chunks collected then concatenated via `System.arraycopy` to avoid boxing millions of Shorts through `MutableList<Short>`
- **Project thumbnails** — Stores first video clip's source URI in `Project.thumbnailUri`; Coil `VideoFrameDecoder` renders frame at display time (no file management needed)
- **Camera capture** — `ActivityResultContracts.CaptureVideo()` + FileProvider URI from cache dir. No CAMERA permission needed — delegated to system camera app via intent.
- **AI tools wiring** — `runAiTool(toolId)` dispatches to `AiFeatures` methods in viewModelScope. All 8 tools fully wired with else branch for unknown tool IDs. Scene detect auto-splits clips at boundaries (reverse-order). Auto captions detect speech via audio energy analysis and create TextOverlay entries. Smart crop uses saliency-weighted region analysis. Auto color analyzes frame histograms and applies brightness/contrast/saturation/temperature effects. Stabilize estimates motion vectors via block matching and applies counter-motion zoom + position keyframes. Denoise analyzes audio noise floor and adjusts volume/fade. Remove BG detects background color from edge pixels and applies chroma key. Track motion uses template matching across frames to generate position keyframes.
- **Audio fade persistence** — `fadeInMs`/`fadeOutMs` stored on Clip model (not local UI state). AudioPanel reads from clip data so values survive panel close/reopen. Serialized in ProjectAutoSave.
- **Duplicate clip** — copies clip with fresh UUIDs for clip + effects, inserts after original, shifts subsequent clips forward. Transition nulled on copy to avoid doubled transitions.
- **Merge clips** — merges selected clip with next adjacent clip from same source. Extends trimEndMs, combines effects, shifts subsequent clips back. Only works for same-source clips.
- **Copy/Paste effects** — `copiedEffects` in EditorState stores copied effect list. Paste creates fresh UUIDs to avoid ID collisions.
- **Freeze frame** — extracts JPEG via MediaMetadataRetriever at playhead, splits clip, inserts 2s still image clip between halves.
- **Share after export** — FileProvider URI + ACTION_SEND intent via `getShareIntent()`. Authority is `${applicationId}.fileprovider`. Requires `<external-files-path>` in file_paths.xml.
- **Save to gallery** — MediaStore API with IS_PENDING pattern (API 29+), direct file copy for API 26-28. No WRITE_EXTERNAL_STORAGE needed on API 29+.
- **Project search/sort** — `ProjectListViewModel` combines allProjects with searchQuery + sortMode flows. SortMode enum: DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, DURATION_DESC.
- **Project duplicate** — copies Room DB record + auto-save JSON file with fresh projectId and timestamp.
- **Social media crop presets** — CropPanel redesigned with platform labels (YouTube/TV, TikTok/Reels, Instagram Square/Portrait, Classic, Cinematic). Added RATIO_4_5 to AspectRatio enum.
- **PowerDirector-style layout** — EditorTopBar (Home/Undo/Redo/Delete/More/Export) + BottomToolArea with two-mode tab bar. Project mode: Edit/Audio/Text/Effects/AI/Aspect tabs. Clip mode: Back/Edit/Audio Tool/Speed/Transform/Effects/Transition/AI tabs. Tabs with sub-menus (Text, AI, clip-Edit) toggle SubMenuGrid overlays. Direct-action tabs dispatch via onAction string IDs to EditorScreen's when-block routing to ViewModel methods. Tab state resets on clip mode change via LaunchedEffect.
- **Voiceover recording** — VoiceoverRecorderEngine (MediaRecorder @Singleton) injected into EditorViewModel. Recording state tracked via StateFlow. Duration updated via polling coroutine (100ms). Recorded audio auto-added to AUDIO track as new clip. Cleanup in onCleared().
- **Loop playback** — ExoPlayer REPEAT_MODE_ALL/OFF toggled via PreviewPanel loop button. State persisted in EditorState.
- **Transition duration control** — Slider in TransitionPicker (100-2000ms, 100ms steps). Updates Transition.durationMs on selected clip.
- **Font selector** — TextEditorSheet offers 6 font families (sans-serif, serif, monospace, cursive, condensed, medium). Stored in TextOverlay.fontFamily.
- **Photo Picker (API 33+)** — PickVisualMedia/PickMultipleVisualMedia for video, image, and multi-select on Android 13+. Falls back to OpenDocument on older APIs.
- **Export error handling** — `exportErrorMessage: String?` in EditorState, displayed in ExportSheet with retry button. Error state set from both Transformer.Listener.onError and catch block. `showExportSheet()` resets error state.
- **Auto-save error logging** — `Log.e(TAG, ...)` on auto-save failures instead of silent `catch (_: Exception) {}`. Still non-crashing but now debuggable via logcat.
- **Project delete cleanup** — `ProjectListViewModel.deleteProject()` now calls `autoSave.clearRecoveryData()` to remove orphaned auto-save JSON files.
- **Waveform fade envelope** — AudioPanel Canvas draws fade in/out envelope overlay (stroke line + dimmed fill) on top of waveform visualization. Reads `fadeInMs`/`fadeOutMs` from clip model.
- **Trim mode indicator** — Timeline shows "TRIM MODE — Drag clip edges to adjust" banner (Mocha.Peach) when `currentTool == EditorTool.TRIM` and a clip is selected.
- **Timeline keyframe dots** — Clips with keyframes display small pink dots along the bottom edge at each keyframe's time position.
- **Timeline effects badge** — Clips with effects show "FX{n}" badge in top-right corner (Mocha.Mauve background).
- **AI disabled tool feedback** — `onDisabledToolTapped` callback on AiToolsPanel. Tapping a tool that requires clip selection shows "Select a clip to use {toolName}" toast. Card always clickable (removed Material3 `enabled=false` which blocked taps).

## Features Wired & Working
- Project gallery (create, open, delete, swipe-to-delete with confirm)
- Navigation: projects list -> editor/{projectId} -> back
- Multi-track timeline (video, audio, overlay, text tracks) with real thumbnails
- Multi-clip playback via ExoPlayer (setMediaItems with clipping)
- Interactive trim handles (drag to adjust in/out points)
- Split clip at playhead
- Merge clip with next adjacent clip (same-source only)
- 40+ video effects (color, filters, blur, distortion, keying) with adjustable parameters
- 25 transition types with GLSL shaders + adjustable duration (100-2000ms)
- Speed control (0.1x-100x) + reverse
- Loop playback toggle
- Keyframe opacity applied during export via RgbMatrix
- Text overlays with 10 animation styles + font family selector (6 fonts)
- Audio panel with volume, fade in/out (persisted on clip), waveform visualization with fade envelope overlay (requires clip selection)
- Voiceover recording (MediaRecorder, auto-added to audio track)
- Effects panel with add -> adjust flow (EffectsPanel -> EffectAdjustmentPanel)
- Transform panel: position X/Y, scale X/Y, rotation, opacity sliders with reset
- Crop panel: social media presets (YouTube/TV 16:9, TikTok/Reels 9:16, Instagram Square 1:1, Instagram Portrait 4:5, Classic 4:3, Portrait Classic 3:4, Cinematic 21:9) — project-level, no clip selection required
- PowerDirector-style UI: compact top bar + two-mode bottom tab bar with contextual sub-menu grids
- Disabled tool feedback: "Select a clip to use Effects/AI tools" toast when tapping disabled tools
- Duplicate clip (inserts copy after selected clip)
- Copy/Paste effects between clips
- Freeze frame (extract frame at playhead, insert as 2s still image)
- Share exported video (ACTION_SEND + FileProvider)
- Save exported video to device gallery (MediaStore)
- Project search (filter by name)
- Project sort (recent, oldest, A-Z, Z-A, longest)
- Duplicate project (Room + auto-save copy)
- AI tools: scene detection (auto-split at boundaries), auto captions (audio energy speech segmentation), smart crop (saliency analysis), auto color correction (histogram-based), video stabilization (motion vector compensation), audio denoise (noise floor analysis), background removal (chroma key auto-detect), motion tracking (template matching keyframes)
- Camera capture via system camera app (CaptureVideo intent)
- Photo Picker for Android 13+ (PickVisualMedia) with OpenDocument fallback
- Project thumbnails on gallery cards (Coil VideoFrameDecoder)
- Export: resolution, codec, bitrate from config; aspect-ratio-aware output dimensions; foreground service with MEDIA_PROCESSING type; error display with retry button; cancel via notification action
- Export notification live progress (ExportService observes VideoEngine StateFlows, updates notification in real-time)
- Export cancellation (notification Cancel button triggers Transformer.cancel(), CANCELLED state propagated)
- Export audio volume + fades (VolumeAudioProcessor applies clip volume/fadeIn/fadeOut to exported audio)
- Export CANCELLED state UI in ExportSheet (icon + message + Done button)
- Text overlay list/edit/delete UI (BottomToolArea text tab shows existing overlays with edit/delete buttons)
- Text overlay editing (TextEditorSheet opens with existing overlay data, save updates instead of creating new)
- Camera temp file cleanup (stale files older than 1 hour deleted on MediaPicker open)
- Export 20+ color/filter effects via RgbMatrix (tint, exposure, gamma, highlights, shadows, vibrance, posterize, cool/warm tone, cyberpunk, noir, vintage, mirror)
- Export clip transforms (rotation, scale, position via MatrixTransformation — static + keyframe-animated)
- Export keyframe-animated scale/rotation/position (per-frame MatrixTransformation with KeyframeEngine interpolation)
- Export keyframe-animated volume (VolumeAudioProcessor evaluates KeyframeEngine per audio sample)
- Export text overlay animations (10 types: fade, slide 4-way, scale, spin, bounce, typewriter — applied in/out)
- Export static clip opacity (RgbMatrix when no keyframe opacity override)
- Export audio track (background music, voiceovers mixed into output via second EditedMediaItemSequence)
- Export text overlays (timed OverlayEffect with styled SpannableString, position anchoring, per-frame alpha gating)
- R8 minification enabled with comprehensive ProGuard keep rules (~5MB APK)
- Undo/redo (50 levels, immutable state snapshots)
- Project auto-save every 30s with full state recovery (errors logged to logcat)
- Project delete cleans up orphaned auto-save files
- Timeline visual indicators: keyframe dots, effects badge, trim mode banner
- Timeline auto-scroll during playback (keeps playhead visible)
- Export progress with clamped percentage and human-readable bitrate descriptions
- Text overlay undo/redo support (add, edit, delete all undoable)
- Effect parameter bounds validation (brightness, contrast, saturation, temperature)
- Export error logging to logcat for debugging
- AI tool cancellation (cancel button on processing indicator)
- Keyframe deduplication (prevents division-by-zero on duplicate timestamps)
- Centralized effect default parameters (EffectType.defaultParams companion)
- SubMenuGrid scroll support for small screens
- Accessibility content descriptions on interactive icons
- Effect adjustment undo debounce (save once on drag start, not every tick)
- Text editor blank text guard (Save disabled when empty)
- Smart project duplicate naming (incremental "Copy N" suffix)
- Auto-save consecutive failure tracking with Log.w after 3 failures
- Unknown action dispatch logging (Log.w for debugging)
- Timeline trim handle division-by-zero guard
- Split validation before undo state (no-op splits don't pollute undo stack)
- Merge validation before undo state (same-source check before undo save)
- Transition duration undo debounce (save once on drag start)
- Waveform extraction error logging (Log.e on codec failure)
- Atomic auto-save copy for project duplication
- Fade envelope bounds guard for tiny clip durations
- AI tool null safety (all `clip!!` replaced with safe non-null val after unified guard)
- Trim bounds coerced to sourceDurationMs (prevents trimEndMs exceeding source)
- Split minimum duration validation (100ms minimum per half, toast feedback)
- Delete/duplicate undo pre-validation (confirm clip exists before saving undo state)
- Deserialization failure logging (Log.w on corrupt clips/effects/keyframes/overlays)
- Fade undo debounce (save once on drag start for fade in/out sliders)
- Fade bounds coercion (fadeIn + fadeOut cannot exceed clip duration)
- Share intent toast feedback (user-visible errors for missing export or deleted file)
- Export progress reset on error (0f on both Transformer.Listener.onError and catch block)
- Export uses project aspect ratio (not hardcoded 16:9)
- Right trim handle upper bound coercion (UI-side, prevents visual glitch beyond source duration)
- Thumbnail cache eviction on zoom change (prevents OOM from stale zoom-level entries)
- Safe bitmap cache clearing (no recycle() on potentially in-use Compose Bitmaps)
- ExportService stopped on setup-phase failures (try/catch around videoEngine.export)
- Deserialization safe getters throughout (optString/optLong, nullable deserializeClip)
- Multi-clip seek/playhead (absolute timeline position across concatenated ExoPlayer media items)
- Paste effects duplicate type filtering (skips already-present effect types)
- Merge contiguous trim validation (requires adjacent trim ranges from same source)
- Voiceover/freeze frame permanent storage (filesDir instead of cacheDir)
- Project-mode AI tab removed (all AI tools require clip selection)
- Snackbar toast z-ordering above bottom sheets
- Project persistence to Room DB
- Catppuccin Mocha dark theme
- Permission handling (media, audio, notifications)
- Export 14 GLSL shader effects (vignette, sharpen, film grain, gaussian/radial/motion blur, tilt shift, mosaic, fisheye, glitch, pixelate, wave, chromatic aberration, chroma key)
- Export 25 transition types via GLSL shaders (dissolve, fade black/white, wipe 4-way, slide 2-way, zoom in/out, spin, flip, cube, ripple, pixelate, directional warp, wind, morph, glitch, circle open, cross zoom, dreamy, heart, swirl)
- Export respects track mute/visible (hidden video tracks excluded, muted tracks silenced, muted audio tracks omitted)
- Export applies frame rate from config via FrameDropEffect (24/30/60fps frame dropping)
- Export applies audio bitrate from config via AudioEncoderSettings (256kbps default)
- Export text overlay fontFamily (TypefaceSpan), backgroundColor (BackgroundColorSpan), alignment (AlignmentSpan)
- Export cancel button in ExportSheet (TextButton in EXPORTING state, wires to VideoEngine.cancelExport)
- Android back button handling (BackHandler: dismiss panel > clear tool > deselect clip)
- Success toasts for split/duplicate/merge operations
- Auto-pause playback when opening any panel (pauseIfPlaying in all show* methods)
- Paste effects dim hint (SubMenuGrid disabledIds with alpha 0.35f when no copied effects)
- Clip filename labels on timeline clips (8sp, semi-transparent, hidden on narrow clips)
- Track mute/visible/lock toggle icons in timeline track headers (11dp color-coded icons)
- Effect enable/disable toggle (eye icon in EffectAdjustmentPanel header, disabled effects skipped in export)
- Project name display in EditorTopBar center (tap to rename via AlertDialog)
- Overflow menu: Add Media, Add Track (Video/Audio/Overlay/Text submenu), Rename Project
- Add Track from overflow menu (creates new track of selected type)

## Gotchas
- Media3 Transformer effects use `@UnstableApi` annotation - suppress with `@OptIn`
- Thumbnail extraction via `MediaMetadataRetriever` is slow - use caching aggressively
- `largeHeap=true` in manifest is required for video editing workloads
- Room `fallbackToDestructiveMigration()` - no `dropAllTables` parameter in Room 2.6.1
- Room DB stores project metadata only - track/clip state serialized separately via ProjectAutoSave
- FFmpeg not yet integrated - using pure Media3 pipeline
- VideoEngine is @Singleton - don't call `release()` from ViewModel onCleared (use `removePlayerListener()` + `resetExportState()`)
- `local.properties` not in git - must be created with sdk.dir path
- RgbMatrix color matrices are row-major 4x4: `[R_out = row0 dot [R,G,B,A]]`. Alpha channel (=1) serves as offset for translation effects (brightness, invert).
- OES shader extension for GLES 3.0 is `GL_OES_EGL_image_external_essl3` (not `GL_OES_EGL_image_external`)
- Room TypeConverters must handle unknown enum values gracefully (try/catch around valueOf)
- `startForegroundService()` required on API 26+ (minSdk), with SDK version check for compatibility
- Auto-save deserialization uses `opt*` methods throughout to survive missing/corrupt fields
- Room DB v1→v2→v3 migration handled by `fallbackToDestructiveMigration()` — drops all data on schema change. Acceptable for dev; needs proper migration before release with user data.
- FileProvider requires `res/xml/file_paths.xml` with `<cache-path>` entry matching the directory used for camera temp files
- FileProvider authority is `${applicationId}.fileprovider` — must match in manifest, file_paths.xml, and all code references
- AI scene detection splits clips in reverse-order (sortedByDescending) to prevent timeline position shifts from invalidating subsequent split points
- AI auto color replaces existing effects of same type (brightness/contrast/saturation/temperature) to prevent stacking
- AI stabilize applies zoom + position keyframes — both stored on Clip model (scaleX/Y + keyframes list)
- AI denoise uses volume boost + fade as proxy for noise gating (real spectral subtraction would require custom audio codec pipeline)
- AI remove BG uses chroma key effect — works well for green/blue screen, moderate for general backgrounds
- AI motion tracking generates POSITION_X/POSITION_Y keyframes, merges with existing keyframes (replaces position keyframes, preserves others)
- TRIM and SPLIT tools must call `dismissAllPanels()` — they bypass the boolean-panel pattern since they don't have their own panels
- Speed panel visibility driven by `currentTool == EditorTool.SPEED` (not a boolean flag like other panels), so it self-dismisses on tool change
- BottomToolArea manages `activeTabId` internally — sub-menu visibility is derived from activeTabId + isClipMode, not stored in ViewModel. LaunchedEffect resets activeTabId when isClipMode changes.
- Merge clips only works with adjacent clips from the same source URI (extends trim range)
- VoiceoverRecorderEngine is @Singleton but `release()` is safe to call from onCleared() (unlike VideoEngine) since each recording session is independent
- Photo Picker `takePersistableUriPermission` may throw SecurityException for picker-selected URIs — caught silently since picker grants temporary access
- ExportSheet error state includes `exportErrorMessage` — reset in `showExportSheet()` to prevent stale errors on reopen
- AiToolsPanel disabled tools use always-clickable Card (not Material3 `enabled=false`) to allow tap feedback dispatch
- AudioPanel fade envelope `drawFadeEnvelope()` uses Compose Path — needs `import androidx.compose.ui.graphics.Path` (already imported via wildcard)
- **NovaCutApp.VERSION** — must match build.gradle.kts versionName. Used for notification channel or display; was stale at "v0.3.0" until v0.12.0 fix.
- **VideoEngine export logging** — `Log.e(TAG, ...)` in both Transformer.Listener.onError and catch block. `TAG = "VideoEngine"` for logcat filtering.
- **Effect parameter bounds** — `coerceIn()` on all effect params in `buildVideoEffect()`: brightness ±1, contrast 0-2, saturation 0-3, temperature ±5. Prevents garbled output from unbounded values.
- **Text overlay undo** — `updateTextOverlay()` now calls `saveUndoState("Edit text")`. `addTextOverlay` and `removeTextOverlay` already had undo.
- **Timeline auto-scroll** — During playback, if playhead crosses 80% of visible timeline width, scrollOffsetMs jumps to place playhead at 25% position. Uses `@Volatile var timelineWidthPx` in ViewModel (outside EditorState to avoid recomposition). Timeline reports width via `onTimelineWidthChanged` callback.
- **Export progress clamped** — `coerceIn(0, 99)` while exporting to avoid premature "100%" display (COMPLETE state shows final confirmation instead).
- **Export bitrate descriptions** — Output details card shows human-readable quality hint ("Studio quality", "Great for YouTube/social", "Good for sharing", "Compact file size") alongside Mbps number.
- **Keyframe deduplication** — `KeyframeEngine.getValueAt()` applies `distinctBy { it.timeOffsetMs }` after sorting to prevent division-by-zero when duplicate keyframes exist at same timestamp.
- **SubMenuGrid scroll** — Grid now uses `verticalScroll(rememberScrollState())` with `heightIn(max = 200.dp)` to handle overflow on small screens.
- **Centralized effect defaults** — `EffectType.defaultParams(type)` companion method in `Project.kt` replaces duplicate default maps in `EditorScreen.kt` and `ToolPanel.kt`. Single source of truth for all 40+ effect default parameters.
- **AI tool cancellation** — `runAiTool()` stores `Job` reference in `aiJob` field. `cancelAiTool()` cancels the coroutine. `CancellationException` re-thrown after toast. AiToolsPanel processing indicator shows "Cancel" button.
- **Accessibility content descriptions** — Added to ExportSheet (Share, Save to gallery, Retry, Export video), AudioPanel (Record voiceover), EditorScreen (Add media) icons.
- **Effect adjustment undo debounce** — `EffectSlider` now has `onDragStarted` callback (like SpeedSlider). `beginEffectAdjust()` saves undo state once when slider drag begins, preventing undo spam during continuous adjustment. Threaded through `EffectAdjustmentPanel.onEffectDragStarted` → `EditorScreen` → `EditorViewModel.beginEffectAdjust()`.
- **Split validation before undo** — `splitClipAtPlayhead()` now validates that the playhead is within the selected clip's bounds BEFORE saving undo state. Prevents polluting undo stack with no-op split attempts.
- **Timeline trim guard** — Both left and right trim handle drag handlers now guard against `currentPixelsPerMs < 0.001f` to prevent division-by-zero at extreme zoom levels.
- **Text editor blank guard** — TextEditorSheet Save button disabled when text is blank. Button color dims to indicate disabled state.
- **Smart duplicate naming** — `ProjectListViewModel.duplicateProject()` strips existing `(Copy N)` suffix and increments: "Project (Copy)" → "Project (Copy 2)" → "Project (Copy 3)" to avoid cascading "(Copy) (Copy)" names.
- **Action dispatch logging** — EditorScreen `onAction` when-block has `else` branch with `Log.w("EditorScreen", "Unknown action: $actionId")` for debugging unhandled action IDs.
- **Auto-save failure tracking** — `ProjectAutoSave` tracks consecutive failures. After 3+ in a row, logs `Log.w` warning. Counter resets on success or `startAutoSave()`. Stale `.tmp` files cleaned up on `loadRecoveryData()`. `saveState()` ensures temp file cleanup on write failure. `copyAutoSave()` and `loadRecoveryData()` now log errors instead of silently swallowing.
- **Merge validation before undo** — `mergeWithNextClip()` now validates next-clip existence and same-source URI BEFORE saving undo state. Prevents undo stack pollution from failed merge attempts. Toasts shown for specific failure reasons.
- **Transition duration undo debounce** — `beginTransitionDurationChange()` saves undo state once when slider drag starts. `TransitionPicker` slider has `onDurationDragStarted` callback with isDragging tracking (same pattern as EffectSlider and SpeedSlider).
- **AudioEngine error logging** — `extractWaveform()` catch block now logs `Log.e(TAG, ...)` with exception details instead of silently returning empty array. TAG = "AudioEngine".
- **Fade envelope bounds guard** — `drawFadeEnvelope()` now returns early for `durationMs <= 10` (previously `<= 0`), preventing extreme path coordinates from tiny clip durations.
- **Atomic copyAutoSave** — `copyAutoSave()` now uses the same temp-file + rename pattern as `saveState()` to prevent partial writes on failure.
- **AI tool null safety** — `runAiTool()` now requires clip for ALL tools (removed `auto_color` exception). All `clip!!` replaced with safe `clip` val that's guaranteed non-null after the unified null guard. Prevents NPE when AI tool dispatched without clip selection.
- **Trim bounds coercion** — `trimClip()` now coerces `trimStartMs` to `0..sourceDurationMs-100` and `trimEndMs` to `trimStartMs+100..sourceDurationMs`. Prevents trim ranges exceeding source file bounds.
- **Split minimum duration** — `splitClipAtPlayhead()` validates both resulting halves are >= 100ms in source time before proceeding. Shows "Clip too short to split here" toast on failure.
- **Delete/duplicate undo pre-validation** — Both `deleteSelectedClip()` and `duplicateSelectedClip()` now verify clip existence in tracks before calling `saveUndoState()`. Same pattern as merge/split validation.
- **Deserialization failure logging** — All `mapNotNull` catch blocks in `ProjectAutoSave` deserialization now log `Log.w(TAG, ...)` with index and exception. Covers clips, effects, keyframes, and text overlays.
- **Fade undo debounce** — `beginFadeAdjust()` saves undo state once on drag start for fade in/out sliders. Uses same `onDragStarted` callback pattern as volume/speed/effect sliders. Wired through `AudioPanel.onFadeDragStarted` → `EditorViewModel.beginFadeAdjust()`.
- **Fade bounds coercion** — `setClipFadeIn()` constrains fadeInMs to `0..(durationMs - fadeOutMs)`. `setClipFadeOut()` constrains fadeOutMs to `0..(durationMs - fadeInMs)`. Prevents fade overlap exceeding clip duration.
- **Share intent toast feedback** — `getShareIntent()` now shows toast before returning null: "No exported video to share" for missing path, "Export file no longer available" for deleted file.
- **Export progress reset on error** — Both error paths in VideoEngine (Transformer.Listener.onError and outer catch) now reset `_exportProgress.value = 0f` alongside setting ERROR state. Ensures retry shows fresh progress bar.
- **Export aspect ratio from project** — `ExportConfig` now has `aspectRatio` field. `startExport()` copies project's aspect ratio into config. `VideoEngine.export()` uses `config.aspectRatio` instead of hardcoded `RATIO_16_9`. All 7 aspect ratios (16:9, 9:16, 1:1, 4:3, 3:4, 4:5, 21:9) correctly applied to export output dimensions.
- **Right trim handle upper bound** — Timeline right trim handle now coerces to `clip.sourceDurationMs` on the UI side, preventing visual glitch where clip appears longer than source during drag.
- **Thumbnail cache zoom eviction** — `LaunchedEffect` evicts thumbnail entries for non-current zoom levels before loading new ones. Prevents unbounded Bitmap memory growth across zoom sessions.
- **Safe bitmap cache clearing** — `clearThumbnailCache()` no longer calls `recycle()` on cached Bitmaps since they may still be referenced by Compose Image composables. GC handles reclamation after references are dropped.
- **ExportService setup-phase safety** — `startExport()` wraps `videoEngine.export()` in try/catch to stop the foreground service even if export setup throws before Transformer listener is registered.
- **Deserialization safe getters** — `deserializeClip()` now uses `optString`/`optLong` for all fields (id, sourceUri, sourceDurationMs, timelineStartMs, trimStartMs, trimEndMs). Returns null for missing sourceUri. Transition uses `optJSONObject` instead of throwing `getJSONObject`. Consistent with `deserializeEffect`/`deserializeKeyframe` patterns.
- **Dead metadata key removed** — `getVideoFrameRate()` no longer calls `extractMetadata(24)` (undocumented constant, always returned null). Falls back directly to 30fps default.
- **Multi-clip seek** — `VideoEngine.seekTo()` now computes which media item index the target position falls into and calls `player.seekTo(index, positionWithinItem)`. `clipDurationsMs` list stored on `prepareTimeline()`. `getAbsolutePositionMs()` returns sum of preceding clip durations + `currentPosition` for accurate playhead sync.
- **Paste effects dedup** — `pasteEffects()` now filters out effect types already present on the target clip before pasting. Shows "Effects already present on clip" if all pasted types are duplicates.
- **Merge contiguous validation** — `mergeWithNextClip()` now validates `clip.trimEndMs == nextClip.trimStartMs` to prevent including trimmed-out footage when merging non-adjacent trim ranges.
- **Voiceover permanent storage** — Voiceover recordings now saved to `filesDir/voiceovers/` instead of `cacheDir`. Freeze frames saved to `filesDir/freeze_frames/`. Both survive cache cleanup and device reboot.
- **Project-mode AI tab removed** — AI tools only available in clip mode (when a clip is selected). Removed dead `projectAiSubMenu` and its tab entry from `projectTabs`.
- **Snackbar z-ordering** — Toast Snackbar now uses `zIndex(10f)` and `bottom = 120.dp` padding to render above bottom sheets and tool panels.
- **ExportService @AndroidEntryPoint** — Service now uses Hilt DI to inject VideoEngine @Singleton. Collects `exportProgress`/`exportState` StateFlows via `combine()` to update notification in real-time. Self-manages lifecycle (stopSelf on COMPLETE/ERROR/CANCELLED). ViewModel no longer calls `stopService()`.
- **Export cancellation** — `VideoEngine.cancelExport()` sets `CANCELLED` state and calls `transformer.cancel()`. `activeTransformer` stored as `@Volatile` field, cleared after export completes or fails. ExportService `ACTION_CANCEL` now calls `videoEngine.cancelExport()` instead of just `stopSelf()`. CANCELLED state added to `ExportState` enum.
- **VolumeAudioProcessor** — Custom `BaseAudioProcessor` that applies volume scaling and fade in/out envelope to 16-bit PCM audio during export. Tracks sample position to compute time offset for fade calculations. Only created when `volume != 1.0f` or `fadeInMs > 0` or `fadeOutMs > 0`.
- **Export audio effects wired** — `Effects(audioProcessors, videoEffects)` now passes `VolumeAudioProcessor` list instead of `emptyList()` for audio. Each clip gets its own processor with its specific volume/fade settings.
- **VolumeAudioProcessor encoding validation** — `onConfigure()` validates `C.ENCODING_PCM_16BIT`. Non-16-bit audio formats rejected with `UnhandledAudioFormatException` to prevent garbled output.
- **ExportSheet CANCELLED state** — Dedicated UI state for cancelled exports: `Icons.Default.Cancel` in Mocha.Peach + "Export Cancelled" text + Done button. Prevents CANCELLED falling through to idle/config view.
- **Text overlay editing flow** — `editingTextOverlayId: String?` in EditorState. `editTextOverlay(id)` sets the ID and shows TextEditorSheet. EditorScreen resolves overlay by ID and passes to sheet. onSave calls `updateTextOverlay` (edit) vs `addTextOverlay` (new).
- **Text overlay list UI** — `TextOverlayList` composable in ToolPanel. Shows when text tab active and overlays exist. Each item: colored icon, text preview (1 line), time range, edit + delete buttons. Scrollable with 150dp max height.
- **Camera temp cleanup** — `LaunchedEffect(Unit)` in MediaPickerSheet deletes files in `cacheDir/camera/` older than 1 hour. Safe because camera launcher completes before user opens picker again.
- **dismissedPanelState includes editingTextOverlayId** — Reset to null alongside all panel booleans to prevent stale overlay editing state.
- **Export color effects expanded** — `buildVideoEffect()` now handles 20+ effect types via RgbMatrix: TINT, EXPOSURE, GAMMA, HIGHLIGHTS, SHADOWS, VIBRANCE, POSTERIZE, COOL_TONE, WARM_TONE, CYBERPUNK, NOIR, VINTAGE, MIRROR (via ScaleAndRotateTransformation). Effects requiring custom GL shaders (blur, distortion, vignette, etc.) still return null.
- **Export clip transforms** — Clip rotation, scaleX, scaleY applied via `ScaleAndRotateTransformation.Builder()` in export. Position X/Y not yet supported (requires MatrixTransformation with translation matrix). Static opacity applied via RgbMatrix when no keyframe opacity overrides exist.
- **Export audio track** — Audio track clips (background music, voiceovers) exported as second `EditedMediaItemSequence` with `setRemoveVideo(true)`. Each audio clip gets its own `VolumeAudioProcessor`. `Composition.Builder` uses `setTransmuxAudio(true)` when no audio track to avoid re-encoding video audio.
- **Export text overlays** — `ExportTextOverlay` class extends `androidx.media3.effect.TextOverlay` (not to be confused with model `TextOverlay`). Renders styled SpannableString with ForegroundColorSpan, AbsoluteSizeSpan, StyleSpan. Time-gated: returns empty string + alpha 0 outside `relStartMs..relEndMs`. Position converted from 0..1 model space to -1..1 anchor space (Y inverted). Added via `OverlayEffect(ImmutableList.copyOf(typed))` after effects but before Presentation.
- **TextOverlay name collision** — `com.novacut.editor.model.TextOverlay` and `androidx.media3.effect.TextOverlay` both imported via wildcards. Export function parameter uses fully qualified `com.novacut.editor.model.TextOverlay`. `ExportTextOverlay` extends fully qualified `androidx.media3.effect.TextOverlay()`.
- **EditedMediaItemSequence.Builder** — Migrated from deprecated `EditedMediaItemSequence(list)` constructor to `EditedMediaItemSequence.Builder(list).build()` pattern (Media3 1.5.x).
- **Portrait resolution fix** — `Resolution.forAspect()` now branches on aspect ratio >= 1 vs < 1. For portrait aspects (9:16, 3:4, 4:5), height (shorter dimension) becomes width and the taller dimension is derived. FHD_1080P + 9:16 now correctly produces 1080x1920 instead of 608x1080.
- **Undo/redo recalculates totalDurationMs** — Both `undo()` and `redo()` now wrap restored state through `recalculateDuration()` to keep timeline duration accurate after state restoration.
- **Auto_captions in clip AI menu** — Moved from project-mode textSubMenu (unreachable, required clip) to clip-mode clipAiSubMenu. `auto_color` also added to clipAiSubMenu (was wired in EditorScreen but missing from menu).
- **Keyframe-animated transforms in export** — `MatrixTransformation` replaces static `ScaleAndRotateTransformation` when keyframes exist for SCALE_X/SCALE_Y/ROTATION/POSITION_X/POSITION_Y. Uses `android.graphics.Matrix` with `postScale`/`postRotate`/`postTranslate` (scale → rotate → translate order). Falls back to static keyframe values when no keyframe for a property.
- **Static clip position in export** — `clip.positionX`/`positionY` now applied via `MatrixTransformation` (previously only rotation/scale were exported). Y axis inverted (`-py`) to match GL coordinate system.
- **Keyframe volume in export** — `VolumeAudioProcessor` accepts optional `keyframes` list. When present, evaluates `KeyframeEngine.getValueAt(VOLUME)` per audio sample instead of using static `volume`. Fade envelopes still applied on top of keyframe volume.

- **Dead ShaderEffects.kt removed** — 509 lines of unused GLSL shader code deleted. All effects use Media3 RgbMatrix/GlEffect in VideoEngine, not custom shader compilation.
- **Waveform extraction on project recovery** — Auto-save restore now launches `extractWaveform()` for all recovered clips. Previously only new clips got waveforms; recovered projects showed placeholders.
- **Text overlay animation export** — `ExportTextOverlay.getOverlaySettings()` now computes per-frame alpha, position offset, scale, and rotation based on `animationIn`/`animationOut` fields. 500ms animation duration. Typewriter handled in `getText()` via progressive character reveal. Bounce uses multi-segment ease-out. Animations compose: in + out can be different types.
- **clip.isReversed not exported** — Known limitation. Media3 Transformer has no reverse playback support. Would require FFmpeg or custom frame-reversal pipeline. `isReversed` works in preview but not in export.
- **Back action dismisses panels** — "back" action in BottomToolArea now calls `dismissAllPanels()` before `selectClip(null)`. Prevents NPE from open panels referencing deselected clip.
- **Export progress poll timeout** — Progress polling loop capped at 2400 iterations (10 minutes at 250ms intervals). On timeout, calls `transformer.cancel()`, sets ERROR state, and reports "Export timed out". Prevents infinite loop if Transformer hangs.

- **Release signing CI fallback** — `build.gradle.kts` release signingConfig now falls back to debug signing when neither `keystore.properties` nor bundled `novacut-release.jks` exist (CI environment). Previously failed with "Keystore file not found" on GitHub Actions.
- **Version string in strings.xml** — `app_version` resource must be updated alongside NovaCutApp.VERSION and build.gradle.kts versionName. Was stuck at v0.1.0 for 30 releases.
- **Backup rules include freeze_frames and voiceovers** — Both directories live in `filesDir` and are now included in `backup_rules.xml` (legacy) and `data_extraction_rules.xml` (Android 12+). Without this, user recordings and freeze frames would be lost on device transfer.
- **ProGuard keeps Room Converters** — Explicit `-keep class com.novacut.editor.engine.db.Converters { *; }` rule added. Without it, R8 could strip the class since it's only referenced by annotation, causing AspectRatio/Resolution TypeConverter crashes at runtime.
- **VoiceoverRecorder double-start cleanup** — `startRecording()` now stops and releases any existing MediaRecorder before creating a new one. Prevents resource leak (mic hardware lock, memory) if called while already recording.
- **estimateRegionMotion divide-by-zero guard** — `estimateRegionMotion()` in AiFeatures.kt now returns `0f to 0f` when bitmap width or height < 8, matching the guard in `estimateMotion()`. Prevents ArithmeticException on malformed video frames during motion tracking.
- **createProject race condition fixed** — `createProject()` now accepts an `onCreated` callback that fires after the Room insert completes, instead of returning the ID synchronously before the async insert. Prevents navigation to a non-existent project.
- **Timeline waveform empty array guard** — `drawWaveform()` now returns early if `samples.isEmpty()` to prevent `coerceIn(0, -1)` IllegalArgumentException when AudioEngine returns empty FloatArray on decode failure.
- **Gallery save null URI handling** — `saveToGallery()` now shows error toast and returns early when `ContentResolver.insert()` returns null (e.g., scoped storage rejection on API 30+). Previously silently fell through to "Saved to gallery" success toast.
- **Auto-scroll pixelsPerMs guard** — Playhead sync loop guards `pixelsPerMs >= 0.001f` before computing `visibleMs` to prevent division-by-zero at very low zoom levels.
- **Text overlay time validation** — `addTextOverlay()` and `updateTextOverlay()` reject overlays where `startTimeMs >= endTimeMs` with toast feedback.
- **Transition duration bounds on deserialization** — `deserializeTransition()` clamps `durationMs` to 100-2000ms range via `coerceIn()` to match UI slider bounds.
- **Export state snapshot** — `startExport()` captures `tracks`, `textOverlays`, and `config` before launching coroutine, preventing race conditions where state changes between validation and export call.
- **Timeline time labels** — `drawTimeRuler()` now draws time labels at major tick marks using `TextMeasurer`. Format: "0s", "5s", "1:00", etc. Ruler height increased from 24dp to 28dp.
- **Timeline playhead drag** — Ruler Canvas has tap + drag gesture handlers (`detectTapGestures` + `detectDragGestures`) for positioning playhead by tapping/dragging on the ruler.
- **Undo/redo stale state fix** — `undo()` and `redo()` now validate `selectedClipId` exists in restored tracks, call `dismissedPanelState()`, and set `currentTool = EditorTool.NONE`.
- **Smart crop applies transform** — AI smart crop now uses `setClipTransform()` to apply positionX/positionY instead of just showing a toast.
- **Fade slider dynamic max** — AudioPanel fade sliders now use `clip.durationMs` as max instead of hardcoded 5000ms.
- **Text overlay position controls** — TextEditorSheet has Horizontal/Vertical position sliders (0-1 range), wired into TextOverlay save callback.
- **Duration slider in seconds** — TextEditorSheet duration slider displays/operates in seconds (0.5-10s) instead of milliseconds.
- **Stroke slider removed** — Non-functional strokeWidth slider removed from TextEditorSheet (SpannableString has no native stroke support).
- **ShaderEffect.kt GLSL framework** — `ShaderEffect` implements `GlEffect`, wraps GLES 3.0 fragment shader + uniforms map. `ShaderProgram` extends `BaseGlShaderProgram`, creates VAO/VBO once in `configure()`, draws fullscreen quad per frame. Uses `androidx.media3.common.util.Size` (NOT `android.util.Size`).
- **Media3 presentationTimeUs is 0-based** — In Transformer export with ClippingConfiguration, `presentationTimeUs` in effects starts from 0 for each clip (not the original media timestamp). Verified by working keyframe opacity and text overlay timing.
- **Transition shaders use uTime for progress** — `uTime = presentationTimeUs / 1_000_000f` (seconds). Progress computed as `uTime * 1000000.0 / uDurationUs`. `uDurationUs = transition.durationMs * 1000f`. After transition duration, progress clamps to 1.0 (fully revealed, no visual change).
- **Wipe shader direction normalization** — `FRAG_WIPE_IN` uses `pos = vTexCoord.x * uDirX + vTexCoord.y * uDirY` with lo/hi range normalization. The `1.04/-0.02` adjusted progress ensures clean black at progress=0 and full reveal at progress=1 (no soft-edge artifact at boundaries).
- **Transition rendering order** — Transitions inserted after regular effects but before opacity/transform/speed/text/Presentation in the videoEffects chain. This means the transition reveals the fully-effected video.
- **GLSL `float a, b` declaration** — Valid in GLSL ES 3.0. Used for `float cs = cos(angle), sn = sin(angle);` in spin/swirl shaders.
- **Heart shape parametric formula** — Uses implicit equation `(x^2 + y^2 - 1)^3 - x^2*y^3 = 0` for clean heart mask in GLSL. Center offset at (0.5, 0.6) for aesthetic framing.
- **Track mute/visible in export** — Video track filtered by `isVisible`, audio from muted video tracks silenced via `VolumeAudioProcessor(volume=0f)`. Audio track filtered by both `isVisible` and `isMuted`. `transmuxAudio` flag derived from filtered audio track state.
- **FrameDropEffect for frame rate** — `FrameDropEffect.createDefaultFrameDropEffect(fps)` added before Presentation in video effects chain. Can only reduce fps (drop frames), not increase. Source fps preserved if target is higher than source.
- **AudioEncoderSettings in Media3 1.5.1** — `DefaultEncoderFactory.Builder.setRequestedAudioEncoderSettings(AudioEncoderSettings)` exists alongside video settings. `AudioEncoderSettings.Builder().setBitrate(int)` controls output audio bitrate.
- **Text overlay fontFamily export** — `TypefaceSpan(overlay.fontFamily)` applied to SpannableString. Android resolves "sans-serif", "serif", "monospace", "cursive" to system fonts. Custom font files would require loading Typeface from assets.
- **Text overlay backgroundColor skip** — `BackgroundColorSpan` only added when alpha channel is non-zero (`color and 0xFF000000 != 0`). Fully transparent (0x00000000) default means no background rendered.
- **Text overlay strokeColor/strokeWidth** — NOT exported. SpannableString has no native stroke support. Would require custom Canvas drawing in a Bitmap-based overlay (not TextOverlay). Documented as known limitation.
- **VolumeOff/VolumeUp AutoMirrored** — `Icons.Default.VolumeOff`/`VolumeUp` are deprecated. Use `Icons.AutoMirrored.Filled.VolumeOff`/`VolumeUp` with explicit import.
- **Effect.enabled already in model** — `Effect` data class has `enabled: Boolean = true` field. `toggleEffectEnabled()` in ViewModel copies effect with `!enabled`. Export filters `clip.effects.filter { it.enabled }`.
- **Track header toggle click targets** — 11dp icons with `clickable` modifier. Small hit target but acceptable for track headers. No `minimumInteractiveComponentSize` override.
- **EditorTopBar project name** — Shown as `Text` with `Modifier.weight(1f)` center-aligned between undo/redo and delete/overflow buttons. Taps open `AlertDialog` for rename. `TextOverflow.Ellipsis` for long names.
- **Add Track overflow submenu** — Separate `DropdownMenu` (not nested). First menu dismisses, then second opens via `showAddTrackMenu` state. Each track type (VIDEO, AUDIO, OVERLAY, TEXT) creates new track via `viewModel.addTrack(type)`.
- **BackHandler priority** — `hasOpenPanel` checks 12 boolean panel states. Priority: dismiss panels > clear tool > deselect clip. `enabled` parameter gates the handler to avoid intercepting when nothing to dismiss.
- **pauseIfPlaying pattern** — Checks `videoEngine.isPlaying()`, calls `videoEngine.pause()`, updates `isPlaying = false` in state. Called at start of every `show*()` method to prevent playback during panel interaction.
- **SubMenuGrid disabledIds** — `Set<String>` parameter. Disabled items get `Modifier.alpha(0.35f)` and no `clickable` modifier. Used for paste_fx when `hasCopiedEffects = false`.
- **Live preview effects** — `VideoEngine.applyPreviewEffects(clip)` builds and applies the selected clip's effects (user effects, color grading, LUT, blend mode, transitions, opacity, transforms) to ExoPlayer via `player.setVideoEffects()`. Called on clip selection, effect add/remove/update/toggle, color grade, blend mode, transition, transform, and opacity changes. Effects are global to the player (not per-clip in playlist), so only the selected clip's effects are shown.
- **Live preview speed** — `VideoEngine.setPreviewSpeed(speed)` applies `PlaybackParameters` to ExoPlayer for real-time speed preview. Updated on speed slider change and during playback when crossing clip boundaries (detected via `getCurrentClipIndex()` in the playhead sync loop).
- **Clip-tracking during playback** — The playhead sync loop now tracks `lastClipIndex`. When ExoPlayer's `currentMediaItemIndex` changes, the current clip's speed and effects are applied automatically, enabling correct preview across multi-clip timelines.
- **ToolPanel "Transform/Motion" tab fixed** — Clip mode "transform" tab now toggles the Motion sub-menu (Transform, Keyframes, Masks, Blend Mode, PiP, Chroma Key) instead of directly opening the Transform panel.
- **ToolPanel "Color" tab wired** — Clip mode "color" tab now toggles the Color sub-menu (Color Grade, Effects, Normalize Audio). Previously had no handler.
- **ToolPanel "Tools" tab wired** — Project mode "project_tools" tab now toggles the Tools sub-menu (Audio Mixer, Beat Detect, Auto Duck, Adjustment Layer, Scopes, Chapters, etc.). Previously had no handler.
- **Media3 upgraded from 1.5.1 to 1.9.2** — Major version bump. `OverlaySettings` renamed to `StaticOverlaySettings`. `ExportTextOverlay.getOverlaySettings()` replaced with `getVertexTransformation()` returning 4x4 column-major GL matrix. Alpha now modulated directly in text ForegroundColorSpan. Added `media3-muxer` dependency (Muxer moved from transformer module).
- **Variable speed export via SpeedProvider** — `SpeedChangeEffect` replaced with `EditedMediaItem.Builder.setSpeed(SpeedProvider)`. When a clip has a `SpeedCurve` with bezier control points, the curve is wired as a per-frame `SpeedProvider` in the export pipeline. Constant speed clips also use SpeedProvider (required by 1.9.x API). Speed curves with ramp up/down/pulse presets now exported correctly.
- **ExoPlayer scrubbing mode** — `VideoEngine.setScrubbingMode(enabled)` wraps `player.setScrubbingModeEnabled()` (Media3 1.8.0+). Enabled during timeline trim drag (`beginTrim()`), disabled on tool change away from TRIM. `beginScrub()`/`endScrub()` exposed for timeline ruler drag. Optimizes seek performance during frequent position changes.

- **Whisper ONNX auto captions** — `WhisperEngine` provides real speech-to-text when model is downloaded (~75MB whisper-tiny.en from HuggingFace). Auto-downloads encoder_model.onnx + decoder_model.onnx + vocab.json to `filesDir/whisper/`. Falls back to energy segmentation when model not available. AiToolsPanel shows model download status card with progress bar. Uses ONNX Runtime Android 1.17.0. Greedy decoding with timestamp tokens (50364+, each = 0.02s). 30-second chunk processing. GPT-2 byte-level BPE vocab decoded via `decodeGpt2Bytes()`. `INTERNET` permission added for model download.

- **MediaPipe selfie segmentation** — `SegmentationEngine` wraps MediaPipe Tasks Vision `ImageSegmenter` with selfie_segmenter.tflite (~256KB, auto-downloaded from Google storage). `BG_REMOVAL` EffectType added. Per-frame segmentation during export via `SegmentationGlEffect` (GL readback + CPU segmentation + mask texture upload). Falls back to chroma key when model not available. `ByteBufferExtractor.extract()` for MPImage float confidence values. AiToolsPanel shows segmentation model card. Threshold slider (0.1-0.9) via EffectAdjustment panel.

- **SegmentationGlEffect FBO safety** — `drawFrame()` saves/restores `GL_FRAMEBUFFER_BINDING` around readback. Copies input to intermediate texture via pass-through shader before `glReadPixels` (avoids GLES feedback loop of sampling and FBO-attaching same texture). Uses separate `copyProgram` and `copyTexture`.
- **BG_REMOVAL skipped in preview** — `applyPreviewEffects` filters out `EffectType.BG_REMOVAL` to prevent per-frame CPU segmentation during live playback (would cause ANR). Only runs during export.
- **bg_replace uses segmentation** — `"bg_replace"` AI tool handler now checks `segmentationEngine.isReady()` and uses `BG_REMOVAL` when model available, falls back to chroma key otherwise.
- **WhisperEngine hardening** — `SessionOptions.close()` after session creation (native memory leak). `NO_SPEECH` token (50362) filtered from text token collection (was decoded as garbled vocabulary text). `Log.e` on corrupt model session creation failure.

- **User template system** — `TemplateManager` saves/loads/deletes user templates as JSON in `filesDir/templates/`. "Save as Template" in editor overflow menu with name dialog. FAB on project list now opens `ProjectTemplateSheet` (preset grid + "My Templates" section). `createFromTemplate()` creates project with template's track layout + text overlays (clips cleared since source URIs won't exist). `UserTemplate` data class with stateJson from `AutoSaveState.serialize()`.

- **Proper Room migrations** — Replaced `fallbackToDestructiveMigration()` with explicit `MIGRATION_1_2` (templateId, proxyEnabled), `MIGRATION_2_3` (version), `MIGRATION_3_4` (baseline freeze). DB version bumped to 4 with `exportSchema = true`. Schema exported to `app/schemas/`. `fallbackToDestructiveMigrationFrom(1)` only destroys from ancient v1 installs.
- **Style transfer AI tool** — `analyzeAndApplyStyle()` samples 3 frames, analyzes luminance/saturation/temperature distribution, applies cinematic color grade (contrast boost, temperature shift, slight desaturation, vignette, film grain). Names the detected style (Noir, Warm Cinematic, Moody, Vibrant Film, Cinematic).
- **Neural upscale AI tool** — `analyzeForUpscale()` detects source resolution, recommends next tier (480p->720p, 720p->1080p, 1080p->1440p, 1440p->4K). Updates project resolution + applies sharpening (strength inversely proportional to source resolution).

- **v2.0.0 bug audit** — Complete 3-agent audit (engine/UI/model layers). Fixed: division-by-zero in energy captions (`windowSamples`, noise analysis `signalSampleCount`), `.average()` crash on empty energy slices, `Clip.durationMs` speed=0 guard (`coerceAtLeast(0.01f)`), bitmap leak in `SegmentationGlEffect.readCopyTextureToBitmap()`, `ExportSheet` empty error string check (`isNullOrBlank`), `AiToolsPanel` null processing tool name fallback, `TemplateManager` silent exceptions now logged. ProGuard: added ONNX Runtime + MediaPipe + javax.lang.model keep/dontwarn rules.
- **BatchExportPanel wired** — `AnimatedVisibility` block added for `state.showBatchExport` in EditorScreen. `startBatchExport()` exports queue items sequentially via `startExport(cacheDir)` loop.
- **VideoScopes frame capture** — `scopeFrame: StateFlow<Bitmap?>` in ViewModel. `updateScopeFrame()` extracts 256x144 thumbnail from selected clip at playhead via `extractThumbnail()`. Updated on seek and scope toggle. Scopes now display real histogram/waveform/vectorscope data.
- **ProxyEngine wired** — Injected into EditorViewModel. `setProxyEnabled()` triggers `generateProxiesForAllClips()` which iterates all clips and calls `ProxyEngine.generateProxy()`. Clears proxies on disable. "Proxy Edit" menu item added to `projectToolsSubMenu`.

- **Settings screen** — `ui/settings/SettingsScreen.kt` with default resolution/frame rate/aspect ratio, auto-save toggle + interval slider, proxy resolution selector, about section (version, engine, AI models). Gear icon in ProjectListScreen header. Navigation route `"settings"`.
- **Export platform presets** — `PlatformPreset` quick-select chips (YouTube, TikTok, Instagram Feed/Reels/Story, Twitter, LinkedIn) added to ExportSheet. Auto-populates resolution/fps/codec. Audio-only toggle switch added.
- **Editor onboarding** — Empty project shows centered hint card ("No clips yet — Tap the + button to add media") with VideoLibrary icon. Hides when clips exist or panel is open. Preview panel hidden until clips added.
- **Timeline zoom controls** — Zoom out (-), fit (reset to 1x), zoom in (+) buttons with percentage label. Added above track headers. Uses 0.75x/1.33x multipliers, clamped to 0.1x-10x range.

- **Settings persistence** — `SettingsRepository` backed by DataStore (`preferencesDataStore`). `SettingsViewModel` with `StateFlow<AppSettings>`. All settings (resolution, fps, aspect ratio, auto-save, proxy) persist across rotation and app restarts. `AppSettings` data class with safe enum deserialization.
- **Timeline multi-select** — Long-press on clip toggles multi-select via `toggleClipMultiSelect()`. Orange highlight for multi-selected clips. Action bar shows "N selected" with Delete and Cancel buttons. `selectedClipIds: Set<String>` in EditorState wired to Timeline. `deleteMultiSelectedClips()` with undo support.
- **MediaManager remove unused** — `removeUnusedMedia()` removes empty non-default tracks with undo. Wired to MediaManagerPanel's "Remove Unused" button (was a toast stub).
- **Key files added**: `engine/SettingsRepository.kt`, `ui/settings/SettingsViewModel.kt`
- **Settings wired to EditorViewModel** — `SettingsRepository` injected. Export defaults (resolution, fps) applied on project open. Auto-save interval/enabled respected. Uses `appliedDefaults` flag (not fragile equality check). Only restarts auto-save when enabled/interval actually change (not on every settings emit).
- **ProjectAutoSave accepts interval** — `startAutoSave(projectId, intervalMs)` parameter added. Interval clamped to 10s-600s.
- **README rewritten** — Full feature list matching v2.3.0, accurate tech stack table, build instructions, AI tool descriptions updated (Whisper needs internet for model download).

## v2.4.0 Bug Audit & Fixes

### Critical Engine Fixes
- **Blend mode shaders fixed** — All 13 blend modes were self-blending no-ops (e.g., `min(c.rgb, c.rgb)` = no-op). Now use mid-gray (0.5) as virtual blend layer since Media3 doesn't support dual-texture compositing. Each mode now produces a distinct visual effect.
- **Proxy engine now actually downscales** — `ProxyEngine.generateProxy()` was creating full-resolution copies (no `Presentation` applied). Now adds `Presentation.createForHeight()` based on `ProxyResolution.scale`. HALF=540p, QUARTER=270p, EIGHTH=135p.
- **ProxyEngine thread-safe map** — Replaced plain `mutableMapOf` with `ConcurrentHashMap` to prevent `ConcurrentModificationException`. Also improved key generation to reduce hash collision risk (`hashCode_length` instead of just `hashCode`).
- **AudioEffectsEngine massive boxing eliminated** — `pcm.map { }.toFloatArray()` replaced with `FloatArray(size) { }` constructor (avoids boxing millions of Float objects). Same for output conversion.
- **Compressor attack/release coefficients were swapped** — Attack should cause fast envelope rise (needs low coeff for fast tracking), release should cause slow decay (needs high coeff). Was reversed.
- **ZCR speech detection cross-channel bug** — `detectSpeechRegions()` was comparing adjacent samples across channel boundaries in stereo. Now steps by `channels` count.
- **ColorMatchEngine MediaMetadataRetriever leak** — `retriever.release()` was inside `try` before `catch`, so exceptions during `getFrameAtTime()` would leak the native resource. Moved to `finally` block.
- **Mask animations no longer frozen at t=0** — `interpolateMaskPoints(mask, 0L)` was hardcoded. Now uses clip midpoint time as best static approximation for mask position during export.
- **Multi-track export** — Was only exporting first VIDEO and first AUDIO track, silently dropping overlays and additional audio. Now collects all visible video tracks (VIDEO + OVERLAY) and all audio tracks.
- **transmuxAudio fix** — When video track was muted, `setTransmuxAudio(true)` was bypassing the VolumeAudioProcessor that enforced the mute. Now correctly sets transmux based on both audio track presence and video mute state.
- **ShaderEffect crash protection** — Fragment shader compile failure now falls back to passthrough shader instead of crashing the Transformer pipeline. GL attribute location -1 check added to prevent undefined behavior on some GPU drivers.

### UI Fixes
- **Playhead sync NPE** — `videoEngine.getPlayer()` could return null during initialization; playhead sync loop now uses `?: continue` guard.
- **Color grading undo debounce** — Was calling `saveUndoState()` on every drag event, flooding undo stack. Now uses `beginColorGradeAdjust()` pattern: undo saved once when drag starts. Wired through `ColorGradingPanel.onDragStarted` -> color wheels and curve editor.
- **BackHandler now dismisses scopes** — `showScopes` was missing from `hasOpenPanel` check. Also added to `dismissedPanelState()`.
- **TextEditorSheet stroke controls removed** — Non-functional stroke width/color UI (SpannableString has no native stroke support) was still present despite being documented as removed. Now removed.
- **Batch export improvements** — Items now properly update status to IN_PROGRESS/COMPLETED/FAILED. Output directory changed from `cacheDir` (subject to system cleanup) to `getExternalFilesDir(DIRECTORY_MOVIES)`.

### Known Remaining Issues
- Blend modes use mid-gray as virtual blend layer (not true dual-texture compositing). Proper compositing requires Media3 Compositor API.
- SmartRenderEngine analysis results not used for actual export (smart render bypass not implemented).
- ProjectArchive.importArchive() not implemented (export-only).
- Speed curve clips don't correctly affect timeline duration calculation (`Clip.durationMs` uses constant speed only).

## v2.5.0 Feature Expansion (Competitor-Inspired)

### New AI Engine Features (AiFeatures.kt)
- **Filler word / silence removal** — `detectFillerAndSilence()` uses Whisper for word-level detection of "um", "uh", "like", "you know", etc. Falls back to energy-based silence detection (< -40dB, > 500ms). Returns `RemovalRegion` list.
- **Beat sync automation** — `generateBeatSyncEdits()` uses `AudioEffectsEngine.detectBeats()` to find beats, maps clips across beat positions. Returns `BeatSyncCut` list.
- **Enhanced smart reframe** — `smartReframe()` samples frames at 500ms, uses saliency analysis to find subject center, generates pan/zoom keyframes for aspect conversion. Smooths with moving average.
- **AI auto-edit / highlight reel** — `generateAutoEdit()` analyzes clips for sharpness (Laplacian), motion, face presence (skin-tone). Ranks by quality, optionally syncs to beats. Selects best segments for target duration.
- **AI noise reduction analysis** — `analyzeNoiseProfile()` extracts audio, computes DFT, classifies noise as HISS/HUM/BROADBAND/CLEAN. Recommends DSP effect params.

### New UI Panels
- **CaptionStyleGallery.kt** — 15 pre-built caption templates (karaoke, word-by-word, bounce, glow, neon, etc.) in 2-column grid with visual previews
- **SpeedPresets.kt** — 10 named speed presets (Bullet Time, Hero Time, Montage, Jump Cut, etc.) with mini Canvas curve previews
- **BeatSyncPanel.kt** — Detect beats button, beat count/BPM stats, visual beat timeline, "Apply Beat Sync" one-tap
- **SmartReframePanel.kt** — 5 aspect ratio cards with platform labels (YouTube, TikTok, Instagram), visual ratio previews
- **AutoSaveIndicator.kt** — Non-intrusive save status indicator (Saving.../Saved/Error) with auto-fade
- **UndoHistoryPanel.kt** — Visual undo history list (like Photoshop), jump-to-state, relative timestamps
- **FirstRunTutorial.kt** — 4-step guided overlay (Add Media, Timeline, Edit & Enhance, Export & Share)

### New Data Models (Project.kt)
- `CaptionTemplateType` enum (15 styles), `CaptionStyleTemplate` data class
- `SpeedPresetType` enum (10 named presets)
- `SaveIndicatorState` enum (HIDDEN/SAVING/SAVED/ERROR)
- `TutorialStep`, `TutorialHighlight`, `UndoHistoryEntry`

### ViewModel & Wiring
- 16 new state fields in `EditorState` (panels, modes, analysis states)
- `EditorMode` enum (EASY/PRO) for progressive disclosure
- `isTimelineCollapsed` for collapsible timeline
- All new panels wired via AnimatedVisibility in EditorScreen
- Action dispatch for 7 new actions (beat_sync, auto_edit, smart_reframe, caption_styles, speed_presets, filler_removal, undo_history)
- ToolPanel sub-menus updated with new entries
- Beat sync analyzes audio and auto-splits clips at beat markers
- Smart reframe changes project aspect ratio one-tap
- Speed presets generate SpeedCurve with proper bezier control points

## v2.5.0 UI Wiring Fixes
- **Easy/Pro mode toggle** — Chip in EditorTopBar between project name and export button. Calls `viewModel.toggleEditorMode()`. Shows mode label colored Mauve (Pro) or Green (Easy).
- **Collapsible timeline** — Toggle row above Timeline with "Timeline" label and expand/collapse icon. AnimatedVisibility wraps Timeline with expandVertically/shrinkVertically. State via `isTimelineCollapsed`.
- **Auto-save indicator wired** — Auto-save getState lambda now triggers `showSaveIndicator(SAVING)` before state capture and `showSaveIndicator(SAVED)` after 500ms delay.
- **Frame step uses project frame rate** — PreviewPanel accepts `frameRate: Int` parameter, computes `frameStepMs = 1000L / frameRate`. Previous/next frame buttons use dynamic step instead of hardcoded 33ms.
- **Timeline track key() calls** — Track headers and main timeline content area use `for (track in tracks) { key(track.id) { ... } }` instead of bare `forEach` for proper Compose recomposition identity.
- **Easy mode tool filtering** — BottomToolArea accepts `editorMode` parameter. In EASY mode, project tabs filtered to edit/audio/text/effects; clip tabs filtered to back/edit/speed/effects/transition. Hides advanced tools (AI, transform, color, aspect, etc.).

## v2.6.0 UX Polish + New Engines

### Easy/Pro Mode & Collapsible Timeline
- **Easy/Pro mode toggle** in EditorTopBar. Easy mode hides advanced tools (AI, transform, color grading, aspect ratio). Shows only edit/audio/text/effects/speed/transition.
- **Collapsible timeline** with expand/collapse toggle row. AnimatedVisibility with expandVertically/shrinkVertically.
- **Auto-save indicator wired** to real save events. Shows "Saving..." then auto-fades to "Saved" after 500ms.
- **Frame step uses project fps** instead of hardcoded 33ms.
- **Timeline key() calls** for proper Compose recomposition identity.

### New Engines
- **EffectShareEngine** (`engine/EffectShareEngine.kt`) — Export/import effect chains + color grades + audio effects as `.ncfx` JSON files. Includes `ImportedEffects` data class.
- **TtsEngine** (`engine/TtsEngine.kt`) — Android TTS wrapper with 8 voice styles (Narrator, Casual, Energetic, Deep, Soft, Fast, Slow, Dramatic). Synthesize to WAV file or preview via speaker.
- **TtsPanel** (`ui/editor/TtsPanel.kt`) — Text input, voice style selector chips, preview button, "Add to Timeline" with progress.
- **FillerRemovalPanel** (`ui/editor/FillerRemovalPanel.kt`) — Detect fillers/silence button, region count display, "Remove All" action.
- **AutoEditPanel** (`ui/editor/AutoEditPanel.kt`) — AI auto-edit highlight reel generator. Shows clip/music/target info cards, generate button.
- **NoiseReductionPanel** (`ui/editor/NoiseReductionPanel.kt`) — AI noise profile analysis + auto-apply DSP filters.

### Wiring (v2.6.0)
- **TTS fully wired** — TtsEngine + EffectShareEngine injected into EditorViewModel. `showTts()`/`hideTts()`/`synthesizeTts()`/`previewTts()`/`stopTtsPreview()` methods added. TtsPanel AnimatedVisibility in EditorScreen. "tts" action in ToolPanel textSubMenu.
- **Filler removal wired** — `analyzeFillers()` calls `AiFeatures.detectFillerAndSilence()`. `applyFillerRemoval()` splits + removes detected regions. FillerRemovalPanel in EditorScreen.
- **Auto edit wired** — `runAutoEdit()` calls `AiFeatures.generateAutoEdit()`, rebuilds video track. AutoEditPanel in EditorScreen.
- **Noise reduction wired** — `analyzeAndReduceNoise()` calls `AiFeatures.analyzeNoiseProfile()`, applies recommended DSP effects. NoiseReductionPanel in EditorScreen.
- **Effect library wired** — `showEffectLibrary()`/`hideEffectLibrary()`/`exportClipEffects()`/`importEffects()` methods. "effect_library" action dispatched.
- **New EditorState fields** — `showTts`, `isSynthesizingTts`, `isTtsAvailable`, `showEffectLibrary`, `showNoiseReduction`, `isAnalyzingNoise` added. All included in `dismissedPanelState()` and `hasOpenPanel`.

### Audit Fixes
- **ProjectArchive import** — `importArchive()` extracts .novacut ZIP to target directory, remaps URIs.
- **SubtitleExporter error logging** — Silent catch now logs `Log.e`.
- **SettingsRepository frame rate validation** — `coerceIn(1, 120)`.
- **SettingsScreen deprecated icon** — `Icons.AutoMirrored.Filled.ArrowBack`.

## v2.7.0 Full Wiring + Pro Features + Engine Fixes

### All Features Now Wired
- **TTS fully wired** — TtsEngine injected into ViewModel. synthesizeTts/previewTts/stopTtsPreview. TtsPanel in EditorScreen. "tts" in textSubMenu.
- **Filler removal wired** — analyzeFillers() -> detectFillerAndSilence(). applyFillerRemoval() splits + removes. FillerRemovalPanel with detect/apply flow.
- **Auto-edit wired** — runAutoEdit() -> generateAutoEdit(). Rebuilds video track with quality-ranked segments. AutoEditPanel with clip/music/target cards.
- **Noise reduction wired** — analyzeAndReduceNoise() -> analyzeNoiseProfile(). Auto-applies recommended DSP. NoiseReductionPanel.
- **Effect library wired** — exportClipEffects/importEffects via EffectShareEngine .ncfx format.

### New Pro Engines
- **MultiCamEngine** — Audio waveform cross-correlation sync. Downsamples to 8kHz mono, normalizes, searches +/- maxOffset. `findSyncOffset()` + `syncMultipleClips()`.
- **EdlExporter** — CMX 3600 EDL + FCPXML 1.10 export. Speed effects (M2 lines), transitions, source comments, asset resources. Desktop import for Premiere/Resolve/FCPX.

### Engine Bug Fixes
- **Clip.durationMs speed curve support** — 20-point average speed sampling when speedCurve exists.
- **ProjectAutoSave.release()** — Cancels scope to prevent leaked coroutines.
- **Timeline thumbnail semaphore** — Max 3 concurrent extractions (was unbounded).
- **SpeedCurveEditor logarithmic slider** — Equal physical distance for 0.1x-1x and 1x-16x ranges.

### New UI Panels
- **FillerRemovalPanel.kt** — Detect + remove flow with region count.
- **AutoEditPanel.kt** — Info cards (clips/music/target) + generate button.
- **NoiseReductionPanel.kt** — Analyze + auto-fix button with progress.

## v2.9.0 Final QA Audit (17 bugs fixed, 0 warnings remaining)

### HIGH severity fixes
- **TTS duration now queried from actual audio** — `getVideoDuration(uri)` replaces hardcoded 3000ms. TTS clips have correct sourceDurationMs.
- **TTS addition is now undoable** — `saveUndoState("Add TTS voice")` before adding clip.
- **addClipToTrack now rebuilds timeline** — TTS/voice clips immediately visible in ExoPlayer.
- **SpeedCurve export uses source time range** — `trimEndMs - trimStartMs` instead of `clip.durationMs` for correct curve normalization.

### MEDIUM severity fixes
- **TTS preview stops on editor close** — `ttsEngine.stopPreview()` added to `onCleared()`.
- **EffectShareEngine stream leak** — `openInputStream` now wrapped in `.use {}`.
- **EffectShareEngine lutPath null safety** — `cg.has("lutPath")` check instead of `optString(key, null)`.
- **Speed preset now rebuilds timeline** — `rebuildTimeline()` added after `applySpeedPreset()`.

### Compile warnings eliminated (14→3)
- `ProjectAutoSave.kt` — `optString(key, null)` → `optString(key, "").takeIf { it.isNotEmpty() }` (2 occurrences)
- `ProxyEngine.kt` — `@OptIn` → `@androidx.annotation.OptIn` for Media3 annotation
- `VideoEngine.kt` — Removed deprecated `onFlush()` override (covered by `onReset()`)
- `AiToolsPanel.kt` — `Icons.Default.VolumeOff` → `Icons.AutoMirrored.Filled.VolumeOff`
- `AudioMixerPanel.kt` — `Divider()` → `HorizontalDivider()`
- `CloudBackupPanel.kt` — `Icons.Default.Login` → `Icons.AutoMirrored.Filled.Login`
- `TextEditorSheet.kt` — `FormatAlignLeft/Right` → `Icons.AutoMirrored.Filled` variants
- `ToolPanel.kt` — `Icons.Default.VolumeUp` → `Icons.AutoMirrored.Filled.VolumeUp`
- Only 3 remaining: Media3 `EditedMediaItemSequence.Builder` deprecation (framework-level, no fix available)

## v3.0.0 — Full Engine Expansion

### New Engines (29 injectable singletons total)
- **SherpaAsrEngine** — WhisperEngine wrapper (Sherpa-ONNX dependency planned, currently delegates to built-in Whisper)
- **NoiseReductionEngine** — Spectral gate heuristic (DeepFilterNet ML planned), 5 mode enums defined
- **PiperTtsEngine** — Android System TTS fallback (Sherpa-ONNX Piper planned), 10 voice profiles defined
- **LottieTemplateEngine** — 10 built-in animated title templates, frame-by-frame rendering via TextDelegate, 4 categories
- **BeatDetectionEngine** — Pure-Kotlin spectral flux onset detection + adaptive thresholding + BPM histogram
- **LoudnessEngine** — EBU R128 measurement (K-weighting, gated blocks, LRA) + 6 platform presets + true-peak limiting
- **ChromaKeyEngine** — Professional YCbCr distance keying + smoothstep feathering + green/blue spill suppression
- **FrameInterpolationEngine** — Stub (requires NCNN+Vulkan), config/data classes defined for RIFE v4.6
- **InpaintingEngine** — LaMa-Dilated (ONNX Runtime + NNAPI), neighbor-fill fallback
- **UpscaleEngine** — Stub (requires model integration), config defined for Real-ESRGAN x4plus
- **VideoMattingEngine** — Stub (requires model integration), config defined for RobustVideoMatting
- **StabilizationEngine** — Stub (requires OpenCV), config defined for L-K/ORB algorithms
- **StyleTransferEngine** — 9 presets (AnimeGANv2 + Fast NST + OpenCV pencil sketch)
- **SmartReframeEngine** — EMA-smoothed crop trajectory, face/pose detection, 3 strategies (stationary/pan/track)
- **FFmpegEngine** — FFmpegX fallback encoder with execute(), subtitle burning, loudness normalization, audio extraction
- **SubtitleRenderEngine** — Canvas rendering + ASS/SSA file generation for burned-in subtitles
- **TapSegmentEngine** — MobileSAM with point/box prompts, mask propagation via optical flow
- **CloudInpaintingEngine** — ProPainter cloud with job submission/tracking/download API
- **TimelineExchangeEngine** — OTIO JSON export/import + FCPXML export for desktop NLE round-tripping
- **RiveTemplateEngine** — 5 interactive templates with state machine inputs
- **SoundpipeDspEngine** — Schroeder reverb, Moog ladder filter, 4 distortion types (Kotlin fallback)
- **EditCommand** — Command-based undo/redo foundation (AddClip/RemoveClip/Trim/Move/Speed/Effect/Compound)
- **ProxyWorkflowEngine** — 3-tier media (thumbnail/proxy/original), auto-switch, storage management

### Other v3.0.0 Changes
- 12 new GLSL transitions (Door Open, Burn, Radial Wipe, Mosaic Reveal, Bounce, Lens Flare, Page Curl, Cross Warp, Angular, Kaleidoscope, Squares Wire, Color Phase)
- Social media export presets (YouTube/TikTok/Instagram/Threads factory methods)
- Magnetic timeline snapping (8dp threshold, diamond indicators)
- Film grain, VHS/Retro, Glitch, Light leak, Gaussian blur shaders
- Slip/slide editing, clip grouping
- ExportService fixes (tap-to-open, error propagation, proper lifecycle)

## v3.1.0 — Code Quality & Engine Improvements
- **StateFlowExt** — Deduplicated MutableStateFlow.update() CAS-loop from 7 delegates
- **CloudInpaintingEngine** — Config persistence, input validation, dynamic isAvailable()
- **FFmpegEngine** — Reflective runtime invocation, concat demuxer, atempo chain (0.25x-16x)
- **NoiseReductionEngine** — Runtime DeepFilterNet detection, OFF mode short-circuit, cascading fallback
- **PiperTtsEngine** — Android system TTS fallback, voice deletion, runtime detection
- Waveform LRU cache (64 entries), haptic feedback, transition icons, clip reorder/move

## v3.2.0 — Performance & UX Hardening
- **Timeline pointerInput optimization** — `pointerInput(Unit)` + `rememberUpdatedState` replacing 6 recreating gesture detectors
- **VideoEngine deduplication** — Extracted `addColorGradingEffects()`, `buildTransitionEffect()`, `addOpacityAndTransformEffects()` (~200 lines removed)
- Export state fix (COMPLETE/ERROR), selection state leak fix, ExportService lifecycle fix
- Delete confirmation dialog, buffering indicator, AudioPanel null guard
- AI operation try/catch wrapping, clip/merge validation guards

## v3.3.0 — Localization, Performance & Reliability
- **Batch pixel access** — `getPixels()` batch reads in AiFeatures (~10x faster on large bitmaps)
- **Bitmap leak fix** — try-finally bitmap recycling in calculateFrameDifference()
- **Exception logging** — Log.w added to WhisperEngine, SegmentationEngine, ProjectAutoSave silent catches
- **String extraction** — 90+ hardcoded UI strings extracted to strings.xml across 15+ panels

## v3.4.0 — Dependency Activation (Reverted)
- Dependencies briefly added for engine wiring, then reverted in v3.5.0 due to unavailable artifacts
- Engine stubs remain for future integration when real dependencies become available

## v3.5.0 — Comprehensive Cleanup & Architecture

### Stub Engine Cleanup (~3,000 lines reduced)
- 11 stub engines cleaned: removed fabricated Maven coordinates (`io.github.nicholasryan:*`), fake HuggingFace URLs, `Class.forName` reflection probes, fake download/inference logic
- Each stub now has clear `Log.d` + `return null/false` with KDoc pointing to ROADMAP.md
- Engines cleaned: FrameInterpolation, Upscale, Stabilization, StyleTransfer, VideoMatting, TapSegment, CloudInpainting, FFmpeg, Rive, SherpaAsr, SmartReframe

### Bug Fixes
- **NoiseReductionEngine**: spectral gate fallback was creating empty output files — now copies input as pass-through
- **PiperTtsEngine**: removed dead Sherpa-ONNX reflection code path (system TTS fallback intact)
- **SoundpipeDspEngine**: fixed Moog filter coefficient (was missing `2π` scaling), renamed "Schroeder reverb" to "Freeverb-style"
- **BeatDetectionEngine**: replaced O(n*k) brute-force DFT with radix-2 Cooley-Tukey FFT (~12x faster, all 513 bins vs 64)

### EditorState Decomposition
- Replaced 40 `showXxx: Boolean` panel flags with `PanelVisibility` class using `Set<PanelId>`
- `PanelId` enum (40 entries) with `isOpen()`, `open()`, `close()`, `closeAll()`
- `hasOpenPanel` reduced from 15-line OR expression to `state.panels.hasOpenPanel`
- EditorState reduced from ~96 to ~57 fields
- Updated across 6 files: EditorViewModel, EditorScreen, 4 delegate files

### EditorScreen Panel Abstraction
- Created `BottomSheetSlot` composable replacing 40 identical AnimatedVisibility blocks
- Standardized enter/exit animations (slideInVertically + fadeIn / slideOutVertically + fadeOut)
- Removed dead CloudBackupPanel from EditorScreen (hardcoded `isSignedIn = false`, all callbacks toast stubs)
- EditorScreen reduced by ~89 lines

### FCPXML Export Deduplication
- Removed `EdlExporter.exportFcpxml()` (~110 lines of dead code, zero callers)
- Removed `ExportFormat.FCPXML` and `ExportFormat.OTIO` enum values (never referenced)
- All FCPXML export now goes through `TimelineExchangeEngine.exportToFcpxml()`

### Build Cleanup
- Removed 5 dead ProGuard keep-rule blocks for absent libraries
- Synced version to 3.5.0 across all files
- Documentation rewritten to honestly distinguish working vs stub features

### VideoEngine Decomposition
- Extracted `EffectBuilder.kt` (~470 lines) — pure `object` with `buildVideoEffect()`, `addColorGradingEffects()`, `buildTransitionEffect()`, `addOpacityAndTransformEffects()` as stateless extension functions
- Extracted `VolumeAudioProcessor.kt` (~75 lines) — audio fade/volume/keyframe processor, was private inner class
- Extracted `ExportTextOverlay.kt` (~200 lines) — text overlay with animation state machine, was private inner class
- VideoEngine reduced from 1,439 → 672 lines (53% reduction)

### EditorViewModel Dependency Cleanup
- Removed 5 unused engine constructor parameters: `ffmpegEngine`, `subtitleRenderEngine`, `piperTtsEngine`, `lottieTemplateEngine`, `tapSegmentEngine`
- Constructor reduced from 32 → 27 parameters

## v3.6.0 — Model Split, Compose Stability & Build Fixes

### Project.kt Split (1,081 → 230 lines)
- Split monolithic Project.kt into 9 focused model files:
  - `model/Effect.kt`, `model/ExportConfig.kt`, `model/Timeline.kt`, `model/Overlay.kt`
  - `model/Caption.kt`, `model/ColorGrading.kt`, `model/Mask.kt`, `model/EditorModels.kt`
- Project.kt now contains only Project, Track, Clip core models

### Compose Stability
- `@Immutable` on 16 model data classes (Project, Track, Clip, Effect, Transition, Keyframe, TextOverlay, etc.)
- `@Stable` on EditorState, `@Immutable` on PanelVisibility
- `derivedStateOf` for hasOpenPanel, hasClips, isClipMode in EditorScreen (reduces recompositions)
- Waveforms `Map<String, FloatArray>` → `Map<String, List<Float>>` for Compose structural equality

### Build Fixes
- ProxyWorkflowEngine: added missing `kotlinx.coroutines.flow.update` import, fixed type inference in `deleteAllProxies()`
- EditorViewModel: fixed `showVoiceoverRecorder` → `PanelId.VOICEOVER_RECORDER` (panel system migration incomplete)
- Removed dead TtsPanel Piper voice selector (empty click handlers)
- Moved hardcoded hilt-work deps to version catalog, pinned all dependency versions

## Next Steps
- Integrate real dependencies when available (Sherpa-ONNX, DeepFilterNet, NCNN, OpenCV, FFmpeg)
- Decompose EditorViewModel (3,300 lines) — convert delegates to proper Hilt-injectable classes
- Bundle AI engines into `AiEnginesBundle` facade to reduce constructor parameters further
- True dual-texture blend mode compositing via Media3 Compositor API
- ProjectArchive.importArchive() full implementation

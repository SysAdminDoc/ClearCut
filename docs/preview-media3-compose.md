# Preview Panel Media3 Compose Evaluation

Last reviewed: 2026-05-17.

## Decision

Keep NovaCut's bespoke `PreviewPanel` for the editor preview. Do not replace it
with the `media3-ui-compose-material3` `Player` Composable in Media3 1.10.1.

`PreviewPanelMedia3ComposePolicy` records this decision in testable Kotlin so
future Media3 upgrades can be evaluated against the same requirements.

## Evidence

- Local preview code: `PreviewPanel.kt` uses a `PlayerView` surface with native
  controls disabled, then layers NovaCut-specific gap recovery, still-image
  fallback, buffering state, a scopes toggle, timecode chrome, and transform
  gestures over the rendered frame.
- Local mini player code: `MiniPlayerBar.kt` seeks through NovaCut's
  timeline-relative `playheadMs / totalDurationMs` contract instead of directly
  trusting the underlying player duration.
- Android Developers documentation for
  `media3-ui-compose-material3`
  (https://developer.android.com/media/media3/ui/compose-material3) says the
  module provides a `Player` Composable, Material3 playback buttons,
  `ProgressSlider`, and `PositionAndDurationText`, with state management
  handled internally.
- Android Developers documentation for Compose-based Media3 UI
  (https://developer.android.com/media/media3/ui/compose) lists
  `ContentFrame` and `PlayerSurface` in the lower-level `media3-ui-compose`
  module, which may be useful later for replacing only the surface wrapper.
- The `ProgressSlider` API reference
  (https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/material3/indicator/ProgressSlider.composable)
  says the slider displays player position and performs the underlying
  `Player.seekTo` internally before `onValueChangeFinished`; that conflicts
  with NovaCut's trim-aware timeline seek path.
- Google Maven metadata
  (https://dl.google.com/dl/android/maven2/androidx/media3/media3-ui-compose-material3/maven-metadata.xml)
  reports latest/release `1.10.1` on 2026-05-17. The resolved AAR SHA-256 is
  `0e0789cef85d948f924c0cec365021a56f6cc63b8c9888cacd05357f83e00112`.

## Parity Gap

`media3-ui-compose-material3` 1.10.1 does not directly satisfy these preview
requirements:

- Timeline-relative seeking over the edited project duration.
- Gap state and jump-to-next-content behavior.
- Still-image preview fallback through Coil.
- Clip transform gesture editing with start/end callbacks.
- NovaCut scopes toggle and custom Mocha preview chrome.

## Future Revisit Criteria

Revisit full replacement only if Media3 exposes an externally controlled
progress/timeline model that does not call `Player.seekTo` internally, plus
extension points for editor overlay gestures and gap/still-image states.

Targeted adoption remains plausible:

- `media3-ui-compose` `ContentFrame` or `PlayerSurface` could replace the
  current `AndroidView(PlayerView)` surface once it preserves the existing
  `onPlayerViewReady` integrations.
- Individual Material3 playback buttons can be reconsidered if they can be
  themed to Mocha and wired through NovaCut callbacks instead of native player
  commands.

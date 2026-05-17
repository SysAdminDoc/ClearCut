# Media3 ProgressSlider Evaluation

Last reviewed: 2026-05-17.

## Decision

Do not replace NovaCut's timeline ruler or `MiniPlayerBar` slider with
`media3-ui-compose-material3` `ProgressSlider` in Media3 1.10.1.

`TimelineProgressSliderPolicy` records the decision in testable Kotlin.

## Evidence

- `Timeline.kt` maps pointer position to edited project time through
  `scrollOffsetMs`, `pixelsPerMs`, zoom level, visible duration, markers, snap
  targets, clip hit areas, and scrub lifecycle callbacks.
- `TimelineOverviewBar` renders a separate project overview with visible-window
  and playhead affordances. It is not a player progress slider.
- `MiniPlayerBar.kt` uses a standard Material3 `Slider` with external
  `playheadMs / totalDurationMs` state and calls NovaCut's `onSeek(Long)`
  callback.
- Android Developers `ProgressSlider` API docs
  (https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/material3/indicator/ProgressSlider.composable)
  describe a player-position slider. The underlying `Player.seekTo` operation
  is performed internally before `onValueChangeFinished`.
- Android Developers Media3 Material3 Compose docs
  (https://developer.android.com/media/media3/ui/compose-material3) position
  `ProgressSlider` as part of player controls, not as a zoomed NLE ruler.

## Parity Gap

Media3 1.10.1 `ProgressSlider` does not provide:

- Externally supplied project-timeline value.
- External seek callback without internal `Player.seekTo`.
- Timeline zoom and scroll-window mapping.
- Marker, snap, clip, and track overlays.
- NovaCut scrub start/end callbacks.

## Revisit Criteria

Revisit only if Media3 exposes an externally controlled progress component that
can render a project timeline value, delegate seeks without performing
`Player.seekTo`, and accept editor overlays or slots for markers, clips, and
visible-window state.

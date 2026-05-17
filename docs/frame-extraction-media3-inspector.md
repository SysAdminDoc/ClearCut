# Frame Extraction and Media3 Inspector

Last reviewed: 2026-05-17.

## Decision

Do not add `androidx.media3:media3-inspector-frame:1.10.1` yet. NovaCut's
current timeline thumbnail strip, contact sheet, and freeze-frame paths can stay
on `MediaMetadataRetriever` until they need HDR fidelity, exported effect
stacks, or custom decoder selection.

`FrameExtractionPolicy` records the migration gate in code.

## Current Local Paths

- `VideoEngine.extractThumbnail(...)` uses `MediaMetadataRetriever.getFrameAtTime`
  with `OPTION_CLOSEST_SYNC`, scales the bitmap, caches it, and feeds timeline
  strips plus contact-sheet/export thumbnail callers.
- `VideoEngine.extractThumbnailStrip(...)` builds thumbnail strips through
  `extractThumbnail(...)`.
- `VideoEngine.extractFrameToFile(...)` uses `MediaMetadataRetriever` for the
  current JPEG freeze-frame export.
- Additional AI analysis helpers in `AiFeatures`, `AiThumbnailEngine`,
  `ColorMatchEngine`, `FlashSafetyEngine`, `TalkingHeadFramingEngine`, and
  `SegmentationEngine` sample representative frames with
  `MediaMetadataRetriever`.
- `gradle/libs.versions.toml` and `app/build.gradle.kts` do not currently
  depend on `media3-inspector` or `media3-inspector-frame`.
- The source tree has no `FrameExtractor` or `ExperimentalFrameExtractor`
  imports.

## External Evidence

- AndroidX Media3 release notes
  (https://developer.android.com/jetpack/androidx/releases/media3) say users
  should depend on the new `:media3-inspector-frame` module and import
  `androidx.media3.inspector.frame.FrameExtractor`.
- Media3 Inspector docs
  (https://developer.android.com/media/media3/inspector) describe
  `FrameExtractor` as the frame/thumbnail replacement for platform
  `MediaMetadataRetriever`.
- Google Maven metadata
  (https://dl.google.com/dl/android/maven2/androidx/media3/media3-inspector-frame/maven-metadata.xml)
  reports latest/release `1.10.1` on 2026-05-17.
- The resolved `media3-inspector-frame:1.10.1` AAR is 24,988 bytes, has SHA-256
  `ded4a5275a5f977afaa3fb4b1b933667629e2526efbfb94b4bcf2b96fc20e2a0`, and
  contains no native library entries.
- Source inspection of `FrameExtractor` 1.10.1 shows `setEffects(...)`,
  `setMediaCodecSelector(...)`, `setExtractHdrFrames(...)`, `getFrame(...)`,
  and `getThumbnail()`.

## Migration Criteria

Stay on `MediaMetadataRetriever` for:

- Small cached SDR timeline thumbnails.
- Contact-sheet thumbnails.
- Representative AI analysis frames where exact effect/HDR fidelity is not
  user-visible.

Use `media3-inspector-frame` with
`androidx.media3.inspector.frame.FrameExtractor` for:

- HDR review frames or still export that must preserve HDR bitmap fidelity.
- Effect-aware thumbnails that should match Media3 export/preview effects.
- Custom decoder selection or software-decoder fallback requirements.

Do not use:

- `androidx.media3.inspector.FrameExtractor`
- `androidx.media3.transformer.ExperimentalFrameExtractor`

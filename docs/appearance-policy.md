# NovaCut Appearance Policy

Last updated: 2026-06-04

NovaCut exposes three appearance modes:

- System (dark canvas): follows the user's preference entry point but resolves
  to the dark editing canvas until a light palette has complete screenshot,
  video-preview, and contrast QA.
- Dark: the standard Catppuccin Mocha-inspired editing canvas.
- High Contrast Dark: the dark canvas with boosted semantic text, strokes,
  outlines, selected chips, and disabled-state tokens.

## Light Palette Rationale

NovaCut edits video and image content where the preview surface must stay
visually neutral. A light palette can change perceived contrast/color around
the preview and needs full gallery, editor, export, Settings, tablet, and
caption QA before it becomes a selectable production mode. Until then, System
is intentionally dark-only and the explicit user-facing accessibility mode is
High Contrast Dark.

## Regression Gates

- `NovaCutAppearancePolicyTest` locks text contrast, non-text indicator
  contrast, selected-chip readability, and the current System-to-Dark policy.
- `NovaCutSmokeTest` enables Compose accessibility checks and runs root checks
  on the project gallery, editor empty state, media picker, export sheet,
  Settings, and privacy dashboard.
- `docs/appearance-policy.md` is the release-facing explanation for why a light
  palette is deferred.

## Follow-Up Scope

Some older editor surfaces still reference raw `Mocha` tokens directly. Shared
chrome components now consume `LocalNovaCutColors`, but remaining direct-token
surfaces should migrate as they are touched so High Contrast Dark eventually
covers every panel, overlay, timeline affordance, and canvas decoration.

# NovaCut Play Listing Assets

Last updated: 2026-06-04

NovaCut's Play listing package lives under
`fastlane/metadata/android/en-US/`.

## Structure

- `title.txt`, `short_description.txt`, and `full_description.txt` contain the
  locale text metadata.
- `privacy_policy_url.txt` stores the public privacy policy URL to enter in
  Play Console.
- `images/icon.png` is the 512x512 32-bit PNG Play icon.
- `images/featureGraphic.png` is the 1024x500 feature graphic.
- `images/phoneScreenshots/` contains four 1080x1920 phone screenshots.
- `images/tenInchScreenshots/` contains four 1920x1080 tablet screenshots.
- `images/asset_inventory.json` records alt text, captions, dimensions, and SVG
  source files for every committed PNG.
- `images/_source/` contains deterministic SVG source art for the committed
  PNGs.

## Commands

Regenerate assets when the listing art changes:

```powershell
python scripts\generate_play_listing_assets.py
```

Validate committed metadata and disclosure files:

```powershell
python scripts\validate_play_listing_assets.py
```

The CI release gate runs the validator. The generator is not required in CI
because the rendered PNGs are committed.

## Source Requirements Checked

- Google Play preview assets: app icon must be a 512x512 32-bit PNG with alpha,
  the feature graphic must be a 1024x500 JPEG or 24-bit PNG, and screenshots
  should show the app experience.
- Google Play Data safety: all published apps need a completed Data safety form
  and privacy policy, and the form should reflect app and SDK behavior.
- Fastlane supply: locale images live under `images/`, with `featureGraphic`,
  `icon`, `phoneScreenshots/`, `sevenInchScreenshots/`, and
  `tenInchScreenshots/` as supported names.

References:

- https://support.google.com/googleplay/android-developer/answer/9866151
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://docs.fastlane.tools/actions/supply/

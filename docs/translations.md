# Translation contribution guide

ClearCut keeps the English resource file as the source of truth. Spanish is the first shipped translation. A new locale can be contributed without changing Kotlin UI code.

## Add a locale

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<BCP47>/strings.xml`, where `<BCP47>` is the Android locale tag you are adding, such as `fr` or `pt-BR`.
2. Translate every string, plural item, and array item. Keep resource names, formatting placeholders such as `%1$s`, and markup intact.
3. Add `<locale android:name="<BCP47>" />` to `app/src/main/res/xml/locales_config.xml`.
4. Add the matching Google Play listing files under `fastlane/metadata/android/<Play locale>/`: `title.txt`, `short_description.txt`, `full_description.txt`, and `privacy_policy_url.txt`. Use the Play locale folder name, such as `fr-FR` or `pt-BR`.
5. Add a numbered changelog file under `fastlane/metadata/android/<Play locale>/changelogs/` when the next release includes the translation.

## Validate before opening a pull request

From the repository root, run:

```text
rtk gradlew :app:testQaUnitTest --tests com.novacut.editor.LocaleResourceCoverageTest --tests com.novacut.editor.UiHardcodedLiteralRatchetTest --dependency-verification strict --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2048m' '-Dorg.gradle.workers.max=1'
```

The locale test checks resource keys and formatting placeholders against English, verifies the locale config entry, and scans every translated resource directory. The UI literal test scans every Kotlin source file under `app/src/main/java/com/novacut/editor/ui` so new UI copy cannot bypass resources. Run the full QA unit suite and `:app:assembleQa` before requesting review.

## Pull request checklist

- Include the resource folder, locale config entry, and Play metadata in one change.
- Keep product names and technical terms consistent with the English UI.
- Mention any intentional untranslated fallback in the PR and add a focused test allowlist entry.
- Attach a screenshot of the locale in the editor when layout or text expansion could affect the result.

Issue #52 is the public coordination point for translation questions. Open a focused pull request with the files above and paste the validation command output in the description so the review can start from reproducible evidence.

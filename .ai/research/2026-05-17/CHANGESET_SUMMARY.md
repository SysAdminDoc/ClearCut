# Changeset Summary - 2026-05-17

## Files Created

| File | Purpose |
|---|---|
| `PROJECT_CONTEXT.md` | Canonical committed project memory for future sessions. |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | Local reconnaissance memo. |
| `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` | Instruction/memory/doc reconciliation. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Local and external source index. |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | Search strategy, query classes, saturation notes, and limitations. |
| `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` | Commercial and open-source competitor comparison. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Raw harvested opportunity backlog. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Scored and tiered roadmap candidates. |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | Dependency, native, release, model integrity, and privacy review. |
| `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` | Model, dataset, integration, packaging, and evaluation review. |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | Summary of this research artifact changeset. |

## Files Modified

| File | Purpose |
|---|---|
| `ROADMAP.md` | Updated last refresh to 2026-05-17 and added Round 7 deep-research synthesis plus new Now-priority rows. |
| `app/src/main/java/com/novacut/editor/engine/CaptionTranslationEngine.kt` | Fixed the supported-language switch for the new Bergamot model variant. |
| `app/src/main/java/com/novacut/editor/model/Project.kt` | Added optional clip display name storage needed by compound-clip breadcrumbs. |
| `app/src/main/java/com/novacut/editor/engine/KeyframeBezierGraph.kt` | Fixed cubic-bezier easing evaluation to solve x(time) before reading y(value). |
| `app/src/main/java/com/novacut/editor/engine/SpeakerSwitchPlanner.kt` | Fixed initial speaker-angle selection when explicit angle assignments reserve the default angle. |
| `app/src/test/java/com/novacut/editor/engine/CompoundNavStackTest.kt` | Switched JVM test fixtures from `Uri.EMPTY` to the repo's `FakeUri`. |
| `app/src/test/java/com/novacut/editor/engine/AdjustmentLayerEngineTest.kt` | Aligned the no-layer plan expectation with the engine's documented whole-clip segment behavior. |

## Local-Only Configuration

`local.properties` was corrected locally so Gradle can find the Android SDK in this workspace:

```properties
sdk.dir=C:\\Users\\--\\AppData\\Local\\Android\\Sdk
```

This file is ignored and intentionally not committed.

## Verification

- `git diff --check`: no whitespace errors; Git reported the normal Windows line-ending warning that `ROADMAP.md` LF will be replaced by CRLF the next time Git touches it.
- `.\gradlew.bat :app:compileDebugKotlin --dry-run`: successful after correcting ignored local `local.properties`.
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon`: successful.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`: compiles and executes 389 tests, with 388 passing and 1 remaining failure:
  - `AutoSaveStateTest > deserialize_capsPathologicalRecoveredCollections`
  - Failure: `java.util.NoSuchElementException at AutoSaveStateTest.kt:287`
  - Notes: the remaining failure occurs after source compile/test-compile succeeds. Earlier compile blockers and unrelated assertion clusters found during verification were repaired in this changeset.

## Commit

This file is part of the final local commit for the 2026-05-17 research pass.

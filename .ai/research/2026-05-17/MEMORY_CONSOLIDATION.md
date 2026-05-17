# Memory Consolidation - 2026-05-17

## Purpose

This memo reconciles tool instructions, repo-local memory, project docs, prior roadmap material, changelog state, and live source evidence. It does not overwrite tool-specific files. Durable project facts were consolidated into root [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md).

## Files Inventoried

| File | Status | Use |
|---|---|---|
| `AGENTS.md` | Local, ignored/untracked | Codex-facing instruction bridge. Points to shared Claude/global instructions and memory. |
| `CLAUDE.md` | Local, ignored/untracked | Claude-facing working notes. Useful but includes stale version/build statements. |
| `.claude/CLAUDE.md` | Local, ignored/untracked | Nested duplicate/variant of Claude working notes. Useful for workflow context, not current truth. |
| `README.md` | Tracked | Product and build overview. Treat as current unless source contradicts it. |
| `ROADMAP.md` | Tracked | Current implementation roadmap. Updated by this run with Round 7. |
| `CHANGELOG.md` | Tracked | Shipped release history. |
| `CROSS-PROJECT-ROADMAP.md` | Tracked | Useful backlog and cross-project idea source, but stale version label. |
| `docs/models.md` | Tracked | Best source for model policy, licenses, delivery channels, and activation gates. |
| `docs/templates.md` | Tracked | Best source for template/plugin compatibility and animation ecosystem. |
| `gradle/libs.versions.toml` | Tracked | Dependency catalog truth. |
| `app/build.gradle.kts` | Tracked | Android SDK/version truth. |

## External Memory Used

Codex memory and shared Claude memory both describe NovaCut as a Kotlin/Compose Android editor with a strong roadmap workflow, model/premium/backup-import work, and release-oriented verification expectations. Those memories were used as orientation only. Every current claim in `PROJECT_CONTEXT.md` and `ROADMAP.md` was checked against live repository files or current external sources.

## Resolved Claims

| Claim | Resolution | Evidence |
|---|---|---|
| Current app version | `v3.74.9`, `versionCode 146` | `app/build.gradle.kts`, `strings.xml`, README, ROADMAP |
| Android SDK targets | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` | `app/build.gradle.kts` |
| Media3 version | 1.10.1 | `gradle/libs.versions.toml`, README |
| Current branch state | `master` ahead of `origin/master` by 33 commits | `git status --short --branch` |
| Tracked instruction files | `AGENTS.md`, `CLAUDE.md`, `.claude/` are ignored/untracked | `git ls-files`, `.gitignore` |
| Current local JDK path | `C:\Program Files\Android\openjdk\jdk-21.0.8` | Local `java -version` via explicit path |
| Current local SDK path | `C:\Users\--\AppData\Local\Android\Sdk` | Local filesystem check and corrected ignored `local.properties` |

## Stale or Contradictory Claims

| Source | Stale/Conflicting Claim | Current Resolution |
|---|---|---|
| `CLAUDE.md` top summary | Older references to compile/target SDK 35 | Source now targets SDK 36. |
| `CLAUDE.md` top summary | Older Media3 1.10.0 reference | Dependency catalog now uses Media3 1.10.1. |
| `CLAUDE.md` top summary | Older Room/database/schema references | Treat source and migrations as truth; do not rely on prose-only DB version claims. |
| `CLAUDE.md` top summary | Older line-count descriptions for large files | Live code has shifted; use `rg`/line counts before making split/refactor decisions. |
| `CROSS-PROJECT-ROADMAP.md` | Advertises an older current version (`v3.49.0`) | Keep as backlog inspiration, not current status. |
| Prior memory | Mentions v3.69/v3.71-era shipped work | Useful history, but current version is v3.74.9. |

## Instruction Reconciliation

`AGENTS.md` and `CLAUDE.md` remain tool-specific and should not be merged away. The committed durable context now lives in `PROJECT_CONTEXT.md`, while the tool files can continue to hold workflow preferences and local-only reminders.

Resolution policy for future sessions:

1. Treat `app/build.gradle.kts`, `gradle/libs.versions.toml`, README, ROADMAP, CHANGELOG, and source files as current truth.
2. Treat `CLAUDE.md`, `.claude/CLAUDE.md`, and external memories as orientation that must be verified.
3. Keep contradictions documented in research notes instead of silently choosing a tool-specific file.
4. Do not delete or wholesale rewrite local instruction files unless the user explicitly asks.

## Consolidated Durable Project Facts

- NovaCut is a privacy-first Android video editor with a Compose/Media3/Room/Hilt stack.
- Feature work should prioritize already-scaffolded engines and user-visible integration over more speculative scaffolding.
- The current roadmap is heavily model-, export-, and platform-readiness driven.
- Android 16 and 16 KB native library compliance are hard release gates.
- Model activation must maintain checksum, license, F-Droid, Play Asset Delivery, and explicit-download discipline.
- Diagnostic export, model registry closure, dependency stabilization, and FFmpeg/license decisions are the highest-leverage near-term work.

## Open Conflicts

No blocking conflicts remain after source reconciliation. The main unresolved item is procedural: ignored local instruction files are useful but stale in places. The committed mitigation is `PROJECT_CONTEXT.md`; future sessions should refresh that file when live architecture or release flow materially changes.

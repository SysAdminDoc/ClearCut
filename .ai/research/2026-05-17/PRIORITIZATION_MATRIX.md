# Prioritization Matrix - 2026-05-17

## Scoring Model

Scores are 1-5. Higher is better except effort and risk, where higher means harder/riskier. Priority favors high impact/reach/confidence with manageable effort/risk.

| Field | Meaning |
|---|---|
| Impact | Product/user/release value if completed. |
| Reach | How broadly it affects users or future work. |
| Confidence | Strength of local/external evidence. |
| Effort | Engineering effort and integration complexity. |
| Risk | Regression, license, privacy, platform, or dependency risk. |

## Now - Next 1-2 Release Cycles

| ID | Candidate | Impact | Reach | Confidence | Effort | Risk | Why Now |
|---|---|---:|---:|---:|---:|---:|---|
| P0.1 | 16 KB native-library verification and release gate | 5 | 5 | 5 | 2 | 4 | `targetSdk = 36` makes this a release blocker for native deps. |
| P0.2 | Settings diagnostic ZIP UI | 5 | 4 | 5 | 2 | 2 | Engine exists; missing user workflow. Strong trust/recovery payoff. |
| P0.3 | Model registry checksum/license/PAD closure | 5 | 4 | 5 | 3 | 3 | Prevents unsafe model activation and protects F-Droid/privacy posture. |
| P0.4 | Dependency stabilization train | 4 | 5 | 4 | 3 | 3 | Several libraries have newer trains; Media3 is already current. |
| P0.5 | FFmpeg 16 KB/license decision document and spike | 5 | 4 | 4 | 3 | 5 | Unblocks many export features but carries license/native-distribution risk. |
| P0.6 | Media3 modular UI/effect parity spike | 3 | 3 | 4 | 2 | 2 | Complete for R6.10: Lottie adoption where parity holds, Player/ProgressSlider non-adoption decisions, and inspector-frame policy gate for HDR/effect/custom-decoder frame extraction. |
| P0.7 | Strings/i18n extraction audit | 3 | 4 | 4 | 2 | 1 | Mechanical quality pass; improves localization and accessibility readiness. |

## Next - 3-5 Release Cycles

| ID | Candidate | Impact | Reach | Confidence | Effort | Risk | Gate |
|---|---|---:|---:|---:|---:|---:|---|
| P1.1 | DeepFilterNet 3 activation | 4 | 3 | 4 | 3 | 3 | Model registry and dependency/native checks. |
| P1.2 | Oboe runtime integration | 4 | 3 | 4 | 3 | 3 | Native/dependency decision and audio regression tests. |
| P1.3 | Mixed copy/re-encode composer | 5 | 4 | 4 | 4 | 4 | FFmpeg decision and export matrix tests. |
| P1.4 | Closed audio-description export | 4 | 3 | 4 | 3 | 2 | TTS/export integration and accessibility QA. |
| P1.5 | Keyframe graph UI | 4 | 3 | 4 | 3 | 2 | Existing graph model; needs Compose UX. |
| P1.6 | Compound clip open/exit UX | 4 | 3 | 4 | 3 | 2 | Existing navigation stack; needs timeline UI. |
| P1.7 | Cut Assistant review completion | 4 | 4 | 4 | 3 | 2 | Existing filler/merge helpers; needs user-facing review polish. |
| P1.8 | Caption translation path | 4 | 3 | 3 | 4 | 3 | Model delivery and evaluation harness. |
| P1.9 | OTIO/FCPXML import parser | 4 | 2 | 4 | 4 | 3 | Interop tests and project mapping rules. |

## Later - Beyond 5 Release Cycles

| ID | Candidate | Impact | Reach | Confidence | Effort | Risk | Reason to Defer |
|---|---|---:|---:|---:|---:|---:|---|
| P2.1 | RIFE frame interpolation | 4 | 2 | 3 | 5 | 5 | Native/Vulkan/model complexity; device-tier gating. |
| P2.2 | Real-ESRGAN upscaling | 4 | 2 | 3 | 5 | 5 | Large model and performance concerns. |
| P2.3 | Robust Video Matting | 4 | 2 | 3 | 5 | 4 | Large model/PAD/eval needed. |
| P2.4 | SAM 2.1 tap-to-segment | 4 | 3 | 3 | 5 | 4 | Model activation and interaction complexity. |
| P2.5 | Stock asset library | 3 | 3 | 3 | 4 | 4 | Network/provider/licensing policy required. |
| P2.6 | Template marketplace | 4 | 3 | 4 | 5 | 4 | Registry/security/moderation/compatibility surface is large. |
| P2.7 | Cross-device project sync | 4 | 3 | 3 | 5 | 5 | Backend/security/account design required. |
| P2.8 | Live streaming output | 3 | 2 | 3 | 5 | 5 | Network/reliability matrix is large. |
| P2.9 | 360/VR editing | 3 | 1 | 3 | 5 | 4 | Specialist workflow; lower immediate reach. |

## Under Consideration

| Candidate | Current Position |
|---|---|
| Gemini Nano / ML Kit GenAI Prompt API | Watch item. Useful for local prompt assistance only where device support exists; no dependency on it for core UX. |
| Cloud-only generative video/lip-sync | Keep behind explicit provider consent and privacy policy. Do not make it core editor behavior. |
| OpenCut-style web/desktop stack ideas | Watch product/UX patterns, not architecture. NovaCut should remain Android-native. |
| Gyroflow integration | Prefer sidecar/project import and algorithm reference before full native gyro implementation. |
| APV / Ultra HDR v2 | APV source chip and Ultra HDR gainmap direction detection are complete; continue tracking real-device APV decode QA and gain-map still export. |

## Rejected or Hold

| Candidate | Decision |
|---|---|
| Bundling unpinned model binaries | Reject until SHA/license/delivery metadata is complete. |
| Unconditional cloud upload for AI tools | Reject. Violates local-first/privacy positioning. |
| GPL-only FFmpeg flavor in the default Play build without explicit posture | Hold. Needs license/channel decision. |
| Blind AGP/Kotlin latest bump to pre-release lines | Hold. Use a toolchain branch and release-note review. |
| Adding new speculative stubs before completing existing scaffolded surfaces | Defer. The repo already has enough scaffolds. |

## Final Priority Order

1. Add repeatable Android 16 / 16 KB native-library verification.
2. Ship Settings diagnostic ZIP UI.
3. Close model registry hashes/licenses/delivery gates.
4. Run dependency stabilization train with build/test evidence.
5. Decide and spike FFmpeg 16 KB/license path.
6. Completed Media3 R6.10 modular audit/adoption: Lottie adoption, Player/ProgressSlider non-adoption, and inspector-frame migration policy.
7. Finish key existing UI integrations: Cut Assistant review, compound clip navigation, keyframe graph, adjustment layers.
8. Resume model activation only after model governance and evaluation harnesses are in place.

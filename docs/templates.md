# NovaCut — Template Plugin Format & Animation-Tool Compatibility Matrix

This doc covers the NovaCut plugin format family (R5.7a, [PluginRegistry](../app/src/main/java/com/novacut/editor/engine/PluginRegistry.kt)) and the compatibility matrix between NovaCut templates and third-party animation tools (R5.7c).

**Last refresh:** 2026-05-16 · See [ROADMAP.md](../ROADMAP.md) Round 5 §R5.7.

---

## 1. NovaCut plugin format family

NovaCut treats template-like assets as a small family of share-able plugins, each detected by file extension via `PluginRegistry.kindForFileName()`:

| Extension | Kind | MIME | Engine | Notes |
|---|---|---|---|---|
| `.novacut-template` | `TEMPLATE` | `application/octet-stream` | [TemplateManager](../app/src/main/java/com/novacut/editor/engine/TemplateManager.kt) + [TemplateCompatibility](../app/src/main/java/com/novacut/editor/engine/TemplateCompatibility.kt) | Project templates with typed slots, brand tokens, motion presets, and compatibility metadata. |
| `.ncfx` | `EFFECT_PACK` | `application/octet-stream` | [EffectShareEngine](../app/src/main/java/com/novacut/editor/engine/EffectShareEngine.kt) | Effect chains, including portable LUT references (filename-based, not absolute paths). |
| `.ncstyle` | `STYLE_PACK` | `application/octet-stream` | Pending (planned alongside the [CaptionStyleGallery](../app/src/main/java/com/novacut/editor/ui/editor/CaptionStyleGallery.kt) marketplace work). | Caption + text style packs. |
| `.cube` / `.3dl` | `LUT_CUBE` / `LUT_3DL` | `text/plain` | [LutEngine](../app/src/main/java/com/novacut/editor/engine/LutEngine.kt) | 3D LUT files (already importable; promoted to first-class plugin so the share sheet treats them like the others). |
| `.ncfxd` | `OPENFX_DESCRIPTOR` | `application/json` | [OpenFxDescriptor](../app/src/main/java/com/novacut/editor/engine/OpenFxDescriptor.kt) | R5.7b — read-only metadata that maps a NovaCut effect's parameters to OpenFX-named equivalents so NLE round-trip (C.14) can preserve effect intent. |

Detection rule: longest matching extension wins. `X.ncfxd` resolves to `OPENFX_DESCRIPTOR`, not `EFFECT_PACK`.

---

## 2. Animation-tool compatibility matrix (R5.7c)

NovaCut templates can ship Lottie / Rive / dotLottie animations as title overlays. This table records what survives a *round trip* between NovaCut and each upstream format. Use it before promising a template will work outside NovaCut.

Legend: ✅ = round-trips · ⚠ = degrades / requires shim · ❌ = not preserved.

| Feature | Lottie (JSON) | dotLottie (.lottie zip) | Rive (.riv) | Glaxnimate (.glaxnimate) |
|---|---|---|---|---|
| Shape layers | ✅ | ✅ | ✅ (native) | ✅ |
| Path keyframes | ✅ | ✅ | ✅ | ✅ |
| Solid / gradient fills | ✅ | ✅ | ✅ | ✅ |
| Trim path animation | ✅ | ✅ | ⚠ (manual recreate) | ✅ |
| Static text layers | ✅ | ✅ | ⚠ (text is bitmap on import) | ✅ |
| Dynamic text via TextDelegate (NovaCut caption/template slots) | ✅ (via `LottieOverlayEffect.textReplacements`) | ✅ | ⚠ (Rive text inputs are typed; explicit binding required) | ❌ (no runtime text API) |
| Theming (color tokens) | ⚠ (manual property substitution) | ✅ (dotLottie color overrides) | ✅ (state-machine color inputs) | ⚠ (manual export step) |
| State machines / interactive inputs | ⚠ (Lottie 7.x ships state machines per R6.16; NovaCut currently pins 6.7.1) | ✅ (dotLottie state machines, requires 7.x) | ✅ (native; A.13 stub ready) | ❌ |
| Vector strokes (line cap, dash) | ✅ | ✅ | ✅ | ✅ |
| Bitmap layers | ✅ | ✅ | ⚠ (limited) | ⚠ (embed cost) |
| Audio | ❌ | ❌ | ❌ | ❌ |
| Embedded fonts | ⚠ (Lottie supports font references; bundling depends on viewer) | ✅ | ⚠ (Rive treats fonts as assets — must bundle) | ⚠ |
| Export back to source format from NovaCut | ❌ | ❌ | ❌ | ❌ |

Notes:
- **NovaCut is import-only** for animation tools. The export side (NovaCut → Lottie / Rive / Glaxnimate JSON) is not in scope; creators should keep their source files in the upstream tool and re-import on change.
- **R6.16 dotLottie path:** when `lottie-compose:7.x` lands with the state-machine API, the dotLottie column becomes the recommended path because it carries state machines + theming + 10-15× smaller bundle size than equivalent JSON.
- **A.13 Rive:** parked at Under Consideration per the Forward View (R6.16 makes Lottie state machines competitive). Keep the `RiveTemplateEngine` stub and reflection probe so a future creator workflow can flip A.13 back to Next with one dep change.

---

## 3. Plugin compatibility checks before import

For each plugin file the user imports, the registry kind drives the validation pipeline:

```
PluginRegistry.kindForFileName(name)
  ├─ TEMPLATE          → TemplateCompatibility.validate(...) (schema, app version, required-feature gate)
  ├─ EFFECT_PACK       → EffectShareEngine.parsePack(...) + (future) NovaCutVersionCheck
  ├─ STYLE_PACK        → pending: CaptionStyleCompatibility (mirror of TemplateCompatibility)
  ├─ LUT_CUBE/3DL      → LutEngine.parse(...) — already validates shape + dimensions
  └─ OPENFX_DESCRIPTOR → OpenFxDescriptor.fromJson(...) (schema version, required fields, range sanity)
```

Validation failures surface as a structured `ImportReport` (already wired for `.novacut-template`); same UX must apply to the new kinds when their loaders land.

---

## 4. License hygiene per shared format

Shared assets must carry redistributable licenses. The plugin formats are vehicle-only — the creator is responsible for the rights of the embedded content. NovaCut's responsibility:

- Refuse to import files whose declared license string conflicts with redistribution (e.g. AnimeGAN model weights with research-only clauses for A.11).
- Surface the declared license on the import-confirmation sheet so the user sees what they're accepting.
- For OpenFX descriptors (R5.7b): the descriptor itself is metadata-only and not copyrightable in the legal sense, but the upstream OpenFX plugin name it maps to may carry trademark restrictions (e.g. "Resolve FX" is a Blackmagic trademark). Treat OpenFX IDs as opaque identifiers; do not surface the upstream plugin name in NovaCut UI without verifying the trademark grant.

---

## 5. Reproducibility hooks

R5.6c (reproducible release builds) extends to plugins. Every built-in `.ncfx` / `.ncstyle` / `.cube` shipped in the release APK must:

- Be byte-identical across rebuilds of the same source.
- Carry a SHA-256 column in [docs/models.md](models.md) §1 when it lives outside the source tree.
- Pass `PluginRegistry.kindForFileName()` round-trip (the loader and the registry must agree on the kind).

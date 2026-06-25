package com.novacut.editor.engine

import android.net.Uri
import java.io.File
import java.util.Locale

/**
 * R5.7a — Plugin registry.
 *
 * ClearCut's first plugin format was `.clearcut-template`, defined by
 * [TemplateManager] + [TemplateCompatibility]. This registry promotes the
 * concept to a small family of share-able assets so they all flow through
 * one share-intent path and one compatibility-check entry point:
 *
 *   - `.clearcut-template`  — project templates (existing).
 *   - `.ncfx`              — effect packs (chains of ClearCut effects,
 *                            including portable LUT references that are
 *                            filename-based not absolute-path-based).
 *   - `.ncstyle`           — caption + text style packs.
 *   - `.cube` / `.3dl`     — 3D LUT files (already importable via LutEngine;
 *                            promoted to first-class plugin so the share
 *                            sheet treats them like the others).
 *   - `.ncfxd`             — OpenFX descriptor JSON (R5.7b). Carries metadata
 *                            that maps a ClearCut effect's parameters to the
 *                            OpenFX-named parameters, so NLE round-trip
 *                            (C.14) can preserve effect intent across
 *                            DaVinci Resolve / Premiere imports.
 *
 * This object is the *registry*, not the loader. Each Kind already has a
 * concrete loader engine (TemplateManager / LutEngine / etc.); the registry
 * exists to provide one shared classification + share-intent surface so the
 * UI doesn't have to repeat the type-detection logic.
 */
object PluginRegistry {

    enum class Kind(
        val displayName: String,
        val fileExtension: String,
        val mimeType: String,
    ) {
        TEMPLATE("Project template", ".clearcut-template", "application/octet-stream"),
        EFFECT_PACK("Effect pack", ".ncfx", "application/octet-stream"),
        STYLE_PACK("Caption / text style pack", ".ncstyle", "application/octet-stream"),
        LUT_CUBE("LUT (.cube)", ".cube", "text/plain"),
        LUT_3DL("LUT (.3dl)", ".3dl", "text/plain"),
        OPENFX_DESCRIPTOR("OpenFX effect descriptor", ".ncfxd", "application/json"),
    }

    /**
     * Identify the [Kind] of a file by name. Returns null for unknown
     * extensions. Case-insensitive on the extension.
     */
    fun kindForFileName(fileName: String): Kind? {
        val lower = fileName.trim().lowercase(Locale.US)
        // Sort by longest extension first so `.clearcut-template` wins over
        // any shorter false-positive substring match.
        return Kind.entries
            .sortedByDescending { it.fileExtension.length }
            .firstOrNull { lower.endsWith(it.fileExtension) }
    }

    /** Convenience for Uri-bearing entry points. */
    fun kindForFile(file: File): Kind? = kindForFileName(file.name)

    /** Convenience for Uri-bearing entry points. */
    fun kindForUri(uri: Uri): Kind? = uri.lastPathSegment?.let { kindForFileName(it) }

    /**
     * Return a stable "open with" share/intent type for the given kind.
     * Today this is just the [Kind.mimeType], but the wrapper exists so
     * any future per-kind override (e.g. routing LUTs through a typed
     * preview) has one place to land.
     */
    fun shareMimeTypeFor(kind: Kind): String = kind.mimeType

    /**
     * Render a human-readable list of supported file extensions, useful for
     * settings copy and import-picker labels.
     */
    fun allSupportedExtensions(): List<String> =
        Kind.entries.map { it.fileExtension }
}

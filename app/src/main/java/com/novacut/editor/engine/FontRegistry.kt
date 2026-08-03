package com.novacut.editor.engine

import android.content.Context
import android.graphics.Typeface
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fontsDir = File(context.filesDir, "fonts").also { it.mkdirs() }
    private val rollbackDir = File(fontsDir, "rollback").also { it.mkdirs() }
    private val typefaceCache = mutableMapOf<String, Typeface>()

    data class ImportedFont(
        val fileName: String,
        val displayName: String,
        val file: File,
        val contentHash: String = "",
        val provenanceSource: String = "local import",
        val canRollback: Boolean = false,
    )

    fun listImportedFonts(): List<ImportedFont> {
        if (!fontsDir.exists()) return emptyList()
        return fontsDir.listFiles()
            ?.filter { it.extension.lowercase() in setOf("ttf", "otf") }
            ?.mapNotNull { file ->
                val typeface = loadTypeface(file) ?: return@mapNotNull null
                ImportedFont(
                    fileName = file.name,
                    displayName = file.nameWithoutExtension.replace(Regex("[_-]"), " "),
                    file = file,
                    contentHash = runCatching { DeclarativePackContract.sha256File(file) }.getOrDefault(""),
                    canRollback = rollbackFileForName(file.name)?.isFile == true,
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    fun importFont(uri: Uri): ImportedFont? {
        // Reject anything that isn't a validated .ttf/.otf name up front, before
        // any bytes are copied.
        val fileName = resolveFileName(uri) ?: run {
            Log.w(TAG, "Rejected font import: unsupported or unnamed file type")
            return null
        }
        val targetFile = fileForName(fileName) ?: return null
        val hadExisting = targetFile.isFile
        if (hadExisting && !backupForRollback(targetFile)) return null

        return try {
            // Bounded, atomic install: copy under a byte ceiling to a temp file,
            // validate it is a real typeface, fsync, then atomically move it into
            // place. writeFileAtomically throws on an empty result or a failed
            // move, so a partial/failed import can never land as a usable font.
            writeFileAtomically(targetFile, requireNonEmpty = true) { tempFile ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Cannot open font URI")
                input.use { source ->
                    tempFile.outputStream().use { output ->
                        copyWithLimit(source, output, MAX_FONT_BYTES)
                    }
                }
                if (loadTypeface(tempFile) == null) {
                    throw java.io.IOException("Not a valid typeface: $fileName")
                }
            }
            // The move committed; confirm the installed file actually reloads
            // before reporting success (a success with an unreadable file would
            // leave a broken entry in the picker).
            if (loadTypeface(targetFile) == null) {
                targetFile.delete()
                if (hadExisting) restoreRollback(targetFile)
                Log.w(TAG, "Imported font is not reloadable: ${RedactedLog.assetId(fileName)}")
                return null
            }
            typefaceCache.remove(targetFile.name)
            ImportedFont(
                fileName = targetFile.name,
                displayName = targetFile.nameWithoutExtension.replace(Regex("[_-]"), " "),
                file = targetFile,
                contentHash = DeclarativePackContract.sha256File(targetFile),
                canRollback = hadExisting,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import font", e)
            null
        }
    }

    fun deleteFont(fileName: String): Boolean {
        val targetFile = fileForName(fileName) ?: return false
        if (!targetFile.isFile || !backupForRollback(targetFile)) return false
        typefaceCache.remove(targetFile.name)
        return targetFile.delete()
    }

    fun canRollback(fileName: String): Boolean =
        rollbackFileForName(fileName)?.isFile == true

    fun rollbackFont(fileName: String): Boolean {
        val targetFile = fileForName(fileName) ?: return false
        val rollbackFile = rollbackFileForName(fileName) ?: return false
        if (!rollbackFile.isFile) return false
        return try {
            writeFileAtomically(targetFile, requireNonEmpty = true) { tempFile ->
                rollbackFile.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (loadTypeface(targetFile) == null) return false
            rollbackFile.delete()
            typefaceCache.remove(targetFile.name)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to roll back font: ${RedactedLog.assetId(fileName)}", e)
            false
        }
    }

    fun resolveTypeface(fontFamily: String): Typeface? {
        if (!fontFamily.startsWith(CUSTOM_PREFIX)) return null
        val fileName = fontFamily.removePrefix(CUSTOM_PREFIX)
        typefaceCache[fileName]?.let { return it }
        val file = fileForName(fileName) ?: return null
        return loadTypeface(file)?.also { typefaceCache[fileName] = it }
    }

    fun resolveTypefaceForText(
        fontFamily: String,
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
    ): Typeface {
        val requested = resolveTypeface(fontFamily)
            ?: Typeface.create(fontFamily.takeIf { it.isNotBlank() } ?: "sans-serif", Typeface.NORMAL)
        val base = if (supportsText(requested, text)) {
            requested
        } else {
            val fallbackName = CaptionFontFallbackPolicy.familyNameForText("sans-serif", text)
            Typeface.create(fallbackName, Typeface.NORMAL)
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }

    internal fun supportsText(typeface: Typeface, text: String): Boolean {
        if (text.isEmpty()) return true
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 48f
        }
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            val type = Character.getType(codePoint)
            val skip = Character.isWhitespace(codePoint) ||
                type == Character.CONTROL.toInt() ||
                type == Character.FORMAT.toInt() ||
                type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            if (!skip && !paint.hasGlyph(String(Character.toChars(codePoint)))) return false
            index += Character.charCount(codePoint)
        }
        return true
    }

    fun isCustomFont(fontFamily: String): Boolean = fontFamily.startsWith(CUSTOM_PREFIX)

    fun fontFamilyKey(fileName: String): String = "$CUSTOM_PREFIX$fileName"

    private fun loadTypeface(file: File): Typeface? = try {
        if (file.exists() && file.length() > 0) Typeface.createFromFile(file) else null
    } catch (e: Exception) {
        Log.w(TAG, "Invalid font file: ${file.redacted()}", e)
        null
    }

    private fun fileForName(fileName: String): File? =
        sanitizedFontFileName(fileName)?.takeIf { it == fileName }?.let { File(fontsDir, it) }

    private fun rollbackFileForName(fileName: String): File? =
        fileForName(fileName)?.let { File(rollbackDir, it.name) }

    private fun backupForRollback(file: File): Boolean {
        val rollbackFile = rollbackFileForName(file.name) ?: return false
        return try {
            writeFileAtomically(rollbackFile, requireNonEmpty = true) { tempFile ->
                file.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preserve font for rollback: ${file.redacted()}", e)
            false
        }
    }

    private fun restoreRollback(file: File): Boolean {
        val rollbackFile = rollbackFileForName(file.name) ?: return false
        if (!rollbackFile.isFile) return false
        return try {
            writeFileAtomically(file, requireNonEmpty = true) { tempFile ->
                rollbackFile.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore font after import failure: ${file.redacted()}", e)
            false
        }
    }

    /**
     * Resolve a sanitized `<stem>.<ext>` file name, or null when the source is
     * not a `.ttf`/`.otf` font. Extension is taken from the source name (never
     * defaulted) so arbitrary file types cannot slip in under a font extension.
     */
    private fun resolveFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameFromCursor = cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
        return sanitizedFontFileName(nameFromCursor ?: uri.lastPathSegment)
    }

    companion object {
        private const val TAG = "FontRegistry"
        const val CUSTOM_PREFIX = "custom:"
        private val ALLOWED_FONT_EXTENSIONS = setOf("ttf", "otf")
        // Byte ceiling for a custom font import. Comfortably covers large CJK
        // faces (typically 10-30 MB) while rejecting pathological/malicious
        // inputs that would otherwise copy without bound into app storage.
        private const val MAX_FONT_BYTES = 48L * 1024 * 1024

        /**
         * Sanitize a source display/path name into a `<stem>.<ext>` font file
         * name, or null when it is not a validated `.ttf`/`.otf` font. The
         * extension is read from the source name and never defaulted, so an
         * arbitrary file type (e.g. `.exe`, no extension) cannot install under a
         * font name. Pure — no Android dependency, unit-tested directly.
         */
        internal fun sanitizedFontFileName(rawName: String?): String? {
            val baseName = rawName?.trim().orEmpty()
            if (baseName.isEmpty()) return null
            val ext = baseName.substringAfterLast('.', "").lowercase()
            if (ext !in ALLOWED_FONT_EXTENSIONS) return null
            val stem = baseName.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(60)
                .ifBlank { "font" }
            return "$stem.$ext"
        }
    }
}

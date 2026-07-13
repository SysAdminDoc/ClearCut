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
    private val fontsDir = File(context.filesDir, "fonts")
    private val typefaceCache = mutableMapOf<String, Typeface>()

    data class ImportedFont(
        val fileName: String,
        val displayName: String,
        val file: File
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
                    file = file
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    fun importFont(uri: Uri): ImportedFont? {
        fontsDir.mkdirs()
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open font URI", e)
            return null
        } ?: return null

        val fileName = resolveFileName(uri)
        val targetFile = File(fontsDir, fileName)
        val partialFile = File(fontsDir, "$fileName.partial")

        return try {
            inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (loadTypeface(partialFile) == null) {
                partialFile.delete()
                Log.w(TAG, "Font file is not a valid typeface: $fileName")
                return null
            }
            partialFile.renameTo(targetFile)
            ImportedFont(
                fileName = targetFile.name,
                displayName = targetFile.nameWithoutExtension.replace(Regex("[_-]"), " "),
                file = targetFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import font", e)
            partialFile.delete()
            null
        }
    }

    fun deleteFont(fileName: String): Boolean {
        typefaceCache.remove(fileName)
        return File(fontsDir, fileName).delete()
    }

    fun resolveTypeface(fontFamily: String): Typeface? {
        if (!fontFamily.startsWith(CUSTOM_PREFIX)) return null
        val fileName = fontFamily.removePrefix(CUSTOM_PREFIX)
        typefaceCache[fileName]?.let { return it }
        val file = File(fontsDir, fileName)
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
        Log.w(TAG, "Invalid font file: ${file.name}", e)
        null
    }

    private fun resolveFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameFromCursor = cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
        val baseName = nameFromCursor ?: uri.lastPathSegment ?: "font_${System.currentTimeMillis()}"
        val ext = baseName.substringAfterLast('.', "ttf").lowercase()
        val stem = baseName.substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(60)
        return "$stem.$ext"
    }

    companion object {
        private const val TAG = "FontRegistry"
        const val CUSTOM_PREFIX = "custom:"
    }
}

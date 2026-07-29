package com.novacut.editor.engine

import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Stable, non-identifying identifiers for anything user-owned that appears in a
 * log line or a diagnostic report.
 *
 * Production logs must never carry a raw URI or filesystem path: `logcat` is
 * readable by paired debug tooling and anything the user pastes into a bug
 * report, and a media path is often the file name of something personal. But a
 * report with no identifier at all is unusable — you cannot tell whether four
 * failures involve one clip or four.
 *
 * The compromise is a short digest of the full reference, so the same asset
 * produces the same id everywhere (logs, export incidents, the support bundle)
 * and can be correlated across a session without ever printing what it is. The
 * digest is unsalted on purpose: correlation has to survive process death and
 * reinstall for a support bundle to be worth reading, and the identifier is not
 * a secret — it is the absence of one.
 *
 * Non-identifying shape (scheme, file extension) is kept because it is what
 * actually helps triage and cannot be traced back to a person.
 */
object RedactedLog {

    private const val ID_HEX_CHARS = 8

    /** `asset#3f9a2c71` — the stable id for an arbitrary reference string. */
    fun assetId(reference: String?): String {
        if (reference.isNullOrEmpty()) return "asset#none"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(reference.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(ID_HEX_CHARS)
        return "asset#$hex"
    }

    /** `asset#3f9a2c71(content,.mp4)` — id plus non-identifying shape. */
    fun uri(uri: Uri?): String {
        if (uri == null) return "asset#none"
        val id = assetId(uri.toString())
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "none"
        val extension = extensionOf(uri.lastPathSegment)
        return "$id($scheme$extension)"
    }

    /** `asset#3f9a2c71(file,.mp4)` — id plus non-identifying shape. */
    fun file(file: File?): String {
        if (file == null) return "asset#none"
        val id = assetId(file.absolutePath)
        return "$id(file${extensionOf(file.name)})"
    }

    /** Redacts a path-like string when the caller only has a `String`. */
    fun path(path: String?): String {
        if (path.isNullOrEmpty()) return "asset#none"
        val id = assetId(path)
        return "$id(path${extensionOf(path)})"
    }

    private fun extensionOf(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return ""
        val extension = name.substring(dot)
        // Bound it: an "extension" longer than this is really part of a name.
        return if (extension.length <= 6 && extension.drop(1).all { it.isLetterOrDigit() }) {
            ",$extension"
        } else {
            ""
        }
    }
}

/** Shorthand so log sites read as `"... for ${uri.redacted()}"`. */
fun Uri?.redacted(): String = RedactedLog.uri(this)

/** Shorthand so log sites read as `"... for ${file.redacted()}"`. */
fun File?.redacted(): String = RedactedLog.file(this)

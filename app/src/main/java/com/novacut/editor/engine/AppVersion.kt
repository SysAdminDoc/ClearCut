package com.novacut.editor.engine

/**
 * Pure semantic-version parsing and comparison for the passive update check.
 *
 * NovaCut versions are `vX.Y.Z` (for example `v3.74.92`). The GitHub releases
 * API reports a `tag_name` in the same shape. This object keeps the comparison
 * logic free of Android and network types so the opt-in gate and the
 * "is a newer release available" decision are fully unit-testable on the JVM.
 *
 * Parsing is deliberately lenient: a leading `v`/`V` is stripped, only the
 * leading dotted-numeric core is considered, and any pre-release/build suffix
 * (`-rc1`, `+build7`, trailing junk) is ignored. Unparseable input compares as
 * "not newer" so a malformed tag can never nag the user to update.
 */
object AppVersion {

    data class Parsed(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<Parsed> {
        override fun compareTo(other: Parsed): Int =
            compareValuesBy(this, other, Parsed::major, Parsed::minor, Parsed::patch)
    }

    /**
     * Parse a `vX.Y.Z` / `X.Y` / `X` string into [Parsed], or null when the
     * leading core is not numeric. Missing minor/patch components default to 0.
     */
    fun parse(raw: String?): Parsed? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        val core = trimmed.takeWhile { it.isDigit() || it == '.' }
        val parts = core.split('.').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return Parsed(
            major = nums.getOrElse(0) { 0 },
            minor = nums.getOrElse(1) { 0 },
            patch = nums.getOrElse(2) { 0 },
        )
    }

    /**
     * True when [latest] is a strictly newer release than [current]. Either
     * input being unparseable yields false so the update notice never fires on
     * garbage data.
     */
    fun isNewer(latest: String?, current: String?): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        return l > c
    }
}

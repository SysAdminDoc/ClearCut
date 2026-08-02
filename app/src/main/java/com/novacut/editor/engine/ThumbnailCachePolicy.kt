package com.novacut.editor.engine

/**
 * Memory policy for decoded timeline thumbnails.
 *
 * A missing preference means automatic: retain the historical heap/8 bound.
 * Explicit budgets are user intent, but stay below heap/4 on normal devices
 * and heap/8 on low-RAM devices so the Settings control cannot turn a cache
 * into an unconditional process-wide memory reservation.
 */
internal object ThumbnailCachePolicy {
    const val MIN_EXPLICIT_MB = 32
    const val MAX_EXPLICIT_MB = 512
    private const val BYTES_PER_MB = 1024L * 1024L

    private val settingsSizesMb = listOf(32, 64, 128, 256)

    fun automaticBytes(maxMemoryBytes: Long): Int = boundedInt(maxMemoryBytes / 8L)

    fun resolveBytes(
        requestedMb: Int?,
        maxMemoryBytes: Long,
        isLowRamDevice: Boolean,
    ): Int {
        if (requestedMb == null) return automaticBytes(maxMemoryBytes)
        val requestedBytes = requestedMb
            .coerceIn(MIN_EXPLICIT_MB, MAX_EXPLICIT_MB)
            .toLong() * BYTES_PER_MB
        return boundedInt(requestedBytes.coerceAtMost(explicitCeilingBytes(maxMemoryBytes, isLowRamDevice)))
    }

    fun availableSettingsSizes(
        maxMemoryBytes: Long,
        isLowRamDevice: Boolean,
    ): List<Int> {
        val ceiling = explicitCeilingBytes(maxMemoryBytes, isLowRamDevice)
        return settingsSizesMb.filter { it.toLong() * BYTES_PER_MB <= ceiling }
    }

    private fun explicitCeilingBytes(maxMemoryBytes: Long, isLowRamDevice: Boolean): Long =
        (if (isLowRamDevice) maxMemoryBytes / 8L else maxMemoryBytes / 4L).coerceAtLeast(1L)

    private fun boundedInt(bytes: Long): Int = bytes
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

package com.novacut.editor.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Thread-safe CAS-loop update for MutableStateFlow.
 * Extracted from delegates to eliminate duplication across 7 delegate classes.
 *
 * Uses an unbounded retry loop (same semantics as kotlinx.coroutines' built-in
 * MutableStateFlow.update) so concurrent writers always converge without crashing.
 * Under high contention a yield is inserted to prevent busy-spinning.
 */
internal inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
    var attempts = 0
    while (true) {
        val prevValue = value
        val nextValue = function(prevValue)
        if (compareAndSet(prevValue, nextValue)) return
        attempts++
        if (attempts % 50 == 0) Thread.yield()
    }
}

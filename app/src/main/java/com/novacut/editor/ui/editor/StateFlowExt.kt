package com.novacut.editor.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Thread-safe CAS-loop update for MutableStateFlow.
 * Extracted from delegates to eliminate duplication across 7 delegate classes.
 */
internal inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
    while (true) {
        val prevValue = value
        val nextValue = function(prevValue)
        if (compareAndSet(prevValue, nextValue)) return
    }
}

package com.novacut.editor.ui.editor

import com.novacut.editor.model.SaveIndicatorState

internal data class SaveAttempt(
    val token: Long,
    val fingerprint: String,
)

internal data class SavedStateStatus(
    val isDirty: Boolean,
    val indicator: SaveIndicatorState,
)

internal class SavedStateTracker {
    private var savedFingerprint: String? = null
    private var currentFingerprint: String? = null
    private var nextToken = 0L
    private var latestStartedToken = 0L
    private var latestCompletedToken = 0L
    private var indicator = SaveIndicatorState.HIDDEN

    @Synchronized
    fun establishBaseline(fingerprint: String): SavedStateStatus {
        savedFingerprint = fingerprint
        currentFingerprint = fingerprint
        indicator = SaveIndicatorState.HIDDEN
        return status()
    }

    @Synchronized
    fun contentChanged(fingerprint: String): SavedStateStatus {
        currentFingerprint = fingerprint
        if (indicator == SaveIndicatorState.SAVED) indicator = SaveIndicatorState.HIDDEN
        return status()
    }

    @Synchronized
    fun beginSave(fingerprint: String): Pair<SaveAttempt, SavedStateStatus> {
        currentFingerprint = fingerprint
        val attempt = SaveAttempt(token = ++nextToken, fingerprint = fingerprint)
        latestStartedToken = attempt.token
        indicator = SaveIndicatorState.SAVING
        return attempt to status()
    }

    @Synchronized
    fun saveSucceeded(attempt: SaveAttempt, currentFingerprint: String): SavedStateStatus {
        this.currentFingerprint = currentFingerprint
        if (attempt.token < latestStartedToken || attempt.token < latestCompletedToken) return status()
        latestCompletedToken = attempt.token
        savedFingerprint = attempt.fingerprint
        indicator = if (this.currentFingerprint == savedFingerprint) {
            SaveIndicatorState.SAVED
        } else {
            SaveIndicatorState.HIDDEN
        }
        return status()
    }

    @Synchronized
    fun saveFailed(attempt: SaveAttempt, currentFingerprint: String): SavedStateStatus {
        this.currentFingerprint = currentFingerprint
        if (attempt.token < latestStartedToken || attempt.token < latestCompletedToken) return status()
        latestCompletedToken = attempt.token
        indicator = SaveIndicatorState.ERROR
        return status()
    }

    @Synchronized
    fun externalSaveFailed(currentFingerprint: String): SavedStateStatus {
        this.currentFingerprint = currentFingerprint
        indicator = SaveIndicatorState.ERROR
        return status()
    }

    private fun status(): SavedStateStatus = SavedStateStatus(
        isDirty = savedFingerprint != null && currentFingerprint != savedFingerprint,
        indicator = indicator,
    )
}

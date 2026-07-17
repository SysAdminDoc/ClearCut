package com.novacut.editor

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sharing media into ClearCut, then returning via Recents after the process
 * was killed, re-delivered the original SEND intent and re-imported it into a
 * duplicate project. The launch intent must only be parsed on a fresh start.
 */
class LaunchIntentReplayGuardTest {

    @Test
    fun freshStart_processesIntent() {
        assertTrue(shouldProcessLaunchIntent(isRecreation = false, intentFlags = 0))
    }

    @Test
    fun recreation_skipsIntent() {
        assertFalse(shouldProcessLaunchIntent(isRecreation = true, intentFlags = 0))
    }

    @Test
    fun launchedFromHistory_skipsIntent() {
        assertFalse(
            shouldProcessLaunchIntent(
                isRecreation = false,
                intentFlags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
            )
        )
    }

    @Test
    fun historyFlagAmongOthers_skipsIntent() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        assertFalse(shouldProcessLaunchIntent(isRecreation = false, intentFlags = flags))
    }
}

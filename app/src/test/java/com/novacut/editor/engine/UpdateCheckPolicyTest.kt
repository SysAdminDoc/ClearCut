package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the privacy gate for the passive update check: the network is only
 * ever eligible to be touched when the build supports the feature AND the user
 * has explicitly opted in.
 */
class UpdateCheckPolicyTest {

    @Test
    fun disabledByDefault_noNetwork() {
        assertEquals(
            UpdateCheckPolicy.Decision.Disabled,
            UpdateCheckPolicy.decide(buildSupportsUpdateCheck = true, userEnabled = false),
        )
        assertFalse(
            UpdateCheckPolicy.mayCheckNetwork(buildSupportsUpdateCheck = true, userEnabled = false),
        )
    }

    @Test
    fun unavailableBuild_neverChecksEvenWhenUserEnabled() {
        assertEquals(
            UpdateCheckPolicy.Decision.Unavailable,
            UpdateCheckPolicy.decide(buildSupportsUpdateCheck = false, userEnabled = true),
        )
        assertFalse(
            UpdateCheckPolicy.mayCheckNetwork(buildSupportsUpdateCheck = false, userEnabled = true),
        )
    }

    @Test
    fun enabledAndAvailable_allowsCheck() {
        assertEquals(
            UpdateCheckPolicy.Decision.Allowed,
            UpdateCheckPolicy.decide(buildSupportsUpdateCheck = true, userEnabled = true),
        )
        assertTrue(
            UpdateCheckPolicy.mayCheckNetwork(buildSupportsUpdateCheck = true, userEnabled = true),
        )
    }

    @Test
    fun updateAvailable_delegatesToVersionComparison() {
        assertTrue(UpdateCheckPolicy.updateAvailable("v3.74.93", "v3.74.92"))
        assertFalse(UpdateCheckPolicy.updateAvailable("v3.74.92", "v3.74.92"))
        assertFalse(UpdateCheckPolicy.updateAvailable(null, "v3.74.92"))
    }
}

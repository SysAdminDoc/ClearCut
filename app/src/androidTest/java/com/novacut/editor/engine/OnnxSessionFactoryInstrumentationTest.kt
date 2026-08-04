package com.novacut.editor.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native capability probe for a supported APK ABI. The test is intentionally
 * green when the provider is absent: the production contract is XNNPACK when
 * loadable and an explicit CPU fallback otherwise. The result is logged so a
 * device run records which side of that contract the ABI exercised.
 */
@RunWith(AndroidJUnit4::class)
class OnnxSessionFactoryInstrumentationTest {

    @Test
    fun xnnpackRegistrationProbeIsSafeAndReportsCapability() {
        val result = OnnxSessionFactory.probeXnnpack()
        Log.i(
            "OnnxSessionFactoryTest",
            "XNNPACK available=${result.available} failure=${result.failureClass}",
        )
        assertTrue(
            "A failed XNNPACK probe must explain the fallback failure class.",
            result.available || !result.failureClass.isNullOrBlank(),
        )
    }
}

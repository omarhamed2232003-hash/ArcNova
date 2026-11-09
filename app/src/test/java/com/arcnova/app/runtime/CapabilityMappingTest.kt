package com.arcnova.app.runtime

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityMappingTest {
    @Test fun arm64IsDetectedFromSupportedAbis() {
        assertEquals(HostAbi.ARM64, CapabilityMappings.hostAbi(listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test fun x64IsDetectedWhenNoArm64AbiExists() {
        assertEquals(HostAbi.X86_64, CapabilityMappings.hostAbi(listOf("x86_64")))
    }

    @Test fun unknownAbiIsNotGuessed() {
        assertEquals(HostAbi.UNKNOWN, CapabilityMappings.hostAbi(listOf("armeabi-v7a")))
    }

    @Test fun thermalBudgetFallsAsDeviceGetsHotter() {
        assertEquals(1.0f, CapabilityMappings.thermalBudget(PowerManager.THERMAL_STATUS_NONE))
        assertEquals(0.65f, CapabilityMappings.thermalBudget(PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(0.20f, CapabilityMappings.thermalBudget(PowerManager.THERMAL_STATUS_CRITICAL))
    }
}

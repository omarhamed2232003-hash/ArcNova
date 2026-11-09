package com.arcnova.app.runtime

import org.junit.Assert.*
import org.junit.Test

class RuntimePlanSelectorTest {
    private val device = DeviceCapabilities(
        HostAbi.ARM64, true, 1, true,
        6L * 1024L * 1024L * 1024L, 0.8f
    )
    private val protonBox = RuntimeCandidate(
        "proton-box64", WindowsLayer.PROTON, X64Translator.BOX64,
        true, 1, true, true,
        setOf(DirectXBackend.DXVK, DirectXBackend.VKD3D_PROTON, DirectXBackend.WINE_D3D)
    )
    private val wineFex = protonBox.copy(
        id = "wine-fex", windowsLayer = WindowsLayer.WINE,
        translator = X64Translator.FEX, preferredForSteam = false
    )

    @Test fun prefersSteamRuntimeForSteamWorkload() {
        val result = RuntimePlanSelector.select(device, listOf(wineFex, protonBox), true, false)
            as RuntimeSelection.Selected
        assertEquals("proton-box64", result.plan.candidate.id)
        assertEquals(DirectXBackend.DXVK, result.plan.directXBackend)
    }

    @Test fun honorsExplicitTranslatorPreference() {
        val result = RuntimePlanSelector.select(
            device, listOf(protonBox, wineFex), false, false,
            preferredTranslator = X64Translator.FEX
        ) as RuntimeSelection.Selected
        assertEquals(X64Translator.FEX, result.plan.candidate.translator)
    }

    @Test fun rejectsUnavailableVulkanAndDirectX12Path() {
        val result = RuntimePlanSelector.select(
            device.copy(vulkanAvailable = false, vulkanVersion = 0),
            listOf(protonBox), true, true
        )
        assertTrue(result is RuntimeSelection.Unsupported)
    }

    @Test fun rejects32BitGameWhenEitherSideCannotRunIt() {
        val result = RuntimePlanSelector.select(
            device, listOf(protonBox.copy(supports32Bit = false)),
            true, false, requires32BitGame = true
        )
        assertTrue(result is RuntimeSelection.Unsupported)
    }

    @Test fun fallsBackToWineD3dWhenVulkanIsUnavailable() {
        val candidate = protonBox.copy(
            requiresVulkan = false,
            directXBackends = setOf(DirectXBackend.WINE_D3D)
        )
        val result = RuntimePlanSelector.select(
            device.copy(vulkanAvailable = false, vulkanVersion = 0),
            listOf(candidate), false, false
        ) as RuntimeSelection.Selected
        assertEquals(DirectXBackend.WINE_D3D, result.plan.directXBackend)
        assertEquals(GraphicsApi.OPENGL, result.plan.graphicsApi)
    }

    @Test fun reducesWorkersWhenThermalHeadroomIsLow() {
        val result = RuntimePlanSelector.select(
            device.copy(thermalHeadroom = 0.1f), listOf(protonBox), true, false
        ) as RuntimeSelection.Selected
        assertEquals(1, result.plan.recommendedWorkerCount)
    }

    @Test fun validatesPerGameProfile() {
        val profile = GameRuntimeProfile(
            "doom3", "proton-box64", X64Translator.BOX64,
            DirectXBackend.DXVK, 1920, 1200
        )
        assertTrue(profile.validationErrors().isEmpty())
        assertTrue(profile.copy(resolutionWidth = 100).validationErrors().isNotEmpty())
    }
}

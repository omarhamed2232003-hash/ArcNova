package com.arcnova.app.runtime

/** Platform-neutral runtime planning; this does not install or launch a runtime. */
enum class HostAbi { ARM64, X86_64, UNKNOWN }
enum class WindowsLayer { WINE, PROTON }
enum class X64Translator { BOX64, FEX }
enum class GraphicsApi { VULKAN, OPENGL }
enum class DirectXBackend { DXVK, VKD3D_PROTON, WINE_D3D }

data class DeviceCapabilities(
    val hostAbi: HostAbi,
    val vulkanAvailable: Boolean,
    val vulkanVersion: Int = 0,
    val supports32Bit: Boolean = false,
    val availableRamBytes: Long,
    val thermalHeadroom: Float = 1f
)

data class RuntimeCandidate(
    val id: String,
    val windowsLayer: WindowsLayer,
    val translator: X64Translator?,
    val requiresVulkan: Boolean,
    val minimumVulkanVersion: Int = 0,
    val supports32Bit: Boolean,
    val preferredForSteam: Boolean,
    val directXBackends: Set<DirectXBackend>
)

data class RuntimePlan(
    val candidate: RuntimeCandidate,
    val graphicsApi: GraphicsApi,
    val directXBackend: DirectXBackend,
    val recommendedWorkerCount: Int,
    val reason: String
)

sealed class RuntimeSelection {
    data class Selected(val plan: RuntimePlan) : RuntimeSelection()
    data class Unsupported(val reasons: List<String>) : RuntimeSelection()
}

/**
 * Deterministic conservative planner. Preference order is not a performance
 * benchmark: measured game/device profiles must override generic defaults.
 */
object RuntimePlanSelector {
    fun select(
        device: DeviceCapabilities,
        candidates: List<RuntimeCandidate>,
        isSteamGame: Boolean,
        requiresDirectX12: Boolean,
        requires32BitGame: Boolean = false,
        preferredTranslator: X64Translator? = null
    ): RuntimeSelection {
        val rejected = mutableListOf<String>()
        val viable = candidates.filter { candidate ->
            val reasons = incompatibilities(device, candidate, requiresDirectX12, requires32BitGame)
            if (reasons.isNotEmpty()) rejected += "${candidate.id}: ${reasons.joinToString()}"
            reasons.isEmpty()
        }
        val selected = viable.sortedWith(
            compareByDescending<RuntimeCandidate> {
                if (preferredTranslator != null && it.translator == preferredTranslator) 1 else 0
            }.thenByDescending { if (isSteamGame && it.preferredForSteam) 1 else 0 }
                .thenByDescending { if (isSteamGame && it.windowsLayer == WindowsLayer.PROTON) 1 else 0 }
        ).firstOrNull() ?: return RuntimeSelection.Unsupported(
            rejected.ifEmpty { listOf("No runtime candidates are configured.") }
        )

        val backend = when {
            requiresDirectX12 -> DirectXBackend.VKD3D_PROTON
            DirectXBackend.DXVK in selected.directXBackends -> DirectXBackend.DXVK
            else -> DirectXBackend.WINE_D3D
        }
        val workers = recommendedWorkers(device)
        val graphics = if (device.vulkanAvailable) GraphicsApi.VULKAN else GraphicsApi.OPENGL
        val reason = "Selected ${selected.id}; ${if (isSteamGame) "Steam" else "general"} profile; " +
            "${graphics.name}; ${backend.name}; $workers background workers."
        return RuntimeSelection.Selected(RuntimePlan(selected, graphics, backend, workers, reason))
    }

    private fun incompatibilities(
        device: DeviceCapabilities,
        candidate: RuntimeCandidate,
        requiresDirectX12: Boolean,
        requires32BitGame: Boolean
    ): List<String> = buildList {
        if (device.hostAbi == HostAbi.UNKNOWN) add("unknown host ABI")
        if (device.hostAbi == HostAbi.ARM64 && candidate.translator == null) {
            add("ARM64 requires an x86-64 translator")
        }
        if (device.hostAbi == HostAbi.X86_64 && candidate.translator != null) {
            add("translator is not applicable to native x86-64 host")
        }
        if (candidate.requiresVulkan &&
            (!device.vulkanAvailable || device.vulkanVersion < candidate.minimumVulkanVersion)
        ) add("required Vulkan capability is unavailable")
        if (requires32BitGame && (!device.supports32Bit || !candidate.supports32Bit)) {
            add("32-bit game execution is unsupported")
        }
        if (requiresDirectX12 && DirectXBackend.VKD3D_PROTON !in candidate.directXBackends) {
            add("DirectX 12 translation backend is unavailable")
        }
        if (device.availableRamBytes < MINIMUM_RUNTIME_RAM_BYTES) {
            add("insufficient available RAM for a safe runtime start")
        }
    }

    private fun recommendedWorkers(device: DeviceCapabilities): Int {
        val cpuBound = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val memoryBound = (device.availableRamBytes / WORKER_MEMORY_BUDGET_BYTES)
            .coerceIn(1, MAX_WORKERS.toLong()).toInt()
        val thermalBound = when {
            device.thermalHeadroom < 0.25f -> 1
            device.thermalHeadroom < 0.50f -> 2
            else -> MAX_WORKERS
        }
        return minOf(cpuBound, memoryBound, thermalBound, MAX_WORKERS).coerceAtLeast(1)
    }

    private const val MINIMUM_RUNTIME_RAM_BYTES = 768L * 1024L * 1024L
    private const val WORKER_MEMORY_BUDGET_BYTES = 512L * 1024L * 1024L
    private const val MAX_WORKERS = 4
}

/** Explicit per-game settings; avoid one global preset for every title. */
data class GameRuntimeProfile(
    val gameId: String,
    val runtimeId: String,
    val translator: X64Translator,
    val graphicsBackend: DirectXBackend,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
    val environment: Map<String, String> = emptyMap()
) {
    fun validationErrors(): List<String> = buildList {
        if (gameId.isBlank()) add("gameId must not be blank")
        if (runtimeId.isBlank()) add("runtimeId must not be blank")
        if (resolutionWidth !in 320..7680) add("resolutionWidth is outside supported bounds")
        if (resolutionHeight !in 240..4320) add("resolutionHeight is outside supported bounds")
        if (environment.keys.any { it.isBlank() }) add("environment keys must not be blank")
        if (environment.values.any { '\u0000' in it }) add("environment values must not contain NUL")
    }
}

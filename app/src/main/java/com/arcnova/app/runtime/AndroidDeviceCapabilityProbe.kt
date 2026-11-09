package com.arcnova.app.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.pm.FeatureInfo
import android.os.Build
import android.os.PowerManager

/**
 * Reads coarse Android framework capabilities without requesting permissions.
 *
 * Vulkan feature declarations are a platform-level hint, not a proof that a
 * particular Vulkan extension/driver path works. Runtime adapters must perform
 * their own instance/device/extension probes before enabling DXVK or VKD3D.
 */
object AndroidDeviceCapabilityProbe {
    fun inspect(context: Context): AndroidDeviceSnapshot {
        val appContext = context.applicationContext
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memory)

        val features = appContext.packageManager.systemAvailableFeatures
            .filterNotNull()
        val vulkanLevel = features.firstOrNull {
            it.name == "android.hardware.vulkan.level"
        }?.version ?: 0
        val vulkanVersion = features.firstOrNull {
            it.name == "android.hardware.vulkan.version"
        }?.version ?: 0
        val vulkanAvailable = vulkanVersion > 0

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        val capabilities = DeviceCapabilities(
            hostAbi = CapabilityMappings.hostAbi(supportedAbis),
            vulkanAvailable = vulkanAvailable,
            vulkanVersion = vulkanVersion,
            supports32Bit = Build.SUPPORTED_32_BIT_ABIS.isNotEmpty(),
            availableRamBytes = memory.availMem,
            thermalHeadroom = CapabilityMappings.thermalBudget(thermalStatus)
        )
        return AndroidDeviceSnapshot(
            capabilities = capabilities,
            supportedAbis = supportedAbis,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidSdk = Build.VERSION.SDK_INT,
            cpuCount = Runtime.getRuntime().availableProcessors(),
            totalRamBytes = memory.totalMem,
            lowMemory = memory.lowMemory,
            vulkanFeatureLevel = vulkanLevel,
            thermalStatus = thermalStatus
        )
    }
}

data class AndroidDeviceSnapshot(
    val capabilities: DeviceCapabilities,
    val supportedAbis: List<String>,
    val deviceModel: String,
    val androidSdk: Int,
    val cpuCount: Int,
    val totalRamBytes: Long,
    val lowMemory: Boolean,
    /** Android package feature hint; not a Vulkan extension/driver validation. */
    val vulkanFeatureLevel: Int,
    val thermalStatus: Int
)

internal object CapabilityMappings {
    fun hostAbi(abis: List<String>): HostAbi = when {
        abis.any { it == "arm64-v8a" } -> HostAbi.ARM64
        abis.any { it == "x86_64" } -> HostAbi.X86_64
        else -> HostAbi.UNKNOWN
    }

    /** Conservative normalized thermal budget used only for background jobs. */
    fun thermalBudget(status: Int): Float = when (status) {
        PowerManager.THERMAL_STATUS_LIGHT -> 0.85f
        PowerManager.THERMAL_STATUS_MODERATE -> 0.65f
        PowerManager.THERMAL_STATUS_SEVERE -> 0.40f
        PowerManager.THERMAL_STATUS_CRITICAL -> 0.20f
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> 0.10f
        else -> 1.0f
    }
}

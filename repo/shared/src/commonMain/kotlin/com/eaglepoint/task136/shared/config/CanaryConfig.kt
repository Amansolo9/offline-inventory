package com.eaglepoint.task136.shared.config

import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.serialization.Serializable

@Serializable
data class CanaryConfig(
    val featureId: String,
    val targetVersion: Int,
    val enabledRoles: Set<String> = emptySet(),
    val enabledDeviceGroups: Set<String> = emptySet(),
    val rolloutPercentage: Int = 100,
)

@Serializable
data class CanaryManifest(
    val features: List<CanaryConfig> = emptyList(),
)

class CanaryEvaluator(
    private val manifest: CanaryManifest = CanaryManifest(),
) {
    fun isFeatureEnabled(
        featureId: String,
        role: Role,
        deviceGroup: String = "default",
        userId: String = "",
    ): Boolean {
        val config = manifest.features.firstOrNull { it.featureId == featureId } ?: return false

        if (config.enabledRoles.isNotEmpty() && role.name !in config.enabledRoles) return false

        if (config.enabledDeviceGroups.isNotEmpty() && deviceGroup !in config.enabledDeviceGroups) return false

        if (config.rolloutPercentage < 100) {
            val hash = (userId.hashCode() and 0x7FFFFFFF) % 100
            if (hash >= config.rolloutPercentage) return false
        }

        return true
    }

    fun resolveFormVersion(
        featureId: String,
        defaultVersion: Int,
        role: Role,
        deviceGroup: String = "default",
        userId: String = "",
    ): Int {
        val config = manifest.features.firstOrNull { it.featureId == featureId } ?: return defaultVersion
        return if (isFeatureEnabled(featureId, role, deviceGroup, userId)) {
            config.targetVersion
        } else {
            defaultVersion
        }
    }
}

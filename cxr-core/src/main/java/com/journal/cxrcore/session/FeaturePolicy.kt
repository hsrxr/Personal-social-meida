package com.journal.cxrcore.session

/**
 * Feature flags gating which capabilities are available for a session type.
 * Only CUSTOMAPP supports CUSTOM_CMD.
 */
enum class CxrFeature {
    AUDIO,
    PHOTO,
    CUSTOM_CMD,
    DEVICE_CONTROL,
}

object FeaturePolicy {
    fun allowedFeatures(): List<CxrFeature> = listOf(
        CxrFeature.AUDIO,
        CxrFeature.PHOTO,
        CxrFeature.CUSTOM_CMD,
        CxrFeature.DEVICE_CONTROL,
    )

    fun isFeatureAllowed(feature: CxrFeature): Boolean = feature in allowedFeatures()
}

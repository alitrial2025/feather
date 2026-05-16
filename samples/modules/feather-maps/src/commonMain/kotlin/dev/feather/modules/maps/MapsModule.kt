package dev.feather.modules.maps

import dev.feather.sdk.FeatherModule

/**
 * Feather Maps Module.
 *
 * Provides a high-performance native map widget using
 * Google Maps on Android and MapKit on iOS.
 */
class MapsModule : FeatherModule {
    override val name = "feather-maps"
    override val version = "1.0.0"

    override fun widgets(): List<String> = listOf("MapView")
}

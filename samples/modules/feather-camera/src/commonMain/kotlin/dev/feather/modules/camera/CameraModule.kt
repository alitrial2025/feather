package dev.feather.modules.camera

import dev.feather.sdk.FeatherModule

/**
 * Feather Camera Module — Template for creating native modules.
 *
 * This module provides camera-related widgets that are resolved
 * by the Host app's ModuleRegistry at runtime.
 *
 * To use in a Feather app:
 * ```kotlin
 * feather install feather-camera
 * ```
 *
 * Then in your App.kt:
 * ```kotlin
 * // The CLI will emit a node with type="CameraPreview"
 * // and the Host app renders it via ModuleRegistry.
 * ```
 */
class CameraModule : FeatherModule {
    override val name = "feather-camera"
    override val version = "1.0.0"

    override fun widgets(): List<String> = listOf(
        "CameraPreview",
        "PhotoPicker"
    )
}

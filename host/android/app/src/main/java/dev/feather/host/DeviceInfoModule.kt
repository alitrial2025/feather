package dev.feather.host

import android.content.Context
import android.os.Build
import org.json.JSONObject

class DeviceInfoModule : FeatherPlugin {
    override val name: String = "feather-device-info"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return when (method) {
            "getDeviceInfo" -> {
                val displayMetrics = context.appContext.resources.displayMetrics
                val scale = displayMetrics.density.toDouble()
                val width = displayMetrics.widthPixels / displayMetrics.density
                val height = displayMetrics.heightPixels / displayMetrics.density

                // Get battery info
                var batteryLevel = -1
                val bm = context.appContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                if (bm != null) {
                    batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                }

                val result = JSONObject().apply {
                    put("os", "android")
                    put("version", Build.VERSION.RELEASE)
                    put("model", Build.MODEL)
                    put("scale", scale)
                    put("width", width.toDouble())
                    put("height", height.toDouble())
                    put("battery", batteryLevel)
                }
                
                context.sendStateAssignment("", result.toString())
                true
            }
            else -> false
        }
    }
}

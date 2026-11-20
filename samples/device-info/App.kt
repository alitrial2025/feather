package feather.deviceinfo

import dev.feather.sdk.Button
import dev.feather.sdk.Column
import dev.feather.sdk.ContainerStyle
import dev.feather.sdk.FeatherApp
import dev.feather.sdk.Platform
import dev.feather.sdk.Text
import dev.feather.sdk.Widget
import dev.feather.sdk.actionText
import dev.feather.sdk.getState
import dev.feather.sdk.getStateJson
import dev.feather.sdk.installApp
import dev.feather.sdk.setStateText
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDetails(val os: String, val version: String, val model: String)

class DeviceInfoApp : FeatherApp() {
    override fun handleAction(action: String, payload: String?) {
        if (action == "__deepLink") setStateText("opened_path", actionText(payload))
    }

    override fun render(): Widget {
        val details = getStateJson<DeviceDetails>("device_info")
        return Column(
            Text("Feather Device Info"),
            Button("Read device info", Platform.getDeviceInfo("device_info")),
            Text(details?.let { "${it.model} · ${it.os} ${it.version}" }
                ?: "Open in a Feather host and tap Read device info."),
            Text(getState("opened_path")?.let { "Opened from $it" } ?: "Opened normally"),
            style = ContainerStyle(padding = 20, spacing = 12, fillMaxWidth = true),
        )
    }
}

fun main() = installApp(DeviceInfoApp())

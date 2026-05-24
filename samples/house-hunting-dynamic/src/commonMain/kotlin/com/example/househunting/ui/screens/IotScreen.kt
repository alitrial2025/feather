package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.IotSensorAlert

fun renderIotScreen(
    bleAuthorized: Boolean,
    alerts: List<IotSensorAlert>
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Smart IoT & Lock Gating", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Bluetooth Low Energy Lock Status Panel
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("🔒 BLE Proximity Key", fontWeight = "bold", fontSize = 15, color = "#FFFFFF"),
                                    Spacer(flex = 1),
                                    Text(if (bleAuthorized) "Authorized ✅" else "Rent Overdue ⚠️", color = if (bleAuthorized) "#A5D6A7" else "#FFCDD2", fontSize = 12, fontWeight = "bold")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            // BLE Range circular mesh
                            Box(
                                children = listOf(
                                    Column(
                                        children = listOf(
                                            Text("-64 dBm", fontWeight = "bold", color = "#FFFFFF", fontSize = 24),
                                            Text("Proximity Range: 2m", color = "#E2E8F0", fontSize = 12)
                                        ),
                                        style = ContainerStyle(alignment = "center", spacing = 4)
                                    )
                                ),
                                style = ContainerStyle(
                                    width = "140px",
                                    height = "140px",
                                    borderColor = if (bleAuthorized) "#81C784" else "#E57373",
                                    borderWidth = 3,
                                    cornerRadius = 70,
                                    alignment = "center",
                                    margin = 12
                                )
                            ),
                            Spacer(flex = 1),
                            Button(
                                text = "🔑 Unlock Main Apartment Gate",
                                action = "action_ble_unlock",
                                background = if (bleAuthorized) "#2E7D32" else "#B0BEC5",
                                textColor = "#FFFFFF",
                                enabled = bleAuthorized,
                                fillMaxWidth = true,
                                cornerRadius = 10,
                                height = "44px"
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(
                    margin = 12,
                    gradient = "linear-gradient(#37474F, #263238)",
                    cornerRadius = 16,
                    elevation = 4
                )
            ),

            // Real-Time IoT Sensor Alerts
            Column(
                children = listOf(
                    Text("Real-Time IoT Security Center", fontWeight = "bold", fontSize = 16, color = "#1E293B"),
                    Spacer(flex = 1),
                    if (alerts.isEmpty()) {
                        Card(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        Text("🛡️ All building sensors online. No leaks or security threats detected.", color = "#2E7D32", fontSize = 13)
                                    ),
                                    style = ContainerStyle(padding = 12)
                                )
                            ),
                            style = ContainerStyle(background = "#E8F5E9", cornerRadius = 8, fillMaxWidth = true)
                        )
                    } else {
                        Column(
                            children = alerts.map { alert ->
                                Card(
                                    children = listOf(
                                        Row(
                                            children = listOf(
                                                Box(
                                                    style = ContainerStyle(
                                                        width = "8px",
                                                        height = "40px",
                                                        background = if (alert.severity == "CRITICAL") "#D32F2F" else "#E65100",
                                                        cornerRadiusTopStart = 4,
                                                        cornerRadiusBottomStart = 4
                                                    )
                                                ),
                                                Column(
                                                    children = listOf(
                                                        Text("⚠️ ${alert.type} ALERT", fontWeight = "bold", fontSize = 13, color = if (alert.severity == "CRITICAL") "#D32F2F" else "#E65100"),
                                                        Text("${alert.location}: ${alert.timestamp}", fontSize = 12, color = "#64748B")
                                                    ),
                                                    style = ContainerStyle(paddingStart = 10, spacing = 2)
                                                )
                                            ),
                                            style = ContainerStyle(alignment = "center_vertical")
                                        )
                                    ),
                                    style = ContainerStyle(fillMaxWidth = true, margin = 4, cornerRadius = 10, elevation = 1)
                                )
                            }
                        )
                    }
                ),
                style = ContainerStyle(padding = 16, spacing = 10)
            )
        )
    )
}

package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData

fun renderArTourScreen(property: PropertyData): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("3D AR Spatial Tour", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Live AR Viewport representation
            Box(
                children = listOf(
                    // Stylized camera spatial meshes using ContainerStyle and borders
                    Column(
                        children = listOf(
                            Text("📐 Living Room: 16ft × 14ft (9ft Ceiling)", color = "#FFFFFF", fontWeight = "bold", fontSize = 14),
                            Text("✨ Spatial Mesh generated successfully", color = "#2E7D32", fontWeight = "medium", fontSize = 12)
                        ),
                        style = ContainerStyle(
                            background = "#000000",
                            opacity = 0.6f,
                            padding = 8,
                            cornerRadius = 8,
                            margin = 12
                        )
                    ),
                    // Centered focus point indicator
                    Box(
                        children = listOf(
                            Box(style = ContainerStyle(width = "20px", height = "20px", borderColor = "#00695C", borderWidth = 2, cornerRadius = 10))
                        ),
                        style = ContainerStyle(alignment = "center", fillMaxWidth = true, fillMaxHeight = true)
                    ),
                    // Status overlay bottom
                    Column(
                        children = listOf(
                            Text("Double-tap screen to place dynamic furniture", color = "#FFFFFF", fontSize = 12)
                        ),
                        style = ContainerStyle(
                            background = "#37474F",
                            opacity = 0.7f,
                            padding = 6,
                            cornerRadius = 6,
                            alignment = "center",
                            margin = 12
                        )
                    )
                ),
                style = ContainerStyle(
                    height = "320px",
                    background = "#263238", // Deep slate representing dark camera feed
                    borderColor = "#00695C",
                    borderWidth = 3,
                    cornerRadius = 16,
                    margin = 12,
                    alignment = "bottom_center"
                )
            ),

            // Spatial Controls Card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("Spatial Modeling Controls", fontWeight = "bold", fontSize = 15),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Button(text = "🛋️ Place Sofa", action = "action_ar_sofa", background = "#00695C", textColor = "#FFFFFF", modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "🛏️ Place Bed", action = "action_ar_bed", background = "#00695C", textColor = "#FFFFFF", modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 8)
                            ),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Button(text = "📏 Measure Room", action = "action_ar_measure", background = "#37474F", textColor = "#FFFFFF", modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "📷 Capture Scan", action = "action_ar_capture", background = "#37474F", textColor = "#FFFFFF", modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 8)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Spatial metadata card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("Property: ${property.title}", fontWeight = "bold", fontSize = 14),
                            Text("Total Spatial Scan Size: 42 MB", fontSize = 12, color = "#64748B"),
                            Text("Format: USDZ (Optimized for Apple QuickLook / Google SceneViewer)", fontSize = 12, color = "#64748B")
                        ),
                        style = ContainerStyle(padding = 12, spacing = 4)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 10, background = "#F1F5F9")
            )
        )
    )
}

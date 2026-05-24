package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData

fun renderCollaborativeScreen(
    property: PropertyData,
    partnerName: String,
    isConnected: Boolean
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Collaborative Viewing Room", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Live Connection Status Banner
            Card(
                children = listOf(
                    Row(
                        children = listOf(
                            Box(
                                style = ContainerStyle(
                                    width = "12px",
                                    height = "12px",
                                    background = if (isConnected) "#2E7D32" else "#E65100",
                                    cornerRadius = 6
                                )
                            ),
                            Column(
                                children = listOf(
                                    Text(if (isConnected) "Active Session: Sync Enabled" else "Waiting for Partner...", fontWeight = "bold", fontSize = 14),
                                    Text("Roommate: $partnerName", fontSize = 12, color = "#64748B")
                                ),
                                style = ContainerStyle(paddingStart = 10, spacing = 2)
                            )
                        ),
                        style = ContainerStyle(alignment = "center_vertical")
                    )
                ),
                style = ContainerStyle(
                    margin = 12,
                    padding = 16,
                    background = if (isConnected) "#E8F5E9" else "#FFF3E0",
                    cornerRadius = 12
                )
            ),

            // Active Listing Preview Sync Card
            Column(
                children = listOf(
                    Text("Partner's Active Viewing", fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                    Spacer(flex = 1),
                    Card(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Box(style = ContainerStyle(width = "60px", height = "60px", background = "#00695C", cornerRadius = 8)),
                                    Column(
                                        children = listOf(
                                            Text(property.title, fontWeight = "bold", fontSize = 14),
                                            Text("📍 ${property.location}", fontSize = 12, color = "#64748B"),
                                            Text("$${property.price}/mo", fontWeight = "bold", fontSize = 13, color = "#2E7D32")
                                        ),
                                        style = ContainerStyle(paddingStart = 12, spacing = 2)
                                    )
                                )
                            )
                        ),
                        action = "action_click_property:${property.id}",
                        style = ContainerStyle(fillMaxWidth = true, cornerRadius = 12, elevation = 2, padding = 12)
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 10)
            ),

            // Collaborative controls
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("Real-Time Synchronization Actions", fontWeight = "bold", fontSize = 15),
                            Spacer(flex = 1),
                            Text("Feather WebSockets automatically match active screens. Swiping images, viewing maps, or selecting time-slots updates both devices instantly.", fontSize = 13, color = "#64748B"),
                            Spacer(flex = 1),
                            Button(
                                text = "🔔 Push Active House to $partnerName",
                                action = "action_collab_push:${property.id}",
                                background = "#00695C",
                                textColor = "#FFFFFF",
                                fillMaxWidth = true,
                                cornerRadius = 10
                            ),
                            Spacer(flex = 1),
                            Button(
                                text = "📞 Join Audio Call Room",
                                action = "action_collab_call",
                                background = "#37474F",
                                textColor = "#FFFFFF",
                                fillMaxWidth = true,
                                cornerRadius = 10
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Session ID metadata
            Column(
                children = listOf(
                    Text("Secure Session Key: Room_8492_Sync", fontSize = 11, color = "#64748B", textAlign = "center")
                ),
                style = ContainerStyle(fillMaxWidth = true, padding = 16)
            )
        )
    )
}

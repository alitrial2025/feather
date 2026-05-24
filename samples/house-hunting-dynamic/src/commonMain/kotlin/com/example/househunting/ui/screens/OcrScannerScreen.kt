package com.example.househunting.ui.screens

import dev.feather.sdk.*

fun renderOcrScannerScreen(
    activeReading: Double,
    unitNumber: String,
    isProcessing: Boolean
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("AI Utility Meter OCR Scanner", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Live Camera scanning viewport representation
            Box(
                children = listOf(
                    // Green targeting framing overlay using borders
                    Box(
                        children = listOf(
                            Box(
                                style = ContainerStyle(
                                    width = "180px",
                                    height = "80px",
                                    borderColor = "#2E7D32",
                                    borderWidth = 2,
                                    cornerRadius = 8
                                )
                            )
                        ),
                        style = ContainerStyle(alignment = "center", fillMaxWidth = true, fillMaxHeight = true)
                    ),
                    // Centered processing details
                    if (isProcessing) {
                        Column(
                            children = listOf(
                                CircularProgress(color = "#FFFFFF", size = 40),
                                Text("AI analyzing digits...", color = "#FFFFFF", fontWeight = "bold", fontSize = 13)
                            ),
                            style = ContainerStyle(
                                background = "#000000",
                                opacity = 0.7f,
                                padding = 12,
                                cornerRadius = 8,
                                alignment = "center",
                                spacing = 8,
                                width = "160px",
                                zIndex = 3
                            )
                        )
                    } else Spacer(flex = 0)
                ),
                style = ContainerStyle(
                    height = "260px",
                    background = "#212121", // Deep grey representing camera scanner preview
                    margin = 12,
                    cornerRadius = 14,
                    alignment = "center"
                )
            ),

            // OCR extracted reading feedback card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("MLKit OCR Scanner Output", fontWeight = "bold", fontSize = 15),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Text("Unit Target:", color = "#64748B", fontSize = 13),
                                    Spacer(flex = 1),
                                    Text("Unit $unitNumber", fontWeight = "bold", color = "#37474F")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Text("Extracted Meter Reading:", color = "#64748B", fontSize = 13),
                                    Spacer(flex = 1),
                                    Text(if (activeReading > 0) "$activeReading kWh" else "Ready to scan...", fontWeight = "bold", color = if (activeReading > 0) "#2E7D32" else "#64748B")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Text("Calculated Bill:", color = "#64748B", fontSize = 13),
                                    Spacer(flex = 1),
                                    Text(if (activeReading > 0) "KES ${(activeReading * 22.0).toInt()}" else "--", fontWeight = "bold", color = "#2E7D32")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Scan control triggers
            Column(
                children = listOf(
                    Button(
                        text = "📸 Point Camera & Trigger OCR Reading",
                        action = "action_ocr_scan_trigger",
                        background = "#00695C",
                        textColor = "#FFFFFF",
                        fillMaxWidth = true,
                        cornerRadius = 10,
                        height = "44px"
                    ),
                    Spacer(flex = 1),
                    Button(
                        text = "⚡ Sync Bill Invoice to Tenant Statement",
                        action = "action_ocr_sync_bill",
                        background = if (activeReading > 0) "#2E7D32" else "#B0BEC5",
                        textColor = "#FFFFFF",
                        enabled = activeReading > 0,
                        fillMaxWidth = true,
                        cornerRadius = 10,
                        height = "44px"
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 12)
            )
        )
    )
}

package com.example.househunting.ui.screens

import dev.feather.sdk.*

fun renderMpesaPromptOverlay(
    amount: Double,
    merchantName: String,
    pinInput: String,
    payType: String
): Widget {
    return Box(
        children = listOf(
            // Dim background overlay
            Card(
                style = ContainerStyle(
                    width = "100%",
                    height = "100%",
                    background = "#00000088", // 50% opacity black
                    cornerRadius = 0
                )
            ),
            
            // The Dialog Modal
            Column(
                children = listOf(
                    // Dialog Header (Branded Safaricom Green)
                    Row(
                        children = listOf(
                            Text("M-PESA PIN CHECKOUT", color = "#FFFFFF", fontWeight = "bold", fontSize = 14)
                        ),
                        style = ContainerStyle(
                            background = "#388E3C", // M-Pesa/Safaricom green
                            padding = 14,
                            fillMaxWidth = true,
                            alignment = "center"
                        )
                    ),
                    
                    // Body Content
                    Column(
                        children = listOf(
                            Text("Payhero M-Pesa Gateway Service", fontWeight = "bold", fontSize = 14, color = "#1E293B"),
                            
                            Spacer(flex = 1),
                            Text("Do you want to pay KES ${amount.toInt()} to:", color = "#64748B", fontSize = 12),
                            Text(merchantName, fontWeight = "bold", color = "#388E3C", fontSize = 16),
                            
                            if (payType == "BOOKING") {
                                Text("Ref: Viewing Booking Fee (Auto-escrow protected)", color = "#475569", fontSize = 11)
                            } else {
                                Text("Ref: Monthly Rent Invoice (Direct payout)", color = "#475569", fontSize = 11)
                            },
                            
                            Spacer(flex = 1),
                            Text("Enter 4-Digit M-Pesa PIN:", fontWeight = "bold", fontSize = 12, color = "#37474F"),
                            
                            // Bullets representation of entered PIN
                            Row(
                                children = (1..4).map { i ->
                                    val isFilled = pinInput.length >= i
                                    Card(
                                        style = ContainerStyle(
                                            width = "14px",
                                            height = "14px",
                                            background = if (isFilled) "#388E3C" else "#E2E8F0",
                                            cornerRadius = 7,
                                            margin = 6
                                        )
                                    )
                                },
                                style = ContainerStyle(alignment = "center", fillMaxWidth = true)
                            ),
                            
                            Spacer(flex = 1),
                            
                            // Virtual numeric keypad grid
                            Column(
                                children = listOf(
                                    renderKeypadRow(listOf("1", "2", "3")),
                                    renderKeypadRow(listOf("4", "5", "6")),
                                    renderKeypadRow(listOf("7", "8", "9")),
                                    renderKeypadRow(listOf("Clear", "0", "Back"))
                                ),
                                style = ContainerStyle(spacing = 6, fillMaxWidth = true)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 10, alignment = "center")
                    ),
                    
                    Divider(color = "#E2E8F0"),
                    
                    // Actions Footer
                    Row(
                        children = listOf(
                            Button(
                                text = "Cancel Payment",
                                action = "action_mpesa_cancel",
                                background = "#F1F5F9",
                                textColor = "#475569",
                                cornerRadius = 8,
                                height = "40px"
                            ),
                            Spacer(flex = 1),
                            Button(
                                text = "Submit PIN",
                                action = "action_mpesa_confirm_pin",
                                background = if (pinInput.length == 4) "#388E3C" else "#B0BEC5",
                                textColor = "#FFFFFF",
                                enabled = pinInput.length == 4,
                                cornerRadius = 8,
                                height = "40px"
                            )
                        ),
                        style = ContainerStyle(padding = 12, spacing = 12, fillMaxWidth = true)
                    )
                ),
                style = ContainerStyle(
                    width = "280px",
                    background = "#FFFFFF",
                    cornerRadius = 16,
                    elevation = 6,
                    alignment = "center"
                )
            )
        ),
        style = ContainerStyle(
            width = "100%",
            height = "100%",
            alignment = "center"
        )
    )
}

private fun renderKeypadRow(keys: List<String>): Widget {
    return Row(
        children = keys.map { key ->
            Button(
                text = key,
                action = "action_mpesa_key:$key",
                background = "#F8F9FA",
                textColor = "#37474F",
                fontSize = 12,
                cornerRadius = 6,
                height = "32px"
            )
        },
        style = ContainerStyle(spacing = 6, fillMaxWidth = true)
    )
}

package com.example.househunting.ui.screens

import dev.feather.sdk.*

fun renderLeaseSigningScreen(
    tenantName: String,
    unitNumber: String,
    isSigned: Boolean
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Digital Smart Lease Agreement", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Scrollable Lease Terms Box
            Column(
                children = listOf(
                    Text("Standard Residential Lease Contract", fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                    Spacer(flex = 1),
                    Card(
                        children = listOf(
                            Column(
                                children = listOf(
                                    Text("This agreement is entered into by and between the Landlord and Tenant: $tenantName.", fontSize = 12, color = "#64748B"),
                                    Spacer(flex = 1),
                                    Text("1. PREMISES: Unit $unitNumber, Fairview Gardens.", fontWeight = "bold", fontSize = 12),
                                    Text("2. TERM: 12 Months starting June 1st, 2026.", fontSize = 12, color = "#37474F"),
                                    Text("3. MONTHLY RENT: $450.00, payable on the 1st of each calendar month.", fontSize = 12, color = "#37474F"),
                                    Text("4. COMPLIANCE: Tenant agrees to follow all estate noise level bounds and AML financial declarations.", fontSize = 12, color = "#37474F")
                                ),
                                style = ContainerStyle(spacing = 6, padding = 12)
                            )
                        ),
                        style = ContainerStyle(background = "#F8F9FA", cornerRadius = 10, fillMaxWidth = true)
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            if (isSigned) {
                // Cryptographic success state
                Card(
                    children = listOf(
                        Column(
                            children = listOf(
                                Text("📄 Lease Cryptographically Signed!", fontWeight = "bold", fontSize = 16, color = "#2E7D32"),
                                Spacer(flex = 1),
                                Text("Ed25519 signature successfully locked in Rust core registry.", fontSize = 12, color = "#64748B"),
                                Spacer(flex = 1),
                                Text("SHA-256 Hash: d7a1b8c...e204a9f", fontSize = 11, color = "#37474F", fontWeight = "bold"),
                                Spacer(flex = 1),
                                Button(text = "Return to Dashboard", action = "action_back", background = "#00695C", textColor = "#FFFFFF")
                            ),
                            style = ContainerStyle(padding = 16, spacing = 10, alignment = "center")
                        )
                    ),
                    style = ContainerStyle(margin = 12, background = "#E8F5E9", cornerRadius = 12)
                )
            } else {
                // Interactive Signature Capture
                Column(
                    children = listOf(
                        Text("Sign lease dynamically below", fontWeight = "bold", fontSize = 14),
                        // Mock Signature Canvas Pad
                        Box(
                            children = listOf(
                                Text("Tap here to place biometric signature...", color = "#90A4AE", fontSize = 13)
                            ),
                            style = ContainerStyle(
                                height = "120px",
                                background = "#FFFFFF",
                                borderColor = "#00695C",
                                borderWidth = 1,
                                cornerRadius = 8,
                                alignment = "center",
                                margin = 8
                            )
                        ),
                        
                        Card(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        Text("🔐 Cryptographic Ed25519 signing enabled.", fontSize = 12, color = "#00695C")
                                    ),
                                    style = ContainerStyle(padding = 10)
                                )
                            ),
                            style = ContainerStyle(background = "#E0F2F1", cornerRadius = 8)
                        ),

                        Spacer(flex = 1),
                        Button(
                            text = "Agree & Sign Lease Contract",
                            action = "action_lease_sign_confirm",
                            background = "#00695C",
                            textColor = "#FFFFFF",
                            fillMaxWidth = true,
                            cornerRadius = 10,
                            height = "44px"
                        )
                    ),
                    style = ContainerStyle(padding = 16, spacing = 12)
                )
            }
        )
    )
}

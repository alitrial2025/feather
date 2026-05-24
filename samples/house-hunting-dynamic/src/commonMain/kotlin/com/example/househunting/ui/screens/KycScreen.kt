package com.example.househunting.ui.screens

import dev.feather.sdk.*

fun renderKycAmlScreen(
    status: String,
    idCaptured: Boolean,
    selfieCaptured: Boolean,
    amlDeclared: Boolean
): Widget {
    return ScrollView(
        children = listOf(
            // Navbar Top
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("KYC / AML Identity Check", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            Column(
                children = listOf(
                    // Overview
                    Text("Identity & Financial Compliance", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Text("To prevent fraudulent properties listing and satisfy Anti-Money Laundering (AML) laws, we require photographic ID check and a liveness biometric selfie.", color = "#64748B", fontSize = 13, textAlign = "center"),

                    Spacer(flex = 1),
                    Divider(color = "#E2E8F0"),
                    Spacer(flex = 1),

                    when (status) {
                        "VERIFIED" -> {
                            Column(
                                children = listOf(
                                    Text("✅ Account Fully Verified", fontWeight = "bold", fontSize = 18, color = "#2E7D32"),
                                    Text("Your identity is verified. You are compliant with all KYC and local AML real-estate regulations.", color = "#64748B", textAlign = "center"),
                                    Spacer(flex = 1),
                                    Button(text = "Back to Dashboard", action = "action_back", background = "#00695C", textColor = "#FFFFFF", fillMaxWidth = true)
                                ),
                                style = ContainerStyle(spacing = 14, alignment = "center")
                            )
                        }
                        "PENDING" -> {
                            Column(
                                children = listOf(
                                    CircularProgress(color = "#00695C", size = 50, strokeWidth = 5),
                                    Text("⏳ Running Verification Checks...", fontWeight = "bold", fontSize = 16, color = "#E65100"),
                                    Text("Our systems are screening your submitted passport against global AML sanction checklists. This will take under 1 minute.", color = "#64748B", textAlign = "center"),
                                    Spacer(flex = 1),
                                    Button(text = "Refresh Verification Status", action = "action_kyc_refresh", background = "#00695C", textColor = "#FFFFFF", fillMaxWidth = true)
                                ),
                                style = ContainerStyle(spacing = 16, alignment = "center", padding = 16)
                            )
                        }
                        else -> { // UNVERIFIED
                            Column(
                                children = listOf(
                                    // STEP 1: Government ID capture
                                    Row(
                                        children = listOf(
                                            Text("Step 1: Government Issued Passport / ID", fontWeight = "bold", fontSize = 14, color = "#37474F"),
                                            Spacer(flex = 1),
                                            Text(if (idCaptured) "✅ Captured" else "❌ Required", color = if (idCaptured) "#2E7D32" else "#D32F2F", fontSize = 12, fontWeight = "bold")
                                        ),
                                        style = ContainerStyle(fillMaxWidth = true)
                                    ),
                                    Button(
                                        text = "📸 Capture ID Front",
                                        action = "action_kyc_capture_id",
                                        background = if (idCaptured) "#2E7D32" else "#00695C",
                                        textColor = "#FFFFFF",
                                        fillMaxWidth = true
                                    ),

                                    Spacer(flex = 1),
                                    Divider(color = "#E2E8F0"),
                                    Spacer(flex = 1),

                                    // STEP 2: Biometric Liveness Selfie
                                    Row(
                                        children = listOf(
                                            Text("Step 2: Biometric Liveness Selfie", fontWeight = "bold", fontSize = 14, color = "#37474F"),
                                            Spacer(flex = 1),
                                            Text(if (selfieCaptured) "✅ Captured" else "❌ Required", color = if (selfieCaptured) "#2E7D32" else "#D32F2F", fontSize = 12, fontWeight = "bold")
                                        ),
                                        style = ContainerStyle(fillMaxWidth = true)
                                    ),
                                    Button(
                                        text = "🤳 Capture Liveness Selfie",
                                        action = "action_kyc_capture_selfie",
                                        background = if (selfieCaptured) "#2E7D32" else "#00695C",
                                        textColor = "#FFFFFF",
                                        fillMaxWidth = true
                                    ),

                                    Spacer(flex = 1),
                                    Divider(color = "#E2E8F0"),
                                    Spacer(flex = 1),

                                    // STEP 3: AML Compliance Declarations
                                    Row(
                                        children = listOf(
                                            Text("Step 3: AML Compliance Declaration", fontWeight = "bold", fontSize = 14, color = "#37474F"),
                                            Spacer(flex = 1),
                                            Text(if (amlDeclared) "✅ Declared" else "❌ Required", color = if (amlDeclared) "#2E7D32" else "#D32F2F", fontSize = 12, fontWeight = "bold")
                                        ),
                                        style = ContainerStyle(fillMaxWidth = true)
                                    ),
                                    Switch(
                                        checked = amlDeclared,
                                        action = "action_kyc_toggle_aml",
                                        label = "I declare that rent payments/receipts are from legal sources."
                                    ),

                                    Spacer(flex = 1),
                                    Button(
                                        text = "Submit Identity for Screening",
                                        action = "action_kyc_submit",
                                        background = if (idCaptured && selfieCaptured && amlDeclared) "#00695C" else "#B0BEC5",
                                        textColor = "#FFFFFF",
                                        enabled = idCaptured && selfieCaptured && amlDeclared,
                                        fillMaxWidth = true,
                                        cornerRadius = 10,
                                        height = "46px"
                                    )
                                ),
                                style = ContainerStyle(spacing = 12)
                            )
                        }
                    }
                ),
                style = ContainerStyle(padding = 16, spacing = 16)
            )
        )
    }
}

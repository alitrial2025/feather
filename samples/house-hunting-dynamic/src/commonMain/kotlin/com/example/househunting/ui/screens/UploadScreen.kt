package com.example.househunting.ui.screens

import dev.feather.sdk.*

fun renderPhotoVideoUploadScreen(
    utitle: String,
    uprice: String,
    ulocation: String,
    photoUri: String,
    videoUri: String,
    kycStatus: String
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("List New Vacant House", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            if (kycStatus != "VERIFIED") {
                // Identity Check Block Block Block!
                Column(
                    children = listOf(
                        Text("⚠️ Identity Check Required", fontWeight = "bold", color = "#D32F2F", fontSize = 16),
                        Text("KYC verification is mandatory before listing new residential properties.", color = "#64748B", textAlign = "center"),
                        Spacer(flex = 1),
                        Button(text = "Verify My Identity Now", action = "action_go_kyc", background = "#00695C", textColor = "#FFFFFF")
                    ),
                    style = ContainerStyle(padding = 24, spacing = 16, alignment = "center")
                )
            } else {
                // Listing creation form
                Column(
                    children = listOf(
                        TextInput(
                            value = utitle,
                            placeholder = "Property Listing Title",
                            label = "Property Title",
                            action = "action_input_title"
                        ),
                        TextInput(
                            value = uprice,
                            placeholder = "Monthly Rent Price (KES)",
                            label = "Rent Price (KES / month)",
                            action = "action_input_price",
                            keyboardType = "number"
                        ),
                        TextInput(
                            value = ulocation,
                            placeholder = "Neighborhood/Street, Nairobi",
                            label = "Location Address",
                            action = "action_input_location"
                        ),

                        // Payhero Payout Settlement Settings
                        Card(
                            children = listOf(
                                Column(
                                    children = listOf(
                                        Text("💸 M-Pesa Payout Settings (Payhero Split-Billing)", fontWeight = "bold", fontSize = 13, color = "#388E3C"),
                                        Spacer(flex = 1),
                                        Text("Select your payout method to receive tenant rent payments directly to your business wallet:", fontSize = 11, color = "#64748B"),
                                        Spacer(flex = 1),
                                        Row(
                                            children = listOf(
                                                Button(text = "Buy Goods Till", action = "action_payout_type_till", background = "#388E3C", textColor = "#FFFFFF", fontSize = 11, cornerRadius = 6),
                                                Spacer(flex = 1),
                                                Button(text = "Business Paybill", action = "action_payout_type_paybill", background = "#F1F5F9", textColor = "#37474F", fontSize = 11, cornerRadius = 6)
                                            ),
                                            style = ContainerStyle(fillMaxWidth = true)
                                        ),
                                        Spacer(flex = 1),
                                        TextInput(
                                            value = "5544332",
                                            placeholder = "Enter M-Pesa Till Number",
                                            label = "M-Pesa Buy Goods Till Number",
                                            action = "action_input_till"
                                        ),
                                        Spacer(flex = 1),
                                        Card(
                                            children = listOf(
                                                Row(
                                                    children = listOf(
                                                        Text("Verified Business Name:", fontSize = 10, color = "#64748B"),
                                                        Spacer(flex = 1),
                                                        Text("FAIRVIEW APARTMENTS LTD", fontWeight = "bold", color = "#2E7D32", fontSize = 10)
                                                    ),
                                                    style = ContainerStyle(padding = 8)
                                                )
                                            ),
                                            style = ContainerStyle(background = "#E8F5E9", cornerRadius = 6, fillMaxWidth = true)
                                        )
                                    ),
                                    style = ContainerStyle(padding = 12, spacing = 8)
                                )
                            ),
                            style = ContainerStyle(background = "#F1F5F9", cornerRadius = 12, elevation = 1, fillMaxWidth = true)
                        ),

                        Text("Media Assets Upload", fontWeight = "bold", fontSize = 15),
                        
                        Row(
                            children = listOf(
                                Button(
                                    text = if (photoUri.isEmpty()) "📸 Camera/Gallery" else "✅ Image Attached",
                                    action = "action_upload_photo",
                                    background = if (photoUri.isEmpty()) "#00695C" else "#2E7D32",
                                    textColor = "#FFFFFF",
                                    modifier = Modifier.weight(1f)
                                ),
                                Spacer(flex = 1),
                                Button(
                                    text = if (videoUri.isEmpty()) "📹 Record Video (Max 1)" else "✅ Video Attached",
                                    action = "action_upload_video",
                                    background = if (videoUri.isEmpty()) "#37474F" else "#2E7D32",
                                    textColor = "#FFFFFF",
                                    modifier = Modifier.weight(1f)
                                )
                            ),
                            style = ContainerStyle(fillMaxWidth = true, spacing = 10)
                        ),

                        // Previews
                        if (photoUri.isNotEmpty() || videoUri.isNotEmpty()) {
                            Row(
                                children = listOf(
                                    if (photoUri.isNotEmpty()) {
                                        Card(
                                            children = listOf(
                                                Column(
                                                    children = listOf(
                                                        Text("Photo Preview", fontSize = 11, color = "#64748B"),
                                                        Text("Attached", fontWeight = "bold", fontSize = 12, color = "#2E7D32")
                                                    ),
                                                    style = ContainerStyle(spacing = 4, padding = 10)
                                                )
                                            ),
                                            style = ContainerStyle(background = "#ECEFF1", cornerRadius = 8, modifier = Modifier.weight(1f))
                                        )
                                    } else Spacer(flex = 0),

                                    if (videoUri.isNotEmpty()) {
                                        Card(
                                            children = listOf(
                                                Column(
                                                    children = listOf(
                                                        Text("Video Preview (1)", fontSize = 11, color = "#64748B"),
                                                        Text("Ready to stream", fontWeight = "bold", fontSize = 12, color = "#2E7D32")
                                                    ),
                                                    style = ContainerStyle(spacing = 4, padding = 10)
                                                )
                                            ),
                                            style = ContainerStyle(background = "#ECEFF1", cornerRadius = 8, modifier = Modifier.weight(1f))
                                        )
                                    } else Spacer(flex = 0)
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 10)
                            )
                        } else Spacer(flex = 0),

                        Spacer(flex = 1),
                        Button(
                            text = "Publish Dynamic Listing",
                            action = "action_publish_listing",
                            background = if (utitle.isNotEmpty() && uprice.isNotEmpty()) "#388E3C" else "#B0BEC5",
                            textColor = "#FFFFFF",
                            enabled = utitle.isNotEmpty() && uprice.isNotEmpty(),
                            fillMaxWidth = true,
                            cornerRadius = 12,
                            height = "48px"
                        )
                    ),
                    style = ContainerStyle(padding = 16, spacing = 16)
                )
            }
        )
    }
}

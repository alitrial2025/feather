package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData

fun renderPropertyDetail(property: PropertyData, getState: (String) -> String?): Widget {
    return ScrollView(
        children = listOf(
            // Sticky top nav bar
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Property Details", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1),
                    Button(text = "Share", action = "action_share", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Large top image representation
            Box(
                children = listOf(
                    Column(
                        children = listOf(
                            Text(property.title, color = "#FFFFFF", fontWeight = "bold", fontSize = 22),
                            Text("📍 ${property.location}", color = "#E2E8F0", fontSize = 13)
                        ),
                        style = ContainerStyle(spacing = 4)
                    )
                ),
                style = ContainerStyle(
                    height = "220px",
                    gradient = "linear-gradient(#00695C, #37474F)",
                    padding = 16,
                    alignment = "bottom_start"
                )
            ),

            // Features Card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("KES ${property.price.toInt()} / mo", fontWeight = "bold", fontSize = 18, color = "#2E7D32"),
                                    Spacer(flex = 1),
                                    Text("⭐ ${property.rating}", fontWeight = "bold")
                                )
                            ),
                            Divider(color = "#E2E8F0"),
                            Row(
                                children = listOf(
                                    Text("${property.beds} Bedrooms"),
                                    Spacer(flex = 1),
                                    Text("${property.baths} Bathrooms"),
                                    Spacer(flex = 1),
                                    Text("${property.sizeSqFt} Sqft")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Payhero M-Pesa Trust Shield Card (Anti-Scam Protection)
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text(if (property.isPayoutVerified) "🛡️ Payhero Payout Verified" else "⚠️ Unverified Merchant", fontWeight = "bold", fontSize = 13, color = if (property.isPayoutVerified) "#2E7D32" else "#D32F2F"),
                                    Spacer(flex = 1),
                                    Text("Trust Score: ${property.trustScore}%", fontWeight = "bold", fontSize = 12, color = if (property.trustScore >= 90) "#2E7D32" else "#E65100")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            if (property.isPayoutVerified) {
                                Row(
                                    children = listOf(
                                        Text("Direct M-Pesa Payout: ", fontSize = 11, color = "#64748B"),
                                        Text(if (property.payoutType == "TILL") "Buy Goods Till ${property.tillNumber}" else "Paybill ${property.paybillNumber} (A/C ${property.paybillAccount})", fontWeight = "bold", color = "#37474F", fontSize = 11)
                                    )
                                )
                            } else {
                                Text("This landlord's payment destination is not verified. Pay with caution.", fontSize = 11, color = "#D32F2F")
                            },
                            Divider(color = "#E2E8F0"),
                            Row(
                                children = listOf(
                                    Box(
                                        children = listOf(Text("🤝", fontSize = 16)),
                                        style = ContainerStyle(
                                            width = "28px",
                                            height = "28px",
                                            background = "#C8E6C9",
                                            cornerRadius = 14,
                                            alignment = "center"
                                        )
                                    ),
                                    Column(
                                        children = listOf(
                                            Text("KES 200 Viewing Fee Reversal Guarantee", fontWeight = "bold", fontSize = 11, color = "#37474F"),
                                            Text("Protected by Payhero. Reversible if landlord is a no-show.", fontSize = 10, color = "#64748B")
                                        ),
                                        style = ContainerStyle(paddingStart = 8)
                                    )
                                ),
                                style = ContainerStyle(alignment = "center_vertical")
                            )
                        ),
                        style = ContainerStyle(padding = 12, spacing = 8)
                    )
                ),
                style = ContainerStyle(margin = 12, background = "#E8F5E9", cornerRadius = 12, elevation = 1)
            ),

            // Description
            Column(
                children = listOf(
                    Text("Description", fontWeight = "bold", fontSize = 16, color = "#1E293B"),
                    Text(property.description, color = "#64748B", fontSize = 14, lineHeight = 20)
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // Key Amenities
            Column(
                children = listOf(
                    Text("Key Amenities", fontWeight = "bold", fontSize = 16, color = "#1E293B"),
                    Row(
                        children = listOf(
                            renderAmenity("WiFi"),
                            Spacer(flex = 1),
                            renderAmenity("24/7 Water"),
                            Spacer(flex = 1),
                            renderAmenity("Security")
                        ),
                        style = ContainerStyle(fillMaxWidth = true, spacing = 8)
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 10)
            ),

            // MapView Integration (House Location Coordinates)
            Column(
                children = listOf(
                    Text("📍 House Coordinates", fontWeight = "bold", fontSize = 16),
                    MapView(
                        latitude = property.latitude,
                        longitude = property.longitude,
                        zoom = 15f
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 10, height = "200px")
            ),

            // Road Proximity & Deposit Card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("📍 Commute & Location Proximity", fontWeight = "bold", fontSize = 14, color = "#1E293B"),
                                    Spacer(flex = 1)
                                )
                            ),
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Text("🛣️ Distance to Tarmac:", fontSize = 12, color = "#64748B"),
                                    Spacer(flex = 1),
                                    Text("${property.distanceToRoadMeters}m walk to ${property.nearestRoadName}", fontWeight = "bold", fontSize = 12, color = "#37474F")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Row(
                                children = listOf(
                                    Text("🚌 Nearest Matatu Stage:", fontSize = 12, color = "#64748B"),
                                    Spacer(flex = 1),
                                    Text(property.nearestMatatuStage, fontWeight = "bold", fontSize = 12, color = "#37474F")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Divider(color = "#E2E8F0"),
                            Row(
                                children = listOf(
                                    Text("💰 Refundable Deposit Required:", fontSize = 12, color = "#64748B"),
                                    Spacer(flex = 1),
                                    Text("KES ${property.refundableDeposit.toInt()}", fontWeight = "bold", fontSize = 12, color = "#2E7D32")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Text("🛡️ Payhero Escrow Protection: The security deposit is held in a secure trust account and can only be deducted for physical damages verified on-app upon vacating.", fontSize = 10, color = "#64748B")
                        ),
                        style = ContainerStyle(padding = 12, spacing = 8)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 1, background = "#F8F9FA")
            ),

            // 📜 House Rules & Policies Card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("📜 House Rules & Policies", fontWeight = "bold", fontSize = 14, color = "#1E293B"),
                                    Spacer(flex = 1)
                                )
                            ),
                            Spacer(flex = 1),
                            Column(
                                children = property.rules.map { rule ->
                                    Row(
                                        children = listOf(
                                            Text("• ", fontWeight = "bold", color = "#00695C", fontSize = 14),
                                            Text(rule, color = "#475569", fontSize = 13)
                                        ),
                                        style = ContainerStyle(paddingVertical = 2)
                                    )
                                }
                            )
                        ),
                        style = ContainerStyle(padding = 12, spacing = 6)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 1, background = "#FFFFFF")
            ),

            // 💬 Resident Reviews & Ratings Card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("💬 Resident Reviews & Feedback", fontWeight = "bold", fontSize = 14, color = "#1E293B"),
                                    Spacer(flex = 1),
                                    Text("⭐ ${property.rating} / 5.0", fontWeight = "bold", color = "#E65100", fontSize = 13)
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            if (property.reviews.isEmpty()) {
                                Text("No reviews posted yet. Be the first to review!", color = "#64748B", fontSize = 12)
                            } else {
                                Column(
                                    children = property.reviews.map { review ->
                                        Card(
                                            children = listOf(
                                                Column(
                                                    children = listOf(
                                                        Row(
                                                            children = listOf(
                                                                Text(review.author, fontWeight = "bold", fontSize = 12, color = "#1E293B"),
                                                                Spacer(flex = 1),
                                                                Text("⭐ ${review.rating.toInt()}/5", fontWeight = "bold", fontSize = 11, color = "#E65100"),
                                                                Spacer(flex = 1),
                                                                Text(review.date, fontSize = 10, color = "#64748B")
                                                            ),
                                                            style = ContainerStyle(fillMaxWidth = true)
                                                        ),
                                                        Spacer(flex = 1),
                                                        Text(review.comment, color = "#475569", fontSize = 12, lineHeight = 16)
                                                    ),
                                                    style = ContainerStyle(padding = 8, spacing = 4)
                                                )
                                            ),
                                            style = ContainerStyle(background = "#F8F9FA", cornerRadius = 8, fillMaxWidth = true, margin = 4)
                                        )
                                    }
                                )
                            },
                            
                            Divider(color = "#E2E8F0"),
                            
                            // Interactive submit review form
                            Text("✍️ Add Resident Feedback & Satisfaction", fontWeight = "bold", fontSize = 13, color = "#37474F"),
                            Text("Your review directly updates the property trust score & average rating.", fontSize = 11, color = "#64748B"),
                            
                            val selectedRating = getState("review_rating_${property.id}") ?: "5",
                            val reviewComment = getState("review_comment_${property.id}") ?: "",
                            
                            Row(
                                children = listOf(
                                    Text("Select Satisfaction: ", fontSize = 12, color = "#475569"),
                                    Spacer(flex = 1),
                                    Text(when(selectedRating) {
                                        "1" -> "😠 1 Star"
                                        "2" -> "😐 2 Stars"
                                        "3" -> "🙂 3 Stars"
                                        "4" -> "😊 4 Stars"
                                        else -> "🤩 5 Stars"
                                    }, fontWeight = "bold", color = "#E65100", fontSize = 12)
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            
                            Row(
                                children = listOf(
                                    Button(text = "1⭐", action = "action_review_rating_select:1:${property.id}", background = if (selectedRating == "1") "#FFEBEE" else "#F1F5F9", textColor = "#D32F2F", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "2⭐", action = "action_review_rating_select:2:${property.id}", background = if (selectedRating == "2") "#FFF3E0" else "#F1F5F9", textColor = "#E65100", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "3⭐", action = "action_review_rating_select:3:${property.id}", background = if (selectedRating == "3") "#FFFDE7" else "#F1F5F9", textColor = "#FBC02D", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "4⭐", action = "action_review_rating_select:4:${property.id}", background = if (selectedRating == "4") "#E8F5E9" else "#F1F5F9", textColor = "#388E3C", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "5⭐", action = "action_review_rating_select:5:${property.id}", background = if (selectedRating == "5") "#E8F5E9" else "#F1F5F9", textColor = "#2E7D32", cornerRadius = 8, modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 4)
                            ),
                            
                            TextInput(
                                value = reviewComment,
                                placeholder = "What was your satisfaction level living here?",
                                label = "Write a review...",
                                action = "action_review_comment_input:${property.id}"
                            ),
                            
                            Button(
                                text = "Publish Verified Review",
                                action = "action_submit_client_review:${property.id}",
                                background = "#E8F5E9",
                                textColor = "#2E7D32",
                                cornerRadius = 8,
                                fillMaxWidth = true,
                                height = "36px",
                                enabled = reviewComment.isNotEmpty()
                            )
                        ),
                        style = ContainerStyle(padding = 12, spacing = 8)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 1, background = "#FFFFFF")
            ),

            // Caretaker contact card
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Box(style = ContainerStyle(width = "40px", height = "40px", background = "#37474F", cornerRadius = 20)),
                                    Column(
                                        children = listOf(
                                            Text("Arthur Pendelton", fontWeight = "bold", fontSize = 14),
                                            Text("Property Caretaker", fontSize = 12, color = "#64748B")
                                        ),
                                        style = ContainerStyle(paddingStart = 10)
                                    )
                                )
                            ),
                            Row(
                                children = listOf(
                                    Button(text = "Message In-App", action = "action_caretaker_msg", background = "#00695C", textColor = "#FFFFFF", modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "Call Caretaker", action = "action_caretaker_call", background = "#37474F", textColor = "#FFFFFF", modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 10)
                                )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 14)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Floating Bottom Price and Book Online Trigger DOCK
            Row(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("Rent Details", fontSize = 12, color = "#64748B"),
                            Text("KES ${property.price.toInt()} / month", fontWeight = "bold", fontSize = 16, color = "#2E7D32")
                        )
                    ),
                    Spacer(flex = 1),
                    Button(
                        text = "🗓️ Book Online",
                        action = "action_go_booking",
                        background = "#00695C",
                        textColor = "#FFFFFF",
                        paddingHorizontal = 24,
                        cornerRadius = 10
                    )
                ),
                style = ContainerStyle(padding = 16, background = "#FFFFFF")
            )
        )
    )
}

private fun renderAmenity(label: String): Widget {
    return Card(
        children = listOf(
            Text(label, fontWeight = "bold", fontSize = 12, color = "#00695C")
        ),
        style = ContainerStyle(background = "#E0F2F1", padding = 8, cornerRadius = 8)
    )
}

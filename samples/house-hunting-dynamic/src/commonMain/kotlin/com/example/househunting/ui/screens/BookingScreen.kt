package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData

fun renderOnlineBookingScreen(
    property: PropertyData,
    bookingStatus: String,
    kycStatus: String,
    selectedDate: String,
    selectedTime: String
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Online Viewing Booking", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Summary Card
            Card(
                children = listOf(
                    Row(
                        children = listOf(
                            Box(style = ContainerStyle(width = "60px", height = "60px", background = "#00695C", cornerRadius = 8)),
                            Column(
                                children = listOf(
                                    Text(property.title, fontWeight = "bold", fontSize = 14),
                                    Text("📍 ${property.location}", fontSize = 12, color = "#64748B"),
                                    Text("KES ${property.price.toInt()} / mo", fontWeight = "bold", fontSize = 13, color = "#2E7D32")
                                ),
                                style = ContainerStyle(paddingStart = 12, spacing = 2)
                            )
                        )
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 1)
            ),

            if (bookingStatus == "CONFIRMED") {
                // Booking Confirmed State View
                Column(
                    children = listOf(
                        Text("🎉 Booking Confirmed!", fontWeight = "bold", fontSize = 20, color = "#2E7D32"),
                        Spacer(flex = 1),
                        Text("Your viewing has been successfully confirmed. Direct payouts scheduled via Payhero.", color = "#64748B", textAlign = "center"),
                        Spacer(flex = 1),
                        Card(
                            children = listOf(
                                Column(
                                    children = listOf(
                                        Text("Date: $selectedDate", fontWeight = "bold", fontSize = 14),
                                        Text("Time: $selectedTime", fontWeight = "bold", fontSize = 14),
                                        Text("Caretaker: Arthur Pendelton", fontSize = 13, color = "#64748B"),
                                        Divider(color = "#E2E8F0"),
                                        Text("Verified M-Pesa Receipt Code:", fontSize = 11, color = "#64748B"),
                                        Text("MPESA-QRD45TGH67", fontWeight = "bold", color = "#388E3C", fontSize = 13)
                                    ),
                                    style = ContainerStyle(spacing = 6, padding = 12)
                                )
                            ),
                            style = ContainerStyle(background = "#F1F5F9", cornerRadius = 10, fillMaxWidth = true)
                        ),
                        Spacer(flex = 1),
                        Button(text = "Return to Dashboard", action = "action_back", background = "#00695C", textColor = "#FFFFFF", fillMaxWidth = true)
                    ),
                    style = ContainerStyle(padding = 24, spacing = 16, alignment = "center")
                )
            } else if (bookingStatus == "STK_SENT") {
                // Awaiting M-Pesa push callback loader spinner
                Column(
                    children = listOf(
                        CircularProgress(color = "#388E3C"),
                        Spacer(flex = 1),
                        Text("Awaiting M-Pesa Payment...", fontWeight = "bold", fontSize = 18, color = "#388E3C"),
                        Text("We sent an M-Pesa STK push prompt for KES 200 to your Safaricom phone. Please enter your M-Pesa PIN to authorize.", textAlign = "center", color = "#475569", fontSize = 13),
                        Spacer(flex = 1),
                        Card(
                            children = listOf(
                                Column(
                                    children = listOf(
                                        Text("Having trouble receiving the prompt?", fontWeight = "bold", fontSize = 12, color = "#1E293B"),
                                        Text("Make sure your M-Pesa SIM is active, or use our Payhero fallback redirect:", fontSize = 11, color = "#64748B"),
                                        Spacer(flex = 1),
                                        Button(
                                            text = "🚀 Launch Real Payhero Link",
                                            action = "action_payhero_real_gateway",
                                            background = "#388E3C",
                                            textColor = "#FFFFFF",
                                            cornerRadius = 8
                                        )
                                    ),
                                    style = ContainerStyle(padding = 12, spacing = 8)
                                )
                            ),
                            style = ContainerStyle(background = "#FFF3E0", cornerRadius = 10, fillMaxWidth = true)
                        ),
                        Spacer(flex = 1),
                        Button(text = "Cancel Request", action = "action_mpesa_cancel", background = "#F1F5F9", textColor = "#64748B", fillMaxWidth = true)
                    ),
                    style = ContainerStyle(padding = 24, spacing = 16, alignment = "center")
                )
            } else {
                // Interactive Calendar Slots Form
                Column(
                    children = listOf(
                        // Blocker verification banner
                        if (kycStatus != "VERIFIED") {
                            Card(
                                children = listOf(
                                    Column(
                                        children = listOf(
                                            Text("⚠️ Verification Required", fontWeight = "bold", color = "#D32F2F", fontSize = 14),
                                            Text("Anti-Money Laundering (AML) regulations require identity confirmation before booking properties online.", fontSize = 12, color = "#D32F2F"),
                                            Spacer(flex = 1),
                                            Button(text = "Complete KYC Verification Now", action = "action_go_kyc", background = "#D32F2F", textColor = "#FFFFFF", cornerRadius = 8)
                                        ),
                                        style = ContainerStyle(spacing = 8, padding = 12)
                                    )
                                ),
                                style = ContainerStyle(background = "#FFEBEE", cornerRadius = 12)
                            )
                        } else Spacer(flex = 0),

                        Spacer(flex = 1),
                        Text("Choose Viewing Date", fontWeight = "bold", fontSize = 16),
                        LazyRow(
                            items = listOf("2026-05-28", "2026-05-29", "2026-05-30", "2026-06-01").map { date ->
                                val isSelected = date == selectedDate
                                Chip(
                                    label = date,
                                    selected = isSelected,
                                    action = "action_book_date:$date",
                                    color = if (isSelected) "#00695C" else "#FFFFFF"
                                )
                            }
                        ),

                        Spacer(flex = 1),
                        Text("Choose Time Slot", fontWeight = "bold", fontSize = 16),
                        LazyRow(
                            items = listOf("10:00 AM", "12:00 PM", "02:00 PM", "04:30 PM").map { time ->
                                val isSelected = time == selectedTime
                                Chip(
                                    label = time,
                                    selected = isSelected,
                                    action = "action_book_time:$time",
                                    color = if (isSelected) "#00695C" else "#FFFFFF"
                                )
                            }
                        ),

                        Spacer(flex = 1),
                        
                        // Payhero Escrow Invoice Breakdown Card
                        Card(
                            children = listOf(
                                Column(
                                    children = listOf(
                                        Text("Billing Breakdown", fontWeight = "bold", fontSize = 13, color = "#1E293B"),
                                        Spacer(flex = 1),
                                        Row(
                                            children = listOf(
                                                Text("Viewing Fee (Landlord Escrow)", fontSize = 12, color = "#64748B"),
                                                Spacer(flex = 1),
                                                Text("KES 200", fontWeight = "bold", color = "#37474F", fontSize = 12)
                                            )
                                        ),
                                        Row(
                                            children = listOf(
                                                Text("Payhero Convenience Fee", fontSize = 12, color = "#64748B"),
                                                Spacer(flex = 1),
                                                Text("KES 10", fontWeight = "bold", color = "#37474F", fontSize = 12)
                                            )
                                        ),
                                        Divider(color = "#E2E8F0"),
                                        Row(
                                            children = listOf(
                                                Text("Total M-Pesa STK Prompt", fontWeight = "bold", fontSize = 13, color = "#2E7D32"),
                                                Spacer(flex = 1),
                                                Text("KES 210", fontWeight = "bold", color = "#2E7D32", fontSize = 14)
                                            )
                                        ),
                                        Spacer(flex = 1),
                                        Text("🛡️ Anti-Scam Terms: The viewing fee is held safely by Payhero. If the landlord does not show up, cancel the booking and receive a full refund.", fontSize = 10, color = "#388E3C")
                                    ),
                                    style = ContainerStyle(padding = 12, spacing = 6)
                                )
                            ),
                            style = ContainerStyle(background = "#E8F5E9", cornerRadius = 10, fillMaxWidth = true)
                        ),

                        Spacer(flex = 1),
                        Button(
                            text = "Pay viewing fee via M-Pesa",
                            action = "action_submit_booking",
                            background = if (kycStatus == "VERIFIED") "#388E3C" else "#B0BEC5",
                            textColor = "#FFFFFF",
                            enabled = kycStatus == "VERIFIED",
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

package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData

fun renderTenantDashboard(
    selectedCat: String,
    featuredProperties: List<PropertyData>,
    recentProperties: List<PropertyData>,
    getState: (String) -> String?
): Widget {
    return ScrollView(
        children = listOf(
            // Search Input Header
            Column(
                children = listOf(
                    SearchBar(
                        placeholder = "Search location, apartments...",
                        action = "action_search_input"
                    )
                ),
                style = ContainerStyle(padding = 16)
            ),

            // Category select row
            Column(
                children = listOf(
                    Text("Categories", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Spacer(flex = 1),
                    LazyRow(
                        items = listOf("APARTMENTS", "BEDSITTERS", "STANDALONE", "MANSIONS").map { cat ->
                            val isSelected = cat == selectedCat
                            Chip(
                                label = cat.toLowerCase().capitalize(),
                                selected = isSelected,
                                action = "action_select_cat:$cat",
                                color = if (isSelected) "#00695C" else "#FFFFFF"
                            )
                        }
                    )
                ),
                style = ContainerStyle(paddingHorizontal = 16, spacing = 8)
            ),

            // Featured horizontal carousel
            Column(
                children = listOf(
                    Row(
                        children = listOf(
                            Text("Featured Properties", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                            Spacer(flex = 1),
                            Button(text = "See All", action = "action_see_all", background = "transparent", textColor = "#00695C", fontSize = 13)
                        ),
                        style = ContainerStyle(fillMaxWidth = true)
                    ),
                    LazyRow(
                        items = featuredProperties.map { prop ->
                            Card(
                                children = listOf(
                                    Box(
                                        children = listOf(
                                            Text(prop.title, fontWeight = "bold", color = "#FFFFFF", fontSize = 15)
                                        ),
                                        style = ContainerStyle(
                                            height = "120px",
                                            gradient = "linear-gradient(#00695C, #37474F)",
                                            padding = 12
                                        )
                                    ),
                                    Column(
                                        children = listOf(
                                            Text("KES ${prop.price.toInt()} / mo", fontWeight = "bold", color = "#2E7D32", fontSize = 14),
                                            Text("📍 ${prop.location}", fontSize = 11, color = "#64748B", fontWeight = "medium")
                                        ),
                                        style = ContainerStyle(padding = 10, spacing = 4)
                                    )
                                ),
                                action = "action_click_property:${prop.id}",
                                style = ContainerStyle(width = "220px", margin = 6, cornerRadius = 12, elevation = 2)
                            )
                        }
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 10)
            ),

            // Recently Viewed Column Feed
            Column(
                children = listOf(
                    Text("Recently Viewed", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Spacer(flex = 1),
                    Column(
                        children = recentProperties.map { prop ->
                            Card(
                                children = listOf(
                                    Row(
                                        children = listOf(
                                            Box(
                                                style = ContainerStyle(
                                                    width = "70px",
                                                    height = "70px",
                                                    background = "#37474F",
                                                    cornerRadius = 8
                                                )
                                            ),
                                            Column(
                                                children = listOf(
                                                    Text(prop.title, fontWeight = "bold", fontSize = 14, color = "#1E293B"),
                                                    Text("📍 ${prop.location}", fontSize = 12, color = "#64748B"),
                                                    Text("KES ${prop.price.toInt()} / mo", fontWeight = "bold", color = "#2E7D32", fontSize = 13)
                                                ),
                                                style = ContainerStyle(paddingStart = 12, spacing = 2)
                                            )
                                        )
                                    )
                                ),
                                action = "action_click_property:${prop.id}",
                                style = ContainerStyle(fillMaxWidth = true, margin = 6, padding = 8, cornerRadius = 10, elevation = 1)
                            )
                        }
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // Prepaid Utilities (KPLC Prepaid Power Tokens Panel)
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("⚡ KPLC Prepaid Tokens (Payhero Direct)", fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                                    Spacer(flex = 1),
                                    Text("Meter: 38221908234", fontSize = 11, color = "#64748B")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            Text("Purchase KPLC electricity tokens instantly via M-Pesa STK Push.", fontSize = 12, color = "#64748B"),
                            
                            val tokenCode = getState("kplc_token_code") ?: "",
                            if (tokenCode.isNotEmpty()) {
                                Card(
                                    children = listOf(
                                        Column(
                                            children = listOf(
                                                Text("LATEST PURCHASED KPLC TOKEN CODE:", fontSize = 10, color = "#388E3C", fontWeight = "bold"),
                                                Text(tokenCode, fontWeight = "bold", fontSize = 16, color = "#1E293B", textAlign = "center")
                                            ),
                                            style = ContainerStyle(padding = 8, spacing = 4, alignment = "center")
                                        )
                                    ),
                                    style = ContainerStyle(background = "#E8F5E9", cornerRadius = 8, fillMaxWidth = true)
                                )
                            } else Spacer(flex = 0),

                            Row(
                                children = listOf(
                                    Button(text = "⚡ KES 500 Token", action = "action_buy_token:500", background = "#388E3C", textColor = "#FFFFFF", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "⚡ KES 1,000 Token", action = "action_buy_token:1000", background = "#388E3C", textColor = "#FFFFFF", cornerRadius = 8, modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 8)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 10)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Postpaid Utilities (Nairobi Water - NCWSC)
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("💧 Nairobi Water Postpaid Bill (NCWSC)", fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                                    Spacer(flex = 1),
                                    Text("A/C: 9872190", fontSize = 11, color = "#64748B")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            Text("Monthly metered water consumption billing directly retrieved from the landlord's OCR meter sync.", fontSize = 12, color = "#64748B"),
                            
                            val waterPaid = getState("water_bill_paid") == "true",
                            Spacer(flex = 1),
                            Row(
                                children = listOf(
                                    Text("Outstanding Water Balance:", fontSize = 12, color = "#64748B"),
                                    Spacer(flex = 1),
                                    Text(if (waterPaid) "KES 0 (PAID ✅)" else "KES 1,200", fontWeight = "bold", color = if (waterPaid) "#2E7D32" else "#D32F2F", fontSize = 13)
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            
                            if (!waterPaid) {
                                Spacer(flex = 1),
                                Button(
                                    text = "💧 Pay Water Bill (KES 1,200) via M-Pesa",
                                    action = "action_buy_water:1200",
                                    background = "#0288D1",
                                    textColor = "#FFFFFF",
                                    fillMaxWidth = true,
                                    cornerRadius = 8
                                )
                            } else Spacer(flex = 0)
                        ),
                        style = ContainerStyle(padding = 16, spacing = 10)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            )
        )
    )
}

fun renderLandlordDashboard(
    myProperties: List<PropertyData>,
    getState: (String) -> String?
): Widget {
    return ScrollView(
        children = listOf(
            // Analytics Stat Cards Carousel
            Column(
                children = listOf(
                    Text("Analytics Summary", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Spacer(flex = 1),
                    LazyRow(
                        items = listOf(
                            renderStatCard("Monthly Revenue", "KES 845,000", "#00695C"),
                            renderStatCard("Occupancy Rate", "83%", "#2E7D32"),
                            renderStatCard("Active Tickets", "3", "#D32F2F")
                        )
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // Quick Actions 2x2 grid
            Column(
                children = listOf(
                    Text("Quick Actions", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Spacer(flex = 1),
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    renderQuickAction("🆕 Add New House", "action_go_upload"),
                                    Spacer(flex = 1),
                                    renderQuickAction("Manage Tenants", "action_manage_tenants_shortcut")
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 12)
                             ),
                             Spacer(flex = 1),
                             Row(
                                 children = listOf(
                                     renderQuickAction("Rent Statements", "action_statements"),
                                     Spacer(flex = 1),
                                     renderQuickAction("KYC Portal", "action_go_kyc")
                                 ),
                                 style = ContainerStyle(fillMaxWidth = true, spacing = 12)
                             )
                        ),
                        style = ContainerStyle(spacing = 10)
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // My Managed Buildings directory
            Column(
                children = listOf(
                    Text("My Managed Buildings", fontWeight = "bold", fontSize = 18, color = "#1E293B"),
                    Spacer(flex = 1),
                    Column(
                        children = myProperties.map { prop ->
                            Card(
                                children = listOf(
                                    Row(
                                        children = listOf(
                                            Column(
                                                children = listOf(
                                                    Text(prop.title, fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                                                    Text("📍 ${prop.location}", fontSize = 12, color = "#64748B"),
                                                    Spacer(flex = 1),
                                                    Text("${prop.occupiedUnits}/${prop.totalUnits} Units Occupied", fontSize = 13, fontWeight = "medium", color = "#37474F"),
                                                    LinearProgress(
                                                        progress = prop.occupiedUnits.toFloat() / prop.totalUnits.toFloat(),
                                                        color = "#2E7D32",
                                                        trackColor = "#E2E8F0"
                                                    )
                                                ),
                                                style = ContainerStyle(flex = 1, spacing = 4)
                                            )
                                        ),
                                        style = ContainerStyle(padding = 12)
                                    )
                                ),
                                action = "action_click_building:${prop.id}",
                                style = ContainerStyle(fillMaxWidth = true, margin = 6, cornerRadius = 12, elevation = 2)
                            )
                        }
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // Payhero M-Pesa SMS Fraud Shield
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("🚨 Payhero M-Pesa SMS Fraud Shield", fontWeight = "bold", fontSize = 15, color = "#D32F2F"),
                            Spacer(flex = 1),
                            Text("Paste any Safaricom payment confirmation SMS here to instantly check its authenticity against the Payhero webhook ledger:", fontSize = 12, color = "#64748B"),
                            Spacer(flex = 1),
                            TextInput(
                                value = getState("sms_input") ?: "",
                                placeholder = "Paste M-Pesa text here...",
                                label = "Incoming SMS Parser",
                                action = "action_sms_input"
                            ),
                            Spacer(flex = 1),
                            
                            val smsStatus = getState("sms_verification_status") ?: "NONE",
                            if (smsStatus == "VERIFIED") {
                                Card(
                                    children = listOf(
                                        Row(
                                            children = listOf(
                                                Text("✅ TRANSACTION AUTHENTIC: KES 45,000 received on 2026-05-24 for Unit A2. Receipt confirmed by Payhero.", fontSize = 11, color = "#2E7D32", fontWeight = "bold")
                                            ),
                                            style = ContainerStyle(padding = 10)
                                        )
                                    ),
                                    style = ContainerStyle(background = "#E8F5E9", cornerRadius = 8, fillMaxWidth = true)
                                )
                            } else if (smsStatus == "SCAM") {
                                Card(
                                    children = listOf(
                                        Row(
                                            children = listOf(
                                                Text("❌ FRAUD ALERT DETECTED: This transaction code is fake or does not exist on Payhero's ledger. Block transaction immediately!", fontSize = 11, color = "#D32F2F", fontWeight = "bold")
                                            ),
                                            style = ContainerStyle(padding = 10)
                                        )
                                    ),
                                    style = ContainerStyle(background = "#FFEBEE", cornerRadius = 8, fillMaxWidth = true)
                                )
                            } else Spacer(flex = 0),

                            Row(
                                children = listOf(
                                    Button(text = "Verify SMS", action = "action_verify_authentic_sms", background = "#2E7D32", textColor = "#FFFFFF", cornerRadius = 8, modifier = Modifier.weight(1f)),
                                    Spacer(flex = 1),
                                    Button(text = "Test Scam Msg", action = "action_verify_scam_sms", background = "#D32F2F", textColor = "#FFFFFF", cornerRadius = 8, modifier = Modifier.weight(1f))
                                ),
                                style = ContainerStyle(fillMaxWidth = true, spacing = 8)
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 10)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Payhero Server Webhooks Console
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("💻 Payhero Server Webhook Console", fontWeight = "bold", fontSize = 14, color = "#E2E8F0"),
                                    Spacer(flex = 1),
                                    Box(
                                        style = ContainerStyle(width = "8px", height = "8px", background = "#4CAF50", cornerRadius = 4)
                                    ),
                                    Text(" Live Callbacks", fontSize = 11, color = "#4CAF50")
                                ),
                                style = ContainerStyle(fillMaxWidth = true)
                            ),
                            Spacer(flex = 1),
                            Card(
                                children = listOf(
                                    Column(
                                        children = listOf(
                                            Text("[2026-05-24 09:42] GET /api/v2/webhook/payhero HTTP/1.1 200 OK", fontSize = 10, color = "#A7F3D0"),
                                            Text("[2026-05-24 09:42] Verified KES 45,000 paid for Unit A1 • Ref: MPESA-QRD45TGH67", fontSize = 10, color = "#FFFFFF"),
                                            Text("[2026-05-24 09:42] Platform fee (KES 2,250) routed to Admin Till", fontSize = 10, color = "#818CF8"),
                                            Text("[2026-05-24 09:56] GET /api/v2/webhook/payhero HTTP/1.1 200 OK", fontSize = 10, color = "#A7F3D0"),
                                            Text("[2026-05-24 09:56] Verified KES 200 Viewing Fee paid for prop1 • Ref: MPESA-XCV210KL4", fontSize = 10, color = "#FFFFFF")
                                        ),
                                        style = ContainerStyle(spacing = 4, padding = 10)
                                    )
                                ),
                                style = ContainerStyle(background = "#1E293B", cornerRadius = 8, fillMaxWidth = true)
                            )
                        ),
                        style = ContainerStyle(padding = 12, spacing = 8)
                    )
                ),
                style = ContainerStyle(margin = 12, background = "#0F172A", cornerRadius = 12, elevation = 3)
            )
        )
    )
}

private fun renderStatCard(title: String, value: String, bgColor: String): Widget {
    return Card(
        children = listOf(
            Column(
                children = listOf(
                    Text(title, color = "#E2E8F0", fontSize = 12),
                    Text(value, color = "#FFFFFF", fontWeight = "bold", fontSize = 20)
                ),
                style = ContainerStyle(spacing = 6)
            )
        ),
        style = ContainerStyle(
            width = "140px",
            height = "80px",
            background = bgColor,
            cornerRadius = 10,
            padding = 12,
            margin = 4
        )
    )
}

private fun renderQuickAction(title: String, action: String): Widget {
    return Card(
        children = listOf(
            Row(
                children = listOf(
                    Text(title, fontWeight = "bold", fontSize = 13, color = "#37474F")
                ),
                style = ContainerStyle(padding = 14, alignment = "center")
            )
        ),
        action = action,
        style = ContainerStyle(
            background = "#FFFFFF",
            cornerRadius = 12,
            elevation = 1,
            fillMaxWidth = true
        )
    )
}

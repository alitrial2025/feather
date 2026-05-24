package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.TransactionRecord

fun renderStatementsScreen(
    activeDateIndex: Float, // slider value from 0f (Jan) to 3f (April)
    statements: List<TransactionRecord>
): Widget {
    val dateLabel = when (activeDateIndex.toInt()) {
        0 -> "January 2026"
        1 -> "February 2026"
        2 -> "March 2026"
        else -> "April 2026"
    }

    // Filter statement records based on slide time indices
    val filteredStatements = statements.filter { record ->
        when (activeDateIndex.toInt()) {
            0 -> record.date.contains("2026-01")
            1 -> record.date.contains("2026-02")
            2 -> record.date.contains("2026-03")
            else -> record.date.contains("2026-04")
        }
    }

    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Time-Travel Rent Statements", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Time-Travel Slider Controller Panel
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Text("⏳ Financial Statement Time-Travel", fontWeight = "bold", fontSize = 14, color = "#FFFFFF"),
                            Spacer(flex = 1),
                            Text("Active History Date: $dateLabel", fontWeight = "bold", fontSize = 16, color = "#2E7D32"),
                            Spacer(flex = 1),
                            // Dynamic Time Slider
                            Slider(
                                value = activeDateIndex,
                                min = 0f,
                                max = 3f,
                                step = 1f,
                                action = "action_time_travel_slide",
                                trackColor = "#00695C",
                                thumbColor = "#2E7D32"
                            ),
                            Spacer(flex = 1),
                            Text("Slide backwards or forwards to visually morph listings and historical balances instantly.", fontSize = 12, color = "#ECEFF1")
                        ),
                        style = ContainerStyle(padding = 16, spacing = 10)
                    )
                ),
                style = ContainerStyle(
                    margin = 12,
                    gradient = "linear-gradient(#37474F, #263238)",
                    cornerRadius = 12,
                    elevation = 3
                )
            ),

            // Financial Ledger Statement lists
            Column(
                children = listOf(
                    Row(
                        children = listOf(
                            Text("Ledger Transactions ($dateLabel)", fontWeight = "bold", fontSize = 15, color = "#1E293B"),
                            Spacer(flex = 1),
                            Text("Total: KES ${filteredStatements.sumOf { it.amount }.toInt()}", fontWeight = "bold", color = "#2E7D32")
                        ),
                        style = ContainerStyle(fillMaxWidth = true)
                    ),
                    Spacer(flex = 1),
                    if (filteredStatements.isEmpty()) {
                        Card(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        Text("No transactions logged in this calendar month.", color = "#64748B", fontSize = 13)
                                    ),
                                    style = ContainerStyle(padding = 12)
                                )
                            ),
                            style = ContainerStyle(background = "#F1F5F9", cornerRadius = 8, fillMaxWidth = true)
                        )
                    } else {
                        Column(
                            children = filteredStatements.map { record ->
                                Card(
                                    children = listOf(
                                        Row(
                                            children = listOf(
                                                Box(
                                                    children = listOf(Text(if (record.type == "RENT_PAYMENT") "💵" else "⚡", fontSize = 18)),
                                                    style = ContainerStyle(
                                                        width = "40px",
                                                        height = "40px",
                                                        background = "#ECEFF1",
                                                        cornerRadius = 20,
                                                        alignment = "center"
                                                    )
                                                ),
                                                Column(
                                                    children = listOf(
                                                        Text(record.tenantName, fontWeight = "bold", fontSize = 14),
                                                        Text("Unit ${record.unitNumber} • ${record.date}", fontSize = 11, color = "#64748B"),
                                                        if (record.mpesaReceiptCode != null) {
                                                            Text("Code: ${record.mpesaReceiptCode}", fontSize = 10, color = "#388E3C", fontWeight = "bold")
                                                        } else {
                                                            Text("Code: Cash/Direct Payout", fontSize = 10, color = "#64748B")
                                                        }
                                                    ),
                                                    style = ContainerStyle(paddingStart = 10, spacing = 2)
                                                ),
                                                Spacer(flex = 1),
                                                Column(
                                                    children = listOf(
                                                        Text(
                                                            text = "+KES ${record.amount.toInt()}",
                                                            fontWeight = "bold",
                                                            fontSize = 13,
                                                            color = "#2E7D32"
                                                        ),
                                                        if (record.mpesaReceiptCode != null) {
                                                            Card(
                                                                children = listOf(
                                                                    Text("M-PESA", color = "#FFFFFF", fontSize = 8, fontWeight = "bold")
                                                                ),
                                                                style = ContainerStyle(background = "#388E3C", paddingHorizontal = 4, paddingVertical = 2, cornerRadius = 4, margin = 2)
                                                            )
                                                        } else Spacer(flex = 0)
                                                    ),
                                                    style = ContainerStyle(alignment = "end")
                                                )
                                            ),
                                            style = ContainerStyle(alignment = "center_vertical")
                                        )
                                    ),
                                    style = ContainerStyle(fillMaxWidth = true, margin = 4, cornerRadius = 10, elevation = 1)
                                )
                            }
                        )
                    }
                ),
                style = ContainerStyle(padding = 16, spacing = 10)
            )
        )
    )
}

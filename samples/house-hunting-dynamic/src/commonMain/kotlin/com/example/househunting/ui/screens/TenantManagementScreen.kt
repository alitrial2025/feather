package com.example.househunting.ui.screens

import dev.feather.sdk.*
import com.example.househunting.data.PropertyData
import com.example.househunting.data.UnitItemData

fun renderTenantManagement(
    property: PropertyData,
    unitsList: List<UnitItemData>,
    getUnitStatus: (String, String) -> String,
    getState: (String) -> String?
): Widget {
    return ScrollView(
        children = listOf(
            // Top Nav
            Row(
                children = listOf(
                    Button(text = "Back", action = "action_back", background = "#F1F5F9", textColor = "#37474F", cornerRadius = 8),
                    Spacer(flex = 1),
                    Text("Unit Directory", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1)
                ),
                style = ContainerStyle(padding = 12, background = "#FFFFFF")
            ),

            // Building Metric summaries card
            Card(
                children = listOf(
                    Row(
                        children = listOf(
                            renderMetric("Occupancy", "${property.occupiedUnits}/${property.totalUnits}"),
                            Spacer(flex = 1),
                            // Calculate live outstanding balances based on simulated state changes!
                            val overdueSum = unitsList.filter { getUnitStatus(it.id, it.status) == "OVERDUE" }.sumOf { it.monthlyRent }
                            renderMetric("Outstanding", "KES ${overdueSum.toInt()}", "#D32F2F")
                        ),
                        style = ContainerStyle(padding = 16, fillMaxWidth = true)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2)
            ),

            // Building Directory List
            Column(
                children = listOf(
                    Text("Units and Payment Status", fontWeight = "bold", fontSize = 16),
                    Spacer(flex = 1),
                    Column(
                        children = unitsList.map { unit ->
                            val status = getUnitStatus(unit.id, unit.status)

                            Card(
                                children = listOf(
                                    Row(
                                        children = listOf(
                                            Box(
                                                children = listOf(Text(unit.number, fontWeight = "bold", color = "#37474F")),
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
                                                    Text(unit.tenantName ?: "Vacant Unit", fontWeight = "bold", fontSize = 14),
                                                    Text("KES ${unit.monthlyRent.toInt()} / mo", fontSize = 12, color = "#64748B"),
                                                    if (unit.mpesaReceiptCode != null || getUnitStatus(unit.id, "") == "PAID") {
                                                        Text("Ref: MPESA-PAID ✅", fontSize = 10, color = "#2E7D32", fontWeight = "bold")
                                                    } else Spacer(flex = 0)
                                                ),
                                                style = ContainerStyle(paddingStart = 10, spacing = 2)
                                            ),
                                            Spacer(flex = 1),
                                            renderStatusBadge(status),
                                            Spacer(flex = 1),
                                            // Tappable payments manually/remind actions
                                            if (status == "OVERDUE") {
                                                Row(
                                                    children = listOf(
                                                        // 📢 Send SMS alert
                                                        Button(text = "📢", action = "action_remind:${unit.id}", background = "#E0F2F1", textColor = "#00695C", cornerRadius = 8, width = "36px"),
                                                        Spacer(flex = 1),
                                                        // 📲 Trigger Safaricom STK Push invoice to tenant
                                                        Button(text = "📲", action = "action_remind_push:${unit.id}", background = "#FFE0B2", textColor = "#E65100", cornerRadius = 8, width = "36px"),
                                                        Spacer(flex = 1),
                                                        // 💵 Record manual cash payment
                                                        Button(text = "💵", action = "action_pay:${unit.id}", background = "#E8F5E9", textColor = "#2E7D32", cornerRadius = 8, width = "36px")
                                                    ),
                                                    style = ContainerStyle(spacing = 4)
                                                )
                                            } else Spacer(flex = 0)
                                        ),
                                        style = ContainerStyle(alignment = "center_vertical")
                                    )
                                ),
                                style = ContainerStyle(fillMaxWidth = true, margin = 6, padding = 10, cornerRadius = 10, elevation = 1)
                            )
                        }
                    )
                ),
                style = ContainerStyle(padding = 16, spacing = 8)
            ),

            // 📜 House Rules & Policies Manager Card (For Landlords to post/remove rules)
            Card(
                children = listOf(
                    Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Text("📜 House Rules & Policies Manager", fontWeight = "bold", fontSize = 16, color = "#1E293B"),
                                    Spacer(flex = 1)
                                )
                            ),
                            Spacer(flex = 1),
                            Text("Post official house and apartment rules for tenants. Residents must abide by these on active lease signing.", fontSize = 12, color = "#64748B"),
                            Spacer(flex = 1),
                            
                            // List current rules
                            Column(
                                children = property.rules.mapIndexed { index, rule ->
                                    Row(
                                        children = listOf(
                                            Text("${index + 1}. ", fontWeight = "bold", color = "#00695C", fontSize = 13),
                                            Text(rule, color = "#475569", fontSize = 13),
                                            Spacer(flex = 1),
                                            Button(
                                                text = "🗑️",
                                                action = "action_delete_rule:${index}:${property.id}",
                                                background = "transparent",
                                                textColor = "#D32F2F",
                                                cornerRadius = 4,
                                                width = "28px"
                                            )
                                        ),
                                        style = ContainerStyle(paddingVertical = 4, alignment = "center_vertical")
                                    )
                                }
                            ),
                            
                            Divider(color = "#E2E8F0"),
                            
                            val ruleInput = getState("rule_input_${property.id}") ?: "",
                            TextInput(
                                value = ruleInput,
                                placeholder = "e.g., Quiet hours start at 10 PM on weekends",
                                label = "Add Apartment Rule / Policy",
                                action = "action_rule_text_input:${property.id}"
                            ),
                            
                            Button(
                                text = "Publish Rule to Fairview Directory",
                                action = "action_add_rule:${property.id}",
                                background = "#00695C",
                                textColor = "#FFFFFF",
                                cornerRadius = 8,
                                fillMaxWidth = true,
                                enabled = ruleInput.isNotEmpty()
                            )
                        ),
                        style = ContainerStyle(padding = 16, spacing = 12)
                    )
                ),
                style = ContainerStyle(margin = 12, cornerRadius = 12, elevation = 2, background = "#FFFFFF")
            )
        )
    }
}

private fun renderMetric(title: String, value: String, textColor: String = "#1E293B"): Widget {
    return Column(
        children = listOf(
            Text(title, color = "#64748B", fontSize = 12),
            Text(value, fontWeight = "bold", fontSize = 22, color = textColor)
        ),
        style = ContainerStyle(spacing = 4)
    )
}

private fun renderStatusBadge(status: String): Widget {
    val (bgColor, txtColor, text) = when (status) {
        "PAID" -> Triple("#E8F5E9", "#2E7D32", "Paid")
        "OVERDUE" -> Triple("#FFEBEE", "#D32F2F", "Overdue")
        else -> Triple("#ECEFF1", "#64748B", "Vacant")
    }

    return Card(
        children = listOf(
            Text(text, color = txtColor, fontWeight = "bold", fontSize = 11)
        ),
        style = ContainerStyle(background = bgColor, paddingHorizontal = 8, paddingVertical = 4, cornerRadius = 6)
    )
}

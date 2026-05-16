package com.example.helloworld

import dev.feather.sdk.*

class App : FeatherApp() {
    override fun render(): Widget {
        return Column(
            // Header section with dark background
            Row(
                Text(
                    "🪶 Feather Demo",
                    color = "#E0E0FF",
                    fontSize = 28,
                    fontWeight = "bold"
                ),
                background = "#1a1a2e",
                padding = 20,
                width = "match",
                alignment = "center"
            ),

            Spacer(),

            // Welcome card
            Column(
                Text(
                    "Welcome to Feather",
                    color = "#333333",
                    fontSize = 22,
                    fontWeight = "600"
                ),
                Text(
                    "Build native KMP apps without Gradle.",
                    color = "#666666",
                    fontSize = 14
                ),
                background = "#F8F9FA",
                padding = 24,
                cornerRadius = 16,
                borderColor = "#E0E0E0",
                borderWidth = 1,
                width = "match",
                spacing = 8
            ),

            Spacer(),

            // Input section
            TextInput(
                value = "",
                placeholder = "Type your name",
                action = "nameChanged",
                background = "#FFFFFF",
                cornerRadius = 12,
                borderColor = "#667eea",
                borderWidth = 2,
                padding = 12
            ),
            StateText(
                key = "nameChanged",
                prefix = "Hello, ",
                fallback = "Hello, stranger!",
                color = "#667eea",
                fontSize = 16,
                fontWeight = "500"
            ),

            Spacer(),

            // Action buttons row
            Row(
                Button(
                    text = "Primary Action",
                    action = "primaryButtonPressed",
                    background = "#667eea",
                    textColor = "#FFFFFF",
                    cornerRadius = 12,
                    paddingHorizontal = 24,
                    paddingVertical = 12,
                    fontWeight = "bold"
                ),
                Button(
                    text = "Secondary",
                    action = "secondaryPressed",
                    background = "#F0F0F0",
                    textColor = "#333333",
                    cornerRadius = 12,
                    paddingHorizontal = 24,
                    paddingVertical = 12
                ),
                spacing = 12,
                alignment = "center"
            ),

            StateText(
                key = "primaryButtonPressed",
                prefix = "Clicks: ",
                fallback = "Clicks: 0",
                color = "#999999",
                fontSize = 12,
                textAlign = "center"
            ),

            // Container style
            padding = 16,
            spacing = 16,
            background = "#FAFBFC",
            width = "match"
        )
    }

    override fun handleAction(action: String, payload: String?) {
        println("Action: $action, Payload: $payload")
    }
}

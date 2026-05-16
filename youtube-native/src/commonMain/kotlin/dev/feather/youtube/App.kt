package dev.feather.youtube

import dev.feather.sdk.*

class App : FeatherApp() {
    override fun render(): Widget {
        return Column(
            // Styled YouTube Header
            Row(
                Text(
                    "YouTube Native 📺",
                    color = "#FFFFFF",
                    fontSize = 22,
                    fontWeight = "bold"
                ),
                background = "#FF0000",
                padding = 16,
                width = "match",
                alignment = "center"
            ),
            
            Spacer(),
            
            // Info Card
            Column(
                Text(
                    "Feather Runtime Active",
                    fontSize = 18,
                    fontWeight = "600",
                    color = "#212121"
                ),
                Text(
                    "Testing D:/youtube-native with dynamic KMP rendering.",
                    fontSize = 14,
                    color = "#757575"
                ),
                background = "#F5F5F5",
                padding = 16,
                cornerRadius = 12,
                spacing = 8,
                width = "match"
            ),
            
            Spacer(),
            
            // Interaction Section
            TextInput(
                placeholder = "Search YouTube...",
                action = "searchQuery",
                background = "#FFFFFF",
                cornerRadius = 8,
                borderColor = "#E0E0E0",
                borderWidth = 1,
                padding = 12
            ),
            
            Button(
                text = "Fetch Trending",
                action = "fetchTrending",
                background = "#FF0000",
                textColor = "#FFFFFF",
                cornerRadius = 8,
                padding = 14,
                width = "match",
                fontWeight = "bold"
            ),
            
            StateText(
                key = "fetchTrending",
                prefix = "Status: ",
                fallback = "Ready to fetch",
                color = "#666666",
                fontSize = 12,
                textAlign = "center"
            ),
            
            padding = 16,
            spacing = 20,
            width = "match"
        )
    }

    override fun handleAction(action: String, payload: String?) {
        // This will be handled by the CLI and forwarded to the host if needed
    }
}

package com.example.netflix

import dev.feather.sdk.*

@Composable
fun NetflixDetails(movieId: String): Widget {
    return Column(
        style = ContainerStyle(
            background = "#000000",
            fillMaxWidth = true,
            fillMaxHeight = true
        )
    ) {
        // Hero background for details
        Box(style = ContainerStyle(height = "300", width = "match")) {
            Image(
                src = getMoviePoster(movieId),
                style = ImageStyle(contentMode = "crop")
            )
            
            // Top Bar
            Row(
                style = ContainerStyle(
                    position = "absolute",
                    top = 10f,
                    left = 10f,
                    spacing = 20
                )
            ) {
                IconButton(
                    icon = "arrow_back",
                    action = "go_home",
                    color = "#FFFFFF"
                )
            }
        }

        Column(style = ContainerStyle(padding = 16, spacing = 10)) {
            Text(
                "Movie Title $movieId",
                style = TextStyle(color = "#FFFFFF", fontSize = 28, fontWeight = "bold")
            )
            
            Row(style = ContainerStyle(spacing = 10)) {
                Text("2024", style = TextStyle(color = "#999999", fontSize = 14))
                Text("18+", style = TextStyle(color = "#999999", fontSize = 14, background = "#333333"))
                Text("2h 15m", style = TextStyle(color = "#999999", fontSize = 14))
            }

            Button(
                text = "Play",
                action = "play_movie=$movieId",
                style = ButtonStyle(
                    background = "#FFFFFF",
                    textColor = "#000000",
                    width = "match"
                )
            )

            Button(
                text = "Download",
                action = "download",
                style = ButtonStyle(
                    background = "#333333",
                    textColor = "#FFFFFF",
                    width = "match"
                )
            )

            Text(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam.",
                style = TextStyle(color = "#CCCCCC", fontSize = 14)
            )
        }
    }
}

fun getMoviePoster(id: String): String {
    return when (id) {
        "1" -> "https://image.tmdb.org/t/p/w500/8cdclKE5IsTM6UEp4C8jY9uS3m.jpg"
        "2" -> "https://image.tmdb.org/t/p/w500/q71tSuH1B7t7o776vebvST94nTV.jpg"
        "3" -> "https://image.tmdb.org/t/p/w500/uS9mYqbOW5KiB169Xj7joF7o41I.jpg"
        "4" -> "https://image.tmdb.org/t/p/w500/cvp99N0MpxZ17PZ7i07vC7Xf9vG.jpg"
        else -> "https://image.tmdb.org/t/p/original/6Mk9E88LpxWd20fJvT3zK9Z6S1p.jpg"
    }
}

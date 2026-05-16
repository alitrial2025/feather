package com.example.netflix

import dev.feather.sdk.*

@Composable
fun NetflixHome(): Widget {
    return ScrollView(
        style = ContainerStyle(
            background = "#000000",
            fillMaxWidth = true,
            fillMaxHeight = true
        )
    ) {
        Column {
            HeroSection()
            
            MovieRow(
                title = "Trending Now",
                movies = listOf(
                    Movie("1", "https://image.tmdb.org/t/p/w500/8cdclKE5IsTM6UEp4C8jY9uS3m.jpg"),
                    Movie("2", "https://image.tmdb.org/t/p/w500/q71tSuH1B7t7o776vebvST94nTV.jpg"),
                    Movie("3", "https://image.tmdb.org/t/p/w500/uS9mYqbOW5KiB169Xj7joF7o41I.jpg"),
                    Movie("4", "https://image.tmdb.org/t/p/w500/cvp99N0MpxZ17PZ7i07vC7Xf9vG.jpg")
                )
            )

            MovieRow(
                title = "Popular on Feather",
                movies = listOf(
                    Movie("5", "https://image.tmdb.org/t/p/w500/f89U3Y9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y.jpg"),
                    Movie("6", "https://image.tmdb.org/t/p/w500/q6Y9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y.jpg"),
                    Movie("7", "https://image.tmdb.org/t/p/w500/z9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y9Y.jpg")
                )
            )
        }
    }
}

@Composable
fun HeroSection(): Widget {
    return Box(
        style = ContainerStyle(
            width = "match",
            height = "450"
        )
    ) {
        Image(
            src = "https://image.tmdb.org/t/p/original/6Mk9E88LpxWd20fJvT3zK9Z6S1p.jpg",
            style = ImageStyle(contentMode = "crop")
        )
        
        // Gradient overlay (Simulated with a semi-transparent box for now)
        Box(
            style = ContainerStyle(
                background = "#00000088", // Semi-transparent black
                position = "absolute",
                bottom = 0f,
                width = "match",
                height = "150",
                alignment = "center"
            )
        ) {
            Column(style = ContainerStyle(alignment = "center")) {
                Text(
                    "The Witcher",
                    style = TextStyle(color = "#FFFFFF", fontSize = 32, fontWeight = "bold")
                )
                Row(style = ContainerStyle(spacing = 10, padding = 10)) {
                    Button(
                        text = "Play",
                        action = "play_hero",
                        style = ButtonStyle(background = "#FFFFFF", textColor = "#000000")
                    )
                    Button(
                        text = "My List",
                        action = "add_list",
                        style = ButtonStyle(background = "#333333", textColor = "#FFFFFF")
                    )
                }
            }
        }
    }
}

@Composable
fun MovieRow(title: String, movies: List<Movie>): Widget {
    return Column(
        style = ContainerStyle(padding = 10)
    ) {
        Text(
            title,
            style = TextStyle(color = "#FFFFFF", fontSize = 18, fontWeight = "bold")
        )
        Spacer(5)
        ScrollView(
            direction = "horizontal",
            style = ContainerStyle(width = "match")
        ) {
            Row(style = ContainerStyle(spacing = 8)) {
                movies.forEach { movie ->
                    MovieCard(movie)
                }
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie): Widget {
    return Clickable(
        action = "select_movie=${movie.id}"
    ) {
        Box(
            style = ContainerStyle(
                width = "120",
                height = "180",
                cornerRadius = 4,
                clip = true
            )
        ) {
            Image(
                src = movie.posterUrl,
                style = ImageStyle(contentMode = "fill")
            )
        }
    }
}

data class Movie(val id: String, val posterUrl: String)

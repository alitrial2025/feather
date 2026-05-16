package com.example.netflix

import dev.feather.sdk.*

@Import("HomeScreen.kt")
@Import("DetailsScreen.kt")
class NetflixApp : FeatherApp() {
    
    val currentScreen = State("currentScreen", "home")
    val selectedMovieId = State("selectedMovieId", "")

    override fun onCreate() {
        println("Netflix Clone Initialized")
    }

    override fun handleAction(action: String, payload: String?) {
        if (action.startsWith("select_movie=")) {
            val id = action.substringAfter("=")
            selectedMovieId.set(id)
            currentScreen.set("details")
        } else if (action == "go_home") {
            currentScreen.set("home")
        } else if (action.startsWith("play_movie=")) {
            currentScreen.set("player")
        } else if (action == "play_hero") {
            selectedMovieId.set("hero")
            currentScreen.set("player")
        }
    }

    override fun render(): Widget {
        return Box(
            style = ContainerStyle(
                background = "#000000",
                fillMaxWidth = true,
                fillMaxHeight = true
            )
        ) {
            when (currentScreen.get()) {
                "home" -> NetflixHome()
                "details" -> NetflixDetails(selectedMovieId.get())
                "player" -> PlayerScreen(selectedMovieId.get())
                else -> Text("Unknown Screen", style = TextStyle(color = "#FFFFFF"))
            }
        }
    }
}

@Composable
fun PlayerScreen(movieId: String): Widget {
    return Box(
        style = ContainerStyle(background = "#000000", fillMaxWidth = true, fillMaxHeight = true)
    ) {
        VideoPlayer(
            src = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            autoplay = true,
            controls = true
        )
        
        IconButton(
            icon = "close",
            action = "go_home",
            color = "#FFFFFF",
            style = ButtonStyle(position = "absolute", top = 20f, left = 20f)
        )
    }
}

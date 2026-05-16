package dev.feather.host

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MediaModule : FeatherModule {
    override val name: String = "feather-media"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean = false

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun canRender(node: FeatherNode): Boolean {
        return node is FeatherNode.VideoPlayerNode || node is FeatherNode.AudioPlayerNode
    }

    @Composable
    override fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) {
        when (node) {
            is FeatherNode.VideoPlayerNode -> FeatherVideoPlayer(node)
            is FeatherNode.AudioPlayerNode -> FeatherAudioPlayer(node)
            else -> Unit
        }
    }
}

@Composable
private fun FeatherVideoPlayer(node: FeatherNode.VideoPlayerNode) {
    val context = LocalContext.current
    val player = remember(node.src) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(node.src))
            repeatMode = if (node.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = node.autoplay
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier
            .applyContainerStyle(node.style)
            .background(Color.Black),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = node.controls
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
            view.useController = node.controls
        },
    )
}

@Composable
private fun FeatherAudioPlayer(node: FeatherNode.AudioPlayerNode) {
    val context = LocalContext.current
    val player = remember(node.src) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(node.src))
            repeatMode = if (node.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = node.autoplay
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .applyContainerStyle(node.style)
            .background(Color.Black),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = node.controls
                controllerShowTimeoutMs = 0
                controllerHideOnTouch = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
            view.useController = node.controls
            if (node.controls) {
                view.showController()
            } else {
                view.hideController()
            }
        },
    )
}

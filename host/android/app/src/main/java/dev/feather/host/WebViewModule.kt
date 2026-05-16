package dev.feather.host

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

class WebViewModule : FeatherModule {
    override val name: String = "feather-webview"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean = false

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun canRender(node: FeatherNode): Boolean {
        return node is FeatherNode.WebViewNode
    }

    @Composable
    override fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) {
        if (node is FeatherNode.WebViewNode) {
            FeatherWebView(node)
        }
    }
}

@Composable
private fun FeatherWebView(node: FeatherNode.WebViewNode) {
    AndroidView(
        modifier = Modifier
            .applyContainerStyle(node.style)
            .background(Color.White),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                loadUrl(node.url)
            }
        },
        update = { view ->
            if (view.url != node.url) {
                view.loadUrl(node.url)
            }
        },
    )
}

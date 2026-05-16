package dev.feather.modules.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.feather.host.FeatherModule
import dev.feather.host.FeatherNode

class MapsModuleAndroid : FeatherModule {
    override val name = "feather-maps"
    override val version = "1.0.0"

    override fun getWidgets(): List<String> = listOf("MapView")

    override fun canRender(node: FeatherNode): Boolean {
        return node is FeatherNode.CustomNode && node.name == "MapView"
    }

    @Composable
    override fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) {
        // In a real production app, we would use:
        // com.google.maps.android.compose.GoogleMap
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Google Maps View (Native)",
                color = Color.DarkGray
            )
        }
    }
}

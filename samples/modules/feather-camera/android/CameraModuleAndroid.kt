package dev.feather.modules.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.feather.host.FeatherModule
import org.json.JSONObject

/**
 * Android-side implementation of the feather-camera module.
 * Register this with ModuleRegistry in the Host app:
 *
 * ```kotlin
 * ModuleRegistry.register(CameraModuleAndroid())
 * ```
 */
class CameraModuleAndroid : FeatherModule {
    override val name = "CameraPreview"

    @Composable
    override fun Render(json: JSONObject, onAction: (String, String?) -> Unit) {
        val context = LocalContext.current
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            onAction("cameraPermission", if (granted) "granted" else "denied")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                // In a real implementation, this would be a CameraX preview.
                Text(
                    text = "Camera Preview Active",
                    color = Color.White,
                )
            } else {
                androidx.compose.material.Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

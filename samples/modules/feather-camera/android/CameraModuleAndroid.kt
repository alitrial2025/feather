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
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import dev.feather.host.FeatherModuleContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CameraModuleAndroid : FeatherModule {
    override val name = "feather-camera"
    override val version = "1.0.0"

    private var imageCapture: ImageCapture? = null
    private val executor: Executor = Executors.newSingleThreadExecutor()

    override fun getWidgets(): List<String> = listOf("CameraPreview")

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        if (method == "takePhoto") {
            imageCapture?.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    context.sendStateAssignment("lastPhoto", base64)
                    image.close()
                }
            })
            return true
        }
        return false
    }

    override fun canRender(node: FeatherNode): Boolean {
        return node is FeatherNode.CustomNode && node.name == "CameraPreview"
    }

    @Composable
    override fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

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
                .height(400.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder().build()
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                println("CameraX Error: ${e.message}")
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                androidx.compose.material.Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("Enable Camera")
                }
            }
        }
    }
}

package dev.feather.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

// ─── Standard Library Modules ───────────────────────────────────────────────────

@Serializable
data class ModuleCallPayload<T>(
    val type: String = "module_call",
    val id: String,
    val module: String,
    val method: String,
    val payload: T,
    val resultStateKey: String? = null
)

inline fun <reified T> moduleCall(module: String, method: String, payload: T, resultStateKey: String? = null): String {
    // Stable render-time descriptor; the core assigns a unique ID on dispatch.
    val id = ""
    val request = ModuleCallPayload(
        id = id,
        module = module,
        method = method,
        payload = payload,
        resultStateKey = resultStateKey
    )
    return Json { encodeDefaults = true }.encodeToString(request)
}

/** Returns the current platform name ("android" or "ios"). */
fun platform(): String = getState("host_platform") ?: "unknown"

/** Generic native reflection bridge — access any platform API without boilerplate. */
object Native {
    @Serializable
    data class ReflectionRequest(
        val className: String,
        val methodName: String,
        val args: List<String> = emptyList(),
        val resultStateKey: String? = null
    )

    /** 
     * Dynamically call a native method on the host platform.
     * 
     * Example: 
     * Native.call("android.os.Build", "getModel", stateKey = "model")
     * Native.call("UIDevice", "name", stateKey = "deviceName")
     */
    fun call(className: String, methodName: String, args: List<String> = emptyList(), stateKey: String? = null) {
        callModuleMsgpack(
            module = "feather-reflection",
            method = "call",
            payload = ReflectionRequest(className, methodName, args, stateKey),
            stateKey = stateKey ?: "last_native_call"
        )
    }

    /** Gets the device model name (e.g. "Pixel 6", "iPhone 15 Pro"). */
    fun getDeviceModel(stateKey: String) {
        if (platform() == "android") {
            call("android.os.Build", "getModel", stateKey = stateKey)
        } else {
            call("UIDevice", "name", stateKey = stateKey)
        }
    }

    /** Gets the OS version string. */
    fun getOSVersion(stateKey: String) {
        if (platform() == "android") {
            call("android.os.Build.VERSION", "getRelease", stateKey = stateKey)
        } else {
            call("UIDevice", "systemVersion", stateKey = stateKey)
        }
    }
}

/** Access system haptics and vibration. */
object Haptics {
    @Serializable data class ImpactRequest(val style: String)
    @Serializable class EmptyRequest

    /** Trigger a basic system vibration. */
    fun vibrate(): String = moduleCall("feather-device", "vibrate", EmptyRequest())
    /** Trigger impact feedback (light, medium, heavy). */
    fun impact(style: String = "medium"): String = moduleCall("feather-device", "impact", ImpactRequest(style))
}

/** Access the system clipboard. */
object Clipboard {
    @Serializable data class SetTextRequest(val text: String)
    @Serializable class EmptyRequest

    /** Set the clipboard text. */
    fun setText(text: String): String = moduleCall("feather-device", "clipboardSet", SetTextRequest(text))
    /** Get the clipboard text and assign it to a state variable. */
    fun getText(stateKey: String): String = moduleCall("feather-device", "clipboardGet", EmptyRequest(), stateKey)
}

/** HTTP networking module — performs requests through the Rust host. */
object Network {
    @Serializable
    data class NetworkRequest(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val bodyType: String? = "json" // "json" or "form"
    )

    /** Perform an HTTP GET and store result in the given state key. */
    fun get(url: String, stateKey: String, headers: Map<String, String> = emptyMap()): String = 
        moduleCall("feather-network", "get", NetworkRequest(url, headers), stateKey)
    
    /** Perform an HTTP POST with a JSON body. */
    fun post(url: String, body: String, stateKey: String, headers: Map<String, String> = emptyMap()): String =
        moduleCall("feather-network", "post", NetworkRequest(url, headers, body, "json"), stateKey)
    
    /** Perform an HTTP POST with form data. */
    fun postForm(url: String, body: Map<String, String>, stateKey: String, headers: Map<String, String> = emptyMap()): String =
        moduleCall("feather-network", "post", NetworkRequest(url, headers, Json.encodeToString(body), "form"), stateKey)

    /** Perform an HTTP PUT with a JSON body. */
    fun put(url: String, body: String, stateKey: String, headers: Map<String, String> = emptyMap()): String =
        moduleCall("feather-network", "put", NetworkRequest(url, headers, body, "json"), stateKey)
    
    /** Perform an HTTP DELETE. */
    fun delete(url: String, stateKey: String, headers: Map<String, String> = emptyMap()): String = 
        moduleCall("feather-network", "delete", NetworkRequest(url, headers), stateKey)
}

/** Persistent key-value storage (SharedPreferences / UserDefaults). */
object Storage {
    @Serializable data class SetRequest(val key: String, val value: String)
    @Serializable data class KeyRequest(val key: String)
    @Serializable class EmptyRequest

    /** Store a value. */
    fun set(key: String, value: String): String = moduleCall("feather-storage", "set", SetRequest(key, value))
    /** Read a value into a state key. */
    fun get(key: String, stateKey: String): String = moduleCall("feather-storage", "get", KeyRequest(key), stateKey)
    /** Remove a stored value. */
    fun remove(key: String): String = moduleCall("feather-storage", "remove", KeyRequest(key))
    /** Clear all stored values. */
    fun clear(): String = moduleCall("feather-storage", "clear", EmptyRequest())
}

/** Camera access module. */
object Camera {
    @Serializable class EmptyRequest

    /** Take a photo and store the base64 result in the given state key. */
    fun takePhoto(stateKey: String): String = moduleCall("feather-camera", "takePhoto", EmptyRequest(), stateKey)

    /** Pick an image from the gallery. */
    fun pickImage(stateKey: String): String = moduleCall("feather-camera", "pickImage", EmptyRequest(), stateKey)
    
    /** Record a video and store the URI. */
    fun recordVideo(stateKey: String): String = moduleCall("feather-camera", "recordVideo", EmptyRequest(), stateKey)
}

/** Native Maps module. */
object Maps {
    @Serializable
    data class MapRequest(
        val latitude: Double,
        val longitude: Double,
        val label: String? = null,
        val zoom: Float? = null
    )

    /** Open the native maps app at the given location. */
    fun open(latitude: Double, longitude: Double, label: String? = null): String =
        moduleCall("feather-maps", "open", MapRequest(latitude, longitude, label))

    /** Add a marker to an existing MapView. */
    fun addMarker(latitude: Double, longitude: Double, label: String? = null): String =
        moduleCall("feather-maps", "addMarker", MapRequest(latitude, longitude, label))
}

/** Location and GPS module. */
object Location {
    @Serializable class EmptyRequest

    /** Get the current location (lat, lng) into the given state key. */
    fun getCurrent(stateKey: String): String = moduleCall("feather-location", "getCurrent", EmptyRequest(), stateKey)
    /** Start watching location changes and update the state key. */
    fun watchPosition(stateKey: String): String = moduleCall("feather-location", "watchPosition", EmptyRequest(), stateKey)
    /** Stop watching location. */
    fun stopWatch(): String = moduleCall("feather-location", "stopWatch", EmptyRequest())
}

/** Push and local notifications module. */
object Notifications {
    @Serializable class EmptyRequest
    @Serializable data class ScheduleRequest(val title: String, val body: String, val delay: Int)

    /** Request notification permissions. */
    fun requestPermission(): String = moduleCall("feather-notifications", "requestPermission", EmptyRequest())
    /** Schedule a local notification. */
    fun scheduleLocal(title: String, body: String, delaySec: Int = 0): String =
        moduleCall("feather-notifications", "scheduleLocal", ScheduleRequest(title, body, delaySec))
    /** Cancel all scheduled notifications. */
    fun cancelAll(): String = moduleCall("feather-notifications", "cancelAll", EmptyRequest())
    /** Get the push token and store it in a state key. */
    fun getToken(stateKey: String): String = moduleCall("feather-notifications", "getToken", EmptyRequest(), stateKey)
}

/** File system access module. */
object FileSystem {
    @Serializable data class PathRequest(val path: String)
    @Serializable data class WriteRequest(val path: String, val content: String)
    @Serializable data class DownloadRequest(val url: String, val dest: String)

    /** Read a file's contents into a state key. */
    fun readFile(path: String, stateKey: String): String =
        moduleCall("feather-files", "read", PathRequest(path), stateKey)
    /** Write content to a file. */
    fun writeFile(path: String, content: String): String =
        moduleCall("feather-files", "write", WriteRequest(path, content))
    /** Delete a file. */
    fun deleteFile(path: String): String = moduleCall("feather-files", "delete", PathRequest(path))
    /** List files in a directory into a state key. */
    fun listDir(path: String, stateKey: String): String =
        moduleCall("feather-files", "list", PathRequest(path), stateKey)
    /** Download a file from a URL to local storage. */
    fun download(url: String, destPath: String, stateKey: String): String =
        moduleCall("feather-files", "download", DownloadRequest(url, destPath), stateKey)
}

/** Device sensor access (accelerometer, gyroscope, etc.). */
object Sensors {
    @Serializable data class IntervalRequest(val interval: Int)
    @Serializable class EmptyRequest

    /** Start accelerometer updates into a state key. */
    fun startAccelerometer(stateKey: String, intervalMs: Int = 100): String =
        moduleCall("feather-device", "startAccelerometer", IntervalRequest(intervalMs), stateKey)
    /** Stop accelerometer updates. */
    fun stopAccelerometer(): String = moduleCall("feather-device", "stopAccelerometer", EmptyRequest())
    /** Start gyroscope updates into a state key. */
    fun startGyroscope(stateKey: String, intervalMs: Int = 100): String =
        moduleCall("feather-device", "startGyroscope", IntervalRequest(intervalMs), stateKey)
    /** Stop gyroscope updates. */
    fun stopGyroscope(): String = moduleCall("feather-device", "stopGyroscope", EmptyRequest())
}

/** Platform capability checks. */
object Platform {
    @Serializable class EmptyRequest
    
    /** Writes a Boolean to [stateKey] indicating whether the current engine has registered the module. */
    fun hasModule(moduleName: String, stateKey: String): String =
        moduleCall("feather-device", "hasModule", mapOf("moduleName" to moduleName), stateKey)

    @Serializable
    data class RuntimeInfo(val runtime: String, val modules: Map<String, String>)

    /** Request the running engine and its native module versions into [stateKey]. */
    fun getRuntimeInfo(stateKey: String): String =
        moduleCall("feather-device", "getRuntimeInfo", EmptyRequest(), stateKey)
        
    /** Gets information about the current device (OS, version, model). */
    fun getDeviceInfo(stateKey: String): String =
        moduleCall("feather-device", "getDeviceInfo", EmptyRequest(), stateKey)
}

/** Native share sheet module. */
object Share {
    @Serializable data class TextRequest(val text: String)
    @Serializable data class UrlRequest(val url: String)
    @Serializable data class FileRequest(val path: String, val mimeType: String)

    /** Share text content. */
    fun text(content: String): String = moduleCall("feather-device", "shareText", TextRequest(content))
    /** Share a URL. */
    fun url(url: String): String = moduleCall("feather-device", "shareUrl", UrlRequest(url))
    /** Share a file by path. */
    fun file(path: String, mimeType: String = "*/*"): String =
        moduleCall("feather-device", "shareFile", FileRequest(path, mimeType))
}

/** Authentication helpers (biometric, social login). */
object Auth {
    @Serializable data class BiometricRequest(val reason: String)
    @Serializable data class OauthRequest(val provider: String, val clientId: String)

    /** Prompt biometric authentication (fingerprint / face). */
    fun biometric(reason: String, stateKey: String): String =
        moduleCall("feather-auth", "biometric", BiometricRequest(reason), stateKey)
    /** Start an OAuth2 flow. */
    fun oauth(provider: String, clientId: String, stateKey: String): String =
        moduleCall("feather-auth", "oauth", OauthRequest(provider, clientId), stateKey)
}

/** Navigation module for programmatic routing. */
object Navigation {
    @Serializable data class RouteRequest(val route: String, val params: Map<String, String>? = null)
    @Serializable class EmptyRequest

    /** Navigate to a named route. */
    fun navigate(route: String, params: Map<String, String>? = null): String = 
        moduleCall("feather-navigation", "navigate", RouteRequest(route, params))
    /** Go back in the navigation stack. */
    fun goBack(): String = moduleCall("feather-navigation", "goBack", EmptyRequest())
    /** Replace the current route. */
    fun replace(route: String, params: Map<String, String>? = null): String = 
        moduleCall("feather-navigation", "replace", RouteRequest(route, params))
    /** Reset the navigation stack to a single route. */
    fun reset(route: String, params: Map<String, String>? = null): String = 
        moduleCall("feather-navigation", "reset", RouteRequest(route, params))
}

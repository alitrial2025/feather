package dev.feather.host

import java.net.URI
import java.net.URLDecoder

/** QR pairing keeps the complete project URL and its out-of-band signing key. */
data class ProjectConnection(val baseUrl: String, val socketUrl: String, val publicKey: String?) {
    companion object {
        fun parse(input: String, allowProjectKey: Boolean): ProjectConnection {
            var address = input.trim()
            var key: String? = null
            if (address.startsWith("feather:")) {
                val link = URI(address)
                require(link.host == "open" && link.rawFragment == null) { "Invalid Feather project link" }
                val values = mutableMapOf<String, String>()
                for (item in link.rawQuery.orEmpty().split('&')) {
                    val parts = item.split('=', limit = 2)
                    require(parts.size == 2 && !values.containsKey(parts[0])) { "Invalid project parameters" }
                    values[parts[0]] = URLDecoder.decode(parts[1], "UTF-8")
                }
                address = values["url"] ?: error("Project URL is missing")
                key = values["key"]?.takeIf { it.isNotBlank() }
                if (key != null) {
                    require(allowProjectKey) { "Project pairing is only available in the development client" }
                    require(Regex("[A-Za-z0-9+/]{43}=").matches(key)) { "Invalid project signing key" }
                }
            }
            if (!address.contains("://")) {
                address = "http://" + address + if (address.contains(':')) "" else ":8080"
            }
            val uri = URI(address)
            require(uri.scheme == "http" || uri.scheme == "https") { "Use an HTTP or HTTPS project URL" }
            require(!uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Invalid project URL" }
            require(uri.port == -1 || uri.port in 1..65535) { "Invalid server port" }
            require(uri.rawPath.orEmpty().split('/').none {
                val part = URLDecoder.decode(it, "UTF-8")
                part == "." || part == ".." || part.contains('/') || part.contains('\\')
            }) { "Invalid project path" }
            val base = address.trimEnd('/')
            val socket = (if (uri.scheme == "https") "wss" else "ws") + base.substring(base.indexOf(':')) + "/ws"
            return ProjectConnection(base, socket, key)
        }
    }
}

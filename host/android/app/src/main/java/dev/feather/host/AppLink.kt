package dev.feather.host

import java.net.URI
import org.json.JSONArray

/** Resolve a path against the declarations of the verified standalone bundle. */
internal fun appLinkPath(input: String, links: JSONArray?): String? {
    val url = runCatching { URI(input) }.getOrNull() ?: return null
    if (url.scheme != "feather" || url.rawAuthority != "app" ||
        url.rawQuery != null || url.rawFragment != null) return null
    val path = url.rawPath ?: return null
    if (!path.startsWith("/") || path.length > 1024 || path.split('/').drop(1).any { segment ->
        segment.isEmpty() || segment == "." || segment == ".." ||
            !segment.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_." }
    }) return null
    for (index in 0 until (links?.length() ?: 0)) {
        val link = links?.optJSONObject(index) ?: continue
        if (link.optString("scheme") != "feather" || link.optString("host") != "app") continue
        if (link.isNull("path_prefix")) continue
        val prefix = link.optString("path_prefix").trimEnd('/')
        if (prefix.isNotEmpty() && (path == prefix || path.startsWith("$prefix/"))) return path
    }
    return null
}

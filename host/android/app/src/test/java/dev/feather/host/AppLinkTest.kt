package dev.feather.host

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinkTest {
    private val links = JSONArray("""[{"scheme":"feather","host":"app","path_prefix":"/device-info"}]""")

    @Test fun acceptsOnlyDeclaredAppPaths() {
        assertEquals("/device-info", appLinkPath("feather://app/device-info", links))
        assertEquals("/device-info/display", appLinkPath("feather://app/device-info/display", links))
        assertNull(appLinkPath("feather://app/device-info", null))
        for (url in listOf(
            "feather://open/device-info",
            "feather://app/device-information",
            "feather://app/device-info/../admin",
            "feather://app/device-info/%2fadmin",
            "feather://app/device-info?target=admin",
            "feather://example.com@app/device-info"
        )) assertNull(url, appLinkPath(url, links))
    }
}

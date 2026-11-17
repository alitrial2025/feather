package dev.feather.host

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CounterDeviceTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun packagedKotlinRunsWithoutADevelopmentServer() {
        app.waitUntil(timeoutMillis = 30000) {
            app.onAllNodesWithText("Kotlin counter: 0").fetchSemanticsNodes().isNotEmpty()
        }
        app.onNodeWithText("Kotlin counter: 0").assertIsDisplayed()
        app.onNodeWithText("Increment").performClick()
        app.waitUntil(timeoutMillis = 15000) {
            app.onAllNodesWithText("Kotlin counter: 1").fetchSemanticsNodes().isNotEmpty()
        }
        app.onNodeWithText("Kotlin counter: 1").assertIsDisplayed()
    }
}

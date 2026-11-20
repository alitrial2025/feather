package dev.feather.host

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ChatDeviceTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun opensConversationAndRestoresMessageAfterActivityRecreation() {
        val message = "Feather device test ${System.currentTimeMillis()}"
        app.waitUntil(30_000) {
            app.onAllNodesWithText("WhatsApp").fetchSemanticsNodes().isNotEmpty()
        }
        app.activityRule.scenario.onActivity { activity ->
            val link = Intent(Intent.ACTION_VIEW, Uri.parse("feather://app/chat/amina"), activity, MainActivity::class.java)
            activity.startActivity(link.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        app.onNodeWithText("Conversation").assertIsDisplayed()
        app.onNode(hasSetTextAction()).performTextReplacement(message)
        app.onNodeWithContentDescription("send").performClick()
        app.onNodeWithContentDescription("back").performClick()
        app.waitUntil(15_000) {
            app.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
        app.activityRule.scenario.recreate()
        app.waitUntil(15_000) {
            app.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
        app.onNodeWithText(message).assertIsDisplayed()
    }
}

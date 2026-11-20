package dev.feather.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonWidgetContractTest {
    @Test
    fun parsesScaffoldNewChatButtonAndConcreteTabs() {
        val payload = """
            {"type":"render","root":{"type":"Scaffold","topBar":null,
              "floatingActionButton":{"type":"FloatingActionButton","icon":"chat","action":"new"},
              "body":{"type":"BottomTabs","tabs":[{"title":"Chats","route":"chats",
                "content":{"type":"Text","text":"Your chats"}}]}}}
        """.trimIndent()
        val (root, errors) = parseRenderMessage(payload, ModuleRegistry())
        assertTrue(errors.toString(), errors.isEmpty())
        val scaffold = root as FeatherNode.ScaffoldNode
        val button = scaffold.floatingActionButton as FeatherNode.FloatingActionButtonNode
        assertEquals("new", button.action)
        val tabs = scaffold.body as FeatherNode.BottomTabsNode
        val tab = tabs.tabs.single() as FeatherNode.TabNode
        assertEquals("chats", tab.route)
        assertEquals("Your chats", (tab.content as FeatherNode.TextNode).text)
    }
}

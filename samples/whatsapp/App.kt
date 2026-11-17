package feather.whatsapp

import dev.feather.sdk.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class Message(val text: String, val mine: Boolean = false, val time: String = "10:42")
data class Chat(val id: String, val name: String, val initials: String, val preview: String, val time: String,
    val color: String, val unread: Int = 0, val group: Boolean = false, val favorite: Boolean = false)

class WhatsApp : FeatherApp() {
    private val green = "#008069"
    private val ink = "#111B21"
    private val muted = "#667781"
    private val chats = listOf(
        Chat("amina", "Amina Hassan", "AH", "Are we still meeting at 6?", "10:42", "#D7E9D3", 2, favorite = true),
        Chat("weekend", "Weekend plans", "WP", "Jane: That sounds perfect!", "10:26", "#E8DBF0", 3, group = true),
        Chat("david", "David Otieno", "DO", "You: See you tomorrow 👋", "09:58", "#D7E9F4", favorite = true),
        Chat("design", "Design team", "DT", "The new mockups look great", "09:32", "#F8E2B5", group = true),
        Chat("sarah", "Sarah Kim", "SK", "Thanks for sending that over", "Yesterday", "#F6D7DA"),
        Chat("family", "Family", "F", "Mum: Have a lovely day ❤️", "Yesterday", "#DDE8CE", group = true),
        Chat("james", "James Mwangi", "JM", "Let's catch up this weekend", "Monday", "#D9DEF2")
    )
    private fun read(key: String, fallback: String = "") = getState(key) ?: fallback
    private fun write(key: String, value: String) = setState(key, Json.encodeToString(value))
    private fun messages(id: String): List<Message> = getState("messages:$id")?.let {
        runCatching { Json.decodeFromString<List<Message>>(it) }.getOrNull()
    } ?: listOf(Message("Hey! How's your day going?", time = "10:38"),
        Message("Really well! Just getting a few things done.", true, "10:39"),
        Message("Are we still meeting at 6?", time = "10:42"))

    override fun onCreate() {
        if (getState("screen") == null) write("screen", "chats")
    }

    override fun handleAction(action: String, payload: String?) {
        val value = payload?.let { runCatching { Json.decodeFromString<String>(it) }.getOrDefault(it) }.orEmpty()
        when {
            action.startsWith("open:") -> {
                val id = action.removePrefix("open:")
                write("chat", id); write("screen", "conversation"); setState("read:$id", "true"); write("notice", "")
            }
            action.startsWith("tab:") -> { write("screen", action.removePrefix("tab:")); write("query", "") }
            action.startsWith("filter:") -> write("filter", action.removePrefix("filter:"))
            action == "back" -> { write("screen", "chats"); write("notice", "") }
            action == "new" -> write("screen", "contacts")
            action == "search" -> setState("searching", if (read("searching") == "true") "false" else "true")
            action == "query" -> write("query", value)
            action == "draft" -> write("draft:${read("chat")}", value)
            action == "send" -> {
                val id = read("chat")
                val text = read("draft:$id").trim()
                if (text.isNotEmpty()) {
                    setState("messages:$id", Json.encodeToString(messages(id) + Message(text, true, "Now")))
                    write("draft:$id", "")
                }
            }
            action == "status-draft" -> write("status-draft", value)
            action == "post-status" -> { write("status", read("status-draft")); write("status-draft", "") }
            action == "call" -> write("notice", "Voice and video calls are coming next.")
        }
    }

    private fun text(value: String, size: Int = 16, color: String = ink, bold: Boolean = false) =
        Text(value, style = TextStyle(fontSize = size, color = color, fontWeight = if (bold) "bold" else "normal"))
    private fun avatar(chat: Chat) = Box(text(chat.initials, 18, green, true),
        style = ContainerStyle(width = "50", height = "50", cornerRadius = 25, background = chat.color, alignment = "center"))
    private fun icon(name: String, action: String, color: String = ink) = IconButton(name, action, color = color, size = 24)
    private fun navigation(active: String): Widget = Row(
        *listOf("Chats" to "chat", "Updates" to "refresh", "Communities" to "person", "Calls" to "phone").map { (title, symbol) ->
            Clickable("tab:${title.lowercase()}", Column(
                Icon(symbol, color = if (title.lowercase() == active) green else muted),
                text(title, 12, if (title.lowercase() == active) green else muted, title.lowercase() == active),
                style = ContainerStyle(width = "85", paddingVertical = 12, spacing = 4, alignment = "center")
            ))
        }.toTypedArray(), style = ContainerStyle(fillMaxWidth = true, background = "#FFFFFF", alignment = "center")
    )
    private fun header(title: String = "WhatsApp"): Widget = Column(
        Row(text(title, 27, green, true), Spacer(), icon("camera", "call"), icon("search", "search"), icon("more_vert", "new"),
            style = ContainerStyle(fillMaxWidth = true, paddingHorizontal = 18, paddingVertical = 12, alignment = "center", background = "#FFFFFF")),
        if (read("searching") == "true") TextInput(value = read("query"), action = "query", placeholder = "Search chats",
            style = InputStyle(background = "#F0F2F5", cornerRadius = 24, padding = 12, width = "fill")) else Box(),
        Row(*listOf("All", "Unread", "Favorites", "Groups").map { label ->
            Button(label, "filter:$label", style = ButtonStyle(
                background = if (read("filter", "All") == label) "#D9FDD3" else "#F0F2F5",
                textColor = if (read("filter", "All") == label) "#006B56" else muted,
                cornerRadius = 22, fontSize = 13, paddingHorizontal = 12, paddingVertical = 4))
        }.toTypedArray(), style = ContainerStyle(paddingHorizontal = 12, spacing = 5, paddingBottom = 10, background = "#FFFFFF")),
        style = ContainerStyle(fillMaxWidth = true, background = "#FFFFFF")
    )
    private fun chatRow(chat: Chat): Widget {
        val sent = getState("messages:${chat.id}")?.let { messages(chat.id).lastOrNull() }
        val unread = if (read("read:${chat.id}") == "true") 0 else chat.unread
        return Clickable("open:${chat.id}", Row(avatar(chat),
            Column(Row(text(chat.name, 16, bold = true), Spacer(), text(sent?.time ?: chat.time, 11, if (unread > 0) green else muted),
                style = ContainerStyle(fillMaxWidth = true)),
                Row(text(sent?.text ?: chat.preview, 13, muted),
                    style = ContainerStyle(fillMaxWidth = true)),
                style = ContainerStyle(width = "260", spacing = 7)),
            style = ContainerStyle(fillMaxWidth = true, paddingHorizontal = 16, paddingVertical = 13, spacing = 13, alignment = "center")))
    }
    override fun render(): Widget {
        val screen = read("screen", "chats")
        if (screen == "conversation") return conversation()
        if (screen == "contacts") return Scaffold(topBar = Row(icon("back", "back"), text("New chat", 22, bold = true),
            style = ContainerStyle(padding = 12, alignment = "center", spacing = 12)), body = LazyColumn(chats.map(::chatRow), ContainerStyle(fillMaxWidth = true)))
        if (screen == "updates") return Scaffold(topBar = header("Updates"), bottomBar = navigation(screen), body = Column(
            text("My status", 20, bold = true), text(read("status", "Share a moment with your contacts"), 15, muted),
            TextInput(value = read("status-draft"), placeholder = "What's on your mind?", action = "status-draft"),
            Button("Post status", "post-status", background = green, textColor = "#FFFFFF"),
            style = ContainerStyle(padding = 20, spacing = 16)))
        if (screen == "calls") return Scaffold(topBar = header("Calls"), bottomBar = navigation(screen), body = Column(
            Icon("phone", size = 48, color = green), text("Your calls", 22, bold = true), text("Voice and video calling is coming next.", 15, muted),
            style = ContainerStyle(padding = 28, spacing = 16, alignment = "center")))
        val query = read("query")
        val filter = read("filter", "All")
        val visible = chats.filter { chat -> chat.name.contains(query, ignoreCase = true) &&
            (screen != "communities" || chat.group) && when (filter) {
                "Unread" -> chat.unread > 0 && read("read:${chat.id}") != "true"
                "Favorites" -> chat.favorite
                "Groups" -> chat.group
                else -> true
            } }
        return Scaffold(topBar = header(if (screen == "communities") "Communities" else "WhatsApp"), bottomBar = navigation(screen),
            floatingActionButton = FloatingActionButton("chat", "new", background = green, iconColor = "#FFFFFF"),
            body = LazyColumn(visible.map(::chatRow) + text("Your conversations, all together.", 12, muted), ContainerStyle(fillMaxWidth = true, background = "#FFFFFF")))
    }
    private fun conversation(): Widget {
        val chat = chats.firstOrNull { it.id == read("chat") } ?: chats.first()
        val bubbles = messages(chat.id).map { message ->
            val bubble = Column(text(message.text), text(message.time, 10, muted),
                style = ContainerStyle(width = "270", padding = 12, spacing = 5, cornerRadius = 14,
                    background = if (message.mine) "#D9FDD3" else "#FFFFFF"))
            Row(*if (message.mine) arrayOf(Spacer(), bubble) else arrayOf(bubble, Spacer()),
                style = ContainerStyle(fillMaxWidth = true, paddingHorizontal = 12, paddingVertical = 5))
        }
        return Scaffold(
            topBar = Row(icon("back", "back", green), avatar(chat), Column(text(chat.name, 17, bold = true), text("Conversation", 12, muted)),
                Spacer(), icon("phone", "call", green), style = ContainerStyle(fillMaxWidth = true, padding = 8, spacing = 5, alignment = "center", background = "#FFFFFF")),
            bottomBar = Column(
                if (read("notice").isNotEmpty()) text(read("notice"), 12, muted) else Box(),
                Row(TextInput(value = read("draft:${chat.id}"), action = "draft", placeholder = "Message",
                    style = InputStyle(width = "280", background = "#FFFFFF", cornerRadius = 25, padding = 12)),
                    IconButton("send", "send", background = green, color = "#FFFFFF"),
                    style = ContainerStyle(fillMaxWidth = true, padding = 10, spacing = 8, alignment = "center", background = "#EFEAE2"))),
            body = LazyColumn(bubbles, style = ContainerStyle(fillMaxWidth = true, fillMaxHeight = true, paddingTop = 14, background = "#EFEAE2")))
    }
}

fun main() { installApp(WhatsApp()) }

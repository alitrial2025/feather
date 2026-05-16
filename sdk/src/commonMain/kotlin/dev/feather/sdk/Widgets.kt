package dev.feather.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─── Core Widget Interface ───────────────────────────────────────────────────

/** Base interface for all renderable Feather UI widgets. */
@Serializable
sealed interface Widget

// ─── Basic Widgets ────────────────────────────────────────────────────────────

/** Renders static text. */
@Serializable
@SerialName("Text")
class Text(
    val text: String,
    val style: TextStyle? = null,
    val color: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val textDecoration: String? = null,
    val maxLines: Int? = null,
    val fontFamily: String? = null,
) : Widget

/** Renders text derived from CLI runtime state. */
@Serializable
@SerialName("StateText")
class StateText(
    val key: String,
    val prefix: String = "",
    val fallback: String = "",
    val style: TextStyle? = null,
    val color: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val textAlign: String? = null,
) : Widget

/** Renders a native text input field. */
@Serializable
@SerialName("TextInput")
class TextInput(
    val value: String = "",
    val placeholder: String = "",
    val action: String,
    val style: InputStyle? = null,
    val label: String? = null,
    val color: String? = null,
    val fontSize: Int? = null,
    val background: String? = null,
    val cornerRadius: Int? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val padding: Int? = null,
    val placeholderColor: String? = null,
    val width: String? = null,
    val height: String? = null,
    val keyboardType: String? = null,
    val maxLength: Int? = null,
    val multiline: Boolean? = null,
    val secure: Boolean? = null,
) : Widget

/** Renders a native button. */
@Serializable
@SerialName("Button")
class Button(
    val text: String,
    val action: String,
    val style: ButtonStyle? = null,
    val enabled: Boolean = true,
    val background: String? = null,
    val textColor: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val cornerRadius: Int? = null,
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val width: String? = null,
    val height: String? = null,
    val opacity: Float? = null,
    val elevation: Int? = null,
) : Widget

/** Renders a remote or local image. */
@Serializable
@SerialName("Image")
class Image(
    val src: String,
    val alt: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val style: ImageStyle? = null,
) : Widget

/** Renders flexible empty space. */
@Serializable
@SerialName("Spacer")
class Spacer(val flex: Int = 1) : Widget

/** Renders a clickable icon. */
@Serializable
@SerialName("Icon")
class Icon(
    val name: String,
    val size: Int = 24,
    val color: String? = null,
    val action: String? = null,
) : Widget

/** A placeholder for dynamic/native widgets provided by modules. */
@Serializable
@SerialName("Custom")
class Custom(
    val module: String,
    val name: String,
    val props: Map<String, String> = emptyMap(),
) : Widget

// ─── Module Widgets ───────────────────────────────────────────────────────────

/** Renders a native camera preview. Provided by feather-camera module. */
@Serializable
@SerialName("CameraPreview")
class CameraPreview(
    val onPermissionResult: String? = null,
) : Widget

/** Renders a native map view. Provided by feather-maps module. */
@Serializable
@SerialName("MapView")
class MapView(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val zoom: Float? = null,
) : Widget

/** Renders a tappable icon button. */
@Serializable
@SerialName("IconButton")
class IconButton(
    val icon: String,
    val action: String,
    val size: Int = 24,
    val color: String? = null,
    val background: String? = null,
) : Widget

// ─── Layout Containers ────────────────────────────────────────────────────────

/** Renders child widgets vertically. */
@Serializable
@SerialName("Column")
class Column(val children: List<Widget>, val style: ContainerStyle? = null) : Widget {
    constructor(
        vararg children: Widget,
        style: ContainerStyle? = null,
        padding: Int? = null,
        paddingHorizontal: Int? = null,
        paddingVertical: Int? = null,
        background: String? = null,
        cornerRadius: Int? = null,
        borderColor: String? = null,
        borderWidth: Int? = null,
        width: String? = null,
        height: String? = null,
        alignment: String? = null,
        spacing: Int? = null,
        opacity: Float? = null,
    ) : this(
        children = children.toList(),
        style = style ?: ContainerStyle(
            padding = padding,
            paddingHorizontal = paddingHorizontal,
            paddingVertical = paddingVertical,
            background = background,
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            borderWidth = borderWidth,
            width = width,
            height = height,
            alignment = alignment,
            spacing = spacing,
            opacity = opacity,
        )
    )
}

/** Renders child widgets horizontally. */
@Serializable
@SerialName("Row")
class Row(val children: List<Widget>, val style: ContainerStyle? = null) : Widget {
    constructor(
        vararg children: Widget,
        style: ContainerStyle? = null,
        padding: Int? = null,
        paddingHorizontal: Int? = null,
        paddingVertical: Int? = null,
        background: String? = null,
        cornerRadius: Int? = null,
        borderColor: String? = null,
        borderWidth: Int? = null,
        width: String? = null,
        height: String? = null,
        alignment: String? = null,
        spacing: Int? = null,
        opacity: Float? = null,
    ) : this(
        children = children.toList(),
        style = style ?: ContainerStyle(
            padding = padding,
            paddingHorizontal = paddingHorizontal,
            paddingVertical = paddingVertical,
            background = background,
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            borderWidth = borderWidth,
            width = width,
            height = height,
            alignment = alignment,
            spacing = spacing,
            opacity = opacity,
        )
    )
}

/** Renders a simple container box (ZStack). */
@Serializable
@SerialName("Box")
class Box(val children: List<Widget>, val style: ContainerStyle? = null) : Widget {
    constructor(vararg children: Widget, style: ContainerStyle? = null) : this(children.toList(), style)
}

/** Efficiently renders a scrollable vertical list. */
@Serializable
@SerialName("LazyColumn")
class LazyColumn(val items: List<Widget>, val style: ContainerStyle? = null) : Widget {
    constructor(vararg items: Widget, style: ContainerStyle? = null) : this(items.toList(), style)
}

/** Efficiently renders a scrollable horizontal list. */
@Serializable
@SerialName("LazyRow")
class LazyRow(val items: List<Widget>, val style: ContainerStyle? = null) : Widget {
    constructor(vararg items: Widget, style: ContainerStyle? = null) : this(items.toList(), style)
}

/** Efficiently renders a scrollable grid. */
@Serializable
@SerialName("LazyGrid")
class LazyGrid(
    val items: List<Widget>,
    val columns: Int = 2,
    val style: ContainerStyle? = null,
) : Widget

/** Scrollable container for arbitrary children. */
@Serializable
@SerialName("ScrollView")
class ScrollView(
    val children: List<Widget>,
    val direction: String = "vertical",
    val style: ContainerStyle? = null,
) : Widget {
    constructor(vararg children: Widget, style: ContainerStyle? = null) : this(children.toList(), style = style)
}

/** Surface is a container with elevation and shape. */
@Serializable
@SerialName("Surface")
class Surface(
    val children: List<Widget>,
    val style: ContainerStyle? = null,
) : Widget {
    constructor(vararg children: Widget, style: ContainerStyle? = null) : this(children.toList(), style)
}

/** A styled card container. */
@Serializable
@SerialName("Card")
class Card(
    val children: List<Widget>,
    val style: ContainerStyle? = null,
    val action: String? = null,
) : Widget {
    constructor(vararg children: Widget, style: ContainerStyle? = null, action: String? = null) : this(children.toList(), style, action)
}

/** Constrains child to a given aspect ratio. */
@Serializable
@SerialName("AspectRatio")
class AspectRatio(
    val ratio: Float,
    val child: Widget,
) : Widget

/** Horizontal line separator. */
@Serializable
@SerialName("Divider")
class Divider(
    val color: String? = null,
    val thickness: Int = 1,
    val indent: Int = 0,
) : Widget

// ─── Interactive Widgets ──────────────────────────────────────────────────────

/** Toggle switch. */
@Serializable
@SerialName("Switch")
class Switch(
    val checked: Boolean = false,
    val action: String,
    val enabled: Boolean = true,
    val trackColor: String? = null,
    val thumbColor: String? = null,
    val label: String? = null,
) : Widget

/** Checkbox. */
@Serializable
@SerialName("Checkbox")
class Checkbox(
    val checked: Boolean = false,
    val action: String,
    val enabled: Boolean = true,
    val color: String? = null,
    val label: String? = null,
) : Widget

/** Radio button (use inside RadioGroup). */
@Serializable
@SerialName("RadioButton")
class RadioButton(
    val value: String,
    val label: String,
    val color: String? = null,
) : Widget

/** Group of radio buttons — only one can be selected. */
@Serializable
@SerialName("RadioGroup")
class RadioGroup(
    val selected: String = "",
    val action: String,
    val options: List<RadioButton>,
) : Widget

/** A continuous slider for selecting a value from a range. */
@Serializable
@SerialName("Slider")
class Slider(
    val value: Float = 0f,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float? = null,
    val action: String,
    val trackColor: String? = null,
    val thumbColor: String? = null,
    val enabled: Boolean = true,
    val label: String? = null,
) : Widget

/** A determinate or indeterminate progress indicator (linear). */
@Serializable
@SerialName("LinearProgress")
class LinearProgress(
    val progress: Float? = null,
    val color: String? = null,
    val trackColor: String? = null,
) : Widget

/** A determinate or indeterminate circular progress indicator. */
@Serializable
@SerialName("CircularProgress")
class CircularProgress(
    val progress: Float? = null,
    val color: String? = null,
    val size: Int = 40,
    val strokeWidth: Int = 4,
) : Widget

/** A search bar with text input and optional actions. */
@Serializable
@SerialName("SearchBar")
class SearchBar(
    val value: String = "",
    val placeholder: String = "Search...",
    val action: String,
    val style: InputStyle? = null,
) : Widget

/** A dropdown menu for selecting one option from a list. */
@Serializable
@SerialName("DropdownMenu")
class DropdownMenu(
    val selected: String = "",
    val options: List<String>,
    val action: String,
    val label: String? = null,
    val style: ContainerStyle? = null,
) : Widget

// ─── Feedback / Overlay Widgets ───────────────────────────────────────────────

/** A modal dialog. */
@Serializable
@SerialName("Dialog")
class Dialog(
    val visible: Boolean = false,
    val title: String? = null,
    val content: Widget,
    val confirmText: String = "OK",
    val dismissText: String? = null,
    val confirmAction: String,
    val dismissAction: String? = null,
) : Widget

/** A bottom sheet that slides up from the bottom. */
@Serializable
@SerialName("BottomSheet")
class BottomSheet(
    val visible: Boolean = false,
    val content: Widget,
    val dismissAction: String? = null,
    val style: ContainerStyle? = null,
) : Widget

/** A short message shown briefly at the bottom of the screen. */
@Serializable
@SerialName("Snackbar")
class Snackbar(
    val message: String,
    val visible: Boolean = false,
    val actionText: String? = null,
    val action: String? = null,
    val duration: Int = 3000,
) : Widget

/** A floating action button. */
@Serializable
@SerialName("FloatingActionButton")
class FloatingActionButton(
    val icon: String = "add",
    val action: String,
    val background: String? = null,
    val iconColor: String? = null,
    val size: String = "normal",
) : Widget

/** A small notification indicator. */
@Serializable
@SerialName("Badge")
class Badge(
    val count: Int = 0,
    val color: String? = null,
    val child: Widget? = null,
) : Widget

/** A compact interactive element for filters or choices. */
@Serializable
@SerialName("Chip")
class Chip(
    val label: String,
    val selected: Boolean = false,
    val action: String? = null,
    val icon: String? = null,
    val color: String? = null,
) : Widget

/** A tooltip displayed on long-press or hover. */
@Serializable
@SerialName("Tooltip")
class Tooltip(
    val message: String,
    val child: Widget,
) : Widget

// ─── Scaffold / App Structure ─────────────────────────────────────────────────

/** Top-level scaffold with optional TopBar, BottomBar, FAB, Drawer. */
@Serializable
@SerialName("Scaffold")
class Scaffold(
    val topBar: Widget? = null,
    val bottomBar: Widget? = null,
    val floatingActionButton: Widget? = null,
    val drawer: Widget? = null,
    val body: Widget,
    val style: ContainerStyle? = null,
) : Widget

/** A top app bar / toolbar. */
@Serializable
@SerialName("TopAppBar")
class TopAppBar(
    val title: String,
    val subtitle: String? = null,
    val navigationIcon: String? = null,
    val navigationAction: String? = null,
    val actions: List<Widget>? = null,
    val background: String? = null,
    val contentColor: String? = null,
    val elevation: Int? = null,
) : Widget

/** A bottom app bar / navigation bar. */
@Serializable
@SerialName("BottomAppBar")
class BottomAppBar(
    val children: List<Widget>,
    val background: String? = null,
    val contentColor: String? = null,
) : Widget

/** Horizontal row of top-level tabs. */
@Serializable
@SerialName("TabRow")
class TabRow(
    val tabs: List<TabItem>,
    val selected: Int = 0,
    val action: String,
    val background: String? = null,
    val contentColor: String? = null,
) : Widget

/** Individual tab in a TabRow. */
@Serializable
@SerialName("TabItem")
class TabItem(
    val title: String,
    val icon: String? = null,
)

/** A swipeable pager (ViewPager). */
@Serializable
@SerialName("Pager")
class Pager(
    val pages: List<Widget>,
    val currentPage: Int = 0,
    val action: String? = null,
    val style: ContainerStyle? = null,
) : Widget

// ─── Logic Widgets ────────────────────────────────────────────────────────────

/** Dynamically renders a template for each item in a state-backed list. */
@Serializable
@SerialName("ForEach")
class ForEach(
    val itemsKey: String,
    val template: Widget
) : Widget

/** Conditionally renders UI based on a state-backed boolean. */
@Serializable
@SerialName("Condition")
class Condition(
    val key: String,
    val then: Widget,
    val otherwise: Widget? = null
) : Widget

// ─── Navigation ───────────────────────────────────────────────────────────────

/** Multi-screen navigator with route-based switching. */
@Serializable
@SerialName("Navigator")
class Navigator(
    val initialRoute: String = "home",
    val screens: Map<String, Widget>,
) : Widget

/** Named screen within a Navigator. */
@Serializable
@SerialName("Screen")
class Screen(
    val route: String,
    val content: Widget,
) : Widget

/** Bottom navigation bar with multiple tabs. */
@Serializable
@SerialName("BottomTabs")
class BottomTabs(
    val tabs: List<Tab>,
    val style: ContainerStyle? = null
) : Widget

/** Individual tab for BottomTabs. */
@Serializable
@SerialName("Tab")
class Tab(
    val title: String,
    val icon: String? = null,
    val route: String,
    val content: Widget
) : Widget

/** A side navigation drawer. */
@Serializable
@SerialName("Drawer")
class Drawer(
    val header: Widget? = null,
    val items: List<DrawerItem>,
    val style: ContainerStyle? = null,
) : Widget

/** An item inside a Drawer. */
@Serializable
@SerialName("DrawerItem")
class DrawerItem(
    val title: String,
    val icon: String? = null,
    val route: String? = null,
    val action: String? = null,
) : Widget

// ─── Media Widgets ────────────────────────────────────────────────────────────

/** Native video player. */
@Serializable
@SerialName("VideoPlayer")
class VideoPlayer(
    val src: String,
    val autoplay: Boolean = false,
    val controls: Boolean = true,
    val loop: Boolean = false,
    val muted: Boolean = false,
    val poster: String? = null,
    val style: ContainerStyle? = null,
) : Widget

/** Native audio player. */
@Serializable
@SerialName("AudioPlayer")
class AudioPlayer(
    val src: String,
    val autoplay: Boolean = false,
    val controls: Boolean = true,
    val loop: Boolean = false,
    val style: ContainerStyle? = null,
) : Widget

// ─── Gesture Wrappers ─────────────────────────────────────────────────────────

/** Captures Pan and Tap gestures. */
@Serializable
@SerialName("GestureDetector")
class GestureDetector(
    val onTap: String? = null,
    val onPan: String? = null,
    val child: Widget,
) : Widget

/** Makes a child widget tappable. */
@Serializable
@SerialName("Clickable")
class Clickable(
    val action: String,
    val child: Widget,
    val ripple: Boolean = true,
) : Widget

/** Detects long press on a child widget. */
@Serializable
@SerialName("LongPressable")
class LongPressable(
    val action: String,
    val child: Widget,
    val duration: Int = 500,
) : Widget

/** Enables swipe-to-reveal actions on a list item. */
@Serializable
@SerialName("SwipeToReveal")
class SwipeToReveal(
    val child: Widget,
    val startAction: String? = null,
    val endAction: String? = null,
    val startContent: Widget? = null,
    val endContent: Widget? = null,
) : Widget

/** Pull-to-refresh wrapper. */
@Serializable
@SerialName("PullToRefresh")
class PullToRefresh(
    val action: String,
    val child: Widget,
    val refreshing: Boolean = false,
) : Widget

// ─── Loading / Placeholder ────────────────────────────────────────────────────

/** A shimmer loading placeholder. */
@Serializable
@SerialName("Placeholder")
class Placeholder(
    val width: String? = null,
    val height: String? = null,
    val cornerRadius: Int = 4,
) : Widget

// ─── WebView ──────────────────────────────────────────────────────────────────

/** Renders a web page inside the app. */
@Serializable
@SerialName("WebView")
class WebView(
    val url: String,
    val style: ContainerStyle? = null,
) : Widget
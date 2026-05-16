package dev.feather.host

import org.json.JSONObject

// MARK: - Style Data

data class FeatherStyle(
    val color: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val textDecoration: String? = null,
    val fontFamily: String? = null,
    val background: String? = null,
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val paddingTop: Int? = null,
    val paddingBottom: Int? = null,
    val paddingStart: Int? = null,
    val paddingEnd: Int? = null,
    val margin: Int? = null,
    val cornerRadius: Int? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val width: String? = null,
    val height: String? = null,
    val minWidth: String? = null,
    val minHeight: String? = null,
    val maxWidth: String? = null,
    val maxHeight: String? = null,
    val alignment: String? = null,
    val spacing: Int? = null,
    val opacity: Float? = null,
    val textColor: String? = null,
    val placeholderColor: String? = null,
    val contentMode: String? = null,
    val shadow: String? = null,
    val elevation: Int? = null,
    val maxLines: Int? = null,
    val flex: Int? = null,
    val fillMaxWidth: Boolean? = null,
    val fillMaxHeight: Boolean? = null,
    val aspectRatio: Float? = null,
    val gradient: String? = null,
    val clip: Boolean? = null,
    val keyboardType: String? = null,
    val maxLength: Int? = null,
    val multiline: Boolean? = null,
    val secure: Boolean? = null,
    val direction: String? = null,
    val position: String? = null,
    val top: Float? = null,
    val bottom: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val zIndex: Float? = null,
    val animations: List<FeatherAnimation>? = null,
)

// MARK: - Animation Data

data class FeatherAnimation(
    val property: String, // "opacity", "scale", "rotation", "translationX", "translationY"
    val duration: Int = 300,
    val delay: Int = 0,
    val easing: String = "linear", // "linear", "easeIn", "easeOut", "easeInOut", "bounce"
    val iterations: Int = 1, // -1 for infinite
    val keyframes: List<FeatherKeyframe>,
)

data class FeatherKeyframe(
    val offset: Float, // 0.0 to 1.0
    val value: Float,
)

// MARK: - Node Tree

sealed interface FeatherNode {
    // Basic
    data class TextNode(val text: String, val style: FeatherStyle? = null) : FeatherNode
    data class ButtonNode(val text: String, val action: String, val enabled: Boolean = true, val style: FeatherStyle? = null) : FeatherNode
    data class TextInputNode(val value: String, val placeholder: String, val action: String, val label: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class ImageNode(val src: String, val alt: String, val style: FeatherStyle? = null) : FeatherNode
    data class IconNode(val name: String, val size: Int = 24, val color: String? = null, val action: String? = null) : FeatherNode
    data class IconButtonNode(val icon: String, val action: String, val size: Int = 24, val color: String? = null, val background: String? = null) : FeatherNode
    data class SpacerNode(val flex: Int = 1) : FeatherNode
    data class DividerNode(val color: String? = null, val thickness: Int = 1, val indent: Int = 0) : FeatherNode

    // Layouts
    data class ColumnNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class RowNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class BoxNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyColumnNode(val items: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyRowNode(val items: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyGridNode(val items: List<FeatherNode>, val columns: Int = 2, val style: FeatherStyle? = null) : FeatherNode
    data class ScrollViewNode(val children: List<FeatherNode>, val direction: String = "vertical", val style: FeatherStyle? = null) : FeatherNode
    data class CardNode(val children: List<FeatherNode>, val action: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class SurfaceNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class AspectRatioNode(val ratio: Float, val child: FeatherNode) : FeatherNode

    // Interactive
    data class SwitchNode(val checked: Boolean, val action: String, val enabled: Boolean = true, val label: String? = null, val trackColor: String? = null, val thumbColor: String? = null) : FeatherNode
    data class CheckboxNode(val checked: Boolean, val action: String, val enabled: Boolean = true, val label: String? = null, val color: String? = null) : FeatherNode
    data class RadioGroupNode(val selected: String, val action: String, val options: List<RadioOptionData>) : FeatherNode
    data class SliderNode(val value: Float, val min: Float, val max: Float, val step: Float?, val action: String, val label: String? = null, val trackColor: String? = null, val thumbColor: String? = null) : FeatherNode
    data class SearchBarNode(val value: String, val placeholder: String, val action: String, val style: FeatherStyle? = null) : FeatherNode
    data class DropdownMenuNode(val selected: String, val options: List<String>, val action: String, val label: String? = null, val style: FeatherStyle? = null) : FeatherNode

    // Progress
    data class LinearProgressNode(val progress: Float?, val color: String?, val trackColor: String?) : FeatherNode
    data class CircularProgressNode(val progress: Float?, val color: String?, val size: Int = 40, val strokeWidth: Int = 4) : FeatherNode

    // Feedback
    data class DialogNode(val visible: Boolean, val title: String?, val content: FeatherNode, val confirmText: String, val dismissText: String?, val confirmAction: String, val dismissAction: String?) : FeatherNode
    data class BottomSheetNode(val visible: Boolean, val content: FeatherNode, val dismissAction: String?, val style: FeatherStyle? = null) : FeatherNode
    data class SnackbarNode(val message: String, val visible: Boolean, val actionText: String?, val action: String?) : FeatherNode
    data class BadgeNode(val count: Int, val color: String?, val child: FeatherNode?) : FeatherNode
    data class ChipNode(val label: String, val selected: Boolean?, val action: String?, val icon: String?, val color: String?) : FeatherNode
    data class TooltipNode(val message: String, val child: FeatherNode) : FeatherNode
    data class FloatingActionButtonNode(val icon: String, val action: String, val background: String?, val iconColor: String?) : FeatherNode
    data class PlaceholderNode(val width: String?, val height: String?, val cornerRadius: Int?) : FeatherNode

    // Scaffold
    data class ScaffoldNode(val topBar: FeatherNode?, val bottomBar: FeatherNode?, val floatingActionButton: FeatherNode?, val drawer: FeatherNode?, val body: FeatherNode, val style: FeatherStyle? = null) : FeatherNode
    data class TopAppBarNode(val title: String, val subtitle: String?, val navigationIcon: String?, val navigationAction: String?, val actions: List<FeatherNode>?, val background: String?, val contentColor: String?) : FeatherNode
    data class BottomAppBarNode(val children: List<FeatherNode>, val background: String?, val contentColor: String?) : FeatherNode
    data class TabRowNode(val tabs: List<TabItemData>, val selected: Int, val action: String, val background: String?, val contentColor: String?) : FeatherNode
    data class PagerNode(val pages: List<FeatherNode>, val currentPage: Int?, val action: String?, val style: FeatherStyle? = null) : FeatherNode
    data class DrawerNode(val header: FeatherNode?, val items: List<DrawerItemData>, val style: FeatherStyle? = null) : FeatherNode

    // Navigation
    data class NavigatorNode(val initialRoute: String, val screens: Map<String, FeatherNode>) : FeatherNode
    data class BottomTabsNode(val tabs: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class TabNode(val title: String, val route: String, val icon: String?, val content: FeatherNode) : FeatherNode

    // Logic (Pre-resolved by Core, but kept for future dynamic work)
    data class ForEachNode(val itemsKey: String, val template: JSONObject) : FeatherNode
    data class ConditionNode(val key: String, val then: FeatherNode, val otherwise: FeatherNode?) : FeatherNode

    // Media
    data class VideoPlayerNode(val src: String, val autoplay: Boolean = false, val controls: Boolean = true, val loop: Boolean = false, val poster: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class AudioPlayerNode(val src: String, val autoplay: Boolean = false, val controls: Boolean = true, val loop: Boolean = false, val style: FeatherStyle? = null) : FeatherNode

    // Web
    data class WebViewNode(val url: String, val style: FeatherStyle? = null) : FeatherNode

    // Gestures
    data class GestureDetectorNode(val onTap: String?, val onPan: String?, val child: FeatherNode) : FeatherNode
    data class ClickableNode(val action: String, val child: FeatherNode, val ripple: Boolean = true) : FeatherNode
    data class LongPressableNode(val action: String, val child: FeatherNode, val duration: Int = 500) : FeatherNode
    data class PullToRefreshNode(val action: String, val child: FeatherNode, val refreshing: Boolean) : FeatherNode
    data class CustomNode(val module: String, val name: String, val props: JSONObject, val style: FeatherStyle? = null) : FeatherNode
}

data class RadioOptionData(val value: String, val label: String, val color: String?)
data class TabItemData(val title: String, val icon: String?)
data class DrawerItemData(val title: String, val icon: String?, val route: String?, val action: String?)

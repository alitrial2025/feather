package dev.feather.sdk

import kotlinx.serialization.Serializable

/** Style properties for Text widgets. */
@Serializable
data class TextStyle(
    val color: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val textDecoration: String? = null,
    val letterSpacing: Int? = null,
    val lineHeight: Int? = null,
    val maxLines: Int? = null,
    val overflow: String? = null,
    val fontFamily: String? = null,
    val animations: List<FeatherAnimation>? = null,
)

/** Style properties for layout containers. */
@Serializable
data class ContainerStyle(
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val paddingTop: Int? = null,
    val paddingBottom: Int? = null,
    val paddingStart: Int? = null,
    val paddingEnd: Int? = null,
    val margin: Int? = null,
    val background: String? = null,
    val cornerRadius: Int? = null,
    val cornerRadiusTopStart: Int? = null,
    val cornerRadiusTopEnd: Int? = null,
    val cornerRadiusBottomStart: Int? = null,
    val cornerRadiusBottomEnd: Int? = null,
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
    val shadow: String? = null,
    val elevation: Int? = null,
    val clip: Boolean? = null,
    val flex: Int? = null,
    val fillMaxWidth: Boolean? = null,
    val fillMaxHeight: Boolean? = null,
    val aspectRatio: Float? = null,
    val gradient: String? = null,
    val position: String? = null,
    val top: Float? = null,
    val bottom: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val zIndex: Int? = null,
    val animations: List<FeatherAnimation>? = null,
)

/** Style properties for Button widgets. */
@Serializable
data class ButtonStyle(
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
    val enabled: Boolean? = null,
    val animations: List<FeatherAnimation>? = null,
)

/** Style properties for TextInput widgets. */
@Serializable
data class InputStyle(
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
    val animations: List<FeatherAnimation>? = null,
)

/** Style properties for Image widgets. */
@Serializable
data class ImageStyle(
    val cornerRadius: Int? = null,
    val width: String? = null,
    val height: String? = null,
    val contentMode: String? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val opacity: Float? = null,
    val aspectRatio: Float? = null,
    val placeholder: String? = null,
    val animations: List<FeatherAnimation>? = null,
)

@Serializable
data class FeatherAnimation(
    val property: String,
    val duration: Int = 300,
    val delay: Int = 0,
    val easing: String = "linear",
    val iterations: Int = 1,
    val keyframes: List<FeatherKeyframe>,
)

@Serializable
data class FeatherKeyframe(
    val offset: Float,
    val value: Float,
)
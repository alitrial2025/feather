use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

// ─── Project Config ───────────────────────────────────────────────────────────

#[derive(Deserialize, Serialize, Clone)]
pub struct FeatherManifest {
    pub runtime: Option<String>,
    pub entry: String,
    pub assets: Option<Vec<String>>,
    #[serde(rename = "generatedIr")]
    pub generated_ir: Option<String>,
    #[serde(rename = "fallbackIr")]
    pub fallback_ir: Option<String>,
    #[serde(rename = "sdkVersion")]
    pub sdk_version: Option<String>,
    pub modules: Option<HashMap<String, String>>,
    pub dependencies: Option<HashMap<String, String>>,
    #[serde(default)]
    pub metadata: AppMetadataConfig,
    #[serde(default)]
    pub deep_links: Vec<DeepLinkConfig>,
    #[serde(default)]
    pub permissions: Vec<String>,
    #[serde(default)]
    pub platform_overrides: HashMap<String, serde_json::Value>,
}

#[derive(Deserialize, Serialize, Clone, Default)]
pub struct AppMetadataConfig {
    pub app_id: Option<String>,
    pub version_name: Option<String>,
    pub version_code: Option<u32>,
    pub display_name: Option<String>,
}

#[derive(Deserialize, Serialize, Clone)]
pub struct DeepLinkConfig {
    pub scheme: String,
    pub host: Option<String>,
    pub path_prefix: Option<String>,
}

#[derive(Deserialize, Serialize, Clone)]
pub struct FeatherLock {
    #[serde(rename = "sdkVersion")]
    pub sdk_version: String,
    pub resolved: HashMap<String, ResolvedModule>,
}

#[derive(Deserialize, Serialize, Clone)]
pub struct ResolvedModule {
    pub version: String,
    pub android: String,
    pub ios: String,
}

// ─── Render Protocol ──────────────────────────────────────────────────────────

#[derive(Serialize)]
pub struct RenderMessage {
    #[serde(rename = "type")]
    pub message_type: &'static str,
    pub protocol_version: u8,
    pub root: WidgetNode,
    pub diagnostics: Vec<Diagnostic>,
    pub state: HashMap<String, String>,
}

#[derive(Serialize)]
pub struct Diagnostic {
    pub level: &'static str,
    pub message: String,
}

// ─── Style ────────────────────────────────────────────────────────────────────

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct WidgetStyle {

    #[serde(skip_serializing_if = "Option::is_none")]
    pub color: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub font_size: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub font_weight: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub font_style: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub font_family: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub text_align: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub text_decoration: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub letter_spacing: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub line_height: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub overflow: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub background: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_horizontal: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_vertical: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_top: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_bottom: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_start: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding_end: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub margin: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub corner_radius: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub corner_radius_top_start: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub corner_radius_top_end: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub corner_radius_bottom_start: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub corner_radius_bottom_end: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub border_color: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub border_width: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub width: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub height: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub min_width: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub min_height: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_width: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_height: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub alignment: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub spacing: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub opacity: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub text_color: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub placeholder_color: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub content_mode: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub shadow: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub elevation: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_lines: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub flex: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub position: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub top: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bottom: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub left: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub right: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub z_index: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub fill_max_width: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub fill_max_height: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub aspect_ratio: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub gradient: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub clip: Option<bool>,
    // Input-specific
    #[serde(skip_serializing_if = "Option::is_none")]
    pub keyboard_type: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_length: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub multiline: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub secure: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub direction: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub animations: Option<Vec<FeatherAnimation>>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct FeatherAnimation {
    pub property: String,
    pub duration: u32,
    pub delay: u32,
    pub easing: String,
    pub iterations: i32,
    pub keyframes: Vec<FeatherKeyframe>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct FeatherKeyframe {
    pub offset: f32,
    pub value: f32,
}

impl WidgetStyle {
    pub fn is_empty(&self) -> bool {
        self.color.is_none()
            && self.font_size.is_none()
            && self.font_weight.is_none()
            && self.font_style.is_none()
            && self.font_family.is_none()
            && self.text_align.is_none()
            && self.text_decoration.is_none()
            && self.letter_spacing.is_none()
            && self.line_height.is_none()
            && self.overflow.is_none()
            && self.background.is_none()
            && self.padding.is_none()
            && self.padding_horizontal.is_none()
            && self.padding_vertical.is_none()
            && self.padding_top.is_none()
            && self.padding_bottom.is_none()
            && self.padding_start.is_none()
            && self.padding_end.is_none()
            && self.margin.is_none()
            && self.corner_radius.is_none()
            && self.corner_radius_top_start.is_none()
            && self.corner_radius_top_end.is_none()
            && self.corner_radius_bottom_start.is_none()
            && self.corner_radius_bottom_end.is_none()
            && self.border_color.is_none()
            && self.border_width.is_none()
            && self.width.is_none()
            && self.height.is_none()
            && self.min_width.is_none()
            && self.min_height.is_none()
            && self.max_width.is_none()
            && self.max_height.is_none()
            && self.alignment.is_none()
            && self.spacing.is_none()
            && self.opacity.is_none()
            && self.text_color.is_none()
            && self.placeholder_color.is_none()
            && self.content_mode.is_none()
            && self.shadow.is_none()
            && self.elevation.is_none()
            && self.max_lines.is_none()
            && self.flex.is_none()
            && self.fill_max_width.is_none()
            && self.fill_max_height.is_none()
            && self.aspect_ratio.is_none()
            && self.gradient.is_none()
            && self.clip.is_none()
            && self.keyboard_type.is_none()
            && self.max_length.is_none()
            && self.multiline.is_none()
            && self.secure.is_none()
            && self.direction.is_none()
            && self.animations.is_none()
    }
}

pub fn skip_empty_style(style: &Option<WidgetStyle>) -> bool {
    style.as_ref().map_or(true, |s| s.is_empty())
}

// ─── Widget Node Tree ─────────────────────────────────────────────────────────

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(tag = "type")]
#[allow(dead_code)]
pub enum WidgetNode {
    // ── Basic ──
    Text {
        text: String,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    StateText {
        key: String,
        #[serde(default)]
        prefix: String,
        #[serde(default)]
        fallback: String,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Button {
        text: String,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        enabled: Option<bool>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    TextInput {
        value: String,
        placeholder: String,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Image {
        src: String,
        alt: String,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Icon {
        name: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        size: Option<u16>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        action: Option<String>,
    },
    IconButton {
        icon: String,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        size: Option<u16>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        background: Option<String>,
    },
    Spacer {
        flex: u8,
    },
    Divider {
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        thickness: Option<u8>,
        #[serde(skip_serializing_if = "Option::is_none")]
        indent: Option<u16>,
    },

    // ── Layouts ──
    Column {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Row {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Box {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    LazyColumn {
        items: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    LazyRow {
        items: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    LazyGrid {
        items: Vec<WidgetNode>,
        columns: u8,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    ScrollView {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        direction: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Card {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        action: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Surface {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    AspectRatio {
        ratio: f32,
        child: std::boxed::Box<WidgetNode>,
    },

    // ── Interactive ──
    Switch {
        checked: bool,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        enabled: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        track_color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        thumb_color: Option<String>,
    },
    Checkbox {
        checked: bool,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        enabled: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
    },
    RadioGroup {
        selected: String,
        action: String,
        options: Vec<RadioOption>,
    },
    Slider {
        value: f32,
        min: f32,
        max: f32,
        #[serde(skip_serializing_if = "Option::is_none")]
        step: Option<f32>,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        track_color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        thumb_color: Option<String>,
    },
    SearchBar {
        value: String,
        placeholder: String,
        action: String,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    DropdownMenu {
        selected: String,
        options: Vec<String>,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        label: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },

    // ── Progress ──
    LinearProgress {
        #[serde(skip_serializing_if = "Option::is_none")]
        progress: Option<f32>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        track_color: Option<String>,
    },
    CircularProgress {
        #[serde(skip_serializing_if = "Option::is_none")]
        progress: Option<f32>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        size: Option<u16>,
        #[serde(skip_serializing_if = "Option::is_none")]
        stroke_width: Option<u16>,
    },

    // ── Feedback / Overlay ──
    Dialog {
        visible: bool,
        #[serde(skip_serializing_if = "Option::is_none")]
        title: Option<String>,
        content: std::boxed::Box<WidgetNode>,
        confirm_text: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        dismiss_text: Option<String>,
        confirm_action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        dismiss_action: Option<String>,
    },
    BottomSheet {
        visible: bool,
        content: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        dismiss_action: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Snackbar {
        message: String,
        visible: bool,
        #[serde(skip_serializing_if = "Option::is_none")]
        action_text: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        action: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        duration: Option<u32>,
    },
    Badge {
        count: u32,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        child: Option<std::boxed::Box<WidgetNode>>,
    },
    Chip {
        label: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        selected: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        action: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        icon: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        color: Option<String>,
    },
    Tooltip {
        message: String,
        child: std::boxed::Box<WidgetNode>,
    },
    FloatingActionButton {
        icon: String,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        background: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        icon_color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        size: Option<String>,
    },
    Placeholder {
        #[serde(skip_serializing_if = "Option::is_none")]
        width: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        height: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        corner_radius: Option<u16>,
    },

    // ── Scaffold / Structure ──
    Scaffold {
        #[serde(skip_serializing_if = "Option::is_none")]
        top_bar: Option<std::boxed::Box<WidgetNode>>,
        #[serde(skip_serializing_if = "Option::is_none")]
        bottom_bar: Option<std::boxed::Box<WidgetNode>>,
        #[serde(skip_serializing_if = "Option::is_none")]
        floating_action_button: Option<std::boxed::Box<WidgetNode>>,
        #[serde(skip_serializing_if = "Option::is_none")]
        drawer: Option<std::boxed::Box<WidgetNode>>,
        body: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    TopAppBar {
        title: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        subtitle: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        navigation_icon: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        navigation_action: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        actions: Option<Vec<WidgetNode>>,
        #[serde(skip_serializing_if = "Option::is_none")]
        background: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        content_color: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        elevation: Option<u16>,
    },
    BottomAppBar {
        children: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        background: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        content_color: Option<String>,
    },
    TabRow {
        tabs: Vec<TabItemData>,
        selected: u8,
        action: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        background: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        content_color: Option<String>,
    },
    Pager {
        pages: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        current_page: Option<u8>,
        #[serde(skip_serializing_if = "Option::is_none")]
        action: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Drawer {
        #[serde(skip_serializing_if = "Option::is_none")]
        header: Option<std::boxed::Box<WidgetNode>>,
        items: Vec<DrawerItemData>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },

    // ── Navigation ──
    Navigator {
        #[serde(rename = "initialRoute")]
        initial_route: String,
        screens: HashMap<String, WidgetNode>,
    },
    BottomTabs {
        tabs: Vec<WidgetNode>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    Tab {
        title: String,
        route: String,
        icon: Option<String>,
        content: std::boxed::Box<WidgetNode>,
    },

    // ── Logic ──
    Screen {
        route: String,
        content: std::boxed::Box<WidgetNode>,
    },
    ForEach {
        #[serde(rename = "itemsKey")]
        items_key: String,
        template: std::boxed::Box<WidgetNode>,
    },
    Condition {
        key: String,
        then: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        otherwise: Option<std::boxed::Box<WidgetNode>>,
    },

    // ── Gesture Wrappers ──
    GestureDetector {
        #[serde(skip_serializing_if = "Option::is_none")]
        on_tap: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        on_pan: Option<String>,
        child: std::boxed::Box<WidgetNode>,
    },
    Clickable {
        action: String,
        child: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        ripple: Option<bool>,
    },
    LongPressable {
        action: String,
        child: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        duration: Option<u16>,
    },
    SwipeToReveal {
        child: std::boxed::Box<WidgetNode>,
        #[serde(skip_serializing_if = "Option::is_none")]
        start_action: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        end_action: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        start_content: Option<std::boxed::Box<WidgetNode>>,
        #[serde(skip_serializing_if = "Option::is_none")]
        end_content: Option<std::boxed::Box<WidgetNode>>,
    },
    PullToRefresh {
        action: String,
        child: std::boxed::Box<WidgetNode>,
        refreshing: bool,
    },

    // ── Media ──
    VideoPlayer {
        src: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        autoplay: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        controls: Option<bool>,
        #[serde(rename = "loop", skip_serializing_if = "Option::is_none")]
        loop_playback: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        muted: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        poster: Option<String>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
    AudioPlayer {
        src: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        autoplay: Option<bool>,
        #[serde(skip_serializing_if = "Option::is_none")]
        controls: Option<bool>,
        #[serde(rename = "loop", skip_serializing_if = "Option::is_none")]
        loop_playback: Option<bool>,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },

    // ── Web ──
    WebView {
        url: String,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },

    // ── Custom ──
    CustomNode {
        module: String,
        name: String,
        props: Value,
        #[serde(skip_serializing_if = "skip_empty_style")]
        style: Option<WidgetStyle>,
    },
}

// ─── Supporting Types ─────────────────────────────────────────────────────────

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct RadioOption {
    pub value: String,
    pub label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub color: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct TabItemData {
    pub title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub icon: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DrawerItemData {
    pub title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub icon: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub route: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub action: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[allow(dead_code)]
pub struct SnackbarData {
    pub message: String,
    pub visible: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub action_text: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub action: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration: Option<u32>,
}

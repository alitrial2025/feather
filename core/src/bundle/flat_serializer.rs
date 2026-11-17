use serde_json::Value;

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
#[repr(i8)]
pub enum WidgetType {
    Text = 0,
    StateText = 1,
    Button = 2,
    TextInput = 3,
    Image = 4,
    Icon = 5,
    IconButton = 6,
    Spacer = 7,
    Divider = 8,
    Column = 9,
    Row = 10,
    Box = 11,
    LazyColumn = 12,
    LazyRow = 13,
    LazyGrid = 14,
    ScrollView = 15,
    Card = 16,
    Surface = 17,
    AspectRatio = 18,
    Switch = 19,
    Checkbox = 20,
    RadioGroup = 21,
    Slider = 22,
    SearchBar = 23,
    DropdownMenu = 24,
    LinearProgress = 25,
    CircularProgress = 26,
    Dialog = 27,
    BottomSheet = 28,
    Snackbar = 29,
    Badge = 30,
    Chip = 31,
    Tooltip = 32,
    FloatingActionButton = 33,
    Placeholder = 34,
    Scaffold = 35,
    TopAppBar = 36,
    BottomAppBar = 37,
    TabRow = 38,
    Pager = 39,
    Drawer = 40,
    Navigator = 41,
    BottomTabs = 42,
    Tab = 43,
    ForEach = 44,
    Condition = 45,
    VideoPlayer = 46,
    AudioPlayer = 47,
    WebView = 48,
    GestureDetector = 49,
    Clickable = 50,
    LongPressable = 51,
    PullToRefresh = 52,
    Custom = 53
}

impl WidgetType {
    pub fn from_str(s: &str) -> Self {
        match s {
            "Text" => WidgetType::Text,
            "StateText" => WidgetType::StateText,
            "Button" => WidgetType::Button,
            "TextInput" => WidgetType::TextInput,
            "Image" => WidgetType::Image,
            "Icon" => WidgetType::Icon,
            "IconButton" => WidgetType::IconButton,
            "Spacer" => WidgetType::Spacer,
            "Divider" => WidgetType::Divider,
            "Column" => WidgetType::Column,
            "Row" => WidgetType::Row,
            "Box" => WidgetType::Box,
            "LazyColumn" => WidgetType::LazyColumn,
            "LazyRow" => WidgetType::LazyRow,
            "LazyGrid" => WidgetType::LazyGrid,
            "ScrollView" => WidgetType::ScrollView,
            "Card" => WidgetType::Card,
            "Surface" => WidgetType::Surface,
            "AspectRatio" => WidgetType::AspectRatio,
            "Switch" => WidgetType::Switch,
            "Checkbox" => WidgetType::Checkbox,
            "RadioGroup" => WidgetType::RadioGroup,
            "Slider" => WidgetType::Slider,
            "SearchBar" => WidgetType::SearchBar,
            "DropdownMenu" => WidgetType::DropdownMenu,
            "LinearProgress" => WidgetType::LinearProgress,
            "CircularProgress" => WidgetType::CircularProgress,
            "Dialog" => WidgetType::Dialog,
            "BottomSheet" => WidgetType::BottomSheet,
            "Snackbar" => WidgetType::Snackbar,
            "Badge" => WidgetType::Badge,
            "Chip" => WidgetType::Chip,
            "Tooltip" => WidgetType::Tooltip,
            "FloatingActionButton" => WidgetType::FloatingActionButton,
            "Placeholder" => WidgetType::Placeholder,
            "Scaffold" => WidgetType::Scaffold,
            "TopAppBar" => WidgetType::TopAppBar,
            "BottomAppBar" => WidgetType::BottomAppBar,
            "TabRow" => WidgetType::TabRow,
            "Pager" => WidgetType::Pager,
            "Drawer" => WidgetType::Drawer,
            "Navigator" => WidgetType::Navigator,
            "BottomTabs" => WidgetType::BottomTabs,
            "Tab" => WidgetType::Tab,
            "ForEach" => WidgetType::ForEach,
            "Condition" => WidgetType::Condition,
            "VideoPlayer" => WidgetType::VideoPlayer,
            "AudioPlayer" => WidgetType::AudioPlayer,
            "WebView" => WidgetType::WebView,
            "GestureDetector" => WidgetType::GestureDetector,
            "Clickable" => WidgetType::Clickable,
            "LongPressable" => WidgetType::LongPressable,
            "PullToRefresh" => WidgetType::PullToRefresh,
            "Custom" => WidgetType::Custom,
            _ => WidgetType::Text,
        }
    }
}

pub fn build_style<'a>(
    builder: &mut flatbuffers::FlatBufferBuilder<'a>,
    style: &Value
) -> Option<flatbuffers::WIPOffset<flatbuffers::TableFinishedWIPOffset>> {
    if !style.is_object() {
        return None;
    }

    let color_offset = style.get("color").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let font_weight_offset = style.get("fontWeight").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let font_style_offset = style.get("fontStyle").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let text_align_offset = style.get("textAlign").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let text_decoration_offset = style.get("textDecoration").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let font_family_offset = style.get("fontFamily").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let background_offset = style.get("background").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let border_color_offset = style.get("borderColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let width_offset = style.get("width").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let height_offset = style.get("height").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let min_width_offset = style.get("minWidth").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let min_height_offset = style.get("minHeight").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let max_width_offset = style.get("maxWidth").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let max_height_offset = style.get("maxHeight").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let alignment_offset = style.get("alignment").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let text_color_offset = style.get("textColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let placeholder_color_offset = style.get("placeholderColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let content_mode_offset = style.get("contentMode").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let shadow_offset = style.get("shadow").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let gradient_offset = style.get("gradient").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let keyboard_type_offset = style.get("keyboardType").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let direction_offset = style.get("direction").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let position_offset = style.get("position").and_then(|v| v.as_str()).map(|s| builder.create_string(s));

    let table_start = builder.start_table();

    if let Some(o) = color_offset { builder.push_slot_always(4, o); }

    let font_size = style.get("fontSize").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(6, font_size, 0);

    if let Some(o) = font_weight_offset { builder.push_slot_always(8, o); }
    if let Some(o) = font_style_offset { builder.push_slot_always(10, o); }
    if let Some(o) = text_align_offset { builder.push_slot_always(12, o); }
    if let Some(o) = text_decoration_offset { builder.push_slot_always(14, o); }
    if let Some(o) = font_family_offset { builder.push_slot_always(16, o); }
    if let Some(o) = background_offset { builder.push_slot_always(18, o); }

    let padding = style.get("padding").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(20, padding, 0);

    let padding_horizontal = style.get("paddingHorizontal").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(22, padding_horizontal, 0);

    let padding_vertical = style.get("paddingVertical").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(24, padding_vertical, 0);

    let padding_top = style.get("paddingTop").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(26, padding_top, 0);

    let padding_bottom = style.get("paddingBottom").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(28, padding_bottom, 0);

    let padding_start = style.get("paddingStart").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(30, padding_start, 0);

    let padding_end = style.get("paddingEnd").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(32, padding_end, 0);

    let margin = style.get("margin").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(34, margin, 0);

    let corner_radius = style.get("cornerRadius").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(36, corner_radius, 0);

    if let Some(o) = border_color_offset { builder.push_slot_always(38, o); }

    let border_width = style.get("borderWidth").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(40, border_width, 0);

    if let Some(o) = width_offset { builder.push_slot_always(42, o); }
    if let Some(o) = height_offset { builder.push_slot_always(44, o); }
    if let Some(o) = min_width_offset { builder.push_slot_always(46, o); }
    if let Some(o) = min_height_offset { builder.push_slot_always(48, o); }
    if let Some(o) = max_width_offset { builder.push_slot_always(50, o); }
    if let Some(o) = max_height_offset { builder.push_slot_always(52, o); }
    if let Some(o) = alignment_offset { builder.push_slot_always(54, o); }

    let spacing = style.get("spacing").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(56, spacing, 0);

    let opacity = style.get("opacity").and_then(|v| v.as_f64()).unwrap_or(1.0) as f32;
    builder.push_slot::<f32>(58, opacity, 1.0f32);

    if let Some(o) = text_color_offset { builder.push_slot_always(60, o); }
    if let Some(o) = placeholder_color_offset { builder.push_slot_always(62, o); }
    if let Some(o) = content_mode_offset { builder.push_slot_always(64, o); }
    if let Some(o) = shadow_offset { builder.push_slot_always(66, o); }

    let elevation = style.get("elevation").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(68, elevation, 0);

    let max_lines = style.get("maxLines").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(70, max_lines, 0);

    let flex = style.get("flex").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(72, flex, 0);

    let fill_max_width = style.get("fillMaxWidth").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(74, fill_max_width, false);

    let fill_max_height = style.get("fillMaxHeight").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(76, fill_max_height, false);

    let aspect_ratio = style.get("aspectRatio").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(78, aspect_ratio, 0.0f32);

    if let Some(o) = gradient_offset { builder.push_slot_always(80, o); }

    let clip = style.get("clip").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(82, clip, false);

    if let Some(o) = keyboard_type_offset { builder.push_slot_always(84, o); }

    let max_length = style.get("maxLength").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(86, max_length, 0);

    let multiline = style.get("multiline").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(88, multiline, false);

    let secure = style.get("secure").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(90, secure, false);

    if let Some(o) = direction_offset { builder.push_slot_always(92, o); }
    if let Some(o) = position_offset { builder.push_slot_always(94, o); }

    let top = style.get("top").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(96, top, 0.0f32);

    let bottom = style.get("bottom").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(98, bottom, 0.0f32);

    let left = style.get("left").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(100, left, 0.0f32);

    let right = style.get("right").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(102, right, 0.0f32);

    let z_index = style.get("zIndex").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(104, z_index, 0.0f32);

    let o = builder.end_table(table_start);
    Some(o)
}

pub fn build_node<'a>(
    builder: &mut flatbuffers::FlatBufferBuilder<'a>,
    json: &Value
) -> flatbuffers::WIPOffset<flatbuffers::TableFinishedWIPOffset> {
    let type_str = json.get("type").and_then(|v| v.as_str()).unwrap_or("Text");
    let widget_type = WidgetType::from_str(type_str);

    let id_offset = json.get("id").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let text_offset = json.get("text").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let action_offset = json.get("action").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let value_offset = json.get("value").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let placeholder_offset = json.get("placeholder").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let label_offset = json.get("label").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let src_offset = json.get("src").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let alt_offset = json.get("alt").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let name_offset = json.get("name").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let color_offset = json.get("color").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let background_offset = json.get("background").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let direction_offset = json.get("direction").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let track_color_offset = json.get("trackColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let thumb_color_offset = json.get("thumbColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let selected_offset = json.get("selected").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let title_offset = json.get("title").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let confirm_text_offset = json.get("confirmText").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let dismiss_text_offset = json.get("dismissText").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let confirm_action_offset = json.get("confirmAction").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let dismiss_action_offset = json.get("dismissAction").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let message_offset = json.get("message").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let action_text_offset = json.get("actionText").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let icon_color_offset = json.get("iconColor").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let subtitle_offset = json.get("subtitle").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let navigation_icon_offset = json.get("navigationIcon").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let navigation_action_offset = json.get("navigationAction").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let initial_route_offset = json.get("initialRoute").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let poster_offset = json.get("poster").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let url_offset = json.get("url").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let on_tap_offset = json.get("onTap").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let on_pan_offset = json.get("onPan").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let module_offset = json.get("module").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    
    let key_offset = json.get("key").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let prefix_offset = json.get("prefix").and_then(|v| v.as_str()).map(|s| builder.create_string(s));
    let fallback_offset = json.get("fallback").and_then(|v| v.as_str()).map(|s| builder.create_string(s));

    let props_offset = json.get("props").map(|p| builder.create_string(&p.to_string()));

    let style_offset = json.get("style").and_then(|s| build_style(builder, s));

    let child_offset = json.get("child").map(|c| build_node(builder, c));
    let content_offset = json.get("content").map(|c| build_node(builder, c));
    let top_bar_offset = json.get("topBar").map(|c| build_node(builder, c));
    let bottom_bar_offset = json.get("bottomBar").map(|c| build_node(builder, c));
    let fab_offset = json.get("floatingActionButton").map(|c| build_node(builder, c));
    let drawer_offset = json.get("drawer").map(|c| build_node(builder, c));
    let body_offset = json.get("body").map(|c| build_node(builder, c));
    let header_offset = json.get("header").map(|c| build_node(builder, c));
    let template_offset = json.get("template").map(|c| build_node(builder, c));
    let then_offset = json.get("then").map(|c| build_node(builder, c));
    let otherwise_offset = json.get("otherwise").map(|c| build_node(builder, c));
    let start_content_offset = json.get("startContent").map(|c| build_node(builder, c));
    let end_content_offset = json.get("endContent").map(|c| build_node(builder, c));

    let children_offsets = if let Some(arr) = json.get("children").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for child in arr {
            offsets.push(build_node(builder, child));
        }
        Some(builder.create_vector(&offsets))
    } else if let Some(arr) = json.get("items").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for child in arr {
            offsets.push(build_node(builder, child));
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let pages_offsets = if let Some(arr) = json.get("pages").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for page in arr {
            offsets.push(build_node(builder, page));
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let tabs_offsets = if let Some(arr) = json.get("tabs").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for tab in arr {
            offsets.push(build_node(builder, tab));
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let options_offsets = if let Some(arr) = json.get("options").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for opt in arr {
            if let Some(s) = opt.as_str() {
                offsets.push(builder.create_string(s));
            }
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let radio_options_offset = if let Some(arr) = json.get("radioOptions").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for opt in arr {
            let val = opt.get("value").and_then(|v| v.as_str()).unwrap_or("");
            let lbl = opt.get("label").and_then(|v| v.as_str()).unwrap_or("");
            let col = opt.get("color").and_then(|v| v.as_str());

            let val_off = builder.create_string(val);
            let lbl_off = builder.create_string(lbl);
            let col_off = col.map(|c| builder.create_string(c));

            let table_start = builder.start_table();
            builder.push_slot_always(4, val_off);
            builder.push_slot_always(6, lbl_off);
            if let Some(col_o) = col_off {
                builder.push_slot_always(8, col_o);
            }
            let t = builder.end_table(table_start);
            offsets.push(t);
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let tab_items_offset = if let Some(arr) = json.get("tabItems").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for opt in arr {
            let title = opt.get("title").and_then(|v| v.as_str()).unwrap_or("");
            let icon = opt.get("icon").and_then(|v| v.as_str());

            let title_off = builder.create_string(title);
            let icon_off = icon.map(|c| builder.create_string(c));

            let table_start = builder.start_table();
            builder.push_slot_always(4, title_off);
            if let Some(icon_o) = icon_off {
                builder.push_slot_always(6, icon_o);
            }
            let t = builder.end_table(table_start);
            offsets.push(t);
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let drawer_items_offset = if let Some(arr) = json.get("drawerItems").and_then(|v| v.as_array()) {
        let mut offsets = Vec::new();
        for opt in arr {
            let title = opt.get("title").and_then(|v| v.as_str()).unwrap_or("");
            let icon = opt.get("icon").and_then(|v| v.as_str());
            let route = opt.get("route").and_then(|v| v.as_str());
            let act = opt.get("action").and_then(|v| v.as_str());

            let title_off = builder.create_string(title);
            let icon_off = icon.map(|c| builder.create_string(c));
            let route_off = route.map(|c| builder.create_string(c));
            let act_off = act.map(|c| builder.create_string(c));

            let table_start = builder.start_table();
            builder.push_slot_always(4, title_off);
            if let Some(o) = icon_off { builder.push_slot_always(6, o); }
            if let Some(o) = route_off { builder.push_slot_always(8, o); }
            if let Some(o) = act_off { builder.push_slot_always(10, o); }
            let t = builder.end_table(table_start);
            offsets.push(t);
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let screens_offset = if let Some(map) = json.get("screens").and_then(|v| v.as_object()) {
        let mut offsets = Vec::new();
        for (route, child) in map {
            let route_off = builder.create_string(route);
            let child_off = build_node(builder, child);

            let table_start = builder.start_table();
            builder.push_slot_always(4, route_off);
            builder.push_slot_always(6, child_off);
            let t = builder.end_table(table_start);
            offsets.push(t);
        }
        Some(builder.create_vector(&offsets))
    } else {
        None
    };

    let table_start = builder.start_table();
    
    builder.push_slot::<i8>(4, widget_type as i8, 0);

    if let Some(o) = id_offset { builder.push_slot_always(6, o); }
    if let Some(o) = text_offset { builder.push_slot_always(8, o); }
    if let Some(o) = action_offset { builder.push_slot_always(10, o); }
    
    let enabled = json.get("enabled").and_then(|v| v.as_bool()).unwrap_or(true);
    builder.push_slot::<bool>(12, enabled, true);

    if let Some(o) = value_offset { builder.push_slot_always(14, o); }
    if let Some(o) = placeholder_offset { builder.push_slot_always(16, o); }
    if let Some(o) = label_offset { builder.push_slot_always(18, o); }
    if let Some(o) = src_offset { builder.push_slot_always(20, o); }
    if let Some(o) = alt_offset { builder.push_slot_always(22, o); }
    if let Some(o) = name_offset { builder.push_slot_always(24, o); }

    let size = json.get("size").and_then(|v| v.as_i64()).unwrap_or(24) as i32;
    builder.push_slot::<i32>(26, size, 24);

    if let Some(o) = color_offset { builder.push_slot_always(28, o); }
    if let Some(o) = background_offset { builder.push_slot_always(30, o); }

    let flex = json.get("flex").and_then(|v| v.as_i64()).unwrap_or(1) as i32;
    builder.push_slot::<i32>(32, flex, 1);

    let thickness = json.get("thickness").and_then(|v| v.as_i64()).unwrap_or(1) as i32;
    builder.push_slot::<i32>(34, thickness, 1);

    let indent = json.get("indent").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(36, indent, 0);

    if let Some(o) = direction_offset { builder.push_slot_always(38, o); }

    let ratio = json.get("ratio").and_then(|v| v.as_f64()).unwrap_or(1.0) as f32;
    builder.push_slot::<f32>(40, ratio, 1.0f32);

    let checked = json.get("checked").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(42, checked, false);

    if let Some(o) = track_color_offset { builder.push_slot_always(44, o); }
    if let Some(o) = thumb_color_offset { builder.push_slot_always(46, o); }
    if let Some(o) = selected_offset { builder.push_slot_always(48, o); }

    let min = json.get("min").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(50, min, 0.0f32);

    let max = json.get("max").and_then(|v| v.as_f64()).unwrap_or(100.0) as f32;
    builder.push_slot::<f32>(52, max, 100.0f32);

    let step = json.get("step").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(54, step, 0.0f32);

    let progress = json.get("progress").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
    builder.push_slot::<f32>(56, progress, 0.0f32);

    let stroke_width = json.get("strokeWidth").and_then(|v| v.as_i64()).unwrap_or(4) as i32;
    builder.push_slot::<i32>(58, stroke_width, 4);

    let visible = json.get("visible").and_then(|v| v.as_bool()).unwrap_or(true);
    builder.push_slot::<bool>(60, visible, true);

    if let Some(o) = title_offset { builder.push_slot_always(62, o); }
    if let Some(o) = confirm_text_offset { builder.push_slot_always(64, o); }
    if let Some(o) = dismiss_text_offset { builder.push_slot_always(66, o); }
    if let Some(o) = confirm_action_offset { builder.push_slot_always(68, o); }
    if let Some(o) = dismiss_action_offset { builder.push_slot_always(70, o); }
    if let Some(o) = message_offset { builder.push_slot_always(72, o); }
    if let Some(o) = action_text_offset { builder.push_slot_always(74, o); }

    let count = json.get("count").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(76, count, 0);

    if let Some(o) = icon_color_offset { builder.push_slot_always(78, o); }

    let corner_radius = json.get("cornerRadius").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(80, corner_radius, 0);

    if let Some(o) = subtitle_offset { builder.push_slot_always(82, o); }
    if let Some(o) = navigation_icon_offset { builder.push_slot_always(84, o); }
    if let Some(o) = navigation_action_offset { builder.push_slot_always(86, o); }

    let current_page = json.get("currentPage").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(88, current_page, 0);

    if let Some(o) = initial_route_offset { builder.push_slot_always(90, o); }

    let autoplay = json.get("autoplay").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(92, autoplay, false);

    let controls = json.get("controls").and_then(|v| v.as_bool()).unwrap_or(true);
    builder.push_slot::<bool>(94, controls, true);

    let loop_val = json.get("loop").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(96, loop_val, false);

    if let Some(o) = poster_offset { builder.push_slot_always(98, o); }
    if let Some(o) = url_offset { builder.push_slot_always(100, o); }
    if let Some(o) = on_tap_offset { builder.push_slot_always(102, o); }
    if let Some(o) = on_pan_offset { builder.push_slot_always(104, o); }

    let ripple = json.get("ripple").and_then(|v| v.as_bool()).unwrap_or(true);
    builder.push_slot::<bool>(106, ripple, true);

    let duration = json.get("duration").and_then(|v| v.as_i64()).unwrap_or(0) as i32;
    builder.push_slot::<i32>(108, duration, 0);

    let refreshing = json.get("refreshing").and_then(|v| v.as_bool()).unwrap_or(false);
    builder.push_slot::<bool>(110, refreshing, false);

    if let Some(o) = module_offset { builder.push_slot_always(112, o); }
    if let Some(o) = props_offset { builder.push_slot_always(114, o); }
    if let Some(o) = style_offset { builder.push_slot_always(116, o); }
    if let Some(o) = children_offsets { builder.push_slot_always(118, o); }
    if let Some(o) = options_offsets { builder.push_slot_always(120, o); }
    if let Some(o) = radio_options_offset { builder.push_slot_always(122, o); }
    if let Some(o) = tab_items_offset { builder.push_slot_always(124, o); }
    if let Some(o) = drawer_items_offset { builder.push_slot_always(126, o); }
    if let Some(o) = screens_offset { builder.push_slot_always(128, o); }
    
    if let Some(o) = key_offset { builder.push_slot_always(130, o); }
    if let Some(o) = prefix_offset { builder.push_slot_always(132, o); }
    if let Some(o) = fallback_offset { builder.push_slot_always(134, o); }

    if let Some(o) = child_offset { builder.push_slot_always(136, o); }
    if let Some(o) = content_offset { builder.push_slot_always(138, o); }
    if let Some(o) = top_bar_offset { builder.push_slot_always(140, o); }
    if let Some(o) = bottom_bar_offset { builder.push_slot_always(142, o); }
    if let Some(o) = fab_offset { builder.push_slot_always(144, o); }
    if let Some(o) = drawer_offset { builder.push_slot_always(146, o); }
    if let Some(o) = body_offset { builder.push_slot_always(148, o); }
    if let Some(o) = header_offset { builder.push_slot_always(150, o); }
    if let Some(o) = pages_offsets { builder.push_slot_always(152, o); }
    if let Some(o) = tabs_offsets { builder.push_slot_always(154, o); }
    if let Some(o) = template_offset { builder.push_slot_always(156, o); }
    if let Some(o) = then_offset { builder.push_slot_always(158, o); }
    if let Some(o) = otherwise_offset { builder.push_slot_always(160, o); }
    if let Some(o) = start_content_offset { builder.push_slot_always(162, o); }
    if let Some(o) = end_content_offset { builder.push_slot_always(164, o); }

    builder.end_table(table_start)
}

pub fn json_to_flatbuffer(json_str: &str) -> Result<Vec<u8>, String> {
    let val: Value = serde_json::from_str(json_str)
        .map_err(|e| format!("Invalid JSON: {}", e))?;
    
    let root_val = if val.get("type").and_then(|t| t.as_str()) == Some("render") {
        val.get("root").ok_or("Missing 'root' in render message")?
    } else {
        &val
    };

    let mut builder = flatbuffers::FlatBufferBuilder::new();
    // Emit scalar defaults explicitly: the JSON defaults differ from layout.fbs.
    builder.force_defaults(true);
    let root_offset = build_node(&mut builder, root_val);
    builder.finish(root_offset, None);
    Ok(builder.finished_data().to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;
    use flatbuffers::{ForwardsUOffset, Table, Vector};

    // Read fields using the schema's vtable offsets, independently of the writer.
    // These helpers only inspect buffers produced by this serializer, not untrusted input.
    fn root(bytes: &[u8]) -> Table<'_> {
        let offset = u32::from_le_bytes(bytes[..4].try_into().unwrap()) as usize;
        unsafe { Table::new(bytes, offset) }
    }

    fn text<'a>(table: Table<'a>, slot: u16) -> Option<&'a str> {
        unsafe { table.get::<ForwardsUOffset<&str>>(slot, None) }
    }

    #[test]
    fn schema_offsets_preserve_nested_nodes_and_styles() {
        let bytes = json_to_flatbuffer(r##"{"type":"render","root":{"type":"Column","id":"root","children":[{"type":"Text","text":"Hello","style":{"color":"#123456","fontSize":18}}]}}"##).unwrap();
        let table = root(&bytes);
        assert_eq!(unsafe { table.get::<i8>(4, Some(0)) }, Some(9));
        assert_eq!(text(table, 6), Some("root"));
        let children = unsafe { table.get::<ForwardsUOffset<Vector<ForwardsUOffset<Table>>>>(118, None) }.unwrap();
        assert_eq!(children.len(), 1);
        let child = children.get(0);
        assert_eq!(text(child, 8), Some("Hello"));
        let style = unsafe { child.get::<ForwardsUOffset<Table>>(116, None) }.unwrap();
        assert_eq!(text(style, 4), Some("#123456"));
        assert_eq!(unsafe { style.get::<i32>(6, Some(0)) }, Some(18));
    }

    #[test]
    fn json_defaults_are_explicit_even_when_schema_defaults_differ() {
        let bytes = json_to_flatbuffer(r#"{"type":"Icon"}"#).unwrap();
        let table = root(&bytes);
        assert_eq!(unsafe { table.get::<i32>(26, Some(0)) }, Some(24));
        assert_eq!(unsafe { table.get::<bool>(60, Some(false)) }, Some(true));
        assert_eq!(unsafe { table.get::<bool>(94, Some(false)) }, Some(true));
    }

    #[test]
    fn rejects_invalid_json_and_render_without_root() {
        assert!(json_to_flatbuffer("{").is_err());
        assert!(json_to_flatbuffer(r#"{"type":"render"}"#).is_err());
    }
}

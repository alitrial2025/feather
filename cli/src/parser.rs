use crate::models::{WidgetNode, WidgetStyle};
use feather_core::AppRuntime;
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Clone)]
pub struct ParserContext {
    pub variables: HashMap<String, String>,
    pub functions: HashMap<String, (String, String)>,
    pub data_classes: HashMap<String, Vec<String>>,
}

pub fn extract_widgets(source: &str, runtime: Arc<AppRuntime>, base_path: Option<&std::path::Path>) -> Vec<WidgetNode> {
    let mut widgets = parse_widgets(source, runtime, base_path);
    if widgets.is_empty() {
        widgets.push(WidgetNode::Text {
            text: "No Feather widgets found in sample app.".to_string(),
            style: None,
        });
    }
    widgets
}

fn parse_widgets(source: &str, runtime: Arc<AppRuntime>, base_path: Option<&std::path::Path>) -> Vec<WidgetNode> {
    let source = preprocess_source(source);
    let mut context = ParserContext {
        variables: HashMap::new(),
        functions: HashMap::new(),
        data_classes: HashMap::new(),
    };
    parse_widgets_inner(&source, runtime, &mut context, base_path)
}

fn preprocess_source(source: &str) -> String {
    let mut result = String::with_capacity(source.len());
    let chars: Vec<char> = source.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        if i + 1 < chars.len() && chars[i] == '/' && chars[i + 1] == '/' {
            while i < chars.len() && chars[i] != '\n' { i += 1; }
            continue;
        }
        if i + 1 < chars.len() && chars[i] == '/' && chars[i + 1] == '*' {
            i += 2;
            while i + 1 < chars.len() && !(chars[i] == '*' && chars[i + 1] == '/') { i += 1; }
            i += 2;
            continue;
        }
        result.push(chars[i]);
        i += 1;
    }
    result.lines()
        .filter(|line| {
            let trimmed = line.trim();
            !trimmed.starts_with("import ") && !trimmed.starts_with("package ")
        })
        .collect::<Vec<_>>().join("\n")
}

fn parse_widgets_inner(source: &str, runtime: Arc<AppRuntime>, context: &mut ParserContext, base_path: Option<&std::path::Path>) -> Vec<WidgetNode> {
    let mut widgets = Vec::new();
    let mut search_start = 0;

    let keywords = [
        "val", "var", "fun", "if", "fetch", "@Import", "data",
        "Text", "StateText", "Button", "TextInput", "Image", "Spacer",
        "Icon", "IconButton", "Divider", "Row", "Column", "Box",
        "LazyColumn", "LazyRow", "LazyGrid", "ScrollView", "Card", "Surface", "AspectRatio",
        "Switch", "Checkbox", "RadioGroup", "Slider", "SearchBar", "DropdownMenu",
        "LinearProgress", "CircularProgress", "Dialog", "BottomSheet", "Snackbar", "Badge", "Chip",
        "Tooltip", "FloatingActionButton", "Placeholder", "Scaffold", "TopAppBar", "BottomAppBar",
        "TabRow", "Pager", "Drawer", "Navigator", "Screen", "BottomTabs", "Tab",
        "ForEach", "Condition", "Clickable", "LongPressable", "PullToRefresh", "SwipeToReveal", "GestureDetector",
        "VideoPlayer", "AudioPlayer", "WebView",
    ];

    while search_start < source.len() {
        let mut first_match: Option<(&str, usize, char)> = None;
        for kw in keywords.iter() {
            let patterns = [format!("{kw}("), format!("{kw} {{"), format!("{kw} ")];
            for (p_idx, pattern) in patterns.iter().enumerate() {
                if let Some(pos) = source[search_start..].find(pattern) {
                    let start_idx = search_start + pos;
                    let valid_boundary = start_idx == 0 || !source.as_bytes()[start_idx - 1].is_ascii_alphanumeric();
                    if valid_boundary && (first_match.is_none() || start_idx < first_match.unwrap().1) {
                        let open_char = if p_idx == 0 { '(' } else if p_idx == 1 { '{' } else { ' ' };
                        first_match = Some((kw, start_idx, open_char));
                    }
                }
            }
        }

        if let Some((kw, start, open_char)) = first_match {
            if kw == "val" || kw == "var" {
                let line_end = source[start..].find('\n').map(|pos| start + pos).unwrap_or(source.len());
                let line = &source[start..line_end];
                if let Some(eq_pos) = line.find('=') {
                    let name = line[kw.len()..eq_pos].trim();
                    let val = resolve_expression(line[eq_pos + 1..].trim(), context, &runtime);
                    if kw == "var" {
                        if runtime.state_value(name).is_none() {
                            runtime.set_state(name, &val);
                        }
                    } else {
                        context.variables.insert(name.to_string(), val);
                    }
                }
                search_start = line_end;
                continue;
            }

            if kw == "@Import" {
                if let Some(op) = source[start..].find('(') {
                    if let Some(cp) = find_closing_brace(&source[start + op..], '(', ')') {
                        let path_arg = source[start + op + 1..start + op + cp].trim().trim_matches('"');
                        if let Some(base) = base_path {
                            let mut import_path = base.join(path_arg);
                            if import_path.extension().is_none() { import_path.set_extension("feather"); }
                            if let Ok(imported_source) = std::fs::read_to_string(&import_path) {
                                let _ = parse_widgets_inner(&imported_source, runtime.clone(), context, Some(&import_path.parent().unwrap_or(base)));
                            }
                        }
                        search_start = start + op + cp + 1;
                        continue;
                    }
                }
            }

            if kw == "data" {
                if source[start..].starts_with("data class ") {
                    let name_start = start + 11;
                    if let Some(op) = source[name_start..].find('(') {
                        let name = source[name_start..name_start + op].trim();
                        if let Some(cp) = find_closing_brace(&source[name_start + op..], '(', ')') {
                            let args_str = source[name_start + op + 1..name_start + op + cp].trim().to_string();
                            let defined_args: Vec<String> = args_str.split(',').map(|s| {
                                let mut d_name = s.trim().split(':').next().unwrap().trim().to_string();
                                if d_name.starts_with("val ") { d_name = d_name[4..].trim().to_string(); }
                                if d_name.starts_with("var ") { d_name = d_name[4..].trim().to_string(); }
                                d_name
                            }).filter(|s| !s.is_empty()).collect();
                            
                            context.data_classes.insert(name.to_string(), defined_args);
                            search_start = name_start + op + cp + 1;
                            continue;
                        }
                    }
                }
            }

            if kw == "fun" || kw == "@Composable" {
                let mut name_start = start + kw.len();
                if kw == "@Composable" {
                    if let Some(f_pos) = source[name_start..].find("fun") {
                        name_start += f_pos + 3;
                    }
                }
                if let Some(op) = source[name_start..].find('(') {
                    let name = source[name_start..name_start + op].trim();
                    if let Some(cp) = find_closing_brace(&source[name_start + op..], '(', ')') {
                        let args_str = source[name_start + op + 1..name_start + op + cp].trim().to_string();
                        if let Some(bs) = source[name_start + op + cp..].find('{') {
                            let body_start = name_start + op + cp + bs;
                            if let Some(be) = find_closing_brace(&source[body_start..], '{', '}') {
                                context.functions.insert(name.to_string(), (args_str, source[body_start + 1..body_start + be].to_string()));
                                search_start = body_start + be + 1;
                                continue;
                            }
                        }
                    }
                }
            }

            if kw == "if" {
                if let Some(op) = source[start..].find('(') {
                    let cond_start = start + op;
                    if let Some(ce) = find_closing_brace(&source[cond_start..], '(', ')') {
                        let condition = &source[cond_start + 1..cond_start + ce];
                        let condition_met = evaluate_condition(condition, context, &runtime);
                        if let Some(bs) = source[cond_start + ce..].find('{') {
                            let body_start = cond_start + ce + bs;
                            if let Some(be) = find_closing_brace(&source[body_start..], '{', '}') {
                                if condition_met {
                                    widgets.extend(parse_widgets_inner(&source[body_start + 1..body_start + be], runtime.clone(), context, base_path));
                                }
                                let remaining = &source[body_start + be + 1..];
                                if remaining.trim_start().starts_with("else") {
                                    if let Some(ebs) = remaining.find('{') {
                                        let else_body_start = body_start + be + 1 + ebs;
                                        if let Some(ebe) = find_closing_brace(&source[else_body_start..], '{', '}') {
                                            if !condition_met {
                                                widgets.extend(parse_widgets_inner(&source[else_body_start + 1..else_body_start + ebe], runtime.clone(), context, base_path));
                                            }
                                            search_start = else_body_start + ebe + 1;
                                            continue;
                                        }
                                    }
                                }
                                search_start = body_start + be + 1;
                                continue;
                            }
                        }
                    }
                }
            }

            if kw == "fetch" {
                if let Some(op) = source[start..].find('(') {
                    let args_start = start + op;
                    if let Some(ae) = find_closing_brace(&source[args_start..], '(', ')') {
                        let args_str = &source[args_start + 1..args_start + ae];
                        let args: Vec<&str> = args_str.split(',').map(|s| s.trim()).collect();
                        if args.len() == 2 {
                            let url = resolve_expression(args[0], context, &runtime);
                            let key = resolve_expression(args[1], context, &runtime);
                            let rt = runtime.clone();
                            tokio::spawn(async move { let _ = rt.fetch(&url, &key).await; });
                        }
                        search_start = args_start + ae + 1;
                        continue;
                    }
                }
            }

            let close_char = if open_char == '(' { ')' } else { '}' };
            let start_bracket = start + source[start..].find(open_char).unwrap();
            if let Some(end_offset) = find_closing_brace(&source[start_bracket..], open_char, close_char) {
                let end_idx = start_bracket + end_offset + 1;
                let body = &source[start..end_idx];
                let inner_body = &source[start_bracket + 1..end_idx - 1];

                let widget = match kw {
                    "Text" => WidgetNode::Text {
                        text: resolve_expression(&extract_first_string_argument(body, "Text").unwrap_or_else(|| "Text".to_string()), context, &runtime),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "StateText" => {
                        let key = extract_named_string_argument(body, "key").unwrap_or_default();
                        let prefix = extract_named_string_argument(body, "prefix").unwrap_or_default();
                        let fallback = extract_named_string_argument(body, "fallback").unwrap_or_default();
                        WidgetNode::Text {
                            text: runtime.state_value(&key).map(|v| format!("{prefix}{v}")).unwrap_or(fallback),
                            style: extract_style_from_body(body, context, &runtime),
                        }
                    }
                    "Button" => WidgetNode::Button {
                        text: resolve_expression(&extract_named_string_argument(body, "text").or_else(|| extract_first_string_argument(body, "Button")).unwrap_or_else(|| "Button".to_string()), context, &runtime),
                        action: extract_named_string_argument(body, "action").unwrap_or_else(|| "buttonPressed".to_string()),
                        enabled: extract_named_bool_argument(body, "enabled"),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "TextInput" => WidgetNode::TextInput {
                        value: extract_named_string_argument(body, "value").unwrap_or_default(),
                        placeholder: extract_named_string_argument(body, "placeholder").unwrap_or_default(),
                        action: extract_named_string_argument(body, "action").unwrap_or_else(|| "textChanged".to_string()),
                        label: extract_named_string_argument(body, "label"),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Row" => WidgetNode::Row {
                        children: parse_widgets_inner(inner_body, runtime.clone(), context, base_path),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Column" => WidgetNode::Column {
                        children: parse_widgets_inner(inner_body, runtime.clone(), context, base_path),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Box" => WidgetNode::Box {
                        children: parse_widgets_inner(inner_body, runtime.clone(), context, base_path),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Image" => WidgetNode::Image {
                        src: resolve_expression(&extract_named_string_argument(body, "src").or_else(|| extract_first_string_argument(body, "Image")).unwrap_or_default(), context, &runtime),
                        alt: extract_named_string_argument(body, "alt").unwrap_or_default(),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Spacer" => WidgetNode::Spacer { flex: 1 },
                    "Icon" => WidgetNode::Icon {
                        name: resolve_expression(&extract_named_string_argument(body, "name").or_else(|| extract_first_string_argument(body, "Icon")).unwrap_or_else(|| "star".to_string()), context, &runtime),
                        size: extract_named_int_argument(body, "size", context),
                        color: extract_named_string_argument(body, "color").map(|s| resolve_expression(&s, context, &runtime)),
                        action: extract_named_string_argument(body, "action"),
                    },
                    "IconButton" => WidgetNode::IconButton {
                        icon: resolve_expression(&extract_named_string_argument(body, "icon").or_else(|| extract_first_string_argument(body, "IconButton")).unwrap_or_else(|| "menu".to_string()), context, &runtime),
                        action: extract_named_string_argument(body, "action").unwrap_or_default(),
                        size: extract_named_int_argument(body, "size", context),
                        color: extract_named_string_argument(body, "color").map(|s| resolve_expression(&s, context, &runtime)),
                        background: extract_named_string_argument(body, "background").map(|s| resolve_expression(&s, context, &runtime)),
                    },
                    "LazyColumn" => WidgetNode::LazyColumn {
                        items: parse_widgets_inner(inner_body, runtime.clone(), context, base_path),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "LazyRow" => WidgetNode::LazyRow {
                        items: parse_widgets_inner(inner_body, runtime.clone(), context, base_path),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Scaffold" => {
                        let body_widgets = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        WidgetNode::Scaffold {
                            top_bar: None, bottom_bar: None, floating_action_button: None, drawer: None,
                            body: Box::new(if body_widgets.len() == 1 { body_widgets.into_iter().next().unwrap() } else { WidgetNode::Column { children: body_widgets, style: None } }),
                            style: extract_style_from_body(body, context, &runtime),
                        }
                    }
                    "VideoPlayer" => WidgetNode::VideoPlayer {
                        src: resolve_expression(&extract_named_string_argument(body, "src").or_else(|| extract_first_string_argument(body, "VideoPlayer")).unwrap_or_default(), context, &runtime),
                        autoplay: extract_named_bool_argument(body, "autoplay"),
                        controls: extract_named_bool_argument(body, "controls"),
                        loop_playback: extract_named_bool_argument(body, "loop"),
                        muted: extract_named_bool_argument(body, "muted"),
                        poster: extract_named_string_argument(body, "poster"),
                        style: extract_style_from_body(body, context, &runtime),
                    },
                    "Screen" => {
                        let route = extract_named_string_argument(body, "route").or_else(|| extract_first_string_argument(body, "Screen")).unwrap_or_default();
                        let content_widgets = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        WidgetNode::Screen {
                            route,
                            content: Box::new(if content_widgets.len() == 1 { content_widgets.into_iter().next().unwrap() } else { WidgetNode::Column { children: content_widgets, style: None } }),
                        }
                    }
                    "Navigator" => {
                        let initial_route = extract_named_string_argument(body, "initialRoute").unwrap_or_else(|| "Home".to_string());
                        let parsed_screens = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        let mut screens = HashMap::new();
                        for screen in parsed_screens {
                            if let WidgetNode::Screen { route, content } = screen {
                                screens.insert(route, *content);
                            }
                        }
                        WidgetNode::Navigator {
                            initial_route,
                            screens,
                        }
                    }
                    "ForEach" => {
                        let items_key = extract_named_string_argument(body, "itemsKey")
                            .or_else(|| extract_first_string_argument(body, "ForEach"))
                            .unwrap_or_default();
                        
                        let template_widgets = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        let template = if template_widgets.len() == 1 {
                            template_widgets.into_iter().next().unwrap()
                        } else {
                            WidgetNode::Column { children: template_widgets, style: None }
                        };

                        WidgetNode::ForEach {
                            items_key,
                            template: Box::new(template),
                        }
                    }
                    "Condition" => {
                        let key = extract_named_string_argument(body, "key")
                            .or_else(|| extract_first_string_argument(body, "Condition"))
                            .unwrap_or_default();

                        let children = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        let (then, otherwise) = if children.len() >= 2 {
                            let mut iter = children.into_iter();
                            (iter.next().unwrap(), Some(Box::new(iter.next().unwrap())))
                        } else if children.len() == 1 {
                            (children.into_iter().next().unwrap(), None)
                        } else {
                            (WidgetNode::Text { text: "No content".to_string(), style: None }, None)
                        };

                        WidgetNode::Condition {
                            key,
                            then: Box::new(then),
                            otherwise,
                        }
                    }
                    "GestureDetector" => {
                        let on_tap = extract_named_string_argument(body, "onTap");
                        let on_pan = extract_named_string_argument(body, "onPan");
                        let children = parse_widgets_inner(inner_body, runtime.clone(), context, base_path);
                        WidgetNode::GestureDetector {
                            on_tap,
                            on_pan,
                            child: Box::new(if children.len() == 1 { children.into_iter().next().unwrap() } else { WidgetNode::Column { children, style: None } }),
                        }
                    }
                    _ => {
                        if let Some((fn_args_str, fn_body)) = context.functions.get(kw).cloned() {
                            let mut local_context = ParserContext {
                                variables: context.variables.clone(),
                                functions: context.functions.clone(),
                                data_classes: context.data_classes.clone(),
                            };

                            let defined_args: Vec<&str> = fn_args_str.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()).collect();
                            let passed_args: Vec<&str> = body.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()).collect();

                            for (i, d_arg) in defined_args.iter().enumerate() {
                                let d_name = d_arg.split(':').next().unwrap().trim();
                                if let Some(p_arg) = passed_args.get(i) {
                                    let mut val_expr = *p_arg;
                                    if let Some(eq_pos) = p_arg.find('=') {
                                        val_expr = p_arg[eq_pos + 1..].trim();
                                    }
                                    let resolved = resolve_expression(val_expr, context, &runtime);
                                    local_context.variables.insert(d_name.to_string(), resolved);
                                }
                            }

                            widgets.extend(parse_widgets_inner(&fn_body, runtime.clone(), &mut local_context, base_path));
                            WidgetNode::Spacer { flex: 0 }
                        } else {
                            WidgetNode::Text { text: format!("Unsupported: {kw}"), style: None }
                        }
                    }
                };
                if !matches!(widget, WidgetNode::Spacer { flex: 0 }) {
                    widgets.push(widget);
                }
                search_start = end_idx;
            } else { break; }
        } else { break; }
    }
    widgets
}

fn find_closing_brace(source: &str, open: char, close: char) -> Option<usize> {
    let mut depth = 0;
    for (i, c) in source.char_indices() {
        if c == open { depth += 1; }
        else if c == close {
            depth -= 1;
            if depth == 0 { return Some(i); }
        }
    }
    None
}

fn extract_first_string_argument(line: &str, function_name: &str) -> Option<String> {
    let prefix = format!("{function_name}(\"");
    if let Some(pos) = line.find(&prefix) {
        let after = &line[pos + prefix.len()..];
        return after.find('"').map(|e| after[..e].to_string());
    }
    let prefix_no_quote = format!("{function_name}(");
    let start = line.find(&prefix_no_quote)? + prefix_no_quote.len();
    let after = &line[start..];
    after.find(|c: char| c == ')' || c == ',').map(|e| after[..e].trim().to_string())
}

fn extract_named_string_argument(line: &str, name: &str) -> Option<String> {
    let p1 = format!("{name} = \"");
    if let Some(pos) = line.find(&p1) {
        let after = &line[pos + p1.len()..];
        return after.find('"').map(|e| after[..e].to_string());
    }
    let p2 = format!("{name} = ");
    let start = line.find(&p2)? + p2.len();
    let after = &line[start..];
    after.find(|c: char| c == ')' || c == ',' || c == '\n').map(|e| after[..e].trim().to_string())
}

fn extract_named_int_argument(line: &str, name: &str, context: &ParserContext) -> Option<u16> {
    let p = format!("{name} = ");
    let start = line.find(&p)? + p.len();
    let after = &line[start..];
    let end = after.find(|c: char| !c.is_ascii_digit() && !c.is_ascii_alphanumeric()).unwrap_or(after.len());
    let raw = after[..end].trim();
    raw.parse().ok().or_else(|| context.variables.get(raw).and_then(|v| v.parse().ok()))
}

fn extract_named_float_argument(line: &str, name: &str) -> Option<f32> {
    let p = format!("{name} = ");
    let start = line.find(&p)? + p.len();
    let after = &line[start..];
    let end = after.find(|c: char| !c.is_ascii_digit() && c != '.').unwrap_or(after.len());
    after[..end].parse().ok()
}

fn extract_named_bool_argument(line: &str, name: &str) -> Option<bool> {
    let p = format!("{name} = ");
    let start = line.find(&p)? + p.len();
    let after = &line[start..];
    if after.starts_with("true") { Some(true) } else if after.starts_with("false") { Some(false) } else { None }
}

fn extract_style_from_body(body: &str, context: &ParserContext, runtime: &Arc<AppRuntime>) -> Option<WidgetStyle> {
    let mut style = WidgetStyle::default();
    style.color = extract_named_string_argument(body, "color").map(|s| resolve_expression(&s, context, runtime));
    style.font_size = extract_named_int_argument(body, "fontSize", context);
    style.font_weight = extract_named_string_argument(body, "fontWeight").map(|s| resolve_expression(&s, context, runtime));
    style.font_style = extract_named_string_argument(body, "fontStyle").map(|s| resolve_expression(&s, context, runtime));
    style.text_align = extract_named_string_argument(body, "textAlign").map(|s| resolve_expression(&s, context, runtime));
    style.background = extract_named_string_argument(body, "background").map(|s| resolve_expression(&s, context, runtime));
    style.padding = extract_named_int_argument(body, "padding", context);
    style.margin = extract_named_int_argument(body, "margin", context);
    style.corner_radius = extract_named_int_argument(body, "cornerRadius", context);
    style.width = extract_named_string_argument(body, "width").map(|s| resolve_expression(&s, context, runtime));
    style.height = extract_named_string_argument(body, "height").map(|s| resolve_expression(&s, context, runtime));
    style.position = extract_named_string_argument(body, "position");
    style.top = extract_named_float_argument(body, "top");
    style.bottom = extract_named_float_argument(body, "bottom");
    style.left = extract_named_float_argument(body, "left");
    style.right = extract_named_float_argument(body, "right");
    style.z_index = extract_named_int_argument(body, "zIndex", context).map(|v| v as i32);
    if style.is_empty() { None } else { Some(style) }
}

fn evaluate_condition(condition: &str, context: &ParserContext, runtime: &Arc<AppRuntime>) -> bool {
    let c = condition.trim();
    if c == "true" { return true; }
    if c == "false" { return false; }
    if let Some(v) = context.variables.get(c) { return v == "true"; }
    if let Some(v) = runtime.state_value(c) { return v == "true"; }
    if c.contains("==") {
        let p: Vec<&str> = c.split("==").collect();
        return p.len() == 2 && resolve_expression(p[0], context, runtime) == resolve_expression(p[1], context, runtime);
    }
    if c.contains("!=") {
        let p: Vec<&str> = c.split("!=").collect();
        return p.len() == 2 && resolve_expression(p[0], context, runtime) != resolve_expression(p[1], context, runtime);
    }
    false
}

fn resolve_expression(input: &str, context: &ParserContext, runtime: &Arc<AppRuntime>) -> String {
    let i = input.trim();
    if i.starts_with('"') && i.ends_with('"') {
        let mut s = i.trim_matches('"').to_string();
        while let Some(start) = s.find("${") {
            if let Some(end) = s[start..].find('}') {
                let expr = &s[start + 2..start + end];
                let resolved = resolve_expression(expr, context, runtime);
                s.replace_range(start..start + end + 1, &resolved);
            } else {
                break;
            }
        }
        return s;
    }
    
    if let Some(v) = context.variables.get(i) { return v.clone(); }
    if let Some(v) = runtime.state_value(i) { return v; }
    
    if let Some(op) = i.find('(') {
        if i.ends_with(')') {
            let name = i[..op].trim();
            if let Some(fields) = context.data_classes.get(name) {
                let args_str = &i[op + 1..i.len() - 1];
                let passed_args: Vec<&str> = args_str.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()).collect();
                let mut json = String::new();
                json.push('{');
                for (idx, field) in fields.iter().enumerate() {
                    if let Some(p_arg) = passed_args.get(idx) {
                        let val = resolve_expression(p_arg, context, runtime);
                        let mut formatted_val = val.clone();
                        if !val.starts_with('"') && !val.starts_with('{') && !val.starts_with('[') && val != "true" && val != "false" && val.parse::<f64>().is_err() {
                            formatted_val = format!("\"{}\"", val);
                        }
                        json.push_str(&format!("\"{}\": {}", field, formatted_val));
                        if idx < fields.len() - 1 { json.push(','); }
                    }
                }
                json.push('}');
                return json;
            }
        }
    }
    
    if let Some((left, right)) = i.split_once('+') {
        let a_str = resolve_expression(left, context, runtime);
        let b_str = resolve_expression(right, context, runtime);
        if let (Ok(a), Ok(b)) = (a_str.parse::<f64>(), b_str.parse::<f64>()) { return (a + b).to_string(); }
        return format!("{}{}", a_str, b_str); // Fallback to string concat
    }
    if let Some((left, right)) = i.split_once('-') {
        if let (Ok(a), Ok(b)) = (resolve_expression(left, context, runtime).parse::<f64>(), resolve_expression(right, context, runtime).parse::<f64>()) { return (a - b).to_string(); }
    }
    if let Some((left, right)) = i.split_once('*') {
        if let (Ok(a), Ok(b)) = (resolve_expression(left, context, runtime).parse::<f64>(), resolve_expression(right, context, runtime).parse::<f64>()) { return (a * b).to_string(); }
    }
    if let Some((left, right)) = i.split_once('/') {
        if let (Ok(a), Ok(b)) = (resolve_expression(left, context, runtime).parse::<f64>(), resolve_expression(right, context, runtime).parse::<f64>()) { return (a / b).to_string(); }
    }
    
    i.to_string()
}

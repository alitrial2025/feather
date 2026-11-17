use pest::Parser;
use pest_derive::Parser;
use crate::models::{WidgetNode, WidgetStyle};
use std::collections::HashMap;

#[derive(Parser)]
#[grammar = "feather.pest"]
pub struct FuiParser;

#[allow(dead_code)]
pub struct FeatherUI {
    pub imports: Vec<String>,
    pub states: HashMap<String, String>,
    pub root: WidgetNode,
}

/// Parses a Feather UI (.fthr) source string into a high-level widget tree.
pub fn parse_fui(source: &str) -> Result<FeatherUI, String> {
    let pairs = FuiParser::parse(Rule::file, source)
        .map_err(|e| format!("Parsing error: {}", e))?;
    
    let mut imports = Vec::new();
    let mut states = HashMap::new();
    let mut root = None;

    let mut current_pairs = pairs.into_iter().next().ok_or("No root pair found")?.into_inner();
    while let Some(pair) = current_pairs.next() {
        match pair.as_rule() {
            Rule::import_stmt => {
                let path = pair.into_inner().next().ok_or("Import statement missing path")?.as_str().trim_matches('"').to_string();
                imports.push(path);
            }
            Rule::state_stmt => {
                let mut inner = pair.into_inner();
                let name = inner.next().ok_or("State statement missing identifier")?.as_str().to_string();
                let expr = evaluate_expression(inner.next().ok_or("State statement missing value expression")?);
                states.insert(name, expr);
            }
            Rule::widget => {
                root = Some(map_widget(pair));
            }
            Rule::EOI => break,
            _ => {}
        }
    }

    Ok(FeatherUI {
        imports,
        states,
        root: root.unwrap_or(WidgetNode::Spacer { flex: 0 }),
    })
}

fn evaluate_expression(pair: pest::iterators::Pair<Rule>) -> String {
    match pair.as_rule() {
        Rule::expression => {
            let mut inner = pair.into_inner();
            let mut result = evaluate_expression(inner.next().unwrap());
            
            while let (Some(op), Some(next_term)) = (inner.next(), inner.next()) {
                let next_val = evaluate_expression(next_term);
                result = fold_binary_op(&result, op.as_str(), &next_val);
            }
            result
        }
        Rule::term => evaluate_expression(pair.into_inner().next().unwrap()),
        Rule::value => {
            let inner = pair.into_inner().next().unwrap();
            inner.as_str().trim().to_string()
        }
        _ => pair.as_str().to_string(),
    }
}

fn fold_binary_op(left: &str, op: &str, right: &str) -> String {
    let left_clean = left.trim_matches('"');
    let right_clean = right.trim_matches('"');

    match op {
        "+" => {
            if let (Ok(l), Ok(r)) = (left_clean.parse::<f64>(), right_clean.parse::<f64>()) {
                (l + r).to_string()
            } else {
                format!("\"{}{}\"", left_clean, right_clean) // Simple AOT concat
            }
        }
        "-" => {
            if let (Ok(l), Ok(r)) = (left_clean.parse::<f64>(), right_clean.parse::<f64>()) {
                (l - r).to_string()
            } else { format!("({} {} {})", left, op, right) }
        }
        "*" => {
            if let (Ok(l), Ok(r)) = (left_clean.parse::<f64>(), right_clean.parse::<f64>()) {
                (l * r).to_string()
            } else { format!("({} {} {})", left, op, right) }
        }
        _ => format!("{} {} {}", left, op, right),
    }
}

fn map_widget(pair: pest::iterators::Pair<Rule>) -> WidgetNode {
    let mut inner = pair.into_inner();
    let name = inner.next().unwrap().as_str();
    
    let mut args = HashMap::new();
    let mut children = Vec::new();

    for part in inner {
        match part.as_rule() {
            Rule::arguments => {
                let mut pos_idx = 0;
                for arg in part.into_inner() {
                    let mut arg_inner = arg.into_inner();
                    let first = arg_inner.next().unwrap();
                    if first.as_rule() == Rule::identifier {
                        // Named argument: key: value
                        let expr = arg_inner.next().unwrap();
                        let val = evaluate_expression(expr);
                        args.insert(first.as_str().to_string(), val);
                    } else {
                        // Positional argument: value
                        let val = evaluate_expression(first);
                        let key = match (name, pos_idx) {
                            ("Text", 0) => "text",
                            ("Button", 0) => "text",
                            ("Button", 1) => "action",
                            ("Image", 0) => "src",
                            ("Image", 1) => "alt",
                            ("Icon", 0) => "name",
                            ("IconButton", 0) => "icon",
                            ("IconButton", 1) => "action",
                            ("TextInput", 0) => "value",
                            ("TextInput", 1) => "placeholder",
                            ("TextInput", 2) => "action",
                            ("Condition", 0) => "key",
                            ("ForEach", 0) => "itemsKey",
                            _ => "text",
                        };
                        args.insert(key.to_string(), val);
                        pos_idx += 1;
                    }
                }
            }
            Rule::children => {
                for child in part.into_inner() {
                    match child.as_rule() {
                        Rule::widget => children.push(map_widget(child)),
                        Rule::if_expr => children.push(map_if(child)),
                        Rule::for_expr => children.push(map_for(child)),
                        _ => {}
                    }
                }
            }
            _ => {}
        }
    }

    let style = extract_style(&args);

    match name {
        "Text" => WidgetNode::Text {
            text: clean_val(args.get("text")),
            style,
        },
        "StateText" => WidgetNode::StateText {
            key: clean_val(args.get("key")),
            prefix: clean_val(args.get("prefix")),
            fallback: clean_val(args.get("fallback")),
            style,
        },
        "Button" => WidgetNode::Button {
            text: clean_val(args.get("text")),
            action: clean_val(args.get("action")),
            enabled: args.get("enabled").map(|v| v == "true"),
            style,
        },
        "TextInput" => WidgetNode::TextInput {
            value: clean_val(args.get("value")),
            placeholder: clean_val(args.get("placeholder")),
            action: clean_val(args.get("action")),
            label: args.get("label").map(|v| clean_val(Some(v))),
            style,
        },
        "Image" => WidgetNode::Image {
            src: clean_val(args.get("src")),
            alt: clean_val(args.get("alt")),
            style,
        },
        "Icon" => WidgetNode::Icon {
            name: clean_val(args.get("name")),
            size: args.get("size").and_then(|v| v.parse().ok()),
            color: args.get("color").map(|v| clean_val(Some(v))),
            action: args.get("action").map(|v| clean_val(Some(v))),
        },
        "IconButton" => WidgetNode::IconButton {
            icon: clean_val(args.get("icon")),
            action: clean_val(args.get("action")),
            size: args.get("size").and_then(|v| v.parse().ok()),
            color: args.get("color").map(|v| clean_val(Some(v))),
            background: args.get("background").map(|v| clean_val(Some(v))),
        },
        "Spacer" => WidgetNode::Spacer {
            flex: args.get("flex").and_then(|v| v.parse().ok()).unwrap_or(1),
        },
        "Divider" => WidgetNode::Divider {
            color: args.get("color").map(|v| clean_val(Some(v))),
            thickness: args.get("thickness").and_then(|v| v.parse().ok()),
            indent: args.get("indent").and_then(|v| v.parse().ok()),
        },
        "Column" => WidgetNode::Column { children, style },
        "Row" => WidgetNode::Row { children, style },
        "Box" => WidgetNode::Box { children, style },
        "LazyColumn" => WidgetNode::LazyColumn { items: children, style },
        "LazyRow" => WidgetNode::LazyRow { items: children, style },
        "ScrollView" => WidgetNode::ScrollView { 
            children, 
            direction: args.get("direction").map(|v| clean_val(Some(v))),
            style 
        },
        "Card" => WidgetNode::Card { 
            children, 
            action: args.get("action").map(|v| clean_val(Some(v))),
            style 
        },
        "Surface" => WidgetNode::Surface { children, style },
        "Switch" => WidgetNode::Switch {
            checked: args.get("checked").map_or(false, |v| v == "true"),
            action: clean_val(args.get("action")),
            enabled: args.get("enabled").map(|v| v == "true"),
            label: args.get("label").map(|v| clean_val(Some(v))),
            track_color: args.get("trackColor").map(|v| clean_val(Some(v))),
            thumb_color: args.get("thumbColor").map(|v| clean_val(Some(v))),
        },
        "Checkbox" => WidgetNode::Checkbox {
            checked: args.get("checked").map_or(false, |v| v == "true"),
            action: clean_val(args.get("action")),
            enabled: args.get("enabled").map(|v| v == "true"),
            label: args.get("label").map(|v| clean_val(Some(v))),
            color: args.get("color").map(|v| clean_val(Some(v))),
        },
        "Slider" => WidgetNode::Slider {
            value: args.get("value").and_then(|v| v.parse().ok()).unwrap_or(0.0),
            min: args.get("min").and_then(|v| v.parse().ok()).unwrap_or(0.0),
            max: args.get("max").and_then(|v| v.parse().ok()).unwrap_or(100.0),
            step: args.get("step").and_then(|v| v.parse().ok()),
            action: clean_val(args.get("action")),
            label: args.get("label").map(|v| clean_val(Some(v))),
            track_color: args.get("trackColor").map(|v| clean_val(Some(v))),
            thumb_color: args.get("thumbColor").map(|v| clean_val(Some(v))),
        },
        "Scaffold" => WidgetNode::Scaffold {
            top_bar: None,
            bottom_bar: None,
            floating_action_button: None,
            drawer: None,
            body: std::boxed::Box::new(if children.is_empty() {
                WidgetNode::Spacer { flex: 0 }
            } else {
                children.remove(0)
            }),
            style,
        },
        "CameraPreview" => WidgetNode::CustomNode {
            module: "feather-camera".to_string(),
            name: "CameraPreview".to_string(),
            props: serde_json::json!({ "onPermissionResult": clean_val(args.get("onPermissionResult")) }),
            style,
        },
        "MapView" => WidgetNode::CustomNode {
            module: "feather-maps".to_string(),
            name: "MapView".to_string(),
            props: serde_json::json!({
                "latitude": args.get("latitude").and_then(|v| v.parse::<f64>().ok()),
                "longitude": args.get("longitude").and_then(|v| v.parse::<f64>().ok()),
                "zoom": args.get("zoom").and_then(|v| v.parse::<f32>().ok()),
            }),
            style,
        },
        _ => WidgetNode::Text {
            text: format!("Unknown widget: {}", name),
            style: None,
        }
    }
}

fn map_if(pair: pest::iterators::Pair<Rule>) -> WidgetNode {
    let mut inner = pair.into_inner();
    let condition = evaluate_expression(inner.next().unwrap());
    
    let mut then_children = Vec::new();
    let mut otherwise = None;

    while let Some(node) = inner.next() {
        if node.as_rule() == Rule::children {
            for child in node.into_inner() {
                match child.as_rule() {
                    Rule::widget => then_children.push(map_widget(child)),
                    Rule::if_expr => then_children.push(map_if(child)),
                    Rule::for_expr => then_children.push(map_for(child)),
                    _ => {}
                }
            }
        } else if node.as_str() == "else" {
             if let Some(else_node) = inner.next() {
                 let mut else_children = Vec::new();
                 for child in else_node.into_inner() {
                     match child.as_rule() {
                         Rule::widget => else_children.push(map_widget(child)),
                         Rule::if_expr => else_children.push(map_if(child)),
                         Rule::for_expr => else_children.push(map_for(child)),
                         _ => {}
                     }
                 }
                 if !else_children.is_empty() {
                     otherwise = Some(std::boxed::Box::new(WidgetNode::Column { children: else_children, style: None }));
                 }
             }
        }
    }

    WidgetNode::Condition {
        key: condition,
        then: std::boxed::Box::new(WidgetNode::Column { children: then_children, style: None }),
        otherwise,
    }
}

fn map_for(pair: pest::iterators::Pair<Rule>) -> WidgetNode {
    let mut inner = pair.into_inner();
    let _item_name = inner.next().unwrap().as_str();
    let items_key = evaluate_expression(inner.next().unwrap());
    
    let mut template_children = Vec::new();
    if let Some(body) = inner.next() {
        for child in body.into_inner() {
             match child.as_rule() {
                Rule::widget => template_children.push(map_widget(child)),
                Rule::if_expr => template_children.push(map_if(child)),
                Rule::for_expr => template_children.push(map_for(child)),
                _ => {}
            }
        }
    }

    WidgetNode::ForEach {
        items_key,
        template: std::boxed::Box::new(WidgetNode::Column { children: template_children, style: None }),
    }
}

fn extract_style(args: &HashMap<String, String>) -> Option<WidgetStyle> {
    let mut style = WidgetStyle::default();
    let mut has_style = false;

    macro_rules! set_style_type {
        ($key:expr, $field:ident, $type:ty) => {
            if let Some(v) = args.get($key) {
                if let Ok(parsed) = v.parse::<$type>() {
                    style.$field = Some(parsed);
                    has_style = true;
                }
            }
        };
    }

    macro_rules! set_style_str {
        ($key:expr, $field:ident) => {
            if let Some(v) = args.get($key) {
                style.$field = Some(v.trim_matches('"').to_string());
                has_style = true;
            }
        };
    }

    set_style_str!("color", color);
    set_style_type!("fontSize", font_size, u16);
    set_style_str!("fontWeight", font_weight);
    set_style_str!("background", background);
    set_style_type!("padding", padding, u16);
    set_style_type!("margin", margin, u16);
    set_style_type!("cornerRadius", corner_radius, u16);
    set_style_str!("width", width);
    set_style_str!("height", height);
    set_style_type!("opacity", opacity, f32);

    if has_style { Some(style) } else { None }
}

fn clean_val(val: Option<&String>) -> String {
    val.map(|v| v.trim_matches('"').to_string()).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use feather_core::AppRuntime;

    #[test]
    fn test_parse_fthr_full() {
        let source = r#"
            state loggedIn = false
            state items = ["Home", "Settings"]
            
            Column {
                if (loggedIn) {
                    Text("Welcome Back!")
                } else {
                    Button("Login", action: "loggedIn = true")
                }
                
                for (item in items) {
                    Text(item)
                }
            }
        "#;
        let fui = parse_fui(source).expect("Should parse valid FUI");
        
        if let WidgetNode::Column { children, .. } = fui.root {
            assert_eq!(children.len(), 2);
        } else {
            panic!("Expected Column root");
        }
    }

    #[test]
    fn test_aot_folding() {
        let source = r#"Text("Result: " + (10 + 20))"#;
        let fui = parse_fui(source).unwrap();
        if let WidgetNode::Text { text, .. } = fui.root {
            assert_eq!(text, "Result: 30");
        } else {
            panic!("Expected folded Text");
        }
    }

    #[test]
    fn keyword_prefixes_and_underscores_remain_valid_identifiers() {
        let fui = parse_fui("state state_name = stateful.age Text(state_name)").unwrap();
        assert_eq!(fui.states.get("state_name").unwrap(), "stateful.age");
    }

    #[test]
    fn test_dot_notation_and_functions() {
        let source = r#"
            state testVal = min(state.user.age, 18)
            Text("Age: " + state.user.age)
        "#;
        let fui = parse_fui(source).unwrap();
        let state_val = fui.states.get("testVal").unwrap();
        assert!(state_val.starts_with("min"));
        assert!(state_val.contains("state.user.age"));
        if let WidgetNode::Text { .. } = fui.root {
            // Success
        } else {
            panic!("Expected Text widget");
        }
    }
}

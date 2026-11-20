use super::{Screen, group, quote};
use roxmltree::{Document, Node};

const ANDROID: &str = "http://schemas.android.com/apk/res/android";

pub fn translate(source: &str) -> Result<Screen, String> {
    let document = Document::parse(source).map_err(|error| error.to_string())?;
    let mut screen = Screen::default();
    screen.limitations.push("Scope: Android layout XML. Kotlin/Java controllers, resources, constraints and native dependencies require migration.".into());
    screen.root = widget(document.root_element(), &mut screen);
    Ok(screen)
}

fn note(screen: &mut Screen, node: Node, text: impl std::fmt::Display) {
    let position = node.document().text_pos_at(node.range().start);
    screen
        .limitations
        .push(format!("{}:{} {text}", position.row, position.col));
}

fn literal(node: Node, property: &str, screen: &mut Screen) -> String {
    let value = node.attribute((ANDROID, property)).unwrap_or("");
    if value.starts_with('@') || value.starts_with('?') {
        note(screen, node, format!("Resolve Android resource {value}"));
    }
    quote(value)
}

fn widget(node: Node, screen: &mut Screen) -> String {
    let class = node.tag_name().name().rsplit('.').next().unwrap_or("");
    for attribute in node.attributes() {
        if !matches!(
            attribute.name(),
            "text" | "hint" | "orientation" | "layout_width" | "layout_height" | "id"
        ) || matches!(attribute.name(), "layout_width" | "layout_height")
            && !matches!(attribute.value(), "match_parent" | "wrap_content")
        {
            note(
                screen,
                node,
                format!("Android property {} needs migration", attribute.name()),
            );
        }
    }
    match class {
        "TextView" => format!("Text({})", literal(node, "text", screen)),
        "Button" => {
            note(
                screen,
                node,
                "Button is disabled until its Kotlin/Java listener is migrated",
            );
            format!(
                "Button({}, \"\", enabled = false)",
                literal(node, "text", screen)
            )
        }
        "EditText" => {
            let key = quote(&format!("rewrite-input:{}", node.range().start));
            let initial = literal(node, "text", screen);
            let hint = literal(node, "hint", screen);
            screen.initializers.push(format!(
                "if (getState({key}) == null) setStateText({key}, {initial})"
            ));
            let action = screen.action(format!("setStateText({key}, actionText(payload))"));
            note(
                screen,
                node,
                "EditText keeps local input state; application validation requires migration",
            );
            format!(
                "TextInput(value = getState({key}) ?: \"\", placeholder = {hint}, action = {action})"
            )
        }
        _ => {
            let mut widgets = Vec::new();
            let target = match class {
                "LinearLayout" if node.attribute((ANDROID, "orientation")) != Some("vertical") => {
                    "Row"
                }
                "LinearLayout" | "layout" => "Column",
                "FrameLayout" => "Box",
                "ScrollView" | "NestedScrollView" => "ScrollView",
                _ => {
                    note(
                        screen,
                        node,
                        format!("Android view {class} requires a Feather equivalent"),
                    );
                    widgets.push(format!(
                        "Text({})",
                        quote(&format!("Unsupported view: {class}"))
                    ));
                    "Column"
                }
            };
            widgets.extend(
                node.children()
                    .filter(Node::is_element)
                    .map(|child| widget(child, screen)),
            );
            group(target, widgets, "")
        }
    }
}

use tree_sitter::{Parser, Node};
use crate::models::{WidgetNode, WidgetStyle};
use feather_core::AppRuntime;
use std::collections::HashMap;
use std::sync::Arc;

pub struct SymbolicParser {
    source: String,
    runtime: Arc<AppRuntime>,
    variables: HashMap<String, String>,
}

impl SymbolicParser {
    pub fn new(source: &str, runtime: Arc<AppRuntime>) -> Self {
        Self {
            source: source.to_string(),
            runtime,
            variables: HashMap::new(),
        }
    }

    pub fn parse_widgets(&mut self) -> Vec<WidgetNode> {
        let mut parser = Parser::new();
        let language = tree_sitter_kotlin::language();
        parser.set_language(&language).expect("Error loading Kotlin grammar");
        
        let tree = parser.parse(&self.source, None).expect("Error parsing source");
        let root_node = tree.root_node();
        
        let mut widgets = Vec::new();
        self.visit_node(root_node, &mut widgets);
        widgets
    }

    fn visit_node<'a>(&mut self, node: Node<'a>, widgets: &mut Vec<WidgetNode>) {
        let kind = node.kind();
        
        match kind {
            "call_expression" => {
                if let Some(widget) = self.map_call_to_widget(node) {
                    widgets.push(widget);
                    return;
                }
            }
            "property_declaration" => {
                self.handle_property_declaration(node);
                return;
            }
            "annotation" => {
                self.handle_annotation(node);
                return;
            }
            _ => {}
        }

        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            self.visit_node(child, widgets);
        }
    }

    fn handle_property_declaration<'a>(&mut self, node: Node<'a>) {
        let mut name = String::new();
        let mut val = String::new();
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            if child.kind() == "variable_declaration" {
                name = self.get_node_text(child);
            } else if child.kind() == "string_literal" {
                val = self.get_node_text(child).trim_matches('"').to_string();
            } else if child.kind() == "integer_literal" || child.kind() == "boolean_literal" {
                val = self.get_node_text(child);
            }
        }
        if !name.is_empty() {
            self.runtime.set_state(&name, &val);
            self.variables.insert(name, val);
        }
    }

    fn handle_annotation<'a>(&mut self, node: Node<'a>) {
        let text = self.get_node_text(node);
        if text.starts_with("@Import") {
            // Basic handling for now
        }
    }

    fn map_call_to_widget<'a>(&mut self, node: Node<'a>) -> Option<WidgetNode> {
        let mut name = String::new();
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            if child.kind() == "simple_identifier" {
                name = self.get_node_text(child);
            }
        }
        
        if name.is_empty() { return None; }
        
        let args = self.get_arguments(node);
        let style = self.extract_style(&args);

        match name.as_str() {
            "Text" => {
                Some(WidgetNode::Text {
                    text: self.resolve_val(args.get("text").cloned().unwrap_or_default()),
                    style,
                })
            }
            "Button" => {
                Some(WidgetNode::Button {
                    text: self.resolve_val(args.get("text").cloned().unwrap_or_default()),
                    action: args.get("action").cloned().unwrap_or_default(),
                    enabled: args.get("enabled").map(|v| v == "true"),
                    style,
                })
            }
            "Image" => {
                Some(WidgetNode::Image {
                    src: args.get("src").cloned().unwrap_or_default(),
                    alt: args.get("alt").cloned().unwrap_or_default(),
                    style,
                })
            }
            "Column" => {
                let mut children = Vec::new();
                if let Some(lambda) = self.get_lambda_body(node) {
                    self.visit_node(lambda, &mut children);
                }
                Some(WidgetNode::Column { children, style })
            }
            "Row" => {
                let mut children = Vec::new();
                if let Some(lambda) = self.get_lambda_body(node) {
                    self.visit_node(lambda, &mut children);
                }
                Some(WidgetNode::Row { children, style })
            }
            "Box" => {
                let mut children = Vec::new();
                if let Some(lambda) = self.get_lambda_body(node) {
                    self.visit_node(lambda, &mut children);
                }
                Some(WidgetNode::Box { children, style })
            }
            "Spacer" => {
                Some(WidgetNode::Spacer {
                    flex: args.get("flex").and_then(|v| v.parse().ok()).unwrap_or(1),
                })
            }
            "Scaffold" => {
                let mut children = Vec::new();
                if let Some(lambda) = self.get_lambda_body(node) {
                    self.visit_node(lambda, &mut children);
                }
                Some(WidgetNode::Scaffold {
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
                })
            }
            _ => None
        }
    }

    fn resolve_val(&self, val: String) -> String {
        if let Some(v) = self.variables.get(&val) {
            v.clone()
        } else {
            val
        }
    }

    fn get_node_text(&self, node: Node) -> String {
        self.source[node.start_byte()..node.end_byte()].to_string()
    }

    fn get_arguments<'a>(&self, node: Node<'a>) -> HashMap<String, String> {
        let mut args = HashMap::new();
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            if child.kind() == "call_suffix" {
                let mut suffix_cursor = child.walk();
                for suffix_child in child.children(&mut suffix_cursor) {
                    if suffix_child.kind() == "value_arguments" {
                        let mut arg_cursor = suffix_child.walk();
                        for arg in suffix_child.children(&mut arg_cursor) {
                            if arg.kind() == "value_argument" {
                                let mut key = String::new();
                                let mut val = String::new();
                                let mut item_cursor = arg.walk();
                                for item in arg.children(&mut item_cursor) {
                                    if item.kind() == "simple_identifier" {
                                        if key.is_empty() {
                                            key = self.get_node_text(item);
                                        } else {
                                            val = self.get_node_text(item);
                                        }
                                    } else if item.kind() == "string_literal" {
                                        val = self.get_node_text(item).trim_matches('"').to_string();
                                    } else if item.kind() == "integer_literal" || item.kind() == "boolean_literal" {
                                        val = self.get_node_text(item);
                                    }
                                }
                                if !key.is_empty() {
                                    args.insert(key, val);
                                }
                            }
                        }
                    }
                }
            }
        }
        args
    }

    fn get_lambda_body<'a>(&self, node: Node<'a>) -> Option<Node<'a>> {
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            if child.kind() == "call_suffix" {
                let mut suffix_cursor = child.walk();
                for suffix_child in child.children(&mut suffix_cursor) {
                    if suffix_child.kind() == "annotated_lambda" {
                        let mut lambda_cursor = suffix_child.walk();
                        for lambda_item in suffix_child.children(&mut lambda_cursor) {
                            if lambda_item.kind() == "lambda_literal" {
                                let mut lit_cursor = lambda_item.walk();
                                for lit_child in lambda_item.children(&mut lit_cursor) {
                                    if lit_child.kind() == "statements" {
                                        return Some(lit_child);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        None
    }

    fn extract_style(&self, args: &HashMap<String, String>) -> Option<WidgetStyle> {
        let mut style = WidgetStyle::default();
        let mut has_style = false;

        if let Some(color) = args.get("color") {
            style.color = Some(color.clone());
            has_style = true;
        }
        if let Some(fs) = args.get("fontSize").and_then(|v| v.parse().ok()) {
            style.font_size = Some(fs);
            has_style = true;
        }
        if let Some(p) = args.get("padding").and_then(|v| v.parse().ok()) {
            style.padding = Some(p);
            has_style = true;
        }
        if has_style { Some(style) } else { None }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use feather_core::AppRuntime;

    #[test]
    fn test_parse_simple_text() {
        let source = r#"Text(text = "Hello Feather")"#;
        let runtime = Arc::new(AppRuntime::new());
        let mut parser = SymbolicParser::new(source, runtime);
        let widgets = parser.parse_widgets();
        
        assert_eq!(widgets.len(), 1);
        if let WidgetNode::Text { text, .. } = &widgets[0] {
            assert_eq!(text, "Hello Feather");
        } else {
            panic!("Expected Text widget");
        }
    }

    #[test]
    fn test_parse_variable_resolution() {
        let source = r#"
            val myText = "Resolved Text"
            Text(text = myText)
        "#;
        let runtime = Arc::new(AppRuntime::new());
        let mut parser = SymbolicParser::new(source, runtime);
        let widgets = parser.parse_widgets();
        
        assert_eq!(widgets.len(), 1);
        if let WidgetNode::Text { text, .. } = &widgets[0] {
            assert_eq!(text, "Resolved Text");
        } else {
            panic!("Expected Text widget with resolved variable");
        }
    }
}

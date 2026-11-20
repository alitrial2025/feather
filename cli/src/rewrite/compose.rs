use super::{Screen, group, quote};
use tree_sitter::{Node, Parser};

pub fn translate(source: &str, component: &str) -> Result<Screen, String> {
    let mut parser = Parser::new();
    parser
        .set_language(&tree_sitter_kotlin::language())
        .map_err(|error| error.to_string())?;
    let tree = parser.parse(source, None).ok_or("Could not parse Kotlin")?;
    let ast = tree.root_node();
    if ast.has_error() {
        return Err("Kotlin source could not be parsed".into());
    }
    if !source
        .lines()
        .any(|line| line.trim_start().starts_with("import androidx.compose."))
    {
        return Err("Expected a Kotlin source file with Jetpack Compose imports".into());
    }
    let mut bodies = Vec::new();
    find_components(ast, source, component, &mut bodies);
    let [body] = bodies[..] else {
        return Err(format!("Expected exactly one function named {component:?}"));
    };
    let mut converter = Compose {
        source,
        screen: Screen::default(),
    };
    converter.screen.limitations.push("Scope: static Compose calls. Modifiers, state, navigation, and event handlers require migration.".into());
    let widgets = converter.content(body);
    if widgets.is_empty() {
        return Err("The selected Kotlin function has no directly convertible UI".into());
    }
    converter.screen.root = group("Column", widgets, "");
    Ok(converter.screen)
}

fn children(node: Node<'_>) -> impl Iterator<Item = Node<'_>> {
    (0..node.named_child_count()).filter_map(move |i| node.named_child(i))
}

fn find_components<'a>(node: Node<'a>, source: &str, name: &str, bodies: &mut Vec<Node<'a>>) {
    if node.kind() == "function_declaration"
        && children(node)
            .find(|child| child.kind() == "simple_identifier")
            .is_some_and(|child| &source[child.byte_range()] == name)
    {
        bodies.extend(children(node).filter(|child| child.kind() == "function_body"));
        return;
    }
    for child in children(node) {
        find_components(child, source, name, bodies);
    }
}

fn call_parts<'a>(node: Node<'a>, suffixes: &mut Vec<Node<'a>>) -> Option<Node<'a>> {
    let callee = node.named_child(0)?;
    let name = match callee.kind() {
        "simple_identifier" => callee,
        "call_expression" => call_parts(callee, suffixes)?,
        _ => return None,
    };
    suffixes.extend(children(node).filter(|child| child.kind() == "call_suffix"));
    Some(name)
}

struct Compose<'a> {
    source: &'a str,
    screen: Screen,
}

impl Compose<'_> {
    fn text(&self, node: Node) -> &str {
        &self.source[node.byte_range()]
    }

    fn issue(&mut self, node: Node, message: impl std::fmt::Display) {
        let pos = node.start_position();
        self.screen
            .limitations
            .push(format!("{}:{} {message}", pos.row + 1, pos.column + 1));
    }

    fn content(&mut self, node: Node) -> Vec<String> {
        match node.kind() {
            "call_expression" => vec![self.widget(node)],
            "function_body" | "statements" | "annotated_lambda" | "lambda_literal" => {
                children(node)
                    .flat_map(|child| self.content(child))
                    .collect()
            }
            "line_comment" | "multiline_comment" => Vec::new(),
            _ => {
                self.issue(node, "Kotlin statement requires manual migration");
                vec![]
            }
        }
    }

    fn widget(&mut self, node: Node) -> String {
        let mut suffixes = Vec::new();
        let Some(callee) = call_parts(node, &mut suffixes) else {
            self.issue(node, "Unsupported Compose call");
            return "Text(\"[unsupported Compose call]\")".into();
        };
        let name = self.text(callee).to_owned();
        let arguments: Vec<_> = suffixes
            .iter()
            .copied()
            .flat_map(children)
            .filter(|child| child.kind() == "value_arguments")
            .flat_map(children)
            .collect();
        let blocks: Vec<_> = suffixes
            .iter()
            .copied()
            .flat_map(children)
            .filter(|child| child.kind() == "annotated_lambda")
            .collect();
        match name.as_str() {
            "Text" => format!("Text({})", self.label(node, &arguments)),
            "Column" | "Row" | "Box" | "Surface" | "MaterialTheme" => {
                if !arguments.is_empty() || matches!(name.as_str(), "Surface" | "MaterialTheme") {
                    self.issue(
                        node,
                        format!("{name} layout modifiers and theme need migration"),
                    );
                }
                let target = match name.as_str() {
                    "Row" => "Row",
                    "Box" => "Box",
                    _ => "Column",
                };
                let widgets = blocks
                    .into_iter()
                    .flat_map(|block| self.content(block))
                    .collect::<Vec<_>>();
                group(target, widgets, "")
            }
            "Button" => {
                let label = blocks
                    .iter()
                    .flat_map(|block| children(*block))
                    .flat_map(children)
                    .flat_map(children)
                    .find(|child| child.kind() == "call_expression")
                    .and_then(|child| {
                        let mut suffix = Vec::new();
                        let callee = call_parts(child, &mut suffix)?;
                        if self.text(callee) != "Text" {
                            return None;
                        }
                        let args: Vec<_> = suffix
                            .into_iter()
                            .flat_map(children)
                            .filter(|child| child.kind() == "value_arguments")
                            .flat_map(children)
                            .collect();
                        Some(self.label(child, &args))
                    })
                    .unwrap_or_else(|| quote("Button"));
                self.issue(
                    node,
                    "Compose Button is disabled until its listener is migrated",
                );
                format!("Button({label}, \"\", enabled = false)")
            }
            _ => {
                self.issue(
                    node,
                    format!("Compose call {name} requires manual migration"),
                );
                format!(
                    "Text({})",
                    quote(&format!("Unsupported Compose call: {name}"))
                )
            }
        }
    }

    fn label(&mut self, node: Node, arguments: &[Node]) -> String {
        let named = |arg: Node| {
            (0..arg.child_count())
                .filter_map(|index| arg.child(index))
                .any(|child| child.kind() == "=")
        };
        let label = arguments
            .iter()
            .copied()
            .find(|arg| {
                named(*arg)
                    && arg
                        .named_child(0)
                        .is_some_and(|key| self.text(key) == "text")
            })
            .or_else(|| arguments.first().copied().filter(|arg| !named(*arg)));
        if arguments.len() > 1 {
            self.issue(node, "Text modifiers need migration");
        }
        let value = label.and_then(|arg| {
            let value = arg.named_child(if named(arg) { 1 } else { 0 })?;
            let raw = self.text(value);
            if value.kind() == "string_literal" && !raw.contains('$') {
                serde_json::from_str::<String>(raw).ok()
            } else {
                None
            }
        });
        if let Some(value) = value {
            quote(&value)
        } else {
            self.issue(node, "Expected literal Compose Text");
            quote("[dynamic text]")
        }
    }
}

use super::{Screen, group, quote};
use std::collections::HashMap;
use tree_sitter::{Node, Parser};

pub fn translate(source: &str, component: Option<&str>) -> Result<Screen, String> {
    let mut parser = Parser::new();
    parser
        .set_language(&tree_sitter_typescript::language_tsx())
        .map_err(|error| error.to_string())?;
    let tree = parser
        .parse(source, None)
        .ok_or("Could not parse the React Native source")?;
    let ast = tree.root_node();
    if ast.has_error() {
        return Err("Source could not be parsed as JSX/TSX".into());
    }
    let scope = if let Some(name) = component {
        let mut candidates = Vec::new();
        collect_components(ast, source, name, &mut candidates);
        let [node] = candidates[..] else {
            return Err(format!(
                "Expected exactly one React component named {name:?}"
            ));
        };
        node
    } else {
        ast
    };
    let mut widget_roots = Vec::new();
    let primary = scope
        .child_by_field_name("body")
        .filter(|body| body.kind() == "statement_block")
        .and_then(|body| {
            let returns = children(body)
                .filter(|child| child.kind() == "return_statement")
                .collect::<Vec<_>>();
            if let [single] = returns[..] {
                Some(single)
            } else {
                None
            }
        });
    collect_roots(primary.unwrap_or(scope), &mut widget_roots);
    let [root] = widget_roots[..] else {
        return Err("Select a file or --component containing exactly one outer JSX tree".into());
    };
    let mut importer = Importer {
        source,
        ast,
        root,
        locals: HashMap::new(),
        component_depth: 0,
        screen: Screen::default(),
        imports: HashMap::new(),
        states: HashMap::new(),
        styles: HashMap::new(),
        uses_named_state_hook: false,
    };
    for statement in children(ast).filter(|node| node.kind() == "import_statement") {
        let Some(package) = statement
            .child_by_field_name("source")
            .and_then(|node| importer.literal(node))
        else {
            continue;
        };
        importer.collect_imports(statement, &package);
    }
    importer.collect_styles(ast);
    let mut parent = root.parent();
    while let Some(node) = parent {
        if matches!(
            node.kind(),
            "function_declaration" | "function_expression" | "arrow_function"
        ) {
            importer.collect_state(node);
            break;
        }
        parent = node.parent();
    }
    importer.screen.limitations.push("Scope: one JSX screen. Navigation, effects, business logic and native dependencies require migration.".into());
    if primary.is_some() {
        let mut alternatives = Vec::new();
        collect_roots(scope, &mut alternatives);
        if alternatives.len() > 1 {
            importer.issue(
                root,
                "Using the main return. Conditional and nested JSX trees require review.",
            );
        }
    }
    importer.screen.root = importer.widget(root);
    Ok(importer.screen)
}

fn children(node: Node<'_>) -> impl Iterator<Item = Node<'_>> {
    (0..node.named_child_count()).filter_map(move |index| node.named_child(index))
}

fn jsx(node: Node) -> bool {
    matches!(node.kind(), "jsx_element" | "jsx_self_closing_element")
}

fn collect_roots<'a>(node: Node<'a>, roots: &mut Vec<Node<'a>>) {
    if jsx(node) {
        roots.push(node);
        return;
    }
    for child in children(node) {
        collect_roots(child, roots);
    }
}

fn collect_components<'a>(node: Node<'a>, source: &str, name: &str, found: &mut Vec<Node<'a>>) {
    let named = node
        .child_by_field_name("name")
        .is_some_and(|id| &source[id.byte_range()] == name);
    if named {
        match node.kind() {
            "function_declaration" => {
                found.push(node);
                return;
            }
            "variable_declarator" => {
                if let Some(function) = node.child_by_field_name("value").filter(|value| {
                    matches!(value.kind(), "arrow_function" | "function_expression")
                }) {
                    found.push(function);
                    return;
                }
            }
            _ => {}
        }
    }
    for child in children(node) {
        collect_components(child, source, name, found);
    }
}

#[derive(Clone)]
struct State {
    key: String,
    setter: String,
    numeric: bool,
}

struct Importer<'a, 'tree> {
    source: &'a str,
    ast: Node<'tree>,
    root: Node<'tree>,
    locals: HashMap<String, Node<'tree>>,
    component_depth: usize,
    screen: Screen,
    imports: HashMap<String, String>,
    states: HashMap<String, State>,
    styles: HashMap<(String, String), Node<'tree>>,
    uses_named_state_hook: bool,
}

fn normalize_color(color: &str) -> Option<String> {
    if color == "transparent" {
        return Some("#00000000".into());
    }
    if let Some(hex) = color.strip_prefix('#') {
        if !hex.bytes().all(|byte| byte.is_ascii_hexdigit()) {
            return None;
        }
        return match hex.len() {
            3 => Some(format!(
                "#{}",
                hex.chars().flat_map(|ch| [ch, ch]).collect::<String>()
            )),
            4 => Some(format!(
                "#{}",
                hex.chars()
                    .cycle()
                    .skip(3)
                    .take(4)
                    .flat_map(|ch| [ch, ch])
                    .collect::<String>()
            )),
            6 => Some(color.into()),
            8 => Some(format!("#{}{}", &hex[6..], &hex[..6])),
            _ => None,
        };
    }
    let (alpha, args) = if let Some(args) = color.strip_prefix("rgba(") {
        (true, args)
    } else {
        (false, color.strip_prefix("rgb(")?)
    };
    let values = args
        .strip_suffix(')')?
        .split(',')
        .map(str::trim)
        .collect::<Vec<_>>();
    if values.len() != if alpha { 4 } else { 3 } {
        return None;
    }
    let r = values[0].parse::<u8>().ok()?;
    let g = values[1].parse::<u8>().ok()?;
    let b = values[2].parse::<u8>().ok()?;
    if alpha {
        let a = values[3].parse::<f64>().ok()?;
        if !(0.0..=1.0).contains(&a) {
            return None;
        }
        Some(format!(
            "#{:02X}{r:02X}{g:02X}{b:02X}",
            (a * 255.0).round() as u8
        ))
    } else {
        Some(format!("#{r:02X}{g:02X}{b:02X}"))
    }
}

impl<'tree> Importer<'_, 'tree> {
    fn text(&self, node: Node) -> &str {
        &self.source[node.byte_range()]
    }

    fn issue(&mut self, node: Node, message: impl std::fmt::Display) {
        let point = node.start_position();
        self.screen
            .limitations
            .push(format!("{}:{} {message}", point.row + 1, point.column + 1));
    }

    fn literal(&self, node: Node) -> Option<String> {
        match node.kind() {
            "string" => {
                let raw = self.text(node);
                if raw.starts_with('"') {
                    return serde_json::from_str(raw).ok();
                }
                let value = raw.strip_prefix('\'')?.strip_suffix('\'')?;
                if value.contains('\\') {
                    return None;
                }
                Some(value.into())
            }
            "number" if self.text(node).parse::<i32>().is_ok() => {
                Some(self.text(node).parse::<i32>().ok()?.to_string())
            }
            _ => None,
        }
    }

    fn collect_imports(&mut self, node: Node, package: &str) {
        if node.kind() == "import_clause" && package == "react-native-reanimated" {
            if let Some(default) = node
                .named_child(0)
                .filter(|child| child.kind() == "identifier")
            {
                self.register_animated(self.text(default).to_owned());
            }
        }
        if node.kind() == "import_specifier" {
            if let Some(name) = node.child_by_field_name("name") {
                let symbol = self.text(name).to_owned();
                let local = node
                    .child_by_field_name("alias")
                    .map(|alias| self.text(alias))
                    .unwrap_or(&symbol)
                    .to_owned();
                if package == "react-native" && symbol == "Animated" {
                    self.register_animated(local);
                } else if package == "react-native" {
                    self.imports.insert(local, symbol);
                } else if package == "lucide-react-native" {
                    let icon = match symbol.as_str() {
                        "Sparkles" | "Star" => Some("star"),
                        "Zap" => Some("play"),
                        "Music" => Some("music"),
                        "Mic2" => Some("mic"),
                        "ChevronLeft" | "ArrowLeft" => Some("back"),
                        _ => None,
                    };
                    if let Some(icon) = icon {
                        self.imports.insert(local, format!("icon:{icon}"));
                    }
                } else if package == "expo-linear-gradient" && symbol == "LinearGradient" {
                    self.imports.insert(local, "Decoration".into());
                } else if package == "react" && symbol == "useState" && local == "useState" {
                    self.uses_named_state_hook = true;
                }
            }
        }
        for child in children(node) {
            self.collect_imports(child, package);
        }
    }

    fn register_animated(&mut self, local: String) {
        self.imports
            .insert(format!("{local}.View"), "AnimatedView".into());
        self.imports
            .insert(format!("{local}.ScrollView"), "AnimatedScrollView".into());
    }

    fn collect_styles(&mut self, root: Node<'tree>) {
        for declaration in children(root)
            .filter(|node| node.kind() == "lexical_declaration")
            .flat_map(children)
        {
            let Some(name) = declaration
                .child_by_field_name("name")
                .filter(|node| node.kind() == "identifier")
            else {
                continue;
            };
            let Some(call) = declaration
                .child_by_field_name("value")
                .filter(|node| node.kind() == "call_expression")
            else {
                continue;
            };
            let Some(function) = call
                .child_by_field_name("function")
                .filter(|node| node.kind() == "member_expression")
            else {
                continue;
            };
            if function
                .child_by_field_name("object")
                .and_then(|node| self.imports.get(self.text(node)))
                .map(String::as_str)
                != Some("StyleSheet")
                || !function
                    .child_by_field_name("property")
                    .is_some_and(|node| self.text(node) == "create")
            {
                continue;
            }
            let Some(object) = call
                .child_by_field_name("arguments")
                .and_then(|node| node.named_child(0))
                .filter(|node| node.kind() == "object")
            else {
                continue;
            };
            let sheet = self.text(name).to_owned();
            for rule in children(object) {
                let Some(key) = rule
                    .child_by_field_name("key")
                    .filter(|node| node.kind() == "property_identifier")
                else {
                    continue;
                };
                let Some(value) = rule
                    .child_by_field_name("value")
                    .filter(|node| node.kind() == "object")
                else {
                    continue;
                };
                self.styles
                    .insert((sheet.clone(), self.text(key).to_owned()), value);
            }
        }
    }

    fn collect_state(&mut self, function: Node) {
        if !self.uses_named_state_hook {
            return;
        }
        let Some(body) = function.child_by_field_name("body") else {
            return;
        };
        // Only consider declarations in the selected component's immediate function body.
        for declaration in children(body)
            .filter(|node| matches!(node.kind(), "lexical_declaration" | "variable_declaration"))
            .flat_map(children)
        {
            let Some(binding) = declaration
                .child_by_field_name("name")
                .filter(|node| node.kind() == "array_pattern")
            else {
                continue;
            };
            let Some(call) = declaration
                .child_by_field_name("value")
                .filter(|node| node.kind() == "call_expression")
            else {
                continue;
            };
            if !call
                .child_by_field_name("function")
                .is_some_and(|node| self.text(node) == "useState")
            {
                continue;
            }
            let names = children(binding).collect::<Vec<_>>();
            let [name, setter] = names[..] else { continue };
            if name.kind() != "identifier" || setter.kind() != "identifier" {
                continue;
            }
            let Some(initial) = call
                .child_by_field_name("arguments")
                .and_then(|args| args.named_child(0))
            else {
                continue;
            };
            let Some(value) = self.literal(initial) else {
                self.issue(
                    initial,
                    "Only literal string and integer useState initializers are supported",
                );
                continue;
            };
            let key = quote(&format!("rewrite-state:{}", self.text(name)));
            let setting = format!(
                "if (getState({key}) == null) setStateText({key}, {})",
                quote(&value)
            );
            let state = State {
                key,
                setter: self.text(setter).into(),
                numeric: initial.kind() == "number",
            };
            self.states.insert(self.text(name).into(), state);
            self.screen.initializers.push(setting);
        }
    }

    fn bound_value(&self, node: Node<'tree>) -> Node<'tree> {
        let node = if node.kind() == "jsx_expression" {
            node.named_child(0).unwrap_or(node)
        } else {
            node
        };
        if node.kind() == "identifier" {
            return self.locals.get(self.text(node)).copied().unwrap_or(node);
        }
        if node.kind() == "member_expression" {
            if let (Some(object), Some(property)) = (
                node.child_by_field_name("object"),
                node.child_by_field_name("property"),
            ) {
                if let Some(bound) = self.locals.get(self.text(object)) {
                    return self
                        .object_field(*bound, self.text(property))
                        .unwrap_or(node);
                }
            }
        }
        node
    }

    fn object_field(&self, object: Node<'tree>, field: &str) -> Option<Node<'tree>> {
        if object.kind() != "object" {
            return None;
        }
        children(object)
            .filter(|pair| pair.kind() == "pair")
            .find_map(|pair| {
                let key = pair.child_by_field_name("key")?;
                if self.text(key) == field {
                    pair.child_by_field_name("value")
                } else {
                    None
                }
            })
    }

    fn static_map(&mut self, expression: Node<'tree>) -> Option<Vec<String>> {
        let call = expression
            .named_child(0)
            .filter(|node| node.kind() == "call_expression")?;
        let function = call
            .child_by_field_name("function")
            .filter(|node| node.kind() == "member_expression")?;
        if self.text(function.child_by_field_name("property")?) != "map" {
            return None;
        }
        let array_name = function
            .child_by_field_name("object")
            .filter(|node| node.kind() == "identifier")?;
        let declaration = children(self.ast)
            .filter(|node| {
                node.kind() == "lexical_declaration" && self.text(*node).starts_with("const ")
            })
            .flat_map(children)
            .find(|node| {
                node.child_by_field_name("name")
                    .is_some_and(|name| self.text(name) == self.text(array_name))
            })?;
        let array = declaration
            .child_by_field_name("value")
            .filter(|node| node.kind() == "array")?;
        let rows: Vec<_> = children(array).collect();
        if rows.len() > 128 || rows.iter().any(|row| row.kind() != "object") {
            return None;
        }
        let args = call.child_by_field_name("arguments")?;
        if args.named_child_count() != 1 {
            return None;
        }
        let callback = args
            .named_child(0)
            .filter(|node| node.kind() == "arrow_function")?;
        let parameter = callback
            .child_by_field_name("parameter")
            .or_else(|| {
                let params = callback.child_by_field_name("parameters")?;
                if params.named_child_count() != 1 {
                    return None;
                }
                let parameter = params.named_child(0)?;
                parameter.child_by_field_name("pattern").or(Some(parameter))
            })
            .filter(|node| node.kind() == "identifier")?;
        let mut body = callback.child_by_field_name("body")?;
        while body.kind() == "parenthesized_expression" {
            body = body.named_child(0)?;
        }
        if !jsx(body) {
            return None;
        }
        let key = self.text(parameter).to_owned();
        let previous = self.locals.remove(&key);
        let mut widgets = Vec::new();
        for row in rows {
            self.locals.insert(key.clone(), row);
            widgets.push(self.widget(body));
        }
        self.locals.remove(&key);
        if let Some(previous) = previous {
            self.locals.insert(key, previous);
        }
        Some(widgets)
    }

    fn local_component(&mut self, opening: Node<'tree>, tag: &str) -> Option<String> {
        if self.component_depth >= 8 {
            return None;
        }
        let mut candidates = Vec::new();
        collect_components(self.ast, self.source, tag, &mut candidates);
        let [component] = candidates[..] else {
            return None;
        };
        let params = component.child_by_field_name("parameters")?;
        if params.named_child_count() != 1 {
            return None;
        }
        let parameter = params.named_child(0)?;
        let pattern = parameter
            .child_by_field_name("pattern")
            .unwrap_or(parameter);
        if pattern.kind() != "object_pattern" {
            return None;
        }
        let mut locals = HashMap::new();
        for binding in children(pattern) {
            if binding.kind() != "shorthand_property_identifier_pattern" {
                return None;
            }
            let key = self.text(binding);
            let value = children(opening)
                .filter(|attr| attr.kind() == "jsx_attribute")
                .find(|attr| {
                    attr.named_child(0)
                        .is_some_and(|name| self.text(name) == key)
                })?
                .named_child(1)?;
            locals.insert(key.to_owned(), self.bound_value(value));
        }
        let mut body = component.child_by_field_name("body")?;
        while body.kind() == "parenthesized_expression" {
            body = body.named_child(0)?;
        }
        if !jsx(body) {
            return None;
        }
        let previous = std::mem::replace(&mut self.locals, locals);
        self.component_depth += 1;
        let widget = self.widget(body);
        self.component_depth -= 1;
        self.locals = previous;
        Some(widget)
    }

    fn expression(&mut self, node: Node<'tree>) -> String {
        let bound = self.bound_value(node);
        if let Some(value) = self.literal(bound) {
            return quote(&value);
        }
        if node.kind() == "identifier" {
            if let Some(state) = self.states.get(self.text(node)) {
                return format!("(getState({}) ?: \"\")", state.key);
            }
        }
        self.issue(node, "Dynamic expression requires manual migration");
        quote("[dynamic value]")
    }

    fn fragment_text(&mut self, node: Node<'tree>) -> Vec<String> {
        match node.kind() {
            "jsx_text" => {
                let mut value = self.text(node).to_owned();
                if value.contains('\n') {
                    value = value
                        .lines()
                        .map(str::trim)
                        .filter(|line| !line.is_empty())
                        .collect::<Vec<_>>()
                        .join(" ");
                }
                if value.is_empty() {
                    vec![]
                } else {
                    vec![quote(&value)]
                }
            }
            "html_character_reference" => {
                let raw = self.text(node);
                let value = match raw {
                    "&amp;" => "&",
                    "&lt;" => "<",
                    "&gt;" => ">",
                    "&quot;" => "\"",
                    "&apos;" | "&#39;" => "'",
                    _ => raw,
                };
                vec![quote(value)]
            }
            "jsx_expression" => node
                .named_child(0)
                .filter(|node| node.kind() != "comment")
                .map(|child| vec![self.expression(child)])
                .unwrap_or_default(),
            "jsx_element"
                if node
                    .child_by_field_name("open_tag")
                    .and_then(|tag| tag.child_by_field_name("name"))
                    .and_then(|name| self.imports.get(self.text(name)))
                    .is_some_and(|name| name == "Text") =>
            {
                self.issue(
                    node,
                    "Nested Text is flattened; nested styles are not retained",
                );
                children(node)
                    .filter(|child| {
                        !matches!(child.kind(), "jsx_opening_element" | "jsx_closing_element")
                    })
                    .flat_map(|child| self.fragment_text(child))
                    .collect()
            }
            _ => {
                self.issue(node, "Unsupported content inside Text");
                vec![]
            }
        }
    }

    fn action(&mut self, handler: Option<Node>, input: bool) -> Option<String> {
        let expr = handler?.named_child(0)?;
        let statement = if input {
            let setter = self.text(expr);
            let state = self
                .states
                .values()
                .find(|s| !s.numeric && s.setter == setter)?;
            format!("setStateText({}, actionText(payload))", state.key)
        } else {
            let call = expr
                .child_by_field_name("body")
                .filter(|body| body.kind() == "call_expression")?;
            let setter = self.text(call.child_by_field_name("function")?);
            let (name, state) = self.states.iter().find(|(_, s)| s.setter == setter)?;
            let args = call.child_by_field_name("arguments")?;
            if args.named_child_count() != 1 {
                return None;
            }
            let argument = args.named_child(0)?;
            let value = if let Some(value) = self.literal(argument) {
                if state.numeric != (argument.kind() == "number") {
                    return None;
                }
                quote(&value)
            } else {
                if !state.numeric
                    || argument.kind() != "binary_expression"
                    || self.text(argument.child_by_field_name("left")?) != name
                    || self.text(argument.child_by_field_name("operator")?) != "+"
                {
                    return None;
                }
                let delta = self
                    .text(argument.child_by_field_name("right")?)
                    .parse::<u16>()
                    .ok()?;
                format!(
                    "((getState({})?.toLongOrNull() ?: 0L) + {delta}L).toString()",
                    state.key
                )
            };
            format!("setStateText({}, {value})", state.key)
        };
        Some(self.screen.action(statement))
    }

    fn style(&mut self, node: Node, container: bool) -> (Vec<String>, bool) {
        let Some(value) = node.named_child(0) else {
            return (vec![], false);
        };
        self.style_value(value, container)
    }

    fn style_value(&mut self, node: Node, container: bool) -> (Vec<String>, bool) {
        if node.kind() == "array" {
            let mut row = false;
            let mut fields: Vec<String> = Vec::new();
            for item in children(node) {
                let (part, is_row) = self.style_value(item, container);
                row |= is_row;
                for new in part {
                    let property = new.split_once(" = ").map(|(key, _)| key);
                    fields.retain(|old| old.split_once(" = ").map(|(key, _)| key) != property);
                    fields.push(new);
                }
            }
            return (fields, row);
        }
        let object = match node.kind() {
            "object" => Some(node),
            "member_expression" => (|| {
                let sheet = self.text(node.child_by_field_name("object")?).to_owned();
                let name = self.text(node.child_by_field_name("property")?).to_owned();
                self.styles.get(&(sheet, name)).copied()
            })(),
            _ => None,
        };
        let Some(object) = object else {
            self.issue(
                node,
                "Only inline literals and file-scope StyleSheet rules are supported",
            );
            return (vec![], false);
        };
        let mut fields = Vec::new();
        let mut row = false;
        for pair in children(object) {
            let key = pair.child_by_field_name("key").map(|key| self.text(key));
            let value = pair
                .child_by_field_name("value")
                .and_then(|value| self.literal(value));
            match (key, value.as_deref(), container) {
                (Some("flexDirection"), Some("row" | "column"), true) => {
                    row = value.as_deref() == Some("row")
                }
                (Some("backgroundColor"), Some(color), true) => {
                    if let Some(color) = normalize_color(color) {
                        fields.push(format!("background = {}", quote(&color)));
                    } else {
                        self.issue(pair, "Unsupported CSS color");
                    }
                }
                (Some("color"), Some(color), false) => {
                    if let Some(color) = normalize_color(color) {
                        fields.push(format!("color = {}", quote(&color)));
                    } else {
                        self.issue(pair, "Unsupported CSS color");
                    }
                }
                (Some("fontWeight"), Some(weight), false) => {
                    fields.push(format!("fontWeight = {}", quote(weight)))
                }
                (
                    Some(
                        key @ ("padding" | "paddingHorizontal" | "paddingVertical" | "gap"
                        | "borderRadius" | "paddingTop" | "paddingBottom"),
                    ),
                    Some(value),
                    true,
                ) if value.parse::<u16>().is_ok() => {
                    let key = match key {
                        "gap" => "spacing",
                        "borderRadius" => "cornerRadius",
                        _ => key,
                    };
                    fields.push(format!("{key} = {value}"));
                }
                (Some(key @ ("width" | "height")), Some(value), true)
                    if value.parse::<u16>().is_ok() || value == "100%" =>
                {
                    let size = if value == "100%" { "fill" } else { value };
                    fields.push(format!("{key} = {}", quote(size)));
                }
                (Some("overflow"), Some("hidden"), true) => fields.push("clip = true".into()),
                (Some("alignItems"), Some("center"), true) => {
                    fields.push("alignment = \"center\"".into())
                }
                (Some("fontSize"), Some(value), false) if value.parse::<u16>().is_ok() => {
                    fields.push(format!("fontSize = {value}"))
                }
                _ => self.issue(pair, "Style property requires manual migration"),
            }
        }
        (fields, row)
    }

    fn content_widgets(&mut self, children: &[Node<'tree>]) -> Vec<String> {
        let mut widgets = Vec::new();
        for &child in children {
            if jsx(child) {
                widgets.push(self.widget(child));
            } else if child.kind() == "jsx_expression" {
                if let Some(items) = self.static_map(child) {
                    widgets.extend(items);
                } else {
                    let bound = self.bound_value(child);
                    if jsx(bound) {
                        widgets.push(self.widget(bound));
                    } else {
                        widgets.extend(
                            self.fragment_text(child)
                                .into_iter()
                                .map(|part| format!("Text({part})")),
                        );
                    }
                }
            } else if child.kind() == "jsx_text" && !self.text(child).trim().is_empty() {
                self.issue(child, "Text outside a Text component was wrapped");
                widgets.push(format!("Text({})", quote(self.text(child).trim())));
            } else if child.kind() == "html_character_reference" {
                widgets.extend(
                    self.fragment_text(child)
                        .into_iter()
                        .map(|part| format!("Text({part})")),
                );
            }
        }
        widgets
    }

    fn widget(&mut self, node: Node<'tree>) -> String {
        let opening = node.child_by_field_name("open_tag").unwrap_or(node);
        let name = opening.child_by_field_name("name");
        let kind = name
            .and_then(|name| self.imports.get(self.text(name)))
            .map(String::as_str)
            .unwrap_or(if name.is_none() { "View" } else { "Unknown" })
            .to_owned();
        let kind = match kind.as_str() {
            "AnimatedView" | "AnimatedScrollView" => {
                self.issue(node, "Animated container uses a static Feather layout; animation and scroll callbacks require migration");
                if kind == "AnimatedView" {
                    "View"
                } else {
                    "ScrollView"
                }
            }
            _ => kind.as_str(),
        };
        if kind == "Unknown" {
            if let Some(tag) = name.map(|name| self.text(name).to_owned()) {
                if let Some(widget) = self.local_component(opening, &tag) {
                    return widget;
                }
            }
        }
        if let Some(icon) = kind.strip_prefix("icon:") {
            let color = children(opening)
                .filter(|attr| attr.kind() == "jsx_attribute")
                .find(|attr| {
                    attr.named_child(0)
                        .is_some_and(|key| self.text(key) == "color")
                })
                .and_then(|attr| attr.named_child(1))
                .map(|value| self.bound_value(value))
                .and_then(|value| self.literal(value))
                .and_then(|color| normalize_color(&color));
            return if let Some(color) = color {
                format!("Icon({}, color = {})", quote(icon), quote(&color))
            } else {
                format!("Icon({})", quote(icon))
            };
        }
        if kind == "Decoration" {
            self.issue(node, "Gradient decoration omitted from the Feather preview");
            return "Box(listOf())".into();
        }
        let mut attrs = HashMap::new();
        let allowed: &[&str] = match kind {
            "Text" | "View" | "SafeAreaView" => &["style"],
            "ScrollView" => &["style", "contentContainerStyle"],
            "Image" => &["source", "style"],
            "Pressable" | "TouchableOpacity" => &["onPress", "style", "key"],
            "Button" => &["title", "onPress"],
            "TextInput" => &["value", "placeholder", "onChangeText"],
            _ => &[],
        };
        for attr in children(opening)
            .filter(|node| matches!(node.kind(), "jsx_attribute" | "jsx_expression"))
        {
            let Some(key) = attr.named_child(0).map(|key| self.text(key).to_owned()) else {
                continue;
            };
            if allowed.contains(&key.as_str()) {
                if let Some(value) = attr.named_child(1) {
                    attrs.insert(key, value);
                } else {
                    self.issue(attr, "Expected a property value");
                }
            } else {
                self.issue(attr, format!("Property {key} requires manual migration"));
            }
        }
        let content = children(node)
            .filter(|child| !matches!(child.kind(), "jsx_opening_element" | "jsx_closing_element"))
            .filter(|_| jsx(node) && node.kind() == "jsx_element")
            .collect::<Vec<_>>();
        let mut label = || {
            let parts = content
                .iter()
                .flat_map(|child| self.fragment_text(*child))
                .collect::<Vec<_>>();
            if parts.is_empty() {
                quote("")
            } else {
                parts.join(" + ")
            }
        };
        match kind {
            "Text" => {
                let label = label();
                let style = attrs
                    .get("style")
                    .map(|style| self.style(*style, false).0)
                    .unwrap_or_default();
                if style.is_empty() {
                    format!("Text({label})")
                } else {
                    format!("Text({label}, style = TextStyle({}))", style.join(", "))
                }
            }
            "Image" => {
                let uri = attrs
                    .get("source")
                    .and_then(|source| source.named_child(0))
                    .and_then(|object| self.object_field(object, "uri"))
                    .and_then(|value| self.literal(self.bound_value(value)))
                    .filter(|url| url.starts_with("https://"));
                if let Some(uri) = uri {
                    format!(
                        "Image({}, style = ImageStyle(width = \"fill\", height = \"156\", contentMode = \"crop\"))",
                        quote(&uri)
                    )
                } else {
                    self.issue(
                        node,
                        "Image needs a literal HTTPS URI or a migrated local asset",
                    );
                    "Box(listOf())".into()
                }
            }
            "Pressable" | "TouchableOpacity" => {
                let widgets = self.content_widgets(&content);
                let single_icon = widgets.len() == 1 && widgets[0].starts_with("Icon(");
                let body = if single_icon {
                    group(
                        "Box",
                        widgets,
                        ", style = ContainerStyle(width = \"40\", height = \"40\", alignment = \"center\")",
                    )
                } else {
                    group(
                        "Column",
                        widgets,
                        ", style = ContainerStyle(padding = 12, spacing = 10, cornerRadius = 12, background = \"#243447\", fillMaxWidth = true)",
                    )
                };
                if let Some(action) = self.action(attrs.get("onPress").copied(), false) {
                    format!("Clickable({action}, {body})")
                } else {
                    self.issue(
                        node,
                        format!(
                            "{kind} handler requires manual migration; its content is retained"
                        ),
                    );
                    body
                }
            }
            "Button" => {
                let label = self.property(attrs.get("title").copied());
                let action = self.action(attrs.get("onPress").copied(), false);
                if let Some(action) = action {
                    format!("Button({label}, {action})")
                } else {
                    self.issue(
                        node,
                        format!("{kind} is disabled until its handler is migrated"),
                    );
                    format!("Button({label}, \"\", enabled = false)")
                }
            }
            "TextInput" => {
                let value = self.property(attrs.get("value").copied());
                let placeholder = self.property(attrs.get("placeholder").copied());
                if let Some(action) = self.action(attrs.get("onChangeText").copied(), true) {
                    format!(
                        "TextInput(value = {value}, placeholder = {placeholder}, action = {action})"
                    )
                } else {
                    self.issue(node, "TextInput requires a string useState setter");
                    format!("Text({placeholder})")
                }
            }
            _ => {
                let mut parts = Vec::new();
                if !matches!(kind, "View" | "SafeAreaView" | "ScrollView") {
                    let tag = name
                        .map(|name| self.text(name))
                        .unwrap_or("component")
                        .to_owned();
                    self.issue(
                        node,
                        format!("Component {tag} requires a Feather equivalent"),
                    );
                    parts.push(format!(
                        "Text({})",
                        quote(&format!("Unsupported component: {tag}"))
                    ));
                }
                parts.extend(self.content_widgets(&content));
                let (mut style, row) = attrs
                    .get("contentContainerStyle")
                    .or_else(|| attrs.get("style"))
                    .map(|style| self.style(*style, true))
                    .unwrap_or_default();
                if node == self.root {
                    style.push("fillMaxWidth = true".into());
                    style.push("fillMaxHeight = true".into());
                }
                if kind == "ScrollView" && !row {
                    parts = vec![group(
                        "Column",
                        parts,
                        ", style = ContainerStyle(spacing = 16, fillMaxWidth = true)",
                    )];
                }
                let container_style = if style.is_empty() {
                    String::new()
                } else {
                    format!(", style = ContainerStyle({})", style.join(", "))
                };
                let widget = if kind == "ScrollView" {
                    "ScrollView"
                } else if row {
                    "Row"
                } else {
                    "Column"
                };
                group(widget, parts, &container_style)
            }
        }
    }

    fn property(&mut self, node: Option<Node<'tree>>) -> String {
        match node {
            Some(node) if node.kind() == "jsx_expression" => node
                .named_child(0)
                .map(|value| self.expression(value))
                .unwrap_or_else(|| quote("")),
            Some(node) => {
                // JSX literals do not interpret backslash escapes.
                let text = self.text(node);
                if text.len() >= 2 {
                    quote(&text[1..text.len() - 1])
                } else {
                    quote("")
                }
            }
            None => quote(""),
        }
    }
}

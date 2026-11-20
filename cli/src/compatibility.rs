use crate::{bundler::javascript_document, compiler::compile_javascript, config::DevServerConfig};
use feather_core::AppRuntime;
use serde_json::Value;
use std::path::PathBuf;

const PROTOCOL_VERSION: u64 = 1;

const SHARED: &str = "AspectRatio AudioPlayer BottomTabs Box Button Card Checkbox CircularProgress \
    Clickable Column Divider FloatingActionButton Icon IconButton Image LazyColumn LazyGrid LazyRow \
    LinearProgress Navigator Row Scaffold ScrollView SearchBar Slider Spacer Surface Switch Text \
    TextInput TopAppBar VideoPlayer WebView";
const ANDROID_ONLY: &str = "Custom GestureDetector";
const IOS_ONLY: &str = "Badge BottomAppBar BottomSheet Chip Condition Dialog Drawer DropdownMenu \
    ForEach LongPressable Pager Placeholder PullToRefresh RadioGroup Snackbar TabRow Tooltip";

pub fn inspect(project: PathBuf, actions: Vec<String>) -> Result<(), String> {
    let config = DevServerConfig::new(project, std::net::Ipv4Addr::LOCALHOST.into(), 8080, false)?;
    if config.manifest.runtime.as_deref() != Some("kotlin-js") {
        return Err("inspect requires a project with the kotlin-js runtime".into());
    }
    compile_javascript(&config.project_path)?;
    let runtime = AppRuntime::new();
    check_screen(&config, &runtime, "initial screen")?;
    for action in actions {
        runtime.apply_action_event(&serde_json::json!({ "action": action }).to_string());
        check_screen(&config, &runtime, &format!("after {action:?}"))?;
    }
    println!("JSON protocol {PROTOCOL_VERSION}: checked widget types on Android and iOS");
    Ok(())
}

fn check_screen(config: &DevServerConfig, runtime: &AppRuntime, label: &str) -> Result<(), String> {
    let document: Value = serde_json::from_str(&javascript_document(config, runtime)?)
        .map_err(|error| error.to_string())?;
    if document["protocol_version"].as_u64() != Some(PROTOCOL_VERSION) {
        return Err(format!(
            "Unsupported JSON protocol version: {}",
            document["protocol_version"]
        ));
    }
    let mut issues = Vec::new();
    if let Some(diagnostics) = document["diagnostics"].as_array() {
        issues.extend(
            diagnostics
                .iter()
                .filter_map(|event| event["message"].as_str().map(str::to_owned)),
        );
    }
    inspect_widget(&document["root"], "root", &mut issues);
    if issues.is_empty() {
        println!("{label}: no incompatible widget types found");
        Ok(())
    } else {
        Err(format!("{label}\n{}", issues.join("\n")))
    }
}

fn inspect_widget(widget: &Value, path: &str, issues: &mut Vec<String>) {
    let Some(kind) = widget["type"].as_str() else {
        issues.push(format!("{path}: expected a widget with a type"));
        return;
    };
    if !SHARED.split_whitespace().any(|name| name == kind) {
        let missing = if ANDROID_ONLY.split_whitespace().any(|name| name == kind) {
            "iOS"
        } else if IOS_ONLY.split_whitespace().any(|name| name == kind) {
            "Android"
        } else {
            "Android and iOS"
        };
        issues.push(format!(
            "{path}: {kind} is unsupported by the {missing} JSON parser"
        ));
    }

    for key in ["children", "items", "actions", "pages"] {
        if key == "items" && !matches!(kind, "LazyColumn" | "LazyGrid" | "LazyRow") {
            continue;
        }
        if let Some(children) = widget[key].as_array() {
            for (index, child) in children.iter().enumerate() {
                inspect_widget(child, &format!("{path}.{key}[{index}]"), issues);
            }
        }
    }
    for key in [
        "child",
        "body",
        "topBar",
        "bottomBar",
        "floatingActionButton",
        "drawer",
        "header",
        "content",
        "template",
        "then",
        "otherwise",
        "startContent",
        "endContent",
    ] {
        if !widget[key].is_null() {
            inspect_widget(&widget[key], &format!("{path}.{key}"), issues);
        }
    }
    if let Some(screens) = widget["screens"].as_object() {
        for (route, screen) in screens {
            inspect_widget(screen, &format!("{path}.screens[{route:?}]"), issues);
        }
    }
    if kind == "BottomTabs" {
        if let Some(tabs) = widget["tabs"].as_array() {
            for (index, tab) in tabs.iter().enumerate() {
                inspect_widget(
                    &tab["content"],
                    &format!("{path}.tabs[{index}].content"),
                    issues,
                );
            }
        }
    }

    if kind == "TopAppBar"
        && widget["actions"]
            .as_array()
            .is_some_and(|actions| !actions.is_empty())
    {
        issues.push(format!("{path}.actions: iOS ignores TopAppBar actions"));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeSet;

    #[test]
    fn host_widget_lists_match_the_native_parser_cases() {
        let android =
            include_str!("../../host/android/app/src/main/java/dev/feather/host/Renderer.kt")
                .split_once("private fun parseNode(")
                .unwrap()
                .1
                .split_once("private fun getIconByName(")
                .unwrap()
                .0;
        let android_types: BTreeSet<_> = android
            .lines()
            .filter_map(|line| {
                line.strip_prefix("        \"")?
                    .split_once("\" ->")
                    .map(|(kind, _)| kind)
            })
            .collect();

        let ios = include_str!("../../host/ios/FeatherHost/FeatherProtocol.swift")
            .split_once("static func parseNode(")
            .unwrap()
            .1
            .split_once("// MARK: Helpers")
            .unwrap()
            .0;
        let ios_types: BTreeSet<_> = ios
            .lines()
            .filter_map(|line| {
                line.strip_prefix("        case \"")?
                    .split_once("\":")
                    .map(|(kind, _)| kind)
            })
            .collect();

        assert_eq!(
            android_types,
            SHARED
                .split_whitespace()
                .chain(ANDROID_ONLY.split_whitespace())
                .collect()
        );
        assert_eq!(
            ios_types,
            SHARED
                .split_whitespace()
                .chain(IOS_ONLY.split_whitespace())
                .collect()
        );
    }

    #[test]
    fn checks_the_fab_and_reports_nested_route_paths() {
        let root = serde_json::json!({
            "type": "Scaffold",
            "floatingActionButton": {"type": "FloatingActionButton", "action": "new"},
            "body": {"type": "BottomTabs", "tabs": [
                {"content": {"type": "Navigator", "screens": {
                    "home": {"type": "SwipeToReveal", "child": {"type": "Text"}}
                }}}
            ]}
        });
        let mut issues = Vec::new();
        inspect_widget(&root, "root", &mut issues);
        assert_eq!(
            issues,
            [
                "root.body.tabs[0].content.screens[\"home\"]: SwipeToReveal is unsupported by the Android and iOS JSON parser"
            ]
        );
    }
}

use crate::config::DevServerConfig;
use crate::constants::{ASSET_EXTENSIONS, FEATHER_SDK_VERSION};
use crate::models::{Diagnostic, RenderMessage, WidgetNode};
use crate::parser::extract_widgets;
use feather_core::{
    artifact_for, discover_modules, with_delta, AppRuntime, BundleArtifact, BundleBuild,
    BundleDelta, BundleManifest,
};
use serde_json::Value;
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;

pub fn render_document(config: &DevServerConfig, runtime: &Arc<AppRuntime>) -> String {
    // Try WASM execution first — if a compiled .wasm exists, use it
    let wasm_path = config.project_path.join(".feather").join("app.wasm");
    if wasm_path.exists() {
        if let Ok(wasm_bytes) = fs::read(&wasm_path) {
            if runtime.load_wasm(wasm_bytes).is_ok() {
                println!("🪶 WASM engine loaded from {}", wasm_path.display());
                if let Some(json) = runtime.render_wasm() {
                    return json;
                }
            }
        }
    }

    if let Ok(bundle) = build_bundle(config, runtime) {
        return bundle.render_payload;
    }

    fallback_render_document(config, runtime)
}

fn fallback_render_document(config: &DevServerConfig, runtime: &Arc<AppRuntime>) -> String {
    if let Some(render_payload) = read_render_payload(&config.generated_ir_path, runtime) {
        return render_payload;
    }

    if let Some(render_payload) = read_render_payload(&config.fallback_ir_path, runtime) {
        return render_payload;
    }

    let (root, diagnostics) = match fs::read_to_string(&config.entry_path) {
        Ok(source) => (
            WidgetNode::Column {
                children: extract_widgets(&source, Arc::clone(runtime), config.entry_path.parent()),
                style: None,
            },
            Vec::new(),
        ),
        Err(error) => (
            WidgetNode::Text {
                text: format!("Could not read app entry: {error}"),
                style: None,
            },
            vec![Diagnostic {
                level: "error",
                message: format!("Could not read {}: {error}", config.entry_path.display()),
            }],
        ),
    };

    let mut state_map = HashMap::new();
    let all_state_json = runtime.get_all_state();
    if let Ok(Value::Object(map)) = serde_json::from_str::<Value>(&all_state_json) {
        for (k, v) in map {
            state_map.insert(k, v.as_str().unwrap_or(&v.to_string()).to_string());
        }
    }

    serde_json::to_string(&RenderMessage {
        message_type: if diagnostics.is_empty() { "render" } else { "render_error" },
        protocol_version: 1,
        root,
        diagnostics,
        state: state_map,
    })
    .unwrap_or_else(|error| {
        format!(
            r#"{{"type":"render","root":{{"type":"Text","text":"Serialization failed: {error}"}}}}"#
        )
    })
}

pub fn build_bundle(
    config: &DevServerConfig,
    runtime: &Arc<AppRuntime>,
) -> Result<BundleBuild, String> {
    let render_payload = load_render_payload(config, runtime)?;
    fs::create_dir_all(&config.bundle_path)
        .map_err(|error| format!("Could not create bundle directory: {error}"))?;
    fs::create_dir_all(config.bundle_path.join("assets"))
        .map_err(|error| format!("Could not create assets directory: {error}"))?;

    let render_path = config.bundle_path.join("app.feather.json");
    fs::write(&render_path, &render_payload)
        .map_err(|error| format!("Could not write render artifact: {error}"))?;

    let modules_payload = "[]\n";
    fs::write(config.bundle_path.join("modules.json"), modules_payload)
        .map_err(|error| format!("Could not write modules artifact: {error}"))?;

    let render_artifact = artifact_for("app.feather.json", render_payload.as_bytes());
    let dependencies = discover_dependencies(config)?;
    let assets = copy_assets(config)?;
    copy_wasm_artifact(config)?;
    let modules = discover_modules(&render_payload);
    let previous_manifest = read_previous_manifest(&config.bundle_path.join("manifest.json"));
    let manifest = BundleManifest {
        name: config
            .project_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("feather-app")
            .to_string(),
        bundle_version: 0,
        sdk_version: FEATHER_SDK_VERSION.to_string(),
        protocol_version: 1,
        render: render_artifact,
        assets,
        modules,
        dependencies,
        delta: BundleDelta {
            changed: Vec::new(),
            removed: Vec::new(),
        },
    };
    let manifest = with_delta(manifest, previous_manifest);

    let manifest_payload = serde_json::to_string_pretty(&manifest)
        .map_err(|error| format!("Could not serialize bundle manifest: {error}"))?;
    fs::write(config.bundle_path.join("manifest.json"), manifest_payload)
        .map_err(|error| format!("Could not write bundle manifest: {error}"))?;

    Ok(BundleBuild {
        manifest,
        render_payload,
    })
}

fn copy_wasm_artifact(config: &DevServerConfig) -> Result<(), String> {
    let wasm_path = config.project_path.join(".feather").join("app.wasm");
    if !wasm_path.exists() {
        return Ok(());
    }

    let bytes = fs::read(&wasm_path)
        .map_err(|error| format!("Could not read WASM artifact {}: {error}", wasm_path.display()))?;
    write_if_changed(&config.bundle_path.join("app.wasm"), &bytes)
        .map_err(|error| format!("Could not write WASM artifact: {error}"))
}

fn discover_dependencies(config: &DevServerConfig) -> Result<Vec<BundleArtifact>, String> {
    let mut dependencies = Vec::new();
    push_file_artifact(
        &mut dependencies,
        &config.project_path,
        &config.entry_path,
        "dependency",
    )?;

    if let Some(path) = &config.generated_ir_path {
        if path.exists() {
            push_file_artifact(&mut dependencies, &config.project_path, path, "dependency")?;
        }
    }

    if let Some(path) = &config.fallback_ir_path {
        if path.exists() {
            push_file_artifact(&mut dependencies, &config.project_path, path, "dependency")?;
        }
    }

    Ok(dependencies)
}

fn copy_assets(config: &DevServerConfig) -> Result<Vec<BundleArtifact>, String> {
    let mut asset_files = Vec::new();
    for path in &config.asset_paths {
        collect_asset_files(path, &mut asset_files)?;
    }

    let mut artifacts = Vec::new();
    for asset_file in asset_files {
        let relative_path = asset_file
            .strip_prefix(&config.project_path)
            .unwrap_or(&asset_file)
            .to_string_lossy()
            .replace('\\', "/");
        let bundle_relative_path = format!("assets/{relative_path}");
        let bundle_asset_path = config.bundle_path.join(&bundle_relative_path);

        if let Some(parent) = bundle_asset_path.parent() {
            fs::create_dir_all(parent)
                .map_err(|error| format!("Could not create asset directory: {error}"))?;
        }

        let bytes = fs::read(&asset_file)
            .map_err(|error| format!("Could not read asset {}: {error}", asset_file.display()))?;
        write_if_changed(&bundle_asset_path, &bytes)
            .map_err(|error| format!("Could not write asset {}: {error}", asset_file.display()))?;
        artifacts.push(artifact_for(&bundle_relative_path, &bytes));
    }

    artifacts.sort_by(|left, right| left.path.cmp(&right.path));
    Ok(artifacts)
}

fn collect_asset_files(path: &Path, assets: &mut Vec<PathBuf>) -> Result<(), String> {
    if !path.exists() {
        return Ok(());
    }

    if path.is_file() {
        if is_asset_file(path) {
            assets.push(path.to_path_buf());
        }
        return Ok(());
    }

    for entry in fs::read_dir(path)
        .map_err(|error| format!("Could not read asset directory {}: {error}", path.display()))?
    {
        let entry = entry.map_err(|error| format!("Could not read asset entry: {error}"))?;
        collect_asset_files(&entry.path(), assets)?;
    }

    Ok(())
}

fn is_asset_file(path: &Path) -> bool {
    path.extension()
        .and_then(|extension| extension.to_str())
        .map(|extension| {
            ASSET_EXTENSIONS
                .iter()
                .any(|asset_extension| extension.eq_ignore_ascii_case(asset_extension))
        })
        .unwrap_or(false)
}

fn push_file_artifact(
    artifacts: &mut Vec<BundleArtifact>,
    project_path: &Path,
    file_path: &Path,
    label: &str,
) -> Result<(), String> {
    let bytes = fs::read(file_path)
        .map_err(|error| format!("Could not read {label} {}: {error}", file_path.display()))?;
    let relative_path = file_path
        .strip_prefix(project_path)
        .unwrap_or(file_path)
        .to_string_lossy()
        .replace('\\', "/");
    artifacts.push(artifact_for(&relative_path, &bytes));
    artifacts.sort_by(|left, right| left.path.cmp(&right.path));
    Ok(())
}

fn write_if_changed(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    if fs::read(path)
        .map(|existing| existing == bytes)
        .unwrap_or(false)
    {
        return Ok(());
    }

    fs::write(path, bytes)
}

fn read_previous_manifest(path: &Path) -> Option<BundleManifest> {
    let source = fs::read_to_string(path).ok()?;
    serde_json::from_str(&source).ok()
}

fn load_render_payload(
    config: &DevServerConfig,
    runtime: &Arc<AppRuntime>,
) -> Result<String, String> {
    if let Some(render_payload) = read_render_payload(&config.generated_ir_path, runtime) {
        return Ok(render_payload);
    }

    if let Some(render_payload) = read_render_payload(&config.fallback_ir_path, runtime) {
        return Ok(render_payload);
    }

    let payload = fallback_render_document(config, runtime);
    serde_json::from_str::<Value>(&payload)
        .map_err(|error| format!("Fallback render payload was invalid: {error}"))?;
    Ok(payload)
}

fn read_render_payload(path: &Option<PathBuf>, runtime: &Arc<AppRuntime>) -> Option<String> {
    let path = path.as_ref()?;
    let payload = fs::read_to_string(path).ok()?;
    
    let value: Value = serde_json::from_str(&payload).ok()?;
    let resolved = runtime.resolve_state_text_nodes(serde_json::to_string(&value).ok()?);
    let mut value: Value = serde_json::from_str(&resolved).ok()?;
    
    if let Value::Object(ref mut map) = value {
        let mut state_map = HashMap::new();
        let all_state_json = runtime.get_all_state();
        if let Ok(Value::Object(s_map)) = serde_json::from_str::<Value>(&all_state_json) {
            for (k, v) in s_map {
                state_map.insert(k, v.as_str().unwrap_or(&v.to_string()).to_string());
            }
        }
        map.insert("state".to_string(), serde_json::to_value(state_map).unwrap());
    }
    
    serde_json::to_string(&value).ok()
}

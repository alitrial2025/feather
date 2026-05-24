use crate::config::DevServerConfig;
use crate::constants::{ASSET_EXTENSIONS, FEATHER_SDK_VERSION};
use crate::models::{Diagnostic, RenderMessage, WidgetNode};
use crate::symbolic_parser::SymbolicParser;
use crate::fui_parser::parse_fui;
use crate::optimizer::UiOptimizer;
use feather_core::bundle::{
    artifact_for, discover_modules, read_previous_manifest, with_delta, AppMetadata, BundleArtifact,
    BundleBuild, BundleDelta, BundleManifest, DeepLink,
};
use feather_core::AppRuntime;
use flate2::write::GzEncoder;
use flate2::Compression;
use serde::Serialize;
use serde_json::Value;
use std::collections::HashMap;
use std::fs;
use std::io::Write;
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
        Ok(source) => {
            if config.entry_path.extension().map_or(false, |ext| ext == "fthr") {
                match parse_fui(&source) {
                    Ok(fui) => (fui.root, Vec::new()),
                    Err(e) => (
                        WidgetNode::Text { text: format!("FUI Error: {e}"), style: None },
                        vec![Diagnostic { level: "error", message: e }]
                    )
                }
            } else {
                let mut parser = SymbolicParser::new(&source, Arc::clone(runtime));
                (
                    WidgetNode::Column {
                        children: parser.parse_widgets(),
                        style: None,
                    },
                    Vec::new(),
                )
            }
        }
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

    // Phase 1.5: FlexBuffers Binary Protocol (Zero-Copy)
    if let Ok(value) = serde_json::from_str::<Value>(&render_payload) {
        if let Ok(root_node) = serde_json::from_value::<WidgetNode>(value.get("root").cloned().unwrap_or(Value::Null)) {
            // Prepare state map for optimization
            let mut state_map = HashMap::new();
            if let Some(Value::Object(map)) = value.get("state") {
                for (k, v) in map {
                    state_map.insert(k.clone(), v.as_str().unwrap_or(&v.to_string()).to_string());
                }
            }

            // Apply AOT Optimizations
            let optimized_node = UiOptimizer::optimize(root_node, &mut state_map);
            
            // FlexBuffers (Android/Rust)
            let mut s = flexbuffers::FlexbufferSerializer::new();
            if optimized_node.serialize(&mut s).is_ok() {
                fs::write(config.bundle_path.join("app.feather.fxb"), s.view())
                    .map_err(|error| format!("Could not write FlexBuffers artifact: {error}"))?;
            }
            
            // MessagePack (iOS fallback/parity)
            if let Ok(mp_bytes) = rmp_serde::to_vec(&optimized_node) {
                fs::write(config.bundle_path.join("app.feather.mp"), &mp_bytes)
                    .map_err(|error| format!("Could not write MessagePack artifact: {error}"))?;
            }

            // FlatBuffers zero-copy binary serialization
            match feather_core::bundle::flat_serializer::json_to_flatbuffer(&render_payload) {
                Ok(fb_bytes) => {
                    fs::write(config.bundle_path.join("app.feather.fb"), &fb_bytes)
                        .map_err(|error| format!("Could not write FlatBuffers artifact: {error}"))?;
                    if let Ok(compressed_fb) = compress_bytes(&fb_bytes) {
                        fs::write(config.bundle_path.join("app.feather.fb.gz"), &compressed_fb)
                            .map_err(|error| format!("Could not write compressed FlatBuffers artifact: {error}"))?;
                    }
                }
                Err(e) => {
                    println!("Failed to serialize FlatBuffers: {}", e);
                }
            }
        }
    }

    // Phase 18: Gzip compression
    let compressed_render = compress_bytes(render_payload.as_bytes())?;
    fs::write(config.bundle_path.join("app.feather.json.gz"), &compressed_render)
        .map_err(|error| format!("Could not write compressed render artifact: {error}"))?;

    let modules_payload = "[]\n";
    fs::write(config.bundle_path.join("modules.json"), modules_payload)
        .map_err(|error| format!("Could not write modules artifact: {error}"))?;

    // Key Generation Setup and manifest signing
    let dot_feather = config.project_path.join(".feather");
    let private_key_path = dot_feather.join("private_key.der");
    let public_key_path = dot_feather.join("public_key.txt");
    fs::create_dir_all(&dot_feather).ok();

    let key_pair = if private_key_path.exists() {
        let key_bytes = fs::read(&private_key_path)
            .map_err(|e| format!("Failed to read private key: {e}"))?;
        ring::signature::Ed25519KeyPair::from_pkcs8(&key_bytes)
            .map_err(|e| format!("Failed to parse private key: {e}"))?
    } else {
        let rng = ring::rand::SystemRandom::new();
        let pkcs8_bytes = ring::signature::Ed25519KeyPair::generate_pkcs8(&rng)
            .map_err(|e| format!("Failed to generate private key: {e}"))?;
        fs::write(&private_key_path, pkcs8_bytes.as_ref())
            .map_err(|e| format!("Failed to write private key: {e}"))?;
        let key_pair = ring::signature::Ed25519KeyPair::from_pkcs8(pkcs8_bytes.as_ref())
            .map_err(|e| format!("Failed to parse generated key: {e}"))?;
        use base64::Engine as _;
        let pub_key_b64 = base64::engine::general_purpose::STANDARD.encode(key_pair.public_key().as_ref());
        fs::write(&public_key_path, &pub_key_b64)
            .map_err(|e| format!("Failed to write public key: {e}"))?;
        println!("🔑 Generated a new Ed25519 keypair for code signing!");
        println!("🔑 Public Key (Base64): {}", pub_key_b64);
        println!("🔑 Please hardcode this public key in your Android/iOS host applications.");
        key_pair
    };

    let render_artifact = artifact_for("app.feather.json", render_payload.as_bytes());
    let dependencies = discover_dependencies(config)?;
    let assets = copy_assets(config)?;
    let wasm = copy_wasm_artifact(config)?;
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
        wasm,
        assets,
        fonts: Vec::new(),
        modules,
        dependencies,
        delta: BundleDelta {
            changed: Vec::new(),
            removed: Vec::new(),
        },
        metadata: AppMetadata {
            app_id: config.manifest.metadata.app_id.clone(),
            version_name: config.manifest.metadata.version_name.clone(),
            version_code: config.manifest.metadata.version_code,
            display_name: config.manifest.metadata.display_name.clone(),
        },
        deep_links: config
            .manifest
            .deep_links
            .iter()
            .map(|dl| DeepLink {
                scheme: dl.scheme.clone(),
                host: dl.host.clone(),
                path_prefix: dl.path_prefix.clone(),
            })
            .collect(),
        platform_overrides: config.manifest.platform_overrides.clone(),
        signature: None,
    };
    let mut manifest = with_delta(manifest, previous_manifest);
    manifest.signature = None;

    // Sign the manifest JSON bytes (with signature set to None)
    let manifest_val = serde_json::to_value(&manifest)
        .map_err(|error| format!("Could not convert bundle manifest to value: {error}"))?;
    let manifest_bytes = serde_json::to_vec(&manifest_val)
        .map_err(|error| format!("Could not serialize bundle manifest for signing: {error}"))?;

    let signature_bytes = key_pair.sign(&manifest_bytes);
    use base64::Engine as _;
    let signature_b64 = base64::engine::general_purpose::STANDARD.encode(signature_bytes.as_ref());
    manifest.signature = Some(signature_b64);

    let manifest_payload = serde_json::to_string_pretty(&manifest)
        .map_err(|error| format!("Could not serialize bundle manifest: {error}"))?;
    fs::write(config.bundle_path.join("manifest.json"), &manifest_payload)
        .map_err(|error| format!("Could not write bundle manifest: {error}"))?;

    Ok(BundleBuild {
        manifest,
        render_payload,
    })
}

fn compress_bytes(data: &[u8]) -> Result<Vec<u8>, String> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(data).map_err(|e| e.to_string())?;
    encoder.finish().map_err(|e| e.to_string())
}

fn copy_wasm_artifact(config: &DevServerConfig) -> Result<Option<BundleArtifact>, String> {
    let wasm_path = config.project_path.join(".feather").join("app.wasm");
    if !wasm_path.exists() {
        return Ok(None);
    }

    let bytes = fs::read(&wasm_path)
        .map_err(|error| format!("Could not read WASM artifact {}: {error}", wasm_path.display()))?;
    write_if_changed(&config.bundle_path.join("app.wasm"), &bytes)
        .map_err(|error| format!("Could not write WASM artifact: {error}"))?;
    
    // Compressed WASM
    let compressed = compress_bytes(&bytes)?;
    fs::write(config.bundle_path.join("app.wasm.gz"), &compressed)
        .map_err(|error| format!("Could not write compressed WASM artifact: {error}"))?;
        
    Ok(Some(artifact_for("app.wasm", &bytes)))
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

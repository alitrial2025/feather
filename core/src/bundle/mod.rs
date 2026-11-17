pub mod flat_serializer;
pub mod diff;

use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use hex::encode as hex_encode;
use std::collections::HashMap;

#[derive(Clone, Deserialize, Serialize)]
pub struct BundleManifest {
    pub name: String,
    pub bundle_version: u8,
    pub sdk_version: String,
    pub protocol_version: u8,
    pub render: BundleArtifact,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub runtime: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub javascript: Option<BundleArtifact>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub wasm: Option<BundleArtifact>,
    pub assets: Vec<BundleArtifact>,
    #[serde(default)]
    pub fonts: Vec<BundleArtifact>,
    pub modules: Vec<BundleModule>,
    pub dependencies: Vec<BundleArtifact>,
    pub delta: BundleDelta,
    #[serde(default)]
    pub metadata: AppMetadata,
    #[serde(default)]
    pub deep_links: Vec<DeepLink>,
    #[serde(default)]
    pub platform_overrides: HashMap<String, serde_json::Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub signature: Option<String>,
}

#[derive(Clone, Deserialize, Serialize, Default)]
pub struct AppMetadata {
    pub app_id: Option<String>,
    pub version_name: Option<String>,
    pub version_code: Option<u32>,
    pub display_name: Option<String>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct DeepLink {
    pub scheme: String,
    pub host: Option<String>,
    pub path_prefix: Option<String>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BundleDelta {
    pub changed: Vec<String>,
    pub removed: Vec<String>,
}

#[derive(Clone, Deserialize, Serialize)]
pub struct BundleArtifact {
    pub path: String,
    pub sha256: String,
    pub bytes: u64,
}

#[derive(Clone, Deserialize, Serialize, uniffi::Record)]
pub struct BundleModule {
    pub name: String,
    pub version: String,
    #[serde(default)]
    pub platforms: Vec<String>,
    #[serde(default)]
    pub permissions: Vec<String>,
}

#[derive(Clone)]
pub struct BundleBuild {
    pub manifest: BundleManifest,
    pub render_payload: String,
}

pub fn artifact_for(path: &str, bytes: &[u8]) -> BundleArtifact {
    let mut hasher = Sha256::new();
    hasher.update(bytes);

    BundleArtifact {
        path: path.to_string(),
        sha256: hex_encode(hasher.finalize()),
        bytes: bytes.len() as u64,
    }
}

pub fn discover_modules(render_payload: &str) -> Vec<BundleModule> {
    let Ok(value) = serde_json::from_str::<Value>(render_payload) else {
        return Vec::new();
    };

    let mut modules = Vec::new();
    collect_modules_from_value(&value, &mut modules);
    modules.sort_by(|left, right| left.name.cmp(&right.name));
    modules.dedup_by(|left, right| left.name == right.name);
    modules
}

pub fn read_previous_manifest(path: &std::path::Path) -> Option<BundleManifest> {
    let source = std::fs::read_to_string(path).ok()?;
    serde_json::from_str(&source).ok()
}

pub fn with_delta(
    mut manifest: BundleManifest,
    previous: Option<BundleManifest>,
) -> BundleManifest {
    let Some(previous) = previous else {
        manifest.delta = BundleDelta {
            changed: artifact_paths(&manifest),
            removed: Vec::new(),
        };
        return manifest;
    };

    let previous_artifacts = artifact_map(&previous);
    let current_artifacts = artifact_map(&manifest);

    let mut changed = current_artifacts
        .iter()
        .filter_map(|(path, sha256)| match previous_artifacts.get(path) {
            Some(previous_sha256) if previous_sha256 == sha256 => None,
            _ => Some(path.clone()),
        })
        .collect::<Vec<_>>();
    let mut removed = previous_artifacts
        .keys()
        .filter(|path| !current_artifacts.contains_key(*path))
        .cloned()
        .collect::<Vec<_>>();

    changed.sort();
    removed.sort();
    manifest.delta = BundleDelta { changed, removed };
    manifest
}

fn artifact_map(manifest: &BundleManifest) -> HashMap<String, String> {
    let mut artifacts = HashMap::new();
    artifacts.insert(manifest.render.path.clone(), manifest.render.sha256.clone());

    if let Some(javascript) = &manifest.javascript {
        artifacts.insert(javascript.path.clone(), javascript.sha256.clone());
    }

    if let Some(wasm) = &manifest.wasm {
        artifacts.insert(wasm.path.clone(), wasm.sha256.clone());
    }

    for asset in &manifest.assets {
        artifacts.insert(asset.path.clone(), asset.sha256.clone());
    }

    for font in &manifest.fonts {
        artifacts.insert(font.path.clone(), font.sha256.clone());
    }

    for dependency in &manifest.dependencies {
        artifacts.insert(
            format!("dependency:{}", dependency.path),
            dependency.sha256.clone(),
        );
    }

    artifacts
}

fn artifact_paths(manifest: &BundleManifest) -> Vec<String> {
    let mut paths = artifact_map(manifest).into_keys().collect::<Vec<_>>();
    paths.sort();
    paths
}

fn collect_modules_from_value(value: &Value, modules: &mut Vec<BundleModule>) {
    match value {
        Value::Object(object) => {
            if let Some(module) = object.get("module").and_then(Value::as_str) {
                let platforms = object.get("modulePlatforms")
                    .and_then(Value::as_array)
                    .map(|arr| arr.iter().filter_map(Value::as_str).map(String::from).collect())
                    .unwrap_or_default();
                    
                let permissions = object.get("modulePermissions")
                    .and_then(Value::as_array)
                    .map(|arr| arr.iter().filter_map(Value::as_str).map(String::from).collect())
                    .unwrap_or_default();

                modules.push(BundleModule {
                    name: module.to_string(),
                    version: object
                        .get("moduleVersion")
                        .and_then(Value::as_str)
                        .unwrap_or("1.0.0")
                        .to_string(),
                    platforms,
                    permissions,
                });
            }

            object
                .values()
                .for_each(|child| collect_modules_from_value(child, modules));
        }
        Value::Array(children) => {
            children
                .iter()
                .for_each(|child| collect_modules_from_value(child, modules));
        }
        _ => {}
    }
}

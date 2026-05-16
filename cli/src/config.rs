use crate::constants::{BUNDLE_DIR_NAME, FEATHER_SDK_VERSION};
use crate::models::FeatherManifest;
use std::collections::HashMap;
use std::fs;
use std::net::IpAddr;
use std::path::{Path, PathBuf};

#[derive(Clone)]
pub struct DevServerConfig {
    pub project_path: PathBuf,
    pub entry_path: PathBuf,
    pub asset_paths: Vec<PathBuf>,
    pub bundle_path: PathBuf,
    pub generated_ir_path: Option<PathBuf>,
    pub fallback_ir_path: Option<PathBuf>,
    pub host: IpAddr,
    pub port: u16,
}

impl DevServerConfig {
    pub fn new(project_path: PathBuf, host: IpAddr, port: u16) -> Result<Self, String> {
        let project_path = project_path
            .canonicalize()
            .map_err(|error| format!("Project path does not exist: {error}"))?;
        let manifest_path = project_path.join("feather.json");

        let manifest: FeatherManifest = if manifest_path.exists() {
            let manifest_source = fs::read_to_string(&manifest_path)
                .map_err(|error| format!("Could not read {}: {error}", manifest_path.display()))?;
            serde_json::from_str(&manifest_source)
                .map_err(|error| format!("Invalid {}: {error}", manifest_path.display()))?
        } else {
            println!("No feather.json found; auto-detecting Feather app...");
            let entry = auto_detect_entry(&project_path)?;
            let relative_entry = entry
                .strip_prefix(&project_path)
                .unwrap_or(&entry)
                .to_string_lossy()
                .replace('\\', "/");

            fs::create_dir_all(project_path.join(".feather").join("cache"))
                .map_err(|error| format!("Could not create .feather cache: {error}"))?;

            println!("   Detected entry: {relative_entry}");
            println!("   Using internal .feather cache; no project config was written.");

            FeatherManifest {
                entry: relative_entry,
                assets: Some(auto_detect_assets(&project_path)),
                generated_ir: None,
                fallback_ir: None,
                sdk_version: Some(FEATHER_SDK_VERSION.to_string()),
                modules: Some(HashMap::new()),
                dependencies: None,
            }
        };

        let entry_path = project_path.join(&manifest.entry);
        if !entry_path.exists() {
            return Err(format!(
                "Entry file not found: {}\nCreate it or update feather.json",
                entry_path.display()
            ));
        }

        let asset_paths = manifest
            .assets
            .unwrap_or_default()
            .into_iter()
            .map(|asset_path| project_path.join(asset_path))
            .collect::<Vec<_>>();
        let bundle_path = project_path.join(BUNDLE_DIR_NAME);
        let generated_ir_path = manifest
            .generated_ir
            .map(|generated_ir| project_path.join(generated_ir));
        let fallback_ir_path = manifest
            .fallback_ir
            .map(|fallback_ir| project_path.join(fallback_ir));

        Ok(Self {
            project_path,
            entry_path,
            asset_paths,
            bundle_path,
            generated_ir_path,
            fallback_ir_path,
            host,
            port,
        })
    }
}

fn auto_detect_assets(project_path: &Path) -> Vec<String> {
    [
        "src/commonMain/resources",
        "src/main/resources",
        "assets",
        "app/src/main/res/drawable",
        "android-native/app/src/main/res/drawable",
    ]
    .iter()
    .filter(|path| project_path.join(path).exists())
    .map(|path| path.to_string())
    .collect()
}

/// Recursively scan for Kotlin files containing Feather widget calls.
/// Prioritizes files named App.kt, then files containing FeatherApp/Widget.
fn auto_detect_entry(project_path: &Path) -> Result<PathBuf, String> {
    let mut candidates: Vec<(PathBuf, u32)> = Vec::new();

    fn scan_dir(dir: &Path, candidates: &mut Vec<(PathBuf, u32)>) {
        let Ok(entries) = fs::read_dir(dir) else {
            return;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                let name = path.file_name().unwrap_or_default().to_string_lossy();
                if name.starts_with('.')
                    || name == "build"
                    || name == "gradle"
                    || name == ".gradle"
                    || name == "node_modules"
                {
                    continue;
                }
                scan_dir(&path, candidates);
            } else if path.extension().map_or(false, |ext| ext == "kt") {
                let Ok(source) = fs::read_to_string(&path) else {
                    continue;
                };
                let mut score: u32 = 0;

                for keyword in &[
                    "Text(",
                    "Column(",
                    "Row(",
                    "Button(",
                    "TextInput(",
                    "Image(",
                ] {
                    if source.contains(keyword) {
                        score += 1;
                    }
                }
                if source.contains("FeatherApp") {
                    score += 10;
                }
                if path.file_name().map_or(false, |name| name == "App.kt") {
                    score += 5;
                }
                if source.contains("fun render()") {
                    score += 3;
                }
                if score > 0 {
                    candidates.push((path, score));
                }
            }
        }
    }

    scan_dir(project_path, &mut candidates);

    if candidates.is_empty() {
        return Err(format!(
            "No Kotlin files with Feather widgets found in {}\n\
             Create an App.kt with Text(), Column(), etc. or add a feather.json manually.",
            project_path.display()
        ));
    }

    candidates.sort_by(|left, right| {
        right
            .1
            .cmp(&left.1)
            .then_with(|| left.0.components().count().cmp(&right.0.components().count()))
    });

    Ok(candidates[0].0.clone())
}

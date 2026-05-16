use crate::constants::{FEATHER_SDK_VERSION, OFFICIAL_MODULES};
use crate::models::{FeatherLock, FeatherManifest, ResolvedModule};
use feather_core::discover_modules;
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

pub async fn install_module(project_path: PathBuf, module: String) {
    println!(
        "Installing module '{module}' into project: {}",
        project_path.display()
    );

    let manifest_path = project_path.join("feather.json");

    // Read existing manifest or create a default one
    let mut manifest: FeatherManifest = if manifest_path.exists() {
        let manifest_source = fs::read_to_string(&manifest_path).unwrap_or_default();
        serde_json::from_str(&manifest_source).unwrap_or_else(|_| FeatherManifest {
            entry: "src/commonMain/kotlin/com/example/helloworld/App.kt".to_string(),
            assets: None,
            generated_ir: None,
            fallback_ir: None,
            sdk_version: Some("1.0.0".to_string()),
            modules: Some(HashMap::new()),
            dependencies: None,
        })

    } else {
        FeatherManifest {
            entry: "src/commonMain/kotlin/com/example/helloworld/App.kt".to_string(),
            assets: None,
            generated_ir: None,
            fallback_ir: None,
            sdk_version: Some("1.0.0".to_string()),
            modules: Some(HashMap::new()),
            dependencies: None,
        }

    };

    // Update manifest
    if manifest.sdk_version.is_none() {
        manifest.sdk_version = Some("1.0.0".to_string());
    }

    let mut modules = manifest.modules.unwrap_or_default();
    modules.insert(module.clone(), "1.0.0".to_string());
    manifest.modules = Some(modules);

    let updated_manifest = serde_json::to_string_pretty(&manifest).unwrap();
    if let Err(e) = fs::write(&manifest_path, updated_manifest) {
        eprintln!("Failed to write feather.json: {e}");
        return;
    }
    println!("Updated feather.json");

    // Write lockfile
    let lock_path = project_path.join("feather.lock");
    let mut lockfile: FeatherLock = if lock_path.exists() {
        let lock_source = fs::read_to_string(&lock_path).unwrap_or_default();
        serde_json::from_str(&lock_source).unwrap_or_else(|_| FeatherLock {
            sdk_version: manifest
                .sdk_version
                .clone()
                .unwrap_or_else(|| "1.0.0".to_string()),
            resolved: HashMap::new(),
        })
    } else {
        FeatherLock {
            sdk_version: manifest
                .sdk_version
                .clone()
                .unwrap_or_else(|| "1.0.0".to_string()),
            resolved: HashMap::new(),
        }
    };

    // Add resolved module to lockfile
    let resolved_module = ResolvedModule {
        version: "1.0.0".to_string(),
        android: format!("dev.feather:{}:1.0.0", module.replace("feather-", "")),
        ios: format!(
            "https://github.com/feather-modules/{}.git",
            module.replace("feather-", "")
        ),
    };
    lockfile.resolved.insert(module.clone(), resolved_module);

    let updated_lockfile = serde_json::to_string_pretty(&lockfile).unwrap();
    if let Err(e) = fs::write(&lock_path, updated_lockfile) {
        eprintln!("Failed to write feather.lock: {e}");
        return;
    }
    println!("Updated feather.lock");
}

pub async fn doctor(project_path: PathBuf) {
    println!("🩺 Feather Doctor — {}", project_path.display());
    println!();
    let mut issues = 0u32;
    let mut installed_modules = Vec::new();

    // Check feather.json
    let manifest_path = project_path.join("feather.json");
    if !manifest_path.exists() {
        eprintln!("  ❌ Missing feather.json");
        issues += 1;
    } else {
        println!("  ✅ Found feather.json");
        if let Ok(source) = fs::read_to_string(&manifest_path) {
            match serde_json::from_str::<FeatherManifest>(&source) {
                Ok(manifest) => {
                    println!("     Entry: {}", manifest.entry);
                    if let Some(sdk) = &manifest.sdk_version {
                        println!("     SDK: {sdk}");
                        if sdk != FEATHER_SDK_VERSION {
                            eprintln!("     ⚠️  SDK version mismatch (Project: {sdk}, CLI: {FEATHER_SDK_VERSION})");
                            issues += 1;
                        }
                    } else {
                        eprintln!("     ⚠️  No sdkVersion");
                        issues += 1;
                    }
                    if let Some(modules) = &manifest.modules {
                        println!("     Modules: {}", modules.len());
                        for (name, _) in modules {
                            installed_modules.push(name.clone());
                            if !OFFICIAL_MODULES.contains(&name.as_str()) {
                                println!("     ⚠️  Third-party module: {name} (may not be supported on all hosts)");
                            }
                        }
                    }
                    // Check entry file exists
                    let entry = project_path.join(&manifest.entry);
                    if entry.exists() {
                        println!("  ✅ Entry file exists");
                    } else {
                        eprintln!("  ❌ Entry file not found: {}", manifest.entry);
                        issues += 1;
                    }
                }
                Err(e) => {
                    eprintln!("  ❌ Invalid feather.json: {e}");
                    issues += 1;
                }
            }
        }
    }

    // Check lockfile
    let lock_path = project_path.join("feather.lock");
    if !lock_path.exists() {
        eprintln!("  ⚠️  Missing feather.lock (run 'feather install <module>')");
    } else {
        println!("  ✅ Found feather.lock");
    }

    // Check fallback IR and discover required modules
    let fallback_path = project_path.join(".feather").join("app.feather.json");
    if fallback_path.exists() {
        println!("  ✅ Fallback IR present");
        if let Ok(payload) = fs::read_to_string(&fallback_path) {
            let required = discover_modules(&payload);
            for req in required {
                if !installed_modules.contains(&req.name) {
                    eprintln!("  ❌ Module '{}' is used in fallback IR but not declared in feather.json", req.name);
                    issues += 1;
                }
            }
        }
    } else {
        eprintln!("  ⚠️  No fallback IR (.feather/app.feather.json)");
    }

    // Check Rust toolchain
    match std::process::Command::new("rustc")
        .arg("--version")
        .output()
    {
        Ok(output) if output.status.success() => {
            let version = String::from_utf8_lossy(&output.stdout);
            println!("  ✅ Rust: {}", version.trim());
        }
        _ => eprintln!("  ⚠️  Rust toolchain not found"),
    }

    println!();
    if issues == 0 {
        println!("🎉 No issues found!");
    } else {
        eprintln!("⚠️  Found {issues} issue(s).");
    }
}

pub async fn list_modules(project_path: PathBuf) {
    let manifest_path = project_path.join("feather.json");
    if !manifest_path.exists() {
        eprintln!("Project not found (no feather.json).");
        return;
    }

    let manifest_source = fs::read_to_string(&manifest_path).unwrap_or_default();
    if let Ok(manifest) = serde_json::from_str::<FeatherManifest>(&manifest_source) {
        println!("Installed Modules:");
        if let Some(modules) = manifest.modules {
            for (name, version) in modules {
                println!("  - {name}@{version}");
            }
        } else {
            println!("  (None)");
        }
    } else {
        eprintln!("Failed to parse feather.json");
    }
}

pub async fn upgrade_modules(project_path: PathBuf) {
    println!("⬆️  Upgrading modules for: {}", project_path.display());

    let manifest_path = project_path.join("feather.json");
    if !manifest_path.exists() {
        eprintln!("No feather.json found.");
        return;
    }

    let manifest_source = fs::read_to_string(&manifest_path).unwrap_or_default();
    let mut manifest: FeatherManifest = match serde_json::from_str(&manifest_source) {
        Ok(m) => m,
        Err(e) => {
            eprintln!("Failed to parse feather.json: {e}");
            return;
        }
    };

    // Bump SDK version
    let old_sdk = manifest
        .sdk_version
        .clone()
        .unwrap_or_else(|| "0.0.0".to_string());
    manifest.sdk_version = Some(FEATHER_SDK_VERSION.to_string());
    println!("  SDK: {old_sdk} → {FEATHER_SDK_VERSION}");

    // Bump all module versions
    if let Some(ref mut modules) = manifest.modules {
        for (name, version) in modules.iter_mut() {
            let old = version.clone();
            *version = FEATHER_SDK_VERSION.to_string();
            if old != *version {
                println!("  {name}: {old} → {version}");
            }
        }
    }

    let updated = serde_json::to_string_pretty(&manifest).unwrap();
    if let Err(e) = fs::write(&manifest_path, &updated) {
        eprintln!("Failed to write feather.json: {e}");
        return;
    }

    // Regenerate lockfile
    let lock_path = project_path.join("feather.lock");
    if lock_path.exists() {
        let _ = fs::remove_file(&lock_path);
    }

    if let Some(modules) = &manifest.modules {
        let mut lockfile = FeatherLock {
            sdk_version: manifest
                .sdk_version
                .clone()
                .unwrap_or_else(|| FEATHER_SDK_VERSION.to_string()),
            resolved: HashMap::new(),
        };
        for (name, version) in modules {
            lockfile.resolved.insert(
                name.clone(),
                ResolvedModule {
                    version: version.clone(),
                    android: format!("dev.feather:{}:{version}", name.replace("feather-", "")),
                    ios: format!(
                        "https://github.com/feather-modules/{}.git",
                        name.replace("feather-", "")
                    ),
                },
            );
        }
        let lock_json = serde_json::to_string_pretty(&lockfile).unwrap();
        let _ = fs::write(&lock_path, lock_json);
        println!("  Regenerated feather.lock");
    }

    println!("✅ Upgrade complete.");
}

pub async fn init_project(name: String) {
    println!("🪶 Initializing new Feather project: {name}");

    let project_dir = PathBuf::from(&name);
    if project_dir.exists() {
        eprintln!("Directory '{name}' already exists.");
        return;
    }

    let src_dir = project_dir
        .join("src")
        .join("commonMain")
        .join("kotlin")
        .join("com")
        .join("example")
        .join(&name);
    let feather_dir = project_dir.join(".feather");

    fs::create_dir_all(&src_dir).unwrap();
    fs::create_dir_all(&feather_dir).unwrap();

    // feather.json
    let manifest = serde_json::json!({
        "entry": format!("src/commonMain/kotlin/com/example/{name}/App.kt"),
        "assets": ["src/commonMain/resources"],
        "generatedIr": "build/generated/ksp/metadata/commonMain/kotlin/feather/app.feather.json",
        "fallbackIr": ".feather/app.feather.json",
        "sdkVersion": FEATHER_SDK_VERSION,
        "modules": {}
    });
    fs::write(
        project_dir.join("feather.json"),
        serde_json::to_string_pretty(&manifest).unwrap(),
    )
    .unwrap();

    // App.kt
    let app_source = format!(
        r#"package com.example.{name}

import dev.feather.sdk.*

class App : FeatherApp() {{
    override fun render(): Widget {{
        return Column(
            Text("Hello from {name}!"),
            Button(
                text = "Click me",
                action = "buttonClicked"
            ),
            StateText(
                key = "buttonClicked",
                prefix = "Clicks: ",
                fallback = "Clicks: 0"
            )
        )
    }}
}}
"#
    );
    fs::write(src_dir.join("App.kt"), app_source).unwrap();

    // Fallback IR
    let fallback_ir = serde_json::json!({
        "type": "render",
        "protocol_version": 1,
        "root": {
            "type": "Column",
            "children": [
                {"type": "Text", "text": format!("Hello from {name}!")},
                {"type": "Button", "text": "Click me", "action": "buttonClicked"},
                {"type": "StateText", "key": "buttonClicked", "prefix": "Clicks: ", "fallback": "Clicks: 0"}
            ]
        },
        "diagnostics": []
    });
    fs::write(
        feather_dir.join("app.feather.json"),
        serde_json::to_string_pretty(&fallback_ir).unwrap(),
    )
    .unwrap();

    // build.gradle.kts
    let build_gradle = format!(
        r#"plugins {{
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}}

kotlin {{
    androidTarget()
    jvmToolchain(17)
    sourceSets {{
        commonMain.dependencies {{
            implementation(project(":sdk"))
        }}
    }}
}}

dependencies {{
    add("kspCommonMainMetadata", project(":compiler:ksp"))
}}

android {{
    namespace = "com.example.{name}"
    compileSdk = 36
    defaultConfig {{
        minSdk = 24
    }}
}}
"#
    );
    fs::write(project_dir.join("build.gradle.kts"), build_gradle).unwrap();

    println!("✅ Project created at ./{name}");
    println!();
    println!("Next steps:");
    println!("  cd {name}");
    println!("  feather doctor --project .");
    println!("  feather start --project .");
}

pub async fn build_production(project_path: PathBuf, target: String) {
    println!("🪶 Feather Build: Generating production artifact for {}...", target);
    let mut command = if cfg!(windows) {
        std::process::Command::new("cmd")
    } else {
        std::process::Command::new("sh")
    };
    
    if cfg!(windows) {
        command.args(["/C", "..\\scripts\\feather-build.bat"]);
    } else {
        command.arg("../scripts/feather-build.sh");
    }
    
    command.current_dir(&project_path);
    
    match command.status() {
        Ok(status) if status.success() => {
            println!("✅ Successfully packaged AOT production bundle for {}", target);
        }
        Ok(status) => {
            eprintln!("❌ Build failed with status: {}", status);
            std::process::exit(1);
        }
        Err(e) => {
            eprintln!("❌ Failed to execute build script: {}", e);
            std::process::exit(1);
        }
    }
}

/// Compile Kotlin source files to a .wasm binary using the Kotlin/WASM compiler.
pub async fn compile_wasm(project_path: PathBuf) {
    println!("🪶 Compiling Kotlin → WebAssembly...");

    let feather_dir = project_path.join(".feather");
    if !feather_dir.exists() {
        fs::create_dir_all(&feather_dir).unwrap();
    }

    let output_wasm = feather_dir.join("app.wasm");

    // Discover all .kt source files in the project
    let src_dir = project_path.join("src");
    let kt_files = discover_kt_files(&src_dir);
    if kt_files.is_empty() {
        eprintln!("❌ No .kt source files found in {}", src_dir.display());
        std::process::exit(1);
    }

    println!("  Found {} Kotlin source files", kt_files.len());

    // Build the kotlinc command
    // Using Gradle's Kotlin/Wasm target is the primary path.
    // This command provides a fallback using kotlinc directly.
    let mut command = if cfg!(windows) {
        std::process::Command::new("cmd")
    } else {
        std::process::Command::new("sh")
    };

    let gradle_wrapper = if cfg!(windows) { "gradlew.bat" } else { "./gradlew" };
    let gradle_path = project_path.join(gradle_wrapper);

    if gradle_path.exists() {
        // Use Gradle if available (preferred — handles dependencies)
        println!("  Using Gradle to compile WASM target...");
        if cfg!(windows) {
            command.args(["/C", gradle_wrapper, "compileKotlinWasmWasi"]);
        } else {
            command.args(["-c", &format!("{} compileKotlinWasmWasi", gradle_wrapper)]);
        }
        command.current_dir(&project_path);
    } else {
        // Fallback: try direct kotlinc invocation
        println!("  Using kotlinc directly (no Gradle wrapper found)...");
        let file_args: Vec<String> = kt_files.iter().map(|f| f.display().to_string()).collect();
        if cfg!(windows) {
            let mut args = vec!["/C".to_string(), "kotlinc-wasm".to_string()];
            args.extend(file_args);
            args.extend(["-o".to_string(), output_wasm.display().to_string()]);
            command.args(&args);
        } else {
            let file_list = file_args.join(" ");
            command.args(["-c", &format!("kotlinc-wasm {} -o {}", file_list, output_wasm.display())]);
        }
    }

    match command.status() {
        Ok(status) if status.success() => {
            // If Gradle was used, we need to copy the output to .feather/app.wasm
            let gradle_output = project_path
                .join("build")
                .join("compileSync")
                .join("wasmWasi")
                .join("main")
                .join("productionExecutable")
                .join("kotlin");

            if gradle_output.exists() {
                // Find the .wasm file in the Gradle output
                if let Ok(entries) = fs::read_dir(&gradle_output) {
                    for entry in entries.flatten() {
                        if entry.path().extension().map_or(false, |ext| ext == "wasm") {
                            fs::copy(entry.path(), &output_wasm).unwrap();
                            println!("  Copied {} → {}", entry.path().display(), output_wasm.display());
                            break;
                        }
                    }
                }
            }

            if output_wasm.exists() {
                let size = fs::metadata(&output_wasm).map(|m| m.len()).unwrap_or(0);
                println!("✅ WASM binary compiled: {} ({} bytes)", output_wasm.display(), size);
            } else {
                println!("⚠️ Compilation succeeded but .wasm output not found. Check Gradle output paths.");
            }
        }
        Ok(status) => {
            eprintln!("❌ WASM compilation failed with status: {}", status);
            std::process::exit(1);
        }
        Err(e) => {
            eprintln!("❌ Failed to execute Kotlin compiler: {}", e);
            eprintln!("   Make sure kotlinc or Gradle is in your PATH.");
            std::process::exit(1);
        }
    }
}

/// Recursively discover all .kt files in a directory.
fn discover_kt_files(dir: &std::path::Path) -> Vec<PathBuf> {
    let mut files = Vec::new();
    if !dir.exists() {
        return files;
    }
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                files.extend(discover_kt_files(&path));
            } else if path.extension().map_or(false, |ext| ext == "kt") {
                files.push(path);
            }
        }
    }
    files
}


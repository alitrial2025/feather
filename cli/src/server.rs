use crate::bundler::{build_bundle, render_document};
use crate::config::DevServerConfig;
use feather_core::AppRuntime;
use futures_util::{SinkExt, StreamExt};
use local_ip_address::local_ip;
use notify::{Config, RecommendedWatcher, RecursiveMode, Watcher};
use qr2term::print_qr;
use std::fs;
use std::sync::Arc;
use tokio::sync::broadcast;
use warp::{Filter, Reply};

#[derive(Default)]
struct LayoutBaseline {
    root: Option<String>,
    recovering: bool,
}

pub async fn run_server(config: DevServerConfig, session: Option<String>) {
    let host = config.host;
    let port = config.port;

    // Detect local IP for easier connection from mobile devices
    let my_local_ip = if host.is_unspecified() || (host.is_loopback() && session.is_none()) {
        local_ip().unwrap_or(host)
    } else {
        host
    };

    let runtime = Arc::new(AppRuntime::default());
    if let Err(error) = build_bundle(&config, &runtime) { eprintln!("Initial bundle: {error}"); }
    let prefix = session.as_ref().map(|token| format!("/session/{token}")).unwrap_or_default();
    let origin = get_public_url(port)
        .unwrap_or_else(|| format!("http://{}", std::net::SocketAddr::new(my_local_ip, port)));
    let base = format!("{}{prefix}", origin.trim_end_matches('/'));
    let key = fs::read_to_string(config.project_path.join(".feather/public_key.txt")).unwrap_or_default();
    let name = config.manifest.metadata.display_name.clone().unwrap_or_else(|| config.project_path.file_name().unwrap().to_string_lossy().to_string());
    let connection_uri = crate::connection::project_uri(&base, key.trim(), &name);
    println!("🪶 Feather project ready: {name}");
    if session.is_some() {
        println!("Connection page: {base}/");
        if host == std::net::Ipv4Addr::UNSPECIFIED {
            println!("Local connection page: http://127.0.0.1:{port}{prefix}/");
        }
    }
    println!("Scan this QR in Feather Go:");
    if let Err(error) = print_qr(&connection_uri) { eprintln!("QR generation failed: {error}"); }
    let metadata = serde_json::json!({"name":name,"runtime":config.manifest.runtime.clone().unwrap_or_else(||"layout".into()),"connection_uri":connection_uri,"sdk_version":"1.0.0"});
    let metadata_route = warp::path!("project").and(warp::get()).map(move || warp::reply::json(&metadata));
    let svg = match qrcode::QrCode::new(connection_uri.as_bytes()) {
        Ok(code) => code.render::<qrcode::render::svg::Color>().min_dimensions(320,320).build(),
        Err(error) => { eprintln!("Project connection URL exceeds QR capacity: {error}"); return; }
    };
    let qr_route = warp::path!("qr.svg").and(warp::get()).map(move || warp::reply::with_header(svg.clone(), "content-type", "image/svg+xml"));
    let landing_route = warp::path::end().and(warp::get()).map(|| warp::reply::html(include_str!("connect.html")));
    let apk = std::env::var_os("FEATHER_CLIENT_APK").map(std::path::PathBuf::from).unwrap_or_else(|| config.project_path.join(".feather/client.apk"));
    let apk_route = warp::path!("client.apk").and(warp::get()).and(warp::fs::file(apk))
        .with(warp::reply::with::header("content-type", "application/vnd.android.package-archive"))
        .with(warp::reply::with::header("content-disposition", "attachment; filename=feather-go.apk"));

    println!("Project: {}", config.project_path.display());
    println!("Entry: {}", config.entry_path.display());
    println!("Bundle: {}", config.bundle_path.display());
    if let Some(generated_ir_path) = &config.generated_ir_path {
        println!("Generated IR: {}", generated_ir_path.display());
    }

    let (tx, _) = broadcast::channel::<String>(16);
    let tx = Arc::new(tx);
    let previous_layout = Arc::new(std::sync::Mutex::new(LayoutBaseline::default()));
    {
        let initial_doc = render_document(&config, &runtime);
        if let Ok(val) = serde_json::from_str::<serde_json::Value>(&initial_doc) {
            let mut baseline = previous_layout.lock().unwrap();
            if val.get("type").and_then(|value| value.as_str()) == Some("render_error") {
                baseline.recovering = true;
            } else if let Some(root) = val.get("root") {
                baseline.root = Some(root.to_string());
            }
        }
    }

    let tx_clone = Arc::clone(&tx);
    let watcher_config = config.clone();
    let watcher_runtime = Arc::clone(&runtime);
    let watcher_prev = Arc::clone(&previous_layout);
    tokio::task::spawn_blocking(move || {
        if let Err(e) = watch_files(watcher_config, tx_clone, watcher_runtime, watcher_prev) {
            eprintln!("File watcher error: {:?}", e);
        }
    });

    // Strategy A: Reactive polling for state changes
    let poll_tx = Arc::clone(&tx);
    let poll_runtime = Arc::clone(&runtime);
    let poll_config = config.clone();
    let poll_prev = Arc::clone(&previous_layout);
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(tokio::time::Duration::from_millis(100));
        loop {
            interval.tick().await;
            for message in poll_runtime.drain_console_logs() {
                crate::app_logs::console("Preview", &message);
            }
            
            // Drain module calls first
            let calls = poll_runtime.drain_module_calls();
            for call in calls {
                let _ = poll_tx.send(call);
            }

            let needed = {
                poll_runtime.poll_render_needed()
            };
            if needed {
                handle_layout_update(&poll_config, &poll_runtime, &poll_tx, &poll_prev);
            }
        }
    });

    let bundle_config = config.clone();
    let bundle_runtime = Arc::clone(&runtime);
    let artifact_config = config.clone();
    let artifact_runtime = Arc::clone(&runtime);
    let filter_config = config.clone();
    let filter_runtime = Arc::clone(&runtime);

    let tx_filter = warp::any().map(move || Arc::clone(&tx));
    let config_filter = warp::any().map(move || filter_config.clone());
    let runtime_filter = warp::any().map(move || Arc::clone(&filter_runtime));
    let prev_filter = warp::any().map(move || Arc::clone(&previous_layout));
    let bundle_route = warp::path!("bundle" / "manifest")
        .and(warp::get())
        .map(
            move || match build_bundle(&bundle_config, &bundle_runtime) {
                Ok(bundle) => warp::reply::json(&bundle.manifest),
                Err(error) => warp::reply::json(&serde_json::json!({
                    "error": error,
                })),
            },
        );
    let artifact_route = warp::path!("bundle" / "artifact" / String)
        .and(warp::get())
        .map(move |artifact_path: String| {
            let normalized_path = artifact_path.replace('\\', "/");
            let mut is_safe = true;
            for comp in std::path::Path::new(&normalized_path).components() {
                if !matches!(comp, std::path::Component::Normal(_)) {
                    is_safe = false;
                    break;
                }
            }

            if !is_safe {
                return warp::http::Response::builder()
                    .status(warp::http::StatusCode::BAD_REQUEST)
                    .body(Vec::from("invalid artifact path"))
                    .unwrap_or_else(|_| warp::http::Response::new(Vec::from("invalid artifact path")));
            }

            if let Err(error) = build_bundle(&artifact_config, &artifact_runtime) {
                let body = error.into_bytes();
                return warp::http::Response::builder()
                    .status(warp::http::StatusCode::INTERNAL_SERVER_ERROR)
                    .body(body.clone())
                    .unwrap_or_else(|_| warp::http::Response::new(body));
            }

            let artifact_file = artifact_config.bundle_path.join(normalized_path);
            match fs::read(artifact_file) {
                Ok(payload) => warp::http::Response::builder()
                    .status(warp::http::StatusCode::OK)
                    .body(payload.clone())
                    .unwrap_or_else(|_| warp::http::Response::new(payload)),
                Err(error) => {
                    let msg = format!("artifact not found: {error}").into_bytes();
                    warp::http::Response::builder()
                        .status(warp::http::StatusCode::NOT_FOUND)
                        .body(msg.clone())
                        .unwrap_or_else(|_| warp::http::Response::new(msg))
                }
            }
        });

    let ws_route = warp::path("ws")
        .and(warp::ws())
        .and(tx_filter)
        .and(config_filter)
        .and(runtime_filter)
        .and(prev_filter)
        .and(warp::header::optional::<String>("origin"))
        .map(
            |ws: warp::ws::Ws,
             tx: Arc<broadcast::Sender<String>>,
             config: DevServerConfig,
             runtime: Arc<AppRuntime>,
             prev_layout: Arc<std::sync::Mutex<LayoutBaseline>>,
             origin: Option<String>| {
                if origin.as_deref().is_some_and(|value| !is_origin_allowed(value, config.port)) {
                    return warp::reply::with_status("Forbidden origin", warp::http::StatusCode::FORBIDDEN).into_response();
                }
                ws.on_upgrade(move |socket| handle_connection(socket, tx, config, runtime, prev_layout, origin)).into_response()
            },
        );

    let devtools_html = include_str!("devtools.html");
    
    let devtools_route = warp::path!("devtools")
        .and(warp::get())
        .map(move || warp::reply::html(devtools_html));

    let devtools_index_route = warp::path!("devtools" / "index.html")
        .and(warp::get())
        .map(move || warp::reply::html(devtools_html));

    let routes = landing_route.or(metadata_route).or(qr_route).or(apk_route).or(ws_route)
        .or(bundle_route)
        .or(artifact_route)
        .or(devtools_route)
        .or(devtools_index_route);

    if let Some(token) = session {
        warp::serve(warp::path("session").and(warp::path(token)).and(routes)).run((host, port)).await;
    } else {
        warp::serve(routes).run((host, port)).await;
    }
}

fn watch_files(
    config: DevServerConfig,
    tx: Arc<broadcast::Sender<String>>,
    runtime: Arc<AppRuntime>,
    previous_layout: Arc<std::sync::Mutex<LayoutBaseline>>,
) -> notify::Result<()> {
    let (std_tx, std_rx) = std::sync::mpsc::channel();

    let mut watcher = RecommendedWatcher::new(std_tx, Config::default())?;
    watcher.watch(&config.project_path, RecursiveMode::Recursive)?;

    println!(
        "Watching for file changes in {}...",
        config.project_path.display()
    );

    // Wait for a quiet editor-save window. A rename/create burst is one update.
    loop {
        let first = match std_rx.recv() {
            Ok(event) => event,
            Err(_) => break,
        };
        let mut paths = std::collections::HashSet::new();
        collect_refresh_paths(first, &config, &mut paths);
        if paths.is_empty() { continue; }
        // Bound the batch as well as the quiet window, so a continuous writer
        // cannot postpone updates indefinitely.
        let deadline = std::time::Instant::now() + std::time::Duration::from_millis(500);
        while std::time::Instant::now() < deadline {
            match std_rx.recv_timeout(std::time::Duration::from_millis(75)) {
                Ok(event) => collect_refresh_paths(event, &config, &mut paths),
                Err(std::sync::mpsc::RecvTimeoutError::Timeout) => break,
                Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => break,
            }
        }
        if config.manifest.runtime.as_deref() == Some("kotlin-js") {
            match crate::compiler::compile_javascript(&config.project_path) {
                Ok(()) => {
                    handle_layout_update(&config, &runtime, &tx, &previous_layout);
                    let _ = tx.send(serde_json::json!({"type":"hot-reload-js", "path":"app.js"}).to_string());
                }
                Err(error) => {
                    previous_layout.lock().unwrap().recovering = true;
                    let _ = tx.send(serde_json::json!({
                        "type": "render_error", "protocol_version": 1,
                        "root": {"type": "Text", "text": "Kotlin compilation failed"},
                        "diagnostics": [{"level": "error", "message": error}]
                    }).to_string());
                }
            }
            continue;
        }
        let changed_wasm = paths.contains(&config.project_path.join(".feather/app.wasm"));
        if changed_wasm {
            let result = preserve_and_refresh_state(&runtime, &tx, || {
                crate::bundler::build_bundle(&config, &runtime)
            });
            match result {
                Ok(_) => { let _ = tx.send(serde_json::json!({
                    "type": "hot-reload-wasm", "path": "app.wasm"
                }).to_string()); }
                Err(error) => eprintln!("WASM refresh failed: {error}"),
            }
        } else {
            println!("Refreshing {} changed input(s)", paths.len());
            preserve_and_refresh_state(&runtime, &tx, || {
                handle_layout_update(&config, &runtime, &tx, &previous_layout)
            });
        }
    }

    Ok(())
}

fn collect_refresh_paths(
    event: notify::Result<notify::Event>,
    config: &DevServerConfig,
    paths: &mut std::collections::HashSet<std::path::PathBuf>,
) {
    match event {
        Ok(event) if matches!(event.kind, notify::EventKind::Create(_) | notify::EventKind::Modify(_) | notify::EventKind::Remove(_)) => {
            paths.extend(event.paths.into_iter().filter(|path| is_refresh_input(config, path)));
        }
        Err(error) => eprintln!("watch error: {error}"),
        _ => {}
    }
}

fn is_refresh_input(config: &DevServerConfig, path: &std::path::Path) -> bool {
    // Check explicit compiler outputs before excluding build/cache directories.
    if config.generated_ir_path.as_deref() == Some(path)
        || config.fallback_ir_path.as_deref() == Some(path)
        || path == config.project_path.join(".feather/app.wasm") {
        return true;
    }
    let Ok(relative) = path.strip_prefix(&config.project_path) else { return false; };
    if relative.components().any(|part| matches!(part.as_os_str().to_str(),
        Some("build" | "target" | "dist" | ".feather" | ".featherbundle" | ".git" | ".gradle" | ".idea" | "node_modules"))) {
        return false;
    }
    path == config.entry_path || config.asset_paths.iter().any(|assets| path.starts_with(assets))
        || matches!(path.extension().and_then(|ext| ext.to_str()), Some("kt" | "fthr"))
}

async fn handle_connection(
    ws: warp::ws::WebSocket,
    tx: Arc<broadcast::Sender<String>>,
    config: DevServerConfig,
    runtime: Arc<AppRuntime>,
    previous_layout: Arc<std::sync::Mutex<LayoutBaseline>>,
    origin: Option<String>,
) {
    if let Some(ref origin_str) = origin {
        if !is_origin_allowed(origin_str, config.port) {
            eprintln!("⚠️  [SECURITY WARNING]: Rejected connection from unauthorized origin: {}", origin_str);
            return;
        }
    }

    println!("Client connected!");

    let (mut ws_tx, mut ws_rx) = ws.split();
    let mut rx = tx.subscribe();

    let msg = warp::ws::Message::text(render_document(&config, &runtime));
    if let Err(e) = ws_tx.send(msg).await {
        eprintln!("Error sending message: {}", e);
        return;
    }

    let mut ws_tx_task = tokio::spawn(async move {
        while let Ok(msg) = rx.recv().await {
            let ws_msg = warp::ws::Message::text(msg);
            if let Err(e) = ws_tx.send(ws_msg).await {
                eprintln!("Error sending update: {}", e);
                break;
            }
        }
    });

    // Task to handle incoming messages from the client
    let event_tx = Arc::clone(&tx);
    let event_runtime = Arc::clone(&runtime);
    let event_config = config.clone();
    let event_prev = Arc::clone(&previous_layout);
    let mut ws_rx_task = tokio::spawn(async move {
        while let Some(result) = ws_rx.next().await {
            match result {
                Ok(msg) => {
                    if let Ok(text) = msg.to_str() {
                        let is_handled = if let Ok(json) = serde_json::from_str::<serde_json::Value>(text) {
                            if let Some(msg_type) = json.get("type").and_then(|v| v.as_str()) {
                                if matches!(msg_type, "log" | "error" | "diagnostic") {
                                    crate::app_logs::device_event(&json, msg_type);
                                    true
                                } else if msg_type == "devtools_state_update" || msg_type == "state_update" {
                                    if let (Some(key), Some(value)) = (
                                        json.get("key").and_then(|v| v.as_str()),
                                        json.get("value")
                                    ) {
                                        let val_str = serde_json::to_string(value).unwrap_or_default();
                                        event_runtime.set_state(key, &val_str);
                                        
                                        let sync_packet = serde_json::json!({
                                            "type": if msg_type == "devtools_state_update" { "devtools_state_update" } else { "state_update" },
                                            "key": key,
                                            "value": value
                                        });
                                        let _ = event_tx.send(sync_packet.to_string());
                                    }
                                    true
                                } else if msg_type == "runtime_state" {
                                    if let Some(state) = json.get("state").and_then(|value| value.as_object()) {
                                        for (key, value) in state { event_runtime.set_state(key, &value.to_string()); }
                                    }
                                    true
                                } else if msg_type == "action" {
                                    if event_config.manifest.runtime.as_deref() == Some("kotlin-js") {
                                        apply_action_event(text, &event_runtime);
                                        handle_layout_update(&event_config, &event_runtime, &event_tx, &event_prev);
                                    } else {
                                        let _ = event_tx.send(text.to_string());
                                    }
                                    true
                                } else { false }
                            } else { false }
                        } else { false };

                        if !is_handled {
                            println!("Received host event: {text}");
                            apply_action_event(text, &event_runtime);
                            handle_layout_update(&event_config, &event_runtime, &event_tx, &event_prev);
                        }
                    }
                }
                Err(e) => {
                    eprintln!("WebSocket error: {}", e);
                    break;
                }
            }
        }
    });

    // Wait for either task to finish
    tokio::select! {
        _ = (&mut ws_tx_task) => ws_rx_task.abort(),
        _ = (&mut ws_rx_task) => ws_tx_task.abort(),
    };

    println!("Client disconnected.");
}

fn apply_action_event(payload: &str, runtime: &Arc<AppRuntime>) {
    runtime.apply_action_event(payload);
}

fn handle_layout_update(
    config: &DevServerConfig,
    runtime: &Arc<AppRuntime>,
    tx: &Arc<broadcast::Sender<String>>,
    previous_layout: &Arc<std::sync::Mutex<LayoutBaseline>>,
) -> String {
    let new_doc = render_document(config, runtime);
    
    let new_val: serde_json::Value = match serde_json::from_str(&new_doc) {
        Ok(v) => v,
        Err(_) => {
            let _ = tx.send(new_doc.clone());
            return new_doc;
        }
    };
    
    // Syntax errors are diagnostics, never the baseline for the next patch.
    if new_val.get("type").and_then(|value| value.as_str()) == Some("render_error") {
        previous_layout.lock().unwrap().recovering = true;
        let _ = tx.send(new_doc.clone());
        return new_doc;
    }

    let new_root = match new_val.get("root") {
        Some(r) => r.to_string(),
        None => {
            let _ = tx.send(new_doc.clone());
            return new_doc;
        }
    };

    let mut prev = previous_layout.lock().unwrap();
    if prev.recovering {
        // Hosts may have displayed an error tree; a delta against the last good
        // tree would then apply to the wrong document. Restore a full snapshot.
        let _ = tx.send(new_doc.clone());
    } else if let Some(ref old_root) = prev.root {
        let patches = feather_core::diff_layouts(old_root.clone(), new_root.clone());
        if !patches.is_empty() {
            let patch_msg = serde_json::json!({
                "type": "patch",
                "patches": patches,
            }).to_string();
            let _ = tx.send(patch_msg);
        }
    } else {
        let _ = tx.send(new_doc.clone());
    }
    
    prev.root = Some(new_root);
    prev.recovering = false;
    new_doc
}

fn get_public_url(port: u16) -> Option<String> {
    if let Ok(url) = std::env::var("FEATHER_PUBLIC_URL") {
        if url.starts_with("https://") || url.starts_with("http://") { return Some(url); }
    }
    // 1. Check for Google Cloud Workstations (Monospace)
    if let Ok(web_host) = std::env::var("WEB_HOST") {
        return Some(format!("https://{}-{}", port, web_host));
    }

    // 2. Check for VS Code Browser Port (Codespaces / Dev Containers)
    if let Ok(cluster) = std::env::var("GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN") {
        return Some(format!("https://{}-{}.{}", std::env::var("CODESPACE_NAME").unwrap_or_default(), port, cluster));
    }

    None
}

fn preserve_and_refresh_state<F, R>(runtime: &Arc<AppRuntime>, tx: &Arc<broadcast::Sender<String>>, f: F) -> R 
where
    F: FnOnce() -> R,
{
    let state_json = runtime.get_all_state();
    let result = f();
    if let Ok(serde_json::Value::Object(map)) = serde_json::from_str::<serde_json::Value>(&state_json) {
        for (key, val) in map {
            let val_str = serde_json::to_string(&val).unwrap_or_default();
            runtime.set_state(&key, &val_str);
            let sync_packet = serde_json::json!({
                "type": "state_update",
                "key": key,
                "value": val
            });
            let _ = tx.send(sync_packet.to_string());
        }
    }
    result
}

// Browser origins must identify this server, not merely a private-looking hostname.
fn is_origin_allowed(origin: &str, port: u16) -> bool {
    let Ok(uri) = origin.parse::<warp::http::Uri>() else { return false; };
    let Some(scheme) = uri.scheme_str() else { return false; };
    if !matches!(scheme, "http" | "https") || uri.query().is_some() || !matches!(uri.path(), "" | "/") {
        return false;
    }
    let Some(authority) = uri.authority() else { return false; };
    if authority.as_str().contains('@') { return false; }
    let actual_port = authority.port_u16().unwrap_or(if scheme == "https" { 443 } else { 80 });
    let local_host = matches!(authority.host(), "localhost" | "127.0.0.1" | "[::1]");
    if local_host && actual_port == port { return true; }
    get_public_url(port).is_some_and(|public_url| {
        public_url.parse::<warp::http::Uri>().is_ok_and(|public| {
            public.scheme_str() == Some(scheme) && public.authority() == Some(authority)
        })
    })
}

#[cfg(test)]
mod security_tests {
    use super::*;

    #[test]
    fn accepts_local_devtools_origins() {
        for origin in ["http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080"] {
            assert!(is_origin_allowed(origin, 8080), "{origin}");
        }
    }

    #[test]
    fn rejects_foreign_origins_and_prefix_spoofing() {
        for origin in ["http://localhost:9999", "https://evil.ngrok.io", "http://10.evil.test:8080",
            "http://192.168.evil.test:8080", "http://172.16.evil.test:8080", "null",
            "http://localhost:8080@evil.test", "http://localhost:8080/path", "http://localhost:8080?x=1"] {
            assert!(!is_origin_allowed(origin, 8080), "{origin}");
        }
    }
}

#[cfg(test)]
mod refresh_tests {
    use super::*;
    use std::path::PathBuf;

    fn config() -> DevServerConfig {
        DevServerConfig {
            project_path: PathBuf::from("/app"),
            entry_path: PathBuf::from("/app/src/App.kt"),
            asset_paths: vec![PathBuf::from("/app/assets")],
            bundle_path: PathBuf::from("/app/.featherbundle"),
            generated_ir_path: Some(PathBuf::from("/app/build/generated/app.json")),
            fallback_ir_path: Some(PathBuf::from("/app/.feather/layout.json")),
            host: "127.0.0.1".parse().unwrap(),
            port: 8080,
            tunnel: false,
            manifest: serde_json::from_str(r#"{"entry":"src/App.kt"}"#).unwrap(),
        }
    }

    #[test]
    fn watches_source_assets_and_explicit_compiler_outputs() {
        let config = config();
        for path in ["/app/src/App.kt", "/app/ui/screen.fthr", "/app/assets/cover.png",
            "/app/build/generated/app.json", "/app/.feather/layout.json", "/app/.feather/app.wasm"] {
            assert!(is_refresh_input(&config, std::path::Path::new(path)), "{path}");
        }
    }

    #[test]
    fn excludes_generated_outputs_keys_logs_and_outside_paths() {
        let config = config();
        for path in ["/app/.featherbundle/app.feather.json", "/app/.feather/private_key.der",
            "/app/server.log", "/app/build/cache.kt", "/app/.git/source.kt", "/app/node_modules/app.fthr",
            "/elsewhere/App.kt", "/app/target/generated.kt", "/app/.feather/cache/generated.kt"] {
            assert!(!is_refresh_input(&config, std::path::Path::new(path)), "{path}");
        }
    }

    #[test]
    fn read_notifications_do_not_trigger_refresh() {
        let mut paths = std::collections::HashSet::new();
        let path = PathBuf::from("/app/src/App.kt");
        collect_refresh_paths(Ok(notify::Event::new(notify::EventKind::Access(
            notify::event::AccessKind::Read)).add_path(path.clone())), &config(), &mut paths);
        assert!(paths.is_empty());
        collect_refresh_paths(Ok(notify::Event::new(notify::EventKind::Modify(
            notify::event::ModifyKind::Any)).add_path(path.clone())), &config(), &mut paths);
        assert_eq!(paths, std::collections::HashSet::from([path]));
    }

    #[test]
    fn refresh_restores_nested_typed_state_and_publishes_it() {
        let runtime = Arc::new(AppRuntime::default());
        let state = serde_json::json!({"count": 8, "items": [true, null, {"name": "Ada"}]});
        runtime.set_state("session", &state.to_string());
        let (sender, mut receiver) = broadcast::channel(16);
        preserve_and_refresh_state(&runtime, &Arc::new(sender), || {
            runtime.set_state("session", "0");
        });
        assert_eq!(serde_json::from_str::<serde_json::Value>(&runtime.get_state("session").unwrap()).unwrap(), state);
        let update: serde_json::Value = serde_json::from_str(&receiver.try_recv().unwrap()).unwrap();
        assert_eq!(update["type"], "state_update");
        assert_eq!(update["value"], state);
    }
}

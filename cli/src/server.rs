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
use warp::Filter;

pub async fn run_server(config: DevServerConfig) {
    let host = config.host;
    let port = config.port;

    // Detect local IP for easier connection from mobile devices
    let my_local_ip = if host.is_loopback() || host.is_unspecified() {
        local_ip().unwrap_or(host)
    } else {
        host
    };

    let mut connect_url = format!("ws://{}:{}/ws", my_local_ip, port);
    let mut tunnel_msg = "".to_string();

    if config.tunnel {
        if let Some(public_url) = get_public_url(port) {
            connect_url = format!("{}/ws", public_url.replace("http", "ws"));
            tunnel_msg = format!("🌐  Public Tunnel: {}\n", public_url);
        } else {
            tunnel_msg = "⚠️   Tunneling requested but no environment provider found.\n    Install ngrok: 'ngrok http 8080'\n".to_string();
        }
    }

    println!("\n-------------------------------------------");
    println!("🪶  Feather Dev Server is active!");
    println!("🔗  Connect at: {}", connect_url);
    if !tunnel_msg.is_empty() {
        print!("{}", tunnel_msg);
    }
    println!("-------------------------------------------");
    println!("Scan this QR code with your phone to connect:");
    if let Err(e) = print_qr(&connect_url) {
        eprintln!("(Could not generate QR code: {})", e);
    }
    println!("-------------------------------------------\n");

    println!("Project: {}", config.project_path.display());
    println!("Entry: {}", config.entry_path.display());
    println!("Bundle: {}", config.bundle_path.display());
    if let Some(generated_ir_path) = &config.generated_ir_path {
        println!("Generated IR: {}", generated_ir_path.display());
    }

    let (tx, _) = broadcast::channel::<String>(16);
    let tx = Arc::new(tx);
    let runtime = Arc::new(AppRuntime::default());

    let previous_layout = Arc::new(std::sync::Mutex::new(None));
    {
        let initial_doc = render_document(&config, &runtime);
        if let Ok(val) = serde_json::from_str::<serde_json::Value>(&initial_doc) {
            if let Some(root) = val.get("root") {
                *previous_layout.lock().unwrap() = Some(root.to_string());
            }
        }
    }

    let tx_clone = Arc::clone(&tx);
    let watcher_config = config.clone();
    let watcher_runtime = Arc::clone(&runtime);
    let watcher_prev = Arc::clone(&previous_layout);
    tokio::spawn(async move {
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
             prev_layout: Arc<std::sync::Mutex<Option<String>>>,
             origin: Option<String>| {
                ws.on_upgrade(move |socket| handle_connection(socket, tx, config, runtime, prev_layout, origin))
            },
        );

    let devtools_html = include_str!("devtools.html");
    
    let devtools_route = warp::path!("devtools")
        .and(warp::get())
        .map(move || warp::reply::html(devtools_html));

    let devtools_index_route = warp::path!("devtools" / "index.html")
        .and(warp::get())
        .map(move || warp::reply::html(devtools_html));

    let routes = ws_route
        .or(bundle_route)
        .or(artifact_route)
        .or(devtools_route)
        .or(devtools_index_route);

    warp::serve(routes).run((host, port)).await;
}

fn watch_files(
    config: DevServerConfig,
    tx: Arc<broadcast::Sender<String>>,
    runtime: Arc<AppRuntime>,
    previous_layout: Arc<std::sync::Mutex<Option<String>>>,
) -> notify::Result<()> {
    let (std_tx, std_rx) = std::sync::mpsc::channel();

    let mut watcher = RecommendedWatcher::new(std_tx, Config::default())?;
    watcher.watch(&config.project_path, RecursiveMode::Recursive)?;

    println!(
        "Watching for file changes in {}...",
        config.project_path.display()
    );

    for res in std_rx {
        match res {
            Ok(event) => {
                // Ignore build and cache directories
                let should_ignore = event.paths.iter().any(|path| {
                    let p = path.to_string_lossy();
                    p.contains("/build/") || 
                    p.contains("/.gradle/") || 
                    p.contains("/.idea/") || 
                    p.contains("/.git/") ||
                    p.contains("/.featherbundle/") ||
                    p.contains("/node_modules/")
                });

                if should_ignore {
                    continue;
                }

                let changed_wasm = event
                    .paths
                    .iter()
                    .any(|path| path.extension().and_then(|ext| ext.to_str()) == Some("wasm"));
                
                if changed_wasm {
                    println!("WASM changed: {:?}", event.paths);
                    let _ = preserve_and_refresh_state(&runtime, &tx, || {
                        crate::bundler::build_bundle(&config, &runtime)
                    });
                    let _ = tx.send(
                        serde_json::json!({
                            "type": "hot-reload-wasm",
                            "path": "app.wasm"
                        })
                        .to_string(),
                    );
                } else {
                    println!("File changed: {:?}", event.paths);
                    let _ = preserve_and_refresh_state(&runtime, &tx, || {
                        handle_layout_update(&config, &runtime, &tx, &previous_layout)
                    });
                }
            }
            Err(e) => eprintln!("watch error: {:?}", e),
        }
    }

    Ok(())
}

async fn handle_connection(
    ws: warp::ws::WebSocket,
    tx: Arc<broadcast::Sender<String>>,
    config: DevServerConfig,
    runtime: Arc<AppRuntime>,
    previous_layout: Arc<std::sync::Mutex<Option<String>>>,
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
                                if msg_type == "log" {
                                    if let Some(log_msg) = json.get("message").and_then(|v| v.as_str()) {
                                        println!("📱 [DEVICE LOG]: {}", log_msg);
                                    }
                                    true
                                } else if msg_type == "error" {
                                    if let Some(err_msg) = json.get("message").and_then(|v| v.as_str()) {
                                        eprintln!("📱 🚨 [DEVICE ERROR]: {}", err_msg);
                                    }
                                    true
                                } else if msg_type == "devtools_state_update" {
                                    if let (Some(key), Some(value)) = (
                                        json.get("key").and_then(|v| v.as_str()),
                                        json.get("value")
                                    ) {
                                        let val_str = serde_json::to_string(value).unwrap_or_default();
                                        event_runtime.set_state(key, &val_str);
                                        
                                        let sync_packet = serde_json::json!({
                                            "type": "state_update",
                                            "key": key,
                                            "value": value
                                        });
                                        let _ = event_tx.send(sync_packet.to_string());
                                    }
                                    true
                                } else if msg_type == "action" {
                                    let _ = event_tx.send(text.to_string());
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
    previous_layout: &Arc<std::sync::Mutex<Option<String>>>,
) -> String {
    let new_doc = render_document(config, runtime);
    
    let new_val: serde_json::Value = match serde_json::from_str(&new_doc) {
        Ok(v) => v,
        Err(_) => {
            let _ = tx.send(new_doc.clone());
            return new_doc;
        }
    };
    
    let new_root = match new_val.get("root") {
        Some(r) => r.to_string(),
        None => {
            let _ = tx.send(new_doc.clone());
            return new_doc;
        }
    };

    let mut prev = previous_layout.lock().unwrap();
    if let Some(ref old_root) = *prev {
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
    
    *prev = Some(new_root);
    new_doc
}

fn get_public_url(port: u16) -> Option<String> {
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

fn extract_host(origin: &str) -> Option<&str> {
    let host_part = if origin.starts_with("http://") {
        &origin[7..]
    } else if origin.starts_with("https://") {
        &origin[8..]
    } else {
        return None;
    };

    let host_part = host_part.trim_end_matches('/');

    // If it's an IPv6 address, it starts with '['
    if host_part.starts_with('[') {
        if let Some(close_bracket_idx) = host_part.find(']') {
            return Some(&host_part[..close_bracket_idx + 1]);
        }
    }

    // For IPv4 or domain name, find the colon separating the port
    match host_part.find(':') {
        Some(idx) => Some(&host_part[..idx]),
        None => Some(host_part),
    }
}

fn is_origin_allowed(origin: &str, port: u16) -> bool {
    let host = match extract_host(origin) {
        Some(h) => h,
        None => return false,
    };

    // Standard local loopbacks
    if host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]" {
        return true;
    }

    // Local private subnets (e.g. 192.168.x.x, 10.x.x.x, 172.16.x.x-172.31.x.x)
    if host.starts_with("192.168.") || host.starts_with("10.") {
        return true;
    }
    if host.starts_with("172.") {
        if let Some(second_octet) = host.split('.').nth(1).and_then(|s| s.parse::<u8>().ok()) {
            if second_octet >= 16 && second_octet <= 31 {
                return true;
            }
        }
    }

    // Tunnel/workstation domains
    if host.ends_with(".ngrok.io") || host.ends_with(".ngrok-free.app") {
        return true;
    }

    // Dynamic public tunnel/GCloud/Codespaces validation
    if let Some(public_url) = get_public_url(port) {
        if let Some(p_host) = extract_host(&public_url) {
            if host == p_host {
                return true;
            }
        }
    }

    false
}

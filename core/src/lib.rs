pub mod javascript;
pub mod gc;
pub mod rewriter;
pub mod state;
pub mod bundle;
pub mod wasm;

uniffi::setup_scaffolding!();

pub use state::StateValue;
pub use bundle::{BundleManifest, BundleArtifact, BundleModule, BundleDelta, BundleBuild, artifact_for, discover_modules, read_previous_manifest, with_delta};
pub use wasm::WasmRuntime;

use std::collections::HashMap;
use std::sync::Arc;
use std::sync::Mutex;

// ─── UniFFI Exports ───────────────────────────────────────────────────────────

#[uniffi::export]
pub fn get_greeting() -> String {
    "Hello from Feather Core (Rust)!".to_string()
}

#[uniffi::export]
pub fn diff_layouts(old_json: String, new_json: String) -> Vec<bundle::diff::UiPatch> {
    bundle::diff::diff_layouts(&old_json, &new_json)
}

#[uniffi::export]
pub fn verify_manifest_signature(manifest_json: String, signature_b64: String, public_key_b64: String) -> bool {
    use ring::signature::{self, UnparsedPublicKey};
    use base64::{prelude::BASE64_STANDARD, Engine as _};
    use serde_json::Value;

    let Ok(mut val) = serde_json::from_str::<Value>(&manifest_json) else {
        return false;
    };
    if let Some(obj) = val.as_object_mut() {
        obj.remove("signature");
    }
    let Ok(cleaned_json_bytes) = serde_json::to_vec(&val) else {
        return false;
    };

    let Ok(pubkey_bytes) = BASE64_STANDARD.decode(public_key_b64.trim()) else {
        return false;
    };
    let Ok(sig_bytes) = BASE64_STANDARD.decode(signature_b64.trim()) else {
        return false;
    };

    let peer_public_key = UnparsedPublicKey::new(&signature::ED25519, &pubkey_bytes);
    peer_public_key.verify(&cleaned_json_bytes, &sig_bytes).is_ok()
}

#[derive(uniffi::Object)]
pub struct Engine {
    state: Mutex<String>,
}

#[uniffi::export]
impl Engine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Engine {
            state: Mutex::new("Initialized".to_string()),
        }
    }

    pub fn start(&self) {
        let mut state = self.state.lock().unwrap();
        *state = "Running".to_string();
        println!("Feather Engine started.");
    }

    pub fn get_state(&self) -> String {
        let state = self.state.lock().unwrap();
        state.clone()
    }
}

// ─── App Runtime ──────────────────────────────────────────────────────────────

#[derive(Clone, Debug)]
pub struct ModuleCall {
    pub module_name: String,
    pub action: String,
    pub payload: String,
    pub cb_key: String,
}

#[derive(Clone, Debug, serde::Serialize, uniffi::Record)]
pub struct NativeEvent {
    pub name: String,
    pub payload: String,
}

#[derive(Default, uniffi::Object)]
pub struct AppRuntime {
    state: Arc<Mutex<HashMap<String, StateValue>>>,
    needs_render: Arc<Mutex<bool>>,
    modules: Mutex<HashMap<String, Arc<dyn NativeModule>>>,
    custom_widgets: Mutex<HashMap<String, String>>, // WidgetName -> ModuleName
    wasm_engine: Mutex<Option<WasmRuntime>>,
    js_engine: Mutex<Option<javascript::JavaScriptRuntime>>,
    console_buffer: Arc<Mutex<Vec<String>>>,
    diagnostics_buffer: Arc<Mutex<Vec<Diagnostic>>>,
    action_payload: Arc<Mutex<String>>,
    pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
    next_request_id: std::sync::atomic::AtomicU64,
    event_queue: Arc<Mutex<Vec<NativeEvent>>>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ModuleError {
    #[error("Module execution failed: {err_msg}")]
    ExecutionFailed { err_msg: String },
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct Diagnostic {
    pub level: String,
    pub message: String,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct ModuleInfo {
    pub name: String,
    pub version: String,
}

#[uniffi::export(with_foreign)]
pub trait NativeModule: Send + Sync {
    fn get_info(&self) -> ModuleInfo;
    fn get_widgets(&self) -> Vec<String>;
    fn call_sync(&self, action: String, payload: String) -> Result<String, ModuleError>;
    fn call_async(&self, action: String, payload: String, callback: std::sync::Arc<dyn NativeCallback>);
}

#[uniffi::export(with_foreign)]
pub trait NativeCallback: Send + Sync {
    fn on_success(&self, result: String);
    fn on_error(&self, error: String);
}

#[uniffi::export]
impl AppRuntime {
    #[uniffi::constructor]
    pub fn new() -> Self {
        AppRuntime {
            state: Arc::new(Mutex::new(HashMap::new())),
            needs_render: Arc::new(Mutex::new(false)),
            modules: Mutex::new(HashMap::new()),
            custom_widgets: Mutex::new(HashMap::new()),
            wasm_engine: Mutex::new(None),
            js_engine: Mutex::new(None),
            console_buffer: Arc::new(Mutex::new(Vec::new())),
            diagnostics_buffer: Arc::new(Mutex::new(Vec::new())),
            action_payload: Arc::new(Mutex::new(String::new())),
            pending_module_calls: Arc::new(Mutex::new(Vec::new())),
            next_request_id: std::sync::atomic::AtomicU64::new(0),
            event_queue: Arc::new(Mutex::new(Vec::new())),
        }
    }

    /// Load code only after the app-pinned key authenticates the manifest and
    /// the signed JavaScript artifact metadata matches the exact source bytes.
    pub fn load_signed_javascript(&self, manifest_json: String, source: String, public_key: String) -> Result<(), ModuleError> {
        let failure = |message: &str| ModuleError::ExecutionFailed { err_msg: message.into() };
        let manifest: BundleManifest = serde_json::from_str(&manifest_json).map_err(|_| failure("Invalid bundle manifest"))?;
        let signature = manifest.signature.clone().ok_or_else(|| failure("Unsigned bundle"))?;
        if !verify_manifest_signature(manifest_json, signature, public_key) {
            return Err(failure("Manifest signature verification failed"));
        }
        if manifest.protocol_version != 1 || manifest.sdk_version != "1.0.0"
            || manifest.runtime.as_deref() != Some("kotlin-js") || manifest.wasm.is_some() {
            return Err(failure("Unsupported bundle protocol, SDK or runtime"));
        }
        let artifact = manifest.javascript.ok_or_else(|| failure("Missing JavaScript artifact"))?;
        if artifact.path != "app.js" || artifact.bytes != source.len() as u64
            || artifact.sha256 != artifact_for("app.js", source.as_bytes()).sha256 {
            return Err(failure("JavaScript artifact verification failed"));
        }
        self.load_javascript(source)
    }

    pub fn load_javascript(&self, source: String) -> Result<(), ModuleError> {
        use sha2::Digest;
        let hash = hex::encode(sha2::Sha256::digest(source.as_bytes()));
        let mut current = self.js_engine.lock().unwrap();
        if current.as_ref().is_some_and(|engine| engine.source_hash == hash) { return Ok(()); }
        // New code initializes against a private snapshot. A bad reload must not
        // mutate the live state or enqueue native side effects.
        let mut live_state = self.state.lock().unwrap();
        let staged_state = Arc::new(Mutex::new(live_state.clone()));
        let staged_logs = Arc::new(Mutex::new(Vec::new()));
        let staged_calls = Arc::new(Mutex::new(Vec::new()));
        let engine = javascript::JavaScriptRuntime::new(&source, staged_state.clone(),
            self.needs_render.clone(), staged_logs.clone(), staged_calls.clone())
            .map_err(|err_msg| ModuleError::ExecutionFailed { err_msg })?;
        engine.render().map_err(|err_msg| ModuleError::ExecutionFailed { err_msg })?;
        *live_state = staged_state.lock().unwrap().clone();
        engine.bind_state(self.state.clone());
        self.console_buffer.lock().unwrap().extend(staged_logs.lock().unwrap().drain(..));
        self.pending_module_calls.lock().unwrap().extend(staged_calls.lock().unwrap().drain(..));
        // Rebind logs/module queues for subsequent calls as well.
        engine.bind_outputs(self.console_buffer.clone(), self.pending_module_calls.clone());
        *current = Some(engine);
        *self.wasm_engine.lock().unwrap() = None;
        *self.needs_render.lock().unwrap() = true;
        Ok(())
    }

    pub fn render_javascript(&self) -> Result<String, ModuleError> {
        let guard = self.js_engine.lock().unwrap();
        let engine = guard.as_ref().ok_or_else(|| ModuleError::ExecutionFailed { err_msg: "No JavaScript application loaded".into() })?;
        let events = self.drain_native_events();
        if !events.is_empty() {
            engine.events(serde_json::to_string(&events).map_err(|error| ModuleError::ExecutionFailed { err_msg: error.to_string() })?)
                .map_err(|err_msg| ModuleError::ExecutionFailed { err_msg })?;
        }
        engine.render().map_err(|err_msg| ModuleError::ExecutionFailed { err_msg })
    }

    pub fn has_javascript(&self) -> bool { self.js_engine.lock().unwrap().is_some() }

    pub fn render_javascript_document(&self) -> Result<String, ModuleError> {
        let root: serde_json::Value = serde_json::from_str(&self.render_javascript()?)
            .map_err(|error| ModuleError::ExecutionFailed { err_msg: error.to_string() })?;
        let state: serde_json::Value = serde_json::from_str(&self.get_all_state())
            .map_err(|error| ModuleError::ExecutionFailed { err_msg: error.to_string() })?;
        let state = state.as_object().unwrap().iter().map(|(key, value)| {
            (key.clone(), serde_json::Value::String(value.as_str().map(str::to_string).unwrap_or_else(|| value.to_string())))
        }).collect::<serde_json::Map<String, serde_json::Value>>();
        let diagnostics = self.drain_diagnostics().into_iter().map(|diagnostic| serde_json::json!({"level":diagnostic.level,"message":diagnostic.message})).collect::<Vec<_>>();
        Ok(self.resolve_state_text_nodes(serde_json::json!({"type":"render", "protocol_version":1, "root":root, "state":state, "diagnostics":diagnostics}).to_string()))
    }

    pub fn load_wasm(&self, wasm_bytes: Vec<u8>) -> Result<(), ModuleError> {
        // Already supported modules must retain their original semantics; only
        // attempt the experimental GC translation when native validation fails.
        let baseline_wasm = if wasmi::Module::new(&wasmi::Engine::default(), &wasm_bytes[..]).is_ok() {
            wasm_bytes
        } else {
            crate::rewriter::GcRewriter::new(&wasm_bytes).rewrite()
                .map_err(|error| ModuleError::ExecutionFailed { err_msg: format!("Unsupported WASM module: {error}") })?
        };

        let runtime = WasmRuntime::new(
            &baseline_wasm, 
            self.state.clone(), 
            self.console_buffer.clone(),
            self.action_payload.clone(),
            self.pending_module_calls.clone(),
            self.event_queue.clone(),
        ).map_err(|e| ModuleError::ExecutionFailed { err_msg: format!("WASM error: {}", e) })?;
        *self.js_engine.lock().unwrap() = None;
        *self.wasm_engine.lock().unwrap() = Some(runtime);
        Ok(())
    }

    pub fn register_module(&self, name: String, module: Arc<dyn NativeModule>) {
        let widgets = module.get_widgets();
        let mut custom_widgets = self.custom_widgets.lock().unwrap();
        for widget in widgets {
            custom_widgets.insert(widget, name.clone());
        }
        self.modules.lock().unwrap().insert(name, module);
    }

    pub fn get_registered_modules(&self) -> Vec<ModuleInfo> {
        self.modules.lock().unwrap().values().map(|m| m.get_info()).collect()
    }

    pub fn check_requirements(&self, required_modules: Vec<BundleModule>, host_platform: String) -> Vec<String> {
        let registered = self.modules.lock().unwrap();
        let mut missing = Vec::new();

        for req in required_modules {
            if !registered.contains_key(&req.name) {
                let err_msg = if !req.platforms.is_empty() && !req.platforms.contains(&host_platform) {
                    format!("Module {} is not supported on {}", req.name, host_platform)
                } else {
                    format!("Missing required module: {}", req.name)
                };
                self.report_diagnostic("ERROR".to_string(), err_msg.clone());
                missing.push(err_msg);
            }
        }

        missing
    }

    pub fn hot_reload_wasm(&self, wasm_bytes: Vec<u8>) -> Result<(), ModuleError> {
        let mut rewriter = crate::rewriter::GcRewriter::new(&wasm_bytes);
        let baseline_wasm = match rewriter.rewrite() {
            Ok(bytes) => bytes,
            Err(e) => {
                println!("Feather: Hot-reload rewrite failed: {}", e);
                wasm_bytes
            }
        };

        let runtime = WasmRuntime::new(
            &baseline_wasm, 
            self.state.clone(), 
            self.console_buffer.clone(),
            self.action_payload.clone(),
            self.pending_module_calls.clone(),
            self.event_queue.clone(),
        ).map_err(|e| ModuleError::ExecutionFailed { err_msg: format!("Hot-reload error: {}", e) })?;
        *self.wasm_engine.lock().unwrap() = Some(runtime);
        *self.needs_render.lock().unwrap() = true;
        Ok(())
    }

    pub fn drain_console_logs(&self) -> Vec<String> {
        let mut buffer = self.console_buffer.lock().unwrap();
        let logs = buffer.clone();
        buffer.clear();
        logs
    }

    pub fn drain_diagnostics(&self) -> Vec<Diagnostic> {
        let mut buffer = self.diagnostics_buffer.lock().unwrap();
        let diagnostics = buffer.clone();
        buffer.clear();
        diagnostics
    }

    pub fn report_diagnostic(&self, level: String, message: String) {
        self.diagnostics_buffer.lock().unwrap().push(Diagnostic { level, message });
    }

    pub fn push_native_event(&self, name: String, payload: String) {
        self.event_queue.lock().unwrap().push(NativeEvent { name, payload });
        *self.needs_render.lock().unwrap() = true;
    }

    pub fn drain_native_events(&self) -> Vec<NativeEvent> {
        let mut queue = self.event_queue.lock().unwrap();
        let events = queue.clone();
        queue.clear();
        events
    }

    pub fn deliver_module_result(&self, callback_id: String, result_json: String) {
        let mut state = self.state.lock().unwrap();
        match serde_json::from_str::<serde_json::Value>(&result_json) {
            Ok(val) => { state.insert(callback_id, StateValue::from_json_value(&val)); }
            Err(_) => { state.insert(callback_id, StateValue::Str(result_json)); }
        }
        drop(state);
        *self.needs_render.lock().unwrap() = true;
    }

    pub fn deliver_module_error(&self, callback_id: String, error: String) {
        let mut state = self.state.lock().unwrap();
        state.insert(format!("{}_error", callback_id), StateValue::Str(error));
        drop(state);
        *self.needs_render.lock().unwrap() = true;
    }

    pub fn has_wasm(&self) -> bool {
        self.wasm_engine.lock().unwrap().is_some()
    }

    pub fn render_wasm(&self) -> Option<String> {
        let mut engine = self.wasm_engine.lock().unwrap();
        if let Some(ref mut wasm) = *engine {
            match wasm.call_render() {
                Ok(json) if !json.is_empty() => Some(json),
                Ok(_) => None,
                Err(e) => {
                    println!("WASM render error: {}", e);
                    None
                }
            }
        } else {
            None
        }
    }

    pub fn poll_render_needed(&self) -> bool {
        let calls = {
            let mut queue = self.pending_module_calls.lock().unwrap();
            let c = queue.clone();
            queue.clear();
            c
        };
        for call in calls {
            let modules = self.modules.lock().unwrap();
            if let Some(m) = modules.get(&call.module_name) {
                struct Cb {
                    cb_key: String,
                    state: Arc<Mutex<HashMap<String, StateValue>>>,
                    needs_render: Arc<Mutex<bool>>,
                }
                impl NativeCallback for Cb {
                    fn on_success(&self, result: String) {
                        let mut state = self.state.lock().unwrap();
                        if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(&result) {
                            state.insert(self.cb_key.clone(), StateValue::from_json_value(&parsed));
                        } else {
                            state.insert(self.cb_key.clone(), StateValue::Str(result));
                        }
                        *self.needs_render.lock().unwrap() = true;
                    }
                    fn on_error(&self, error: String) {
                        let mut state = self.state.lock().unwrap();
                        state.insert(format!("{}_error", self.cb_key), StateValue::Str(error));
                        *self.needs_render.lock().unwrap() = true;
                    }
                }
                m.call_async(call.action, call.payload, Arc::new(Cb {
                    cb_key: call.cb_key,
                    state: self.state.clone(),
                    needs_render: self.needs_render.clone(),
                }));
            }
        }

        let mut needed = self.needs_render.lock().unwrap();
        let val = *needed;
        *needed = false;
        val
    }

    pub fn drain_module_calls(&self) -> Vec<String> {
        let mut queue = self.pending_module_calls.lock().unwrap();
        let calls = queue.clone();
        queue.clear();
        
        calls.iter().map(|c| {
            serde_json::json!({
                "type": "module_call",
                "id": format!("request_{}", self.next_request_id.fetch_add(1, std::sync::atomic::Ordering::Relaxed)),
                "module": c.module_name,
                "method": c.action,
                "payload": serde_json::from_str::<serde_json::Value>(&c.payload).unwrap_or(serde_json::Value::Null),
                "resultStateKey": c.cb_key
            }).to_string()
        }).collect()
    }

    pub fn apply_action_event(&self, payload: &str) {
        let action = payload.trim();
        
        let (action_str, payload_val) = if action.starts_with('{') {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(action) {
                if json.get("type").and_then(|v| v.as_str()) == Some("module_call") {
                    let module_name = json.get("module").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    let method = json.get("method").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    let payload_val = json.get("payload").cloned().unwrap_or(serde_json::Value::Null);
                    let cb_key = json.get("resultStateKey").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    
                    if module_name.is_empty() || method.is_empty() {
                        self.report_diagnostic("error".into(), "Module requests require module and method".into());
                        return;
                    }
                    let mut queue = self.pending_module_calls.lock().unwrap();
                    if queue.len() >= 128 {
                        self.report_diagnostic("error".into(), "Module request queue is full".into());
                        return;
                    }
                    queue.push(ModuleCall {
                        module_name,
                        action: method,
                        payload: payload_val.to_string(),
                        cb_key,
                    });
                    *self.needs_render.lock().unwrap() = true;
                    return;
                }

                let a = json.get("action").and_then(|v| v.as_str()).unwrap_or(action).to_string();
                let p = json.get("payload").cloned().unwrap_or(serde_json::Value::Null);
                (a, p)
            } else { (action.to_string(), serde_json::Value::Null) }
        } else { (action.to_string(), serde_json::Value::Null) };

        // SDK button actions may contain a serialized module descriptor. Route
        // that intent to the native bridge rather than to an app action handler.
        if serde_json::from_str::<serde_json::Value>(&action_str).ok()
            .is_some_and(|value| value.get("type").and_then(|v| v.as_str()) == Some("module_call")) {
            self.apply_action_event(&action_str);
            return;
        }

        if let Some(engine) = self.js_engine.lock().unwrap().as_ref() {
            if let Err(error) = engine.action(&action_str, Some(payload_val.to_string())) {
                self.report_diagnostic("error".to_string(), format!("JavaScript action failed: {error}"));
            }
            *self.needs_render.lock().unwrap() = true;
            return;
        }

        if let Some(engine) = self.wasm_engine.lock().unwrap().as_mut() {
            if let Err(e) = engine.call_action(&action_str) {
                println!("WASM action error: {}", e);
            }
            *self.needs_render.lock().unwrap() = true;
            return;
        }

        let mut state = self.state.lock().unwrap();
        
        if action_str.starts_with("module:") {
            let parts: Vec<&str> = action_str[7..].split('/').collect();
            if parts.len() == 2 {
                let module_name = parts[0];
                let module_action = parts[1];
                let modules = self.modules.lock().unwrap();
                if let Some(module) = modules.get(module_name) {
                    let payload_str = serde_json::to_string(&payload_val).unwrap_or_default();
                    if let Ok(result) = module.call_sync(module_action.to_string(), payload_str) {
                        if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(&result) {
                            state.insert(action_str.to_string(), StateValue::from_json_value(&parsed));
                        } else {
                            state.insert(action_str.to_string(), StateValue::Str(result));
                        }
                    }
                }
            }
        } else if let Some(key) = action_str.strip_suffix("++") {
            let key = key.trim();
            let next = match state.get(key) {
                Some(StateValue::Int(i)) => *i + 1,
                Some(StateValue::Str(s)) => s.parse::<i64>().unwrap_or(0) + 1,
                _ => 1,
            };
            state.insert(key.to_string(), StateValue::Int(next));
        } else if let Some(key) = action_str.strip_suffix("--") {
            let key = key.trim();
            let next = match state.get(key) {
                Some(StateValue::Int(i)) => *i - 1,
                Some(StateValue::Str(s)) => s.parse::<i64>().unwrap_or(0) - 1,
                _ => -1,
            };
            state.insert(key.to_string(), StateValue::Int(next));
        } else if let Some(eq_pos) = action_str.find('=') {
            let key = action_str[..eq_pos].trim();
            let mut val_str = action_str[eq_pos + 1..].trim();
            if val_str.starts_with('"') && val_str.ends_with('"') {
                val_str = val_str.trim_matches('"');
            }
            if let Ok(i) = val_str.parse::<i64>() {
                state.insert(key.to_string(), StateValue::Int(i));
            } else if val_str == "true" {
                state.insert(key.to_string(), StateValue::Bool(true));
            } else if val_str == "false" {
                state.insert(key.to_string(), StateValue::Bool(false));
            } else {
                state.insert(key.to_string(), StateValue::Str(val_str.to_string()));
            }
        } else {
            match payload_val {
                serde_json::Value::String(value) => { state.insert(action_str.to_string(), StateValue::Str(value)); }
                serde_json::Value::Bool(b) => { state.insert(action_str.to_string(), StateValue::Bool(b)); }
                serde_json::Value::Number(n) => {
                    if let Some(i) = n.as_i64() { state.insert(action_str.to_string(), StateValue::Int(i)); }
                    else if let Some(f) = n.as_f64() { state.insert(action_str.to_string(), StateValue::Float(f)); }
                }
                serde_json::Value::Array(_) | serde_json::Value::Object(_) => {
                    let typed = StateValue::from_json_value(&payload_val);
                    state.insert(action_str.to_string(), typed);
                }
                serde_json::Value::Null => {
                    let next = match state::get_nested_state(&state, &action_str) {
                        Some(StateValue::Int(n)) => n + 1,
                        Some(StateValue::Str(s)) => s.parse::<i64>().unwrap_or(0) + 1,
                        _ => 1,
                    };
                    state.insert(action_str.to_string(), StateValue::Int(next));
                }
            }
        }
        
        drop(state);
        *self.needs_render.lock().unwrap() = true;
    }

    pub fn set_state(&self, key: &str, json_value: &str) {
        let mut state = self.state.lock().unwrap();
        match serde_json::from_str::<serde_json::Value>(json_value) {
            Ok(value) => {
                state.insert(key.to_string(), StateValue::from_json_value(&value));
            }
            Err(_) => {
                state.insert(key.to_string(), StateValue::Str(json_value.to_string()));
            }
        }
        *self.needs_render.lock().unwrap() = true;
    }

    pub fn get_state(&self, key: &str) -> Option<String> {
        let state = self.state.lock().unwrap();
        state
            .get(key)
            .map(|v| serde_json::to_string(v).unwrap_or_default())
    }

    pub fn get_all_state(&self) -> String {
        let state = self.state.lock().unwrap();
        serde_json::to_string(&*state).unwrap_or_else(|_| "{}".to_string())
    }

    pub fn resolve_state_text_nodes(&self, json_payload: String) -> String {
        let Ok(mut value) = serde_json::from_str::<serde_json::Value>(&json_payload) else {
            return json_payload;
        };

        self.resolve_inner(&mut value);
        serde_json::to_string(&value).unwrap_or(json_payload)
    }

    pub fn state_value(&self, key: &str) -> Option<String> {
        let state = self.state.lock().unwrap();
        state::get_nested_state(&state, key).map(|v| v.as_str())
    }

    pub fn state_bool(&self, key: &str) -> bool {
        self.state
            .lock()
            .unwrap()
            .get(key)
            .map(|v| v.as_bool())
            .unwrap_or(false)
    }

    pub fn state_list(&self, key: &str) -> Vec<String> {
        self.state
            .lock()
            .unwrap()
            .get(key)
            .map(|v| match v {
                StateValue::List(items) => {
                    items.iter().map(|i| serde_json::to_string(i).unwrap_or_default()).collect()
                }
                _ => Vec::new(),
            })
            .unwrap_or_default()
    }
}

impl AppRuntime {
    fn resolve_inner(&self, value: &mut serde_json::Value) {
        match value {
            serde_json::Value::Object(object) => {
                let node_type = object
                    .get("type")
                    .and_then(serde_json::Value::as_str)
                    .unwrap_or_default()
                    .to_string();

                match node_type.as_str() {
                    "StateText" => {
                        let key = object
                            .get("key")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let prefix = object
                            .get("prefix")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or_default();
                        let fallback = object
                            .get("fallback")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or_default()
                            .to_string();

                        let state = self.state.lock().unwrap();
                        let text = state::get_nested_state(&state, &key)
                            .map(|value| format!("{prefix}{}", value.as_str()))
                            .unwrap_or(fallback);

                        object.clear();
                        object.insert("type".to_string(), serde_json::Value::String("Text".to_string()));
                        object.insert("text".to_string(), serde_json::Value::String(text));
                    }
                    "Condition" => {
                        let key = object
                            .get("key")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or_default()
                            .to_string();

                        let state = self.state.lock().unwrap();
                        let condition_met = state::get_nested_state(&state, &key)
                            .map(|v| v.as_bool())
                            .unwrap_or(false);
                        drop(state);

                        if condition_met {
                            if let Some(then_node) = object.get("then").cloned() {
                                *value = then_node;
                                self.resolve_inner(value);
                            }
                        } else if let Some(otherwise_node) = object.get("otherwise").cloned() {
                            *value = otherwise_node;
                            self.resolve_inner(value);
                        } else {
                            object.clear();
                            object.insert("type".to_string(), serde_json::Value::String("Spacer".to_string()));
                            object.insert("flex".to_string(), serde_json::Value::Number(0.into()));
                        }
                    }
                    "ForEach" => {
                        let items_key = object
                            .get("itemsKey")
                            .and_then(serde_json::Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let template = object.get("template").cloned();

                        let state = self.state.lock().unwrap();
                        let items = state::get_nested_state(&state, &items_key).map(|v| v.as_list()).unwrap_or_default();
                        drop(state);

                        if let Some(template) = template {
                            let mut children = Vec::new();
                            for (index, item) in items.iter().enumerate() {
                                let mut child = template.clone();
                                let child_str = serde_json::to_string(&child)
                                    .unwrap_or_default()
                                    .replace("$item", &item.as_str())
                                    .replace("$index", &index.to_string());
                                if let Ok(resolved) = serde_json::from_str::<serde_json::Value>(&child_str) {
                                    child = resolved;
                                }
                                self.resolve_inner(&mut child);
                                children.push(child);
                            }

                            object.clear();
                            object.insert("type".to_string(), serde_json::Value::String("Column".to_string()));
                            object.insert("children".to_string(), serde_json::Value::Array(children));
                        }
                    }
                    _ => {
                        object.values_mut().for_each(|child| {
                            self.resolve_inner(child);
                        });
                    }
                }
            }
            serde_json::Value::Array(children) => {
                children.iter_mut().for_each(|child| {
                    self.resolve_inner(child);
                });
            }
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use std::sync::{Arc, Mutex};

    #[test]
    fn test_memory_bridge_read_string() {
        let wat = r#"
            (module
                (import "env" "set_render_tree" (func $set_render_tree (param i32 i32)))
                (memory (export "memory") 1)
                (data (i32.const 42) "Hello, Virtual DOM!")
                
                (func (export "handle_action")
                    (call $set_render_tree (i32.const 42) (i32.const 19))
                )
            )
        "#;
        
        let wasm_bytes = wat::parse_str(wat).unwrap();
        let state = Arc::new(Mutex::new(HashMap::new()));
        let console_buffer = Arc::new(Mutex::new(Vec::new()));
        let action_payload = Arc::new(Mutex::new(String::new()));
        let pending_module_calls = Arc::new(Mutex::new(Vec::new()));
        let event_queue = Arc::new(Mutex::new(Vec::new()));
        
        let mut runtime = WasmRuntime::new(
            &wasm_bytes, 
            state, 
            console_buffer,
            action_payload,
            pending_module_calls,
            event_queue
        ).expect("Failed to initialize WasmRuntime");
        
        runtime.call_action("trigger").expect("Failed to call action");
        
        let render_tree = runtime.call_render().expect("Failed to call render");
        assert_eq!(render_tree, "Hello, Virtual DOM!");
    }

    #[test]
    fn test_wasmgc_ref_cast_trap() {
        let wat = r#"
            (module
                (type $Base (struct (field i32)))
                (type $Derived (struct (field i32) (field i32)))
                
                (import "env" "gc_struct_new_default" (func $gc_struct_new_default (param i32) (result i32)))
                (import "env" "gc_ref_cast" (func $gc_ref_cast (param i32 i32) (result i32)))

                (func (export "handle_action")
                    ;; Allocate a Base (type index 0)
                    (call $gc_struct_new_default (i32.const 0))
                    ;; Cast it to Derived (type index 1) -> should trap
                    (i32.const 1)
                    (call $gc_ref_cast)
                    drop
                )
            )
        "#;
        
        let wasm_bytes = wat::parse_str(wat).unwrap();
        // Since we are testing the REWRITTEN module, but here we manually use the import names that match my rewriter's target.
        // Actually, it's easier to just use the GcRewriter on a real WasmGC module.
        
        let mut rewriter = crate::rewriter::GcRewriter::new(&wasm_bytes);
        let rewritten = rewriter.rewrite().unwrap();

        let state = Arc::new(Mutex::new(HashMap::new()));
        let console_buffer = Arc::new(Mutex::new(Vec::new()));
        let action_payload = Arc::new(Mutex::new(String::new()));
        let pending_module_calls = Arc::new(Mutex::new(Vec::new()));
        let event_queue = Arc::new(Mutex::new(Vec::new()));
        
        let mut runtime = WasmRuntime::new(
            &rewritten, 
            state, 
            console_buffer,
            action_payload,
            pending_module_calls,
            event_queue
        ).expect("Failed to initialize WasmRuntime");
        
        let result = runtime.call_action("trigger");
        assert!(result.is_err(), "Cast from Base to Derived should have trapped");
    }
}

pub mod gc;
pub mod rewriter;

uniffi::setup_scaffolding!();

use hex::encode as hex_encode;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use wasmi::{Engine as WasmEngine, Linker, Module, Store};

// ─── Typed State System ───────────────────────────────────────────────────────

/// Typed state values that go beyond simple String counters.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum StateValue {
    Null,
    Bool(bool),
    Int(i64),
    Float(f64),
    Str(String),
    List(Vec<StateValue>),
    Map(HashMap<String, StateValue>),
}

fn get_nested_state<'a>(state: &'a HashMap<String, StateValue>, path: &str) -> Option<&'a StateValue> {
    if let Some(val) = state.get(path) {
        return Some(val);
    }
    let normalized = path.replace('[', ".").replace(']', "");
    let mut parts = normalized.split('.');
    let first = parts.next()?;
    let mut current = state.get(first)?;

    for part in parts {
        if part.is_empty() { continue; }
        match current {
            StateValue::Map(m) => { current = m.get(part)?; }
            StateValue::List(l) => {
                if let Ok(idx) = part.parse::<usize>() { current = l.get(idx)?; } else { return None; }
            }
            _ => return None,
        }
    }
    Some(current)
}

impl StateValue {
    pub fn as_str(&self) -> String {
        match self {
            StateValue::Null => "null".to_string(),
            StateValue::Bool(b) => b.to_string(),
            StateValue::Int(n) => n.to_string(),
            StateValue::Float(f) => f.to_string(),
            StateValue::Str(s) => s.clone(),
            StateValue::List(items) => serde_json::to_string(items).unwrap_or_default(),
            StateValue::Map(map) => serde_json::to_string(map).unwrap_or_default(),
        }
    }

    pub fn as_bool(&self) -> bool {
        match self {
            StateValue::Null => false,
            StateValue::Bool(b) => *b,
            StateValue::Int(n) => *n != 0,
            StateValue::Float(f) => *f != 0.0,
            StateValue::Str(s) => !s.is_empty() && s != "false" && s != "0",
            StateValue::List(items) => !items.is_empty(),
            StateValue::Map(map) => !map.is_empty(),
        }
    }

    pub fn as_int(&self) -> i64 {
        match self {
            StateValue::Null => 0,
            StateValue::Bool(b) => if *b { 1 } else { 0 },
            StateValue::Int(n) => *n,
            StateValue::Float(f) => *f as i64,
            StateValue::Str(s) => s.parse().unwrap_or(0),
            StateValue::List(items) => items.len() as i64,
            StateValue::Map(map) => map.len() as i64,
        }
    }

    pub fn as_float(&self) -> f64 {
        match self {
            StateValue::Null => 0.0,
            StateValue::Bool(b) => if *b { 1.0 } else { 0.0 },
            StateValue::Int(n) => *n as f64,
            StateValue::Float(f) => *f,
            StateValue::Str(s) => s.parse().unwrap_or(0.0),
            _ => 0.0,
        }
    }

    pub fn as_list(&self) -> Vec<StateValue> {
        match self {
            StateValue::List(items) => items.clone(),
            _ => Vec::new(),
        }
    }

    fn from_json_value(value: &Value) -> StateValue {
        match value {
            Value::Null => StateValue::Null,
            Value::Bool(b) => StateValue::Bool(*b),
            Value::Number(n) => {
                if let Some(i) = n.as_i64() {
                    StateValue::Int(i)
                } else {
                    StateValue::Float(n.as_f64().unwrap_or(0.0))
                }
            }
            Value::String(s) => StateValue::Str(s.clone()),
            Value::Array(arr) => {
                StateValue::List(arr.iter().map(StateValue::from_json_value).collect())
            }
            Value::Object(map) => {
                let mut result = HashMap::new();
                for (key, val) in map {
                    result.insert(key.clone(), StateValue::from_json_value(val));
                }
                StateValue::Map(result)
            }
        }
    }
}

// ─── UniFFI Exports ───────────────────────────────────────────────────────────

#[uniffi::export]
pub fn get_greeting() -> String {
    "Hello from Feather Core (Rust)!".to_string()
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

// ─── App Runtime (Typed State) ────────────────────────────────────────────────

#[derive(Clone, Debug)]
pub struct ModuleCall {
    pub module_name: String,
    pub action: String,
    pub payload: String,
    pub cb_key: String,
}

#[derive(Default, uniffi::Object)]
pub struct AppRuntime {
    state: Arc<Mutex<HashMap<String, StateValue>>>,
    needs_render: Arc<Mutex<bool>>,
    modules: Mutex<HashMap<String, Arc<dyn NativeModule>>>,
    wasm_engine: Mutex<Option<WasmRuntime>>,
    console_buffer: Arc<Mutex<Vec<String>>>,
    action_payload: Arc<Mutex<String>>,
    pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ModuleError {
    #[error("Module execution failed: {0}")]
    ExecutionFailed(String),
}

#[uniffi::export(with_foreign)]
pub trait NativeModule: Send + Sync {
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
            wasm_engine: Mutex::new(None),
            console_buffer: Arc::new(Mutex::new(Vec::new())),
            action_payload: Arc::new(Mutex::new(String::new())),
            pending_module_calls: Arc::new(Mutex::new(Vec::new())),
        }
    }

    /// Load and instantiate a WebAssembly module
    pub fn load_wasm(&self, wasm_bytes: Vec<u8>) -> Result<(), ModuleError> {
        // Phase 13 Integration: Strip WasmGC Types and Inject GC Host Imports
        let mut rewriter = crate::rewriter::GcRewriter::new(&wasm_bytes);
        let baseline_wasm = match rewriter.rewrite() {
            Ok(bytes) => bytes,
            Err(e) => {
                println!("Feather: Failed to rewrite WasmGC module: {}", e);
                wasm_bytes // Fallback to original bytes
            }
        };

        let runtime = WasmRuntime::new(
            &baseline_wasm, 
            self.state.clone(), 
            self.modules.lock().unwrap().keys().cloned().collect(),
            self.console_buffer.clone(),
            self.action_payload.clone(),
            self.pending_module_calls.clone(),
        ).map_err(|e| ModuleError::ExecutionFailed(format!("WASM error: {}", e)))?;
        *self.wasm_engine.lock().unwrap() = Some(runtime);
        Ok(())
    }

    /// Register a native platform module (e.g. Camera, Network, DB)
    pub fn register_module(&self, name: String, module: Arc<dyn NativeModule>) {
        self.modules.lock().unwrap().insert(name, module);
    }

    /// Hot-reload: Replace the running WASM module with a new binary
    /// without losing state. The state map is preserved across reloads.
    pub fn hot_reload_wasm(&self, wasm_bytes: Vec<u8>) -> Result<(), ModuleError> {
        let mut rewriter = crate::rewriter::GcRewriter::new(&wasm_bytes);
        let baseline_wasm = match rewriter.rewrite() {
            Ok(bytes) => bytes,
            Err(e) => {
                println!("Feather: Hot-reload rewrite failed: {}", e);
                wasm_bytes
            }
        };

        // Preserve the existing state across the reload
        let runtime = WasmRuntime::new(
            &baseline_wasm, 
            self.state.clone(),
            self.modules.lock().unwrap().keys().cloned().collect(),
            self.console_buffer.clone(),
            self.action_payload.clone(),
            self.pending_module_calls.clone(),
        ).map_err(|e| ModuleError::ExecutionFailed(format!("Hot-reload error: {}", e)))?;
        *self.wasm_engine.lock().unwrap() = Some(runtime);
        *self.needs_render.lock().unwrap() = true;
        Ok(())
    }

    /// Drain and return all console log messages buffered since the last call.
    /// Used by native shells to display Kotlin errors in a diagnostics overlay.
    pub fn drain_console_logs(&self) -> Vec<String> {
        let mut buffer = self.console_buffer.lock().unwrap();
        let logs = buffer.clone();
        buffer.clear();
        logs
    }

    /// Deliver an async native module result back into the runtime.
    /// Called by the native shell when a module callback completes (e.g., Camera returns a URI).
    pub fn deliver_module_result(&self, callback_id: String, result_json: String) {
        let mut state = self.state.lock().unwrap();
        // Store the result under the callback_id key (which is the stateKey the SDK requested)
        match serde_json::from_str::<Value>(&result_json) {
            Ok(val) => { state.insert(callback_id, StateValue::from_json_value(&val)); }
            Err(_) => { state.insert(callback_id, StateValue::Str(result_json)); }
        }
        drop(state);
        *self.needs_render.lock().unwrap() = true;
    }

    /// Deliver an async native module error back into the runtime.
    pub fn deliver_module_error(&self, callback_id: String, error: String) {
        let mut state = self.state.lock().unwrap();
        state.insert(format!("{}_error", callback_id), StateValue::Str(error));
        drop(state);
        *self.needs_render.lock().unwrap() = true;
    }

    /// Check if a WASM engine is loaded.
    pub fn has_wasm(&self) -> bool {
        self.wasm_engine.lock().unwrap().is_some()
    }

    /// Execute the WASM app's render_ui and return the JSON render tree.
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

    /// Check if the UI needs a re-render.
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
                        if let Ok(parsed) = serde_json::from_str::<Value>(&result) {
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



    /// Drain and return all pending module calls as JSON strings.
    pub fn drain_module_calls(&self) -> Vec<String> {
        let mut queue = self.pending_module_calls.lock().unwrap();
        let calls = queue.clone();
        queue.clear();
        
        calls.iter().map(|c| {
            serde_json::json!({
                "type": "module_call",
                "module": c.module_name,
                "method": c.action,
                "payload": serde_json::from_str::<Value>(&c.payload).unwrap_or(Value::Null),
                "resultStateKey": c.cb_key
            }).to_string()
        }).collect()
    }

    /// Process an action event from the host and update typed state.
    pub fn apply_action_event(&self, payload: &str) {
        let action = payload.trim();
        
        let (action_str, payload_val) = if action.starts_with('{') {
            if let Ok(json) = serde_json::from_str::<Value>(action) {
                // Handle structured module calls directly
                if json.get("type").and_then(|v| v.as_str()) == Some("module_call") {
                    let module_name = json.get("module").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    let method = json.get("method").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    let payload_val = json.get("payload").cloned().unwrap_or(Value::Null);
                    let cb_key = json.get("resultStateKey").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                    
                    self.pending_module_calls.lock().unwrap().push(ModuleCall {
                        module_name,
                        action: method,
                        payload: payload_val.to_string(),
                        cb_key,
                    });
                    *self.needs_render.lock().unwrap() = true;
                    return;
                }

                let a = json.get("action").and_then(|v| v.as_str()).unwrap_or(action).to_string();
                let p = json.get("payload").cloned().unwrap_or(Value::Null);
                (a, p)
            } else { (action.to_string(), Value::Null) }
        } else { (action.to_string(), Value::Null) };

        // Route action to WASM engine if loaded
        if let Some(engine) = self.wasm_engine.lock().unwrap().as_mut() {
            if let Err(e) = engine.call_action(&action_str) {
                println!("WASM action error: {}", e);
            }
            // Trigger a re-render after WASM logic completes
            *self.needs_render.lock().unwrap() = true;
            return;
        }

        let mut state = self.state.lock().unwrap();
        let changed = true;

        if action_str.starts_with("module:") {
            // "module:Camera/takePicture"
            let parts: Vec<&str> = action_str[7..].split('/').collect();
            if parts.len() == 2 {
                let module_name = parts[0];
                let module_action = parts[1];
                let modules = self.modules.lock().unwrap();
                if let Some(module) = modules.get(module_name) {
                    let payload_str = serde_json::to_string(&payload_val).unwrap_or_default();
                    if let Ok(result) = module.call_sync(module_action.to_string(), payload_str) {
                        if let Ok(parsed) = serde_json::from_str::<Value>(&result) {
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
            // Standard assignment from payload value
            match payload_val {
                Value::String(value) => { state.insert(action_str.to_string(), StateValue::Str(value)); }
                Value::Bool(b) => { state.insert(action_str.to_string(), StateValue::Bool(b)); }
                Value::Number(n) => {
                    if let Some(i) = n.as_i64() { state.insert(action_str.to_string(), StateValue::Int(i)); }
                    else if let Some(f) = n.as_f64() { state.insert(action_str.to_string(), StateValue::Float(f)); }
                }
                Value::Array(_) | Value::Object(_) => {
                    let typed = StateValue::from_json_value(&payload_val);
                    state.insert(action_str.to_string(), typed);
                }
                Value::Null => {
                    let next = match get_nested_state(&state, &action_str) {
                        Some(StateValue::Int(n)) => n + 1,
                        Some(StateValue::Str(s)) => s.parse::<i64>().unwrap_or(0) + 1,
                        _ => 1,
                    };
                    state.insert(action_str.to_string(), StateValue::Int(next));
                }
            }
        }
        
        drop(state);
        if changed {
            *self.needs_render.lock().unwrap() = true;
        }
    }

    /// Set a state value directly (for programmatic state updates).
    pub fn set_state(&self, key: &str, json_value: &str) {
        let mut state = self.state.lock().unwrap();
        match serde_json::from_str::<Value>(json_value) {
            Ok(value) => {
                state.insert(key.to_string(), StateValue::from_json_value(&value));
            }
            Err(_) => {
                state.insert(key.to_string(), StateValue::Str(json_value.to_string()));
            }
        }
        *self.needs_render.lock().unwrap() = true;
    }

    /// Get a state value as JSON string.
    pub fn get_state(&self, key: &str) -> Option<String> {
        let state = self.state.lock().unwrap();
        state
            .get(key)
            .map(|v| serde_json::to_string(v).unwrap_or_default())
    }

    /// Get all state as JSON.
    pub fn get_all_state(&self) -> String {
        let state = self.state.lock().unwrap();
        serde_json::to_string(&*state).unwrap_or_else(|_| "{}".to_string())
    }

    /// Resolve StateText and Condition nodes in the render payload.
    pub fn resolve_state_text_nodes(&self, json_payload: String) -> String {
        let Ok(mut value) = serde_json::from_str::<Value>(&json_payload) else {
            return json_payload;
        };

        self.resolve_inner(&mut value);
        serde_json::to_string(&value).unwrap_or(json_payload)
    }

    /// Look up a single state value as a display string.
    pub fn state_value(&self, key: &str) -> Option<String> {
        let state = self.state.lock().unwrap();
        get_nested_state(&state, key).map(|v| v.as_str())
    }

    /// Look up a state value as a boolean.
    pub fn state_bool(&self, key: &str) -> bool {
        self.state
            .lock()
            .unwrap()
            .get(key)
            .map(|v| v.as_bool())
            .unwrap_or(false)
    }

    /// Look up a state value as a list (for ForEach).
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
    /// Fetch a URL and store the response body in state under `key`.
    pub async fn fetch(&self, url: &str, key: &str) {
        match reqwest::get(url).await {
            Ok(resp) => {
                let body = resp.text().await.unwrap_or_default();
                let mut state = self.state.lock().unwrap();
                // Try to parse as JSON, otherwise store as string
                match serde_json::from_str::<Value>(&body) {
                    Ok(json_val) => {
                        state.insert(key.to_string(), StateValue::from_json_value(&json_val));
                    }
                    Err(_) => {
                        state.insert(key.to_string(), StateValue::Str(body));
                    }
                }
                drop(state);
                *self.needs_render.lock().unwrap() = true;
            }
            Err(e) => {
                let mut state = self.state.lock().unwrap();
                state.insert(
                    format!("{key}_error"),
                    StateValue::Str(format!("Fetch failed: {e}")),
                );
                drop(state);
                *self.needs_render.lock().unwrap() = true;
            }
        }
    }

    fn resolve_inner(&self, value: &mut Value) {
        match value {
            Value::Object(object) => {
                let node_type = object
                    .get("type")
                    .and_then(Value::as_str)
                    .unwrap_or_default()
                    .to_string();

                match node_type.as_str() {
                    "StateText" => {
                        let key = object
                            .get("key")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let prefix = object
                            .get("prefix")
                            .and_then(Value::as_str)
                            .unwrap_or_default();
                        let fallback = object
                            .get("fallback")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();

                        let state = self.state.lock().unwrap();
                        let text = get_nested_state(&state, &key)
                            .map(|value| format!("{prefix}{}", value.as_str()))
                            .unwrap_or(fallback);

                        object.clear();
                        object.insert("type".to_string(), Value::String("Text".to_string()));
                        object.insert("text".to_string(), Value::String(text));
                    }
                    "Condition" => {
                        let key = object
                            .get("key")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();

                        let state = self.state.lock().unwrap();
                        let condition_met = get_nested_state(&state, &key)
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
                            // No otherwise — render nothing (empty text)
                            object.clear();
                            object.insert("type".to_string(), Value::String("Spacer".to_string()));
                            object.insert("flex".to_string(), Value::Number(0.into()));
                        }
                    }
                    "ForEach" => {
                        let items_key = object
                            .get("itemsKey")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .to_string();
                        let template = object.get("template").cloned();

                        let state = self.state.lock().unwrap();
                        let items = get_nested_state(&state, &items_key).map(|v| v.as_list()).unwrap_or_default();
                        drop(state);

                        if let Some(template) = template {
                            let mut children = Vec::new();
                            for (index, item) in items.iter().enumerate() {
                                let mut child = template.clone();
                                // Replace $item placeholders in the template
                                let _item_json = serde_json::to_string(item).unwrap_or_default();
                                let child_str = serde_json::to_string(&child)
                                    .unwrap_or_default()
                                    .replace("$item", &item.as_str())
                                    .replace("$index", &index.to_string());
                                if let Ok(resolved) = serde_json::from_str::<Value>(&child_str) {
                                    child = resolved;
                                }
                                self.resolve_inner(&mut child);
                                children.push(child);
                            }

                            object.clear();
                            object.insert("type".to_string(), Value::String("Column".to_string()));
                            object.insert("children".to_string(), Value::Array(children));
                        }
                    }
                    _ => {
                        object.values_mut().for_each(|child| {
                            self.resolve_inner(child);
                        });
                    }
                }
            }
            Value::Array(children) => {
                children.iter_mut().for_each(|child| {
                    self.resolve_inner(child);
                });
            }
            _ => {}
        }
    }
}

// ─── Bundle System ────────────────────────────────────────────────────────────

#[derive(Clone, Deserialize, Serialize)]
pub struct BundleManifest {
    pub name: String,
    pub bundle_version: u8,
    pub sdk_version: String,
    pub protocol_version: u8,
    pub render: BundleArtifact,
    pub assets: Vec<BundleArtifact>,
    pub modules: Vec<BundleModule>,
    pub dependencies: Vec<BundleArtifact>,
    pub delta: BundleDelta,
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

#[derive(Clone, Deserialize, Serialize)]
pub struct BundleModule {
    pub name: String,
    pub version: String,
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

    for asset in &manifest.assets {
        artifacts.insert(asset.path.clone(), asset.sha256.clone());
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
                modules.push(BundleModule {
                    name: module.to_string(),
                    version: object
                        .get("moduleVersion")
                        .and_then(Value::as_str)
                        .unwrap_or("1.0.0")
                        .to_string(),
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

// ─── WebAssembly Execution Engine ─────────────────────────────────────────────

pub struct HostState {
    state: Arc<Mutex<HashMap<String, StateValue>>>,
    render_tree: Arc<Mutex<String>>,
    gc_heap: Arc<Mutex<gc::GcHeap>>,
    console_buffer: Arc<Mutex<Vec<String>>>,
    module_names: Vec<String>,
    action_payload: Arc<Mutex<String>>,
    pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
}

pub struct WasmRuntime {
    store: Store<HostState>,
    instance: wasmi::Instance,
    render_tree: Arc<Mutex<String>>,
}

impl WasmRuntime {
    pub fn new(
        wasm_bytes: &[u8], 
        state: Arc<Mutex<HashMap<String, StateValue>>>,
        module_names: Vec<String>,
        console_buffer: Arc<Mutex<Vec<String>>>,
        action_payload: Arc<Mutex<String>>,
        pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
    ) -> Result<Self, wasmi::Error> {
        let engine = WasmEngine::default();
        let module = Module::new(&engine, wasm_bytes)?;
        
        let render_tree = Arc::new(Mutex::new(String::new()));
        let gc_heap = Arc::new(Mutex::new(gc::GcHeap::new()));
        let mut store = Store::new(&engine, HostState { 
            state, 
            render_tree: render_tree.clone(), 
            gc_heap: gc_heap.clone(),
            console_buffer: console_buffer.clone(),
            module_names,
            action_payload,
            pending_module_calls,
        });
        let mut linker = <Linker<HostState>>::new(&engine);
        
        // Helper to read a string from wasm memory
        fn read_string(caller: &wasmi::Caller<'_, HostState>, ptr: i32, len: i32) -> Option<String> {
            let memory = caller.get_export("memory")?.into_memory()?;
            let mut buffer = vec![0; len as usize];
            memory.read(caller, ptr as usize, &mut buffer).ok()?;
            String::from_utf8(buffer).ok()
        }

        // Helper to write a string back to wasm memory
        fn write_string(caller: &mut wasmi::Caller<'_, HostState>, ptr: i32, data: &str) -> Option<()> {
            let memory = caller.get_export("memory")?.into_memory()?;
            memory.write(caller, ptr as usize, data.as_bytes()).ok()?;
            Some(())
        }
        
        // ── Host Function: env.console_log ──────────────────────────────────
        linker.func_wrap("env", "console_log", |caller: wasmi::Caller<'_, HostState>, ptr: i32, len: i32| {
            if let Some(msg) = read_string(&caller, ptr, len) {
                println!("[WASM] {}", msg);
                // Buffer for error boundary diagnostics (Phase 17)
                caller.data().console_buffer.lock().unwrap().push(msg);
            }
        })?;
        
        // ── Host Function: env.update_state ─────────────────────────────────
        linker.func_wrap("env", "update_state", |caller: wasmi::Caller<'_, HostState>, key_ptr: i32, key_len: i32, val_ptr: i32, val_len: i32| {
            if let (Some(key), Some(val_str)) = (read_string(&caller, key_ptr, key_len), read_string(&caller, val_ptr, val_len)) {
                let mut state = caller.data().state.lock().unwrap();
                if let Ok(parsed) = serde_json::from_str::<Value>(&val_str) {
                    state.insert(key, StateValue::from_json_value(&parsed));
                } else {
                    state.insert(key, StateValue::Str(val_str));
                }
            }
        })?;

        // ── Host Function: env.set_render_tree ──────────────────────────────
        linker.func_wrap("env", "set_render_tree", |caller: wasmi::Caller<'_, HostState>, ptr: i32, len: i32| {
            if let Some(json) = read_string(&caller, ptr, len) {
                *caller.data().render_tree.lock().unwrap() = json;
            }
        })?;

        // ── Host Function: env.get_action_payload ───────────────────────────
        linker.func_wrap("env", "get_action_payload", |mut caller: wasmi::Caller<'_, HostState>, ptr: i32| -> i32 {
            let payload = caller.data().action_payload.lock().unwrap().clone();
            if payload.is_empty() { return 0; }
            write_string(&mut caller, ptr, &payload);
            payload.len() as i32
        })?;

        // ── Host Function: env.fetch_network ────────────────────────────────
        linker.func_wrap("env", "fetch_network", |mut caller: wasmi::Caller<'_, HostState>, url_ptr: i32, url_len: i32, result_ptr: i32| -> i32 {
            if let Some(url) = read_string(&caller, url_ptr, url_len) {
                println!("[WASM] fetch_network: {}", url);
                if let Ok(response) = reqwest::blocking::get(&url) {
                    if let Ok(text) = response.text() {
                        write_string(&mut caller, result_ptr, &text);
                        return text.len() as i32;
                    }
                }
            }
            -1i32
        })?;

        // ── Host Function: env.get_state ────────────────────────────────────
        linker.func_wrap("env", "get_state", |mut caller: wasmi::Caller<'_, HostState>, key_ptr: i32, key_len: i32, result_ptr: i32| -> i32 {
            if let Some(key) = read_string(&caller, key_ptr, key_len) {
                let serialized = {
                    let state = caller.data().state.lock().unwrap();
                    if let Some(val) = state.get(&key) {
                        Some(val.as_str())
                    } else { None }
                };
                if let Some(s) = serialized {
                    write_string(&mut caller, result_ptr, &s);
                    return s.len() as i32;
                }
            }
            0i32
        })?;

        // ── Host Function: env.call_module (Phase 16) ───────────────────────
        // Routes WASM calls to registered native modules.
        // The WASM app passes: module_name, action, payload, callback_state_key
        // The result is stored in state[callback_state_key] for the next render.
        linker.func_wrap("env", "call_module", |caller: wasmi::Caller<'_, HostState>, 
            name_ptr: i32, name_len: i32, 
            action_ptr: i32, action_len: i32, 
            payload_ptr: i32, payload_len: i32,
            cb_key_ptr: i32, cb_key_len: i32| -> i32 
        {
            let module_name = read_string(&caller, name_ptr, name_len);
            let action = read_string(&caller, action_ptr, action_len);
            let payload = read_string(&caller, payload_ptr, payload_len);
            let cb_key = read_string(&caller, cb_key_ptr, cb_key_len);

            if let (Some(name), Some(act), Some(pay), Some(key)) = (module_name, action, payload, cb_key) {
                println!("[WASM] call_module: {}/{} -> state[{}]", name, act, key);
                caller.data().state.lock().unwrap().insert(format!("{}_loading", key), StateValue::Bool(true));
                caller.data().pending_module_calls.lock().unwrap().push(ModuleCall {
                    module_name: name,
                    action: act,
                    payload: pay,
                    cb_key: key,
                });
                0i32
            } else {
                -1i32
            }
        })?;

        // ═══════════════════════════════════════════════════════════════════
        // GC Host Functions — WasmGC operations backed by our Rust GcHeap
        // ═══════════════════════════════════════════════════════════════════

        // gc_struct_new(type_id: i32, field_count: i32) -> i32 (handle)
        // Fields are pre-initialized to I32(0). Use gc_struct_set to populate.
        linker.func_wrap("env", "gc_struct_new", |caller: wasmi::Caller<'_, HostState>, type_id: i32, field_count: i32| -> i32 {
            let fields = vec![gc::GcValue::I32(0); field_count as usize];
            let gc_ref = caller.data().gc_heap.lock().unwrap().struct_new(type_id as u32, fields);
            gc_ref.to_handle()
        })?;

        // gc_struct_get(handle: i32, field_idx: i32) -> i32
        linker.func_wrap("env", "gc_struct_get", |caller: wasmi::Caller<'_, HostState>, handle: i32, field_idx: i32| -> i32 {
            let gc_ref = gc::GcRef::from_handle(handle);
            let heap = caller.data().gc_heap.lock().unwrap();
            match heap.struct_get(gc_ref, field_idx as u32) {
                Some(gc::GcValue::I32(v)) => v,
                Some(gc::GcValue::Ref(r)) => r.to_handle(),
                _ => 0,
            }
        })?;

        // gc_struct_set(handle: i32, field_idx: i32, value: i32)
        linker.func_wrap("env", "gc_struct_set", |caller: wasmi::Caller<'_, HostState>, handle: i32, field_idx: i32, value: i32| {
            let gc_ref = gc::GcRef::from_handle(handle);
            caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::I32(value));
        })?;

        // gc_array_new(type_id: i32, length: i32) -> i32 (handle)
        linker.func_wrap("env", "gc_array_new", |caller: wasmi::Caller<'_, HostState>, type_id: i32, length: i32| -> i32 {
            let elements = vec![gc::GcValue::I32(0); length as usize];
            let gc_ref = caller.data().gc_heap.lock().unwrap().array_new(type_id as u32, elements);
            gc_ref.to_handle()
        })?;

        // gc_array_get(handle: i32, index: i32) -> i32
        linker.func_wrap("env", "gc_array_get", |caller: wasmi::Caller<'_, HostState>, handle: i32, index: i32| -> i32 {
            let gc_ref = gc::GcRef::from_handle(handle);
            let heap = caller.data().gc_heap.lock().unwrap();
            match heap.array_get(gc_ref, index as u32) {
                Some(gc::GcValue::I32(v)) => v,
                Some(gc::GcValue::Ref(r)) => r.to_handle(),
                _ => 0,
            }
        })?;

        // gc_array_set(handle: i32, index: i32, value: i32)
        linker.func_wrap("env", "gc_array_set", |caller: wasmi::Caller<'_, HostState>, handle: i32, index: i32, value: i32| {
            let gc_ref = gc::GcRef::from_handle(handle);
            caller.data().gc_heap.lock().unwrap().array_set(gc_ref, index as u32, gc::GcValue::I32(value));
        })?;

        // gc_array_len(handle: i32) -> i32
        linker.func_wrap("env", "gc_array_len", |caller: wasmi::Caller<'_, HostState>, handle: i32| -> i32 {
            let gc_ref = gc::GcRef::from_handle(handle);
            let heap = caller.data().gc_heap.lock().unwrap();
            heap.array_len(gc_ref).unwrap_or(0) as i32
        })?;

        // gc_ref_test(handle: i32, expected_type_id: i32) -> i32 (0 or 1)
        linker.func_wrap("env", "gc_ref_test", |caller: wasmi::Caller<'_, HostState>, handle: i32, expected_type_id: i32| -> i32 {
            let gc_ref = gc::GcRef::from_handle(handle);
            let heap = caller.data().gc_heap.lock().unwrap();
            if heap.ref_test(gc_ref, expected_type_id as u32) { 1 } else { 0 }
        })?;

        // gc_collect() — force a GC cycle
        linker.func_wrap("env", "gc_collect", |caller: wasmi::Caller<'_, HostState>| {
            caller.data().gc_heap.lock().unwrap().collect();
        })?;

        let instance = linker.instantiate_and_start(&mut store, &module)?;
        
        Ok(WasmRuntime { store, instance, render_tree })
    }
    
    /// Call the WASM app's handle_action export.
    pub fn call_action(&mut self, action: &str) -> Result<(), wasmi::Error> {
        *self.store.data().action_payload.lock().unwrap() = action.to_string();
        let func = self.instance.get_export(&self.store, "handle_action")
            .and_then(wasmi::Extern::into_func);
            
        if let Some(handle_action) = func {
            let mut results = [];
            handle_action.call(&mut self.store, &[], &mut results)?;
        }
        
        Ok(())
    }

    /// Call the WASM app's render_ui export and return the JSON render tree.
    pub fn call_render(&mut self) -> Result<String, wasmi::Error> {
        let func = self.instance.get_export(&self.store, "render_ui")
            .and_then(wasmi::Extern::into_func);
            
        if let Some(render_ui) = func {
            let mut results = [];
            render_ui.call(&mut self.store, &[], &mut results)?;
        }
        
        Ok(self.render_tree.lock().unwrap().clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use std::sync::{Arc, Mutex};

    #[test]
    fn test_memory_bridge_read_string() {
        // This test simulates the `withWasmString` Kotlin logic by writing
        // a string into Wasm linear memory and calling the host `set_render_tree`.
        let wat = r#"
            (module
                (import "env" "set_render_tree" (func $set_render_tree (param i32 i32)))
                (memory (export "memory") 1)
                ;; Write "Hello, Virtual DOM!" (19 bytes) at offset 42
                (data (i32.const 42) "Hello, Virtual DOM!")
                
                (func (export "trigger_render")
                    ;; Call set_render_tree(ptr=42, len=19)
                    (call $set_render_tree (i32.const 42) (i32.const 19))
                )
            )
        "#;
        
        let wasm_bytes = wat::parse_str(wat).unwrap();
        let state = Arc::new(Mutex::new(HashMap::new()));
        let console_buffer = Arc::new(Mutex::new(Vec::new()));
        
        let mut runtime = WasmRuntime::new(&wasm_bytes, state, vec![], console_buffer).expect("Failed to initialize WasmRuntime");
        
        // Invoke the trigger function manually
        let func = runtime.instance.get_export(&runtime.store, "trigger_render")
            .unwrap()
            .into_func()
            .unwrap();
            
        func.call(&mut runtime.store, &[], &mut []).expect("Failed to execute WASM function");
        
        // Verify that the host successfully extracted the string from the Wasm memory!
        let render_tree = runtime.render_tree.lock().unwrap().clone();
        assert_eq!(render_tree, "Hello, Virtual DOM!");
    }
}

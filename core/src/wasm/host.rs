use crate::state::{StateValue, get_nested_state};
use crate::gc;
use std::sync::{Arc, Mutex};
use std::collections::HashMap;
use wasmi::{Linker, Caller};
use crate::ModuleCall;

pub struct HostState {
    pub state: Arc<Mutex<HashMap<String, StateValue>>>,
    pub render_tree: Arc<Mutex<String>>,
    pub gc_heap: Arc<Mutex<gc::GcHeap>>,
    pub console_buffer: Arc<Mutex<Vec<String>>>,
    pub action_payload: Arc<Mutex<String>>,
    pub pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
    pub event_queue: Arc<Mutex<Vec<crate::NativeEvent>>>,
}

pub fn register_host_functions(linker: &mut Linker<HostState>) -> Result<(), wasmi::Error> {
    // Helper to read a string from wasm memory
    fn read_string(caller: &Caller<'_, HostState>, ptr: i32, len: i32) -> Option<String> {
        let memory = caller.get_export("memory")?.into_memory()?;
        let mut buffer = vec![0; len as usize];
        memory.read(caller, ptr as usize, &mut buffer).ok()?;
        String::from_utf8(buffer).ok()
    }

    // Helper to write a string back to wasm memory
    fn write_string(caller: &mut Caller<'_, HostState>, ptr: i32, data: &str) -> Option<()> {
        let memory = caller.get_export("memory")?.into_memory()?;
        memory.write(caller, ptr as usize, data.as_bytes()).ok()?;
        Some(())
    }

    // ── Host Function: env.console_log ──────────────────────────────────
    linker.func_wrap("env", "console_log", |caller: Caller<'_, HostState>, ptr: i32, len: i32| {
        if let Some(msg) = read_string(&caller, ptr, len) {
            println!("[WASM] {}", msg);
            caller.data().console_buffer.lock().unwrap().push(msg);
        }
    })?;

    // ── Host Function: env.update_state ─────────────────────────────────
    linker.func_wrap("env", "update_state", |caller: Caller<'_, HostState>, key_ptr: i32, key_len: i32, val_ptr: i32, val_len: i32| {
        if let (Some(key), Some(val_str)) = (read_string(&caller, key_ptr, key_len), read_string(&caller, val_ptr, val_len)) {
            let mut state = caller.data().state.lock().unwrap();
            if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(&val_str) {
                state.insert(key, StateValue::from_json_value(&parsed));
            } else {
                state.insert(key, StateValue::Str(val_str));
            }
        }
    })?;

    // ── Host Function: env.set_render_tree ──────────────────────────────
    linker.func_wrap("env", "set_render_tree", |caller: Caller<'_, HostState>, ptr: i32, len: i32| {
        if let Some(json) = read_string(&caller, ptr, len) {
            *caller.data().render_tree.lock().unwrap() = json;
        }
    })?;

    // ── Host Function: env.set_render_tree_bin ──────────────────────────
    linker.func_wrap("env", "set_render_tree_bin", |caller: Caller<'_, HostState>, ptr: i32, len: i32| {
        let memory = match caller.get_export("memory").and_then(|e| e.into_memory()) {
            Some(m) => m,
            None => return,
        };
        let mut buffer = vec![0u8; len as usize];
        if memory.read(&caller, ptr as usize, &mut buffer).is_ok() {
            // Store as base64 or a special binary prefix for the orchestrator to handle
            // For now, we'll convert it back to a base64 string so the existing UniFFI String return works
            // but the host will know to decode it if it starts with "base64:"
            let encoded = format!("base64:{}", base64::encode(&buffer));
            *caller.data().render_tree.lock().unwrap() = encoded;
        }
    })?;

    // ── Host Function: env.get_action_payload ───────────────────────────
    linker.func_wrap("env", "get_action_payload", |mut caller: Caller<'_, HostState>, ptr: i32| -> i32 {
        let payload = caller.data().action_payload.lock().unwrap().clone();
        if payload.is_empty() { return 0; }
        write_string(&mut caller, ptr, &payload);
        payload.len() as i32
    })?;

    // ── Host Function: env.drain_native_events ──────────────────────────
    linker.func_wrap("env", "drain_native_events", |mut caller: Caller<'_, HostState>, ptr: i32| -> i32 {
        let events = {
            let mut queue = caller.data().event_queue.lock().unwrap();
            let e = queue.clone();
            queue.clear();
            e
        };
        if events.is_empty() { return 0; }
        
        if let Ok(json) = serde_json::to_string(&events) {
            write_string(&mut caller, ptr, &json);
            return json.len() as i32;
        }
        0
    })?;

    // ── Host Function: env.fetch_network ────────────────────────────────
    linker.func_wrap("env", "fetch_network", |mut caller: Caller<'_, HostState>, url_ptr: i32, url_len: i32, result_ptr: i32| -> i32 {
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
    linker.func_wrap("env", "get_state", |mut caller: Caller<'_, HostState>, key_ptr: i32, key_len: i32, result_ptr: i32| -> i32 {
        if let Some(key) = read_string(&caller, key_ptr, key_len) {
            let serialized = {
                let state = caller.data().state.lock().unwrap();
                if let Some(val) = get_nested_state(&state, &key) {
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

    // ── Host Function: env.call_module ───────────────────────
    linker.func_wrap("env", "call_module", |caller: Caller<'_, HostState>, 
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

    // ── Host Function: env.msgpack_update_state ─────────────────────────────
    linker.func_wrap("env", "msgpack_update_state", |caller: Caller<'_, HostState>, ptr: i32, len: i32| -> i32 {
        let memory = match caller.get_export("memory").and_then(|e| e.into_memory()) {
            Some(m) => m,
            None => return -1,
        };
        let mut buffer = vec![0u8; len as usize];
        if memory.read(&caller, ptr as usize, &mut buffer).is_err() { return -2; }

        let updates: HashMap<String, StateValue> = match rmp_serde::from_slice(&buffer) {
            Ok(u) => u,
            Err(_) => return -3,
        };

        let mut state = caller.data().state.lock().unwrap();
        for (k, v) in updates {
            state.insert(k, v);
        }
        0i32
    })?;

    // ── Host Function: env.msgpack_get_state ────────────────────────────────
    linker.func_wrap("env", "msgpack_get_state", |mut caller: Caller<'_, HostState>, key_ptr: i32, key_len: i32, result_ptr: i32| -> i32 {
        let key = match read_string(&caller, key_ptr, key_len) {
            Some(k) => k,
            None => return -1,
        };

        let val = {
            let state = caller.data().state.lock().unwrap();
            get_nested_state(&state, &key).cloned()
        };

        if let Some(v) = val {
            if let Ok(bytes) = rmp_serde::to_vec(&v) {
                let memory = match caller.get_export("memory").and_then(|e| e.into_memory()) {
                    Some(m) => m,
                    None => return -1,
                };
                if memory.write(&mut caller, result_ptr as usize, &bytes).is_ok() {
                    return bytes.len() as i32;
                }
            }
        }
        0i32
    })?;

    // ── Host Function: env.msgpack_call_module ──────────────────────────────
    linker.func_wrap("env", "msgpack_call_module", |caller: Caller<'_, HostState>, ptr: i32, len: i32| -> i32 {
        let memory = match caller.get_export("memory").and_then(|e| e.into_memory()) {
            Some(m) => m,
            None => return -1,
        };
        let mut buffer = vec![0u8; len as usize];
        if memory.read(&caller, ptr as usize, &mut buffer).is_err() { return -2; }

        #[derive(serde::Deserialize)]
        struct MsgpackCall {
            module: String,
            method: String,
            payload: serde_json::Value,
            #[serde(rename = "resultStateKey")]
            cb_key: String,
        }

        match rmp_serde::from_slice::<MsgpackCall>(&buffer) {
            Ok(call) => {
                println!("[WASM] msgpack_call_module: {}/{} -> state[{}]", call.module, call.method, call.cb_key);
                caller.data().state.lock().unwrap().insert(format!("{}_loading", call.cb_key), StateValue::Bool(true));
                caller.data().pending_module_calls.lock().unwrap().push(ModuleCall {
                    module_name: call.module,
                    action: call.method,
                    payload: call.payload.to_string(),
                    cb_key: call.cb_key,
                });
                0i32
            }
            Err(_) => -3,
        }
    })?;

    // ═══════════════════════════════════════════════════════════════════
    // GC Host Functions
    // ═══════════════════════════════════════════════════════════════════

    linker.func_wrap("env", "gc_struct_new", |caller: Caller<'_, HostState>, type_id: i32, field_count: i32| -> i32 {
        let fields = vec![gc::GcValue::I32(0); field_count as usize];
        let gc_ref = caller.data().gc_heap.lock().unwrap().struct_new(type_id as u32, fields);
        gc_ref.to_handle()
    })?;

    linker.func_wrap("env", "gc_struct_new_default", |caller: Caller<'_, HostState>, type_id: i32| -> i32 {
        let gc_ref = caller.data().gc_heap.lock().unwrap().struct_new_default(type_id as u32);
        gc_ref.to_handle()
    })?;

    linker.func_wrap("env", "gc_struct_get_i32", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32| -> i32 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        match heap.struct_get(gc_ref, field_idx as u32) {
            Some(gc::GcValue::I32(v)) => v,
            Some(gc::GcValue::Ref(r)) => r.to_handle(),
            _ => 0,
        }
    })?;

    linker.func_wrap("env", "gc_struct_get_i64", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32| -> i64 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        match heap.struct_get(gc_ref, field_idx as u32) {
            Some(gc::GcValue::I64(v)) => v,
            _ => 0,
        }
    })?;

    linker.func_wrap("env", "gc_struct_get_f32", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32| -> f32 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        match heap.struct_get(gc_ref, field_idx as u32) {
            Some(gc::GcValue::F32(v)) => v,
            _ => 0.0,
        }
    })?;

    linker.func_wrap("env", "gc_struct_get_f64", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32| -> f64 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        match heap.struct_get(gc_ref, field_idx as u32) {
            Some(gc::GcValue::F64(v)) => v,
            _ => 0.0,
        }
    })?;

    linker.func_wrap("env", "gc_struct_set_i32", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32, value: i32| {
        let gc_ref = gc::GcRef::from_handle(handle);
        caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::I32(value));
    })?;
    
    linker.func_wrap("env", "gc_struct_set_ref", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32, ref_handle: i32| {
        let gc_ref = gc::GcRef::from_handle(handle);
        let child_ref = gc::GcRef::from_handle(ref_handle);
        caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::Ref(child_ref));
    })?;

    linker.func_wrap("env", "gc_struct_set_i64", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32, value: i64| {
        let gc_ref = gc::GcRef::from_handle(handle);
        caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::I64(value));
    })?;

    linker.func_wrap("env", "gc_struct_set_f32", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32, value: f32| {
        let gc_ref = gc::GcRef::from_handle(handle);
        caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::F32(value));
    })?;

    linker.func_wrap("env", "gc_struct_set_f64", |caller: Caller<'_, HostState>, handle: i32, field_idx: i32, value: f64| {
        let gc_ref = gc::GcRef::from_handle(handle);
        caller.data().gc_heap.lock().unwrap().struct_set(gc_ref, field_idx as u32, gc::GcValue::F64(value));
    })?;

    linker.func_wrap("env", "gc_array_new", |caller: Caller<'_, HostState>, type_id: i32, length: i32| -> i32 {
        let elements = vec![gc::GcValue::I32(0); length as usize];
        let gc_ref = caller.data().gc_heap.lock().unwrap().array_new(type_id as u32, elements);
        gc_ref.to_handle()
    })?;

    linker.func_wrap("env", "gc_array_new_default", |caller: Caller<'_, HostState>, type_id: i32, length: i32| -> i32 {
        let gc_ref = caller.data().gc_heap.lock().unwrap().array_new_default(type_id as u32, length as u32);
        gc_ref.to_handle()
    })?;

    linker.func_wrap("env", "gc_array_get_i32", |caller: Caller<'_, HostState>, handle: i32, index: i32| -> i32 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        match heap.array_get(gc_ref, index as u32) {
            Some(gc::GcValue::I32(v)) => v,
            Some(gc::GcValue::Ref(r)) => r.to_handle(),
            _ => 0,
        }
    })?;

    linker.func_wrap("env", "gc_array_set_i32", |caller: Caller<'_, HostState>, handle: i32, index: i32, value: i32| {
        let gc_ref = gc::GcRef::from_handle(handle);
        caller.data().gc_heap.lock().unwrap().array_set(gc_ref, index as u32, gc::GcValue::I32(value));
    })?;
    
    linker.func_wrap("env", "gc_array_set_ref", |caller: Caller<'_, HostState>, handle: i32, index: i32, ref_handle: i32| {
        let gc_ref = gc::GcRef::from_handle(handle);
        let child_ref = gc::GcRef::from_handle(ref_handle);
        caller.data().gc_heap.lock().unwrap().array_set(gc_ref, index as u32, gc::GcValue::Ref(child_ref));
    })?;

    linker.func_wrap("env", "gc_array_len", |caller: Caller<'_, HostState>, handle: i32| -> i32 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        heap.array_len(gc_ref).unwrap_or(0) as i32
    })?;

    linker.func_wrap("env", "gc_ref_test", |caller: Caller<'_, HostState>, handle: i32, expected_type_id: i32| -> i32 {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        if heap.ref_test(gc_ref, expected_type_id as u32) { 1 } else { 0 }
    })?;

    linker.func_wrap("env", "gc_ref_cast", |caller: Caller<'_, HostState>, handle: i32, expected_type_id: i32| -> Result<i32, wasmi::Error> {
        let gc_ref = gc::GcRef::from_handle(handle);
        let heap = caller.data().gc_heap.lock().unwrap();
        if gc_ref == gc::GcRef::Null || heap.ref_test(gc_ref, expected_type_id as u32) {
            Ok(handle)
        } else {
            Err(wasmi::Error::new("WasmGC: ref.cast failed"))
        }
    })?;

    linker.func_wrap("env", "gc_collect", |caller: Caller<'_, HostState>| {
        caller.data().gc_heap.lock().unwrap().collect();
    })?;

    Ok(())
}

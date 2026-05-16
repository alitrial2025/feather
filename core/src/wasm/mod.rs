pub mod host;

use std::sync::{Arc, Mutex};
use wasmi::{Engine as WasmEngine, Module, Store, Linker};
use crate::state::StateValue;
use crate::ModuleCall;
use crate::gc;
pub use host::HostState;

pub struct WasmRuntime {
    store: Store<HostState>,
    instance: wasmi::Instance,
    render_tree: Arc<Mutex<String>>,
}

impl WasmRuntime {
    pub fn new(
        wasm_bytes: &[u8], 
        state: Arc<Mutex<std::collections::HashMap<String, StateValue>>>,
        console_buffer: Arc<Mutex<Vec<String>>>,
        action_payload: Arc<Mutex<String>>,
        pending_module_calls: Arc<Mutex<Vec<ModuleCall>>>,
        event_queue: Arc<Mutex<Vec<crate::NativeEvent>>>,
    ) -> Result<Self, wasmi::Error> {
        let engine = WasmEngine::default();
        let module = Module::new(&engine, wasm_bytes)?;
        
        let render_tree = Arc::new(Mutex::new(String::new()));
        let gc_heap = Arc::new(Mutex::new(gc::GcHeap::new()));

        // Discover types from the WASM binary and register them in GcHeap
        Self::discover_types(wasm_bytes, &mut gc_heap.lock().unwrap());

        let mut store = Store::new(&engine, HostState { 
            state, 
            render_tree: render_tree.clone(), 
            gc_heap: gc_heap.clone(),
            console_buffer: console_buffer.clone(),
            action_payload,
            pending_module_calls,
            event_queue,
        });
        let mut linker = <Linker<HostState>>::new(&engine);
        
        host::register_host_functions(&mut linker)?;

        let instance = linker.instantiate_and_start(&mut store, &module)?;
        
        Ok(WasmRuntime { store, instance, render_tree })
    }

    fn discover_types(wasm_bytes: &[u8], gc_heap: &mut gc::GcHeap) {
        use wasmparser::{Parser, Payload, CompositeType, StorageType, ValType};

        let mut parser = Parser::new(0);
        let mut remaining = wasm_bytes;
        let mut type_idx = 0;
        while !remaining.is_empty() {
            let (payload, consumed) = match parser.parse(remaining, true) {
                Ok(wasmparser::Chunk::Parsed { payload, consumed }) => (payload, consumed),
                _ => break,
            };
            remaining = &remaining[consumed..];

            if let Payload::TypeSection(reader) = payload {
                for rec_group in reader {
                    let Ok(group) = rec_group else { continue };
                    for sub_type in group.types() {
                        let idx = type_idx;
                        type_idx += 1;
                        match &sub_type.composite_type {
                            CompositeType::Struct(s) => {
                                let mut field_types = Vec::new();
                                let mut field_mutability = Vec::new();
                                for field in &s.fields {
                                    field_types.push(match field.element_type {
                                        StorageType::Val(ValType::I32) => gc::GcType::I32,
                                        StorageType::Val(ValType::I64) => gc::GcType::I64,
                                        StorageType::Val(ValType::F32) => gc::GcType::F32,
                                        StorageType::Val(ValType::F64) => gc::GcType::F64,
                                        _ => gc::GcType::Ref,
                                    });
                                    field_mutability.push(field.mutable);
                                }
                                gc_heap.register_struct_type(gc::StructType {
                                    type_id: idx,
                                    field_count: field_types.len(),
                                    field_types,
                                    field_mutability,
                                });
                            }
                            CompositeType::Array(a) => {
                                let element_type = match a.0.element_type {
                                    StorageType::Val(ValType::I32) => gc::GcType::I32,
                                    StorageType::Val(ValType::I64) => gc::GcType::I64,
                                    StorageType::Val(ValType::F32) => gc::GcType::F32,
                                    StorageType::Val(ValType::F64) => gc::GcType::F64,
                                    _ => gc::GcType::Ref,
                                };
                                gc_heap.register_array_type(gc::ArrayType {
                                    type_id: idx,
                                    element_type,
                                    element_mutable: a.0.mutable,
                                });
                            }
                            _ => {}
                        }
                    }
                }
            }
        }
    }
    
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

    pub fn call_render(&mut self) -> Result<String, wasmi::Error> {
        // Prefer render_ui_bin (Binary IR / MessagePack) for performance
        let binary_func = self.instance.get_export(&self.store, "render_ui_bin")
            .and_then(wasmi::Extern::into_func);
            
        if let Some(render_ui_bin) = binary_func {
            let mut results = [];
            render_ui_bin.call(&mut self.store, &[], &mut results)?;
        } else {
            // Fallback to legacy render_ui (JSON IR)
            let json_func = self.instance.get_export(&self.store, "render_ui")
                .and_then(wasmi::Extern::into_func);
                
            if let Some(render_ui) = json_func {
                let mut results = [];
                render_ui.call(&mut self.store, &[], &mut results)?;
            }
        }
        
        Ok(self.render_tree.lock().unwrap().clone())
    }
}

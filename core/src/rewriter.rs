//! # WasmGC Bytecode Rewriter
//!
//! Translates WasmGC instructions emitted by Kotlin/Wasm into standard Wasm 1.0
//! instructions that call into the host's `GcHeap`.
//!
//! ## Transformations
//! - `struct.new $T` -> `call $gc_struct_new(T_id, num_fields)`
//! - `struct.get $T $F` -> `call $gc_struct_get_T(handle, F_idx)`
//! - `struct.set $T $F` -> `call $gc_struct_set_T(handle, F_idx, val)`
//! - `array.new $T` -> `call $gc_array_new(T_id, len)`
//! - `array.get $T` -> `call $gc_array_get_T(handle, idx)`
//! - `array.set $T` -> `call $gc_array_set_T(handle, idx, val)`
//! - `array.len` -> `call $gc_array_len(handle)`
//! - `ref.test` -> `call $gc_ref_test(handle, type_id)`
//! - `ref.cast` -> `call $gc_ref_cast(handle, type_id)`

use wasm_encoder::{
    CodeSection, EntityType, ExportSection, FunctionSection, ImportSection, Instruction,
    MemorySection, Module, TypeSection, ValType, RefType, Elements, ElementSection, ConstExpr,
    DataSection, TableSection, GlobalSection, HeapType
};
use wasmparser::{Chunk, Parser, Payload, CompositeType, ExternalKind};
use std::collections::HashMap;

#[derive(Debug, thiserror::Error)]
pub enum RewriteError {
    #[error("Failed to parse Wasm: {0}")]
    Parse(#[from] wasmparser::BinaryReaderError),
    #[error("Unsupported Wasm feature: {0}")]
    Unsupported(String),
}

pub struct GcRewriter {
    input: Vec<u8>,
    gc_struct_new_idx: u32,
    gc_struct_new_default_idx: u32,
    gc_struct_get_i32_idx: u32,
    gc_struct_get_i64_idx: u32,
    gc_struct_get_f32_idx: u32,
    gc_struct_get_f64_idx: u32,
    gc_struct_set_i32_idx: u32,
    gc_struct_set_ref_idx: u32,
    gc_struct_set_i64_idx: u32,
    gc_struct_set_f32_idx: u32,
    gc_struct_set_f64_idx: u32,
    gc_array_new_idx: u32,
    gc_array_new_default_idx: u32,
    gc_array_get_i32_idx: u32,
    gc_array_set_i32_idx: u32,
    gc_array_set_ref_idx: u32,
    gc_array_len_idx: u32,
    gc_ref_test_idx: u32,
    gc_ref_cast_idx: u32,
    gc_collect_idx: u32,
    original_import_func_count: u32,
    original_type_count: u32,
    
    struct_field_counts: HashMap<u32, u32>,
    struct_field_types: HashMap<u32, Vec<ValType>>,
    
    scratch_i32_idx: u32,
    scratch_i64_idx: u32,
    scratch_f32_idx: u32,
    scratch_f64_idx: u32,
}

impl GcRewriter {
    pub fn new(input: &[u8]) -> Self {
        Self {
            input: input.to_vec(),
            gc_struct_new_idx: 0,
            gc_struct_new_default_idx: 0,
            gc_struct_get_i32_idx: 0,
            gc_struct_get_i64_idx: 0,
            gc_struct_get_f32_idx: 0,
            gc_struct_get_f64_idx: 0,
            gc_struct_set_i32_idx: 0,
            gc_struct_set_ref_idx: 0,
            gc_struct_set_i64_idx: 0,
            gc_struct_set_f32_idx: 0,
            gc_struct_set_f64_idx: 0,
            gc_array_new_idx: 0,
            gc_array_new_default_idx: 0,
            gc_array_get_i32_idx: 0,
            gc_array_set_i32_idx: 0,
            gc_array_set_ref_idx: 0,
            gc_array_len_idx: 0,
            gc_ref_test_idx: 0,
            gc_ref_cast_idx: 0,
            gc_collect_idx: 0,
            original_import_func_count: 0,
            original_type_count: 0,
            struct_field_counts: HashMap::new(),
            struct_field_types: HashMap::new(),
            scratch_i32_idx: 0,
            scratch_i64_idx: 0,
            scratch_f32_idx: 0,
            scratch_f64_idx: 0,
        }
    }

    pub fn shift_func_idx(&self, idx: u32) -> u32 {
        if idx >= self.original_import_func_count {
            idx + 20
        } else {
            idx
        }
    }

    pub fn rewrite(&mut self) -> Result<Vec<u8>, RewriteError> {
        let mut module = Module::new();
        let mut parser = Parser::new(0);

        self.count_imports()?;

        let base = self.original_import_func_count;
        self.gc_struct_new_idx         = base + 0;
        self.gc_struct_get_i32_idx     = base + 1;
        self.gc_struct_get_i64_idx     = base + 2;
        self.gc_struct_get_f32_idx     = base + 3;
        self.gc_struct_get_f64_idx     = base + 4;
        self.gc_struct_set_i32_idx     = base + 5;
        self.gc_struct_set_ref_idx     = base + 6;
        self.gc_struct_set_i64_idx     = base + 7;
        self.gc_struct_set_f32_idx     = base + 8;
        self.gc_struct_set_f64_idx     = base + 9;
        self.gc_array_new_idx          = base + 10;
        self.gc_array_get_i32_idx      = base + 11;
        self.gc_array_set_i32_idx      = base + 12;
        self.gc_array_set_ref_idx      = base + 13;
        self.gc_array_len_idx          = base + 14;
        self.gc_ref_test_idx           = base + 15;
        self.gc_ref_cast_idx           = base + 16;
        self.gc_collect_idx            = base + 17;
        self.gc_struct_new_default_idx = base + 18;
        self.gc_array_new_default_idx  = base + 19;

        let mut code_section = None;

        let mut remaining = &self.input[..];
        while !remaining.is_empty() {
            let (payload, consumed) = match parser.parse(remaining, true)? {
                Chunk::Parsed { payload, consumed } => (payload, consumed),
                Chunk::NeedMoreData(_) => unreachable!(),
            };
            remaining = &remaining[consumed..];

            // If we are accumulating CodeSection and hit a new section (or End)
            if code_section.is_some() && !matches!(payload, Payload::CodeSectionEntry(_) | Payload::CodeSectionStart { .. }) {
                module.section(&code_section.take().unwrap());
            }

            match payload {
                Payload::Version { .. } => {}
                Payload::TypeSection(reader) => {
                    let mut type_section = TypeSection::new();
                    for rec_group in reader {
                        let rec_group = rec_group?;
                        for ty in rec_group.types() {
                            self.original_type_count += 1;
                            match &ty.composite_type {
                                CompositeType::Func(f) => {
                                    let params: Vec<ValType> = f.params().iter().map(map_val_type).collect();
                                    let results: Vec<ValType> = f.results().iter().map(map_val_type).collect();
                                    type_section.function(params, results);
                                }
                                CompositeType::Struct(s) => {
                                    let type_idx = self.original_type_count - 1;
                                    self.struct_field_counts.insert(type_idx, s.fields.len() as u32);
                                    let field_types = s.fields.iter().map(|f| map_val_type(&f.element_type.unpack())).collect();
                                    self.struct_field_types.insert(type_idx, field_types);
                                    type_section.function(vec![], vec![]);
                                }
                                CompositeType::Array(_) => {
                                    type_section.function(vec![], vec![]);
                                }
                            }
                        }
                    }

                    // Append host function types
                    // gc_struct_new: (type_id: i32, field_count: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_struct_get_i32: (handle: i32, field_idx: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_struct_get_i64: (handle: i32, field_idx: i32) -> i64
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I64]);
                    // gc_struct_get_f32: (handle: i32, field_idx: i32) -> f32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::F32]);
                    // gc_struct_get_f64: (handle: i32, field_idx: i32) -> f64
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::F64]);
                    // gc_struct_set_i32: (handle: i32, field_idx: i32, value: i32) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::I32], vec![]);
                    // gc_struct_set_ref: (handle: i32, field_idx: i32, ref_handle: i32) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::I32], vec![]);
                    // gc_struct_set_i64: (handle: i32, field_idx: i32, value: i64) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::I64], vec![]);
                    // gc_struct_set_f32: (handle: i32, field_idx: i32, value: f32) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::F32], vec![]);
                    // gc_struct_set_f64: (handle: i32, field_idx: i32, value: f64) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::F64], vec![]);
                    // gc_array_new: (type_id: i32, length: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_array_get_i32: (handle: i32, index: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_array_set_i32: (handle: i32, index: i32, value: i32) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::I32], vec![]);
                    // gc_array_set_ref: (handle: i32, index: i32, ref_handle: i32) -> ()
                    type_section.function(vec![ValType::I32, ValType::I32, ValType::I32], vec![]);
                    // gc_array_len: (handle: i32) -> i32
                    type_section.function(vec![ValType::I32], vec![ValType::I32]);
                    // gc_ref_test: (handle: i32, expected_type_id: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_ref_cast: (handle: i32, expected_type_id: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);
                    // gc_collect: () -> ()
                    type_section.function(vec![], vec![]);
                    // gc_struct_new_default: (type_id: i32) -> i32
                    type_section.function(vec![ValType::I32], vec![ValType::I32]);
                    // gc_array_new_default: (type_id: i32, length: i32) -> i32
                    type_section.function(vec![ValType::I32, ValType::I32], vec![ValType::I32]);

                    module.section(&type_section);
                }
                Payload::ImportSection(reader) => {
                    let mut import_section = ImportSection::new();
                    for import in reader {
                        let import = import?;
                        let entity_type = match import.ty {
                            wasmparser::TypeRef::Func(idx) => EntityType::Function(idx),
                            wasmparser::TypeRef::Table(t) => EntityType::Table(wasm_encoder::TableType {
                                element_type: RefType::FUNCREF,
                                minimum: t.initial,
                                maximum: t.maximum,
                            }),
                            wasmparser::TypeRef::Memory(m) => EntityType::Memory(wasm_encoder::MemoryType {
                                minimum: m.initial,
                                maximum: m.maximum,
                                memory64: m.memory64,
                                shared: m.shared,
                            }),
                            wasmparser::TypeRef::Global(g) => EntityType::Global(wasm_encoder::GlobalType {
                                val_type: map_val_type(&g.content_type),
                                mutable: g.mutable,
                            }),
                            wasmparser::TypeRef::Tag(_) => continue,
                        };
                        import_section.import(import.module, import.name, entity_type);
                    }

                    let base_type_idx = self.original_type_count;
                    import_section.import("env", "gc_struct_new",     EntityType::Function(base_type_idx + 0));
                    import_section.import("env", "gc_struct_get_i32", EntityType::Function(base_type_idx + 1));
                    import_section.import("env", "gc_struct_get_i64", EntityType::Function(base_type_idx + 2));
                    import_section.import("env", "gc_struct_get_f32", EntityType::Function(base_type_idx + 3));
                    import_section.import("env", "gc_struct_get_f64", EntityType::Function(base_type_idx + 4));
                    import_section.import("env", "gc_struct_set_i32", EntityType::Function(base_type_idx + 5));
                    import_section.import("env", "gc_struct_set_ref", EntityType::Function(base_type_idx + 6));
                    import_section.import("env", "gc_struct_set_i64", EntityType::Function(base_type_idx + 7));
                    import_section.import("env", "gc_struct_set_f32", EntityType::Function(base_type_idx + 8));
                    import_section.import("env", "gc_struct_set_f64", EntityType::Function(base_type_idx + 9));
                    import_section.import("env", "gc_array_new",      EntityType::Function(base_type_idx + 10));
                    import_section.import("env", "gc_array_get_i32",  EntityType::Function(base_type_idx + 11));
                    import_section.import("env", "gc_array_set_i32",  EntityType::Function(base_type_idx + 12));
                    import_section.import("env", "gc_array_set_ref",  EntityType::Function(base_type_idx + 13));
                    import_section.import("env", "gc_array_len",      EntityType::Function(base_type_idx + 14));
                    import_section.import("env", "gc_ref_test",       EntityType::Function(base_type_idx + 15));
                    import_section.import("env", "gc_ref_cast",       EntityType::Function(base_type_idx + 16));
                    import_section.import("env", "gc_collect",        EntityType::Function(base_type_idx + 17));
                    import_section.import("env", "gc_struct_new_default", EntityType::Function(base_type_idx + 18));
                    import_section.import("env", "gc_array_new_default",  EntityType::Function(base_type_idx + 19));

                    module.section(&import_section);
                }
                Payload::FunctionSection(reader) => {
                    let mut section = FunctionSection::new();
                    for ty in reader {
                        section.function(ty?);
                    }
                    module.section(&section);
                }
                Payload::TableSection(reader) => {
                    let mut section = TableSection::new();
                    for t in reader {
                        let t = t?;
                        section.table(wasm_encoder::TableType {
                            element_type: RefType::FUNCREF,
                            minimum: t.ty.initial,
                            maximum: t.ty.maximum,
                        });
                    }
                    module.section(&section);
                }
                Payload::MemorySection(reader) => {
                    let mut section = MemorySection::new();
                    for m in reader {
                        let m = m?;
                        section.memory(wasm_encoder::MemoryType {
                            minimum: m.initial,
                            maximum: m.maximum,
                            memory64: m.memory64,
                            shared: m.shared,
                        });
                    }
                    module.section(&section);
                }
                Payload::GlobalSection(reader) => {
                    let mut section = GlobalSection::new();
                    let mut global_count = 0;
                    for g in reader {
                        let g = g?;
                        global_count += 1;
                        let ty = wasm_encoder::GlobalType {
                            val_type: map_val_type(&g.ty.content_type),
                            mutable: g.ty.mutable,
                        };
                        // Extract simple init expressions
                        let mut op_reader = g.init_expr.get_operators_reader();
                        let op = op_reader.read()?;
                        let expr = match op {
                            wasmparser::Operator::I32Const { value } => ConstExpr::i32_const(value),
                            wasmparser::Operator::I64Const { value } => ConstExpr::i64_const(value),
                            wasmparser::Operator::F32Const { value } => ConstExpr::f32_const(f32::from_bits(value.bits())),
                            wasmparser::Operator::F64Const { value } => ConstExpr::f64_const(f64::from_bits(value.bits())),
                            wasmparser::Operator::GlobalGet { global_index } => ConstExpr::global_get(global_index),
                            wasmparser::Operator::RefNull { .. } => ConstExpr::ref_null(HeapType::Func),
                            wasmparser::Operator::RefFunc { function_index } => ConstExpr::ref_func(self.shift_func_idx(function_index)),
                            _ => ConstExpr::i32_const(0), // Fallback
                        };
                        section.global(ty, &expr);
                    }
                    
                    self.scratch_i32_idx = global_count;
                    self.scratch_i64_idx = global_count + 1;
                    self.scratch_f32_idx = global_count + 2;
                    self.scratch_f64_idx = global_count + 3;

                    section.global(wasm_encoder::GlobalType { val_type: ValType::I32, mutable: true }, &ConstExpr::i32_const(0));
                    section.global(wasm_encoder::GlobalType { val_type: ValType::I64, mutable: true }, &ConstExpr::i64_const(0));
                    section.global(wasm_encoder::GlobalType { val_type: ValType::F32, mutable: true }, &ConstExpr::f32_const(0.0));
                    section.global(wasm_encoder::GlobalType { val_type: ValType::F64, mutable: true }, &ConstExpr::f64_const(0.0));

                    module.section(&section);
                }
                Payload::ExportSection(reader) => {
                    let mut section = ExportSection::new();
                    for e in reader {
                        let e = e?;
                        match e.kind {
                            ExternalKind::Func => {
                                section.export(e.name, wasm_encoder::ExportKind::Func, self.shift_func_idx(e.index));
                            }
                            ExternalKind::Table => {
                                section.export(e.name, wasm_encoder::ExportKind::Table, e.index);
                            }
                            ExternalKind::Memory => {
                                section.export(e.name, wasm_encoder::ExportKind::Memory, e.index);
                            }
                            ExternalKind::Global => {
                                section.export(e.name, wasm_encoder::ExportKind::Global, e.index);
                            }
                            ExternalKind::Tag => continue,
                        }
                    }
                    module.section(&section);
                }
                Payload::StartSection { func, range: _ } => {
                    module.section(&wasm_encoder::StartSection { function_index: self.shift_func_idx(func) });
                }
                Payload::ElementSection(reader) => {
                    let mut section = ElementSection::new();
                    for el in reader {
                        let el = el?;
                        match el.items {
                            wasmparser::ElementItems::Functions(funcs) => {
                                let funcs_vec: Result<Vec<u32>, _> = funcs.into_iter().map(|f| f.map(|idx| self.shift_func_idx(idx))).collect();
                                if let Ok(f_vec) = funcs_vec {
                                    if let wasmparser::ElementKind::Active { table_index, offset_expr } = el.kind {
                                        let mut op_reader = offset_expr.get_operators_reader();
                                        let op = op_reader.read().unwrap_or(wasmparser::Operator::I32Const { value: 0 });
                                        let expr = match op {
                                            wasmparser::Operator::I32Const { value } => ConstExpr::i32_const(value),
                                            _ => ConstExpr::i32_const(0),
                                        };
                                        section.active(Some(table_index.unwrap_or(0)), &expr, Elements::Functions(&f_vec));
                                    }
                                }
                            }
                            _ => {} // Skip expressions for now
                        }
                    }
                    module.section(&section);
                }
                Payload::DataCountSection { count, range: _ } => {
                    module.section(&wasm_encoder::DataCountSection { count });
                }
                Payload::DataSection(reader) => {
                    let mut section = DataSection::new();
                    for d in reader {
                        let d = d?;
                        match d.kind {
                            wasmparser::DataKind::Active { memory_index, offset_expr } => {
                                let mut op_reader = offset_expr.get_operators_reader();
                                let op = op_reader.read().unwrap_or(wasmparser::Operator::I32Const { value: 0 });
                                let expr = match op {
                                    wasmparser::Operator::I32Const { value } => ConstExpr::i32_const(value),
                                    _ => ConstExpr::i32_const(0),
                                };
                                section.active(memory_index, &expr, d.data.iter().copied());
                            }
                            wasmparser::DataKind::Passive => {
                                section.passive(d.data.iter().copied());
                            }
                        }
                    }
                    module.section(&section);
                }
                Payload::CodeSectionStart { .. } => {
                    code_section = Some(CodeSection::new());
                }
                Payload::CodeSectionEntry(body) => {
                    let mut function = wasm_encoder::Function::new(
                        body.get_locals_reader()?.into_iter().map(|l| {
                            let l = l.unwrap();
                            (l.0, map_val_type(&l.1))
                        }).collect::<Vec<_>>()
                    );

                    let mut operators = body.get_operators_reader()?;
                    while !operators.eof() {
                        let op = operators.read()?;
                        match op {
                            wasmparser::Operator::StructNew { struct_type_index } => {
                                let field_count = self.struct_field_counts.get(&(struct_type_index as u32)).cloned().unwrap_or(0);
                                function.instruction(&Instruction::I32Const(struct_type_index as i32));
                                function.instruction(&Instruction::I32Const(field_count as i32));
                                function.instruction(&Instruction::Call(self.gc_struct_new_idx));
                            }
                            wasmparser::Operator::StructNewDefault { struct_type_index } => {
                                function.instruction(&Instruction::I32Const(struct_type_index as i32));
                                function.instruction(&Instruction::Call(self.gc_struct_new_default_idx));
                            }

                            wasmparser::Operator::StructGet { struct_type_index, field_index } => {
                                let val_type = self.struct_field_types.get(&struct_type_index)
                                    .and_then(|fields| fields.get(field_index as usize))
                                    .copied()
                                    .unwrap_or(ValType::I32);
                                
                                function.instruction(&Instruction::I32Const(field_index as i32));
                                let call_idx = match val_type {
                                    ValType::I64 => self.gc_struct_get_i64_idx,
                                    ValType::F32 => self.gc_struct_get_f32_idx,
                                    ValType::F64 => self.gc_struct_get_f64_idx,
                                    _ => self.gc_struct_get_i32_idx,
                                };
                                function.instruction(&Instruction::Call(call_idx));
                            }
                            wasmparser::Operator::StructSet { struct_type_index, field_index } => {
                                let val_type = self.struct_field_types.get(&struct_type_index)
                                    .and_then(|fields| fields.get(field_index as usize))
                                    .copied()
                                    .unwrap_or(ValType::I32);
                                
                                let (scratch_idx, call_idx) = match val_type {
                                    ValType::I32 => (self.scratch_i32_idx, self.gc_struct_set_i32_idx),
                                    ValType::I64 => (self.scratch_i64_idx, self.gc_struct_set_i64_idx),
                                    ValType::F32 => (self.scratch_f32_idx, self.gc_struct_set_f32_idx),
                                    ValType::F64 => (self.scratch_f64_idx, self.gc_struct_set_f64_idx),
                                    _ => (self.scratch_i32_idx, self.gc_struct_set_ref_idx),
                                };

                                function.instruction(&Instruction::GlobalSet(scratch_idx));
                                function.instruction(&Instruction::I32Const(field_index as i32));
                                function.instruction(&Instruction::GlobalGet(scratch_idx));
                                function.instruction(&Instruction::Call(call_idx));
                            }
                            wasmparser::Operator::ArrayNew { array_type_index } => {
                                function.instruction(&Instruction::I32Const(array_type_index as i32));
                                function.instruction(&Instruction::Call(self.gc_array_new_idx));
                            }
                            wasmparser::Operator::ArrayNewDefault { array_type_index } => {
                                function.instruction(&Instruction::I32Const(array_type_index as i32));
                                function.instruction(&Instruction::Call(self.gc_array_new_default_idx));
                            }
                            wasmparser::Operator::ArrayGet { .. } => {
                                // Default to i32 for now
                                function.instruction(&Instruction::Call(self.gc_array_get_i32_idx));
                            }
                            wasmparser::Operator::ArraySet { .. } => {
                                // Default to i32 for now, or detect type if we track array types
                                function.instruction(&Instruction::GlobalSet(self.scratch_i32_idx));
                                function.instruction(&Instruction::GlobalGet(self.scratch_i32_idx));
                                function.instruction(&Instruction::Call(self.gc_array_set_i32_idx));
                            }
                            wasmparser::Operator::ArrayLen => {
                                function.instruction(&Instruction::Call(self.gc_array_len_idx));
                            }
                            wasmparser::Operator::RefTestNonNull { hty } | wasmparser::Operator::RefTestNullable { hty } => {
                                let type_id = match hty {
                                    wasmparser::HeapType::Concrete(idx) => match idx {
                                        wasmparser::UnpackedIndex::Module(i) => i,
                                        _ => 0,
                                    },
                                    _ => 0,
                                };
                                function.instruction(&Instruction::I32Const(type_id as i32));
                                function.instruction(&Instruction::Call(self.gc_ref_test_idx));
                            }
                            wasmparser::Operator::RefCastNonNull { hty } | wasmparser::Operator::RefCastNullable { hty } => {
                                let type_id = match hty {
                                    wasmparser::HeapType::Concrete(idx) => match idx {
                                        wasmparser::UnpackedIndex::Module(i) => i,
                                        _ => 0,
                                    },
                                    _ => 0,
                                };
                                function.instruction(&Instruction::I32Const(type_id as i32));
                                function.instruction(&Instruction::Call(self.gc_ref_cast_idx));
                            }
                            _ => {
                                let translated = self.translate_op(&op);
                                function.instruction(&translated);
                            }
                        }
                    }
                    if let Some(cs) = code_section.as_mut() {
                        cs.function(&function);
                    }
                }
                _ => {} // Drop other sections
            }
        }
        
        // Ensure code section is appended if it's the last section
        if let Some(cs) = code_section {
            module.section(&cs);
        }

        Ok(module.finish())
    }

    fn count_imports(&mut self) -> Result<(), RewriteError> {
        let mut parser = Parser::new(0);
        let mut remaining = &self.input[..];
        while !remaining.is_empty() {
            let (payload, consumed) = match parser.parse(remaining, true)? {
                Chunk::Parsed { payload, consumed } => (payload, consumed),
                Chunk::NeedMoreData(_) => unreachable!(),
            };
            remaining = &remaining[consumed..];

            if let Payload::ImportSection(reader) = payload {
                for import in reader {
                    let import = import?;
                    if let wasmparser::TypeRef::Func(_) = import.ty {
                        self.original_import_func_count += 1;
                    }
                }
                break;
            }
        }
        Ok(())
    }

    fn translate_op<'a>(&self, op: &wasmparser::Operator<'a>) -> Instruction<'a> {
        use wasmparser::Operator as P;
        use wasm_encoder::Instruction as E;

        match op {
            P::Unreachable => E::Unreachable,
            P::Nop => E::Nop,
            P::Block { blockty } => E::Block(map_block_type(*blockty)),
            P::Loop { blockty } => E::Loop(map_block_type(*blockty)),
            P::If { blockty } => E::If(map_block_type(*blockty)),
            P::Else => E::Else,
            P::End => E::End,
            P::Br { relative_depth } => E::Br(*relative_depth),
            P::BrIf { relative_depth } => E::BrIf(*relative_depth),
            P::Return => E::Return,
            P::Call { function_index } => E::Call(self.shift_func_idx(*function_index)),
            P::CallIndirect { type_index, table_index, table_byte: _ } => E::CallIndirect { ty: *type_index, table: *table_index },
            P::RefFunc { function_index } => E::RefFunc(self.shift_func_idx(*function_index)),

            P::LocalGet { local_index } => E::LocalGet(*local_index),
            P::LocalSet { local_index } => E::LocalSet(*local_index),
            P::LocalTee { local_index } => E::LocalTee(*local_index),
            P::GlobalGet { global_index } => E::GlobalGet(*global_index),
            P::GlobalSet { global_index } => E::GlobalSet(*global_index),

            P::I32Load { memarg } => E::I32Load(map_memarg(memarg)),
            P::I64Load { memarg } => E::I64Load(map_memarg(memarg)),
            P::F32Load { memarg } => E::F32Load(map_memarg(memarg)),
            P::F64Load { memarg } => E::F64Load(map_memarg(memarg)),
            P::I32Load8S { memarg } => E::I32Load8S(map_memarg(memarg)),
            P::I32Load8U { memarg } => E::I32Load8U(map_memarg(memarg)),
            P::I32Load16S { memarg } => E::I32Load16S(map_memarg(memarg)),
            P::I32Load16U { memarg } => E::I32Load16U(map_memarg(memarg)),
            P::I32Store { memarg } => E::I32Store(map_memarg(memarg)),
            P::I64Store { memarg } => E::I64Store(map_memarg(memarg)),
            P::F32Store { memarg } => E::F32Store(map_memarg(memarg)),
            P::F64Store { memarg } => E::F64Store(map_memarg(memarg)),
            P::I32Store8 { memarg } => E::I32Store8(map_memarg(memarg)),
            P::I32Store16 { memarg } => E::I32Store16(map_memarg(memarg)),
            
            P::I64Load8S { memarg } => E::I64Load8S(map_memarg(memarg)),
            P::I64Load8U { memarg } => E::I64Load8U(map_memarg(memarg)),
            P::I64Load16S { memarg } => E::I64Load16S(map_memarg(memarg)),
            P::I64Load16U { memarg } => E::I64Load16U(map_memarg(memarg)),
            P::I64Load32S { memarg } => E::I64Load32S(map_memarg(memarg)),
            P::I64Load32U { memarg } => E::I64Load32U(map_memarg(memarg)),
            P::I64Store8 { memarg } => E::I64Store8(map_memarg(memarg)),
            P::I64Store16 { memarg } => E::I64Store16(map_memarg(memarg)),
            P::I64Store32 { memarg } => E::I64Store32(map_memarg(memarg)),

            P::MemorySize { mem, mem_byte: _ } => E::MemorySize(*mem),
            P::MemoryGrow { mem, mem_byte: _ } => E::MemoryGrow(*mem),
            P::MemoryCopy { dst_mem, src_mem } => E::MemoryCopy { src_mem: *src_mem, dst_mem: *dst_mem },
            P::MemoryFill { mem } => E::MemoryFill(*mem),

            P::I32Const { value } => E::I32Const(*value),
            P::I64Const { value } => E::I64Const(*value),
            P::F32Const { value } => E::F32Const(f32::from_bits(value.bits())),
            P::F64Const { value } => E::F64Const(f64::from_bits(value.bits())),

            P::I32Eqz => E::I32Eqz, P::I32Eq => E::I32Eq, P::I32Ne => E::I32Ne,
            P::I32LtS => E::I32LtS, P::I32LtU => E::I32LtU, P::I32GtS => E::I32GtS, P::I32GtU => E::I32GtU,
            P::I32LeS => E::I32LeS, P::I32LeU => E::I32LeU, P::I32GeS => E::I32GeS, P::I32GeU => E::I32GeU,
            P::I64Eqz => E::I64Eqz, P::I64Eq => E::I64Eq, P::I64Ne => E::I64Ne,
            P::I64LtS => E::I64LtS, P::I64LtU => E::I64LtU, P::I64GtS => E::I64GtS, P::I64GtU => E::I64GtU,
            P::I64LeS => E::I64LeS, P::I64LeU => E::I64LeU, P::I64GeS => E::I64GeS, P::I64GeU => E::I64GeU,
            P::F32Eq => E::F32Eq, P::F32Ne => E::F32Ne, P::F32Lt => E::F32Lt, P::F32Gt => E::F32Gt, P::F32Le => E::F32Le, P::F32Ge => E::F32Ge,
            P::F64Eq => E::F64Eq, P::F64Ne => E::F64Ne, P::F64Lt => E::F64Lt, P::F64Gt => E::F64Gt, P::F64Le => E::F64Le, P::F64Ge => E::F64Ge,

            P::Drop => E::Drop, 
            P::Select => E::Select,
            
            P::I32Add => E::I32Add, P::I32Sub => E::I32Sub, P::I32Mul => E::I32Mul,
            P::I32DivS => E::I32DivS, P::I32DivU => E::I32DivU, P::I32RemS => E::I32RemS, P::I32RemU => E::I32RemU,
            P::I32And => E::I32And, P::I32Or => E::I32Or, P::I32Xor => E::I32Xor,
            P::I32Shl => E::I32Shl, P::I32ShrS => E::I32ShrS, P::I32ShrU => E::I32ShrU,
            P::I32Rotl => E::I32Rotl, P::I32Rotr => E::I32Rotr,
            
            P::I64Add => E::I64Add, P::I64Sub => E::I64Sub, P::I64Mul => E::I64Mul,
            P::I64DivS => E::I64DivS, P::I64DivU => E::I64DivU, P::I64RemS => E::I64RemS, P::I64RemU => E::I64RemU,
            P::I64And => E::I64And, P::I64Or => E::I64Or, P::I64Xor => E::I64Xor,
            P::I64Shl => E::I64Shl, P::I64ShrS => E::I64ShrS, P::I64ShrU => E::I64ShrU,
            P::I64Rotl => E::I64Rotl, P::I64Rotr => E::I64Rotr,

            P::F32Add => E::F32Add, P::F32Sub => E::F32Sub, P::F32Mul => E::F32Mul, P::F32Div => E::F32Div,
            P::F64Add => E::F64Add, P::F64Sub => E::F64Sub, P::F64Mul => E::F64Mul, P::F64Div => E::F64Div,
            
            P::I32WrapI64 => E::I32WrapI64,
            P::I32TruncF32S => E::I32TruncF32S, P::I32TruncF64S => E::I32TruncF64S,
            P::I32TruncF32U => E::I32TruncF32U, P::I32TruncF64U => E::I32TruncF64U,
            P::I64ExtendI32S => E::I64ExtendI32S, P::I64ExtendI32U => E::I64ExtendI32U,
            P::I64TruncF32S => E::I64TruncF32S, P::I64TruncF32U => E::I64TruncF32U,
            P::I64TruncF64S => E::I64TruncF64S, P::I64TruncF64U => E::I64TruncF64U,
            
            P::F32ConvertI32S => E::F32ConvertI32S, P::F64ConvertI32S => E::F64ConvertI32S,
            P::F32ConvertI32U => E::F32ConvertI32U, P::F64ConvertI32U => E::F64ConvertI32U,
            P::F32ConvertI64S => E::F32ConvertI64S, P::F64ConvertI64S => E::F64ConvertI64S,
            P::F32ConvertI64U => E::F32ConvertI64U, P::F64ConvertI64U => E::F64ConvertI64U,
            
            P::F32DemoteF64 => E::F32DemoteF64, P::F64PromoteF32 => E::F64PromoteF32,
            P::I32ReinterpretF32 => E::I32ReinterpretF32, P::F32ReinterpretI32 => E::F32ReinterpretI32,
            P::I64ReinterpretF64 => E::I64ReinterpretF64, P::F64ReinterpretI64 => E::F64ReinterpretI64,

            P::RefNull { .. } => E::I32Const(0), 
            P::RefIsNull => E::I32Eqz, 
            
            P::Throw { .. } => E::Unreachable,
            P::Rethrow { .. } => E::Unreachable,

            _ => E::Unreachable,
        }
    }
}

fn map_val_type(ty: &wasmparser::ValType) -> ValType {
    match ty {
        wasmparser::ValType::I32 => ValType::I32,
        wasmparser::ValType::I64 => ValType::I64,
        wasmparser::ValType::F32 => ValType::F32,
        wasmparser::ValType::F64 => ValType::F64,
        wasmparser::ValType::V128 => ValType::V128,
        wasmparser::ValType::Ref(_) => ValType::I32, 
    }
}

fn map_block_type(bt: wasmparser::BlockType) -> wasm_encoder::BlockType {
    match bt {
        wasmparser::BlockType::Empty => wasm_encoder::BlockType::Empty,
        wasmparser::BlockType::Type(t) => wasm_encoder::BlockType::Result(map_val_type(&t)),
        wasmparser::BlockType::FuncType(idx) => wasm_encoder::BlockType::FunctionType(idx),
    }
}

fn map_memarg(memarg: &wasmparser::MemArg) -> wasm_encoder::MemArg {
    wasm_encoder::MemArg {
        offset: memarg.offset,
        align: memarg.align as u32,
        memory_index: memarg.memory,
    }
}
// Diagnostic comment

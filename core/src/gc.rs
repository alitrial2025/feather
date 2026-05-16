//! # Feather GC Heap
//!
//! A managed garbage-collected heap for executing Kotlin/WasmGC binaries
//! on top of the baseline `wasmi` WASM 1.0 interpreter.
//!
//! ## Architecture
//!
//! Kotlin/Wasm emits WasmGC instructions (`struct.new`, `array.new`, `ref.cast`, etc.)
//! that are not supported by wasmi. We solve this by:
//!
//! 1. **Pre-processing**: A bytecode rewriter transforms WasmGC instructions into
//!    calls to host-provided GC functions before loading into wasmi.
//!
//! 2. **GcHeap**: A Rust-side mark-and-sweep garbage collector that manages all
//!    reference-typed objects (structs, arrays, i31ref, externref).
//!
//! 3. **Host Functions**: `gc_struct_new`, `gc_struct_get`, `gc_struct_set`,
//!    `gc_array_new`, `gc_array_get`, `gc_array_set`, `gc_ref_cast`, `gc_collect`
//!    are linked into the WASM instance and operate on the GcHeap.
//!
//! Objects on the GcHeap are referenced by i32 handles (indices) from WASM code.

use std::collections::{HashMap, HashSet};

// ─── GC Object Types ─────────────────────────────────────────────────────────

/// A type descriptor for a WasmGC struct, describing its field types.
#[derive(Clone, Debug)]
pub struct StructType {
    pub type_id: u32,
    pub field_count: usize,
    pub field_types: Vec<GcType>,
    /// true = mutable, false = immutable, per field
    pub field_mutability: Vec<bool>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum GcType {
    I32,
    I64,
    F32,
    F64,
    Ref,
}

impl GcType {
    pub fn default_value(&self) -> GcValue {
        match self {
            GcType::I32 => GcValue::I32(0),
            GcType::I64 => GcValue::I64(0),
            GcType::F32 => GcValue::F32(0.0),
            GcType::F64 => GcValue::F64(0.0),
            GcType::Ref => GcValue::NullRef,
        }
    }
}

/// A type descriptor for a WasmGC array.
#[derive(Clone, Debug)]
pub struct ArrayType {
    pub type_id: u32,
    pub element_type: GcType,
    pub element_mutable: bool,
}

/// A value stored in a GC-managed object field or array element.
#[derive(Clone, Debug, PartialEq)]
pub enum GcValue {
    I32(i32),
    I64(i64),
    F32(f32),
    F64(f64),
    Ref(GcRef),
    /// Null reference of a given type
    NullRef,
}

/// A reference to a GC-managed object.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum GcRef {
    /// Index into GcHeap::objects
    Struct(u32),
    /// Index into GcHeap::arrays
    Array(u32),
    /// i31ref — a 31-bit integer packed into a reference
    I31(i32),
    /// externref — opaque handle from the host
    Extern(u32),
    /// Null
    Null,
}

impl GcRef {
    /// Convert to an i32 handle for WASM. Encodes the variant in the top 2 bits.
    pub fn to_handle(self) -> i32 {
        match self {
            GcRef::Null => 0,
            GcRef::Struct(idx) => (1 << 30) | (idx as i32),
            GcRef::Array(idx) => (2 << 30) | (idx as i32),
            GcRef::I31(val) => (3i32 << 30) | (val & 0x3FFF_FFFF),
            GcRef::Extern(idx) => (1 << 29) | (idx as i32),
        }
    }

    /// Decode an i32 handle back to a GcRef.
    pub fn from_handle(handle: i32) -> GcRef {
        if handle == 0 {
            return GcRef::Null;
        }
        let tag = (handle >> 30) & 0x3;
        let payload = handle & 0x3FFF_FFFF;
        match tag {
            1 => GcRef::Struct(payload as u32),
            2 => GcRef::Array(payload as u32),
            3 => GcRef::I31(payload),
            _ => {
                // Check for Extern tag (bit 29)
                if (handle >> 29) & 1 == 1 {
                    GcRef::Extern((handle & 0x1FFF_FFFF) as u32)
                } else {
                    GcRef::Null
                }
            }
        }
    }
}

// ─── GC Objects ──────────────────────────────────────────────────────────────

/// A GC-managed struct instance.
#[derive(Clone, Debug)]
pub struct GcStruct {
    pub type_id: u32,
    pub fields: Vec<GcValue>,
    /// Used by mark-and-sweep
    pub marked: bool,
}

/// A GC-managed array instance.
#[derive(Clone, Debug)]
pub struct GcArray {
    pub type_id: u32,
    pub elements: Vec<GcValue>,
    pub marked: bool,
}

// ─── The Heap ────────────────────────────────────────────────────────────────

/// The main garbage-collected heap. All GC operations go through this.
pub struct GcHeap {
    /// Registered struct type descriptors (type_id -> StructType)
    pub struct_types: HashMap<u32, StructType>,
    /// Registered array type descriptors (type_id -> ArrayType)
    pub array_types: HashMap<u32, ArrayType>,

    /// All live struct objects. Slots can be `None` (freed).
    pub structs: Vec<Option<GcStruct>>,
    /// All live array objects.
    pub arrays: Vec<Option<GcArray>>,

    /// Free list indices for struct slots
    struct_free: Vec<u32>,
    /// Free list indices for array slots
    array_free: Vec<u32>,

    /// Root set — handles that are reachable from the WASM stack.
    /// Updated by the host before each GC cycle.
    pub roots: HashSet<i32>,

    /// Total allocations since last collection
    alloc_count: usize,
    /// Threshold to trigger automatic collection
    alloc_threshold: usize,
}

impl GcHeap {
    pub fn new() -> Self {
        GcHeap {
            struct_types: HashMap::new(),
            array_types: HashMap::new(),
            structs: Vec::new(),
            arrays: Vec::new(),
            struct_free: Vec::new(),
            array_free: Vec::new(),
            roots: HashSet::new(),
            alloc_count: 0,
            alloc_threshold: 1024, // Collect every 1024 allocations
        }
    }

    // ─── Type Registration ───────────────────────────────────────────────

    /// Register a struct type descriptor.
    pub fn register_struct_type(&mut self, stype: StructType) {
        self.struct_types.insert(stype.type_id, stype);
    }

    /// Register an array type descriptor.
    pub fn register_array_type(&mut self, atype: ArrayType) {
        self.array_types.insert(atype.type_id, atype);
    }

    // ─── Allocation ──────────────────────────────────────────────────────

    /// Allocate a new struct of the given type with the provided field values.
    pub fn struct_new(&mut self, type_id: u32, fields: Vec<GcValue>) -> GcRef {
        self.maybe_collect();

        let obj = GcStruct {
            type_id,
            fields,
            marked: false,
        };

        let idx = if let Some(free_idx) = self.struct_free.pop() {
            self.structs[free_idx as usize] = Some(obj);
            free_idx
        } else {
            let idx = self.structs.len() as u32;
            self.structs.push(Some(obj));
            idx
        };

        self.alloc_count += 1;
        GcRef::Struct(idx)
    }

    /// Allocate a new struct of the given type with default values.
    pub fn struct_new_default(&mut self, type_id: u32) -> GcRef {
        let fields = if let Some(stype) = self.struct_types.get(&type_id) {
            stype.field_types.iter().map(|t| t.default_value()).collect()
        } else {
            Vec::new()
        };
        self.struct_new(type_id, fields)
    }

    /// Allocate a new array of the given type with initial elements.
    pub fn array_new(&mut self, type_id: u32, elements: Vec<GcValue>) -> GcRef {
        self.maybe_collect();

        let obj = GcArray {
            type_id,
            elements,
            marked: false,
        };

        let idx = if let Some(free_idx) = self.array_free.pop() {
            self.arrays[free_idx as usize] = Some(obj);
            free_idx
        } else {
            let idx = self.arrays.len() as u32;
            self.arrays.push(Some(obj));
            idx
        };

        self.alloc_count += 1;
        GcRef::Array(idx)
    }

    /// Allocate a new array of the given type with default values.
    pub fn array_new_default(&mut self, type_id: u32, len: u32) -> GcRef {
        let default_val = if let Some(atype) = self.array_types.get(&type_id) {
            atype.element_type.default_value()
        } else {
            GcValue::I32(0)
        };
        let elements = vec![default_val; len as usize];
        self.array_new(type_id, elements)
    }

    // ─── Field / Element Access ──────────────────────────────────────────

    /// Get a field from a struct.
    pub fn struct_get(&self, gc_ref: GcRef, field_idx: u32) -> Option<GcValue> {
        match gc_ref {
            GcRef::Struct(idx) => {
                self.structs.get(idx as usize)?
                    .as_ref()?
                    .fields.get(field_idx as usize)
                    .cloned()
            }
            _ => None,
        }
    }

    /// Set a field on a struct.
    pub fn struct_set(&mut self, gc_ref: GcRef, field_idx: u32, value: GcValue) -> bool {
        match gc_ref {
            GcRef::Struct(idx) => {
                if let Some(Some(obj)) = self.structs.get_mut(idx as usize) {
                    if let Some(field) = obj.fields.get_mut(field_idx as usize) {
                        *field = value;
                        return true;
                    }
                }
                false
            }
            _ => false,
        }
    }

    /// Get an element from an array.
    pub fn array_get(&self, gc_ref: GcRef, index: u32) -> Option<GcValue> {
        match gc_ref {
            GcRef::Array(idx) => {
                self.arrays.get(idx as usize)?
                    .as_ref()?
                    .elements.get(index as usize)
                    .cloned()
            }
            _ => None,
        }
    }

    /// Set an element on an array.
    pub fn array_set(&mut self, gc_ref: GcRef, index: u32, value: GcValue) -> bool {
        match gc_ref {
            GcRef::Array(idx) => {
                if let Some(Some(obj)) = self.arrays.get_mut(idx as usize) {
                    if let Some(elem) = obj.elements.get_mut(index as usize) {
                        *elem = value;
                        return true;
                    }
                }
                false
            }
            _ => false,
        }
    }

    /// Get the length of an array.
    pub fn array_len(&self, gc_ref: GcRef) -> Option<u32> {
        match gc_ref {
            GcRef::Array(idx) => {
                self.arrays.get(idx as usize)?
                    .as_ref()
                    .map(|a| a.elements.len() as u32)
            }
            _ => None,
        }
    }

    // ─── Type Checking ───────────────────────────────────────────────────

    /// Check if a reference is of a given type (for ref.cast / ref.test).
    pub fn ref_test(&self, gc_ref: GcRef, expected_type_id: u32) -> bool {
        match gc_ref {
            GcRef::Struct(idx) => {
                self.structs.get(idx as usize)
                    .and_then(|o| o.as_ref())
                    .map_or(false, |s| s.type_id == expected_type_id)
            }
            GcRef::Array(idx) => {
                self.arrays.get(idx as usize)
                    .and_then(|o| o.as_ref())
                    .map_or(false, |a| a.type_id == expected_type_id)
            }
            GcRef::Null => false,
            _ => false,
        }
    }

    /// Clear all roots.
    pub fn clear_roots(&mut self) {
        self.roots.clear();
    }

    /// Add a root.
    pub fn add_root(&mut self, handle: i32) {
        if handle != 0 {
            self.roots.insert(handle);
        }
    }

    // ─── Garbage Collection (Mark & Sweep) ───────────────────────────────

    fn maybe_collect(&mut self) {
        if self.alloc_count >= self.alloc_threshold {
            self.collect();
        }
    }

    /// Run a full mark-and-sweep GC cycle.
    pub fn collect(&mut self) {
        // Phase 1: Clear all marks
        for slot in &mut self.structs {
            if let Some(obj) = slot {
                obj.marked = false;
            }
        }
        for slot in &mut self.arrays {
            if let Some(obj) = slot {
                obj.marked = false;
            }
        }

        // Phase 2: Mark from roots
        let mut worklist: Vec<GcRef> = self.roots.iter()
            .map(|&h| GcRef::from_handle(h))
            .collect();

        while let Some(gc_ref) = worklist.pop() {
            self.mark_iterative(gc_ref, &mut worklist);
        }

        // Phase 3: Sweep unmarked objects
        let mut freed = 0usize;
        for (i, slot) in self.structs.iter_mut().enumerate() {
            if let Some(obj) = slot {
                if !obj.marked {
                    *slot = None;
                    self.struct_free.push(i as u32);
                    freed += 1;
                }
            }
        }
        for (i, slot) in self.arrays.iter_mut().enumerate() {
            if let Some(obj) = slot {
                if !obj.marked {
                    *slot = None;
                    self.array_free.push(i as u32);
                    freed += 1;
                }
            }
        }

        if freed > 0 {
            println!("[GC] Collected {} objects", freed);
        }

        // Adaptive threshold: grow if we're collecting too often
        if self.alloc_count >= self.alloc_threshold {
            self.alloc_threshold = (self.alloc_threshold * 3) / 2;
        }
        self.alloc_count = 0;
    }

    /// Mark an object and add its children to the worklist.
    fn mark_iterative(&mut self, gc_ref: GcRef, worklist: &mut Vec<GcRef>) {
        match gc_ref {
            GcRef::Struct(idx) => {
                if let Some(Some(obj)) = self.structs.get_mut(idx as usize) {
                    if obj.marked {
                        return;
                    }
                    obj.marked = true;
                    for field in &obj.fields {
                        if let GcValue::Ref(child_ref) = field {
                            worklist.push(*child_ref);
                        }
                    }
                }
            }
            GcRef::Array(idx) => {
                if let Some(Some(obj)) = self.arrays.get_mut(idx as usize) {
                    if obj.marked {
                        return;
                    }
                    obj.marked = true;
                    for elem in &obj.elements {
                        if let GcValue::Ref(child_ref) = elem {
                            worklist.push(*child_ref);
                        }
                    }
                }
            }
            _ => {} // I31, Extern, Null — nothing to trace
        }
    }

    // ─── Statistics ──────────────────────────────────────────────────────

    pub fn live_struct_count(&self) -> usize {
        self.structs.iter().filter(|s| s.is_some()).count()
    }

    pub fn live_array_count(&self) -> usize {
        self.arrays.iter().filter(|a| a.is_some()).count()
    }

    pub fn total_live(&self) -> usize {
        self.live_struct_count() + self.live_array_count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_struct_alloc_and_access() {
        let mut heap = GcHeap::new();
        heap.register_struct_type(StructType {
            type_id: 1,
            field_count: 2,
            field_types: vec![GcType::I32, GcType::I32],
            field_mutability: vec![true, true],
        });

        let r = heap.struct_new(1, vec![GcValue::I32(42), GcValue::I32(99)]);
        assert_eq!(heap.struct_get(r, 0), Some(GcValue::I32(42)));
        assert_eq!(heap.struct_get(r, 1), Some(GcValue::I32(99)));

        heap.struct_set(r, 0, GcValue::I32(100));
        assert_eq!(heap.struct_get(r, 0), Some(GcValue::I32(100)));
    }

    #[test]
    fn test_struct_new_default() {
        let mut heap = GcHeap::new();
        heap.register_struct_type(StructType {
            type_id: 1,
            field_count: 3,
            field_types: vec![GcType::I32, GcType::F64, GcType::Ref],
            field_mutability: vec![true, true, true],
        });

        let r = heap.struct_new_default(1);
        assert_eq!(heap.struct_get(r, 0), Some(GcValue::I32(0)));
        assert_eq!(heap.struct_get(r, 1), Some(GcValue::F64(0.0)));
        assert_eq!(heap.struct_get(r, 2), Some(GcValue::NullRef));
    }

    #[test]
    fn test_array_alloc_and_access() {
        let mut heap = GcHeap::new();
        heap.register_array_type(ArrayType {
            type_id: 2,
            element_type: GcType::I32,
            element_mutable: true,
        });

        let r = heap.array_new(2, vec![GcValue::I32(1), GcValue::I32(2), GcValue::I32(3)]);
        assert_eq!(heap.array_len(r), Some(3));
        assert_eq!(heap.array_get(r, 1), Some(GcValue::I32(2)));

        heap.array_set(r, 1, GcValue::I32(20));
        assert_eq!(heap.array_get(r, 1), Some(GcValue::I32(20)));
    }

    #[test]
    fn test_gc_collects_unreachable() {
        let mut heap = GcHeap::new();
        heap.register_struct_type(StructType {
            type_id: 1,
            field_count: 1,
            field_types: vec![GcType::I32],
            field_mutability: vec![true],
        });

        // Allocate two objects
        let r1 = heap.struct_new(1, vec![GcValue::I32(1)]);
        let r2 = heap.struct_new(1, vec![GcValue::I32(2)]);

        // Only root r1
        heap.roots.insert(r1.to_handle());

        // Collect — r2 should be freed
        heap.collect();

        assert_eq!(heap.live_struct_count(), 1);
        assert!(heap.struct_get(r1, 0).is_some());
        assert!(heap.struct_get(r2, 0).is_none());
    }

    #[test]
    fn test_gc_preserves_reachable_graph() {
        let mut heap = GcHeap::new();
        heap.register_struct_type(StructType {
            type_id: 1,
            field_count: 1,
            field_types: vec![GcType::Ref],
            field_mutability: vec![true],
        });

        // A -> B (A's field 0 references B)
        let b = heap.struct_new(1, vec![GcValue::I32(99)]);
        let a = heap.struct_new(1, vec![GcValue::Ref(b)]);

        // Only root A — B should survive because A references it
        heap.roots.insert(a.to_handle());
        heap.collect();

        assert_eq!(heap.live_struct_count(), 2);
        assert!(heap.struct_get(b, 0).is_some());
    }

    #[test]
    fn test_handle_roundtrip() {
        let cases = vec![
            GcRef::Null,
            GcRef::Struct(42),
            GcRef::Array(100),
            GcRef::I31(12345),
        ];
        for r in cases {
            let handle = r.to_handle();
            let decoded = GcRef::from_handle(handle);
            assert_eq!(r, decoded, "Roundtrip failed for {:?}", r);
        }
    }
}
